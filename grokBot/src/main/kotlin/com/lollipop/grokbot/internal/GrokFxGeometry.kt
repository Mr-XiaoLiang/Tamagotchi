package com.lollipop.grokbot.internal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Silhouette metrics of one body, mirrors `shapeMetrics()` of the web build. */
internal class GrokShapeMetrics(
    val shape: GrokShapeData,
    /** Silhouette sampled as a polar ring around `(R, R)`. */
    val ring: FloatArray,
    /** Turn trick scale, never `0` (the web build falls back to `1`). */
    val tilt: Float,
    /** Half width of the widest horizontal belt of the silhouette. */
    val belt: Float,
) {
    /** Face placement of the body. Shared with [GrokShapeData], do not mutate. */
    val face: GrokBot get() = shape.face
}

/**
 * Ring / silhouette geometry shared by the overlay effects.
 *
 * Port of the geometry half of `temp/replica/src/fx.js` (lines 61-205 and the caches at 843-908).
 * Polygons are flat `FloatArray`s of `x, y` pairs, like [GrokMath], so rings feed straight into
 * `spanPoly` / `lerpPoly` / `makeTurnAt`.
 *
 * Deliberately NOT ported here, because they belong to drawing or already exist in [GrokMath]:
 * `el`/`closedSpline`/`capsule`/`taper`/`smoothLine`/`pencilPose` (drawing side), and
 * `flattenPath`/`spanHalf`/`lerpRing`/`clamp`/`rand`/`Rc`/`y1e`/`K2`.
 */
internal object GrokFx {

    /** Sample count of every generated ring. */
    const val RING_SAMPLES = 96

    /**
     * Overlay presence at which the body outline has become the overlay ring completely.
     *
     * Below it the outline is blended between the silhouette and the ring, see the `bodyD` block of
     * `_paint` in the web build.
     */
    const val P_BLEND = 0.62f

    // ---------------------------------------------------------------- rings

    /** Circle of radius [r] centred on `(r, r)`. */
    fun circleRing(r: Float, n: Int = RING_SAMPLES): FloatArray {
        val out = FloatArray(n * 2)
        val step = 2f * PI.toFloat()
        for (i in 0 until n) {
            val t = i.toFloat() / n * step
            out[i * 2] = r + cos(t) * r
            out[i * 2 + 1] = r + sin(t) * r
        }
        return out
    }

    /** Polar silhouette of [pts]: for every angle, the farthest edge hit from `(r, r)`. */
    fun polarRing(pts: FloatArray, r: Float, n: Int = RING_SAMPLES): FloatArray {
        val count = GrokMath.pointCount(pts)
        val out = FloatArray(n * 2)
        val step = 2f * PI.toFloat()
        for (i in 0 until n) {
            val angle = i.toFloat() / n * step
            val dx = cos(angle)
            val dy = sin(angle)
            var best = 0f
            for (l in 0 until count) {
                val c = l * 2
                val u = ((l + 1) % count) * 2
                val cx = pts[c] - r
                val cy = pts[c + 1] - r
                val ux = pts[u] - r
                val uy = pts[u + 1] - r
                val den = (ux - cx) * dy - (uy - cy) * dx
                if (abs(den) < 1e-9f) continue
                val k = (cx * dy - cy * dx) / -den
                if (k < 0f || k > 1f) continue
                val v = (cx + (ux - cx) * k) * dx + (cy + (uy - cy) * k) * dy
                if (v > best) best = v
            }
            out[i * 2] = r + dx * best
            out[i * 2 + 1] = r + dy * best
        }
        return out
    }

    /** Rotates [ring] by [offset] samples around `(r, r)`. */
    fun rotateRing(ring: FloatArray, offset: Int, r: Float): FloatArray {
        val count = GrokMath.pointCount(ring)
        val angle = offset.toFloat() / count * (2f * PI.toFloat())
        val dx = cos(angle)
        val dy = sin(angle)
        val out = FloatArray(ring.size)
        for (l in 0 until count) {
            val src = (((l - offset) % count) + count) % count
            val px = ring[src * 2] - r
            val py = ring[src * 2 + 1] - r
            out[l * 2] = r + px * dx - py * dy
            out[l * 2 + 1] = r + px * dy + py * dx
        }
        return out
    }

    private class RingEntry(val ring: FloatArray, val radius: Float)

    private val ringCache = HashMap<String, RingEntry>()

    /** Cached [polarRing] of an SVG body outline. Recomputes if [r] ever changes. */
    fun shapeRing(path: String, r: Float): FloatArray {
        val hit = ringCache[path]
        if (hit != null && hit.radius == r) return hit.ring
        val ring = polarRing(GrokMath.flattenPath(path), r)
        ringCache[path] = RingEntry(ring, r)
        return ring
    }

    /** Ring the overlay effects are drawn against. `pencil` uses its teardrop outline. */
    fun overlayRing(kind: String, r: Float, teardropPath: String?): FloatArray {
        if (kind == KIND_PENCIL && teardropPath != null) {
            val ring = polarRing(GrokMath.flattenPath(teardropPath), r)
            return rotateRing(ring, GrokMath.pointCount(ring) / 2, r)
        }
        return circleRing(r)
    }

    // ---------------------------------------------------------------- caches

    private val turnAtCache = HashMap<String, ((Float) -> FloatArray)?>()

    /**
     * Depth lookup used by the 3D turn trick, or `null` for bodies without depth samples.
     * Cached per solid name.
     */
    fun turnAtOf(name: String, path: String, r: Float): ((Float) -> FloatArray)? {
        if (turnAtCache.containsKey(name)) return turnAtCache[name]
        val solid = GrokGeo.SOLIDS[name]
        val fn = if (solid == null) null else GrokMath.makeTurnAt(solid, shapeRing(path, r), r)
        turnAtCache[name] = fn
        return fn
    }

    private val beltCache = HashMap<String, Float>()

    /** Half width of the widest horizontal belt of a body, cached per path. */
    fun beltRadius(path: String, r: Float): Float {
        beltCache[path]?.let { return it }
        val ring = shapeRing(path, r)
        val count = GrokMath.pointCount(ring)
        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (i in 0 until count) {
            val y = ring[i * 2 + 1]
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        var best = 0f
        var y = top
        val span = FloatArray(2)
        while (y <= bottom) {
            GrokMath.spanPoly(ring, y, r, span)
            val half = (span[1] - span[0]) / 2f
            if (half > best) best = half
            y += 2f
        }
        beltCache[path] = best
        return best
    }

    /** Silhouette metrics of [shape]. Safe to call repeatedly, everything inside is cached. */
    fun shapeMetrics(shape: GrokShapeData, r: Float): GrokShapeMetrics = GrokShapeMetrics(
        shape = shape,
        ring = shapeRing(shape.path, r),
        tilt = if (shape.tiltScale == 0f) 1f else shape.tiltScale,
        belt = beltRadius(shape.path, r),
    )

    /** Overlay kind whose ring is derived from a teardrop outline instead of a circle. */
    const val KIND_PENCIL = "pencil"

    /** Id of the teardrop silhouette, the outline `KIND_PENCIL` derives its ring from. */
    const val KIND_TEARDROP = "teardrop"
}
