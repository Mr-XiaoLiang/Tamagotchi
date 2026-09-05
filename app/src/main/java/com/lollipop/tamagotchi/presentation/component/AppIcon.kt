package com.lollipop.tamagotchi.presentation.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lollipop.tamagotchi.presentation.theme.ColorToken

/**
 * Drawable → ImageBitmap（无 androidx.core 依赖，用 Canvas 手动绘制；自适应图标亦支持）。
 * 抽为共享顶层函数：功能清单网格与编辑页都用真实 App 图标。
 */
fun Drawable.toImageBitmap(): ImageBitmap {
    val w = if (intrinsicWidth > 0) intrinsicWidth else 48
    val h = if (intrinsicHeight > 0) intrinsicHeight else 48
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bmp.asImageBitmap()
}

/** App 图标：优先真实图标（Drawable→ImageBitmap），缺失时回退占位圆。 */
@Composable
fun AppIcon(pkg: String, icons: Map<String, ImageBitmap?>, size: Dp = 46.dp) {
    val bmp = icons[pkg]
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(ColorToken.PillOutline))
    }
}
