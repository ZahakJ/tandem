package io.github.zahakj.tandem.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.DeckGames
import io.github.zahakj.tandem.Emulators
import io.github.zahakj.tandem.GameCompanion
import io.github.zahakj.tandem.LaunchSpec
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.data.SystemPrefs
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * One system's page, opened from a tile on the Systems screen: the system's art over its hero picture with the Show
 * in library switch, then everything else as cards you can see at once: its emulators with their app icons (A makes
 * one the default, or gets one that isn't installed), the screen its games start on, what the bottom screen shows
 * while they play, and its folders. No row hides its choices behind ‹ ›. B goes back to the tile.
 */

private val Gap = 10.dp

@OptIn(ExperimentalLayoutApi::class)
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
    val columns = if (inner >= 640.dp) 4 else if (inner >= 420.dp) 3 else 2
    val cell = (inner - Gap * (columns - 1)) / columns

    PanelSurface(Modifier.width(panelWidth).heightIn(max = maxHeight)) {
        Hero(e, Modifier.fillMaxWidth()) {
            ShowSwitch(config.enabled, Modifier.focusAs("show")) {
                SystemPrefs.updateSystem(p.id) { it.copy(enabled = !it.enabled) }
                Sounds.toggle(!config.enabled)
                Toasts.show(if (config.enabled) "${p.shortName} is hidden from your library. It waits under Turned off" else "${p.shortName} is back in your library")
                onChanged()
            }
        }
        Spacer(Modifier.height(8.dp))
        val scroll = rememberScrollState()
        Column(
            Modifier.weight(1f, fill = false).fadingEdges(scroll).verticalScroll(scroll).padding(horizontal = FocusRoomSide, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!p.isApps) {
                Section(
                    "Emulators", Palette.Butter,
                    when {
                        emus.isEmpty() -> "None listed for this system"
                        default == null -> "None installed yet: A on one gets it"
                        else -> "A makes one the default"
                    },
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap), verticalArrangement = Arrangement.spacedBy(Gap), maxItemsInEachRow = columns) {
                        emus.forEach { (i, spec) ->
                            key(i) {
                                EmulatorCard(spec, installed = spec in players, isDefault = spec == default, Modifier.width(cell).focusAs("emu:$i")) {
                                    when {
                                        spec == default -> Toasts.show("${spec.name} already plays ${p.shortName} games")
                                        spec in players -> {
                                            SystemPrefs.updateSystem(p.id) { it.copy(player = spec.name) }
                                            Sounds.confirm()
                                            Toasts.show("${spec.name} now plays ${p.shortName} games")
                                        }
                                        else -> if (!Emulators.openStore(ctx, spec)) Toasts.show("Couldn't open a store for ${spec.name}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (hasSecond) {
                val single = SystemPrefs.data.singleScreen
                Section("Start games on", Palette.Sky, if (single) "Single screen mode is on, so games stay on top for now" else "A game's own Start on (Y › More) wins") {
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
                Section("Bottom screen while playing", Palette.Lavender, "For this system's games; a game can still have its own") {
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
                Section("Folders", Palette.Mint, "Where ${p.shortName} games are read from, besides your game folders") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Gap), verticalArrangement = Arrangement.spacedBy(Gap), maxItemsInEachRow = columns) {
                        present.forEach { folder ->
                            key(folder) {
                                val dir = Library.folder(folder)
                                val added = folder in config.folders
                                FolderCard(
                                    dir.name, shortPath(dir), if (added) "Added · A takes it out" else "Built in", if (added) Palette.Peach else Palette.Mint,
                                    Glyph.Folder, Modifier.width(cell).focusAs("f:$folder"),
                                ) {
                                    if (!added) {
                                        Toasts.show("Games in ${shortPath(dir)} show up here")
                                    } else {
                                        SystemPrefs.updateSystem(p.id) { it.copy(folders = it.folders - folder) }
                                        onChanged()
                                        Undo.offer("Took out ${dir.name}") {
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
                                "Also looks in", names.joinToString(", "), "Not there yet", Palette.Muted, Glyph.Search,
                                Modifier.width(cell).focusAs("absent"),
                            ) { Toasts.show("Make ${shortPath(Library.folder(absent.first()))} and put ${p.shortName} games in it") }
                        }
                        FolderCard(
                            "Add a folder…", "Only this system's games, anywhere", "Pick", Palette.Mint, Glyph.Plus,
                            Modifier.width(cell).focusAs("add"), onClick = onAddFolder,
                        )
                    }
                }
            }
        }
    }
}

/** A titled group of cards, with a short note on what A does there. */
@Composable
private fun Section(title: String, color: Color, note: String?, content: @Composable ColumnScope.() -> Unit) {
    Column {
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
                    listOfNotNull(e.maker, plural(e.games.size, "game")).joinToString("  ·  "),
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
                Text("Show in library", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(if (on) "On" else "Off · under Turned off", style = MaterialTheme.typography.labelMedium, color = if (on) Palette.Mint else Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Toggle(on, Palette.Mint)
        }
    }
}

/** A card that is one of several choices: outlined and ticked in [color] while [chosen]. */
@Composable
private fun ChoiceCard(color: Color, chosen: Boolean, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    FocusCard(modifier, glow = color, shape = shape, lifts = false, onClick = onClick) {
        Box(
            Modifier.fillMaxSize().background(if (chosen) color.copy(alpha = 0.2f) else Palette.NightDeep.copy(alpha = 0.35f))
                .border(1.5.dp, if (chosen) color.copy(alpha = 0.85f) else Color.Transparent, shape),
        ) {
            content()
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
                Text(if (bottom) "Bottom screen" else "Top screen", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    if (bottom) "The small screen, handy for touch" else "The big screen, the usual",
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
        GameCompanion.ART -> Glyph.Image to "The game's art and details"
        GameCompanion.TANDEM -> Glyph.Sparkle to "Clock, Pip and quick toggles"
        GameCompanion.DARK -> Glyph.Battery to "Black until tapped, saves power"
        GameCompanion.DECK -> Glyph.Text to "Keyboard and trackpad"
        else -> Glyph.Screens to "For emulators that use both"
    }
    ChoiceCard(color, chosen, modifier.height(96.dp), onClick) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            GlyphChip(glyph, if (chosen) color else Palette.Muted, size = 30.dp, round = true)
            Spacer(Modifier.weight(1f))
            ShrinkToFit(Modifier.fillMaxWidth(), alignment = Alignment.CenterStart) {
                Text(label.substringBefore(":"), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            Text(note, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    ChoiceCard(color, isDefault, modifier.height(72.dp), onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (installed && spec.component != LaunchSpec.ANY_APP) {
                AppIcon(spec.packageName, Modifier.size(44.dp))
            } else {
                GlyphChip(if (installed) Glyph.Gamepad else Glyph.Download, color, size = 44.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).padding(end = if (isDefault) 18.dp else 0.dp)) {
                Text(spec.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        isDefault -> "Default"
                        installed -> "Make default"
                        else -> "Get it" + (spec.origin?.let { " · $it" } ?: "")
                    },
                    style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
