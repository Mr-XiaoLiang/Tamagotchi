package com.lollipop.tamagotchi.presentation.face

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lollipop.tamagotchi.presentation.component.ScreenMetrics

/**
 * Robot 表情的尺寸口径（doc/10 §2.2）。
 *
 * 表情边长 = 屏幕短边 × [SIZE_FACTOR]（用户真机校准为 0.15，230dp 圆屏 ≈ 34.5dp）。
 * **常驻小表情**与**全屏 Robot 模式下钉在屏幕底部中央的宠物缩略**共享这一尺寸，
 * 调参只改这里一处即可两端同步。
 */
object RobotTokens {
    /** 常驻表情边长占屏幕短边的比例。 */
    const val SIZE_FACTOR = 0.15f

    /**
     * **全屏态**表情边长占屏幕短边的比例（230dp 圆屏 ≈ 142.6dp，即半径 71.3dp）。
     *
     * 不能是 1.0（= minSide）：那样可怜的机器人会被拉到贴边、看起来「撑满屏幕」，
     * 而且没有余量 —— 弹跳 / 旋转 / 迸发会直接顶到屏缘出血，观感不像 Demo 里那个
     * 会蹦跶的小家伙。留出环带：
     * - 与主环（r=111dp）留 ≈40dp，OSD 环形进度条在 Robot 展开时仍完整可见；
     * - 与三向把手（r=89dp）留 ≈18dp，箭头不被压住。
     * 想更胖 / 更瘦只调这里（≥0.7 会开始咬把手）。
     */
    const val FULL_SIZE_FACTOR = 0.62f

    /**
     * 底部宠物缩略与屏底边缘热区带之间留的呼吸位。
     *
     * 缩略**必须整体落在边缘带之外**（[AdaptTokens.EDGE_BAND] = 32dp），否则点它会同时
     * 触发「底缘上滑拖开操作面板」，表现为「点回收不了宠物、倒先把面板拖出来了」。
     */
    val THUMB_EDGE_GAP = 4.dp
}

/** 表情边长（常驻小表情 / 底部宠物缩略共用）。 */
fun ScreenMetrics.robotFaceSize(): Dp = minSide * RobotTokens.SIZE_FACTOR

/** 全屏态（ROBOT）表情边长：居中留白，给弹跳/旋转留的活动余量在此。 */
fun ScreenMetrics.robotFullFaceSize(): Dp = minSide * RobotTokens.FULL_SIZE_FACTOR

/**
 * 底部宠物缩略中心到屏心的纵向距离：**整体扣掉底向边缘热区带**，
 * 保证点缩略只切换模式、绝不会顺带拖开底部面板。
 *
 * 230dp 圆屏：115 − 32 − 17.25 − 4 ≈ 61.75dp（原值取 `handleR` = 89dp，整个按钮泡在边缘带里）。
 * 上限已到头——还想再往下的话，只能先缩小边缘带或缩略本身。
 */
fun ScreenMetrics.robotThumbOffsetY(): Dp =
    radius - edgeBand - robotFaceSize() / 2f - RobotTokens.THUMB_EDGE_GAP
