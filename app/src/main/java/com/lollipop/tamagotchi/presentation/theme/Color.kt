package com.lollipop.tamagotchi.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * ColorToken —— 全项目唯一配色源（doc/06 §4.1）。
 *
 * 规则：
 *  1. 一切 UI 配色从这里取，禁止在组件/页面内写死新色值；
 *  2. 背景全局纯黑 `bg`；页面主题背景以 RadialGradient 由中心低饱和色收敛到 `bg`（doc/06 §4.2）；
 *  3. 语义色取莫奈低饱和基调，鲜艳色只允许进入中央（宠物/气泡），绝不进入屏缘。
 */
object ColorToken {
    // ── 界面基色（doc/06 §4.1） ─────────────────────────────
    /** 全局基底色：纯黑，圆屏本体与黑边一体，外圈收敛终色。 */
    val bg = Color(0xFF000000)

    /** 页面 RadialGradient 中心色占位（低饱和深色调，页面自行决定具体色）。 */
    val pageGlowPlaceholder = Color(0xFF1C232B)

    /** 统一默认主题色：实心胶囊填充 / 描边 / 主文字（近白）。 */
    val Accent = Color(0xFFF2F3F5)

    /** Accent 上的反色（实心胶囊内文字/图标，黑）。 */
    val OnAccent = Color(0xFF101418)

    /** 次要文字 / 空心胶囊内容（复用 IDLE 灰）。 */
    val Text2 = Color(0xFFB0BEC5)

    /** 实心胶囊（可点击）容器色 —— 等于 Accent，语义别名。 */
    val PillFilled = Accent

    /** 空心胶囊（不可点击）描边色 —— 透明底 + 1dp 描边。 */
    val PillOutline = Text2

    // ── 属性色（doc/01 §3 / doc/06 §4） ────────────────────
    val Satiation = Color(0xFFD9AF85) // 饱腹 琥珀
    val Mood = Color(0xFF84BCAE)      // 心情 青绿
    val Health = Color(0xFFCF9AA2)    // 健康 玫红
    val Intelligence = Color(0xFF9E94C9) // 智力 蓝紫（M12 起用）
    val Hygiene = Color(0xFF93BFCF)   // 清洁 浅蓝（M11 起用）

    // ── 状态色（doc/06 §4） ───────────────────────────────
    val Idle = Text2                  // IDLE 灰
    val Walking = Color(0xFF79B3A6)   // WALKING 青
    val Eating = Color(0xFFDCA46E)    // EATING 橙
    val Sleeping = Color(0xFF8792BE)  // SLEEPING 蓝
    val Sick = Color(0xFFC6899E)      // SICK 紫红
    val Sad = Color(0xFF7E8FB4)       // SAD 靛
    val Excited = Color(0xFFE6CD8C)   // EXCITED 亮黄（情绪峰值，仍压饱和）

    /**
     * 告急预警色（低值 RED 语义，doc/01 §3 / doc/06 §2 图标闪烁）。
     * doc/06 §4.1 未给 HEX，编码期暂定；真机/后续回写 doc/06 §4.1。
     */
    val Warn = Color(0xFFCF5D5D)
}
