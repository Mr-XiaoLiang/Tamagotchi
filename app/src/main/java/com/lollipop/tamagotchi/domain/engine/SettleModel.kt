package com.lollipop.tamagotchi.domain.engine

import com.lollipop.tamagotchi.core.attribute.AttributeDelta
import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.AttributeMap
import com.lollipop.tamagotchi.core.attribute.AttributeRegistry
import com.lollipop.tamagotchi.domain.model.Traits
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 结算速率模型（doc/01 §6.1 + M5 §速率档，Task M5.S1 / M7.S1）。
 *
 * 抽出为 SettleEngine 与 OfflineTimelineBuilder 的**唯一速率事实源**，避免两套积分漂移：
 * 长离线事件化时间线的「骨架消耗」必须 ≈ 快速积分，否则 v2 数值平衡表失效（doc/03 §7.5）。
 * 两个消费者都调用 [simulateSegments] 得到逐段 delta 与终态。
 */
internal object DecayModel {

    const val HOUR_MS: Float = 3_600_000f

    // 作息窗（doc/02 §3）
    const val NIGHT_START_HOUR = 22
    const val NIGHT_END_HOUR = 7

    // 状态推进阈值（doc/01 §5）
    const val MOOD_SAD_THRESHOLD = 20f
    const val SAT_HUNGRY_THRESHOLD = 20f
    const val HYG_DIRTY_THRESHOLD = 30f

    /** mood<20 持续 ≥ 本时长 → SAD（doc/01 §5）。 */
    const val SAD_TRIGGER_HOURS: Float = 4f

    /** 清洁低的心情衰减加成（doc/01 §4.2：×1.3）。 */
    const val DIRTY_MOOD_MULTIPLIER = 1.3f

    /** 饥饿额外心情消耗（doc/01 §6.1 饥饿行：额外 -2.0/h）。 */
    const val HUNGER_MOOD_EXTRA_PER_H = -2.0f

    /** SAD 期间健康缓慢下滑初值（doc/01 §5 未给数值，S1 落地注记：可调）。 */
    const val SAD_HEALTH_DROP_PER_H = -1.0f

    enum class Regime { DAY, SLEEP, SICK }

    /** 作息：22:00~07:00（本地时区）为夜间（与 BehaviorFSM.isNight 同口径）。 */
    fun isNight(ms: Long, zoneId: ZoneId): Boolean {
        val hour = Instant.ofEpochMilli(ms).atZone(zoneId).hour
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
    }

    fun satDelta(regime: Regime, traits: Traits): Float = when (regime) {
        Regime.DAY -> -2.0f
        Regime.SLEEP -> -0.2f
        Regime.SICK -> -3.0f
    } * (0.5f + traits.appetite)

    fun moodDelta(regime: Regime, traits: Traits, hyg: Float, sat: Float): Float {
        if (regime == Regime.SLEEP) return 0f // 睡眠无情绪波动，也不受饥饿/脏影响
        var rate = when (regime) {
            Regime.DAY -> -1.0f
            Regime.SICK -> -2.0f
            Regime.SLEEP -> 0f
        } * (1.2f - traits.temper * 0.3f)
        if (hyg < HYG_DIRTY_THRESHOLD) rate *= DIRTY_MOOD_MULTIPLIER
        if (sat < SAT_HUNGRY_THRESHOLD) rate += HUNGER_MOOD_EXTRA_PER_H
        return rate
    }

    fun healthDelta(regime: Regime, sadStart: Boolean): Float = when (regime) {
        Regime.SLEEP -> +1.0f // 睡觉回血
        Regime.SICK -> 0f // SICK clamp 不随时间恶化
        Regime.DAY -> if (sadStart) SAD_HEALTH_DROP_PER_H else 0f
    }

    fun hygDelta(regime: Regime): Float = when (regime) {
        Regime.DAY -> -0.6f
        Regime.SLEEP -> -0.1f
        Regime.SICK -> -0.3f
    }
}

/** 单个昼夜切片（≤60min，边界对齐整点）。 */
internal data class Segment(val start: Long, val end: Long, val night: Boolean)

/** 单个切片的模拟结果。 */
internal data class SegmentSim(
    val segment: Segment,
    val regime: DecayModel.Regime,
    val delta: AttributeDelta,
    val attrsAfter: AttributeMap,
)

/** 整窗模拟输出（SettleEngine 与 时间线生成器共用）。 */
internal data class SimulationResult(
    val segments: List<SegmentSim>,
    val endAttrs: AttributeMap,
    val sadHours: Float,
    val sick: Boolean,
)

/** 昼夜分片（对齐整点，段长 ≤ 60min）。 */
internal fun dayNightSegments(from: Long, until: Long, zoneId: ZoneId): List<Segment> {
    val segs = ArrayList<Segment>()
    var t = from
    while (t < until) {
        val boundary = Instant.ofEpochMilli(t).atZone(zoneId)
            .truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
        val end = minOf(until, boundary)
        segs += Segment(start = t, end = end, night = DecayModel.isNight(t, zoneId))
        t = end
    }
    return segs
}

/**
 * 纯函数：把 [from, until] 按昼夜分段积分，返回逐段 delta 与终态。
 *
 * - [sickStart]：起始是否已在 SICK（health≤0），决定后续切片是否走 SICK 档；
 * - [sadStart]：起始是否处于 SAD 态（决定 DAY 段健康是否下滑，doc/01 §5）；
 * - [sadStartHours]：起始已累计的 mood<20 连续时长（SettleEngine 传快照值；生成器传 0，
 *   仅影响 SAD 触发判定，与属性 delta 无关）。
 *
 * 与 doc/01 §6.1 速率档、M5 落地注记完全一致；SettleEngine 与 OfflineTimelineBuilder 共用。
 */
internal fun simulateSegments(
    from: Long,
    until: Long,
    zoneId: ZoneId,
    startAttrs: AttributeMap,
    traits: Traits,
    sickStart: Boolean,
    sadStart: Boolean,
    sadStartHours: Float = 0f,
): SimulationResult {
    val SAT = AttributeId.SATIATION
    val MOOD = AttributeId.MOOD
    val HEALTH = AttributeId.HEALTH
    val HYG = AttributeId.HYGIENE

    var attrs = startAttrs
    var sick = sickStart
    var sadHours = sadStartHours
    val segs = ArrayList<SegmentSim>()

    for (seg in dayNightSegments(from, until, zoneId)) {
        val hours = (seg.end - seg.start).toFloat() / DecayModel.HOUR_MS
        val regime = when {
            sick -> DecayModel.Regime.SICK
            seg.night -> DecayModel.Regime.SLEEP
            else -> DecayModel.Regime.DAY
        }
        // moodDelta 用「段起始」的 hyg/sat（与 M5 integrate 行为一致）
        val dSat = DecayModel.satDelta(regime, traits) * hours
        val dMood = DecayModel.moodDelta(regime, traits, attrs[HYG], attrs[SAT]) * hours
        val dHealth = DecayModel.healthDelta(regime, sadStart) * hours
        val dHyg = DecayModel.hygDelta(regime) * hours
        val delta = AttributeDelta.of(SAT to dSat, MOOD to dMood, HEALTH to dHealth, HYG to dHyg)
        val after = attrs
            .set(SAT, attrs[SAT] + dSat)
            .set(MOOD, attrs[MOOD] + dMood)
            .set(HEALTH, attrs[HEALTH] + dHealth)
            .set(HYG, attrs[HYG] + dHyg)
        if (after[HEALTH] <= 0f) sick = true
        sadHours = if (after[MOOD] < DecayModel.MOOD_SAD_THRESHOLD) sadHours + hours else 0f
        segs += SegmentSim(seg, regime, delta, after)
        attrs = after
    }
    return SimulationResult(segs, attrs, sadHours, sick)
}

/** 数值守恒辅助：把某组事件按属性聚合为 delta map。 */
internal fun sumPhysioDelta(events: List<OfflineEvent>): Map<AttributeId, Float> {
    val acc = AttributeRegistry.all.associate { it.id to 0f }.toMutableMap()
    for (e in events) {
        for (meta in AttributeRegistry.all) acc[meta.id] = acc[meta.id]!! + e.delta[meta.id]
    }
    return acc
}
