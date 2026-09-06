package com.lollipop.tamagotchi.domain.engine

import com.lollipop.tamagotchi.core.attribute.AttributeDelta
import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.AttributeMap
import com.lollipop.tamagotchi.core.attribute.AttributeRegistry
import com.lollipop.tamagotchi.core.behavior.PetState
import com.lollipop.tamagotchi.domain.log.EventLog
import com.lollipop.tamagotchi.domain.log.EventLogType
import com.lollipop.tamagotchi.domain.log.SessionLog
import com.lollipop.tamagotchi.domain.model.PetProfile
import java.time.ZoneId
import kotlin.random.Random

/**
 * 在线随机事件引擎（doc/03 §1/§2/§4，Task M9.S1）。
 *
 * 纯 domain、零 UI 依赖：输入 [EventContext]，按「候选过滤 → 权重抽签」产出 [PetEvent]。
 * 与离线时间线（[OfflineTimelineBuilder]）共用同一套事件表字段（condition/weight/effect/cooldown），
 * 本里程碑只落地**在线单发**角色（doc/03 §4 在线子集）。
 *
 * **防刷纪律（doc/03 §2.3）**：
 * - 全局在线节奏：距上次任意在线事件不足 [GLOBAL_MIN_INTERVAL_MS] 不触发；
 * - 单事件冷却 + 日触发上限（[DAILY_CAP]），防止同一事件连刷；
 * - 全局概率低（低频调味），候选按 weight × 时段/性格权重 抽签。
 *
 * **事件不改世界观**：只产出数值微扰 + 表现提示（[PetEvent.stateHint]）。
 * 其中 EXCITED/EATING 等瞬态提示**不落持久快照**；IDLE/WALKING/SLEEPING/SAD/SICK 等
 * 真实 FSM 状态提示会被 [applyEffect] 写回 fsmState（让 FSM 从该态继续，如 yawn→SLEEPING）。
 */
class EventEngine(seed: Long) {

    private val random = Random(seed)

    /**
     * 在线事件触发上下文（doc/03 §2.2 / §6）。
     * @param recentLogs 本会话近期日志（含 ts 升序），用于冷却/日上限判定；
     *   调用方应传入 `sessionLog.liveLogsSince(now - 30min)` 之类窗口（覆盖最长单事件冷却）。
     */
    data class EventContext(
        val snapshot: PetProfile,
        val now: Long,
        val recentLogs: List<EventLog> = emptyList(),
        val zoneId: ZoneId = ZoneId.systemDefault(),
    )

    /** 单个事件的生效结果（数值微扰 + 表现提示）。 */
    data class EventEffect(
        val attrDelta: AttributeDelta,
        /** 表现层瞬态/状态提示；瞬态（EXCITED/EATING）不落持久快照，真实 FSM 态才写回。 */
        val stateHint: PetState?,
        val bubbleId: String,
    )

    /** 命中事件输出（写入会话日志的 refId / note 来源）。 */
    data class PetEvent(
        val id: String,
        val bubbleId: String,
        val stateHint: PetState?,
        val attrDelta: AttributeDelta,
        val logType: EventLogType,
    )

    /** 触发结果：事件 + 应用数值微扰后的新快照（fsmState 仅在真实 FSM 态提示下改变）。 */
    data class TriggeredEvent(
        val event: PetEvent,
        val profile: PetProfile,
    )

    /** 在线事件定义（doc/03 §3 / §4 初版库；与离线事件表同字段）。 */
    data class RandomEventDef(
        val id: String,
        val weight: Int,
        val cooldownMs: Long,
        val condition: (EventContext) -> Boolean,
        /** 时段 / 性格权重修正（doc/03 §2.2），默认 1。 */
        val timeSlotWeight: (EventContext) -> Float = { 1f },
        val effect: (EventContext) -> EventEffect,
    )

    // ── 候选过滤 + 权重抽签 ──────────────────────────────────

    /**
     * 候选过滤：条件成立 + 单事件冷却通过 + 日上限未达 + 全局节奏允许。
     * 仅过滤，不抽签——供 [rollEvent] 与测试复用。
     */
    internal fun candidates(context: EventContext): List<RandomEventDef> {
        // 全局在线节奏：距上次任意在线事件不足间隔 → 无候选
        if (!globalIntervalElapsed(context)) return emptyList()
        val dayStart = context.now - DAY_MS
        return EVENTS.filter { def ->
            def.condition(context) &&
                cooldownPassed(def, context) &&
                dailyUnderCap(def, context, dayStart)
        }
    }

    /** 候选 id 列表（测试便捷）。 */
    internal fun candidateIds(context: EventContext): List<String> =
        candidates(context).map { it.id }

    /**
     * 按 [EventContext] 抽签返回命中事件 id；无候选返回 null。
     * 仅依赖注入 rng（由 seed 决定）→ 同 seed + 同 context 可复现。
     */
    fun rollEvent(context: EventContext): String? {
        val cands = candidates(context)
        if (cands.isEmpty()) return null
        val weights = cands.map { (it.weight * it.timeSlotWeight(context)).coerceAtLeast(0f) }
        return pick(cands, weights).id
    }

    /**
     * 命中后产出结果：应用数值微扰（clamp 由 [AttributeMap] 保证）+ 可选写日志。
     * 无事件返回 null。
     */
    fun trigger(context: EventContext, log: SessionLog? = null): TriggeredEvent? {
        val id = rollEvent(context) ?: return null
        val def = EVENTS.first { it.id == id }
        val eff = def.effect(context)
        val event = PetEvent(
            id = def.id,
            bubbleId = eff.bubbleId,
            stateHint = eff.stateHint,
            attrDelta = eff.attrDelta,
            logType = EventLogType.RANDOM_EVENT,
        )
        val profile = applyEffect(context.snapshot, event)
        log?.append(
            EventLog(
                ts = context.now,
                type = EventLogType.RANDOM_EVENT,
                refId = event.id,
                before = context.snapshot.attributes,
                delta = event.attrDelta,
                state = context.snapshot.fsmState,
                note = event.bubbleId,
            ),
        )
        return TriggeredEvent(event, profile)
    }

    /** 应用事件数值微扰；真实 FSM 态提示写回 fsmState，瞬态提示（EXCITED/EATING）不落持久快照。 */
    fun applyEffect(profile: PetProfile, event: PetEvent): PetProfile {
        // attrDelta 是「变化量」(+/-)，需叠加到当前值（AttributeMap.apply 视为绝对值，不能直接用）。
        val updates = AttributeRegistry.all.associate { meta ->
            meta.id to (profile.attributes[meta.id] + event.attrDelta[meta.id])
        }
        val attrs = profile.attributes.apply(updates)
        val nextState = if (event.stateHint != null && event.stateHint in PERSISTENT_STATES) {
            event.stateHint
        } else {
            profile.fsmState
        }
        return profile.copy(attributes = attrs, fsmState = nextState)
    }

    // ── 内部工具 ──────────────────────────────────────────────

    private fun globalIntervalElapsed(context: EventContext): Boolean {
        val lastOnline = context.recentLogs
            .filter { it.type == EventLogType.RANDOM_EVENT && it.ts <= context.now }
            .maxByOrNull { it.ts }?.ts
        if (lastOnline == null) return true // 无在线事件历史 → 允许触发
        return context.now - lastOnline >= GLOBAL_MIN_INTERVAL_MS
    }

    private fun cooldownPassed(def: RandomEventDef, context: EventContext): Boolean {
        val last = context.recentLogs
            .filter { it.type == EventLogType.RANDOM_EVENT && it.refId == def.id && it.ts <= context.now }
            .maxByOrNull { it.ts }?.ts
        if (last == null) return true // 从未触发过 → 冷却通过
        return context.now - last >= def.cooldownMs
    }

    private fun dailyUnderCap(def: RandomEventDef, context: EventContext, dayStart: Long): Boolean {
        val count = context.recentLogs.count {
            it.type == EventLogType.RANDOM_EVENT &&
                it.refId == def.id &&
                it.ts in dayStart..context.now
        }
        return count < DAILY_CAP
    }

    private fun pick(candidates: List<RandomEventDef>, weights: List<Float>): RandomEventDef {
        val total = weights.sum()
        if (total <= 0f) return candidates.first()
        var r = random.nextFloat() * total
        for (i in candidates.indices) {
            r -= weights[i]
            if (r <= 0f) return candidates[i]
        }
        return candidates.last()
    }

    private companion object {
        const val MIN_MS: Long = 60_000L
        const val DAY_MS: Long = 24 * 60 * MIN_MS

        /** 两次在线事件最小间隔（doc/03 §2.3：5~10min，取下限）。 */
        const val GLOBAL_MIN_INTERVAL_MS: Long = 5 * MIN_MS

        /** 单事件日触发上限（doc/03 §2.3）。 */
        const val DAILY_CAP: Int = 3

        /** 真实 FSM 状态（落持久快照）；其余为瞬态提示不落盘。 */
        val PERSISTENT_STATES: Set<PetState> = setOf(
            PetState.IDLE, PetState.WALKING, PetState.SLEEPING, PetState.SAD, PetState.SICK,
        )

        val IDLE_OR_WALKING: Set<PetState> = setOf(PetState.IDLE, PetState.WALKING)
    }

    // ── 在线事件库（doc/03 §4 初版库在线子集）──────────────────

    private fun isNight(ctx: EventContext): Boolean = DecayModel.isNight(ctx.now, ctx.zoneId)

    private val EVENTS: List<RandomEventDef> = listOf(
        RandomEventDef(
            id = "found_food",
            weight = 8,
            cooldownMs = 10 * MIN_MS,
            condition = { ctx ->
                ctx.snapshot.fsmState in IDLE_OR_WALKING &&
                    ctx.snapshot.attributes[AttributeId.SATIATION] < 70f
            },
            effect = {
                EventEffect(
                    attrDelta = AttributeDelta.of(AttributeId.SATIATION to +5f),
                    stateHint = PetState.EXCITED,
                    bubbleId = "event.found_food",
                )
            },
        ),
        RandomEventDef(
            id = "sneeze",
            weight = 4,
            cooldownMs = 30 * MIN_MS,
            condition = { ctx -> ctx.snapshot.attributes[AttributeId.HEALTH] < 60f },
            effect = {
                EventEffect(
                    attrDelta = AttributeDelta.EMPTY,
                    stateHint = null,
                    bubbleId = "event.sneeze",
                )
            },
        ),
        RandomEventDef(
            id = "curious_walk",
            weight = 5,
            cooldownMs = 15 * MIN_MS,
            condition = { ctx ->
                ctx.snapshot.fsmState in IDLE_OR_WALKING &&
                    !isNight(ctx) &&
                    ctx.snapshot.personality.traits.activity > 0.6f
            },
            // 好动特质越高，越爱自己溜达（doc/03 §5 性格加权）
            timeSlotWeight = { ctx -> 0.5f + ctx.snapshot.personality.traits.activity },
            effect = {
                EventEffect(
                    attrDelta = AttributeDelta.EMPTY,
                    stateHint = PetState.WALKING,
                    bubbleId = "event.curious_walk",
                )
            },
        ),
        RandomEventDef(
            id = "yawn",
            weight = 6,
            cooldownMs = 20 * MIN_MS,
            condition = { ctx -> isNight(ctx) && ctx.snapshot.fsmState != PetState.SLEEPING },
            effect = {
                EventEffect(
                    attrDelta = AttributeDelta.EMPTY,
                    stateHint = PetState.SLEEPING,
                    bubbleId = "event.yawn",
                )
            },
        ),
        RandomEventDef(
            id = "beg_food",
            weight = 6,
            cooldownMs = 8 * MIN_MS,
            condition = { ctx -> ctx.snapshot.attributes[AttributeId.SATIATION] < 25f },
            effect = {
                EventEffect(
                    attrDelta = AttributeDelta.EMPTY,
                    stateHint = null,
                    bubbleId = "event.beg_food",
                )
            },
        ),
        RandomEventDef(
            id = "dream",
            weight = 5,
            cooldownMs = 10 * MIN_MS,
            condition = { ctx -> ctx.snapshot.fsmState == PetState.SLEEPING },
            effect = {
                EventEffect(
                    attrDelta = AttributeDelta.EMPTY,
                    stateHint = null,
                    bubbleId = "event.dream",
                )
            },
        ),
        RandomEventDef(
            id = "treat_hunt",
            weight = 4,
            cooldownMs = 12 * MIN_MS,
            condition = { ctx ->
                ctx.snapshot.personality.traits.appetite > 0.5f &&
                    ctx.snapshot.attributes[AttributeId.SATIATION] < 50f
            },
            effect = {
                EventEffect(
                    attrDelta = AttributeDelta.of(AttributeId.SATIATION to +8f),
                    stateHint = null,
                    bubbleId = "event.treat_hunt",
                )
            },
        ),
        // 注：doc/03 §4 `mood_boost`（抚摸后 5min mood 恢复 +30%、限一次）依赖跨事件内存，
        // 超出本 S1 在线单发范围，留 M9.S2 / 后续打磨再接入。
    )
}
