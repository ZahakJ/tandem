package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.ui.ControlsGuard.Fix
import org.junit.Assert.assertEquals
import org.junit.Test

/** What happens when the library on the bottom screen can't take a key (ScreenRoles.kt, BottomScreen.kt). */
class ControlsGuardTest {
    private fun decide(onBottom: Boolean = true, focused: Boolean = false, loaded: Boolean = true, scanning: Boolean = false, home: Boolean = true) =
        ControlsGuard.decide(onBottom, focused, loaded, scanning, home)

    @Test fun focusedOrNotSwappedNeedsNothing() {
        assertEquals(Fix.None, decide(focused = true))
        assertEquals(Fix.None, decide(onBottom = false))
        assertEquals(Fix.None, decide(onBottom = false, loaded = false))
    }

    /** The reported bug: the splash on the bottom screen with no scan running. Load, don't swap. */
    @Test fun noLibraryAndNothingLoadingItLoadsIt() {
        assertEquals(Fix.Load, decide(loaded = false, scanning = false))
        assertEquals(Fix.Wait, decide(loaded = false, scanning = true))
    }

    @Test fun aLoadedHomeThatCantTakeTheControlsSwapsBack() {
        assertEquals(Fix.SwapBack, decide())
        // Another screen may simply have nothing to focus; B still leads home.
        assertEquals(Fix.None, decide(home = false))
    }
}
