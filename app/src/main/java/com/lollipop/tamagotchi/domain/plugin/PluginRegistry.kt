package com.lollipop.tamagotchi.domain.plugin

/**
 * 插件默认注册表（doc/05 §3）：纯数据，无 Android 依赖。
 * 新增插件 = 在此加一行（或未来改 assets 声明式注册，doc/05 §6）。
 */
object PluginRegistry {
    fun defaults(): List<PluginSpec> = listOf(
        PluginSpec(
            id = "launch.wechat", nameKey = "plugin_wechat", iconKey = "ic_plugin_wechat",
            type = PluginTriggerType.LAUNCH_APP, expectedPackages = listOf("com.tencent.mm"), sort = 0,
        ),
        PluginSpec(
            id = "launch.alipay", nameKey = "plugin_alipay", iconKey = "ic_plugin_alipay",
            type = PluginTriggerType.LAUNCH_APP,
            expectedPackages = listOf("com.eg.android.AlipayGphone"), sort = 1,
        ),
        PluginSpec(
            id = "launch.health", nameKey = "plugin_health", iconKey = "ic_plugin_health",
            type = PluginTriggerType.LAUNCH_APP,
            expectedPackages = listOf("com.sec.android.app.shealth", "com.google.android.apps.fitness"),
            sort = 2,
        ),
        PluginSpec(
            id = "launch.phone", nameKey = "plugin_phone", iconKey = "ic_plugin_phone",
            type = PluginTriggerType.LAUNCH_APP,
            expectedPackages = listOf("com.google.android.dialer", "com.android.contacts"), sort = 3,
        ),
        PluginSpec(
            id = "settings.wifi", nameKey = "plugin_wifi", iconKey = "ic_plugin_wifi",
            type = PluginTriggerType.OPEN_SETTINGS, settingsTarget = SettingsTarget.WIFI, sort = 4,
        ),
        PluginSpec(
            id = "settings.bluetooth", nameKey = "plugin_bluetooth", iconKey = "ic_plugin_bluetooth",
            type = PluginTriggerType.OPEN_SETTINGS, settingsTarget = SettingsTarget.BLUETOOTH, sort = 5,
        ),
        PluginSpec(
            id = "settings.battery", nameKey = "plugin_battery", iconKey = "ic_plugin_battery",
            type = PluginTriggerType.OPEN_SETTINGS, settingsTarget = SettingsTarget.BATTERY, sort = 6,
        ),
        PluginSpec(
            id = "action.torch", nameKey = "plugin_torch", iconKey = "ic_plugin_torch",
            type = PluginTriggerType.LOCAL_ACTION, localAction = LocalActionKind.TORCH, sort = 7,
        ),
        PluginSpec(
            id = "action.dnd", nameKey = "plugin_dnd", iconKey = "ic_plugin_dnd",
            type = PluginTriggerType.LOCAL_ACTION, localAction = LocalActionKind.DND, sort = 8,
        ),
        PluginSpec(
            id = "action.timer", nameKey = "plugin_timer", iconKey = "ic_plugin_timer",
            type = PluginTriggerType.LOCAL_ACTION, localAction = LocalActionKind.TIMER, sort = 9,
        ),
    )
}
