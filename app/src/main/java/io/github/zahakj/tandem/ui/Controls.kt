package io.github.zahakj.tandem.ui

import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import io.github.zahakj.tandem.data.Store
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How a button prompt is drawn. A null colour means "use the caller's colour". */
data class PromptGlyph(val glyph: String, val color: Color?, val onColor: Color?, val square: Boolean = false)

/**
 * Button prompts in the user's preferred style. Callers name the *action* button ("A" = confirm, "B" = back,
 * "X", "Y", plus "Start"/"Select"/"L"/"R"); with A/B or X/Y swapped, the prompt shows the physical button that
 * now does that job.
 */
object Prompts {
    val styles = listOf("tandem" to "Tandem", "xbox" to "Xbox", "playstation" to "PlayStation", "nintendo" to "Nintendo")

    fun label(id: String) = styles.firstOrNull { it.first == id }?.second ?: "Tandem"

    fun of(button: String): PromptGlyph {
        val look = Store.settings.look
        val physical = when (button) {
            "A" -> if (look.swapAB) "B" else "A"
            "B" -> if (look.swapAB) "A" else "B"
            "X" -> if (look.swapXY) "Y" else "X"
            "Y" -> if (look.swapXY) "X" else "Y"
            else -> button
        }
        val short = when (physical) { "Start" -> "+"; "Select" -> "−"; else -> physical }
        return when (look.prompts) {
            "xbox" -> when (physical) {
                "A" -> PromptGlyph("A", Color(0xFF6CC24A), Color.White)
                "B" -> PromptGlyph("B", Color(0xFFE5484D), Color.White)
                "X" -> PromptGlyph("X", Color(0xFF3B8EEA), Color.White)
                "Y" -> PromptGlyph("Y", Color(0xFFF2C12E), Color(0xFF222222))
                else -> PromptGlyph(short, Color(0xFF5A5A66), Color.White)
            }
            "playstation" -> when (physical) {
                // Shapes in their classic colours on a dark disc.
                "A" -> PromptGlyph("✕", Color(0xFF26262E), Color(0xFF7FA8FF))
                "B" -> PromptGlyph("○", Color(0xFF26262E), Color(0xFFFF6B6B))
                "X" -> PromptGlyph("□", Color(0xFF26262E), Color(0xFFF78FD6))
                "Y" -> PromptGlyph("△", Color(0xFF26262E), Color(0xFF4FE0B0))
                else -> PromptGlyph(short, Color(0xFF26262E), Color.White)
            }
            "nintendo" -> PromptGlyph(short, Palette.Text, Palette.Night)
            else -> PromptGlyph(short, null, null)
        }
    }

    /** Applies the A/B and X/Y swaps to a raw key code from the controller. */
    fun remap(keyCode: Int): Int {
        val look = Store.settings.look
        return when {
            look.swapAB && keyCode == KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            look.swapAB && keyCode == KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            look.swapXY && keyCode == KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            look.swapXY && keyCode == KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> keyCode
        }
    }
}

/** The clock in the user's chosen format; null when the clock is turned off. */
object Clock {
    val formats = listOf("system" to "Like the system", "12" to "12-hour", "24" to "24-hour", "off" to "Hidden")

    fun label(id: String) = formats.firstOrNull { it.first == id }?.second ?: "Like the system"

    fun format(date: Date): String? = when (Store.settings.look.clock) {
        "off" -> null
        "12" -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        "24" -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        else -> DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    }

    /** Always a time, for places that are all about the clock (the bottom-screen clock face). */
    fun always(date: Date): String = format(date) ?: DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
}

/**
 * The time to the minute, for clocks that show hours and minutes: it changes right when the minute does and
 * recomposes its reader once a minute, not on every check.
 */
@androidx.compose.runtime.Composable
fun rememberMinute(): androidx.compose.runtime.State<Date> =
    androidx.compose.runtime.produceState(minuteNow()) {
        while (true) {
            kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L + 20L)
            value = minuteNow()
        }
    }

private fun minuteNow(): Date = System.currentTimeMillis().let { Date(it - it % 60_000L) }
