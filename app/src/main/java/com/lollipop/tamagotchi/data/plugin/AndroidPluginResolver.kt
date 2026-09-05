package com.lollipop.tamagotchi.data.plugin

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.lollipop.tamagotchi.domain.plugin.PluginResolver
import com.lollipop.tamagotchi.domain.plugin.SettingsTarget

/**
 * 真实插件解析（doc/05 §5）：用 PackageManager 探测可拉起包名、用 startActivity 打开设置页。
 * 失败一律返回 false，由 [com.lollipop.tamagotchi.domain.plugin.PluginExecutor] 走 notifier 提示，不抛异常。
 */
class AndroidPluginResolver(private val context: Context) : PluginResolver {
    override fun resolveLaunch(packages: List<String>): String? {
        val pm = context.packageManager
        for (pkg in packages) {
            pm.getLaunchIntentForPackage(pkg)?.let { return pkg }
        }
        return null
    }

    override fun openSettings(target: SettingsTarget): Boolean {
        val intent = when (target) {
            SettingsTarget.WIFI -> Intent(Settings.Panel.ACTION_WIFI)
            SettingsTarget.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            SettingsTarget.BATTERY -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Throwable) {
            false
        }
    }
}
