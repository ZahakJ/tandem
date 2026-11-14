package io.github.zahakj.tandem.ui

import androidx.compose.ui.graphics.Color
import io.github.zahakj.tandem.Binding
import io.github.zahakj.tandem.Bindings
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Command
import io.github.zahakj.tandem.data.ControllerStore
import io.github.zahakj.tandem.data.Store
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How a button prompt is drawn. A null colour means "use the caller's colour". */
data class PromptGlyph(val glyph: String, val color: Color?, val onColor: Color?, val square: Boolean = false)

/**
 * Button prompts in the user's preferred style. Callers name the *action* button ("A" = open, "B" = back, "Y" =
 * options, "L1" = previous... the logical [Button]s and A and B, or a [Command] by name): the prompt shows the
 * button that command is bound to (Bindings.kt), as a chord or a hold in words when that is what it takes. A name
 * that is no command ("L", "R") is drawn as it is.
 */
object Prompts {
    val styles = listOf("tandem" to "Tandem", "xbox" to "Xbox", "playstation" to "PlayStation", "nintendo" to "Nintendo")

    fun label(id: String) = styles.firstOrNull { it.first == id }?.second ?: "Tandem"

    /** The command a hint's button name stands for, or null for a plain button name. */
    fun commandOf(button: String): Command? = when (button) {
        "A" -> Command.Open
        "B" -> Command.Back
        else -> Button.entries.firstOrNull { it.name == button }?.command ?: Command.named(button)
    }

    fun of(button: String): PromptGlyph {
        val look = Store.settings.look
        val command = commandOf(button) ?: return physical(button, look.prompts)
        val binding = ControllerStore.binding(command)
        val single = binding?.takeIf { !it.hold }?.single
        if (single != null) return physical(single.name, look.prompts)
        // A chord, a hold, or no button at all: words in a pill, in the style's plain colours.
        val text = binding?.let { bindingText(it, look.prompts) } ?: "—"
        return when (look.prompts) {
            "xbox" -> PromptGlyph(text, Color(0xFF5A5A66), Color.White)
            "playstation" -> PromptGlyph(text, Color(0xFF26262E), Color.White)
            "nintendo" -> PromptGlyph(text, Palette.Text, Palette.Night)
            else -> PromptGlyph(text, null, null)
        }
    }

    /** The glyph for one button as printed on the controller ([physical]: "A", "L1", "Select", "Home"). */
    fun physical(physical: String, style: String): PromptGlyph {
        val short = text(physical, style)
        return when (style) {
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

    /**
     * The text on a prompt for [physical]: its letter, and for Start and Select their names. Only the Nintendo style
     * draws them as + and −, as printed on its controllers; the Thor and most others label them by name.
     */
    fun text(physical: String, style: String): String = when (physical) {
        "Start" -> if (style == "nintendo") "+" else "Start"
        "Select" -> if (style == "nintendo") "−" else "Select"
        else -> physical
    }

    /** What [c] is bound to right now, in words for a sentence ("L1+R1+Select"), or "no button". */
    fun words(c: Command): String = ControllerStore.binding(c)?.let { bindingText(it, Store.settings.look.prompts) } ?: "no button"

    /** A binding in words, each button as [text] says: "L1+R1+Select" ("L1+R1+−" in the Nintendo style), "Hold Start". */
    fun bindingText(b: Binding, style: String): String =
        (if (b.hold) "Hold " else "") + b.pads.sortedBy { it.ordinal }.joinToString("+") { text(it.name, style) }

    /**
     * Swap A and B and Swap X and Y used to be flags of their own, applied to the key codes; they are bindings now
     * (Open on B and Back on A, Options on X and Action on Y: [Bindings.swap]). Run once at start, after the stores.
     */
    fun migrateSwaps() {
        val look = Store.settings.look
        if (!look.swapAB && !look.swapXY) return
        var map = ControllerStore.bindings
        if (look.swapAB) map = Bindings.swap(map, Command.Open, Command.Back, true)
        if (look.swapXY) map = Bindings.swap(map, Command.Options, Command.Action, true)
        ControllerStore.setBindings(map)
        Store.updateSettings { it.copy(look = it.look.copy(swapAB = false, swapXY = false)) }
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
