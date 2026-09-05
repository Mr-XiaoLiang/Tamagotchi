package com.lollipop.tamagotchi.domain.plugin

/**
 * 插件触发执行（doc/05 §5）：纯函数，不依赖 Android。
 * 真正构造 Intent / 执行本地动作 / 弹提示由注入端口承担（data 层实现），
 * 故本类与 [PluginResolver] 等端口均可脱离安卓被单测。
 *
 * @return true = 已触发；false = 未找到/失败（已通过 [PluginNotifier] 提示）。
 */
object PluginExecutor {
    fun execute(
        spec: PluginSpec,
        resolver: PluginResolver,
        localActions: LocalActionRunner,
        notifier: PluginNotifier,
    ): Boolean = when (spec.type) {
        PluginTriggerType.LAUNCH_APP -> {
            val pkg = resolver.resolveLaunch(spec.expectedPackages)
            if (pkg != null) true else { notifier.onNotFound(spec); false }
        }
        PluginTriggerType.OPEN_SETTINGS -> {
            val target = spec.settingsTarget
            if (target != null && resolver.openSettings(target)) true
            else { notifier.onNotFound(spec); false }
        }
        PluginTriggerType.LOCAL_ACTION -> {
            val action = spec.localAction
            if (action != null && localActions.run(action)) true
            else { notifier.onNotFound(spec); false }
        }
    }
}
