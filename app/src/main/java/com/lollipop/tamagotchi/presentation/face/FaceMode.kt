package com.lollipop.tamagotchi.presentation.face

/**
 * 主屏「谁在当脸」（doc/10 §4.1）。
 *
 * - [COMPANION]：宠物游走（现状）——顶部常驻 Robot 小表情 + 中央宠物活动区；
 * - [ROBOT]：Robot 表情全屏，宠物缩为静态图标钉在屏幕底部中央（点它回 [COMPANION]）。
 *
 * 只挂在 `PetScreen` 组合层：**不进 `PetProfile`**（与情绪同为运行时状态，D2），
 * 也不新开 Activity —— 避免重建 `PetState` / FSM / SessionLog，且便于共享尺寸做过渡。
 */
enum class FaceMode {
    COMPANION,
    ROBOT,
}

/**
 * COMPANION ⇄ ROBOT 切换时长（D8 / M20.S1 过渡动画）。
 *
 * 共享元素（同一个 Robot 表情）在这段时间里插值尺寸与位置：
 * 34.5dp ⇄ 143dp、−iconRowR ⇄ 屏心。手表屏小，280ms 够看清又不拖沓。
 */
const val ROBOT_SWITCH_MS = 280
