package com.lollipop.tamagotchi.presentation.face

import androidx.compose.ui.unit.Dp
import com.lollipop.tamagotchi.presentation.component.ScreenMetrics

/**
 * Robot 表情的尺寸口径（doc/10 §2.2）。
 *
 * 表情边长 = 屏幕短边 × [SIZE_FACTOR]（用户真机校准为 0.15，230dp 圆屏 ≈ 34.5dp）。
 * **常驻小表情**与**全屏 Robot 模式下钉在屏幕底部中央的宠物缩略**共享这一尺寸，
 * 调参只改这里一处即可两端同步。
 */
object RobotTokens {
    /** 表情边长占屏幕短边的比例。 */
    const val SIZE_FACTOR = 0.15f
}

/** 表情边长（常驻小表情 / 底部宠物缩略共用）。 */
fun ScreenMetrics.robotFaceSize(): Dp = minSide * RobotTokens.SIZE_FACTOR
