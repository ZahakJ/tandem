package io.github.zahakj.tandem.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Every UI sound. Each [SoundPack] has its own version of each. */
enum class Sfx {
    Move, Select, Back, MenuOpen, MenuClose, ToggleOn, ToggleOff, Error, Launch, Favorite, Confirm, Toast,
    Achievement, Found, Key, SaverIn, SaverOut, Page, Swap,
}

/** A family of UI sounds that belong together. */
enum class SoundPack(val id: String, val label: String, val blurb: String) {
    Modern("modern", "Modern", "Soft taps and gentle tones, short and quiet, like a current console. Tandem's own sound."),
    // The old "soft" id (the default until Modern) is left unmatched, so it moves to Modern with everyone else.
    Playful("playful", "Playful", "Glassy marimba taps and warm chimes: Tandem's original, cheerful set."),
    Chiptune("chip", "Chiptune", "Square-wave blips and coin-box fanfares, like an 8-bit console."),
    Bubbly("bubbly", "Bubbly", "Round water-drop bloops and fizzy sparkles."),
    Minimal("minimal", "Minimal", "Quiet, short and subtle. Barely there."),
    ;

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } ?: Modern }
}

/**
 * Renders UI sounds, made once at startup into short mono buffers. All of them sit in C major pentatonic so any two
 * sounds that overlap still agree with each other.
 */
object SfxSynth {
    fun render(pack: SoundPack, kind: Sfx, rate: Int): FloatArray {
        if (pack == SoundPack.Modern) return ModernSfx.render(kind, rate)
        val b = Buf(rate, lengthOf(kind))
        val v = PackVoice(pack, b)
        when (kind) {
            Sfx.Move -> v.tick()
            Sfx.Select -> { v.note(0.0, 81, 0.09, 0.7); v.note(0.05, 88, 0.16, 0.8) }
            Sfx.Back -> { v.note(0.0, 86, 0.07, 0.55); v.note(0.045, 79, 0.13, 0.6) }
            Sfx.MenuOpen -> { v.whoosh(0.0, 0.17, up = true, 0.5); v.note(0.12, 84, 0.18, 0.55); v.note(0.16, 91, 0.2, 0.35) }
            Sfx.MenuClose -> { v.whoosh(0.0, 0.15, up = false, 0.45); v.note(0.09, 79, 0.15, 0.45) }
            Sfx.ToggleOn -> { v.click(0.0, 0.5); v.note(0.0, 79, 0.06, 0.55); v.note(0.04, 84, 0.12, 0.65) }
            Sfx.ToggleOff -> { v.click(0.0, 0.4); v.note(0.0, 84, 0.06, 0.45); v.note(0.04, 79, 0.1, 0.45) }
            Sfx.Error -> { v.low(0.0, 55, 0.1, 0.8); v.low(0.13, 54, 0.14, 0.75) }
            Sfx.Launch -> {
                // Over in about 0.6 s, before the game is on screen (see Sounds.launch).
                v.whoosh(0.0, 0.22, up = true, 0.5)
                // Pip's motif from the theme song: D G B D, rising into a chirp.
                v.note(0.0, 74, 0.06, 0.5); v.note(0.05, 79, 0.06, 0.55); v.note(0.10, 83, 0.07, 0.6); v.note(0.15, 86, 0.12, 0.75)
                v.chirp(0.2, 0.5)
            }
            Sfx.Favorite -> { for ((i, m) in intArrayOf(91, 96, 100, 103, 108).withIndex()) v.note(i * 0.035, m, 0.35, 0.55 - i * 0.05) }
            Sfx.Confirm -> { v.click(0.0, 0.9); v.note(0.02, 88, 0.25, 0.7); v.note(0.02, 95, 0.25, 0.4) }
            Sfx.Toast -> { v.pop(0.0, 0.6); v.note(0.03, 91, 0.12, 0.35) }
            Sfx.Achievement -> {
                v.note(0.0, 72, 0.1, 0.6); v.note(0.08, 76, 0.1, 0.62); v.note(0.16, 79, 0.1, 0.65)
                v.note(0.26, 84, 0.9, 0.8); v.note(0.26, 88, 0.9, 0.55); v.note(0.26, 91, 0.9, 0.5); v.note(0.26, 72, 0.9, 0.45)
                for ((i, m) in intArrayOf(96, 100, 103, 108).withIndex()) v.note(0.42 + i * 0.05, m, 0.4, 0.3)
            }
            Sfx.Found -> { v.note(0.0, 91, 0.04, 0.4); v.note(0.04, 95, 0.07, 0.4) }
            Sfx.Key -> { v.click(0.0, 0.55); v.note(0.0, 96, 0.025, 0.18) }
            Sfx.SaverIn -> { v.note(0.0, 79, 0.6, 0.45); v.note(0.18, 76, 0.6, 0.4); v.note(0.36, 72, 1.0, 0.4); v.note(0.36, 64, 1.0, 0.25) }
            Sfx.SaverOut -> { v.note(0.0, 72, 0.12, 0.45); v.note(0.06, 76, 0.12, 0.5); v.note(0.12, 79, 0.15, 0.55); v.note(0.18, 84, 0.35, 0.6) }
            Sfx.Page -> { v.whoosh(0.0, 0.09, up = true, 0.4); v.note(0.04, 86, 0.05, 0.45) }
            Sfx.Swap -> { v.whoosh(0.0, 0.2, up = false, 0.45); v.note(0.1, 79, 0.1, 0.5); v.note(0.16, 86, 0.14, 0.55) }
        }
        val level = peakTarget(kind) * if (pack == SoundPack.Minimal) 0.75f else 1f
        return b.finish(if (pack == SoundPack.Minimal) 0.0 else if (pack == SoundPack.Chiptune) 0.05 else 0.16, level)
    }

    private fun lengthOf(kind: Sfx): Double = when (kind) {
        Sfx.Move, Sfx.Key -> 0.09
        Sfx.Found -> 0.25
        Sfx.Select, Sfx.Back, Sfx.ToggleOn, Sfx.ToggleOff, Sfx.Toast -> 0.4
        Sfx.MenuOpen, Sfx.MenuClose, Sfx.Error -> 0.5
        Sfx.Confirm -> 0.6
        Sfx.Favorite -> 0.8
        Sfx.Page -> 0.25
        Sfx.Swap -> 0.6
        Sfx.Launch -> 0.65
        Sfx.Achievement -> 1.8
        Sfx.SaverIn -> 2.0
        Sfx.SaverOut -> 0.9
    }

    /** Loudness of each sound relative to the others: ticks quiet, celebrations bright. */
    private fun peakTarget(kind: Sfx): Float = when (kind) {
        Sfx.Move -> 0.32f
        Sfx.Key -> 0.3f
        Sfx.Found -> 0.35f
        Sfx.ToggleOff, Sfx.Back, Sfx.MenuClose -> 0.5f
        Sfx.Page -> 0.45f
        Sfx.Select, Sfx.ToggleOn, Sfx.MenuOpen, Sfx.Toast, Sfx.Swap -> 0.58f
        Sfx.Error -> 0.6f
        Sfx.SaverIn, Sfx.SaverOut -> 0.5f
        // At most -3 dBFS, so nothing clips when two overlap at full volume.
        Sfx.Launch, Sfx.Achievement, Sfx.Favorite, Sfx.Confirm -> 0.7f
    }

    /**
     * The tone under a hold-to-confirm ring: a soft sine gliding up an octave (D5 to D6) over [seconds], swelling a
     * little as it goes, so letting go anywhere sounds like stopping on the way up. The same in every pack.
     */
    fun holdRise(rate: Int, seconds: Double): FloatArray {
        val b = Buf(rate, seconds + 0.04)
        var ph = 0.0
        var ph2 = 0.0
        val n = (seconds * rate).toInt()
        val attack = rate * 0.02
        for (i in 0 until n) {
            val p = i.toDouble() / n
            val f = 587.33 * 2.0.pow(p)
            ph += f / rate; if (ph >= 1) ph -= 1
            ph2 += f * 2 / rate; if (ph2 >= 1) ph2 -= 1
            val env = (if (i < attack) i / attack else 1.0) * (0.45 + 0.55 * p) * (if (p > 0.94) (1 - p) / 0.06 else 1.0)
            b.data[i] += ((sin(2 * PI * ph) + 0.18 * sin(2 * PI * ph2)) * env).toFloat()
        }
        return b.finish(0.0, 0.42f)
    }

    /** Pip's voice: four little syllables ("pi", "pa", "po", "pu"), played quickly at different pitches. */
    fun pipSyllable(index: Int, rate: Int): FloatArray {
        val b = Buf(rate, 0.12)
        val formants = arrayOf(doubleArrayOf(2600.0, 400.0), doubleArrayOf(1300.0, 850.0), doubleArrayOf(900.0, 550.0), doubleArrayOf(700.0, 350.0))[index and 3]
        val f1 = Svf().apply { set(formants[0], 4.0, rate) }
        val f2 = Svf().apply { set(formants[1], 3.0, rate) }
        val dur = 0.075 + index * 0.004
        var ph = 0.0
        val n = (dur * rate).toInt()
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val p = t / dur
            // A little up-down contour, like a question and an answer.
            val f = 620.0 * (1 + 0.18 * sin(PI * p) + if (index == 1) 0.08 * p else -0.05 * p)
            ph += f / rate; if (ph >= 1) ph -= 1
            val src = Wave.pulse(ph, f / rate, 0.3).toFloat()
            val env = (if (t < 0.006) t / 0.006 else 1.0) * (if (p > 0.7) (1 - p) / 0.3 else 1.0)
            val s = f1.bp(src) * 0.9f + f2.bp(src) * 1.1f + src * 0.12f
            b.data[i] += (s * env).toFloat()
        }
        return b.finish(0.0, 0.5f)
    }

    /** A short buffer with helpers; [finish] adds a reverb tail, removes DC and sets the level. */
    internal class Buf(val rate: Int, seconds: Double) {
        val data = FloatArray((seconds * rate).toInt())

        fun finish(reverbMix: Double, peak: Float): FloatArray {
            if (reverbMix > 0) {
                val rv = Reverb(rate).apply { configure(0.3, 0.5) }
                for (i in data.indices) {
                    rv.process(data[i], data[i])
                    data[i] += ((rv.outL + rv.outR) * 0.5f * reverbMix).toFloat()
                }
            }
            val hp = OnePole().apply { set(40.0, rate) }
            for (i in data.indices) data[i] = hp.hp(data[i])
            // Fade the last 15 ms so the end never clicks.
            val fade = minOf(data.size, rate * 15 / 1000)
            for (i in 0 until fade) data[data.size - 1 - i] *= i.toFloat() / fade
            var max = 1e-6f
            for (s in data) max = maxOf(max, abs(s))
            val g = peak / max
            for (i in data.indices) data[i] *= g
            return data
        }
    }

    /** How each pack plays a note, a tick, a whoosh and so on. */
    internal class PackVoice(private val pack: SoundPack, private val b: Buf) {
        private val rate = b.rate
        private val noise = Noise(0x5EED)

        private fun idx(t: Double) = (t * rate).toInt()

        fun note(at: Double, midi: Int, len: Double, vel: Double) {
            val f0 = Wave.midiToHz(midi.toDouble())
            val start = idx(at)
            val n = idx(len + if (pack == SoundPack.Chiptune) 0.0 else len * 1.5)
            var ph = 0.0
            for (i in 0 until n) {
                val k = start + i
                if (k >= b.data.size) break
                val t = i.toDouble() / rate
                val s = when (pack) {
                    SoundPack.Playful -> {
                        // Glass marimba: fundamental plus the 4th and 10th partials, which die away quickly.
                        val e = env(t, 0.002, len * 0.45)
                        ph += f0 / rate
                        (sin(2 * PI * ph) + 0.28 * sin(8 * PI * ph) * exp(-t / 0.03) + 0.06 * sin(20 * PI * ph) * exp(-t / 0.01)) * e
                    }
                    SoundPack.Chiptune -> {
                        // 25 % pulse with a volume that steps down 60 times a second, like an old sound chip.
                        val step = kotlin.math.floor(t * 60) / 60
                        val e = if (t > len) 0.0 else kotlin.math.floor((1 - step / len).coerceIn(0.0, 1.0) * 15) / 15
                        ph += f0 / rate; if (ph >= 1) ph -= 1
                        Wave.pulse(ph, f0 / rate, 0.25) * e * 0.55
                    }
                    SoundPack.Bubbly -> {
                        // A water drop: the pitch rises quickly into the note.
                        val f = f0 * (0.72 + 0.28 * (1 - exp(-t / 0.018)))
                        ph += f / rate
                        (sin(2 * PI * ph) + 0.1 * sin(4 * PI * ph)) * env(t, 0.003, len * 0.35)
                    }
                    SoundPack.Minimal, SoundPack.Modern -> {
                        ph += f0 / rate
                        sin(2 * PI * ph) * env(t, 0.002, len * 0.25) * 0.8
                    }
                }
                b.data[k] += (s * vel).toFloat()
            }
        }

        /** A very short low note for errors: rounder and darker. */
        fun low(at: Double, midi: Int, len: Double, vel: Double) {
            val f0 = Wave.midiToHz(midi.toDouble())
            val start = idx(at)
            var ph = 0.0
            val lp = OnePole().apply { set(1200.0, rate) }
            for (i in 0 until idx(len * 2)) {
                val k = start + i
                if (k >= b.data.size) break
                val t = i.toDouble() / rate
                val f = f0 * (1 - 0.06 * (t / len).coerceAtMost(1.0))
                ph += f / rate; if (ph >= 1) ph -= 1
                val src = when (pack) {
                    SoundPack.Chiptune -> Wave.pulse(ph, f / rate, 0.5) * 0.6
                    else -> sin(2 * PI * ph) * 0.8 + Wave.tri(ph) * 0.3
                }
                val e = if (pack == SoundPack.Chiptune) (if (t < len) 1.0 else 0.0) else env(t, 0.004, len * 0.5)
                b.data[k] += (lp.lp(src.toFloat()) * e * vel).toFloat()
            }
        }

        /** Move tick: the most frequent sound, so it is the shortest and softest. */
        fun tick() {
            when (pack) {
                SoundPack.Playful -> {
                    var ph = 0.0
                    for (i in 0 until idx(0.05)) {
                        val t = i.toDouble() / rate
                        val f = 1560.0 - 360.0 * (t / 0.03).coerceAtMost(1.0)
                        ph += f / rate
                        b.data[i] += (sin(2 * PI * ph) * env(t, 0.0015, 0.011) * 0.7 + sin(4 * PI * ph) * env(t, 0.001, 0.004) * 0.15).toFloat()
                    }
                }
                SoundPack.Chiptune -> note(0.0, 96, 0.03, 0.8)
                SoundPack.Bubbly -> note(0.0, 86, 0.035, 0.9)
                SoundPack.Minimal, SoundPack.Modern -> {
                    val bp = Svf().apply { set(2200.0, 2.0, rate) }
                    for (i in 0 until idx(0.02)) {
                        val t = i.toDouble() / rate
                        b.data[i] += bp.bp(noise.next()) * env(t, 0.0005, 0.003).toFloat() * 0.8f
                    }
                }
            }
        }

        fun click(at: Double, vel: Double) {
            val bp = Svf().apply { set(if (pack == SoundPack.Chiptune) 6000.0 else 3200.0, 1.5, rate) }
            val start = idx(at)
            for (i in 0 until idx(0.015)) {
                val k = start + i
                if (k >= b.data.size) break
                val t = i.toDouble() / rate
                b.data[k] += (bp.bp(noise.next()) * env(t, 0.0003, 0.0025) * vel * 0.9).toFloat()
            }
        }

        /** A swish of air rising or falling; the chiptune pack uses a pitch sweep instead. */
        fun whoosh(at: Double, len: Double, up: Boolean, vel: Double) {
            val soften = OnePole().apply { set(5000.0, rate) }
            val start = idx(at)
            val n = idx(len)
            val bp = Svf()
            var ph = 0.0
            for (i in 0 until n) {
                val k = start + i
                if (k >= b.data.size) break
                val p = i.toDouble() / n
                val q = if (up) p else 1 - p
                val swell = sin(PI * p).pow(1.5)
                val s = when (pack) {
                    SoundPack.Chiptune -> {
                        val f = 300.0 * 8.0.pow(q)
                        ph += f / rate; if (ph >= 1) ph -= 1
                        Wave.pulse(ph, f / rate, 0.5) * 0.35 * (if (p < 0.85) 1.0 else (1 - p) / 0.15)
                    }
                    SoundPack.Bubbly -> {
                        // Rising bubbles: a wobbling sine sweep.
                        val f = 500.0 * 3.0.pow(q) * (1 + 0.06 * sin(2 * PI * 38 * p * len))
                        ph += f / rate
                        sin(2 * PI * ph) * swell * 0.45
                    }
                    else -> {
                        if (i % 16 == 0) bp.set(500.0 * 10.0.pow(q), 1.2, rate)
                        soften.lp(bp.bp(noise.next())) * swell * (if (pack == SoundPack.Minimal) 0.7 else 1.1)
                    }
                }
                b.data[k] += (s * vel).toFloat()
            }
        }

        /** Pip's chirp: a quick, bright upward "bwee!". */
        fun chirp(at: Double, vel: Double) {
            val start = idx(at)
            val len = 0.14
            var ph = 0.0
            for (i in 0 until idx(len)) {
                val k = start + i
                if (k >= b.data.size) break
                val t = i.toDouble() / rate
                val p = t / len
                val f = 1300.0 + 1500.0 * p * p + 120.0 * sin(2 * PI * 32 * t)
                ph += f / rate; if (ph >= 1) ph -= 1
                val src = if (pack == SoundPack.Chiptune) Wave.pulse(ph, f / rate, 0.25) * 0.6 else sin(2 * PI * ph)
                b.data[k] += (src * env(t, 0.004, 0.06) * vel).toFloat()
            }
        }

        /** A soft "pop": a quick upward bloop for notifications. */
        fun pop(at: Double, vel: Double) {
            val start = idx(at)
            var ph = 0.0
            for (i in 0 until idx(0.08)) {
                val k = start + i
                if (k >= b.data.size) break
                val t = i.toDouble() / rate
                val f = 320.0 + 520.0 * (1 - exp(-t / 0.012))
                ph += f / rate; if (ph >= 1) ph -= 1
                val src = if (pack == SoundPack.Chiptune) Wave.pulse(ph, f / rate, 0.5) * 0.5 else sin(2 * PI * ph)
                b.data[k] += (src * env(t, 0.002, 0.022) * vel).toFloat()
            }
        }

        private fun env(t: Double, attack: Double, tau: Double): Double = when {
            t < 0 -> 0.0
            t < attack -> t / attack
            else -> exp(-(t - attack) / tau)
        }
    }
}
