package com.lollipop.tamagotchi.domain.model

import com.lollipop.tamagotchi.core.attribute.FoodFlavor

/**
 * 不可变性格特质 6 维（doc/02 §4.2）：每维 0~1，开档骰定一生不变。
 * 维度含义（traits → 决策倾向）：
 * - activity   好动 ↔ 慵懒（行走速度 / WALKING:IDLE 时长比）
 * - affinity   粘人 ↔ 独立（迎接反馈 / SAD 易触发）
 * - appetite   贪吃 ↔ 挑食（饱腹衰减 / 乞食频率 / 口味权重）
 * - curiosity  好奇 ↔ 淡定（随机事件池 / 探索频率）
 * - temper     温和 ↔ 急躁（心情波动幅值 / 情绪表达强度）
 * - learner    聪慧 ↔ 憨直（学习增速系数：0~1 → 单次学习增益倍率 0.5×~1.5×；亦影响互动解锁次数）
 */
data class Traits(
    val activity: Float,
    val affinity: Float,
    val appetite: Float,
    val curiosity: Float,
    val temper: Float,
    /** 聪慧/智力：固定属性，学习增速的系数（0~1 → 增益倍率 0.5×~1.5×），不随游戏进程变化、永不衰减。 */
    val learner: Float,
) {
    init {
        require(
            activity in 0f..1f && affinity in 0f..1f && appetite in 0f..1f &&
                curiosity in 0f..1f && temper in 0f..1f && learner in 0f..1f,
        ) { "traits 必须在 [0,1]：$this" }
    }

    /** 按 6 维固定顺序取列表（SP JSON / 建档微条遍历用）。 */
    val orderedValues: List<Float>
        get() = listOf(activity, affinity, appetite, curiosity, temper, learner)

    companion object {
        val NAMES: List<String> =
            listOf("好动", "粘人", "贪吃", "好奇", "温和", "聪慧")
    }
}

/** 性格描述词（主 / 副；仅文案，非逻辑——doc/02 §4.3）。 */
data class TraitDescriptor(
    val primary: String,
    val secondary: String,
) {
    /** 组合展示用文案，如「调皮粘人的小家伙」。 */
    val phrase: String get() = "${primary}${secondary}的小家伙"
}

/**
 * 完整开档性格（doc/02 §4.2 / doc/01 §9 personality 字段）：
 * seed 可复现 → traits + flavor 全部确定性派生。
 */
data class Personality(
    val seed: Long,
    val traits: Traits,
    val flavor: FoodFlavor,
)
