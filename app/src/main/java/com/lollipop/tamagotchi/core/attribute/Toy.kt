package com.lollipop.tamagotchi.core.attribute

/**
 * 玩具类型（doc/09 §5.1 玩耍/玩具扩展，Task M13）。
 *
 * 仅承载数值/文案事实：不同玩具 → 心情收益档、情绪表现类别（[ToyVibe]）、稀有度（[ToyRarity]）不同。
 * 解锁来源见 [ToyUnlock]：默认拥有 / 智力解锁（M12 知识阈值）/ 里程碑计数解锁（doc/04 §3.3）/
 * 随机事件掉落（doc/03，运行时授予，初始不可见）；无货币、非购买（doc/09 §1）。
 */
enum class ToyType(
    val label: String,
    val icon: String,
    val moodDelta: Float,
    val healthCost: Float,
    val hygieneCost: Float,
    val satCost: Float,
    val vibe: ToyVibe,
    val rarity: ToyRarity,
    val unlock: ToyUnlock,
) {
    /** 小球：活泼、基础陪伴。 */
    BALL("小球", "⚾", 14f, 2f, 3f, 4f, ToyVibe.LIVELY, ToyRarity.COMMON, ToyUnlock.Default),

    /** 逗猫棒：活泼、逗趣。 */
    WAND("逗猫棒", "🪶", 18f, 1f, 2f, 3f, ToyVibe.LIVELY, ToyRarity.COMMON, ToyUnlock.Default),

    /** 麻绳结：温顺拉扯。 */
    ROPE("麻绳结", "🪢", 12f, 3f, 4f, 3f, ToyVibe.GENTLE, ToyRarity.COMMON, ToyUnlock.Default),

    /** 解谜球：智力解锁（知识≥30），专注把玩。 */
    PUZZLE("解谜球", "🧩", 20f, 1f, 1f, 2f, ToyVibe.FOCUSED, ToyRarity.RARE, ToyUnlock.ByKnowledge(30)),

    /** 机关盒：智力解锁（知识≥60），更费心思。 */
    GADGET("机关盒", "📦", 22f, 2f, 2f, 2f, ToyVibe.FOCUSED, ToyRarity.RARE, ToyUnlock.ByKnowledge(60)),

    /** 小王冠：里程碑解锁（玩耍累计≥50），珍贵纪念。 */
    CROWN("小王冠", "👑", 25f, 1f, 1f, 1f, ToyVibe.GENTLE, ToyRarity.EPIC,
        ToyUnlock.ByMilestone(MilestoneStat.PLAY, 50)),

    /** 流星坠：随机事件掉落（doc/03），初始不可见，运行时授予。 */
    STAR("流星坠", "🌟", 30f, 0f, 0f, 0f, ToyVibe.LIVELY, ToyRarity.EPIC, ToyUnlock.ByEvent),
    ;
}

/**
 * 玩具的情绪表现类别（doc/09 §5.1；M13.S2 差异表现）。
 * 仅作表现层标签 + 映射到情绪层；core 不依赖 UI，由 presentation 经 `ToyVibe.toEmotion()` 消费。
 */
enum class ToyVibe(val label: String) {
    /** 活泼：追逗猫棒/小球的跑跳类 → 表现层蹦跳（Emotion.HAPPY）。 */
    LIVELY("活泼"),
    /** 温顺：麻绳结/小王冠的亲昵类 → 表现层侧头躲闪（Emotion.SHY）。 */
    GENTLE("温顺"),
    /** 专注：解谜球/机关盒的动脑类 → 表现层前倾歪头思考（Emotion.CURIOUS）。 */
    FOCUSED("专注"),
}

/** 稀有度（仅文案/排序用，不影响数值）。 */
enum class ToyRarity(val label: String) {
    COMMON("普通"),
    RARE("稀有"),
    EPIC("珍贵"),
}

/** 里程碑计数键（对应 [Milestones] 字段，doc/04 §3.3）。 */
enum class MilestoneStat(val statName: String) {
    FEED("feed"),
    PLAY("play"),
    PET("pet"),
    HEAL("heal"),
    CLEAN("clean"),
    STUDY("study"),
    SICK("sickTotal"),
    DAYS("daysTogether"),
}

/** 玩具解锁来源（纯数据；判定逻辑见 domain.toy.ToyRules）。 */
sealed interface ToyUnlock {
    /** 开档即拥有。 */
    data object Default : ToyUnlock

    /** 智力（知识）达阈值解锁（M12 解锁表 doc/01 §4.1）。 */
    data class ByKnowledge(val knowledge: Int) : ToyUnlock

    /** 里程碑计数达阈值解锁（doc/04 §3.3）。 */
    data class ByMilestone(val stat: MilestoneStat, val count: Long) : ToyUnlock

    /** 随机事件掉落（doc/03），运行时经 [com.lollipop.tamagotchi.domain.toy.ToyRules.isUnlocked]
     * 的 eventGranted 授予，初始不可见。 */
    data object ByEvent : ToyUnlock
}
