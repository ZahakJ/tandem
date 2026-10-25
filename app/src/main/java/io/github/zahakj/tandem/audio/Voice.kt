package io.github.zahakj.tandem.audio

import kotlin.math.pow

/**
 * One sounding note of a [Patch]. Voices are pooled by the player and reused, so starting a note never allocates.
 * Pitch, filter and FM brightness are updated once per block (control rate); amplitude per sample.
 */
internal class Voice(private val rate: Int) {
    var active = false
    var part = -1
    var patch: Patch = DEFAULT
    var midi = 60
    var age = 0L
    var delay = 0
    var gate = 0
    var level = 0f
    private var stage = 0 // 0 attack, 1 decay/sustain, 2 release
    private var vel = 1f
    private var rawVel = 1f
    private var freq = 440.0
    private var targetFreq = 440.0
    private val ph = DoubleArray(3)
    private var modPh = 0.0
    private var lfoPh = 0.0
    private var t = 0
    private var fEnv = 1f
    private var idxEnv = 1f
    private var tineEnv = 1f
    private val svf = Svf()
    private val bpf = Svf()
    private val noise = Noise(0x13579BD)
    private val chord = IntArray(4)
    private var chordN = 0
    private var gl = 0.7f
    private var gr = 0.7f
    // Karplus-Strong string
    private val ks = FloatArray(4096)
    private var ksW = 0
    private var ksDelay = 100.0
    private var ksLast = 0f
    private var ksGain = 0.99f
    private var ksTone = 0.5f
    private val exLp = OnePole()
    private val exLp2 = OnePole()
    private val tmp = FloatArray(4096)
    private var attackInc = 1f
    private var decayCoef = 0f
    private var releaseCoef = 0f
    private var ksTuning = 0.0
    private var nextStrike = 0.0
    // Modal instruments: each partial is a decaying phasor (re, im) rotated by (cr, ci) every sample.
    private val mRe = DoubleArray(MAX_MODES)
    private val mIm = DoubleArray(MAX_MODES)
    private val mCr = DoubleArray(MAX_MODES)
    private val mCi = DoubleArray(MAX_MODES)
    private val mA0 = DoubleArray(MAX_MODES)
    private val mF = DoubleArray(MAX_MODES)
    private val mT = DoubleArray(MAX_MODES)
    private var mN = 0
    private var ham = 0f
    private var hamDecay = 0.99f
    private val hamLp = OnePole()
    // Wavetable instruments: up to five players reading one table, each with its own tuning and vibrato.
    private var table: FloatArray = EMPTY
    private val tph = DoubleArray(MAX_ENS)
    private val tmul = DoubleArray(MAX_ENS)
    private val tvib = DoubleArray(MAX_ENS)
    private var ensN = 1
    private var ensGain = 1f
    private var seed = 0f

    fun start(p: Patch, partIndex: Int, note: Int, velocity: Float, delaySamples: Int, gateSamples: Int, chordNotes: IntArray?, legato: Boolean) {
        val wasActive = active && legato
        active = true
        patch = p; part = partIndex; midi = note; delay = delaySamples; gate = gateSamples
        vel = 1f - p.velSens + p.velSens * velocity
        rawVel = velocity
        val m = note + 12 * p.octave
        targetFreq = Wave.midiToHz(m.toDouble())
        chordN = 0
        if (chordNotes != null) { chordN = minOf(4, chordNotes.size); for (i in 0 until chordN) chord[i] = chordNotes[i] + 12 * p.octave }
        if (!wasActive || p.glide <= 0) freq = targetFreq
        val pan = p.pan.coerceIn(-1f, 1f)
        gl = (0.7071 * (1 - pan) ).toFloat().coerceAtMost(1f)
        gr = (0.7071 * (1 + pan)).toFloat().coerceAtMost(1f)
        attackInc = (1.0 / (p.attack.coerceAtLeast(0.0005) * rate)).toFloat()
        decayCoef = coef(p.decay / 3, rate)
        releaseCoef = coef(p.release / 3, rate)
        if (!wasActive) {
            level = 0f; t = 0; fEnv = 1f; idxEnv = 1f; tineEnv = 1f
            ph[0] = 0.0; ph[1] = 0.33; ph[2] = 0.67; modPh = 0.0; lfoPh = 0.0
            svf.reset(); bpf.reset()
            seed = noise.unit()
            nextStrike = if (p.tremolo > 0) 1.0 / p.tremolo else 1e9
            if (p.osc == Osc.Pluck) excite(p)
            if (p.osc == Osc.Plucked) { excite(p); ks.fill(0f); ksW = 0; pick(p, 1f, fresh = true) }
            if (Modes.isModal(p.osc)) strike(p, m)
            if (Tables.isTable(p.osc)) {
                table = Tables.get(p.osc, m, rate)
                ensN = if (p.osc == Osc.Strings) p.unison.coerceIn(1, MAX_ENS) else if (p.osc == Osc.Accordion) 3 else 1
                ensGain = (1.0 / kotlin.math.sqrt(ensN.toDouble())).toFloat()
                for (k in 0 until ensN) { tph[k] = noise.unit().toDouble(); tvib[k] = noise.unit().toDouble(); tmul[k] = 1.0 }
            }
        } else {
            // Legato: keep the envelope, retrigger the filter a little.
            fEnv = maxOf(fEnv, 0.5f)
        }
        stage = 0
    }

    private fun excite(p: Patch) {
        ksDelay = (rate / targetFreq - 0.5).coerceIn(2.0, ks.size - 2.0)
        val period = ksDelay + 0.5
        // Seconds for the string to ring down by 60 dB; higher notes ring a little shorter.
        val t60 = (0.4 + p.ring * 4.5) * (220.0 / targetFreq).pow(0.3).coerceIn(0.4, 1.6)
        ksGain = 10.0.pow(-3.0 * (period / rate) / t60).toFloat()
        ksTone = (0.25 + p.bright * 0.5).toFloat()
        exLp.set(800.0 + p.bright * 7000.0, rate); exLp.z = 0f
        ks.fill(0f)
        val n = period.toInt() + 1
        var mean = 0f
        for (i in 0 until n) { ks[i] = exLp.lp(noise.next()); mean += ks[i] }
        mean /= n
        for (i in 0 until n) ks[i] -= mean
        ksW = n
        ksLast = 0f
        ksTuning = 0.0
    }

    /**
     * Plucks an acoustic string: the initial shape is the string pulled aside at one point (a triangle), softened by
     * the finger or plectrum, plus a breath of noise. Harder plucks are brighter. Repeated picks (tremolo) add to what
     * is already ringing.
     */
    private fun pick(p: Patch, strength: Float, fresh: Boolean) {
        val n = (ksDelay + 0.5).toInt()
        val beta = 0.18 + 0.08 * seed
        val cut = (500.0 + p.bright * 5500.0) * (0.45 + 0.75 * rawVel)
        exLp.set(cut, rate); exLp.z = 0f
        exLp2.set(cut * 1.5, rate); exLp2.z = 0f
        var mean = 0f
        val start = if (fresh) 0 else ksW - n
        for (i in 0 until n) {
            val x = i.toDouble() / n
            val tri = (if (x < beta) x / beta else (1 - x) / (1 - beta)).toFloat()
            val v = exLp2.lp(exLp.lp(tri + 0.08f * noise.next())) * strength
            tmp[i] = v; mean += v
        }
        mean /= n
        if (fresh) {
            for (i in 0 until n) ks[i] = (tmp[i] - mean) * 1.6f
            ksW = n
        } else {
            // Added on top of what is ringing, faded in and out over the period so the string never jumps (no click).
            for (i in 0 until n) {
                val j = (start + i) and 4095
                ks[j] += (tmp[i] - mean) * 1.6f * Wave.sin1(0.5 * i / n)
            }
        }
    }

    private fun strike(p: Patch, m: Int) {
        mN = Modes.fill(p.osc, m, targetFreq, rawVel, rate, seed, mF, mA0, mT)
        for (k in 0 until mN) {
            val w = 2 * Math.PI * mF[k] / rate
            val r = 10.0.pow(-3.0 / (mT[k] * rate))
            mCr[k] = r * kotlin.math.cos(w); mCi[k] = r * kotlin.math.sin(w)
            mRe[k] = mA0[k]; mIm[k] = 0.0
        }
        // The hammer or mallet itself: a short burst of filtered noise, brighter when struck harder.
        when (p.osc) {
            Osc.Piano -> { ham = 0.03f + 0.07f * rawVel; hamDecay = coef(0.006, rate).let { 1 - it }; hamLp.set(1500.0 + 5000.0 * rawVel, rate) }
            Osc.Timpani -> { ham = 0.25f + 0.2f * rawVel; hamDecay = 1 - coef(0.018, rate); hamLp.set(260.0, rate) }
            else -> { ham = 0.015f * rawVel; hamDecay = 1 - coef(0.004, rate); hamLp.set(2500.0, rate) }
        }
        hamLp.z = 0f
    }

    /** Strikes the same modes again (timpani roll), on top of what is still ringing. */
    private fun restrike(strength: Double) {
        for (k in 0 until mN) mRe[k] += mA0[k] * strength
        ham = maxOf(ham, 0.15f * strength.toFloat())
    }

    fun release() { if (stage < 2) stage = 2 }

    /** Fast fade used when a voice is stolen. */
    fun kill() { stage = 2; releaseCoef = coef(0.004, rate) }

    /** The same key struck again: the damper stops the old note quickly, but not with a click. */
    fun damp() { stage = 2; releaseCoef = coef(0.03, rate) }

    /**
     * Adds [n] samples of this voice into the buses. [pitchMod] is a frequency multiplier (tape wobble), [duck] the
     * sidechain gain for this block.
     */
    fun render(
        n: Int, pitchMod: Double, duck: Float,
        dryL: FloatArray, dryR: FloatArray, revL: FloatArray, revR: FloatArray,
        echo: FloatArray, choL: FloatArray, choR: FloatArray, meter: FloatArray,
    ) {
        val p = patch
        var i = 0
        if (delay > 0) { val skip = minOf(delay, n); delay -= skip; i = skip; if (i >= n) return }
        val blockSec = (n - i).toDouble() / rate
        val sec = t.toDouble() / rate
        val osc = p.osc
        val isTable = Tables.isTable(osc)
        val isModal = Modes.isModal(osc)

        // --- control rate ---
        if (p.glide > 0 && freq != targetFreq) {
            val k = 1 - kotlin.math.exp(-blockSec / p.glide)
            freq += (targetFreq - freq) * k
        }
        var f = freq * pitchMod
        if (chordN > 1 && p.chipArpHz > 0) {
            val idx = ((sec * p.chipArpHz).toLong() % chordN).toInt()
            f = Wave.midiToHz(chord[idx].toDouble()) * pitchMod
        }
        if (p.bendFrom != 0.0 && sec < p.bendTime * 4) f *= 2.0.pow(p.bendFrom * kotlin.math.exp(-sec / p.bendTime) / 12)
        var vibSemis = 0.0
        if (p.vibDepth > 0 && sec > p.vibDelay) {
            val fade = ((sec - p.vibDelay) / 0.5).coerceAtMost(1.0)
            vibSemis = p.vibDepth * fade
            lfoPh += p.vibRate * blockSec; if (lfoPh >= 1) lfoPh -= 1
            if (!isTable) f *= 2.0.pow(vibSemis * Wave.sin1(lfoPh) / 12)
        } else if (p.pwmRate > 0) {
            lfoPh += p.pwmRate * blockSec; if (lfoPh >= 1) lfoPh -= 1
        }
        val dt = f / rate
        var amp = p.gain * vel * duck
        if (p.cutoff > 0) {
            val track = (f / 261.6).pow(p.keyTrack)
            val cut = p.cutoff * track + p.envMod * fEnv * vel
            svf.set(cut, p.resonance, rate)
            fEnv *= kotlin.math.exp(-blockSec / p.filterDecay.coerceAtLeast(0.01)).toFloat()
        }
        var breath = 0f
        if (isTable) {
            // Each player of a section drifts a little in tune and has its own vibrato speed and phase.
            val spread = if (ensN > 1) p.detune else 0.0
            for (k in 0 until ensN) {
                val pos = if (ensN > 1) k.toDouble() / (ensN - 1) * 2 - 1 else 0.0
                tvib[k] += p.vibRate * (1 + 0.06 * k) * blockSec; if (tvib[k] >= 1) tvib[k] -= 1
                val drift = 1.5 * Wave.sinAny(sec * (0.11 + 0.07 * k) + k * 0.23 + seed)
                val cents = spread * pos + (if (ensN > 1) drift else 0.0) + 100 * vibSemis * Wave.sin1(tvib[k])
                tmul[k] = 1 + cents * 0.000577623 // 2^(c/1200) ≈ 1 + c·ln2/1200 for these few cents
            }
            // Louder playing is brighter: the filter opens with velocity and with the swell of the envelope.
            val dyn = rawVel * (0.35f + 0.65f * level)
            val b = p.bright
            val cut = when (osc) {
                Osc.Strings -> f * 2 + 1200 + 8000 * dyn * b
                Osc.Clarinet -> f * 2 + 500 + 4000 * dyn * b
                Osc.Horn -> f * 1.5 + 250 + 3800 * dyn * dyn * b
                Osc.ConcertFlute -> f * 3 + 1500 + 4000 * dyn * b
                else -> f * 2 + 1400 + 5000 * dyn * b
            }
            svf.set(cut.coerceAtMost(12000.0), 0.62, rate)
            val attackPart = kotlin.math.exp(-sec / 0.12).toFloat()
            when (osc) {
                Osc.Strings -> { bpf.set(2600.0, 0.8, rate); breath = (p.breath * (0.2 + 0.8 * attackPart) * 0.35).toFloat() }
                Osc.ConcertFlute -> { bpf.set(minOf(f * 2.0, 6000.0), 1.3, rate); breath = (p.breath * (0.5 + 1.5 * kotlin.math.exp(-sec / 0.05))).toFloat() }
                else -> { bpf.set(2200.0, 0.9, rate); breath = (p.breath * (0.3 + 0.7 * attackPart) * 0.3).toFloat() }
            }
            if (osc == Osc.Accordion) amp *= (1 + 0.07 * Wave.sinAny(sec * 0.45 + seed)).toFloat()
        }
        if (p.tremolo > 0 && stage < 2 && sec >= nextStrike) {
            val strength = 0.55 + 0.35 * noise.unit()
            if (osc == Osc.Plucked) pick(p, strength.toFloat() * 0.9f, fresh = false) else if (isModal) restrike(strength * 0.5)
            nextStrike += (1.0 / p.tremolo) * (0.9 + 0.2 * noise.unit())
        }
        if (p.fmDecay > 0) idxEnv *= kotlin.math.exp(-blockSec / p.fmDecay).toFloat()
        tineEnv *= kotlin.math.exp(-blockSec / 0.06).toFloat()
        if (osc == Osc.Flute) bpf.set(f * 2.0, 1.2, rate)
        val pw = (p.pw + p.pwmDepth * Wave.sin1(lfoPh)).coerceIn(0.05, 0.95)
        val idx = p.fmIndex * (0.35 + 0.65 * vel) * (if (p.fmDecay > 0) 0.25 + 0.75 * idxEnv else 1.0)
        val tine = tineEnv * vel
        val detuneUp = 2.0.pow(p.detune / 1200)
        val detuneDown = 1 / detuneUp
        if (osc == Osc.Pluck || osc == Osc.Plucked) {
            // Pitch bends move the string length.
            ksTuning = rate / f - 0.5 - ksDelay
        }
        val filtered = p.cutoff > 0 || isTable
        val sendRev = p.reverb
        val sendEcho = p.echo
        val sendCho = p.chorus
        val tab = table
        val tsize = Tables.SIZE.toDouble()
        var peak = 0f

        // --- audio rate ---
        while (i < n) {
            // Envelope
            when (stage) {
                0 -> { level += attackInc; if (level >= 1f) { level = 1f; stage = 1 } }
                1 -> level += (p.sustain - level) * decayCoef
                else -> level -= level * releaseCoef
            }
            if (stage < 2 && delay == 0) { if (gate > 0) gate-- else stage = 2 }
            var s: Double = when (osc) {
                Osc.Sine -> { ph[0] += dt; if (ph[0] >= 1) ph[0] -= 1; Wave.sin1(ph[0]).toDouble() }
                Osc.Triangle -> { ph[0] += dt; if (ph[0] >= 1) ph[0] -= 1; Wave.tri(ph[0]) * 0.9 + Wave.sin1(ph[0]) * 0.1 }
                Osc.ChipTriangle -> {
                    ph[0] += dt; if (ph[0] >= 1) ph[0] -= 1
                    kotlin.math.floor((Wave.tri(ph[0]) + 1) * 7.5) / 7.5 - 1
                }
                Osc.Saw -> {
                    ph[0] += dt; if (ph[0] >= 1) ph[0] -= 1
                    var v = Wave.saw(ph[0], dt)
                    if (p.unison > 1) {
                        val d1 = dt * detuneUp; ph[1] += d1; if (ph[1] >= 1) ph[1] -= 1; v += Wave.saw(ph[1], d1)
                        if (p.unison > 2) { val d2 = dt * detuneDown; ph[2] += d2; if (ph[2] >= 1) ph[2] -= 1; v += Wave.saw(ph[2], d2) }
                        v *= if (p.unison > 2) 0.5 else 0.65
                    }
                    v
                }
                Osc.Pulse -> {
                    ph[0] += dt; if (ph[0] >= 1) ph[0] -= 1
                    var v = Wave.pulse(ph[0], dt, pw)
                    if (p.unison > 1) { val d1 = dt * detuneUp; ph[1] += d1; if (ph[1] >= 1) ph[1] -= 1; v = (v + Wave.pulse(ph[1], d1, pw)) * 0.6 }
                    v * 0.7
                }
                Osc.Fm -> {
                    ph[0] += dt; if (ph[0] >= 1) ph[0] -= 1
                    modPh += dt * p.fmRatio; if (modPh >= 1) modPh -= kotlin.math.floor(modPh)
                    Wave.sinAny(ph[0] + idx * Wave.sin1(modPh) / (2 * Math.PI)).toDouble()
                }
                Osc.EPiano -> {
                    ph[0] += dt; if (ph[0] >= 1) ph[0] -= 1
                    // Warm FM body (brighter when played harder) plus a short bell-like "tine" at the attack.
                    val body = Wave.sinAny(ph[0] + idx * 0.16 * Wave.sin1(ph[0]))
                    val tn = if (tine > 0.01) (Wave.sinAny(ph[0] * 4.0) * 0.5 + Wave.sinAny(ph[0] * 7.0) * 0.3) * tine * 0.18 else 0.0
                    body + tn
                }
                Osc.Pluck, Osc.Plucked -> {
                    val d = ksDelay + ksTuning
                    val r = ksW - d
                    val ri = kotlin.math.floor(r).toInt()
                    val fr = (r - ri).toFloat()
                    val a = ks[ri and 4095]; val b = ks[(ri + 1) and 4095]
                    val y = a + (b - a) * fr
                    val out = (y * ksTone + ksLast * (1 - ksTone)) * ksGain
                    ksLast = y
                    ks[ksW and 4095] = out
                    ksW = (ksW + 1) and 0x3FFFFFFF
                    y.toDouble() * 1.6
                }
                Osc.Flute -> {
                    ph[0] += dt; if (ph[0] >= 1) ph[0] -= 1
                    val tone = Wave.sin1(ph[0]) + 0.12 * Wave.sinAny(ph[0] * 2) + 0.04 * Wave.sinAny(ph[0] * 3)
                    val br = bpf.bp(noise.next()) * p.breath * (0.4 + 0.6 * (if (sec < 0.1) 1.0 else 0.4))
                    tone + br
                }
                Osc.Noise -> noise.next().toDouble()
                Osc.Piano, Osc.Celesta, Osc.Glockenspiel, Osc.Timpani -> {
                    var acc = 0.0
                    for (k in 0 until mN) {
                        val re = mRe[k]; val im = mIm[k]
                        mRe[k] = re * mCr[k] - im * mCi[k]
                        val ni = re * mCi[k] + im * mCr[k]
                        mIm[k] = ni
                        acc += ni
                    }
                    if (ham > 1e-5f) { acc += hamLp.lp(noise.next()) * ham; ham *= hamDecay }
                    acc
                }
                Osc.Strings, Osc.Clarinet, Osc.Oboe, Osc.Accordion, Osc.Horn, Osc.ConcertFlute -> {
                    var acc = 0f
                    for (k in 0 until ensN) {
                        var x = tph[k] + dt * tmul[k]
                        if (x >= 1) x -= 1
                        tph[k] = x
                        val xi = x * tsize
                        val i0 = xi.toInt()
                        val a = tab[i0]
                        acc += a + (tab[i0 + 1] - a) * (xi - i0).toFloat()
                    }
                    acc *= ensGain
                    if (breath > 0f) acc += bpf.bp(noise.next()) * breath
                    acc.toDouble()
                }
            }
            if (filtered) s = svf.lp(s.toFloat()).toDouble()
            var e = level
            if (p.steppedEnv) e = kotlin.math.floor(e * 15f) / 15f
            val v = (s * e * amp).toFloat()
            val l = v * gl; val r = v * gr
            dryL[i] += l; dryR[i] += r
            if (sendRev > 0) { revL[i] += l * sendRev; revR[i] += r * sendRev }
            if (sendEcho > 0) echo[i] += v * sendEcho
            if (sendCho > 0) { choL[i] += l * sendCho; choR[i] += r * sendCho }
            val a = if (v < 0) -v else v
            if (a > peak) peak = a
            t++
            i++
        }
        if (peak > meter[part]) meter[part] = peak
        if (isModal) {
            // Drop partials that have died away, or that sit 50 dB under the strongest one (inaudible next to it);
            // the note ends when nothing is left. This keeps a pedalled piano cheap.
            var strongest = 0.0
            for (k in 0 until mN) strongest = maxOf(strongest, mRe[k] * mRe[k] + mIm[k] * mIm[k])
            val floor = maxOf(1e-8, strongest * 1e-5)
            var k = 0
            while (k < mN) {
                val mag = mRe[k] * mRe[k] + mIm[k] * mIm[k]
                if (mag < floor && (p.tremolo <= 0 || stage == 2)) {
                    mN--
                    mRe[k] = mRe[mN]; mIm[k] = mIm[mN]; mCr[k] = mCr[mN]; mCi[k] = mCi[mN]; mA0[k] = mA0[mN]
                } else k++
            }
            if (mN == 0 && ham < 1e-5f) active = false
        }
        // Inaudible: free the voice (released notes, and bells or plucks that have rung out while still held).
        if (level < 0.001f && (stage == 2 || (stage == 1 && p.sustain == 0f))) active = false
        if ((osc == Osc.Pluck || osc == Osc.Plucked) && p.tremolo <= 0 && stage >= 1 && peak < 0.00005f && t > rate / 10) active = false
    }

    companion object {
        val DEFAULT = Patch(Osc.Sine)
        const val MAX_MODES = 48
        const val MAX_ENS = 5
        private val EMPTY = FloatArray(Tables.SIZE + 1)
    }
}
