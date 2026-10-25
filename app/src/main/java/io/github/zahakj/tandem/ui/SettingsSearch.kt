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
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection

/*
 * Settings search (X anywhere in Settings): a controller keyboard on the left, results on the right as you type.
 * It searches every row of every page (also the folded "More options" ones) by label, by synonyms the row lists
 * ("nintendo" finds Swap A and B), by its group and page and, last, by its help text; plus the pages, Quick looks,
 * themes and home layouts. A result opens its page, unfolds its group, and the row pulses so the eye finds it.
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
    "look", "home", "Colour/Accent colour", "Home screen/Home layout", "Buttons/Swap A and B", "Screens/Swap screens",
    "Music/Background music", "Artwork/Scrape library", "preset:cocoon", "Home screen/Use Tandem as the Home screen",
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
    val results = remember(q, hits.size) {
        if (q.isEmpty()) Suggested.mapNotNull { s -> hits.firstOrNull { it.key == s || it.key == "cat:$s" } }
        else matchHits(hits, q, 40)
    }
    SearchPanes(query, q, results)
}

/** Everything settings search can find, with live values. */
@Composable
internal fun settingsHits(): List<Hit> {
    val cats = settingsCatalog()
    return buildList {
        for (c in cats) {
            add(
                Hit(
                    "cat:${c.id}", c.title, "Settings", c.summary, c.color, c.icon, c.blurb,
                    c.title, c.blurb, "",
                ) { jump(Route.Settings, "cat:${c.id}") },
            )
            for (s in c.sections) {
                for (r in s.rows + s.more) {
                    val extra = r in s.more
                    val key = rowKey(s.title, r.label)
                    add(
                        Hit(
                            key, r.label, "${c.title} › ${s.title}" + if (extra) " › More" else "", r.shownValue, r.color, r.icon, r.help,
                            r.label, "${r.keywords} ${s.title} ${c.title}", r.help.orEmpty(),
                        ) {
                            if (extra) SettingsUi.setOpen(c.route.key, s.title, true)
                            jump(c.route, key)
                            SettingsUi.flash(key)
                        },
                    )
                }
            }
        }
        for (p in Presets.all) {
            add(Hit("preset:${p.id}", p.title, "Settings › Quick looks", "", p.color, p.icon, p.blurb, p.title, "preset quick look " + p.changes.joinToString(" "), p.blurb) {
                jump(Route.Settings, "preset:${p.id}")
            })
        }
        for (t in BuiltInThemes.list) {
            add(Hit("theme:${t.id}", t.name, "Look & feel › Themes", "Theme", t.accent, Glyph.Theme, t.tagline, t.name, "theme colours", t.tagline) {
                jump(Route.Appearance, "theme:${t.id}")
            })
        }
        for (p in ThemePacks.packs) {
            add(Hit("theme:${p.id}", p.name, "Look & feel › Theme packs", p.format.label, p.spec.accent, Glyph.Theme, null, p.name, "theme pack cocoon", "") {
                jump(Route.Appearance, "theme:${p.id}")
            })
        }
        for ((id, name) in HomeLayouts) {
            add(Hit("layout:$id", name, "Home & layout › Layouts", "Layout", Palette.Butter, Glyph.Home, null, name, "home layout $id", "") {
                jump(SettingsCategories.route("home"), "layout:$id")
            })
        }
    }
}

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
                if (query.isEmpty()) Text("⌕ Search settings", style = MaterialTheme.typography.titleLarge, color = Palette.Muted, maxLines = 1)
                else TailText("⌕ $query", Palette.Text)
            }
            LazyVerticalGrid(
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
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(k, style = MaterialTheme.typography.titleSmall, maxLines = 1) }
                    }
                }
            }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ButtonHint("X", "Delete", Palette.Peach)
                if (results.isNotEmpty()) ButtonHint("◀▶", "Results", Palette.Sky)
                ButtonHint("B", "Close", Palette.Lavender)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                if (q.isEmpty()) "Popular" else "${results.size}${if (results.size >= 40) "+" else ""} found",
                Modifier.padding(start = FocusRoomSide, top = 4.dp), style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1,
            )
            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Pip(64.dp, mood = 2)
                        Spacer(Modifier.height(8.dp))
                        Text("Nothing called \"${query.trim()}\"", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Try what it does: \"bigger\", \"quiet\", \"buttons\"", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, textAlign = TextAlign.Center)
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
                    ButtonHint("A", "Go there", Palette.Mint)
                    ButtonHint("B", "Keys", Palette.Lavender)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(h: Hit, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(
        modifier.fillMaxWidth(), glow = h.color, shape = RoundedCornerShape(16.dp),
        onFocus = { Selection.current = Selected.Setting(h.title, h.value, h.help, h.color, "Go there") },
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

/** Opens [route] (from wherever in Settings we are) with [key] highlighted. */
private fun jump(route: Route, key: String) {
    FocusMemory.remember(route.key, key)
    val here = Nav.route
    when {
        here == route -> Nav.focusTick++
        route == Route.Settings -> Nav.toggleSettings()
        here == Route.Settings -> Nav.go(route)
        SettingsCategories.idOf(here) != null -> Nav.replace(route)
        else -> Nav.go(route)
    }
}
