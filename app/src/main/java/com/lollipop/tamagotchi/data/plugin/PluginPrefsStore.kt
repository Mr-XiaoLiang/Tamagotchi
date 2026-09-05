package com.lollipop.tamagotchi.data.plugin

import android.content.Context
import android.content.SharedPreferences
import com.lollipop.tamagotchi.domain.plugin.PluginPrefs

/**
 * 面板选择持久化（doc/05 §2 / 01 §9）：plugins.selected（逗号分隔包名序列）。
 * 仅承载 SP 读写；过滤/排序纯逻辑在 [PluginPrefs.applySelection]（domain）。
 */
class PluginPrefsStore(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("plugin_prefs", Context.MODE_PRIVATE)

    fun load(): PluginPrefs {
        val selected = sp.getString("selected", null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        return PluginPrefs(selected = selected)
    }

    fun setSelected(list: List<String>) {
        sp.edit().putString("selected", list.joinToString(",")).apply()
    }

    fun add(pkg: String) {
        setSelected((load().selected + pkg).distinct())
    }

    fun remove(pkg: String) {
        setSelected(load().selected - pkg)
    }

    fun move(pkg: String, dir: Int) {
        val list = load().selected.toMutableList()
        val i = list.indexOf(pkg)
        if (i < 0) return
        val j = i + dir
        if (j < 0 || j >= list.size) return
        list[i] = list[j].also { list[j] = list[i] }
        setSelected(list)
    }

    fun reset() {
        sp.edit().remove("selected").apply()
    }
}
