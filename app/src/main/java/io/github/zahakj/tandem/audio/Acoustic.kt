package io.github.zahakj.tandem.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The acoustic instruments' "physics", kept apart from the voice that plays them.
 *
 * Bowed and blown instruments read a single-cycle wavetable made once per note (the harmonic spectrum, body
 * formants and band limit for that pitch are baked in), so playing one costs a table read per player plus a filter.
 * Struck instruments are modal: a handful of exponentially decaying sine partials at the instrument's own
 * (often inharmonic) frequency ratios, each run as a two-multiply rotation with its decay built in.
 */

/** Per-note wavetables for the table-based instruments, built on first use and then shared by every voice. */
internal object Tables {
    const val SIZE = 2048
    private var rate = 0
    private var cache = arrayOfNulls<FloatArray>(Osc.entries.size * 128)

    fun isTable(o: Osc) = o == Osc.Strings || o == Osc.Clarinet || o == Osc.Oboe || o == Osc.Accordion || o == Osc.Horn || o == Osc.ConcertFlute

    @Synchronized
    fun get(o: Osc, midi: Int, rate: Int): FloatArray {
        if (rate != this.rate) { cache = arrayOfNulls(Osc.entries.size * 128); this.rate = rate }
        val key = o.ordinal * 128 + midi.coerceIn(0, 127)
        cache[key]?.let { return it }
        val t = build(o, Wave.midiToHz(midi.coerceIn(0, 127).toDouble()), rate)
        cache[key] = t
        return t
    }

    private fun bump(f: Double, centre: Double, widthOct: Double): Double {
        val o = ln(f / centre) / ln(2.0) / widthOct
        return exp(-o * o)
    }

    private fun lp(f: Double, fc: Double, order: Int): Double = 1 / (1 + (f / fc).pow(order))

    /** Amplitude of harmonic [h] (at [f] Hz) for each instrument. */
    private fun spectrum(o: Osc, h: Int, f: Double): Double = when (o) {
        Osc.Strings -> h.toDouble().pow(-0.95) * (0.5 + 0.8 * bump(f, 450.0, 0.5) + 0.55 * bump(f, 2600.0, 0.5)) * lp(f, 7000.0, 2)
        Osc.Clarinet -> (if (h % 2 == 1) 1.0 else if (f < 1600) 0.06 else 0.45) / h * lp(f, 3200.0, 3)
        Osc.Oboe -> h.toDouble().pow(-0.6) * (if (h == 1) 0.6 else 1.0) *
            (0.35 + 1.2 * bump(f, 1150.0, 0.45) + 0.6 * bump(f, 2900.0, 0.4)) * lp(f, 4500.0, 4)
        Osc.Accordion -> h.toDouble().pow(-0.75) * (0.6 + 0.8 * bump(f, 1300.0, 0.6)) * lp(f, 3800.0, 3)
        Osc.Horn -> h.toDouble().pow(-1.3) * (1 + 0.6 * bump(f, 400.0, 0.6)) * lp(f, 1700.0, 3)
        Osc.ConcertFlute -> when (h) { 1 -> 1.0; 2 -> 0.3; 3 -> 0.11; 4 -> 0.045; 5 -> 0.02; else -> 0.0 }
        else -> if (h == 1) 1.0 else 0.0
    }

    private fun build(o: Osc, f0: Double, rate: Int): FloatArray {
        val limit = minOf(rate * 0.42, 12000.0)
        val n = maxOf(1, (limit / f0).toInt())
        val amps = DoubleArray(n + 1)
        var power = 0.0
        for (h in 1..n) { amps[h] = spectrum(o, h, h * f0); power += amps[h] * amps[h] / 2 }
        // Fixed scattered phases keep the crest factor low for the rich spectra; flute and clarinet stay in sine phase.
        val scatter = o != Osc.ConcertFlute && o != Osc.Clarinet
        val t = FloatArray(SIZE + 1)
        for (h in 1..n) {
            val a = amps[h]
            if (a < 1e-4) continue
            val ph = if (scatter) (h * h * 0.6180339887) % 1.0 else 0.0
            for (i in 0 until SIZE) t[i] += (a * Wave.sinAny(h.toDouble() * i / SIZE + ph)).toFloat()
        }
        // Same loudness (RMS) for every note.
        val g = (0.5 / sqrt(power.coerceAtLeast(1e-9))).toFloat()
        for (i in 0 until SIZE) t[i] *= g
        t[SIZE] = t[0]
        return t
    }
}

/** Partials (frequency, amplitude, 60 dB decay time) for the modal instruments. */
internal object Modes {
    fun isModal(o: Osc) = o == Osc.Piano || o == Osc.Celesta || o == Osc.Glockenspiel || o == Osc.Timpani

    /**
     * Fills [freq], [amp], [t60] and returns how many partials there are. [vel] is the raw key velocity (0..1), which
     * sets brightness as well as loudness. [seed] gives each note its own tiny detuning of the string unisons.
     */
    fun fill(o: Osc, midi: Int, f0: Double, vel: Float, rate: Int, seed: Float, freq: DoubleArray, amp: DoubleArray, t60: DoubleArray): Int {
        val limit = minOf(rate * 0.42, if (o == Osc.Piano) 12000.0 else 9500.0)
        var n = 0
        fun add(f: Double, a: Double, t: Double) {
            if (f >= limit || n >= freq.size || a < 1e-4) return
            freq[n] = f; amp[n] = a; t60[n] = t; n++
        }
        when (o) {
            Osc.Piano -> {
                // Stiff strings: partial k sits at k·f0·sqrt(1 + B·k²); B grows towards the treble.
                val b = 7e-5 * 2.0.pow((midi - 21) / 24.0)
                val v = vel.toDouble()
                // A harder blow excites higher partials: a steeper spectral tilt when played softly.
                val fc = (1200 + 6000 * v.pow(1.2)) * (1 + f0 / 1500)
                val t1 = (7.5 * (261.6 / f0).pow(0.62)).coerceIn(0.9, 16.0)
                // Three strings per note, a fraction of a cent apart: their beating gives the piano its shimmer and
                // two-stage decay (a quick first fade, then a long quiet aftersound).
                val cents = 0.35 + 0.3 * seed
                val up = 2.0.pow(cents / 1200)
                for (k in 1..32) {
                    val fk = k * f0 * sqrt(1 + b * k * k)
                    if (fk >= limit) break
                    // Hammer struck at about 1/8 of the string: those harmonics are weakened.
                    val strike = 0.25 + 0.75 * abs(sin(Math.PI * k / 8.3))
                    val a = k.toDouble().pow(-0.85) * strike / (1 + (fk / fc).pow(2.0))
                    val tk = t1 / (1 + fk / 1600) * (1 + 0.2 * (k % 2))
                    add(fk * up, a * 0.62, tk)
                    if (k <= 8) add(fk / up, a * 0.38, tk * 2.8)
                }
            }
            Osc.Celesta -> {
                val t = (3.2 * (523.0 / f0).pow(0.4)).coerceIn(0.8, 5.0)
                add(f0, 1.0, t)
                add(f0 * 3.99, 0.22 * (0.4 + vel), 0.32)
                add(f0 * 9.8, 0.05 * vel, 0.08)
                add(f0 * 1.0016, 0.25, t * 1.3)
            }
            Osc.Glockenspiel -> {
                val t = (2.4 * (1000.0 / f0).pow(0.3)).coerceIn(0.8, 4.0)
                add(f0, 1.0, t)
                add(f0 * 2.76, 0.32 * (0.5 + vel * 0.5), t * 0.35)
                add(f0 * 5.4, 0.14 * vel, t * 0.15)
                add(f0 * 8.93, 0.05 * vel, t * 0.07)
            }
            Osc.Timpani -> {
                // Circular membrane modes as tuned by the kettle; the principal tone is the first one here.
                val ratios = doubleArrayOf(1.0, 1.504, 1.742, 2.0, 2.245, 2.494, 2.8, 0.62)
                val amps = doubleArrayOf(1.0, 0.55, 0.32, 0.3, 0.16, 0.12, 0.07, 0.25)
                val ts = doubleArrayOf(2.6, 1.7, 1.2, 1.1, 0.8, 0.6, 0.45, 0.25)
                for (i in ratios.indices) add(f0 * ratios[i], amps[i] * (if (i > 2) 0.5 + vel else 1f).toDouble(), ts[i])
            }
            else -> add(f0, 1.0, 1.0)
        }
        // Normalise so every note of an instrument is about as loud (RMS), whatever its brightness.
        var sum = 0.0
        for (i in 0 until n) sum += amp[i] * amp[i]
        val g = 0.9 / sqrt(sum.coerceAtLeast(1e-9))
        for (i in 0 until n) amp[i] *= g
        return n
    }
}
