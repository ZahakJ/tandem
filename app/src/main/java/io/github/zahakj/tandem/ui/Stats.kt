package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.Platform
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.PlaySession
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.everPlayed
import io.github.zahakj.tandem.plural
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** The tabs on the stats screen; L1 and R1 switch between them. */
object StatsTabs {
    val names = listOf("Overview", "Sessions", "Achievements")
    var tab by mutableIntStateOf(0)
    fun shift(by: Int) { tab = (tab + by + names.size) % names.size }
}

/** Everything the stats screen shows, worked out once from the library and the play history. */
private class PlayStats(games: List<Game>, val sessions: List<PlaySession>) {
    val zone: ZoneId = ZoneId.systemDefault()
    val today: LocalDate = LocalDate.now(zone)
    /** Every game with any play history (Tandem's or imported), the same set everywhere on this screen. */
    val played = games.filter { it.meta.everPlayed }
    private val timed = played.filter { it.meta.playSeconds > 0 }
    val totalSeconds = timed.sumOf { it.meta.playSeconds }
    val gamesPlayed = played.size
    val perSystem: List<Pair<Platform, Long>> = timed.groupBy { it.platform.id }
        .map { (_, g) -> g.first().platform to g.sumOf { it.meta.playSeconds } }
        .sortedByDescending { it.second }
    val top: List<Game> = timed.sortedByDescending { it.meta.playSeconds }.take(10)

    private fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** Seconds played on each of the last 30 days; the last entry is today. */
    val days = LongArray(30).also { arr ->
        for (s in sessions) {
            val ago = ChronoUnit.DAYS.between(dateOf(s.s), today)
            if (ago in 0..29) arr[(29 - ago).toInt()] += s.d
        }
    }

    private val playDays: Set<LocalDate> = sessions.map { dateOf(it.s) }.toSet()

    /** Days in a row with play, ending today (or yesterday, so the streak survives until you play today). */
    val streak: Int = run {
        var d = if (today in playDays) today else today.minusDays(1)
        var n = 0
        while (d in playDays) { n++; d = d.minusDays(1) }
        n
    }

    val bestStreak: Int = run {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in playDays.sorted()) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        best
    }

    val averageSession: Long = if (sessions.isEmpty()) 0 else sessions.sumOf { it.d } / sessions.size

    /** When you usually play, weighted by how long. */
    val timeOfDay: String? = run {
        if (sessions.size < 3) return@run null
        val buckets = LongArray(4)
        for (s in sessions) {
            val h = Instant.ofEpochMilli(s.s).atZone(zone).hour
            buckets[when (h) { in 5..11 -> 0; in 12..17 -> 1; in 18..22 -> 2; else -> 3 }] += s.d
        }
        when (buckets.indices.maxBy { buckets[it] }) {
            0 -> "You're an early bird: most of your play happens in the morning."
            1 -> "Afternoon adventurer: that's when you play the most."
            2 -> "Evenings are your game time. Cosy!"
            else -> "A true night owl. Most of your play is after 11 PM."
        }
    }
}

/** Sessions that may be shown: hidden games only when they're revealed. */
private fun shownSessions(): List<PlaySession> =
    HomeStore.sessions.filter { Privacy.canShowKey(it.k) }

private fun titleFor(key: String, game: Game?): String = sessionTitle(key, game)

@Composable
fun StatsScreen() {
    val tab = StatsTabs.tab
    // "Play sessions…" shows one game's sessions until Stats is left.
    DisposableEffect(Unit) { onDispose { PlaySessions.only = null; PlaySessions.focused = null } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatsTabs.names.forEachIndexed { i, name ->
                val color = listOf(Palette.Mint, Palette.Sky, Palette.Butter)[i]
                FocusCard(
                    Modifier.height(30.dp),
                    glow = color,
                    shape = RoundedCornerShape(50),
                    onFocus = { Selection.current = Selected.Home },
                    onClick = { StatsTabs.tab = i },
                ) {
                    Box(
                        Modifier.fillMaxHeight().background(if (i == tab) color.copy(alpha = 0.85f) else color.copy(alpha = 0.14f)).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(name, style = MaterialTheme.typography.labelLarge, color = if (i == tab) Palette.NightDeep else color) }
                }
            }
            Spacer(Modifier.weight(1f))
            ButtonHint("L1", "", Palette.Muted)
            ButtonHint("R1", "Switch tab", Palette.Muted)
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> Overview()
                1 -> SessionsList()
                else -> AchievementsTab()
            }
        }
    }
    PlaySessionPopups()
}

@Composable
private fun Overview() {
    val ctx = LocalContext.current
    val sessions = shownSessions()
    val games = Collections.visibleGames()
    val stats = remember(sessions, games, Store.meta) { PlayStats(games, sessions) }
    val route = "stats:0"
    if (stats.played.isEmpty() && sessions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyNote("Play something and I'll keep count here, all on this device", "Go back", Modifier.takesFocus(route, "empty", true), mood = 1) { Nav.back() }
        }
        return
    }
    // Imported play time with no sessions recorded in Tandem yet: say so, rather than showing contradictory zeros.
    val noHistory = sessions.isEmpty()
    val keys = listOf("total", "games", "streak", "sessions", "chart") + stats.perSystem.map { "sys:${it.first.id}" } +
        stats.top.map { "top:${it.key}" } + listOfNotNull("fact".takeIf { stats.timeOfDay != null })
    val target = rememberFocusTarget(route, keys)
    val list = rememberRouteListState(route)
    val targetItem = when {
        target == null -> -1
        target in listOf("total", "games", "streak", "sessions") -> 0
        target == "chart" -> 1
        target.startsWith("sys:") -> 2
        target.startsWith("top:") -> if (stats.perSystem.isEmpty()) 2 else 3
        else -> 2 + (if (stats.perSystem.isEmpty()) 0 else 1) + (if (stats.top.isEmpty()) 0 else 1)
    }
    KeepInView(list, targetItem)
    LazyColumn(
        Modifier.fillMaxSize().fadingEdges(list),
        state = list,
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = FocusRoom, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item("tiles") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Total play", if (stats.totalSeconds > 0) formatPlayTime(stats.totalSeconds) else "–",
                    "across ${plural(stats.played.size, "game")}", Palette.Mint,
                    Modifier.weight(1f).takesFocus(route, "total", target == "total"), route, "total",
                )
                StatTile("Games played", "${stats.gamesPlayed}", "of ${games.size} in your library", Palette.Sky, Modifier.weight(1f).takesFocus(route, "games", target == "games"), route, "games")
                StatTile(
                    "Streak",
                    if (noHistory) "New" else if (stats.streak == 0) "–" else plural(stats.streak, "day"),
                    if (noHistory) "history starts today" else "best ${plural(stats.bestStreak, "day")}", Palette.Peach,
                    Modifier.weight(1f).takesFocus(route, "streak", target == "streak"), route, "streak",
                )
                StatTile(
                    "Sessions", if (noHistory) "–" else "${sessions.size}",
                    when {
                        noHistory -> "history starts today"
                        stats.averageSession < 60 -> "short and sweet"
                        else -> "about ${formatPlayTime(stats.averageSession)} each"
                    },
                    Palette.Butter,
                    Modifier.weight(1f).takesFocus(route, "sessions", target == "sessions"), route, "sessions",
                )
            }
        }
        item("chart") { DayChart(stats, noHistory, Modifier.takesFocus(route, "chart", target == "chart")) }
        if (stats.perSystem.isNotEmpty()) item("systems") {
            Column {
                SectionTitle("By system", Palette.Lavender)
                val max = stats.perSystem.first().second.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((platform, seconds) in stats.perSystem) {
                        val color = Palette.forName(platform.id)
                        val key = "sys:${platform.id}"
                        FocusCard(
                            Modifier.fillMaxWidth().height(44.dp).takesFocus(route, key, key == target),
                            glow = color, shape = RoundedCornerShape(14.dp),
                            onFocus = { Libraries.byId(platform.id)?.let { Selection.current = Selected.System(it) } },
                            entry = true,
                            onClick = { Nav.go(Route.System(platform.id)) },
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(platform.shortName, Modifier.width(70.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(50)).background(Palette.NightDeep)) {
                                    Box(Modifier.fillMaxWidth(seconds / max.toFloat()).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))))
                                }
                                Text(formatPlayTime(seconds), Modifier.padding(start = 12.dp).width(84.dp), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        if (stats.top.isNotEmpty()) item("top") {
            Column {
                SectionTitle("Most played", Palette.Peach)
                val topRow = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyRow(Modifier.fadingEdges(topRow, horizontal = true).focusRow(), state = topRow, horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom)) {
                    items(stats.top, key = { it.key }) { g ->
                        val key = "top:${g.key}"
                        Column(Modifier.width(104.dp)) {
                            GameCard(
                                g, Modifier.size(104.dp).takesFocus(route, key, key == target),
                                onFocus = { Selection.current = Selected.GameItem(g) },
                                onClick = { Launcher.launch(ctx, g) },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(formatPlayTime(g.meta.playSeconds), style = MaterialTheme.typography.labelMedium, color = Palette.Peach, maxLines = 1)
                        }
                    }
                }
            }
        }
        stats.timeOfDay?.let { fact ->
            item("fact") {
                FocusCard(
                    Modifier.fillMaxWidth().takesFocus(route, "fact", target == "fact"), glow = Palette.Lavender,
                    onFocus = { FocusMemory.remember(route, "fact"); Selection.current = Selected.Home },
                    onClick = { Toasts.show("Your stats never leave this device") },
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pip(46.dp, mood = 1)
                        Spacer(Modifier.width(12.dp))
                        Text(fact, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, detail: String, color: Color, modifier: Modifier, route: String, key: String) {
    FocusCard(modifier.height(96.dp), glow = color, onFocus = { FocusMemory.remember(route, key); Selection.current = Selected.Home }, onClick = { Toasts.show("$label: $value · $detail") }) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color.copy(alpha = 0.28f), Palette.Card))))
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The last 30 days as soft rounded bars that grow in; left and right pick a day. */
@Composable
private fun DayChart(stats: PlayStats, noHistory: Boolean, modifier: Modifier) {
    var selected by androidx.compose.runtime.remember { mutableIntStateOf(29) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow)) }
    val days = stats.days
    val monthTotal = days.sum()
    val day = stats.today.minusDays((29 - selected).toLong())
    val dayName = when (selected) {
        29 -> "Today"
        28 -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    if (monthTotal == 0L) {
        // Nothing to chart: a friendly line instead of a row of empty dots.
        FocusCard(
            modifier.fillMaxWidth().height(110.dp), glow = Palette.Mint,
            onFocus = { Selection.current = Selected.Home }, onClick = {},
        ) {
            Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Pip(56.dp, mood = if (noHistory) 1 else 2)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Last 30 days", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (noHistory) "Your play history starts today. Play something and your days fill in here."
                        else "No play in the last 30 days. Your next session shows up here.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                    )
                }
            }
        }
        return
    }
    FocusCard(
        modifier.fillMaxWidth().height(210.dp).onPreviewKeyEvent { e ->
            val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> return@onPreviewKeyEvent false }
            // Let focus leave at the ends.
            if (selected + d !in 0..29) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) selected += d
            true
        },
        glow = Palette.Mint,
        onFocus = { Selection.current = Selected.Home },
        onClick = { selected = 29 },
    ) { focused ->
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last 30 days", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Pill(if (monthTotal == 0L) "Nothing yet" else formatPlayTime(monthTotal) + " in total", Palette.Mint)
            }
            Text(
                "$dayName · " + if (days[selected] == 0L) "no play" else formatPlayTime(days[selected]),
                style = MaterialTheme.typography.labelLarge, color = if (focused) Palette.Peach else Palette.Muted,
            )
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                val max = maxOf(days.max(), 30 * 60L).toFloat()
                val slot = size.width / 30f
                val barW = slot * 0.62f
                val base = size.height - 4.dp.toPx()
                val topRoom = 10.dp.toPx()
                drawLine(
                    Palette.Muted.copy(alpha = 0.25f), Offset(0f, base + 2.dp.toPx()), Offset(size.width, base + 2.dp.toPx()),
                    strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
                )
                val best = days.indices.maxBy { days[it] }
                for (i in 0 until 30) {
                    val cx = slot * i + slot / 2
                    val v = days[i]
                    val isSel = i == selected && focused
                    if (v == 0L) {
                        drawCircle(if (isSel) Palette.Peach else Palette.Muted.copy(alpha = 0.35f), 2.5.dp.toPx(), Offset(cx, base - 2.dp.toPx()))
                        continue
                    }
                    val h = ((base - topRoom) * (v / max) * grow.value).coerceAtLeast(5.dp.toPx())
                    val brush = if (isSel) Brush.verticalGradient(listOf(Palette.Butter, Palette.Peach), startY = base - h, endY = base)
                    else Brush.verticalGradient(listOf(Palette.Mint, Palette.Sky.copy(alpha = 0.75f)), startY = base - h, endY = base)
                    drawRoundRect(brush, Offset(cx - barW / 2, base - h), Size(barW, h), CornerRadius(barW / 2))
                    if (i == best && grow.value > 0.9f) {
                        // A little sparkle on your biggest day.
                        val sy = base - h - 6.dp.toPx()
                        drawCircle(Palette.Butter, 2.5.dp.toPx(), Offset(cx, sy))
                        drawCircle(Palette.Butter.copy(alpha = 0.35f), 5.dp.toPx(), Offset(cx, sy))
                    }
                }
            }
            Row {
                Text(stats.today.minusDays(29).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, modifier = Modifier.weight(1f))
                Text("Today", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
            }
        }
    }
}

/**
 * Every session, newest first and by day; only one game's when opened from its Y menu. A plays the game; Y or a
 * long press opens the session's menu (PlaySessions.kt).
 */
@Composable
private fun SessionsList() {
    val ctx = LocalContext.current
    val only = PlaySessions.only
    val all = shownSessions().let { l -> if (only == null) l else l.filter { it.k == only } }
    val sessions = all.asReversed().take(400)
    val route = PlaySessions.ROUTE
    val byKey = remember(Libraries.all) { Libraries.games.associateBy { it.key } }
    val onlyTitle = only?.let { titleFor(it, byKey[it]) }
    // Y on a session opens its menu; anywhere else it does what it always does.
    InterceptButtons(Nav.route == Route.Stats && StatsTabs.tab == 1 && !Nav.overlayOpen && Layers.top == null) { b ->
        val s = PlaySessions.focused?.takeIf { b == Button.Y } ?: return@InterceptButtons false
        PlaySessions.openMenu(s)
        true
    }
    if (sessions.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (only != null) {
                EmptyNote("No sessions for $onlyTitle: its play time may come from an import", "Show all sessions", Modifier.takesFocus(route, "all", true), color = Palette.Sky) {
                    PlaySessions.only = null
                }
            } else {
                EmptyNote("Every time you play, a session shows up here", "See the overview", Modifier.takesFocus(route, "empty", true)) { StatsTabs.tab = 0 }
            }
        }
        return
    }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val groups = sessions.groupBy { Instant.ofEpochMilli(it.s).atZone(zone).toLocalDate() }
    val heads = listOfNotNull("all".takeIf { only != null }, "clear")
    val target = rememberFocusTarget(route, heads + sessions.map { PlaySessions.rowKey(it) })
    val list = rememberRouteListState(route)
    var index = if (target in heads) 0 else -1
    var i = 1
    for ((_, l) in groups) { i++; for (s in l) { if (PlaySessions.rowKey(s) == target) index = i; i++ } }
    KeepInView(list, index)
    LazyColumn(
        Modifier.fillMaxSize().fadingEdges(list),
        state = list,
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = FocusRoom, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("head") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (only != null) PillButton("Show all", Palette.Sky, Modifier.takesFocus(route, "all", target == "all")) {
                    PlaySessions.only = null
                    FocusMemory.remember(route, "all")
                }
                PillButton(if (only != null) "Clear these sessions" else "Clear all sessions", Palette.Peach, Modifier.takesFocus(route, "clear", target == "clear")) {
                    Confirm.hold(if (only != null) "Clear $onlyTitle's sessions?" else "Clear all sessions?", clearDetail(all, onlyTitle), "Clear") {
                        PlaySessions.clear(all)
                        Toasts.show(plural(all.size, "session") + " cleared")
                    }
                }
                Text(
                    onlyTitle?.let { "$it · ${plural(all.size, "session")}" } ?: plural(all.size, "session"),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                ButtonHint("Y", "Edit session", Palette.Butter)
            }
        }
        for ((date, list) in groups) {
            item("d:$date") {
                val label = when (ChronoUnit.DAYS.between(date, today)) {
                    0L -> "Today"
                    1L -> "Yesterday"
                    else -> date.format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
                }
                SectionTitle("$label · ${formatPlayTime(list.sumOf { it.d })}", Palette.Sky, Modifier.padding(top = 6.dp))
            }
            items(list, key = { "s:${it.k}:${it.s}" }) { s ->
                val game = byKey[s.k]
                val key = PlaySessions.rowKey(s)
                val color = game?.let { Palette.forName(it.platform.id) } ?: Palette.Lavender
                FocusCard(
                    Modifier.fillMaxWidth().height(58.dp).takesFocus(route, key, key == target)
                        .onFocusChanged { if (it.isFocused) PlaySessions.focused = s else if (PlaySessions.focused == s) PlaySessions.focused = null },
                    glow = color, shape = RoundedCornerShape(14.dp),
                    onFocus = { Selection.current = game?.let { Selected.GameItem(it) } ?: Selected.Home },
                    // A long press is Y: the session's menu.
                    entry = true,
                    onClick = { if (game != null) Launcher.launch(ctx, game) else Toasts.show("That game isn't in your library any more") },
                ) {
                    Row(Modifier.fillMaxSize().padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) { SessionArt(s.k, game) }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(titleFor(s.k, game), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                Instant.ofEpochMilli(s.s).atZone(zone).toLocalTime().format(timeFormat),
                                style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1,
                            )
                        }
                        game?.let { Pill(it.platform.shortName, color) }
                        Spacer(Modifier.width(8.dp))
                        Text(formatPlayTime(s.d), Modifier.width(76.dp), style = MaterialTheme.typography.labelLarge, color = Palette.Mint, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun csv(vararg fields: Any?): String = fields.joinToString(",") { f ->
    val s = f?.toString().orEmpty()
    if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

/**
 * Writes play-stats.csv and play-sessions.csv to ROMs/tandem. Hidden games are always left out, whatever is shown
 * on screen.
 */
fun exportStats(ctx: Context) {
    val games = Libraries.games.filter { !Privacy.isHidden(it) && (it.meta.playSeconds > 0 || it.meta.playCount > 0) }
    val sessions = HomeStore.sessions.filter { !Privacy.isHiddenKey(it.k) }
    val byKey = Libraries.games.associateBy { it.key }
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    var ok = false
    inBackground({
        ok = runCatching {
            val dir = File(Library.romsDir, "tandem").apply { mkdirs() }
            File(dir, "play-stats.csv").writeText(
                buildString {
                    appendLine(csv("Title", "System", "File", "Minutes played", "Times played", "Last played"))
                    for (g in games.sortedByDescending { it.meta.playSeconds }) {
                        appendLine(csv(g.title, g.platform.name, g.file.name, g.meta.playSeconds / 60, g.meta.playCount, g.meta.lastPlayed.takeIf { it > 0 }?.let { fmt.format(Instant.ofEpochMilli(it)) }))
                    }
                },
            )
            File(dir, "play-sessions.csv").writeText(
                buildString {
                    appendLine(csv("Title", "System", "Started", "Minutes"))
                    for (s in sessions) {
                        val g = byKey[s.k]
                        appendLine(csv(titleFor(s.k, g), g?.platform?.name ?: s.k.substringBefore('/'), fmt.format(Instant.ofEpochMilli(s.s)), s.d / 60))
                    }
                },
            )
        }.isSuccess
    }) {
        Toasts.show(if (ok) "Saved play-stats.csv and play-sessions.csv in ROMs/tandem" else "Couldn't write to ROMs/tandem")
    }
}
