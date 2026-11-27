package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx

/*
 * The first Settings screen: eight boxy tiles, three to a row, the seven pages and then Search, two columns wide (X
 * works anywhere in Settings too). The tiles are one set in the theme's accent (SettingsTiles.kt), each with a tiny live
 * sketch of what is set inside. The side panel, as tall as the grid, shows what's inside the highlighted page with
 * the values of its main settings, so a glance often answers the question without opening anything (the bottom
 * screen shows the page's summary too; with one screen the panel is the only place).
 */

private const val SEARCH_KEY = "search"

@Composable
fun SettingsScreen() {
    // The seven pages; their subpages open from rows on them.
    val cats = settingsCatalog().filter { it.parent == null }
    val routeKey = Route.Settings.key
    val keys = cats.map { "cat:${it.id}" } + SEARCH_KEY
    // The first visit opens on the first page's tile.
    remember { if (FocusMemory.get(routeKey) == null) FocusMemory.remember(routeKey, "cat:look") }
    val target = rememberFocusTarget(routeKey, keys)
    var focused by remember { mutableStateOf(FocusMemory.get(routeKey) ?: "cat:look") }
    // The tiles rise in, one after another, each time Settings is opened (not when coming back from a page).
    val layer = LocalLayer.current
    val active = layer !is KeptLayer || !layer.dormant
    val opens = remember { intArrayOf(0) }
    val openKey = remember(active) { if (active && Nav.dir >= 0) ++opens[0] else opens[0] }
    val accent = Palette.Accent
    fun onFocus(key: String, title: String, value: String, detail: String, action: String) {
        FocusMemory.remember(routeKey, key)
        focused = key
        Selection.current = Selected.Setting(title, value, detail, accent, action)
    }
    // Eight tiles: the pages in order, Search last. Three to a row, so three rows of boxy tiles fill the grid's height
    // (four by two would be tall pillars); Search, alone with Play & system on the last row, spans two columns.
    val tiles = cats.map { it.id } + SEARCH_KEY
    val rows = tiles.chunked(3)
    val shape = RoundedCornerShape(Themes.live.corner.coerceIn(8f, 26f).dp + 4.dp)
    val gap = 14.dp
    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 22.dp)) {
        // The grid and the panel share their top and bottom edges. Every tile is one column wide and one row tall
        // (Search two columns and the gap between), so the edges line up down and across with equal gutters.
        BoxWithConstraints(
            Modifier.weight(1.5f).fillMaxHeight().padding(start = FocusRoomSide, end = FocusRoomSide, top = FocusRoom, bottom = 22.dp),
        ) {
            val col = (maxWidth - gap * 2) / 3
            val rowH = (maxHeight - gap * (rows.size - 1)) / rows.size
            // Every name in one size, the size the set fits in.
            val nameStyle = tileNameStyle(cats.map { it.title } + Tx.s(R.string.settings_hint_search), col - TileNameInset)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEachIndexed { r, row ->
                    Row(Modifier.focusRow(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEachIndexed { i, id ->
                            val index = r * 3 + i
                            val span = if (id == SEARCH_KEY) 4 - row.size else 1
                            val tileMod = Modifier.size(col * span + gap * (span - 1), rowH).riseIn(openKey, delayMs = 30L + index * 45L, distance = 26.dp, fromScale = 0.92f)
                            if (id == SEARCH_KEY) {
                                SettingsHomeTile(
                                    Tx.s(R.string.settings_hint_search), nameStyle, Glyph.Search, tileMod.takesFocus(routeKey, SEARCH_KEY, target == SEARCH_KEY), shape,
                                    onFocus = { onFocus(SEARCH_KEY, Tx.s(R.string.settings_home_search), "", Tx.s(R.string.settings_home_search_help), Tx.s(R.string.settings_hint_search)) },
                                    onClick = { SettingsSearch.show() },
                                ) { SettingsTileHint(SEARCH_KEY) }
                            } else {
                                val c = cats.first { it.id == id }
                                val key = "cat:$id"
                                SettingsHomeTile(
                                    c.title, nameStyle, c.icon, tileMod.takesFocus(routeKey, key, key == target), shape,
                                    onFocus = { onFocus(key, c.title, c.summary, c.blurb, Tx.s(R.string.settings_a_open)) },
                                    onClick = { Nav.go(c.route) },
                                ) { SettingsTileHint(id) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(0.75f).fillMaxHeight().padding(top = FocusRoom, bottom = 22.dp)
                .clip(RoundedCornerShape(24.dp)).background(Palette.Card.copy(alpha = 0.72f)).padding(18.dp),
        ) {
            AnimatedContent(focused, transitionSpec = { fadeIn(tween(160, delayMillis = 70)) togetherWith fadeOut(tween(70)) }, label = "settings-side") { key ->
                when {
                    key.startsWith("cat:") -> cats.firstOrNull { "cat:${it.id}" == key }?.let { CategoryPanel(it, tint = accent) }
                    else -> SearchPanel()
                }
            }
        }
    }
}

/** A page of Settings: Look & feel, Home & layout... (see SettingsCatalog.kt). */
@Composable
fun SettingsPage(id: String) {
    val cat = settingsCatalog().firstOrNull { it.id == id } ?: return
    val routeKey = cat.route.key
    val header = when (id) {
        "look" -> lookHeader(routeKey)
        "home" -> layoutHeader(routeKey)
        else -> null
    }
    // While nothing on the page has published a row yet (the first frames, a gallery without a row), the details
    // screen explains the page itself instead of falling back to the idle greeting.
    val summary = Selected.Setting(cat.title, cat.summary, cat.blurb, cat.color, Tx.s(R.string.common_choose))
    SettingsIdle.Publish(summary, cat.route)
    SettingsLayout(
        routeKey, cat.sections,
        sidePanelFallback = { CategoryPanel(cat, inside = true) },
        header = header?.content, headerKeys = header?.keys ?: emptyList(), headerRow = header?.row ?: { null },
    )
}

/**
 * What's inside a page: its blurb, its main settings with their values, and its groups. [tint] colours it all in one
 * colour (the Settings home's accent) instead of the page's and its rows' own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPanel(c: SettingsCategory, inside: Boolean = false, tint: Color? = null) {
    Column(Modifier.fillMaxSize()) {
        val own = tint?.let { Palette.readable(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphChip(c.icon, tint ?: c.color, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ShrinkText(c.title, MaterialTheme.typography.titleLarge, maxLines = 1)
                if (c.summary.isNotBlank()) PartsText(c.summary, MaterialTheme.typography.titleSmall, color = own ?: c.color)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(c.blurb, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            c.highlights.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShrinkText(r.label, MaterialTheme.typography.labelLarge, Modifier.weight(1f), maxLines = 2)
                    Spacer(Modifier.width(8.dp))
                    // A value can be a long name (a theme's): two lines, shrinking a little, before any "…".
                    ShrinkText(r.shownValue, MaterialTheme.typography.labelLarge, Modifier.weight(0.8f, fill = false), maxLines = 2, color = own ?: r.color, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }
        if (!inside) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ButtonHint("A", Tx.s(R.string.settings_a_open), tint ?: c.color)
                ButtonHint("X", Tx.s(R.string.settings_hint_search), tint ?: Palette.Sky)
                ButtonHint("B", Tx.s(R.string.common_back), Palette.Muted)
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ButtonHint("L1", "")
                ButtonHint("R1", Tx.s(R.string.settings_home_pages), Palette.Lavender)
                ButtonHint("X", Tx.s(R.string.settings_hint_search), Palette.Sky)
                // A subpage goes back to its page.
                if (c.parent != null) ButtonHint("B", SettingsCategories.title(c.parent), Palette.Muted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPanel() {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphChip(Glyph.Search, Palette.Accent, 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(Tx.s(R.string.settings_home_search), style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            Tx.s(R.string.settings_home_search_panel),
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, overflow = TextOverflow.Ellipsis,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ButtonHint("A", Tx.s(R.string.settings_hint_search), Palette.Accent)
            ButtonHint("B", Tx.s(R.string.common_back), Palette.Muted)
        }
    }
}
