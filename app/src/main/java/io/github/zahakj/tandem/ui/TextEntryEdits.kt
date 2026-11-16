package io.github.zahakj.tandem.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Keep native editing, paste and the controller keyboard on the same selection-aware draft. */
internal object TextEntryEdits {
    fun singleLine(value: TextFieldValue): TextFieldValue =
        value.copy(text = value.text.replace('\r', ' ').replace('\n', ' '))

    fun insert(value: TextFieldValue, inserted: String): TextFieldValue {
        val start = value.selection.min
        val end = value.selection.max
        val text = inserted.replace('\r', ' ').replace('\n', ' ')
        return TextFieldValue(value.text.replaceRange(start, end, text), TextRange(start + text.length))
    }

    fun backspace(value: TextFieldValue): TextFieldValue {
        if (!value.selection.collapsed) return insert(value, "")
        val end = value.selection.start
        if (end == 0) return value
        // A pasted non-BMP character must not leave an orphaned UTF-16 surrogate when removed by the pad.
        val start = value.text.offsetByCodePoints(end, -1)
        return TextFieldValue(value.text.removeRange(start, end), TextRange(start))
    }
}
