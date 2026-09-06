package com.lollipop.tamagotchi.presentation.screen

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.lollipop.tamagotchi.core.attribute.FoodType
import com.lollipop.tamagotchi.core.attribute.PlayType
import com.lollipop.tamagotchi.core.attribute.ToyType
import com.lollipop.tamagotchi.data.store.PetStore
import com.lollipop.tamagotchi.data.time.SystemClock
import com.lollipop.tamagotchi.domain.engine.ActionType
import com.lollipop.tamagotchi.domain.engine.PetActions
import com.lollipop.tamagotchi.domain.engine.SettleEngine
import com.lollipop.tamagotchi.domain.engine.EventEngine
import com.lollipop.tamagotchi.domain.engine.SettlementSummary
import com.lollipop.tamagotchi.domain.util.FxSeq
import com.lollipop.tamagotchi.domain.log.InMemorySessionLog
import com.lollipop.tamagotchi.domain.log.EventLog
import com.lollipop.tamagotchi.domain.log.EventLogType
import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.presentation.base.BaseActivity
import com.lollipop.tamagotchi.presentation.boot.BootLog
import com.lollipop.tamagotchi.presentation.boot.BootStage
import com.lollipop.tamagotchi.presentation.screen.setup.SetupProfileScreen
import com.lollipop.tamagotchi.presentation.screen.OnlineEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 主屏宠物 Activity（doc/00 §7 / Task.md M1.S2；M3.S2 建档闭环；M5.S2 settle 接入）。
 *
 * boot 流程：
 * 1. [showShell] Logo 壳停留一帧后注入 Compose；
 * 2. 读档：有档 → [PetScreen]（真实快照）；无档/坏档 → [SetupProfileScreen] 建档；
 * 3. 建档确认 → PetProfile.new（注册表建档初值 + 随机性格）→ [PetStore.save] → 切主屏；
 * 4. debug 构建主屏长按 → 快捷面板「重开档」清档回到建档（release 不注入回调）；
 *    同入口的精灵核对屏内置「结算 Debug：时间旅行」回拨 lastSettledAt（M5.S2）。
 *
 * 离线结算（⑤，doc/08 §1/§3）由 [EntryFlow] 编排：
 * - 冷启动：五阶揭示完成后 force 结算一次；
 * - 热恢复：距上次结算 ≥ [RESUME_SETTLE_GAP_MS] 才再结算（幂等兜底）；
 * - 时间旅行（debug）：回拨 lastSettledAt 后立即结算，无需重启即可看扣减。
 */
class PetActivity : BaseActivity() {

    override fun onBootStart() {
        showShell()
        val store = PetStore(this)
        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        lifecycleScope.launch {
            // 让 Logo 壳停留一个完整首帧，随后注入 Compose 主屏（M1 无真实加载）
            delay(200)
            injectContent(load = { Unit }) {
                EntryFlow(
                    store = store,
                    debugTools = isDebug,
                )
            }
        }
    }
}

/** 热恢复再结算的间隔阈值（doc/08 §3「≥5s 再 settle」）。 */
private const val RESUME_SETTLE_GAP_MS = 5_000L

/** 在线随机事件探测间隔（doc/03 §2.3 全局节奏 5~10min，此处前台每 30s 探一次）。 */
private const val EVENT_CHECK_MS = 30_000L

/** 传给引擎的近期日志窗口：覆盖最长单事件冷却（sneeze 30min）。 */
private const val EVENT_LOG_WINDOW_MS = 30 * 60_000L

/**
 * 有档 / 建档路由。建档确认存盘后置 profile 即切主屏；
 * profile 的创建只此一处（PetStore.save → 内存态 → UI），无二义事实源。
 *
 * settle 编排（M5.S2，doc/08 §1 第⑤阶）：入口统一走 [requestSettle]——
 * 后台 [SettleEngine.settle]（纯计算 + SP commit 同放 Default，不阻塞主线程）→
 * 主线程一次 apply = [PetStore.save] + profile state 更新（数值层/主环/状态环随之重组刷新）。
 */
@Composable
private fun EntryFlow(
    store: PetStore,
    debugTools: Boolean,
) {
    val initial = remember { store.load() }
    var profile by remember { mutableStateOf(initial) }
    val scope = rememberCoroutineScope()
    val settleEngine = remember { SettleEngine() }
    val clock = remember { SystemClock() }
    // 会话内动作流水（M6.S2：喂食/玩耍/抚摸/治疗追加；本进程生命周期，不跨冷启动）
    val sessionLog = remember { InMemorySessionLog() }
    // M9.S2 在线随机事件引擎（seed 取启动时刻，保证每会话抽签序列不同）
    val eventEngine = remember { EventEngine(seed = clock.nowMillis()) }
    // 最近一次在线随机事件（命中风波自增号；供 PetScreen 重启短演出）
    var onlineEvent by remember { mutableStateOf<OnlineEvent?>(null) }
    // M9.S2 会话回顾：本会话命中的在线事件（供状态面板「本次动态」展示）
    var onlineReview by remember { mutableStateOf<List<EventLog>>(emptyList()) }
    // 最近一次动作执行事件（成功才置位；[fxSeq] 单调自增、动作与在线事件共用同一序号空间，
    // 供 PetScreen/PetLivingSprite 按 nonce 去重时全局唯一，避免两类事件序号撞车导致某次演出被误吞）
    var lastActionEvent by remember { mutableStateOf<ActionEvent?>(null) }
    // 单一序号源：动作事件与在线事件共用同一 FxSeq 实例，保证全局 nonce 唯一（回归点：曾因两套计数撞车漏播演出）
    val fxSeq = remember { FxSeq() }
    // 上次尝试结算的墙钟（进程内）：用于热恢复节流；冷启动 force 结算不受限。
    var lastSettleWall by remember { mutableLongStateOf(0L) }
    // 最近一次结算摘要（含离线时间线 + 结局基调），供 PetScreen 迎接气泡 / 回放（M7.S2）。
    var lastSettleSummary by remember { mutableStateOf<SettlementSummary?>(null) }

    suspend fun runSettle(tag: String, force: Boolean) {
        val cur = profile ?: return
        val now = clock.nowMillis()
        if (!force && lastSettleWall != 0L && now - lastSettleWall < RESUME_SETTLE_GAP_MS) {
            BootLog.s(BootStage.Settle, "$tag：距上次结算 < ${RESUME_SETTLE_GAP_MS}ms，跳过")
            return
        }
        lastSettleWall = now
        BootLog.s(
            BootStage.Settle,
            "$tag 开始：now=$now lastSettledAt=${cur.lastSettledAt} 窗口=${now - cur.lastSettledAt}ms",
        )
        val outcome = withContext(Dispatchers.Default) {
            val r = settleEngine.settle(now, cur)
            if (r.changed) store.save(r.profile)
            r
        }
        if (outcome.changed) {
            profile = outcome.profile
            sessionLog.openWith(outcome.summary) // M7.S2 开场段回填
            lastSettleSummary = outcome.summary
            val d = outcome.summary.totalDelta
            val attrSummary = d.perAttribute.entries.joinToString(" ") { "${it.key}=${it.value}" }
            BootLog.s(
                BootStage.Settle,
                "$tag apply：elapsedMs=${outcome.summary.elapsedMs} 状态→${outcome.profile.fsmState} Δ{ $attrSummary }",
            )
        } else {
            BootLog.s(BootStage.Settle, "$tag noOp：窗口 ≤ 0，快照不变")
        }
    }

    fun requestSettle(tag: String, force: Boolean = false) {
        scope.launch { runSettle(tag, force) }
    }

    /**
     * M6.S2 动作执行：domain [PetActions] 纯函数 → Default 存档 → 主线程 apply profile
     * + 上抛 [ActionEvent]（成功才置位；被 UI 判定的冷却/属性满/熟睡不会走到这里）。
     */
    fun performAction(type: ActionType, food: FoodType?, toy: ToyType? = null) {
        scope.launch {
            val cur = profile ?: return@launch
            val now = clock.nowMillis()
            val result = withContext(Dispatchers.Default) {
                val r = when (type) {
                    ActionType.FEED -> PetActions.onFeed(cur, now, food ?: return@withContext null, sessionLog)
                    ActionType.PLAY -> PetActions.onPlay(
                        cur, now,
                        style = PlayType.entries[(now / 1000).toInt().mod(PlayType.entries.size)],
                        log = sessionLog,
                        toy = toy,
                    )
                    ActionType.PET -> PetActions.onPet(cur, now, sessionLog)
                    ActionType.HEAL -> PetActions.onHeal(cur, now, sessionLog)
                    ActionType.CLEAN -> PetActions.onClean(cur, now, sessionLog)
                    ActionType.STUDY -> PetActions.onStudy(cur, now, sessionLog)
                }
                if (r != null) store.save(r.profile)
                r
            } ?: return@launch
            profile = result.profile
            lastActionEvent = ActionEvent(id = fxSeq.next(), result = result)
        }
    }

    /** Debug 时间旅行（M5.S2）：把 lastSettledAt 拨回 [hours] 前并立即结算（幂等，可重复点）。 */
    fun timeTravelBack(hours: Long) {
        scope.launch {
            val cur = profile ?: return@launch
            val now = clock.nowMillis()
            val shifted = cur.copy(lastSettledAt = now - hours * 3_600_000L)
            store.save(shifted)
            profile = shifted
            BootLog.s(BootStage.Settle, "Debug 时间旅行：lastSettledAt 拨回 ${hours}h")
            runSettle("时间旅行结算", force = true)
        }
    }

    // 热恢复结算（doc/08 §3）：仅前台化触发；距上次 < 阈值由 runSettle 内节流拦截。
    // 首组合时 lifecycle 已处于 RESUMED，lastActive 初始 true → 冷启动不在此重复结算
    // （冷启动由 PetScreen 五阶 reveal 完成回调 force 触发）。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var lastActive = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    if (!lastActive) requestSettle("热恢复")
                    lastActive = true
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> lastActive = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // M9.S2 在线节奏：前台每 [EVENT_CHECK_MS] 探一次；全局间隔 / 单事件冷却 / 日上限由引擎保证不连刷
    // （doc/03 §2.3）。命中 → 应用数值微扰 + 写 RANDOM_EVENT 日志 + 上抛 [OnlineEvent] 供 PetScreen 演出。
    LaunchedEffect(Unit) {
        while (true) {
            delay(EVENT_CHECK_MS)
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) continue
            val cur = profile ?: continue
            val now = clock.nowMillis()
            val recent = sessionLog.liveLogsSince(now - EVENT_LOG_WINDOW_MS)
            val triggered = eventEngine.trigger(
                EventEngine.EventContext(cur, now, recent),
                sessionLog,
            ) ?: continue
            withContext(Dispatchers.Default) { store.save(triggered.profile) }
            profile = triggered.profile
            onlineEvent = OnlineEvent(nonce = fxSeq.next(), petEvent = triggered.event)
            onlineReview = sessionLog.liveLogsSince(0).filter { it.type == EventLogType.RANDOM_EVENT }
        }
    }

    val current = profile
    if (current == null) {
        SetupProfileScreen(
            onConfirm = { pet, personality ->
                val created = PetProfile.new(
                    petId = pet.id,
                    petName = pet.displayName,
                    personality = personality,
                    now = System.currentTimeMillis(),
                )
                store.save(created)
                profile = created
            },
        )
    } else {
        PetScreen(
            profile = current,
            settleSummary = lastSettleSummary,
            onSettleReady = { requestSettle("冷启动五阶", force = true) },
            onTimeTravel = if (debugTools) { { hours -> timeTravelBack(hours) } } else null,
            onResetProfile = if (debugTools) {
                {
                    store.delete()
                    profile = null
                }
            } else {
                null
            },
            onAction = { type, food, toy -> performAction(type, food, toy) },
            actionEvent = lastActionEvent,
            onlineEvent = onlineEvent,
            onlineReview = onlineReview,
        )
    }
}
