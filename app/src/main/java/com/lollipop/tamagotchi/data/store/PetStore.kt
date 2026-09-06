package com.lollipop.tamagotchi.data.store

import android.content.Context
import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.domain.model.PetTomb

/**
 * 宠物档案持久化（doc/01 §9 / M15）：SharedPreferences 多键 JSON。
 * 独一事实源：任何读档/建档/重开档/换宠归档都必须经由此处，杜绝散落二次缓存。
 *
 * 存取策略：
 * - [save] 用 commit() 同步落盘——建档/存档后进程可立即被杀，保证快照不丢；
 * - [load] 坏档（JSON 损坏 / 无身份）一律返回 null → 上层走建档流程，不崩溃；
 * - [delete] 供 debug 重开档/清档（release 无入口）。
 *
 * M15 起从「单键当前宠」升级为「当前档 + 墓碑档」多键布局（schema v3，含 v2 单键迁移——
 * 旧 v2 档即当前档，墓碑集合为空，无需改写），详见 doc/04 §3.3 / Task.md M15。
 */
class PetStore(context: Context) : KVStore {

    private val sp = context.getSharedPreferences("pet_store", Context.MODE_PRIVATE)
    private val archive = PetArchive(this)

    override fun get(key: String): String? = sp.getString(key, null)
    override fun put(key: String, value: String): Boolean =
        sp.edit().putString(key, value).commit()
    override fun remove(key: String): Boolean =
        sp.edit().remove(key).commit()
    override fun keys(): Set<String> = sp.all.keys

    /** 当前档案；无档 / 坏档返回 null。 */
    fun load(): PetProfile? = archive.loadCurrent()

    /** 覆盖写当前档案（建档 / 后续每笔快照提交 / 切回墓碑）。 */
    fun save(profile: PetProfile): Boolean = archive.saveCurrent(profile)

    /** 删除档案（debug 重开档入口使用）。 */
    fun delete() = archive.deleteCurrent()

    // ── M15.S1 多档：墓碑 + 切换 ──

    /** 换宠前把当前宠最终快照写入墓碑（电子墓碑素材）。 */
    fun archiveCurrent(profile: PetProfile, now: Long) = archive.archiveCurrent(profile, now)

    /** 把墓碑宠切回为当前档，返回切回的档案；无此墓碑返回 null。 */
    fun switchTo(petId: String): PetProfile? = archive.switchTo(petId)

    /** 墓碑列表（按归档先后）。 */
    fun listTombs(): List<PetTomb> = archive.listTombs()

    /** 删除指定墓碑（不可恢复）。 */
    fun deleteTomb(petId: String) = archive.deleteTomb(petId)
}
