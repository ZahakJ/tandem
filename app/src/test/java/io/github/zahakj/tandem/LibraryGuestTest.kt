package io.github.zahakj.tandem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the bottom-screen home shows Tandem's library, and who gets button presses then (LibraryGuest.kt). */
class LibraryGuestTest {
    private val app = SecondaryHome.Front.App("com.discord")

    @Test fun anAppOnTopPutsTheLibraryBelow() {
        assertTrue(LibraryGuest.wanted(app, mainIdle = true, defaultHome = true, singleScreen = false))
    }

    @Test fun otherwiseThePanelStays() {
        // A game (its companion has the screen), nothing on top, no library activity to borrow, another Home app,
        // or single-screen mode.
        assertFalse(LibraryGuest.wanted(SecondaryHome.Front.Game("org.ppsspp.ppsspp"), mainIdle = true, defaultHome = true, singleScreen = false))
        assertFalse(LibraryGuest.wanted(null, mainIdle = true, defaultHome = true, singleScreen = false))
        assertFalse(LibraryGuest.wanted(app, mainIdle = false, defaultHome = true, singleScreen = false))
        assertFalse(LibraryGuest.wanted(app, mainIdle = true, defaultHome = false, singleScreen = false))
        assertFalse(LibraryGuest.wanted(app, mainIdle = true, defaultHome = true, singleScreen = true))
    }

    @Test fun onlyTheLibraryBelowGetsButtons() {
        val below = Any()
        val top = Any()
        assertTrue(LibraryGuest.accepts(below, guestClock = below))
        assertFalse(LibraryGuest.accepts(top, guestClock = below))
        assertFalse(LibraryGuest.accepts(null, guestClock = below))
        // Not borrowed: everyone, as before.
        assertTrue(LibraryGuest.accepts(top, guestClock = null))
        assertTrue(LibraryGuest.accepts(null, guestClock = null))
    }
}
