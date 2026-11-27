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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.LaunchedEffect
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
 * This week on screen: the board (a Pick of the week banner, then a row per slot of cards with their reasons),
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
 * Sized to the room it gets: a Pick of the week banner and two whole rows always show, each row's cards share its
 * width (up to four across, then it scrolls), and the highlighted row always comes into view whole.
 */
@Composable
fun WeekBoardScreen(routeKey: String, popup: Boolean = false) {
    // Rebuilt (cheaply, from the cache) when the library, the details, what is hidden, Up next or the board's own file change.
    val board = remember(Libraries.all, Store.meta, Privacy.view, WeekStore.state, WidgetStore.config.upNext, Lang.revision.intValue) { ThisWeek.board() }
    val games = remember(board) { ThisWeek.games().associateBy { it.key } }
    val fresh = remember(board, games) { board.rows.map { r -> r to r.picks.mapNotNull { p -> games[p.key]?.let { p to it } } }.filter { it.second.isNotEmpty() } }
    if (fresh.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyNote(
                Tx.s(if (ThisWeek.enabled) R.string.week_none else R.string.week_off), Tx.s(R.string.week_none_action),
                Modifier.takesFocus(routeKey, "empty", true),
            ) { openSetting("home/games", WeekOnKey) }
        }
        return
    }
    WeekBoardOn.Showing(popup)
    // The row the highlight is in: X re-rolls it.
    val focusedSlot = remember { mutableStateOf<WeekSlot?>(null) }
    // What shows: the board, except while a re-rolled row deals its new covers in, when the old ones go out first.
    // A row's cards are its places (first, second...), not its games: a re-roll puts new games in the same cards, so
    // the highlighted card keeps the focus and its place, and nothing on the board moves or resizes. (Keyed by game,
    // the highlighted card went away with its game: the focus fell to the next row, the board scrolled down to it,
    // and a second or two later scrolled back when the focus was handed back to the new cover.)
    var rows by remember { mutableStateOf(fresh) }
    val redeal = remember { Redeal() }
    LaunchedEffect(fresh) {
        val old = rows
        if (old === fresh) return@LaunchedEffect
        fun picksOf(r: List<Pair<WeekRow, List<Pair<WeekPick, Game>>>>) = r.associate { (row, picks) -> row.slot to (row.subject to picks.map { it.first.key }) }
        val before = picksOf(old)
        val after = picksOf(fresh)
        // One row changed (a re-roll, by X or the Y menu): it alone animates. Anything else just shows.
        val slot = (before.keys + after.keys).filter { before[it] != after[it] }.singleOrNull()?.takeIf { it in before && it in after }
        if (slot == null) { redeal.reset(); rows = keepSame(old, fresh); return@LaunchedEffect }
        val holds = focusedSlot.value == slot
        redeal.run(slot, still = !Flair.entering) {
            // The highlight was in the row: it stays in its place, on the game dealt there, in the same frame the
            // covers go in. The card holding it is the same card, so it never lets go; the board only learns its new
            // game (for the target, X, and the bottom screen).
            if (holds) {
                val was = before.getValue(slot).second
                val held = (FocusMemory.holder(routeKey) ?: FocusMemory.get(routeKey))?.removePrefix("w:")
                val now = fresh.first { it.first.slot == slot }.second
                val (p, g) = now[was.indexOf(held).coerceIn(0, now.lastIndex)]
                FocusMemory.remember(routeKey, "w:${p.key}")
                // From the Y menu nothing on the board holds it: the card takes it back when the menu closes.
                if (FocusMemory.holder(routeKey) != null) {
                    FocusMemory.hold(routeKey, "w:${p.key}", true)
                    Selection.current = Selected.GameItem(g)
                }
            }
            rows = keepSame(old, fresh)
        }
    }
    val keys = remember(rows) { rows.flatMap { (_, picks) -> picks.map { (p, _) -> "w:${p.key}" } } }
    val target = rememberFocusTarget(routeKey, keys)
    val active = layerActive()
    InterceptButtons(active) { b ->
        if (b != Button.X || io.github.zahakj.tandem.ui.Nav.overlayOpen) return@InterceptButtons false
        val slot = focusedSlot.value ?: return@InterceptButtons false
        // Up next is your own list, not a draw.
        if (slot == WeekSlot.UpNext) { Sounds.error(); Toasts.show(Tx.s(R.string.week_reroll_upnext)); return@InterceptButtons true }
        // The deal above hands the highlight to the row's new first cover.
        ThisWeek.reroll(slot) { changed -> if (changed) Sounds.page() else rerollRanOut() }
        true
    }
    val outer = rememberRouteListState(routeKey)
    val targetRow = rows.indexOfFirst { (_, picks) -> picks.any { (p, _) -> "w:${p.key}" == target } }
    // In a popup the header (the week, X and Y) is the popup's own; on a page it stays put above the rows, so it never
    // scrolls under the top bar (a highlighted first row nudged the list just enough to fade it out).
    KeepInView(outer, targetRow)
    Column(Modifier.fillMaxSize()) {
    if (!popup) Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(BoardHead).padding(horizontal = BoardSide), verticalAlignment = Alignment.CenterVertically) {
        Text(Tx.s(R.string.week_title), style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(
            weekOfText(), Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.labelLarge, color = Palette.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        WeekBoardHints()
    }
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        // Sized as before, when the header scrolled with the rows: the cards keep their size.
        val fit = rememberBoardFit(maxWidth, if (popup) maxHeight else maxHeight + BoardHead + 4.dp, popup)
        ListScrolling(outer, pad = KeepRoom) {
            LazyColumn(
                Modifier.fillMaxSize().fadingEdges(outer), state = outer,
                contentPadding = PaddingValues(bottom = BoardBottom), verticalArrangement = Arrangement.spacedBy(BoardGap),
            ) {
                for (entry in rows) {
                    item(entry.first.slot.id) {
                        // Only the row holding the highlight hears which card it is, so the rest skip when it moves.
                        val rowTarget = target?.takeIf { t -> entry.second.any { (p, _) -> "w:${p.key}" == t } }
                        BoardRow(entry, rowTarget, routeKey, fit, redeal, focusedSlot)
                    }
                }
            }
        }
    }
    }
}

/** A row on the board as it shows: a row and its picks with their games. */
private typealias BoardEntry = Pair<WeekRow, List<Pair<WeekPick, Game>>>

/**
 * [fresh], keeping each row of [old] that came out the same (the same object), so on a re-roll only the row that was
 * dealt again composes: the others are skipped whole.
 */
private fun keepSame(old: List<BoardEntry>, fresh: List<BoardEntry>): List<BoardEntry> {
    val bySlot = old.associateBy { it.first.slot }
    return fresh.map { e -> bySlot[e.first.slot]?.takeIf { it == e } ?: e }
}

/**
 * One row of the board ([target]: the highlighted card's key when it is in this row). The whole row comes into view
 * with its highlighted card, its heading too. Every row is as tall as its cards, whatever is dealt into them: a
 * re-roll never moves the board.
 */
@Composable
private fun BoardRow(
    entry: BoardEntry, target: String?, routeKey: String, fit: BoardFit, redeal: Redeal,
    focusedSlot: androidx.compose.runtime.MutableState<WeekSlot?>,
) {
    val ctx = LocalContext.current
    val (row, picks) = entry
    Column(Modifier.showsFromTop(whole = true)) {
        if (row.slot == WeekSlot.Pick) {
            val (p, g) = picks.first()
            val key = "w:${p.key}"
            HeroCard(
                g, p, Modifier.fillMaxWidth().padding(horizontal = BoardSide, vertical = CardRoom).height(fit.hero)
                    .redealt(redeal, row.slot, 0, reach = 8.dp).takesFocus(routeKey, key, key == target),
                onFocus = { focusedSlot.value = WeekSlot.Pick; Selection.current = Selected.GameItem(g) },
            ) { Launcher.launch(ctx, g) }
        } else {
            SectionTitle(WeekText.title(row), WeekText.color(row.slot), Modifier.padding(horizontal = BoardSide))
            val state = rememberRouteListState("$routeKey:${row.slot.id}")
            KeepInView(state, picks.indexOfFirst { (p, _) -> "w:${p.key}" == target })
            val width = fit.cardWidth(picks.size)
            PaddedScrolling {
                LazyRow(
                    Modifier.fillMaxWidth().fadingEdges(state, horizontal = true).focusRow(), state = state,
                    contentPadding = PaddingValues(horizontal = BoardSide, vertical = CardRoom), horizontalArrangement = Arrangement.spacedBy(CardGap),
                ) {
                    // Keyed by place: see `rows` in WeekBoardScreen.
                    itemsIndexed(picks, key = { i, _ -> i }) { i, (p, g) ->
                        val key = "w:${p.key}"
                        PickCard(
                            g, p, Modifier.size(width, fit.card).redealt(redeal, row.slot, i).takesFocus(routeKey, key, key == target),
                            wide = width >= WideCard,
                            onFocus = { focusedSlot.value = row.slot; Selection.current = Selected.GameItem(g) },
                        ) { Launcher.launch(ctx, g) }
                    }
                }
            }
        }
    }
}

/** A re-roll that drew the same games again: the row has nothing else this week. */
private fun rerollRanOut() {
    Sounds.error()
    Toasts.show(Tx.s(R.string.week_reroll_none))
}

/** The board's margins: its sides, between rows, the room a highlighted card grows and glows into, between cards. */
private val BoardSide = 22.dp
private val BoardGap = 4.dp
private val BoardBottom = 10.dp
private val BoardHead = 36.dp
private val CardRoom = 9.dp
private val CardGap = 12.dp
/** Cards a row shows across; a longer row shows one fewer and a part of the next, which peeks in at the end. */
private const val Across = 4
/** A card this wide has room for the whole reason, under the game's name. */
private val WideCard = 300.dp

/**
 * How big the board's pieces are in the room it has: the Pick of the week banner and two whole rows always show, and
 * the cards take what is left (bigger on a page than in a popup, smaller with large fonts). A row's cards share its
 * width, up to four across, so a short row fills it too; a longer one scrolls.
 */
private class BoardFit(val hero: Dp, val card: Dp, private val room: Dp) {
    fun cardWidth(n: Int): Dp {
        if (n <= Across) return (room - CardGap * (n - 1)) / n.coerceAtLeast(1)
        // More than fit: three whole and part of the next, wide enough for the chip and the name.
        return (room - CardGap * (Across - 1)) / (Across - 0.6f)
    }
}

@Composable
private fun rememberBoardFit(width: Dp, height: Dp, popup: Boolean): BoardFit {
    val fontScale = LocalDensity.current.fontScale
    return remember(width, height, popup, fontScale) {
        val h = height.value
        val head = if (popup) 0f else BoardHead.value + BoardGap.value + 4f
        // The banner: the box, the tag and name on a line, two lines of why (the words grow with the font).
        val hero = (h * 0.22f).coerceIn(84f * fontScale, 112f * fontScale)
        val heroItem = hero + CardRoom.value * 2 + BoardGap.value
        // A row: its heading, then the card with room for its glow.
        val title = 28f * fontScale
        val around = title + CardRoom.value * 2 + BoardGap.value
        val card = ((h - head - heroItem - BoardBottom.value) / 2f - around).coerceIn(68f * fontScale, 140f)
        // A dp short of the whole width, so rounding to pixels never makes a full row scroll (and fade) by a pixel.
        BoardFit(hero.dp, card.dp, width - BoardSide * 2 - 1.dp)
    }
}

/** Pick of the week: a banner with the game's hero art fading in from the end side, the box, its name and why. */
@Composable
private fun HeroCard(g: Game, p: WeekPick, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
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
            Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight().aspectRatio(0.75f).clip(RoundedCornerShape(10.dp))) { GameArt(g) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(WeekText.tag(WeekSlot.Pick), WeekText.color(WeekSlot.Pick))
                        Spacer(Modifier.width(10.dp))
                        Text(g.title, style = MaterialTheme.typography.titleLarge.onArt(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(WeekText.why(p), style = MaterialTheme.typography.bodyMedium.onArt(), color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * A pick in a row: a card with the box at the start and, beside it, the reason on a chip and the game's name (on a
 * [wide] card, the reason in full under it too), on a ground of the row's colour.
 */
@Composable
private fun PickCard(g: Game, p: WeekPick, modifier: Modifier, wide: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    val c = WeekText.color(p.slot)
    FocusCard(modifier, glow = c, entry = true, onFocus = onFocus, onClick = onClick) {
        Row(Modifier.fillMaxSize().background(lerpColor(c, Palette.Night, 0.86f))) {
            Box(Modifier.fillMaxHeight().aspectRatio(0.85f)) { GameArt(g) }
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = 9.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 1.dp)) {
                    Text(WeekText.chip(p), style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(g.title, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = if (wide) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (wide) Text(WeekText.why(p), style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
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
            ThisWeek.reroll(p.slot) { changed -> if (changed) Sounds.page() else rerollRanOut() }
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
