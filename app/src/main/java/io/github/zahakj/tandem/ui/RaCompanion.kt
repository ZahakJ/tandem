package io.github.zahakj.tandem.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.CompanionInfo
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.NetGate
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.RaList
import io.github.zahakj.tandem.RetroAchievements
import io.github.zahakj.tandem.Tx
import kotlinx.coroutines.launch

/*
 * The in-game achievements page on the bottom screen (CompanionScreen.kt), for a game with a RetroAchievements set:
 * the achievements still to get first, with their descriptions; a tap on one opens it, and that tap (and only that
 * one) fetches players' comments on it from RetroAchievements, blurred until a second tap. The controller stays with
 * the game; this page is touch only.
 *
 * Network: the set is shown from what was kept on disk; only if nothing was kept is it asked for, once, when the page
 * opens ("Look up again" asks again). Comments only on a tap. Never polled while playing, never for hidden games,
 * never in private mode, never while signed out.
 */

object CompanionAchievements {
    var open by mutableStateOf(false)
        private set
    private var forKey: String? = null

    fun show(key: String) { forKey = key; open = true }
    fun hide() { open = false }

    /** The companion now shows [key]'s game: the page stays open only for the game it was opened for. */
    fun follow(key: String) { if (forKey != key) hide() }

    /** Whether the page is offered for [info]'s game: signed in, a system with achievements, not hidden, not known to have none. */
    fun offered(info: CompanionInfo): Boolean {
        if (!RetroAchievements.enabled || RetroAchievements.consoleId(info.systemId) == null) return false
        val game = Libraries.byKey(info.key) ?: return false
        return !Privacy.isHidden(game) && RetroAchievements.knownMatch(game) != 0
    }
}

/** The chip on the companion's faces that opens the page. */
@Composable
internal fun AchievementsChip(info: CompanionInfo, chip: @Composable (String, Color, () -> Unit) -> Unit) {
    val offered = remember(info.key, RetroAchievements.enabled) { CompanionAchievements.offered(info) }
    if (offered) chip(Tx.s(R.string.screens_comp_achievements), RaColor) { CompanionAchievements.show(info.key) }
}

@Composable
fun CompanionAchievementsPanel(info: CompanionInfo) {
    val game = remember(info.key) { Libraries.byKey(info.key)?.takeIf { !Privacy.isHidden(it) } }
    var progress by remember(info.key) { mutableStateOf<RetroAchievements.Progress?>(null) }
    var done by remember(info.key) { mutableStateOf(false) }
    var again by remember(info.key) { mutableIntStateOf(0) }
    LaunchedEffect(info.key, again) {
        val g = game ?: run { done = true; return@LaunchedEffect }
        if (again == 0) progress = RetroAchievements.stored(g)
        // Asked only when nothing was kept, or when "Look up again" was tapped.
        if (progress == null || again > 0) RetroAchievements.progress(g, maxAgeMs = if (again > 0) 0L else 10 * 60 * 1000L)?.let { progress = it }
        done = true
    }
    var filter by remember(info.key) { mutableStateOf(RaList.Filter.Locked) }
    var opened by remember(info.key) { mutableStateOf<Int?>(null) }
    val p = progress
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TapChip(Tx.s(R.string.apps_manual_back_game), Palette.Lavender) { CompanionAchievements.hide() }
            Text(p?.title?.ifBlank { null } ?: info.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (game != null) TapChip(Tx.s(R.string.accounts_steam_look_again), RaColor) { again++ }
        }
        if (p != null && p.total > 0) {
            RaProgressHeader(p)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in listOf(RaList.Filter.Locked, RaList.Filter.Missable, RaList.Filter.All)) {
                    val on = f == filter
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(if (on) RaColor.copy(alpha = 0.28f) else Palette.NightDeep)
                            .clickable { filter = f; opened = null }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(raFilterLabel(f), style = MaterialTheme.typography.labelLarge, color = if (on) RaColor else Palette.Muted, maxLines = 1) }
                }
            }
        }
        val list = remember(p, filter) { RaList.shown(p?.achievements.orEmpty(), filter, RaList.Sort.Default, p?.players ?: 0) }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                game == null -> PipNote(Tx.s(R.string.accounts_steam_nothing), Tx.s(R.string.screens_comp_ach_no_game), mood = 2)
                p == null && !done -> PipNote(Tx.s(R.string.accounts_steam_fetching), Tx.s(R.string.accounts_achievements_loading_text))
                p == null -> PipNote(Tx.s(R.string.accounts_achievements_failed), Tx.s(if (NetGate.closed) R.string.accounts_ra_list_private else R.string.accounts_ra_list_failed_text), mood = 2)
                p.achievements.isEmpty() -> PipNote(Tx.s(R.string.accounts_steam_nothing), Tx.s(R.string.accounts_ra_list_none_text), mood = 2)
                list.isEmpty() -> PipNote(Tx.s(R.string.accounts_ra_list_empty), Tx.s(R.string.screens_comp_ach_empty), mood = 1)
                else -> {
                    val state = rememberLazyListState()
                    LazyColumn(
                        Modifier.fillMaxSize().fadingEdges(state), state = state,
                        contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(list, key = { "${it.id}:${it.order}" }) { a ->
                            CompanionAchievementCard(a, p.players, opened == a.id) { opened = if (opened == a.id) null else a.id }
                        }
                    }
                }
            }
        }
        if (p != null && list.isNotEmpty() && opened == null) {
            Text(Tx.s(R.string.screens_comp_ach_tap), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One achievement; opened, its whole description and players' hints, fetched by the tap that opened it. */
@Composable
private fun CompanionAchievementCard(a: RetroAchievements.Achievement, players: Int, open: Boolean, onTap: () -> Unit) {
    val scope = rememberCoroutineScope()
    var page by remember(a.id) { mutableStateOf(RetroAchievements.hints.cached(a.id)) }
    var asking by remember(a.id) { mutableStateOf(false) }
    var failed by remember(a.id) { mutableStateOf(false) }
    var shown by remember(a.id) { mutableStateOf(RaHintsPopup.isRevealed(a.id)) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Card.copy(alpha = 0.85f)).animateContentSize()
            .clickable {
                onTap()
                // The tap that opens it is the only thing that fetches its hints.
                if (!open && page == null && !asking) {
                    asking = true
                    failed = false
                    scope.launch {
                        page = RetroAchievements.hints.load(a.id)
                        failed = page == null
                        asking = false
                    }
                }
            }
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RaBadge(a, 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(a.title, style = MaterialTheme.typography.titleSmall, color = if (a.earned) Palette.Text else Palette.Text.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.description, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = if (open) 6 else 2, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Pill(Tx.p(R.plurals.accounts_ra_list_pts, a.points), RaColor)
                if (a.type == RaList.MISSABLE) Pill(Tx.s(R.string.accounts_ra_list_missable), Palette.Peach)
                else raRarity(a, players)?.let { Pill(it, Palette.Lavender) }
            }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            val pg = page
            when {
                asking || (pg == null && !failed) -> Text(Tx.s(R.string.accounts_ra_hints_asking), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                pg == null -> Text(Tx.s(if (NetGate.closed) R.string.accounts_ra_hints_private else R.string.accounts_ra_hints_failed_text), style = MaterialTheme.typography.labelLarge, color = Palette.Peach)
                pg.comments.isEmpty() -> Text(Tx.s(R.string.accounts_ra_hints_none_text), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
                else -> Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.NightDeep)
                        .clickable { shown = true; RaHintsPopup.reveal(a.id) },
                ) {
                    if (!shown) Text(
                        Tx.s(R.string.accounts_ra_hints_spoilers_tap), Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                        style = MaterialTheme.typography.labelLarge, color = Palette.Peach,
                    )
                    // A few at a time on this small screen; More fetches the next page (another tap).
                    for (c in pg.comments) RaComment(c, shown)
                    if (pg.more) Box(
                        Modifier.fillMaxWidth().height(44.dp).clickable { scope.launch { RetroAchievements.hints.more(a.id)?.let { page = it } } },
                        contentAlignment = Alignment.Center,
                    ) { Text(Tx.s(R.string.accounts_ra_hints_more, pg.comments.size, pg.total), style = MaterialTheme.typography.titleSmall, color = RaColor) }
                }
            }
        }
    }
}

@Composable
private fun TapChip(text: String, color: Color, onTap: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).clickable(onClick = onTap).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1) }
}

