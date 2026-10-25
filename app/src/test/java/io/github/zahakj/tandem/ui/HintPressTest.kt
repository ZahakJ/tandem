package io.github.zahakj.tandem.ui

import android.view.KeyEvent
import io.github.zahakj.tandem.Button
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a tapped button hint presses (Touch.kt): the same button the controller would. */
class HintPressTest {
    @Test fun aAndBAreTheirKeysAfterTheSwaps() {
        // The hint names the logical button, so a swap is already applied: A always selects, B always goes back.
        assertEquals(HintPress.Key(KeyEvent.KEYCODE_BUTTON_A), HintPress.of("A"))
        assertEquals(HintPress.Key(KeyEvent.KEYCODE_BUTTON_B), HintPress.of("B"))
    }

    @Test fun theOtherButtonsGoThroughButtons() {
        for (b in Button.entries) assertEquals(HintPress.Pad(b), HintPress.of(b.name))
        assertEquals(HintPress.Pad(Button.Start), HintPress.of("Start"))
        assertEquals(HintPress.Pad(Button.L2), HintPress.of("L2"))
    }

    @Test fun dpadHintsPressTheArmNearestTheTap() {
        val across = HintPress.of("◀▶") as HintPress.Dpad
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, across.key(0.1f))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, across.key(0.9f))
        val along = HintPress.of("▲▼") as HintPress.Dpad
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, along.key(0.2f))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, along.key(0.7f))
    }

    @Test fun unknownNamesPressNothing() {
        assertNull(HintPress.of(""))
        assertNull(HintPress.of("Home"))
        assertNull(HintPress.of("a"))
    }
}
