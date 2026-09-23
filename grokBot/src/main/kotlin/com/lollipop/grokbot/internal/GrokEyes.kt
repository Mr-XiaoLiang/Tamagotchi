package com.lollipop.grokbot.internal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A queued blink keyframe. */
internal class GrokBlink(var at: Float, var value: Float)

/** Result of one eye pass: the two transformed polygons and the badge state. */
internal class GrokEyeFrame(val polygonSize: Int) {
    val visible = BooleanArray(2) { true }
    val polys = arrayOf(FloatArray(polygonSize), FloatArray(polygonSize))
    var badgeVisible = false
    var badgeX = 0f
    var badgeY = 0f
    var badgeR = 0f
}

/** Everything the eye pass needs, mirrors the `opt` object of the web build. */
internal class GrokEyeOptions {
    var now = 0f
    var morphT = 0f
    var shape: GrokShapeData = GrokGeo.SHAPES.first()
    var face: GrokBot = GrokBot()
    var uniformEyes = GrokTabs.UNIFORM_EYES
    var eyeScaleProp = 1f
    var eyeBoostX = 1f
    var blinkX = 1f
    var gazeX = 0f
    var gazeY = 0f
    var winkAt = -10000f
    var winkEye = 0
    var turn: Float? = null
    var cr: FloatArray? = null
    var pointer: FloatArray? = null
    var notifyX = 0f
    var overlayX = 0f
    var badgeColor: Int = DEFAULT_BADGE
    var extrasZr = 0f
    var extrasWi = 0f
    var ringHint: FloatArray? = null
    var top: Float = 0f
    var bottom: Float = 0f
    var emphasisBlend = 0f
    var badgeRing: FloatArray? = null

    /**
     * Scratch owned by the engine (this object is reused for every frame), so the pass can hand the
     * centroid of each polygon and one silhouette sample back without allocating.
     *
     * The silhouette sample is the reason this matters: it is taken once per vertex, 48 times per
     * frame, and a fresh `FloatArray` there was 2.4 kB of garbage per frame — see `Task.md` T5.2 and
     * [GrokFrameBudgetTest], which bounds exactly this.
     */
    val cents = arrayOf(FloatArray(2), FloatArray(2))
    val span = FloatArray(2)

    companion object {
        const val DEFAULT_BADGE = 0xFF1D9BF0.toInt()
    }
}

internal object GrokEyes {

    fun queueBlink(queue: ArrayDeque<GrokBlink>, now: Float, random: Random) {
        queue.addLast(GrokBlink(now, 0.05f))
        queue.addLast(GrokBlink(now + 70f, 0.05f))
        queue.addLast(GrokBlink(now + 150f, 1.08f))
        queue.addLast(GrokBlink(now + 300f, 1f))
        if (random.nextFloat() < 0.14f) {
            queue.addLast(GrokBlink(now + 370f, 0.05f))
            queue.addLast(GrokBlink(now + 480f, 1f))
        }
    }

    fun consumeBlink(queue: ArrayDeque<GrokBlink>, now: Float): Float? {
        var key: Float? = null
        while (queue.isNotEmpty() && now >= queue.first().at) {
            key = queue.removeFirst().value
        }
        return key
    }

    fun winkLid(base: Float, now: Float, winkAt: Float, winkEye: Int, i: Int): Float {
        var lid = max(base, 0.04f)
        if (i == winkEye && now < winkAt + 320f) {
            val xr = (now - winkAt) / 320f
            val fr = if (xr < 0.42f) 1f - xr / 0.42f else (xr - 0.42f) / 0.58f
            lid = max(lid * GrokMath.clamp(fr, 0f, 1f), 0.04f)
        }
        return lid
    }

    /**
     * Places both eyes on the body for this frame.
     *
     * The original renderer wrote SVG transforms, here the vertices are transformed directly, which
     * is what keeps this pass allocation free: every intermediate ([GrokEyeOptions.cents],
     * [GrokEyeOptions.span]) is engine-owned scratch rather than a fresh array. That claim is not
     * taken on trust — `GrokFrameBudgetTest` measures this pass at 48 vertices per frame, and it was
     * 2.4 kB of garbage per frame before the scratch was introduced (`Task.md` T5.2).
     */
    fun paint(opt: GrokEyeOptions, polys: Array<FloatArray>, out: GrokEyeFrame) {
        val now = opt.now
        val face = opt.face
        val tune = GrokTabs.FaceTune
        val re = GrokGeo.R
        val pulse = 1f + 0.07f * sin(opt.morphT * PI.toFloat())

        val fx = face.x
        val fy = face.y
        val fsx = face.sx * tune.GAP
        val fsy = face.sy * tune.HEIGHT
        val feye = face.eye * tune.SIZE
        val leftDx = face.leftDx

        val shiftX = if (opt.uniformEyes) leftDx else 0f
        val cents = opt.cents
        GrokMath.centroid(polys[0], cents[0])
        GrokMath.centroid(polys[1], cents[1])
        var a1 = 0f
        var o1 = 0f
        var i = 0
        while (i < polys[0].size) {
            a1 = max(a1, abs(polys[0][i] - cents[0][0]))
            i += 2
        }
        i = 0
        while (i < polys[1].size) {
            o1 = max(o1, abs(polys[1][i] - cents[1][0]))
            i += 2
        }
        val l1 = abs(cents[1][0] - (cents[0][0] + shiftX)) * fsx
        val pre = if (opt.uniformEyes) 0f else GrokGeo.BOTTOM_EYE_COUNT.toFloat()
        val ee = if (a1 + o1 > 0.5f) GrokMath.clamp((l1 - pre) / (a1 + o1), 0.35f, 4f) else 4f
        val uee = (if (opt.uniformEyes) 1f else feye) * GrokMath.clamp(opt.eyeScaleProp, 0.25f, 4f)
        val ox = min(GrokMath.clamp(opt.eyeBoostX, 0.2f, 2f) * uee, ee / pulse)
        val hee = min(ox * GrokMath.clamp(tune.EYE_WIDTH, 0.2f, 3f), ee / pulse)
        val u1 = ox * GrokMath.clamp(tune.EYE_HEIGHT, 0.2f, 3f)

        val ringHint = opt.ringHint
        val shape = opt.shape
        val top = opt.top
        val bottom = opt.bottom
        val emphasis = opt.emphasisBlend
        val midX = (cents[0][0] + cents[1][0]) / 2f
        val midY = (cents[0][1] + cents[1][1]) / 2f
        val pullX = (re - midX) * 0.42f * emphasis
        val pullY = (re - midY) * 0.42f * emphasis
        val gazeW = if (opt.pointer != null) 0.2f else 1f
        val badgeRing = opt.badgeRing ?: ringHint
        var badgeX = re
        var badgeY = shape.top
        if (badgeRing != null && badgeRing.isNotEmpty()) {
            val points = badgeRing.size / 2
            val idx = ((points.toFloat() * 7f / 8f).roundToInt() % points + points) % points
            badgeX = badgeRing[idx * 2]
            badgeY = badgeRing[idx * 2 + 1]
        }

        for (eye in 0..1) {
            val poly = polys[eye]
            val gn = cents[eye][0]
            val ti = cents[eye][1]
            val dest = out.polys[eye]
            val lid = winkLid(opt.blinkX, now, opt.winkAt, opt.winkEye, eye)
            val ea = gn + (if (eye == 0) shiftX else 0f)
            var ca = re + fx
            var wo = (ea - re) * fsx
            var scX = 1f
            var scY = 1f
            var km = 1f
            var ree = 0f
            var fee = 0f
            var zee = 1f
            var visible = true
            var tre = 1f
            var sre = GrokMath.clamp(re + fy + (ti - re) * fsy, top + 2f, bottom - 2f)
            val cr = opt.cr
            val use3d = cr != null

            if (cr != null) {
                val xr = (ea - re) / re
                val fr = (re - ti) / re
                val ia = sqrt(max(0f, 1f - xr * xr - fr * fr)).let { if (it == 0f) 0.02f else it }
                val li = cr[0] * xr + cr[1] * fr + cr[2] * ia
                val bl = cr[3] * xr + cr[4] * fr + cr[5] * ia
                val io = cr[6] * xr + cr[7] * fr + cr[8] * ia
                wo = li * re * fsx
                sre = GrokMath.clamp(re + fy - bl * re * fsy, top + 2f, bottom - 2f)
                var uo = -fr * xr
                var tl = 1f - fr * fr
                var zi = -fr * ia
                val yo = sqrt(uo * uo + tl * tl + zi * zi)
                if (yo < 1e-6f) {
                    uo = 0f
                    tl = 0f
                    zi = 1f
                } else {
                    uo /= yo
                    tl /= yo
                    zi /= yo
                }
                val md = fr * zi - ia * tl
                val oc = ia * uo - xr * zi
                val yu = xr * tl - fr * uo
                val vm = cr[0] * uo + cr[1] * tl + cr[2] * zi
                val hme = cr[3] * uo + cr[4] * tl + cr[5] * zi
                val nre = cr[0] * md + cr[1] * oc + cr[2] * yu
                val ere = cr[3] * md + cr[4] * oc + cr[5] * yu
                val cre = md
                val ha = -oc
                val ci = uo
                val ys = -tl
                val ku = (cre * ys - ci * ha).let { if (it == 0f) 1e-6f else it }
                val ql = ys / ku
                val gee = -ci / ku
                val bm = -ha / ku
                val ire = cre / ku
                km = nre * ql + vm * bm
                fee = nre * gee + vm * ire
                ree = -ere * ql + -hme * bm
                zee = -ere * gee + -hme * ire
                scX = max(sqrt(km * km + ree * ree), 0.02f)
                scY = max(sqrt(fee * fee + zee * zee), 0.02f)
                visible = io > 0.02f
                tre = GrokMath.dke(GrokMath.clamp(io / 0.5f, 0f, 1f))
            }

            val turn = opt.turn
            if (turn != null) {
                liveSpan(ringHint, shape, sre, re, opt.span)
                val spL = opt.span[0]
                val spR = opt.span[1]
                val rad = max((spR - spL) / 2f, 12f)
                ca = (spL + spR) / 2f
                val li0 = asin(GrokMath.clamp(wo / rad, -1f, 1f))
                val bl0 = li0 + turn
                val io0 = cos(bl0)
                val uo0 = max(cos(li0), 0.02f)
                visible = io0 > 0.02f
                scX = max(io0, 0.02f) / uo0
                wo = rad * sin(bl0)
                tre = GrokMath.dke(GrokMath.clamp(io0 / 0.5f, 0f, 1f))
            }

            var kj = sin(now * 42e-5f + eye) * 1.4f + sin(now * 0.001f + eye * 2f) * 0.5f
            var ko = sin(now * 58e-5f + eye) * 0.9f
            val pointer = opt.pointer
            if (pointer != null) {
                kj += pointer[0] * (1f - 0.6f * emphasis) + pullX
                ko += pointer[1] * (1f - 0.6f * emphasis) + pullY
            } else {
                kj += pullX
                ko += pullY
            }
            kj += opt.gazeX * gazeW + opt.extrasZr
            ko += opt.gazeY * gazeW + opt.extrasWi
            val notifyClamped = GrokMath.clamp(opt.notifyX, 0f, 1f)
            kj -= 10f * notifyClamped
            ko += 7f * notifyClamped

            val vee = GrokMath.clamp(scX * hee * pulse, 0.02f, 2.4f)
            val scy = GrokMath.clamp(scY * lid * u1 * pulse, 0.02f, 2.4f)
            out.visible[eye] = visible && opt.overlayX < 0.5f
            val useTurnOr3d = turn != null || use3d
            val ume = GrokGeo.TOP_EYE_COUNT * scy + 2f
            val vl = GrokMath.clamp(
                if (useTurnOr3d) sre + ko * fsy else re + fy + (ti + ko - re) * fsy,
                top + ume,
                bottom - ume,
            )
            var o2 = -Float.MAX_VALUE
            var xl = Float.MAX_VALUE
            var p = 0
            while (p < poly.size) {
                val frp = (poly[p] - gn) * vee
                liveSpan(ringHint, shape, vl + (poly[p + 1] - ti) * scy, re, opt.span)
                if (opt.span[0] - frp > o2) o2 = opt.span[0] - frp
                if (opt.span[1] - frp < xl) xl = opt.span[1] - frp
                p += 2
            }
            val xre = ca + wo + kj * fsx
            val lx = if (o2 <= xl) GrokMath.clamp(xre, o2, xl) else (o2 + xl) / 2f
            var dd = lx + (xre - lx) * (1f - tre)
            var yj = vl
            if (opt.notifyX > 0.01f) {
                val xr = 20f * GrokMath.clamp(opt.notifyX, 0f, 1.4f)
                val fr = dd - badgeX
                val ia = yj - badgeY
                val li = sqrt(fr * fr + ia * ia).let { if (it == 0f) 1f else it }
                val bl = fr / li
                val io = ia / li
                val uo = (if (eye == 0) a1 else o1) * vee
                val dx = uo * bl
                val dy = GrokGeo.TOP_EYE_COUNT * scy * io
                val tl = sqrt(dx * dx + dy * dy)
                val zi = xr + tl + 5f
                if (li < zi) {
                    dd += bl * (zi - li)
                    yj += io * (zi - li)
                }
            }

            if (use3d) {
                val frM = GrokMath.clamp((if (turn != null) scX else 1f) * hee * pulse, 0.02f, 2.4f)
                val iaM = GrokMath.clamp(lid * u1 * pulse, 0.02f, 2.4f)
                val liM = km * frM
                val blM = ree * frM
                val ioM = fee * iaM
                val uoM = zee * iaM
                p = 0
                while (p < poly.size) {
                    val qx = poly[p] - gn
                    val qy = poly[p + 1] - ti
                    dest[p] = dd + liM * qx + ioM * qy
                    dest[p + 1] = yj + blM * qx + uoM * qy
                    p += 2
                }
            } else {
                p = 0
                while (p < poly.size) {
                    dest[p] = dd + (poly[p] - gn) * vee
                    dest[p + 1] = yj + (poly[p + 1] - ti) * scy
                    p += 2
                }
            }
        }

        val amt = GrokMath.clamp(opt.notifyX, 0f, 1.4f)
        out.badgeVisible = amt > 0.01f
        out.badgeX = badgeX
        out.badgeY = badgeY
        out.badgeR = 20f * amt
    }

    private fun liveSpan(
        ringHint: FloatArray?,
        shape: GrokShapeData,
        y: Float,
        re: Float,
        out: FloatArray,
    ) {
        if (ringHint != null) {
            GrokMath.spanPoly(ringHint, y, re, out)
        } else {
            GrokMath.spanAt(shape.path, re).at(y, out)
        }
    }
}
