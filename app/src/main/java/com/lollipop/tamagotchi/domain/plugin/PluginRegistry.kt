package com.lollipop.tamagotchi.domain.plugin

/**
 * 内置非 App 功能注册表（预留接口，doc/05 §3）——当前为空。
 *
 * M8 重构后，App 类入口走**动态发现**（PackageManager，见 [com.lollipop.tamagotchi.data.plugin.AppLister]），
 * 不再在此预设。非 App 功能（系统设置 / 本地动作：Wi-Fi、蓝牙、电量、手电筒、勿扰、计时器等）
 * 的接入接口已就绪（[PluginSpec] / [PluginExecutor] / [PluginResolver] / [LocalActionRunner] / [PluginNotifier]），
 * 后续在此登记并接入，结构上无需改动面板。
 */
object PluginRegistry {
    /** 当前无内置非 App 功能；返回空表，面板仅展示用户选择的 App。 */
    fun defaults(): List<PluginSpec> = emptyList()
}
