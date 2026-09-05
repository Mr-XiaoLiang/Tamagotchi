package com.lollipop.tamagotchi.domain.engine

import com.lollipop.tamagotchi.core.attribute.AttributeDelta
import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.AttributeMap
import com.lollipop.tamagotchi.core.attribute.AttributeRegistry
import com.lollipop.tamagotchi.core.attribute.FoodType
import com.lollipop.tamagotchi.core.behavior.PetState
import com.lollipop.tamagotchi.domain.log.EventLog
import com.lollipop.tamagotchi.domain.log.EventLogType
import com.lollipop.tamagotchi.domain.log.SessionLog
import com.lollipop.tamagotchi.domain.model.Milestones
import com.lollipop.tamagotchi.domain.model.PetProfile

/**
 * 玩家动作类型（doc/01 §10 面板动作集）。M1–M10 主链只实现四种：
 * 喂食（选食物）/ 玩耍 / 抚摸 / 治疗；清洁/学习随 M11/M12（doc/09 §5.2/§5.3）。
 */
enum class ActionType { FEED, PLAY, PET, HEAL }

/**
 * 动作 FSM 提示（doc/02 §1.1）：喂食 → EATING、玩耍 → EXCITED 是短状态（2~3s 后回落）；
 * 抚摸「亲昵」与治疗无独立 PetState（doc/02 §1.1 注释：以短动作动画+气泡在现有状态上叠加）。
 * M6.S1 只出提示；表现层（M6.S2）据此播放，不把短态落持久快照（防冷启卡死锁态）。
 */
enum class ActionHint { EATING, EXCITED, AFFECTION, TREATED }

/** 动作不可执行的展示原因（doc/06 §8.3：面板空心胶囊 = 只读信息位）。 */
sealed interface ActionDenied {

    /** 冷却中：展示剩余倒计时。 */
    data class Cooldown(
        val nextUsableAt: Long,
        val remainMs: Long,
    ) : ActionDenied

    /** 条件属性已到上限（不可再喂/玩到过满，doc/01 §10 条件表）。 */
    data class AttributeCeiling(
        val id: AttributeId,
        val current: Float,
    ) : ActionDenied

    /** 治疗仅 SICK 可点：非 SICK 不出现（面板隐藏而非空心），引擎侧同样拦截。 */
    data object NotSick : ActionDenied
}

/**
 * 一次动作结果：
 * - [profile]：数值/冷却/统计已更新的新快照（fsmState 保持，仅 SAD 即时解除/治疗结束 SICK 时迁移）；
 * - [delta]：实际生效增量（边际收益 + clamp 后），供属性浮字展示；
 * - [hint]：动作 FSM 提示（表现层短演出用）；
 * - [cooldownUntil]：本次写下的下次可用时刻（治疗 null——其约束为「仅 SICK」）；
 * - [note]：补充信息（如食物类型名，供日志/气泡）。
 */
data class ActionResult(
    val profile: PetProfile,
    val delta: AttributeDelta,
    val hint: ActionHint,
    val cooldownUntil: Long?,
    val note: String? = null,
)

/**
 * 动作可用条件 + 数值规则（doc/01 §6.3/§7/§10）。
 *
 * 可用性（条件表）：
 * - 喂食：`now >= feed_until` 且 `satiation < 95`（冷却 2h）；
 * - 玩耍：`now >= play_until` 且 `mood < 90`（冷却 1h）；
 * - 抚摸：`now >= pet_until`（冷却 10min，无条件属性限制）；
 * - 治疗：`fsmState == SICK`（无冷却字段，治愈即不可用）。
 *
 * 数值规则：
 * - 边际收益递减（doc/01 §6.3）：恢复类增益 `gain = base × (100 - current) / 100`
 *   （饱腹/心情/清洁/智力通用）；health 回血不走递减（营养餐微量、治疗 +40 固定且确定——doc/01 §6.3 白字未含 health）。
 * - 口味契合（doc/01 §7）：food.flavor == personality.flavor → 该餐 sat/mood 收益 ×1.1。
 */
object ActionRule {

    const val FEED_COOLDOWN_MS: Long = 2 * 60 * 60 * 1000L
    const val PLAY_COOLDOWN_MS: Long = 1 * 60 * 60 * 1000L
    const val PET_COOLDOWN_MS: Long = 10 * 60 * 1000L

    /** 口味契合加成（doc/01 §7：额外 +10%）。 */
    const val FLAVOR_BONUS: Float = 1.1f

    /** SAD 解除线（doc/02 §1.2 转移表「心情回升到 40+」）。 */
    const val SAD_RECOVER_MOOD: Float = 40f

    /** 喂食 sat 上限（doc/01 §10：<95 可喂）。 */
    const val FEED_SAT_CEILING: Float = 95f

    /** 玩耍 mood 上限（doc/01 §10：<90 可玩）。 */
    const val PLAY_MOOD_CEILING: Float = 90f

    /** 治疗 health +40（doc/01 §6.2/§10）。 */
    const val HEAL_GAIN: Float = 40f

    /** 喂食附带清洁代价（doc/01 §6.2 备注：-4）。 */
    const val FEED_HYGIENE_COST: Float = 4f

    fun feedDenied(profile: PetProfile, now: Long): ActionDenied? {
        cooldown(profile.cooldowns.feedUntil, now)?.let { return it }
        val sat = profile.attributes[AttributeId.SATIATION]
        if (sat >= FEED_SAT_CEILING) return ActionDenied.AttributeCeiling(AttributeId.SATIATION, sat)
        return null
    }

    fun playDenied(profile: PetProfile, now: Long): ActionDenied? {
        cooldown(profile.cooldowns.playUntil, now)?.let { return it }
        val mood = profile.attributes[AttributeId.MOOD]
        if (mood >= PLAY_MOOD_CEILING) return ActionDenied.AttributeCeiling(AttributeId.MOOD, mood)
        return null
    }

    fun petDenied(profile: PetProfile, now: Long): ActionDenied? =
        cooldown(profile.cooldowns.petUntil, now)

    fun healDenied(profile: PetProfile): ActionDenied? =
        if (profile.fsmState != PetState.SICK) ActionDenied.NotSick else null

    /** 冷却判定：now < until → Cooldown（含剩余时长）。 */
    fun cooldown(until: Long, now: Long): ActionDenied.Cooldown? =
        if (now < until) ActionDenied.Cooldown(until, until - now) else null

    /** 边际收益递减：`base × (100 - current) / 100`（doc/01 §6.3）。 */
    fun diminishedGain(base: Float, current: Float): Float =
        base * (100f - current) / 100f

    /** 口味契合系数：匹配 ×[FLAVOR_BONUS]，否则 ×1。 */
    fun flavorMultiplier(food: FoodType, profile: PetProfile): Float =
        if (food.flavor == profile.personality.flavor) FLAVOR_BONUS else 1f
}

/**
 * 玩家动作执行（doc/01 §6.2/§10；Task M6.S1）。
 *
 * 全部纯函数：denied 非空 → 返回 null（未执行、快照不变）；成功返回 [ActionResult]。
 * 成功后即时：属性按边际收益/clamp 应用、冷却写 next-until、stats（里程碑）命中 +1、
 * 可选 [log] 追加 `ACTION_*` 条目（内存会话日志，doc/04 §3.1）。
 *
 * fsmState 语义（M6.S1 落地决策，doc/02 §1.2）：
 * - 短动作态 EATING/EXCITED **不写持久快照**（由表现层临时演出，防动作后杀进程冷启卡锁态）；
 * - SAD：动作后 mood 回升 ≥ [ActionRule.SAD_RECOVER_MOOD]（40）→ 即时解除为 IDLE（sadDurationHours 清零）；
 * - SICK：只有 [onHeal] 结束（→ IDLE）；喂食/玩耍安抚生病的宠物只动数值、不改状态。
 */
object PetActions {

    private val SAT = AttributeId.SATIATION
    private val MOOD = AttributeId.MOOD
    private val HEALTH = AttributeId.HEALTH
    private val INT = AttributeId.INTELLIGENCE
    private val HYG = AttributeId.HYGIENE

    /**
     * 喂食（选一种食物，doc/01 §7）：sat/mood 按口味契合 ×1.1 后走边际收益；
     * health 照类型表固定；hyg 附 -4。冷却 2h。成功 stats.feed +1。
     */
    fun onFeed(
        profile: PetProfile,
        now: Long,
        food: FoodType,
        log: SessionLog? = null,
    ): ActionResult? {
        if (ActionRule.feedDenied(profile, now) != null) return null
        val cur = profile.attributes
        val k = ActionRule.flavorMultiplier(food, profile)
        val attrs = cur
            .set(SAT, cur[SAT] + ActionRule.diminishedGain(food.satDelta * k, cur[SAT]))
            .set(MOOD, cur[MOOD] + ActionRule.diminishedGain(food.moodDelta * k, cur[MOOD]))
            .set(HEALTH, cur[HEALTH] + food.healthDelta)
            .set(HYG, cur[HYG] - ActionRule.FEED_HYGIENE_COST)
        val after = withSadRecovery(profile, attrs).copy(
            cooldowns = profile.cooldowns.copy(
                feedUntil = now + ActionRule.FEED_COOLDOWN_MS,
            ),
            milestones = bump(profile.milestones) { it.copy(feed = it.feed + 1) },
        )
        return finish(profile, now, after, ActionHint.EATING,
            now + ActionRule.FEED_COOLDOWN_MS, EventLogType.ACTION_FEED, log, food.name)
    }

    /**
     * 玩耍：mood +15（边际）、health +2、hyg -2、int +1（边际，学习型）。冷却 1h。
     * 成功 stats.play +1。
     */
    fun onPlay(
        profile: PetProfile,
        now: Long,
        log: SessionLog? = null,
    ): ActionResult? {
        if (ActionRule.playDenied(profile, now) != null) return null
        val cur = profile.attributes
        val attrs = cur
            .set(MOOD, cur[MOOD] + ActionRule.diminishedGain(PLAY_MOOD_GAIN, cur[MOOD]))
            .set(HEALTH, cur[HEALTH] + PLAY_HEALTH_GAIN)
            .set(HYG, cur[HYG] + PLAY_HYGIENE_COST)
            .set(INT, cur[INT] + ActionRule.diminishedGain(PLAY_INT_GAIN, cur[INT]))
        val after = withSadRecovery(profile, attrs).copy(
            cooldowns = profile.cooldowns.copy(
                playUntil = now + ActionRule.PLAY_COOLDOWN_MS,
            ),
            milestones = bump(profile.milestones) { it.copy(play = it.play + 1) },
        )
        return finish(profile, now, after, ActionHint.EXCITED,
            now + ActionRule.PLAY_COOLDOWN_MS, EventLogType.ACTION_PLAY, log, null)
    }

    /** 抚摸：mood +5（边际，轻互动）。冷却 10min。成功 stats.pet +1。 */
    fun onPet(
        profile: PetProfile,
        now: Long,
        log: SessionLog? = null,
    ): ActionResult? {
        if (ActionRule.petDenied(profile, now) != null) return null
        val cur = profile.attributes
        val attrs = cur.set(MOOD, cur[MOOD] + ActionRule.diminishedGain(PET_MOOD_GAIN, cur[MOOD]))
        val after = withSadRecovery(profile, attrs).copy(
            cooldowns = profile.cooldowns.copy(
                petUntil = now + ActionRule.PET_COOLDOWN_MS,
            ),
            milestones = bump(profile.milestones) { it.copy(pet = it.pet + 1) },
        )
        return finish(profile, now, after, ActionHint.AFFECTION,
            now + ActionRule.PET_COOLDOWN_MS, EventLogType.ACTION_PET, log, null)
    }

    /**
     * 治疗：仅 SICK 可用；health +40（固定，doc/01 §6.2/§10）并结束 SICK（→ IDLE）。
     * 无冷却字段（治愈即不可再点）。成功 stats.heal +1。
     */
    fun onHeal(
        profile: PetProfile,
        now: Long,
        log: SessionLog? = null,
    ): ActionResult? {
        if (ActionRule.healDenied(profile) != null) return null
        val cur = profile.attributes
        val attrs = cur.set(HEALTH, cur[HEALTH] + ActionRule.HEAL_GAIN)
        val after = profile.copy(
            attributes = attrs,
            fsmState = PetState.IDLE,
            milestones = bump(profile.milestones) { it.copy(heal = it.heal + 1) },
        )
        return finish(profile, now, after, ActionHint.TREATED,
            null, EventLogType.ACTION_HEAL, log, null)
    }

    // ── 私有工具 ───────────────────────────────────────────

    /** SAD 即时解除：动作后 mood ≥ 40 → IDLE 并清零持续计时（doc/02 §1.2）。 */
    private fun withSadRecovery(profile: PetProfile, attrs: AttributeMap): PetProfile =
        if (profile.fsmState == PetState.SAD && attrs[MOOD] >= ActionRule.SAD_RECOVER_MOOD) {
            profile.copy(
                attributes = attrs,
                fsmState = PetState.IDLE,
                sadDurationHours = 0f,
            )
        } else {
            profile.copy(attributes = attrs)
        }

    private fun bump(stats: Milestones, update: (Milestones) -> Milestones): Milestones = update(stats)

    private fun finish(
        before: PetProfile,
        now: Long,
        after: PetProfile,
        hint: ActionHint,
        cooldownUntil: Long?,
        type: EventLogType,
        log: SessionLog?,
        note: String?,
    ): ActionResult {
        val attrsBefore = before.attributes
        val delta = AttributeDelta(
            AttributeRegistry.all.associate { meta ->
                meta.id to (after.attributes[meta.id] - attrsBefore[meta.id])
            },
        )
        log?.append(
            EventLog(
                ts = now,
                type = type,
                before = attrsBefore,
                delta = delta,
                state = after.fsmState,
                note = note,
            ),
        )
        return ActionResult(profile = after, delta = delta, hint = hint,
            cooldownUntil = cooldownUntil, note = note)
    }

    private const val PLAY_MOOD_GAIN = 15f
    private const val PLAY_HEALTH_GAIN = 2f
    private const val PLAY_HYGIENE_COST = -2f
    private const val PLAY_INT_GAIN = 1f
    private const val PET_MOOD_GAIN = 5f
}
