package io.github.zahakj.tandem.ui

import android.view.KeyEvent
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Trying layouts, the try-on owns L1 / R1, L2 / R2 and Start on every layout, even one put on after it opened (whose
 * handlers are newer), and A / B are its Keep and Cancel.
 */
class TryOnDispatchTest {
    @Test
    fun frontHandlerGoesBeforeNewerOnes() {
        val seen = ArrayList<String>()
        val tryOn = Buttons.intercept({ b -> seen += "try-on"; b == Button.R1 }, front = true)
        // A layout put on while trying: registered later, so before this it saw R1 first.
        val layout = Buttons.intercept({ seen += "layout"; true })
        try {
            Buttons.press(Button.R1)
            assertEquals(listOf("try-on"), seen)
            seen.clear()
            // What the front handler lets through still reaches the layout.
            Buttons.press(Button.X)
            assertEquals(listOf("try-on", "layout"), seen)
        } finally {
            layout()
            tryOn()
        }
    }

    @Test
    fun withoutFrontTheNewestStillGoesFirst() {
        val seen = ArrayList<String>()
        val older = Buttons.intercept({ seen += "older"; true })
        val newer = Buttons.intercept({ seen += "newer"; true })
        try {
            Buttons.press(Button.R1)
            assertEquals(listOf("newer"), seen)
        } finally {
            newer()
            older()
        }
    }

    @Test
    fun keysKeepAndCancel() {
        assertEquals(TryOnKeys.Role.Keep, TryOnKeys.of(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(TryOnKeys.Role.Keep, TryOnKeys.of(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(TryOnKeys.Role.Keep, TryOnKeys.of(KeyEvent.KEYCODE_ENTER))
        assertEquals(TryOnKeys.Role.Cancel, TryOnKeys.of(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(TryOnKeys.Role.Cancel, TryOnKeys.of(KeyEvent.KEYCODE_BACK))
        // The D-pad goes through to the layout.
        assertNull(TryOnKeys.of(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(TryOnKeys.of(KeyEvent.KEYCODE_DPAD_RIGHT))
    }
}
