package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button

/** A request for the user to type something with the controller. */
data class TextRequest(
    val title: String,
    val initial: String,
    val secret: Boolean,
    val onDone: (String) -> Unit,
    /** Gets the text after every key, for a search that narrows as you type (and the first text again on Cancel). */
    val onChange: ((String) -> Unit)? = null,
)

object TextEntry {
    var request by mutableStateOf<TextRequest?>(null)

    /** Opens the on-screen keyboard; [onDone] gets the text when the user presses Done, [onChange] after every key. */
    fun ask(title: String, initial: String = "", secret: Boolean = false, onChange: ((String) -> Unit)? = null, onDone: (String) -> Unit) {
        // Every keyboard opens on its first letter.
        FocusMemory.forget(ROUTE)
        request = TextRequest(title, initial, secret, onDone, onChange)
    }
}

private const val ROUTE = "textentry"
private val LOWER = "abcdefghijklmnopqrstuvwxyz1234567890".map { it.toString() }
private val SYMBOLS = "-_.@:/!?&'()+=#%*,;~".map { it.toString() }
private val CONTROLS = listOf("⇧", "#+=", "Space", "⌫", "Done")

/**
 * The on-screen keyboard used anywhere text is needed (API keys, names). Shift toggles capitals, "#+=" shows
 * symbols; focus stays on the key you pressed. X deletes, Start is Done, B cancels. Rendered once in the app root,
 * above everything else.
 */
@Composable
fun TextEntryOverlay() {
    val req = TextEntry.request
    var text by remember(req) { mutableStateOf(req?.initial.orEmpty()) }
    var upper by remember(req) { mutableStateOf(false) }
    var symbols by remember(req) { mutableStateOf(false) }
    val close: () -> Unit = { TextEntry.request = null; Nav.focusTick++ }
    val done: () -> Unit = {
        val r = req
        if (r != null) { val t = text; close(); r.onDone(t) }
    }
    val cancel: () -> Unit = { req?.onChange?.invoke(req.initial); close() }
    androidx.compose.runtime.LaunchedEffect(req, text) { req?.onChange?.invoke(text) }
    InterceptButtons(req != null) { b ->
        when (b) {
            Button.X -> text = text.dropLast(1)
            Button.Start -> done()
            else -> Unit
        }
        true
    }
    val shown = rememberLatest(req)
    OverlayPanel(req != null, onClose = cancel) {
        val r = shown ?: return@OverlayPanel
        val letters = if (symbols) SYMBOLS else LOWER.map { if (upper) it.uppercase() else it }
        val keys = letters + CONTROLS
        // Keys are named by what they type, so after Shift the letters are new keys; focus stays on Shift.
        val target = rememberFocusTarget(ROUTE, keys.map { "key:$it" })
        Column(
            Modifier.width(620.dp).clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(r.title, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.NightDeep).padding(12.dp)) {
                val visible = if (r.secret && text.isNotEmpty()) "•".repeat(text.length - 1) + text.last() else text
                // Long text shows its end, where you're typing.
                TailText(visible, Palette.Text)
            }
            LazyVerticalGrid(GridCells.Fixed(10), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(keys, key = { it }, span = { k -> GridItemSpan(if (k.length > 1 && k != "⇧" && k != "⌫") 2 else 1) }) { k ->
                    // Shift does nothing for symbols, so it looks switched off there.
                    val off = k == "⇧" && symbols
                    val on = (k == "⇧" && upper && !symbols) || (k == "#+=" && symbols)
                    FocusCard(
                        Modifier.height(34.dp).takesFocus(ROUTE, "key:$k", target == "key:$k"),
                        glow = if (k == "Done") Palette.Mint else Palette.Lavender, shape = RoundedCornerShape(10.dp),
                        onClick = {
                            Sounds.key()
                            when (k) {
                                "⇧" -> if (!symbols) upper = !upper
                                "#+=" -> symbols = !symbols
                                "Space" -> text += " "
                                "⌫" -> text = text.dropLast(1)
                                "Done" -> done()
                                else -> text += k
                            }
                        },
                    ) {
                        Box(
                            Modifier.fillMaxSize().background(if (on) Palette.Lavender.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(k, style = MaterialTheme.typography.titleSmall, color = if (off) Palette.Muted.copy(alpha = 0.5f) else Palette.Text, maxLines = 1)
                        }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ButtonHint("A", "Type", Palette.Mint)
                ButtonHint("X", "Delete", Palette.Peach)
                ButtonHint("Start", "Done", Palette.Sky)
                ButtonHint("B", "Cancel", Palette.Lavender)
            }
        }
    }
}
