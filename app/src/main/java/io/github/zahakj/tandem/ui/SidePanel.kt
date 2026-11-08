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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * (QuickControls) and the device's notifications (Notes) below, grouped by app: A opens one, Y clears it and the
 * highlight moves on to the next. Everything scrolls as one, so the notifications get the whole panel when there are
 * many. In private mode the notifications are only counted, never read out.
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
                    if (n.clearable) {
                        // The highlight goes to the card below (or above, for the last), not back to the top.
                        val shown = Notes.list.filter { it.clearable }.let(::byApp)
                        val i = shown.indexOfFirst { it.key == n.key }
                        val next = shown.getOrNull(i + 1) ?: shown.getOrNull(i - 1)
                        FocusMemory.remember(KEY, next?.let { "n:${it.key}" } ?: "clearall")
                        Notes.dismiss(n); Sounds.back(); Nav.focusTick++
                    } else { Toasts.show("This one can't be cleared"); Sounds.error() }
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
        // Notifications to read and clear; the ones that can't be cleared (USB, a running service) are status, shown apart.
        val notes = Notes.list.filter { it.clearable }
        val status = Notes.list.filter { !it.clearable }
        val private = Privacy.enabled
        val (battery, charging) = remember(now) { SystemControls.battery(ctx) }
        val (free, total) = remember(now / 600_000) { SystemControls.storage() }
        val grouped = byApp(notes)
        val list = rememberLazyListState()
        Column(
            Modifier.width(460.dp).fillMaxHeight().padding(top = 10.dp, bottom = 10.dp, end = 10.dp)
                .trapFocus().clip(RoundedCornerShape(28.dp)).background(Palette.Card).swallowTaps(),
        ) {
            PaddedScrolling(56.dp) {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().fadingEdges(list), state = list,
                    // Room on every side for a highlighted card to grow and glow without being clipped.
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item("head") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pip(46.dp, mood = if (notes.isEmpty()) 0 else 1)
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
                    }
                    item("controls") { Column(Modifier.padding(top = 4.dp)) { QuickControls(KEY, narrow = true) } }
                    item("notes-title") {
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Notifications", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (notes.isNotEmpty()) FocusCard(
                                Modifier.height(34.dp).remembersFocus(KEY, "clearall", false), glow = Palette.Peach, shape = RoundedCornerShape(50),
                                onClick = { Notes.clearAll(); Sounds.back() },
                            ) {
                                Text("Clear all", Modifier.align(Alignment.Center).padding(horizontal = 14.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            }
                        }
                    }
                    when {
                        !allowed -> item("allow") {
                            FocusCard(
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
                                    Text("They stay on this device. Tandem only shows them here.", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                                }
                            }
                        }
                        notes.isEmpty() -> item("empty") {
                            Text("Nothing new.", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                        }
                        else -> items(grouped, key = { "n:" + it.key }) { n ->
                            // The app's name once, above its first card; its other cards follow without it.
                            val first = grouped.firstOrNull { it.pkg == n.pkg } === n
                            NoteCard(
                                n, private, now, showApp = first, focused = focusedNote == n.key,
                                modifier = Modifier.animateItem().remembersFocus(KEY, "n:${n.key}", false).onFocusChanged {
                                    if (it.isFocused) focusedNote = n.key else if (focusedNote == n.key) focusedNote = null
                                },
                            )
                        }
                    }
                    // What Android keeps up while something is on (USB, a running service): small chips, not cards to clear.
                    if (allowed && status.isNotEmpty()) item("status") { StatusChips(status, private) { n -> focusedNote = n } }
                }
            }
        }
    }
}

/** Notifications grouped by app, the app with the newest first, each app's newest first. */
private fun byApp(notes: List<Note>): List<Note> =
    notes.groupBy { it.pkg }.values.sortedByDescending { g -> g.maxOf { it.time } }.flatMap { g -> g.sortedByDescending { it.time } }

/**
 * Status notifications (USB, a service that keeps running) as one slim row of chips under the list, sliding sideways
 * when they don't all fit: the app's icon and the notification's title. A opens.
 */
@Composable
private fun StatusChips(status: List<Note>, private: Boolean, onFocus: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("On the device", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
        Spacer(Modifier.width(2.dp))
        for (n in status) androidx.compose.runtime.key(n.key) {
            FocusCard(
                Modifier.height(28.dp).remembersFocus(KEY, "s:${n.key}", false).onFocusChanged { if (it.isFocused) onFocus(null) },
                glow = Palette.Sky, shape = RoundedCornerShape(50),
                onClick = { Panels.close(); Notes.open(n) },
            ) {
                Row(Modifier.fillMaxHeight().padding(start = 5.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(50))) { TileArtView(TileArt.App(n.pkg), 18.dp) }
                    Spacer(Modifier.width(6.dp))
                    Text(if (private) "Status" else n.title.ifBlank { n.text }.take(28), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
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
private fun NoteCard(n: Note, private: Boolean, now: Long, showApp: Boolean, focused: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val app = remember(n.pkg) { RunningGames.appLabel(ctx, n.pkg) }
    FocusCard(
        modifier.fillMaxWidth(), glow = Palette.Sky, shape = RoundedCornerShape(18.dp),
        onClick = { Panels.close(); Notes.open(n) },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            // The icon only on an app's first card; the rest line up under it.
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp))) { if (showApp) TileArtView(TileArt.App(n.pkg), 32.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (showApp) app else "", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(
                        DateUtils.getRelativeTimeSpanString(n.time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                        style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1,
                    )
                    // What Y does, on the card it does it to.
                    if (focused) { Spacer(Modifier.width(8.dp)); ButtonHint("Y", "Clear", Palette.Butter) }
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
