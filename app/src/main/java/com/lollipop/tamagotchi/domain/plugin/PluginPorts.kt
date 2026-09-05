package com.lollipop.tamagotchi.domain.plugin

/**
 * 触发解析端口（doc/05 §5）：真实实现在 data 层（PackageManager / startActivity），
 * 此处仅纯接口，便于 [PluginExecutor] 单测时注入假实现。
 */
interface PluginResolver {
    /** 返回第一个已安装可拉起的包名；全部未安装返回 null。 */
    fun resolveLaunch(packages: List<String>): String?

    /** 打开系统设置页，返回是否成功拉起。 */
    fun openSettings(target: SettingsTarget): Boolean
}

/** 本地动作执行端口（TORCH/DND/TIMER 真实实现在 data 层）。 */
interface LocalActionRunner {
    fun run(kind: LocalActionKind): Boolean
}

/** 触发失败（未找到/拉起失败）通知端口：presentation 层接 Bubble/Toast。 */
interface PluginNotifier {
    fun onNotFound(spec: PluginSpec)
}
