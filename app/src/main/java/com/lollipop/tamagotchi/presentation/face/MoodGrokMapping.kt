package com.lollipop.tamagotchi.presentation.face

import com.lollipop.grokbot.GrokMood
import com.lollipop.tamagotchi.domain.engine.Mood
import com.lollipop.tamagotchi.presentation.render.Emotion

/**
 * 情绪 → 表情（doc/10 §3.1 表右列）。
 *
 * 宿主以 [GrokMode.HOLD] 持有表情：这里只做「当前情绪该摆哪张脸」的查表，
 * 待机循环动画由 Robot 自己跑（D4 常动），宿主不逐帧干预。
 */
fun Mood.toGrokMood(): GrokMood = when (this) {
    Mood.SLEEP -> GrokMood.SLEEPING
    Mood.SLEEPY -> GrokMood.DROWSY
    Mood.SICK -> GrokMood.SAD
    Mood.HUNGRY -> GrokMood.CONFUSED
    Mood.DIRTY -> GrokMood.SUSPICIOUS
    Mood.SAD -> GrokMood.SAD
    Mood.IDLE -> GrokMood.IDLE
    Mood.PLAYFUL -> GrokMood.PLAYFUL
    Mood.HAPPY -> GrokMood.HAPPY
    Mood.CURIOUS -> GrokMood.CURIOUS
    Mood.EXCITED -> GrokMood.CELEBRATE
    Mood.THINKING -> GrokMood.THINKING
}

/**
 * 情绪 → 宠物精灵形变（doc/07 §3 Emotion）。
 *
 * 与 [toGrokMood] 同源于一次 [Mood]（D6）：脸在笑、身子也在笑，不各算一套。
 * 短演出（动作 / 玩具）的情绪覆盖优先于此处，见 `PetLivingSprite`。
 */
fun Mood.toEmotion(): Emotion = when (this) {
    Mood.SLEEP, Mood.SLEEPY -> Emotion.TIRED
    Mood.SICK -> Emotion.SICK
    Mood.HUNGRY, Mood.DIRTY, Mood.SAD -> Emotion.SAD
    Mood.IDLE -> Emotion.NONE
    Mood.PLAYFUL, Mood.HAPPY, Mood.EXCITED -> Emotion.HAPPY
    Mood.CURIOUS, Mood.THINKING -> Emotion.CURIOUS
}
