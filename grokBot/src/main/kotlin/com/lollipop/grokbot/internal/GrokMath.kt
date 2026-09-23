package com.lollipop.grokbot.internal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Springs, easing, path flattening and the small 3D helpers the character needs.
 *
 * The formulas are a direct port of the original web renderer so that the motion
 * timing and the silhouette maths stay pixel compatible.
 */
internal object GrokMath {

    /** Fixed integration step used by every spring in the engine. */
    const val DT = 1f / 120f

    /** Half-width of the band around a whole slice count that [springSteps] snaps to it. */
    private const val SLICE_SNAP = 1e-3f

    /**
     * How many slices `dt` is integrated in, so that no slice exceeds [DT].
     *
     * The web build reads this as `max(1, ceil(dt / DT))`, which at 60 fps lands *exactly* on the
     * discontinuity of `ceil`: `dt / DT` is an exact multiple of `DT` by construction, because a
     * 1/60 s frame is two 1/120 s steps. The last bit of `dt` then decides between 2 slices and 3 -
     * between a 1/120 s step and a 1/180 s one - so the integration step changes by 50% on the
     * strength of a rounding error, and two implementations fed the same frame times can disagree
     * on most of them.
     *
     * Snapping a hair either side of a whole count to that count removes the flip. The bound `ceil`
     * exists for still holds: a slice is at most `DT` plus the snap band (a thousandth of a step,
     * 8 µs), which cannot reach a spring's stability.
     */
    fun springSteps(dt: Float): Int {
        val slices = dt / DT
        val whole = round(slices)
        return if (abs(slices - whole) < SLICE_SNAP) {
            max(1, whole.toInt())
        } else {
            max(1, ceil(slices).toInt())
        }
    }

    fun clamp(n: Float, a: Float, b: Float): Float = min(b, max(a, n))

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun rand(a: Float, b: Float, random: Random): Float = a + random.nextFloat() * (b - a)

    fun sign(random: Random): Float = if (random.nextFloat() < 0.5f) -1f else 1f

    /** [sign] as a direction multiplier, for the state fields the web build keeps integral. */
    fun signI(random: Random): Int = if (random.nextFloat() < 0.5f) -1 else 1

    /** easeInOutCubic */
    fun k2(n: Float): Float =
        if (n < 0.5f) 4f * n * n * n else 1f - (-2f * n + 2f).pow(3) / 2f

    /** easeOutCubic */
    fun rc(n: Float): Float = 1f - (1f - n).pow(3)

    /** easeOutBack */
    fun y1e(n: Float): Float =
        1f + 2.70158f * (n - 1f).pow(3) + 1.70158f * (n - 1f).pow(2)

    /** smoothstep */
    fun dke(n: Float): Float = n * n * (3f - 2f * n)

    fun xT(n: Float, bs: Float): Float = 1f - exp(ln(1f - n) * 60f * bs)

    fun rn(n: Float, bs: Float = 1f / 60f): Float = xT(n, bs)

    // ------------------------------------------------------------------ polygons

    fun polyPath(pts: FloatArray): String {
        val sb = StringBuilder("M")
        var i = 0
        while (i + 1 < pts.size) {
            if (i > 0) sb.append('L')
            sb.append(fmt(pts[i])).append(' ').append(fmt(pts[i + 1]))
            i += 2
        }
        return sb.append('Z').toString()
    }

    private fun fmt(v: Float): String {
        val scaled = kotlin.math.round(v * 100f) / 100f
        return if (scaled == floor(scaled) && abs(scaled) < 1e7f) {
            scaled.toInt().toString()
        } else {
            scaled.toString()
        }
    }

    fun pointCount(pts: FloatArray): Int = pts.size / 2

    /** Mean of the polygon's vertices, written into [out]. */
    fun centroid(pts: FloatArray, out: FloatArray) {
        var x = 0f
        var y = 0f
        var i = 0
        while (i + 1 < pts.size) {
            x += pts[i]
            y += pts[i + 1]
            i += 2
        }
        val n = pointCount(pts).toFloat()
        out[0] = x / n
        out[1] = y / n
    }

    /** Writes `a + (b - a) * t` into [out]. Both inputs must share the same size. */
    fun lerpPoly(a: FloatArray, b: FloatArray, t: Float, out: FloatArray) {
        for (i in out.indices) out[i] = a[i] + (b[i] - a[i]) * t
    }

    fun lerpFace(a: GrokBot, b: GrokBot, t: Float, out: GrokBot) {
        out.x = a.x + (b.x - a.x) * t
        out.y = a.y + (b.y - a.y) * t
        out.sx = a.sx + (b.sx - a.sx) * t
        out.sy = a.sy + (b.sy - a.sy) * t
        out.eye = a.eye + (b.eye - a.eye) * t
        out.leftDx = a.leftDx + (b.leftDx - a.leftDx) * t
    }

    // --------------------------------------------------------------- path maths

    private val pathTokens = Regex("[MLCQZmlcqz]|-?\\d*\\.?\\d+(?:e[-+]?\\d+)?")

    /**
     * Samples a path into a flat `[x0, y0, x1, y1, ...]` polyline.
     *
     * Straight segments are split roughly every [step] units, curves use a
     * chord-length estimate so the sampling density stays even.
     */
    fun flattenPath(d: String, step: Float = 4f): FloatArray {
        val tokens = pathTokens.findAll(d).map { it.value }.toList()
        val out = ArrayList<Float>(tokens.size * 2)
        var r = 0
        var cmd = ""
        var ox = 0f
        var oy = 0f
        var cx = 0f
        var cy = 0f

        fun rd(): Float = tokens[r++].toFloat()
        fun emit(f: (Float) -> FloatArray, len: Float) {
            val n = max(2, ceil(len / step).toInt())
            for (k in 1..n) {
                val p = f(k / n.toFloat())
                out.add(p[0])
                out.add(p[1])
            }
        }

        while (r < tokens.size) {
            val token = tokens[r]
            if (token[0].isLetter()) {
                cmd = token.uppercase()
                r++
            }
            if (cmd == "Z") {
                val dx = cx - ox
                val dy = cy - oy
                val len = sqrt(dx * dx + dy * dy)
                if (len > 0.01f) emit({ f -> floatArrayOf(ox + dx * f, oy + dy * f) }, len)
                ox = cx
                oy = cy
                // Guard against malformed paths that would otherwise spin forever.
                if (r >= tokens.size || !tokens[r][0].isLetter()) break
                continue
            }
            if (r >= tokens.size) break
            when (cmd) {
                "M" -> {
                    ox = rd()
                    oy = rd()
                    cx = ox
                    cy = oy
                    out.add(ox)
                    out.add(oy)
                    cmd = "L"
                }

                "L" -> {
                    val x = rd()
                    val y = rd()
                    val dx = x - ox
                    val dy = y - oy
                    emit({ f -> floatArrayOf(ox + dx * f, oy + dy * f) }, sqrt(dx * dx + dy * dy))
                    ox = x
                    oy = y
                }

                "Q" -> {
                    val qx = rd()
                    val qy = rd()
                    val ex = rd()
                    val ey = rd()
                    val sx = ox
                    val sy = oy
                    emit({ f ->
                        val n = 1f - f
                        floatArrayOf(
                            n * n * sx + 2f * n * f * qx + f * f * ex,
                            n * n * sy + 2f * n * f * qy + f * f * ey,
                        )
                    }, sqrt((qx - ox) * (qx - ox) + (qy - oy) * (qy - oy)) + sqrt((ex - qx) * (ex - qx) + (ey - qy) * (ey - qy)))
                    ox = ex
                    oy = ey
                }

                "C" -> {
                    val x1 = rd()
                    val y1 = rd()
                    val x2 = rd()
                    val y2 = rd()
                    val x3 = rd()
                    val y3 = rd()
                    val sx = ox
                    val sy = oy
                    emit({ f ->
                        val a = 1f - f
                        floatArrayOf(
                            a * a * a * sx + 3f * a * a * f * x1 + 3f * a * f * f * x2 + f * f * f * x3,
                            a * a * a * sy + 3f * a * a * f * y1 + 3f * a * f * f * y2 + f * f * f * y3,
                        )
                    }, sqrt((x1 - ox) * (x1 - ox) + (y1 - oy) * (y1 - oy)) +
                        sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)) +
                        sqrt((x3 - x2) * (x3 - x2) + (y3 - y2) * (y3 - y2)))
                    ox = x3
                    oy = y3
                }

                else -> r++
            }
        }
        return out.toFloatArray()
    }

    /**
     * Vertical sampling table of a closed path: for any `y` it answers the
     * left/right silhouette edges, which drives the "stroke/span" rendering.
     */
    class Span internal constructor(
        private val top: Float,
        private val height: Float,
        private val samples: Int,
        private val left: FloatArray,
        private val right: FloatArray,
    ) {
        /** Samples the span at [y], written into [out] as `[left, right]`. */
        fun at(y: Float, out: FloatArray) {
            val v = clamp((y - top) / height * samples - 0.5f, 0f, (samples - 1).toFloat())
            val k = floor(v).toInt()
            val f = v - k
            val b = min(k + 1, samples - 1)
            out[0] = left[k] + (left[b] - left[k]) * f
            out[1] = right[k] + (right[b] - right[k]) * f
        }
    }

    fun buildSpan(pts: FloatArray, re: Float, samples: Int = 160): Span {
        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        var i = 0
        while (i + 1 < pts.size) {
            val y = pts[i + 1]
            if (y < top) top = y
            if (y > bottom) bottom = y
            i += 2
        }
        val height = bottom - top
        val leftArr = FloatArray(samples)
        val rightArr = FloatArray(samples)
        val count = pointCount(pts)
        for (m in 0 until samples) {
            val y = top + height * (m + 0.5f) / samples
            // The web build seeds these with `-Infinity`/`Infinity` so that the `isFinite` fallback
            // below can restore `re` on rows where the silhouette lies entirely on one side. Kotlin's
            // `Float.MAX_VALUE` *is* finite, so using it as the sentinel silently kept ~3.4e38 as a
            // real edge.
            var hi = Float.NEGATIVE_INFINITY
            var lo = Float.POSITIVE_INFINITY
            for (b in 0 until count) {
                val x0 = pts[b * 2]
                val y0 = pts[b * 2 + 1]
                val idx = (b + 1) % count
                val x1 = pts[idx * 2]
                val y1 = pts[idx * 2 + 1]
                if ((y0 <= y) == (y1 <= y)) continue
                val ex = x0 + (x1 - x0) * (y - y0) / (y1 - y0)
                if (ex <= re) {
                    if (ex > hi) hi = ex
                } else if (ex < lo) {
                    lo = ex
                }
            }
            leftArr[m] = if (hi.isFinite()) hi else re
            rightArr[m] = if (lo.isFinite()) lo else re
        }
        return Span(top, height, samples, leftArr, rightArr)
    }

    private val spanCache = HashMap<String, Span>()

    fun spanAt(path: String, re: Float): Span =
        spanCache.getOrPut(path) { buildSpan(flattenPath(path), re) }

    /** Live silhouette of an arbitrary polygon (used while shape morphing), written into [out]. */
    fun spanPoly(poly: FloatArray, y: Float, re: Float, out: FloatArray) {
        var hi = Float.NEGATIVE_INFINITY
        var lo = Float.POSITIVE_INFINITY
        val count = pointCount(poly)
        for (r in 0 until count) {
            val x0 = poly[r * 2]
            val y0 = poly[r * 2 + 1]
            val idx = (r + 1) % count
            val x1 = poly[idx * 2]
            val y1 = poly[idx * 2 + 1]
            if ((y0 <= y) == (y1 <= y)) continue
            val l = x0 + (x1 - x0) * (y - y0) / (y1 - y0)
            if (l <= re) {
                if (l > hi) hi = l
            } else if (l < lo) {
                lo = l
            }
        }
        out[0] = if (hi.isFinite()) hi else re
        out[1] = if (lo.isFinite()) lo else re
    }

    // -------------------------------------------------------------------- 3D

    fun rot3(turn: Float, tilt: Float, roll: Float): FloatArray {
        val d = PI.toFloat() / 180f
        val ui = cos(turn * d)
        val si = sin(turn * d)
        val ea = cos(tilt * d)
        val ca = sin(tilt * d)
        val wo = cos(roll * d)
        val wc = sin(roll * d)
        return floatArrayOf(
            wo * ui - wc * ca * si, -wc * ea, wo * si + wc * ca * ui,
            wc * ui + wo * ca * si, wo * ea, wc * si - wo * ca * ui,
            -ea * si, ca, ea * ui,
        )
    }

    fun relRot(a: GrokRot, b: GrokRot): FloatArray {
        val gn = rot3(a.turn, a.tilt, a.roll)
        val g = rot3(b.turn, b.tilt, b.roll)
        return floatArrayOf(
            gn[0] * g[0] + gn[1] * g[1] + gn[2] * g[2],
            gn[0] * g[3] + gn[1] * g[4] + gn[2] * g[5],
            gn[0] * g[6] + gn[1] * g[7] + gn[2] * g[8],
            gn[3] * g[0] + gn[4] * g[1] + gn[5] * g[2],
            gn[3] * g[3] + gn[4] * g[4] + gn[5] * g[5],
            gn[3] * g[6] + gn[4] * g[7] + gn[5] * g[8],
            gn[6] * g[0] + gn[7] * g[1] + gn[8] * g[2],
            gn[6] * g[3] + gn[7] * g[4] + gn[8] * g[5],
            gn[6] * g[6] + gn[7] * g[7] + gn[8] * g[8],
        )
    }

    /** Silhouette radii of a solid of revolution at yaw [angle]. */
    fun solidRadii(solid: Array<FloatArray>, angle: Float, n: Int = 96): FloatArray {
        val c = cos(angle)
        val s = sin(angle)
        val rx = FloatArray(solid.size)
        val ry = FloatArray(solid.size)
        val rr = FloatArray(solid.size)
        for ((i, row) in solid.withIndex()) {
            rx[i] = row[0] * c + row[2] * s
            ry[i] = row[1]
            rr[i] = row[3]
        }
        val raw = FloatArray(n)
        for (idx in 0 until n) {
            val u = (idx / n.toFloat()) * PI.toFloat() * 2f
            val d = cos(u)
            val m = sin(u)
            var f = 0f
            for (i in rx.indices) {
                val h = rx[i]
                val y = ry[i]
                val k = rr[i]
                val v = d * h + m * y
                val b = v * v - (h * h + y * y) + k * k
                if (b <= 0f) continue
                val x = v + sqrt(b)
                if (x > f) f = x
            }
            raw[idx] = f
        }
        val o = raw.size
        return FloatArray(o) { i ->
            (raw[(i - 2 + o) % o] + 4f * raw[(i - 1 + o) % o] + 6f * raw[i] +
                4f * raw[(i + 1) % o] + raw[(i + 2) % o]) / 16f
        }
    }

    /** Scale the polar ring by `solidRadii(yaw) / solidRadii(0)`. */
    fun makeTurnAt(solid: Array<FloatArray>, ring: FloatArray, re: Float): (Float) -> FloatArray {
        val rest = solidRadii(solid, 0f)
        return { yaw ->
            val yawRadii = solidRadii(solid, yaw)
            var v = FloatArray(rest.size) { i ->
                clamp((yawRadii[i] + 12f) / (rest[i] + 12f), 0.32f, 1.5f)
            }
            val n = v.size
            repeat(3) {
                val prev = v
                v = FloatArray(n) { i ->
                    (prev[(i - 2 + n) % n] + 4f * prev[(i - 1 + n) % n] + 6f * prev[i] +
                        4f * prev[(i + 1) % n] + prev[(i + 2) % n]) / 16f
                }
            }
            FloatArray(ring.size) { i ->
                re + (ring[i] - re) * v[i / 2]
            }
        }
    }

    /**
     * Maps a pointer position onto an ellipse around the mark so the face can
     * look at it without the eyes leaving the body.
     */
    fun mapPointer(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        px: Float,
        py: Float,
        sensitivity: Float = 0.6f,
        innerRadius: Float = 22f,
        outerRadius: Float = 14f,
        spread: Float = 2f,
    ): FloatArray {
        val cx = left + width / 2f
        val cy = top + height / 2f
        val dx = px - cx
        val dy = py - cy
        val len = sqrt(dx * dx + dy * dy)
        val o = min(1f, sqrt(len / (width * spread)))
        val angle = atan2(dy, dx)
        return floatArrayOf(
            cx + sensitivity * (outerRadius / innerRadius) * o * cos(angle) * width,
            cy + sensitivity * o * sin(angle) * height,
        )
    }
}

/** A damped spring, mirrors the JS `{x, v, t}` triple. */
internal class GrokSpring(x: Float) {
    var x: Float = x
    var v: Float = 0f
    var t: Float = x

    fun step(freq: Float, damp: Float, dt: Float) {
        v += (-2f * damp * freq * v - freq * freq * (x - t)) * dt
        x += v * dt
        if (!x.isFinite() || !v.isFinite()) {
            x = t
            v = 0f
        }
    }

    fun set(value: Float) {
        x = value
        t = value
        v = 0f
    }

    fun snapTo(value: Float) {
        t = value
    }
}

/** Euler angles of a pose. */
internal class GrokRot(turn: Float = 0f, tilt: Float = 0f, roll: Float = 0f) {
    var turn: Float = turn
    var tilt: Float = tilt
    var roll: Float = roll

    fun set(turn: Float, tilt: Float, roll: Float) {
        this.turn = turn
        this.tilt = tilt
        this.roll = roll
    }

    fun set(other: GrokRot) = set(other.turn, other.tilt, other.roll)
}

/** Face placement on the body plus the eye aperture scale. */
internal class GrokBot(
    var x: Float = 0f,
    var y: Float = 0f,
    var sx: Float = 1f,
    var sy: Float = 1f,
    var eye: Float = 1f,
    var leftDx: Float = 0f,
) {
    fun set(
        x: Float,
        y: Float,
        sx: Float,
        sy: Float,
        eye: Float,
        leftDx: Float = 0f,
    ) {
        this.x = x
        this.y = y
        this.sx = sx
        this.sy = sy
        this.eye = eye
        this.leftDx = leftDx
    }
}
