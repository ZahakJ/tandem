package io.github.zahakj.tandem.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.BuildConfig
import io.github.zahakj.tandem.Releases
import io.github.zahakj.tandem.UpdateState
import io.github.zahakj.tandem.Updates
import kotlinx.coroutines.launch

/**
 * A new Tandem (Updates.kt): its version and release notes, with Update and Not now. Opened only by the user (Start ›
 * the update row, or Settings › Play & system › About › Check now), never by itself. Up and Down scroll the notes; the buttons keep the focus.
 */
object UpdatePopup {
    const val ROUTE = "update"

    var open by mutableStateOf(false)
        private set

    /** Opens on Update: picking the notice was the ask, so A once more installs. */
    fun show() { FocusMemory.forget(ROUTE); FocusMemory.remember(ROUTE, "yes"); open = true }

    fun close() { open = false; Nav.focusTick++ }
}

@Composable
fun UpdateOverlay() {
    val rel = Updates.found
    val visible = UpdatePopup.open && rel != null
    val shown = rememberLatest(rel)
    InterceptButtons(visible) { true }
    OverlayPanel(visible, onClose = { UpdatePopup.close() }) {
        val r = shown ?: return@OverlayPanel
        val ctx = LocalContext.current
        val state = Updates.state
        val target = rememberFocusTarget(UpdatePopup.ROUTE, listOf("no", "yes"))
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val notes = remember(r.notes) { Releases.plainNotes(r.notes).ifEmpty { "No notes came with this release." } }
        PanelSurface(
            Modifier.width(600.dp).onPreviewKeyEvent { e ->
                val by = when (e.key) { Key.DirectionUp -> -180f; Key.DirectionDown -> 180f; else -> return@onPreviewKeyEvent false }
                if (e.type == KeyEventType.KeyDown) scope.launch { scroll.animateScrollBy(by) }
                true
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pip(52.dp, mood = 2)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Tandem ${r.version}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("You have ${Updates.current}", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1)
                }
            }
            Text(
                notes, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(max = 240.dp).fadingEdges(scroll).verticalScroll(scroll),
            )
            // Where it stands, or why it didn't work; a debug build is told up front that it can't take the release.
            val line = when {
                state is UpdateState.Problem -> state.text
                state != UpdateState.Idle -> Updates.statusLine()
                BuildConfig.DEBUG -> "This is a debug build: the release can't install over it"
                else -> null
            }
            if (line != null) Text(
                line, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (state is UpdateState.Problem) Palette.Peach else Palette.Muted,
            )
            Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton("Not now", Palette.Mint, Modifier.takesFocus(UpdatePopup.ROUTE, "no", target == "no")) { UpdatePopup.close() }
                val yes = when (state) {
                    is UpdateState.Downloading -> "${state.pct} %"
                    UpdateState.Verifying -> "Verifying…"
                    is UpdateState.Problem -> "Try again"
                    else -> "Update"
                }
                PillButton(yes, Palette.Peach, Modifier.takesFocus(UpdatePopup.ROUTE, "yes", target == "yes")) { Updates.install(ctx) }
            }
        }
    }
}
