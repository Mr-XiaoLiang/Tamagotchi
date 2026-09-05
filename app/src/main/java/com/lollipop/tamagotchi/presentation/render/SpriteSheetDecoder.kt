package com.lollipop.tamagotchi.presentation.render

import androidx.compose.ui.geometry.Rect

/**
 * 精灵切片解码器 + 常量表（doc/02 §2.3 / doc/07 §1；M2.S1 用 BULBASAUR.png 实测钉死）。
 *
 * 每张 assets/sprite 下 `*.png` = 256×256，4×4 网格，单帧 64×64。
 * 实测结论：**每行 = 朝向（0 下 / 1 左 / 2 右 / 3 上），每列 = 行走帧（0→3）**。
 * `dir → row`、`frame → col` 均在此做常量；若个别表与此不符，只需在此修正映射。
 */
object SpriteSheetDecoder {
    const val SHEET_PX = 256
    const val GRID = 4
    const val FRAME_PX = 64 // SHEET_PX / GRID

    /** 朝向（行号映射；M2.S1 实测：行=朝向）。 */
    enum class Dir(val row: Int) {
        Down(0),
        Left(1),
        Right(2),
        Up(3),
        ;

        companion object {
            /** 取 0..3 安全下标（FSM 输出 dir ∈ 0..3 时用）。 */
            fun of(i: Int): Dir = entries[i and 3]
        }
    }

    /** 帧 0..3 → 列号（取模回绕，防越界）。 */
    fun colOf(frame: Int): Int = frame and 3

    /** 切格子像素区域（整表坐标系），渲染时作为 srcRect。 */
    fun frameRect(dir: Dir, frame: Int): Rect {
        val c = colOf(frame)
        val r = dir.row
        return Rect(
            left = (c * FRAME_PX).toFloat(),
            top = (r * FRAME_PX).toFloat(),
            right = ((c + 1) * FRAME_PX).toFloat(),
            bottom = ((r + 1) * FRAME_PX).toFloat(),
        )
    }
}
