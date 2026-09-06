package com.lollipop.tamagotchi.domain.engine

import com.lollipop.tamagotchi.core.attribute.AttributeDelta
import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.AttributeMap
import com.lollipop.tamagotchi.core.attribute.AttributeRegistry
import com.lollipop.tamagotchi.core.attribute.FoodType
import com.lollipop.tamagotchi.core.attribute.PlayType
import com.lollipop.tamagotchi.core.attribute.ToyType
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
enum class ActionType { FEED, PLAY, PET, HEAL, CLEAN, STUDY }

/**
 * 动作 FSM 提示（doc/02 §1.1）：喂食 → EATING、玩耍 → EXCITED 是短状态（2~3s 后回落）；
 * 抚摸「亲昵」与治疗无独立 PetState（doc/02 §1.1 注释：以短动作动画+气泡在现有状态上叠加）。
 * M6.S1 只出提示；表现层（M6.S2）据此播放，不把短态落持久快照（防冷启卡死锁态）。
 */
enum class ActionHint { EATING, EXCITED, AFFECTION, TREATED, CLEANING, STUDYING }

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
    /** 触发动作所用的玩具（玩耍专属；表现层据此选玩具情绪类别，doc/09 §5.1）。 */
    val toy: ToyType? = null,
    /** 学习解锁档位（智力跨越该阈值时非 null，供 UI 弹「学会新招」气泡；doc/01 §4.1）。 */
    val unlockTier: Int? = null,
)

/**
 * 动作可用条件 + 数值规则（doc/01 §6.3/§7/§10）。
 *
 * 可用性（条件表）：
 * - 喂食：`now >= feed_until` 且 `satiation < 95`（冷却 5s，防连点 + 缓冲）；
 * - 玩耍：`now >= play_until` 且 `mood < 90`（冷却 5s）；
 * - 抚摸：`now >= pet_until`（冷却 5s，无条件属性限制）；
 * - 治疗：`fsmState == SICK`（无冷却字段，治愈即不可用）。
 *
 * 数值规则：
 * - 边际收益递减（doc/01 §6.3）：恢复类增益 `gain = base × (100 - current) / 100`
 *   （饱腹/心情/清洁/智力通用）；health 回血不走递减（营养餐微量、治疗 +40 固定且确定——doc/01 §6.3 白字未含 health）。
 * - 口味契合（doc/01 §7）：food.flavor == personality.flavor → 该餐 sat/mood 收益 ×1.1。
 */
object ActionRule {

    /** 动作最小间隔：统一 5s，仅防连点 + 留一点缓冲（doc/01 §10；原 2h/1h/10min 已放宽）。 */
    const val FEED_COOLDOWN_MS: Long = 5 * 1000L
    const val PLAY_COOLDOWN_MS: Long = 5 * 1000L
    const val PET_COOLDOWN_MS: Long = 5 * 1000L
    const val CLEAN_COOLDOWN_MS: Long = 5 * 1000L
    /** 学习冷却 5s（doc/01 §6.2 / §10；与喂/玩/抚/清洁统一最小间隔）。 */
    const val STUDY_COOLDOWN_MS: Long = 5 * 1000L

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
    /** 治疗附带饱食消耗（doc/01 §6.2：疗伤也费体力）。 */
    const val HEAL_SAT_COST: Float = 3f

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

    fun cleanDenied(profile: PetProfile, now: Long): ActionDenied? =
        cooldown(profile.cooldowns.cleanUntil, now)

    fun studyDenied(profile: PetProfile, now: Long): ActionDenied? =
        cooldown(profile.cooldowns.studyUntil, now)

    /** 学习增益受 learner 倍率系数加权（doc/02 §4.4：learnSpeed = (0.5 + learner) × base，learner 固定 0~1 → 增益 0.5×~1.5×）。 */
    fun studyIntGain(base: Float, learner: Float): Float = base * (0.5f + learner)

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
 * 成功后即时：属性按「特质缩放 × 边际收益 × 随机浮动」应用并 clamp、冷却写 next-until、
 * stats（里程碑）命中 +1、可选 [log] 追加 `ACTION_*` 条目（内存会话日志，doc/04 §3.1）。
 *
 * **联动模型（用户平衡需求 / doc/01 §6.3 补充）**：每个属性的增减都受宠物固定特质（traits）
 * 与当前浮动值共同影响，且每次增减带 ±[JITTER] 随机浮动（避免固定参数）：
 * - 喂食：饱腹/心情走「口味×特质×边际递减」；健康按食物表固定；并触发交叉副作用——
 *   同时增饱腹+健康的食物同步降心情（健康餐不开心），增心情的食物同步降健康、并可能降清洁。
 * - 玩耍：心情↑、健康↓、清洁↓、饱食↓、知识小幅↓（学识仅被玩消耗，不随时间长被动衰减）；不同 [PlayType] 幅度不同；
 *   知识损耗随好奇(curiosity)减免（好奇的宠物玩也在琢磨）。
 * - 抚摸：心情↑（随急躁放大、随粘人(affinity)放大）；无副作用。
 * - 学习：知识↑（受隐藏智商 learner 加权）、心情↓、饱食↓。
 * - 清洁：清洁↑；health 偏低时顺带补少量健康；耗饱食。
 * - 治疗：健康↑（固定）、耗饱食。
 * 所有动作冷却统一 5s。
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
    private val INT = AttributeId.KNOWLEDGE
    private val HYG = AttributeId.HYGIENE

    /**
     * 喂食（选一种食物，doc/01 §7）：sat/mood 按「口味契合 × 特质缩放 × 边际收益」；
     * health 照类型表固定（不递减）。并触发交叉副作用（用户平衡需求）：
     * - 同时增饱腹+健康 → 同步降心情（健康餐让人不开心）；
     * - 增心情 → 同步降健康，并可能降清洁（越开心越不讲究）。
     * 全部增量经 [jitter] 随机浮动。冷却 5s。成功 stats.feed +1。
     */
    fun onFeed(
        profile: PetProfile,
        now: Long,
        food: FoodType,
        log: SessionLog? = null,
    ): ActionResult? {
        if (ActionRule.feedDenied(profile, now) != null) return null
        val cur = profile.attributes
        val traits = profile.personality.traits
        val k = ActionRule.flavorMultiplier(food, profile)
        // 基础增量：饱腹/心情走「口味×特质×边际递减」；健康按食物表固定。
        val satGain = ActionRule.diminishedGain(
            traitScale(food.satDelta * k, traits.appetite, APPETITE_SLOPE), cur[SAT])
        val moodBase = traitScale(food.moodDelta * k, traits.temper, TEMPER_SLOPE)
        val moodGain = if (moodBase > 0f) ActionRule.diminishedGain(moodBase, cur[MOOD]) else moodBase
        val healthGain = food.healthDelta * k
        // 交叉副作用
        var moodOut = moodGain
        var healthOut = healthGain
        var hygOut = -ActionRule.FEED_HYGIENE_COST.toFloat()
        if (healthGain > 0f && satGain > 0f) {
            moodOut -= FEED_HEALTH_TO_MOOD * healthGain          // (a) 健康餐 → 降心情
        }
        if (moodGain > 0f) {
            healthOut -= FEED_MOOD_TO_HEALTH * moodGain           // (b) 开心餐 → 降健康
            if (effectRng() < FEED_MOOD_TO_HYG_PROB) {
                hygOut -= FEED_MOOD_TO_HYG * moodGain             // (b) 可能降清洁
            }
        }
        val attrs = cur
            .set(SAT, cur[SAT] + jitter(satGain))
            .set(MOOD, cur[MOOD] + jitter(moodOut))
            .set(HEALTH, cur[HEALTH] + jitter(healthOut))
            .set(HYG, cur[HYG] + jitter(hygOut))
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
     * 玩耍：心情↑、健康↓、清洁↓、饱食↓；知识小幅↓（学识仅被玩消耗，不随时间长被动衰减）。不同 [style] 幅度不同
     * （doc/01 §6.2：翻滚/追尾巴/逗弄）。心情增益随急躁放大、健康损耗随好动放大、知识损耗随好奇减免，
     * 全部带随机浮动。
     * 冷却 5s。成功 stats.play +1。
     */
    fun onPlay(
        profile: PetProfile,
        now: Long,
        style: PlayType = PlayType.DEFAULT,
        log: SessionLog? = null,
        toy: ToyType? = null,
    ): ActionResult? {
        if (ActionRule.playDenied(profile, now) != null) return null
        val cur = profile.attributes
        val traits = profile.personality.traits
        // 选了玩具 → 用玩具数值档（doc/09 §5.1：玩具差异化收益）；否则用基础玩耍方式。
        val baseMood = toy?.moodDelta ?: style.moodDelta
        val baseHealth = toy?.healthCost ?: style.healthCost
        val baseHyg = toy?.hygieneCost ?: style.hygieneCost
        val baseSat = toy?.satCost ?: style.satCost
        val moodGain = ActionRule.diminishedGain(
            traitScale(baseMood, traits.temper, TEMPER_SLOPE), cur[MOOD])
        val dHealth = -traitScale(baseHealth, traits.activity, ACTIVITY_SLOPE)
        val dHyg = -baseHyg
        val dSat = -baseSat
        val dInt = -PlayType.KNOWLEDGE_COST * (1f - traits.curiosity)  // 好奇→玩耍也在琢磨，知识损耗随好奇减免
        val attrs = cur
            .set(MOOD, cur[MOOD] + jitter(moodGain))
            .set(HEALTH, cur[HEALTH] + jitter(dHealth))
            .set(HYG, cur[HYG] + jitter(dHyg))
            .set(SAT, cur[SAT] + jitter(dSat))
            .set(INT, cur[INT] + jitter(dInt))
        val after = withSadRecovery(profile, attrs).copy(
            cooldowns = profile.cooldowns.copy(
                playUntil = now + ActionRule.PLAY_COOLDOWN_MS,
            ),
            milestones = bump(profile.milestones) { it.copy(play = it.play + 1) },
        )
        return finish(profile, now, after, ActionHint.EXCITED,
            now + ActionRule.PLAY_COOLDOWN_MS, EventLogType.ACTION_PLAY, log, toy?.label ?: style.label,
            toy = toy)
    }

    /** 抚摸：心情↑（边际，随急躁放大、随粘人放大）；无副作用；冷却 5s。成功 stats.pet +1。 */
    fun onPet(
        profile: PetProfile,
        now: Long,
        log: SessionLog? = null,
    ): ActionResult? {
        if (ActionRule.petDenied(profile, now) != null) return null
        val cur = profile.attributes
        val traits = profile.personality.traits
        val moodGain = ActionRule.diminishedGain(
            traitScale(PET_MOOD_GAIN, traits.temper, TEMPER_SLOPE) * affinityFactor(traits.affinity),
            cur[MOOD])
        val attrs = cur.set(MOOD, cur[MOOD] + jitter(moodGain))
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
     * 清洁：清洁↑（clamp 100）；health 偏低（<[CLEAN_HEALTH_THRESHOLD]）时顺带补少量健康；耗饱食
     * （洗澡也费体力）。全部经 [jitter] 浮动。冷却 5s。成功 stats.clean +1。
     */
    fun onClean(
        profile: PetProfile,
        now: Long,
        log: SessionLog? = null,
    ): ActionResult? {
        if (ActionRule.cleanDenied(profile, now) != null) return null
        val cur = profile.attributes
        val dHyg = CLEAN_HYGIENE_GAIN
        val dSat = -CLEAN_SAT_COST
        val dHealth = if (cur[HEALTH] < CLEAN_HEALTH_THRESHOLD) CLEAN_HEALTH_BONUS else 0f
        val attrs = cur
            .set(HYG, cur[HYG] + jitter(dHyg))
            .set(SAT, cur[SAT] + jitter(dSat))
            .set(HEALTH, cur[HEALTH] + jitter(dHealth))
        val after = profile.copy(
            attributes = attrs,
            cooldowns = profile.cooldowns.copy(cleanUntil = now + ActionRule.CLEAN_COOLDOWN_MS),
            milestones = bump(profile.milestones) { it.copy(clean = it.clean + 1) },
        )
        return finish(profile, now, after, ActionHint.CLEANING,
            now + ActionRule.CLEAN_COOLDOWN_MS, EventLogType.ACTION_CLEAN, log, null)
    }

    /**
     * 学习（M12，doc/09 §5.2 / 01 §6.2）：知识↑（受隐藏智商 learner 加权 + 边际递减）、心情↓、饱食↓。
     * 知识只由学习增长、只被玩耍主动减（doc/01 §4.1：学识为累积量，不随时间长被动衰减）。全部经 [jitter] 浮动。
     * 冷却 5s。成功 stats.study +1；跨越解锁档 → 弹「学会新招」。
     */
    fun onStudy(
        profile: PetProfile,
        now: Long,
        log: SessionLog? = null,
    ): ActionResult? {
        if (ActionRule.studyDenied(profile, now) != null) return null
        val cur = profile.attributes
        val learner = profile.personality.traits.learner
        val beforeInt = cur[INT]
        val intGain = ActionRule.diminishedGain(
            ActionRule.studyIntGain(STUDY_INT_GAIN, learner), cur[INT])
        val attrs = cur
            .set(INT, cur[INT] + jitter(intGain))
            .set(MOOD, cur[MOOD] + jitter(-STUDY_MOOD_COST))
            .set(SAT, cur[SAT] + jitter(-STUDY_SAT_COST))
        val after = profile.copy(
            attributes = attrs,
            cooldowns = profile.cooldowns.copy(studyUntil = now + ActionRule.STUDY_COOLDOWN_MS),
            milestones = bump(profile.milestones) { it.copy(study = it.study + 1) },
        )
        // 知识跨越解锁档位 → 弹「学会新招」（取本窗首次跨越的最高档；doc/01 §4.1）
        val unlockedTier = STUDY_UNLOCK_TIERS.firstOrNull { beforeInt < it && attrs[INT] >= it }
        return finish(profile, now, after, ActionHint.STUDYING,
            now + ActionRule.STUDY_COOLDOWN_MS, EventLogType.ACTION_STUDY, log, null,
            unlockTier = unlockedTier)
    }

    /**
     * 治疗：仅 SICK 可用；health +40（固定，clamp 100）并结束 SICK（→ IDLE）；耗少量饱食。
     * 全部经 [jitter] 浮动。无冷却字段（治愈即不可再点）。成功 stats.heal +1。
     */
    fun onHeal(
        profile: PetProfile,
        now: Long,
        log: SessionLog? = null,
    ): ActionResult? {
        if (ActionRule.healDenied(profile) != null) return null
        val cur = profile.attributes
        val attrs = cur
            .set(HEALTH, cur[HEALTH] + jitter(ActionRule.HEAL_GAIN))
            .set(SAT, cur[SAT] + jitter(-ActionRule.HEAL_SAT_COST))
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
        toy: ToyType? = null,
        unlockTier: Int? = null,
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
            cooldownUntil = cooldownUntil, note = note, toy = toy, unlockTier = unlockTier)
    }

    private const val PET_MOOD_GAIN = 5f

    private const val CLEAN_HYGIENE_GAIN = 35f
    private const val CLEAN_SAT_COST = 3f
    private const val CLEAN_HEALTH_THRESHOLD = 30f   // health 低于此值，清洁顺带补少量健康
    private const val CLEAN_HEALTH_BONUS = 5f

    private const val STUDY_INT_GAIN = 5f
    private const val STUDY_MOOD_COST = 5f
    private const val STUDY_SAT_COST = 3f
    /** 知识解锁档位（doc/01 §4.1：解锁更多互动/玩具/事件；阈值首版，待真机校准）。 */
    private val STUDY_UNLOCK_TIERS = listOf(30, 60, 90)

    // ── 喂食交叉副作用（用户平衡需求 / doc/01 §6.3 补充）──
    // (a) 同时增饱腹+健康的食物 → 同步降心情（健康餐让人不开心）：心情惩罚 ∝ 健康增量。
    private const val FEED_HEALTH_TO_MOOD = 0.3f
    // (b) 增心情的食物 → 同步降健康；并有一定概率降清洁（越开心越不讲究）。
    private const val FEED_MOOD_TO_HEALTH = 0.15f
    private const val FEED_MOOD_TO_HYG_PROB = 0.3f
    private const val FEED_MOOD_TO_HYG = 0.2f

    // ── 浮动 / 特质缩放 ───────────────────────────────────
    /** 每次增减的随机浮动幅度（±，相对值）；测试注入固定 rng 时退化为无浮动。 */
    internal var effectRng: () -> Float = { kotlin.random.Random.nextFloat() }
    private const val JITTER = 0.2f
    /** 特质线性缩放斜率：trait=0.5 时缩放系数为 1（默认个体数值不变，便于既有断言稳定）。 */
    private const val APPETITE_SLOPE = 0.8f   // 饱腹增益随贪吃放大
    private const val TEMPER_SLOPE = 0.4f     // 心情增益/波动随急躁放大
    private const val ACTIVITY_SLOPE = 0.4f   // 玩耍健康损耗随好动放大
    private const val AFFINITY_SLOPE = 1.0f   // 抚摸心情增益随粘人放大

    /** 随机浮动：v × (1 ± JITTER)；effectRng=0.5 时退化为 v（测试用）。 */
    private fun jitter(v: Float): Float = v * (1f + (effectRng() - 0.5f) * 2f * JITTER)

    /** 特质线性缩放：trait=0.5 → 系数 1（不动默认值），越高/低越放大/缩小。 */
    private fun traitScale(v: Float, trait: Float, slope: Float): Float =
        v * (1f + slope * (trait - 0.5f))

    /** 抚摸心情增益随粘人(affinity)缩放：affinity=0.5→系数1，越高越放大（粘人更受用），越低越冷淡。 */
    private fun affinityFactor(affinity: Float): Float =
        1f + AFFINITY_SLOPE * (affinity - 0.5f)
}
