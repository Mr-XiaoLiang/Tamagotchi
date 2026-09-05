package com.lollipop.tamagotchi.core.motion

/**
 * 归一化坐标（doc/02 §2.1）：(x,y) ∈ [-1,1]²，原点屏心，活动边界为圆
 * （R=[Direction.WALK_RADIUS]，宠物中心最大可达圆；模型本体留白由渲染映射扣除）。
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
        /**
         * 宠物中心可达的最大半径（归一化，屏半径=1；doc/02 §2.1）。
         * M4.S2 布局调整：活动范围 = 全屏（宠物渲染在最底层，环/把手 overlay 允许重叠），
         * 故不再留环形进度区；「不出屏」由渲染映射扣除模型半径与呼吸余量保证
         * （px 偏移 = pos × (屏半径 − 模型半径 − 呼吸余量)）。
         */
        const val WALK_RADIUS = 1f

        /** 0..3 → 朝向（越界取模回绕，M4 FSM 随机换向用）。 */
        fun of(index: Int): Direction = entries[(index and 3)]
    }
}
