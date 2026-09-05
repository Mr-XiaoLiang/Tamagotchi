package com.lollipop.tamagotchi.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lollipop.tamagotchi.presentation.theme.ColorToken
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/**
 * 主环 RingProgressBar（doc/06 §2 布局修正）：
 *  - 圆形排列 = 图标→进度条→图标→进度条→…回环到首个图标；
 *    图标中心与**进度条宽度中心同半径**（骑在环带宽正中，不向内缩）；
 *  - 每段弧的**扫过角度不再是固定 120°**：图标均分圆周作「段界」，槽位角 = 360°/N，
 *    弧段只占据**减去两侧图标角向占位、间距与圆头探出之后的剩余角度**；
 *  - 底轨 = 该可用角全量（低透明度），前景按 value/100 覆盖，均圆头端帽；
 *  - [outerRadius]：环外缘半径（调用方传入「屏半径 - 贴边距」，使环接近屏幕边缘充当刻度环）。
 * M1 假值三段（68 / 74 / 81），低值预警/闪烁动画在 M7 属性告急接入。
 */
@Composable
fun RingProgressBar(
    outerRadius: Dp,
    modifier: Modifier = Modifier,
    values: List<Float> = listOf(68f, 74f, 81f),
    colors: List<Color> = listOf(ColorToken.Satiation, ColorToken.Mood, ColorToken.Health),
    strokeWidth: Dp = 6.dp,
    iconSize: Dp = 12.dp,
    iconGap: Dp = 4.dp,
) {
    val n = values.size
    require(n == colors.size) { "主环弧段与配色数量一致" }
    require(n >= 1) { "主环至少一段" }

    val canvasSize = outerRadius * 2

    Box(modifier = modifier) {
        Canvas(Modifier.size(canvasSize)) {
            val strokePx = strokeWidth.toPx()
            val iconPx = iconSize.toPx()
            val gapPx = iconGap.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            // 环中线半径（外缘 outerRadius - 半笔宽）
            val midR = outerRadius.toPx() - strokePx / 2f
            // 图标中心半径 = 进度条宽度中心半径：图标骑在环带宽正中（不再内缩）
            val iconR = midR

            val slotDeg = 360f / n
            // 图标角向半宽：方图标向心侧顶点离圆心最近、张角最大，
            // 故用「向心侧距离 = iconR - iconPx/2」作分母（保守，避免弧端压到图标）
            val iconHalfDeg = (atan((iconPx / 2f) / (iconR - iconPx / 2f)) * 180f / PI).toFloat()
            // 间距沿环中线的角宽
            val gapDeg = (atan(gapPx / midR) * 180f / PI).toFloat()
            // 圆头端帽超出名义端点的半笔宽角宽
            val capDeg = (atan((strokePx / 2f) / midR) * 180f / PI).toFloat()
            // 单侧保留角 = 图标半宽 + 间距 + 圆头探出 → 弧端可见端帽恰好停在图标外 gap 处
            val reserveDeg = iconHalfDeg + gapDeg + capDeg
            // 弧段可用角 = 槽位角 - 两侧保留角（小于 120° 的剩余区）
            val spanDeg = (slotDeg - 2f * reserveDeg).coerceAtLeast(0f)

            for (i in 0 until n) {
                // 图标中心角（段起点/段界）
                val boundary = -90f + slotDeg * i
                val frac = (values[i] / 100f).coerceIn(0f, 1f)
                val trackColor = colors[i].copy(alpha = 0.16f)
                // 弧段从「越过图标占位区」后开始
                val arcStart = boundary + reserveDeg

                // 底轨（可用角全量，图标两侧留白、各段互不粘连）
                drawArc(
                    color = trackColor,
                    startAngle = arcStart,
                    sweepAngle = spanDeg,
                    useCenter = false,
                    topLeft = Offset(center.x - midR, center.y - midR),
                    size = Size(midR * 2, midR * 2),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
                // 前景（按值覆盖，扫过角度 ≤ 可用角）
                if (frac > 0f) {
                    drawArc(
                        color = colors[i],
                        startAngle = arcStart,
                        sweepAngle = spanDeg * frac,
                        useCenter = false,
                        topLeft = Offset(center.x - midR, center.y - midR),
                        size = Size(midR * 2, midR * 2),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
                // 段界小图标（绘于段起点）
                val rad = boundary * PI / 180.0
                drawMiniGlyph(
                    index = i,
                    center = Offset(
                        center.x + (cos(rad) * iconR).toFloat(),
                        center.y + (sin(rad) * iconR).toFloat(),
                    ),
                    tint = colors[i],
                    side = iconPx,
                )
            }
        }
    }
}

/** 段起点小图标（碗 / 气球 / 十字），极简几何占位，M3+ 换规范图标素材。 */
private fun DrawScope.drawMiniGlyph(index: Int, center: Offset, tint: Color, side: Float) {
    val left = center.x - side / 2f
    val top = center.y - side / 2f
    val w = side
    when (index) {
        // 碗（饱腹）：侧视碗沿线 + 下弧
        0 -> {
            val lineW = w * 0.16f
            drawLine(
                color = tint,
                start = Offset(left + w * 0.08f, top + w * 0.40f),
                end = Offset(left + w * 0.92f, top + w * 0.40f),
                strokeWidth = lineW,
                cap = StrokeCap.Round,
            )
            drawArc(
                color = tint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(left + w * 0.10f, top + w * 0.40f),
                size = Size(w * 0.8f, w * 0.8f),
                style = Stroke(width = lineW, cap = StrokeCap.Round),
            )
        }
        // 气球（心情）：圆 + 两撇绳
        1 -> {
            val lineW = w * 0.12f
            drawCircle(
                color = tint,
                radius = w * 0.26f,
                center = Offset(center.x, top + w * 0.36f),
                style = Stroke(width = lineW),
            )
            drawLine(
                color = tint,
                start = Offset(center.x, top + w * 0.60f),
                end = Offset(center.x - w * 0.14f, top + w * 0.96f),
                strokeWidth = lineW * 0.8f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = tint,
                start = Offset(center.x, top + w * 0.60f),
                end = Offset(center.x + w * 0.14f, top + w * 0.96f),
                strokeWidth = lineW * 0.8f,
                cap = StrokeCap.Round,
            )
        }
        // 十字（健康）
        else -> {
            val lineW = w * 0.16f
            drawLine(
                color = tint,
                start = Offset(center.x, top + w * 0.14f),
                end = Offset(center.x, top + w * 0.86f),
                strokeWidth = lineW,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = tint,
                start = Offset(left + w * 0.14f, center.y),
                end = Offset(left + w * 0.86f, center.y),
                strokeWidth = lineW,
                cap = StrokeCap.Round,
            )
        }
    }
}
