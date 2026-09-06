package com.lollipop.tamagotchi.presentation.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lollipop.tamagotchi.presentation.theme.ColorToken

/**
 * 圆屏安全区（doc/06 §8.2）：
 * 内容横向内缩安全边距，保证胶囊/文字不被圆屏缘裁切；屏缘装饰性元素另以 [roundEdgeFade] 渐隐。
 * 运行换算按 min(screenW, screenH) 取固定比例（含真机校准预留）。
 */
@Composable
fun roundSafeInset(): Dp = screenMetrics().edgeSafeInset

/**
 * 列表/滚动容器上下缘渐隐（EdgeFade）：列表越出可视区的内容向屏幕缘渐隐为黑，避免硬裁切。
 * 仅对滚动可视窗口生效，绘制在内容之上、随容器高度变化。
 */
fun Modifier.roundEdgeFade(top: Dp = 32.dp, bottom: Dp = 32.dp): Modifier = this.drawWithContent {
    drawContent()
    val t = top.toPx()
    val b = bottom.toPx()
    if (t > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(ColorToken.bg, Color.Transparent),
                startY = 0f,
                endY = t,
            ),
            topLeft = Offset.Zero,
            size = Size(size.width, t),
        )
    }
    if (b > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, ColorToken.bg),
                startY = size.height - b,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - b),
            size = Size(size.width, b),
        )
    }
}

/** 隔行小间距（RoundList 统一行距），避免各页自行拍脑袋写 4/8/12。 */
@Composable
fun RoundListSpacer() {
    Spacer(Modifier.fillMaxWidth().height(4.dp))
}
