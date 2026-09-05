package com.lollipop.tamagotchi.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import kotlin.math.min

/** Overlay 面板的四个去向（doc/06 §5）：顶（状态）/ 底（操作）/ 右（快捷）。 */
enum class SheetEdge { Top, Bottom, Start, End }

/**
 * RoundSheet —— overlay 内容容器（doc/06 §8.3）。
 * 功能页允许以同心圆径向渐变作主题背景（doc/06 §4.2）：中心 = [glow] 页面色 → 向屏缘收敛黑；
 * 内容横向内缩圆屏安全边距 [roundSafeInset]，避免被屏缘裁切。
 */
@Composable
fun RoundSheet(
    edge: SheetEdge,
    modifier: Modifier = Modifier,
    glow: Color = Color.White.copy(alpha = 0.04f),
    content: @Composable ColumnScope.() -> Unit,
) {
    // 内容一律自上而下排列：面板「对侧固定收起条 + 滚动列表区」的先后由调用方按其布局组装，
    // 滚动区带 weight(1f) 会把整块占满；edge 仅决定背景晕光与语义，不再影响排列方向。
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .glowFrom(edge, glow)
            .padding(horizontal = roundSafeInset(), vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

/** 面板向 [edge] 方向晕开页面主题色，渐隐到黑（面板背景，非内容）。 */
private fun Modifier.glowFrom(edge: SheetEdge, glow: Color): Modifier = this.drawBehind {
    val minSide = min(size.width, size.height)
    val center = when (edge) {
        SheetEdge.Top -> Offset(size.width / 2f, 0f)
        SheetEdge.Bottom -> Offset(size.width / 2f, size.height)
        SheetEdge.Start -> Offset(0f, size.height / 2f)
        SheetEdge.End -> Offset(size.width, size.height / 2f)
    }
    val glowColor = if (glow == ColorToken.bg) ColorToken.pageGlowPlaceholder else glow
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to glowColor,
                0.45f to glowColor.copy(alpha = glowColor.alpha * 0.4f),
                1f to Color.Transparent,
            ),
            center = center,
            radius = minSide * 0.9f,
        ),
        radius = minSide * 0.9f,
        center = center,
    )
}
