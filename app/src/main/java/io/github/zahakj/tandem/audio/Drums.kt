package io.github.zahakj.tandem.audio

import kotlin.math.exp

/** Drum sounds. Patterns in songs refer to these by name. */
enum class Drum { Kick, Snare, Clap, HatClosed, HatOpen, Shaker, Rim, TomLow, TomHigh, Crash, Wood, Riser }

/** How a kit sounds overall. */
enum class KitStyle {
    /** Soft and dusty: round kick, low-passed snare, quiet hats (lo-fi). */
    LoFi,
    /** Light percussion for gentle songs: woodblock, shaker, soft taiko-like toms. */
    Light,
    /** Old sound-chip drums: triangle kick, short noise snare and hats. */
    Chip,
    /** Big 80s drums: punchy kick, snare with a long gated tail. */
    Retro,
    /** Bright and bouncy pop kit. */
    Pop,
    /**
     * Orchestral and brushes: felt concert bass drum (Kick), brush taps (Snare), brush swishes (HatClosed short,
     * HatOpen long), suspended cymbal with soft mallets (Crash) and its rolled swell (Riser), triangle (Wood).
     */
    Orchestra,
}

/** A drum kit: a style, overall level, and per-drum level trims. */
data class Kit(val style: KitStyle, val gain: Float = 0.5f, val reverb: Float = 0.15f, val trims: Map<Drum, Float> = emptyMap()) {
    internal val trimArray = FloatArray(Drum.entries.size) { trims[Drum.entries[it]] ?: 1f }
}

/** One playing drum hit. Drum voices are pooled and reused. */
internal class DrumVoice(private val rate: Int) {
    var active = false
    var drum = Drum.Kick
    var kit: Kit? = null
    private var t = 0
    var delay = 0
    private var vel = 1f
    private var lenSamples = 0
    private var phase = 0.0
    private var phase2 = 0.0
    private val noise = Noise(0x2468ACE)
    private val f1 = Svf()
    private val f2 = Svf()
    private var hp = OnePole()
    private var lfsr = 1
    private var lfsrOut = 1f
    private var lfsrCount = 0.0
    private var choke = 1f
    private var choking = false
    private var pan = 0f
    private var kf0 = 50.0
    private var kf1 = 150.0
    private var kpt = 0.03
    private var kdec = 0.3
    private var maxT = 0
    /** Set when this hit should duck other instruments (kicks). */
    var ducks = false

    fun start(d: Drum, k: Kit, v: Float, delaySamples: Int, lengthSamples: Int) {
        active = true; drum = d; kit = k; vel = v; delay = delaySamples; t = 0; phase = 0.0; phase2 = 0.0
        lenSamples = lengthSamples; choke = 1f; choking = false
        maxT = if (k.style != KitStyle.Orchestra) rate * 3 else when (d) {
            Drum.Snare, Drum.HatClosed, Drum.Shaker, Drum.Rim -> rate
            Drum.Kick, Drum.TomLow, Drum.TomHigh, Drum.HatOpen -> rate * 4
            Drum.Riser -> lengthSamples + rate * 5
            else -> rate * 8
        }
        f1.reset(); f2.reset(); hp.z = 0f
        ducks = d == Drum.Kick
        pan = when (d) {
            Drum.HatClosed, Drum.HatOpen -> 0.25f; Drum.Shaker -> -0.3f; Drum.Rim, Drum.Wood -> -0.2f
            Drum.TomHigh -> 0.3f; Drum.TomLow -> -0.25f; else -> 0f
        }
        val chip = k.style == KitStyle.Chip
        when (k.style) {
            KitStyle.LoFi -> { kf0 = 48.0; kf1 = 150.0; kpt = 0.03; kdec = 0.28 }
            KitStyle.Retro -> { kf0 = 46.0; kf1 = 190.0; kpt = 0.035; kdec = 0.42 }
            KitStyle.Chip -> { kf0 = 55.0; kf1 = 260.0; kpt = 0.025; kdec = 0.14 }
            KitStyle.Light -> { kf0 = 58.0; kf1 = 120.0; kpt = 0.04; kdec = 0.3 }
            KitStyle.Pop -> { kf0 = 52.0; kf1 = 170.0; kpt = 0.03; kdec = 0.3 }
            KitStyle.Orchestra -> { kf0 = 50.0; kf1 = 68.0; kpt = 0.08; kdec = 0.8 }
        }
        if (k.style == KitStyle.Orchestra) {
            when (d) {
                Drum.Snare -> { f1.set(4200.0, 0.7, rate); hp.set(700.0, rate) }
                Drum.HatClosed, Drum.HatOpen -> { f1.set(5200.0, 0.6, rate); hp.set(1400.0, rate) }
                Drum.Crash, Drum.Riser -> { f1.set(5000.0, 0.6, rate); hp.set(1600.0, rate) }
                Drum.Shaker -> { f1.set(5000.0, 1.2, rate); hp.set(2500.0, rate) }
                else -> {}
            }
            return
        }
        when (d) {
            Drum.Snare -> { f1.set(if (k.style == KitStyle.LoFi) 2600.0 else 4200.0, 0.8, rate); hp.set(400.0, rate) }
            Drum.Clap -> { f1.set(1300.0, 1.6, rate) }
            Drum.HatClosed, Drum.HatOpen -> { f1.set(if (chip) 9000.0 else 8500.0, 1.0, rate); hp.set(6000.0, rate) }
            Drum.Shaker -> { f1.set(6500.0, 1.4, rate); hp.set(3000.0, rate) }
            Drum.Crash -> { f1.set(7000.0, 0.7, rate); hp.set(3500.0, rate) }
            Drum.Rim -> { f1.set(1900.0, 3.0, rate) }
            else -> {}
        }
    }

    /** Closed hat cuts an open hat short, like a real hi-hat. */
    fun choke() { choking = true }

    /** Adds this hit into the buses for [n] samples starting at [off]. */
    fun render(n: Int, dryL: FloatArray, dryR: FloatArray, revL: FloatArray, revR: FloatArray) {
        val k = kit ?: return
        val g = k.gain * k.trimArray[drum.ordinal] * vel
        val gl = g * (1f - pan) * 0.5f * 1.414f
        val gr = g * (1f + pan) * 0.5f * 1.414f
        val send = k.reverb * (if (drum == Drum.Snare && k.style == KitStyle.Retro) 2.2f else 1f)
        val chokeCoef = coef(0.012, rate)
        var i = 0
        if (delay > 0) { val skip = minOf(delay, n); delay -= skip; i = skip }
        while (i < n) {
            val s = sample(k.style)
            if (choking) choke += (0f - choke) * chokeCoef
            val v = s * choke
            dryL[i] += v * gl; dryR[i] += v * gr
            revL[i] += v * gl * send; revR[i] += v * gr * send
            t++
            i++
            if (t > maxT || (choking && choke < 1e-4f)) { active = false; return }
        }
    }

    private fun env(tau: Double): Float = exp(-t / (tau * rate)).toFloat()

    private fun chipNoise(hz: Double, short: Boolean): Float {
        lfsrCount += hz / rate
        while (lfsrCount >= 1) {
            lfsrCount -= 1
            val bit = (lfsr xor (lfsr shr if (short) 6 else 1)) and 1
            lfsr = (lfsr shr 1) or (bit shl 14)
            lfsrOut = if (lfsr and 1 == 1) 1f else -1f
        }
        return lfsrOut
    }

    /** Swell from 0 to 1 over [attack] seconds, then decay with time constant [tau]. */
    private fun swell(sec: Double, attack: Double, tau: Double): Float =
        if (sec < attack) (sec / attack).toFloat().let { it * it } else exp(-(sec - attack) / tau).toFloat()

    private fun orchestra(sec: Double): Float = when (drum) {
        Drum.Kick -> {
            val f = kf0 + (kf1 - kf0) * exp(-sec / kpt)
            phase += f / rate; if (phase >= 1) phase -= 1
            phase2 += f * 1.47 / rate; if (phase2 >= 1) phase2 -= 1
            val a = if (t < 150) t / 150f else 1f
            (Wave.sin1(phase) * env(kdec) + Wave.sin1(phase2) * 0.25f * env(0.25)) * a
        }
        Drum.Snare -> hp.hp(f1.lp(noise.next())) * (swell(sec, 0.002, 0.045) * 0.8f + swell(sec, 0.03, 0.12) * 0.2f) * 0.9f
        Drum.HatClosed -> hp.hp(f1.lp(noise.next())) * swell(sec, 0.035, 0.07) * 0.5f
        Drum.HatOpen -> hp.hp(f1.lp(noise.next())) * swell(sec, 0.16, 0.22) * 0.5f
        Drum.Shaker -> hp.hp(f1.bp(noise.next())) * swell(sec, 0.015, 0.05) * 0.6f
        Drum.Crash -> hp.hp(f1.lp(noise.next())) * swell(sec, 0.012, 1.6) * 0.28f
        Drum.Riser -> {
            // A suspended cymbal rolled with soft mallets, swelling into the next downbeat and ringing on past it.
            val len = lenSamples.coerceAtLeast(1)
            val a = if (t < len) (t.toDouble() / len).let { it * it * it }.toFloat() else exp(-(t - len).toDouble() / (rate * 0.7)).toFloat()
            if (t > len + rate * 9 / 2) { active = false; 0f } else hp.hp(f1.lp(noise.next())) * a * 0.2f
        }
        Drum.Wood -> {
            // Triangle: a few high inharmonic partials with a long ring.
            phase += 1870.0 / rate; if (phase >= 1) phase -= 1
            phase2 += 4710.0 / rate; if (phase2 >= 1) phase2 -= 1
            val a = if (t < 24) t / 24f else 1f
            (Wave.sin1(phase) * env(0.9) * 0.5f + Wave.sin1(phase2) * env(0.3) * 0.25f + noise.next() * env(0.002) * 0.2f) * a * 0.35f
        }
        else -> sampleOf(KitStyle.Light, sec)
    }

    private fun sample(style: KitStyle): Float {
        val sec = t.toDouble() / rate
        return if (style == KitStyle.Orchestra) orchestra(sec) else sampleOf(style, sec)
    }

    private fun sampleOf(style: KitStyle, sec: Double): Float {
        return when (drum) {
            Drum.Kick -> {
                val f = kf0 + (kf1 - kf0) * exp(-sec / kpt)
                phase += f / rate; if (phase >= 1) phase -= 1
                val body = if (style == KitStyle.Chip) {
                    val tr = Wave.tri(phase).toFloat(); (kotlin.math.floor(tr * 7.5f) / 7.5f)
                } else Wave.sin1(phase)
                val click = if (t < rate / 200) noise.next() * 0.25f * (1f - t / (rate / 200f)) else 0f
                val a = if (t < 48) t / 48f else 1f
                (body * env(kdec) * a + click * if (style == KitStyle.LoFi) 0.3f else 1f) * 0.9f
            }
            Drum.Snare -> {
                val chip = style == KitStyle.Chip
                val nz = if (chip) chipNoise(9000.0, false) else noise.next()
                phase += 185.0 / rate; if (phase >= 1) phase -= 1
                phase2 += 330.0 / rate; if (phase2 >= 1) phase2 -= 1
                val tone = (Wave.sin1(phase) * 0.6f + Wave.sin1(phase2) * 0.4f) * env(0.05)
                val nEnv = when (style) {
                    KitStyle.Retro -> if (sec < 0.22) env(0.12) else env(0.12) * exp(-(sec - 0.22) / 0.01).toFloat()
                    KitStyle.LoFi -> env(0.11)
                    KitStyle.Chip -> env(0.07)
                    else -> env(0.13)
                }
                val shaped = if (chip) nz else hp.hp(f1.lp(nz))
                val a = if (t < 24) t / 24f else 1f
                (tone * (if (chip) 0.3f else 0.55f) + shaped * nEnv * 0.75f) * a * 0.75f
            }
            Drum.Clap -> {
                // Three fast bursts and a tail: the "smack" of hands.
                val burst = when {
                    sec < 0.01 -> exp(-sec / 0.003)
                    sec < 0.02 -> exp(-(sec - 0.01) / 0.003)
                    sec < 0.03 -> exp(-(sec - 0.02) / 0.003)
                    else -> exp(-(sec - 0.03) / 0.09)
                }.toFloat()
                f1.bp(noise.next()) * burst * 1.4f
            }
            Drum.HatClosed, Drum.HatOpen -> {
                val nz = if (style == KitStyle.Chip) chipNoise(28000.0, true) else noise.next()
                val tau = if (drum == Drum.HatOpen) 0.22 else if (style == KitStyle.LoFi) 0.03 else 0.04
                val a = if (t < 16) t / 16f else 1f
                hp.hp(f1.lp(nz)) * env(tau) * a * 0.55f
            }
            Drum.Shaker -> {
                val a = if (sec < 0.012) (sec / 0.012).toFloat() else env(0.05) * exp(0.012 / 0.05).toFloat()
                hp.hp(f1.bp(noise.next())) * a * 0.7f
            }
            Drum.Rim -> {
                phase += 1700.0 / rate; if (phase >= 1) phase -= 1
                (Wave.sin1(phase) * 0.5f + f1.bp(noise.next()) * 0.6f) * env(0.012)
            }
            Drum.TomLow, Drum.TomHigh -> {
                val base = if (drum == Drum.TomLow) (if (style == KitStyle.Light) 85.0 else 100.0) else 160.0
                val f = base * (1 + 0.5 * exp(-sec / 0.03))
                phase += f / rate; if (phase >= 1) phase -= 1
                val a = if (t < 32) t / 32f else 1f
                val body = if (style == KitStyle.Chip) Wave.tri(phase).toFloat() else Wave.sin1(phase)
                (body * env(if (style == KitStyle.Light) 0.35 else 0.22) + noise.next() * 0.08f * env(0.02)) * a * 0.8f
            }
            Drum.Crash -> {
                val nz = if (style == KitStyle.Chip) chipNoise(20000.0, false) else noise.next()
                val a = if (t < 64) t / 64f else 1f
                hp.hp(f1.lp(nz)) * env(if (style == KitStyle.Chip) 0.35 else 0.9) * a * 0.45f
            }
            Drum.Wood -> {
                phase += 920.0 / rate; if (phase >= 1) phase -= 1
                phase2 += 2650.0 / rate; if (phase2 >= 1) phase2 -= 1
                val a = if (t < 16) t / 16f else 1f
                (Wave.sin1(phase) * env(0.035) + Wave.sin1(phase2) * 0.3f * env(0.01)) * a * 0.6f
            }
            Drum.Riser -> {
                // Filtered noise sweeping up over the event's length, swelling in: a transition "whoosh".
                val p = (t.toDouble() / lenSamples.coerceAtLeast(1)).coerceIn(0.0, 1.0)
                if (t % 32 == 0) f2.set(300.0 * Math.pow(30.0, p), 2.5, rate)
                val amp = (p * p).toFloat()
                if (t >= lenSamples) { active = false; 0f } else f2.bp(noise.next()) * amp * 0.5f
            }
        }
    }

}
