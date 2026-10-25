package io.github.zahakj.tandem.audio

/**
 * Plays a compiled [Song] into stereo float buffers. All state is allocated in the constructor; [render] allocates
 * nothing, so it can run on an audio thread for hours without waking the garbage collector.
 *
 * Variation: every time the song loops, drum fills are picked again, lead parts may switch to their alternate patch,
 * ghost notes and timing are re-rolled, so the music never repeats exactly.
 */
class Player(val song: Song, val rate: Int, seed: Int = 1) {
    private val timeline = Compiler.compile(song)
    private val events = timeline.events
    private val voices = Array(MAX_VOICES) { Voice(rate) }
    private val drums = Array(MAX_DRUMS) { DrumVoice(rate) }
    private val parts = song.parts
    private val mix = song.mix

    private val baseSamplesPerStep = rate * 60.0 / song.bpm / 4
    private val tempoMap = timeline.tempo
    /** Samples per step right now (the tempo map bends it for rubato). */
    private var samplesPerStep = baseSamplesPerStep / tempoMap[0]
    private var pos = 0.0 // in steps
    private var next = 0
    private var loop = 0
    private var ageCounter = 0L
    private val rnd = Noise(seed * 7919 + 17)
    private val groupChoice = IntArray(timeline.groupVariants.size + 1)
    private var stealDrum = 0
    private val monoVoice = IntArray(parts.size) { -1 }
    var finished = false; private set

    // Buses for one block.
    private val dryL = FloatArray(BLOCK); private val dryR = FloatArray(BLOCK)
    private val revL = FloatArray(BLOCK); private val revR = FloatArray(BLOCK)
    private val echoIn = FloatArray(BLOCK)
    private val choL = FloatArray(BLOCK); private val choR = FloatArray(BLOCK)

    private val reverb = Reverb(rate).apply { configure(mix.reverbSize, mix.reverbDamp) }
    private val hall = if (mix.hall) Hall(rate).apply { configure(mix.reverbSize, mix.reverbDamp, mix.hallDecay, mix.preDelayMs) } else null
    private val glue = if (mix.glue > 0) BusComp(rate).apply { thresholdDb = -12.0 - 12.0 * mix.glue; ratio = 1.0 + mix.glue } else null
    // Mastering for the tape-warmed acoustic songs: a gentle "air" shelf (about +5 dB from 6 kHz up), then a soft
    // roll-off of the very top, as on a well-recorded tape.
    private val airL = OnePole().apply { set(4500.0, rate) }
    private val airR = OnePole().apply { set(4500.0, rate) }
    private val topL = OnePole().apply { set(17000.0, rate) }
    private val topR = OnePole().apply { set(17000.0, rate) }
    private val echo = Echo(rate).apply {
        delaySamples = (samplesPerStep * mix.echoSteps).toInt().coerceIn(1, rate * 2 - 1)
        feedback = mix.echoFeedback
    }
    private val chorus = Chorus(rate).apply { rateHz = mix.chorusRate; depthMs = mix.chorusDepth }
    private val limiter = Limiter(rate)
    private val lofiL = Svf().apply { if (mix.lofi > 0) set(mix.lofi, 0.6, rate) }
    private val lofiR = Svf().apply { if (mix.lofi > 0) set(mix.lofi, 0.6, rate) }
    private val dcL = OnePole().apply { set(18.0, rate) }
    private val dcR = OnePole().apply { set(18.0, rate) }
    private val crackleNoise = Noise(99)
    private val rainNoise = Noise(4242)
    private val rainL = Svf().apply { set(2600.0, 0.5, rate) }
    private val rainR = Svf().apply { set(2900.0, 0.5, rate) }
    private val rainLow = OnePole().apply { set(0.6, rate) }
    private val crackleTone = OnePole().apply { set(2500.0, rate) }
    private var popLevel = 0f
    private var duckEnv = 0f
    private val duckCoef = coef(mix.duckRelease * 60 / song.bpm / 3, rate)
    private var wowPh = 0.0
    private var flutterPh = 0.0

    /** Peak level of each part over the last few blocks, for visualizers (0..1, decays). */
    val meters = FloatArray(parts.size)
    private val blockMeters = FloatArray(parts.size)

    /** How many times the song has looped. */
    val loops: Int get() = loop

    /** Seconds into the song (resets when it loops). */
    val seconds: Double get() = secondsAt(pos)
    val lengthSeconds: Double = secondsAt(timeline.end)

    private fun secondsAt(step: Double): Double {
        var s = 0.0
        val whole = step.toInt().coerceAtMost(tempoMap.size - 1)
        for (i in 0 until whole) s += 1.0 / tempoMap[i]
        s += (step - whole) / tempoMap[whole]
        return s * baseSamplesPerStep / rate
    }

    init {
        rollVariations()
        // Build the wavetables of every note this song plays now, so the audio loop never has to.
        for (e in events) {
            val part = parts[e.part]
            for (p in listOfNotNull(part.patch, part.alt)) if (Tables.isTable(p.osc)) Tables.get(p.osc, e.note + 12 * p.octave, rate)
        }
    }

    private fun rollVariations() {
        for (g in 1 until groupChoice.size) {
            val n = timeline.groupVariants[g - 1]
            // The plain groove 25 % of the time, otherwise one of the fills.
            groupChoice[g] = if (rnd.unit() < 0.25f || n <= 1) 0 else 1 + (rnd.unit() * (n - 1)).toInt().coerceAtMost(n - 2)
        }
    }

    /** Renders [frames] stereo frames, interleaved, into [out] (length >= 2 * frames). */
    fun render(out: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val n = minOf(BLOCK, frames - done)
            renderBlock(out, done * 2, n)
            done += n
        }
        for (p in meters.indices) {
            meters[p] = maxOf(blockMeters[p], meters[p] * 0.86f)
            blockMeters[p] = 0f
        }
    }

    private fun swung(step: Double): Double {
        if (song.swing == 0.0) return step
        // Swing pushes every off-beat 8th later (and the 16ths around it proportionally), like an MPC's swing.
        val s = step / 2
        val whole = kotlin.math.floor(s)
        val swingShift = if (whole.toLong() % 2 == 1L) song.swing * (1 - (s - whole)) * 2 else 0.0
        return step + swingShift
    }

    private fun renderBlock(out: FloatArray, offset: Int, n: Int) {
        dryL.fill(0f, 0, n); dryR.fill(0f, 0, n); revL.fill(0f, 0, n); revR.fill(0f, 0, n)
        echoIn.fill(0f, 0, n); choL.fill(0f, 0, n); choR.fill(0f, 0, n)
        samplesPerStep = baseSamplesPerStep / tempoMap[pos.toInt().coerceIn(0, tempoMap.size - 1)]
        val blockSteps = n / samplesPerStep

        // Trigger every event that starts inside this block.
        while (!finished) {
            if (next >= events.size || events[next].start >= timeline.end) {
                if (pos + blockSteps < timeline.end) break
                if (song.oneShot) { finished = true; break }
                // Loop: jump back, keeping the fractional position so the groove stays steady.
                pos -= timeline.end - timeline.loopStart
                next = timeline.loopStartIndex
                loop++
                rollVariations()
                continue
            }
            val e = events[next]
            val at = (swung(e.start + e.nudge) - pos) * samplesPerStep
            if (at >= n) break
            trigger(e, at.toInt().coerceIn(0, n - 1))
            next++
        }

        // Tape wobble: slow wow plus a little flutter.
        var pitchMod = 1.0
        if (mix.wow > 0) {
            wowPh += n.toDouble() / rate * 0.55; if (wowPh >= 1) wowPh -= 1
            flutterPh += n.toDouble() / rate * 6.3; if (flutterPh >= 1) flutterPh -= 1
            val cents = mix.wow * (Wave.sin1(wowPh) + 0.25 * Wave.sin1(flutterPh))
            pitchMod = Math.pow(2.0, cents / 1200)
        }

        // Sidechain: kicks push the envelope up; it relaxes over the block.
        for (d in drums) if (d.active && d.ducks && d.delay < n) { duckEnv = 1f; d.ducks = false }
        val duckNow = duckEnv
        duckEnv *= Math.pow((1 - duckCoef).toDouble(), n.toDouble()).toFloat()

        for (v in voices) {
            if (!v.active) continue
            val duck = 1f - v.patch.duck * duckNow * duckNow.coerceAtMost(1f)
            v.render(n, pitchMod, duck, dryL, dryR, revL, revR, echoIn, choL, choR, blockMeters)
        }
        for (d in drums) if (d.active) d.render(n, dryL, dryR, revL, revR)

        hall?.control(n)
        glue?.control(n)
        val tape = mix.tape
        val revRet = mix.reverbReturn
        val echoRet = mix.echoReturn
        val choRet = mix.chorusReturn
        val crackle = mix.crackle.toFloat()
        val rain = mix.rain.toFloat()
        for (i in 0 until n) {
            var wetL: Float
            var wetR: Float
            if (hall != null) { hall.process(revL[i], revR[i]); wetL = hall.outL; wetR = hall.outR }
            else { reverb.process(revL[i], revR[i]); wetL = reverb.outL; wetR = reverb.outR }
            echo.process(echoIn[i], echoIn[i])
            chorus.process(choL[i], choR[i])
            var l = dryL[i] + wetL * revRet + echo.outL * echoRet + chorus.outL * choRet
            var r = dryR[i] + wetR * revRet + echo.outR * echoRet + chorus.outR * choRet
            if (glue != null) { glue.detect(l, r); val g = glue.gain(); l *= g; r *= g }
            if (tape > 0f) {
                l = Tape.warm(l, tape); r = Tape.warm(r, tape)
                l = topL.lp(l + 0.9f * (l - airL.lp(l))); r = topR.lp(r + 0.9f * (r - airR.lp(r)))
            }
            if (mix.lofi > 0) { l = lofiL.lp(l); r = lofiR.lp(r) }
            if (crackle > 0) {
                // Vinyl: a soft hiss bed and sparse pops.
                if (crackleNoise.unit() < 0.00005f * crackle) popLevel = (0.02f + 0.06f * crackleNoise.unit()) * crackle
                val pop = crackleTone.lp(crackleNoise.next() * popLevel)
                popLevel *= 0.93f
                val hiss = crackleNoise.next() * 0.0025f * crackle
                l += hiss + pop; r += hiss * 0.9f + pop * 0.8f
            }
            if (rain > 0) {
                // Rain on a window: band-passed noise with a slowly breathing level.
                val swell = 0.75f + 0.25f * rainLow.lp(rainNoise.next() * 6f).coerceIn(-1f, 1f)
                l += rainL.bp(rainNoise.next()) * 0.05f * rain * swell
                r += rainR.bp(rainNoise.next()) * 0.05f * rain * swell
            }
            l = dcL.hp(l) * mix.master
            r = dcR.hp(r) * mix.master
            limiter.process(l, r, out, offset + i * 2)
        }
        pos += blockSteps
    }

    private fun trigger(e: Ev, offset: Int) {
        if (e.minLoop > loop) return
        if (e.group > 0 && groupChoice[e.group] != e.variant) return
        if (e.prob < 1f && rnd.unit() > e.prob) return
        val part = parts[e.part]
        val human = if (mix.humanize > 0) (rnd.unit() * mix.humanize * rate / 1000).toInt() else 0
        val vel = (e.vel * (1f + mix.velJitter * rnd.next())).coerceIn(0.05f, 1f)
        val kit = part.kit
        if (kit != null) {
            val d = Drum.entries[e.note]
            if (d == Drum.HatClosed) for (v in drums) if (v.active && v.drum == Drum.HatOpen) v.choke()
            var v = drums[0]
            var found = false
            for (d0 in drums) if (!d0.active) { v = d0; found = true; break }
            if (!found) { v = drums[stealDrum]; stealDrum = (stealDrum + 1) % drums.size }
            if (vel > blockMeters[e.part]) blockMeters[e.part] = vel * 0.8f
            v.start(d, kit, vel, offset + human, (e.len * samplesPerStep).toInt())
            return
        }
        val patch = if (part.alt != null && loop % 2 == 1) part.alt else part.patch!!
        val gate = (e.len * samplesPerStep * 0.92).toInt().coerceAtLeast(rate / 100)
        if (patch.mono) {
            val idx = monoVoice[e.part]
            if (idx >= 0) {
                val v = voices[idx]
                if (v.active && v.part == e.part) {
                    v.start(patch, e.part, e.note, vel, offset + human, gate, e.chord, legato = true)
                    v.age = ++ageCounter
                    return
                }
            }
        }
        // The same key struck again: its damper stops the old note (pianos, harps, mallets).
        if (patch.sustain >= 1f || patch.pedal) for (o in voices) if (o.active && o.part == e.part && o.midi == e.note && o.delay == 0) o.damp()
        val v = freeVoice()
        v.start(patch, e.part, e.note, vel, offset + human, gate, e.chord, legato = false)
        v.age = ++ageCounter
        if (patch.mono) monoVoice[e.part] = voices.indexOf(v)
    }

    private fun freeVoice(): Voice {
        var best: Voice? = null
        for (v in voices) if (!v.active) return v
        // Steal the quietest releasing voice, else the oldest.
        for (v in voices) if (best == null || v.level < best.level) best = v
        return best!!
    }

    companion object {
        const val BLOCK = 64
        const val MAX_VOICES = 40
        const val MAX_DRUMS = 10
    }
}

/** Renders a whole one-shot song (jingles) into a mono buffer. */
fun renderOneShot(song: Song, rate: Int, tailSeconds: Double = 0.8): FloatArray {
    val p = Player(song, rate)
    val frames = ((song.seconds + tailSeconds) * rate).toInt()
    val st = FloatArray(frames * 2)
    p.render(st, frames)
    val mono = FloatArray(frames) { (st[it * 2] + st[it * 2 + 1]) * 0.5f }
    // Drop the silent tail, then fade the last 20 ms so nothing clicks.
    var end = frames
    while (end > rate / 4 && kotlin.math.abs(mono[end - 1]) < 0.0005f) end--
    val out = mono.copyOf(end)
    val fade = minOf(end, rate / 50)
    for (i in 0 until fade) out[end - 1 - i] *= i.toFloat() / fade
    return out
}
