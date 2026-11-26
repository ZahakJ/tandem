package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.DateKind
import io.github.zahakj.tandem.Dates
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.Launcher
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.ThisWeek
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.TimeToBeat
import io.github.zahakj.tandem.data.WeekBoard
import io.github.zahakj.tandem.data.WeekFact
import io.github.zahakj.tandem.data.WeekPick
import io.github.zahakj.tandem.data.WeekRow
import io.github.zahakj.tandem.data.WeekSlot
import io.github.zahakj.tandem.data.WeekStore
import io.github.zahakj.tandem.data.WeekWhy
import io.github.zahakj.tandem.data.WidgetStore
import io.github.zahakj.tandem.data.playStatus
import java.time.ZoneId

/*
 * This week on screen: the board (a big Pick of the week card, then a row per slot, each card with its reason),
 * its words in the language of the moment, the Y menu's feedback rows, the bottom screen's reason and facts, and
 * the two Settings rows. The picking itself is data/WeekBoard.kt; the library side is WeekPicks.kt.
 */

/** Every word the board says, made from the pick's values each time it shows (never kept). */
object WeekText {
    fun color(slot: WeekSlot): Color = when (slot) {
        WeekSlot.Pick, WeekSlot.Hunt -> Palette.Butter
        WeekSlot.UpNext, WeekSlot.Short, WeekSlot.New -> Palette.Sky
        WeekSlot.Resume, WeekSlot.Genre -> Palette.Mint
        WeekSlot.Almost, WeekSlot.Gem -> Palette.Peach
        WeekSlot.Because, WeekSlot.Shelf, WeekSlot.Explore -> Palette.Lavender
    }

    /** The slot's short name, for a tag on a card or the bottom screen. */
    fun tag(slot: WeekSlot): String = Tx.s(
        when (slot) {
            WeekSlot.UpNext -> R.string.week_tag_upnext
            WeekSlot.Pick -> R.string.week_tag_pick
            WeekSlot.Resume -> R.string.week_tag_resume
            WeekSlot.Almost -> R.string.week_tag_almost
            WeekSlot.Short -> R.string.week_tag_short
            WeekSlot.Because -> R.string.week_tag_because
            WeekSlot.Hunt -> R.string.week_tag_hunt
            WeekSlot.Gem -> R.string.week_tag_gem
            WeekSlot.Shelf -> R.string.week_tag_shelf
            WeekSlot.New -> R.string.week_tag_new
            WeekSlot.Genre -> R.string.week_tag_genre
            WeekSlot.Explore -> R.string.week_tag_explore
        },
    )

    /** A row's heading. */
    fun title(row: WeekRow): String {
        val s = row.subject.orEmpty()
        return when (row.slot) {
            WeekSlot.UpNext -> Tx.s(R.string.week_slot_upnext)
            WeekSlot.Pick -> Tx.s(R.string.week_slot_pick)
            WeekSlot.Resume -> Tx.s(R.string.week_slot_resume)
            WeekSlot.Almost -> Tx.s(R.string.week_slot_almost)
            WeekSlot.Short -> Tx.s(R.string.week_slot_short)
            WeekSlot.Because -> Tx.s(R.string.week_slot_because, s)
            WeekSlot.Hunt -> Tx.s(R.string.week_slot_hunt)
            WeekSlot.Gem -> Tx.s(R.string.week_slot_gem)
            WeekSlot.Shelf -> Tx.s(R.string.week_slot_shelf)
            WeekSlot.New -> Tx.s(R.string.week_slot_new)
            WeekSlot.Genre -> Tx.s(R.string.week_slot_genre, s)
            WeekSlot.Explore -> Tx.s(R.string.week_slot_explore, s)
        }
    }

    private fun hltb(seconds: Int) = TimeToBeat.hours(seconds)
    private fun played(seconds: Long) = formatPlayLong(seconds)
    private fun month(ms: Long) = Dates.text(ms, DateKind.MonthYear)
    private fun stars(rating: Float) = "★ " + "%.1f".format(Lang.locale, rating * 5)

    /** Why the game is here, in a sentence: the bottom screen's big line, and the hero card's. */
    fun why(p: WeekPick): String = when (val w = p.why) {
        is WeekWhy.Taste -> Tx.s(R.string.week_why_taste, played(w.seconds), w.genre)
        WeekWhy.TopRated -> Tx.s(R.string.week_why_top_rated)
        is WeekWhy.Waiting -> Tx.s(R.string.week_why_waiting, month(w.since))
        WeekWhy.Queued -> Tx.s(R.string.week_why_queued)
        is WeekWhy.LeftOff -> if (w.days < 14) Tx.p(R.plurals.week_why_left_off_days, w.days, w.days, played(w.seconds))
            else Tx.p(R.plurals.week_why_left_off_weeks, w.days / 7, w.days / 7, played(w.seconds))
        is WeekWhy.TimeLeft -> Tx.s(R.string.week_why_time_left, hltb(w.seconds))
        is WeekWhy.AchLeft -> Tx.p(R.plurals.week_why_ach_left, w.total, w.earned, w.total)
        is WeekWhy.Short -> Tx.s(R.string.week_why_short, hltb(w.seconds))
        is WeekWhy.Studio -> Tx.s(R.string.week_why_studio, w.finished, w.developer)
        is WeekWhy.Series -> Tx.s(R.string.week_why_series, w.finished)
        is WeekWhy.SameGenre -> Tx.s(R.string.week_why_same_genre, w.finished, w.genre)
        is WeekWhy.Gem -> Tx.s(R.string.week_why_gem, stars(w.rating))
        is WeekWhy.Shelved -> Tx.s(R.string.week_why_shelved, month(w.since))
        is WeekWhy.Backlogged -> Tx.s(R.string.week_why_backlogged, month(w.since))
        is WeekWhy.Untouched -> Tx.s(R.string.week_why_untouched, month(w.since))
        is WeekWhy.Hunt -> Tx.p(R.plurals.week_why_hunt, w.total, w.earned, w.total)
        is WeekWhy.Added -> if (w.days == 0) Tx.s(R.string.week_why_added_today) else Tx.p(R.plurals.week_why_added, w.days, w.days)
        is WeekWhy.YourGenre -> Tx.s(R.string.week_why_your_genre, played(w.seconds), w.genre)
        is WeekWhy.NewGenre -> Tx.s(if (w.never) R.string.week_why_genre_of_week else R.string.week_why_new_genre, w.genre)
    }

    /** The reason in a word or two, on the chip under a card. */
    fun chip(p: WeekPick): String = when (val w = p.why) {
        is WeekWhy.Taste, WeekWhy.TopRated, is WeekWhy.Waiting -> tag(WeekSlot.Pick)
        WeekWhy.Queued -> tag(WeekSlot.UpNext)
        is WeekWhy.LeftOff -> if (w.days < 14) Tx.p(R.plurals.week_chip_days_ago, w.days, w.days) else Tx.p(R.plurals.week_chip_weeks_ago, w.days / 7, w.days / 7)
        is WeekWhy.TimeLeft -> Tx.s(R.string.week_chip_left, hltb(w.seconds))
        is WeekWhy.AchLeft -> "${w.earned} / ${w.total}"
        is WeekWhy.Hunt -> "${w.earned} / ${w.total}"
        is WeekWhy.Short -> Tx.s(R.string.week_chip_hltb, hltb(w.seconds))
        is WeekWhy.Studio -> Tx.s(R.string.week_chip_studio)
        is WeekWhy.Series -> Tx.s(R.string.week_chip_series)
        is WeekWhy.SameGenre -> Tx.s(R.string.week_chip_genre)
        is WeekWhy.Gem -> stars(w.rating)
        is WeekWhy.Shelved -> Tx.s(R.string.week_chip_since, month(w.since))
        is WeekWhy.Backlogged -> Tx.s(R.string.week_chip_since, month(w.since))
        is WeekWhy.Untouched -> Tx.s(R.string.week_chip_since, month(w.since))
        is WeekWhy.Added -> if (w.days == 0) Tx.s(R.string.week_chip_new_today) else Tx.p(R.plurals.week_chip_added, w.days, w.days)
        is WeekWhy.YourGenre, is WeekWhy.NewGenre -> Tx.s(R.string.week_chip_fresh)
    }

    /** The facts that back it up, for chips on the bottom screen. */
    fun facts(p: WeekPick): List<String> = p.facts.map { f ->
        when (f) {
            is WeekFact.TimeLeft -> Tx.s(R.string.week_fact_left, hltb(f.seconds))
            is WeekFact.Hltb -> Tx.s(R.string.week_fact_hltb, hltb(f.seconds))
            is WeekFact.Achievements -> Tx.p(R.plurals.week_fact_achievements, f.total, f.earned, f.total)
            is WeekFact.GenreTime -> Tx.s(R.string.week_fact_genre_time, f.genre, played(f.seconds))
            is WeekFact.Played -> Tx.s(R.string.week_fact_played, played(f.seconds))
        }
    }
}

// ---------------------------------------------------------------------------------------------------- the board

/**
 * The board while it is on show, as a page or a popup: the other screen then says why the highlighted game is on it
 * (large, in place of its description on Spotlight) and shows the board's own buttons, X Re-roll and Y Options.
 */
object WeekBoardOn {
    private var count by mutableIntStateOf(0)
    /** Opened as a popup (the icon grid's folders): L1 / R1 don't flip lists there. */
    var popup by mutableStateOf(false)
        private set
    val shown: Boolean get() = count > 0

    /** The pick [game] is on the board on show, or null (no board on show, or not one of its games). */
    fun pickOf(game: Game): WeekPick? = if (shown) ThisWeek.pickOf(game) else null

    @Composable
    internal fun Showing(popup: Boolean) {
        DisposableEffect(popup) {
            count++
            this@WeekBoardOn.popup = popup
            onDispose { count-- }
        }
    }
}

/** The board's own buttons, in its header (or the popup's). */
@Composable
fun WeekBoardHints() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        ButtonHint("X", Tx.s(R.string.week_hint_reroll), Palette.Sky)
        ButtonHint("Y", Tx.s(R.string.screens_hint_options), Palette.Butter)
    }
}

/** "Week of 23 Nov", under the board's title. */
fun weekOfText(): String =
    Tx.s(R.string.week_header_of, Dates.text(WeekBoard.mondayOf(System.currentTimeMillis(), ZoneId.systemDefault()), DateKind.DayMonth))

/**
 * The board, when This week is opened (as a page or a [popup]): Y on a pick has the feedback rows, X re-rolls its row.
 * Sized to the room it gets: the Pick of the week card shrinks so the first row shows under it, covers shrink when a
 * row would not fit whole, and the highlighted row always comes into view whole, its chips and names included.
 */
@Composable
fun WeekBoardScreen(routeKey: String, popup: Boolean = false) {
    val ctx = LocalContext.current
    // Rebuilt (cheaply, from the cache) when the library, the details, what is hidden, Up next or the board's own file change.
    val board = remember(Libraries.all, Store.meta, Privacy.view, WeekStore.state, WidgetStore.config.upNext, Lang.revision.intValue) { ThisWeek.board() }
    val games = remember(board) { ThisWeek.games().associateBy { it.key } }
    val rows = remember(board, games) { board.rows.map { r -> r to r.picks.mapNotNull { p -> games[p.key]?.let { p to it } } }.filter { it.second.isNotEmpty() } }
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyNote(
                Tx.s(if (ThisWeek.enabled) R.string.week_none else R.string.week_off), Tx.s(R.string.week_none_action),
                Modifier.takesFocus(routeKey, "empty", true),
            ) { openSetting("home/games", WeekOnKey) }
        }
        return
    }
    WeekBoardOn.Showing(popup)
    val keys = remember(rows) { rows.flatMap { (_, picks) -> picks.map { (p, _) -> "w:${p.key}" } } }
    val target = rememberFocusTarget(routeKey, keys)
    // The row the highlight is in: X re-rolls it.
    val focusedSlot = remember { mutableStateOf<WeekSlot?>(null) }
    val active = layerActive()
    InterceptButtons(active) { b ->
        if (b != Button.X || io.github.zahakj.tandem.ui.Nav.overlayOpen) return@InterceptButtons false
        val slot = focusedSlot.value ?: return@InterceptButtons false
        // Up next is your own list, not a draw.
        if (slot == WeekSlot.UpNext) { Sounds.error(); Toasts.show(Tx.s(R.string.week_reroll_upnext)); return@InterceptButtons true }
        ThisWeek.reroll(slot)
        Sounds.page()
        true
    }
    val outer = rememberRouteListState(routeKey)
    val targetRow = rows.indexOfFirst { (_, picks) -> picks.any { (p, _) -> "w:${p.key}" == target } }
    // In a popup the header (the week, X and Y) is the popup's own; on a page it is item 0 and the rows follow.
    val head = if (popup) 0 else 1
    KeepInView(outer, if (targetRow < 0) -1 else targetRow + head)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fit = rememberBoardFit(maxHeight)
        ListScrolling(outer, pad = KeepRoom) {
            LazyColumn(
                Modifier.fillMaxSize().fadingEdges(outer), state = outer,
                contentPadding = PaddingValues(top = if (popup) 0.dp else 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!popup) item("head") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Tx.s(R.string.week_title), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(weekOfText(), style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        WeekBoardHints()
                    }
                }
                for ((row, picks) in rows) {
                    item(row.slot.id) {
                        // The whole row comes into view with its highlighted cover: the heading, chips and names too.
                        Column(Modifier.showsFromTop(whole = true)) {
                            if (row.slot == WeekSlot.Pick) {
                                val (p, g) = picks.first()
                                val key = "w:${p.key}"
                                HeroCard(
                                    g, p, Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = FocusRoom).height(fit.hero).takesFocus(routeKey, key, key == target),
                                    compact = fit.hero < 150.dp,
                                    onFocus = { focusedSlot.value = WeekSlot.Pick; Selection.current = Selected.GameItem(g) },
                                ) { Launcher.launch(ctx, g) }
                            } else {
                                SectionTitle(WeekText.title(row), WeekText.color(row.slot), Modifier.padding(start = 22.dp, end = 22.dp))
                                val state = rememberRouteListState("$routeKey:${row.slot.id}")
                                KeepInView(state, picks.indexOfFirst { (p, _) -> "w:${p.key}" == target })
                                PaddedScrolling {
                                    LazyRow(
                                        Modifier.fadingEdges(state, horizontal = true).focusRow(), state = state,
                                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = FocusRoom), horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        items(picks, key = { it.first.key }) { (p, g) ->
                                            val key = "w:${p.key}"
                                            PickCard(
                                                g, p, fit.card, Modifier.takesFocus(routeKey, key, key == target),
                                                onFocus = { focusedSlot.value = row.slot; Selection.current = Selected.GameItem(g) },
                                            ) { Launcher.launch(ctx, g) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** How big the board's pieces are in the room it has. */
private class BoardFit(val hero: Dp, val card: DpSize)

@Composable
private fun rememberBoardFit(height: Dp): BoardFit {
    val base = Grid.homeCard
    val fontScale = LocalDensity.current.fontScale
    return remember(height, base, fontScale) {
        // A row: its heading, the cover with room for its glow, the chip and the name (the words grow with the font).
        val words = (30 + 22 + 19) * fontScale
        val around = (FocusRoom * 2).value + 6 + 6
        // A row fits whole between the list's faded edges; covers shrink (keeping their shape) when it wouldn't.
        val roomForCard = height.value - words - around - FadeEdge.value * 2
        val scale = (roomForCard / base.height.value).coerceIn(0.6f, 1f)
        val card = DpSize(base.width * scale, base.height * scale)
        val row = words + around + card.height.value
        // Pick of the week leaves room for the first row under it.
        val hero = (height.value - row - (FocusRoom * 2).value - 6).coerceIn(120f, 170f)
        BoardFit(hero.dp, card)
    }
}

/** Pick of the week: wide, with the game's hero art fading in from the end side, the box, its name and why. */
@Composable
private fun HeroCard(g: Game, p: WeekPick, modifier: Modifier, compact: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    val lib = Libraries.of(g)
    val art = remember(g.key, g.meta.hero, g.meta.screenshot) { lib?.let { it.hero(g) ?: it.screenshot(g) } }
    val rtl = isRtl
    FocusCard(modifier, glow = WeekText.color(WeekSlot.Pick), entry = true, onFocus = onFocus, onClick = onClick) {
        Box(Modifier.fillMaxSize().background(lerpColor(WeekText.color(WeekSlot.Pick), Palette.Night, 0.82f))) {
            if (art != null) {
                // The art fades out towards the words, never a dark band behind them.
                val stops = if (rtl) arrayOf(0f to 1f, 0.45f to 0.85f, 0.75f to 0f) else arrayOf(0.25f to 0f, 0.55f to 0.85f, 1f to 1f)
                AsyncImage(art, null, Modifier.fillMaxSize().fadeMaskHorizontal(*stops), contentScale = ContentScale.Crop)
            }
            // Short (a popup, a small screen): the name on one line in a smaller size, so the reason still shows whole.
            Row(Modifier.fillMaxSize().padding(if (compact) 10.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight().aspectRatio(0.75f).clip(RoundedCornerShape(10.dp))) { GameArt(g) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)) {
                    Pill(WeekText.tag(WeekSlot.Pick), WeekText.color(WeekSlot.Pick))
                    Text(
                        g.title, style = (if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall).onArt(),
                        maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(WeekText.why(p), style = MaterialTheme.typography.bodyMedium.onArt(), color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A pick in a row: its cover, and the reason on a chip under it. */
@Composable
private fun PickCard(g: Game, p: WeekPick, size: DpSize, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    Column(Modifier.width(size.width), horizontalAlignment = Alignment.CenterHorizontally) {
        GameCard(g, modifier.size(size), onFocus = onFocus, onClick = onClick, showTitle = false)
        Spacer(Modifier.height(6.dp))
        val c = WeekText.color(p.slot)
        Box(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(WeekText.chip(p), style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            g.title, Modifier.fillMaxWidth().padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = Palette.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

// ---------------------------------------------------------------------------------------------------- bottom screen

/**
 * On the bottom screen, under a game's chips: why it is on this week's board, in large type ([large], bigger still
 * while the board is on show), with the slot's tag and up to three facts behind it. Nothing for a game that isn't on
 * the board (or while This week is off).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.WeekReason(game: Game, onArt: Boolean, large: Boolean = false) {
    val board = remember(Libraries.all, Store.meta, Privacy.view, WeekStore.state, WidgetStore.config.upNext) { ThisWeek.board() }
    val p = remember(board, game.key) { if (ThisWeek.enabled) board.pickOf(game.key) else null } ?: return
    val c = WeekText.color(p.slot)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(Tx.s(R.string.week_title), Palette.readable(c), if (onArt) Modifier.darkChip() else Modifier)
        Text(WeekText.tag(p.slot), style = MaterialTheme.typography.labelLarge.let { if (onArt) it.onArt() else it }, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val big = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
    Text(WeekText.why(p), style = big.let { if (onArt) it.onArt() else it }, maxLines = if (large) 3 else 2, overflow = TextOverflow.Ellipsis)
    val facts = WeekText.facts(p).take(3)
    if (facts.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (f in facts) if (onArt) Pill(f, Palette.readable(Palette.Sky), Modifier.darkChip()) else Pill(f, Palette.Sky)
    }
}

// ---------------------------------------------------------------------------------------------------- Y menu

/**
 * The feedback rows of Y on a game on this week's board: Not for me, Played it elsewhere, and other picks for its
 * row. Up next is the menu's own row. Kept on the device; Undo puts each back.
 */
fun weekMenuActions(g: Game, close: () -> Unit): List<MenuAction> {
    val p = ThisWeek.pickOf(g) ?: return emptyList()
    return buildList {
        if (p.slot != WeekSlot.UpNext) add(MenuAction("week-reroll", Tx.s(R.string.week_menu_reroll), Glyph.Refresh, WeekText.color(p.slot), value = WeekText.tag(p.slot),
            help = Tx.s(R.string.week_menu_reroll_help)) {
            ThisWeek.reroll(p.slot)
            Sounds.page()
            close()
        })
        add(MenuAction("week-not", Tx.s(R.string.week_menu_not_for_me), Glyph.Close, Palette.Peach, help = Tx.s(R.string.week_menu_not_for_me_help)) {
            ThisWeek.notForMe(g)
            Undo.offer(Tx.s(R.string.week_menu_not_for_me_done, g.title)) { ThisWeek.undoNotForMe(g) }
            close()
        })
        if (g.meta.playStatus?.beaten != true) add(MenuAction("week-elsewhere", Tx.s(R.string.week_menu_elsewhere), Glyph.Trophy, Palette.Butter, help = Tx.s(R.string.week_menu_elsewhere_help)) {
            val before = ThisWeek.playedElsewhere(g)
            Undo.offer(Tx.s(R.string.week_menu_elsewhere_done, g.title)) {
                Store.update(g.key) { it.copy(status = before.status, statusAt = before.statusAt, finishedAt = before.finishedAt) }
            }
            close()
        })
    }
}

// ---------------------------------------------------------------------------------------------------- Settings

/** Focus key of the Weekly picks row (its group and label), for the empty board's button. */
internal val WeekOnKey get() = rowKey("This week", "Weekly picks")

/** Home & layout › How games show › This week: the switch and Reset suggestions. */
@Composable
fun weekSettings(): SettingSection {
    val s = WeekStore.state
    return SettingSection(Tx.s(R.string.week_settings_group), en = "This week",
        rows = listOf(
            toggle(Tx.s(R.string.week_settings_on), Glyph.Star, Palette.Butter, Tx.s(R.string.week_settings_on_help), s.on, en = "Weekly picks") { on ->
                ThisWeek.setOn(on)
            }.copy(keywords = "this week weekly picks recommendations suggestions discover discovery pick of the week board what to play next"),
            SettingRow(
                Tx.s(R.string.week_settings_reset),
                if (s.notForMe.isEmpty()) "" else Tx.p(R.plurals.week_settings_reset_value, s.notForMe.size, s.notForMe.size),
                Palette.Peach, Glyph.Refresh, Tx.s(R.string.week_settings_reset_help),
                action = Tx.s(R.string.week_settings_reset_action), en = "Reset suggestions",
                keywords = "reset suggestions not for me forget history picks this week weekly recommendations clear",
            ) {
                ThisWeek.reset()
                Toasts.show(Tx.s(R.string.week_settings_reset_done))
            },
        ),
    )
}
