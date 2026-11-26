package io.github.zahakj.tandem.audio

import io.github.zahakj.tandem.data.Look
import io.github.zahakj.tandem.data.Settings
import io.github.zahakj.tandem.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** The Calm soundtrack plays through cleanly and quieter than Meadow Tales; Plaza is the default, picked soundtracks stay. */
class CalmTest {
    private val rate = 48000

    /** Peak and RMS over one pass of [song] and a few seconds into its loop. */
    private fun levels(song: Song): Pair<Float, Double> {
        val p = Player(song, rate)
        val frames = ((p.lengthSeconds + 4) * rate).toInt()
        val buf = FloatArray(1024 * 2)
        var peak = 0f
        var sum = 0.0
        var n = 0L
        var done = 0
        while (done < frames) {
            p.render(buf, 1024)
            for (s in buf) { peak = maxOf(peak, abs(s)); sum += s * s; n++ }
            done += 1024
        }
        assertTrue("${song.id} didn't loop", p.loops >= 1)
        return peak to sqrt(sum / n)
    }

    @Test fun calmIsUnclippedAndQuieterThanMeadow() {
        val meadow = levels(Meadow.all.first()).second
        for (song in Calm.all) {
            val (peak, rms) = levels(song)
            println("%-14s peak %.3f rms %.4f (meadow %.4f) %.0f s".format(song.id, peak, rms, meadow, song.seconds))
            assertTrue("${song.id} peaks at $peak", peak < 0.7f)
            assertTrue("${song.id} is silent", rms > 0.002)
            assertTrue("${song.id} isn't quieter than Meadow Tales", rms < meadow)
        }
    }

    @Test fun plazaIsTheDefaultButPickedSoundtracksStay() {
        // New setups, and ones that never chose, play Plaza.
        assertEquals("plaza", Store.keepSoundtrack(Settings()).look.soundtrackId)
        assertEquals("plaza", Store.keepSoundtrack(Settings(welcomeDone = true)).look.soundtrackId)
        // Calm as the welcome wrote it down (the old default) follows the default to Plaza...
        val oldDefault = Settings(welcomeDone = true, look = Look(soundtrack = "calm"))
        assertEquals("plaza", Store.keepSoundtrack(oldDefault).look.soundtrackId)
        // ...but Calm picked on purpose stays, and so does Meadow Tales.
        val picked = Settings(welcomeDone = true, look = Look(soundtrack = "calm", soundtrackPicked = true))
        assertEquals("calm", Store.keepSoundtrack(picked).look.soundtrackId)
        assertEquals("meadow", Store.keepSoundtrack(Settings(welcomeDone = true, look = Look(soundtrack = "meadow"))).look.soundtrackId)
        // Plaza picked stays Plaza.
        assertEquals("plaza", Store.keepSoundtrack(Settings(look = Look(soundtrack = "plaza", soundtrackPicked = true))).look.soundtrackId)
        assertEquals(Calm.all, Tracks.of("calm"))
        assertEquals(Meadow.all, Tracks.of("meadow"))
    }
}
