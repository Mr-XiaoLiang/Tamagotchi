package com.lollipop.tamagotchi.core.attribute

/**
 * 可变属性快照（doc/01 §1.1）：0~100 的只读 map，set 产生新快照。
 * 由 [AttributeRegistry] 驱动语义；刻意不可变，便于 SP 快照与 FSM 纯函数。
 */
class AttributeMap private constructor(
    private val values: Map<AttributeId, Float>,
) {
    operator fun get(id: AttributeId): Float = values.getValue(id)

    /** 全部已注册可变属性（注册表驱动遍历）。 */
    val entries: Map<AttributeId, Float> get() = values

    /** 深度拷贝 set（夹取到注册表 [floor, ceiling]）；快照语义返回新对象。 */
    fun set(id: AttributeId, raw: Float): AttributeMap {
        val v = AttributeRegistry.meta(id).clamp(raw)
        return AttributeMap(values + (id to v))
    }

    /** 批量应用后返回新快照。 */
    fun apply(updates: Map<AttributeId, Float>): AttributeMap =
        updates.entries.fold(this) { acc, (id, v) -> acc.set(id, v) }

    override fun equals(other: Any?): Boolean =
        other is AttributeMap && other.values == values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "AttributeMap(values=$values)"

    companion object {
        /** 从注册表建档初值构建（AttributeRegistry.initialSnapshot）。 */
        internal fun of(raw: Map<AttributeId, Float>): AttributeMap {
            // 只允许注册过的可变属性；注册表驱动，防魔法键。
            val cleaned = raw.filterKeys { it in AttributeRegistry.all.map { m -> m.id } }
            return AttributeMap(cleaned)
        }

        /** 全 0 起点（测试/极端档用）。 */
        fun zero(): AttributeMap =
            of(AttributeRegistry.all.associate { it.id to 0f })
    }
}
