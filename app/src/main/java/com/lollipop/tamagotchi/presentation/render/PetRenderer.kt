package com.lollipop.tamagotchi.presentation.render

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * 宠物渲染器 v0（Task.md M2.S2；doc/07 §4/§8）。
 *
 * 本文件三件套：
 * - [PetRenderer]：v0 渲染参数与常量（IDLE 呼吸幅度 / 周期），并承载绘制语义说明。
 * - [drawPetFrame]：无状态纯绘制——把整表 [image] 的源子矩形画进目标矩形 [dst]；
 *   源帧由 [SpriteSheetDecoder.frameRect] 切取，不额外分配子 Bitmap（doc/07 §6）。
 * - [PetStaticSprite]：Compose 壳——圆形裁切遮罩（防形变露边，doc/07 §9）+
 *   IDLE 呼吸（纵向 ±2px、2s 周期，doc/07 §4.4 层1）+ 中心化 dstRect。
 *   上层定位语义：给 [PetStaticSprite] 的 modifier 尺寸 = **模型占位尺寸（spriteDia）**，
 *   不是活动区尺寸；活动区（三个把手内尖以内中央区，06 §1 R≈0.60 屏半径）是模型
 *   可跑动/位移的范围，模型只占其中约 0.30 屏直径并居中（放大活动区 ≠ 放大模型）。
 *
 * 过滤策略：64×64 源帧放大到模型占位尺寸时默认 [FilterQuality.None]（近邻像素，
 * 复古素材最锐利不糊），是否改 MEDIUM 待真机观感定稿（doc/07 §9）。
 */
object PetRenderer {

    /** IDLE 呼吸幅度：纵向 ±2px（字面像素；doc/07 §4.4，真机观感 M4.S2 可复核调参）。 */
    const val IDLE_AMP_PX = 2f

    /** IDLE 呼吸单程时长 ms（往返 = 2s 周期；doc/07 §4.4）。 */
    const val IDLE_HALF_MS = 1000

    /** 目标矩形占模型占位尺寸比例（留 2% 透明余量：呼吸/亚像素不裁到透明区破边）。 */
    const val DST_FILL = 0.96f
}

/**
 * 单帧绘制（顶层扩展，DrawScope 内可直接调用）：
 * 把整表 [image] 的 [src] 子矩形画进目标矩形 [dst]。
 * [filterQuality] 默认近邻（None）；真机观感定稿见 doc/07 §9。
 */
fun DrawScope.drawPetFrame(
    image: ImageBitmap,
    src: Rect,
    dst: Rect,
    filterQuality: FilterQuality = FilterQuality.None,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset(src.left.toInt(), src.top.toInt()),
        srcSize = IntSize(src.width.toInt(), src.height.toInt()),
        dstOffset = IntOffset(dst.left.toInt(), dst.top.toInt()),
        dstSize = IntSize(dst.width.toInt(), dst.height.toInt()),
        filterQuality = filterQuality,
    )
}

/**
 * 静态宠物（IDLE 立姿）Compose 壳（M2.S2 v0）：
 * 圆形裁切遮罩（clip CircleShape，形变/越界不外露）+
 * IDLE 呼吸（纵向 ±[PetRenderer.IDLE_AMP_PX]px，2s 往返）+
 * 中心化 dstRect（源帧占活动区直径 [PetRenderer.DST_FILL]，居中）。
 *
 * @param sheet 整表 256×256 位图；null 表示素材未就绪（保留占位不绘）。
 * @param modifier 布局修饰（尺寸 = 活动区直径，由调用方给定）。
 * @param dir 立姿朝向，默认 Down（面向玩家，行 0）。
 * @param frame 帧号，默认 0（IDLE 首帧）。
 */
@Composable
fun PetStaticSprite(
    sheet: ImageBitmap?,
    modifier: Modifier = Modifier,
    dir: SpriteSheetDecoder.Dir = SpriteSheetDecoder.Dir.Down,
    frame: Int = 0,
) {
    val transition = rememberInfiniteTransition(label = "petIdle")
    val breath by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PetRenderer.IDLE_HALF_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathY",
    )
    Canvas(modifier.clip(CircleShape)) {
        if (sheet != null) {
            val s = this.size.minDimension
            val dstSide = s * PetRenderer.DST_FILL
            val left = (s - dstSide) / 2f
            val yOff = breath * PetRenderer.IDLE_AMP_PX
            drawPetFrame(
                image = sheet,
                src = SpriteSheetDecoder.frameRect(dir, frame),
                dst = Rect(left, left + yOff, left + dstSide, left + dstSide + yOff),
            )
        }
    }
}
