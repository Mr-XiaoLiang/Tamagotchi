package com.lollipop.grokbot.internal

import com.lollipop.grokbot.GrokMood
import com.lollipop.grokbot.GrokMoodGroup

/** Playlists, timings and spring constants, ported one to one from the web build. */
internal object GrokTabs {

    val GROUPS: List<GrokMoodGroup> = listOf(
        GrokMoodGroup(
            "Lifecycle",
            listOf(
                GrokMood.SLEEPING, GrokMood.WAKING, GrokMood.IDLE, GrokMood.LISTENING,
                GrokMood.THINKING, GrokMood.SEARCHING, GrokMood.WORKING,
            ),
        ),
        GrokMoodGroup(
            "Reactions",
            listOf(
                GrokMood.EXCITED, GrokMood.SURPRISED, GrokMood.SUSPICIOUS, GrokMood.ANGRY,
                GrokMood.DROWSY, GrokMood.HAPPY, GrokMood.CURIOUS, GrokMood.CONFUSED,
                GrokMood.BORED, GrokMood.PROUD, GrokMood.SHY, GrokMood.SAD,
                GrokMood.LAUGHING, GrokMood.SCARED, GrokMood.PLAYFUL, GrokMood.CELEBRATE,
            ),
        ),
        GrokMoodGroup("Agent morphs", listOf(GrokMood.ORBIT, GrokMood.RADAR, GrokMood.PROGRESS)),
        GrokMoodGroup(
            "Product lifecycle",
            listOf(
                GrokMood.SPAWNING, GrokMood.HUMMING, GrokMood.LOADING, GrokMood.DICTATING,
                GrokMood.WRITING, GrokMood.SENDING, GrokMood.RECEIVING, GrokMood.UPLOADING,
                GrokMood.NOTIFYING, GrokMood.ALERTING, GrokMood.DRAGGING, GrokMood.BOUNCING,
                GrokMood.POWERING_DOWN,
            ),
        ),
    )

    val EYE_PLAYLIST: Map<GrokMood, IntArray> = mapOf(
        GrokMood.SLEEPING to intArrayOf(13, 22, 4),
        GrokMood.WAKING to intArrayOf(13),
        GrokMood.IDLE to intArrayOf(0, 8),
        GrokMood.LISTENING to intArrayOf(10, 1, 19),
        GrokMood.THINKING to intArrayOf(8, 16, 14, 17, 5),
        GrokMood.SEARCHING to intArrayOf(15, 9, 3, 20, 12, 18),
        GrokMood.WORKING to intArrayOf(7, 16, 11, 10),
        GrokMood.EXCITED to intArrayOf(2, 17, 21, 3, 11),
        GrokMood.SURPRISED to intArrayOf(3, 21),
        GrokMood.SUSPICIOUS to intArrayOf(14, 5, 23),
        GrokMood.ANGRY to intArrayOf(7, 16),
        GrokMood.DROWSY to intArrayOf(4, 22, 13),
        GrokMood.HAPPY to intArrayOf(2, 11, 17, 19),
        GrokMood.CURIOUS to intArrayOf(3, 21, 0, 15),
        GrokMood.CONFUSED to intArrayOf(14, 5, 8),
        GrokMood.BORED to intArrayOf(4, 22, 0),
        GrokMood.PROUD to intArrayOf(15, 8, 2),
        GrokMood.SHY to intArrayOf(0, 24, 13),
        GrokMood.SAD to intArrayOf(4, 13, 22),
        GrokMood.LAUGHING to intArrayOf(2, 11, 17),
        GrokMood.SCARED to intArrayOf(3, 21),
        GrokMood.PLAYFUL to intArrayOf(2, 17, 11, 8),
        GrokMood.CELEBRATE to intArrayOf(2, 8, 17),
        GrokMood.ORBIT to intArrayOf(0, 8),
        GrokMood.RADAR to intArrayOf(0, 8),
        GrokMood.PROGRESS to intArrayOf(0, 8),
        GrokMood.SPAWNING to intArrayOf(3, 0),
        GrokMood.HUMMING to intArrayOf(0, 8),
        GrokMood.LOADING to intArrayOf(0, 8),
        GrokMood.DICTATING to intArrayOf(10, 1, 19),
        GrokMood.SENDING to intArrayOf(0, 8),
        GrokMood.RECEIVING to intArrayOf(19, 0, 8),
        GrokMood.UPLOADING to intArrayOf(15, 9, 8),
        GrokMood.WRITING to intArrayOf(15, 9),
        GrokMood.NOTIFYING to intArrayOf(3, 21, 0),
        GrokMood.ALERTING to intArrayOf(3, 21),
        GrokMood.BOUNCING to intArrayOf(2, 17),
        GrokMood.DRAGGING to intArrayOf(3, 15, 0),
        GrokMood.POWERING_DOWN to intArrayOf(13, 22),
    )

    /** Millisecond hold range for each eye in the playlist. */
    val EYE_HOLD_MS: Map<GrokMood, FloatArray> = mapOf(
        GrokMood.SLEEPING to floatArrayOf(6000f, 10000f),
        GrokMood.WAKING to floatArrayOf(800f, 800f),
        GrokMood.IDLE to floatArrayOf(9000f, 16000f),
        GrokMood.LISTENING to floatArrayOf(2800f, 5000f),
        GrokMood.THINKING to floatArrayOf(2000f, 3600f),
        GrokMood.SEARCHING to floatArrayOf(1000f, 1800f),
        GrokMood.WORKING to floatArrayOf(1800f, 3200f),
        GrokMood.EXCITED to floatArrayOf(1100f, 2000f),
        GrokMood.SURPRISED to floatArrayOf(2500f, 4000f),
        GrokMood.SUSPICIOUS to floatArrayOf(2600f, 4500f),
        GrokMood.ANGRY to floatArrayOf(2200f, 3800f),
        GrokMood.DROWSY to floatArrayOf(4000f, 8000f),
        GrokMood.HAPPY to floatArrayOf(2500f, 4500f),
        GrokMood.CURIOUS to floatArrayOf(1800f, 3200f),
        GrokMood.CONFUSED to floatArrayOf(2200f, 3800f),
        GrokMood.BORED to floatArrayOf(3500f, 6000f),
        GrokMood.PROUD to floatArrayOf(3500f, 6000f),
        GrokMood.SHY to floatArrayOf(3000f, 5500f),
        GrokMood.SAD to floatArrayOf(4000f, 7000f),
        GrokMood.LAUGHING to floatArrayOf(1200f, 2400f),
        GrokMood.SCARED to floatArrayOf(900f, 1800f),
        GrokMood.PLAYFUL to floatArrayOf(1500f, 3000f),
        GrokMood.CELEBRATE to floatArrayOf(1400f, 2600f),
        GrokMood.ORBIT to floatArrayOf(4000f, 8000f),
        GrokMood.RADAR to floatArrayOf(4000f, 8000f),
        GrokMood.PROGRESS to floatArrayOf(4000f, 8000f),
        GrokMood.SPAWNING to floatArrayOf(1200f, 1200f),
        GrokMood.HUMMING to floatArrayOf(5000f, 9000f),
        GrokMood.LOADING to floatArrayOf(6000f, 10000f),
        GrokMood.DICTATING to floatArrayOf(4000f, 8000f),
        GrokMood.SENDING to floatArrayOf(4000f, 8000f),
        GrokMood.RECEIVING to floatArrayOf(4000f, 8000f),
        GrokMood.UPLOADING to floatArrayOf(4000f, 8000f),
        GrokMood.WRITING to floatArrayOf(4000f, 8000f),
        GrokMood.NOTIFYING to floatArrayOf(1500f, 2600f),
        GrokMood.ALERTING to floatArrayOf(2000f, 3600f),
        GrokMood.BOUNCING to floatArrayOf(3000f, 6000f),
        GrokMood.DRAGGING to floatArrayOf(1600f, 3000f),
        GrokMood.POWERING_DOWN to floatArrayOf(6000f, 9000f),
    )

    /** Blink interval range, moods missing from the table never blink. */
    val BLINK_MS: Map<GrokMood, FloatArray> = mapOf(
        GrokMood.IDLE to floatArrayOf(6000f, 14000f),
        GrokMood.LISTENING to floatArrayOf(3000f, 7000f),
        GrokMood.THINKING to floatArrayOf(3500f, 7000f),
        GrokMood.SEARCHING to floatArrayOf(1600f, 4000f),
        GrokMood.WORKING to floatArrayOf(2800f, 5500f),
        GrokMood.EXCITED to floatArrayOf(2000f, 4000f),
        GrokMood.SURPRISED to floatArrayOf(1800f, 3500f),
        GrokMood.SUSPICIOUS to floatArrayOf(4500f, 8000f),
        GrokMood.ANGRY to floatArrayOf(3500f, 7000f),
        GrokMood.HAPPY to floatArrayOf(2500f, 5000f),
        GrokMood.CURIOUS to floatArrayOf(2500f, 5500f),
        GrokMood.CONFUSED to floatArrayOf(2800f, 5500f),
        GrokMood.BORED to floatArrayOf(4000f, 8000f),
        GrokMood.PROUD to floatArrayOf(3500f, 7000f),
        GrokMood.SHY to floatArrayOf(3000f, 6000f),
        GrokMood.SAD to floatArrayOf(4000f, 8000f),
        GrokMood.LAUGHING to floatArrayOf(2500f, 5000f),
        GrokMood.SCARED to floatArrayOf(1200f, 3000f),
        GrokMood.PLAYFUL to floatArrayOf(2000f, 4500f),
        GrokMood.CELEBRATE to floatArrayOf(2200f, 4500f),
        GrokMood.HUMMING to floatArrayOf(4000f, 8000f),
        GrokMood.NOTIFYING to floatArrayOf(2000f, 4000f),
        GrokMood.DRAGGING to floatArrayOf(2200f, 4500f),
    )

    /** Moods that never blink, mirrors the `null` entries of the source table. */
    val NO_BLINK: Set<GrokMood> = setOf(
        GrokMood.SLEEPING, GrokMood.WAKING, GrokMood.DROWSY, GrokMood.ORBIT, GrokMood.RADAR,
        GrokMood.PROGRESS, GrokMood.SPAWNING, GrokMood.LOADING, GrokMood.DICTATING,
        GrokMood.SENDING, GrokMood.RECEIVING, GrokMood.UPLOADING, GrokMood.WRITING,
        GrokMood.ALERTING, GrokMood.BOUNCING, GrokMood.POWERING_DOWN,
    )

    val ONBOARDING: List<GrokMood> = listOf(
        GrokMood.CURIOUS, GrokMood.HAPPY, GrokMood.PLAYFUL, GrokMood.EXCITED,
        GrokMood.LISTENING, GrokMood.PROUD, GrokMood.LAUGHING, GrokMood.SHY,
    )
    const val ONBOARDING_MS = 1200f

    /** Alternates between `idle` and the onboarding reel, the original walk cycle. */
    fun onboardMood(n: Int): GrokMood =
        if (n % 2 == 0) GrokMood.IDLE
        else ONBOARDING[((n - 1) / 2).floorMod(ONBOARDING.size)]

    private fun Int.floorMod(m: Int): Int = ((this % m) + m) % m

    // ------------------------------------------------------------------ springs

    object Springs {
        val spin = floatArrayOf(5f, 0.9f)
        val x = floatArrayOf(3.5f, 1f)
        val y = floatArrayOf(4f, 1f)
        val squash = floatArrayOf(10f, 0.8f)
        val blink = floatArrayOf(26f, 1f)
        val eyeScale = floatArrayOf(9f, 0.85f)
        val gazeX = floatArrayOf(13f, 1f)
        val gazeY = floatArrayOf(13f, 1f)
        val notify = floatArrayOf(9f, 0.55f)
        val humDots = floatArrayOf(6f, 1f)
        val overlay = floatArrayOf(14f, 1f)
        val overlayMix = floatArrayOf(11f, 1f)
        val shape = floatArrayOf(10f, 1f)
        val overlayTurn = floatArrayOf(14f, 1f)
        val spinTurn = floatArrayOf(6.2f, 1f)
    }

    object FaceTune {
        const val SIZE = 0.86f
        const val GAP = 1.18f
        const val HEIGHT = 1f
        const val EYE_WIDTH = 0.96f
        const val EYE_HEIGHT = 0.92f
    }

    val POSE = GrokRot(17f, -14f, 29f)
    const val POSE_SCALE = 1f
    val POSE_HOME = GrokRot(33f, -19f, 38f)
    const val UNIFORM_EYES = true

    /** Moods that flip the eye pair horizontally. */
    val FLIP_EYES: Set<GrokMood> = setOf(GrokMood.HAPPY, GrokMood.EXCITED, GrokMood.PROUD)

    /** Moods that use the rounded "bottom" eye pair. */
    val BOTTOM_EYES: Set<GrokMood> = setOf(GrokMood.PLAYFUL)

    /**
     * Moods whose idle reel is the `vertex` trick set (`V_T` of the web tables).
     * Same members as [FLIP_EYES], kept separate because the two tables answer different questions.
     */
    val V_T: Set<GrokMood> = FLIP_EYES

    /** Moods whose idle reel is the `bottom` trick set (`B_T` of the web tables). */
    val B_T: Set<GrokMood> = BOTTOM_EYES

    val WINK_STATES: Set<GrokMood> = setOf(
        GrokMood.IDLE, GrokMood.HAPPY, GrokMood.EXCITED, GrokMood.CURIOUS, GrokMood.PLAYFUL,
    )

    val SHAPE_ZOOM: Map<String, Float> = mapOf(
        "blob" to 0.92f, "pebble" to 0.96f, "squircle" to 0.84f, "tablet" to 1f,
        "wedge" to 0.94f, "hex" to 0.94f, "cloud" to 1f, "teardrop" to 1f,
    )

    fun shapeZoom(name: String): Float = SHAPE_ZOOM[name] ?: 1f

    fun poseScale(name: String): Float = shapeZoom(name) * GrokGeo.VIEW_SCALE

    fun shapeEyeScale(name: String): Float = SHAPE_ZOOM.getValue("blob") / shapeZoom(name)

    /** Extra zoom applied to the overlay animation of each mood. */
    val OVERLAY_ZOOM: Map<String, Float> = mapOf(
        "dots" to 1.5f, "orbit" to 1.14f, "radar" to 1.14f, "progress" to 1.32f,
        "gather" to 1.15f, "wave" to 1.42f, "send" to 1.12f, "receive" to 1.12f,
        "dock" to 1.3f, "ball" to 1.22f, "whirl" to 1.45f, "pencil" to 1.18f,
        "bang" to 1.28f, "standby" to 1.75f,
    )

    fun overlayViewZoom(kind: String?, scale: Float): Float =
        if (kind == null) 1f else maxOf((OVERLAY_ZOOM[kind] ?: 1f) / maxOf(scale, 1f), 1f)

    /** Two stop ink gradient used by the "ink" body tint. */
    class Ink(val lightFrom: String, val lightTo: String, val darkFrom: String, val darkTo: String)

    val INK: Map<String, Ink> = mapOf(
        "black" to Ink("#585858", "#000000", "#FFFFFF", "#C2C2C2"),
        "brown" to Ink("#AE8968", "#855C36", "#A27952", "#604227"),
        "red" to Ink("#FF5667", "#E02135", "#FF3E51", "#A21826"),
        "orange" to Ink("#FF8838", "#E05B00", "#FF781C", "#C24E00"),
        "yellow" to Ink("#FFAF38", "#E08600", "#FFA31C", "#C27400"),
        "green" to Ink("#1CCF82", "#009957", "#00C972", "#008048"),
        "cyan" to Ink("#58D3C5", "#00A592", "#1CC3B0", "#007769"),
        "blue" to Ink("#459FFE", "#0E74E0", "#2A92FE", "#0C64C1"),
        "violet" to Ink("#B792FE", "#804EE0", "#9159FE", "#5C39A1"),
        "magenta" to Ink("#FF77BE", "#E02A88", "#FF47A6", "#A21E62"),
        "gray" to Ink("#A6A6A6", "#696969", "#B7B7B7", "#777777"),
    )

    const val INK_ANGLE = 135f

    /** Default eye fill, matching `--disk` of the original design. */
    const val EYE_BG = 0xFFF3EFE6.toInt()
    const val STAR_FALLBACK = 0xFFF4C34E.toInt()
}
