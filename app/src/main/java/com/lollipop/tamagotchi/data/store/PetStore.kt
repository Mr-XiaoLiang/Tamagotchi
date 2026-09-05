package com.lollipop.tamagotchi.data.store

import android.content.Context
import com.lollipop.tamagotchi.domain.model.PetProfile

/**
 * 宠物档案持久化（doc/01 §9）：SharedPreferences 单键 JSON。
 * 独一事实源：任何读档/建档/重开档都必须经由此处，杜绝散落二次缓存。
 *
 * 存取策略：
 * - [save] 用 commit() 同步落盘——建档/存档后进程可立即被杀，保证快照不丢；
 * - [load] 坏档（JSON 损坏 / 无身份）一律返回 null → 上层走建档流程，不崩溃；
 * - [delete] 供 debug 重开档/清档（release 无入口）。
 */
class PetStore(context: Context) {

    private val sp = context.getSharedPreferences("pet_store", Context.MODE_PRIVATE)

    /** 当前档案；无档 / 坏档返回 null。 */
    fun load(): PetProfile? {
        val raw = sp.getString(PetProfileCodec.PREFS_KEY, null) ?: return null
        return try {
            PetProfileCodec.fromJson(raw)
        } catch (_: Throwable) {
            null
        }
    }

    /** 覆盖写当前档案（建档 / 后续每笔快照提交）。 */
    fun save(profile: PetProfile): Boolean =
        sp.edit()
            .putString(PetProfileCodec.PREFS_KEY, PetProfileCodec.toJson(profile))
            .commit()

    /** 删除档案（debug 重开档入口使用）。 */
    fun delete() {
        sp.edit().remove(PetProfileCodec.PREFS_KEY).commit()
    }
}
