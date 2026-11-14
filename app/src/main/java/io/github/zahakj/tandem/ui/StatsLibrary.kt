package io.github.zahakj.tandem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.GameStatus
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.TimeToBeat
import io.github.zahakj.tandem.data.playStatus
import io.github.zahakj.tandem.everPlayed
import io.github.zahakj.tandem.plural
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The library by status, worked out once per change. */
private class Catalog(games: List<Game>) {
    val by: Map<GameStatus, List<Game>> = games.filter { it.meta.playStatus != null }.groupBy { it.meta.playStatus!! }
    fun of(s: GameStatus) = by[s].orEmpty()
    val playing = of(GameStatus.Playing).sortedByDescending { maxOf(it.meta.lastPlayed, it.meta.statusAt) }
    val backlog = of(GameStatus.Backlog).sortedBy { it.meta.statusAt }
    val beaten = (of(GameStatus.Finished) + of(GameStatus.Completed)).sortedByDescending { it.meta.finishedAt }
    val shelved = of(GameStatus.Shelved).sortedByDescending { it.meta.statusAt }
    val endless = of(GameStatus.Endless).sortedByDescending { it.meta.playSeconds }
    private val year = LocalDate.now().year
    val beatenThisYear = beaten.count { it.meta.finishedAt > 0 && Instant.ofEpochMilli(it.meta.finishedAt).atZone(ZoneId.systemDefault()).year == year }
    /** HowLongToBeat's main story for the backlog games that have one, and how many of them do. */
    val backlogTimed = backlog.mapNotNull { it.meta.timeToBeat?.main?.takeIf { s -> s > 0 } }
    /** Games with play history but no status yet. */
    val untracked = games.count { it.meta.playStatus == null && it.meta.everPlayed }
}

/**
 * Stats › Library: your games by where you are with them. Four tiles (A opens that collection), then a shelf for each
 * status with something on it. A plays a game; Y on it sets its status like anywhere else.
 */
@Composable
fun LibraryTab() {
    val ctx = LocalContext.current
    val route = "stats:library"
    val games = Collections.visibleGames()
    val cat = remember(games, Store.meta, Privacy.view) { Catalog(games.filter { Privacy.canShow(it) }) }
    val shelves = listOf(
        Triple("playing", "Playing", cat.playing to GameStatus.Playing),
        Triple("backlog", "Backlog", cat.backlog to GameStatus.Backlog),
        Triple("beaten", "Finished and completed", cat.beaten to GameStatus.Finished),
        Triple("shelved", "Shelved", cat.shelved to GameStatus.Shelved),
        Triple("endless", "Endless", cat.endless to GameStatus.Endless),
    ).filter { it.third.first.isNotEmpty() }
    val tiles = listOf("t:playing", "t:backlog", "t:beaten", "t:completed")
    val keys = tiles + listOfNotNull("untracked".takeIf { cat.untracked > 0 }) + shelves.flatMap { (id, _, p) -> p.first.map { "$id:${it.key}" } }
    val target = rememberFocusTarget(route, keys)
    val list = rememberRouteListState(route)
    val targetItem = when {
        target == null -> -1
        target in tiles -> 0
        target == "untracked" -> 1
        else -> (if (cat.untracked > 0) 2 else 1) + shelves.indexOfFirst { target.startsWith(it.first + ":") }.coerceAtLeast(0)
    }
    KeepInView(list, targetItem)
    if (cat.by.isEmpty() && cat.untracked == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyNote("Y › Status on any game puts it in your backlog, or marks it finished. Your library sorts itself out here", "Go back", Modifier.takesFocus(route, "empty", true), mood = 1) { Nav.back() }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().fadingEdges(list),
        state = list,
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = FocusRoom, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("tiles") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val backlogLeft = cat.backlogTimed.sum().toLong()
                StatTile(
                    "Playing", "${cat.playing.size}", cat.playing.firstOrNull()?.let { "latest: ${it.title}" } ?: "nothing on the go",
                    GameStatus.Playing.color, Modifier.weight(1f).takesFocus(route, "t:playing", target == "t:playing"), route, "t:playing",
                ) { openFolder(Route.Collection(Collections.PLAYING)) }
                StatTile(
                    "Backlog", "${cat.backlog.size}",
                    if (backlogLeft > 0) "about ${TimeToBeat.hours(backlogLeft.toInt())} to clear" + if (cat.backlogTimed.size < cat.backlog.size) "+" else "" else "games waiting",
                    GameStatus.Backlog.color, Modifier.weight(1f).takesFocus(route, "t:backlog", target == "t:backlog"), route, "t:backlog",
                ) { openFolder(Route.Collection(Collections.BACKLOG)) }
                StatTile(
                    "Finished", "${cat.beaten.size}", "${cat.beatenThisYear} this year",
                    GameStatus.Finished.color, Modifier.weight(1f).takesFocus(route, "t:beaten", target == "t:beaten"), route, "t:beaten",
                ) { openFolder(Route.Collection(Collections.BEATEN)) }
                val done = cat.of(GameStatus.Completed).size
                StatTile(
                    "Completed", "$done", if (cat.beaten.isEmpty()) "100% runs" else "${done * 100 / cat.beaten.size}% of finished games",
                    GameStatus.Completed.color, Modifier.weight(1f).takesFocus(route, "t:completed", target == "t:completed"), route, "t:completed",
                ) { openFolder(Route.Collection(Collections.BEATEN)) }
            }
        }
        if (cat.untracked > 0) item("untracked") {
            FocusCard(
                Modifier.fillMaxWidth().takesFocus(route, "untracked", target == "untracked"), glow = Palette.Lavender,
                onFocus = { FocusMemory.remember(route, "untracked"); Selection.current = Selected.Home },
                onClick = { StatusSorter.open() },
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pip(40.dp, mood = 1)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${plural(cat.untracked, "game")} you've played ${if (cat.untracked == 1) "has" else "have"} no status yet. A sorts them one by one, most played first.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        for ((id, title, pair) in shelves) item("shelf:$id") {
            val (list, status) = pair
            Column {
                SectionTitle("$title · ${list.size}", status.color)
                val row = rememberLazyListState()
                LazyRow(Modifier.fadingEdges(row, horizontal = true).focusRow(), state = row, horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom)) {
                    items(list, key = { it.key }) { g ->
                        val key = "$id:${g.key}"
                        Column(Modifier.width(104.dp)) {
                            GameCard(
                                g, Modifier.size(104.dp).takesFocus(route, key, key == target),
                                onFocus = { FocusMemory.remember(route, key); Selection.current = Selected.GameItem(g) },
                                onClick = { Launcher.launch(ctx, g) }, showTitle = false,
                            )
                            Spacer(Modifier.height(4.dp))
                            CoverCaption(shelfCaption(g, status), status.color)
                        }
                    }
                }
            }
        }
    }
}

/** What a game on a status shelf says under it: progress, when it was finished, or how long it has waited. */
private fun shelfCaption(g: Game, s: GameStatus): String {
    val m = g.meta
    return when (s) {
        GameStatus.Playing -> playProgress(m) ?: "just started"
        GameStatus.Finished, GameStatus.Completed ->
            (if (m.playStatus == GameStatus.Completed) "★ " else "✓ ") + (m.finishedAt.takeIf { it > 0 }?.let { ago(it) } ?: m.playStatus?.label.orEmpty())
        GameStatus.Backlog -> m.timeToBeat?.main?.takeIf { it > 0 }?.let { "~" + TimeToBeat.hours(it) } ?: "waiting " + ago(m.statusAt).removeSuffix(" ago")
        GameStatus.Shelved, GameStatus.Endless -> if (m.playSeconds >= 60) formatPlayTime(m.playSeconds) else s.label
    }
}

/** "3 days ago", "2 months ago". */
private fun ago(time: Long): String {
    if (time <= 0) return ""
    val days = (System.currentTimeMillis() - time) / 86_400_000L
    return when {
        days < 1 -> "today"
        days < 2 -> "yesterday"
        days < 31 -> "$days days ago"
        days < 365 -> "${days / 30} months ago".replace("1 months", "1 month")
        else -> "${days / 365} years ago".replace("1 years", "1 year")
    }
}

/**
 * Sorting played games that have no status yet, one at a time: the most played first, its cover big and a row of
 * statuses beside it. A on a status sets it and brings the next game; Skip leaves it be; B stops.
 */
object StatusSorter {
    /** The games still to sort this time, by key; null while closed. */
    var queue by androidx.compose.runtime.mutableStateOf<List<String>?>(null)
    var done by androidx.compose.runtime.mutableIntStateOf(0)

    fun open() {
        val keys = Collections.visibleGames().filter { Privacy.canShow(it) && it.meta.playStatus == null && it.meta.everPlayed }
            .sortedByDescending { it.meta.playSeconds }.map { it.key }
        if (keys.isEmpty()) { Toasts.show("Every game you've played has a status"); return }
        done = 0
        FocusMemory.forget(ROUTE)
        queue = keys
    }

    fun close() { queue = null; Nav.focusTick++ }

    /** Moves on past the current game. */
    fun next() {
        val q = queue ?: return
        done++
        if (q.size <= 1) { close(); Toasts.show("All sorted! ${plural(done, "game")} done") } else queue = q.drop(1)
    }

    const val ROUTE = "statussorter"
}

@Composable
fun StatusSorterPopup() {
    val q = StatusSorter.queue
    val shown = rememberLatest(q)
    PanelGuard(q != null)
    InterceptButtons(q != null && TextEntry.request == null) { true }
    OverlayPanel(q != null, onClose = { StatusSorter.close() }) {
        val keys = shown ?: return@OverlayPanel
        val g = remember(keys.firstOrNull(), io.github.zahakj.tandem.Libraries.all) { keys.firstOrNull()?.let { io.github.zahakj.tandem.Libraries.byKey(it) } }
        if (g == null) { androidx.compose.runtime.LaunchedEffect(keys) { StatusSorter.next() }; return@OverlayPanel }
        val route = StatusSorter.ROUTE
        val choices = GameStatus.entries.map { it.id } + "skip"
        val target = rememberFocusTarget(route, choices)
        PanelSurface(Modifier.width(720.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sort your played games", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                ButtonHint("A", "Set", Palette.Mint)
                Spacer(Modifier.width(12.dp))
                ButtonHint("B", "Stop for now", Palette.Lavender)
                Spacer(Modifier.width(12.dp))
                Pill("${StatusSorter.done + 1} of ${StatusSorter.done + keys.size}", Palette.Sky)
            }
            Spacer(Modifier.height(10.dp))
            Row {
                androidx.compose.animation.AnimatedContent(g, label = "sorter-game") { game ->
                    Column(Modifier.width(180.dp)) {
                        Box(Modifier.size(180.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))) { GameArt(game) }
                        Spacer(Modifier.height(8.dp))
                        Text(game.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(game.platform.shortName, playProgress(game.meta)).joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (s in GameStatus.entries) {
                        FocusCard(
                            Modifier.fillMaxWidth().height(36.dp).takesFocus(route, s.id, s.id == target), glow = s.color,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            onFocus = { FocusMemory.remember(route, s.id) },
                            onClick = { StatusUi.set(g, s); StatusSorter.next() },
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50))) { StatusMarkArt(s) }
                                Spacer(Modifier.width(10.dp))
                                Text(s.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(s.blurb, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
                            }
                        }
                    }
                    FocusCard(
                        Modifier.fillMaxWidth().height(34.dp).takesFocus(route, "skip", target == "skip"), glow = Palette.Lavender,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        onFocus = { FocusMemory.remember(route, "skip") }, onClick = { StatusSorter.next() },
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Skip this one", style = MaterialTheme.typography.titleSmall, color = Palette.Lavender) }
                    }
                }
            }
        }
    }
}
