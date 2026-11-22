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

    @Test fun anEmulatorOpenedOutsideTandemIsNotNamedAfterTheLastGameTandemStartedInIt() {
        // GameNative opened by hand: its slot has no path, so the game Tandem last started there isn't named.
        val closing = CloseCheck.wouldClose(listOf(Slot(gamenative, null)), gamenative, balatro, anyApp = false, closePrevious = true)
        val library = mapOf(hades to "a game that isn't open")
        assertEquals(listOf<String?>(null), closing.map { CloseCheck.gameFor(it, library) })
        assertEquals("a game that isn't open", CloseCheck.gameFor(Slot(gamenative, hades), library))
    }

    @Test fun anEmulatorWithNoGameOfTandemsIsClosedWithoutAsking() {
        val closing = CloseCheck.wouldClose(listOf(Slot(gamenative, null), Slot(eden, zelda)), retroarch, celeste, anyApp = false, closePrevious = true)
        assertEquals(listOf(eden), CloseCheck.worthAsking(closing).map { it.pkg })
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

    // The question's words come from the string resources; here, whom it names and how.
    @Test fun oneGameIsNamedInTheQuestion() {
        assertEquals(CloseCheck.Who.One("Hades II"), CloseCheck.who(listOf(Entry("Hades II", "GameNative", hidden = false))))
        assertEquals("Hades II", CloseCheck.shownName(Entry("Hades II", "GameNative", hidden = false)))
    }

    @Test fun anEmulatorWithoutATandemGameIsNamedByItself() {
        assertEquals(CloseCheck.Who.One("RetroArch"), CloseCheck.who(listOf(Entry(null, "RetroArch", hidden = false))))
    }

    @Test fun severalGamesAreCounted() {
        assertEquals(CloseCheck.Who.Many(2), CloseCheck.who(listOf(Entry("Hades II", "GameNative", false), Entry("Zelda", "Eden", false))))
    }

    @Test fun aHiddenGameIsNeverNamedNorIsItsEmulator() {
        val secret = Entry("Secret Game", "GameNative", hidden = true)
        assertEquals(CloseCheck.Who.One(null), CloseCheck.who(listOf(secret)))
        assertEquals(null, CloseCheck.shownName(secret))
        // Among others, it is still left unnamed.
        assertEquals(CloseCheck.Who.Many(2), CloseCheck.who(listOf(Entry("Zelda", "Eden", false), secret)))
    }
}
