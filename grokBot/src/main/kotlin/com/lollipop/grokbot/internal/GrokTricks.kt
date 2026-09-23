package com.lollipop.grokbot.internal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Which one-shot trick the character is playing. Mirrors the strings of `tricks.js`. */
internal enum class GrokTrickKind {
    /** Half a turn, then a hop. */
    SPIN_BOUNCE,

    /** Several quick turns followed by a dizzy wobble. */
    SPIN_DIZZY,

    /** Accelerate / slide / decelerate to an exact number of turns. */
    SPIN_WILD,
}

/** A running trick: what it is, when it started, which way and how many turns. */
internal class GrokTrick(
    val kind: GrokTrickKind,
    /** Start timestamp in milliseconds, same clock as `GrokEngine.now`. */
    val t0: Float,
    /** `+1` or `-1`, the direction of the spin. */
    val dir: Float,
    /** How many full turns the trick should make. */
    val turns: Int,
)

/**
 * Output of one [GrokTricks.evalTrick] pass.
 *
 * Field names are kept identical to the web build (`Kr`, `yi`, `ki`, `Yr`, `Zr`, `wi`) so the
 * engine port stays mechanical; they are independent wobble channels consumed by the engine.
 *
 * Reused every frame, never allocated per tick.
 */
internal class GrokTrickEval {
    /** Absolute rotation override, `null` when the trick does not drive the rotation. */
    var turn: Float? = null
    var kr = 0f
    var yi = 0f
    var ki = 0f
    var yr = 0f
    var zr = 0f
    var wi = 0f

    /** Vertical aperture multiplier, `null` when the trick does not touch it. */
    var lidMul: Float? = null
    var eyeBoost: Float? = null

    /** Always `0`; the caller uses [GrokTricks.hopY]. Kept for parity with the web build. */
    var hop = 0f
    var done = true
    var wantHop = false

    fun reset() {
        turn = null
        kr = 0f
        yi = 0f
        ki = 0f
        yr = 0f
        zr = 0f
        wi = 0f
        lidMul = null
        eyeBoost = null
        hop = 0f
        done = true
        wantHop = false
    }
}

/**
 * Hop curve plus the three one-shot spin tricks.
 *
 * Direct port of `temp/replica/src/tricks.js`.
 */
internal object GrokTricks {

    /** Ballistic height of each hop segment, in design pixels. */
    private val HOP_H = floatArrayOf(48f, 28f, 14f, 6f)

    /** Duration of each hop segment, in seconds. */
    private val HOP_D = floatArrayOf(0.5f, 0.382f, 0.27f, 0.177f)

    /** Total hop length in seconds (1.329). */
    val HOP_DUR: Float = (0.5 + 0.382 + 0.27 + 0.177).toFloat()

    /**
     * Vertical offset of a hop started at [hopAt], or `null` once it has finished.
     *
     * Returns `0` for a hop that never started (`hopAt < 0`).
     */
    fun hopY(hopAt: Float, now: Float): Float? {
        if (hopAt < 0f) return 0f
        val et = (now - hopAt) / 1000f
        if (et >= HOP_DUR) return null
        var en = 0f
        for (i in HOP_D.indices) {
            val d = HOP_D[i]
            if (et < en + d) {
                val bn = (et - en) / d
                return -4f * HOP_H[i] * bn * (1f - bn)
            }
            en += d
        }
        return 0f
    }

    /**
     * Starts a trick at [now], or `null` when [reduce] (reduced motion) is on or the mix is paused.
     *
     * `spinDizzy` picks 3 or 4 turns at random, `spinWild` always 9, everything else a single turn.
     */
    fun startTrick(kind: GrokTrickKind, reduce: Boolean, now: Float, random: Random): GrokTrick? {
        if (reduce) return null
        val dir = GrokMath.sign(random)
        val turns = when (kind) {
            GrokTrickKind.SPIN_DIZZY -> GrokMath.rand(3f, 4f, random).roundToInt()
            GrokTrickKind.SPIN_WILD -> 9
            GrokTrickKind.SPIN_BOUNCE -> 1
        }
        return GrokTrick(kind, now, dir, turns)
    }

    /**
     * Evaluates [trick] at [now] into [out]. A `null` trick simply resets [out] and marks it done.
     *
     * [out] is fully overwritten, so the caller may keep reusing one instance.
     */
    fun evalTrick(trick: GrokTrick?, now: Float, out: GrokTrickEval) {
        out.reset()
        if (trick == null) return

        val et = (now - trick.t0) / 1000f
        val dir = trick.dir
        val turns = trick.turns
        val full = 2f * PI.toFloat()
        out.done = false

        when (trick.kind) {
            GrokTrickKind.SPIN_DIZZY -> {
                val on = 0.55f + turns * 0.16f
                val bn = 1.5f
                if (et < on) {
                    val cn = et / on
                    out.turn = turns * full * dir * (cn * cn)
                } else if (et < on + bn) {
                    val cn = et - on
                    val bi = (1f - cn / bn).pow(1.3f)
                    out.kr = sin(cn * 10f) * 17f * dir * bi
                    out.yi = cos(cn * 10f) * 10f * dir * bi
                    out.ki = sin(cn * 20f) * 3f * bi
                    out.lidMul = 0.46f + 0.14f * sin(cn * 21f)
                    out.eyeBoost = 1.03f
                } else {
                    out.done = true
                }
            }

            GrokTrickKind.SPIN_WILD -> {
                val windUp = 0.24f
                val accel = 0.3f
                val slide = 2.3f - 0.3f
                val decel = 1.25f
                val preRoll = 0.5f
                // windUp + accel + slide + decel
                val spinEnd = 3.79f
                val driftStart = windUp + accel + slide
                val turnRate = (turns * full + preRoll) / (accel / 2f + slide + decel / 4f)

                if (et < spinEnd + 1.7f) {
                    val cr = when {
                        et < windUp ->
                            -preRoll * (1f - cos(et / windUp * PI.toFloat())) / 2f

                        et < windUp + accel -> {
                            val ua = et - windUp
                            -preRoll + turnRate * ua * ua / (2f * accel)
                        }

                        et < driftStart ->
                            -preRoll + turnRate * (accel / 2f + (et - windUp - accel))

                        et < spinEnd -> {
                            val ua = (et - driftStart) / decel
                            -preRoll + turnRate * (accel / 2f + slide) +
                                turnRate * decel * (1f - (1f - ua).pow(4f)) / 4f
                        }

                        else -> turns * full
                    }
                    out.turn = cr * dir

                    var pl = 0f
                    if (et > driftStart) {
                        val ua = min((et - driftStart) / decel, 1f)
                        pl = if (ua < 0.4f) 0f else ((ua - 0.4f) / 0.6f).pow(2f)
                        if (et >= spinEnd) pl = (1f - (et - spinEnd) / 1.7f).pow(1.6f)
                    }
                    val yl = max(et - driftStart, 0f)
                    out.yr = cr / (turns * full) * 3f * 360f * dir
                    out.kr = sin(yl * 9.2f) * 11f * dir * pl
                    out.yi = (cos(yl * 9.2f) - 1f) * 6f * dir * pl
                    out.ki = sin(yl * 18.4f) * 2.6f * pl
                    out.zr = sin(yl * 11.5f) * 13f * dir * pl
                    out.wi = (cos(yl * 9f) - 1f) * 3.5f * pl
                    out.lidMul = 1.14f - 0.44f * pl + 0.1f * sin(yl * 16f) * pl
                    out.eyeBoost = 1.12f - 0.09f * pl
                } else {
                    out.done = true
                }
            }

            GrokTrickKind.SPIN_BOUNCE -> {
                if (et < 0.7f) {
                    out.turn = turns * full * dir * GrokMath.k2(et / 0.7f)
                } else {
                    out.wantHop = true
                    out.done = true
                }
            }
        }
    }

    /**
     * A spring driving the body towards `turns * 2pi * dir`.
     * The caller owns it and drops it once [spinTurnSettled] is true.
     */
    fun makeSpinTurn(turns: Float, dir: Float): GrokSpring {
        val s = GrokSpring(0f)
        s.t = turns * (2f * PI.toFloat()) * dir
        return s
    }

    /** True when the spin spring is close enough to its target to be released. */
    fun spinTurnSettled(s: GrokSpring): Boolean =
        abs(s.t - s.x) < 0.004f && abs(s.v) < 0.015f
}
