package com.lollipop.grokbot

import com.lollipop.grokbot.internal.GrokTabs

/**
 * Every expression the character can show.
 *
 * The list mirrors the original artwork: a life cycle, a set of reactions and a
 * few "agent morph" moods that replace the body with a tool animation.
 */
public enum class GrokMood(public val id: String) {
    SLEEPING("sleeping"),
    WAKING("waking"),
    IDLE("idle"),
    LISTENING("listening"),
    THINKING("thinking"),
    SEARCHING("searching"),
    WORKING("working"),

    EXCITED("excited"),
    SURPRISED("surprised"),
    SUSPICIOUS("suspicious"),
    ANGRY("angry"),
    DROWSY("drowsy"),
    HAPPY("happy"),
    CURIOUS("curious"),
    CONFUSED("confused"),
    BORED("bored"),
    PROUD("proud"),
    SHY("shy"),
    SAD("sad"),
    LAUGHING("laughing"),
    SCARED("scared"),
    PLAYFUL("playful"),
    CELEBRATE("celebrate"),

    ORBIT("orbit"),
    RADAR("radar"),
    PROGRESS("progress"),

    SPAWNING("spawning"),
    HUMMING("humming"),
    LOADING("loading"),
    DICTATING("dictating"),
    WRITING("writing"),
    SENDING("sending"),
    RECEIVING("receiving"),
    UPLOADING("uploading"),
    NOTIFYING("notifying"),
    ALERTING("alerting"),
    DRAGGING("dragging"),
    BOUNCING("bouncing"),
    POWERING_DOWN("powering-down"),
    ;

    public companion object {
        private val byId: Map<String, GrokMood> = entries.associateBy { it.id }

        /** Looks a mood up by its web identifier, `null` when unknown. */
        public fun fromId(id: String): GrokMood? = byId[id]

        /** Every mood bucketed the way the artwork groups them, for building pickers. */
        public val groups: List<GrokMoodGroup> get() = GrokTabs.GROUPS

        /** Moods the onboarding reel cycles through, `idle` in between. */
        public val onboarding: List<GrokMood> get() = GrokTabs.ONBOARDING

        /** Delay between two beats of the onboarding reel. */
        public val onboardingIntervalMillis: Long get() = GrokTabs.ONBOARDING_MS.toLong()

        /**
         * Mood of beat [n] of the onboarding reel: `idle` on even beats, the reel otherwise.
         * Mirrors `pjn(n)` of the web build.
         */
        public fun onboardMood(n: Int): GrokMood = GrokTabs.onboardMood(n)
    }
}

/** A labelled bucket of moods, used to build pickers in the host app. */
public class GrokMoodGroup(
    public val label: String,
    public val moods: List<GrokMood>,
)
