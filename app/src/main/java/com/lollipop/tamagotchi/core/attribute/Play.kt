package com.lollipop.tamagotchi.core.attribute

/**
 * 玩耍方式（doc/01 §6.2 / 用户平衡需求：不同的玩耍方式反馈不同）。
 *
 * 仅承载数值事实：每种玩法给出一组基线增量（饱腹/心情/健康/清洁/知识），
 * 实际生效还会叠加：Trait 线性缩放（急躁放大心情、好动放大健康损耗）、
 * 当前值边际递减（心情增益）、以及每次随机浮动（±JITTER）。
 *
 * 知识代价固定为 [KNOWLEDGE_COST]（不变笨，仅被玩消耗，不被动衰减）。
 */
enum class PlayType(
    val label: String,
    val moodDelta: Float,
    val healthCost: Float,
    val hygieneCost: Float,
    val satCost: Float,
) {
    /** 翻滚：中等强度，默认玩法。 */
    TUMBLE("翻滚", 15f, 2f, 3f, 4f),

    /** 追尾巴：高强度，心情涨最多但更费健康/清洁/饱食。 */
    CHASE("追尾巴", 18f, 3f, 4f, 5f),

    /** 逗弄：轻互动，消耗低、心情涨少。 */
    NUDGE("逗弄", 10f, 1f, 2f, 2f),

    /** 知识固定代价（所有玩法一致，doc/01 §4.1：不变笨）。 */
    ;

    companion object {
        /** 知识代价：玩耍固定扣减，与玩法无关。 */
        const val KNOWLEDGE_COST: Float = 2f

        /** UI 未指定时的默认玩法。 */
        val DEFAULT: PlayType = TUMBLE
    }
}
