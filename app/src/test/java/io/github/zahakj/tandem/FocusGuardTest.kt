package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Taking the controller back when another app's window holds the focus on the bottom display (FocusGuard.kt). */
class FocusGuardTest {
    private val own = "io.github.zahakj.tandem"

    private fun out(bottomFocus: String, topFocus: String, top: Int) = """
          Display: mDisplayId=4 (organized)
          mCurrentFocus=$bottomFocus
          Display: mDisplayId=0 (organized)
          mCurrentFocus=$topFocus
          mTopFocusedDisplayId=$top
    """.trimIndent()

    /** What the AYN Thor showed after Cemu crashed: Discord, opened earlier on the bottom screen, took the keys. */
    private val stray = out(
        "Window{4f1a2b u0 com.discord/com.discord.main.MainActivity}",
        "Window{dfc51d0 u0 io.github.zahakj.tandem/io.github.zahakj.tandem.MainActivity}",
        top = 4,
    )

    @Test fun readsTheFocusOfEachDisplay() {
        val f = FocusGuard.parse(stray)!!
        assertEquals(4, f.top)
        assertEquals("com.discord", f.byDisplay[4])
        assertEquals(own, f.byDisplay[0])
    }

    @Test fun readsNoFocusAndBadOutput() {
        val f = FocusGuard.parse(out("null", "Window{1 u0 $own/.MainActivity}", top = 0))!!
        assertTrue(4 in f.byDisplay)
        assertNull(f.byDisplay[4])
        assertNull(FocusGuard.parse(null))
        assertNull(FocusGuard.parse("Can't find service: window"))
    }

    @Test fun anotherAppOnTheBottomDisplayIsTakenBack() {
        assertEquals(FocusGuard.Fix.CoverBottom, FocusGuard.decide(FocusGuard.parse(stray), own, bottom = 4, ownsBottom = true))
    }

    @Test fun evenWhenTheTopDisplayHasTheKeys() {
        // The keys are fine, but the bottom screen shows that app's navigation bar.
        val f = FocusGuard.parse(out("Window{1 u0 com.android.launcher3/.secondarydisplay.SecondaryDisplayLauncher}", "Window{2 u0 $own/.MainActivity}", top = 0))
        assertEquals(FocusGuard.Fix.CoverBottom, FocusGuard.decide(f, own, bottom = 4, ownsBottom = true))
    }

    @Test fun nothingFocusedOnTheDisplayWithTheKeys() {
        val f = FocusGuard.parse(out("null", "Window{2 u0 $own/.MainActivity}", top = 4))
        assertEquals(FocusGuard.Fix.CoverBottom, FocusGuard.decide(f, own, bottom = 4, ownsBottom = true))
        // Nothing focused down there while the top screen has the keys: nothing in the way.
        val calm = FocusGuard.parse(out("null", "Window{2 u0 $own/.MainActivity}", top = 0))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decide(calm, own, bottom = 4, ownsBottom = true))
    }

    @Test fun tandemsOwnWindowsAreFine() {
        val home = FocusGuard.parse(out("Window{6788ae8 u0 $own/$own.SecondaryHomeActivity}", "Window{2 u0 $own/.MainActivity}", top = 4))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decide(home, own, bottom = 4, ownsBottom = true))
        val bare = FocusGuard.parse(out("Window{6788ae8 u0 $own}", "Window{2 u0 $own/.MainActivity}", top = 4))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decide(bare, own, bottom = 4, ownsBottom = true))
    }

    @Test fun systemWindowsAreLeftAlone() {
        assertFalse(FocusGuard.isForeign("NotificationShade", own))
        assertFalse(FocusGuard.isForeign("com.android.systemui", own))
        assertFalse(FocusGuard.isForeign("android", own))
        assertFalse(FocusGuard.isForeign(null, own))
        assertTrue(FocusGuard.isForeign("org.mozilla.ironfox", own))
        val shade = FocusGuard.parse(out("Window{1 u0 NotificationShade}", "Window{2 u0 $own/.MainActivity}", top = 4))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decide(shade, own, bottom = 4, ownsBottom = true))
    }

    @Test fun anAppTandemOpenedOnTheBottomScreenKeepsIt() {
        // An app or game started down there on purpose, single-screen mode, or no bottom screen: nothing to take back.
        val f = FocusGuard.parse(stray)
        assertEquals(FocusGuard.Fix.None, FocusGuard.decide(f, own, bottom = 4, ownsBottom = false))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decide(f, own, bottom = null, ownsBottom = true))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decide(f, own, bottom = 7, ownsBottom = true))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decide(null, own, bottom = 4, ownsBottom = true))
    }

    @Test fun withoutShizukuOnlyHomeCounts() {
        assertEquals(FocusGuard.Fix.CoverBottom, FocusGuard.decideBlind(home = true, libraryFocused = false, bottomHomeFocused = false, dreaming = false, ownsBottom = true))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decideBlind(home = false, libraryFocused = false, bottomHomeFocused = false, dreaming = false, ownsBottom = true))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decideBlind(home = true, libraryFocused = true, bottomHomeFocused = false, dreaming = false, ownsBottom = true))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decideBlind(home = true, libraryFocused = false, bottomHomeFocused = true, dreaming = false, ownsBottom = true))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decideBlind(home = true, libraryFocused = false, bottomHomeFocused = false, dreaming = true, ownsBottom = true))
        assertEquals(FocusGuard.Fix.None, FocusGuard.decideBlind(home = true, libraryFocused = false, bottomHomeFocused = false, dreaming = false, ownsBottom = false))
    }
}
