package com.lollipop.tamagotchi.domain.plugin

/**
 * 面板选择偏好（doc/05 §2 / 01 §9）：用户勾选展示的 App 包名**有序**序列，持久化在 data 层。
 *
 * - [selected] 为空 = 首次运行 /「显示全部」：展示系统可读到的全部可启动 App（动态发现，非预设）。
 * - [selected] 非空 = 仅展示这些包，并按其顺序。
 *
 * 非 App 功能（Wi-Fi / 手电筒 / 勿扰 …）的接入接口保留在 [PluginSpec] / [PluginExecutor] /
 * [PluginRegistry]（结构已就绪，doc/05 §3），此处不放入选单，留待后续内置或接入系统功能。
 */
data class PluginPrefs(
    val selected: List<String> = emptyList(),
)

/**
 * 按用户选择过滤 + 排序可启动 App 列表（纯函数、可单测，不触碰 Android）。
 * [selected] 为空时原样返回（调用方应传入全量发现列表，即「显示全部」语义）。
 */
fun List<AppEntry>.applySelection(prefs: PluginPrefs): List<AppEntry> {
    if (prefs.selected.isEmpty()) return this
    val rank = prefs.selected.withIndex().associate { (i, pkg) -> pkg to i }
    return filter { it.packageName in rank }.sortedBy { rank[it.packageName] }
}
