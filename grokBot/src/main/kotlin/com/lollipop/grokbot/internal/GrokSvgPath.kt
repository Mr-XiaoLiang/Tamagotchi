package com.lollipop.grokbot.internal

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pen pose of the `pencil` overlay, reuse one instance across frames. */
internal class GrokPencilPose {
    var x = 0f
    var y = 0f
    var wig = 0f
    var rot = 0f
    var lift = false
}

/**
 * SVG `d` builders of the effect layer, plus the two small poses it needs.
 *
 * Port of `fx.js` lines 67-205. The overlay hands the renderer the very same path strings the web
 * build writes into `d` attributes, and the renderer parses them with `PathParser`. Polygons stay
 * flat `FloatArray`s, matching [GrokMath].
 */
internal object GrokSvgPath {

    /** Duration of one pencil cycle in milliseconds. */
    const val PENCIL_MS = 2500f

    /** `circlePathOf(R)`: the body circle as a closed spline. */
    fun circlePathOf(r: Float): String = closedSpline(GrokFx.circleRing(r))

    /** Rounded capsule [w] wide and [h] tall, centred on `(r, r)`. */
    fun capsule(w: Float, h: Float, r: Float): String {
        val half = w / 2f
        val top = r - h / 2f + half
        val bottom = r + h / 2f - half
        return buildString(96) {
            append("M").append(r - half).append(' ').append(top)
            append("A").append(half).append(' ').append(half).append(" 0 0 1 ").append(r + half).append(' ').append(top)
            append("L").append(r + half).append(' ').append(bottom)
            append("A").append(half).append(' ').append(half).append(" 0 0 1 ").append(r - half).append(' ').append(bottom)
            append('Z')
        }
    }

    /** Teardrop [w] wide tapering to [tip], [h] tall, centred on `(r, r)`. */
    fun taper(w: Float, tip: Float, h: Float, r: Float): String {
        val top = w / 2f
        val bottom = tip / 2f
        val topY = r - h / 2f
        val bottomY = r + h / 2f
        return buildString(96) {
            append("M").append(r - top).append(' ').append(topY + top)
            append("A").append(top).append(' ').append(top).append(" 0 0 1 ").append(r + top).append(' ').append(topY + top)
            append("L").append(r + bottom).append(' ').append(bottomY - bottom)
            append("A").append(bottom).append(' ').append(bottom).append(" 0 0 1 ").append(r - bottom).append(' ').append(bottomY - bottom)
            append('Z')
        }
    }

    /** Closed Catmull-Rom spline through [pts]. */
    fun closedSpline(pts: FloatArray): String {
        val count = GrokMath.pointCount(pts)
        val out = StringBuilder(count * 48)
        out.append('M').append(fix(pts[0], 2)).append(' ').append(fix(pts[1], 2))
        for (s in 0 until count) {
            val before = ((s - 1 + count) % count) * 2
            val here = s * 2
            val next = ((s + 1) % count) * 2
            val after = ((s + 2) % count) * 2
            out.append('C')
                .append(fix(pts[here] + (pts[next] - pts[before]) / 6f, 2)).append(' ')
                .append(fix(pts[here + 1] + (pts[next + 1] - pts[before + 1]) / 6f, 2)).append(' ')
                .append(fix(pts[next] - (pts[after] - pts[here]) / 6f, 2)).append(' ')
                .append(fix(pts[next + 1] - (pts[after + 1] - pts[here + 1]) / 6f, 2)).append(' ')
                .append(fix(pts[next], 2)).append(' ').append(fix(pts[next + 1], 2))
        }
        return out.append('Z').toString()
    }

    /** Open Catmull-Rom spline through [pts]. */
    fun smoothLine(pts: FloatArray): String {
        val count = GrokMath.pointCount(pts)
        val out = StringBuilder(count * 48)
        out.append('M').append(fix(pts[0], 1)).append(' ').append(fix(pts[1], 1))
        if (count == 2) {
            return out.append('L').append(fix(pts[2], 1)).append(' ').append(fix(pts[3], 1)).toString()
        }
        for (i in 0 until count - 1) {
            val before = max(i - 1, 0) * 2
            val here = i * 2
            val next = (i + 1) * 2
            val after = min(i + 2, count - 1) * 2
            out.append('C')
                .append(fix(pts[here] + (pts[next] - pts[before]) / 6f, 1)).append(' ')
                .append(fix(pts[here + 1] + (pts[next + 1] - pts[before + 1]) / 6f, 1)).append(' ')
                .append(fix(pts[next] - (pts[after] - pts[here]) / 6f, 1)).append(' ')
                .append(fix(pts[next + 1] - (pts[after + 1] - pts[here + 1]) / 6f, 1)).append(' ')
                .append(fix(pts[next], 1)).append(' ').append(fix(pts[next + 1], 1))
        }
        return out.toString()
    }

    /** Speech wave amplitude, `0.42`..`1.0`. */
    fun wave(now: Float): Float =
        0.42f + 0.29f * sin(now * 0.0021f) * sin(now * 0.0034f) + 0.29f * sin(now * 0.0013f + 1.7f)

    /** Pen pose of the `pencil` overlay, written into [out]. */
    fun pencilPose(now: Float, stateAt: Float, out: GrokPencilPose): GrokPencilPose {
        val elapsed = now - stateAt
        val phase = (((elapsed / PENCIL_MS) % 1f) + 1f) % 1f
        if (phase < 0.68f) {
            val t = phase / 0.68f
            val ease = t * t * (3f - 2f * t)
            val fade = GrokMath.clamp(t / 0.08f, 0f, 1f) * GrokMath.clamp((1f - t) / 0.08f, 0f, 1f)
            out.x = -54f + 118f * ease
            out.y = 26f
            out.wig = sin(t * 24f) * 3.2f * fade
            out.rot = 17f + sin(elapsed * 6e-4f)
            out.lift = false
            return out
        }
        val back = GrokMath.k2((phase - 0.68f) / 0.32f)
        out.x = 64f - 118f * back
        out.y = 26f - 20f * sin(back * PI.toFloat())
        out.wig = 0f
        out.rot = 17f - 2f * sin(back * PI.toFloat()) + sin(elapsed * 6e-4f)
        out.lift = true
        return out
    }

    /** Rounds to [decimals] and drops the trailing `.0`, keeping path strings short. */
    private fun fix(value: Float, decimals: Int): String {
        var factor = 1f
        repeat(decimals) { factor *= 10f }
        return (value * factor).roundToInt().let { if (it % factor.toInt() == 0) (it / factor.toInt()).toString() else (it / factor).toString() }
    }
}
