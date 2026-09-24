package com.lollipop.tamagotchi.presentation.render

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * 宠物静态 pose 帧缩略（doc/10 §4.1 / D5）：ROBOT 模式下钉在屏幕底部中央的「回游走」按钮。
 *
 * 只画一帧（朝下、帧 0）、**不呼吸不走动也不跑 FSM**：全屏 Robot 时宠物只是个图标，
 * 最省电；尺寸由调用方给（与常驻表情共享 `RobotTokens.SIZE_FACTOR`）。
 */
@Composable
fun PetPoseThumb(
    sheet: ImageBitmap?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = modifier.then(
            if (contentDescription != null) {
                Modifier.semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        ),
    ) {
        if (sheet == null) return@Canvas
        drawPetFrame(
            image = sheet,
            src = SpriteSheetDecoder.frameRect(SpriteSheetDecoder.Dir.Down, 0),
            dst = Rect(0f, 0f, size.width, size.height),
        )
    }
}
