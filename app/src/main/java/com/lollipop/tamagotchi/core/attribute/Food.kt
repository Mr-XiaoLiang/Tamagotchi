package com.lollipop.tamagotchi.core.attribute

/**
 * 食物类型 + 口味标签（doc/01 §7）。无经济：喂食免费但类型驱动差异，口味契合加成。
 * 仅承载数值/文案事实；契合加成 +10% 由动作规则层（M6）应用。
 */
enum class FoodType(
    val label: String,
    val icon: String,
    val satDelta: Float,
    val moodDelta: Float,
    val healthDelta: Float,
    val flavor: FoodFlavor,
) {
    BERRIES("树果餐", "🍎", +30f, +2f, +1f, FoodFlavor.BALANCED),
    MEAL("能量餐", "🍚", +40f, 0f, 0f, FoodFlavor.HEARTY),
    NUTRITION("营养餐", "🥦", +20f, +1f, +8f, FoodFlavor.LIGHT),
    SNACK("小零食", "🍮", +12f, +15f, -1f, FoodFlavor.SWEET),
    PORRIDGE("特调糊糊", "🥣", +25f, +5f, +3f, FoodFlavor.NOVEL),
    ;
}

/** 口味偏好标签（doc/01 §7 口味契合加成；与 doc/02 §4.2 flavor 对齐）。 */
enum class FoodFlavor(val label: String) {
    BALANCED("均衡"),
    HEARTY("贪吃"),
    LIGHT("清淡"),
    SWEET("甜口"),
    NOVEL("新奇"),
}
