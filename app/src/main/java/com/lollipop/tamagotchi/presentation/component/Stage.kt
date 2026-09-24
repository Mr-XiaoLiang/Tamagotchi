package com.lollipop.tamagotchi.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import com.lollipop.tamagotchi.presentation.theme.ColorToken

/**
 * 舞台口径的 [ScreenMetrics]：由 [WatchStage] 注入（恒为 [AdaptTokens.DESIGN_WIDTH] 见方）。
 *
 * 未处于舞台内时为 `null`，[screenMetrics] 退回真实屏幕推导（兜底）。
 */
val LocalStageMetrics = staticCompositionLocalOf<ScreenMetrics?> { null }

/** 舞台缩放下限：纯防御（异常 constraints 下不至于算出 0 或负）；正常路径不会触及。 */
private const val STAGE_SCALE_MIN = 0.1f

/**
 * 圆形舞台：把整个界面锁进 **1:1 的圆**里、居中，并按设计 DPI **整体等比缩放**（doc/06 §8）。
 *
 * ### 为什么需要
 * 本工程所有布局以「圆表短边」为唯一基准：主环半径、三向把手、状态图标行、列表留白
 * 都由 [screenMetrics]（短边推导）算出，**圆内接安全区**是隐含前提。手机 / 平板这类
 * 非 1:1 屏的短边是 360dp 甚至 800dp，那套公式会把元素**各自**拉伸：主环变大、列表
 * 留白变夸张（长边一半！），圆形安全区前提直接失效。
 *
 * ### 做法：整体缩放，而非逐项适配
 *  1. **内部坐标系恒为 [AdaptTokens.DESIGN_WIDTH] 见方** —— 通过替换 [LocalDensity] 实现。
 *     刻意**不用 `Modifier.graphicsLayer { scale }`**：那只改绘制，触摸坐标不跟着变换，
 *     三向抽屉的跟手拖拽会整体错位。改 density 则是 layout 层面的换算，点击/拖拽都正确。
 *  2. **缩放比 = 可用短边 / 基准边长**，封顶 [AdaptTokens.SCALE_MAX]：手机填满短边不浪费，
 *     平板/大屏不过分放大。舞台外露出的部分填纯黑（与舞台同色），视觉无缝。
 *  3. **文字与图形一起等比**：density 换了之后 dp 与 sp 同步换算，不存在「字放大了图没跟上」。
 *     系统的字体缩放偏好（fontScale）原样保留，不会被吃掉。
 *  4. **注入舞台口径的 [ScreenMetrics]**：业务侧拿到的短边恒为基准值、`scale` 恒为 1.0 ——
 *     所有尺寸公式退化成圆表原值。**「DPI 变大」只作用在外层这一次缩放上**，内部计算前提不变。
 *  5. 手势阈值（touchSlop / 最小热区）按同一比例换算，放大后手感与圆表一致（否则大屏上手势
 *     会变得过于灵敏）。
 */
@Composable
fun WatchStage(content: @Composable () -> Unit) {
    val base = AdaptTokens.DESIGN_WIDTH
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorToken.bg),
        contentAlignment = Alignment.Center,
    ) {
        // 量的是**真实**可用区（此刻 density 尚未替换）：决定舞台整体放到多大
        val avail = minOf(maxWidth, maxHeight)
        val scale = (avail / base).coerceIn(STAGE_SCALE_MIN, AdaptTokens.SCALE_MAX)

        val density = LocalDensity.current
        val viewConfig = LocalViewConfiguration.current
        val stageDensity = remember(density, scale) {
            Density(density.density * scale, density.fontScale)
        }
        val stageViewConfig = remember(viewConfig, scale) { scaledViewConfiguration(viewConfig, scale) }
        val stageMetrics = remember { ScreenMetrics(base, base) }

        CompositionLocalProvider(
            LocalDensity provides stageDensity,
            LocalViewConfiguration provides stageViewConfig,
            LocalStageMetrics provides stageMetrics,
        ) {
            // 圆表上 scale ≈ 1，舞台与屏幕内接圆重合，观感与改造前一致；
            // 非 1:1 屏上锁成居中的圆，四角露出纯黑背景。
            Box(
                modifier = Modifier
                    .size(base)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

/**
 * 手势阈值按 [scale] 等比放大：slop 是**像素**值，舞台放大后同样的像素位移对应的
 * 「舞台 dp」变小，不改就会比圆表更灵敏（轻微移动即判定为拖拽）。
 */
private fun scaledViewConfiguration(base: ViewConfiguration, scale: Float): ViewConfiguration =
    object : ViewConfiguration by base {
        override val touchSlop: Float get() = base.touchSlop * scale
        override val minimumTouchTargetSize: DpSize
            get() = DpSize(
                base.minimumTouchTargetSize.width * scale,
                base.minimumTouchTargetSize.height * scale,
            )
    }
