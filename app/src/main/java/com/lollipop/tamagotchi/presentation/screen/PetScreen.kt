package com.lollipop.tamagotchi.presentation.screen

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.AttributeRegistry
import com.lollipop.tamagotchi.data.sprite.SpriteRepository
import com.lollipop.tamagotchi.domain.model.PetProfile
import com.lollipop.tamagotchi.presentation.boot.BootStage
import com.lollipop.tamagotchi.presentation.boot.ShellBridge
import com.lollipop.tamagotchi.presentation.render.PetLivingSprite
import com.lollipop.tamagotchi.presentation.component.ColorDot
import com.lollipop.tamagotchi.presentation.component.PillItem
import com.lollipop.tamagotchi.presentation.component.RoundEdgeSpace
import com.lollipop.tamagotchi.presentation.component.RoundList
import com.lollipop.tamagotchi.presentation.component.RoundListSpacer
import com.lollipop.tamagotchi.presentation.component.RoundSheet
import com.lollipop.tamagotchi.presentation.component.SheetEdge
import com.lollipop.tamagotchi.presentation.theme.BlackGlowBackground
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import com.lollipop.tamagotchi.presentation.screen.debug.DebugSpeciesGridScreen
import com.lollipop.tamagotchi.presentation.ui.RingProgressBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 三 overlay：状态（顶）/ 操作（底）/ 快捷功能（右）。一次只开一种（互斥）。 */
private enum class Panel { Status, Action, Quick }

/** 三向把手自动隐藏时长：从箭头自身显现时刻起计，到时淡出（doc/06 §1，避免常驻观感）。 */
private const val HANDLE_HINT_MS = 10_000L

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
    onSettleReady: () -> Unit,
    onTimeTravel: ((hours: Long) -> Unit)? = null,
    onResetProfile: (() -> Unit)? = null,
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

    /** 打开抽屉（箭头点击）。点击打开带滑入动画（拖拽落位已由手势直接写满）。 */
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
                            awaitEachGesture {
                                // requireUnconsumed=false：内层可点控件（debug 长按/EdgeHint）会消费 down
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val start = down.position
                                // 抽屉已开时不通过本手势再开新抽屉（收回交给箭头/点外部/返回键）
                                if (panel != null) return@awaitEachGesture
                                var last = start
                                var acc = Offset.Zero
                                var zone: Panel? = null
                                val engagePx = 14.dp.toPx()
                                val abandonPx = 28.dp.toPx()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val moved = change.position - last
                                    acc += moved
                                    last = change.position
                                    if (!event.changes.any { it.pressed }) {
                                        // 松手：过半保留，否则弹回收起。
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
                                        }
                                        break
                                    }
                                    if (zone == null) {
                                        val cand = sheetDragZone(start, size)
                                        if (cand != null && sheetDragDominant(cand, acc) &&
                                            sheetDragTravel(cand, acc) >= engagePx
                                        ) {
                                            zone = cand
                                            panel = cand // 先挂载（此刻全收起），随后跟手指逐帧展开
                                            reveal = 0f
                                        } else if (acc.getDistance() > abandonPx) {
                                            break // 起点方向不符 / 不在三向带：非抽屉手势
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
            val minSide = if (maxWidth < maxHeight) maxWidth else maxHeight
            val screenR = minSide / 2
            // 环近贴屏缘充当刻度环（外缘距屏缘 4dp 呼吸位）
            val ringOuter = screenR - 4.dp
            // 三向把手 = 上层 overlay 箭头，贴主环内沿悬浮（环段图标内缘≈ringOuter-9，箭头外缘≈ringOuter-11）
            // 宠物活动范围=全屏底层，可与环/把手重叠穿过（M4.S2 布局调整）
            val handleR = ringOuter - 22.dp

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
                    .size(minSide)
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
                )
            }

            // ── 三向手势把手（白色半透明小箭头，overlay 置顶层、贴主环内沿）──
            // 顶▼（下拉=状态）/ 底▲（上滑=操作）/ 右◀（左滑=快捷），r≈0.77 屏半径
            // 各自从所属层显现起计 10s 后自动淡出（见上 hintHidden*）；淡出后不可见（不可点），
            // 打开面板等操作通过边缘滑动手势/面板内收起钮完成，不再依赖把手复现。
            // （宠物在最底层移动，可从把手下方经过；把手可点热区 ≥30dp，本容器透明不挡宠物 hit）
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = -handleR)
                    .alpha(topHintAlpha),
            ) {
                EdgeHint(
                    dir = ChevronDir.Down,
                    enabled = !hintHiddenTop,
                    onClick = { openSheet(Panel.Status) },
                )
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = handleR)
                    .alpha(bottomAlpha),
            ) {
                EdgeHint(
                    dir = ChevronDir.Up,
                    enabled = !hintHiddenBottom,
                    onClick = { openSheet(Panel.Action) },
                )
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = handleR)
                    .alpha(quickAlpha),
            ) {
                EdgeHint(
                    dir = ChevronDir.Left,
                    enabled = !hintHiddenQuick,
                    onClick = { openSheet(Panel.Quick) },
                )
            }

            // ── Overlay 层（置顶，互斥）────────────────────────
            // 面板挂载 = panel 非空；reveal（0..1）决定整块面板平移出/入屏的程度。
            // reveal 以 lambda 传给 graphicsLayer，只触发重绘、不引发整树重组。
            val mounted = panel
            if (mounted != null) {
                OverlayLayer(
                    p = mounted,
                    profile = profile,
                    onResetProfile = onResetProfile,
                    reveal = { reveal },
                    sheetExtent = sheetExtent,
                    onDismiss = { closeSheet() },
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
        }
    }
}

// ───────────────────────── Overlay 层 ─────────────────────────

@Composable
private fun BoxScope.OverlayLayer(
    p: Panel,
    profile: PetProfile,
    onResetProfile: (() -> Unit)?,
    reveal: () -> Float,
    sheetExtent: SnapshotStateMap<Panel, Int>,
    onDismiss: () -> Unit,
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
                    StatusPanelBody(profile = profile, onDismiss = onDismiss)
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
                    ActionPanelBody(onDismiss)
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
        Box(
            Modifier
                .size(30.dp)
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

/** 状态面板（顶）：滚动列表在上，对侧（底部）固定收起条，列表滚动不影响收起条。 */
@Composable
private fun ColumnScope.StatusPanelBody(profile: PetProfile, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .weight(1f),
    ) {
        RoundList {
            RoundEdgeSpace(48.dp)
            PanelTitle("状态 · ${profile.petName}")
            AttributeRegistry.all.forEachIndexed { index, meta ->
                if (index > 0) RoundListSpacer()
                PillItem(
                    "${meta.id.label}  ${profile.attributes[meta.id].roundToInt()}",
                    filled = false,
                    icon = { ColorDot(attributeColor(meta.id)) },
                    onClick = null,
                )
            }
            RoundListSpacer()
            Text(
                "建档快照实时值 · 衰减/冷却随结算推进（M5/M6）",
                color = ColorToken.Text2,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
            RoundEdgeSpace(48.dp)
        }
    }
    DismissStrip(dir = ChevronDir.Up, onDismiss)
}

/** 属性 → 主题色（与主环/建档预览同源，doc/01 §3）。 */
@Composable
private fun attributeColor(id: AttributeId): Color = when (id) {
    AttributeId.SATIATION -> ColorToken.Satiation
    AttributeId.MOOD -> ColorToken.Mood
    AttributeId.HEALTH -> ColorToken.Health
    AttributeId.INTELLIGENCE -> ColorToken.Intelligence
    AttributeId.HYGIENE -> ColorToken.Hygiene
}

/** 操作面板（底）：对侧（顶部）固定收起条 + 下方滚动列表，列表滚动不影响收起条。 */
@Composable
private fun ColumnScope.ActionPanelBody(onDismiss: () -> Unit) {
    DismissStrip(dir = ChevronDir.Down, onDismiss)
    Box(
        Modifier
            .fillMaxWidth()
            .weight(1f),
    ) {
        RoundList {
            RoundEdgeSpace(48.dp)
            PanelTitle("操作 · 占位")
            PillItem("投喂 · 占位", filled = true, color = ColorToken.Satiation, onClick = null)
            RoundListSpacer()
            PillItem("玩耍 · 占位", filled = true, color = ColorToken.Mood, onClick = null)
            RoundListSpacer()
            PillItem("抚摸 · 占位", filled = false, onClick = null)
            RoundListSpacer()
            Text(
                "M6 接真实动作与冷却",
                color = ColorToken.Text2,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
            )
            RoundEdgeSpace(48.dp)
        }
    }
}

/** 功能面板（右）：对侧（左缘）固定竖收起条 + 右侧滚动列表，列表滚动不影响收起条。 */
@Composable
private fun ColumnScope.QuickPanelBody(
    onDismiss: () -> Unit,
    onResetProfile: (() -> Unit)?,
) {
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
            RoundList {
                RoundEdgeSpace()
                PanelTitle("功能 · 占位")
                PillItem("通讯呼叫 · M8", filled = false, onClick = null)
                RoundListSpacer()
                PillItem("休闲小游戏 · M16", filled = false, onClick = null)
                RoundListSpacer()
                Text(
                    "原生功能清单（doc/05）",
                    color = ColorToken.Text2,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                if (onResetProfile != null) {
                    RoundListSpacer()
                    PillItem(
                        "重开档 · Debug",
                        filled = false,
                        color = ColorToken.Warn,
                        onClick = {
                            onDismiss()
                            onResetProfile()
                        },
                    )
                }
                RoundEdgeSpace()
            }
        }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight()
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            MiniChevron(dir = dir, tint = ColorToken.Text2, size = 20.dp)
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
    Box(
        modifier = Modifier
            .width(36.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            MiniChevron(dir = dir, tint = ColorToken.Text2, size = 20.dp)
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
 * 手势把手：白色半透明单箭头（无底色/描边/标签），提示该方向边缘可滑入面板；点击热区透明但足够大。
 * @param enabled false = 自动隐藏期（淡出中/已隐藏）：箭头透明、点击穿透下层（不吞手势），
 *   不留下「看不见却能点开面板」的区域；alpha 由外层 Box 统一驱动。
 */
@Composable
private fun EdgeHint(dir: ChevronDir, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MiniChevron(
            dir = dir,
            tint = Color.White.copy(alpha = 0.6f),
            size = 22.dp,
        )
    }
}

// ─────────────────────── 抽屉跟手手势路由 ───────────────────────

/** 起点所在的三向开启带（顶缘下拉=状态 / 下缘上滑=操作 / 右缘左滑=功能）。 */
private fun sheetDragZone(start: Offset, scope: IntSize): Panel? = when {
    start.y < scope.height * 0.40f -> Panel.Status
    start.y > scope.height * 0.60f -> Panel.Action
    start.x > scope.width * 0.55f -> Panel.Quick
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
