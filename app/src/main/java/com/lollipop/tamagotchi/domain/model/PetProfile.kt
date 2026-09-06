package com.lollipop.tamagotchi.domain.model

import com.lollipop.tamagotchi.core.attribute.AttributeMap
import com.lollipop.tamagotchi.core.attribute.AttributeRegistry
import com.lollipop.tamagotchi.core.behavior.PetState
import com.lollipop.tamagotchi.core.motion.Direction

/**
 * 宠物位置快照（doc/01 §9 `pos`）：
 * (x,y) 为归一化坐标（活动圆内，见 core.motion），dir 对应切表行号（Direction.ordinal），frame 当前帧序号。
 * M2/M3 尚未漫游：建档居中朝下静止，x/y/frame 恒为初始值，待 M4 行走闭环接管。
 */
data class PetPosition(
    val x: Float = 0f,
    val y: Float = 0f,
    val dir: Direction = Direction.DOWN,
    val frame: Int = 0,
)

/**
 * 行为冷却时间（doc/01 §9 `cooldowns`）：一律存「下次可用时刻」（epoch millis，0=可用）。
 * 规则引擎判断 `now >= x`（doc/01 §9 注释）。纯值对象，不承载规则逻辑。
 */
data class Cooldowns(
    val feedUntil: Long = 0L,
    val playUntil: Long = 0L,
    val petUntil: Long = 0L,
    val cleanUntil: Long = 0L,
    val studyUntil: Long = 0L,
)

/**
 * 里程碑计数（doc/01 §9 `stats` / doc/04 §3.3）：跨会话累计，动作/事件命中 +1；缺省视为 0。
 */
data class Milestones(
    val feed: Long = 0L,
    val play: Long = 0L,
    val pet: Long = 0L,
    val heal: Long = 0L,
    val clean: Long = 0L,
    val sickTotal: Long = 0L,
    val daysTogether: Long = 0L,
)

/**
 * 插件可见性/排序（doc/01 §9 `plugins`）：隐藏插件 / 快捷面板排序。
 * M3 建档为空列表，完整承载以保 schema 无信息丢失；M10 插件闭环接管。
 */
data class PetPlugins(
    val hidden: List<String> = emptyList(),
    val sort: List<String> = emptyList(),
)

/**
 * 宠物档案（doc/01 §9 schema v2）——单键 JSON 的领域映射，SP 快照唯一事实源。
 * 全部字段与 pet_profile 键一一对应；属性以 [AttributeMap]（注册表驱动）承载。
 * 时间一律 epoch millis；性格见 [Personality]。
 */
data class PetProfile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val petId: String,
    val petName: String,
    val createdAt: Long,
    val lastSettledAt: Long,
    val attributes: AttributeMap,
    val fsmState: PetState = PetState.IDLE,
    val position: PetPosition = PetPosition(),
    val isAsleep: Boolean = false,
    val sadDurationHours: Float = 0f,
    val cooldowns: Cooldowns = Cooldowns(),
    val milestones: Milestones = Milestones(),
    val personality: Personality,
    val plugins: PetPlugins = PetPlugins(),
) {
    companion object {
        /** pet_profile 单键 JSON schema 版本（doc/01 §9）。 */
        const val SCHEMA_VERSION: Int = 2

        /**
         * 建档工厂：开档即闭的完整快照。
         * 属性取 [AttributeRegistry.initialSnapshot]（饱腹 80/心情 80/健康 100/智力 0/清洁 100），
         * 居中朝下 IDLE、清醒、无冷却/里程碑；lastSettledAt = 建档时刻（首启无离线时长）。
         */
        fun new(
            petId: String,
            petName: String,
            personality: Personality,
            now: Long,
        ): PetProfile = PetProfile(
            petId = petId,
            petName = petName,
            createdAt = now,
            lastSettledAt = now,
            attributes = AttributeRegistry.initialSnapshot(),
            personality = personality,
        )
    }
}
