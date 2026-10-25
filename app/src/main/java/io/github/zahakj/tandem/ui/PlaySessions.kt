package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.data.ExtrasStore
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.PlayHistory
import io.github.zahakj.tandem.data.PlaySession
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.plural
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/*
 * Editing play sessions from Stats › Sessions: Y (or a long press) on a session opens its menu (edit length, change
 * date and time, merge with the next one, delete), and each game's Y menu has "Play sessions…" for that game alone.
 * Every change goes through HomeStore.editSessions, which works the game's totals out again. Journal notes follow
 * their session: moved with it, joined when two merge, dropped when it goes.
 */
object PlaySessions {
    /** Only this game's sessions show (opened from its Y menu); null for all. */
    var only by mutableStateOf<String?>(null)
    var menuFor by mutableStateOf<PlaySession?>(null)
    var lengthFor by mutableStateOf<PlaySession?>(null)
    var dateFor by mutableStateOf<PlaySession?>(null)

    /** The session row holding the focus, for Y. */
    internal var focused: PlaySession? = null

    val open: Boolean get() = menuFor != null || lengthFor != null || dateFor != null

    /** From a game's Y menu: Stats › Sessions with only [g]'s sessions. */
    fun openFor(g: Game) {
        Nav.closeOverlays()
        only = g.key
        StatsTabs.tab = 1
        FocusMemory.forget(ROUTE)
        if (Nav.route != Route.Stats) Nav.go(Route.Stats) else Nav.focusTick++
    }

    fun openMenu(s: PlaySession) {
        FocusMemory.forget(MENU)
        menuFor = s
    }

    fun close() {
        menuFor = null; lengthFor = null; dateFor = null
        Nav.focusTick++
    }

    const val ROUTE = "stats:1"
    internal const val MENU = "sessionmenu"

    internal fun rowKey(s: PlaySession) = "${s.k}:${s.s}"
    private fun noteKey(s: PlaySession) = "${s.k}@${s.s}"

    fun setLength(s: PlaySession, seconds: Long) {
        HomeStore.editSessions { PlayHistory.setLength(it, s, seconds) }
        FocusMemory.remember(ROUTE, rowKey(s))
    }

    fun moveTo(s: PlaySession, start: Long) {
        var moved: PlaySession? = null
        HomeStore.editSessions { list -> PlayHistory.moveTo(list, s, start).also { moved = it.second }.first }
        val m = moved ?: return
        if (m.s != s.s) ExtrasStore.updateNotes { n ->
            val note = n.sessions[noteKey(s)] ?: return@updateNotes n
            n.copy(sessions = n.sessions - noteKey(s) + (noteKey(m) to note))
        }
        FocusMemory.remember(ROUTE, rowKey(m))
    }

    fun mergeNext(s: PlaySession) {
        val next = PlayHistory.next(HomeStore.sessions, s)?.takeIf { PlayHistory.canMerge(s, it) } ?: return
        HomeStore.editSessions { PlayHistory.mergeNext(it, s) }
        ExtrasStore.updateNotes { n ->
            val second = n.sessions[noteKey(next)] ?: return@updateNotes n
            val joined = listOfNotNull(n.sessions[noteKey(s)], second).joinToString(" · ").take(240)
            n.copy(sessions = n.sessions - noteKey(next) + (noteKey(s) to joined))
        }
        FocusMemory.remember(ROUTE, rowKey(s))
    }

    fun delete(s: PlaySession) {
        HomeStore.editSessions { PlayHistory.delete(it, s) }
        dropNotes(listOf(s))
    }

    /** Deletes [these] sessions at once ("Clear all sessions"). */
    fun clear(these: List<PlaySession>) {
        val gone = these.mapTo(HashSet()) { it.k to it.s }
        HomeStore.editSessions { list -> list.filterNot { (it.k to it.s) in gone } }
        dropNotes(these)
    }

    private fun dropNotes(these: List<PlaySession>) {
        val keys = these.mapTo(HashSet()) { noteKey(it) }
        if (ExtrasStore.notes.sessions.keys.any { it in keys }) ExtrasStore.updateNotes { n -> n.copy(sessions = n.sessions - keys) }
    }
}

/** A session's game: from the library, or null when it is no longer there. */
internal fun sessionGame(key: String): Game? = Libraries.games.firstOrNull { it.key == key }

/** A session's title: the game's, the one kept when it was deleted, or its file name tidied. */
internal fun sessionTitle(key: String, game: Game?): String =
    game?.title ?: Store.get(key).title ?: Library.cleanTitle(key.substringAfter('/').substringBeforeLast('.'))

/** A session's cover: the game's art, or for a game no longer in the library its stored art if it is still there. */
@Composable
internal fun SessionArt(key: String, game: Game?) {
    if (game != null) { GameArt(game); return }
    val m = Store.get(key)
    val art = remember(key, m) { listOfNotNull(m.box, m.hero, m.screenshot).firstOrNull { File(it).isFile } }
    if (art != null) {
        AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        val c = Palette.forName(key.substringBefore('/'))
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.copy(alpha = 0.5f), Palette.CardHigh))))
    }
}

private val zone: ZoneId get() = ZoneId.systemDefault()
private fun at(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** The session popups, drawn over the Stats screen. */
@Composable
fun PlaySessionPopups() {
    SessionMenu()
    LengthEditor()
    DateEditor()
}

@Composable
private fun SessionHeader(s: PlaySession, game: Game?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp))) { SessionArt(s.k, game) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(sessionTitle(s.k, game), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${at(s.s).format(dayFormat)} · ${at(s.s).format(timeFormat)} · ${formatPlayLong(s.d)}",
                style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SessionMenu() {
    val s = PlaySessions.menuFor
    val shown = rememberLatest(s)
    PanelGuard(s != null)
    InterceptButtons(s != null && TextEntry.request == null && Confirm.request == null) { true }
    OverlayPanel(s != null, onClose = { PlaySessions.close() }) {
        val cur = shown ?: return@OverlayPanel
        val game = remember(cur.k, Libraries.all) { sessionGame(cur.k) }
        val next = PlayHistory.next(HomeStore.sessions, cur)
        val merge = next?.takeIf { PlayHistory.canMerge(cur, it) }
        val keys = listOfNotNull("length", "date", "merge".takeIf { merge != null }, "only".takeIf { PlaySessions.only == null }, "delete")
        val target = rememberFocusTarget(PlaySessions.MENU, keys)
        val color = game?.let { SystemColors.of(it.platform.id) } ?: Palette.Lavender
        val r = PlaySessions.MENU
        PanelSurface(Modifier.width(470.dp)) {
            SessionHeader(cur, game)
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                MenuRow("Edit length", formatPlayLong(cur.d), "length", r, target, color) {
                    PlaySessions.menuFor = null; FocusMemory.forget(LENGTH); PlaySessions.lengthFor = cur
                }
                MenuRow("Change date and time", at(cur.s).format(DateTimeFormatter.ofPattern("d MMM, ")) + at(cur.s).format(timeFormat), "date", r, target, Palette.Sky) {
                    PlaySessions.menuFor = null; FocusMemory.forget(DATE); PlaySessions.dateFor = cur
                }
                if (merge != null) MenuRow("Merge with next", "+ ${formatPlayLong(merge.d)}", "merge", r, target, Palette.Butter) {
                    PlaySessions.mergeNext(cur)
                    Toasts.show("Merged into one session of ${formatPlayLong(cur.d + merge.d)}")
                    PlaySessions.close()
                }
                if (PlaySessions.only == null) MenuRow("Only this game", null, "only", r, target, Palette.Lavender) {
                    PlaySessions.only = cur.k
                    FocusMemory.remember(PlaySessions.ROUTE, PlaySessions.rowKey(cur))
                    PlaySessions.close()
                }
                HoldMenuRow("Delete", "delete", r, target, Palette.Peach) {
                    PlaySessions.delete(cur)
                    Toasts.show("Session deleted")
                    PlaySessions.close()
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ButtonHint("A", "Choose", Palette.Mint)
                ButtonHint("B", "Close", Palette.Lavender)
            }
        }
    }
}

private const val LENGTH = "sessionlength"
private const val DATE = "sessiondate"

/** Edit length: ◀▶ a minute, L1 / R1 ten minutes. */
@Composable
private fun LengthEditor() {
    val s = PlaySessions.lengthFor
    val shown = rememberLatest(s)
    var seconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(s) { if (s != null) seconds = s.d }
    fun step(minutes: Int) {
        val m = (seconds / 60 + minutes).coerceIn(PlayHistory.MIN_SECONDS / 60, PlayHistory.MAX_SECONDS / 60)
        if (m * 60 != seconds) { seconds = m * 60; Sounds.move() }
    }
    PanelGuard(s != null)
    InterceptButtons(s != null && TextEntry.request == null) { b ->
        when (b) { Button.L1 -> step(-10); Button.R1 -> step(10); else -> Unit }
        true
    }
    OverlayPanel(s != null, onClose = { PlaySessions.close() }) {
        val cur = shown ?: return@OverlayPanel
        val game = remember(cur.k, Libraries.all) { sessionGame(cur.k) }
        val target = rememberFocusTarget(LENGTH, listOf("value", "cancel", "save"))
        PanelSurface(Modifier.width(470.dp)) {
            SessionHeader(cur, game)
            Spacer(Modifier.height(14.dp))
            StepCard("Length", formatPlayLong(seconds), Palette.Mint, Modifier.takesFocus(LENGTH, "value", target == "value"), { step(it) })
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ButtonHint("◀▶", "1 min")
                ButtonHint("L1", "")
                ButtonHint("R1", "10 min")
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                PillButton("Cancel", Palette.Lavender, Modifier.takesFocus(LENGTH, "cancel", target == "cancel")) { PlaySessions.close() }
                PillButton("Save", Palette.Mint, Modifier.takesFocus(LENGTH, "save", target == "save")) {
                    if (seconds != cur.d) { PlaySessions.setLength(cur, seconds); Toasts.show("Length set to ${formatPlayLong(seconds)}") }
                    PlaySessions.close()
                }
            }
        }
    }
}

/** Change date and time: ◀▶ a day or a minute, L1 / R1 a week or an hour. */
@Composable
private fun DateEditor() {
    val s = PlaySessions.dateFor
    val shown = rememberLatest(s)
    var start by remember { mutableLongStateOf(0L) }
    var row by remember { mutableStateOf("day") }
    LaunchedEffect(s) { if (s != null) { start = s.s; row = "day" } }
    fun step(which: String, by: Int, big: Boolean) {
        val cur = shown ?: return
        val t = at(start)
        val moved = when (which) {
            "day" -> t.plusDays(by * if (big) 7L else 1L)
            else -> if (big) t.plusHours(by.toLong()) else t.plusMinutes(by.toLong())
        }.toInstant().toEpochMilli()
        // A session can't end in the future.
        val latest = System.currentTimeMillis() - cur.d * 1000
        val next = moved.coerceIn(0L, maxOf(latest, 0L))
        if (next != start) { start = next; Sounds.move() }
    }
    PanelGuard(s != null)
    InterceptButtons(s != null && TextEntry.request == null) { b ->
        when (b) { Button.L1 -> step(row, -1, true); Button.R1 -> step(row, 1, true); else -> Unit }
        true
    }
    OverlayPanel(s != null, onClose = { PlaySessions.close() }) {
        val cur = shown ?: return@OverlayPanel
        val game = remember(cur.k, Libraries.all) { sessionGame(cur.k) }
        val target = rememberFocusTarget(DATE, listOf("day", "time", "cancel", "save"))
        val t = at(start)
        PanelSurface(Modifier.width(470.dp)) {
            SessionHeader(cur, game)
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StepCard("Day", t.format(dayFormat), Palette.Sky, Modifier.takesFocus(DATE, "day", target == "day"), { step("day", it, false) }) { row = "day" }
                StepCard("Time", t.format(timeFormat), Palette.Mint, Modifier.takesFocus(DATE, "time", target == "time"), { step("time", it, false) }) { row = "time" }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ButtonHint("◀▶", "Change")
                ButtonHint("L1", "")
                ButtonHint("R1", if (row == "day") "A week" else "An hour")
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                PillButton("Cancel", Palette.Lavender, Modifier.takesFocus(DATE, "cancel", target == "cancel")) { PlaySessions.close() }
                PillButton("Save", Palette.Mint, Modifier.takesFocus(DATE, "save", target == "save")) {
                    if (start != cur.s) {
                        PlaySessions.moveTo(cur, start)
                        Toasts.show("Moved to ${at(start).format(dayFormat)}, ${at(start).format(timeFormat)}")
                    }
                    PlaySessions.close()
                }
            }
        }
    }
}

/** A row whose value ◀▶ changes; also tappable arrows for touch. [onFocus] says this row is the one L1 / R1 step. */
@Composable
private fun StepCard(label: String, value: String, color: Color, modifier: Modifier, onStep: (Int) -> Unit, onFocus: () -> Unit = {}) {
    FocusCard(
        modifier.fillMaxWidth().height(52.dp).onPreviewKeyEvent { e ->
            val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> return@onPreviewKeyEvent false }
            if (e.type == KeyEventType.KeyDown) onStep(d)
            true
        },
        glow = color, shape = RoundedCornerShape(14.dp), onFocus = onFocus, onClick = {},
    ) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("‹", Modifier.clickable { onStep(-1) }.padding(horizontal = 10.dp), style = MaterialTheme.typography.titleLarge, color = if (focused) color else Palette.Muted)
            Text(value, Modifier.width(150.dp), style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text("›", Modifier.clickable { onStep(1) }.padding(horizontal = 10.dp), style = MaterialTheme.typography.titleLarge, color = if (focused) color else Palette.Muted)
        }
    }
}

/** What "Clear all sessions" says about [these]. */
internal fun clearDetail(these: List<PlaySession>, title: String?): String {
    val what = plural(these.size, "session") + " (${formatPlayLong(these.sumOf { it.d })})"
    val of = if (title != null) " of $title" else ""
    return "$what$of will be removed, and play time, play count and last played worked out again from what's left. " +
        "Play time imported from another frontend stays. This can't be undone."
}
