package com.lollipop.tamagotchi.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lollipop.tamagotchi.presentation.boot.BootStage
import com.lollipop.tamagotchi.presentation.boot.ShellBridge
import com.lollipop.tamagotchi.presentation.component.ColorDot
import com.lollipop.tamagotchi.presentation.component.PillItem
import com.lollipop.tamagotchi.presentation.component.RoundList
import com.lollipop.tamagotchi.presentation.component.RoundListSpacer
import com.lollipop.tamagotchi.presentation.component.RoundSheet
import com.lollipop.tamagotchi.presentation.component.SheetEdge
import com.lollipop.tamagotchi.presentation.theme.BlackGlowBackground
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import com.lollipop.tamagotchi.presentation.ui.RingProgressBar
import kotlin.math.abs

/** 三 overlay：状态（顶）/ 操作（底）/ 快捷功能（右）。一次只开一种（互斥）。 */
private enum class Panel { Status, Action, Quick }

/**
 * 主屏四区 + 三 overlay 路由（doc/06 §1/§2/§5，Task.md M1.S2）。
 *
 * 四区：顶部状态图标区（主环段起点图标）→ 中央活动区（宠物占位）→ 主环 → 底部入口条；
 * 三 overlay：顶下拉=状态、底上滑=操作、右左滑=快捷清单；三个方向各自带「反方向单箭头」收回钮
 * （Material Symbols single_arrow 字形，点击即收起，像抽屉推回），另有点遮罩 / 返回键兜底；背景不透明黑保证可读。
 * M1 全部为占位内容；真实属性值 M3、宠物 M2、动作 M6 起接入。
 */
@Composable
fun PetScreen(onSettleReady: () -> Unit) {
    var stage by remember { mutableStateOf(BootStage.Shell) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    // 记录最近一次可见面板：退出动画期间 content 仍按旧面板绘制
    var lastPanel by remember { mutableStateOf<Panel?>(null) }

    fun open(p: Panel) {
        lastPanel = p
        panel = p
    }

    LaunchedEffect(Unit) {
        ShellBridge.reveal { stage = it }
        onSettleReady()
    }
    BackHandler(enabled = panel != null) { panel = null }

    BlackGlowBackground(glow = Color.White.copy(alpha = 0.04f)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(if (panel == null) Modifier.edgeSwipeToOpen { open(it) } else Modifier),
        ) {
            val minSide = if (maxWidth < maxHeight) maxWidth else maxHeight
            val screenR = minSide / 2
            // 环近贴屏缘充当刻度环（外缘距屏缘 4dp 呼吸位）
            val ringOuter = screenR - 4.dp
            // 三向手势把手：悬浮于宠物区与主环之间的空带（r≈0.30 屏半径，不占环位/屏缘）
            val handleR = minSide * 0.30f

            val quickAlpha by animateFloatAsState(
                targetValue = if (stage >= BootStage.Quick) 1f else 0f,
                animationSpec = tween(220),
                label = "quick",
            )
            val petAlpha by animateFloatAsState(
                targetValue = if (stage >= BootStage.Pet) 1f else 0f,
                animationSpec = tween(220),
                label = "pet",
            )
            val statusAlpha by animateFloatAsState(
                targetValue = if (stage >= BootStage.Status) 1f else 0f,
                animationSpec = tween(220),
                label = "status",
            )
            val bottomAlpha by animateFloatAsState(
                targetValue = if (stage >= BootStage.Settle) 1f else 0f,
                animationSpec = tween(220),
                label = "bottom",
            )

            // ── 中央活动区（宠物占位，M2 接真宠）──────────────────
            // R_pet ≈ 0.30 屏半径（直径 0.60×screenR）
            val petDia = minSide * 0.30f
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(petAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(petDia)
                        .border(1.dp, ColorToken.Text2.copy(alpha = 0.25f), CircleShape),
                ) {
                    Box(Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)) {
                        Text(
                            "R宠物",
                            color = ColorToken.Text2.copy(alpha = 0.35f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "活动区 · M2 接真宠",
                    color = ColorToken.Text2.copy(alpha = 0.55f),
                    fontSize = 9.sp,
                )
            }

            // ── 主环（贴边刻度环；段起点图标在环内侧）──────────
            Box(
                Modifier
                    .align(Alignment.Center)
                    .alpha(statusAlpha),
            ) {
                RingProgressBar(outerRadius = ringOuter)
            }

            // ── 三向手势把手（瘦身悬浮于宠物区与主环空带，不挤环）──
            // 顶「▼ 状态」/ 底「▲ 操作」/ 右「◀ 功能」，r≈0.60 屏半径
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = -handleR)
                    .alpha(statusAlpha),
            ) {
                EdgeHint(text = "状态", dir = ChevronDir.Down, onClick = { open(Panel.Status) })
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = handleR)
                    .alpha(bottomAlpha),
            ) {
                EdgeHint(text = "操作", dir = ChevronDir.Up, onClick = { open(Panel.Action) })
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = handleR)
                    .alpha(quickAlpha),
            ) {
                EdgeHint(text = "功能", dir = ChevronDir.Left, onClick = { open(Panel.Quick) })
            }

            // ── Overlay 层（置顶，互斥）────────────────────────
            val shown = panel ?: lastPanel
            if (shown != null) {
                AnimatedVisibility(
                    visible = panel != null,
                    enter = panelEnter(panel ?: shown),
                    exit = panelExit(lastPanel ?: shown),
                ) {
                    OverlayLayer(shown, onDismiss = { panel = null })
                }
            }
        }
    }
}

// ───────────────────────── Overlay 层 ─────────────────────────

@Composable
private fun BoxScope.OverlayLayer(p: Panel, onDismiss: () -> Unit) {
    val edge = when (p) {
        Panel.Status -> SheetEdge.Top
        Panel.Action -> SheetEdge.Bottom
        Panel.Quick -> SheetEdge.End
    }
    // 背景不透明黑：面板区不再透出主屏内容（可读性），点遮罩任意处收起（返回键兜底见 BackHandler）
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(ColorToken.bg)
                .clickable { onDismiss() },
        )
        when (p) {
            Panel.Status -> Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            ) {
                RoundSheet(edge = edge, glow = ColorToken.Health.copy(alpha = 0.07f)) {
                    StatusPanelContent(onDismiss)
                }
            }

            Panel.Action -> Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                RoundSheet(edge = edge, glow = ColorToken.Satiation.copy(alpha = 0.08f)) {
                    ActionPanelContent(onDismiss)
                }
            }

            Panel.Quick -> Box(
                Modifier
                    .fillMaxWidth(0.86f)
                    .fillMaxSize()
                    .align(Alignment.CenterEnd),
            ) {
                RoundSheet(edge = edge, glow = ColorToken.Mood.copy(alpha = 0.07f)) {
                    QuickPanelContent(onDismiss)
                }
            }
        }
    }
}

/**
 * 面板头部：标题 + 反方向单箭头收回钮（Material Symbols single_arrow，
 * doc/06 §5「面板自身反方向滑回」的可见把手）。
 * [closeDir] = 该面板收回方向（顶面板=Up / 下面板=Down / 右面板=Right），点击即收起。
 */
@Composable
private fun RoundHeader(title: String, closeDir: ChevronDir, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = ColorToken.Accent.copy(alpha = 0.9f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(ColorToken.Accent.copy(alpha = 0.08f))
                .border(1.dp, ColorToken.Text2.copy(alpha = 0.22f), CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            MiniChevron(dir = closeDir, tint = ColorToken.Text2, size = 11.dp)
        }
    }
}

@Composable
private fun StatusPanelContent(onDismiss: () -> Unit) {
    RoundHeader("状态 · 占位", ChevronDir.Up, onDismiss)
    RoundList {
        PillItem(
            "饱腹  68",
            filled = false,
            icon = { ColorDot(ColorToken.Satiation) },
            onClick = null,
        )
        RoundListSpacer()
        PillItem(
            "心情  74",
            filled = false,
            icon = { ColorDot(ColorToken.Mood) },
            onClick = null,
        )
        RoundListSpacer()
        PillItem(
            "健康  81",
            filled = false,
            icon = { ColorDot(ColorToken.Health) },
            onClick = null,
        )
        RoundListSpacer()
        Text(
            "M3 接入实时数值 / 低值预警",
            color = ColorToken.Text2.copy(alpha = 0.45f),
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun ActionPanelContent(onDismiss: () -> Unit) {
    RoundHeader("操作 · 占位", ChevronDir.Down, onDismiss)
    RoundList {
        PillItem("投喂 · 占位", filled = true, color = ColorToken.Satiation, onClick = null)
        RoundListSpacer()
        PillItem("玩耍 · 占位", filled = true, color = ColorToken.Mood, onClick = null)
        RoundListSpacer()
        PillItem("抚摸 · 占位", filled = false, onClick = null)
        RoundListSpacer()
        Text(
            "M6 接真实动作与冷却",
            color = ColorToken.Text2.copy(alpha = 0.45f),
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun QuickPanelContent(onDismiss: () -> Unit) {
    RoundHeader("功能 · 占位", ChevronDir.Right, onDismiss)
    RoundList {
        PillItem("通讯呼叫 · M8", filled = false, onClick = null)
        RoundListSpacer()
        PillItem("休闲小游戏 · M16", filled = false, onClick = null)
        RoundListSpacer()
        Text(
            "原生功能清单（doc/05）",
            color = ColorToken.Text2.copy(alpha = 0.45f),
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
    }
}

// ──────────────────── Material Symbols 单箭头 + 手势把手（瘦身版） ────────────────────

/** 方向指示（旋转 single_arrow 字形得到，doc/06 §5）。 */
private enum class ChevronDir { Up, Down, Left, Right }

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
private fun MiniChevron(
    dir: ChevronDir,
    tint: Color,
    size: Dp = 12.dp,
    modifier: Modifier = Modifier,
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

/** 小把手：Material Symbols 单箭头 + 词，悬浮于中央宠物区与贴边主环之间的空带。 */
@Composable
private fun EdgeHint(text: String, dir: ChevronDir, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(ColorToken.bg.copy(alpha = 0.35f))
            .border(1.dp, ColorToken.Text2.copy(alpha = 0.3f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniChevron(
            dir = dir,
            tint = ColorToken.Text2.copy(alpha = 0.8f),
            size = 9.dp,
        )
        Spacer(Modifier.width(3.dp))
        Text(text, color = ColorToken.Text2.copy(alpha = 0.8f), fontSize = 9.sp)
    }
}

// ───────────────────────── 手势路由 ─────────────────────────

private fun panelEnter(p: Panel) = when (p) {
    Panel.Status -> slideInVertically(tween(240)) { -it } + fadeIn(tween(200))
    Panel.Action -> slideInVertically(tween(240)) { it } + fadeIn(tween(200))
    Panel.Quick -> slideInHorizontally(tween(240)) { it } + fadeIn(tween(200))
}

private fun panelExit(p: Panel) = when (p) {
    Panel.Status -> slideOutVertically(tween(200)) { -it } + fadeOut(tween(160))
    Panel.Action -> slideOutVertically(tween(200)) { it } + fadeOut(tween(160))
    Panel.Quick -> slideOutHorizontally(tween(200)) { it } + fadeOut(tween(160))
}

/**
 * 边缘手势：仅 overlay 关闭时监听。
 *  顶缘 1/3 下拉 → 状态；底缘 2/5 上滑 → 操作；右 45% 左滑 → 快捷。
 * 判定前不消费事件（不干扰胶囊点击），判定成功后消费剩余滑动。
 */
private fun Modifier.edgeSwipeToOpen(onOpen: (Panel) -> Unit): Modifier = pointerInput(Unit) {
    val minSwipe = 38.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        val start = down.position
        var last = start
        var acc = Offset.Zero
        var opened = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            // 用相邻帧 position 差累计位移，判定为整段滑动方向与距离
            acc += change.position - last
            last = change.position
            if (!opened) {
                val target = classifyPanel(start, acc, size, minSwipe)
                if (target != null) {
                    opened = true
                    onOpen(target)
                }
            }
            if (!event.changes.any { it.pressed }) break
        }
    }
}

private fun classifyPanel(
    start: Offset,
    acc: Offset,
    scope: IntSize,
    minSwipe: Float,
): Panel? {
    if (acc.getDistance() < minSwipe) return null
    val horizontal = abs(acc.x) > abs(acc.y) * 1.2f
    return when {
        horizontal && acc.x < 0f && start.x > scope.width * 0.55f -> Panel.Quick
        !horizontal && acc.y < 0f && start.y > scope.height * 0.60f -> Panel.Action
        !horizontal && acc.y > 0f && start.y < scope.height * 0.40f -> Panel.Status
        else -> null
    }
}
