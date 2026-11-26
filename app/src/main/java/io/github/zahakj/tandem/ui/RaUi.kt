package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.DateKind
import io.github.zahakj.tandem.Dates
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.NetGate
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.RaList
import io.github.zahakj.tandem.RetroAchievements
import io.github.zahakj.tandem.SteamApi
import io.github.zahakj.tandem.Tx
import kotlinx.coroutines.launch

/*
 * A RetroAchievements game's achievements (Y › Achievements): the progress in the set, every achievement with its
 * badge, points, rarity and a Missable tag, filters (All · Locked · Unlocked · Missable) and sorts (set order, rarest,
 * most points). A on one opens players' hints for it, fetched then and only then, blurred until asked for again.
 * The pieces (badge, header, hint text) are shared with the in-game page on the bottom screen (RaCompanion.kt).
 *
 * Network: the list is asked for when the popup opens (one call, kept on disk afterwards; what was kept shows at
 * once), X asks again, and an achievement's comments are asked for only on A. Nothing for hidden games, nothing in
 * private mode, nothing while signed out.
 */

/** RetroAchievements' gold, for everything RetroAchievements here, as the progress strip uses it. */
internal val RaColor get() = Palette.Butter

private val greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** The size badges are decoded at: RetroAchievements' are 64 px, and none is drawn bigger than this. */
private const val BADGE_PX = 96

/** An achievement's badge: coloured once earned, grey and faded while locked. Decoded small, cached by Coil. */
@Composable
internal fun RaBadge(a: RetroAchievements.Achievement, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val request = remember(a.badgeUrl) { ImageRequest.Builder(ctx).data(a.badgeUrl).size(BADGE_PX).build() }
    AsyncImage(
        request, a.title,
        modifier.size(size).clip(RoundedCornerShape(size * 0.2f)).background(Palette.CardHigh).alpha(if (a.earned) 1f else 0.55f),
        colorFilter = if (a.earned) null else greyscale,
    )
}

/** The filter's name, for its chip. */
internal fun raFilterLabel(f: RaList.Filter): String = Tx.s(
    when (f) {
        RaList.Filter.All -> R.string.accounts_ra_list_all
        RaList.Filter.Locked -> R.string.accounts_ra_list_locked
        RaList.Filter.Unlocked -> R.string.accounts_ra_list_unlocked
        RaList.Filter.Missable -> R.string.accounts_ra_list_missable
    },
)

private fun raSortLabel(s: RaList.Sort): String = Tx.s(
    when (s) {
        RaList.Sort.Default -> R.string.accounts_ra_list_sort_default
        RaList.Sort.Rarity -> R.string.accounts_ra_list_sort_rarity
        RaList.Sort.Points -> R.string.accounts_ra_list_sort_points
    },
)

/** The share of players who have [a], as a label ("2.3 %"), or null when unknown. */
internal fun raRarity(a: RetroAchievements.Achievement, players: Int): String? =
    RaList.share(a, players)?.let { SteamApi.percentLabel(it * 100f) }

/** The line under an achievement: locked, or when it was unlocked and in which mode. */
internal fun raEarnedLine(a: RetroAchievements.Achievement): String {
    if (!a.earned) return Tx.s(R.string.accounts_steam_locked)
    val ms = raDateMs(a.earnedAt)
    return when {
        a.earnedHardcore && ms > 0 -> Tx.s(R.string.accounts_ra_list_hardcore_on, Dates.text(ms, DateKind.Date))
        a.earnedHardcore -> Tx.s(R.string.accounts_ra_list_hardcore_unlocked)
        ms > 0 -> Tx.s(R.string.accounts_steam_unlocked_on, Dates.text(ms, DateKind.Date))
        else -> Tx.s(R.string.accounts_steam_unlocked)
    }
}

/** The progress in a set: earned of total, a bar, points, and how much was hardcore. */
@Composable
internal fun RaProgressHeader(p: RetroAchievements.Progress) {
    val fraction = if (p.total == 0) 0f else (p.earned.toFloat() / p.total).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏆 ${p.earned} / ${p.total}", style = MaterialTheme.typography.titleSmall, color = RaColor, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(Palette.NightDeep)) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(RoundedCornerShape(50)).background(RaColor))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (p.total > 0 && p.earned >= p.total) Tx.s(R.string.accounts_achievements_mastered) else Tx.s(R.string.common_percent, (fraction * 100).toInt()),
                style = MaterialTheme.typography.labelLarge, color = RaColor, maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.points > 0) Pill(Tx.p(R.plurals.accounts_ra_list_points, p.points, p.earnedPoints, p.points), RaColor)
            if (p.hardcoreCount > 0) Pill(Tx.p(R.plurals.accounts_ra_list_hardcore, p.hardcoreCount), Palette.Peach)
            val soft = p.achievements.count { it.earned && !it.earnedHardcore }
            if (soft > 0) Pill(Tx.p(R.plurals.accounts_ra_list_softcore, soft), Palette.Lavender)
        }
    }
}

/** One comment on an achievement: who and when, then the text, blurred until [revealed]. */
@Composable
internal fun RaComment(c: RaList.Comment, revealed: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val ms = raDateMs(c.at)
        Text(
            listOfNotNull(c.user.ifBlank { null }, if (ms > 0) Dates.text(ms, DateKind.Date) else null).joinParts(),
            style = MaterialTheme.typography.labelMedium, color = RaColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        // Blurred (and faded, where Android can't blur) until asked for: a hint can give the game away.
        Text(
            c.text, style = MaterialTheme.typography.bodyMedium, color = Palette.Text,
            modifier = if (revealed) Modifier else Modifier.blur(7.dp).alpha(0.35f),
        )
    }
}

// --- The popup ---------------------------------------------------------------------------------------------------

/** The popup with every achievement of one RetroAchievements game, and its hints (Y › Achievements). */
object RaUi {
    const val ROUTE = "raach"
    const val HINTS = "raach:hints"

    var popupFor by mutableStateOf<Game?>(null)
        private set

    /** The achievement whose hints are showing, over the list. */
    var hintFor by mutableStateOf<RetroAchievements.Achievement?>(null)

    /** Kept for the session, so the list opens the way it was left. */
    var filter by mutableStateOf(RaList.Filter.All)
    var sort by mutableStateOf(RaList.Sort.Default)

    /** Bumped by X: ask RetroAchievements again. */
    var refresh by mutableIntStateOf(0)

    fun openFor(game: Game) {
        FocusMemory.forget(ROUTE)
        hintFor = null
        refresh = 0
        popupFor = game
    }

    fun close() {
        popupFor = null
        hintFor = null
        Nav.focusTick++
    }

    /**
     * True for a game the Y menu row and the popup apply to: signed in, not hidden, a system with achievements, not a
     * Steam game, and not known to have none (matched to "none" by hand or by an earlier look).
     */
    fun applies(game: Game): Boolean =
        RetroAchievements.enabled && !Privacy.isHidden(game) && !SteamUi.applies(game) &&
            RetroAchievements.consoleId(game.platform.id) != null && RetroAchievements.knownMatch(game) != 0
}

/** Placed in HomeHost, so it opens over any screen. */
@Composable
fun RaAchievementsPopup() {
    val g = RaUi.popupFor
    val shown = rememberLatest(g)
    PanelGuard(g != null)
    InterceptButtons(g != null && TextEntry.request == null) { b ->
        val hint = RaUi.hintFor
        when {
            hint != null && b == Button.X -> RaHintsPopup.more(hint)
            hint != null -> Unit
            b == Button.L1 || b == Button.R1 -> {
                // L1 is the chip on the left: the previous one left to right, the next one right to left.
                val by = (if (b == Button.R1) 1 else -1) * (if (rtlNow) -1 else 1)
                RaUi.filter = RaList.step(RaUi.filter, by)
                FocusMemory.forget(RaUi.ROUTE)
                Nav.focusTick++
                Sounds.page()
            }
            b == Button.Y -> { RaUi.sort = RaList.next(RaUi.sort); Sounds.toggle() }
            b == Button.X -> {
                Toasts.show(Tx.s(R.string.accounts_ra_list_asking))
                RaUi.refresh++
            }
        }
        true
    }
    OverlayPanel(g != null, onClose = { if (RaUi.hintFor != null) { RaUi.hintFor = null; Nav.focusTick++ } else RaUi.close() }) {
        val game = shown ?: return@OverlayPanel
        var progress by remember(game.key) { mutableStateOf<RetroAchievements.Progress?>(null) }
        var done by remember(game.key) { mutableStateOf(false) }
        LaunchedEffect(game.key) { progress = RetroAchievements.stored(game) }
        // Once on opening, and again on X: never while scrolling or filtering.
        LaunchedEffect(game.key, RaUi.refresh) {
            val again = RaUi.refresh > 0
            val p = RetroAchievements.progress(game, maxAgeMs = if (again) 0L else 10 * 60 * 1000L)
            if (p != null) progress = p
            else if (again) Toasts.show(Tx.s(if (NetGate.closed) R.string.accounts_ra_list_private else R.string.accounts_ra_list_failed))
            done = true
        }
        val hint = RaUi.hintFor
        PanelSurface(Modifier.width(680.dp)) {
            if (hint != null) with(RaHintsPopup) { Body(game, hint) }
            else RaListBody(game, progress, done)
        }
    }
}

@Composable
private fun ColumnScope.RaListBody(game: Game, p: RetroAchievements.Progress?, done: Boolean) {
    val filter = RaUi.filter
    val sort = RaUi.sort
    val all = p?.achievements.orEmpty()
    val list = remember(p, filter, sort) { RaList.shown(all, filter, sort, p?.players ?: 0) }
    val counts = remember(p) { RaList.counts(all) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p?.title?.ifBlank { null } ?: game.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p == null) {
                Text(
                    Tx.s(if (done) R.string.accounts_ra_list_failed else R.string.accounts_ra_list_asking),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Pill("RetroAchievements", RaColor) // tx-ok: a name
    }
    if (p != null && p.total > 0) {
        Spacer(Modifier.height(8.dp))
        RaProgressHeader(p)
        Spacer(Modifier.height(10.dp))
        // The filters, as chips L1 / R1 move between (a tap picks one too).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in RaList.Filter.entries) {
                val on = f == filter
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) RaColor.copy(alpha = 0.28f) else Palette.NightDeep)
                        .then(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)),
                ) {
                    Text(
                        "${raFilterLabel(f)} · ${counts[f] ?: 0}",
                        style = MaterialTheme.typography.labelLarge, color = if (on) RaColor else Palette.Muted, maxLines = 1,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    val keys = list.map { "a:${it.id}:${it.order}" }
    val target = rememberFocusTarget(RaUi.ROUTE, keys)
    val state = rememberLazyListState()
    KeepInView(state, keys.indexOf(target))
    when {
        p == null && !done -> PipNote(Tx.s(R.string.accounts_steam_fetching), Tx.s(R.string.accounts_achievements_loading_text))
        p == null -> PipNote(Tx.s(R.string.accounts_achievements_failed), Tx.s(R.string.accounts_ra_list_failed_text), mood = 2)
        all.isEmpty() -> PipNote(Tx.s(R.string.accounts_steam_nothing), Tx.s(R.string.accounts_ra_list_none_text), mood = 2)
        list.isEmpty() -> PipNote(Tx.s(R.string.accounts_ra_list_empty), Tx.s(R.string.accounts_ra_list_empty_text), mood = 1)
        else -> LazyColumn(
            Modifier.weight(1f, fill = false).fadingEdges(state),
            state = state,
            contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { "a:${it.id}:${it.order}" }) { a ->
                val key = "a:${a.id}:${a.order}"
                RaAchievementRow(a, p.players, Modifier.takesFocus(RaUi.ROUTE, key, key == target), onFocus = {
                    FocusMemory.remember(RaUi.ROUTE, key)
                    // The other screen shows the highlighted one with its whole description.
                    io.github.zahakj.tandem.Selection.current = io.github.zahakj.tandem.Selected.Setting(a.title, raEarnedLine(a), a.description.ifBlank { null }, RaColor, Tx.s(R.string.accounts_ra_list_hints))
                }) {
                    RaHintsPopup.open(game, a)
                }
            }
        }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ButtonHint("A", Tx.s(R.string.accounts_ra_list_hints), Palette.Mint)
        ButtonHint("L1 R1", Tx.s(R.string.accounts_ra_list_filter), RaColor)
        ButtonHint("Y", raSortLabel(sort), Palette.Sky)
        ButtonHint("X", Tx.s(R.string.accounts_steam_look_again), RaColor)
        ButtonHint("B", Tx.s(R.string.common_close), Palette.Lavender)
    }
}

/** One achievement: badge, title, description, when it was earned, its points, rarity and a Missable tag. */
@Composable
private fun RaAchievementRow(a: RetroAchievements.Achievement, players: Int, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val rarity = raRarity(a, players)
    FocusCard(modifier.fillMaxWidth().height(72.dp), glow = RaColor, shape = RoundedCornerShape(16.dp), onFocus = onFocus, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RaBadge(a, 50.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(a.title, style = MaterialTheme.typography.titleSmall, color = if (a.earned) Palette.Text else Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (a.description.isNotBlank()) Text(a.description, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(raEarnedLine(a), style = MaterialTheme.typography.labelSmall, color = if (a.earned) RaColor else Palette.Muted, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Pill(Tx.p(R.plurals.accounts_ra_list_pts, a.points), RaColor)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (a.type == RaList.MISSABLE) Pill(Tx.s(R.string.accounts_ra_list_missable), Palette.Peach)
                    if (rarity != null) Pill(rarity, if (RaList.share(a, players)!! <= 0.1f) Palette.Butter else Palette.Lavender)
                }
            }
        }
    }
}

/** The hints over the list: the achievement, then players' comments on it, fetched when it was opened. */
object RaHintsPopup {
    /** Comments shown in full: blurred until A (or a tap) asks for them. Per achievement, for the session. */
    private val revealed = mutableStateOf(setOf<Int>())
    /** The comments of the achievement showing, once fetched; null while asking. */
    var page by mutableStateOf<RaList.Page?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    private var forId = 0

    /** A on an achievement: show it, and fetch its comments now (the only time they are fetched). */
    fun open(game: Game, a: RetroAchievements.Achievement) {
        if (Privacy.isHidden(game)) return
        FocusMemory.forget(RaUi.HINTS)
        forId = a.id
        page = RetroAchievements.hints.cached(a.id)
        failed = false
        RaUi.hintFor = a
        Nav.focusTick++
        if (page == null) kotlinx.coroutines.MainScope().launch {
            val p = RetroAchievements.hints.load(a.id)
            if (forId != a.id) return@launch
            page = p
            failed = p == null
        }
    }

    /** X, or More: the next page of comments. */
    fun more(a: RetroAchievements.Achievement) {
        if (page?.more != true) return
        kotlinx.coroutines.MainScope().launch {
            val p = RetroAchievements.hints.more(a.id)
            if (forId == a.id && p != null) page = p
        }
    }

    fun isRevealed(id: Int) = id in revealed.value
    fun reveal(id: Int) { revealed.value = revealed.value + id }

    @Composable
    fun ColumnScope.Body(game: Game, a: RetroAchievements.Achievement) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RaBadge(a, 64.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(a.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (a.description.isNotBlank()) Text(a.description, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(raEarnedLine(a), style = MaterialTheme.typography.labelMedium, color = if (a.earned) RaColor else Palette.Muted, maxLines = 1)
            }
            Pill(Tx.p(R.plurals.accounts_ra_list_pts, a.points), RaColor)
        }
        Spacer(Modifier.height(10.dp))
        SectionTitle(Tx.s(R.string.accounts_ra_hints_title), RaColor)
        Spacer(Modifier.height(6.dp))
        val p = page
        val open = isRevealed(a.id)
        val keys = p?.comments?.indices?.map { "h:$it" }.orEmpty() + listOfNotNull("more".takeIf { p?.more == true })
        val target = rememberFocusTarget(RaUi.HINTS, keys.ifEmpty { listOf("none") })
        val state = rememberLazyListState()
        KeepInView(state, keys.indexOf(target))
        when {
            p == null && failed -> PipNote(Tx.s(R.string.accounts_ra_hints_failed), Tx.s(if (NetGate.closed) R.string.accounts_ra_hints_private else R.string.accounts_ra_hints_failed_text), mood = 2)
            p == null -> PipNote(Tx.s(R.string.accounts_ra_hints_asking), Tx.s(R.string.accounts_ra_hints_asking_text))
            p.comments.isEmpty() -> PipNote(Tx.s(R.string.accounts_ra_hints_none), Tx.s(R.string.accounts_ra_hints_none_text), mood = 1)
            else -> {
                if (!open) Text(Tx.s(R.string.accounts_ra_hints_spoilers), style = MaterialTheme.typography.labelLarge, color = Palette.Peach, maxLines = 2)
                LazyColumn(
                    Modifier.weight(1f, fill = false).heightIn(max = 520.dp).fadingEdges(state),
                    state = state,
                    contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(p.comments, key = { i, _ -> "h:$i" }) { i, c ->
                        FocusCard(
                            Modifier.fillMaxWidth().takesFocus(RaUi.HINTS, "h:$i", target == "h:$i"), glow = RaColor, shape = RoundedCornerShape(14.dp),
                            lifts = false, onFocus = { FocusMemory.remember(RaUi.HINTS, "h:$i") }, onClick = { reveal(a.id) },
                        ) { RaComment(c, open) }
                    }
                    if (p.more) item("more") {
                        FocusCard(
                            Modifier.fillMaxWidth().height(48.dp).takesFocus(RaUi.HINTS, "more", target == "more"), glow = RaColor, shape = RoundedCornerShape(14.dp),
                            onFocus = { FocusMemory.remember(RaUi.HINTS, "more") }, onClick = { more(a) },
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(Tx.s(R.string.accounts_ra_hints_more, p.comments.size, p.total), style = MaterialTheme.typography.titleSmall, color = RaColor, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (p != null && p.comments.isNotEmpty() && !open) ButtonHint("A", Tx.s(R.string.accounts_ra_hints_show), Palette.Mint)
            if (p?.more == true) ButtonHint("X", Tx.s(R.string.accounts_ra_hints_more_short), RaColor)
            ButtonHint("B", Tx.s(R.string.common_back), Palette.Lavender)
        }
    }
}

