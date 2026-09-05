package com.lollipop.tamagotchi.core.behavior

/**
 * 行为状态机状态（doc/02 §1.1）：宠物对外表现 = FSM 状态 × 属性/情绪修饰层。
 */
enum class PetState(val label: String) {
    IDLE("站立"),
    WALKING("行走"),
    EATING("进食"),
    SLEEPING("睡觉"),
    SICK("生病"),
    SAD("难过"),
    EXCITED("开心"),
}
