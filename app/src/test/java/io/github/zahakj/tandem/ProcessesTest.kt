package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading `ps` and telling an emulator Tandem started from one opened again since (Running.kt). */
class ProcessesTest {
    private val now = 1_700_000_000_000L

    @Test fun elapsedTimes() {
        assertEquals(64L, Processes.elapsedSeconds("01:04"))
        assertEquals(3 * 3600L + 64, Processes.elapsedSeconds("03:01:04"))
        assertEquals(86_400L + 3600 + 1, Processes.elapsedSeconds("1-01:00:01"))
        assertNull(Processes.elapsedSeconds("ELAPSED"))
        assertNull(Processes.elapsedSeconds("12"))
    }

    @Test fun psNamesAndStarts() {
        val out = """
                ELAPSED NAME
               16:27:45 io.github.zahakj.tandem
                  02:10 app.gamenative
                  01:58 A:\English\KSP_x64.exe
             2-00:00:00 dev.eden.eden_emulator:EmulationProcess
        """.trimIndent()
        val (names, started) = Processes.parsePs(out, now)
        assertTrue("app.gamenative" in names)
        assertTrue("A:\\English\\KSP_x64.exe" in names)
        assertTrue("dev.eden.eden_emulator:EmulationProcess" in names)
        assertEquals(now - 130_000, started["app.gamenative"])
        assertEquals(now - 2 * 86_400_000L, started["dev.eden.eden_emulator:EmulationProcess"])
        assertNull(started["ELAPSED NAME"])
    }

    @Test fun restartedOnlyWhenItCameUpLater() {
        val launched = now - 3_600_000
        assertFalse(Processes.restartedSince(launched + 4_000, launched))
        assertFalse(Processes.restartedSince(launched - 60_000, launched))
        assertTrue(Processes.restartedSince(launched + 600_000, launched))
        // Unknown either way: never a restart.
        assertFalse(Processes.restartedSince(null, launched))
        assertFalse(Processes.restartedSince(now, 0))
    }

    @Test fun snapshotAsksPerPackage() {
        val launched = now - 3_600_000
        val snap = Processes.Snapshot(setOf("app.gamenative"), null, mapOf("app.gamenative" to now - 130_000))
        assertTrue(snap.restartedSince("app.gamenative", launched))
        assertFalse(snap.restartedSince("org.example.other", launched))
    }

    @Test fun sameGameNeedsARecordAndNoLaterStart() {
        val launched = now - 3_600_000
        val gn = "app.gamenative"
        fun snap(vararg started: Pair<String, Long>) = Processes.Snapshot(started.map { it.first }.toSet(), null, started.toMap())
        // Started by Tandem, still the same process and game.
        assertTrue(Processes.sameGame(snap(gn to launched + 2_000, "A:\\Blue Prince\\BluePrince.exe" to launched + 40_000), gn, launched, windows = true))
        // Closed by Tandem since (no record), then opened by hand.
        assertFalse(Processes.sameGame(snap(gn to now - 60_000), gn, 0, windows = true))
        // The app was opened again later.
        assertFalse(Processes.sameGame(snap(gn to now - 60_000), gn, launched, windows = true))
        // Same app process, but another game started in it much later; Wine's own programs don't count.
        val switched = snap(gn to launched + 2_000, "A:\\English\\KSP_x64.exe" to now - 600_000)
        assertFalse(Processes.sameGame(switched, gn, launched, windows = true))
        assertTrue(Processes.sameGame(switched, gn, launched, windows = false))
        assertTrue(Processes.sameGame(snap(gn to launched + 2_000, "C:\\windows\\system32\\winedevice.exe" to now), gn, launched, windows = true))
        // Nothing seen: the record stands.
        assertTrue(Processes.sameGame(null, gn, launched, windows = true))
    }
}
