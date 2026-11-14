package io.github.zahakj.tandem

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Deck's keys, layouts and sticky modifiers (DeckKeys.kt). */
class DeckKeysTest {
    @Test fun codesMatchAndroids() {
        // The constants are written out so the file has no Android dependency; here the SDK's own values check them.
        assertEquals(KeyEvent.KEYCODE_A, DeckCodes.letter('a'))
        assertEquals(KeyEvent.KEYCODE_Z, DeckCodes.letter('Z'))
        assertEquals(KeyEvent.KEYCODE_0, DeckCodes.digit('0'))
        assertEquals(KeyEvent.KEYCODE_9, DeckCodes.digit('9'))
        assertEquals(KeyEvent.KEYCODE_F1, DeckCodes.function(1))
        assertEquals(KeyEvent.KEYCODE_F12, DeckCodes.function(12))
        assertEquals(KeyEvent.KEYCODE_NUMPAD_0, DeckCodes.numpad(0))
        assertEquals(KeyEvent.KEYCODE_NUMPAD_9, DeckCodes.numpad(9))
        val pairs = listOf(
            DeckCodes.DPAD_UP to KeyEvent.KEYCODE_DPAD_UP, DeckCodes.DPAD_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            DeckCodes.DPAD_LEFT to KeyEvent.KEYCODE_DPAD_LEFT, DeckCodes.DPAD_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
            DeckCodes.COMMA to KeyEvent.KEYCODE_COMMA, DeckCodes.PERIOD to KeyEvent.KEYCODE_PERIOD,
            DeckCodes.ALT_LEFT to KeyEvent.KEYCODE_ALT_LEFT, DeckCodes.SHIFT_LEFT to KeyEvent.KEYCODE_SHIFT_LEFT,
            DeckCodes.TAB to KeyEvent.KEYCODE_TAB, DeckCodes.SPACE to KeyEvent.KEYCODE_SPACE, DeckCodes.ENTER to KeyEvent.KEYCODE_ENTER,
            DeckCodes.DEL to KeyEvent.KEYCODE_DEL, DeckCodes.GRAVE to KeyEvent.KEYCODE_GRAVE, DeckCodes.MINUS to KeyEvent.KEYCODE_MINUS,
            DeckCodes.EQUALS to KeyEvent.KEYCODE_EQUALS, DeckCodes.LEFT_BRACKET to KeyEvent.KEYCODE_LEFT_BRACKET,
            DeckCodes.RIGHT_BRACKET to KeyEvent.KEYCODE_RIGHT_BRACKET, DeckCodes.BACKSLASH to KeyEvent.KEYCODE_BACKSLASH,
            DeckCodes.SEMICOLON to KeyEvent.KEYCODE_SEMICOLON, DeckCodes.APOSTROPHE to KeyEvent.KEYCODE_APOSTROPHE,
            DeckCodes.SLASH to KeyEvent.KEYCODE_SLASH, DeckCodes.PAGE_UP to KeyEvent.KEYCODE_PAGE_UP, DeckCodes.PAGE_DOWN to KeyEvent.KEYCODE_PAGE_DOWN,
            DeckCodes.ESCAPE to KeyEvent.KEYCODE_ESCAPE, DeckCodes.FORWARD_DEL to KeyEvent.KEYCODE_FORWARD_DEL,
            DeckCodes.CTRL_LEFT to KeyEvent.KEYCODE_CTRL_LEFT, DeckCodes.CAPS_LOCK to KeyEvent.KEYCODE_CAPS_LOCK,
            DeckCodes.MOVE_HOME to KeyEvent.KEYCODE_MOVE_HOME, DeckCodes.MOVE_END to KeyEvent.KEYCODE_MOVE_END,
            DeckCodes.INSERT to KeyEvent.KEYCODE_INSERT, DeckCodes.NUM_LOCK to KeyEvent.KEYCODE_NUM_LOCK,
            DeckCodes.NUMPAD_DIVIDE to KeyEvent.KEYCODE_NUMPAD_DIVIDE, DeckCodes.NUMPAD_MULTIPLY to KeyEvent.KEYCODE_NUMPAD_MULTIPLY,
            DeckCodes.NUMPAD_SUBTRACT to KeyEvent.KEYCODE_NUMPAD_SUBTRACT, DeckCodes.NUMPAD_ADD to KeyEvent.KEYCODE_NUMPAD_ADD,
            DeckCodes.NUMPAD_DOT to KeyEvent.KEYCODE_NUMPAD_DOT, DeckCodes.NUMPAD_ENTER to KeyEvent.KEYCODE_NUMPAD_ENTER,
            DeckCodes.META_SHIFT_ON to KeyEvent.META_SHIFT_ON, DeckCodes.META_SHIFT_LEFT_ON to KeyEvent.META_SHIFT_LEFT_ON,
            DeckCodes.META_ALT_ON to KeyEvent.META_ALT_ON, DeckCodes.META_ALT_LEFT_ON to KeyEvent.META_ALT_LEFT_ON,
            DeckCodes.META_CTRL_ON to KeyEvent.META_CTRL_ON, DeckCodes.META_CTRL_LEFT_ON to KeyEvent.META_CTRL_LEFT_ON,
        )
        for ((ours, androids) in pairs) assertEquals(androids, ours)
    }

    @Test fun labelsMapToCodes() {
        assertEquals(KeyEvent.KEYCODE_A, DeckLayouts.codeOf("a"))
        assertEquals(KeyEvent.KEYCODE_ENTER, DeckLayouts.codeOf("Enter"))
        assertEquals(KeyEvent.KEYCODE_DEL, DeckLayouts.codeOf("⌫"))
        assertEquals(KeyEvent.KEYCODE_SPACE, DeckLayouts.codeOf("Space"))
        assertEquals(KeyEvent.KEYCODE_F12, DeckLayouts.codeOf("F12"))
        assertEquals(KeyEvent.KEYCODE_ESCAPE, DeckLayouts.codeOf("Esc"))
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, DeckLayouts.codeOf("←"))
        assertEquals(KeyEvent.KEYCODE_PAGE_DOWN, DeckLayouts.codeOf("PgDn"))
        assertEquals(DeckMouse.Left.code, DeckLayouts.codeOf("L click"))
        assertNull(DeckLayouts.codeOf("Hyper"))
    }

    @Test fun fullLayoutRowsLineUp() {
        for (numpad in listOf(false, true)) for (fn in listOf(false, true)) {
            val layout = DeckLayouts.full(numpad, fn)
            for (row in layout.rows) assertEquals("row ${row.map { it.label }}", DeckLayouts.FULL_UNITS, row.sumOf { it.width.toDouble() }.toFloat(), 0.001f)
            assertEquals(if (fn) 6 else 5, layout.rows.size)
            // Each layout can switch to the other and back.
            assertTrue(layout.keys.any { it.code == DeckLayouts.TOGGLE_NUMPAD })
            assertTrue(layout.keys.any { it.code == DeckLayouts.TOGGLE_FN })
        }
        assertTrue(DeckLayouts.full(fn = true).keys.count { it.label.startsWith("F") && it.label.drop(1).toIntOrNull() != null } == 12)
    }

    @Test fun fullKeyboardHasTheWholeAlphabet() {
        val labels = DeckLayouts.full().keys.map { it.label }.toSet()
        for (c in 'a'..'z') assertTrue("$c", c.toString() in labels)
        for (c in '0'..'9') assertTrue("$c", c.toString() in labels)
        for (k in listOf("Shift", "Ctrl", "Alt", "Tab", "Esc", "Enter", "⌫", "Space", "↑", "↓", "←", "→")) assertTrue(k, k in labels)
        val numpad = DeckLayouts.full(numpad = true).keys
        for (n in 0..9) assertTrue(numpad.any { it.code == DeckCodes.numpad(n) })
    }

    @Test fun gameLayoutHasWhatGamesBind() {
        val layout = DeckLayouts.game
        for (row in layout.rows) assertEquals(DeckLayouts.GAME_UNITS, row.sumOf { it.width.toDouble() }.toFloat(), 0.001f)
        val labels = layout.keys.map { it.label }.toSet()
        for (k in listOf("w", "a", "s", "d", "Space", "Shift", "Ctrl", "e", "r", "f", "q", "Tab", "Esc", "1", "2", "3", "4", "5", "6", "Enter", "L click", "R click")) {
            assertTrue(k, k in labels)
        }
        assertEquals(2, layout.keys.count { it.kind == DeckKeyKind.Mouse })
    }

    @Test fun everyRealKeyHasACode() {
        val all = DeckLayouts.full(fn = true).keys + DeckLayouts.full(numpad = true).keys + DeckLayouts.game.keys
        for (k in all) {
            when (k.kind) {
                DeckKeyKind.Key, DeckKeyKind.Modifier -> assertTrue(k.label, k.code > 0)
                DeckKeyKind.Mouse -> assertTrue(k.label, DeckMouse.forCode(k.code) != null)
                DeckKeyKind.Toggle -> assertTrue(k.label, k.code == DeckLayouts.TOGGLE_NUMPAD || k.code == DeckLayouts.TOGGLE_FN)
                DeckKeyKind.Gap -> Unit
            }
            assertTrue(k.label, k.width > 0f)
        }
        // Within one layout, no two keys with the same label do different things, except the numpad's digits and
        // minus under the number row, labelled alike as on a real keyboard.
        val numpadCodes = DeckCodes.NUMPAD_0..DeckCodes.NUMPAD_ENTER
        for (layout in listOf(DeckLayouts.full(fn = true), DeckLayouts.full(numpad = true), DeckLayouts.game)) {
            val byLabel = layout.keys.filter { it.code !in numpadCodes }.groupBy { it.label }
            for ((label, keys) in byLabel) assertEquals(label, 1, keys.map { it.code }.distinct().size)
            val pad = layout.keys.filter { it.code in numpadCodes }.groupBy { it.label }
            for ((label, keys) in pad) assertEquals(label, 1, keys.map { it.code }.distinct().size)
        }
    }

    @Test fun shiftedLabels() {
        val one = DeckLayouts.full().keys.first { it.label == "1" }
        assertEquals("!", one.labelWith(true))
        assertEquals("1", one.labelWith(false))
        val a = DeckLayouts.full().keys.first { it.label == "a" }
        assertEquals("A", a.labelWith(true))
        val enter = DeckLayouts.full().keys.first { it.label == "Enter" }
        assertEquals("Enter", enter.labelWith(true))
    }

    @Test fun whichKeysRepeat() {
        val keys = DeckLayouts.full(fn = true).keys + DeckLayouts.game.keys
        assertTrue(keys.first { it.label == "a" }.repeats)
        assertTrue(keys.first { it.label == "⌫" }.repeats)
        assertTrue(keys.first { it.label == "←" }.repeats)
        assertFalse(keys.first { it.label == "Esc" }.repeats)
        assertFalse(keys.first { it.label == "Enter" }.repeats)
        assertFalse(keys.first { it.label == "Shift" }.repeats)
        assertFalse(keys.first { it.label == "L click" }.repeats)
        assertFalse(keys.first { it.label == "123" }.repeats)
    }

    @Test fun modifiersArmForOneKeyAndLockOnAHold() {
        val m = DeckModifiers()
        assertTrue(m.active().isEmpty())
        m.tap(DeckModifiers.Mod.Shift)
        assertEquals(DeckModifiers.State.Armed, m.state(DeckModifiers.Mod.Shift))
        assertEquals(listOf(DeckModifiers.Mod.Shift), m.take())
        // Used up by that key.
        assertEquals(DeckModifiers.State.Off, m.state(DeckModifiers.Mod.Shift))
        assertTrue(m.take().isEmpty())

        m.hold(DeckModifiers.Mod.Ctrl)
        m.tap(DeckModifiers.Mod.Alt)
        assertEquals(listOf(DeckModifiers.Mod.Ctrl, DeckModifiers.Mod.Alt), m.take())
        // Ctrl stays locked, Alt was armed for one key.
        assertEquals(listOf(DeckModifiers.Mod.Ctrl), m.take())
        m.tap(DeckModifiers.Mod.Ctrl)
        assertTrue(m.take().isEmpty())
        // A second tap on an armed modifier takes it off again.
        m.tap(DeckModifiers.Mod.Shift); m.tap(DeckModifiers.Mod.Shift)
        assertEquals(DeckModifiers.State.Off, m.state(DeckModifiers.Mod.Shift))
    }

    @Test fun metaStateRoundTrips() {
        val mods = listOf(DeckModifiers.Mod.Shift, DeckModifiers.Mod.Ctrl)
        val meta = DeckModifiers.meta(mods)
        assertEquals(KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON, meta)
        assertEquals(mods, DeckModifiers.fromMeta(meta))
        assertEquals(0, DeckModifiers.meta(emptyList()))
        assertTrue(DeckModifiers.fromMeta(0).isEmpty())
        assertEquals(DeckCodes.SHIFT_LEFT, DeckModifiers.Mod.forCode(KeyEvent.KEYCODE_SHIFT_LEFT)?.code)
        assertNull(DeckModifiers.Mod.forCode(KeyEvent.KEYCODE_A))
    }

    @Test fun whichGamesGetADeck() {
        assertTrue(DeckGames.suits("windows", "app.gamenative"))
        assertTrue(DeckGames.suits("windows", null))
        assertTrue(DeckGames.suits("windows", "com.limelight"))
        // Winlator builds, GameNative and Moonlight, whatever the system that started them.
        assertTrue(DeckGames.suits("dos", "com.winlator"))
        assertTrue(DeckGames.suits("dos", "com.winlator.cmod"))
        assertTrue(DeckGames.suits("android", "com.limelight"))
        assertFalse(DeckGames.suits("switch", "org.yuzu.yuzu_emu.ea"))
        assertFalse(DeckGames.suits("gba", null))
        assertFalse(DeckGames.suits(null, "com.winlatorish.other"))
    }
}
