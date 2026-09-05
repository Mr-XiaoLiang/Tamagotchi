package com.lollipop.tamagotchi.domain.plugin

/**
 * 插件触发类型（doc/05 §2）。
 * - LAUNCH_APP：拉起第三方 App（白名单依序探测包名）
 * - OPEN_SETTINGS：打开系统设置页
 * - LOCAL_ACTION：本地执行动作（手电筒/勿扰/计时）
 */
enum class PluginTriggerType {
    LAUNCH_APP,
    OPEN_SETTINGS,
    LOCAL_ACTION,
}

/** 系统设置页目标（OPEN_SETTINGS 用，doc/05 §3）。 */
enum class SettingsTarget {
    WIFI,
    BLUETOOTH,
    BATTERY,
}

/** 本地动作类型（LOCAL_ACTION 用，doc/05 §3）。 */
enum class LocalActionKind {
    TORCH,
    DND,
    TIMER,
}

/**
 * 插件声明（doc/05 §2）：domain 纯数据，**不持有 Android Intent / lambda / R 引用**——
 * Intent 构造与本地动作实现放 data 层，由 [PluginExecutor] 通过注入端口调用，保证本类可单测。
 *
 * @param id           稳定 id（"launch.wechat" / "action.torch"）
 * @param nameKey      显示名多语言 key（presentation 层映射 R.string）
 * @param iconKey      图标 key（presentation 层映射 drawable）
 * @param type         触发类型
 * @param expectedPackages LAUNCH_APP 依序探测的包名白名单
 * @param settingsTarget   OPEN_SETTINGS 目标
 * @param localAction      LOCAL_ACTION 类型
 * @param sort         默认排序权重
 * @param enabledByDefault 默认是否可用（false 可预留禁用位）
 */
data class PluginSpec(
    val id: String,
    val nameKey: String,
    val iconKey: String,
    val type: PluginTriggerType,
    val expectedPackages: List<String> = emptyList(),
    val settingsTarget: SettingsTarget? = null,
    val localAction: LocalActionKind? = null,
    val sort: Int = 0,
    val enabledByDefault: Boolean = true,
)
