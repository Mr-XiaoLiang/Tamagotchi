package com.lollipop.tamagotchi.presentation.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 页面主题背景（doc/06 §4.2）：
 *  - 基底全屏纯黑 [ColorToken.bg]（保证外圈与设备黑边零色差）；
 *  - 圆心一处同心圆径向渐变：中心 = 页面低饱和深色调 [glow]，随半径收敛到黑；
 *  - [glowRadiusFraction] 为渐变半径占屏幕半径的比例（0..1）。
 *
 * 主屏（宠物常驻）只给中央极淡辉光；功能页可用更强的页面色相做主题背景。
 */
@Composable
fun BlackGlowBackground(
    modifier: Modifier = Modifier,
    glow: Color = Color.White.copy(alpha = 0.05f),
    glowRadiusFraction: Float = 1.0f,
    content: @Composable () -> Unit = {},
) {
    Box(modifier.background(ColorToken.bg).then(modifier)) {
        Canvas(Modifier.fillMaxSize()) {
            val minSide = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            // 渐变终点色即 bg，径向渐变半径之内线性收敛到纯黑；
            // 半径之外无渐变 → 露出底层 bg，外圈恒为 #000000。
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow, ColorToken.bg),
                    center = center,
                    radius = minSide / 2f * glowRadiusFraction.coerceIn(0.1f, 1.2f),
                ),
                radius = minSide / 2f * glowRadiusFraction.coerceIn(0.1f, 1.2f),
                center = center,
            )
        }
        content()
    }
}
