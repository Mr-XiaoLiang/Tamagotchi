package com.lollipop.tamagotchi.core.motion

/**
 * 归一化坐标（doc/02 §2.1）：(x,y) ∈ [-1,1]²，原点屏心，活动边界为圆。
 */
data class NormalizedPos(
    val x: Float,
    val y: Float,
) {
    /** 距屏心距离平方（用于圆边界判断 x²+y²<=R²）。 */
    fun radiusSquared(): Float = x * x + y * y

    /** 与目标合成（用于步进：newPos = pos + dir×v，doc/02 §2.2）。 */
    fun step(deltaX: Float, deltaY: Float): NormalizedPos =
        NormalizedPos(x + deltaX, y + deltaY)
}

/**
 * 朝向（doc/02 §2.3 行号映射与 M2.S1 实测钉死一致）：
 * 行=朝向 0 下 / 1 左 / 2 右 / 3 上；此 ordinal 即切表 row 序号。
 */
enum class Direction(val dx: Float, val dy: Float) {
    DOWN(0f, 1f),
    LEFT(-1f, 0f),
    RIGHT(1f, 0f),
    UP(0f, -1f),
    ;

    companion object {
        /** 活动边界半径 R≈0.82（留环形进度区；doc/02 §2.1）。 */
        const val ACTIVITY_RADIUS = 0.82f

        /** 0..3 → 朝向（越界取模回绕，M4 FSM 随机换向用）。 */
        fun of(index: Int): Direction = entries[(index and 3)]
    }
}
