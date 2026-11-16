package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button

/** A request for text, entered with the controller, a keyboard, or paste. */
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

/**
 * A keyboard key's face: its label, tinted while [on] (Shift held, symbols switched in) and faded when [off] (a key
 * that does nothing right now). Shared by the controller keyboard here and the Deck (DeckPanel.kt).
 */
@Composable
internal fun KeyFace(
    label: String, on: Boolean, off: Boolean = false, modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = Palette.Lavender, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall,
) {
    Box(
        modifier.fillMaxSize().background(if (on) tint.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = style, color = if (off) Palette.Muted.copy(alpha = 0.5f) else Palette.Text, maxLines = 1)
    }
}

private const val ROUTE = "textentry"
private val LOWER = "abcdefghijklmnopqrstuvwxyz1234567890".map { it.toString() }
private val SYMBOLS = "-_.@:/!?&'()+=#%*,;~".map { it.toString() }
private val CONTROLS = listOf("⇧", "#+=", "Space", "⌫", "Paste", "Done")

/**
 * The on-screen keyboard used anywhere text is needed (API keys, names). Shift toggles capitals, "#+=" shows
 * symbols; focus stays on the key you pressed. X deletes, Start is Done, B cancels. Rendered once in the app root,
 * above everything else. The editable line also supports hardware typing and native paste; the Paste key reads
 * the clipboard only when selected. Drafts stay in memory, and secret entries are fully masked.
 */
@Composable
fun TextEntryOverlay() {
    val req = TextEntry.request
    var value by remember(req) {
        val initial = req?.initial.orEmpty()
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    var upper by remember(req) { mutableStateOf(false) }
    var symbols by remember(req) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val close: () -> Unit = { value = TextFieldValue(); TextEntry.request = null; Nav.focusTick++ }
    val done: () -> Unit = {
        val r = req
        if (r != null) { val text = value.text; close(); r.onDone(text) }
    }
    val cancel: () -> Unit = { req?.onChange?.invoke(req.initial); close() }
    androidx.compose.runtime.LaunchedEffect(req, value.text) { req?.onChange?.invoke(value.text) }
    InterceptButtons(req != null) { b ->
        when (b) {
            Button.X -> value = TextEntryEdits.backspace(value)
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
        // Keep the first controller key as the initial target. Clicking the native field joins the same focus layer.
        val target = rememberFocusTarget(ROUTE, keys.map { "key:$it" } + "input")
        val grid = rememberLazyGridState()
        KeepInView(grid, keys.indexOfFirst { "key:$it" == target })
        var editing by remember { mutableStateOf(false) }
        Column(
            Modifier.width(620.dp).clip(RoundedCornerShape(26.dp)).background(Palette.Card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(r.title, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge, maxLines = 2)
            BasicTextField(
                value = value,
                onValueChange = { value = TextEntryEdits.singleLine(it) },
                modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth()
                    .takesFocus(ROUTE, "input", target == "input")
                    .onFocusChanged { editing = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> { if (event.type == KeyEventType.KeyDown) done(); true }
                            Key.Escape -> { if (event.type == KeyEventType.KeyDown) cancel(); true }
                            Key.DirectionDown -> {
                                if (event.type == KeyEventType.KeyDown) focus.moveFocus(FocusDirection.Down)
                                true
                            }
                            else -> false
                        }
                    }
                    .clip(RoundedCornerShape(14.dp)).background(Palette.NightDeep)
                    .border(1.dp, if (editing) Palette.Mint else Palette.CardHigh, RoundedCornerShape(14.dp)).padding(12.dp),
                enabled = req != null,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Mint),
                visualTransformation = if (r.secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = if (r.secret) KeyboardType.Password else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { done() }),
                decorationBox = { field ->
                    Box {
                        if (value.text.isEmpty()) Text("Click to type or paste", color = Palette.Muted, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        field()
                    }
                },
            )
            LazyVerticalGrid(GridCells.Fixed(10), state = grid, modifier = Modifier.weight(1f, fill = false).fadingEdges(grid), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                "Space" -> value = TextEntryEdits.insert(value, " ")
                                "⌫" -> value = TextEntryEdits.backspace(value)
                                "Paste" -> clipboard.getText()?.text?.let { value = TextEntryEdits.insert(value, it) }
                                "Done" -> done()
                                else -> value = TextEntryEdits.insert(value, k)
                            }
                        },
                    ) { KeyFace(k, on, off) }
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
