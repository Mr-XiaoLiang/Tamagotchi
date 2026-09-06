package com.lollipop.tamagotchi.data.store

import android.content.Context

/**
 * 偏好设置纯逻辑（M17，doc/09 §5.7）：与宠物快照 [PetProfile] 解耦的独立偏好键集合，底层 [KVStore]。
 * 不依赖 Android，可 JVM 单测（见 [SettingsStoreTest]）；与 [PetStore] 各持独立 SP，互不污染。
 */
class SettingsPrefs(private val kv: KVStore) {

    /** 换宠时是否把旧宠归档为「过往」（默认开；关 = 覆盖写重开，与 M3 debug 重开档口径一致）。 */
    fun isArchiveEnabled(): Boolean = kv.get(KEY_ARCHIVE_ENABLED)?.let { it == "true" } ?: true
    fun setArchiveEnabled(value: Boolean) { kv.put(KEY_ARCHIVE_ENABLED, value.toString()) }

    /** 设置页是否显示「过往」入口（默认开）。 */
    fun isTombEntryVisible(): Boolean = kv.get(KEY_TOMB_VISIBLE)?.let { it == "true" } ?: true
    fun setTombEntryVisible(value: Boolean) { kv.put(KEY_TOMB_VISIBLE, value.toString()) }

    /** 墓碑「切回」是否需二次确认（默认开）。 */
    fun isSwitchBackConfirm(): Boolean = kv.get(KEY_SWITCH_CONFIRM)?.let { it == "true" } ?: true
    fun setSwitchBackConfirm(value: Boolean) { kv.put(KEY_SWITCH_CONFIRM, value.toString()) }

    companion object {
        const val KEY_ARCHIVE_ENABLED = "archive_enabled"
        const val KEY_TOMB_VISIBLE = "tomb_entry_visible"
        const val KEY_SWITCH_CONFIRM = "switch_back_confirm"
    }
}

/**
 * 偏好设置持久化（Android 侧）：独立 SP 文件 `settings_store`，与 [PetStore] 的 `pet_store` 解耦。
 * 仅承载游戏内偏好开关，不存任何宠物快照（T-09 唯一持久化点仍成立——只是新增一份偏好 SP）。
 */
class SettingsStore(context: Context) : KVStore {

    private val sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    private val prefs = SettingsPrefs(this)

    override fun get(key: String): String? = sp.getString(key, null)
    override fun put(key: String, value: String): Boolean =
        sp.edit().putString(key, value).commit()
    override fun remove(key: String): Boolean = sp.edit().remove(key).commit()
    override fun keys(): Set<String> = sp.all.keys

    fun isArchiveEnabled(): Boolean = prefs.isArchiveEnabled()
    fun setArchiveEnabled(value: Boolean) = prefs.setArchiveEnabled(value)
    fun isTombEntryVisible(): Boolean = prefs.isTombEntryVisible()
    fun setTombEntryVisible(value: Boolean) = prefs.setTombEntryVisible(value)
    fun isSwitchBackConfirm(): Boolean = prefs.isSwitchBackConfirm()
    fun setSwitchBackConfirm(value: Boolean) = prefs.setSwitchBackConfirm(value)

    companion object {
        const val NAME = "settings_store"
    }
}
