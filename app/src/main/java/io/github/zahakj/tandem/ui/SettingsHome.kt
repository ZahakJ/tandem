package io.github.zahakj.tandem.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.Store

/*
 * The first Settings screen: nine big tiles, one per page, each saying what is set right now; a search bar on top
 * (X works anywhere in Settings); and Quick looks underneath, whole setups in one press. The side panel shows
 * what's inside the highlighted page, with the values of its main settings, so a glance often answers the question
 * without opening anything.
 */

private const val SEARCH_KEY = "search"

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val cats = settingsCatalog()
    val routeKey = Route.Settings.key
    val presets = Presets.all
    val keys = listOf(SEARCH_KEY) + cats.map { "cat:${it.id}" } + presets.map { "preset:${it.id}" }
    // The first visit opens on the first page's tile, not the search bar.
    remember { if (FocusMemory.get(routeKey) == null) FocusMemory.remember(routeKey, "cat:look") }
    val target = rememberFocusTarget(routeKey, keys)
    var focused by remember { mutableStateOf(FocusMemory.get(routeKey) ?: "cat:look") }
    val state = rememberRouteListState(routeKey)
    val rows = cats.chunked(3)
    KeepInView(
        state,
        when {
            target == SEARCH_KEY -> 0
            target?.startsWith("cat:") == true -> 1 + cats.indexOfFirst { "cat:${it.id}" == target }.coerceAtLeast(0) / 3
            target?.startsWith("preset:") == true -> 2 + rows.size
            else -> -1
        },
    )
    fun onFocus(key: String, title: String, value: String, detail: String, color: androidx.compose.ui.graphics.Color, action: String) {
        FocusMemory.remember(routeKey, key)
        focused = key
        Selection.current = Selected.Setting(title, value, detail, color, action)
    }
    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 22.dp)) {
        LazyColumn(
            Modifier.weight(1.45f).fillMaxHeight().fadingEdges(state),
            state = state,
            contentPadding = PaddingValues(start = FocusRoomSide, end = FocusRoomSide, top = FocusRoom, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search") {
                FocusCard(
                    Modifier.fillMaxWidth().height(42.dp).takesFocus(routeKey, SEARCH_KEY, target == SEARCH_KEY),
                    glow = Palette.Sky, shape = RoundedCornerShape(50),
                    onFocus = { onFocus(SEARCH_KEY, "Search settings", "", "Find any setting by name, or by what it does: \"bigger icons\", \"nintendo buttons\", \"music\".", Palette.Sky, "Search") },
                    onClick = { SettingsSearch.show() },
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Search, Palette.Muted, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Search settings", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Palette.Muted, maxLines = 1)
                        ButtonHint("X", "")
                    }
                }
            }
            rows.forEachIndexed { r, row ->
                item(key = "cats$r") {
                    Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { c ->
                            val key = "cat:${c.id}"
                            CategoryTile(
                                c, Modifier.weight(1f).height(98.dp).takesFocus(routeKey, key, key == target),
                                onFocus = { onFocus(key, c.title, c.summary, c.blurb, c.color, "Open") },
                            ) { Nav.go(c.route) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item(key = "presets-title") { SettingsHeader("Quick looks", "One press · press again to undo") }
            item(key = "presets") {
                Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    presets.forEach { p ->
                        val key = "preset:${p.id}"
                        PresetCard(
                            p, Modifier.weight(1f).height(104.dp).takesFocus(routeKey, key, key == target),
                            onFocus = { onFocus(key, p.title, if (Presets.undoable == p.id) "Applied" else "", p.blurb, p.color, if (Presets.undoable == p.id) "Undo" else "Use") },
                        ) { Presets.press(ctx, p) }
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
                    key.startsWith("cat:") -> cats.firstOrNull { "cat:${it.id}" == key }?.let { CategoryPanel(it) }
                    key.startsWith("preset:") -> Presets.byId(key.removePrefix("preset:"))?.let { PresetPanel(it) }
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
    val summary = Selected.Setting(cat.title, cat.summary, cat.blurb, cat.color, "Choose")
    SettingsIdle.Publish(summary, cat.route)
    SettingsLayout(
        routeKey, cat.sections,
        sidePanelFallback = { CategoryPanel(cat, inside = true) },
        header = header?.content, headerKeys = header?.keys ?: emptyList(), headerRow = header?.row ?: { null },
    )
}

/** A page's tile: its icon, name and the current state in a few words. */
@Composable
private fun CategoryTile(c: SettingsCategory, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusCard(modifier, glow = c.color, shape = RoundedCornerShape(20.dp), onFocus = onFocus, onClick = onClick) {
        // The tile's own colour, deepened, behind the text (no dark band): see UI-RULES §5.
        Box(Modifier.fillMaxSize().background(lerp(c.color, Palette.Night, 0.82f)))
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            GlyphChip(c.icon, c.color, 32.dp)
            Spacer(Modifier.weight(1f))
            Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            PartsText(c.summary, MaterialTheme.typography.labelLarge, color = c.color)
        }
    }
}

/** A Quick look card: a live sketch of the layout it gives, its name, and whether it's in use or can be undone. */
@Composable
private fun PresetCard(p: LookPreset, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val look = Store.settings.look
    val inUse = p.matches(look, Store.settings)
    FocusCard(modifier, glow = p.color, shape = RoundedCornerShape(18.dp), onFocus = onFocus, onClick = onClick) {
        Column(Modifier.fillMaxSize()) {
            LayoutPreview(p.layout.ifEmpty { look.homeLayout }, Modifier.fillMaxWidth().weight(1f), rounded = false)
            Row(Modifier.fillMaxWidth().background(Palette.Card).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    Presets.undoable == p.id -> Text("↶", style = MaterialTheme.typography.labelLarge, color = p.color)
                    inUse -> Box(Modifier.size(8.dp).clip(CircleShape).background(p.color))
                }
            }
        }
    }
}

/** What's inside a page: its blurb, its main settings with their values, and its groups. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPanel(c: SettingsCategory, inside: Boolean = false) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphChip(c.icon, c.color, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                PartsText(c.summary, MaterialTheme.typography.titleSmall, color = c.color)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(c.blurb, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            c.highlights.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(r.shownValue, Modifier.weight(0.8f, fill = false), style = MaterialTheme.typography.labelLarge, color = r.color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (!inside) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ButtonHint("A", "Open", c.color)
                ButtonHint("X", "Search", Palette.Sky)
                ButtonHint("B", "Back", Palette.Muted)
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ButtonHint("L1", "")
                ButtonHint("R1", "Pages", Palette.Lavender)
                ButtonHint("X", "Search", Palette.Sky)
            }
        }
    }
}

/** A Quick look explained: a live sketch, what it changes, and A to use (or undo) it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetPanel(p: LookPreset) {
    val look = Store.settings.look
    val undo = Presets.undoable == p.id
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphChip(p.icon, p.color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when { undo -> "Applied · A undoes it"; p.matches(look, Store.settings) -> "Looks like this now"; else -> "Quick look" },
                    style = MaterialTheme.typography.titleSmall, color = p.color, maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FitInside(Modifier.fillMaxWidth().weight(1f)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LayoutPreview(p.layout.ifEmpty { look.homeLayout }, Modifier.fillMaxWidth().aspectRatio(1.9f))
                p.changes.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(p.color))
                        Spacer(Modifier.width(8.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ButtonHint("A", if (undo) "Undo" else "Use", p.color)
            ButtonHint("B", "Back", Palette.Muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPanel() {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphChip(Glyph.Search, Palette.Sky, 40.dp)
            Spacer(Modifier.width(12.dp))
            Text("Search settings", style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Type a few letters of a setting, or what you want: \"bigger icons\", \"nintendo\", \"quiet\", \"cocoon\". " +
                "A result takes you straight to it and shows where it lives. X searches from any Settings page.",
            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, overflow = TextOverflow.Ellipsis,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ButtonHint("A", "Search", Palette.Sky)
            ButtonHint("B", "Back", Palette.Muted)
        }
    }
}
