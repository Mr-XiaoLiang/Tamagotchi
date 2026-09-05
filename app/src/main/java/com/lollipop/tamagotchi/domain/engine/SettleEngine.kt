package com.lollipop.tamagotchi.domain.engine

import com.lollipop.tamagotchi.core.attribute.AttributeDelta
import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.AttributeMap
import com.lollipop.tamagotchi.core.attribute.AttributeRegistry
import com.lollipop.tamagotchi.core.behavior.PetState
import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.domain.model.Traits
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 打开即结算（doc/01 §8，Task M5.S1）。
 *
 * 纯 domain、零 UI 依赖：输入 `(now, 快照)`，输出 [Settlement]（更新快照 + [SettlementSummary]）。
 *
 * **M5 落地范围（快速积分兜底全程）**：
 * - 幂等：`now <= lastSettledAt` → noOp（快照原样返回，`changed=false`）；
 * - 积分路径：把窗口切成 ≤1h 的段（昼夜边界对齐），逐段按速率表累计——
 *   trait 系数、饥饿/脏/作息修正都落在「段」这一粒度上，误差可控（doc/01 §6 数值平衡不失效）；
 * - doc/01 §8.1 的「≥20min 事件化时间线」是 M7 分支：M5 先用本积分兜住任意窗口
 *   （保证主链完整、时间旅行 24h 手测可用），[MIN_TIMELINE_MS] 常量在此声明为未来切换阈值；
 * - 状态推进（doc/01 §5/§8.2）：health 触底 → SICK；mood<20 连续 ≥[SAD_TRIGGER_HOURS]h → SAD；
 *   否则 → IDLE。睡眠（SLEEPING）**不由本引擎写入**——作息是渲染期 FSM 的职责
 *   （FSM 需要从快照 SAD/IDLE 起播才能保留「醒来回 SAD」的 wakeTo 语义，见 BehaviorFSM）；
 * - 无压力落地：属性逐段经 [AttributeMap.set] clamp 到注册表 [floor, ceiling]，
 *   0 只是地板，SICK 不随时间恶化、无死亡路径；
 * - `sickTotal` 里程碑仅在「非 SICK → SICK」跃迁时 +1（doc/01 §9 stats，只增不减）。
 *
 * **速率档与 trait 系数（S1 落地注记，待实测可调，见 doc/01 §11 回写）**：
 * | 档 | sat/h | mood/h | health/h | hyg/h |
 * |---|---|---|---|---|
 * | 白昼清醒 | -2.0 | -1.0 | 0 | -0.6 |
 * | 夜间入睡（22~7，SICK 除外） | -0.2 | 0 | +1.0 | -0.1 |
 * | SICK | -3.0 | -2.0 | 0 | -0.3 |
 * - sat × (0.5+appetite)（贪吃饿得快，doc/02 §4.4 同款）；
 * - mood × (1.2 − temper×0.3)（温和更稳，doc/02 §4.4；temper 高=温和）；
 * - 清醒段（含 SICK）：hyg<30 → mood ×1.3（doc/01 §4.2）；sat<20 → mood 额外 -2.0/h（doc/01 §6.1 饥饿行）；
 * - SAD 期间 health 仅清醒段 -1.0/h（doc/01 §5「缓慢下滑」初值，可调）；
 * - 夜间视为入睡（FSM 保证 22~7 入睡、SICK 不睡），故「夜间」档 = doc/01 §6.1「睡眠态」行，
 *   合并为「白天 / 睡眠 / SICK」三档实现（睡眠回血在夜里离线发生）。
 */
class SettleEngine(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {

    /**
     * 结算一次。同 `now` 重复结算幂等：第二次 `now <= lastSettledAt` → noOp，
     * 快照与 [SettlementSummary] 均原样/零值返回。
     */
    fun settle(now: Long, snapshot: PetProfile): Settlement {
        val last = snapshot.lastSettledAt
        if (now <= last) {
            return Settlement(
                profile = snapshot,
                summary = SettlementSummary(elapsedMs = 0L, totalDelta = AttributeDelta.EMPTY),
                changed = false,
            )
        }
        return integrate(last, now, snapshot)
    }

    // ── 快速积分主流程 ─────────────────────────────────────

    private fun integrate(last: Long, now: Long, snapshot: PetProfile): Settlement {
        val traits = snapshot.personality.traits
        val startAttrs = snapshot.attributes
        var attrs = startAttrs
        var sick = attrs[HEALTH] <= 0f
        // SAD 历史：快照处于 SAD 才算「难过期持续」，其 health 下滑只对清醒段生效。
        // 本窗内新触发的 SAD 从下一窗起才计入下滑——与 FSM「SAD 由 settle 写入、下窗延续」一致。
        val sadStart = snapshot.fsmState == PetState.SAD
        var sadHours = snapshot.sadDurationHours

        for (seg in dayNightSegments(last, now)) {
            val hours = (seg.end - seg.start).toFloat() / HOUR_MS
            val regime = when {
                sick -> Regime.SICK
                seg.night -> Regime.SLEEP
                else -> Regime.DAY
            }
            attrs = attrs
                .set(SATIATION, attrs[SATIATION] + satDelta(regime, traits) * hours)
                .set(MOOD, attrs[MOOD] + moodDelta(regime, traits, attrs) * hours)
                .set(HEALTH, attrs[HEALTH] + healthDelta(regime, sadStart) * hours)
                .set(HYGIENE, attrs[HYGIENE] + hygDelta(regime) * hours)

            if (attrs[HEALTH] <= 0f) sick = true
            // 连续低于 SAD 阈值的累计：任一段末回升即清零（「持续」被打断）。
            sadHours = if (attrs[MOOD] < MOOD_SAD_THRESHOLD) sadHours + hours else 0f
        }

        // ── 状态推进（温和，doc/01 §5/§8.2）─────────────────
        val health = attrs[HEALTH]
        val mood = attrs[MOOD]
        val state = when {
            health <= 0f -> PetState.SICK
            mood < MOOD_SAD_THRESHOLD && sadHours >= SAD_TRIGGER_HOURS -> PetState.SAD
            else -> PetState.IDLE
        }
        val milestones = if (state == PetState.SICK && snapshot.fsmState != PetState.SICK) {
            snapshot.milestones.copy(sickTotal = snapshot.milestones.sickTotal + 1)
        } else {
            snapshot.milestones
        }
        // 情绪最终恢复（≥阈值）→ 持续计时清零；否则保留累计（下窗续计）。
        val finalSadHours = if (mood >= MOOD_SAD_THRESHOLD) 0f else sadHours

        val updated = snapshot.copy(
            lastSettledAt = now,
            attributes = attrs,
            fsmState = state,
            sadDurationHours = finalSadHours,
            milestones = milestones,
        )
        val totalDelta = AttributeDelta(
            AttributeRegistry.all.associate { it.id to (attrs[it.id] - startAttrs[it.id]) },
        )
        val summary = SettlementSummary(elapsedMs = now - last, totalDelta = totalDelta)
        return Settlement(profile = updated, summary = summary, changed = true)
    }

    // ── 速率（每属性 / 每小时；本文件头部注记表）────────────

    private fun satDelta(regime: Regime, traits: Traits): Float =
        when (regime) {
            Regime.DAY -> -2.0f
            Regime.SLEEP -> -0.2f
            Regime.SICK -> -3.0f
        } * (0.5f + traits.appetite)

    private fun moodDelta(regime: Regime, traits: Traits, attrs: AttributeMap): Float {
        if (regime == Regime.SLEEP) return 0f // 睡眠无情绪波动，也不受饥饿/脏影响
        var rate = when (regime) {
            Regime.DAY -> -1.0f
            Regime.SICK -> -2.0f
            Regime.SLEEP -> 0f
        } * (1.2f - traits.temper * 0.3f)
        if (attrs[HYGIENE] < HYG_DIRTY_THRESHOLD) rate *= DIRTY_MOOD_MULTIPLIER
        if (attrs[SATIATION] < SAT_HUNGRY_THRESHOLD) rate += HUNGER_MOOD_EXTRA_PER_H
        return rate
    }

    private fun healthDelta(regime: Regime, sadStart: Boolean): Float = when (regime) {
        Regime.SLEEP -> +1.0f // 睡觉回血
        Regime.SICK -> 0f // SICK clamp 不随时间恶化
        Regime.DAY -> if (sadStart) SAD_HEALTH_DROP_PER_H else 0f
    }

    private fun hygDelta(regime: Regime): Float = when (regime) {
        Regime.DAY -> -0.6f
        Regime.SLEEP -> -0.1f
        Regime.SICK -> -0.3f
    }

    // ── 时段切片（昼夜边界对齐，段长 ≤ 60min）──────────────

    private data class Segment(val start: Long, val end: Long, val night: Boolean)

    private fun dayNightSegments(from: Long, until: Long): List<Segment> {
        val segs = ArrayList<Segment>()
        var t = from
        while (t < until) {
            val zdt = Instant.ofEpochMilli(t).atZone(zoneId)
            val boundary = zdt.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
            val end = minOf(until, boundary)
            segs += Segment(start = t, end = end, night = isNight(t))
            t = end
        }
        return segs
    }

    /** 作息：22:00~07:00（本地时区）为夜间（与 BehaviorFSM.isNight 同口径）。 */
    private fun isNight(ms: Long): Boolean {
        val hour = Instant.ofEpochMilli(ms).atZone(zoneId).hour
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
    }

    private enum class Regime { DAY, SLEEP, SICK }

    /** 结算结果：更新后快照 + 输出契约。 */
    data class Settlement(
        val profile: PetProfile,
        val summary: SettlementSummary,
        /** false = noOp（now ≤ lastSettledAt），快照未被改动。 */
        val changed: Boolean,
    )

    companion object {
        private val SATIATION = AttributeId.SATIATION
        private val MOOD = AttributeId.MOOD
        private val HEALTH = AttributeId.HEALTH
        private val HYGIENE = AttributeId.HYGIENE

        /** doc/01 §8.1 长离线事件化阈值（≥ 本值切时间线）；M7 启用，M5 积分兜底全程。 */
        const val MIN_TIMELINE_MS: Long = 20 * 60 * 1000L

        private const val HOUR_MS: Float = 3_600_000f

        // 作息窗（doc/02 §3）
        private const val NIGHT_START_HOUR = 22
        private const val NIGHT_END_HOUR = 7

        // 状态推进阈值（doc/01 §5）
        private const val MOOD_SAD_THRESHOLD = 20f
        private const val SAT_HUNGRY_THRESHOLD = 20f
        private const val HYG_DIRTY_THRESHOLD = 30f

        /** mood<20 持续 ≥ 本时长 → SAD（doc/01 §5）。 */
        const val SAD_TRIGGER_HOURS: Float = 4f

        /** 清洁低的心情衰减加成（doc/01 §4.2：×1.3）。 */
        private const val DIRTY_MOOD_MULTIPLIER = 1.3f

        /** 饥饿额外心情消耗（doc/01 §6.1 饥饿行：额外 -2.0/h）。 */
        private const val HUNGER_MOOD_EXTRA_PER_H = -2.0f

        /** SAD 期间健康缓慢下滑初值（doc/01 §5 未给数值，S1 落地注记：可调）。 */
        private const val SAD_HEALTH_DROP_PER_H = -1.0f
    }
}
