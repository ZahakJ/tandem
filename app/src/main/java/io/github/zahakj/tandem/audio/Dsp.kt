package io.github.zahakj.tandem.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/*
 * Small DSP building blocks for Tandem's synthesizer. Pure Kotlin with no Android imports, so the same code renders
 * music on the phone and in a plain JVM program (used to check levels while composing). Everything here works on
 * preallocated state: nothing allocates while audio is running.
 */

internal object Wave {
    private const val SIZE = 4096
    private val table = FloatArray(SIZE + 1) { sin(2 * PI * it / SIZE).toFloat() }

    /** sin(2π·[phase]) for a phase in [0, 1), from a table with linear interpolation. */
    fun sin1(phase: Double): Float {
        val x = phase * SIZE
        val i = x.toInt() and (SIZE - 1)
        val f = (x - x.toInt()).toFloat()
        val a = table[i]
        return a + (table[i + 1] - a) * f
    }

    /** sin of any phase (wraps). */
    fun sinAny(phase: Double): Float {
        var x = phase - phase.toLong()
        if (x < 0) x += 1.0
        return sin1(x)
    }

    /** PolyBLEP correction for a step at phase 0, with phase increment [dt]. */
    fun blep(t: Double, dt: Double): Double = when {
        t < dt -> { val x = t / dt; x + x - x * x - 1.0 }
        t > 1.0 - dt -> { val x = (t - 1.0) / dt; x * x + x + x + 1.0 }
        else -> 0.0
    }

    fun saw(t: Double, dt: Double): Double = 2.0 * t - 1.0 - blep(t, dt)

    fun pulse(t: Double, dt: Double, pw: Double): Double {
        var v = if (t < pw) 1.0 else -1.0
        v += blep(t, dt)
        var t2 = t - pw
        if (t2 < 0) t2 += 1.0
        v -= blep(t2, dt)
        // Keep the average at zero whatever the width, so narrow pulses don't add DC.
        return v - (2 * pw - 1)
    }

    fun tri(t: Double): Double = if (t < 0.5) 4 * t - 1 else 3 - 4 * t

    fun midiToHz(m: Double): Double = 440.0 * 2.0.pow((m - 69) / 12.0)
}

/** Fast xorshift noise; [next] returns -1..1. */
internal class Noise(seed: Int = 0x1234567) {
    private var s = if (seed == 0) 1 else seed
    fun next(): Float {
        s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5)
        return (s and 0xFFFFFF) / 8388608f - 1f
    }
    fun unit(): Float = (next() + 1f) * 0.5f
}

/** Zavalishin/Simper state-variable filter. Call [set] at control rate, then [lp]/[bp]/[hp] per sample. */
internal class Svf {
    private var ic1 = 0f
    private var ic2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var a3 = 0f
    private var k = 1f
    var low = 0f; private set
    var band = 0f; private set
    var high = 0f; private set

    fun set(cutoff: Double, q: Double, rate: Int) {
        val fc = cutoff.coerceIn(20.0, rate * 0.45)
        val g = tan(PI * fc / rate)
        k = (1.0 / q.coerceAtLeast(0.3)).toFloat()
        a1 = (1.0 / (1.0 + g * (g + k))).toFloat()
        a2 = (g * a1).toFloat()
        a3 = (g * a2).toFloat()
    }

    fun tick(v0: Float) {
        val v3 = v0 - ic2
        val v1 = a1 * ic1 + a2 * v3
        val v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        low = v2; band = v1; high = v0 - k * v1 - v2
    }

    fun lp(v: Float): Float { tick(v); return low }
    fun bp(v: Float): Float { tick(v); return band }
    fun hp(v: Float): Float { tick(v); return high }
    fun reset() { ic1 = 0f; ic2 = 0f }
}

/** One-pole low-pass. */
internal class OnePole {
    var a = 1f
    var z = 0f
    fun set(cutoff: Double, rate: Int) { a = (1 - exp(-2 * PI * cutoff / rate)).toFloat() }
    fun lp(x: Float): Float { z += a * (x - z); return z }
    fun hp(x: Float): Float { z += a * (x - z); return x - z }
}

/** Time constant helper: per-sample coefficient reaching ~63 % of the way in [seconds]. */
internal fun coef(seconds: Double, rate: Int): Float =
    if (seconds <= 0.0) 1f else (1 - exp(-1.0 / (seconds * rate))).toFloat()

/** A light stereo reverb: input diffusion into a 4-line feedback delay network with damping. */
internal class Reverb(private val rate: Int) {
    private val baseLens = intArrayOf(1601, 1867, 2053, 2251)
    private val lines = Array(4) { FloatArray(6000) }
    private val lens = IntArray(4)
    private val pos = IntArray(4)
    private val damp = FloatArray(4)
    private var fb = 0.8f
    private var dampA = 0.3f
    private val diffL = Allpass(rate * 7 / 1000 + 1, 0.6f)
    private val diffR = Allpass(rate * 9 / 1000 + 3, 0.6f)
    private val diff2L = Allpass(rate * 3 / 1000 + 5, 0.5f)
    private val diff2R = Allpass(rate * 4 / 1000 + 1, 0.5f)
    var outL = 0f; private set
    var outR = 0f; private set

    init { configure(0.75, 0.4) }

    /** [size] 0..1 (room to hall), [damping] 0..1 (bright to dark). */
    fun configure(size: Double, damping: Double) {
        val scale = rate / 48000.0 * (0.6 + size * 1.6)
        for (i in 0 until 4) lens[i] = (baseLens[i] * scale).toInt().coerceIn(100, 5999)
        // Decay time grows with size (about 0.8 s to 3 s).
        val rt60 = 0.8 + size * 2.4
        val meanLen = lens.average() / rate
        fb = 10.0.pow(-3.0 * meanLen / rt60).toFloat().coerceIn(0f, 0.97f)
        dampA = (0.15 + (1 - damping) * 0.75).toFloat()
    }

    fun process(inL: Float, inR: Float) {
        val a = diff2L.process(diffL.process(inL))
        val b = diff2R.process(diffR.process(inR))
        val d0 = lines[0][pos[0]]; val d1 = lines[1][pos[1]]; val d2 = lines[2][pos[2]]; val d3 = lines[3][pos[3]]
        // Householder mixing keeps the network lossless before the feedback gain.
        val sum = (d0 + d1 + d2 + d3) * 0.5f
        var m0 = d0 - sum; var m1 = d1 - sum; var m2 = d2 - sum; var m3 = d3 - sum
        damp[0] += dampA * (m0 - damp[0]); m0 = damp[0]
        damp[1] += dampA * (m1 - damp[1]); m1 = damp[1]
        damp[2] += dampA * (m2 - damp[2]); m2 = damp[2]
        damp[3] += dampA * (m3 - damp[3]); m3 = damp[3]
        lines[0][pos[0]] = a + m0 * fb
        lines[1][pos[1]] = b + m1 * fb
        lines[2][pos[2]] = a + m2 * fb
        lines[3][pos[3]] = b + m3 * fb
        for (i in 0 until 4) { pos[i]++; if (pos[i] >= lens[i]) pos[i] = 0 }
        outL = (d0 + d2) * 0.5f
        outR = (d1 + d3) * 0.5f
    }

    fun clear() { for (l in lines) l.fill(0f); damp.fill(0f); diffL.clear(); diffR.clear(); diff2L.clear(); diff2R.clear() }
}

internal class Allpass(len: Int, private val g: Float) {
    private val buf = FloatArray(len.coerceAtLeast(1))
    private var p = 0
    fun process(x: Float): Float {
        val d = buf[p]
        val y = -g * x + d
        buf[p] = x + g * y
        if (++p >= buf.size) p = 0
        return y
    }
    fun clear() = buf.fill(0f)
}

/** A gentle stereo chorus: one modulated delay per side, LFOs a quarter turn apart. */
internal class Chorus(private val rate: Int) {
    private val size = 4096
    private val bufL = FloatArray(size)
    private val bufR = FloatArray(size)
    private var w = 0
    private var lfo = 0.0
    var rateHz = 0.35
    var depthMs = 2.2
    var baseMs = 11.0
    var outL = 0f; private set
    var outR = 0f; private set

    fun process(inL: Float, inR: Float) {
        bufL[w] = inL; bufR[w] = inR
        lfo += rateHz / rate; if (lfo >= 1) lfo -= 1
        val dl = (baseMs + depthMs * Wave.sin1(lfo)) * rate / 1000.0
        val dr = (baseMs + depthMs * Wave.sinAny(lfo + 0.25)) * rate / 1000.0
        outL = read(bufL, dl); outR = read(bufR, dr)
        w = (w + 1) and (size - 1)
    }

    private fun read(b: FloatArray, delay: Double): Float {
        val r = w - delay
        val ri = kotlin.math.floor(r).toInt()
        val f = (r - ri).toFloat()
        val i0 = ri and (size - 1)
        val i1 = (ri + 1) and (size - 1)
        return b[i0] + (b[i1] - b[i0]) * f
    }

    fun clear() { bufL.fill(0f); bufR.fill(0f) }
}

/** Tempo-synced ping-pong echo with a darkening feedback loop. */
internal class Echo(rate: Int) {
    private val size = Integer.highestOneBit(rate * 2) * 2
    private val bufL = FloatArray(size)
    private val bufR = FloatArray(size)
    private var w = 0
    var delaySamples = rate / 4
    var feedback = 0.35f
    private val toneL = OnePole().apply { set(3200.0, rate) }
    private val toneR = OnePole().apply { set(3200.0, rate) }
    var outL = 0f; private set
    var outR = 0f; private set

    fun process(inL: Float, inR: Float) {
        val r = (w - delaySamples) and (size - 1)
        val yl = bufL[r]; val yr = bufR[r]
        // Ping-pong: each side feeds the other.
        bufL[w] = toneL.lp((inL + inR) * 0.5f + yr * feedback)
        bufR[w] = toneR.lp(yl * feedback)
        w = (w + 1) and (size - 1)
        outL = yl; outR = yr
    }

    fun clear() { bufL.fill(0f); bufR.fill(0f) }
}

/**
 * The end of every chain: a peak limiter (instant attack, smooth release) followed by a soft clipper, so nothing
 * ever goes over full scale even when voices pile up.
 */
internal class Limiter(rate: Int, private val ceiling: Float = 0.89f) {
    private var env = 0f
    private val release = coef(0.12, rate)
    var gain = 1f; private set

    fun process(l: Float, r: Float, out: FloatArray, i: Int) {
        val peak = maxOf(abs(l), abs(r))
        env = if (peak > env) peak else env + (peak - env) * release
        gain = if (env > ceiling) ceiling / env else 1f
        out[i] = soft(l * gain)
        out[i + 1] = soft(r * gain)
    }

    companion object {
        /** Transparent below ~0.7, rounds off smoothly to ±1 above. */
        fun soft(x: Float): Float {
            val a = abs(x)
            if (a < 0.7f) return x
            val over = (a - 0.7f) / 0.3f
            val y = 0.7f + 0.3f * (over / (1f + over))
            return if (x < 0) -y else y
        }
    }
}

/**
 * A concert-hall reverb: pre-delay, input diffusion, then an 8-line feedback delay network with a Hadamard mixing
 * matrix, slowly modulated delay lengths (so long tails don't ring metallically) and per-line damping that makes
 * highs die away faster than lows, as in a real room.
 */
internal class Hall(private val rate: Int) {
    private val baseLens = intArrayOf(1123, 1361, 1607, 1871, 2111, 2357, 2593, 2843)
    private val bufSize: Int
    private val lines: Array<FloatArray>
    private val mask: Int
    private var w = 0
    private val lens = DoubleArray(8)
    private val cur = DoubleArray(8)
    private val gains = FloatArray(8)
    private val damp = FloatArray(8)
    private val lfo = DoubleArray(8) { it * 0.137 }
    private val lfoRate = DoubleArray(8) { 0.07 + 0.045 * it }
    private val modDepth = rate / 48000.0 * 9.0
    private var dampA = 0.3f
    private val x = FloatArray(8)
    private val pre: FloatArray
    private val preMask: Int
    private var preW = 0
    private var preLen = 1
    private val diffL1 = Allpass(rate * 5 / 1000 + 1, 0.62f)
    private val diffL2 = Allpass(rate * 11 / 1000 + 3, 0.55f)
    private val diffR1 = Allpass(rate * 6 / 1000 + 5, 0.62f)
    private val diffR2 = Allpass(rate * 13 / 1000 + 1, 0.55f)
    private val loCutL = OnePole().apply { set(120.0, rate) }
    private val loCutR = OnePole().apply { set(120.0, rate) }
    var outL = 0f; private set
    var outR = 0f; private set

    init {
        val need = (2843 * 2.3 * rate / 48000.0 + 64).toInt()
        bufSize = Integer.highestOneBit(need) * 2
        mask = bufSize - 1
        lines = Array(8) { FloatArray(bufSize) }
        pre = FloatArray(Integer.highestOneBit(rate / 8) * 2)
        preMask = pre.size - 1
        configure(0.7, 0.5, 2.4, 24.0)
    }

    /** [size] 0..1, [damping] 0..1 (bright to dark), [rt60] decay seconds, [preDelayMs] gap before the tail. */
    fun configure(size: Double, damping: Double, rt60: Double, preDelayMs: Double) {
        val scale = rate / 48000.0 * (0.7 + size * 1.5)
        for (i in 0 until 8) { lens[i] = baseLens[i] * scale; cur[i] = lens[i] }
        for (i in 0 until 8) gains[i] = 10.0.pow(-3.0 * (lens[i] / rate) / rt60).toFloat()
        // Damping as a one-pole low-pass in the loop: from about 9 kHz (bright) down to 2.5 kHz (dark).
        val fc = 9000.0 * 0.28.pow(damping)
        dampA = (1 - exp(-2 * PI * fc / rate)).toFloat()
        preLen = (preDelayMs * rate / 1000).toInt().coerceIn(1, pre.size - 1)
    }

    /** Call once per block of [n] samples, before [process]: moves the modulated delay lengths. */
    fun control(n: Int) {
        for (i in 0 until 8) {
            lfo[i] += lfoRate[i] * n / rate; if (lfo[i] >= 1) lfo[i] -= 1
            cur[i] = lens[i] + modDepth * Wave.sin1(lfo[i])
        }
    }

    fun process(inL: Float, inR: Float) {
        pre[preW] = (inL + inR) * 0.5f
        val side = (inL - inR) * 0.5f
        val mid = pre[(preW - preLen) and preMask]
        preW = (preW + 1) and preMask
        // Keep a little of the input's width, and keep rumble out of the tail.
        val a = diffL2.process(diffL1.process(loCutL.hp(mid + side * 0.5f)))
        val b = diffR2.process(diffR1.process(loCutR.hp(mid - side * 0.5f)))
        for (i in 0 until 8) {
            val r = w - cur[i]
            val ri = kotlin.math.floor(r).toInt()
            val f = (r - ri).toFloat()
            val l = lines[i]
            val s0 = l[ri and mask]
            x[i] = s0 + (l[(ri + 1) and mask] - s0) * f
        }
        outL = (x[0] - x[2] + x[4] - x[6]) * 0.5f
        outR = (x[1] - x[3] + x[5] - x[7]) * 0.5f
        // Fast 8-point Hadamard transform (orthogonal after scaling, so the loop stays lossless before the gains).
        val h0 = x[0] + x[1]; val h1 = x[0] - x[1]; val h2 = x[2] + x[3]; val h3 = x[2] - x[3]
        val h4 = x[4] + x[5]; val h5 = x[4] - x[5]; val h6 = x[6] + x[7]; val h7 = x[6] - x[7]
        val t0 = h0 + h2; val t2 = h0 - h2; val t1 = h1 + h3; val t3 = h1 - h3
        val t4 = h4 + h6; val t6 = h4 - h6; val t5 = h5 + h7; val t7 = h5 - h7
        val k = 0.35355339f
        x[0] = (t0 + t4) * k; x[4] = (t0 - t4) * k; x[1] = (t1 + t5) * k; x[5] = (t1 - t5) * k
        x[2] = (t2 + t6) * k; x[6] = (t2 - t6) * k; x[3] = (t3 + t7) * k; x[7] = (t3 - t7) * k
        for (i in 0 until 8) {
            damp[i] += dampA * (x[i] - damp[i])
            val inp = if (i and 1 == 0) a else b
            lines[i][w] = (if (i < 4) inp else -inp) + damp[i] * gains[i]
        }
        w = (w + 1) and mask
    }

    fun clear() { for (l in lines) l.fill(0f); damp.fill(0f); pre.fill(0f) }
}

/**
 * Gentle stereo-linked bus compression (about 2:1 with a soft knee), the "glue" that makes an ensemble sound like
 * one recording. The gain is worked out once per block and ramped per sample.
 */
internal class BusComp(rate: Int) {
    var thresholdDb = -20.0
    var ratio = 2.0
    private var env = 0f
    private val att = coef(0.02, rate)
    private val rel = coef(0.3, rate)
    private var g = 1f
    private var gStep = 0f

    /** Updates the gain ramp from the detector; call once per block of [n] samples. */
    fun control(n: Int) {
        val db = 20 * kotlin.math.log10(env.coerceAtLeast(1e-6f).toDouble())
        val over = db - thresholdDb
        val knee = 8.0
        val red = when {
            over <= -knee / 2 -> 0.0
            over >= knee / 2 -> over * (1 - 1 / ratio)
            else -> (1 - 1 / ratio) * (over + knee / 2) * (over + knee / 2) / (2 * knee)
        }
        gStep = (10.0.pow(-red / 20).toFloat() - g) / n
    }

    fun detect(l: Float, r: Float) {
        val p = maxOf(abs(l), abs(r))
        env += (if (p > env) att else rel) * (p - env)
    }

    fun gain(): Float { g += gStep; return g }
}

/** Tape warmth: a soft, slightly asymmetric saturation (mostly odd harmonics, a touch of even ones). */
internal object Tape {
    fun warm(x: Float, drive: Float): Float {
        val c = (x * (1f + drive)).coerceIn(-1.5f, 1.5f)
        val y = c - c * c * c * (4f / 27f) + 0.03f * drive * c * c
        return y / (1f + drive * 0.8f)
    }
}
