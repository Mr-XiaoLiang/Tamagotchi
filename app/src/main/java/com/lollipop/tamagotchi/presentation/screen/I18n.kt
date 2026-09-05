package com.lollipop.tamagotchi.presentation.screen

import androidx.annotation.StringRes
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.FoodFlavor
import com.lollipop.tamagotchi.core.attribute.FoodType

/**
 * 枚举 → 字符串资源映射（M7 多语言基建）。
 *
 * 映射放在 presentation 层而非 core 枚举：core 保持纯值对象、不引 Android 资源
 * （见 core/attribute/Attribute.kt 注释）。UI 一律经 [stringResource] / [android.content.Context.getString]
 * 取词，不再出现硬编码文案；新增枚举值只需在此补一行 + 在 res/values/strings.xml 加键。
 */

@get:StringRes
val AttributeId.labelRes: Int
    get() = when (this) {
        AttributeId.SATIATION -> R.string.attr_satiation
        AttributeId.MOOD -> R.string.attr_mood
        AttributeId.HEALTH -> R.string.attr_health
        AttributeId.INTELLIGENCE -> R.string.attr_intelligence
        AttributeId.HYGIENE -> R.string.attr_hygiene
    }

@get:StringRes
val FoodType.labelRes: Int
    get() = when (this) {
        FoodType.BERRIES -> R.string.food_berries
        FoodType.MEAL -> R.string.food_meal
        FoodType.NUTRITION -> R.string.food_nutrition
        FoodType.SNACK -> R.string.food_snack
        FoodType.PORRIDGE -> R.string.food_porridge
    }

@get:StringRes
val FoodFlavor.labelRes: Int
    get() = when (this) {
        FoodFlavor.BALANCED -> R.string.flavor_balanced
        FoodFlavor.HEARTY -> R.string.flavor_hearty
        FoodFlavor.LIGHT -> R.string.flavor_light
        FoodFlavor.SWEET -> R.string.flavor_sweet
        FoodFlavor.NOVEL -> R.string.flavor_novel
    }

/** 建档六维 trait 名（顺序与 [com.lollipop.tamagotchi.domain.model.Traits.orderedValues] 一致）。 */
val TRAIT_NAMES_RES: List<Int> = listOf(
    R.string.trait_activity,
    R.string.trait_affinity,
    R.string.trait_appetite,
    R.string.trait_curiosity,
    R.string.trait_temper,
    R.string.trait_learner,
)
