package com.lollipop.grokbot.internal

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import com.lollipop.grokbot.parseHexColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** Colour stops of a belt gradient, the five `stop-color` entries of the web build. */
private const val RIBBON_STOPS = 5

/**
 * Colours the renderer paints with.
 *
 * They follow the configuration rather than the frame, so the host refreshes this once per
 * configuration change with [updateFrom] instead of once per frame.
 */
internal class GrokFrameStyle {
    /** `--fg` of the web build: body fill and the tint of every overlay element. */
    var bodyColor: Color = Color.Black

    /** `--bg` of the web build: the fill of the eye cut outs. */
    var eyeFill: Color = Color(GrokTabs.EYE_BG)

    /** Fill of the badge blob, the `circle` inside the body group. */
    var badgeColor: Color = Color(GrokEyeOptions.DEFAULT_BADGE)

    /** Reads the palette [engine] currently resolves to. */
    fun updateFrom(engine: GrokEngine) {
        bodyColor = parseHexColor(engine.fgHex)
        eyeFill = Color(engine.eyeBg)
        badgeColor = Color(engine.badgeColor)
    }
}

/**
 * Draws one [GrokFrame] onto a canvas.
 *
 * This is the Kotlin counterpart of the SVG tree the web build keeps around: the engine produced the
 * frame as data, and everything the DOM used to do implicitly is spelled out here.
 *
 * The draw order follows `FX.attach` (`temp/replica/src/fx.js`), which builds the tree as the
 * particle `back` container, then the overlay elements, then the body group, then the particle
 * `front` container: the `z < 0` halves of a belt stay behind the character while the `z >= 0`
 * halves wrap around it, and everything else happens behind the body outline, the eyes clipped to
 * it and the badge. Only the body group carries the frame transform and opacity.
 *
 * Instances are stateful (path cache, scratch objects) and are meant to be created once per
 * composable with `remember`.
 */
internal class GrokRenderer(capacity: Int = 64) {
    private val paths = GrokPathCache(capacity)
    private val matrix = Matrix()
    private val scratch = Path()

    /** Parsed particle colours, cached: the spark palette is a handful of repeated hex strings. */
    private val colors = HashMap<String, Color>(8)

    /**
     * Paints [frame] with [style], mapping the frame view box onto the largest centred square of the
     * canvas, the `preserveAspectRatio` default of the web build.
     *
     * [poseScale] is the per silhouette zoom of `_applyPoseScale` (`character.js` lines 299-313),
     * which the web build applied as a CSS `transform: scale()` around the centre of the element.
     * A canvas is uniformly scaled instead, which is the same thing once the fit above has centred
     * the artwork.
     */
    fun DrawScope.drawGrokFrame(frame: GrokFrame, style: GrokFrameStyle, poseScale: Float = 1f) {
        val box = frame.viewBox
        if (box.size <= 0f) return
        val scale = minOf(size.width, size.height) / box.size
        if (scale <= 0f) return
        val left = (size.width - box.size * scale) / 2f - box.x * scale
        val top = (size.height - box.size * scale) / 2f - box.y * scale
        withTransform({
            if (poseScale != 1f) {
                scale(poseScale, poseScale, Offset(size.width / 2f, size.height / 2f))
            }
            translate(left, top)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawParticles(frame, back = true)
            drawOverlay(frame, style)
            drawBody(frame, style)
            drawParticles(frame, back = false)
        }
    }

    /**
     * The particle layer of [frame]: the sparks and belt ribbons that sit [back] of the body, or the
     * part of a ribbon that has swung in front of it.
     *
     * The web build keeps the two apart (`FX.attach`): sparks and the `z < 0` halves of a ribbon are
     * appended to `back`, which is attached before the overlay elements, the `z >= 0` halves to
     * `front`, attached after the body group. Sparks never reach `front`, so the second pass only
     * has ribbons to do.
     */
    private fun DrawScope.drawParticles(frame: GrokFrame, back: Boolean) {
        val particles = frame.particles
        for (slot in particles.indices) {
            val particle = particles[slot]
            val alpha = particle.alpha
            if (alpha <= 0f) continue
            if (particle.orbit == null) {
                if (back) drawSpark(particle, alpha)
            } else {
                drawRibbon(particle, alpha, front = !back)
            }
        }
    }

    /**
     * One burst spark, the `star`, `round` and streak branches of the web build's `step`.
     *
     * A star is the unit [GrokGeo.STAR_PATH] placed by `translate(x y) rotate(rot) scale(size)`, a
     * round spark is the plain `circle` the web build writes for it, and the rest are capsules of
     * `width` x `height` rotated onto the velocity, drawn from the top left corner the `rect` element
     * uses.
     */
    private fun DrawScope.drawSpark(particle: GrokParticle, alpha: Float) {
        val color = colorOf(particle.color)
        when {
            particle.star -> withTransform({
                translate(particle.x, particle.y)
                rotate(particle.rot, Offset.Zero)
                scale(particle.size, particle.size, Offset.Zero)
            }) {
                drawPath(paths.path(GrokGeo.STAR_PATH), color, alpha)
            }

            particle.round -> drawCircle(color, particle.size, Offset(particle.x, particle.y), alpha)

            else -> withTransform({
                rotate(particle.angle, Offset(particle.x, particle.y))
            }) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(
                        particle.x - particle.width / 2f,
                        particle.y - particle.height / 2f,
                    ),
                    size = Size(particle.width, particle.height),
                    cornerRadius = CornerRadius(particle.rx),
                    alpha = alpha,
                )
            }
        }
    }

    /**
     * One belt ribbon: the half of it that sits [front] of the body, or behind it.
     *
     * Every band of a side becomes a sub-path of one outline, so that the bands of a trail that
     * overlap after a depth change are filled as a single shape and share one alpha instead of
     * stacking up where they cross. The outline is then filled with the gradient the web build
     * builds out of five `hsl` stops running from the oldest trail sample to the newest, which is
     * the "colourful" half of the effect: both sides share it, so the front half of a ribbon keeps
     * the colour of the part that went behind the character.
     */
    private fun DrawScope.drawRibbon(particle: GrokParticle, alpha: Float, front: Boolean) {
        val count = if (front) particle.frontBandCount else particle.backBandCount
        if (count <= 0) return
        val hist = particle.hist
        if (hist.size < 2) return
        val bands = if (front) particle.frontBands else particle.backBands
        scratch.reset()
        for (slot in 0 until count) band(scratch, bands[slot])
        val from = hist[0]
        val to = hist[hist.size - 1]
        // The platform shader rejects a gradient with a non finite axis, where the browser silently
        // paints nothing, so a trail that collapsed into NaN is left out.
        if (!from.x.isFinite() || !from.y.isFinite() || !to.x.isFinite() || !to.y.isFinite()) return
        drawPath(
            path = scratch,
            brush = Brush.linearGradient(
                colors = ribbonColors(particle),
                start = Offset(from.x, from.y),
                end = Offset(to.x, to.y),
            ),
            alpha = alpha,
        )
    }

    /**
     * Appends one band to [target], the `band()` builder of the web build's `ribbon()`.
     *
     * The outline runs down the outer side to the head, turns the corner there when the run reaches
     * the head of the trail, comes back along the inner side and turns again when the run starts at
     * the tail. [GrokRibbonBand.headCap] / [GrokRibbonBand.tailCap] carry the radius of those turns,
     * or `0` when the run was cut by a depth change and the edge closes as a straight line instead.
     */
    private fun band(target: Path, band: GrokRibbonBand) {
        val points = band.pointCount
        if (points < 2) return
        val outer = band.plus
        val inner = band.minus
        target.moveTo(outer[0], outer[1])
        for (point in 1 until points) target.lineTo(outer[point * 2], outer[point * 2 + 1])
        cap(target, outer, inner, points - 1, band.headCap)
        for (point in points - 1 downTo 0) target.lineTo(inner[point * 2], inner[point * 2 + 1])
        cap(target, inner, outer, 0, band.tailCap)
        target.close()
    }

    /**
     * Closes one end of a band with the half turn the web build draws as `A <r> <r> 0 0 0`.
     *
     * That sweep flag is always `0`, a counter clockwise half turn, and the direction it bulges in
     * follows from the offset side of a band being its tangent turned by 90 degrees: from the outer
     * point of the head it leaves along the trail, from the inner point of the tail it leaves back
     * along it. The chord of the turn is the diameter of the circle, so the arc lands exactly on the
     * point the outline continues from.
     */
    private fun cap(target: Path, from: FloatArray, to: FloatArray, point: Int, radius: Float) {
        if (radius <= 0f) return
        val startX = from[point * 2]
        val startY = from[point * 2 + 1]
        val centerX = (startX + to[point * 2]) / 2f
        val centerY = (startY + to[point * 2 + 1]) / 2f
        target.arcTo(
            rect = Rect(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
            startAngleDegrees = atan2(startY - centerY, startX - centerX) * 180f / PI.toFloat(),
            sweepAngleDegrees = -180f,
            forceMoveTo = false,
        )
    }

    /**
     * Stops of a belt gradient, the five `stop-color` entries the web build writes onto it.
     *
     * The hues start at the particle hue, drift by `hueVel * life` and are spread by `hueSpan`, the
     * lightness runs from 56% to 100% over the five stops and the hue is rounded to whole degrees,
     * which is the value the browser is handed after the web build formats the attribute.
     */
    private fun ribbonColors(particle: GrokParticle): List<Color> {
        val hue = particle.hue + particle.hueVel * particle.life
        return List(RIBBON_STOPS) { stop ->
            val t = stop / (RIBBON_STOPS - 1f)
            hsl(
                hueDegrees = hue + t * particle.hueSpan,
                saturation = 0.56f,
                lightness = (56f + 11f * t).roundToInt() / 100f,
            )
        }
    }

    /** `hsl(<hue> <saturation> <lightness>)` of CSS, the colour space the belt stops are written in. */
    private fun hsl(hueDegrees: Float, saturation: Float, lightness: Float): Color {
        // The browser writes the hue as `hsl(<number> …)` and a NaN would become `hsl(NaN …)`, which
        // it drops; `roundToInt` throws on NaN instead, so the wrap of a non finite hue is pinned.
        val hue = if (hueDegrees.isFinite()) {
            (((hueDegrees % 360f) + 360f) % 360f).roundToInt()
        } else {
            0
        }
        val sector = hue / 60f
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val second = chroma * (1f - abs(sector % 2f - 1f))
        val red: Float
        val green: Float
        val blue: Float
        when (sector.toInt()) {
            0 -> {
                red = chroma
                green = second
                blue = 0f
            }

            1 -> {
                red = second
                green = chroma
                blue = 0f
            }

            2 -> {
                red = 0f
                green = chroma
                blue = second
            }

            3 -> {
                red = 0f
                green = second
                blue = chroma
            }

            4 -> {
                red = second
                green = 0f
                blue = chroma
            }

            else -> {
                red = chroma
                green = 0f
                blue = second
            }
        }
        val match = lightness - chroma / 2f
        return Color(red + match, green + match, blue + match)
    }

    /** [parseHexColor] memoised per string, so a spark does not parse its colour on every frame. */
    private fun colorOf(hex: String): Color = colors.getOrPut(hex) { parseHexColor(hex) }

    /** Everything the effect layer shows this frame, all of it behind the body. */
    private fun DrawScope.drawOverlay(frame: GrokFrame, style: GrokFrameStyle) {
        val elements = frame.overlay.elements
        for (slot in elements.indices) {
            val element = elements[slot]
            if (!element.visible) continue
            val alpha = element.opacity
            if (alpha <= 0f) continue
            val color = style.bodyColor
            // SVG default when a painter leaves the attribute alone.
            val width = if (element.strokeWidth > 0f) element.strokeWidth else 1f
            val d = element.path
            withTransform({ applyTo(element) }) {
                when {
                    d != null && element.stroke -> drawPath(paths.path(d), color, alpha, Stroke(width))
                    d != null -> drawPath(paths.path(d), color, alpha)
                    !element.stroke ->
                        drawCircle(color, element.radius, Offset(element.circleX, element.circleY), alpha)
                    element.dash > 0f -> drawDashProgress(element, color, alpha, width)
                    else -> drawCircle(
                        color,
                        element.radius,
                        Offset(element.circleX, element.circleY),
                        alpha,
                        Stroke(width),
                    )
                }
            }
        }
    }

    /**
     * The `radar` progress ring: a circle stroked with `stroke-dasharray: <circumference>` and a
     * `stroke-dashoffset` that leaves `1 - offset / dash` of the outline visible.
     *
     * A dash pattern of `dash, dash` starting at `offset` shows exactly the last
     * `1 - offset / dash` of the circle, which is an arc from that angle back to the start, so the
     * arc is drawn directly instead of relying on the dash phase of the platform.
     */
    private fun DrawScope.drawDashProgress(
        element: GrokOverlayItem,
        color: Color,
        alpha: Float,
        width: Float,
    ) {
        val visible = (1f - element.dashOffset / element.dash).coerceIn(0f, 1f)
        if (visible <= 0f) return
        val start = 360f * (1f - visible)
        val radius = element.radius
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = 360f - start,
            useCenter = false,
            topLeft = Offset(element.circleX - radius, element.circleY - radius),
            size = Size(radius * 2f, radius * 2f),
            alpha = alpha,
            style = Stroke(width),
        )
    }

    /**
     * Body outline, eyes clipped to it, and the badge blob, all inside the frame transform.
     *
     * The four calls transcribe the web build's transform attribute
     * (`character.js` 723-733) one for one, and that list is
     * `translate(R + tx, R + ty) rotate(rot) scale(sx, sy) translate(-R, -R)`. The `rotate` and the
     * `scale` of an SVG transform list act around the *origin*, so the port passes [Offset.Zero]
     * explicitly - `DrawTransform.rotate` and `scale` default to the centre of the draw scope, and
     * handing them `pivot` instead would apply the pivot twice, once inside the call and once through
     * the trailing `translate(-pivot, -pivot)`. The two only agree while the body is unit scaled,
     * which is why the mistake used to survive: as soon as the body shrinks into the `dots` overlay
     * dot the composed transforms differ by `pivot * (1 - scale)` and the dot lands in the corner.
     */
    private fun DrawScope.drawBody(frame: GrokFrame, style: GrokFrameStyle) {
        val opacity = frame.opacity
        if (opacity <= 0f || frame.bodyPath.isEmpty()) return
        val body = paths.path(frame.bodyPath)
        val eyes = frame.eyeFrame
        val pivot = frame.pivot
        withTransform({
            translate(frame.translateX, frame.translateY)
            rotate(frame.rotation, Offset.Zero)
            scale(frame.scaleX, frame.scaleY, Offset.Zero)
            translate(-pivot, -pivot)
        }) {
            drawPath(body, style.bodyColor, opacity)
            clipPath(body) {
                for (eye in 0 until 2) {
                    if (!eyes.visible[eye]) continue
                    polygon(scratch, eyes.polys[eye])
                    drawPath(scratch, style.eyeFill, opacity)
                }
            }
            if (eyes.badgeVisible) {
                drawCircle(style.badgeColor, eyes.badgeR, Offset(eyes.badgeX, eyes.badgeY), opacity)
            }
        }
    }

    /**
     * Applies the `2x3` affine transform of [element].
     *
     * [Matrix] stores `a`, `b`, `c`, `d` and the translation at the indices below, which is
     * `x' = a*x + c*y + e` and `y' = b*x + d*y + f` for `matrix(a b c d e f)`.
     */
    private fun DrawTransform.applyTo(element: GrokOverlayItem) {
        val m = element.matrix
        if (m[0] == 1f && m[1] == 0f && m[2] == 0f && m[3] == 1f && m[4] == 0f && m[5] == 0f) return
        matrix.reset()
        matrix.values[0] = m[0]
        matrix.values[1] = m[1]
        matrix.values[4] = m[2]
        matrix.values[5] = m[3]
        matrix.values[12] = m[4]
        matrix.values[13] = m[5]
        transform(matrix)
    }

    /** Closed polygon through the flattened `x, y` pairs of [points], the `polyPath` of the web build. */
    private fun polygon(target: Path, points: FloatArray) {
        target.reset()
        var index = 0
        while (index + 1 < points.size) {
            if (index == 0) {
                target.moveTo(points[0], points[1])
            } else {
                target.lineTo(points[index], points[index + 1])
            }
            index += 2
        }
        target.close()
    }
}
