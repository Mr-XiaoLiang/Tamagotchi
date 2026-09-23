package com.lollipop.grokbot.internal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** One orbit plane the belt particles ride on. */
internal class GrokPlane(var tilt: Float, var roll: Float)

/** Orbital parameters of a belt particle. */
internal class GrokOrbit(
    var lam: Float,
    var lamVel: Float,
    var tilt: Float,
    var roll: Float,
    var rad: Float,
    var radVel: Float,
    var follow: Float,
    var carry: Float,
    var arc: Float,
)

/** One sample of a ribbon trail. */
internal class GrokRibbonNode(var x: Float = 0f, var y: Float = 0f, var l: Float = 0f, var z: Float = 0f)

/** A queued belt spawn: [at] is a timestamp, [i] the belt index. */
internal class GrokSpawn(val at: Float, val i: Int)

/** One side of a ribbon band: an outline that the renderer fills. */
internal class GrokRibbonBand {
    /** Outer boundary as flat `x, y` pairs, [pointCount] points long. */
    var plus = FloatArray(INITIAL)
        private set
    var minus = FloatArray(INITIAL)
        private set
    var pointCount = 0
        private set

    /** Radius of the leading cap, `0` when the band ends on a flat edge. */
    var headCap = 0f
        private set

    /** Radius of the trailing cap, `0` when the band starts on a flat edge. */
    var tailCap = 0f
        private set

    /** `z >= 0` half of the trail, drawn above the body. */
    var front = true
        private set

    fun reset(front: Boolean, capacity: Int) {
        this.front = front
        pointCount = 0
        headCap = 0f
        tailCap = 0f
        if (plus.size < capacity) {
            plus = FloatArray(capacity)
            minus = FloatArray(capacity)
        }
    }

    fun add(x: Float, y: Float, offsetX: Float, offsetY: Float) {
        if (pointCount * 2 + 2 > plus.size) {
            plus = plus.copyOf(plus.size * 2)
            minus = minus.copyOf(minus.size * 2)
        }
        plus[pointCount * 2] = x + offsetX
        plus[pointCount * 2 + 1] = y + offsetY
        minus[pointCount * 2] = x - offsetX
        minus[pointCount * 2 + 1] = y - offsetY
        pointCount++
    }

    fun setCaps(head: Float, tail: Float) {
        headCap = head
        tailCap = tail
    }

    /** Number of points the renderer should read from [plus] / [minus]. */
    fun filledCount(): Int = pointCount * 2

    private companion object {
        const val INITIAL = 64
    }
}

/**
 * A burst spark or a belt ribbon particle.
 *
 * The web build owns one SVG node per particle; here the particle only carries the numbers the
 * renderer needs, and is dropped from the list when it dies instead of being removed from the DOM.
 */
internal class GrokParticle {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f
    var max = 0f
    var r = 0f
    var rot = 0f
    var vr = 0f
    var color = ""
    var round = false
    var star = false

    /** Opacity of a spark, or the trail alpha of a belt particle. */
    var alpha = 0f

    /** Rendered size / orientation of a spark, already resolved for the current frame. */
    var size = 0f
    var width = 0f
    var height = 0f
    var rx = 0f
    var angle = 0f

    /** Non null for belt particles. */
    var orbit: GrokOrbit? = null

    /** How far the ribbon has retracted, `0`..`1`. Belt particles only. */
    var ret = 0f

    var hue = 0f
    var hueSpan = 0f
    var hueVel = 0f

    /** Half width of the ribbon at the head. Belt particles only. */
    var trailWidth = 0f

    /** Trail samples, oldest first. Belt particles only. */
    val hist = ArrayList<GrokRibbonNode>(64)

    /**
     * Ribbon bands of the current frame, split by depth. Belt particles only.
     *
     * The web build concatenates each side into one path; the renderer must fill all
     * [frontBandCount] / [backBandCount] entries as sub-paths of a single Path so that
     * overlapping runs keep the same alpha.
     */
    val frontBands = ArrayList<GrokRibbonBand>(4)
    val backBands = ArrayList<GrokRibbonBand>(4)
    var frontBandCount = 0
    var backBandCount = 0
}

/** Palette used by burst sparks. Local to the effect layer, not [GrokGeo.PALETTE]. */
private val SPARK_PALETTE = arrayOf("#f9705c", "#5b95f0", "#3fbe86", "#f5b13f", "#9a72ee", "#35c3bd")

/**
 * Burst sparks and belt ribbons.
 *
 * Direct port of `createParticles` in `temp/replica/src/fx.js` (lines 206-461). Same maths, same
 * timings; the SVG side (`el`, `back`/`front` containers, `idPrefix`, `data-trail` marks) is gone
 * and replaced by plain fields on [GrokParticle], which the renderer turns into draw calls.
 *
 * @param reduce when true no particle is ever created, mirroring `prefers-reduced-motion`.
 * @param radius live radius of the stage, the web build's `getRadius()`.
 */
internal class GrokParticles(
    private val reduce: Boolean,
    private val radius: () -> Float,
    private val random: Random = Random.Default,
) {

    /** `0.9`, the spin speed above which belts can be seeded. */
    private val thresh = 0.9f

    /** `5`, the spin speed that always seeds belts. */
    private val hard = 5f

    private val parts = ArrayList<GrokParticle>(128)
    private val planes = ArrayList<GrokPlane>(4)
    private val spawnQ = ArrayList<GrokSpawn>(8)

    private var spin = 0f
    private var sizeScale = 1f
    private var wide = false
    private var sustain = false
    private var last = -1f

    private var hue0 = 0f
    private var beltN = 4
    private var prevSpin = 0f
    private var spinVel = 0f
    private var seeding = false
    private var cooling = false

    /** Live particles, for the renderer. Never mutate the list. */
    fun particles(): List<GrokParticle> = parts

    /** True while anything is still on screen or queued. */
    fun hasLife(): Boolean = parts.isNotEmpty() || spawnQ.isNotEmpty()

    private fun scale(): Float = radius() / GrokGeo.R

    /** Queues a radial spark burst. */
    fun burst(count: Int = 20, spread: Float = 1f, flare: Float = 0f) {
        if (reduce || parts.size > 120) return
        for (sparkIndex in 0 until count) {
            val angle = (sparkIndex.toFloat() / count) * (2f * PI.toFloat()) + GrokMath.rand(-0.35f, 0.35f, random)
            val ring = GrokMath.rand(96f, 116f, random) * scale()
            val speed = GrokMath.rand(170f, 360f, random) * spread
            val side = -sin(angle)
            val lift = cos(angle)
            val drift = flare * speed * 0.2f
            val isStar = random.nextFloat() < 0.18f
            parts.add(
                GrokParticle().apply {
                    x = GrokGeo.R + cos(angle) * ring
                    y = GrokGeo.R + sin(angle) * ring
                    vx = cos(angle) * speed + side * drift
                    vy = sin(angle) * speed + lift * drift - GrokMath.rand(20f, 75f, random)
                    life = 0f
                    max = GrokMath.rand(0.45f, 0.85f, random)
                    r = if (isStar) GrokMath.rand(4f, 7f, random) else GrokMath.rand(3.5f, 8f, random)
                    rot = GrokMath.rand(0f, 360f, random)
                    vr = GrokMath.rand(-260f, 260f, random)
                    color = if (isStar) GrokGeo.STAR_COLOR else SPARK_PALETTE[random.nextInt(SPARK_PALETTE.size)]
                    round = !isStar && random.nextFloat() < 0.3f
                    star = isStar
                },
            )
        }
    }

    private fun makePlanes(count: Int = 1) {
        val roll0 = GrokMath.rand(-0.85f, 0.85f, random)
        planes.clear()
        for (i in 0 until count) {
            planes.add(
                GrokPlane(
                    GrokMath.rand(0.16f, 0.5f, random),
                    roll0 + (i * PI.toFloat()) / count + GrokMath.rand(-0.12f, 0.12f, random),
                ),
            )
        }
        beltN = if (count > 1) count * 3 else GrokMath.rand(3f, 5f, random).toInt()
        hue0 = GrokMath.rand(0f, 360f, random)
    }

    private fun spawnBelt(lam: Float, dir: Float, index: Int) {
        if (parts.size > 110) return
        if (planes.isEmpty()) makePlanes()
        val plane = planes[index % planes.size]
        parts.add(
            GrokParticle().apply {
                x = GrokGeo.R
                y = GrokGeo.R
                life = 0f
                max = 9f
                r = when {
                    beltN <= 3 -> GrokMath.rand(8f, 10.5f, random)
                    beltN == 4 -> GrokMath.rand(6.6f, 8.6f, random)
                    else -> GrokMath.rand(5.6f, 7.4f, random)
                }
                rot = GrokMath.rand(0f, 360f, random)
                vr = GrokMath.rand(-240f, 240f, random)
                color = SPARK_PALETTE[random.nextInt(SPARK_PALETTE.size)]
                hue = hue0 + (index * 360f) / max(beltN.toFloat(), 1f) + GrokMath.rand(-14f, 14f, random)
                hueSpan = GrokMath.rand(45f, 95f, random) * if (random.nextFloat() < 0.5f) 1f else -1f
                hueVel = GrokMath.rand(18f, 42f, random) * if (random.nextFloat() < 0.5f) 1f else -1f
                orbit = GrokOrbit(
                    lam = lam,
                    lamVel = dir * GrokMath.rand(0.5f, 1.1f, random),
                    tilt = plane.tilt + GrokMath.rand(-0.04f, 0.04f, random),
                    roll = plane.roll + GrokMath.rand(-0.05f, 0.05f, random),
                    rad = scale() * 116f +
                        (index / planes.size) * (38f / max(ceilDiv(beltN, planes.size) - 1f, 1f)) +
                        GrokMath.rand(-1.5f, 1.5f, random),
                    radVel = GrokMath.rand(0f, 2.5f, random),
                    follow = GrokMath.rand(0.74f, 0.94f, random),
                    carry = 0f,
                    arc = GrokMath.rand(2.2f, 3.4f, random),
                )
            },
        )
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

    /** Projects an orbital position to screen space. */
    private fun projectX(o: GrokOrbit, lam: Float): Float {
        val flat = o.rad * sin(lam)
        val vertical = -o.rad * cos(lam) * sin(o.tilt)
        return GrokGeo.R + flat * cos(o.roll) - vertical * sin(o.roll)
    }

    private fun projectY(o: GrokOrbit, lam: Float): Float {
        val flat = o.rad * sin(lam)
        val vertical = -o.rad * cos(lam) * sin(o.tilt)
        return GrokGeo.R + flat * sin(o.roll) + vertical * cos(o.roll)
    }

    private fun depth(o: GrokOrbit, lam: Float): Float = cos(lam) * cos(o.tilt)

    /**
     * Rebuilds the trail outline of [p] into its front / back bands.
     *
     * The bands are the two boundaries of a tapered capsule per depth run, so the renderer can
     * fill them with real arcs instead of the SVG `A` commands the web build emits.
     */
    private fun buildRibbon(p: GrokParticle, bandWidth: Float) {
        p.frontBandCount = 0
        p.backBandCount = 0
        val hist = p.hist
        val n = hist.size
        if (n < 2) return

        val offsetX = FloatArray(n)
        val offsetY = FloatArray(n)
        for (i in 0 until n) {
            val prev = hist[if (i > 0) i - 1 else 0]
            val next = hist[if (i < n - 1) i + 1 else n - 1]
            var dx = next.x - prev.x
            var dy = next.y - prev.y
            val len = hypot(dx, dy).let { if (it == 0f) 1f else it }
            dx /= len
            dy /= len
            val half = bandWidth * (0.5f + 0.5f * (i / (n - 1).toFloat())) / 2f
            offsetX[i] = -dy * half
            offsetY[i] = dx * half
        }

        var start = 0
        while (start < n) {
            val front = hist[start].z >= 0f
            var end = start
            while (end + 1 < n && (hist[end + 1].z >= 0f) == front) end++
            val from = max(start - 1, 0)
            val to = min(end + 1, n - 1)
            if (to > from) {
                val bands = if (front) p.frontBands else p.backBands
                val slot = if (front) p.frontBandCount else p.backBandCount
                val band = if (slot < bands.size) bands[slot] else GrokRibbonBand().also { bands.add(it) }
                band.reset(front, to - from + 1)
                for (i in from..to) band.add(hist[i].x, hist[i].y, offsetX[i], offsetY[i])
                band.setCaps(
                    head = if (to == n - 1) max(hypot(offsetX[to], offsetY[to]), 0.2f) else 0f,
                    tail = if (from == 0) max(hypot(offsetX[0], offsetY[0]), 0.2f) else 0f,
                )
                if (front) p.frontBandCount++ else p.backBandCount++
            }
            start = end + 1
        }
    }

    /** Frame to frame spin speed, and the seeding / cooling transitions it drives. */
    private fun tickVel(dt: Float) {
        var delta = spin - prevSpin
        if (!delta.isFinite() || abs(delta) > 1.2f) delta = 0f
        prevSpin = spin
        val wasFast = abs(spinVel) >= thresh
        spinVel = if (dt > 0f) delta / dt else 0f
        val nowFast = abs(spinVel) >= thresh
        if (!wasFast && nowFast) {
            makePlanes(if (wide) 3 else 1)
            seeding = false
            cooling = false
        }
        if (wasFast && !nowFast) {
            spawnQ.clear()
            cooling = false
        }
    }

    /** Queues one belt round while the body spins fast enough. */
    private fun seedBelts(now: Float) {
        if (reduce) return
        val speed = abs(spinVel)
        val live = parts.any { it.orbit != null && it.ret < 1f }
        if (sustain && seeding && spawnQ.isEmpty() && speed >= thresh && !live) {
            seeding = false
            cooling = true
        }
        if (!seeding && (speed >= hard || (sustain && cooling && speed >= thresh))) {
            seeding = true
            cooling = false
            spawnQ.clear()
            for (i in 0 until beltN) spawnQ.add(GrokSpawn(now + i * GrokMath.rand(55f, 105f, random), i))
        }
        while (spawnQ.isNotEmpty() && now >= spawnQ[0].at) {
            val queued = spawnQ.removeAt(0)
            spawnBelt(spin - GrokMath.rand(0f, 0.18f, random), signOf(spinVel).let { if (it == 0f) 1f else it }, queued.i)
        }
    }

    private fun signOf(v: Float): Float = if (v > 0f) 1f else if (v < 0f) -1f else 0f

    /** Advances every particle, dropping the dead ones in place. */
    private fun step(dt: Float, realDt: Float) {
        if (parts.isEmpty()) return
        val spinning = abs(spinVel) >= thresh
        var kept = 0
        for (i in parts.indices) {
            val p = parts[i]
            p.life += if (p.life > 0f) realDt else dt
            val t = GrokMath.clamp(p.life / p.max, 0f, 1f)
            val orbit = p.orbit
            if (orbit != null) {
                val away = !spinning || t > 0.55f
                p.ret = GrokMath.clamp(p.ret + (if (away) realDt / 0.5f else -realDt / 0.35f), 0f, 1f)
                if (p.ret >= 1f) continue
            } else if (p.life >= p.max) {
                continue
            }
            p.alpha = when {
                orbit != null -> min(1f, p.life / 0.26f)
                t < 0.1f -> t / 0.1f
                else -> (1f - (t - 0.1f) / 0.9f).pow(1.7f)
            }
            if (orbit != null) stepOrbit(p, orbit, spinning, dt) else stepSpark(p, t, dt)
            parts[kept++] = p
        }
        while (parts.size > kept) parts.removeAt(parts.size - 1)
    }

    private fun stepOrbit(p: GrokParticle, orbit: GrokOrbit, spinning: Boolean, dt: Float) {
        if (spinning) {
            orbit.carry = spinVel * orbit.follow
            orbit.lam += spinVel * dt * orbit.follow + orbit.lamVel * dt
            orbit.rad += orbit.radVel * dt
        } else {
            orbit.lam += (orbit.carry + orbit.lamVel) * dt
            orbit.carry *= exp(-2.6f * dt)
            orbit.lamVel *= exp(-2.6f * dt)
            orbit.rad += orbit.radVel * dt
        }
        p.x = projectX(orbit, orbit.lam)
        p.y = projectY(orbit, orbit.lam)

        val near = 0.72f + 0.28f * GrokMath.clamp(depth(orbit, orbit.lam), 0f, 1f)
        val grow = min(p.life / 0.34f, 1f)
        val ease = grow * grow * (3f - 2f * grow)
        val width = max(p.r * near * 1.7f * sizeScale * ease * (1f - 0.72f * p.ret * p.ret), 0.5f)
        p.trailWidth = width

        val previousLam = p.hist.lastOrNull()?.l ?: orbit.lam
        val span = orbit.lam - previousLam
        val steps = min(ceil(abs(span) / 0.09f).toInt(), 24)
        for (s in 1..steps) {
            val l = previousLam + (span * s) / steps
            p.hist.add(GrokRibbonNode(projectX(orbit, l), projectY(orbit, l), l, depth(orbit, l)))
        }
        if (p.hist.isEmpty()) {
            p.hist.add(GrokRibbonNode(p.x, p.y, orbit.lam, depth(orbit, orbit.lam)))
        }

        val arc = orbit.arc * (1f - p.ret * p.ret * (3f - 2f * p.ret))
        while (p.hist.size > 2 && abs(orbit.lam - p.hist[0].l) > arc) p.hist.removeAt(0)
        val overflow = abs(orbit.lam - p.hist[0].l) - arc
        if (p.hist.size >= 2 && overflow > 0f) {
            val l = p.hist[0].l + signOf(orbit.lam - p.hist[0].l) * overflow
            val head = p.hist[0]
            head.x = projectX(orbit, l)
            head.y = projectY(orbit, l)
            head.l = l
            head.z = depth(orbit, l)
        }
        while (p.hist.size > 48) p.hist.removeAt(0)

        if (p.hist.size >= 2) {
            buildRibbon(p, width)
        } else {
            p.frontBandCount = 0
            p.backBandCount = 0
        }
    }

    private fun stepSpark(p: GrokParticle, t: Float, dt: Float) {
        p.x += p.vx * dt
        p.y += p.vy * dt
        val damp = 0.94f.pow(dt * 60f)
        p.vx *= damp
        p.vy = p.vy * damp + 40f * dt
        val size = max(p.r * (1f - t * 0.4f), 0.5f)
        p.size = size
        if (p.star) {
            p.rot += p.vr * dt
        } else if (!p.round) {
            p.width = max(size * 2f, min(hypot(p.vx, p.vy) * 0.05f, 30f))
            p.height = size * 1.5f
            p.rx = p.height / 2f
            p.angle = atan2(p.vy, p.vx) * 180f / PI.toFloat()
        }
    }

    /**
     * One animation frame.
     *
     * @param now timestamp in milliseconds.
     * @param dt physics step, usually the clamped frame delta.
     * @param sizeScale global size multiplier of the character.
     * @param spinAngle current spin angle, its derivative drives the belts.
     * @param sustainBelts keep seeding belts while spinning slowly.
     */
    fun update(
        now: Float,
        dt: Float,
        sizeScale: Float,
        spinAngle: Float,
        wideStyle: Boolean,
        sustainBelts: Boolean,
    ) {
        val realDt = if (last < 0f) dt else max((now - last) / 1000f, 0f)
        last = now
        this.sizeScale = sizeScale
        spin = spinAngle
        wide = wideStyle
        sustain = sustainBelts
        tickVel(dt)
        seedBelts(now)
        step(dt, realDt)
    }
}

