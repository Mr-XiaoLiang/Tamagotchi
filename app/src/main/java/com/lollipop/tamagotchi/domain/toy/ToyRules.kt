package com.lollipop.tamagotchi.domain.toy

import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.MilestoneStat
import com.lollipop.tamagotchi.core.attribute.ToyType
import com.lollipop.tamagotchi.core.attribute.ToyUnlock
import com.lollipop.tamagotchi.domain.model.Milestones
import com.lollipop.tamagotchi.domain.model.PetProfile

/**
 * 玩具可用性规则（doc/09 §5.1 / M13.S1）：无货币，解锁来源三选一——
 * 默认拥有 / 智力解锁（[ToyUnlock.ByKnowledge]）/ 里程碑解锁（[ToyUnlock.ByMilestone]）/
 * 随机事件掉落（[ToyUnlock.ByEvent]，运行时经 [eventGranted] 授予）。
 *
 * 纯函数、零 Android 依赖，可单测（M13.S1 基建拍）。
 */
object ToyRules {

    fun isUnlocked(
        toy: ToyType,
        profile: PetProfile,
        eventGranted: Set<ToyType> = emptySet(),
    ): Boolean = when (val u = toy.unlock) {
        ToyUnlock.Default -> true
        ToyUnlock.ByEvent -> toy in eventGranted
        is ToyUnlock.ByKnowledge -> profile.attributes[AttributeId.KNOWLEDGE] >= u.knowledge
        is ToyUnlock.ByMilestone -> milestoneValue(profile.milestones, u.stat) >= u.count
    }

    fun unlockedToys(
        profile: PetProfile,
        eventGranted: Set<ToyType> = emptySet(),
    ): Set<ToyType> = ToyType.entries.filter { isUnlocked(it, profile, eventGranted) }.toSet()

    private fun milestoneValue(m: Milestones, stat: MilestoneStat): Long = when (stat) {
        MilestoneStat.FEED -> m.feed
        MilestoneStat.PLAY -> m.play
        MilestoneStat.PET -> m.pet
        MilestoneStat.HEAL -> m.heal
        MilestoneStat.CLEAN -> m.clean
        MilestoneStat.STUDY -> m.study
        MilestoneStat.SICK -> m.sickTotal
        MilestoneStat.DAYS -> m.daysTogether
    }
}
