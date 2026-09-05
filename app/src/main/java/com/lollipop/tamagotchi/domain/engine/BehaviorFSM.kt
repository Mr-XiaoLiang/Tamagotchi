package com.lollipop.tamagotchi.domain.engine

import com.lollipop.tamagotchi.core.behavior.PetState
import com.lollipop.tamagotchi.core.motion.Direction
import com.lollipop.tamagotchi.core.motion.NormalizedPos
import com.lollipop.tamagotchi.domain.model.PetProfile
import java.time.Instant
import java.time.ZoneId
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 行为 tick 输出契约（Task M4.S1，doc/02 §1.1/§2/§5 草案简化）：
 * 一次 [BehaviorFSM.step] 的「下一表现」。渲染层（M4.S2 起）据此消费：
 * - [state]：下一状态（自决策 IDLE/WALKING/SLEEPING，锁态 SICK/SAD 由上层写入快照、本机保持）；
 * - [dir]/[frame]：切表取帧用（Direction.ordinal=行、frame=列，见 doc/02 §2.3）；
 * - [position]：下一归一化坐标（恒在可活动圆 R=1 内，见 doc/02 §2.1；
 *   模型不出屏由渲染映射扣除本体半径保证）。
 * M6 动作态需要的 bubble/emotion/delta 在动作层扩展，本契约不预开洞。
 */
data class FSMResult(
    val state: PetState,
    val dir: Direction,
    val frame: Int,
    val position: NormalizedPos,
)

/**
 * 宠物自主行为状态机（doc/02 §1/§2/§3，Task M4.S1）。
 *
 * 纯 domain、零 UI 依赖：输入 (now, 快照)，输出 [FSMResult]。
 *
 * - **自决策范围 = IDLE ↔ WALKING（含昼夜 SLEEPING）**；EATING/EXCITED 由动作（M6）触发，
 *   本版快照若已处该状态则保持原样，不做计时迁移（Task M4.S1 注意）。
 * - **SICK / SAD 由 settle（M5）判定后写入快照**：SICK 最高优先级（夜间也不入睡），本机保持；
 *   SAD 白天保持、夜间让位于睡眠，醒来回到入睡前语义（SAD→SAD，其余→IDLE）。
 * - **作息**：22:00~07:00（本地时区）倾向入睡；醒来回 IDLE/SAD 并重置自决策计时。
 *   深夜互动收益 = 0 属 settle/动作层（M5/M6），本机不判定。
 * - **行走（防穿模，doc/02 §2.2）**：v = 0.03/tick × (0.6+0.8×activity)；
 *   越出可活动圆（x²+y²>R²，R=[WALK_RADIUS]=1，全屏）→ clamp 贴圆 + 随机换向（4 向中选半径不增者）；
 *   （「模型不走出屏幕」由渲染映射扣模型半径与呼吸余量实现，见 PetRenderer。）
 *   触墙换向 ≥ [MAX_WALL_TURNS] 次 → 回 IDLE。行走帧序 0→3 每 tick +1。
 * - **随机决策**：IDLE 2~4s → 随机新方向开始 WALKING；
 *   WALKING 3~5s × (1.6-activity) → 回 IDLE（activity 高→短、低→长，doc/02 §4.4）。
 * - **可复现**：以 seed 注入随机源，同 seed + 同 (now, 快照) 序列 → 结果序列一致。
 *   tick 推进用 now 增量（每 step 至多累计 [DEFAULT_TICK_MS]），真机主循环 ~250ms/步即 4FPS。
 */
class BehaviorFSM(
    seed: Long,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val random = Random(seed)

    /** 上次 now（心跳计时起点；null = 首个 tick 不计时差）。 */
    private var lastNow: Long? = null

    /** 快照对齐标记：false 表示尚未按快照初始化自决策计时。 */
    private var initialized = false

    /** 当前自决策状态（IDLE/WALKING/SLEEPING 决策面内）。 */
    private var selfState: PetState = PetState.IDLE

    /** 自决策状态已推进毫秒（cap 于 tick 上限，防跳帧过量计时）。 */
    private var elapsedMs: Long = 0L

    /** 本段状态随机决策的目标毫秒数。 */
    private var phaseMs: Long = 0L

    /** 本次 WALKING 触墙换向计数。 */
    private var wallTurns: Int = 0

    /** 入睡前语义：醒来回 SAD 或 IDLE。 */
    private var wakeTo: PetState = PetState.IDLE

    /**
     * 推进一个行为 tick。
     *
     * @param now 当前墙钟 epoch millis（本地作息按 [zoneId] 折算）。
     * @param snapshot 领域快照：fsmState（含 settle 写入的 SICK/SAD）、position、personality.traits。
     * @param tickMs 心跳上限：本次计时推进 = min(now-上次, tickMs)，保证单步不跨多帧。
     */
    fun step(
        now: Long,
        snapshot: PetProfile,
        tickMs: Long = DEFAULT_TICK_MS,
    ): FSMResult {
        val raw = snapshot.position
        val pos = clampToCircle(NormalizedPos(raw.x, raw.y))
        val cur = snapshot.fsmState

        // ① SICK 最高优先级（doc/02 §1.2）：由 settle/治愈负责进出，本机保持（夜间也不入睡）。
        if (cur == PetState.SICK) {
            return FSMResult(PetState.SICK, raw.dir, 0, pos)
        }

        // ② 作息（doc/02 §3）：夜间倾向入睡 / 醒来（离开 22:00~07:00 窗）。
        val night = isNight(now)
        if (night) {
            if (cur != PetState.SLEEPING) {
                wakeTo = if (cur == PetState.SAD) PetState.SAD else PetState.IDLE
            }
            beginPhase(PetState.SLEEPING)
            return FSMResult(PetState.SLEEPING, raw.dir, 0, pos)
        }
        if (cur == PetState.SLEEPING) {
            val target = wakeTo
            wakeTo = PetState.IDLE
            beginPhase(PetState.IDLE)
            return FSMResult(target, raw.dir, 0, pos)
        }

        // ③ 锁态保持：SAD（settle 恢复，M5）；EATING/EXCITED（动作引擎恢复，M6）。
        if (cur == PetState.SAD || cur == PetState.EATING || cur == PetState.EXCITED) {
            return FSMResult(cur, raw.dir, 0, pos)
        }

        // ④ 自决策面：IDLE/WALKING。
        advanceHeartbeat(now, tickMs)
        if (!initialized || selfState != cur) {
            beginPhase(if (cur == PetState.WALKING) PetState.WALKING else PetState.IDLE)
            initialized = true
        }
        return when (selfState) {
            PetState.WALKING -> walkTick(snapshot)
            else -> idleTick(snapshot)
        }
    }

    // ── 自决策实现 ─────────────────────────────────────────

    private fun idleTick(snapshot: PetProfile): FSMResult {
        val raw = snapshot.position
        val pos = clampToCircle(NormalizedPos(raw.x, raw.y))
        if (elapsedMs < phaseMs) {
            // IDLE：原地站立（呼吸帧归零，呼吸动效由渲染层叠加）。
            return FSMResult(PetState.IDLE, raw.dir, 0, pos)
        }
        // IDLE 2~4s 到点 → 随机新方向开走。
        selfState = PetState.WALKING
        phaseMs = walkDurationMs(snapshot)
        elapsedMs = 0L
        wallTurns = 0
        val dir = Direction.of(random.nextInt(4))
        return FSMResult(PetState.WALKING, dir, 0, pos)
    }

    private fun walkTick(snapshot: PetProfile): FSMResult {
        val raw = snapshot.position
        val pos = clampToCircle(NormalizedPos(raw.x, raw.y))
        if (elapsedMs >= phaseMs) {
            // 走满 3~5s×(1.6-activity) → 停。
            beginPhase(PetState.IDLE)
            return FSMResult(PetState.IDLE, raw.dir, 0, pos)
        }
        val activity = snapshot.personality.traits.activity
        val v = BASE_SPEED * (0.6f + 0.8f * activity)
        val dir = raw.dir
        val next = pos.step(dir.dx * v, dir.dy * v)
        if (next.radiusSquared() <= RADIUS_SQ + EPSILON) {
            // 合法落点：行走帧 0→3 循环（每 tick +1）。
            return FSMResult(PetState.WALKING, dir, (raw.frame + 1) and 3, next)
        }
        // 越出活动圆：clamp 贴边 + 随机换向（防穿模，doc/02 §2.2）。
        wallTurns++
        val bounded = clampToCircle(next)
        if (wallTurns >= MAX_WALL_TURNS) {
            // 触墙换向 N 次后回 IDLE。
            beginPhase(PetState.IDLE)
            return FSMResult(PetState.IDLE, dir, 0, bounded)
        }
        val turnDir = pickSafeDirection(bounded)
        return FSMResult(PetState.WALKING, turnDir, raw.frame, bounded)
    }

    // ── 工具 ───────────────────────────────────────────────

    /** 开始一段 [state] 的计时：清空累计并掷随机时长。 */
    private fun beginPhase(state: PetState) {
        selfState = state
        elapsedMs = 0L
        wallTurns = 0
        phaseMs = when (state) {
            PetState.IDLE -> randomMs(IDLE_MIN_MS, IDLE_MAX_MS)
            PetState.WALKING -> randomMs(WALK_MIN_MS, WALK_MAX_MS)
            else -> 0L // SLEEPING 等由作息管，不走随机时长
        }
    }

    private fun walkDurationMs(snapshot: PetProfile): Long {
        val activity = snapshot.personality.traits.activity
        val factor = (1.6f - activity).coerceIn(0.6f, 1.6f)
        return (randomMs(WALK_MIN_MS, WALK_MAX_MS) * factor).toLong().coerceAtLeast(500L)
    }

    /** 从 [fromMs, toMs] 闭区间随机取时长。 */
    private fun randomMs(fromMs: Long, toMs: Long): Long =
        random.nextLong(fromMs, toMs + 1)

    /** 心跳：cap 增量，保证单步不过量计时（防后台恢复跨帧）。 */
    private fun advanceHeartbeat(now: Long, tickMs: Long) {
        val prev = lastNow
        lastNow = now
        if (prev != null && now > prev) {
            elapsedMs += (now - prev).coerceAtMost(tickMs)
        }
    }

    /** 作息判定：本地时区 22:00~07:00 为夜间。 */
    private fun isNight(now: Long): Boolean {
        val hour = Instant.ofEpochMilli(now).atZone(zoneId).hour
        return hour >= 22 || hour < 7
    }

    /** 归一化坐标贴圆（x²+y²>R² 时等比缩回 R）。 */
    private fun clampToCircle(p: NormalizedPos): NormalizedPos {
        val r2 = p.radiusSquared()
        if (r2 <= RADIUS_SQ) return p
        val s = WALK_RADIUS / sqrt(r2)
        return NormalizedPos(p.x * s, p.y * s)
    }

    /** 换向：从 4 方向中随机选「下一步半径不增」者；贴边内圈无候选时回退 4 向。 */
    private fun pickSafeDirection(pos: NormalizedPos): Direction {
        val safe = Direction.entries.filter { d ->
            val p = NormalizedPos(pos.x + d.dx * BASE_SPEED, pos.y + d.dy * BASE_SPEED)
            p.radiusSquared() <= pos.radiusSquared() + EPSILON
        }
        val pool = safe.ifEmpty { Direction.entries.toList() }
        return pool[random.nextInt(pool.size)]
    }

    companion object {
        /** 默认心跳 250ms = 4FPS 一帧（doc/02 §2.2）。 */
        const val DEFAULT_TICK_MS: Long = 250L

        /** 宠物中心可达最大半径 R=1（全屏；模型留白由渲染映射扣除，doc/02 §2.1）。 */
        const val WALK_RADIUS: Float = Direction.WALK_RADIUS

        /** 行走 base 速度（单位 tick，归一化坐标；doc/02 §2.2）。 */
        const val BASE_SPEED: Float = 0.03f

        /** 触墙换向 N 次后回 IDLE（doc/02 §1.2）。 */
        const val MAX_WALL_TURNS: Int = 3

        private const val RADIUS_SQ: Float = WALK_RADIUS * WALK_RADIUS
        private const val EPSILON: Float = 1e-4f
        private const val IDLE_MIN_MS: Long = 2_000L
        private const val IDLE_MAX_MS: Long = 4_000L
        private const val WALK_MIN_MS: Long = 3_000L
        private const val WALK_MAX_MS: Long = 5_000L
    }
}
