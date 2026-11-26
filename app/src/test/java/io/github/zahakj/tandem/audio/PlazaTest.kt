package io.github.zahakj.tandem.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * The Plaza soundtrack: every piece plays through cleanly, as loud as the others, and comes round its loop without a
 * jump in level. With TANDEM_MUSIC_OUT set to a folder, it also writes each piece there as a WAV to listen to (two
 * passes of a looping piece, so its seam is heard twice; the jingle once) with a table of the numbers.
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
        }
        val mean = levels.values.average()
        for ((id, l) in levels) check("$id is ${l - mean} dB from the others", abs(l - mean) <= 1.5)
        // For reference: the older soundtracks' first pieces.
        for (song in listOf(Calm.all.first(), Meadow.all.first())) {
            val r = render(song, passes = 1, tail = 0.0)
            report.append("%-16s peak %6.2f dBFS  loudness %6.2f LUFS (reference)\n".format(song.id, db(peak(r.data).toDouble()), loudness(r.data)))
        }
        println(report)
        out?.let { File(it, "levels.txt").writeText(report.toString()) }
        assertTrue(problems.joinToString("; "), problems.isEmpty())
    }

    @Test fun plazaFollowsTheSceneAndOthersDont() {
        assertEquals("plaza", Tracks.forScene("plaza", Tracks.Scene.Home)?.id)
        assertEquals("shop", Tracks.forScene("plaza", Tracks.Scene.Settings)?.id)
        assertEquals("lounge", Tracks.forScene("plaza", Tracks.Scene.Idle)?.id)
        assertNull(Tracks.forScene("calm", Tracks.Scene.Settings))
        assertNull(Tracks.forScene("meadow", Tracks.Scene.Home))
        assertNotNull(Tracks.jingle("plaza"))
        assertNull(Tracks.jingle("calm"))
        assertTrue(Plaza.passby.oneShot)
        assertEquals(Plaza.all, Tracks.of("plaza"))
        assertEquals(Plaza.all, Tracks.of("nonsense"))
        // The jingle is short; the pieces are long enough not to wear thin.
        assertTrue(Plaza.passby.seconds in 8.0..12.5)
        for (s in Plaza.all) assertTrue("${s.id} is ${s.seconds} s", s.seconds > 45)
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
