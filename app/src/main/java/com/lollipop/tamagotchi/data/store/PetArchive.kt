package com.lollipop.tamagotchi.data.store

import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.domain.model.PetTomb

/**
 * 多档宠物档案逻辑（M15.S1，doc/04 §3.3 / 09 §5.5）：当前档（正在养）+ 墓碑集合（换宠归档）。
 * 纯逻辑、不依赖 Android；底层 [KVStore] 由 [PetStore] 用 SharedPreferences 实现，单测用内存假实现。
 *
 * 语义（用户决策）：档案 ≠ 日志——SessionLog 不在本类持久化（04 §3.1 不变）；
 * 墓碑 = 换宠时刻的最终快照（含 stats 终值 / 共处时长），只读、可切回。
 */
class PetArchive(private val kv: KVStore) {

    /** 当前档；无档 / 坏档返回 null。 */
    fun loadCurrent(): PetProfile? {
        val raw = kv.get(PetProfileCodec.PREFS_KEY) ?: return null
        return PetProfileCodec.fromJson(raw)
    }

    /** 覆盖写当前档（建档 / 每次快照提交 / 切回墓碑）。 */
    fun saveCurrent(profile: PetProfile): Boolean =
        kv.put(PetProfileCodec.PREFS_KEY, PetProfileCodec.toJson(profile))

    /** 删除当前档（debug 重开档入口）。 */
    fun deleteCurrent() {
        kv.remove(PetProfileCodec.PREFS_KEY)
    }

    /** 墓碑列表（按归档先后）。 */
    fun listTombs(): List<PetTomb> = PetTombCodec.listFromJson(kv.get(TOMBS_KEY))

    /**
     * 把当前宠最终快照写入墓碑（电子墓碑素材）。不删当前档——
     * 调用方随后 [saveCurrent] 覆盖写新宠即完成「换宠」。同 petId 墓碑去重。
     */
    fun archiveCurrent(profile: PetProfile, now: Long) {
        val tomb = PetTomb(
            petId = profile.petId,
            profile = profile,
            retiredAt = now,
            companionshipMs = (now - profile.createdAt).coerceAtLeast(0L),
        )
        val next = listTombs().filter { it.petId != profile.petId } + tomb
        kv.put(TOMBS_KEY, PetTombCodec.listToJson(next))
    }

    /**
     * 快照切换（swap 语义）：把墓碑宠反序列化为当前档（切回继续养），并从墓碑集合移除。
     * 切回前**先把当前正在养的宠归档**（避免被覆盖丢失），因此任何一次切换都不会丢宠——
     * 例如当前 B、切回墓碑 A，则 B 被归档进「过往」、A 成为当前档。
     * 返回切回的档案；无此墓碑返回 null。
     */
    fun switchTo(petId: String, now: Long = System.currentTimeMillis()): PetProfile? {
        val tomb = listTombs().firstOrNull { it.petId == petId } ?: return null
        // 切回前先把当前宠（如 B）归档，防止被覆盖丢失
        val current = loadCurrent()
        if (current != null) {
            archiveCurrent(current, now)
        }
        val remaining = listTombs().filter { it.petId != petId }
        kv.put(TOMBS_KEY, PetTombCodec.listToJson(remaining))
        saveCurrent(tomb.profile)
        return tomb.profile
    }

    /** 删除指定墓碑（不可恢复）。 */
    fun deleteTomb(petId: String) {
        val remaining = listTombs().filter { it.petId != petId }
        kv.put(TOMBS_KEY, PetTombCodec.listToJson(remaining))
    }

    companion object {
        /** 墓碑集合的 SP key（v3 多档布局新增；当前档仍用 [PetProfileCodec.PREFS_KEY]）。 */
        const val TOMBS_KEY = "pet_tombs_v3"
    }
}
