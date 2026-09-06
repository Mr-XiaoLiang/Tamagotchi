package com.lollipop.tamagotchi.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 屏幕适配统一口径（doc/06 §8 / doc/08）。
 *
 * 所有响应式尺寸都从 [ScreenMetrics] 取值，**调参只改 [AdaptTokens] 一处**，
 * 避免「适配调试 = 重构」：换设备宽度 / 调边距时，无需回到各业务布局里翻公式。
 *
 * - [ScreenMetrics.radius] / [ScreenMetrics.ringOuter] / [ScreenMetrics.handleR] / [ScreenMetrics.iconRowR]
 *   由真实屏宽（短边）等比推导，主屏主环、三向把手、状态图标行随之缩放。
 * - [ScreenMetrics.scale] 仅随屏宽「放大」（下限 1.0，不缩小，护住小屏 ≥30dp 触控热区），
 *   固定尺寸（图标/按钮）经 [ScreenMetrics.dp] 放大以填充大屏；230dp 基准屏下恒为 1.0，观感不变。
 */
object AdaptTokens {
    /** 设计基准屏宽（圆表参考值），屏宽与之比例为 [ScreenMetrics.scale]。 */
    val DESIGN_WIDTH = 230.dp
    /** 固定尺寸缩放区间下限（=1 表示不缩小，护住小屏触控热区）。 */
    val SCALE_MIN = 1f
    /** 固定尺寸缩放区间上限（大屏封顶，避免图标过大）。 */
    val SCALE_MAX = 1.4f

    /** 主环外缘距屏缘呼吸位。 */
    val RING_EDGE_GAP = 4.dp
    /** 三向把手相对主环外缘的内缩量（贴环内沿悬浮）。 */
    val HANDLE_RING_INSET = 22.dp
    /** 状态图标行所在半径占主环外缘的比例。 */
    val ICON_ROW_FACTOR = 0.62f

    /** 横向安全内缩 = 短边 × 比例，clamp 到 [SAFE_MIN, SAFE_MAX]。 */
    val SAFE_FACTOR = 0.06f
    val SAFE_MIN = 14.dp
    val SAFE_MAX = 30.dp

    /** 边缘热区带带深（点按展开 / 跟手拖拽起点判定区）。 */
    val EDGE_BAND = 32.dp

    /** 圆屏列表首/末留白默认标题行半高。 */
    val LIST_TITLE_HALF = 24.dp
}

/**
 * 由真实屏幕尺寸推导的适配集合；用 [screenMetrics] 获取（按配置缓存）。
 * 业务布局只读取这里导出的字段，不自己写尺寸公式。
 */
data class ScreenMetrics(
    val width: Dp,
    val height: Dp,
) {
    val minSide: Dp = if (width < height) width else height
    val longSide: Dp = if (width > height) width else height
    /** 屏半径（圆表取短边半）。 */
    val radius: Dp = minSide / 2f
    /** 固定尺寸缩放因子：屏宽 > 基准时放大以填充大屏；≤ 基准时恒为 1.0（不缩小，护小屏触控）。 */
    val scale: Float = (minSide / AdaptTokens.DESIGN_WIDTH).coerceIn(AdaptTokens.SCALE_MIN, AdaptTokens.SCALE_MAX)

    /** 主环外缘半径。 */
    val ringOuter: Dp = radius - AdaptTokens.RING_EDGE_GAP
    /** 三向把手所在半径（贴主环内沿）。 */
    val handleR: Dp = ringOuter - AdaptTokens.HANDLE_RING_INSET
    /** 状态图标行半径。 */
    val iconRowR: Dp = ringOuter * AdaptTokens.ICON_ROW_FACTOR
    /** 横向安全内缩。 */
    val edgeSafeInset: Dp = (minSide * AdaptTokens.SAFE_FACTOR).coerceIn(AdaptTokens.SAFE_MIN, AdaptTokens.SAFE_MAX)
    /** 边缘热区带带深。 */
    val edgeBand: Dp = AdaptTokens.EDGE_BAND

    /** 圆屏列表首/末留白（长边半屏 − 标题行半高），下限 0 防负高。 */
    fun listEdge(titleRowHalf: Dp = AdaptTokens.LIST_TITLE_HALF): Dp =
        (longSide * 0.5f - titleRowHalf).coerceAtLeast(0.dp)

    /** 固定 dp 按 [scale] 缩放（基准屏不变）。 */
    fun dp(base: Dp): Dp = base * scale
}

/** 读取并缓存当前屏幕尺寸推导的 [ScreenMetrics]（按屏幕宽高变化重算）。 */
@Composable
fun screenMetrics(): ScreenMetrics {
    val config = LocalConfiguration.current
    return remember(config.screenWidthDp, config.screenHeightDp) {
        ScreenMetrics(config.screenWidthDp.dp, config.screenHeightDp.dp)
    }
}
