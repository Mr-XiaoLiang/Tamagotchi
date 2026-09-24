package com.lollipop.tamagotchi.domain.engine

import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.behavior.PetState
import com.lollipop.tamagotchi.domain.model.PetProfile
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 情绪（doc/10 §3）：宠物的「心情」，与属性/FSM 状态同源派生，**不落盘**（D2 已拍板）。
 *
 * 命名约定：持久态（SLEEP/SLEEPY/SICK/HUNGRY/DIRTY/SAD/IDLE）由属性与状态推导；
 * 瞬态（PLAYFUL/HAPPY/CURIOUS/EXCITED/THINKING）由交互与事件覆盖，到点回落到持久态。
 */
enum class Mood {
    /** 冷启动初态：离线结算尚未完成，先睡着（doc/10 §3.1）。 */
    SLEEP,
    SLEEPY,
    SICK,
    HUNGRY,
    DIRTY,
    SAD,
    IDLE,

    // ── 瞬态（交互 / 事件 / 动作）────────────────
    PLAYFUL,
    HAPPY,
    CURIOUS,
    EXCITED,
    THINKING,
}

/**
 * 情绪瞬态的触发源（doc/10 §3.2 第三/四行）。
 *
 * [mood] 为覆盖用的瞬态，[holdMs] 为默认持续时长（被生理态压制时缩短，见 [MoodEngine.onEvent]）。
 */
enum class MoodEvent(
    val mood: Mood,
    val holdMs: Long,
) {
    FEED(Mood.HAPPY, 5_000L),
    PLAY(Mood.PLAYFUL, 6_000L),
    PET(Mood.HAPPY, 4_000L),
    HEAL(Mood.HAPPY, 4_000L),
    CLEAN(Mood.EXCITED, 4_000L),
    STUDY(Mood.THINKING, 6_000L),
    /** 在线随机事件：先好奇地张望（doc/03 §2.3）。 */
    RANDOM(Mood.CURIOUS, 5_000L),

    // ── 2.0 逗弄（doc/10 §3.3）：全屏 Robot 的娱乐交互 ──────────
    AMUSE_TAP(Mood.PLAYFUL, 4_000L),
    AMUSE_SPIN(Mood.EXCITED, 4_000L),
    AMUSE_BURST(Mood.HAPPY, 4_000L),
    AMUSE_HOLD(Mood.THINKING, 5_000L),
    ;

    companion object {
        /** 玩家动作 → 情绪事件（M19 起再补 AMUSE 逗弄系列）。 */
        fun of(type: ActionType): MoodEvent = when (type) {
            ActionType.FEED -> FEED
            ActionType.PLAY -> PLAY
            ActionType.PET -> PET
            ActionType.HEAL -> HEAL
            ActionType.CLEAN -> CLEAN
            ActionType.STUDY -> STUDY
        }
    }
}

/**
 * 情绪状态：持久态 [base] + 瞬态 [transient]（[until] 到期即回落）。
 *
 * [current] 是给 UI 的唯一出口：**一次 Mood 两处消费**——Robot 表情与宠物精灵形变（doc/10 D6 同源）。
 */
data class MoodState(
    val base: Mood = Mood.SLEEP,
    val transient: Mood? = null,
    /** 瞬态到期时刻（epoch millis）；无瞬态时为 0。 */
    val until: Long = 0L,
) {
    val current: Mood get() = transient ?: base
}

/**
 * 情绪内核（doc/10 §3）：纯函数、零 Android、可 JVM 单测。
 *
 * 三入口：
 * - [onBoot]：离线结算完成后，按「离线结局 + 当前属性」落初始情绪（取代初态 [Mood.SLEEP]）；
 * - [update]：属性漂移 / 瞬态到期 → 回落基线；
 * - [onEvent]：动作 / 在线事件 → 瞬态覆盖。
 *
 * **生理/持久态压制瞬态**（doc/10 §3.1）：生病与睡着时不接瞬态；饿/脏/闷时瞬态只留「一瞬」
 * （[SUPPRESSED_MS]，被摸也只开心一下就回落）。
 *
 * 「应用停止即结束」：情绪只活在调用方的组合层 State 里，不写 [PetProfile]。
 */
object MoodEngine {

    /** 低值预警阈值（与状态面板 LOW_VALUE_WARN 同源，doc/06 §2）。 */
    const val LOW_WARN: Float = 30f

    /** 健康低值：低于此值视为病恹恹（即使 fsmState 尚未推进到 SICK）。 */
    const val SICK_WARN: Float = 30f

    /** 被生理态压制时，瞬态的存活时长（「一瞬」）。 */
    const val SUPPRESSED_MS: Long = 1_500L

    /** 迎接瞬态（onBoot）的默认存活时长。 */
    const val GREET_MS: Long = 6_000L

    /** 夜间作息档起止（22:00~07:00，与 doc/01 §8.1 结算速率档一致）。 */
    private const val NIGHT_START_HOUR = 22
    private const val NIGHT_END_HOUR = 7

    /** 冷启动初态（结算完成前）：睡着。 */
    val BOOT: MoodState = MoodState(Mood.SLEEP)

    /**
     * 持久态基线：按「病 > 睡 > 饿 > 脏 > 闷 > 常态」优先级推导（doc/10 §3.1 表）。
     *
     * 睡眠除 `fsmState == SLEEPING` 外也认**夜间作息档**：夜间结算不会把 fsmState 推进到
     * SLEEPING（SettleEngine 只落 SICK/SAD/IDLE），只按作息档判定才能与 FSM 的夜间入睡同源。
     */
    fun baseline(
        profile: PetProfile,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Mood {
        val a = profile.attributes
        return when {
            profile.fsmState == PetState.SICK || a[AttributeId.HEALTH] < SICK_WARN -> Mood.SICK
            profile.fsmState == PetState.SLEEPING || isNight(now, zoneId) -> Mood.SLEEPY
            a[AttributeId.SATIATION] < LOW_WARN -> Mood.HUNGRY
            a[AttributeId.HYGIENE] < LOW_WARN -> Mood.DIRTY
            profile.fsmState == PetState.SAD || a[AttributeId.MOOD] < LOW_WARN -> Mood.SAD
            else -> Mood.IDLE
        }
    }

    /**
     * 启动落点：结算完成后调用。属性已定的负面态优先（属性说了算）；
     * 其余情况由离线结局 [SettlementSummary.endingMood] 给一段迎接瞬态（如久别重逢 → HAPPY）。
     */
    fun onBoot(
        now: Long,
        profile: PetProfile,
        summary: SettlementSummary?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): MoodState {
        val base = baseline(profile, now, zoneId)
        if (base in HARD_SUPPRESS) return MoodState(base)
        val transient = when (summary?.endingMood) {
            EndingMood.JOYFUL -> Mood.HAPPY
            EndingMood.GRUMBLING -> Mood.SAD
            EndingMood.NEEDY -> Mood.CURIOUS
            EndingMood.SLEEPY -> Mood.SLEEPY
            EndingMood.SICKLY -> null
            null -> null
        }
        if (transient == null) return MoodState(base)
        return MoodState(
            base = base,
            transient = transient,
            until = now + holdMs(base, transient, GREET_MS),
        )
    }

    /**
     * 前台推进：重算基线 + 处理瞬态到期（属性漂移导致的情绪变化也在这里落定）。
     * [now] 到 [MoodState.until] 即丢弃瞬态；基线升为病/睡时瞬态立即失效。
     */
    fun update(
        now: Long,
        profile: PetProfile,
        state: MoodState,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): MoodState {
        val base = baseline(profile, now, zoneId)
        val transient = state.transient ?: return state.copy(base = base)
        if (now >= state.until || base in HARD_SUPPRESS) {
            return MoodState(base = base)
        }
        return state.copy(base = base)
    }

    /**
     * 交互 / 事件：给一段瞬态。病与睡**不接**瞬态（只更新基线）；饿/脏/闷只留 [SUPPRESSED_MS] 一瞬。
     */
    fun onEvent(
        state: MoodState,
        event: MoodEvent,
        profile: PetProfile,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): MoodState {
        val base = baseline(profile, now, zoneId)
        if (base in HARD_SUPPRESS) return MoodState(base)
        return MoodState(
            base = base,
            transient = event.mood,
            until = now + holdMs(base, event.mood, event.holdMs),
        )
    }

    /** 瞬态实际存活时长：基线为「饿/脏/闷」时压到一瞬。 */
    private fun holdMs(base: Mood, transient: Mood, defaultMs: Long): Long {
        if (transient in HARD_SUPPRESS) return defaultMs
        return if (base in SOFT_SUPPRESS) SUPPRESSED_MS else defaultMs
    }

    /** 硬压制：病 / 睡 —— 瞬态完全不生效。 */
    private val HARD_SUPPRESS = setOf(Mood.SICK, Mood.SLEEPY, Mood.SLEEP)

    /** 软压制：饿 / 脏 / 闷 —— 瞬态只留一瞬。 */
    private val SOFT_SUPPRESS = setOf(Mood.HUNGRY, Mood.DIRTY, Mood.SAD)

    /** 夜间作息档（22:00~07:00）。 */
    private fun isNight(now: Long, zoneId: ZoneId): Boolean {
        val hour = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zoneId).hour
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
    }
}
