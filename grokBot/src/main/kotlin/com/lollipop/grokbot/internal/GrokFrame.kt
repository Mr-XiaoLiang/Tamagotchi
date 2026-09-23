package com.lollipop.grokbot.internal

/** Viewport window of one frame, the `viewBox` attribute of the web build. */
internal class GrokViewBox {
    var x = 0f
    var y = 0f

    /** Side of the square window, `2 * half`. */
    var size = 0f
}

/**
 * Everything the renderer draws for one frame, the replacement for the attribute writes of `_paint`
 * (`temp/replica/src/character.js` lines 709-842).
 *
 * The web build pushed each value straight into the DOM; here [GrokEngine.paint] fills this object
 * instead and the renderer turns it into draw calls. The engine owns one instance and rewrites it in
 * place, so the contents are only valid until the next [GrokEngine.paint].
 *
 * The effect layer is deliberately *not* copied in: [overlay] is the live [GrokOverlay], whose slots
 * already hold the current frame, and [particles] is read straight off [GrokParticles], so nothing is
 * duplicated per frame.
 *
 * @param overlay effect layer painted alongside this frame.
 * @param polygonSize vertex count of one eye polygon. Every polygon of `GrokGeo.EYES` carries the
 *   same count, which is also the size of [GrokEyeFrame.polys] entries.
 */
internal class GrokFrame(val overlay: GrokOverlay, polygonSize: Int) {

    /** Body outline. The web build also installed it as the eye clip path. */
    var bodyPath: String = ""

    var translateX = 0f
    var translateY = 0f

    /** Body rotation in degrees. */
    var rotation = 0f

    var scaleX = 1f
    var scaleY = 1f

    /**
     * Opacity of the whole body, `1` unless the `standby` overlay or the notify dots dim it.
     *
     * It multiplies whatever alpha the body fill already carries.
     */
    var opacity = 1f

    /** Pivot the transform rotates and scales around, `(R, R)` in design units. */
    val pivot: Float = GrokGeo.R

    val viewBox = GrokViewBox()

    /** Eye outlines of this frame, placement transform already applied. */
    val eyeFrame = GrokEyeFrame(polygonSize)

    /**
     * Live particles of this frame: the burst sparks and the belt ribbons, the `back` and `front`
     * containers of the web build.
     *
     * Like [overlay] this is the list [GrokParticles] owns, so it already holds the current frame
     * and is not copied.
     */
    var particles: List<GrokParticle> = emptyList()
}
