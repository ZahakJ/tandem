package io.github.zahakj.tandem.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Renders every UI sound and launch jingle and checks their levels and lengths. With TANDEM_SFX_OUT set to a folder,
 * it also writes them there as WAV files to listen to, with a table of peak, loudness and length.
 */
class SfxTest {
    private val rate = 48000
    private val out = System.getenv("TANDEM_SFX_OUT")?.let { File(it).apply { mkdirs() } }
    private val report = StringBuilder()

    private fun check(name: String, data: FloatArray, maxSeconds: Double) {
        var peak = 0f
        for (s in data) peak = maxOf(peak, abs(s))
        val seconds = data.size.toDouble() / rate
        val loud = ModernSfx.dbfs(ModernSfx.loudness(data, rate))
        report.append("%-28s peak %6.1f dBFS  loud %6.1f dB  %4.0f ms%n".format(name, ModernSfx.dbfs(peak.toDouble()), loud, seconds * 1000))
        out?.let { writeWav(File(it, "$name.wav"), data) }
        assertTrue("$name peaks at ${ModernSfx.dbfs(peak.toDouble())} dBFS", peak < 0.708f)
        assertTrue("$name is ${seconds}s long", seconds <= maxSeconds)
        // Ends in silence, so SoundPool never stops it on a click.
        assertTrue("$name doesn't end quietly", abs(data.last()) < 1e-3f)
    }

    @Test fun everySoundIsShortQuietAndUnclipped() {
        for (pack in SoundPack.entries) for (k in Sfx.entries) {
            val max = when {
                pack == SoundPack.Modern && (k == Sfx.Move || k == Sfx.Key) -> 0.06
                k == Sfx.Launch -> 0.7
                else -> 2.0
            }
            check("${pack.id}-${k.name}", SfxSynth.render(pack, k, rate), max)
        }
        for (f in Family.entries) check("jingle-launch-${f.name}", renderJingle(Jingles.launch(f), rate, 0.6f, LAUNCH_JINGLE_SECONDS), 0.7)
        out?.let { File(it, "levels.txt").writeText(report.toString()) }
        println(report)
    }

    /** The Modern set is levelled by loudness: everything of one kind within a couple of dB of its target. */
    @Test fun modernLoudnessIsConsistent() {
        val ui = listOf(Sfx.Select, Sfx.Back, Sfx.ToggleOn, Sfx.ToggleOff, Sfx.MenuOpen, Sfx.MenuClose, Sfx.Toast, Sfx.Swap)
            .map { ModernSfx.dbfs(ModernSfx.loudness(SfxSynth.render(SoundPack.Modern, it, rate), rate)) }
        assertTrue("UI sounds spread over ${ui.max() - ui.min()} dB", ui.max() - ui.min() < 3.0)
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) data.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data.capacity())
        }
        file.outputStream().use { it.write(header.array()); it.write(data.array()) }
    }
}
