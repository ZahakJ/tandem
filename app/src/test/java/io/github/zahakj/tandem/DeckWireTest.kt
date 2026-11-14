package io.github.zahakj.tandem

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the Deck's keys and clicks are written out for the shell, and how events are patched for the binder (DeckWire.kt). */
class DeckWireTest {
    @Test fun keysNameTheDisplay() {
        assertEquals("input -d 0 keyevent 29", DeckShell.key(DeckCodes.A))
        assertEquals("input -d 0 keyevent 66", DeckShell.key(DeckCodes.ENTER, meta = 0))
    }

    @Test fun modifiersBecomeAKeyCombination() {
        val ctrl = DeckModifiers.meta(listOf(DeckModifiers.Mod.Ctrl))
        assertEquals("input -d 0 keycombination 113 29", DeckShell.key(DeckCodes.A, ctrl))
        val all = DeckModifiers.meta(DeckModifiers.Mod.entries)
        assertEquals("input -d 0 keycombination 59 113 57 ${DeckCodes.letter('s')}", DeckShell.key(DeckCodes.letter('s'), all))
    }

    @Test fun textIsQuotedForTheShell() {
        assertEquals("input -d 0 text 'hello'", DeckShell.text("hello"))
        assertEquals("input -d 0 text 'two%swords'", DeckShell.text("two words"))
        assertEquals("input -d 0 text 'it'\\''s'", DeckShell.text("it's"))
        assertEquals("input -d 0 text '\$HOME;rm'", DeckShell.text("\$HOME;rm"))
        assertNull(DeckShell.text(""))
        assertNull(DeckShell.text("a\nb"))
    }

    @Test fun clicksLandAtThePointer() {
        assertEquals("input -d 0 tap 960 540", DeckShell.tap(960, 540))
        assertEquals("input -d 0 swipe 960 540 960 540 600", DeckShell.hold(960, 540))
        assertEquals("input -d 0 swipe 10 20 10 20 300", DeckShell.hold(10, 20, 300))
    }

    @Test fun scrollIsAShortSwipe() {
        // A finger moving down the pad pulls content down: the swipe goes up the screen.
        assertEquals("input -d 0 swipe 960 540 960 300 150", DeckShell.scroll(960, 540, 2, 1080))
        assertEquals("input -d 0 swipe 960 540 960 660 150", DeckShell.scroll(960, 540, -1, 1080))
        assertNull(DeckShell.scroll(960, 540, 0, 1080))
        // Pinned at the edge, there is nowhere to swipe to.
        assertNull(DeckShell.scroll(960, 0, 3, 1080))
        assertEquals("input -d 0 swipe 960 1079 960 0 150", DeckShell.scroll(960, 1079, 20, 1080))
    }

    private fun ints(vararg v: Int): ByteArray = ByteArray(v.size * 4).also { b -> v.forEachIndexed { i, x -> DeckParcel.writeInt(b, i * 4, x) } }

    @Test fun intsAreLittleEndian() {
        val b = ints(0x04030201, -1)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, -1, -1, -1, -1), b)
        assertEquals(0x04030201, DeckParcel.readInt(b, 0))
        assertEquals(-1, DeckParcel.readInt(b, 4))
    }

    @Test fun keyEventDisplayIsPatchedWhenTheLayoutMatches() {
        val source = 0x101 // SOURCE_KEYBOARD
        // token, id, device, source, display, hmac (null), action, key code, repeat, meta, scan code, flags
        val b = ints(DeckParcel.TOKEN_KEY, 7, -1, source, -1, -1, 0, DeckCodes.A, 0, 0, 0, 0)
        assertTrue(DeckParcel.keyDisplay(b, deviceId = -1, source = source, action = 0, keyCode = DeckCodes.A, displayId = 0))
        assertEquals(0, DeckParcel.readInt(b, 16))
        // Nothing else moved.
        assertEquals(DeckCodes.A, DeckParcel.readInt(b, 28))
        assertEquals(DeckParcel.TOKEN_KEY, DeckParcel.readInt(b, 0))
    }

    @Test fun keyEventWithAnHmacIsFoundPastIt() {
        val source = 0x101
        val hmac = IntArray(8) { 0x11111111 }
        val b = ints(DeckParcel.TOKEN_KEY, 7, -1, source, -1, 32, *hmac, 1, DeckCodes.ENTER, 0, 0)
        assertTrue(DeckParcel.keyDisplay(b, -1, source, action = 1, keyCode = DeckCodes.ENTER, displayId = 4))
        assertEquals(4, DeckParcel.readInt(b, 16))
    }

    @Test fun keyEventWithAnotherLayoutIsLeftAlone() {
        val source = 0x101
        val original = ints(DeckParcel.TOKEN_KEY, 7, -1, source, -1, -1, 0, DeckCodes.A, 0, 0, 0, 0)
        val wrongToken = original.copyOf().also { DeckParcel.writeInt(it, 0, DeckParcel.TOKEN_MOTION) }
        assertFalse(DeckParcel.keyDisplay(wrongToken, -1, source, 0, DeckCodes.A, 0))
        // The key code where the layout says it is doesn't match: an older Android (no id field) shifted everything.
        val older = ints(DeckParcel.TOKEN_KEY, -1, source, -1, 0, DeckCodes.A, 0, 0, 0, 0, 0, 0)
        assertFalse(DeckParcel.keyDisplay(older, -1, source, 0, DeckCodes.A, 0))
        assertEquals(-1, DeckParcel.readInt(older, 12))
        val short = ints(DeckParcel.TOKEN_KEY, 7, -1)
        assertFalse(DeckParcel.keyDisplay(short, -1, source, 0, DeckCodes.A, 0))
    }

    @Test fun motionEventDisplayAndButtonArePatched() {
        val source = 0x2002 // SOURCE_MOUSE
        val action = 11 // ACTION_BUTTON_PRESS
        val hmac = IntArray(8)
        // token, pointers, samples, id, device, source, display, hmac, action, action button, flags, edge, meta, button state
        val b = ints(DeckParcel.TOKEN_MOTION, 1, 1, 9, 0, source, 0, 32, *hmac, action, 0, 0, 0, 0, 1)
        assertTrue(DeckParcel.motionFields(b, pointerCount = 1, source = source, action = action, buttonState = 1, displayId = 0, actionButton = 1))
        assertEquals(1, DeckParcel.readInt(b, 64 + 4))
        assertEquals(1, DeckParcel.readInt(b, 64 + 20))
        assertEquals(0, DeckParcel.readInt(b, 24))
    }

    @Test fun motionEventWithAnotherLayoutIsLeftAlone() {
        val source = 0x2002
        val hmac = IntArray(8)
        val b = ints(DeckParcel.TOKEN_MOTION, 1, 1, 9, 0, source, 0, 32, *hmac, 7, 0, 0, 0, 0, 0)
        // Built as a hover (action 7) but asked to patch a press: the check fails and nothing is written.
        val before = b.copyOf()
        assertFalse(DeckParcel.motionFields(b, 1, source, action = 11, buttonState = 1, displayId = 0, actionButton = 1))
        assertArrayEquals(before, b)
        assertFalse(DeckParcel.motionFields(b, 2, source, 7, 0, 0, 0))
        assertFalse(DeckParcel.motionFields(ints(DeckParcel.TOKEN_MOTION, 1), 1, source, 7, 0, 0, 0))
    }
}
