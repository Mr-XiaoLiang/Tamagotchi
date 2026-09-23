package com.lollipop.grokbot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.lollipop.grokbot.internal.GrokEngine
import com.lollipop.grokbot.internal.GrokFrameStyle
import com.lollipop.grokbot.internal.GrokFrameTrace
import com.lollipop.grokbot.internal.GrokPoint
import com.lollipop.grokbot.internal.GrokRenderer

/**
 * Everything a host drives a [GrokBot] with, plus the clock that keeps it running.
 *
 * The state owns the [GrokEngine] and the [GrokRenderer], so it has to outlive the composition
 * that draws it; get one from [rememberGrokBotState] or from the `state` parameter of [GrokBot].
 *
 * [mood] is observable, so a host can highlight the mood it selected in its own UI. Every other
 * member is either immutable or a plain switch onto the engine and does not trigger recomposition.
 * All actions are safe to call at any time, including before the first frame.
 */
@Stable
public class GrokBotState internal constructor(config: GrokBotConfig) {

    internal val engine: GrokEngine = GrokEngine(config.toEngineOptions())

    /**
     * Only the draw pass needs this, so it is built on the first frame rather than with the state.
     *
     * It carries a path cache and scratch `Path`/`Matrix` instances, which are the one part of this
     * class that needs a live graphics stack; keeping it out of the constructor is what lets the
     * engine be driven - and diffed by [apply] - in a plain JVM test.
     */
    internal val renderer: GrokRenderer by lazy { GrokRenderer() }

    internal val style: GrokFrameStyle = GrokFrameStyle()

    /** Reused touch position; the engine only keeps the reference, it never owns the point. */
    private val pointer = GrokPoint()

    /** Configuration the engine currently reflects, the base of the diff in [apply]. */
    private var config: GrokBotConfig = config

    private var started: Boolean = false

    /**
     * Stamp of the last driven frame.
     *
     * The engine works on an absolute millisecond clock, so every call made from outside the frame
     * loop uses this value instead of a platform clock of its own.
     */
    private var clock: Float = 0f

    /** Reused by [frameTrace]; the record is only ever read as the string handed to the sink. */
    private val traceBuffer = StringBuilder(256)

    /** Frames driven so far, the `f=` field of the record. */
    private var frameIndex = 0

    /** Notified by the engine on every mood entry, wired up by [GrokBot]. */
    internal var onMoodChange: ((GrokMood) -> Unit)? = null

    /**
     * Mood on screen.
     *
     * While [GrokBotConfig.mode] is [GrokMode.ONBOARDING] it advances on its own every
     * [GrokMood.onboardingIntervalMillis]; [show] parks it on one mood.
     */
    public var mood: GrokMood by mutableStateOf(config.mood)
        private set

    /** Configuration the running engine was last told about. */
    public val currentConfig: GrokBotConfig get() = config

    /** Freezes the animation on the current frame; the face keeps following the pointer state. */
    public var paused: Boolean
        get() = engine.paused
        set(value) {
            engine.setPaused(value)
        }

    /** Whether the eyes track the touch position of [GrokBot]. */
    public var followPointer: Boolean
        get() = engine.followPointer
        set(value) {
            engine.setFollowPointer(value)
        }

    /**
     * Sink for the per-frame curve record, handed one line after every driven frame; `null`, the
     * default, records nothing.
     *
     * This is the device half of the reference fixture test. It exists so a character that
     * misbehaves on a phone can be read off logcat in the vocabulary of `reference/frames.txt` - the
     * springs, the gaze, the blink, the eye morph, the composed transform - instead of in prints
     * written for the occasion. [GrokFrameTrace] documents the fields, and why the geometry is not
     * among them.
     *
     * Nothing is allocated while it is `null` beyond the counter increment, and the line is built in
     * a buffer that is reused, so a sink that writes to logcat is the only cost of turning it on.
     */
    public var frameTrace: ((String) -> Unit)? = null

    init {
        engine.build()
        engine.options.changeListener = { snapshot ->
            mood = snapshot.state
            onMoodChange?.invoke(snapshot.state)
        }
    }

    /** Enters [mood] and holds it, the `hold` mode of the web build. */
    public fun show(mood: GrokMood, resetEyes: Boolean = true) {
        engine.setMode(GrokMode.HOLD.id, clock)
        engine.setState(mood, clock, resetEyes)
    }

    /** Restarts the onboarding reel, which alternates `idle` with [GrokMood.onboarding]. */
    public fun playOnboarding() {
        engine.setMode(GrokMode.ONBOARDING.id, clock)
    }

    /** Spins the body by [turns] full turns. Ignored while a spin is already running. */
    public fun spin(turns: Float = 1f) {
        engine.spinOnce(turns)
    }

    /** Hops once. */
    public fun bounce() {
        engine.bounceOnce(clock)
    }

    /** Emits one particle burst; ignored when [GrokBotConfig.reduceMotion] is set. */
    public fun burst() {
        engine.burstOnce()
    }

    /**
     * Pushes [config] onto the engine, one changed option at a time.
     *
     * [GrokBotConfig.loginWrap] and [GrokBotConfig.reduceMotion] are read once when the engine is
     * created and cannot be switched afterwards; changing them here has no effect.
     */
    internal fun apply(config: GrokBotConfig) {
        val previous = this.config
        if (previous == config) return
        if (previous.shape != config.shape) engine.setShape(config.shape.id, clock)
        if (previous.flatInk != config.flatInk) engine.setInk(config.flatInk?.toArgb())
        if (previous.color != config.color || previous.scheme != config.scheme) {
            engine.setColor(config.color.id, config.scheme.id)
        }
        if (previous.eyeColor != config.eyeColor) engine.setEyeColor(config.eyeColor?.toArgb())
        if (previous.badgeColor != config.badgeColor) engine.badgeColor = config.badgeColor.toArgb()
        if (previous.followPointer != config.followPointer) {
            engine.setFollowPointer(config.followPointer)
        }
        if (previous.emphasis != config.emphasis) engine.setEmphasis(config.emphasis)
        if (previous.paused != config.paused) engine.setPaused(config.paused)
        if (previous.mode != config.mode) engine.setMode(config.mode.id, clock)
        if (config.mode == GrokMode.HOLD && previous.mood != config.mood) {
            engine.setState(config.mood, clock)
        }
        this.config = config
        style.updateFrom(engine)
    }

    /**
     * Advances the engine to [now] and repaints the frame it owns.
     *
     * The clock starts at the first stamp, so the very first frame both starts the engine and enters
     * [currentConfig]'s mood, the two calls the web build makes from its constructor.
     */
    internal fun step(now: Float) {
        clock = now
        if (!started) {
            started = true
            engine.start(now)
            engine.setState(config.mood, now, resetEyes = true)
            style.updateFrom(engine)
        }
        engine.tick(now)
        engine.paint(now)
        frameIndex++
        val trace = frameTrace
        if (trace != null) {
            traceBuffer.setLength(0)
            GrokFrameTrace.append(traceBuffer, frameIndex, engine)
            trace(traceBuffer.toString())
        }
    }

    /** Publishes the layout of the canvas, in window pixels. */
    internal fun setStage(left: Float, top: Float, width: Float, height: Float) {
        engine.setStage(left, top, width, height, clock)
    }

    /** Records a touch at ([x], [y]) of the canvas, in window pixels. */
    internal fun pointerAt(x: Float, y: Float) {
        pointer.x = engine.stage.left + x
        pointer.y = engine.stage.top + y
        engine.pointerRaw = pointer
    }

    /** Drops the touch position, so the eyes ease back to the centre. */
    internal fun pointerAway() {
        engine.pointerRaw = null
    }
}

/**
 * Remembers a [GrokBotState] seeded with [config].
 *
 * The engine is only created once; later [config] values reach it through [GrokBot].
 */
@Composable
public fun rememberGrokBotState(config: GrokBotConfig = GrokBotConfig()): GrokBotState =
    remember { GrokBotState(config) }

/**
 * Draws the Grok character and keeps it animating.
 *
 * The artwork is a square: it fills the largest centred square the incoming constraints allow, and
 * [GrokBotConfig.sizePx] pins that square to a fixed size. Every frame the engine is ticked with
 * the Compose frame clock and painted with the renderer, which caches the parsed contours, so the
 * composable itself never allocates per frame.
 *
 * [config] is the declarative source of truth: a change to it is pushed onto the running engine
 * instead of rebuilding it, which is why the animation survives e.g. a colour switch.
 *
 * @param config options of the character. When [state] is supplied, it should be the same
 *   configuration the state was remembered with, otherwise the difference is applied on the first
 *   frame.
 * @param state state to drive, from [rememberGrokBotState] by default.
 * @param onMoodChange called on every mood entry, including the ones the onboarding reel picks.
 */
@Composable
public fun GrokBot(
    modifier: Modifier = Modifier,
    config: GrokBotConfig = GrokBotConfig(),
    state: GrokBotState = rememberGrokBotState(config),
    onMoodChange: ((GrokMood) -> Unit)? = null,
) {
    state.onMoodChange = onMoodChange
    SideEffect { state.apply(config) }

    // Read by the draw pass, so a tick invalidates it and the canvas redraws.
    val frame = remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        var origin = -1L
        while (true) {
            withFrameNanos { nanos ->
                if (origin < 0L) origin = nanos
                state.step((nanos - origin) / 1_000_000f)
                // A paused face cannot change, so there is nothing to redraw after the first frame.
                if (!state.paused || frame.intValue == 0) frame.intValue++
            }
        }
    }

    Canvas(
        modifier = modifier
            .then(if (config.sizePx != null) Modifier.size(config.sizePx) else Modifier)
            .onGloballyPositioned { coordinates ->
                val origin = coordinates.positionInWindow()
                val size = coordinates.size
                state.setStage(origin.x, origin.y, size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(state) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Exit) {
                            state.pointerAway()
                            continue
                        }
                        val position = event.changes.lastOrNull()?.position ?: continue
                        state.pointerAt(position.x, position.y)
                    }
                }
            },
    ) {
        frame.intValue
        with(state.renderer) {
            drawGrokFrame(state.engine.frame, state.style, state.engine.poseScale)
        }
    }
}
