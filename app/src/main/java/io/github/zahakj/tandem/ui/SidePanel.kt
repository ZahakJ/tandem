package io.github.zahakj.tandem.ui

import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Note
import io.github.zahakj.tandem.Notes
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The side panel: R2 on home slides it in from the right. Pip and the time at the top, the quick settings
 * (QuickControls) and the device's notifications (Notes) below: A opens one, Y clears it. In private mode the
 * notifications are only counted, never read out.
 */

private const val KEY = "side"

@Composable
fun SidePanelHost() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // The highlighted notification, for Y.
    var focusedNote by remember { mutableStateOf<String?>(null) }
    InterceptButtons(Panels.side && TextEntry.request == null) { b ->
        when (b) {
            Button.R2 -> { Panels.close(); Sounds.menuClose(); true }
            Button.Y -> {
                Notes.list.firstOrNull { it.key == focusedNote }?.let { n ->
                    if (n.clearable) { Notes.dismiss(n); Sounds.back() } else { Toasts.show("This one can't be cleared"); Sounds.error() }
                }
                true
            }
            else -> false
        }
    }
    SidePanel(Panels.side, onClose = { Panels.close() }) {
        var allowed by remember { mutableStateOf(Notes.allowed(ctx)) }
        // Just allowed: ask Android to connect the listener now rather than at some later point.
        LaunchedEffect(allowed) { if (allowed && !Notes.connected) runCatching { NotificationListenerService.requestRebind(Notes.component(ctx)) } }
        // The clock in the header, to the minute.
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
        val notes = Notes.list
        val private = Privacy.enabled
        val (battery, charging) = remember(now) { SystemControls.battery(ctx) }
        val (free, total) = remember(now / 600_000) { SystemControls.storage() }
        // Pip, the settings and the hints stay put; only the notifications scroll.
        Column(
            Modifier.width(460.dp).fillMaxHeight().padding(top = 10.dp, bottom = 10.dp, end = 10.dp)
                .trapFocus().clip(RoundedCornerShape(28.dp)).background(Palette.Card).swallowTaps()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pip(46.dp, mood = if (notes.isEmpty()) 0 else 3)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pipLine(notes.size, allowed), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        DateUtils.formatDateTime(ctx, now, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL),
                        style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill("${if (charging) "⚡ " else ""}$battery%", if (battery <= 20 && !charging) Palette.Peach else Palette.Mint)
                    if (total > 0) Pill("${android.text.format.Formatter.formatShortFileSize(ctx, free)} free", Palette.Sky)
                }
            }
            Spacer(Modifier.height(12.dp))
            QuickControls(KEY, narrow = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (notes.any { it.clearable }) FocusCard(
                    Modifier.height(34.dp).remembersFocus(KEY, "clearall", false), glow = Palette.Peach, shape = RoundedCornerShape(50),
                    onClick = { Notes.clearAll(); Sounds.back() },
                ) {
                    Text("Clear all", Modifier.align(Alignment.Center).padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
            Spacer(Modifier.height(6.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { when {
                !allowed -> FocusCard(
                    Modifier.fillMaxWidth().remembersFocus(KEY, "allow", false), glow = Palette.Lavender, shape = RoundedCornerShape(18.dp),
                    onClick = {
                        if (Shell.ready()) scope.launch {
                            allowed = withContext(Dispatchers.IO) { Notes.allowWithShizuku(ctx) }
                            if (!allowed) openAccess(ctx)
                        } else openAccess(ctx)
                    },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Let Pip show your notifications", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "They stay on this device. Tandem only shows them here.",
                            style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
                        )
                    }
                }
                notes.isEmpty() -> Text("Nothing new.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in notes) androidx.compose.runtime.key(n.key) {
                        NoteCard(n, private, now, Modifier.remembersFocus(KEY, "n:${n.key}", false).onFocusChanged {
                            if (it.isFocused) focusedNote = n.key else if (focusedNote == n.key) focusedNote = null
                        })
                    }
                }
            } }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ButtonHint("◀▶", "Adjust", Palette.Butter)
                ButtonHint("A", "Open", Palette.Mint)
                if (notes.isNotEmpty()) ButtonHint("Y", "Clear", Palette.Butter)
                ButtonHint("R2", "Close", Palette.Lavender)
            }
        }
    }
}

/** What Pip says at the top of the panel. */
private fun pipLine(count: Int, allowed: Boolean): String = when {
    !allowed -> greeting()
    count == 0 -> "${greeting()} All quiet."
    count == 1 -> "One thing for you."
    else -> "$count things for you."
}

private fun greeting(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning!"
    in 12..17 -> "Good afternoon!"
    in 18..22 -> "Good evening!"
    else -> "Up late? Me too."
}

private fun openAccess(ctx: android.content.Context) {
    Panels.close()
    Toasts.show("Turn on Tandem here, then come back")
    runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** One notification: the app's icon and name, when, and what it says (in private mode, only that there is one). */
@Composable
private fun NoteCard(n: Note, private: Boolean, now: Long, modifier: Modifier) {
    val ctx = LocalContext.current
    val app = remember(n.pkg) { RunningGames.appLabel(ctx, n.pkg) }
    FocusCard(
        modifier.fillMaxWidth(), glow = Palette.Sky, shape = RoundedCornerShape(18.dp),
        onClick = { Panels.close(); Notes.open(n) },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))) { TileArtView(TileArt.App(n.pkg), 36.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(
                        " · " + DateUtils.getRelativeTimeSpanString(n.time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE),
                        style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1,
                    )
                }
                if (private) Text("Hidden in private mode", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1)
                else {
                    if (n.title.isNotBlank()) Text(n.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (n.text.isNotBlank()) Text(n.text, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
