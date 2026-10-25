package io.github.zahakj.tandem.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.ExtrasStore
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.PlaySession
import io.github.zahakj.tandem.plural
import java.util.Calendar
import java.util.Date

/*
 * Journal: the play history as a timeline (newest first, by day), each session with a note of your own, and a
 * Games view with total time and a note per game. Sessions come from HomeStore (the same data as Stats); notes are
 * kept in journal.json. Hidden games stay out of it while they're hidden.
 */

private fun sessionKey(s: PlaySession) = "${s.k}@${s.s}"

private fun dayLabel(time: Long): String {
    val now = System.currentTimeMillis()
    return when {
        DateUtils.isToday(time) -> "Today"
        DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        now - time < 6 * DateUtils.DAY_IN_MILLIS -> DateFormat.format("EEEE", time).toString()
        else -> DateFormat.format("EEEE d MMMM yyyy", time).toString()
    }
}

private fun dayOf(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** One line in the timeline: a day heading or a session. */
private sealed interface JournalLine {
    val key: String
    data class Day(val day: Long, val seconds: Long) : JournalLine { override val key = "day:$day" }
    data class Session(val s: PlaySession, val game: Game) : JournalLine { override val key = sessionKey(s) }
}

@Composable
fun JournalScreen() {
    val app = BuiltInApps.byId("journal")!!
    val route = Route.Tool("journal").key
    var view by remember { mutableStateOf("timeline") }
    val notes = ExtrasStore.notes
    val byKey = remember(Libraries.all) { Libraries.games.associateBy { it.key } }
    val sessions = HomeStore.sessions
    val showHidden = Privacy.view
    val lines = remember(sessions, byKey, showHidden) {
        val out = ArrayList<JournalLine>()
        var day = -1L
        val visible = sessions.asReversed().mapNotNull { s -> byKey[s.k]?.takeIf { Privacy.canShow(it) }?.let { s to it } }.take(600)
        val perDay = visible.groupBy { dayOf(it.first.s) }.mapValues { (_, v) -> v.sumOf { it.first.d } }
        for ((s, g) in visible) {
            val d = dayOf(s.s)
            if (d != day) { out += JournalLine.Day(d, perDay[d] ?: 0); day = d }
            out += JournalLine.Session(s, g)
        }
        out
    }
    val games = remember(sessions, byKey, showHidden) {
        sessions.groupBy { it.k }.mapNotNull { (k, v) -> byKey[k]?.takeIf { Privacy.canShow(it) }?.let { Triple(it, v.sumOf { s -> s.d }, v.size) } }
            .sortedByDescending { it.second }
    }
    val chips = listOf("timeline" to "Timeline", "games" to "Games", "notes" to "With notes")
    val rowKeys = when (view) {
        "games" -> games.map { "game:${it.first.key}" }
        "notes" -> lines.filterIsInstance<JournalLine.Session>().filter { notes.sessions[it.key] != null }.map { it.key }
        else -> lines.filterIsInstance<JournalLine.Session>().map { it.key }
    }
    val keys = chips.map { "chip:${it.first}" } + rowKeys
    val target = rememberFocusTarget("$route:$view", keys)
    fun editSession(line: JournalLine.Session) {
        TextEntry.ask("A note for ${line.game.title} (${formatPlayLong(line.s.d)})", notes.sessions[line.key].orEmpty()) { t ->
            ExtrasStore.updateNotes { n -> n.copy(sessions = if (t.isBlank()) n.sessions - line.key else n.sessions + (line.key to t.trim().take(240))) }
        }
    }
    fun editGame(g: Game) {
        TextEntry.ask("A note about ${g.title}", notes.games[g.key].orEmpty()) { t ->
            ExtrasStore.updateNotes { n -> n.copy(games = if (t.isBlank()) n.games - g.key else n.games + (g.key to t.trim().take(400))) }
        }
    }
    InterceptButtons(toolActive("journal")) { b ->
        val at = FocusMemory.get("$route:$view")
        if (b != Button.Y) return@InterceptButtons false
        val g = lines.filterIsInstance<JournalLine.Session>().firstOrNull { it.key == at }?.game ?: games.firstOrNull { "game:${it.first.key}" == at }?.first
        g?.let { editGame(it) }
        g != null
    }
    val total = remember(sessions) { sessions.sumOf { it.d } }
    ToolScaffold(app, if (sessions.isEmpty()) null else "${formatPlayLong(total)} logged", hints = {
        ButtonHint("A", "Note", Palette.Mint)
        ButtonHint("Y", "Game note", Palette.Butter)
        ButtonHint("B", "Back", Palette.Lavender)
    }) {
        if (lines.isEmpty()) {
            EmptyNote(
                "Play something and each session shows up here, ready for a note", "Go back",
                Modifier.takesFocus("$route:$view", "empty", true), Modifier.align(Alignment.Center), mood = 0,
            ) { Nav.back() }
        } else {
            Column(Modifier.fillMaxSize()) {
                ToolChips("$route:$view", chips, view, target, app.color) { FocusMemory.remember("$route:$it", "chip:$it"); view = it }
                val state = rememberRouteListState("$route:$view")
                val shownLines: List<Any> = when (view) {
                    "games" -> games
                    "notes" -> lines.filter { it is JournalLine.Session && notes.sessions[it.key] != null || it is JournalLine.Day }
                        .let { l -> l.filterIndexed { i, x -> x !is JournalLine.Day || (l.getOrNull(i + 1) is JournalLine.Session) } }
                    else -> lines
                }
                val idx = shownLines.indexOfFirst { x ->
                    when (x) { is JournalLine -> x.key == target; is Triple<*, *, *> -> "game:${(x.first as Game).key}" == target; else -> false }
                }
                if (idx >= 0) KeepInView(state, idx)
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).fadingEdges(state), state = state,
                    contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(shownLines, key = { x -> when (x) { is JournalLine -> x.key; is Triple<*, *, *> -> "game:${(x.first as Game).key}"; else -> x.hashCode() } }) { x ->
                        when (x) {
                            is JournalLine.Day -> Text(
                                "${dayLabel(x.day)} · ${formatPlayLong(x.seconds)}", Modifier.padding(start = 4.dp, top = 8.dp),
                                style = MaterialTheme.typography.labelLarge, color = app.color, maxLines = 1,
                            )
                            is JournalLine.Session -> {
                                val note = notes.sessions[x.key]
                                val time = DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current).format(Date(x.s.s))
                                ToolRow(
                                    x.game.title, note ?: "${formatPlayLong(x.s.d)} · $time · ${x.game.platform.shortName}",
                                    SystemColors.of(x.game.platform.id), Modifier.takesFocus("$route:$view", x.key, x.key == target),
                                    tag = if (note != null) formatPlayTime(x.s.d) else null,
                                    onFocus = { Selection.current = Selected.GameItem(x.game) }, onClick = { editSession(x) },
                                ) { GameArt(x.game) }
                            }
                            is Triple<*, *, *> -> {
                                val g = x.first as Game
                                val k = "game:${g.key}"
                                ToolRow(
                                    g.title, notes.games[g.key] ?: "${plural(x.third as Int, "session")} · ${g.platform.name}",
                                    SystemColors.of(g.platform.id), Modifier.takesFocus("$route:$view", k, k == target),
                                    tag = formatPlayTime(x.second as Long),
                                    onFocus = { Selection.current = Selected.GameItem(g) }, onClick = { editGame(g) },
                                ) { GameArt(g) }
                            }
                        }
                    }
                }
            }
        }
    }
}

