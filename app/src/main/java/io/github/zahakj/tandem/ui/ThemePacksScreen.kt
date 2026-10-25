package io.github.zahakj.tandem.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.Look
import io.github.zahakj.tandem.data.PackParts
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Theme packs: every installed pack (Tandem's own and Cocoon ones) with a live preview, its author and what
 * it brings. A opens a pack: use it, lend its icons to another theme, switch its parts on and off, or remove it.
 * The first rows import a pack from a folder or a .zip with the system file picker.
 */
@Composable
fun ThemePacksScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val route = Route.ThemePacks.key
    val look = Store.settings.look
    val packs = ThemePacks.packs
    var busy by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf<String?>(FocusMemory.get(route)) }

    fun imported(result: PackImport.Result) {
        busy = false
        when (result) {
            is PackImport.Result.Done -> {
                scope.launch {
                    ThemePacks.refresh(force = true)
                    val id = "pack:${result.dir.name}"
                    FocusMemory.remember(route, id)
                    Nav.focusTick++
                    Toasts.show("Added ${result.name}. Press A on it to use it")
                }
            }
            is PackImport.Result.Failed -> { Toasts.show(result.reason); Sounds.error() }
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        Toasts.show("Importing…")
        scope.launch { imported(withContext(Dispatchers.IO) { PackImport.fromTree(ctx, uri) }) }
    }
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        Toasts.show("Importing…")
        scope.launch { imported(withContext(Dispatchers.IO) { PackImport.fromZip(ctx, uri) }) }
    }

    val actions = listOf("import:folder", "import:zip", "reload")
    val keys = actions + packs.map { it.id }
    val target = rememberFocusTarget(route, keys)
    val state = rememberRouteListState(route)
    KeepInView(state, keys.indexOf(target))
    LaunchedEffect(Unit) { ThemePacks.refresh() }

    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 22.dp)) {
        LazyColumn(
            Modifier.weight(1.3f).fillMaxHeight().fadingEdges(state),
            state = state,
            contentPadding = PaddingValues(start = FocusRoomSide, end = FocusRoomSide, top = FocusRoom, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "h:add") { SettingsHeader("Add packs") }
            item(key = "import:folder") {
                ActionRow("Import a folder", "A pack folder: theme.json, wallpapers, icons", Glyph.Folder, Palette.Mint, "import:folder", target, route, { focused = it }) {
                    if (!busy) runCatching { pickFolder.launch(null) }.onFailure { Toasts.show("No file picker on this device") }
                }
            }
            item(key = "import:zip") {
                ActionRow("Import a .zip", "A zipped Tandem or Cocoon pack", Glyph.Download, Palette.Sky, "import:zip", target, route, { focused = it }) {
                    if (!busy) runCatching { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
                        .onFailure { Toasts.show("No file picker on this device") }
                }
            }
            item(key = "reload") {
                ActionRow("Look again", "ROMs/tandem/themes and Cocoon's ROMs/themes", Glyph.Refresh, Palette.Butter, "reload", target, route, { focused = it }) {
                    scope.launch {
                        ThemePacks.refresh(force = true)
                        val n = ThemePacks.packs.size
                        Toasts.show(if (n == 0) "No theme packs found" else "Found ${plural(n, "theme pack")}")
                    }
                }
            }
            item(key = "h:installed") { SettingsHeader(if (packs.isEmpty()) "No packs yet" else "Installed") }
            if (packs.isEmpty()) item(key = "empty") {
                Text(
                    "Cocoon packs you already downloaded appear here by themselves. Or import one above; it is copied into ROMs/tandem/themes.",
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
            }
            items(packs, key = { it.id }) { p ->
                PackRow(p, look, Modifier.takesFocus(route, p.id, p.id == target), onFocus = {
                    FocusMemory.remember(route, p.id); focused = p.id
                    Selection.current = Selected.Setting(p.name, p.format.label, p.description.ifBlank { p.contents }, p.spec.accent)
                }) { FocusMemory.forget("packmenu"); open = p.id }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(0.8f).fillMaxHeight().padding(top = FocusRoom, bottom = 22.dp).clip(RoundedCornerShape(24.dp))
                .background(Palette.Card.copy(alpha = 0.72f)).padding(18.dp),
        ) {
            val p = ThemePacks.byId(focused)
            if (p != null) PackDetails(p, look) else PacksHelp()
        }
    }

    PackMenu(open?.let { ThemePacks.byId(it) }, onClose = { open = null; Nav.focusTick++ })
}

private fun plural(n: Int, one: String) = "$n ${if (n == 1) one else one + "s"}"

@Composable
private fun ActionRow(
    label: String, detail: String, icon: Glyph, color: androidx.compose.ui.graphics.Color, key: String, target: String?, route: String,
    onFocus: (String?) -> Unit, onClick: () -> Unit,
) {
    FocusCard(
        Modifier.fillMaxWidth().takesFocus(route, key, key == target), glow = color, shape = RoundedCornerShape(16.dp),
        onFocus = { FocusMemory.remember(route, key); onFocus(null) }, onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphChip(icon, color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PackRow(p: ThemePack, look: Look, focus: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val spec = Themes.withParts(p.spec, look)
    FocusCard(Modifier.fillMaxWidth().height(86.dp).then(focus), glow = spec.accent, shape = RoundedCornerShape(16.dp), onFocus = onFocus, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ThemeMiniature(spec, p, Modifier.fillMaxHeight().aspectRatio(16f / 10f).clip(RoundedCornerShape(10.dp)), tile = 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(p.author.ifBlank { null }?.let { "by $it" }, p.format.label).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(p.contents, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            when {
                look.theme == p.id -> Pill("In use", spec.accent)
                look.iconPack == p.id -> Pill("Icons", spec.accent)
            }
        }
    }
}

/** The side panel for the highlighted pack: a big live preview, who made it and what it brings. */
@Composable
private fun PackDetails(p: ThemePack, look: Look) {
    val spec = Themes.withParts(p.spec, look)
    Column(Modifier.fillMaxSize()) {
        ThemeMiniature(spec, p, Modifier.fillMaxWidth().aspectRatio(1.7f).clip(RoundedCornerShape(16.dp)), moving = true, tile = 52.dp)
        Spacer(Modifier.height(12.dp))
        Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (p.author.isNotBlank()) Text("by ${p.author}" + if (p.version.isNotBlank()) " · v${p.version}" else "", style = MaterialTheme.typography.titleSmall, color = spec.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).fadingEdges(scroll).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (p.description.isNotBlank()) Text(p.description.replace("\r", ""), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            Text(p.contents.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
            if (p.credits.isNotEmpty()) Text("Art: " + p.credits.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            Text(
                if (p.removable) "Stored in ROMs/tandem/themes/${p.dir.name}" else "Installed by Cocoon in ROMs/themes/${p.dir.name}. Tandem only reads it.",
                style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
            )
        }
    }
}

@Composable
private fun PacksHelp() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(72.dp, mood = 1)
        Spacer(Modifier.height(12.dp))
        Text("Theme packs", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Packs bring colours, wallpapers and art for every system. Tandem reads its own packs and packs in the Cocoon format. " +
                "Packs keep their author's credit; please share them the way their authors ask.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
        )
    }
}

private fun editLook(change: (Look) -> Look) = Store.updateSettings { it.copy(look = change(it.look)) }

private fun setPart(id: String, change: (PackParts) -> PackParts) = editLook { l ->
    val now = change(l.packParts[id] ?: PackParts())
    l.copy(packParts = if (now == PackParts()) l.packParts - id else l.packParts + (id to now))
}

/** A on a pack: use it, lend its icons, choose its parts, remove it. */
@Composable
private fun PackMenu(pack: ThemePack?, onClose: () -> Unit) {
    val shown = rememberLatest(pack)
    OverlayPanel(pack != null, onClose = onClose) {
        val p = shown ?: return@OverlayPanel
        val look = Store.settings.look
        val parts = ThemePacks.parts(p.id)
        val routeKey = "packmenu"
        val inUse = look.theme == p.id
        val hasArt = !p.art.isEmpty
        val keys = listOfNotNull(
            "use", "icons".takeIf { hasArt && !inUse }, "colors", "wallpaper".takeIf { p.hasWallpaper },
            "icon".takeIf { p.art.iconCount > 0 || p.art.collectionCount > 0 }, "heroes".takeIf { p.art.heroCount > 0 },
            "frames".takeIf { p.art.overlayCount > 0 }, "remove".takeIf { p.removable },
        )
        val target = rememberFocusTarget(routeKey, keys)
        val scroll = rememberScrollState()
        Column(
            Modifier.width(440.dp).background(Palette.Card, RoundedCornerShape(24.dp)).padding(18.dp),
        ) {
            Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(p.author.ifBlank { null }?.let { "by $it" }, p.format.label).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.weight(1f, fill = false).heightIn(max = 360.dp).fadingEdges(scroll).verticalScroll(scroll).padding(vertical = 10.dp, horizontal = FocusRoomSide),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MenuRow(if (inUse) "In use" else "Use this theme", null, "use", routeKey, target, Palette.Mint) {
                    if (!inUse) {
                        applyTheme(p.id)
                        Toasts.show(if (p.author.isNotBlank()) "${p.name} by ${p.author}" else p.name)
                    }
                    onClose()
                }
                if ("icons" in keys) {
                    val lent = look.iconPack == p.id
                    MenuRow(if (lent) "Stop using its icons" else "Use its icons with my theme", null, "icons", routeKey, target, Palette.Sky) {
                        editLook { it.copy(iconPack = if (lent) "auto" else p.id) }
                        onClose()
                    }
                }
                MenuRow("Colours", onOff(parts.colors), "colors", routeKey, target, Palette.Lavender) { setPart(p.id) { it.copy(colors = !it.colors) } }
                if ("wallpaper" in keys) MenuRow("Wallpaper", onOff(parts.wallpaper), "wallpaper", routeKey, target, Palette.Lavender) { setPart(p.id) { it.copy(wallpaper = !it.wallpaper) } }
                if ("icon" in keys) MenuRow("System icons", onOff(parts.icons), "icon", routeKey, target, Palette.Lavender) { setPart(p.id) { it.copy(icons = !it.icons) } }
                if ("heroes" in keys) MenuRow("Heroes & logos", onOff(parts.heroes), "heroes", routeKey, target, Palette.Lavender) { setPart(p.id) { it.copy(heroes = !it.heroes) } }
                if ("frames" in keys) MenuRow("Cover frames", onOff(parts.frames), "frames", routeKey, target, Palette.Lavender) { setPart(p.id) { it.copy(frames = !it.frames) } }
                if ("remove" in keys) MenuRow("Remove pack", null, "remove", routeKey, target, Palette.Peach) {
                    onClose()
                    Confirm.hold("Remove ${p.name}?", "Deletes ROMs/tandem/themes/${p.dir.name}. You can import it again later.", "Remove") {
                        if (PackImport.remove(p)) {
                            editLook { l ->
                                l.copy(
                                    theme = if (l.theme == p.id) "pastel-night" else l.theme,
                                    iconPack = if (l.iconPack == p.id) "auto" else l.iconPack,
                                    packParts = l.packParts - p.id,
                                )
                            }
                            ThemePacks.reload()
                            Toasts.show("Removed ${p.name}")
                        } else Toasts.show("Couldn't remove that pack")
                    }
                }
            }
        }
    }
}
