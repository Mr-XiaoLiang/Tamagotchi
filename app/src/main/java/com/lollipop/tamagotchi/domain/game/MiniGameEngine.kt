package com.lollipop.tamagotchi.domain.game

import kotlin.random.Random

/** 一只泡泡：归一化坐标(0..1) + 剩余存活毫秒。id 用于点击命中与去重。 */
data class Bubble(
    val id: Int,
    val x: Float,
    val y: Float,
    val remainingMs: Long,
)

/** 一局小游戏的纯状态（无 Android 依赖，可单测）。 */
data class MiniGameState(
    val timeLeftMs: Long,
    val score: Int,
    val over: Boolean,
)

/**
 * 休闲小游戏（M16，doc/09 §5.6）纯逻辑：计时 / 计分 / 泡泡生成。
 * 全部为纯函数，UI 负责把归一化坐标映射到圆屏安全区、按 ttl 驱动存活与点击命中。
 * 游戏结果仅会话级，不写入 stats / 不动宠物状态（与陪伴解耦）。
 */
object MiniGameEngine {

    const val DEFAULT_TOTAL_MS = 30_000L
    const val SPAWN_INTERVAL_MS = 850L
    const val BUBBLE_TTL_MS = 1_200L
    const val MAX_BUBBLES = 6

    fun newGame(totalMs: Long = DEFAULT_TOTAL_MS): MiniGameState =
        MiniGameState(timeLeftMs = totalMs, score = 0, over = false)

    /** 推进时间；到 0 即结束且不再负向。已结束则幂等返回。 */
    fun tick(state: MiniGameState, dtMs: Long): MiniGameState {
        if (state.over) return state
        val left = (state.timeLeftMs - dtMs).coerceAtLeast(0L)
        return state.copy(timeLeftMs = left, over = left <= 0L)
    }

    /** 命中得分；仅在进行中有效（结束后不再计分）。 */
    fun pop(state: MiniGameState): MiniGameState {
        if (state.over) return state
        return state.copy(score = state.score + 1)
    }

    /** 生成一只归一化坐标(0..1)的泡泡；rng 驱动可确定性单测。 */
    fun nextBubble(rng: Random, id: Int, ttlMs: Long = BUBBLE_TTL_MS): Bubble =
        Bubble(
            id = id,
            x = rng.nextFloat().coerceIn(0f, 1f),
            y = rng.nextFloat().coerceIn(0f, 1f),
            remainingMs = ttlMs,
        )
}
