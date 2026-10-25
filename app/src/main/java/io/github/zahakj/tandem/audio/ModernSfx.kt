package io.github.zahakj.tandem.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The "Modern" pack: soft, short and quiet, like a current console's menu. Every sound is built from the same four
 * pieces, so they all sound like one instrument:
 *
 * - a tap: a 1-2 ms burst of band-passed noise, the "touch" of a button;
 * - a body: a low sine that drops a little in pitch, the soft "thock" under the tap;
 * - a tone: a sine with a whisper of FM at the very start (so it speaks, then turns pure), and a quick decay;
 * - air: a breath of noise swept through a band-pass, for panels and screens sliding.
 *
 * Tones come from one palette (D major with its 7th), the whole set is low-passed, and each sound is levelled to a
 * loudness target rather than to a peak, so a tap and a chime feel equally present.
 */
internal object ModernSfx {
    // The palette, in Hz.
    private const val D5 = 587.33; private const val E5 = 659.26; private const val Fs5 = 739.99; private const val A5 = 880.0
    private const val B5 = 987.77; private const val Cs6 = 1108.73; private const val D6 = 1174.66; private const val E6 = 1318.51
    private const val Fs6 = 1479.98; private const val A6 = 1760.0; private const val D7 = 2349.32
    private const val A4 = 440.0; private const val Gs4 = 415.30; private const val D4 = 293.66

    fun render(kind: Sfx, rate: Int): FloatArray {
        val b = Kit(rate, lengthOf(kind))
        when (kind) {
            // Navigation: a tap and a small body, done in under 50 ms so held D-pad scrolling never piles up.
            Sfx.Move -> { b.tap(0.0, 3600.0, 0.5); b.body(0.0, 1250.0, 0.55, 0.006) }
            Sfx.Key -> { b.tap(0.0, 4200.0, 0.55); b.body(0.0, 1600.0, 0.35, 0.004) }
            Sfx.Page -> { b.air(0.0, 0.075, 1800.0, 4200.0, 0.55, q = 1.1); b.tap(0.02, 3000.0, 0.35); b.body(0.02, 900.0, 0.4, 0.008) }
            Sfx.Select -> { b.tap(0.0, 3400.0, 0.7); b.body(0.0, 700.0, 0.5, 0.012); b.tone(0.004, A5, 0.55, 0.045); b.tone(0.022, E6, 0.32, 0.05) }
            Sfx.Back -> { b.tap(0.0, 2800.0, 0.6); b.body(0.0, 520.0, 0.5, 0.012); b.tone(0.004, E5, 0.5, 0.04, glide = -0.03) }
            Sfx.ToggleOn -> { b.tap(0.0, 3800.0, 0.6); b.tone(0.0, B5, 0.4, 0.022); b.tone(0.028, E6, 0.5, 0.045) }
            Sfx.ToggleOff -> { b.tap(0.0, 3000.0, 0.55); b.tone(0.0, E6, 0.3, 0.02); b.tone(0.028, B5, 0.45, 0.04) }
            Sfx.MenuOpen -> { b.air(0.0, 0.16, 700.0, 3400.0, 0.7); b.tone(0.07, D6, 0.4, 0.06); b.tone(0.1, A6, 0.22, 0.07) }
            Sfx.MenuClose -> { b.air(0.0, 0.14, 3000.0, 650.0, 0.6); b.tone(0.05, A5, 0.4, 0.05, glide = -0.02) }
            Sfx.Swap -> {
                // Two screens trading places: a breath down then up, and two tones that answer each other.
                b.air(0.0, 0.12, 2600.0, 700.0, 0.55); b.air(0.1, 0.14, 700.0, 3000.0, 0.55)
                b.tone(0.06, A5, 0.35, 0.05); b.tone(0.15, D6, 0.4, 0.07)
            }
            Sfx.Error -> {
                // Two muted low notes a semitone apart: clearly "no", never harsh.
                b.tap(0.0, 1800.0, 0.45)
                b.tone(0.0, A4, 0.8, 0.06, fm = 0.4, harm = 0.3); b.tone(0.11, Gs4, 0.8, 0.08, fm = 0.4, harm = 0.3)
            }
            Sfx.Confirm -> {
                b.tap(0.0, 3400.0, 0.7); b.body(0.0, 600.0, 0.5, 0.015)
                b.tone(0.005, D6, 0.5, 0.11); b.tone(0.005, A6, 0.28, 0.1); b.tone(0.005, D5, 0.25, 0.12)
            }
            Sfx.Favorite -> {
                b.tap(0.0, 4200.0, 0.45)
                for ((i, f) in doubleArrayOf(D6, Fs6, A6, D7).withIndex()) b.tone(i * 0.038, f, 0.42 - i * 0.04, 0.09 + i * 0.03)
                b.air(0.08, 0.2, 5000.0, 7000.0, 0.18, q = 2.0)
            }
            Sfx.Toast -> { b.pop(0.0, 0.6); b.tone(0.02, A6, 0.25, 0.05) }
            Sfx.Found -> { b.tone(0.0, A6, 0.4, 0.025); b.tone(0.035, D7, 0.35, 0.035) }
            Sfx.Launch -> launch(b)
            Sfx.Achievement -> {
                b.tap(0.0, 3400.0, 0.5)
                b.tone(0.0, D5, 0.45, 0.06); b.tone(0.07, Fs5, 0.48, 0.06); b.tone(0.14, A5, 0.5, 0.07)
                for ((f, v) in listOf(D6 to 0.5, Fs6 to 0.32, A6 to 0.3, Cs6 * 2 to 0.18)) b.tone(0.22, f, v, 0.32)
                b.body(0.22, 300.0, 0.35, 0.06)
                b.air(0.2, 0.5, 4000.0, 7000.0, 0.15, q = 2.0)
            }
            Sfx.SaverIn -> { b.tone(0.0, A5, 0.35, 0.25); b.tone(0.16, Fs5, 0.33, 0.25); b.tone(0.32, D5, 0.35, 0.4); b.tone(0.32, A4, 0.2, 0.4) }
            Sfx.SaverOut -> { b.tap(0.0, 3200.0, 0.4); b.tone(0.0, D5, 0.35, 0.08); b.tone(0.06, Fs5, 0.38, 0.08); b.tone(0.12, A5, 0.42, 0.18) }
        }
        return b.finish(levelDb(kind), if (kind == Sfx.Launch) 0.06 else 0.035)
    }

    /**
     * The launch sting, about half a second: a breath of air rises while a soft three-note figure climbs into an open
     * chord over a low bloom, then everything decays on its own (no hold, no tail to be cut off by the game).
     */
    private fun launch(b: Kit) {
        b.tap(0.0, 3200.0, 0.6)
        b.body(0.0, 220.0, 0.7, 0.06)
        b.air(0.0, 0.24, 500.0, 4200.0, 0.5)
        b.tone(0.0, D5, 0.38, 0.05)
        b.tone(0.055, A5, 0.42, 0.05)
        b.tone(0.11, D6, 0.46, 0.06)
        // The open chord: D, A, E on top (a sus2 colour that sounds like "go" rather than "the end").
        b.tone(0.165, D6, 0.45, 0.1)
        b.tone(0.165, A6, 0.3, 0.09)
        b.tone(0.17, E6 * 2, 0.12, 0.06)
        b.tone(0.165, D4, 0.3, 0.1)
    }

    private fun lengthOf(kind: Sfx): Double = when (kind) {
        Sfx.Move, Sfx.Key -> 0.05
        Sfx.Page -> 0.1
        Sfx.Found -> 0.16
        Sfx.Select, Sfx.Back, Sfx.ToggleOn, Sfx.ToggleOff, Sfx.Toast -> 0.22
        Sfx.MenuOpen, Sfx.MenuClose -> 0.32
        Sfx.Error, Sfx.Swap -> 0.42
        Sfx.Confirm -> 0.5
        Sfx.Favorite -> 0.6
        Sfx.Launch -> 0.62
        Sfx.SaverOut -> 0.7
        Sfx.Achievement -> 1.4
        Sfx.SaverIn -> 1.5
    }

    /** Loudness (dB, loudest 30 ms) of each sound: navigation whispers, celebrations speak up a little. */
    private fun levelDb(kind: Sfx): Double = when (kind) {
        Sfx.Move, Sfx.Key, Sfx.Found -> -23.0
        Sfx.Page -> -22.0
        Sfx.SaverIn, Sfx.SaverOut -> -21.0
        Sfx.Select, Sfx.Back, Sfx.ToggleOn, Sfx.ToggleOff, Sfx.Toast, Sfx.MenuOpen, Sfx.MenuClose, Sfx.Swap -> -19.0
        Sfx.Error -> -18.0
        Sfx.Confirm, Sfx.Favorite, Sfx.Launch, Sfx.Achievement -> -16.0
    }

    /** Peak ceiling for every sound (-4.4 dBFS), whatever its loudness target. */
    private const val PEAK = 0.6f

    /** A mono buffer and the four building blocks. */
    private class Kit(val rate: Int, seconds: Double) {
        val data = FloatArray((seconds * rate).toInt())
        private val noise = Noise(0x7A9D)

        private fun idx(t: Double) = (t * rate).toInt()

        /** A noise burst through a band-pass at [center]: the click of a button. */
        fun tap(at: Double, center: Double, vel: Double, tau: Double = 0.0011) {
            val bp = Svf().apply { set(center, 1.3, rate) }
            val start = idx(at)
            for (i in 0 until idx(tau * 9)) {
                val k = start + i
                if (k >= data.size) break
                val t = i.toDouble() / rate
                data[k] += (bp.bp(noise.next()) * env(t, 0.0002, tau) * vel * 1.6).toFloat()
            }
        }

        /** A sine that starts a little sharp and settles: the soft weight under a tap. */
        fun body(at: Double, f: Double, vel: Double, tau: Double) {
            val start = idx(at)
            var ph = 0.0
            for (i in 0 until idx(tau * 7)) {
                val k = start + i
                if (k >= data.size) break
                val t = i.toDouble() / rate
                ph += f * (1 + 0.35 * exp(-t / 0.004)) / rate
                data[k] += (sin(2 * PI * ph) * env(t, 0.0008, tau) * vel).toFloat()
            }
        }

        /**
         * A short tone: FM brightness at the start that fades within ~10 ms, then a pure sine decaying over [tau].
         * [harm] adds a soft 2nd partial (for low notes, so they read on small speakers); [glide] bends the pitch by
         * that fraction over the note.
         */
        fun tone(at: Double, f: Double, vel: Double, tau: Double, fm: Double = 0.8, harm: Double = 0.0, glide: Double = 0.0) {
            val start = idx(at)
            var pc = 0.0
            var pm = 0.0
            for (i in 0 until idx(0.004 + tau * 6.5)) {
                val k = start + i
                if (k >= data.size) break
                val t = i.toDouble() / rate
                val fr = f * (1 + glide * (1 - exp(-t / (tau * 0.8))))
                pc += fr / rate; pm += fr * 2 / rate
                if (pc >= 1) pc -= 1
                if (pm >= 1) pm -= 1
                val index = fm * exp(-t / 0.009)
                val s = sin(2 * PI * pc + index * sin(2 * PI * pm)) + harm * sin(4 * PI * pc) * exp(-t / (tau * 0.6))
                data[k] += (s * env(t, 0.0025, tau) * vel).toFloat()
            }
        }

        /** Noise swept from [from] to [to] Hz through a band-pass, swelling quickly and fading slowly. */
        fun air(at: Double, len: Double, from: Double, to: Double, vel: Double, q: Double = 0.9) {
            val bp = Svf()
            val start = idx(at)
            val n = idx(len)
            for (i in 0 until n) {
                val k = start + i
                if (k >= data.size) break
                val p = i.toDouble() / n
                if (i % 16 == 0) bp.set(from * (to / from).pow(p), q, rate)
                val peak = 0.35
                val e = if (p < peak) sin(PI / 2 * p / peak).pow(2) else cos(PI / 2 * (p - peak) / (1 - peak)).pow(2)
                data[k] += (bp.bp(noise.next()) * e * vel * 0.9).toFloat()
            }
        }

        /** A rounded upward "pop" for notifications. */
        fun pop(at: Double, vel: Double) {
            val start = idx(at)
            var ph = 0.0
            for (i in 0 until idx(0.07)) {
                val k = start + i
                if (k >= data.size) break
                val t = i.toDouble() / rate
                ph += (420.0 + 360.0 * (1 - exp(-t / 0.01))) / rate
                data[k] += (sin(2 * PI * ph) * env(t, 0.002, 0.018) * vel).toFloat()
            }
        }

        /**
         * Low-passes the whole sound (nothing glassy above ~6 kHz), adds a touch of room, removes DC, eases the last
         * 8 ms (30 ms for longer sounds) to silence and sets the level: loudness to [db], but never a peak over [PEAK].
         */
        fun finish(db: Double, room: Double): FloatArray {
            val rv = Reverb(rate).apply { configure(0.25, 0.6) }
            val lp = Svf().apply { set(6200.0, 0.7, rate) }
            val hp = OnePole().apply { set(60.0, rate) }
            for (i in data.indices) {
                val x = data[i]
                rv.process(x, x)
                data[i] = hp.hp(lp.lp(x + ((rv.outL + rv.outR) * 0.5f * room).toFloat()))
            }
            val fade = minOf(data.size, rate * (if (data.size > rate * 3 / 10) 30 else 8) / 1000)
            for (i in 0 until fade) { val x = i.toFloat() / fade; data[data.size - 1 - i] *= x * x }
            val loud = loudness(data, rate)
            var g = 10.0.pow(db / 20) / loud.coerceAtLeast(1e-6)
            var peak = 1e-6f
            for (s in data) peak = maxOf(peak, abs(s))
            g = minOf(g, PEAK / peak.toDouble())
            for (i in data.indices) data[i] = (data[i] * g).toFloat()
            return data
        }

        private fun env(t: Double, attack: Double, tau: Double): Double = when {
            t < 0 -> 0.0
            t < attack -> 0.5 - 0.5 * cos(PI * t / attack)
            else -> exp(-(t - attack) / tau)
        }
    }

    /** RMS of the loudest 30 ms window. */
    fun loudness(data: FloatArray, rate: Int): Double {
        val w = (rate * 0.03).toInt().coerceAtMost(data.size).coerceAtLeast(1)
        var sum = 0.0
        for (i in 0 until w) sum += data[i] * data[i]
        var best = sum
        for (i in w until data.size) {
            sum += data[i] * data[i] - data[i - w] * data[i - w]
            if (sum > best) best = sum
        }
        return sqrt(best.coerceAtLeast(0.0) / w)
    }

    fun dbfs(x: Double): Double = 20 * log10(x.coerceAtLeast(1e-9))
}
