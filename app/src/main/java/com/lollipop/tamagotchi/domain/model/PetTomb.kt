package com.lollipop.tamagotchi.domain.model

/**
 * 电子墓碑（M15，doc/04 §3.3 / 09 §5.5）：换宠时把被换下宠物的**最终快照**归档为墓碑。
 * 本质仍是快照存取、不落日志（doc/04 §3.1 不变）。
 *
 * @param petId   墓碑宠物身份（与 [PetProfile.petId] 一致，墓碑主键）。
 * @param profile 最终快照（含 [PetProfile.milestones] 终值、属性、性格等）。
 * @param retiredAt 归档时刻（epoch millis）。
 * @param companionshipMs 共处时长（retiredAt − [PetProfile.createdAt]，≥0）。
 */
data class PetTomb(
    val petId: String,
    val profile: PetProfile,
    val retiredAt: Long,
    val companionshipMs: Long,
)
