package com.lollipop.tamagotchi.domain.plugin

/**
 * 用户插件偏好（doc/05 §2 / 01 §9）：自定义排序序列 + 隐藏集合。纯数据，持久化在 data 层。
 *
 * @param order  自定义排序的 id 序列；空 = 用 [PluginSpec.sort] 默认序
 * @param hidden 被用户隐藏（长按移除）的插件 id 集合
 */
data class PluginPrefs(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
)

/**
 * 应用用户偏好：过滤隐藏项，再按序排列（[PluginPrefs.order] 命中者优先，未命中者回落默认 [PluginSpec.sort]）。
 * 纯函数、可单测，不触碰 Android。
 */
fun List<PluginSpec>.applyPrefs(prefs: PluginPrefs): List<PluginSpec> {
    val visible = filter { it.id !in prefs.hidden }
    if (prefs.order.isEmpty()) return visible.sortedBy { it.sort }
    val rank = prefs.order.withIndex().associate { (i, id) -> id to i }
    return visible.sortedBy { rank[it.id] ?: (Int.MAX_VALUE - it.sort) }
}
