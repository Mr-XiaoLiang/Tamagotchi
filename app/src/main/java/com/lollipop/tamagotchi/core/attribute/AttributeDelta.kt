package com.lollipop.tamagotchi.core.attribute

/**
 * 属性增量（doc/01 §8.3 / doc/03 §2.2 的 AttributeDelta 落地）：
 * 一次结算 / 动作 / 事件的「变化量」聚合，正=增、负=减。
 * 纯值对象：只读 map + 便捷取值，不承载任何规则。
 */
data class AttributeDelta(
    val perAttribute: Map<AttributeId, Float>,
) {
    /** 该属性变化量；未涉及 = 0。 */
    operator fun get(id: AttributeId): Float = perAttribute[id] ?: 0f

    /** 是否完全没有变化（noOp 结算用）。 */
    val isEmpty: Boolean get() = perAttribute.isEmpty()

    companion object {
        val EMPTY: AttributeDelta = AttributeDelta(emptyMap())

        /** 从成对参数构建（测试/事件便捷）。 */
        fun of(vararg entries: Pair<AttributeId, Float>): AttributeDelta =
            AttributeDelta(entries.toMap())
    }
}
