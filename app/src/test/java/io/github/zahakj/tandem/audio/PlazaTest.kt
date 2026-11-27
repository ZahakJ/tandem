package io.github.zahakj.tandem.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The built-in music, Plaza and its sister grooves Lagoon and Terrace: every groove plays through cleanly, as loud as
 * the others and quiet enough for background music, has no tune out in front, and comes round its loop without a
 * jump in level or a click. With TANDEM_MUSIC_OUT set to a folder, it also writes each piece there as a WAV to listen
 * to (two passes of a groove, so its seam is heard twice; the jingle once), a 45-second excerpt of each groove around
 * its first seam ("<id>-preview.wav"), and a table of the numbers.
 */
class PlazaTest {
    private val rate = 48000
    private val out = System.getenv("TANDEM_MUSIC_OUT")?.let { File(it).apply { mkdirs() } }

    private class Render(val data: FloatArray, val seams: List<Int>, val partPeaks: FloatArray)

    /** Renders [song] (interleaved stereo) for [passes] passes of its loop, noting the frame of every loop seam. */
    private fun render(song: Song, passes: Int, tail: Double): Render {
        val p = Player(song, rate, seed = 7)
        val loopSeconds = song.form.drop(song.loopFrom).sumOf { song.sections.getValue(it).bars } * song.beats * 60.0 / song.bpm
        val seconds = if (song.oneShot) p.lengthSeconds + tail else p.lengthSeconds + loopSeconds * (passes - 1) + tail
        val frames = (seconds * rate).toInt()
        val data = FloatArray(frames * 2)
        val block = FloatArray(64 * 2)
        val seams = ArrayList<Int>()
        val peaks = FloatArray(song.parts.size)
        var done = 0
        var loops = 0
        while (done < frames) {
            val n = minOf(64, frames - done)
            p.render(block, n)
            System.arraycopy(block, 0, data, done * 2, n * 2)
            if (p.loops != loops) { loops = p.loops; seams += done }
            for (k in peaks.indices) peaks[k] = maxOf(peaks[k], p.meters[k])
            done += n
        }
        return Render(data, seams, peaks)
    }

    private fun db(x: Double) = 20 * log10(x.coerceAtLeast(1e-9))

    /** A biquad, for the K-weighting filters. */
    private class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        fun f(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    /**
     * Integrated loudness in LUFS (ITU-R BS.1770 at 48 kHz): K-weighted (a high shelf for the head, a low cut), mean
     * square over 400 ms blocks overlapping by 75 %, gated at -70 LUFS and then 10 LU under the ungated mean.
     */
    private fun loudness(d: FloatArray): Double {
        val frames = d.size / 2
        val k = Array(2) {
            Biquad(1.53512485958697, -2.69169618940638, 1.19839281085285, -1.69065929318241, 0.73248077421585) to
                Biquad(1.0, -2.0, 1.0, -1.99004745483398, 0.99007225036621)
        }
        val sq = DoubleArray(frames)
        for (i in 0 until frames) {
            var s = 0.0
            for (c in 0..1) { val y = k[c].second.f(k[c].first.f(d[i * 2 + c].toDouble())); s += y * y }
            sq[i] = s
        }
        val w = (rate * 0.4).toInt()
        val blocks = ArrayList<Double>()
        var i = 0
        while (i + w <= frames) { var s = 0.0; for (j in i until i + w) s += sq[j]; blocks += s / w; i += w / 4 }
        fun lufs(ms: Double) = -0.691 + 10 * log10(ms.coerceAtLeast(1e-12))
        val abs = blocks.filter { lufs(it) > -70 }
        val rel = lufs(abs.average()) - 10
        return lufs(abs.filter { lufs(it) > rel }.average())
    }

    private fun rmsDb(d: FloatArray, from: Int, to: Int): Double {
        var s = 0.0
        for (k in from * 2 until to * 2) s += d[k] * d[k]
        return db(sqrt(s / ((to - from) * 2)))
    }

    /** The biggest jump between neighbouring samples (either channel) over frames [from] until [to]. */
    private fun maxStep(d: FloatArray, from: Int, to: Int): Float {
        var m = 0f
        for (i in maxOf(1, from) until minOf(to, d.size / 2)) for (c in 0..1) m = maxOf(m, abs(d[i * 2 + c] - d[(i - 1) * 2 + c]))
        return m
    }

    private fun peak(d: FloatArray): Float { var m = 0f; for (s in d) m = maxOf(m, abs(s)); return m }

    @Test fun piecesAreCleanLevelAndLoopSmoothly() {
        val problems = ArrayList<String>()
        fun check(what: String, ok: Boolean) { if (!ok) problems += what }
        val report = StringBuilder()
        val levels = HashMap<String, Double>()
        for (song in Plaza.all + Plaza.passby) {
            val t0 = System.nanoTime()
            val r = render(song, passes = 2, tail = if (song.oneShot) 1.6 else 3.0)
            val speed = r.data.size / 2.0 / rate / ((System.nanoTime() - t0) / 1e9)
            val pk = peak(r.data)
            val loud = loudness(r.data)
            levels[song.id] = loud
            report.append("%-7s %5.1f BPM  %4.1f s loop  %4.0fx real time  peak %6.2f dBFS  loudness %6.2f LUFS".format(
                song.id, song.bpm, song.seconds, speed, db(pk.toDouble()), loud))
            check("${song.id} peaks at ${db(pk.toDouble())} dBFS", pk < 0.708f)
            if (!song.oneShot) {
                check("${song.id} didn't loop twice", r.seams.size >= 2)
                // Around every seam: the bar before and the bar after are about as loud (a downbeat is naturally a
                // little louder than the end of the bar before it, so this is a bar each, not a few samples).
                val w = (song.beats * 60.0 / song.bpm * rate).toInt()
                for (s in r.seams) {
                    val before = rmsDb(r.data, s - w, s)
                    val after = rmsDb(r.data, s, s + w)
                    val edge = rmsDb(r.data, s, s + rate / 100) - rmsDb(r.data, s - rate / 100, s)
                    // The same 10 ms either side of an ordinary downbeat eight bars earlier, to compare with.
                    val d = s - 8 * w
                    val ref = rmsDb(r.data, d, d + rate / 100) - rmsDb(r.data, d - rate / 100, d)
                    // No click: the biggest sample-to-sample step within 5 ms of the seam, next to the biggest anywhere.
                    val near = maxStep(r.data, s - rate / 200, s + rate / 200)
                    check("${song.id} has a step of $near at its seam", near <= maxStep(r.data, 0, r.data.size / 2))
                    report.append("  seam @%.2fs bar %+.1f dB, 10 ms %+.1f dB (a downbeat 8 bars earlier %+.1f), step %.3f of %.3f".format(
                        s.toDouble() / rate, after - before, edge, ref, near, maxStep(r.data, 0, r.data.size / 2)))
                    check("${song.id} jumps ${after - before} dB at its seam", abs(after - before) < 3.0)
                }
            } else {
                // The jingle rings out into silence.
                val endLevel = rmsDb(r.data, r.data.size / 2 - rate / 10, r.data.size / 2)
                report.append("  end %.1f dB".format(endLevel))
                check("${song.id} doesn't ring out (${endLevel} dB)", endLevel < -45)
            }
            report.append('\n')
            report.append("        parts: " + song.parts.indices.joinToString(" ") { "%s %.2f".format(song.parts[it].name, r.partPeaks[it]) } + '\n')
            out?.let { writeWav(File(it, "${song.id}.wav"), r.data) }
            if (!song.oneShot) out?.let { writeWav(File(it, "${song.id}-preview.wav"), excerpt(r.data, r.seams[0] - 25 * rate, r.seams[0] + 20 * rate)) }
        }
        val mean = levels.values.average()
        for ((id, l) in levels) check("$id is ${l - mean} dB from the others", abs(l - mean) <= 1.5)
        // Every groove (and the jingle) at the same, background level: -23 LUFS give or take half a dB, a few dB
        // under the first Plaza (about -19.5 LUFS), so picking another never makes the music jump.
        for ((id, l) in levels) check("$id is at $l LUFS, not -23 +-0.5", l in -23.5..-22.5)
        println(report)
        out?.let { File(it, "levels.txt").writeText(report.toString()) }
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    @Test fun threeGroovesThatPlayEverywhere() {
        assertEquals(listOf("plaza", "lagoon", "terrace"), Plaza.all.map { it.id })
        assertEquals(Tracks.ids, Plaza.all.map { it.id })
        assertEquals(Tracks.DEFAULT, Tracks.ids.first())
        for (s in Plaza.all) assertEquals(s, Tracks.of(s.id))
        // A removed soundtrack, or nonsense, is Plaza.
        for (id in listOf("calm", "meadow", "shop", "lounge", "nonsense")) assertEquals("plaza", Tracks.of(id).id)
        assertTrue(Tracks.jingle.oneShot)
        assertEquals(Plaza.passby, Tracks.jingle)
        assertEquals(Plaza.all + Plaza.passby, Tracks.all)
        // Each its own: different keys (the first chord) and tempos.
        assertEquals(3, Plaza.all.map { it.bpm }.toSet().size)
        assertEquals(3, Plaza.all.map { it.sections.getValue(it.form[it.loopFrom]).chords.substringBefore(' ') }.toSet().size)
        // The jingle is short; the grooves loop over half a minute or more.
        assertTrue("passby is ${Plaza.passby.seconds} s", Plaza.passby.seconds in 6.0..10.0)
        for (s in Plaza.all) assertTrue("${s.id} is ${s.seconds} s", s.seconds > 30)
    }

    @Test fun groovesAreRhythmBeds() {
        val ornaments = setOf("lead", "whistle", "melody", "tune", "motif", "answer", "glock", "celesta", "vibes", "pad", "keys")
        for (s in Plaza.all) {
            // Only a chord instrument, a bass and percussion: no tune, no motif, no ornaments.
            for (p in s.parts) assertTrue("${s.id} has a ${p.name}", p.name !in ornaments)
            for ((name, sec) in s.sections) for (g in sec.gens) {
                assertTrue("${s.id}/$name writes out a line (${g::class.simpleName})", g !is MelodyGen && g !is PadGen && g !is ArpGen)
                if (g is DrumGen) assertTrue("${s.id}/$name has fills, a crash or a riser", g.fills.isEmpty() && !g.crash && !g.riser)
            }
            val bars = s.form.drop(s.loopFrom).sumOf { s.sections.getValue(it).bars }
            assertTrue("${s.id} loops over $bars bars", bars in 16..32 && bars % 2 == 0)
            // Every note always plays (nothing only sometimes, nothing only from the second pass)...
            val t = Compiler.compile(s)
            val loop = t.events.filter { it.start >= t.loopStart - 1e-6 }
            for (e in loop) assertTrue("${s.id}: ${s.parts[e.part].name} has a maybe-note", e.prob >= 1f && e.minLoop == 0)
            // ...and every part, the chord instrument above all, plays the very same rhythm every two bars: only the
            // pitches move, with the chords. A note outside its part's pattern would show up as a different bar.
            val period = 2 * s.stepsPerBar
            for (part in s.parts.indices) {
                val rhythms = loop.filter { it.part == part }
                    .groupBy { ((it.start - t.loopStart) / period).toInt() }
                    .mapValues { (_, evs) -> evs.map { (it.start - t.loopStart) % period }.toSortedSet() }
                assertEquals("${s.id}: ${s.parts[part].name} skips a bar", bars / 2, rhythms.size)
                assertEquals("${s.id}: ${s.parts[part].name} changes its rhythm", 1, rhythms.values.toSet().size)
            }
        }
    }

    @Test fun removedSoundtracksMoveToPlaza() {
        fun after(look: io.github.zahakj.tandem.data.Look) =
            io.github.zahakj.tandem.data.Store.keepSoundtrack(io.github.zahakj.tandem.data.Settings(welcomeDone = true, look = look)).look
        // New setups, and ones that never chose, play Plaza.
        assertEquals("plaza", after(io.github.zahakj.tandem.data.Look()).soundtrackId)
        // Calm and Meadow Tales are gone: picked or not, a setup that named one follows the default to Plaza.
        for (old in listOf("calm", "meadow", "something-else")) for (picked in listOf(false, true)) {
            val look = after(io.github.zahakj.tandem.data.Look(soundtrack = old, soundtrackPicked = picked))
            assertEquals(null, look.soundtrack)
            assertEquals(false, look.soundtrackPicked)
            assertEquals("plaza", look.soundtrackId)
        }
        // An old id read without the migration still reads as Plaza.
        assertEquals("plaza", io.github.zahakj.tandem.data.Look(soundtrack = "meadow").soundtrackId)
        // The three grooves, picked, stay.
        for (id in Tracks.ids) {
            val look = after(io.github.zahakj.tandem.data.Look(soundtrack = id, soundtrackPicked = true))
            assertEquals(id, look.soundtrackId)
            assertTrue(look.soundtrackPicked)
        }
    }

    /**
     * [d]'s frames from [from] until [to] with 0.5 s fades at either end, for a short preview around a seam. Not a
     * test of anything: the fades are only so the excerpt doesn't start or stop with a click.
     */
    private fun excerpt(d: FloatArray, from: Int, to: Int): FloatArray {
        val f = from.coerceAtLeast(0); val t = to.coerceAtMost(d.size / 2)
        val out = d.copyOfRange(f * 2, t * 2)
        val fade = rate / 2
        val n = out.size / 2
        for (i in 0 until minOf(fade, n)) {
            val g = i.toFloat() / fade
            for (c in 0..1) { out[i * 2 + c] *= g; out[(n - 1 - i) * 2 + c] *= g }
        }
        return out
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(2); putInt(rate); putInt(rate * 4); putShort(4); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        file.outputStream().use { it.write(header.array()); it.write(data.array()) }
    }
}
