package com.lollipop.tamagotchi.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.Path
import com.lollipop.tamagotchi.core.attribute.AttributeId
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
    attributes: List<AttributeId> = listOf(AttributeId.SATIATION, AttributeId.MOOD, AttributeId.HEALTH),
    strokeWidth: Dp = 6.dp,
    iconSize: Dp = 12.dp,
    iconGap: Dp = 4.dp,
) {
    val n = values.size
    require(n == colors.size) { "主环弧段与配色数量一致" }
    require(n == attributes.size) { "主环弧段与属性数量一致" }
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
                drawAttributeGlyph(
                    id = attributes[i],
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

/**
 * 行内迷你进度环（doc/06 §3.2 状态行「迷你进度条」——用户拍板用环形、置于行尾）：
 * 底轨 = 全圆低透明槽道，前景按 value/100 从 12 点起顺时针覆盖，圆头端帽；
 * 属性行随值直观显示余量；数值仍由行文本承载（环内不放字，规避圆形内小字裁切，
 * 见 doc/06 §8 最小字号约束）。低值警示由调用方换色（M6.S2 预警呼吸阶段接入）。
 */
@Composable
fun MiniProgressRing(
    value: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Dp = 3.5.dp,
    warn: Boolean = false,
) {
    val frac = (value / 100f).coerceIn(0f, 1f)
    // 低值预警（warn）：前景弧换告警色 + 2Hz 呼吸（doc/06 §2/§3.2；弧/轨为装饰性描边，允许低 alpha）
    val ringColor = if (warn) ColorToken.Warn else color
    var pulse by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(warn) {
        while (warn) {
            pulse = 0.45f
            kotlinx.coroutines.delay(500)
            pulse = 1f
            kotlinx.coroutines.delay(500)
        }
        pulse = 1f
    }
    val alpha = if (warn) pulse else 1f
    Canvas(modifier.size(size)) {
        val strokePx = strokeWidth.toPx()
        val radius = (this.size.width - strokePx) / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        // 底轨：全圆低透明槽道
        drawCircle(
            color = ringColor.copy(alpha = 0.16f * alpha),
            radius = radius,
            center = center,
            style = Stroke(width = strokePx),
        )
        // 前景：自 12 点按 frac 覆盖
        if (frac > 0f) {
            drawArc(
                color = ringColor.copy(alpha = alpha),
                startAngle = -90f,
                sweepAngle = 360f * frac,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * 段起点小图标（碗=饱腹 / 气球=心情 / 十字=健康 / 水滴=清洁 / 五角星=智力），
 * 极简几何占位，与状态面板共用同一套字形：统一 [side] 盒、居中绘制，保证各图标视觉大小一致，
 * 便于用户按形状对照（doc/06 §3.1）。M3+ 若接入规范图标素材，仅需在此替换几何实现。
 */
internal fun DrawScope.drawAttributeGlyph(id: AttributeId, center: Offset, tint: Color, side: Float) {
    val left = center.x - side / 2f
    val top = center.y - side / 2f
    val w = side
    when (id) {
        // 碗（饱腹）：碗口线 + 下弧
        AttributeId.SATIATION -> {
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
        AttributeId.MOOD -> {
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
        AttributeId.HEALTH -> {
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
        // 水滴（清洁；面板专属，主环无此段）
        AttributeId.HYGIENE -> {
            val path = Path().apply {
                moveTo(center.x, top + w * 0.12f)
                cubicTo(left + w * 0.04f, top + w * 0.52f, left + w * 0.20f, top + w * 0.90f, center.x, top + w * 0.90f)
                cubicTo(left + w * 0.80f, top + w * 0.90f, left + w * 0.96f, top + w * 0.52f, center.x, top + w * 0.12f)
                close()
            }
            drawPath(path, tint)
        }
        // 五角星（智力；面板专属，主环无此段）
        AttributeId.INTELLIGENCE -> {
            val outer = w * 0.40f
            val inner = w * 0.17f
            val cx = center.x
            val cy = center.y
            val star = Path().apply {
                for (i in 0 until 10) {
                    val ang = -Math.PI / 2 + Math.PI / 5 * i
                    val rad = if (i % 2 == 0) outer else inner
                    val x = (cx + rad * Math.cos(ang)).toFloat()
                    val y = (cy + rad * Math.sin(ang)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(star, tint)
        }
    }
}
