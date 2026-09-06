package com.lollipop.tamagotchi.domain.engine

import com.lollipop.tamagotchi.core.attribute.AttributeDelta

/**
 * 离线事件化时间线的输出端类型（doc/03 §7.6）。
 *
 * M5.S1 仅为 [SettlementSummary] 的契约占位：快速积分不铺时间线，字段恒 null；
 * 长离线铺排（生成器 + 事件库 + 结尾基调推导）在 M7 由 OfflineTimelineBuilder 落地。
 */
enum class OfflineKind { PHYSIO, SUDDEN, OUTING }

/** 会话结尾基调（doc/03 §7.6）：M7 由时间线末尾事件推导，交给迎接语气 / 会话回顾。 */
enum class EndingMood { JOYFUL, GRUMBLING, NEEDY, SLEEPY, SICKLY }

/** 一条离线事件（doc/03 §7.6）：回放时间轴条目，瞬态、仅内存（doc/04 §3.1）。 */
data class OfflineEvent(
    val ts: Long,
    val kind: OfflineKind,
    val eventId: String,
    val bubbleId: String,
    val delta: AttributeDelta,
    val highlight: Boolean,
    /** 户外出行事件携带的目的地（[com.lollipop.tamagotchi.domain.engine.PokemonPlaces]）；非出行事件为 null。 */
    val place: Place? = null,
)

/**
 * 结算输出（doc/01 §8.3）：
 * - [elapsedMs]：本窗离线时长（0 = noOp）；
 * - [totalDelta]：各属性聚合变化（SettleEntry 只记这条，doc/04 §7）；
 * - [offlineTimeline]/[endingMood]：长离线事件时间线与结尾基调，快速积分（M5）恒 null，
 *   M7 事件化分支填充。
 */
data class SettlementSummary(
    val elapsedMs: Long,
    val totalDelta: AttributeDelta,
    val offlineTimeline: List<OfflineEvent>? = null,
    val endingMood: EndingMood? = null,
)
