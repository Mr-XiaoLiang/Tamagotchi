package com.lollipop.grokbot.internal

import com.lollipop.grokbot.GrokMood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Per mood body motion: how the mark leans, bounces, squashes and how open the
 * eyes are. Direct port of the web `applyPose` switch.
 */
internal class GrokPoseResult {
    var spin = 0f
    var tx = 0f
    var ty = 0f
    var squash = 1f
    var lid = 1f
    var eyeBoost = 1f

    fun reset() {
        spin = 0f
        tx = 0f
        ty = 0f
        squash = 1f
        lid = 1f
        eyeBoost = 1f
    }
}

internal class GrokPoseExtra {
    var eyeTo = -1
    var eyeMorphX = 0f
    var blinkX = 1f
}

internal class GrokGaze(var x: Float = 0f, var y: Float = 0f, var holdMin: Float = 0f, var holdMax: Float = 0f) {

    fun set(x: Float, y: Float, holdMin: Float, holdMax: Float) {
        this.x = x
        this.y = y
        this.holdMin = holdMin
        this.holdMax = holdMax
    }
}

internal object GrokPose {

    fun apply(
        state: GrokMood,
        mt: Float,
        dtState: Float,
        now: Float,
        ctx: GrokCtx,
        extra: GrokPoseExtra,
        random: Random,
        out: GrokPoseResult,
    ) {
        out.reset()
        val pt = 0f
        var lid = 1f
        var eyeBoost = 1f
        var spin = pt
        var tx = 0f
        var ty = 0f
        var squash = 1f

        when (state) {
            GrokMood.SLEEPING -> {
                val en = min(dtState / 2f, 1f)
                val zt = sin(GrokMath.clamp(dtState / 0.5f, 0f, 1f) * PI.toFloat())
                spin = pt + 4f * en + sin(mt * 0.25f) * 2f
                tx = -2f * en
                ty = 8f * en + sin(mt * 0.55f) * 3f - zt * 5f
                squash = 1f + sin(mt * 0.55f) * 0.016f + zt * 0.05f
                val playlist = GrokTabs.EYE_PLAYLIST[GrokMood.SLEEPING]
                if (playlist != null && playlist.contains(extra.eyeTo)) {
                    lid = if (extra.eyeMorphX > 0.85f) 1f else 0.08f
                } else if (dtState < 1.2f) {
                    val dn = min(1f, dtState / 1f)
                    lid = max(0.08f, 1f - dn * (1f + 0.15f * sin(dtState * 6.5f)))
                } else {
                    lid = 0.08f
                    if (extra.blinkX < 0.18f) ctx.forceSleepEye = true
                }
            }

            GrokMood.WAKING -> {
                if (dtState < 0.5f) {
                    lid = 0.07f
                    ty = 6f
                    ctx.wakeEye = intArrayOf(3, 12)
                } else if (dtState < 1.2f) {
                    lid = 1f
                    eyeBoost = 1.12f
                    ty = -5f
                    tx = 0f
                    spin = pt
                    squash = 1.04f
                    if (!ctx.wakingBurst) ctx.wantBurst = floatArrayOf(GrokMath.rand(9f, 13f, random), 0.8f)
                } else if (dtState < 2.2f) {
                    ty = 0f
                    squash = 1f
                    ctx.wakeEye = intArrayOf(0, 7)
                    if (dtState < 1.4f) ctx.wakeBlink = true
                } else {
                    val et = min((dtState - 2.2f) / 0.8f, 1f)
                    ctx.wakeEye = intArrayOf(0, 7)
                    spin = pt + sin(et * PI.toFloat() * 3f) * 6f * (1f - et)
                    ty = sin(mt * 0.9f) * 2f
                }
            }

            GrokMood.IDLE -> {
                spin = pt + sin(mt * 0.5f) * 1.5f + sin(mt * 0.17f) * 0.6f
                tx = sin(mt * 0.27f) * 1f
                ty = sin(mt * 0.85f) * 1.2f
                squash = 1f + sin(mt * 0.85f) * 0.007f
            }

            GrokMood.LISTENING -> {
                spin = pt + 8f + sin(mt * 0.5f) * 1.5f
                tx = 2f
                ty = -2f + sin(mt * 0.8f) * 0.8f
                squash = 1.015f
                if (now >= ctx.nodUntil) {
                    ctx.nodUntil = now + GrokMath.rand(1800f, 3200f, random)
                    ctx.nodEnd = now + 380f
                }
                if (now < ctx.nodEnd) {
                    val et = 1f - (ctx.nodEnd - now) / 380f
                    ty += sin(et * PI.toFloat()) * 4.5f
                    spin += sin(et * PI.toFloat()) * 2f
                }
            }

            GrokMood.THINKING -> {
                spin = pt - 9f + sin(mt * 0.35f) * 5f
                tx = sin(mt * 0.3f) * 5f
                ty = sin(mt * 0.6f) * 2.5f
                squash = 1f
            }

            GrokMood.SEARCHING -> {
                val et = sin(mt * 1.3f)
                spin = pt + et * 13f
                tx = et * 7f
                ty = sin(mt * 1.7f) * 3f
                squash = 1f
                if (now >= ctx.stAt) {
                    ctx.wantPn = floatArrayOf(1f, GrokMath.sign(random))
                    ctx.stAt = now + GrokMath.rand(4000f, 7000f, random)
                }
            }

            GrokMood.WORKING -> {
                val et = sin(mt * PI.toFloat() * 2f * 1.6f)
                spin = pt + 4f + et * 2.5f
                tx = 3f
                ty = 1.5f + max(0f, et) * 3f
                squash = 1f - max(0f, et) * 0.02f
                if (now >= ctx.stAt) {
                    ctx.wantPn = floatArrayOf(1f, 1f)
                    ctx.stAt = now + GrokMath.rand(6000f, 9000f, random)
                }
            }

            GrokMood.EXCITED -> {
                val et = (mt * 2.2f) % 1f
                val en = sin(et * PI.toFloat())
                ty = -en * 10f + 2f
                squash = if (et < 0.1f) 0.92f else if (et < 0.3f) 1.05f else 1f
                tx = sin(mt * 1.1f) * 4f
                eyeBoost = 1.06f
                spin = pt + sin(mt * PI.toFloat() * 2f * 1.1f) * 7f
                if (now >= ctx.stAt) {
                    ctx.wantPn = floatArrayOf(1f, GrokMath.sign(random))
                    ctx.stAt = now + GrokMath.rand(2800f, 5000f, random)
                }
            }

            GrokMood.SURPRISED -> {
                val et = min(dtState / 1.2f, 1f)
                tx = -4f * (1f - et)
                ty = -8f * (1f - et)
                squash = if (dtState < 0.2f) 1.08f else 1f
                eyeBoost = 1.15f - et * 0.08f
                spin = pt + sin(mt * 11f) * 1.5f * (1f - et)
            }

            GrokMood.SUSPICIOUS -> {
                spin = pt - 6f + sin(mt * 0.3f) * 3f
                tx = sin(mt * 0.25f) * -4f
                ty = 1f + sin(mt * 0.45f) * 1.2f
                squash = 1f
                lid = 0.85f
                if (now >= ctx.impulseAt) {
                    ctx.spinKick = 30f
                    ctx.impulseAt = now + GrokMath.rand(4000f, 7000f, random)
                }
            }

            GrokMood.ANGRY -> {
                if (now >= ctx.impulseAt) {
                    ctx.angryShakeUntil = now + 420f
                    ctx.tyKick = 70f
                    ctx.impulseAt = now + GrokMath.rand(1800f, 3200f, random)
                }
                spin = pt + if (now < ctx.angryShakeUntil) sin(now * 0.05f) * 4.5f else 0f
                tx = 0f
                ty = 3.5f
                squash = 0.975f
            }

            GrokMood.DROWSY -> {
                spin = pt + sin(mt * 0.32f) * 2.5f
                tx = sin(mt * 0.2f) * 1.5f
                ty = 6f + sin(mt * 0.36f) * 2.2f
                squash = 1f + sin(mt * 0.36f) * 0.022f
                lid = 0.34f + sin(mt * 0.8f) * 0.07f
                if (now >= ctx.nodUntil && ctx.slumpAt == 0f) ctx.slumpAt = now
                if (ctx.slumpAt != 0f) {
                    val en = (now - ctx.slumpAt) / 1000f
                    val zt = 1.7f
                    val dn = 0.3f
                    val on = 1.5f
                    if (en < zt) {
                        val bn = en / zt
                        val cn = bn * bn
                        val bi = sin(bn * PI.toFloat() * 2.5f) * 2.2f * (1f - bn)
                        ty = 6f + cn * 19f + bi
                        spin = pt + cn * 10f
                        lid = 0.34f - cn * (0.34f - 0.04f)
                        squash = 1f - cn * 0.045f
                    } else if (en < zt + dn) {
                        val bn = (en - zt) / dn
                        val cn = sin(bn * PI.toFloat())
                        ty = 25f - cn * 7f
                        spin = pt + 10f - cn * 4f
                        lid = 0.04f + cn * 0.42f
                    } else if (en < zt + dn + on) {
                        val bn = (en - zt - dn) / on
                        val cn = 1f - (1f - bn).pow(2.2f)
                        ty = 25f - 19f * cn
                        spin = pt + 10f * (1f - cn)
                        lid = 0.46f + (0.34f - 0.46f) * cn
                        if (bn > 0.32f && bn < 0.46f) lid = 0.05f
                    } else {
                        ctx.slumpAt = 0f
                        ctx.nodUntil = now + GrokMath.rand(1500f, 3500f, random)
                    }
                }
            }

            GrokMood.HAPPY -> {
                val et = sin(mt * 2.4f)
                spin = pt + sin(mt * 1.2f) * 3f
                tx = sin(mt * 1.1f) * 2.5f
                ty = -abs(et) * 3f
                squash = 1f + et * 0.02f
                eyeBoost = 1.05f
            }

            GrokMood.CURIOUS -> {
                spin = pt + 10f + sin(mt * 0.7f) * 6f
                tx = sin(mt * 0.6f) * 5f
                ty = -2f + sin(mt * 0.9f) * 1.5f
                squash = 1.01f
                eyeBoost = 1.08f
                if (now >= ctx.nodUntil) {
                    ctx.nodUntil = now + GrokMath.rand(1600f, 2800f, random)
                    ctx.nodEnd = now + 440f
                }
                if (now < ctx.nodEnd) {
                    val et = 1f - (ctx.nodEnd - now) / 440f
                    tx += sin(et * PI.toFloat()) * 8f
                    spin += sin(et * PI.toFloat()) * 5f
                }
            }

            GrokMood.CONFUSED -> {
                val et = sin(mt * 0.8f)
                spin = pt + et * 12f
                tx = et * 3f
                ty = sin(mt * 0.5f) * 2f
                squash = 1f
                lid = 0.9f
                if (now >= ctx.impulseAt) {
                    ctx.spinKick = 22f
                    ctx.impulseAt = now + GrokMath.rand(2600f, 4200f, random)
                }
            }

            GrokMood.BORED -> {
                spin = pt - 3f + sin(mt * 0.25f) * 4f
                tx = sin(mt * 0.2f) * 4f
                ty = 5f + sin(mt * 0.35f) * 1.5f
                squash = 0.99f
                lid = 0.6f
                eyeBoost = 0.98f
                if (now >= ctx.impulseAt) {
                    ctx.nodEnd = now + 600f
                    ctx.impulseAt = now + GrokMath.rand(4000f, 7000f, random)
                }
                if (now < ctx.nodEnd) {
                    val et = 1f - (ctx.nodEnd - now) / 600f
                    squash = 1f + sin(et * PI.toFloat()) * 0.05f
                    ty += sin(et * PI.toFloat()) * 3f
                }
            }

            GrokMood.PROUD -> {
                spin = pt + sin(mt * 0.4f) * 2.5f
                tx = sin(mt * 0.35f) * 2f
                ty = -4f + sin(mt * 0.6f)
                squash = 1.03f
                eyeBoost = 1.02f
                lid = 0.9f
            }

            GrokMood.SHY -> {
                spin = pt - 8f + sin(mt * 0.5f) * 3f
                tx = -3f + sin(mt * 0.4f) * 2f
                ty = 3f
                squash = 0.98f
                eyeBoost = 0.95f
                lid = 0.85f
            }

            GrokMood.SAD -> {
                spin = pt + 3f + sin(mt * 0.3f) * 2f
                tx = sin(mt * 0.25f) * 1.5f
                ty = 7f + sin(mt * 0.4f)
                squash = 0.97f
                lid = 0.7f
                eyeBoost = 0.97f
            }

            GrokMood.LAUGHING -> {
                val et = sin(mt * PI.toFloat() * 2f * 3.2f)
                spin = pt + et * 4f
                tx = sin(mt * 2f) * 2f
                ty = -abs(et) * 5f
                squash = 1f + et * 0.03f
                lid = 0.7f
            }

            GrokMood.SCARED -> {
                spin = pt + sin(now * 0.04f) * 2f
                tx = -2f + sin(now * 0.05f) * 1.5f
                ty = 2f + sin(mt * 1.5f)
                squash = 0.97f
                eyeBoost = 1.12f
                lid = 1.05f
            }

            GrokMood.PLAYFUL -> {
                spin = pt + sin(mt * 1.4f) * 8f
                tx = sin(mt * 1.1f) * 4f
                ty = -abs(sin(mt * 2.2f)) * 3f
                squash = 1f + sin(mt * 2.2f) * 0.015f
                eyeBoost = 1.06f
                if (now >= ctx.stAt) {
                    ctx.wantPn = floatArrayOf(1f, GrokMath.sign(random))
                    ctx.stAt = now + GrokMath.rand(3500f, 6000f, random)
                }
            }

            GrokMood.CELEBRATE -> {
                spin = pt
                tx = 0f
                ty = -abs(sin(mt * 1.6f)) * 2.5f
                squash = 1f
                eyeBoost = 1.1f
                lid = 1.1f
            }

            GrokMood.DRAGGING -> {
                val en = (dtState % 3.4f) / 3.4f
                if (en < 0.12f) {
                    tx = -16f
                    ty = -22f
                    spin = pt - 5f
                } else if (en < 0.62f) {
                    val dn = (en - 0.12f) / 0.5f
                    tx = -16f + 32f * GrokMath.k2(dn)
                    ty = -22f + sin(mt * 1.4f) * 2f
                    spin = pt + sin(mt * 2.6f) * 6f
                    eyeBoost = 1.06f
                } else {
                    val cycle = floor(dtState / 3.4f).toInt()
                    if (cycle != ctx.dragCycle) {
                        ctx.dragCycle = cycle
                        ctx.tyKick = 90f
                    }
                    tx = 16f
                    ty = 0f
                    spin = pt
                }
                squash = 1f
            }

            GrokMood.HUMMING -> {
                spin = pt + sin(mt * 0.4f) * 2f
                tx = sin(mt * 0.3f) * 1.5f
                ty = sin(mt * 0.7f) * 1.5f
                squash = 1f
            }

            GrokMood.NOTIFYING -> {
                if (!ctx.notifyPop && dtState > 0.12f) {
                    ctx.notifyPop = true
                    ctx.tyKick = -26f
                    ctx.wantBlink = true
                }
                eyeBoost = 1f + 0.05f * exp(-dtState * 3f)
                spin = pt + 3f
                tx = 2f
                ty = -1f
                squash = 1f
            }

            else -> {
                spin = pt
                tx = 0f
                ty = 0f
                squash = 1f
            }
        }

        out.spin = spin
        out.tx = tx
        out.ty = ty
        out.squash = squash
        out.lid = lid
        out.eyeBoost = eyeBoost
    }

    fun nextGaze(state: GrokMood, random: Random, out: GrokGaze) {
        when (state) {
            GrokMood.IDLE -> out.set(0f, 0f, 2500f, 5500f)
            GrokMood.LISTENING -> out.set(
                GrokMath.rand(-0.3f, 0.3f, random) * 15f,
                GrokMath.rand(-0.25f, 0.25f, random) * 9f, 2200f, 4200f,
            )

            GrokMood.THINKING -> out.set(
                GrokMath.sign(random) * GrokMath.rand(0.5f, 1f, random) * 15f,
                -GrokMath.rand(0.4f, 1f, random) * 9f, 1500f, 2800f,
            )

            GrokMood.SEARCHING -> out.set(
                GrokMath.sign(random) * GrokMath.rand(0.7f, 1f, random) * 15f,
                GrokMath.rand(-1f, 1f, random) * 9f, 550f, 1150f,
            )

            GrokMood.WORKING -> out.set(
                GrokMath.rand(-0.4f, 0.4f, random) * 15f,
                GrokMath.rand(0.4f, 1f, random) * 9f, 1200f, 2400f,
            )

            GrokMood.EXCITED -> out.set(
                GrokMath.rand(-1f, 1f, random) * 15f,
                GrokMath.rand(-1f, 0.3f, random) * 9f, 700f, 1400f,
            )

            GrokMood.SURPRISED -> out.set(0f, 0f, 1600f, 2600f)
            GrokMood.SUSPICIOUS -> out.set(GrokMath.sign(random) * 15f, 0.3f * 9f, 2200f, 4200f)
            GrokMood.ANGRY -> out.set(
                GrokMath.rand(-0.2f, 0.2f, random) * 15f, 0.2f * 9f, 1800f, 3200f,
            )

            GrokMood.DROWSY -> out.set(
                GrokMath.rand(-0.4f, 0.4f, random) * 15f,
                GrokMath.rand(0.4f, 1f, random) * 9f, 2500f, 4500f,
            )

            GrokMood.HAPPY -> out.set(
                GrokMath.rand(-0.7f, 0.7f, random) * 15f,
                -GrokMath.rand(0f, 0.6f, random) * 9f, 1800f, 3400f,
            )

            GrokMood.CURIOUS -> out.set(
                GrokMath.sign(random) * GrokMath.rand(0.6f, 1f, random) * 15f,
                GrokMath.rand(-1f, 1f, random) * 9f, 950f, 1900f,
            )

            GrokMood.CONFUSED -> out.set(
                GrokMath.sign(random) * GrokMath.rand(0.5f, 1f, random) * 15f,
                GrokMath.rand(-0.6f, 1f, random) * 9f, 1100f, 2300f,
            )

            GrokMood.BORED -> out.set(
                GrokMath.sign(random) * GrokMath.rand(0.7f, 1f, random) * 15f,
                GrokMath.rand(0.4f, 0.9f, random) * 9f, 3000f, 6000f,
            )

            GrokMood.PROUD -> out.set(
                GrokMath.rand(-0.3f, 0.3f, random) * 15f,
                -GrokMath.rand(0.3f, 0.7f, random) * 9f, 2600f, 4600f,
            )

            GrokMood.SHY -> out.set(
                GrokMath.sign(random) * GrokMath.rand(0.6f, 1f, random) * 15f,
                GrokMath.rand(0.5f, 1f, random) * 9f, 2000f, 4000f,
            )

            GrokMood.SAD -> out.set(
                GrokMath.rand(-0.3f, 0.3f, random) * 15f,
                GrokMath.rand(0.6f, 1f, random) * 9f, 2800f, 5000f,
            )

            GrokMood.LAUGHING -> out.set(
                GrokMath.rand(-0.5f, 0.5f, random) * 15f,
                -GrokMath.rand(0.2f, 0.6f, random) * 9f, 800f, 1700f,
            )

            GrokMood.SCARED -> out.set(
                GrokMath.sign(random) * GrokMath.rand(0.7f, 1f, random) * 15f,
                GrokMath.rand(-0.6f, 0.6f, random) * 9f, 450f, 1050f,
            )

            GrokMood.PLAYFUL -> out.set(
                GrokMath.sign(random) * GrokMath.rand(0.5f, 1f, random) * 15f,
                -GrokMath.rand(0f, 0.6f, random) * 9f, 900f, 1800f,
            )

            GrokMood.NOTIFYING -> {
                val look = random.nextFloat() < 0.72f
                out.set(
                    (if (look) 0.45f else 0.1f) * 15f,
                    -(if (look) 0.3f else 0.05f) * 9f, 1200f, 2400f,
                )
            }

            else -> out.set(
                GrokMath.rand(-0.4f, 0.4f, random) * 15f,
                GrokMath.rand(-0.3f, 0.3f, random) * 9f, 2500f, 5000f,
            )
        }
    }
}
