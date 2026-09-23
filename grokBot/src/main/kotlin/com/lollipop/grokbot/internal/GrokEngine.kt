package com.lollipop.grokbot.internal

import com.lollipop.grokbot.GrokMood
import kotlin.jvm.JvmName
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** A mutable 2D point, mirrors the `{x, y}` literals of the web build. */
internal class GrokPoint(var x: Float = 0f, var y: Float = 0f)

/**
 * Follow state of the face: `tx`/`ty` is the wanted offset in design units, `x`/`y` the
 * smoothed value the eyes actually use. Mirrors `this.pointer` of the web build.
 */
internal class GrokPointer(
    var x: Float = 0f,
    var y: Float = 0f,
    var tx: Float = 0f,
    var ty: Float = 0f,
)

/**
 * Window space layout of the stage, the Compose counterpart of `getBoundingClientRect()`.
 *
 * The web build reads it back from the DOM and only refreshes the cached copy every 200 ms;
 * here the renderer already knows the numbers and simply hands them over each frame.
 */
internal class GrokStageRect(
    var left: Float = 0f,
    var top: Float = 0f,
    var width: Float = 0f,
    var height: Float = 0f,
)

/**
 * Constructor options of [GrokEngine], the Kotlin side of `character.js` `opts`.
 *
 * Every default matches `GrokCharacter`'s `loginWrap` branch, which is the configuration the
 * shipped product uses.
 */
internal class GrokEngineOptions(
    var shape: String = "blob",
    var color: String = "black",
    var scheme: String = "light",
    var mode: String = "onboarding",
    var state: GrokMood = GrokMood.IDLE,
    /** `false` reproduces the stripped-down "no login" variant. */
    var loginWrap: Boolean = true,
    var emphasis: Boolean = false,
    var followPointer: Boolean = false,
    var paused: Boolean = false,
    var reduceMotion: Boolean = false,
    /** Overrides the body tint with a single flat colour. */
    var inkFlat: Int? = null,
    /** Overrides the eye disk colour. */
    var eyeColor: Int? = null,
    /** Colour of the badge blob inside the eye, `opts.badgeColor` of the web build. */
    var badgeColor: Int = GrokEyeOptions.DEFAULT_BADGE,
    /** Forces the rendered square size in px; `null` keeps the host size. */
    var sizePx: Float? = null,
    var changeListener: ((GrokSnapshot) -> Unit)? = null,
)

/**
 * Per-state scratch values rebuilt by [GrokEngine.setState], mirrors `_freshCtx` of the web build.
 *
 * The same object is the `ctx` argument of [GrokPose.apply]: the pose switch reads and writes it
 * directly, and [GrokEngine.tick] drains the "request" slots (`wakeEye`, `wantPn`, `wantBurst`,
 * …) once per frame. The timers are absolute millisecond stamps, so they stay comparable with
 * the frame clock.
 */
internal class GrokCtx(now: Float, random: Random) {
    var nodUntil: Float = now + 1800f
    var nodEnd: Float = 0f
    var angryShakeUntil: Float = 0f
    var impulseAt: Float = now + GrokMath.rand(500f, 1200f, random)
    var tyKick: Float = 0f
    var spinKick: Float = 0f
    var forceSleepEye: Boolean = false

    /** `[eyeIndex, stiffness]` pair for [GrokEngine.morphEyes]. */
    var wakeEye: IntArray? = null
    var wakeBlink: Boolean = false
    var wakingBlinked: Boolean = false
    var slumpAt: Float = 0f
    var stAt: Float = now + GrokMath.rand(6000f, 10000f, random)

    /** `[turns, direction]` pair for [GrokEngine.pn]. */
    var wantPn: FloatArray? = null
    var wantBlink: Boolean = false
    var dragCycle: Int = -1
    var notifyPop: Boolean = false

    /** `[count, spread]` pair for [GrokParticles.burst]. */
    var wantBurst: FloatArray? = null
    var wakingBurst: Boolean = false
}

/** Immutable view of the engine, emitted through [GrokEngineOptions.changeListener]. */
internal class GrokSnapshot(
    val state: GrokMood,
    val mode: String,
    val shape: String,
    val color: String,
    val scheme: String,
    val eyeFrom: Int,
    val eyeTo: Int,
    val spin: Float,
    val tx: Float,
    val ty: Float,
    val squash: Float,
    val blink: Float,
    val overlay: String?,
)

/**
 * Orchestrator port of `character.js` `GrokCharacter` (L2).
 *
 * The web build talks to an SVG tree; here the same state machine produces a [GrokFrame]
 * which [GrokRenderer] draws. This file only owns state, timing and the public setters.
 *
 * All times are the same millisecond clock the web build uses (`performance.now()`), supplied
 * by the caller so the engine stays free of platform APIs.
 */
internal class GrokEngine(
    val options: GrokEngineOptions = GrokEngineOptions(),
    val random: Random = Random(0x5EED),
) {

    // ------------------------------------------------------------------ identity

    var shapeName: String = options.shape
        private set

    var colorId: String = options.color
        private set

    var scheme: String = options.scheme
        private set

    var mode: String = options.mode
        private set

    var state: GrokMood = options.state
        private set

    /** `false` mirrors the stripped variant, which skips pose, tuning and eye scaling. */
    val loginWrap: Boolean = options.loginWrap

    val eyeTopology: Boolean = options.loginWrap

    val faceTune: Boolean = options.loginWrap

    val pose: GrokRot = if (options.loginWrap) GrokRot(GrokTabs.POSE.turn, GrokTabs.POSE.tilt, GrokTabs.POSE.roll) else GrokRot()

    val poseHome: GrokRot = if (options.loginWrap) GrokRot(GrokTabs.POSE_HOME.turn, GrokTabs.POSE_HOME.tilt, GrokTabs.POSE_HOME.roll) else GrokRot()

    var uniformEyes: Boolean = options.loginWrap

    var eyeScaleProp: Float = if (options.loginWrap) GrokTabs.shapeEyeScale(options.shape) else 1f

    var emphasis: Boolean = options.emphasis

    var followPointer: Boolean = options.followPointer

    var gazeTarget: GrokPoint? = null

    var paused: Boolean = options.paused

    val reduceMotion: Boolean = options.reduceMotion

    var sizePx: Float? = options.sizePx

    var eyeColor: Int? = options.eyeColor
        private set

    var inkFlat: Int? = options.inkFlat
        private set

    /**
     * Colour of the badge blob inside the eye.
     *
     * The web build reads it back from the `stroke` of the badge element on every frame; here the
     * host owns that element, so this stays a plain mutable field that [paint] copies into the eye
     * options.
     */
    var badgeColor: Int = options.badgeColor

    /** Current `--fg` value, already resolved for [scheme]. */
    var fgHex: String = "#000000"
        private set

    /** Two stops of the `--ink` gradient, already resolved for [scheme]. */
    var inkFromHex: String = "#585858"
        private set
    var inkToHex: String = "#000000"
        private set

    var eyeBg: Int = GrokTabs.EYE_BG
        private set

    /** Poses resolved by [applyPoseScale], applied to the whole stage. */
    var poseScale: Float = if (options.loginWrap) GrokTabs.poseScale(options.shape) else 1f
        private set

    // ------------------------------------------------------------------ springs

    val spin = GrokSpring(0f)
    val tx = GrokSpring(0f)
    val ty = GrokSpring(0f)
    val squash = GrokSpring(1f)
    val blink = GrokSpring(1f)
    val eyeScale = GrokSpring(1f)
    val gazeX = GrokSpring(0f)
    val gazeY = GrokSpring(0f)
    val eyeMorph = GrokSpring(1f)
    val overlay = GrokSpring(0f)
    val overlayMix = GrokSpring(1f)
    val notify = GrokSpring(0f)
    val humDots = GrokSpring(0f)
    val shapeSpring = GrokSpring(1f)
    val overlayTurn = GrokSpring(0f)
    var emphasisBlend: Float = 0f

    // ------------------------------------------------------------------ eyes

    var eyeFrom: Int = 0
    var eyeTo: Int = 0
    var eyeStiffness: Float = 7f
    var eyeIdx: Int = 0

    /** Polygon snapshot taken when an eye morph starts, filled by the frame pass. */
    var fromPolys: Array<FloatArray>? = null

    // ------------------------------------------------------------------ clock

    var t0: Float = 0f
        private set
    var stateAt: Float = 0f
        private set
    var last: Float = 0f
        private set
    var moodN: Int = 0

    var eyeUntil: Float = 0f
    var blinkUntil: Float = 0f
    var gazeUntil: Float = 0f
    val blinkQueue: ArrayDeque<GrokBlink> = ArrayDeque()

    var winkAt: Float = -1e9f
    var winkEye: Int = 0
    var winkUntil: Float = 0f

    // ------------------------------------------------------------------ runtime

    /** Spin trick spring, `GrokTricks.makeSpinTurn(...)`. */
    var spinTurn: GrokSpring? = null
    var trick: GrokTrick? = null
    var hopAt: Float = -1f
    var trickAt: Float = 0f
    var trickCycle: Int = 0
    var wildWide: Boolean = false
    var ovSpin: Float = 0f
    var ovTurnAcc: Float = 0f
    var ovOn: Boolean = false
    var ovTurnDir: Int = 1

    val pointer = GrokPointer()
    var pointerRaw: GrokPoint? = null

    /** Stage layout, see [setStage]. */
    val stage = GrokStageRect()

    /** Stamp of the last layout refresh, mirrors `rectAt` of the web build. */
    var rectAt: Float = -1e9f
    var pxW: Float = 190f
        private set
    var pxAt: Float = 0f

    // ------------------------------------------------------------------ shape morph

    var prevShape: String = options.shape
    var prevFace: GrokBot? = null
    var prevRing: FloatArray? = null
    var prevTilt: Float? = null
    var prevBelt: Float? = null

    // ------------------------------------------------------------------ overlay bookkeeping

    var ovKind: String? = null
    var ovPrev: String? = null
    var ovTarget: String? = null
    var ovRest: Boolean = false
    var ovRestAt: Float = 0f

    var partScale: Float = 1f
    var celebrateAt: Float = -1f

    val extras: GrokCharacterExtras = GrokCharacterExtras()

    /** Per-frame scratch of [tick], reused so that a frame never allocates. */
    private val poseOut = GrokPoseResult()
    private val poseExtra = GrokPoseExtra()
    private val trickEval = GrokTrickEval()
    private val gaze = GrokGaze()

    // ------------------------------------------------------------------ subsystems

    /** Overlay painters, driven by the frame pass. */
    val fx: GrokOverlay = GrokOverlay(random)

    /** Spark/ribbon emitter, driven by the frame pass. */
    val particles: GrokParticles =
        GrokParticles(options.reduceMotion, { particleRadius() }, random)

    lateinit var ctx: GrokCtx
        private set

    /**
     * Resets every clock derived value. The web build does this in the constructor once
     * `performance.now()` is known; here the host passes the first frame stamp.
     */
    fun start(now: Float) {
        t0 = now
        stateAt = now
        last = now
        moodN = 0
        eyeUntil = now + GrokMath.rand(EYE_HOLD[0], EYE_HOLD[1], random)
        blinkUntil = now + GrokMath.rand(1500f, 7000f, random)
        gazeUntil = now + 800f
        winkAt = -1e9f
        eyeIdx = 0
        winkUntil = now + GrokMath.rand(3000f, 8000f, random)
        trickAt = now + GrokMath.rand(2500f, 5000f, random)
        trickCycle = random.nextInt(5)
        ovRestAt = 0f
        rectAt = -1e9f
        ctx = freshCtx(now)
        resetInk()
        setColor(colorId, scheme)
        applyPoseScale()
    }

    fun destroy() {
        spinTurn = null
        trick = null
    }

    /** Mirrors `_freshCtx`. */
    fun freshCtx(now: Float): GrokCtx = GrokCtx(now, random)

    /** Mirrors `_freshCtx` for the idle hold window of the current mood. */
    private val EYE_HOLD: FloatArray
        get() = GrokTabs.EYE_HOLD_MS[GrokMood.IDLE] ?: floatArrayOf(2500f, 5500f)

    private fun resetInk() {
        fx.resetInk()
    }

    // ------------------------------------------------------------------ setters

    fun setMode(mode: String, now: Float) {
        this.mode = mode
        if (mode == "onboarding") {
            moodN = 0
            stateAt = now
            setState(GrokMood.IDLE, now, resetEyes = true)
        }
    }

    @JvmName("applyPaused")
    fun setPaused(value: Boolean) {
        paused = value
    }

    @JvmName("applyEmphasis")
    fun setEmphasis(value: Boolean) {
        emphasis = value
    }

    @JvmName("applyFollowPointer")
    fun setFollowPointer(value: Boolean) {
        followPointer = value
        if (!value) {
            pointerRaw = null
            gazeTarget = null
        }
    }

    @JvmName("applyGazeTarget")
    fun setGazeTarget(point: GrokPoint?) {
        gazeTarget = point
    }

    /**
     * Swaps the body silhouette. When a previous morph is still running its intermediate ring,
     * face and tilt are frozen as the new "from" state so the swap stays continuous.
     */
    fun setShape(name: String, now: Float) {
        val next = GrokGeo.SHAPES.firstOrNull { it.id == name } ?: return
        if (name == shapeName) return
        val current = GrokGeo.SHAPES.firstOrNull { it.id == shapeName } ?: return
        val k = GrokMath.k2(GrokMath.clamp(shapeSpring.x, 0f, 1f))
        val rest = GrokFx.shapeMetrics(current, GrokGeo.R)
        val prevFace = prevFace
        val prevRing = prevRing
        if (k >= 1f || prevFace == null || prevRing == null) {
            this.prevFace = rest.face
            this.prevRing = rest.ring
            this.prevTilt = rest.tilt
            this.prevBelt = rest.belt
        } else {
            val faceOut = prevFace
            GrokMath.lerpFace(prevFace, rest.face, k, faceOut)
            val ringOut = prevRing
            GrokMath.lerpPoly(prevRing, rest.ring, k, ringOut)
            this.prevTilt = (this.prevTilt ?: rest.tilt) + (rest.tilt - (this.prevTilt ?: rest.tilt)) * k
            this.prevBelt = (this.prevBelt ?: rest.belt) + (rest.belt - (this.prevBelt ?: rest.belt)) * k
        }
        prevShape = shapeName
        shapeName = next.id
        shapeSpring.x = 0f
        shapeSpring.v = 0f
        shapeSpring.t = 1f
        if (loginWrap) eyeScaleProp = GrokTabs.shapeEyeScale(next.id)
        applyPoseScale()
        cycleShapeTrick(now)
    }

    fun setColor(id: String, scheme: String? = null) {
        colorId = id
        if (scheme != null) this.scheme = scheme
        val palette = GrokGeo.PALETTE[id] ?: GrokGeo.PALETTE.getValue("black")
        val flat = inkFlat
        fgHex = if (flat != null) hexOf(flat) else if (this.scheme == "dark") palette.dark else palette.light
        val ink = GrokTabs.INK[id] ?: GrokTabs.INK.getValue("black")
        val dark = this.scheme == "dark"
        inkFromHex = if (dark) ink.darkFrom else ink.lightFrom
        inkToHex = if (dark) ink.darkTo else ink.lightTo
        eyeBg = eyeColor ?: GrokTabs.EYE_BG
    }

    fun setInk(flat: Int?) {
        inkFlat = flat
        setColor(colorId)
    }

    fun setEyeColor(color: Int?) {
        eyeColor = color
        eyeBg = color ?: GrokTabs.EYE_BG
    }

    /**
     * Enters a mood: rebuilds the eye playlist cursor, the blink/wink/gaze timers and the
     * per-state context. Mirrors `setState` of the web build.
     */
    fun setState(name: GrokMood, now: Float, resetEyes: Boolean = false) {
        val list = GrokTabs.EYE_PLAYLIST[name] ?: return
        state = name
        stateAt = now
        eyeIdx = 0
        if (resetEyes) {
            eyeFrom = list[0]
            eyeTo = list[0]
            fromPolys = null
            eyeMorph.x = 1f
            eyeMorph.t = 1f
            eyeMorph.v = 0f
        } else if (name != GrokMood.SLEEPING && name != GrokMood.WAKING) {
            morphEyes(list[0], if (name == GrokMood.EXCITED) 10f else 8f)
        }
        eyeUntil = stateAt + GrokMath.rand(EYE_HOLD_MS(name)[0], EYE_HOLD_MS(name)[1], random)
        val blink = if (name in GrokTabs.NO_BLINK) null else GrokTabs.BLINK_MS[name]
        // The *first* blink of a mood is scheduled from the fixed 1500..7000 ms window
        // (`character.js:247`), not from the mood's own cadence range: the cadence range only
        // drives the repeats (`character.js:617`). Scheduling the first one from the cadence
        // moved every blink of a fast-blinker earlier, which is invisible at the resting lid but
        // straddles the sample instant for `curious` (2 x 4000 ms) and `surprised`.
        blinkUntil = if (blink != null) {
            stateAt + GrokMath.rand(1500f, 7000f, random)
        } else {
            Float.POSITIVE_INFINITY
        }
        gazeUntil = stateAt + GrokMath.rand(500f, 1400f, random)
        winkUntil = stateAt + GrokMath.rand(3000f, 8000f, random)
        ctx = freshCtx(stateAt)
        ctx.stAt = stateAt + when (name) {
            GrokMood.EXCITED -> GrokMath.rand(400f, 1100f, random)
            GrokMood.SEARCHING -> GrokMath.rand(800f, 1600f, random)
            GrokMood.WORKING -> GrokMath.rand(1200f, 2400f, random)
            else -> GrokMath.rand(6000f, 10000f, random)
        }
        celebrateAt = if (name == GrokMood.CELEBRATE) stateAt + 140f else -1f
        trick = null
        spinTurn = null
        hopAt = -1f
        wildWide = false
        if (name != GrokMood.WRITING) fx.resetInk()
        if (name != GrokMood.WAKING && name != GrokMood.SLEEPING && name != GrokMood.DROWSY) {
            GrokEyes.queueBlink(blinkQueue, stateAt, random)
        }
        options.changeListener?.invoke(snapshot())
    }

    private fun EYE_HOLD_MS(name: GrokMood): FloatArray =
        GrokTabs.EYE_HOLD_MS[name] ?: floatArrayOf(2500f, 5500f)

    fun snapshot(): GrokSnapshot = GrokSnapshot(
        state = state,
        mode = mode,
        shape = shapeName,
        color = colorId,
        scheme = scheme,
        eyeFrom = eyeFrom,
        eyeTo = eyeTo,
        spin = spin.x,
        tx = tx.x,
        ty = ty.x,
        squash = squash.x,
        blink = blink.x,
        overlay = ovKind,
    )

    // ------------------------------------------------------------------ one shot actions

    fun spinOnce(turns: Float = 1f) {
        pn(turns)
    }

    fun bounceOnce(now: Float) {
        hop(now)
    }

    fun burstOnce() {
        if (!reduceMotion) particles.burst(22, 1.1f, 0.3f)
    }

    /** Mirrors `_applyPoseScale`; the host reads [poseScale] to size the stage. */
    fun applyPoseScale() {
        poseScale = if (loginWrap) GrokTabs.poseScale(shapeName) else poseScale
    }

    // ------------------------------------------------------------------ frame pass

    /** Rounded body outline, the `fx.circlePath` of the web build. */
    private val circlePath: String = GrokSvgPath.circlePathOf(GrokGeo.R)

    /** Vertex count of one eye polygon. Every polygon of [GrokGeo.EYES] shares it. */
    private val eyePolySize: Int = GrokGeo.EYES[0][0].size

    /**
     * Frame last produced by [paint].
     *
     * The engine owns the single instance and rewrites it in place, so the renderer has to consume
     * it before the next call.
     */
    val frame: GrokFrame = GrokFrame(fx, eyePolySize)

    /** Per-frame scratch of [paint], reused so that painting a frame never allocates. */
    private val scratchEyes = GrokEyeOptions()
    private val scratchPolys = arrayOf(FloatArray(eyePolySize), FloatArray(eyePolySize))
    private val scratchFace = GrokBot()
    private val scratchRing = FloatArray(GrokFx.RING_SAMPLES * 2)
    private val scratchBodyRing = FloatArray(GrokFx.RING_SAMPLES * 2)
    private val scratchPointer = FloatArray(2)

    /**
     * One shot geometry wiring, the counterpart of `_build`.
     *
     * The web build also creates the SVG tree here; per ADR-2 there is none on this side, the
     * renderer resolves [shapeName] against [GrokGeo.SHAPES] on every frame. What is left is the
     * overlay path cache the effect layer draws with.
     */
    fun build() {
        fx.setPaths(
            circle = circlePath,
            pencil = GrokSvgPath.capsule(30f, 88f, GrokGeo.R),
            bang = GrokSvgPath.taper(30f, 17f, 96f, GrokGeo.R),
        )
    }

    /**
     * Fills [frame] with the geometry of one frame: body transform, outline, viewport and eyes.
     * Mirrors `_paint` of the web build, with every attribute write turned into a [frame] field.
     *
     * The SVG handles of the original (`svg`, `group`, `body`, `clipPath`, `eyeEls`, `badge`) have
     * no counterpart per ADR-2, the renderer owns them; that is why the view box, the outline and
     * the badge colour travel inside the frame instead of being read back from a tree.
     *
     * Expects [tick] to have run for the same stamp. The shape lookup can only fail if the engine
     * was built with an id [setShape] already rejected, in which case the frame is left stale.
     */
    fun paint(now: Float) {
        val r = GrokGeo.R
        val shape = GrokGeo.SHAPES.firstOrNull { it.id == shapeName } ?: return

        val morphK = GrokMath.k2(GrokMath.clamp(shapeSpring.x, 0f, 1f))
        val prevFace = prevFace
        val prevRing = prevRing
        val morphing = morphK < 0.999f && prevFace != null
        val face: GrokBot
        if (morphing) {
            GrokMath.lerpFace(prevFace!!, shape.face, morphK, scratchFace)
            face = scratchFace
        } else {
            face = shape.face
        }
        val restTilt = tiltOf(shape)
        val tilt = if (morphing) {
            val from = prevTilt
                ?: GrokGeo.SHAPES.firstOrNull { it.id == prevShape }?.let { tiltOf(it) }
                ?: 1f
            from + (restTilt - from) * morphK
        } else {
            restTilt
        }

        val presence = GrokMath.clamp(overlay.x, 0f, 1f)
        val mix = GrokMath.clamp(overlayMix.x, 0f, 1f)
        // The dot pulse reads the flag, so it has to be fresh before `extras`, not before `paint`.
        fx.reduce = reduceMotion
        fx.extras(now, stateAt, ovKind, ovPrev, presence, mix)
        val ov = fx.extras

        val bodyW = 1f - presence
        val ex = extras
        val exTurn = ex.turn
        frame.translateX = r + tx.x * bodyW + ex.yi * bodyW + ov.offsetX * presence
        frame.translateY = r + (ty.x + ex.hop) * bodyW + ex.ki * bodyW -
            ov.dotsLift * ov.dotsAmount + ov.offsetY * presence
        // `character.js:728` adds `Yr` (degrees) here, not `turn` (radians, the shape-ring angle).
        frame.rotation = (spin.x * bodyW + ex.kr * bodyW) * tilt +
            ex.yr * bodyW + ov.rotation * presence
        frame.scaleX = bodyW + ov.radiusScale * presence
        frame.scaleY = squash.x * bodyW + ov.radiusScale * presence
        frame.opacity = (1f - (1f - ov.dotsTone) * ov.dotsAmount) * (1f - ov.fade)

        val spinning = exTurn != null
        val restRing = if (morphing && prevRing != null) {
            GrokMath.lerpPoly(prevRing, GrokFx.shapeRing(shape.path, r), morphK, scratchRing)
            scratchRing
        } else {
            GrokFx.shapeRing(shape.path, r)
        }
        var liveRing = restRing
        var turned = false
        val turnAt = if (!morphing && spinning) GrokFx.turnAtOf(shapeName, shape.path, r) else null
        if (turnAt != null) {
            liveRing = turnAt(exTurn!!)
            turned = true
        }

        val faceTop: Float
        val faceBottom: Float
        if (morphing || turned) {
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            var i = 1
            while (i < liveRing.size) {
                val y = liveRing[i]
                if (y < lo) lo = y
                if (y > hi) hi = y
                i += 2
            }
            faceTop = lo
            faceBottom = hi
        } else {
            faceTop = shape.top
            faceBottom = shape.bottom
        }

        frame.bodyPath = bodyOutline(shape, liveRing, morphing, turned, presence)

        fx.paint(now, stateAt, ovKind, ovPrev, presence, mix, r, reduceMotion)

        // The particle layer is the `back` / `front` containers of the web build. `tick` already
        // stepped them, so the renderer just takes the live list.
        frame.particles = particles.particles()

        // `opts.pose.scale || 1`: the pose scale is a constant on this side, the pose itself only
        // carries the three angles.
        val pScale = if (GrokTabs.POSE_SCALE == 0f) 1f else GrokTabs.POSE_SCALE
        val zCur = GrokTabs.overlayViewZoom(ovKind, pScale)
        val zPrev = GrokTabs.overlayViewZoom(ovPrev, pScale)
        val shrink = 1f - GrokMath.dke(GrokMath.clamp((pxW - 44f) / 90f, 0f, 1f))
        val zoom = 1f + (zCur * mix + zPrev * (1f - mix) - 1f) * presence * shrink
        val viewHalf = GrokGeo.VIEW_WIDTH / 2f / zoom
        frame.viewBox.x = GrokGeo.VIEW_MID - viewHalf
        frame.viewBox.y = GrokGeo.VIEW_MID - viewHalf
        frame.viewBox.size = viewHalf * 2f

        val eyeMorphT = GrokMath.clamp(eyeMorph.x, 0f, 1f)
        currentPolysInto(eyeMorphT, scratchPolys)

        val overlayLive = presence > 0.001f || abs(overlayTurn.t - overlayTurn.x) > 0.01f
        var cyl = if (overlayLive) overlayTurn.x else null
        if (exTurn != null) cyl = (cyl ?: 0f) + exTurn

        val hasPointer = gazeTarget != null || (followPointer && pointerRaw != null)
        val eyePointer: FloatArray?
        if (hasPointer) {
            scratchPointer[0] = pointer.x
            scratchPointer[1] = pointer.y
            eyePointer = scratchPointer
        } else {
            eyePointer = null
        }

        val eyes = scratchEyes
        eyes.now = now
        eyes.morphT = eyeMorphT
        eyes.shape = shape
        eyes.face = face
        eyes.uniformEyes = uniformEyes
        eyes.eyeScaleProp = eyeScaleProp
        eyes.eyeBoostX = eyeScale.x
        eyes.blinkX = blink.x
        eyes.gazeX = gazeX.x
        eyes.gazeY = gazeY.x
        eyes.winkAt = winkAt
        eyes.winkEye = winkEye
        eyes.turn = cyl
        eyes.cr = if (eyeTopology) GrokMath.relRot(pose, poseHome) else null
        eyes.pointer = eyePointer
        eyes.notifyX = notify.x
        eyes.overlayX = overlay.x
        eyes.badgeColor = badgeColor
        eyes.extrasZr = ex.zr
        eyes.extrasWi = ex.wi
        eyes.ringHint = if (morphing || turned) liveRing else null
        eyes.top = faceTop
        eyes.bottom = faceBottom
        eyes.emphasisBlend = emphasisBlend
        eyes.badgeRing = restRing
        GrokEyes.paint(eyes, scratchPolys, frame.eyeFrame)

        val hum = GrokMath.clamp(humDots.x, 0f, 1f)
        if (hum <= 0.01f) return
        for (i in 0 until 2) {
            val angle = ovSpin * 0.85f + i * PI.toFloat()
            val orbit = shape.radius * 1.3f
            val tone = 0.55f + 0.45f * GrokMath.clamp((cos(angle) + 1f) / 2f, 0f, 1f)
            val dot = fx.part(3 + i)
            dot.show()
            dot.setCircle(
                r + orbit * sin(angle),
                r - orbit * 0.38f * cos(angle) - 8f,
                7.5f * tone * hum,
            )
            dot.opacity = (0.3f + 0.7f * tone) * hum
        }
    }

    /**
     * Outline of the frame: the silhouette at rest, the running overlay ring, or a blend of the two
     * while the overlay fades in and out. Mirrors the `bodyD` block of `_paint`.
     *
     * [liveRing] is the ring the outline starts from, [morphing] and [turned] tell whether the first
     * branch may fall back to [GrokShapeData.path], and [presence] is the clamped `overlay.x` the
     * blend weight is derived from.
     */
    private fun bodyOutline(
        shape: GrokShapeData,
        liveRing: FloatArray,
        morphing: Boolean,
        turned: Boolean,
        presence: Float,
    ): String {
        val r = GrokGeo.R
        val blend = GrokMath.clamp(presence / GrokFx.P_BLEND, 0f, 1f)
        val pencil = ovKind == GrokFx.KIND_PENCIL || ovPrev == GrokFx.KIND_PENCIL
        val teardrop = GrokGeo.SHAPES.firstOrNull { it.id == GrokFx.KIND_TEARDROP }?.path
        return when {
            blend >= 1f ->
                if (pencil) {
                    GrokSvgPath.closedSpline(
                        GrokFx.overlayRing(ovKind ?: "", r, teardrop),
                    )
                } else {
                    circlePath
                }
            blend <= 0f && !morphing && !turned -> shape.path
            else -> {
                val ring = if (blend <= 0f) {
                    liveRing
                } else {
                    val to = GrokFx.overlayRing(ovKind ?: ovPrev ?: "", r, teardrop)
                    GrokMath.lerpPoly(liveRing, to, GrokMath.k2(blend), scratchBodyRing)
                    scratchBodyRing
                }
                GrokSvgPath.closedSpline(ring)
            }
        }
    }

    /** `shape.tiltScale || 1`, how much a 3D turn is squashed for this silhouette. */
    private fun tiltOf(shape: GrokShapeData): Float =
        if (shape.tiltScale == 0f) 1f else shape.tiltScale

    /**
     * Publishes the stage layout for [updatePointer]. The web build throttles the DOM read to
     * 200 ms; here the numbers come from the Compose layout, so they are always current and
     * [now] only keeps [rectAt] in sync with the original bookkeeping.
     */
    fun setStage(left: Float, top: Float, width: Float, height: Float, now: Float) {
        stage.left = left
        stage.top = top
        stage.width = width
        stage.height = height
        rectAt = now
    }

    /**
     * Drives the effect layer of the current mood: picks the target kind, runs the on/off cycle
     * of the cycling moods and cross fades when the kind changes. Mirrors `_stepOverlay`.
     */
    fun stepOverlay(now: Float) {
        val want = fx.map[state.id]
        if (want != ovTarget) {
            ovTarget = want
            fx.overlayAt = now
            ovRest = false
            ovRestAt = 0f
        }
        var on = want != null
        if (want != null && fx.cycle.contains(state.id)) {
            if (!ovRest && now - fx.overlayAt > (fx.cycleOn[state.id] ?: 2500f)) {
                ovRest = true
                ovRestAt = now
            } else if (ovRest && now - ovRestAt > fx.cycleOff) {
                ovRest = false
                fx.overlayAt = now
            }
            on = !ovRest
        }
        overlay.t = if (on) 1f else 0f
        if (on != ovOn) {
            if (!reduceMotion) {
                if (on) ovTurnDir = GrokMath.signI(random)
                ovTurnAcc += PI.toFloat() * ovTurnDir
                overlayTurn.t = ovTurnAcc
            }
            ovOn = on
        }
        if (want != null && want != ovKind) {
            if (ovKind != null && overlay.x > 0.02f) {
                ovPrev = ovKind
                overlayMix.x = 0f
                overlayMix.v = 0f
                overlayMix.t = 1f
            } else {
                ovPrev = null
                overlayMix.x = 1f
                overlayMix.v = 0f
                overlayMix.t = 1f
            }
            ovKind = want
            fx.overlayAt = now
            if (want != "pencil") fx.resetInk()
        }
        if (want == null && overlay.x < 0.004f) {
            ovKind = null
            ovPrev = null
        }
        if (overlayMix.x > 0.996f) ovPrev = null
    }

    /**
     * Eases the pointer towards its target. [gazeTarget] is already an absolute window space
     * point, while `pointerRaw` is a raw touch position that first gets projected onto the
     * ellipse around the stage centre so the eyes never leave the body.
     */
    fun updatePointer() {
        val src = gazeTarget ?: if (followPointer) pointerRaw else null
        if (src != null && stage.width > 0f) {
            val mapped = if (gazeTarget != null) {
                src
            } else {
                val p = GrokMath.mapPointer(
                    stage.left,
                    stage.top,
                    stage.width,
                    stage.height,
                    src.x,
                    src.y,
                )
                GrokPoint(p[0], p[1])
            }
            pointer.tx = GrokMath.clamp(
                (mapped.x - (stage.left + stage.width / 2f)) / stage.width,
                -0.6f,
                0.6f,
            ) * 22f
            pointer.ty = GrokMath.clamp(
                (mapped.y - (stage.top + stage.height / 2f)) / stage.height,
                -0.6f,
                0.6f,
            ) * 14f
        } else {
            pointer.tx = 0f
            pointer.ty = 0f
        }
        val z = GrokMath.rn(0.16f)
        pointer.x += (pointer.tx - pointer.x) * z
        pointer.y += (pointer.ty - pointer.y) * z
    }

    /**
     * Advances the whole character by one frame: the pose switch, the one-shot requests it leaves
     * behind, the idle reels (tricks, eye morphs, blinks, gaze, winks), the spring integration and
     * finally the particle emitter. Mirrors `_tick` of the web build.
     *
     * [dt] is clamped to 100 ms so a long stall (backgrounded app, slow first frame) cannot blow up
     * the spring integration. A [paused] engine still consumes the frame stamp but runs no pass.
     *
     * The trailing `_paint` call of the web build is not here: it belongs to the drawing layer.
     * [now] is an absolute millisecond stamp on the same clock as every other stamp in this class.
     */
    fun tick(now: Float) {
        val dt = min((now - last) / 1000f, 0.1f)
        last = now
        if (paused) return

        if (mode == "onboarding" && now - stateAt >= GrokTabs.ONBOARDING_MS) {
            moodN += 1
            setState(GrokTabs.onboardMood(moodN), now)
        }

        val mt = (now - t0) / 1000f
        val dtState = (now - stateAt) / 1000f
        poseExtra.eyeTo = eyeTo
        poseExtra.eyeMorphX = eyeMorph.x
        poseExtra.blinkX = blink.x
        GrokPose.apply(state, mt, dtState, now, ctx, poseExtra, random, poseOut)
        spin.t = poseOut.spin
        tx.t = poseOut.tx
        ty.t = poseOut.ty
        squash.t = poseOut.squash
        eyeScale.t = poseOut.eyeBoost

        if (ctx.tyKick != 0f) {
            ty.v += ctx.tyKick
            ctx.tyKick = 0f
        }
        if (ctx.spinKick != 0f) {
            spin.v += ctx.spinKick
            ctx.spinKick = 0f
        }
        if (ctx.forceSleepEye) {
            ctx.forceSleepEye = false
            morphEyes(13, 11f)
        }
        val wakeEye = ctx.wakeEye
        if (wakeEye != null) {
            morphEyes(wakeEye[0], wakeEye[1].toFloat())
            ctx.wakeEye = null
        }
        if (ctx.wakeBlink && !ctx.wakingBlinked && blinkQueue.isEmpty()) {
            GrokEyes.queueBlink(blinkQueue, now, random)
            ctx.wakingBlinked = true
        }
        ctx.wakeBlink = false
        if (ctx.wantBlink) {
            GrokEyes.queueBlink(blinkQueue, now, random)
            ctx.wantBlink = false
        }
        val wantPn = ctx.wantPn
        if (wantPn != null) {
            pn(wantPn[0], wantPn[1])
            ctx.wantPn = null
        }
        val wantBurst = ctx.wantBurst
        if (wantBurst != null) {
            particles.burst(ceil(wantBurst[0]).toInt(), wantBurst[1])
            ctx.wakingBurst = true
            ctx.wantBurst = null
        }

        stepOverlay(now)

        if (celebrateAt > 0f && now >= celebrateAt && trick == null && spinTurn == null) {
            trick = GrokTricks.startTrick(GrokTrickKind.SPIN_WILD, reduceMotion, now, random)
            celebrateAt = now + 6200f
        }

        if (now >= trickAt) {
            if ((state in GrokTabs.V_T || state in GrokTabs.B_T) &&
                spinTurn == null && hopAt < 0f && trick == null
            ) {
                val roll = random.nextFloat()
                if (state in GrokTabs.V_T) {
                    if (roll < 0.55f) {
                        pn(1f)
                    } else {
                        trick = GrokTricks.startTrick(
                            GrokTrickKind.SPIN_BOUNCE, reduceMotion, now, random,
                        )
                    }
                } else if (roll < 0.34f) {
                    trick = GrokTricks.startTrick(GrokTrickKind.SPIN_BOUNCE, reduceMotion, now, random)
                } else if (roll < 0.62f) {
                    hop(now)
                } else if (roll < 0.86f) {
                    trick = GrokTricks.startTrick(GrokTrickKind.SPIN_DIZZY, reduceMotion, now, random)
                } else {
                    pn(1f)
                }
            }
            trickAt = now + GrokMath.rand(9000f, 18000f, random)
        }

        GrokTricks.evalTrick(trick, now, trickEval)
        if (trickEval.wantHop) hop(now)
        if (trickEval.done) trick = null
        var hopValue = GrokTricks.hopY(hopAt, now)
        if (hopValue == null) {
            hopAt = -1f
            hopValue = 0f
        }
        var turn = trickEval.turn
        var activeSpin = spinTurn
        if (activeSpin != null) {
            turn = (turn ?: 0f) + activeSpin.x
            if (GrokTricks.spinTurnSettled(activeSpin)) {
                spinTurn = null
                activeSpin = null
            }
        }
        extras.copyFrom(trickEval)
        extras.turn = turn
        extras.hop = hopValue

        val trickEyeBoost = extras.eyeBoost
        if (trickEyeBoost != null) eyeScale.t = trickEyeBoost

        val playlist = GrokTabs.EYE_PLAYLIST[state]
        val eyeHold = GrokTabs.EYE_HOLD_MS[state]
        if (state != GrokMood.WAKING && state != GrokMood.SLEEPING && now >= eyeUntil &&
            playlist != null && playlist.isNotEmpty() && eyeHold != null
        ) {
            eyeIdx = (eyeIdx + 1 +
                floor(GrokMath.rand(0f, (playlist.size - 1).toFloat(), random)).toInt()
                ) % playlist.size
            val stiffness =
                if (state == GrokMood.SEARCHING || state == GrokMood.EXCITED) 10f else 6f
            morphEyes(playlist[eyeIdx], stiffness)
            eyeUntil = now + GrokMath.rand(eyeHold[0], eyeHold[1], random)
        }

        val blinkCadence = GrokTabs.BLINK_MS[state]
        if (blinkCadence != null && now >= blinkUntil) {
            GrokEyes.queueBlink(blinkQueue, now, random)
            blinkUntil = now + GrokMath.rand(blinkCadence[0], blinkCadence[1], random)
        }
        val blinkKey = GrokEyes.consumeBlink(blinkQueue, now)
        blink.t = blinkKey
            ?: if (blinkQueue.isNotEmpty()) blink.t else (extras.lidMul ?: poseOut.lid)

        if (now >= gazeUntil) {
            GrokPose.nextGaze(state, random, gaze)
            gazeX.t = gaze.x
            gazeY.t = gaze.y
            gazeUntil = now + GrokMath.rand(gaze.holdMin, gaze.holdMax, random)
        }

        if (state in GrokTabs.WINK_STATES && now >= winkUntil) {
            winkAt = now
            winkEye = if (random.nextFloat() < 0.5f) 0 else 1
            winkUntil = now + GrokMath.rand(4500f, 10000f, random)
        }

        emphasisBlend += ((if (emphasis) 1f else 0f) - emphasisBlend) * GrokMath.rn(0.12f)
        if (emphasis) {
            eyeScale.t = max(eyeScale.t, 1.32f)
            blink.t = max(blink.t, 1.18f)
        }

        val humming = state == GrokMood.HUMMING
        val loading = state == GrokMood.LOADING
        if ((humming || loading) && !reduceMotion) {
            val depth = if (loading) 3f else 1.6f
            val speed = when {
                dtState < 0.5f -> 7f * GrokMath.k2(dtState / 0.5f)
                dtState < 1.3f -> 7f + (depth - 7f) * GrokMath.k2((dtState - 0.5f) / 0.8f)
                else -> depth + 0.3f * sin(dtState * 0.5f)
            }
            ovSpin += speed * dt
        }

        if (reduceMotion) {
            GrokTabs.EYE_PLAYLIST[state]?.firstOrNull()?.let { morphEyes(it) }
            spin.t = 0f
            tx.t = 0f
            ty.t = 0f
            squash.t = 1f
            blink.t = 1f
            eyeScale.t = 1f
        }

        val springs = GrokTabs.Springs
        val steps = GrokMath.springSteps(dt)
        val step = dt / steps
        for (i in 0 until steps) {
            eyeMorph.step(eyeStiffness, 1f, step)
            spinTurn?.step(springs.spinTurn[0], springs.spinTurn[1], step)
            spin.step(springs.spin[0], springs.spin[1], step)
            tx.step(springs.x[0], springs.x[1], step)
            ty.step(springs.y[0], springs.y[1], step)
            squash.step(springs.squash[0], springs.squash[1], step)
            blink.step(springs.blink[0], springs.blink[1], step)
            eyeScale.step(springs.eyeScale[0], springs.eyeScale[1], step)
            notify.step(springs.notify[0], springs.notify[1], step)
            humDots.step(springs.humDots[0], springs.humDots[1], step)
            gazeX.step(springs.gazeX[0], springs.gazeX[1], step)
            gazeY.step(springs.gazeY[0], springs.gazeY[1], step)
            overlay.step(springs.overlay[0], springs.overlay[1], step)
            overlayMix.step(springs.overlayMix[0], springs.overlayMix[1], step)
            shapeSpring.step(springs.shape[0], springs.shape[1], step)
            overlayTurn.step(springs.overlayTurn[0], springs.overlayTurn[1], step)
        }
        if (reduceMotion) {
            overlayMix.x = 1f
            overlayTurn.x = overlayTurn.t
            overlay.x = overlay.t
        }
        notify.t = if (state == GrokMood.NOTIFYING) 1f else 0f
        humDots.t = if (state == GrokMood.HUMMING) 1f else 0f

        val extraTurn = extras.turn
        var spinAngle = 0f
        if (activeSpin != null) {
            spinAngle = activeSpin.x
        } else if (extraTurn != null) {
            spinAngle = extraTurn
        } else if (humming || loading) {
            spinAngle = ovSpin
        }
        if (now - pxAt > 500f) {
            if (stage.width > 0f) {
                pxW = stage.width
                partScale = GrokMath.clamp((340f / stage.width).pow(0.7f), 1f, 2.6f)
            }
            pxAt = now
        }
        particles.update(
            now = now,
            dt = dt,
            sizeScale = partScale,
            spinAngle = spinAngle,
            wideStyle = trick?.kind == GrokTrickKind.SPIN_WILD || wildWide || humming,
            sustainBelts = humming || loading,
        )

        updatePointer()
    }

    /**
     * Radius the spark belt orbits at. Mirrors the `getRadius` probe of the web build: the belt
     * radius of the current silhouette, blended through a running shape morph, plus the lift the
     * `loading` overlay applies.
     */
    private fun particleRadius(): Float {
        val shape = GrokGeo.SHAPES.firstOrNull { it.id == shapeName } ?: return GrokGeo.R
        val k = GrokMath.k2(GrokMath.clamp(shapeSpring.x, 0f, 1f))
        val to = GrokFx.beltRadius(shape.path, GrokGeo.R)
        val prev = prevBelt
        var radius = if (k < 0.999f && prev != null) prev + (to - prev) * k else to
        if (state == GrokMood.LOADING) {
            radius += (52f - radius) * GrokMath.clamp(overlay.x, 0f, 1f)
        }
        return radius
    }

    // ------------------------------------------------------------------ eye morph

    /**
     * Starts a morph towards another eye pair. The polygon snapshot is taken by
     * [currentPolys] and consumed by the frame pass.
     */
    fun morphEyes(index: Int, stiffness: Float = 7f) {
        if (index == eyeTo && eyeMorph.t == 1f) return
        val t = GrokMath.clamp(eyeMorph.x, 0f, 1f)
        eyeFrom = eyeTo
        fromPolys = currentPolys(t)
        eyeTo = index
        eyeMorph.x = 0f
        eyeMorph.v = 0f
        eyeMorph.t = 1f
        eyeStiffness = stiffness
    }

    /** Blends the previous and next eye pair by [t]. Mirrors `_currentPolys`. */
    fun currentPolys(t: Float): Array<FloatArray> {
        val size = GrokGeo.EYES[0][0].size
        return arrayOf(lerpPoly(0, t, FloatArray(size)), lerpPoly(1, t, FloatArray(size)))
    }

    /**
     * [currentPolys] without the allocation: blends the pair into [out], whose arrays must be as
     * long as an eye polygon. [paint] uses this, because it runs on every frame.
     */
    fun currentPolysInto(t: Float, out: Array<FloatArray>) {
        lerpPoly(0, t, out[0])
        lerpPoly(1, t, out[1])
    }

    /** Blends vertex `eye` of the current morph into [out] and returns it. */
    private fun lerpPoly(eye: Int, t: Float, out: FloatArray): FloatArray {
        val eyes = GrokGeo.EYES
        val from = (fromPolys ?: eyes[eyeFrom])[eye]
        val to = eyes[eyeTo][eye]
        val count = minOf(from.size, to.size, out.size)
        for (i in 0 until count) out[i] = from[i] + (to[i] - from[i]) * t
        return out
    }

    // ------------------------------------------------------------------ tricks

    /**
     * Starts a spin towards [turns] full rotations. [dir] lets the pose layer reuse the direction
     * it already picked through `ctx.wantPn`; the default draws a fresh sign, as in the web build.
     */
    private fun pn(turns: Float = 1f, dir: Float = GrokMath.sign(random)) {
        if (reduceMotion || paused || spinTurn != null) return
        spinTurn = GrokTricks.makeSpinTurn(turns, dir)
    }

    private fun hop(now: Float) {
        if (hopAt < 0f) hopAt = now
    }

    /** Advances the shape trick rotation, mirrors `_cycleShapeTrick`. */
    fun cycleShapeTrick(now: Float) {
        if (reduceMotion || paused) return
        trickCycle = (trickCycle + 1) % 5
        wildWide = false
        when (trickCycle) {
            0 -> pn(1f)
            1 -> {
                wildWide = true
                pn(2f)
            }
            2 -> trick = GrokTricks.startTrick(GrokTrickKind.SPIN_BOUNCE, reduceMotion, now, random)
            3 -> trick = GrokTricks.startTrick(GrokTrickKind.SPIN_DIZZY, reduceMotion, now, random)
            else -> {
                pn(1f)
                particles.burst(16, 0.95f, 0.3f)
            }
        }
    }

    private fun hexOf(value: Int): String {
        val rgb = value and 0xFFFFFF
        val digits = rgb.toString(16).uppercase()
        return "#" + "0".repeat(6 - digits.length) + digits
    }

    /**
     * One shot wiring, the counterpart of the constructor tail of `character.js`.
     *
     * It has to stay the last declaration of the class: [build] and [start] read the property
     * initializers above ([frame], the scratch buffers, the cached glyph paths), and Kotlin runs
     * those in textual order.
     */
    init {
        build()
        start(0f)
    }

    private companion object
}

/** Per-frame scratch written by the frame pass, mirrors `this.extras` of the web build. */
internal class GrokCharacterExtras {
    /** Absolute rotation override, `null` when neither a trick nor a spin turn drives it. */
    var turn: Float? = null
    var kr: Float = 0f
    var yi: Float = 0f
    var ki: Float = 0f
    var yr: Float = 0f
    var zr: Float = 0f
    var wi: Float = 0f
    var hop: Float = 0f

    /** Vertical aperture multiplier of the running trick, `null` when it does not touch it. */
    var lidMul: Float? = null
    var eyeBoost: Float? = null

    /** Takes over the wobble channels of one trick evaluation, mirrors `{...tf}`. */
    fun copyFrom(eval: GrokTrickEval) {
        turn = eval.turn
        kr = eval.kr
        yi = eval.yi
        ki = eval.ki
        yr = eval.yr
        zr = eval.zr
        wi = eval.wi
        hop = eval.hop
        lidMul = eval.lidMul
        eyeBoost = eval.eyeBoost
    }
}
