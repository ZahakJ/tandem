package io.github.zahakj.tandem.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx

/*
 * Settings search (X anywhere in Settings): a controller keyboard on the left, results on the right as you type.
 * It searches every row of every page and subpage by label, by synonyms the row lists ("nintendo" finds Swap A and B),
 * by its group and page and, last, by its help text; plus the pages, themes, home layouts, and the few settings that
 * live on a screen of their own (the scrape options, the art sources). A result opens its page (a subpage over its
 * page, so Back goes there) and the row pulses so the eye finds it.
 */

object SettingsSearch {
    var open by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    internal var inResults by mutableStateOf(false)

    fun show() {
        FocusMemory.forget(KEYS_ROUTE)
        FocusMemory.forget(RESULTS_ROUTE)
        inResults = false
        open = true
    }

    fun close() { open = false; Nav.focusTick++ }
}

private const val KEYS_ROUTE = "settings-search"
private const val RESULTS_ROUTE = "settings-search:results"
private val SEARCH_KEYS = ("ABCDEFG" + "HIJKLMN" + "OPQRSTU" + "VWXYZ12" + "3456789" + "0").map { it.toString() } + listOf("Space", "⌫", "Clear")

/** What a key of [SEARCH_KEYS] shows: its letter, or Space and Clear in the language (the keys stay ids). */
private fun keyLabel(k: String) = when (k) {
    "Space" -> Tx.s(R.string.settings_search_space)
    "Clear" -> Tx.s(R.string.common_clear)
    else -> k
}

/** One thing search can find, with where it lives and how to get there. */
internal class Hit(
    val key: String,
    val title: String,
    val where: String,
    val value: String,
    val color: Color,
    val icon: Glyph?,
    val help: String?,
    /** Label words weigh most, then synonyms and place, then the help text. */
    val strong: String,
    val words: String,
    val weak: String,
    val go: () -> Unit,
)

/** Things worth suggesting before anything is typed: what people look for most. */
private val Suggested = listOf(
    "look", "home", "Colour/Accent colour", "layout:grid", "Buttons/Swap A and B", "Screens/Swap screens",
    "Music/Music", "Artwork & accounts/Scrape library", "Tandem/Use Tandem as the Home screen",
)

@Composable
fun SettingsSearchOverlay() {
    val open = SettingsSearch.open
    InterceptButtons(open && TextEntry.request == null) { b ->
        when (b) {
            Button.X -> SettingsSearch.query = SettingsSearch.query.dropLast(1)
            else -> Unit
        }
        true
    }
    OverlayPanel(open, onClose = { SettingsSearch.close() }) {
        if (!open) return@OverlayPanel
        SearchBody()
    }
}

@Composable
private fun SearchBody() {
    val hits = settingsHits()
    val query = SettingsSearch.query
    val q = normalize(query)
    // Hits hold text in the language: a change of language makes them again.
    val results = remember(q, hits.size, Lang.revision.intValue) {
        if (q.isEmpty()) Suggested.mapNotNull { s -> hits.firstOrNull { it.key == s || it.key == "cat:$s" } }
        else matchHits(hits, q, 40)
    }
    SearchPanes(query, q, results)
}

/** Everything settings search can find, with live values. */
@Composable
internal fun settingsHits(): List<Hit> {
    val cats = settingsCatalog()
    // A subpage holding a row of its own name (Updates) is found once: by that row, which opens the subpage.
    val twins = cats.filter { c -> c.parent != null && c.sections.any { s -> s.rows.any { it.en == c.en } } }.map { it.parent to it.en }.toSet()
    return buildList {
        for (c in cats) {
            // A subpage is found by its row on its page; the seven pages by their tiles.
            if (c.parent == null) add(
                Hit(
                    "cat:${c.id}", c.title, Tx.s(R.string.settings_search_where_settings), c.summary, c.color, c.icon, c.blurb,
                    c.title, "${c.blurb} ${englishWords(c.en, c.title)}", "",
                ) { jump(Route.Settings, "cat:${c.id}") },
            )
            // Where a row lives: its page (a subpage after its page), and its group when that says more.
            val page = c.parent?.let { Tx.s(R.string.settings_search_where_in, SettingsCategories.title(it), c.title) } ?: c.title
            for (s in c.sections) {
                for (r in s.rows) {
                    if (r.opens && (c.id to r.en) in twins) continue
                    val key = rowKey(s.en, r.en)
                    add(
                        Hit(
                            key, r.label, if (s.en == c.en) page else Tx.s(R.string.settings_search_where_in, page, s.title), r.shownValue, r.color, r.icon, r.help,
                            // The English label, group and page ([SettingRow.en]) count as synonyms, so English words find
                            // a setting in every language (the search keyboard has Latin letters only).
                            r.label,
                            "${r.keywords} ${s.title} ${c.title} ${englishWords(r.en, r.label)} ${englishWords(s.en, s.title)} ${englishWords(c.en, c.title)}",
                            r.help.orEmpty(),
                        ) {
                            jump(c.route, key)
                            SettingsUi.flash(key)
                        },
                    )
                }
            }
        }
        addAll(elsewhereHits())
        for (t in BuiltInThemes.list) {
            // A built-in theme's English name finds it too.
            val en = if (t.nameRes != 0) englishWords(Tx.en(t.nameRes), t.name) else ""
            add(
                Hit(
                    "theme:${t.id}", t.name, Tx.s(R.string.settings_search_where_themes), Tx.s(R.string.settings_search_theme), t.accent, Glyph.Theme,
                    t.tagline, t.name, "theme colours $en", t.tagline,
                ) { jump(Route.Appearance, "theme:${t.id}") },
            )
        }
        for (p in ThemePacks.packs) {
            add(Hit("theme:${p.id}", p.name, Tx.s(R.string.settings_search_where_packs), p.format.label, p.spec.accent, Glyph.Theme, null, p.name, "theme pack cocoon", "") {
                jump(Route.Appearance, "theme:${p.id}")
            })
        }
        for ((id, name) in HomeLayouts) {
            add(Hit("layout:$id", name, Tx.s(R.string.settings_search_where_layouts), Tx.s(R.string.settings_search_layout), Palette.Butter, Glyph.Home, null, name, "home layout $id", "") {
                jump(SettingsCategories.route("home"), "layout:$id")
            })
        }
    }
}

/** A row's or group's English name ([SettingRow.en]) as extra search words, when it isn't what shows already ([shown]). */
private fun englishWords(en: String, shown: String) = if (en == shown) "" else en

/**
 * Hits for the normalized query [q], best first: every word typed must match. A word at the start of the label
 * beats one inside it, which beats a synonym, the group or the page, which beats the help text.
 */
internal fun matchHits(hits: List<Hit>, q: String, limit: Int): List<Hit> {
    val tokens = q.split(" ").filter { it.isNotEmpty() }
    return hits.mapNotNull { h ->
        val strong = normalize(h.strong)
        val words = normalize(h.words)
        val weak = normalize(h.weak)
        var score = 0
        for (t in tokens) {
            score += when {
                strong.startsWith(t) -> 0
                strong.split(" ").any { it.startsWith(t) } -> 1
                strong.contains(t) -> 2
                words.split(" ").any { it.startsWith(t) } -> 3
                weak.split(" ").any { it.startsWith(t) } -> 6
                else -> return@mapNotNull null
            }
        }
        h to score
    }.sortedBy { it.second }.map { it.first }.distinctBy { it.key + it.where }.take(limit)
}

@Composable
private fun SearchPanes(query: String, q: String, results: List<Hit>) {
    LaunchedEffect(results.isEmpty()) { if (results.isEmpty()) SettingsSearch.inResults = false }
    androidx.activity.compose.BackHandler(enabled = SettingsSearch.inResults) { SettingsSearch.inResults = false; Nav.focusTick++ }

    val keyTarget = rememberFocusTarget(KEYS_ROUTE, SEARCH_KEYS.map { "key:$it" })
    val resultKeys = results.map { it.key + "|" + it.where }
    val resultTarget = rememberFocusTarget(RESULTS_ROUTE, resultKeys)
    Row(Modifier.fillMaxSize().background(Palette.Card, RoundedCornerShape(26.dp)).padding(14.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight().onFocusChanged { if (it.hasFocus) SettingsSearch.inResults = false }) {
            Box(Modifier.padding(horizontal = 8.dp).fillMaxWidth().background(Palette.NightDeep, RoundedCornerShape(14.dp)).padding(12.dp)) {
                if (query.isEmpty()) Text(Tx.s(R.string.settings_search_box), style = MaterialTheme.typography.titleLarge, color = Palette.Muted, maxLines = 1)
                else TailText("⌕ $query", Palette.Text)
            }
            // A–Z in their usual order in every language.
            LeftToRight { LazyVerticalGrid(
                GridCells.Fixed(7), Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false,
            ) {
                items(SEARCH_KEYS.size, key = { SEARCH_KEYS[it] }, span = { i -> GridItemSpan(if (SEARCH_KEYS[i].length > 1 || SEARCH_KEYS[i] == "⌫") 2 else 1) }) { i ->
                    val k = SEARCH_KEYS[i]
                    FocusCard(
                        Modifier.height(32.dp).takesFocus(KEYS_ROUTE, "key:$k", !SettingsSearch.inResults && keyTarget == "key:$k"),
                        glow = Palette.Sky, shape = RoundedCornerShape(10.dp),
                        onClick = {
                            SettingsSearch.query = when (k) {
                                "⌫" -> query.dropLast(1)
                                "Clear" -> ""
                                "Space" -> if (query.isBlank() || query.endsWith(" ")) query else "$query "
                                else -> query + k.lowercase()
                            }
                        },
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(keyLabel(k), style = MaterialTheme.typography.titleSmall, maxLines = 1) }
                    }
                }
            } }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ButtonHint("X", Tx.s(R.string.settings_search_delete), Palette.Peach)
                if (results.isNotEmpty()) ButtonHint("◀▶", Tx.s(R.string.settings_search_results), Palette.Sky)
                ButtonHint("B", Tx.s(R.string.common_close), Palette.Lavender)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                when {
                    q.isEmpty() -> Tx.s(R.string.settings_search_popular)
                    results.size >= 40 -> Tx.p(R.plurals.settings_search_found_more, results.size)
                    else -> Tx.p(R.plurals.settings_search_found, results.size)
                },
                Modifier.padding(start = FocusRoomSide, top = 4.dp), style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1,
            )
            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Pip(64.dp, mood = 2)
                        Spacer(Modifier.height(8.dp))
                        Text(Tx.s(R.string.settings_search_nothing, query.trim()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(Tx.s(R.string.settings_search_try), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
                    }
                }
            } else {
                val list = androidx.compose.foundation.lazy.rememberLazyListState()
                if (SettingsSearch.inResults) KeepInView(list, resultKeys.indexOf(resultTarget))
                LazyColumn(
                    Modifier.weight(1f).fadingEdges(list).onFocusChanged { if (it.hasFocus) SettingsSearch.inResults = true },
                    state = list,
                    contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.key + "|" + it.where }) { h ->
                        val k = h.key + "|" + h.where
                        ResultRow(h, Modifier.takesFocus(RESULTS_ROUTE, k, SettingsSearch.inResults && resultTarget == k)) {
                            SettingsSearch.close()
                            h.go()
                        }
                    }
                }
                Row(Modifier.padding(horizontal = FocusRoomSide, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ButtonHint("A", Tx.s(R.string.settings_search_go), Palette.Mint)
                    ButtonHint("B", Tx.s(R.string.settings_search_keys), Palette.Lavender)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(h: Hit, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(
        modifier.fillMaxWidth(), glow = h.color, shape = RoundedCornerShape(16.dp),
        onFocus = { Selection.current = Selected.Setting(h.title, h.value, h.help, h.color, Tx.s(R.string.settings_search_go)) },
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (h.icon != null) { GlyphChip(h.icon, h.color); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(h.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(h.where, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (h.value.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                ValuePill(h.value, h.color, Modifier.weight(0.6f, fill = false))
            }
        }
    }
}

/**
 * Settings that live on a screen of their own, not in a Settings row: what a scrape fetches, the region and language
 * (the Scrape library screen), each art source (its card under Set up sources), and play stats. Search still finds
 * them and takes you there.
 */
private fun elsewhereHits(): List<Hit> {
    val cfg = io.github.zahakj.tandem.data.ScrapeConfig.settings
    val scrape = Tx.s(R.string.settings_search_where_scrape)
    fun open(then: () -> Unit) { jump(SettingsCategories.route("library"), rowKey("Artwork & accounts", "Scrape library")); then() }
    // Each found by its name in the language and in English.
    fun named(@StringRes id: Int) = Tx.s(id).let { it to englishWords(Tx.en(id), it) }
    val (kinds, kindsEn) = named(R.string.settings_search_kinds)
    val (region, regionEn) = named(R.string.settings_search_region)
    val (language, languageEn) = named(R.string.settings_search_language)
    val (stats, statsEn) = named(R.string.settings_search_stats)
    return listOf(
        Hit(
            "scrape:kinds", kinds, scrape, Tx.p(R.plurals.settings_search_kinds_value, cfg.kinds.size), Palette.Butter, Glyph.Image,
            Tx.s(R.string.settings_search_kinds_help),
            kinds, "media kinds box logo hero screenshot video scrape $kindsEn", "",
        ) { open { ScrapeUi.open(now = true) } },
        Hit(
            "scrape:region", region, scrape, cfg.regionPref.label, Palette.Butter, Glyph.Sort,
            Tx.s(R.string.settings_search_region_help),
            region, "usa europe japan country scrape $regionEn", "",
        ) { open { ScrapeUi.open(now = true) } },
        Hit(
            "scrape:language", language, scrape, io.github.zahakj.tandem.data.LANGUAGES[cfg.languagePref] ?: cfg.languagePref, Palette.Butter, Glyph.Text,
            Tx.s(R.string.settings_search_language_help),
            language, "translation descriptions scrape $languageEn", "",
        ) { open { ScrapeUi.open(now = true) } },
        Hit(
            "stats", stats, Tx.s(R.string.settings_search_where_stats), "", Palette.Butter, Glyph.Chart,
            Tx.s(R.string.settings_search_stats_help),
            stats, "statistics time history sessions trophies $statsEn", "",
        ) { Nav.go(Route.Stats) },
    ) + cfg.sourceOrder.map { s ->
        Hit(
            "source:${s.name}", s.label, Tx.s(R.string.settings_search_where_sources), "", Palette.Butter, Glyph.Key, Tx.s(R.string.settings_search_source_help, s.gives),
            s.label, "scraper source account key token api order ${s.name.lowercase()}", "",
        ) { open { ArtSetup.open(s) } }
    }
}

/** Opens Settings at row [key] of page [id] (a subpage over its page), from anywhere: the way to one setting. */
fun openSetting(id: String, key: String) {
    jump(SettingsCategories.route(id), key)
    SettingsUi.flash(key)
}

/** Opens [route] (from wherever we are) with [key] highlighted; a subpage over its page, with its row highlighted. */
private fun jump(route: Route, key: String) {
    FocusMemory.remember(route.key, key)
    val id = SettingsCategories.idOf(route)
    val parent = id?.let(SettingsCategories::parentOf)?.let(SettingsCategories::route)
    if (id != null && parent != null) SettingsPlan.linkKey(id)?.let { FocusMemory.remember(parent.key, it) }
    // From a subpage, search starts from its page (Back from the result goes there, not to this subpage).
    if (Nav.route != route && SettingsCategories.idOf(Nav.route)?.let(SettingsCategories::parentOf) != null) Nav.back()
    val here = Nav.route
    when {
        here == route -> Nav.focusTick++
        route == Route.Settings -> Nav.toggleSettings()
        parent != null && here == parent -> Nav.goNow(route)
        parent != null && SettingsCategories.idOf(here) != null -> { Nav.replace(parent); Nav.goNow(route) }
        parent != null -> { Nav.goNow(parent); Nav.goNow(route) }
        here == Route.Settings -> Nav.go(route)
        SettingsCategories.idOf(here) != null -> Nav.replace(route)
        else -> Nav.go(route)
    }
}

/** The Show hidden games row on Play & system › Privacy & hidden, where an empty home sends you when every game is hidden. */
const val ShowHiddenKey = "Privacy & hidden/Show hidden games"
