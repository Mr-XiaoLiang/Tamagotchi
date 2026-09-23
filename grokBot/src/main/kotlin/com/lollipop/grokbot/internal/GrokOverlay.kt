package com.lollipop.grokbot.internal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * One of the 19 fixed overlay elements.
 *
 * The web build allocates `dots[2]`, `rings[7]`, `parts[7]` and `glyphs[3]` once and rewrites them
 * every frame, so a painter writing to `parts[5]` overwrites whatever another painter put there.
 * Keeping the same fixed slots reproduces that behaviour exactly.
 */
internal class GrokOverlayItem {
    /** Draw this element. Hidden elements are skipped, like `display:none`. */
    var visible = false

    /** SVG path of a glyph, `null` when the element is a circle. */
    var path: String? = null

    var circleX = 0f
    var circleY = 0f
    var radius = 0f

    var stroke = false
    var strokeWidth = 0f

    /** Dash length of a stroked circle, `0` for a solid stroke. */
    var dash = 0f
    var dashOffset = 0f

    /** `2x3` affine transform applied to [path]: `x' = m0*x + m2*y + m4`. */
    val matrix = FloatArray(6)

    var opacity = 0f

    fun hide() {
        visible = false
    }

    fun show() {
        visible = true
    }

    fun setCircle(x: Float, y: Float, r: Float) {
        path = null
        circleX = x
        circleY = y
        radius = r
        identity()
    }

    /** Identity transform, the state of an element whose path needs no matrix. */
    fun identity() {
        matrix[0] = 1f
        matrix[1] = 0f
        matrix[2] = 0f
        matrix[3] = 1f
        matrix[4] = 0f
        matrix[5] = 0f
    }

    /** `translate(tx, ty) scale(s) translate(-pivot, -pivot)`, the circle scaling trick. */
    fun setTranslateScale(tx: Float, ty: Float, scale: Float, pivot: Float) {
        identity()
        matrix[0] = scale
        matrix[3] = scale
        matrix[4] = tx - pivot * scale
        matrix[5] = ty - pivot * scale
    }

    /** `translate(tx, ty) rotate(deg) scale(s) translate(-pivot, -pivot)`. */
    fun setRotateScale(
        tx: Float,
        ty: Float,
        degrees: Float,
        scale: Float,
        pivot: Float,
    ) {
        val rad = degrees * PI_OVER_180
        val c = kotlin.math.cos(rad) * scale
        val s = kotlin.math.sin(rad) * scale
        matrix[0] = c
        matrix[1] = s
        matrix[2] = -s
        matrix[3] = c
        matrix[4] = tx - pivot * (c - s)
        matrix[5] = ty - pivot * (s + c)
    }

    private companion object {
        const val PI_OVER_180 = 0.017453292f
    }
}

/** Body offsets the current overlay asks for, mirrors `extras()` of the web build. */
internal class GrokOverlayExtras {
    /** Blend weight of the `dots` overlay. */
    var dotsAmount = 0f

    /** `pop` of the second dot pulse, already weighted by the transition. */
    var dotsPop = 1f

    /** `lift` of the dot pulse, in design units; it drags the body up while the dots run. */
    var dotsLift = 0f

    /** `tone` of the dot pulse, `1` at the peak of a pulse and `0.5` between two of them. */
    var dotsTone = 1f

    /** Horizontal body offset in design units. */
    var offsetX = 0f

    /** Vertical body offset in design units. */
    var offsetY = 0f

    /** Body rotation in degrees. */
    var rotation = 0f

    /** Body radius multiplier. */
    var radiusScale = 1f

    /** Extra fade applied while `standby` is on. */
    var fade = 0f

    /** Body zoom of the current overlay kind. */
    var zoom = 1f

    /** Blended overlay radius, used by `radar`. */
    var bodyRadius = 19f
}

/**
 * The overlay effect layer: 14 painters driven by the current mood.
 *
 * Port of `class OverlayLayer` in `temp/replica/src/fx.js` (lines 463-841). The SVG containers and
 * the `setAttribute` calls become [GrokOverlayItem] slots; maths, timings and painter order are
 * unchanged. `circlePath`, `pencilPath` and `bangPath` are prepared by the engine, like
 * `character.js` lines 367-369 do.
 */
internal class GrokOverlay(
    private val random: Random = Random.Default,
) {

    /** Mood id to overlay kind, the `MAP` table. */
    val map: Map<String, String> = mapOf(
        "thinking" to "dots",
        "orbit" to "orbit",
        "radar" to "radar",
        "progress" to "progress",
        "spawning" to "gather",
        "dictating" to "wave",
        "sending" to "send",
        "receiving" to "receive",
        "uploading" to "dock",
        "bouncing" to "ball",
        "loading" to "whirl",
        "powering-down" to "standby",
        "writing" to "pencil",
        "alerting" to "bang",
    )

    /** Moods whose overlay loops on its own. */
    val cycle: Set<String> = setOf("progress", "spawning")

    /** Loop length in milliseconds, keyed by mood id. */
    val cycleOn: Map<String, Float> = mapOf("progress" to 2500f, "spawning" to 2000f)

    /** Idle time before a looping overlay restarts, in milliseconds. */
    val cycleOff = 1500f

    /** Body zoom per overlay kind. */
    val scale: Map<String, Float> = mapOf(
        "dots" to 1.5f, "orbit" to 1.14f, "radar" to 1.14f, "progress" to 1.32f, "gather" to 1.15f,
        "wave" to 1.42f, "send" to 1.12f, "receive" to 1.12f, "dock" to 1.3f, "ball" to 1.22f,
        "whirl" to 1.45f, "pencil" to 1.18f, "bang" to 1.28f, "standby" to 1.75f,
    )

    /** Overlay radius per kind, in design units. */
    val radius: Map<String, Float> = mapOf(
        "dots" to 22f, "orbit" to 19f, "radar" to 19f, "progress" to 19f, "gather" to 19f, "wave" to 16f,
        "send" to 20f, "receive" to 20f, "dock" to 20f, "ball" to 18f, "whirl" to 15f, "pencil" to 17f,
        "bang" to 13f, "standby" to 13f,
    )

    /** Fallback overlay radius, `|| 19` in the web build. */
    val defaultRadius = 19f

    /** Radius and spacing of the two `dots` markers. */
    val dotRadius = 22f
    val dotGap = 62f

    /** Dot pop strength. */
    val popLow = 0.84f
    val popHigh = 0.22f

    /** Cycle lengths of `send` and `receive`, in milliseconds. */
    val sendMs = 1500f
    val recvMs = 1700f

    /** Timestamp the current overlay started at, set by the engine. */
    var overlayAt = 0f

    private val dots = Array(2) { GrokOverlayItem() }
    private val rings = Array(7) { GrokOverlayItem() }
    private val parts = Array(7) { GrokOverlayItem() }
    private val glyphs = Array(3) { GrokOverlayItem() }

    /** All 19 elements in draw order: dots, rings, parts, glyphs. */
    val elements: List<GrokOverlayItem> = dots.toList() + rings.toList() + parts.toList() + glyphs.toList()

    /**
     * One of the seven particle slots, `parts[i]` of the web build.
     *
     * Slot `3 + i` is where the character paints its humming dots, overwriting whatever a painter
     * happened to leave there for the frame.
     */
    fun part(index: Int): GrokOverlayItem = parts[index]

    /** Extras of the last [paint] call. */
    val extras = GrokOverlayExtras()

    private val pen = GrokPencilPose()
    private val ink = ArrayList<Float>(128)

    /**
     * `prefers-reduced-motion` of the current frame.
     *
     * [extras] reads it, and the web build writes `_reduce` *before* calling `extras`, so the owner
     * has to refresh this before the first of the two calls, not inside [paint].
     */
    var reduce = false
    private var recvDir = -0.7f
    private var recvTick = -1f
    private var circlePath = ""
    private var pencilPath = ""
    private var bangPath = ""
    private var pulseLift = 0f
    private var pulsePop = 1f
    private var pulseTone = 1f

    /** Installs the body paths the glyph painters draw. */
    fun setPaths(circle: String, pencil: String, bang: String) {
        circlePath = circle
        pencilPath = pencil
        bangPath = bang
    }

    /** Drops the pencil trail, e.g. when the mood changes. */
    fun resetInk() {
        ink.clear()
    }

    /** Presence of [name] during a cross fade, the `amount()` helper. */
    fun amount(name: String, cur: String?, prev: String?, presence: Float, mix: Float): Float = when (name) {
        cur -> presence * mix
        prev -> presence * (1f - mix)
        else -> 0f
    }

    private fun hideAll() {
        for (element in elements) element.hide()
    }

    /** Writes the dot pop of [slot] into the `pulse*` fields. */
    private fun dotsPulse(now: Float, slot: Int, presence: Float) {
        var phase = ((((now - overlayAt) / 1400f + 0.119f) % 1f) + 1f) % 1f
        var distance = abs(phase - slot / 3f)
        distance = min(distance, 1f - distance)
        val tone = if (reduce) 1f else exp(-(distance * distance) / (2f * 0.15f * 0.15f))
        val on = if (reduce) 0f else 1f
        pulseLift = tone * 9f * presence * on
        pulsePop = 1f + on * (popLow + popHigh * tone - 1f)
        pulseTone = 1f - on * 0.5f * (1f - tone)
    }

    /**
     * Body offsets, zoom and overlay radius of the current frame, written into [extras].
     * Port of `extras()` in `fx.js` lines 781-838.
     */
    fun extras(
        now: Float,
        stateAt: Float,
        cur: String?,
        prev: String?,
        presence: Float,
        mix: Float,
    ) {
        val dotsAmount = amount("dots", cur, prev, presence, mix)
        dotsPulse(now, 1, presence)
        var localScale = 1f
        if (cur == "dots" || prev == "dots") {
            val floor = if (presence > 0.001f) presence else 0.001f
            localScale = 1f + (pulsePop - 1f) * (dotsAmount / floor)
        }

        val receiveAmount = amount("receive", cur, prev, presence, mix)
        if (receiveAmount > THRESHOLD) {
            val t = ((((now - stateAt) / recvMs) % 1f) + 1f) % 1f
            val swell = GrokMath.clamp((t - 0.58f) / 0.34f, 0f, 1f)
            localScale *= 1f + 0.11f * sin(swell * PI.toFloat()) * receiveAmount
        }

        val sendAmount = amount("send", cur, prev, presence, mix)
        if (sendAmount > THRESHOLD) {
            val t = ((((now - stateAt) / sendMs) % 1f) + 1f) % 1f
            val shrink = if (t < 0.18f) -0.06f * sin((t / 0.18f) * PI.toFloat()) else 0f
            val grow = if (t >= 0.18f && t < 0.42f) 0.05f * sin(((t - 0.18f) / 0.24f) * PI.toFloat()) else 0f
            localScale *= 1f + (shrink + grow) * sendAmount
        }

        val bangAmount = amount("bang", cur, prev, presence, mix)
        if (bangAmount > THRESHOLD) {
            localScale *= 1f + 0.04f * exp(-(((now - stateAt) / 1000f) % 2.2f) * 5.5f) * bangAmount
        }

        var offsetX = 0f
        var offsetY = 0f
        var rotation = 0f

        val pencilAmount = amount("pencil", cur, prev, presence, mix)
        if (pencilAmount > THRESHOLD) {
            GrokSvgPath.pencilPose(now, stateAt, pen)
            offsetX += pen.x * pencilAmount
            offsetY += (pen.y + pen.wig * 0.5f) * pencilAmount
            rotation += pen.rot * pencilAmount
        }
        if (bangAmount > THRESHOLD) offsetY += 58f * bangAmount

        val whirlAmount = amount("whirl", cur, prev, presence, mix)
        if (whirlAmount > THRESHOLD) {
            val t = now / 1000f
            offsetX += (sin(t * 0.9f) * 2f + sin(t * 1.7f) * 0.8f) * whirlAmount
            offsetY += (sin(t * 1.3f) * 2.4f + sin(t * 0.6f) * 1.2f) * whirlAmount
        }

        val ballAmount = amount("ball", cur, prev, presence, mix)
        if (ballAmount > THRESHOLD) {
            val t = (now - stateAt) / 1000f
            val bounce = 0.62f
            val peak = 52f
            val period = (8f * peak) / (bounce * bounce)
            val fallTime = kotlin.math.sqrt((2f * 40f) / period)
            val height = if (t < fallTime) {
                40f - 0.5f * period * t * t
            } else {
                val phase = ((((t - fallTime) / bounce) % 1f) + 1f) % 1f
                4f * peak * phase * (1f - phase)
            }
            offsetY += (40f - height) * ballAmount
        }

        val blendedRadius = blendRadius(cur, prev, mix)
        val standbyAmount = amount("standby", cur, prev, presence, mix)
        val zoomCur = if (cur != null) maxOf(scale[cur] ?: 1f, 1f) else 1f
        val zoomPrev = if (prev != null) maxOf(scale[prev] ?: 1f, 1f) else zoomCur

        extras.dotsAmount = dotsAmount
        extras.dotsPop = pulsePop
        extras.dotsLift = pulseLift
        extras.dotsTone = pulseTone
        extras.offsetX = offsetX
        extras.offsetY = offsetY
        extras.rotation = rotation
        extras.radiusScale = blendedRadius / GrokGeo.R * localScale
        extras.fade = if (standbyAmount > 0f) (0.28f + 0.2f * sin(now * 0.0016f)) * standbyAmount else 0f
        extras.zoom = 1f + (zoomCur * mix + zoomPrev * (1f - mix) - 1f) * presence
        extras.bodyRadius = blendedRadius
    }

    /** Blended overlay radius of `cur` and `prev`, `A2` of the web build. */
    private fun blendRadius(cur: String?, prev: String?, mix: Float): Float {
        if (cur == null) return defaultRadius
        val current = radius[cur] ?: defaultRadius
        val previous = if (prev != null) radius[prev] ?: current else current
        return current * mix + previous * (1f - mix)
    }

    /**
     * Paints one frame into the fixed slots.
     *
     * @param now frame time in milliseconds.
     * @param stateAt timestamp the current mood started at.
     * @param cur overlay kind currently shown, or `null`.
     * @param prev overlay kind fading out, or `null`.
     * @param presence overall presence of the overlay, `0`..`1`.
     * @param mix cross fade position, `0` previous, `1` current.
     * @param r design radius of the body.
     * @param reduceMotion reduced motion requested.
     */
    fun paint(
        now: Float,
        stateAt: Float,
        cur: String?,
        prev: String?,
        presence: Float,
        mix: Float,
        r: Float,
        reduceMotion: Boolean,
    ) {
        hideAll()
        reduce = reduceMotion
        extras(now, stateAt, cur, prev, presence, mix)
        val dotsAmount = amount("dots", cur, prev, presence, mix)
        if (dotsAmount > THRESHOLD) paintDots(dotsAmount, now, r)
        val orbitAmount = amount("orbit", cur, prev, presence, mix)
        if (orbitAmount > THRESHOLD) paintOrbit(orbitAmount, now, r)
        val radarAmount = amount("radar", cur, prev, presence, mix)
        if (radarAmount > THRESHOLD) paintRadar(radarAmount, now, r, extras.bodyRadius)
        val progressAmount = amount("progress", cur, prev, presence, mix)
        if (progressAmount > THRESHOLD) paintProgress(progressAmount, now, r)
        val gatherAmount = amount("gather", cur, prev, presence, mix)
        if (gatherAmount > THRESHOLD) paintGather(gatherAmount, now, r)
        val waveAmount = amount("wave", cur, prev, presence, mix)
        if (waveAmount > THRESHOLD) paintWave(waveAmount, now, r)
        val sendAmount = amount("send", cur, prev, presence, mix)
        if (sendAmount > THRESHOLD) paintSend(sendAmount, now, stateAt, r)
        val receiveAmount = amount("receive", cur, prev, presence, mix)
        if (receiveAmount > THRESHOLD) paintReceive(receiveAmount, now, stateAt, r)
        val dockAmount = amount("dock", cur, prev, presence, mix)
        if (dockAmount > THRESHOLD) paintDock(dockAmount, now, stateAt, r)
        val pencilAmount = amount("pencil", cur, prev, presence, mix)
        if (pencilAmount > THRESHOLD) paintPencil(pencilAmount, now, stateAt, r)
        val bangAmount = amount("bang", cur, prev, presence, mix)
        if (bangAmount > THRESHOLD) paintBang(bangAmount, now, stateAt, r)
        val standbyAmount = amount("standby", cur, prev, presence, mix)
        if (standbyAmount > THRESHOLD) paintStandby(standbyAmount, now, r)
    }

    /** Two dot markers either side of the body. `fx.js` 537-550. */
    private fun paintDots(ze: Float, now: Float, r: Float) {
        val gaps = floatArrayOf(r - dotGap, r + dotGap)
        for (i in 0 until 2) {
            val element = dots[i]
            val lt = GrokMath.clamp((ze - i * 0.12f) / (1f - i * 0.12f), 0f, 1f)
            if (lt <= THRESHOLD) {
                element.hide()
                continue
            }
            val fade = GrokMath.rc(lt)
            val travel = GrokMath.y1e(lt)
            dotsPulse(now, if (i == 0) 0 else 2, ze)
            element.show()
            element.path = circlePath
            element.stroke = false
            element.setTranslateScale(
                r + (gaps[i] - r) * travel,
                r - pulseLift,
                dotRadius * fade * pulsePop / r * 1.02f,
                r,
            )
            element.opacity = fade * pulseTone
        }
    }

    /** Five beads orbiting the body. `fx.js` 552-563. */
    private fun paintOrbit(ze: Float, now: Float, r: Float) {
        val fade = GrokMath.rc(ze)
        val reach = 52f * GrokMath.y1e(ze)
        val baseRadius = 12f
        val spin = now * 0.0017f
        for (i in 0 until 5) {
            val element = parts[i]
            val angle = spin + i * TWO_PI / 5f
            val front = cos(angle)
            val depth = 0.5f + 0.5f * GrokMath.clamp(front, 0f, 1f)
            element.show()
            element.setCircle(
                r + reach * sin(angle),
                r - reach * 0.42f * cos(angle),
                maxOf(baseRadius * depth * fade, 0.3f),
            )
            element.opacity = GrokMath.clamp((front + 0.4f) / 0.6f, 0.18f, 1f) * fade
        }
    }

    /** Three rings expanding to the body edge. `fx.js` 565-578. */
    private fun paintRadar(ze: Float, now: Float, r: Float, bodyRadius: Float) {
        val fade = GrokMath.rc(ze)
        for (i in 0 until 3) {
            val element = rings[i]
            val t = (((now / 1300f + i / 3f) % 1f) + 1f) % 1f
            element.show()
            element.stroke = true
            element.dash = 0f
            element.setCircle(r, r, bodyRadius + (104f - bodyRadius) * t)
            element.strokeWidth = 3.4f * (1f - t * 0.55f)
            element.opacity = fade * (1f - t) * 0.9f
        }
    }

    /** Track plus a growing arc, looping every 2.5s. `fx.js` 580-602. */
    private fun paintProgress(ze: Float, now: Float, r: Float) {
        val fade = GrokMath.rc(ze)
        val grow = GrokMath.y1e(ze)
        val trackRadius = 62f
        val cycle = cycleOn["progress"] ?: 2500f
        val lt = GrokMath.clamp((now - overlayAt) / cycle, 0f, 1f)
        val sweep = GrokMath.clamp(lt / 0.85f, 0f, 1f)

        val track = rings[3]
        track.show()
        track.stroke = true
        track.strokeWidth = 5f
        track.dash = 0f
        track.setCircle(r, r, trackRadius * grow)
        track.opacity = fade * 0.16f

        val arc = rings[4]
        val arcRadius = trackRadius * grow
        val circumference = TWO_PI * arcRadius
        arc.show()
        arc.stroke = true
        arc.strokeWidth = 5f
        arc.setCircle(r, r, arcRadius)
        arc.dash = circumference
        arc.dashOffset = circumference * (1f - sweep)
        arc.setRotateScale(r, r, -90f, 1f, r)
        arc.opacity = fade
    }

    /** Five beads spiralling in from outside. `fx.js` 604-617. */
    private fun paintGather(ze: Float, now: Float, r: Float) {
        val fade = GrokMath.rc(ze)
        val cycle = cycleOn["spawning"] ?: 2000f
        for (i in 0 until 5) {
            val element = parts[i]
            val yn = GrokMath.clamp(((now - overlayAt) / cycle - i * 0.09f) / 0.62f, 0f, 1f)
            if (yn >= 1f) {
                element.hide()
                continue
            }
            val one = 1f - yn
            val ease = 1f - one * one * one
            val angle = i * 2.4f + yn * 2.2f
            val distance = 96f * (1f - ease)
            element.show()
            element.setCircle(
                r + distance * cos(angle),
                r + distance * sin(angle) * 0.8f,
                9f * (0.5f + 0.5f * ease) * fade,
            )
            element.opacity = fade * GrokMath.clamp(yn * 5f, 0f, 1f) * (1f - ease * 0.25f)
        }
    }

    /** Four dots riding the wave line. `fx.js` 619-641. */
    private fun paintWave(ze: Float, now: Float, r: Float) {
        val lanes = floatArrayOf(-2f, -1f, 1f, 2f)
        val gap = 44f
        for (i in 0 until 4) {
            val element = if (i < 2) dots[i] else parts[3 + i]
            val lane = lanes[i]
            val weight = abs(lane) * 0.1f
            val an = GrokMath.clamp((ze - weight) / (1f - weight), 0f, 1f)
            if (an <= THRESHOLD) {
                element.hide()
                continue
            }
            val travel = GrokMath.y1e(an)
            val height = GrokSvgPath.wave(now) * (0.55f + 0.45f * sin(now * 0.012f - abs(lane) * 1.05f))
            val radius = (7f + 9f * GrokMath.clamp(height, 0.08f, 1f)) * GrokMath.rc(an)
            val lift = 6f * GrokMath.clamp(height, 0f, 1f) * an
            element.show()
            if (i < 2) {
                element.path = circlePath
                element.stroke = false
                element.setTranslateScale(
                    r + lane * gap * travel,
                    r - lift,
                    radius / r * 1.02f,
                    r,
                )
            } else {
                element.setCircle(r + lane * gap * travel, r - lift, radius)
            }
            element.opacity = an
        }
    }

    /** Two beads flying up right, plus a shock ring. `fx.js` 643-676. */
    private fun paintSend(ze: Float, now: Float, stateAt: Float, r: Float) {
        val fade = GrokMath.rc(ze)
        val t = ((((now - stateAt) / sendMs) % 1f) + 1f) % 1f
        val lead = GrokMath.clamp((t - 0.18f) / 0.55f, 0f, 1f)
        val leadEase = lead * lead * (0.4f + 0.6f * lead)
        val dirX = 0.74f
        val dirY = -0.62f

        val head = parts[5]
        if (lead > 0f && lead < 1f) {
            val distance = 108f * leadEase
            head.show()
            head.setCircle(
                r + dirX * distance,
                r + dirY * distance,
                10f * (1f - leadEase * 0.55f) * fade,
            )
            head.opacity = fade * (1f - leadEase * leadEase)
        } else {
            head.hide()
        }

        val tail = parts[6]
        val trail = GrokMath.clamp((t - 0.26f) / 0.55f, 0f, 1f)
        val trailEase = trail * trail * (0.4f + 0.6f * trail)
        if (lead > 0f && trail > 0f && trail < 1f) {
            val distance = 108f * trailEase
            tail.show()
            tail.setCircle(
                r + dirX * distance,
                r + dirY * distance,
                5f * (1f - trailEase * 0.6f) * fade,
            )
            tail.opacity = fade * 0.3f * (1f - trailEase)
        } else {
            tail.hide()
        }

        val ring = rings[5]
        val pop = GrokMath.clamp((t - 0.18f) / 0.3f, 0f, 1f)
        if (pop > 0f && pop < 1f) {
            ring.show()
            ring.stroke = true
            ring.dash = 0f
            ring.setCircle(r, r, 20f + 34f * GrokMath.rc(pop))
            ring.strokeWidth = 2.8f * (1f - pop)
            ring.opacity = fade * (1f - pop) * 0.8f
        } else {
            ring.hide()
        }
    }

    /** Incoming bead on a fresh random bearing every cycle. `fx.js` 678-702. */
    private fun paintReceive(ze: Float, now: Float, stateAt: Float, r: Float) {
        val fade = GrokMath.rc(ze)
        val elapsed = now - stateAt
        val tick = kotlin.math.floor(elapsed / recvMs)
        if (tick != recvTick) {
            recvTick = tick
            recvDir = GrokMath.rand(-PI_F * 1.25f, PI_F * 0.25f, random)
        }
        val lt = (((elapsed / recvMs) % 1f) + 1f) % 1f
        val yn = GrokMath.clamp(lt / 0.6f, 0f, 1f)
        val one = 1f - yn
        val an = 1f - one * one * one
        val dirX = cos(recvDir)
        val dirY = sin(recvDir)
        val distance = 108f * (1f - an)

        val head = parts[5]
        if (yn < 1f) {
            val bend = 18f * sin(yn * PI_F) * (1f - an * 0.7f)
            head.show()
            head.setCircle(
                r + dirX * distance + -dirY * bend,
                r + dirY * distance + dirX * bend,
                3.5f + 6.5f * an,
            )
            head.opacity = fade * GrokMath.clamp(yn * 3.5f, 0f, 1f) * (0.3f + 0.7f * an)
        } else {
            head.hide()
        }

        val ring = rings[6]
        val pop = GrokMath.clamp((lt - 0.58f) / 0.32f, 0f, 1f)
        if (pop > 0f && pop < 1f) {
            ring.show()
            ring.stroke = true
            ring.dash = 0f
            ring.setCircle(r, r, 20f + 26f * GrokMath.rc(pop))
            ring.strokeWidth = 2.8f * (1f - pop)
            ring.opacity = fade * (1f - pop) * 0.8f
        } else {
            ring.hide()
        }
    }

    /** Two beads swinging up into the body. `fx.js` 704-720. */
    private fun paintDock(ze: Float, now: Float, stateAt: Float, r: Float) {
        val fade = GrokMath.rc(ze)
        val t = (now - stateAt) / 1000f
        val orbitRadius = 42f
        val spin = 1.1f
        for (i in 0 until 2) {
            val element = parts[5 + i]
            val p = GrokMath.clamp((t - (0.2f + i * 1.3f)) / 0.9f, 0f, 1f)
            if (p <= 0f) {
                element.hide()
                continue
            }
            val one = 1f - p
            val ease = 1f - one * one * one
            val angle = now * 0.001f * spin + i * PI_F
            val targetX = r + orbitRadius * sin(angle)
            val targetY = r + orbitRadius * 0.5f * cos(angle) + sin(now * 0.003f + i) * 2f
            val fromX = r - 120f + i * 30f
            val fromY = r + 95f
            element.show()
            element.setCircle(
                fromX + (targetX - fromX) * ease,
                fromY + (targetY - fromY) * ease,
                (7f + 3f * ease) * fade,
            )
            element.opacity = fade * GrokMath.clamp(p * 4f, 0f, 1f)
        }
    }

    /** The pencil glyph and the ink trail it leaves. `fx.js` 722-749. */
    private fun paintPencil(ze: Float, now: Float, stateAt: Float, r: Float) {
        GrokSvgPath.pencilPose(now, stateAt, pen)
        val glyph = glyphs[0]
        val reach = 68f
        val angle = (pen.rot - 90f) * PI_F / 180f
        glyph.show()
        glyph.path = pencilPath
        glyph.stroke = false
        glyph.setRotateScale(
            r + (pen.x + cos(angle) * reach) * ze,
            r + (pen.y + pen.wig * 0.15f + sin(angle) * reach) * ze,
            pen.rot * ze,
            GrokMath.rc(ze),
            r,
        )
        glyph.opacity = GrokMath.clamp(ze * 1.6f - 0.3f, 0f, 1f)

        if (ze > 0.6f && !pen.lift) {
            val x = r + pen.x
            val y = r + pen.y + pen.wig + 19f
            val count = ink.size
            if (count < 2 || hypot(x - ink[count - 2], y - ink[count - 1]) > 2.4f) {
                ink.add(x)
                ink.add(y)
                if (ink.size > INK_POINTS * 2) {
                    ink.removeAt(0)
                    ink.removeAt(0)
                }
            } else {
                ink[count - 2] = x
                ink[count - 1] = y
            }
        } else if (ink.isNotEmpty()) {
            ink.removeAt(0)
            ink.removeAt(0)
        }

        val line = glyphs[1]
        if (ink.size < 4) {
            line.hide()
        } else {
            line.show()
            line.identity()
            line.path = GrokSvgPath.smoothLine(ink.toFloatArray())
            line.stroke = true
            line.strokeWidth = 6f
            line.opacity = GrokMath.clamp(ze * 1.2f, 0f, 1f)
        }
    }

    /** The alert bang glyph. `fx.js` 751-760. */
    private fun paintBang(ze: Float, now: Float, stateAt: Float, r: Float) {
        val glyph = glyphs[2]
        val t = (now - stateAt) / 1000f
        val amt = GrokMath.rc(GrokMath.clamp(ze * 1.1f, 0f, 1f))
        val decay = exp(-(((t % 2.2f) + 2.2f) % 2.2f) * 5.5f)
        val wobble = sin(t * 42f) * 2.2f * decay

        val scale = GrokMath.clamp(ze * 1.2f, 0f, 1f)
        val dropY = -26f - (1f - amt) * 70f
        val pivotY = r - 74f
        val rad = wobble * PI_F / 180f
        val c = cos(rad)
        val s = sin(rad)
        val wx = (1f - scale) * r - r
        val wy = (1f - scale) * r - pivotY
        glyph.show()
        glyph.path = bangPath
        glyph.stroke = false
        glyph.matrix[0] = scale * c
        glyph.matrix[1] = scale * s
        glyph.matrix[2] = -scale * s
        glyph.matrix[3] = scale * c
        glyph.matrix[4] = c * wx - s * wy + r
        glyph.matrix[5] = s * wx + c * wy + pivotY + dropY
        glyph.opacity = GrokMath.clamp(ze * 1.5f - 0.2f, 0f, 1f)
    }

    /** Breathing core plus a contracting halo. `fx.js` 762-779. */
    private fun paintStandby(ze: Float, now: Float, r: Float) {
        val fade = GrokMath.rc(ze)
        val core = parts[4]
        val breath = 0.5f + 0.5f * sin(now * 0.0016f)
        core.show()
        core.setCircle(r, r, 26f + 7f * breath)
        core.opacity = fade * (0.06f + 0.1f * breath)

        val halo = rings[2]
        if (ze < 0.995f) {
            halo.show()
            halo.stroke = true
            halo.dash = 0f
            halo.strokeWidth = 2.4f
            halo.setCircle(r, r, 104f - 88f * fade)
            halo.opacity = (1f - fade) * 0.5f
        } else {
            halo.hide()
        }
    }

    private companion object {
        /** Painters below this presence are skipped, like the web build. */
        const val THRESHOLD = 0.004f

        const val PI_F = 3.1415927f
        const val TWO_PI = 6.2831855f

        /** Points kept in the pencil ink trail, `64` in the web build. */
        const val INK_POINTS = 64
    }
}
