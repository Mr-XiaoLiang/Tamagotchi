package com.lollipop.tamagotchi.presentation.screen

import androidx.annotation.StringRes
import com.lollipop.tamagotchi.R
import com.lollipop.tamagotchi.core.attribute.AttributeId
import com.lollipop.tamagotchi.core.attribute.FoodFlavor
import com.lollipop.tamagotchi.core.attribute.FoodType
import com.lollipop.tamagotchi.domain.engine.EndingMood

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
        AttributeId.KNOWLEDGE -> R.string.attr_knowledge
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

// ── M7 离线叙事：结局基调 / 事件气泡 / 迎接文案 ──────────────

/** 离线结局基调 → 多语言文案（presentation 层映射，doc/03 §7.6）。 */
val EndingMood.labelRes: Int
    get() = when (this) {
        EndingMood.JOYFUL -> R.string.ending_joyful
        EndingMood.GRUMBLING -> R.string.ending_grumbling
        EndingMood.NEEDY -> R.string.ending_needy
        EndingMood.SLEEPY -> R.string.ending_sleepy
        EndingMood.SICKLY -> R.string.ending_sickly
    }

/** 离线事件 id → 气泡文案资源 id（doc/03 §7.2/§7.4 事件库）；未知 id 回退 null。 */
fun offlineEventBubbleRes(eventId: String): Int? = when (eventId) {
    "wait_hungry" -> R.string.ev_wait_hungry
    "dull_wander" -> R.string.ev_dull_wander
    "dust_dirty" -> R.string.ev_dust_dirty
    "sleep_sound" -> R.string.ev_sleep_sound
    "ail_sick" -> R.string.ev_ail_sick
    "idle_pass" -> R.string.ev_idle_pass
    "found_leftover" -> R.string.ev_found_leftover
    "self_play" -> R.string.ev_self_play
    "nice_dream" -> R.string.ev_nice_dream
    "spooked_noise" -> R.string.ev_spooked_noise
    "knock_bowl" -> R.string.ev_knock_bowl
    else -> null
}

/**
 * 迎接气泡文案：长离线优先以 [endingMood] 定调，短离线按离开时长分档（doc/04 §4/§5）。
 * [offlineMs] 为离开时长（毫秒），[endingMood] 为 null 表示短离线（无时间线）。
 */
fun greetingRes(offlineMs: Long, endingMood: EndingMood?): Int {
    if (endingMood != null) return endingMood.labelRes
    val min = offlineMs / 60_000L
    return when {
        min < 10 -> R.string.greet_back
        min < 120 -> R.string.greet_miss
        min < 360 -> R.string.greet_wait
        min < 1440 -> R.string.greet_longwait
        else -> R.string.greet_verylong
    }
}
