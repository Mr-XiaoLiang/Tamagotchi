package com.lollipop.tamagotchi.data.plugin

import android.content.Context
import android.content.SharedPreferences
import com.lollipop.tamagotchi.domain.plugin.PluginPrefs

/**
 * 插件偏好持久化（doc/05 §2 / 01 §9）：plugins.order（逗号分隔 id 序列）/ plugins.hidden（id 集合）。
 * 仅承载 SP 读写；排序/过滤的纯逻辑在 [PluginPrefs.applyPrefs]（domain）。
 */
class PluginPrefsStore(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("plugin_prefs", Context.MODE_PRIVATE)

    fun load(): PluginPrefs {
        val order = sp.getString("order", null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val hidden = sp.getStringSet("hidden", emptySet()) ?: emptySet()
        return PluginPrefs(order = order, hidden = hidden)
    }

    fun setHidden(id: String, hidden: Boolean) {
        val set = load().hidden.toMutableSet()
        if (hidden) set.add(id) else set.remove(id)
        sp.edit().putStringSet("hidden", set).apply()
    }

    fun setOrder(order: List<String>) {
        sp.edit().putString("order", order.joinToString(",")).apply()
    }

    fun reset() {
        sp.edit().remove("order").remove("hidden").apply()
    }
}
