package com.lollipop.tamagotchi.domain.engine

import com.lollipop.tamagotchi.core.attribute.AttributeDelta
import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.AttributeMap
import com.lollipop.tamagotchi.core.attribute.AttributeRegistry
import com.lollipop.tamagotchi.core.behavior.PetState
import com.lollipop.tamagotchi.domain.model.PetProfile
import java.time.ZoneId

/**
 * 打开即结算（doc/01 §8，Task M5.S1 / M7.S1）。
 *
 * 纯 domain、零 UI 依赖：输入 `(now, 快照)`，输出 [Settlement]（更新快照 + [SettlementSummary]）。
 *
 * **两档路径（doc/01 §8.1）**：
 * - 短离线（< [MIN_TIMELINE_MS]，20min）：[integrate] 快速积分，不铺时间线；
 * - 长离线（≥ [MIN_TIMELINE_MS]）：在快速积分结果（数值守恒基准）之上，由 [OfflineTimelineBuilder]
 *   生成「在你离开期间发生了…」事件时间线（[SettlementSummary.offlineTimeline] + [EndingMood]），
 *   骨架事件聚合 = 快速积分 totalDelta（doc/03 §7.5 守恒），详见 doc/03 §7 / 01 §8.2。
 *
 * 速率档 / trait 系数 / 状态推进 全部集中在 [DecayModel] + [simulateSegments]（M7 抽出共用，
 * 防 SettleEngine 与生成器漂移），本文件只编排流程。
 *
 * **速率档与 trait 系数（S1 落地注记，待实测可调，见 doc/01 §11 回写）**：
 * | 档 | sat/h | mood/h | health/h | hyg/h |
 * |---|---|---|---|---|
 * | 白昼清醒 | -2.0 | -1.0 | 0 | -0.6 |
 * | 夜间入睡（22~7，SICK 除外） | -0.2 | 0 | +1.0 | -0.1 |
 * | SICK | -3.0 | -2.0 | 0 | -0.3 |
 * - sat × (0.5+appetite)；mood × (1.2 − temper×0.3)；
 * - 清醒段：hyg<30 → mood ×1.3；sat<20 → mood 额外 -2.0/h；
 * - SAD 期间 health 仅清醒段 -1.0/h（sadStart 标志，doc/01 §5）。
 */
class SettleEngine(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val timelineBuilder: OfflineTimelineBuilder = DefaultOfflineTimelineBuilder(zoneId),
) {

    /**
     * 结算一次。同 `now` 重复结算幂等：第二次 `now <= lastSettledAt` → noOp，快照原样返回。
     * 长离线（≥ [MIN_TIMELINE_MS]）在快速积分（守恒基准）之上附事件化时间线。
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
        // 快速积分（同时是长离线的数值守恒基准）
        val ref = integrate(last, now, snapshot)
        val elapsed = now - last
        if (elapsed < MIN_TIMELINE_MS) return ref

        // 长离线：附事件化时间线（doc/03 §7 / 01 §8.2）
        val timeline = timelineBuilder.build(
            window = last..now,
            startAttrs = snapshot.attributes,
            personality = snapshot.personality,
            startSad = snapshot.fsmState == PetState.SAD,
        )
        val summary = ref.summary.copy(
            offlineTimeline = timeline.events,
            endingMood = timeline.endingMood,
        )
        return ref.copy(summary = summary)
    }

    // ── 快速积分主流程（与生成器共用 simulateSegments）─────────

    private fun integrate(last: Long, now: Long, snapshot: PetProfile): Settlement {
        val traits = snapshot.personality.traits
        val sim = simulateSegments(
            from = last,
            until = now,
            zoneId = zoneId,
            startAttrs = snapshot.attributes,
            traits = traits,
            sickStart = snapshot.attributes[AttributeId.HEALTH] <= 0f,
            sadStart = snapshot.fsmState == PetState.SAD,
            sadStartHours = snapshot.sadDurationHours,
        )
        val endAttrs = sim.endAttrs
        val health = endAttrs[AttributeId.HEALTH]
        val mood = endAttrs[AttributeId.MOOD]
        // 状态推进（温和，doc/01 §5/§8.2）：health 触底 → SICK；mood<20 持续 ≥4h → SAD；否则 IDLE
        val state = when {
            health <= 0f -> PetState.SICK
            mood < DecayModel.MOOD_SAD_THRESHOLD && sim.sadHours >= DecayModel.SAD_TRIGGER_HOURS -> PetState.SAD
            else -> PetState.IDLE
        }
        val milestones = if (state == PetState.SICK && snapshot.fsmState != PetState.SICK) {
            snapshot.milestones.copy(sickTotal = snapshot.milestones.sickTotal + 1)
        } else {
            snapshot.milestones
        }
        // 情绪最终恢复（≥阈值）→ 持续计时清零；否则保留累计（下窗续计）
        val finalSadHours = if (mood >= DecayModel.MOOD_SAD_THRESHOLD) 0f else sim.sadHours

        val updated = snapshot.copy(
            lastSettledAt = now,
            attributes = endAttrs,
            fsmState = state,
            sadDurationHours = finalSadHours,
            milestones = milestones,
        )
        val totalDelta = AttributeDelta(
            AttributeRegistry.all.associate { it.id to (endAttrs[it.id] - snapshot.attributes[it.id]) },
        )
        val summary = SettlementSummary(elapsedMs = now - last, totalDelta = totalDelta)
        return Settlement(profile = updated, summary = summary, changed = true)
    }

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
    }
}
