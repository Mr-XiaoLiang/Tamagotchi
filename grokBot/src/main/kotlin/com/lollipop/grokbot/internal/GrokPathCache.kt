package com.lollipop.grokbot.internal

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Bounded cache of the parsed form of the SVG `d` strings the engine emits.
 *
 * The frame carries path *strings* (see [GrokFrame.bodyPath] and [GrokOverlayItem.path]), so without
 * this every frame would rebuild the same handful of contours. A lookup is keyed by the `d` string:
 * a hit hands back the cached [Path] untouched, a miss clears the slot that was filled longest ago
 * and parses into it. That keeps the number of live [Path] instances at [capacity] instead of one per
 * frame, and parsing itself allocates nothing beyond the native path storage.
 *
 * [Path] is mutable and the returned instances are shared, so a caller has to finish drawing with a
 * path before asking for the next one.
 */
internal class GrokPathCache(private val capacity: Int = 64) {
    private val keys = arrayOfNulls<String>(capacity)
    private val hashes = IntArray(capacity)
    private val paths = Array(capacity) { Path() }
    private var cursor = 0

    /** Parsed form of [d]; an empty path when [d] is blank or unreadable. */
    fun path(d: String): Path {
        if (d.isEmpty()) return Empty
        val hash = d.hashCode()
        for (slot in 0 until capacity) {
            if (hashes[slot] == hash && keys[slot] == d) return paths[slot]
        }
        val slot = cursor
        cursor++
        if (cursor == capacity) cursor = 0
        hashes[slot] = hash
        keys[slot] = d
        return GrokSvgParser(d).parse(paths[slot])
    }

    private companion object {
        /** Handed out for blank input; never used as a parse target. */
        val Empty = Path()
    }
}

/**
 * Parser for the SVG path subset [GrokSvgPath] emits: `M`, `L`, `H`, `V`, `C`, `S`, `Q`, `T`, `A` and
 * `Z` in both cases, with the usual implicit repetition of the previous command.
 *
 * [GrokMath.flattenPath] is *not* reused here: it only understands `M`/`L`/`C`/`Q`/`Z` and would
 * desynchronise on the `A` commands of [GrokSvgPath.taper], and it produces sample points where the
 * renderer needs curves. Elliptical arcs are converted to cubic segments with the endpoint to centre
 * conversion of the SVG 1.1 specification, appendix F.6.5.
 */
private class GrokSvgParser(private val d: String) {
    private var index = 0
    private var cursorX = 0f
    private var cursorY = 0f
    private var startX = 0f
    private var startY = 0f

    /** Command of the current iteration, promoted from `M`/`m` to `L`/`l` after the first pair. */
    private var command = ' '

    /** Command of the previous iteration, used for the reflection of `S` and `T`. */
    private var previous = ' '

    private var cubicX = 0f
    private var cubicY = 0f
    private var quadX = 0f
    private var quadY = 0f

    fun parse(target: Path): Path {
        target.reset()
        while (true) {
            skipSeparators()
            if (index >= d.length) break
            previous = command
            val start = index
            if (d[index].isLetter()) {
                command = d[index]
                index++
            } else if (command == ' ') {
                break
            }
            when (command) {
                'M' -> {
                    cursorX = number()
                    cursorY = number()
                    target.moveTo(cursorX, cursorY)
                    startX = cursorX
                    startY = cursorY
                    command = 'L'
                }
                'm' -> {
                    cursorX += number()
                    cursorY += number()
                    target.moveTo(cursorX, cursorY)
                    startX = cursorX
                    startY = cursorY
                    command = 'l'
                }
                'L' -> lineTo(target, false)
                'l' -> lineTo(target, true)
                'H' -> horizontalTo(target, false)
                'h' -> horizontalTo(target, true)
                'V' -> verticalTo(target, false)
                'v' -> verticalTo(target, true)
                'C' -> cubicTo(target, false)
                'c' -> cubicTo(target, true)
                'S' -> smoothCubicTo(target, false)
                's' -> smoothCubicTo(target, true)
                'Q' -> quadTo(target, false)
                'q' -> quadTo(target, true)
                'T' -> smoothQuadTo(target, false)
                't' -> smoothQuadTo(target, true)
                'A' -> arcTo(target, false)
                'a' -> arcTo(target, true)
                'Z', 'z' -> {
                    target.close()
                    cursorX = startX
                    cursorY = startY
                }
                else -> return target
            }
            if (index == start) index++ // malformed input, never spin on the same character
        }
        return target
    }

    private fun lineTo(target: Path, relative: Boolean) {
        val baseX = if (relative) cursorX else 0f
        val baseY = if (relative) cursorY else 0f
        cursorX = baseX + number()
        cursorY = baseY + number()
        target.lineTo(cursorX, cursorY)
    }

    private fun horizontalTo(target: Path, relative: Boolean) {
        cursorX = (if (relative) cursorX else 0f) + number()
        target.lineTo(cursorX, cursorY)
    }

    private fun verticalTo(target: Path, relative: Boolean) {
        cursorY = (if (relative) cursorY else 0f) + number()
        target.lineTo(cursorX, cursorY)
    }

    private fun cubicTo(target: Path, relative: Boolean) {
        val baseX = if (relative) cursorX else 0f
        val baseY = if (relative) cursorY else 0f
        val x1 = baseX + number()
        val y1 = baseY + number()
        val x2 = baseX + number()
        val y2 = baseY + number()
        cursorX = baseX + number()
        cursorY = baseY + number()
        target.cubicTo(x1, y1, x2, y2, cursorX, cursorY)
        cubicX = x2
        cubicY = y2
    }

    private fun smoothCubicTo(target: Path, relative: Boolean) {
        val reflect = previous == 'C' || previous == 'c' || previous == 'S' || previous == 's'
        val baseX = if (relative) cursorX else 0f
        val baseY = if (relative) cursorY else 0f
        val x1 = if (reflect) 2f * cursorX - cubicX else cursorX
        val y1 = if (reflect) 2f * cursorY - cubicY else cursorY
        val x2 = baseX + number()
        val y2 = baseY + number()
        cursorX = baseX + number()
        cursorY = baseY + number()
        target.cubicTo(x1, y1, x2, y2, cursorX, cursorY)
        cubicX = x2
        cubicY = y2
    }

    private fun quadTo(target: Path, relative: Boolean) {
        val baseX = if (relative) cursorX else 0f
        val baseY = if (relative) cursorY else 0f
        val x1 = baseX + number()
        val y1 = baseY + number()
        cursorX = baseX + number()
        cursorY = baseY + number()
        target.quadraticTo(x1, y1, cursorX, cursorY)
        quadX = x1
        quadY = y1
        cubicX = cursorX
        cubicY = cursorY
    }

    private fun smoothQuadTo(target: Path, relative: Boolean) {
        val reflect = previous == 'Q' || previous == 'q' || previous == 'T' || previous == 't'
        val baseX = if (relative) cursorX else 0f
        val baseY = if (relative) cursorY else 0f
        val x1 = if (reflect) 2f * cursorX - quadX else cursorX
        val y1 = if (reflect) 2f * cursorY - quadY else cursorY
        cursorX = baseX + number()
        cursorY = baseY + number()
        target.quadraticTo(x1, y1, cursorX, cursorY)
        quadX = x1
        quadY = y1
        cubicX = cursorX
        cubicY = cursorY
    }

    private fun arcTo(target: Path, relative: Boolean) {
        val rx = number()
        val ry = number()
        val rotation = number()
        val largeArc = flag()
        val clockwise = flag()
        val baseX = if (relative) cursorX else 0f
        val baseY = if (relative) cursorY else 0f
        val x = baseX + number()
        val y = baseY + number()
        if (rx == 0f || ry == 0f) {
            target.lineTo(x, y)
        } else {
            ellipseTo(target, rx, ry, rotation, largeArc, clockwise, x, y)
        }
        cursorX = x
        cursorY = y
    }

    /** SVG 1.1 F.6.5: endpoint parameterisation of an arc, emitted as cubic segments. */
    private fun ellipseTo(
        target: Path,
        radiusX: Float,
        radiusY: Float,
        rotation: Float,
        largeArc: Boolean,
        clockwise: Boolean,
        x: Float,
        y: Float,
    ) {
        if (cursorX == x && cursorY == y) return
        var rx = abs(radiusX)
        var ry = abs(radiusY)
        val phi = rotation * PI_OVER_180
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val halfX = (cursorX - x) / 2f
        val halfY = (cursorY - y) / 2f
        val x1 = cosPhi * halfX + sinPhi * halfY
        val y1 = -sinPhi * halfX + cosPhi * halfY
        val lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
        if (lambda > 1f) {
            val grow = sqrt(lambda)
            rx *= grow
            ry *= grow
        }
        val rxSq = rx * rx
        val rySq = ry * ry
        val x1Sq = x1 * x1
        val y1Sq = y1 * y1
        val divisor = rxSq * y1Sq + rySq * x1Sq
        val numerator = rxSq * rySq - rxSq * y1Sq - rySq * x1Sq
        var factor = if (divisor <= 0f) 0f else sqrt((numerator / divisor).coerceAtLeast(0f))
        if (largeArc == clockwise) factor = -factor
        val centreX = factor * rx * y1 / ry
        val centreY = -factor * ry * x1 / rx
        val originX = cosPhi * centreX - sinPhi * centreY + (cursorX + x) / 2f
        val originY = sinPhi * centreX + cosPhi * centreY + (cursorY + y) / 2f
        val startX = angle(1f, 0f, (x1 - centreX) / rx, (y1 - centreY) / ry)
        var delta = angle((x1 - centreX) / rx, (y1 - centreY) / ry, (-x1 - centreX) / rx, (-y1 - centreY) / ry)
        if (!clockwise && delta > 0f) delta -= TWO_PI
        if (clockwise && delta < 0f) delta += TWO_PI
        val segments = ceil(abs(delta) / HALF_PI).toInt().coerceAtLeast(1)
        val step = delta / segments
        val alpha = 4f / 3f * tan(step / 4f)
        var theta = startX
        repeat(segments) {
            val next = theta + step
            val cosFrom = cos(theta)
            val sinFrom = sin(theta)
            val cosTo = cos(next)
            val sinTo = sin(next)
            target.cubicTo(
                originX + rx * cosPhi * (cosFrom - alpha * sinFrom) - ry * sinPhi * (sinFrom + alpha * cosFrom),
                originY + rx * sinPhi * (cosFrom - alpha * sinFrom) + ry * cosPhi * (sinFrom + alpha * cosFrom),
                originX + rx * cosPhi * (cosTo + alpha * sinTo) - ry * sinPhi * (sinTo - alpha * cosTo),
                originY + rx * sinPhi * (cosTo + alpha * sinTo) + ry * cosPhi * (sinTo - alpha * cosTo),
                originX + rx * cosPhi * cosTo - ry * sinPhi * sinTo,
                originY + rx * sinPhi * cosTo + ry * cosPhi * sinTo,
            )
            theta = next
        }
    }

    /** Signed angle between two vectors, `0` when either of them is degenerate. */
    private fun angle(ux: Float, uy: Float, vx: Float, vy: Float): Float {
        val lengths = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
        if (lengths == 0f) return 0f
        val cosine = ((ux * vx + uy * vy) / lengths).coerceIn(-1f, 1f)
        val radians = acos(cosine)
        return if (ux * vy - uy * vx < 0f) -radians else radians
    }

    private fun skipSeparators() {
        while (index < d.length) {
            val c = d[index]
            if (c == ' ' || c == ',' || c == '\n' || c == '\r' || c == '\t') index++ else break
        }
    }

    /** Arc flags may be packed without separators, so they are read as single digits. */
    private fun flag(): Boolean {
        skipSeparators()
        if (index >= d.length) return false
        return when (d[index]) {
            '1' -> {
                index++
                true
            }
            '0' -> {
                index++
                false
            }
            else -> number() != 0f
        }
    }

    /**
     * Reads a number in place. `String.toFloat` would allocate one temporary per coordinate, which a
     * morphing body path hits several hundred times per frame.
     */
    private fun number(): Float {
        skipSeparators()
        var negative = false
        if (index < d.length && (d[index] == '-' || d[index] == '+')) {
            negative = d[index] == '-'
            index++
        }
        var value = 0f
        var digits = 0
        while (index < d.length && d[index].isDigit()) {
            value = value * 10f + (d[index] - '0')
            digits++
            index++
        }
        if (index < d.length && d[index] == '.') {
            index++
            var fraction = 0f
            var scale = 1f
            while (index < d.length && d[index].isDigit()) {
                fraction = fraction * 10f + (d[index] - '0')
                scale *= 10f
                digits++
                index++
            }
            value += fraction / scale
        }
        if (digits == 0) return 0f
        if (index < d.length && (d[index] == 'e' || d[index] == 'E')) {
            val mark = index
            index++
            var exponentNegative = false
            if (index < d.length && (d[index] == '-' || d[index] == '+')) {
                exponentNegative = d[index] == '-'
                index++
            }
            var exponent = 0
            var exponentDigits = 0
            while (index < d.length && d[index].isDigit()) {
                exponent = exponent * 10 + (d[index] - '0')
                exponentDigits++
                index++
            }
            if (exponentDigits == 0) index = mark else value *= 10f.pow(if (exponentNegative) -exponent else exponent)
        }
        return if (negative) -value else value
    }

    private companion object {
        const val PI_OVER_180 = 0.017453292f
        const val HALF_PI = (PI / 2.0).toFloat()
        const val TWO_PI = (PI * 2.0).toFloat()
    }
}
