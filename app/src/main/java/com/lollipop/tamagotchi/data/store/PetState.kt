package com.lollipop.tamagotchi.data.store

import android.content.Context
import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.presentation.i18n.PokemonNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 中央宠物状态机（单例、静态事实源）：当前宠档案 = 内存态(StateFlow) + SP 持久化双写。
 *
 * 内聚约定（doc/00 §7）：
 * - 外部【只能读】状态：[profile]（响应式）/ [snapshot]（快照）；任何写都走本对象的受控方法，
 *   禁止各自持属性改了再存（杜绝散落的多处 `store.save` + `profile =`）。
 * - [set]：外部提交宠物的唯一入口，内部自动【保存】当前宠到 SP（store.save）+ 广播新态。
 *   注意：[set] 是「保存」（记录数据到本地持久化），【不是】「存档」（存为墓碑）。
 *   每笔结算快照、动作结算、时间旅行改写、切换形态 都走这 —— 持久化与状态更新内聚于此。
 * - [evolve]/[devolve]：纯模型变更（改角色形态，不存档）；变化由后续任意 [set] 落档。
 * - [evolveList]/[devolveList]：基于当前宠的可进化 / 可退化候选 id（由 PokemonNames 链推导）。
 * - [clear]/[archiveAndClear]：清档 / 换宠归档（写墓碑后清当前档）。
 * [attach] 每次重绑都从 SP 重载（SP 为持久事实源），故其它页经独立 PetStore 改档后，首页重启也能拿到最新值。
 */
object PetState {

    private lateinit var store: PetStore
    private val _profile = MutableStateFlow<PetProfile?>(null)

    /** 绑定 ApplicationContext 并从 SP 重载当前档（每次 Activity 启动调用，幂等且权威）。 */
    fun attach(context: Context) {
        store = PetStore(context.applicationContext)
        _profile.value = store.load()
    }

    /** 当前宠（响应式，只读）。首页据此切「建档列表 / 宠物屏」。 */
    val profile: StateFlow<PetProfile?> = _profile.asStateFlow()

    /** 当前宠（快照读，非响应式）。 */
    fun snapshot(): PetProfile? = _profile.value

    /**
     * 外部建档 / 提交宠物的唯一入口：内部自动存档（store.save）+ 广播新态。
     * 建档确认、每笔结算快照、动作结算、时间旅行改写 都走这 —— 持久化与状态更新内聚于此。
     */
    fun set(pet: PetProfile) {
        store.save(pet)
        _profile.value = pet
    }

    /**
     * 开启新伴侣（宝可梦详情页确认建档）：若存在当前档，先将其【存为档案(墓碑)】，再落档新宠。
     * 这是唯一触发「存档」的入口；切换形态 / 日常结算 / 动作 / 时间旅行都只走 [set]（保存，非存档）。
     * 形态切换不把老形态变墓碑，只在 [set] 里保存到本地。
     */
    fun startNewCompanion(newPet: PetProfile, now: Long = System.currentTimeMillis()) {
        snapshot()?.let { store.archiveCurrent(it, now) }
        set(newPet)
    }

    /**
     * 进化：把当前宠形态切到 [targetId]（保留性格与全部属性/统计），纯模型变更、内部不存档。
     * 返回新模型；调用方需经 [set] 落档。
     */
    fun evolve(targetId: String): PetProfile? {
        val cur = _profile.value ?: return null
        val np = cur.copy(petId = targetId, displayName = PokemonNames.lookup(targetId))
        _profile.value = np
        return np
    }

    /** 退化：同 [evolve]，方向相反。 */
    fun devolve(targetId: String): PetProfile? {
        val cur = _profile.value ?: return null
        val np = cur.copy(petId = targetId, displayName = PokemonNames.lookup(targetId))
        _profile.value = np
        return np
    }

    /** 当前宠可进化候选 id（next 链）。 */
    fun evolveList(): List<String> {
        val cur = _profile.value ?: return emptyList()
        return PokemonNames.nextOf(cur.petId)
    }

    /** 当前宠可退化候选 id（prev 链）。 */
    fun devolveList(): List<String> {
        val cur = _profile.value ?: return emptyList()
        return PokemonNames.prevOf(cur.petId)
    }

    /** 清档（重开档）：删档 + 广播 null → 首页回建档列表。 */
    fun clear() {
        store.delete()
        _profile.value = null
    }

    /** 换宠归档：先写墓碑，再清当前档（设置页「重开档」口径）。 */
    fun archiveAndClear(pet: PetProfile, now: Long) {
        store.archiveCurrent(pet, now)
        clear()
    }
}
