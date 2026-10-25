package io.github.zahakj.tandem

import io.github.zahakj.tandem.CloseCheck.Entry
import io.github.zahakj.tandem.CloseCheck.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What starting a game would close (CloseCheck.kt), and how the question names it, without Android. */
class CloseCheckTest {
    private val retroarch = "com.retroarch"
    private val eden = "dev.eden.eden_emulator"
    private val gamenative = "app.gamenative"

    private val celeste = "/ROMs/snes/Celeste.sfc"
    private val mario = "/ROMs/snes/Super Mario World.sfc"
    private val hades = "/ROMs/windows/Hades II.pcgame"
    private val balatro = "/ROMs/windows/Balatro.pcgame"
    private val zelda = "/ROMs/switch/Zelda.nsp"

    private fun close(running: List<Slot>, pkg: String, path: String, closePrevious: Boolean, anyApp: Boolean = false) =
        CloseCheck.wouldClose(running, pkg, path, anyApp, closePrevious).map { it.pkg }

    @Test fun nothingRunningClosesNothing() {
        assertEquals(emptyList<String>(), close(emptyList(), retroarch, celeste, closePrevious = true))
    }

    @Test fun theSameGameStillRunningResumesWithoutClosingAnything() {
        val running = listOf(Slot(retroarch, celeste), Slot(eden, zelda))
        assertEquals(emptyList<String>(), close(running, retroarch, celeste, closePrevious = false))
        // A resume never closes the others either, even with "Close previous game" on.
        assertEquals(emptyList<String>(), close(running, retroarch, celeste, closePrevious = true))
    }

    @Test fun aDifferentGameInTheSameEmulatorClosesIt() {
        val running = listOf(Slot(retroarch, mario))
        assertEquals(listOf(retroarch), close(running, retroarch, celeste, closePrevious = false))
        assertEquals(listOf(retroarch), close(running, retroarch, celeste, closePrevious = true))
    }

    @Test fun oneEmulatorHoldsManyGames() {
        // GameNative runs every PC game: switching between two of them closes it, picking the same one resumes.
        val running = listOf(Slot(gamenative, hades))
        assertEquals(listOf(gamenative), close(running, gamenative, balatro, closePrevious = false))
        assertEquals(emptyList<String>(), close(running, gamenative, hades, closePrevious = false))
    }

    @Test fun anEmulatorOpenedOutsideTandemCountsAsAnotherGame() {
        val running = listOf(Slot(retroarch, null))
        assertEquals(listOf(retroarch), close(running, retroarch, celeste, closePrevious = false))
    }

    @Test fun closePreviousDecidesWhetherOtherEmulatorsClose() {
        val running = listOf(Slot(eden, zelda), Slot(gamenative, hades))
        assertEquals(listOf(eden, gamenative), close(running, retroarch, celeste, closePrevious = true))
        assertEquals(emptyList<String>(), close(running, retroarch, celeste, closePrevious = false))
    }

    @Test fun theSameEmulatorComesFirstThenTheOthersInOrder() {
        val running = listOf(Slot(eden, zelda), Slot(retroarch, mario), Slot(gamenative, hades))
        assertEquals(listOf(retroarch, eden, gamenative), close(running, retroarch, celeste, closePrevious = true))
        assertEquals(listOf(retroarch), close(running, retroarch, celeste, closePrevious = false))
    }

    @Test fun anyAppShortcutsNeverResumeOrCloseTheirOwnApp() {
        val running = listOf(Slot(eden, zelda))
        assertEquals(listOf(eden), close(running, "*", celeste, closePrevious = true, anyApp = true))
        assertEquals(emptyList<String>(), close(running, "*", celeste, closePrevious = false, anyApp = true))
    }

    @Test fun oneGameIsNamedInTheQuestion() {
        val (title, body) = CloseCheck.headline(listOf(Entry("Hades II", "GameNative", hidden = false)), "Celeste")
        assertEquals("Hades II is still running", title)
        assertEquals("Starting Celeste will close it. Anything you haven't saved there will be lost.", body)
        assertEquals("Resume Hades II", CloseCheck.resumeLabel(Entry("Hades II", "GameNative", hidden = false)))
    }

    @Test fun anEmulatorWithoutATandemGameIsNamedByItself() {
        val (title, _) = CloseCheck.headline(listOf(Entry(null, "RetroArch", hidden = false)), "Celeste")
        assertEquals("RetroArch is still running", title)
    }

    @Test fun severalGamesAreCounted() {
        val (title, body) = CloseCheck.headline(
            listOf(Entry("Hades II", "GameNative", false), Entry("Zelda", "Eden", false)), "Celeste",
        )
        assertEquals("2 games are still running", title)
        assertTrue(body.startsWith("Starting Celeste will close them all."))
    }

    @Test fun aHiddenGameIsNeverNamedNorIsItsEmulator() {
        val secret = Entry("Secret Game", "GameNative", hidden = true)
        val (title, body) = CloseCheck.headline(listOf(secret), "Celeste")
        assertEquals("A hidden game is still running", title)
        for (text in listOf(title, body, CloseCheck.name(secret), CloseCheck.resumeLabel(secret))) {
            assertFalse(text, text.contains("Secret"))
            assertFalse(text, text.contains("GameNative"))
        }
        assertEquals("A hidden game", CloseCheck.name(secret))
        // Among others, it is still left unnamed.
        val (many, _) = CloseCheck.headline(listOf(Entry("Zelda", "Eden", false), secret), "Celeste")
        assertEquals("2 games are still running", many)
    }
}
