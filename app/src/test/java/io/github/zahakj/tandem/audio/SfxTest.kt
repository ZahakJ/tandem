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

    /** Zero crossings a second: a rough measure of how bright a sound is. */
    private fun brightness(data: FloatArray): Double {
        var n = 0
        for (i in 1 until data.size) if ((data[i - 1] < 0f) != (data[i] < 0f)) n++
        return n * rate.toDouble() / data.size
    }

    // Only the navigation sounds: in the others the little tune keeps its notes, only warmer.
    @Test fun thockIsDeeperThanModern() {
        for (k in listOf(Sfx.Move, Sfx.Key, Sfx.Page)) {
            val modern = brightness(SfxSynth.render(SoundPack.Modern, k, rate))
            val thock = brightness(SfxSynth.render(SoundPack.Thock, k, rate))
            report.append("%-10s brightness modern %6.0f  thock %6.0f%n".format(k.name, modern, thock))
            assertTrue("Thock ${k.name} ($thock) isn't clearly deeper than Modern's ($modern)", thock < modern * 0.7)
        }
        println(report)
    }

    @Test fun everySoundIsShortQuietAndUnclipped() {
        for (pack in SoundPack.entries) for (k in Sfx.entries) {
            val max = when {
                ModernSfx.Timbre.of(pack) != null && (k == Sfx.Move || k == Sfx.Key) -> 0.06
                k == Sfx.Launch -> 0.7
                else -> 2.0
            }
            check("${pack.id}-${k.name}", SfxSynth.render(pack, k, rate), max)
        }
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
