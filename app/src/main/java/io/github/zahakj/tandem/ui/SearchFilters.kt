package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.GameStatus
import io.github.zahakj.tandem.data.SearchFilter
import io.github.zahakj.tandem.data.SearchFilter.Chip
import io.github.zahakj.tandem.data.SmartGame
import io.github.zahakj.tandem.data.SmartRule

/*
 * Search's filters: a row of chips above the results (System · Genre · Year · Rating · Status · Played · Favorites).
 * Up from the results (or Right from the keyboard when there are none) reaches the row; L1 / R1 move along it; A on a
 * chip opens a small sheet of its values (Favorites just turns on and off); "Clear filters" sets them all back.
 * They narrow the typed text's results, and with nothing typed they browse the whole library. Local only.
 */

internal object SearchFilters {
    const val ROUTE = "search:filters"
    const val CLEAR = "f:clear"

    fun key(c: Chip) = "f:${c.name}"

    /** The chips' focus keys, in order: Clear last, while anything is set. */
    fun keys(f: SearchFilter): List<String> = Chip.entries.map(::key) + listOfNotNull(CLEAR.takeIf { f.active })

    /** L1 / R1 on the row: the next chip along ([by] mirrored right to left, so L1 is always the one on the left). */
    fun move(by: Int) {
        val keys = keys(SearchState.filter)
        val at = keys.indexOf(FocusMemory.get(ROUTE)).coerceAtLeast(0)
        val next = (at + by * (if (rtlNow) -1 else 1)).coerceIn(0, keys.lastIndex)
        if (next == at) return
        FocusMemory.remember(ROUTE, keys[next])
        Nav.focusTick++
        Sounds.page()
    }

    /** A on a chip: Favorites turns on or off; the others open their sheet. */
    fun press(c: Chip) {
        if (c == Chip.Favorites) {
            SearchState.filter = SearchState.filter.copy(favorites = !SearchState.filter.favorites)
            Sounds.toggle(SearchState.filter.favorites)
        } else SearchState.sheet = c
    }

    fun clear() {
        SearchState.filter = SearchFilter()
        FocusMemory.remember(ROUTE, key(Chip.System))
        Nav.focusTick++
        Sounds.toggle(false)
    }

    fun name(c: Chip): String = Tx.s(
        when (c) {
            Chip.System -> R.string.menus_search_filter_system
            Chip.Genre -> R.string.menus_search_filter_genre
            Chip.Year -> R.string.menus_search_filter_year
            Chip.Rating -> R.string.menus_search_filter_rating
            Chip.Status -> R.string.menus_search_filter_status
            Chip.Played -> R.string.menus_search_filter_played
            Chip.Favorites -> R.string.grid_smart_favorites
        },
    )

    fun stars(rating: Float): String = (rating * 5 + 0.01f).toInt().let { Tx.p(R.plurals.menus_search_filter_stars, it) }

    fun years(from: Int?, to: Int?): String = "${from ?: ""}–${to ?: ""}"

    fun statusLabel(id: String): String = if (id == SmartRule.STATUS_NONE) Tx.s(R.string.screens_status_no_status) else GameStatus.of(id)?.label ?: id

    fun playedLabel(p: String): String = when (p) {
        SmartRule.PLAYED_YES -> Tx.s(R.string.grid_smart_played_yes)
        SmartRule.PLAYED_NO -> Tx.s(R.string.menus_search_filter_never)
        else -> Tx.s(R.string.menus_search_filter_any)
    }

    /** What a chip shows under its name: the value set, or Any. */
    fun value(f: SearchFilter, c: Chip, systemName: (String) -> String): String = when {
        !f.isSet(c) -> Tx.s(R.string.menus_search_filter_any)
        c == Chip.System -> systemName(f.system!!)
        c == Chip.Genre -> f.genre!!
        c == Chip.Year -> years(f.yearFrom, f.yearTo)
        c == Chip.Rating -> stars(f.rating!!)
        c == Chip.Status -> statusLabel(f.status!!)
        c == Chip.Played -> playedLabel(f.played)
        else -> Tx.s(R.string.menus_search_filter_only)
    }
}

/** The row of chips. [active]: the row holds (or may take) the focus. */
@Composable
internal fun SearchFilterRow(games: List<Game>, active: Boolean) {
    val f = SearchState.filter
    val names = remember(games) { games.associate { it.platform.id to it.platform.name } }
    val keys = SearchFilters.keys(f)
    val target = rememberFocusTarget(SearchFilters.ROUTE, keys)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = FocusRoomSide, vertical = 6.dp)
            .onFocusChanged { if (it.hasFocus) { SearchState.inFilters = true; SearchState.inResults = true } },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (c in Chip.entries) {
            val k = SearchFilters.key(c)
            val on = f.isSet(c)
            FilterChip(
                SearchFilters.name(c), SearchFilters.value(f, c) { names[it] ?: it }, on, if (on) Palette.Butter else Palette.Sky,
                Modifier.takesFocus(SearchFilters.ROUTE, k, active && target == k),
                onFocus = { FocusMemory.remember(SearchFilters.ROUTE, k) },
            ) { SearchFilters.press(c) }
        }
        if (f.active) FilterChip(
            Tx.s(R.string.menus_search_filter_clear), Tx.p(R.plurals.menus_search_filter_set, f.count), false, Palette.Peach,
            Modifier.takesFocus(SearchFilters.ROUTE, SearchFilters.CLEAR, active && target == SearchFilters.CLEAR),
            onFocus = { FocusMemory.remember(SearchFilters.ROUTE, SearchFilters.CLEAR) },
        ) { SearchFilters.clear() }
    }
}

@Composable
private fun FilterChip(name: String, value: String, on: Boolean, color: Color, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusCard(modifier.height(48.dp).widthIn(min = 92.dp, max = 220.dp), glow = color, shape = RoundedCornerShape(14.dp), lifts = false, onFocus = onFocus, onClick = onClick) {
        Column(Modifier.background(color.copy(alpha = if (on) 0.22f else 0.06f)).padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text(name, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleSmall, color = if (on) color else Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The sheet of one chip's values (A on the chip): Any first, then what the library has. A picks and closes. */
@Composable
internal fun SearchFilterSheet(views: List<Pair<Game, SmartGame>>) {
    val chip = SearchState.sheet
    val shown = rememberLatest(chip)
    val f = SearchState.filter
    val any = Tx.s(R.string.menus_search_filter_any)
    // Options as text keys ("" for Any), so one popup type serves every chip.
    val options: List<PickOption<String>> = remember(shown, views, f, io.github.zahakj.tandem.Lang.revision.intValue) {
        val c = shown ?: return@remember emptyList()
        val list = when (c) {
            Chip.System -> views.map { it.first.platform }.distinctBy { it.id }.sortedBy { it.name.lowercase() }.map { PickOption(it.id, it.name) }
            Chip.Genre -> views.flatMap { it.second.genres }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }.map { PickOption(it, it) }
            Chip.Year -> SearchFilter.decades(views.mapNotNull { it.second.year }).map { PickOption("$it", SearchFilters.years(it, it + 9)) }
            Chip.Rating -> SearchFilter.RATINGS.map { PickOption("$it", SearchFilters.stars(it)) }
            Chip.Status -> (GameStatus.entries.map { it.id } + SmartRule.STATUS_NONE).map { PickOption(it, SearchFilters.statusLabel(it)) }
            Chip.Played -> listOf(SmartRule.PLAYED_YES, SmartRule.PLAYED_NO).map { PickOption(it, SearchFilters.playedLabel(it)) }
            Chip.Favorites -> emptyList()
        }
        listOf(PickOption("", any)) + list
    }
    val selected = when (shown) {
        Chip.System -> f.system
        Chip.Genre -> f.genre
        Chip.Year -> f.yearFrom?.toString()
        Chip.Rating -> f.rating?.toString()
        Chip.Status -> f.status
        Chip.Played -> f.played.takeIf { it != SmartRule.PLAYED_ANY }
        else -> null
    } ?: ""
    ChoicePopup(
        chip != null, shown?.let(SearchFilters::name).orEmpty(), null, options, selected, Palette.Butter, "search:sheet",
        onClose = { SearchState.sheet = null; Nav.focusTick++ },
    ) { v ->
        val c = shown ?: return@ChoicePopup
        val now = SearchState.filter
        SearchState.filter = if (v.isEmpty()) now.cleared(c) else when (c) {
            Chip.System -> now.copy(system = v)
            Chip.Genre -> now.copy(genre = v)
            Chip.Year -> v.toInt().let { now.copy(yearFrom = it, yearTo = it + 9) }
            Chip.Rating -> now.copy(rating = v.toFloat())
            Chip.Status -> now.copy(status = v)
            Chip.Played -> now.copy(played = v)
            Chip.Favorites -> now
        }
    }
}
