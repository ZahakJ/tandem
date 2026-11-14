package io.github.zahakj.tandem

import io.github.zahakj.tandem.ScreenOff.Show
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bottom screen going dark while a game plays (ScreenOff.kt): mode, idle time and wakes, and what the panel shows. */
class ScreenOffTest {
    private val minute = 60_000L

    /**
     * A panel as the companion runs it: it came up at time 0, is touched (or lit by Home) at [touches], and [at] some
     * time says what it shows, as the activity would work it out from its clock.
     */
    private class Panel(val dark: Boolean, val offAfterMs: Long = 0, val reading: Boolean = false) {
        private var touchedAt: Long? = null
        fun touch(at: Long) { touchedAt = at }
        fun at(now: Long): Show = ScreenOff.show(dark, offAfterMs, touchedAt?.let { now - it }, now, reading)
        fun nextChange(now: Long): Long? = ScreenOff.nextChangeMs(dark, offAfterMs, touchedAt?.let { now - it }, now, reading)
    }

    @Test fun gameArtDimsAfterAMinuteAndStaysUpWithoutATimer() {
        val p = Panel(dark = false)
        assertEquals(Show.Bright, p.at(0))
        assertEquals(Show.Bright, p.at(minute - 1))
        assertEquals(Show.Dim, p.at(minute))
        assertEquals(Show.Dim, p.at(3 * 60 * minute))
        // The dim is the only change coming; once dimmed, nothing more until a touch.
        assertEquals(minute, p.nextChange(0))
        assertNull(p.nextChange(minute))
    }

    @Test fun aTouchWakesADimmedPanelAndStartsTheMinuteOver() {
        val p = Panel(dark = false)
        p.touch(2 * minute)
        assertEquals(Show.Bright, p.at(2 * minute))
        assertEquals(Show.Bright, p.at(3 * minute - 1))
        assertEquals(Show.Dim, p.at(3 * minute))
        assertEquals(minute / 2, p.nextChange(2 * minute + minute / 2))
    }

    @Test fun offAfterTurnsAnyModeDarkUntilTouched() {
        val p = Panel(dark = false, offAfterMs = 2 * minute)
        assertEquals(Show.Bright, p.at(0))
        assertEquals(Show.Dim, p.at(minute))
        assertEquals(Show.Dark, p.at(2 * minute))
        assertEquals(Show.Dark, p.at(60 * minute))
        // Dim first, then dark, then nothing scheduled.
        assertEquals(minute, p.nextChange(0))
        assertEquals(minute, p.nextChange(minute))
        assertNull(p.nextChange(2 * minute))
        p.touch(60 * minute)
        assertEquals(Show.Bright, p.at(60 * minute))
        assertEquals(Show.Dim, p.at(61 * minute))
        assertEquals(Show.Dark, p.at(62 * minute))
    }

    @Test fun offAfterOneMinuteSkipsTheDim() {
        val p = Panel(dark = false, offAfterMs = minute)
        assertEquals(Show.Bright, p.at(minute - 1))
        assertEquals(Show.Dark, p.at(minute))
        assertEquals(minute, p.nextChange(0))
    }

    @Test fun screenOffIsDarkFromTheStart() {
        val p = Panel(dark = true)
        assertEquals(Show.Dark, p.at(0))
        assertEquals(Show.Dark, p.at(10 * minute))
        // Nothing to wait for: a dark panel schedules nothing until it is touched.
        assertNull(p.nextChange(0))
    }

    @Test fun aTouchLightsAScreenOffPanelForAMinute() {
        val p = Panel(dark = true)
        p.touch(5 * minute)
        assertEquals(Show.Bright, p.at(5 * minute))
        assertEquals(Show.Bright, p.at(6 * minute - 1))
        assertEquals(Show.Dark, p.at(6 * minute))
        assertEquals(minute, p.nextChange(5 * minute))
        assertNull(p.nextChange(6 * minute))
        // Lit, it never goes through the dimmed stage: straight back to black.
        assertFalse((5 * minute until 7 * minute step 1000).any { p.at(it) == Show.Dim })
    }

    @Test fun offAfterSaysHowLongATouchLightsAScreenOffPanel() {
        val p = Panel(dark = true, offAfterMs = 5 * minute)
        p.touch(0)
        assertEquals(Show.Bright, p.at(4 * minute))
        assertEquals(Show.Dark, p.at(5 * minute))
        assertEquals(5 * minute, p.nextChange(0))
    }

    @Test fun homeBringingTheGameBackIsAWakeLikeAnyTouch() {
        // The activity records Home (HomeBack) and coming back on show the same way as a touch.
        val p = Panel(dark = true)
        assertEquals(Show.Dark, p.at(minute))
        p.touch(minute)
        assertEquals(Show.Bright, p.at(minute + 1))
    }

    @Test fun aNoteBeingReadNeverGoesDimOrDark() {
        assertEquals(Show.Bright, Panel(dark = true, reading = true).at(10 * minute))
        assertEquals(Show.Bright, Panel(dark = false, offAfterMs = minute, reading = true).at(10 * minute))
        assertNull(Panel(dark = false, reading = true).nextChange(0))
    }

    @Test fun onlyATouchOnABrightPanelReachesWhatIsUnderTheFinger() {
        assertFalse(ScreenOff.touchOnlyWakes(Show.Bright))
        assertTrue(ScreenOff.touchOnlyWakes(Show.Dim))
        assertTrue(ScreenOff.touchOnlyWakes(Show.Dark))
    }

    @Test fun theMostParticularChoiceWins() {
        val art = GameCompanion.ART
        val dark = GameCompanion.DARK
        val tandem = GameCompanion.TANDEM
        // Nothing chosen: the setting for everyone.
        assertEquals(dark, CompanionMode.resolve(game = null, system = null, dual = false, default = dark))
        assertEquals(art, CompanionMode.resolve(null, null, dual = false, default = art))
        // The system's choice beats the setting for everyone; the game's beats the system's.
        assertEquals(tandem, CompanionMode.resolve(null, tandem, dual = false, default = dark))
        assertEquals(art, CompanionMode.resolve(art, tandem, dual = false, default = dark))
        // A dual-screen emulator keeps its screen unless its system or the game says otherwise.
        assertEquals(GameCompanion.OFF, CompanionMode.resolve(null, null, dual = true, default = dark))
        assertEquals(dark, CompanionMode.resolve(null, dark, dual = true, default = art))
    }

    @Test fun offAfterLabels() {
        assertEquals("Never", ScreenOff.offAfterLabel(0))
        assertEquals("After 5 min", ScreenOff.offAfterLabel(5))
        assertEquals(listOf(0, 1, 2, 5, 10), ScreenOff.OFF_AFTER_MINUTES)
    }

    /** Trimmed `dumpsys display` from Android 13 with two built-in screens in the one power group. */
    private val dump = """
        DISPLAY MANAGER (dumpsys display)
          Display Devices: size=2
            DisplayDeviceInfo{"Built-in Screen": uniqueId="local:4630946441858561667", 1920 x 1080, modeId 1, state ON, committedState ON, brightnessMinimum 0.0, brightnessMaximum 1.0, FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE}
              mAdapter=LocalDisplayAdapter
              mUniqueId=local:4630946441858561667
              mBrightnessState=0.39763778
            DisplayDeviceInfo{"Built-in Screen 2": uniqueId="local:4630946482288158084", 1240 x 1080, modeId 2, state ON, committedState ON, brightnessMinimum 0.0, brightnessMaximum 1.0, FLAG_ROTATES_WITH_CONTENT, FLAG_PRESENTATION}
              mAdapter=LocalDisplayAdapter
              mUniqueId=local:4630946482288158084
              mBrightnessState=0.25

          Logical Displays: size=2
            Display 0:
              mDisplayId=0
              mPhase=1
              mPrimaryDisplayDevice=Built-in Screen
              mBaseDisplayInfo=DisplayInfo{"Built-in Screen", displayId 0, displayGroupId 0, FLAG_SECURE, FLAG_TRUSTED, real 1920 x 1080, state ON, type INTERNAL}
            Display 4:
              mDisplayId=4
              mPhase=1
              mPrimaryDisplayDevice=Built-in Screen 2
              mBaseDisplayInfo=DisplayInfo{"Built-in Screen 2", displayId 4, displayGroupId 0, FLAG_PRESENTATION, FLAG_TRUSTED, real 1240 x 1080, state ON, type INTERNAL}

          Display Power Controller:
    """.trimIndent()

    @Test fun readsTheBottomDisplaysGroupStateAndBrightness() {
        val bottom = ScreenOff.displayFacts(dump, 4)!!
        assertEquals(0, bottom.groupId)
        assertFalse(bottom.ownGroup)
        assertEquals("ON", bottom.state)
        assertEquals(0.25f, bottom.brightness!!, 0.0001f)
        val top = ScreenOff.displayFacts(dump, 0)!!
        assertEquals(0.39763778f, top.brightness!!, 0.0001f)
        assertNull(ScreenOff.displayFacts(dump, 2))
        assertNull(ScreenOff.displayFacts("", 4))
    }

    @Test fun aDisplayInItsOwnGroupIsNoticed() {
        val own = dump.replace("FLAG_ROTATES_WITH_CONTENT, FLAG_PRESENTATION}", "FLAG_OWN_DISPLAY_GROUP, FLAG_PRESENTATION}")
            .replace("displayId 4, displayGroupId 0", "displayId 4, displayGroupId 1")
        val bottom = ScreenOff.displayFacts(own, 4)!!
        assertEquals(1, bottom.groupId)
        assertTrue(bottom.ownGroup)
    }
}
