package io.github.zahakj.tandem.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.DeckGames
import io.github.zahakj.tandem.Emulators
import io.github.zahakj.tandem.GameCompanion
import io.github.zahakj.tandem.LaunchSpec
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.data.SystemPrefs
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * One system's page, opened from a tile on the Systems screen: the system's art over its hero picture with the Show
 * in library switch, then everything else as cards you can see at once: its emulators with their app icons (A makes
 * one the default, or gets one that isn't installed), the screen its games start on, what the bottom screen shows
 * while they play, and its folders. No row hides its choices behind ‹ ›. B goes back to the tile.
 */

private val Gap = 10.dp

@Composable
internal fun SystemPage(e: SystemEntry, maxWidth: Dp, maxHeight: Dp, onChanged: () -> Unit, onAddFolder: () -> Unit) {
    val ctx = LocalContext.current
    val p = e.platform
    val color = e.color
    val config = SystemPrefs.config(p.id)
    val route = "system:${p.id}"
    // Installed first (in platforms.json order otherwise); the default stays where it is and is marked, so making
    // another one the default never moves the card under the highlight.
    val players = p.players
    val default = if (p.isApps) null else players.firstOrNull()
    val emus = if (p.isApps) emptyList() else p.allPlayers.withIndex().sortedBy { if (it.value in players) 0 else 1 }
    val hasSecond = Facts.run { ensure(ctx); hasSecondScreen }
    val mode = GameCompanion.systemMode(p)
    val modes = GameCompanion.choices(DeckGames.suits(p.id, default?.packageName))
    // Which folders are there, looked up off the main thread (each is a file check).
    val folders = if (p.isApps) emptyList() else p.folders
    val there by produceState<Set<String>?>(null, folders) {
        value = withContext(Dispatchers.IO) { folders.filter { Library.folder(it).isDirectory }.toSet() }
    }
    val present = folders.filter { it in config.folders || there?.contains(it) == true }
    val absent = if (there == null) emptyList() else folders - present.toSet()

    val keys = buildList {
        emus.forEach { add("emu:${it.index}") }
        add("show")
        if (hasSecond) {
            add("screen:top"); add("screen:bottom")
            modes.forEach { add("mode:${it.first}") }
        }
        present.forEach { add("f:$it") }
        if (absent.isNotEmpty()) add("absent")
        if (!p.isApps) add("add")
    }
    // Opens on the emulator in use (the first card), or Show in library for Android games.
    val target = rememberFocusTarget(route, keys)
    @Composable fun Modifier.focusAs(k: String) = takesFocus(route, k, target == k)

    val panelWidth = maxWidth.coerceAtMost(980.dp)
    // The cards' room: the panel less its padding and the highlight's room on each side.
    val inner = panelWidth - 36.dp - FocusRoomSide * 2
    // Four across like the bottom screen's choices, so a row of cards reads as one deliberate band.
    val columns = if (inner >= 480.dp) 4 else 2

    PanelSurface(Modifier.width(panelWidth).heightIn(max = maxHeight)) {
        Hero(e, Modifier.fillMaxWidth()) {
            ShowSwitch(config.enabled, Modifier.focusAs("show")) {
                SystemPrefs.updateSystem(p.id) { it.copy(enabled = !it.enabled) }
                Sounds.toggle(!config.enabled)
                Toasts.show(Tx.s(if (config.enabled) R.string.library_system_hidden else R.string.library_system_back, p.shortName))
                onChanged()
            }
        }
        Spacer(Modifier.height(8.dp))
        val scroll = rememberScrollState()
        Column(
            Modifier.weight(1f, fill = false).fadingEdges(scroll, length = 36.dp).verticalScroll(scroll).padding(horizontal = FocusRoomSide, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!p.isApps) {
                Section(
                    Tx.s(R.string.library_system_emulators), Palette.Butter,
                    when {
                        emus.isEmpty() -> Tx.s(R.string.library_system_emulators_none)
                        default == null -> Tx.s(R.string.library_system_emulators_none_installed)
                        else -> Tx.s(R.string.library_system_emulators_default)
                    },
                ) {
                    CardGrid(columns) { cell ->
                        emus.forEach { (i, spec) ->
                            key(i) {
                                EmulatorCard(spec, installed = spec in players, isDefault = spec == default, Modifier.width(cell).focusAs("emu:$i")) {
                                    when {
                                        spec == default -> Toasts.show(Tx.s(R.string.library_system_already_plays, spec.name, p.shortName))
                                        spec in players -> {
                                            SystemPrefs.updateSystem(p.id) { it.copy(player = spec.name) }
                                            Sounds.confirm()
                                            Toasts.show(Tx.s(R.string.library_system_now_plays, spec.name, p.shortName))
                                        }
                                        else -> if (!Emulators.openStore(ctx, spec)) Toasts.show(Tx.s(R.string.library_system_no_store, spec.name))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (hasSecond) {
                val single = SystemPrefs.data.singleScreen
                Section(Tx.s(R.string.library_system_start_on), Palette.Sky, Tx.s(if (single) R.string.library_system_start_single else R.string.library_system_start_note)) {
                    Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                        for (bottom in listOf(false, true)) {
                            ScreenCard(bottom, chosen = config.bottomScreen == bottom, Modifier.weight(1f).focusAs(if (bottom) "screen:bottom" else "screen:top")) {
                                if (config.bottomScreen != bottom) {
                                    SystemPrefs.updateSystem(p.id) { it.copy(bottomScreen = bottom) }
                                    Sounds.confirm()
                                }
                            }
                        }
                    }
                }
                Section(Tx.s(R.string.library_system_bottom), Palette.Lavender, Tx.s(R.string.library_system_bottom_note)) {
                    Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                        modes.forEach { (m, label) ->
                            ModeCard(m, label, chosen = m == mode, Modifier.weight(1f).focusAs("mode:$m")) {
                                if (m != mode) {
                                    SystemPrefs.updateSystem(p.id) { it.copy(companion = m) }
                                    Sounds.confirm()
                                }
                            }
                        }
                    }
                }
            }
            if (!p.isApps) {
                Section(Tx.s(R.string.library_system_folders), Palette.Mint, Tx.s(R.string.library_system_folders_note, p.shortName)) {
                    CardGrid(columns) { cell ->
                        present.forEach { folder ->
                            key(folder) {
                                val dir = Library.folder(folder)
                                val added = folder in config.folders
                                FolderCard(
                                    dir.name, shortPath(dir), Tx.s(if (added) R.string.library_system_folder_added else R.string.library_system_folder_built_in), if (added) Palette.Peach else Palette.Mint,
                                    Glyph.Folder, Modifier.width(cell).focusAs("f:$folder"),
                                ) {
                                    if (!added) {
                                        Toasts.show(Tx.s(R.string.library_system_folder_games, shortPath(dir)))
                                    } else {
                                        SystemPrefs.updateSystem(p.id) { it.copy(folders = it.folders - folder) }
                                        onChanged()
                                        Undo.offer(Tx.s(R.string.library_system_folder_took_out, dir.name)) {
                                            SystemPrefs.updateSystem(p.id) { it.copy(folders = (it.folders + folder).distinct()) }
                                            onChanged()
                                        }
                                    }
                                }
                            }
                        }
                        if (absent.isNotEmpty()) {
                            // The usual names that aren't there, as one card: listing each made a wall of "Not there yet".
                            val names = absent.map { Library.folder(it).name }
                            FolderCard(
                                Tx.s(R.string.library_system_also_looks), names.joinToString(", "), Tx.s(R.string.library_system_not_there), Palette.Muted, Glyph.Search,
                                Modifier.width(cell).focusAs("absent"),
                            ) { Toasts.show(Tx.s(R.string.library_system_make_folder, shortPath(Library.folder(absent.first())), p.shortName)) }
                        }
                        FolderCard(
                            Tx.s(R.string.library_system_add_folder), Tx.s(R.string.library_system_add_folder_detail), Tx.s(R.string.library_system_add_folder_pick), Palette.Mint, Glyph.Plus,
                            Modifier.width(cell).focusAs("add"), onClick = onAddFolder,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
/** A titled group of cards, with a short note on what A does there. */
@Composable
private fun Section(title: String, color: Color, note: String?, content: @Composable ColumnScope.() -> Unit) {
    // A card taking the highlight brings its whole section into view, title and all, so you always see which
    // section you're in (on its own a card scrolled in with its title left under the header).
    val whole = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(Modifier.bringIntoViewRequester(whole).onFocusChanged { if (it.hasFocus) scope.launch { whole.bringIntoView() } }) {
        Row(Modifier.padding(start = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (note != null) {
                Spacer(Modifier.width(10.dp))
                Text(note, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        content()
    }
}

/** The page's head: the system's hero picture (or its colour) behind its art, name, maker and game count. */
@Composable
private fun Hero(e: SystemEntry, modifier: Modifier, end: @Composable () -> Unit) {
    val p = e.platform
    val color = e.color
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.height(112.dp).background(Brush.linearGradient(listOf(color.copy(alpha = 0.5f), Palette.Card)), shape)) {
        val hero = e.hero
        if (hero != null) {
            AsyncImage(
                rememberSizedRequest(hero, tilePx(640.dp)), null,
                Modifier.matchParentSize().graphicsLayer { clip = true; this.shape = shape; alpha = 0.7f }, contentScale = ContentScale.Crop,
            )
        }
        // Darker behind the words, so they read over any picture.
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Palette.Card.copy(alpha = 0.94f), Palette.Card.copy(alpha = 0.6f), Color.Transparent)), shape))
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            SystemArt(e, 84.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(e.maker, Tx.p(R.plurals.library_games, e.games.size)).joinToString("  ·  "),
                    style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            end()
        }
    }
}

/** Show in library as a switch: its state in words and a knob that slides. */
@Composable
private fun ShowSwitch(on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier.width(210.dp).height(58.dp), glow = Palette.Mint, shape = RoundedCornerShape(18.dp), lifts = false, onClick = onClick) {
        Row(Modifier.fillMaxSize().background(Palette.NightDeep.copy(alpha = 0.55f)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Tx.s(R.string.library_system_show), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(if (on) Tx.s(R.string.common_on) else Tx.s(R.string.library_system_show_off), style = MaterialTheme.typography.labelMedium, color = if (on) Palette.Mint else Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Toggle(on, Palette.Mint)
        }
    }
}

/** A card that is one of several choices: outlined and ticked in [color] while [chosen]. */
@Composable
private fun ChoiceCard(color: Color, chosen: Boolean, modifier: Modifier, onClick: () -> Unit, content: @Composable (focused: Boolean) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    FocusCard(modifier, glow = color, shape = shape, lifts = false, onClick = onClick) { focused ->
        Box(
            Modifier.fillMaxSize().background(if (chosen) color.copy(alpha = 0.2f) else Palette.NightDeep.copy(alpha = 0.35f))
                .border(1.5.dp, if (chosen) color.copy(alpha = 0.85f) else Color.Transparent, shape),
        ) {
            content(focused)
            if (chosen) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(7.dp).size(20.dp).background(color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", style = MaterialTheme.typography.labelMedium, color = Palette.OnAccent) }
            }
        }
    }
}

/** Top or bottom screen, with a little drawing of the two screens and the chosen one lit. */
@Composable
private fun ScreenCard(bottom: Boolean, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = Palette.Sky
    ChoiceCard(color, chosen, modifier.height(72.dp), onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TwoScreens(lit = if (bottom) 1 else 0, color = if (chosen) color else Palette.Muted, Modifier.size(width = 40.dp, height = 46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Tx.s(if (bottom) R.string.library_bottom_screen else R.string.library_top_screen), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    Tx.s(if (bottom) R.string.library_system_bottom_detail else R.string.library_system_top_detail),
                    style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The handheld's two screens, one above the other, the [lit] one (0 top, 1 bottom) filled. */
@Composable
private fun TwoScreens(lit: Int, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val gap = size.height * 0.1f
        val topH = size.height * 0.5f
        val botW = w * 0.78f
        val botH = size.height - topH - gap
        val r = CornerRadius(w * 0.1f)
        val stroke = Stroke(w * 0.05f)
        val rects = listOf(Offset(0f, 0f) to Size(w, topH), Offset((w - botW) / 2, topH + gap) to Size(botW, botH))
        rects.forEachIndexed { i, (at, sz) ->
            if (i == lit) drawRoundRect(color, at, sz, r) else drawRoundRect(color.copy(alpha = 0.6f), at, sz, r, style = stroke)
        }
    }
}

/** What the bottom screen shows while a game plays, as a card with its icon and a few words on it. */
@Composable
private fun ModeCard(mode: String, label: String, chosen: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = Palette.Lavender
    val (glyph, note) = when (mode) {
        GameCompanion.ART -> Glyph.Image to Tx.s(R.string.library_system_mode_art)
        GameCompanion.TANDEM -> Glyph.Sparkle to Tx.s(R.string.library_system_mode_tandem)
        GameCompanion.DARK -> Glyph.Battery to Tx.s(R.string.library_system_mode_dark)
        GameCompanion.DECK -> Glyph.Text to Tx.s(R.string.library_system_mode_deck)
        else -> Glyph.Screens to Tx.s(R.string.library_system_mode_emulator)
    }
    ChoiceCard(color, chosen, modifier.height(104.dp), onClick) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            GlyphChip(glyph, if (chosen) color else Palette.Muted, size = 30.dp, round = true)
            Spacer(Modifier.weight(1f))
            ShrinkToFit(Modifier.fillMaxWidth(), alignment = Alignment.CenterStart) {
                Text(label.substringBefore(":"), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            Text(note, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** An emulator: its app icon when installed, its name, and what A does (Default, Make default, Get it). */
@Composable
private fun EmulatorCard(spec: LaunchSpec, installed: Boolean, isDefault: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = when {
        isDefault -> Palette.Mint
        installed -> Palette.Butter
        else -> Palette.Peach
    }
    ChoiceCard(color, isDefault, modifier.height(84.dp), onClick) { focused ->
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (installed && spec.component != LaunchSpec.ANY_APP) {
                AppIcon(spec.packageName, Modifier.size(44.dp))
            } else {
                GlyphChip(if (installed) Glyph.Gamepad else Glyph.Download, color, size = 44.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(end = if (isDefault) 18.dp else 0.dp)) {
                Text(spec.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        isDefault -> Tx.s(R.string.library_system_emu_default)
                        // Quiet until highlighted: a loud "Make default" on every card drowned out the one in use.
                        installed -> Tx.s(if (focused) R.string.library_system_emu_make_default else R.string.library_system_emu_installed)
                        else -> spec.origin?.let { Tx.s(R.string.library_system_emu_get_from, it) } ?: Tx.s(R.string.library_system_emu_get)
                    },
                    style = MaterialTheme.typography.labelMedium, color = if (installed && !isDefault && !focused) Palette.Muted else color,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A folder (or the card that adds one): its name, where it is, and what A does there. */
@Composable
private fun FolderCard(name: String, detail: String, state: String, color: Color, glyph: Glyph, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier.height(72.dp), glow = color, shape = RoundedCornerShape(16.dp), lifts = false, onClick = onClick) {
        Row(
            Modifier.fillMaxSize().background(Palette.NightDeep.copy(alpha = 0.35f)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphChip(glyph, color, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).widthIn(min = 0.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Cards [columns] to a row, each exactly a column wide, rows as tall as their tallest card. A FlowRow wrapped the
 * last card of every row early (a quarter of the row stood empty), so the grid places them itself.
 */
@Composable
private fun CardGrid(columns: Int, content: @Composable (cell: Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cell = (maxWidth - Gap * (columns - 1)) / columns
        Layout({ content(cell) }) { measurables, constraints ->
            val gap = Gap.roundToPx()
            val width = constraints.maxWidth
            val cellPx = (width - gap * (columns - 1)) / columns
            val rows = measurables.map { it.measure(Constraints.fixedWidth(cellPx)) }.chunked(columns)
            val heights = rows.map { row -> row.maxOf { it.height } }
            layout(width, heights.sum() + gap * (rows.size - 1).coerceAtLeast(0)) {
                var y = 0
                rows.forEachIndexed { r, row ->
                    row.forEachIndexed { c, p -> p.place(c * (cellPx + gap), y) }
                    y += heights[r] + gap
                }
            }
        }
    }
}
