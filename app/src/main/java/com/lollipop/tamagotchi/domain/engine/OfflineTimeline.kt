package com.lollipop.tamagotchi.domain.engine

import com.lollipop.tamagotchi.core.attribute.AttributeDelta
import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.AttributeMap
import com.lollipop.tamagotchi.core.attribute.AttributeRegistry
import com.lollipop.tamagotchi.domain.model.Personality
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min

/**
 * 离线事件化时间线的输出端（doc/03 §7.6）。
 * 类型 [OfflineKind] / [EndingMood] / [OfflineEvent] 与 [SettlementSummary] 见 Settlement.kt。
 */

/** 一条离线时间线（doc/03 §7.6）：事件序列 + 结尾基调。 */
data class OfflineTimeline(
    val events: List<OfflineEvent>,
    val endingMood: EndingMood,
)

/**
 * 离线事件时间线生成器（doc/03 §7.7，domain 纯 Kotlin 可单测）。
 *
 * 以「快照时间戳 = 窗口起点」为随机种子，保证同一 `lastSettledAt` 重复结算得到同一时间线（幂等）。
 * 生成规则：
 * - 骨架（PHYSIO）事件把快速积分的总预算按时间片份额切分，**聚合严格守恒**（= 快速积分 totalDelta）；
 * - 突发（SUDDEN）扰动插花其间，固定小值、净扰动受控（每 24h 离线 |ΣΔ| ≤ 3 且正向略多）；
 * - 条数封顶 48（>72h 收紧到 24）；
 * - 结尾基调由终态 + 末事件推导。
 */
interface OfflineTimelineBuilder {
    fun build(
        window: ClosedRange<Long>,
        startAttrs: AttributeMap,
        personality: Personality,
        /** 起始是否 SAD 态（决定健康下滑档，见 DecayModel）；默认 false。 */
        startSad: Boolean = false,
    ): OfflineTimeline
}

class DefaultOfflineTimelineBuilder(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : OfflineTimelineBuilder {

    override fun build(
        window: ClosedRange<Long>,
        startAttrs: AttributeMap,
        personality: Personality,
        startSad: Boolean,
    ): OfflineTimeline {
        val start = window.start
        val end = window.endInclusive
        val elapsedMs = end - start
        val traits = personality.traits
        // 种子 = 离线起点（lastSettledAt）→ 同窗口重复结算同线
        val rng = java.util.Random(start)

        // 1) 参考快速积分 → 骨架总预算（与 SettleEngine 同模型，守恒基准）
        val sim = simulateSegments(
            from = start,
            until = end,
            zoneId = zoneId,
            startAttrs = startAttrs,
            traits = traits,
            sickStart = startAttrs[AttributeId.HEALTH] <= 0f,
            sadStart = startSad,
            sadStartHours = 0f,
        )
        val totalDelta = AttributeDelta(
            AttributeRegistry.all.associate { it.id to (sim.endAttrs[it.id] - startAttrs[it.id]) },
        )

        // 2) 事件条数预算（doc/03 §7.3 / §7.5）
        val elapsedHours = elapsedMs / DecayModel.HOUR_MS
        val intervalMin = physioIntervalMinutes(traits, start, end)
        val cap = if (elapsedMs > 72 * 3_600_000L) 24 else 48
        val pCount = minOf(cap, maxOf(1, (elapsedMs / (intervalMin * 60_000f)).toInt()))
        // 突发净扰动上界：每 24h 离线 |ΣΔ| ≤ 3（doc/03 §7.5）
        val suddenBudget = 3f * (elapsedHours / 24f)

        // 3) 生理骨架事件：按时间片份额切分总预算（求和守恒）
        val events = ArrayList<OfflineEvent>()
        val splits = computeSplitPoints(pCount, start, elapsedMs, rng)
        var prev = start
        for (i in 1..pCount) {
            val cur = splits[i]
            val frac = (cur - prev).toFloat() / elapsedMs
            val delta = AttributeDelta(
                AttributeRegistry.all.associate { it.id to totalDelta[it.id] * frac },
            )
            val night = DecayModel.isNight((prev + cur) / 2, zoneId)
            val bubble = physioBubble(delta, night)
            events += OfflineEvent(
                ts = cur,
                kind = OfflineKind.PHYSIO,
                eventId = bubble,
                bubbleId = bubble,
                delta = delta,
                highlight = (i == 1) || (i == pCount),
            )
            prev = cur
        }

        // 4) 突发扰动（正负受控，净值 ≤ budget）
        insertSudden(events, pCount, traits, rng, suddenBudget, cap)

        // 5) 按时间排序
        events.sortBy { it.ts }

        // 6) 结尾基调
        val endingMood = deriveEndingMood(sim.endAttrs, events)

        return OfflineTimeline(events, endingMood)
    }

    // ── 事件条数 / 间隔（doc/03 §7.4）─────────────────────────

    private fun physioIntervalMinutes(traits: com.lollipop.tamagotchi.domain.model.Traits, start: Long, end: Long): Float {
        val elapsed = (end - start).toFloat()
        val nightMs = dayNightSegments(start, end, zoneId).sumOf { if (it.night) it.end - it.start else 0L }
        val nightFrac = if (elapsed > 0f) nightMs.toFloat() / elapsed else 0f
        val dayBase = 50f / (0.6f + 0.8f * traits.activity).coerceAtLeast(0.1f)
        return (dayBase * (1f - nightFrac) + dayBase * 2f * nightFrac).coerceAtLeast(1f)
    }

    /**
     * 返回 P+1 个边界（含 start、end），相邻间带抖动但严格递增。
     * 抖动限制在本片 ±half 内（half=0.5/P），避免大 P 时边界翻转导致 coerceIn(min>max)。
     */
    private fun computeSplitPoints(pCount: Int, start: Long, elapsedMs: Long, rng: java.util.Random): List<Long> {
        val pts = ArrayList<Long>(pCount + 1)
        pts += start
        val half = 0.5f / pCount
        for (i in 1 until pCount) {
            val base = i.toFloat() / pCount
            val jitter = (rng.nextFloat() - 0.5f) * half * 0.8f
            val f = (base + jitter).coerceIn(base - half + 1e-3f, base + half - 1e-3f)
            pts += start + (f * elapsedMs).toLong()
        }
        pts += start + elapsedMs
        return pts
    }

    // ── 事件文案 key（多语言/性格变体在 M7.S2 接 i18n）─────────

    private fun physioBubble(delta: AttributeDelta, night: Boolean): String {
        if (night) return "sleep_sound"
        val dom = AttributeRegistry.all
            .filter { delta[it.id] < -0.01f }
            .maxByOrNull { -delta[it.id] }?.id
        return when (dom) {
            AttributeId.SATIATION -> "wait_hungry"
            AttributeId.MOOD -> "dull_wander"
            AttributeId.HYGIENE -> "dust_dirty"
            AttributeId.HEALTH -> "ail_sick"
            else -> "idle_pass"
        }
    }

    // ── 突发扰动池（doc/03 §7.2）──────────────────────────────

    private data class Sudden(val id: String, val attr: AttributeId, val delta: Float, val positive: Boolean)

    private fun insertSudden(
        events: MutableList<OfflineEvent>,
        pCount: Int,
        traits: com.lollipop.tamagotchi.domain.model.Traits,
        rng: java.util.Random,
        budget: Float,
        cap: Int,
    ) {
        val pool = listOf(
            Sudden("found_leftover", AttributeId.SATIATION, +3f, true),
            Sudden("self_play", AttributeId.MOOD, +3f, true),
            Sudden("nice_dream", AttributeId.MOOD, +2f, true),
            Sudden("spooked_noise", AttributeId.MOOD, -3f, false),
            Sudden("knock_bowl", AttributeId.SATIATION, -2f, false),
        )
        val suddenRate = (0.18f + 0.25f * traits.curiosity).coerceIn(0f, 0.9f)
        var used = 0f
        var made = 0
        for (i in 0 until pCount) {
            if (events.size + made >= cap) break
            if (rng.nextFloat() >= suddenRate) continue
            val positive = rng.nextFloat() < 0.66f
            val cands = pool.filter { it.positive == positive }
            if (cands.isEmpty()) continue
            val s = cands[rng.nextInt(cands.size)]
            if (used + abs(s.delta) > budget + 1e-3f) continue
            used += abs(s.delta)
            val cur = events[i]
            val nxt = events.getOrNull(i + 1)
            val ts = if (nxt != null) (cur.ts + nxt.ts) / 2 else cur.ts - 1
            events += OfflineEvent(
                ts = ts,
                kind = OfflineKind.SUDDEN,
                eventId = s.id,
                bubbleId = s.id,
                delta = AttributeDelta.of(s.attr to s.delta),
                highlight = false,
            )
            made++
        }
    }

    // ── 结尾基调（doc/03 §7.6）────────────────────────────────

    private fun deriveEndingMood(endAttrs: AttributeMap, events: List<OfflineEvent>): EndingMood {
        val health = endAttrs[AttributeId.HEALTH]
        val mood = endAttrs[AttributeId.MOOD]
        val last = events.lastOrNull()
        val lastSudden = events.lastOrNull { it.kind == OfflineKind.SUDDEN }
        return when {
            health <= 0f -> EndingMood.SICKLY
            mood < DecayModel.MOOD_SAD_THRESHOLD -> EndingMood.NEEDY
            lastSudden != null &&
                (lastSudden.delta[AttributeId.MOOD] > 0f || lastSudden.delta[AttributeId.SATIATION] > 0f) -> EndingMood.JOYFUL
            last?.eventId in setOf("sleep_sound", "doze_tired") -> EndingMood.SLEEPY
            else -> EndingMood.GRUMBLING
        }
    }
}
