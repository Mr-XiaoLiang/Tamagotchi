package com.lollipop.tamagotchi.domain.util

/**
 * 单一、单调递增的序号源。
 *
 * 用途（M6.S2 / M9.S2）：动作执行事件（[com.lollipop.tamagotchi.presentation.screen.ActionEvent]）
 * 与在线随机事件（[com.lollipop.tamagotchi.presentation.screen.OnlineEvent]）必须共用**同一个**实例，
 * 以保证两类事件在进程内拥有全局唯一、互不碰撞的 nonce。
 *
 * 这正是修复「首次投喂动画不播放」回归点的核心：曾经动作与在线事件各自维护一套从 0 开始的计数器，
 * 偶发同号后被 [com.lollipop.tamagotchi.presentation.screen.PetScreen] 的「按号去重」逻辑误判为重复，
 * 从而吞掉某次短演出。两类事件必须取自同一个 [FxSeq] 实例——切勿为它们各自 new 一个。
 */
class FxSeq(private var value: Long = 0L) {
    fun next(): Long = ++value
    val current: Long get() = value
}
