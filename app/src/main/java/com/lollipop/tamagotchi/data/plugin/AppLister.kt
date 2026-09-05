package com.lollipop.tamagotchi.data.plugin

import android.content.Context
import android.content.Intent
import com.lollipop.tamagotchi.domain.plugin.AppEntry

/**
 * 系统 App 发现（doc/05 §4 / M8 重构）：用 PackageManager 读取「系统里能读到的所有可启动 App」。
 * 面板不预设任何快捷方式，全部动态发现；图标亦在此加载（[loadIcon]）。
 */
class AppLister(private val context: Context) {

    /** 全部可启动 App（去重 + 按名称排序）。 */
    fun launchableApps(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .mapNotNull { pkg ->
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    AppEntry(pkg, pm.getApplicationLabel(info).toString())
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    /** 加载 App 图标（Drawable）；不可读时返回 null。 */
    fun loadIcon(packageName: String): android.graphics.drawable.Drawable? =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    /**
     * 由单个包名构造 [AppEntry]（按需取展示名，不走全量枚举 IPC）。
     * 功能清单只读本地保存的包名序列，再按需取图标/名称，避免 [launchableApps] 的重枚举。
     * 读取不到（已卸载等）返回 null。
     */
    fun entryOf(packageName: String): AppEntry? =
        runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            AppEntry(packageName, context.packageManager.getApplicationLabel(info).toString())
        }.getOrNull()
}
