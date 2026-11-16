package io.github.zahakj.tandem.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class TextEntryEditsTest {
    @Test fun controllerTypingInsertsAtTheNativeCursor() {
        val edited = TextEntryEdits.insert(TextFieldValue("romm.test", TextRange(4)), "-fixture")
        assertEquals("romm-fixture.test", edited.text)
        assertEquals(TextRange(12), edited.selection)
    }

    @Test fun pasteReplacesSelectedTextAndMovesCursorAfterIt() {
        val edited = TextEntryEdits.insert(TextFieldValue("old token", TextRange(0, 3)), "synthetic")
        assertEquals("synthetic token", edited.text)
        assertEquals(TextRange(9), edited.selection)
    }

    @Test fun reverseSelectionsAreReplacedToo() {
        val edited = TextEntryEdits.insert(TextFieldValue("abcXYZdef", TextRange(6, 3)), "!")
        assertEquals("abc!def", edited.text)
        assertEquals(TextRange(4), edited.selection)
    }

    @Test fun pastingMultilineTextKeepsWordBoundariesInASingleLine() {
        val edited = TextEntryEdits.insert(TextFieldValue(), "first\r\nsecond\nthird")
        assertEquals("first  second third", edited.text)
        assertEquals(TextRange(edited.text.length), edited.selection)
    }

    @Test fun nativeInputNormalizesLineBreaksWithoutMovingItsSelection() {
        val edited = TextEntryEdits.singleLine(TextFieldValue("a\nb\rc", TextRange(2)))
        assertEquals("a b c", edited.text)
        assertEquals(TextRange(2), edited.selection)
    }

    @Test fun controllerDeleteRemovesTheSelection() {
        val edited = TextEntryEdits.backspace(TextFieldValue("abcXYZdef", TextRange(6, 3)))
        assertEquals("abcdef", edited.text)
        assertEquals(TextRange(3), edited.selection)
    }

    @Test fun controllerDeleteWorksInTheMiddleOfNativeText() {
        val edited = TextEntryEdits.backspace(TextFieldValue("abc", TextRange(2)))
        assertEquals("ac", edited.text)
        assertEquals(TextRange(1), edited.selection)
    }

    @Test fun deletingAtTheStartDoesNotRemoveTheLastCharacter() {
        val value = TextFieldValue("abc", TextRange.Zero)
        assertEquals(value, TextEntryEdits.backspace(value))
    }

    @Test fun controllerDeleteRemovesAWholePastedEmoji() {
        val edited = TextEntryEdits.backspace(TextFieldValue("a\uD83D\uDE00b", TextRange(3)))
        assertEquals("ab", edited.text)
        assertEquals(TextRange(1), edited.selection)
    }
}
