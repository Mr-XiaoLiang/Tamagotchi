package com.lollipop.tamagotchi.data.store

/**
 * 极简键值存储抽象（M15）：解耦 Android [android.content.SharedPreferences]，
 * 使 [PetArchive] 纯逻辑可在 JVM 单测中用内存假实现验证（doc/04 §3.3）。
 */
interface KVStore {
    fun get(key: String): String?
    fun put(key: String, value: String): Boolean
    fun remove(key: String): Boolean
    fun keys(): Set<String>
}
