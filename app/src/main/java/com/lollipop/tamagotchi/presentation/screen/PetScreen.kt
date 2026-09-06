package com.lollipop.tamagotchi.presentation.screen

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.lollipop.tamagotchi.presentation.icon.tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.delay
import com.lollipop.tamagotchi.domain.plugin.PluginExecutor
import com.lollipop.tamagotchi.domain.plugin.PluginNotifier
import com.lollipop.tamagotchi.domain.plugin.PluginPrefs
import com.lollipop.tamagotchi.domain.plugin.PluginSpec
import com.lollipop.tamagotchi.domain.plugin.PluginTriggerType
import com.lollipop.tamagotchi.data.plugin.AndroidLocalActionRunner
import com.lollipop.tamagotchi.data.plugin.AndroidPluginResolver
import com.lollipop.tamagotchi.data.plugin.PluginPrefsStore
import com.lollipop.tamagotchi.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.graphics.ImageBitmap

import android.content.Intent
import com.lollipop.tamagotchi.presentation.component.AppIcon
import com.lollipop.tamagotchi.presentation.component.toImageBitmap
import com.lollipop.tamagotchi.domain.plugin.AppEntry
import com.lollipop.tamagotchi.data.plugin.AppLister
import kotlin.math.max
import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.AttributeRegistry
import com.lollipop.tamagotchi.data.sprite.SpriteRepository
import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.presentation.boot.BootStage
import com.lollipop.tamagotchi.presentation.boot.ShellBridge
import com.lollipop.tamagotchi.presentation.render.PetLivingSprite
import com.lollipop.tamagotchi.presentation.component.ColorDot
import com.lollipop.tamagotchi.presentation.component.PillItem
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextAlign
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.RoundList
import com.lollipop.tamagotchi.presentation.component.RoundListSpacer
import com.lollipop.tamagotchi.presentation.component.RoundSheet
import com.lollipop.tamagotchi.presentation.component.roundEdgeFade
import com.lollipop.tamagotchi.presentation.component.roundSafeInset
import com.lollipop.tamagotchi.presentation.component.AdaptTokens
import com.lollipop.tamagotchi.presentation.component.screenMetrics
import com.lollipop.tamagotchi.presentation.component.SheetEdge
import com.lollipop.tamagotchi.presentation.theme.BlackGlowBackground
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import com.lollipop.tamagotchi.presentation.screen.debug.DebugSpeciesGridScreen
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.lollipop.tamagotchi.core.attribute.FoodFlavor
import com.lollipop.tamagotchi.core.attribute.FoodType
import com.lollipop.tamagotchi.core.behavior.PetState
import com.lollipop.tamagotchi.domain.engine.ActionDenied
import com.lollipop.tamagotchi.domain.engine.ActionHint
import com.lollipop.tamagotchi.core.attribute.ToyType
import com.lollipop.tamagotchi.core.attribute.ToyUnlock
import com.lollipop.tamagotchi.domain.toy.ToyRules
import com.lollipop.tamagotchi.domain.engine.ActionResult
import com.lollipop.tamagotchi.domain.engine.ActionRule
import com.lollipop.tamagotchi.domain.engine.ActionType
import com.lollipop.tamagotchi.domain.engine.EventEngine
import com.lollipop.tamagotchi.domain.engine.OfflineEvent
import com.lollipop.tamagotchi.domain.engine.SettlementSummary
import com.lollipop.tamagotchi.domain.log.EventLog
import com.lollipop.tamagotchi.presentation.render.FxFloat
import com.lollipop.tamagotchi.presentation.render.FxKind
import com.lollipop.tamagotchi.presentation.render.PetFx
import com.lollipop.tamagotchi.presentation.render.toEmotion
import com.lollipop.tamagotchi.presentation.ui.MiniProgressRing
import com.lollipop.tamagotchi.presentation.ui.RingProgressBar
import com.lollipop.tamagotchi.presentation.ui.drawAttributeGlyph
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 三 overlay：状态（顶）/ 操作（底）/ 快捷功能（右）。一次只开一种（互斥）。 */
private enum class Panel { Status, Action, Quick }

/** 三向把手自动隐藏时长：从箭头自身显现时刻起计，到时淡出（doc/06 §1，避免常驻观感）。 */
private const val HANDLE_HINT_MS = 10_000L

/**
 * 三向「边缘热区带」带深：贴屏缘的常驻不可见窄带，同时是「点按展开」与「跟手拖拽」的
 * 起点判定区（见 [sheetDragZone]）。带深不过大，避免侵占中央宠物活动区（doc/06 §1/§5）。
 */
private val EdgeBand = AdaptTokens.EDGE_BAND

/** 状态行/图标低值预警阈值（<30，doc/06 §2/§3.1）。 */
private const val LOW_VALUE_WARN = 30f

/**
 * 一次动作执行事件（EntryFlow 动作成功 → 上抛结果供 UI 触发短演出/收面板）。
 * [id] 单调自增：即使连续两次动作内容完全一致，也能驱动 [PetScreen] 重启演出。
 */
data class ActionEvent(
    val id: Long,
    val result: ActionResult,
)

/** 在线随机事件上抛（M9.S2）：命中风波自增号，与 [ActionEvent] 同机制驱动短演出重启。 */
data class OnlineEvent(
    val nonce: Long,
    val petEvent: EventEngine.PetEvent,
)

/** 操作面板页内子级：动作列表 ⇄ 食物选择（投喂子页）⇄ 玩具选择（M13 子页）。 */
private enum class ActionPage { Actions, Feed, Toy }

/** 主屏顶内「状态图标区」图标种类（doc/06 §3.1：饥饿/不开心/脏/病，异常才亮）。 */
private enum class StatusIconKind { HUNGRY, SAD, DIRTY, SICK, KNOWLEDGE }

/**
 * 主屏四区 + 三 overlay 路由（doc/06 §1/§2/§5，Task.md M1.S2）。
 *
 * 四区：顶部状态图标区（主环段起点图标）→ 中央活动区（宠物占位）→ 主环 → 底部入口条；
 * 三 overlay：顶下拉=状态、底上滑=操作、右左滑=快捷清单；三个方向各自带「反方向单箭头」收回钮
 * （Material Symbols single_arrow 字形，点击即收起，像抽屉推回），另有点遮罩 / 返回键兜底；背景不透明黑保证可读。
 * M1 全部为占位内容；M3 起主屏读真实建档快照 [profile]（中央宠物 + 主环 + 状态面板），动作 M6 接入。
 * Debug 安装（应用可调试标记）：中央活动区长按进入 M2.S1 切片核对屏（screen/debug）。
 * [onResetProfile] 非空时（debug）快捷面板出现「重开档」入口，清档回到建档。
 */
@Composable
fun PetScreen(
    profile: PetProfile,
    settleSummary: SettlementSummary? = null,
    onSettleReady: () -> Unit,
    onTimeTravel: ((hours: Long) -> Unit)? = null,
    onResetProfile: (() -> Unit)? = null,
    /** M6.S2：请求执行动作（投喂需 [FoodType]；EntryFlow 执行、存档、上抛 [actionEvent]）。 */
    onAction: (type: ActionType, food: FoodType?, toy: ToyType?) -> Unit = { _, _, _ -> },
    /** M6.S2：最近一次动作执行事件（成功才非空，[ActionEvent.id] 单调自增）。 */
    actionEvent: ActionEvent? = null,
    /** M9.S2：最近一次在线随机事件（命中风波自增号，[OnlineEvent.nonce] 单调自增）。 */
    onlineEvent: OnlineEvent? = null,
    /** M9.S2 会话回顾：本会话命中的在线事件列表（供状态面板「本次动态」）。 */
    onlineReview: List<EventLog> = emptyList(),
) {
    var stage by remember { mutableStateOf(BootStage.Shell) }
    // 当前挂载面板：null=主屏；非 null=抽屉在「拖出中 / 展开动画 / 全开 / 收回动画」任一阶段
    var panel by remember { mutableStateOf<Panel?>(null) }
    // 抽屉展开比 0..1：拖拽全程跟手直写，点击展开 / 松手落位用动画协程过渡（doc/06 §5 抽屉隐喻）
    var reveal by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // 各面板容器真实行程（拖拽跟手的满行程）：本面板打开时量得即写入，跨开合保留；0 表示未量得（按屏径近似）
    val sheetExtent = remember { mutableStateMapOf<Panel, Int>() }
    // Debug 构建（M2.S1）：中央活动区长按进入切片核对屏（release 不可达）
    var debugGrid by remember { mutableStateOf(false) }
    // Debug 判定：本工程未启用 BuildConfig，用应用可调试标记（debug 安装包为可调）
    val isDebug = (LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    // 渲染循环生命周期闸（doc/08 §4）：onPause/onStop 停循环（0 后台 CPU），onStart/onResume 恢复。
    val lifecycleOwner = LocalLifecycleOwner.current
    var appActive by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> appActive = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> appActive = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /** 把展开比动画到 [target]（毫秒 [ms]）；0ms = 直跳（手势落位不重复补动画）。 */
    fun animateRevealTo(target: Float, ms: Int) {
        scope.launch {
            animate(initialValue = reveal, targetValue = target, animationSpec = tween(ms)) { v, _ ->
                reveal = v
            }
        }
    }

    /** 打开抽屉（边缘热区带点按 / 收起后返回）。点按开带滑入动画（拖拽落位已由手势直接写满）。 */
    fun openSheet(p: Panel) {
        if (panel != p) panel = p
        animateRevealTo(1f, 240)
    }

    /** 收抽屉（箭头 / 点外部 / 返回键）：动画滑回后卸载面板。 */
    fun closeSheet() {
        scope.launch {
            animate(initialValue = reveal, targetValue = 0f, animationSpec = tween(200)) { v, _ ->
                reveal = v
            }
            if (reveal == 0f) panel = null
        }
    }

    /**
     * 面板间切换（doc/06 §3.2 三入口合一）：操作面板「状态」首项 = 先收回当前底面板、
     * 再展开顶状态面板（收起下 ⇄ 展开上成对发生），避免两个抽屉同时抢屏。
     */
    fun swapToPanel(target: Panel) {
        val cur = panel ?: return
        if (cur == target) return
        scope.launch {
            animate(initialValue = reveal, targetValue = 0f, animationSpec = tween(140)) { v, _ ->
                reveal = v
            }
            panel = target
            animate(initialValue = 0f, targetValue = 1f, animationSpec = tween(200)) { v, _ ->
                reveal = v
            }
        }
    }

    // ── M6.S2 动作执行事件：成功 → 收面板 + 构建短演出（气泡/浮字/宠物表现）──
    var seenActionId by remember { mutableStateOf(-1L) }
    var currentFx by remember { mutableStateOf<PetFx?>(null) }
    val ctx = LocalContext.current
    LaunchedEffect(actionEvent) {
        val ev = actionEvent ?: return@LaunchedEffect
        if (ev.id == seenActionId) return@LaunchedEffect
        seenActionId = ev.id
        // 先收面板（动作发生在面板内；收起后主屏可见宠物演出）
        closeSheet()
        currentFx = ev.result.toPetFx(ev.id, ctx)
    }

    // ── M9.S2 在线随机事件：命中 → 构建短演出（复用 PetFx 气泡/浮字/宠物表现）──
    // 持久态提示（SLEEPING/WALKING）已由 [onlineEvent.petEvent] 写回 profile.fsmState，
    // 由 PetLivingSprite 自行表现；此处仅驱动一次性气泡演出。
    var seenEventNonce by remember { mutableStateOf(-1L) }
    LaunchedEffect(onlineEvent) {
        val ev = onlineEvent ?: return@LaunchedEffect
        if (ev.nonce == seenEventNonce) return@LaunchedEffect
        seenEventNonce = ev.nonce
        currentFx = ev.petEvent.toPetFx(ev.nonce, ctx)
    }

    LaunchedEffect(Unit) {
        ShellBridge.reveal { stage = it }
        onSettleReady()
    }
    BackHandler(enabled = panel != null) { closeSheet() }
    BackHandler(enabled = debugGrid && panel == null) { debugGrid = false }

    BlackGlowBackground(glow = Color.White.copy(alpha = 0.04f)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(
                    if (!debugGrid) {
                        // 跟手抽屉（doc/06 §5）：手指从三向边缘按正确方向拖动时，
                        // 整块面板沿轴向整体平移、1:1 跟随手指滑入屏内（不需要先过阈值再弹出）；
                        // 松手过半保留、不到一半弹回收起。
                        Modifier.pointerInput(Unit) {
                            val minSidePx = minOf(size.width, size.height).toFloat()
                            val edgePx = EdgeBand.toPx()
                            awaitEachGesture {
                                // requireUnconsumed=false：内层可点控件（debug 长按）会消费 down。
                                // 三向开合不设独立可点控件：本手势统一承担「点按展开」与「跟手拖开」。
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val start = down.position
                                // 抽屉已开时不通过本手势再开新抽屉（收回交给收起钮/点外部/返回键）
                                if (panel != null) return@awaitEachGesture
                                // 起点必须落在三向「边缘热区带」（贴屏缘常驻窄带，见 sheetDragZone）：
                                // 带内起手可点开、也可作拖拽起点；带外（中央宠物区）一律不响应，
                                // 避免大面积可拖带干扰宠物交互。
                                val startZone = sheetDragZone(start, size, edgePx)
                                if (startZone == null) return@awaitEachGesture
                                var last = start
                                var acc = Offset.Zero
                                var zone: Panel? = null
                                val engagePx = 14.dp.toPx()
                                val abandonPx = 28.dp.toPx()
                                val tapSlopPx = 10.dp.toPx()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val moved = change.position - last
                                    acc += moved
                                    last = change.position
                                    if (!event.changes.any { it.pressed }) {
                                        // 松手：
                                        //  - 已进入拖拽（zone!=null）：过半保留，否则弹回收起。
                                        //  - 原地点按（累计位移 ≤ tapSlop）：= 点击边缘热区 → 动画展开。
                                        //  - 意图明确的短拉（同轴正向、位移未过 engage）：死区救援，也展开。
                                        // 手势的 await 块是受限作用域，不能直接调 suspend，动画放到组合作用域。
                                        if (zone != null) {
                                            if (reveal >= 0.5f) {
                                                animateRevealTo(1f, 170)
                                            } else {
                                                scope.launch {
                                                    animate(
                                                        initialValue = reveal,
                                                        targetValue = 0f,
                                                        animationSpec = tween(170),
                                                    ) { v, _ -> reveal = v }
                                                    panel = null
                                                }
                                            }
                                        } else if (!debugGrid && acc.getDistance() <= tapSlopPx) {
                                            openSheet(startZone)
                                        } else if (!debugGrid &&
                                            sheetDragDominant(startZone, acc)
                                        ) {
                                            val travel = sheetDragTravel(startZone, acc)
                                            if (travel >= 0f && travel < engagePx) {
                                                openSheet(startZone)
                                            }
                                        }
                                        break
                                    }
                                    if (zone == null) {
                                        // 同轴拖动过 engage → 进入跟手模式（面板先挂载全收起、随指逐帧展开）
                                        if (sheetDragDominant(startZone, acc) &&
                                            sheetDragTravel(startZone, acc) >= engagePx
                                        ) {
                                            zone = startZone
                                            panel = startZone // 先挂载（此刻全收起），随后跟手指逐帧展开
                                            reveal = 0f
                                        } else if (acc.getDistance() > abandonPx) {
                                            break // 起点在带内但方向不符 / 幅度大：非抽屉手势，放弃
                                        }
                                        continue
                                    }
                                    val span = sheetOpenSpan(zone, sheetExtent, minSidePx)
                                    reveal = (sheetDragTravel(zone, acc) / span).coerceIn(0f, 1f)
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            // 屏幕适配统一口径（见 ScreenAdapt）：主环/把手半径由真实屏宽推导，后期调参只改 AdaptTokens
            val metrics = screenMetrics()
            val ringOuter = metrics.ringOuter
            val handleR = metrics.handleR

            // 三向把手延迟隐藏（doc/06 §1）：每箭头从**自身显现时刻**起计 [HANDLE_HINT_MS] 后
            // 自动淡出，避免常驻观感。三把手分属不同业务层、启动就绪时点不同
            // （Quick→Pet→Status→Settle），故各自独立计时；揭示只在启动链发生一次，超时即不再复显。
            val hintShownTop = stage >= BootStage.Status
            val hintShownBottom = stage >= BootStage.Settle
            val hintShownQuick = stage >= BootStage.Quick
            var hintHiddenTop by remember { mutableStateOf(false) }
            var hintHiddenBottom by remember { mutableStateOf(false) }
            var hintHiddenQuick by remember { mutableStateOf(false) }
            LaunchedEffect(hintShownTop) {
                if (hintShownTop) {
                    delay(HANDLE_HINT_MS)
                    hintHiddenTop = true
                }
            }
            LaunchedEffect(hintShownBottom) {
                if (hintShownBottom) {
                    delay(HANDLE_HINT_MS)
                    hintHiddenBottom = true
                }
            }
            LaunchedEffect(hintShownQuick) {
                if (hintShownQuick) {
                    delay(HANDLE_HINT_MS)
                    hintHiddenQuick = true
                }
            }

            val quickAlpha by animateFloatAsState(
                targetValue = if (hintShownQuick && !hintHiddenQuick) 1f else 0f,
                animationSpec = tween(220),
                label = "quick",
            )
            val petAlpha by animateFloatAsState(
                targetValue = if (stage >= BootStage.Pet) 1f else 0f,
                animationSpec = tween(220),
                label = "pet",
            )
            // 状态层常驻（主环/图标不隐藏）；顶把手单独计时淡出，不复用本 alpha
            val statusAlpha by animateFloatAsState(
                targetValue = if (stage >= BootStage.Status) 1f else 0f,
                animationSpec = tween(220),
                label = "status",
            )
            val topHintAlpha by animateFloatAsState(
                targetValue = if (hintShownTop && !hintHiddenTop) 1f else 0f,
                animationSpec = tween(220),
                label = "topHint",
            )
            val bottomAlpha by animateFloatAsState(
                targetValue = if (hintShownBottom && !hintHiddenBottom) 1f else 0f,
                animationSpec = tween(220),
                label = "bottom",
            )

            // ── 宠物层（M2.S2 → M4.S2 动态化 → M4.S2+ 全屏叠层）────
            // 宠物画布 = 全屏方形（size(minSide)，圆形 clip = 物理屏圆）；可跑范围 = 整个屏幕，
            // 模型尺寸/位移由 PetRenderer 内部换算（模型≈0.30×屏直径，恒不出屏）。
            // 主环/三向把手/面板均在其上层 overlay——宠物走到环带、箭头下方重叠属预期。
            // petAlpha 控制 BootStage.Pet 揭示。
            // 行为循环闸（doc/07 §5 / 08 §4）：前台 + 已揭示宠物 + 无面板/无 debug overlay 才跑
            val livingRunning = appActive && stage >= BootStage.Pet && panel == null && !debugGrid
            val ctx = LocalContext.current
            val petRepo = remember(ctx) { SpriteRepository(ctx.assets) }
            val petSheet = remember(petRepo, profile.petId) {
                runCatching { petRepo.loadSheet(profile.petId).asImageBitmap() }.getOrNull()
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .alpha(petAlpha)
                    .size(metrics.minSide)
                    // 全屏圆裁切（物理屏圆）；宠物不出屏由 FSM clamp + 渲染映射保证，clip 仅为保险
                    .clip(CircleShape)
                    // Debug 安装：长按屏内进入 M2.S1 切片核对屏（release 不携带手势）。
                    // 关键：不能用 detectTapGestures(onLongPress)——它在按下时就 consume down，
                    // 会把主屏根 edgeSwipeToOpen 的 awaitFirstDown(requireUnconsumed) 一起吞掉，
                    // 导致屏内三方向滑动全部失灵。
                    // 这里自实现长按：不消费 down，仅按住不动足够久才触发，滑动正常交还边缘手势。
                    .then(
                        if (isDebug) {
                            Modifier.pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val start = down.position
                                    val holdMs = 500L
                                    val jitterPx = 12.dp.toPx()
                                    var held = false
                                    while (!held) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        if ((change.position - start).getDistance() > jitterPx) break
                                        if (change.uptimeMillis - down.uptimeMillis >= holdMs) held = true
                                    }
                                    if (held) debugGrid = true
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // M4.S2：FSM 主循环宠物（内部 250ms tick 驱动行走/作息表现）
                PetLivingSprite(
                    profile = profile,
                    sheet = petSheet,
                    running = livingRunning,
                    modifier = Modifier.fillMaxSize(),
                    // M6.S2：动作短演出指令（气泡/浮字/蹦跳/咀嚼/亲昵，内部按 tick 相位推进）
                    fx = currentFx,
                )
            }

            // ── 主环（贴边刻度环；段起点图标在环内侧）──────────
            Box(
                Modifier
                    .align(Alignment.Center)
                    .alpha(statusAlpha),
            ) {
                RingProgressBar(
                    outerRadius = ringOuter,
                    values = AttributeRegistry.main.map { profile.attributes[it.id] },
                    attributes = AttributeRegistry.main.map { it.id },
                )
            }

            // ── 三向视觉把手（白色半透明小箭头，overlay 置顶层、贴主环内沿，纯装饰）──
            // 顶▼（下拉=状态）/ 底▲（上滑=操作）/ 右◀（左滑=快捷），r≈0.77 屏半径
            // 箭头不承载点击（无 pointerInput，事件穿透下层）：开合统一由根手势的三向
            // 「边缘热区带」承担（点按展开 / 跟手拖开，见 sheetDragZone）。
            // 各自从所属层显现起计 10s 后随 alpha 淡出（见上 hintHidden*）——提示已到位
            // 不需一直强调；淡出仅视觉，热区带常驻不失效。
            // （宠物在最底层移动，可从把手下方经过；容器透明不挡宠物 hit）
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = -handleR)
                    .alpha(topHintAlpha),
            ) {
                EdgeHint(ChevronDir.Down)
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = handleR)
                    .alpha(bottomAlpha),
            ) {
                EdgeHint(ChevronDir.Up)
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = handleR)
                    .alpha(quickAlpha),
            ) {
                EdgeHint(ChevronDir.Left)
            }

            // ── 状态图标区（顶内「主环内沿」提示带，doc/06 §3.1；点击 = 展开状态面板）──
            // 与顶缘下拉/下面板「状态」同一入口（§3.2 三入口合一）。异常才亮（低值阈值见
            // doc/06 §2）：饥饿/不开心/脏 <30 分别亮碗/云/水滴，SICK 亮「病」十字；
            // 图标沿用对应属性语义色（§4）。平时空载隐藏、不占常观感。
            val iconRowR = metrics.iconRowR
            val statusIcons = if (stage >= BootStage.Status) {
                buildList {
                    if (profile.attributes[AttributeId.SATIATION] < LOW_VALUE_WARN) {
                        add(StatusIconKind.HUNGRY)
                    }
                    if (profile.attributes[AttributeId.MOOD] < LOW_VALUE_WARN) {
                        add(StatusIconKind.SAD)
                    }
                    if (profile.attributes[AttributeId.HYGIENE] < LOW_VALUE_WARN) {
                        add(StatusIconKind.DIRTY)
                    }
                    if (profile.fsmState == PetState.SICK) add(StatusIconKind.SICK)
                }
            } else {
                emptyList()
            }
            if (statusIcons.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset(y = -iconRowR)
                        .alpha(statusAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        statusIcons.forEach { kind ->
                            StatusIconButton(
                                kind = kind,
                                onClick = { openSheet(Panel.Status) },
                            )
                        }
                    }
                }
            }

            // ── Overlay 层（置顶，互斥）────────────────────────
            // 面板挂载 = panel 非空；reveal（0..1）决定整块面板平移出/入屏的程度。
            // reveal 以 lambda 传给 graphicsLayer，只触发重绘、不引发整树重组。
            val mounted = panel
            if (mounted != null) {
                OverlayLayer(
                    p = mounted,
                    profile = profile,
                    settleSummary = settleSummary,
                    onlineReview = onlineReview,
                    onResetProfile = onResetProfile,
                    reveal = { reveal },
                    sheetExtent = sheetExtent,
                    onDismiss = { closeSheet() },
                    onAction = onAction,
                    onShowStatus = { swapToPanel(Panel.Status) },
                )
            }

            // ── Debug 安装：M2.S1 精灵切片核对 overlay（release 不可达）──
            if (isDebug) {
                AnimatedVisibility(
                    visible = debugGrid && panel == null,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(150)),
                ) {
                    DebugSpeciesGridScreen(
                        onDismiss = { debugGrid = false },
                        onTimeTravel = onTimeTravel,
                    )
                }
            }
            // M7.S2 迎接气泡：离线回归一次性问候（靠下浮层，点击/超时消失，避免挡宠物）
            GreetingBubble(settleSummary = settleSummary)
        }
    }
}

// ───────────────────────── Overlay 层 ─────────────────────────

@Composable
private fun BoxScope.OverlayLayer(
    p: Panel,
    profile: PetProfile,
    settleSummary: SettlementSummary? = null,
    onlineReview: List<EventLog> = emptyList(),
    onResetProfile: (() -> Unit)?,
    reveal: () -> Float,
    sheetExtent: SnapshotStateMap<Panel, Int>,
    onDismiss: () -> Unit,
    onAction: (type: ActionType, food: FoodType?, toy: ToyType?) -> Unit,
    onShowStatus: () -> Unit,
) {
    val edge = when (p) {
        Panel.Status -> SheetEdge.Top
        Panel.Action -> SheetEdge.Bottom
        Panel.Quick -> SheetEdge.End
    }
    Box(Modifier.fillMaxSize()) {
        // 面板外任一处点按 = 收回（透明热区，不画背景；主屏内容在面板外仍可见）
        Box(
            Modifier
                .fillMaxSize()
                .clickable { onDismiss() },
        )
        when (p) {
            // 顶抽屉（状态）：本体 = 一整块不透明面板，随 reveal 从屏顶整体平移滑入
            // （停靠屏顶），拖多少就露出多少；对侧（底部）固定收起条。
            // onSizeChanged 记录满行程（滑入 = 平移一个自身高度）供拖拽换算。
            Panel.Status -> Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .sheetSlide(edge = SheetEdge.Top, reveal = reveal)
                    .background(ColorToken.bg)
                    .onSizeChanged { sheetExtent[Panel.Status] = it.height },
            ) {
                RoundSheet(edge = edge, glow = ColorToken.Health.copy(alpha = 0.07f)) {
                    StatusPanelBody(
                        profile = profile,
                        settleTimeline = settleSummary?.offlineTimeline,
                        onlineReview = onlineReview,
                        onDismiss = onDismiss,
                    )
                }
            }

            // 底抽屉（操作）：整块不透明面板随 reveal 从屏底整体平移滑入；对侧（顶部）固定收起条
            Panel.Action -> Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .sheetSlide(edge = SheetEdge.Bottom, reveal = reveal)
                    .background(ColorToken.bg)
                    .onSizeChanged { sheetExtent[Panel.Action] = it.height },
            ) {
                RoundSheet(edge = edge, glow = ColorToken.Satiation.copy(alpha = 0.08f)) {
                    ActionPanelBody(
                        onDismiss = onDismiss,
                        profile = profile,
                        onAction = onAction,
                        onShowStatus = onShowStatus,
                    )
                }
            }

            // 右抽屉（功能）：整块**占满全屏宽度**（与顶/底抽屉同宽，doc/06 §5），
            // 随 reveal 从右缘整体平移滑入盖满；内部为「左缘 36dp 收起条 + 列表占满剩余」
            // 的 Row 结构（QuickPanelBody）。注意：宽度必须 100%，不能收窄成 0.86
            // 再靠左缘留空列——那会显得条带外还有一大片面板（§11 #12）。
            Panel.Quick -> Box(
                Modifier
                    .fillMaxSize()
                    .sheetSlide(edge = SheetEdge.End, reveal = reveal)
                    .background(ColorToken.bg)
                    .onSizeChanged { sheetExtent[Panel.Quick] = it.width },
            ) {
                RoundSheet(edge = edge, glow = ColorToken.Mood.copy(alpha = 0.07f)) {
                    QuickPanelBody(
                        onDismiss = onDismiss,
                        onResetProfile = onResetProfile,
                    )
                }
            }
        }
    }
}

/**
 * 抽屉本体「整体位移」——真正的 Sheet/抽屉观感（doc/06 §5）：
 * [reveal]=0 时整块面板（不透明黑底 + 内容）被平移到对应屏缘之外完全不可见；
 * [reveal]=1 时停在开位。拖拽中位移与手指 1:1，屏幕上出现的是一整块**随手指移动
 * 的面板**，未覆盖区域透出主屏——不是把面板钉在原位、用 clip 裁出可见窗口。
 * 位移量以自身尺寸为满行程（顶/底平移一个高度、右/左平移一个宽度）。
 */
private fun Modifier.sheetSlide(
    edge: SheetEdge,
    reveal: () -> Float,
): Modifier = graphicsLayer {
    val r = reveal().coerceIn(0f, 1f)
    when (edge) {
        SheetEdge.Top -> translationY = -size.height * (1f - r)
        SheetEdge.Bottom -> translationY = size.height * (1f - r)
        SheetEdge.End -> translationX = size.width * (1f - r)
        SheetEdge.Start -> translationX = -size.width * (1f - r)
    }
}

/**
 * 标题行 + 收回钮（随所在滚动列表滚动）。现仅供 Debug 精灵核对屏整屏网格用；
 * 正式三面板（状态/操作/功能）的收回钮不在此处，改由 [DismissStrip]/[DismissStripV]
 * 固定在面板对侧、滚动列表之外的独立区域（doc/06 §5）。
 */
@Composable
internal fun RoundHeader(title: String, closeDir: ChevronDir, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = ColorToken.Accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        val metrics = screenMetrics()
        Box(
            Modifier
                .size(metrics.dp(30.dp))
                .clip(CircleShape)
                .background(ColorToken.Accent.copy(alpha = 0.08f))
                .border(1.dp, ColorToken.Text2.copy(alpha = 0.22f), CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            MiniChevron(dir = closeDir, tint = ColorToken.Text2, size = 16.dp)
        }
    }
}

/**
 * 离线回归一次性问候气泡（M7.S2，doc/04 §5 / doc/03 §7.6）。
 * 长离线优先以 [SettlementSummary.endingMood] 定调，短离线按离开时长分档（I18n.greetingRes）。
 * 靠下浮层（屏幕下方、圆形可视区内，避免居中挡住宠物），点击或 4.5s 后自动消失；
 * 文案走扩展函数映射（core 不引 R）。
 */
@Composable
private fun GreetingBubble(settleSummary: SettlementSummary?) {
    var resId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(settleSummary) {
        if (settleSummary != null) {
            resId = greetingRes(settleSummary.elapsedMs, settleSummary.endingMood)
            delay(4500)
            resId = null
        }
    }
    if (resId != null) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable { resId = null },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
                    .background(ColorToken.bg.copy(alpha = 0.9f), shape = CircleShape)
                    .border(1.dp, ColorToken.Accent.copy(alpha = 0.24f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(resId!!),
                    color = ColorToken.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** 状态面板（顶）：滚动列表在上，对侧（底部）固定收起条，列表滚动不影响收起条。 */
@Composable
private fun ColumnScope.StatusPanelBody(
    profile: PetProfile,
    settleTimeline: List<OfflineEvent>? = null,
    onlineReview: List<EventLog> = emptyList(),
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .weight(1f),
    ) {
        RoundList {
            RoundEdgeSpace(48.dp)
            AttributeRegistry.all.forEachIndexed { index, meta ->
                if (index > 0) RoundListSpacer()
                val color = attributeColor(meta.id)
                val value = profile.attributes[meta.id]
                // 低值预警（<30，doc/06 §2/§3.2）：行尾迷你环换告警色 2Hz 呼吸
                val warn = value < LOW_VALUE_WARN
                PillItem(
                    "${meta.id.label}  ${value.roundToInt()}",
                    filled = false,
                    // 右留白 11dp = 环外缘到行上下边距（46 行高 − 24 环径）/ 2，
                    // 使环与胶囊右端半圆同圆心（同心内嵌观感）。
                    contentPadding = PaddingValues(start = 18.dp, end = 11.dp),
                    // 行首图标与首页环状状态条同源字形 + 同色（低值预警转 Warn），便于按形状对照
                    icon = { AttributeIcon(meta.id, if (warn) ColorToken.Warn else color) },
                    trailing = {
                        MiniProgressRing(value = value, color = color, warn = warn)
                    },
                    onClick = null,
                )
            }
            // M7.S2 离线回放：把本次离线时间线铺进状态面板底部（圆表友好滚动）
            if (settleTimeline != null && settleTimeline.isNotEmpty()) {
                RoundListSpacer()
                PanelTitle(stringResource(R.string.replay_title))
                settleTimeline.forEach { ev ->
                    RoundListSpacer()
                    PillItem(
                        stringResource(offlineEventBubbleRes(ev.eventId) ?: R.string.ev_idle_pass),
                        filled = false,
                        contentPadding = PaddingValues(start = 18.dp, end = 11.dp),
                        icon = { ColorDot(if (ev.highlight) ColorToken.Accent else ColorToken.Text2) },
                        trailing = null,
                        onClick = null,
                    )
                }
            }
            // M9.S2 会话回顾：本会话命中的在线随机事件（与离线回放并列，doc/04 §9.2）
            if (onlineReview.isNotEmpty()) {
                RoundListSpacer()
                PanelTitle(stringResource(R.string.review_online_title))
                onlineReview.reversed().forEach { log ->
                    RoundListSpacer()
                    PillItem(
                        stringResource(eventBubbleRes(log.note ?: "") ?: R.string.ev_idle_pass),
                        filled = false,
                        contentPadding = PaddingValues(start = 18.dp, end = 11.dp),
                        icon = { ColorDot(ColorToken.Accent) },
                        trailing = null,
                        onClick = null,
                    )
                }
            }
            RoundEdgeSpace(48.dp)
        }
    }
    DismissStrip(dir = ChevronDir.Up, onDismiss)
}

/** 属性 → 主题色（与主环/建档预览同源，doc/01 §3）。 */
private fun attributeColor(id: AttributeId): Color = when (id) {
    AttributeId.SATIATION -> ColorToken.Satiation
    AttributeId.MOOD -> ColorToken.Mood
    AttributeId.HEALTH -> ColorToken.Health
    AttributeId.KNOWLEDGE -> ColorToken.Knowledge
    AttributeId.HYGIENE -> ColorToken.Hygiene
}

/**
 * 状态图标按钮（主屏顶内，doc/06 §3.1）：热区 ≥30dp、字形 22dp、颜色随属性语义色；
 * 点击任一图标 = 展开状态面板（与顶缘下拉同入口）。
 */
@Composable
private fun StatusIconButton(kind: StatusIconKind, onClick: () -> Unit) {
    val tint = when (kind) {
        StatusIconKind.HUNGRY -> ColorToken.Satiation
        StatusIconKind.SAD -> ColorToken.Mood
        StatusIconKind.DIRTY -> ColorToken.Hygiene
        StatusIconKind.SICK -> ColorToken.Health
        StatusIconKind.KNOWLEDGE -> ColorToken.Knowledge
    }
    val description = when (kind) {
        StatusIconKind.HUNGRY -> stringResource(R.string.status_icon_hungry)
        StatusIconKind.SAD -> stringResource(R.string.status_icon_sad)
        StatusIconKind.DIRTY -> stringResource(R.string.status_icon_dirty)
        StatusIconKind.SICK -> stringResource(R.string.status_icon_sick)
        StatusIconKind.KNOWLEDGE -> stringResource(R.string.status_icon_knowledge)
    }
    val cd = stringResource(R.string.status_icon_cd, description)
    val metrics = screenMetrics()
    Box(
        modifier = Modifier
            .size(metrics.dp(30.dp))
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(metrics.dp(22.dp))) {
            drawStatusIcon(kind = kind, tint = tint)
        }
    }
}

/** 状态面板行首图标：复用首页主环同源字形（碗/气球/十字…，见 [drawAttributeGlyph]），
 * 尺寸与首页一致（22dp），颜色随属性（低值预警转 Warn），各图标基于同一 side 盒居中绘制、视觉大小统一，便于对照。 */
@Composable
private fun AttributeIcon(id: AttributeId, tint: Color) {
    val metrics = screenMetrics()
    Canvas(Modifier.size(metrics.dp(22.dp))) {
        drawAttributeGlyph(id = id, center = Offset(size.width / 2f, size.height / 2f), tint = tint, side = size.width)
    }
}

/** 状态图标字形（碗=饿 / 云=不开心 / 水滴=脏 / 圆角十字=病 / 四角星=知识），单色随属性色。 */
private fun DrawScope.drawStatusIcon(kind: StatusIconKind, tint: Color) {
    val l = size.width
    val strokeW = l * 0.13f
    when (kind) {
        StatusIconKind.HUNGRY -> {
            // 碗：碗口线 + 碗身下半弧
            drawLine(
                color = tint,
                start = Offset(l * 0.14f, l * 0.36f),
                end = Offset(l * 0.86f, l * 0.36f),
                strokeWidth = strokeW,
            )
            drawArc(
                color = tint,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(l * 0.14f, l * 0.32f),
                size = Size(l * 0.72f, l * 0.72f),
                style = Stroke(width = strokeW),
            )
        }
        StatusIconKind.SAD -> {
            // 云：三圆簇（不开心）
            drawCircle(tint, radius = l * 0.21f, center = Offset(l * 0.5f, l * 0.64f))
            drawCircle(tint, radius = l * 0.17f, center = Offset(l * 0.32f, l * 0.5f))
            drawCircle(tint, radius = l * 0.17f, center = Offset(l * 0.68f, l * 0.5f))
        }
        StatusIconKind.DIRTY -> {
            // 水滴（脏）
            val path = Path().apply {
                moveTo(l * 0.5f, l * 0.1f)
                cubicTo(l * 0.04f, l * 0.52f, l * 0.2f, l * 0.9f, l * 0.5f, l * 0.9f)
                cubicTo(l * 0.8f, l * 0.9f, l * 0.96f, l * 0.52f, l * 0.5f, l * 0.1f)
                close()
            }
            drawPath(path, tint)
        }
        StatusIconKind.SICK -> {
            // 圆角十字（病）
            val r = l * 0.12f
            drawRoundRect(
                color = tint,
                topLeft = Offset(l * 0.26f, l * 0.42f),
                size = Size(l * 0.48f, l * 0.16f),
                cornerRadius = CornerRadius(r, r),
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(l * 0.42f, l * 0.26f),
                size = Size(l * 0.16f, l * 0.48f),
                cornerRadius = CornerRadius(r, r),
            )
        }
        StatusIconKind.KNOWLEDGE -> {
            // 四角星（智慧/聪慧；面板属性图标专属，首页状态条不出现，仅用于状态面板对照）
            val cx = l * 0.5f
            val cy = l * 0.5f
            val outer = l * 0.42f
            val inner = l * 0.17f
            val star = Path().apply {
                for (i in 0..7) {
                    val ang = kotlin.math.PI / 2 * i
                    val rad = if (i % 2 == 0) outer else inner
                    val x = cx + rad * kotlin.math.cos(ang).toFloat()
                    val y = cy - rad * kotlin.math.sin(ang).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(star, tint)
        }
    }
}

/**
 * 操作面板（底）：对侧（顶部）固定收起条 + 滚动列表；页内两级（动作列表 ⇄ 食物选择）。
 * M6.S2 接入真实动作（Task.md M6.S2 / doc/06 §6 + doc/01 §10）：
 * - 「状态」首项 = 收起下、展开上（§3.2 三入口合一）；
 * - 投喂/玩耍/抚摸：可执行 = 实心；冷却/属性满 = 空心只读（行内附原因/倒计时）；
 * - 投喂 → 食物子页（口味类别色覆写胶囊背景，契合口味提示）；动作执行经 [onAction] 上抛；
 * - SICK 时出现「治疗」（引擎条件仅 SICK，见 ActionRule）；熟睡（SLEEPING）时深夜互动
 *   收益为 0，四个动作收起为空心提示行（doc/06 §6 睡眠窗口不互动）。
 */
@Composable
private fun ColumnScope.ActionPanelBody(
    onDismiss: () -> Unit,
    profile: PetProfile,
    onAction: (type: ActionType, food: FoodType?, toy: ToyType?) -> Unit,
    onShowStatus: () -> Unit,
) {
    DismissStrip(dir = ChevronDir.Down, onDismiss)
    Box(
        Modifier
            .fillMaxWidth()
            .weight(1f),
    ) {
        var page by remember { mutableStateOf(ActionPage.Actions) }
        // 面板打开期间每秒推进一次当前时刻：冷却行的剩余倒计时实时刷新（到点自动恢复实心）
        var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                nowMs = System.currentTimeMillis()
            }
        }
        if (page == ActionPage.Actions) {
            ActionListPage(
                profile = profile,
                nowMs = nowMs,
                onShowStatus = onShowStatus,
                onOpenFeed = { page = ActionPage.Feed },
                onOpenToy = { page = ActionPage.Toy },
                onAction = onAction,
            )
        } else if (page == ActionPage.Toy) {
            ToyPage(
                profile = profile,
                onBack = { page = ActionPage.Actions },
                onPick = { onAction(ActionType.PLAY, null, it) },
            )
        } else {
            FeedPage(
                profile = profile,
                onBack = { page = ActionPage.Actions },
                onPick = { onAction(ActionType.FEED, it, null) },
            )
        }
    }
}

/** 操作面板 · 动作列表页。 */
@Composable
private fun ActionListPage(
    profile: PetProfile,
    nowMs: Long,
    onShowStatus: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenToy: () -> Unit,
    onAction: (type: ActionType, food: FoodType?, toy: ToyType?) -> Unit,
) {
    RoundList {
        RoundEdgeSpace(48.dp)
        // 状态：三入口合一的「收起下、展开上」（doc/06 §3.2）
        PillItem(stringResource(R.string.action_status), filled = true, onClick = onShowStatus)
        RoundListSpacer()
        if (profile.fsmState == PetState.SLEEPING) {
            // 熟睡中：深夜互动收益 = 0，动作不占位（doc/06 §6 睡眠窗口不互动）
            PillItem(stringResource(R.string.action_sleeping), filled = false)
        } else {
            val mood = profile.attributes[AttributeId.MOOD]
            val feedDenied = ActionRule.feedDenied(profile, nowMs)
            val playDenied = ActionRule.playDenied(profile, nowMs)
            val petDenied = ActionRule.petDenied(profile, nowMs)
            val cleanDenied = ActionRule.cleanDenied(profile, nowMs)
            // 投喂（可执行 → 进入食物子页；冷却/吃饱 → 空心只读）
            ActionRow(
                title = stringResource(R.string.action_feed),
                denied = feedDenied,
                dotColor = ColorToken.Satiation,
                onClick = onOpenFeed,
            )
            RoundListSpacer()
            ActionRow(
                title = stringResource(R.string.action_play),
                denied = playDenied,
                dotColor = ColorToken.Mood,
                onClick = onOpenToy,
            )
            RoundListSpacer()
            ActionRow(
                title = stringResource(R.string.action_pet),
                denied = petDenied,
                dotColor = ColorToken.Health,
                onClick = { onAction(ActionType.PET, null, null) },
            )
            // 学习：智力只增不减，随时可学（冷却 1h；冷却空心只读，M12）
            val studyDenied = ActionRule.studyDenied(profile, nowMs)
            RoundListSpacer()
            ActionRow(
                title = stringResource(R.string.action_study),
                denied = studyDenied,
                dotColor = ColorToken.Knowledge,
                onClick = { onAction(ActionType.STUDY, null, null) },
            )
            // 清洁：仅「脏了」（hygiene < 告警阈值）才出现；冷却空心只读（M11）
            if (profile.attributes[AttributeId.HYGIENE] < LOW_VALUE_WARN) {
                RoundListSpacer()
                ActionRow(
                    title = stringResource(R.string.action_clean),
                    denied = cleanDenied,
                    dotColor = ColorToken.Hygiene,
                    onClick = { onAction(ActionType.CLEAN, null, null) },
                )
            }
            // 治疗：仅 SICK 才出现（不 SICK 不占位，引擎同条件拦截）
            if (profile.fsmState == PetState.SICK) {
                RoundListSpacer()
                PillItem(stringResource(R.string.action_heal), filled = true, onClick = { onAction(ActionType.HEAL, null, null) })
            }
        }
        RoundEdgeSpace(48.dp)
    }
}

/** 操作面板 · 食物选择子页（doc/06 §6 投喂：口味类别色覆写胶囊背景）。 */
@Composable
private fun FeedPage(
    profile: PetProfile,
    onBack: () -> Unit,
    onPick: (FoodType) -> Unit,
) {
    RoundList {
        RoundEdgeSpace(48.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 子页返回（滚动首元素行，chevron ≥20dp 视觉、热区 ≥30dp）
            val metrics = screenMetrics()
            Box(
                modifier = Modifier
                    .size(metrics.dp(34.dp))
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                MiniChevron(dir = ChevronDir.Left, tint = ColorToken.Accent, size = 20.dp)
            }
            Text(
                stringResource(R.string.feed_title, profile.petName),
                color = ColorToken.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        RoundListSpacer()
        FoodType.entries.forEachIndexed { index, food ->
            if (index > 0) RoundListSpacer()
            val matched = food.flavor == profile.personality.flavor
            val suffix = if (matched) stringResource(R.string.feed_match) else stringResource(food.flavor.labelRes)
            PillItem(
                text = "${food.icon} ${stringResource(food.labelRes)} · $suffix",
                filled = true,
                color = foodTint(food.flavor),
                onClick = { onPick(food) },
            )
        }
        RoundEdgeSpace(48.dp)
    }
}

/** 操作面板 · 玩具选择子页（M13：玩具差异化，已解锁实心可选、未解锁空心带来源）。 */
@Composable
private fun ToyPage(
    profile: PetProfile,
    onBack: () -> Unit,
    onPick: (ToyType) -> Unit,
) {
    RoundList {
        RoundEdgeSpace(48.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val metrics = screenMetrics()
            Box(
                modifier = Modifier
                    .size(metrics.dp(34.dp))
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                MiniChevron(dir = ChevronDir.Left, tint = ColorToken.Accent, size = 20.dp)
            }
            Text(
                stringResource(R.string.toy_title, profile.petName),
                color = ColorToken.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        RoundListSpacer()
        ToyType.entries.forEachIndexed { index, toy ->
            if (index > 0) RoundListSpacer()
            val unlocked = ToyRules.isUnlocked(toy, profile)
            if (unlocked) {
                PillItem(
                    text = "${toy.icon} ${toy.label} · ${toy.vibe.label}",
                    filled = true,
                    color = ColorToken.Mood,
                    onClick = { onPick(toy) },
                )
            } else {
                val reason = when (val u = toy.unlock) {
                    ToyUnlock.Default -> ""
                    is ToyUnlock.ByKnowledge -> stringResource(R.string.toy_locked_knowledge, u.knowledge)
                    is ToyUnlock.ByMilestone -> stringResource(R.string.toy_locked_milestone, u.count)
                    ToyUnlock.ByEvent -> stringResource(R.string.toy_locked_event)
                }
                PillItem(
                    text = "${toy.icon} ${toy.label} · $reason",
                    filled = false,
                )
            }
        }
        RoundEdgeSpace(48.dp)
    }
}

/**
 * 动作行：可执行 = 实心胶囊（[onClick]）；denied 非空 = 空心只读 + 行内原因
 * （doc/06 §8.3：冷却倒计时 / 属性已满说明）。
 */
@Composable
private fun ColumnScope.ActionRow(
    title: String,
    denied: ActionDenied?,
    dotColor: Color,
    onClick: () -> Unit,
) {
    val ctx = LocalContext.current
    if (denied == null) {
        PillItem(
            text = title,
            filled = true,
            icon = { ColorDot(dotColor) },
            onClick = onClick,
        )
    } else {
        PillItem(
            text = "$title · ${denied.reasonText(ctx)}",
            filled = false,
            icon = { ColorDot(dotColor) },
            onClick = null,
        )
    }
}

/** 不可执行原因 → 只读行内说明（空心胶囊文案，doc/06 §8.3）。 */
private fun ActionDenied.reasonText(ctx: Context): String = when (this) {
    is ActionDenied.Cooldown -> ctx.getString(R.string.deny_cooldown, fmtRemain(remainMs))
    is ActionDenied.AttributeCeiling -> when (id) {
        AttributeId.SATIATION -> ctx.getString(R.string.deny_full_satiation)
        else -> ctx.getString(R.string.deny_full_mood)
    }
    ActionDenied.NotSick -> ctx.getString(R.string.deny_not_sick)
}

/** 剩余时长 → mm:ss / h:mm:ss。 */
private fun fmtRemain(remainMs: Long): String {
    val totalSec = ((remainMs.coerceAtLeast(0) + 999) / 1000)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val p2 = { v: Long -> v.toString().padStart(2, '0') }
    return if (h > 0) "$h:${p2(m)}:${p2(s)}" else "${p2(m)}:${p2(s)}"
}

/** 食物口味 → 类别覆写色（M6.S2 新增类别色，doc/06 §4.1）。 */
private fun foodTint(flavor: FoodFlavor): Color = when (flavor) {
    FoodFlavor.BALANCED -> ColorToken.FoodBalanced
    FoodFlavor.HEARTY -> ColorToken.FoodHearty
    FoodFlavor.LIGHT -> ColorToken.FoodLight
    FoodFlavor.SWEET -> ColorToken.FoodSweet
    FoodFlavor.NOVEL -> ColorToken.FoodNovel
}

/** ActionResult → 表现层短演出指令（气泡文案 + 属性浮字；nonce = 动作事件自增号）。 */
private fun ActionResult.toPetFx(nonce: Long, ctx: Context): PetFx {
    val kind = when (hint) {
        ActionHint.EATING -> FxKind.EATING
        ActionHint.EXCITED -> FxKind.EXCITED
        ActionHint.AFFECTION -> FxKind.AFFECTION
        ActionHint.TREATED -> FxKind.TREATED
        ActionHint.CLEANING -> FxKind.CLEANING
        ActionHint.STUDYING -> FxKind.STUDYING
    }
    val bubble = when (hint) {
        ActionHint.EATING -> if (note != null) ctx.getString(R.string.fx_eat_note, note) else ctx.getString(R.string.fx_eat)
        ActionHint.EXCITED -> ctx.getString(R.string.fx_excited)
        ActionHint.AFFECTION -> ctx.getString(R.string.fx_affection)
        ActionHint.TREATED -> ctx.getString(R.string.fx_treated)
        ActionHint.CLEANING -> ctx.getString(R.string.fx_clean)
        ActionHint.STUDYING -> if (unlockTier != null) ctx.getString(R.string.fx_study_unlock) else ctx.getString(R.string.fx_study)
    }
    // 属性浮字：正值在前、负值（如喂食附带的清洁 −4）随后；四舍五入为整数展示
    val floats = AttributeRegistry.all
        .mapNotNull { meta ->
            val d = delta[meta.id]
            if (abs(d) < 0.5f) null else meta.id to d
        }
        .sortedBy { if (it.second > 0) 0 else 1 }
        .map { (id, d) ->
            val sign = if (d > 0) "+" else "-"
            FxFloat(text = "$sign${abs(d).roundToInt()} ${ctx.getString(id.labelRes)}", color = attributeColor(id))
        }
    val emotion = if (hint == ActionHint.EXCITED) toy?.vibe?.toEmotion() else null
    return PetFx(nonce = nonce, kind = kind, bubble = bubble, floats = floats, emotion = emotion)
}

/** 在线事件气泡 id → 字符串资源（M9，doc/03 §4；无匹配回落 ev_idle_pass）。 */
private fun eventBubbleRes(bubbleId: String): Int? = when (bubbleId) {
    "event.found_food" -> R.string.ev_found_food
    "event.sneeze" -> R.string.ev_sneeze
    "event.curious_walk" -> R.string.ev_curious_walk
    "event.yawn" -> R.string.ev_yawn
    "event.beg_food" -> R.string.ev_beg_food
    "event.dream" -> R.string.ev_dream
    "event.treat_hunt" -> R.string.ev_treat_hunt
    else -> null
}

/** [EventEngine.PetEvent] → 表现层短演出指令（气泡文案 + 属性浮字；nonce = 事件自增号）。 */
private fun EventEngine.PetEvent.toPetFx(nonce: Long, ctx: Context): PetFx {
    // 持久态（WALKING/SLEEPING）由 fsmState 自现；瞬态（EXCITED）或纯气泡统一蹦跳表现
    val kind = FxKind.EXCITED
    val bubble = ctx.getString(eventBubbleRes(bubbleId) ?: R.string.ev_idle_pass)
    val floats = AttributeRegistry.all
        .mapNotNull { meta ->
            val d = attrDelta[meta.id]
            if (abs(d) < 0.5f) null else meta.id to d
        }
        .sortedBy { if (it.second > 0) 0 else 1 }
        .map { (id, d) ->
            val sign = if (d > 0) "+" else "-"
            FxFloat(
                text = "$sign${abs(d).roundToInt()} ${ctx.getString(id.labelRes)}",
                color = attributeColor(id),
            )
        }
    return PetFx(nonce = nonce, kind = kind, bubble = bubble, floats = floats)
}

/**
 * 功能面板（右）：动态读取系统 App 的双列图标网格（doc/05 §4 / M8 重构）。
 * - 不预设任何快捷方式：App 经由 PackageManager 动态发现（见 [AppLister]）。
 * - 普通态：双列图标，点击拉起 App，长按进入编辑。
 * - 编辑态：↑↓ 排序 / × 移除 / ＋ 从系统可读到的全部 App 中添加；选择写回 SP。
 * - 非 App 功能（Wi-Fi/手电筒/勿扰…）的接口保留在 domain.plugin（PluginSpec/PluginExecutor…），留待后续接入。
 */
@Composable
private fun ColumnScope.QuickPanelBody(
    onDismiss: () -> Unit,
    onResetProfile: (() -> Unit)?,
) {
    val context = LocalContext.current
    val resolver = remember(context) { AndroidPluginResolver(context) }
    val runner = remember(context) { AndroidLocalActionRunner(context) }
    val store = remember(context) { PluginPrefsStore(context) }
    val lister = remember(context) { AppLister(context) }

    // 功能清单只读本都保存的包名序列；按需取图标/名称（不走 launchableApps 全量枚举 IPC）。
    // 默认 selected 为空 → 不显示任何 App，编辑在独立 Activity 中进行（见 QuickPanelEditActivity）。
    var prefs by remember { mutableStateOf(store.load()) }
    val selectedApps = remember(prefs) { prefs.selected.mapNotNull { lister.entryOf(it) } }
    val icons = remember(prefs) {
        prefs.selected.associateWith { lister.loadIcon(it)?.toImageBitmap() }
    }
    val labels = remember(selectedApps) { selectedApps.associate { it.packageName to it.label } }

    // 从编辑 Activity 返回（onResume）重新读盘，使功能清单刷新为最新选择
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) prefs = store.load()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val toastState = remember { mutableStateOf<String?>(null) }
    val notifier = remember(labels) {
        object : PluginNotifier {
            override fun onNotFound(spec: PluginSpec) {
                val pkg = spec.expectedPackages.firstOrNull()
                val name = if (pkg != null) (labels[pkg] ?: pkg) else context.getString(R.string.app_name)
                toastState.value = "未找到：$name"
            }
        }
    }

    // 触发反馈气泡自动消失（doc/05 §4：失败 Bubble 不弹系统错误）
    LaunchedEffect(toastState.value) {
        toastState.value?.let {
            delay(1600)
            toastState.value = null
        }
    }

    fun launchApp(pkg: String) {
        val spec = PluginSpec(
            id = "app:$pkg", nameKey = "", iconKey = "",
            type = PluginTriggerType.LAUNCH_APP, expectedPackages = listOf(pkg),
        )
        if (PluginExecutor.execute(spec, resolver, runner, notifier)) onDismiss()
    }

    // 进入独立编辑页（全屏 Activity，承载全量枚举 IPC 与编辑交互）
    fun openEdit() {
        context.startActivity(Intent(context, QuickPanelEditActivity::class.java))
        onDismiss()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .weight(1f),
    ) {
        DismissStripV(dir = ChevronDir.Right, onDismiss)
        Box(
            Modifier
                .fillMaxHeight()
                .weight(1f),
        ) {
            AppGridContent(
                apps = selectedApps,
                icons = icons,
                onEdit = { openEdit() },
                onLaunch = { launchApp(it) },
                onLongPress = { openEdit() },
            )

            // 触发失败 Bubble（doc/05 §4）
            toastState.value?.let { msg ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .clip(CircleShape)
                        .background(ColorToken.bg.copy(alpha = 0.92f))
                        .border(1.dp, ColorToken.Text2, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(msg, color = ColorToken.Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * 双列 App 图标网格（普通态）：真实 App 图标（PackageManager 加载）+ 名称。
 * 标题作为滚动首元素（GridItemSpan(2)），打开时借 contentPadding 顶部留白实现竖直居中（doc/06 §8.1）。
 */
@Composable
private fun AppGridContent(
    apps: List<AppEntry>,
    icons: Map<String, ImageBitmap?>,
    onEdit: () -> Unit,
    onLaunch: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    val metrics = screenMetrics()
    val topPad = metrics.listEdge(24.dp)
    val bottomPad = metrics.longSide * 0.5f
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .roundEdgeFade(),
        contentPadding = PaddingValues(
            top = topPad,
            bottom = bottomPad,
            start = roundSafeInset(),
            end = roundSafeInset(),
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (apps.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    stringResource(R.string.quick_empty),
                    color = ColorToken.Text2,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(apps, key = { it.packageName }, contentType = { 0 }) { app ->
            AppGridCell(app = app, icons = icons, onLaunch = onLaunch, onLongPress = onLongPress)
        }
        // 编辑入口固定为最后一个网格单元，样式与 App 图标一致（tune 图标）
        item(key = "quick_edit") {
            EditGridCell(onEdit = onEdit)
        }
    }
}

/** 网格单元：App 图标（真实图标或占位圆）+ 名称（11sp，不透明，doc/06 §8 约束）。 */
@Composable
private fun AppGridCell(
    app: AppEntry,
    icons: Map<String, ImageBitmap?>,
    onLaunch: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    val metrics = screenMetrics()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onLaunch(app.packageName) },
                onLongClick = { onLongPress(app.packageName) },
            )
            .padding(metrics.dp(6.dp)),
    ) {
        AppIcon(app.packageName, icons, size = metrics.dp(46.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            app.label,
            color = ColorToken.Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** 编辑入口网格单元：与 App 图标同款样式，固定为网格最后一个（tune 图标）。 */
@Composable
private fun EditGridCell(onEdit: () -> Unit) {
    val metrics = screenMetrics()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit)
            .padding(metrics.dp(6.dp)),
    ) {
        Box(
            modifier = Modifier
                .size(metrics.dp(46.dp))
                .clip(CircleShape)
                .background(ColorToken.Text2.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                tune,
                contentDescription = null,
                tint = ColorToken.Accent,
                modifier = Modifier.size(metrics.dp(32.dp)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.quick_edit),
            color = ColorToken.Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}



/**
 * 面板标题：滚动流首元素（随列表滚动，doc/06 §8.1）——
 * 它只是标题文字，收起按钮不在标题里，而在对侧固定条 [DismissStrip]/[DismissStripV]。
 */
@Composable
private fun PanelTitle(title: String) {
    Text(
        title,
        color = ColorToken.Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * 面板对侧固定收起条（横条：顶面板在底部 / 底面板在顶部）：
 * 独立于滚动列表外的固定区域，列表滑动不影响它；条内仅一枚「反方向单箭头」。
 * 形态约束：外层仅做透明占位把箭头钉在对侧（高 ≈36dp），**可点热区只围绕箭头
 * （≈48×36dp，≥30dp）**，整行空白不可点——视觉上就是一枚小箭头，不是一条大按钮
 * （doc/06 §5 收起规则 / §11 #10）。
 */
@Composable
private fun DismissStrip(dir: ChevronDir, onDismiss: () -> Unit) {
    val metrics = screenMetrics()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(36.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(metrics.dp(48.dp))
                .fillMaxHeight()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            MiniChevron(dir = dir, tint = ColorToken.Text2, size = metrics.dp(20.dp))
        }
    }
}

/**
 * 面板对侧固定收起条（竖条：右面板在左缘）：
 * 独立于滚动列表外的固定区域，列表滑动不影响它；条内仅一枚「反方向单箭头」。
 * 形态约束：外层仅做透明占位把箭头钉在对侧左缘（宽 ≈36dp，与上/下面板横条的高度
 * 36dp 同截面），列表让出该列；**可点热区只围绕箭头（36×48dp，≥30dp）**，
 * 整列空白不可点——视觉只有一枚小箭头（doc/06 §5 收起规则 / §11 #10）。
 */
@Composable
private fun DismissStripV(dir: ChevronDir, onDismiss: () -> Unit) {
    val metrics = screenMetrics()
    Box(
        modifier = Modifier
            .width(metrics.dp(36.dp))
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.dp(48.dp))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            MiniChevron(dir = dir, tint = ColorToken.Text2, size = metrics.dp(20.dp))
        }
    }
}

// ──────────────────── Material Symbols 单箭头 + 手势把手（瘦身版） ────────────────────

/** 方向指示（旋转 single_arrow 字形得到，doc/06 §5；debug 核对屏复用）。 */
internal enum class ChevronDir { Up, Down, Left, Right }

/**
 * Material Symbols Outlined「single_arrow」24dp 字形：ImageVector 内嵌路径，
 * 取自 Google Fonts 生成的 Compose 源（一条带尾折角的右向单箭头，实心填充）。
 */
private val SingleArrow: ImageVector = ImageVector.Builder(
    name = "SingleArrow",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(7.78f, 19f)
        lineTo(12.78f, 12f)
        lineTo(7.78f, 5f)
        horizontalLineTo(10.23f)
        lineTo(15.23f, 12f)
        lineTo(10.23f, 19f)
        close()
    }
}.build()

/**
 * 单箭头图标：把 [SingleArrow] 字形按 [dir] 旋转到指定指向
 * （右=0° / 下=90° / 左=180° / 上=270°），替换原手工 Canvas 折线画法。
 */
@Composable
internal fun MiniChevron(
    dir: ChevronDir,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    val angle = when (dir) {
        ChevronDir.Right -> 0f
        ChevronDir.Down -> 90f
        ChevronDir.Left -> 180f
        ChevronDir.Up -> 270f
    }
    Icon(
        painter = rememberVectorPainter(SingleArrow),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size).rotate(angle),
    )
}

/**
 * 三向视觉把手（纯装饰，doc/06 §1）：白色半透明单箭头（无底色/描边/标签），只作
 * 「该方向边缘可开面板」的提示。**不承载点击、无 pointerInput、事件穿透下层**——
 * 点按展开与跟手拖开统一走常驻不可见的「边缘热区带」手势（根手势 + [sheetDragZone]），
 * 不留下「看得见才点得动 / 看不见就失效」的区域依赖；alpha 由外层 Box 统一驱动
 * （超时淡出仅隐藏视觉提示，热区依旧常驻可开）。
 */
@Composable
private fun EdgeHint(dir: ChevronDir) {
    val metrics = screenMetrics()
    Box(
        modifier = Modifier.size(metrics.dp(40.dp)),
        contentAlignment = Alignment.Center,
    ) {
        MiniChevron(
            dir = dir,
            tint = Color.White.copy(alpha = 0.6f),
            size = metrics.dp(22.dp),
        )
    }
}

// ─────────────────────── 抽屉跟手手势路由 ───────────────────────

/**
 * 起点所在的三向「边缘热区带」（doc/06 §1/§5）：贴屏缘的常驻不可见窄带，带深 [EdgeBand]
 * （≈32dp，≥30dp 触控下界）。该带同时是「点按展开」与「跟手拖拽」的起点判定——带内起手点按 = 点击展开、
 * 带内向屏内同轴拖动 = 跟手拉开；带外（中央宠物活动区）一律不响应，避免大面积可拖带干扰
 * 宠物交互。顶/底带优先于右带（上/下四角归顶/底带），右带只留中段；无左带（规避系统返回）。
 */
private fun sheetDragZone(start: Offset, scope: IntSize, edgePx: Float): Panel? = when {
    start.y < edgePx -> Panel.Status
    start.y > scope.height.toFloat() - edgePx -> Panel.Action
    start.x > scope.width.toFloat() - edgePx -> Panel.Quick
    else -> null
}

/** 抽屉向外拉开方向上的累计投影位移（向屏内为正），用于换算展开比。 */
private fun sheetDragTravel(zone: Panel, acc: Offset): Float = when (zone) {
    Panel.Status -> acc.y
    Panel.Action -> -acc.y
    Panel.Quick -> -acc.x
}

/** 判定滑向与开启带是否同轴（顶/底要求竖直主导，右要求水平主导），防止横向误开。 */
private fun sheetDragDominant(zone: Panel, acc: Offset): Boolean = when (zone) {
    Panel.Status, Panel.Action -> abs(acc.y) >= abs(acc.x) * 1.25f
    Panel.Quick -> abs(acc.x) >= abs(acc.y) * 1.25f
}

/** 面板满行程（手指拉这么多即全开）：优先用本面板真实容器行程，未量得时按屏径近似。 */
private fun sheetOpenSpan(zone: Panel, extent: Map<Panel, Int>, minSidePx: Float): Float {
    extent[zone]?.let { if (it > 0) return it.toFloat() }
    return minSidePx * when (zone) {
        Panel.Status -> 0.62f
        Panel.Action -> 0.62f
        Panel.Quick -> 0.86f
    }
}
