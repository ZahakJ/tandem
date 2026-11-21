package io.github.zahakj.tandem.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Buttons
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.LibraryRoots
import io.github.zahakj.tandem.Prefs
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Settings › Library & art › Game folders: the folders games are read from (LibraryRoots). The ROMs folder,
 * Tandem's own, is always there; the others were picked with the system folder picker (an SD card, any folder with
 * games in it, however it is laid out) and can be taken out again. A on a folder opens its options, Y (or a long
 * press) takes it out, with Undo: its games leave the library view, their details stay in library.json under the
 * same keys, so adding it back loses nothing.
 */

/** The ROMs folder's key in the list; the other rows are keyed by their path. */
private const val MAIN = "main"
private const val ADD = "add"

@Composable
fun GameFoldersScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val route = Route.GameFolders.key
    val internal = remember { Environment.getExternalStorageDirectory().path }
    val romsPath = Library.romsDir.path
    val roots = SystemPrefs.data.roots
    val libs = Libraries.all
    // Games per root, hidden ones left out as everywhere; a game with no root of its own is the ROMs folder's.
    val counts = remember(libs, io.github.zahakj.tandem.Privacy.view) {
        Collections.visible(libs.orEmpty().flatMap { it.games }).groupingBy { it.root?.path ?: romsPath }.eachCount()
    }
    var focused by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    val keys = listOf(MAIN) + roots + listOf(ADD)
    val target = rememberFocusTarget(route, keys)

    fun rescan() = scope.launch { reloadLibrary(ctx) }

    fun remove(path: String) {
        val tree = SystemPrefs.data.trees[path]
        val name = LibraryRoots.label(path, internal)
        // Focus goes to the row's neighbour as the list was (the row before it, or the ROMs row).
        val i = keys.indexOf(path)
        FocusMemory.remember(route, keys.getOrElse(i - 1) { MAIN })
        SystemPrefs.update { it.copy(roots = it.roots - path, trees = it.trees - path) }
        Nav.focusTick++
        rescan()
        // Its games only leave the view; what Tandem knows about them stays, so coming back costs nothing.
        Undo.offer("Took out $name. Its games are out of sight; their details are kept") {
            SystemPrefs.update { it.copy(roots = (it.roots + path).distinct(), trees = if (tree == null) it.trees else it.trees + (path to tree)) }
            FocusMemory.remember(route, path)
            Nav.focusTick++
            rescan()
        }
    }

    val pick = rememberGameFolderPicker { path ->
        FocusMemory.remember(route, if (path in SystemPrefs.data.roots) path else MAIN)
        Nav.focusTick++
    }

    // Y on a folder takes it out (a long press on the touch screen is Y too).
    LaunchedEffect(Unit) {
        Buttons.events.collect { b ->
            if (Nav.route != Route.GameFolders || Nav.overlayOpen || open != null) return@collect
            if (b == Button.Y) focused?.takeIf { it in SystemPrefs.data.roots }?.let(::remove)
        }
    }

    // Each folder's systems with their pack art, most games first, for the pictures on its card. Looked up off the
    // main thread: a pack picture is a file check.
    val systemsIn by produceState<Map<String, List<FolderSystem>>>(emptyMap(), libs, io.github.zahakj.tandem.Privacy.view, IconPacks.version) {
        value = withContext(Dispatchers.IO) {
            libs.orEmpty().flatMap { lib ->
                Collections.visible(lib.games).groupingBy { it.root?.path ?: romsPath }.eachCount().map { (root, n) -> root to (lib.platform to n) }
            }.groupBy({ it.first }, { it.second }).mapValues { (_, v) ->
                v.sortedByDescending { it.second }.map { (p, n) -> FolderSystem(p, n, IconPacks.systemIcon(p.id)) }
            }
        }
    }

    Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 22.dp)) {
        val grid = rememberRouteGridState(route)
        // The heading takes the grid's first place.
        KeepInView(grid, keys.indexOf(target) + 1)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(250.dp),
            modifier = Modifier.weight(1.3f).fillMaxHeight().fadingEdges(grid),
            state = grid,
            contentPadding = PaddingValues(start = FocusRoomSide, end = FocusRoomSide, top = 6.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "h:where", span = { GridItemSpan(maxLineSpan) }) { SettingsHeader("Where games live") }
            item(key = MAIN) {
                val n = counts[romsPath] ?: 0
                FolderCard(
                    "ROMs", "Tandem's own folder · always in", plural(n, "game"), Palette.Mint, systemsIn[romsPath].orEmpty(),
                    Modifier.takesFocus(route, MAIN, target == MAIN),
                    onFocus = {
                        FocusMemory.remember(route, MAIN); focused = MAIN
                        Selection.current = Selected.Setting("ROMs", plural(n, "game"), "Tandem's own folder on internal storage, where it keeps its data. It is always read for games too, and needn't hold any.", Palette.Mint, "Options")
                    },
                ) { Toasts.show("The ROMs folder is always included. Add your games folders beside it") }
            }
            items(roots, key = { it }) { path ->
                val n = counts[path] ?: 0
                val there = remember(path, libs) { File(path).isDirectory }
                val name = LibraryRoots.label(path, internal)
                val color = if (there) Palette.Sky else Palette.Peach
                FolderCard(
                    name, LibraryRoots.shortPath(path, internal), if (!there) "Not there right now" else if (n == 0 && copies(path, internal) != null) "Copies only" else plural(n, "game"), color, systemsIn[path].orEmpty(),
                    Modifier.takesFocus(route, path, target == path),
                    onFocus = {
                        FocusMemory.remember(route, path); focused = path
                        Selection.current = Selected.Setting(
                            name, if (there) plural(n, "game") else "Not there right now",
                            if (there) "Games are found by their system folders (3DS, gba, PlayStation 2…) or by their kind of file. Y takes it out again." else "The card or folder isn't mounted. Its games come back by themselves once it is.",
                            color, "Options",
                        )
                    },
                    empty = copiesNote(path, internal) ?: "No games found yet",
                ) { FocusMemory.forget("gamefolder"); open = path }
            }
            item(key = ADD) {
                AddCard(Modifier.takesFocus(route, ADD, target == ADD), onFocus = {
                    FocusMemory.remember(route, ADD); focused = ADD
                    Selection.current = Selected.Setting("Pick your games folder", "SD card or device", "Pick the folder your games are in: a folder per system, one system's folder, or games of every kind together.", Palette.Mint, "Pick")
                }) {
                    pick()
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(0.8f).fillMaxHeight().padding(top = FocusRoom, bottom = 22.dp).clip(RoundedCornerShape(24.dp))
                .background(Palette.Card.copy(alpha = 0.72f)).padding(18.dp),
        ) {
            val f = focused
            if (f != null && f in roots) RootDetails(f, counts[f] ?: 0, internal) else FoldersHelp(roots.size, counts[romsPath] ?: 0)
        }
    }

    // A folder's options: both actions side by side as buttons, the usual one filled.
    val shownPath = rememberLatest(open)
    PanelGuard(open != null)
    OverlayPanel(open != null, onClose = { open = null; Nav.focusTick++ }) {
        val path = shownPath ?: return@OverlayPanel
        val routeKey = "gamefolder"
        val name = LibraryRoots.label(path, internal)
        val sheetTarget = rememberFocusTarget(routeKey, listOf("rescan", "remove"))
        PanelSurface(Modifier.width(500.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphChip(Glyph.Folder, Palette.Sky, size = 46.dp, round = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(LibraryRoots.shortPath(path, internal), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().focusRow(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Look for games again", Palette.Mint, Modifier.weight(1f).takesFocus(routeKey, "rescan", sheetTarget == "rescan"), fill = true, filled = true) {
                    open = null
                    Nav.focusTick++
                    Toasts.show("Looking for games…")
                    rescan()
                }
                PillButton("Take it out", Palette.Peach, Modifier.weight(1f).takesFocus(routeKey, "remove", sheetTarget == "remove"), fill = true) {
                    open = null
                    remove(path)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Taking it out hides its games; their details are kept, and Undo (for a few seconds) brings it back.",
                style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
            )
        }
    }
}

/**
 * The system folder picker for "Pick your games folder", shared by Game folders, the empty home and first-run setup.
 * A picked folder is checked (LibraryRoots.check), added as a game folder with its document link (which lets
 * emulators that only open shared links have its games, Launcher) and the library is looked through again. Picking
 * the ROMs folder, or all of internal storage, grants that link for internal storage instead. Returns what opens the
 * picker, at [start] when given; [onAdded] hears the folder's path once it is in (the ROMs path for a granted link).
 */
@Composable
fun rememberGameFolderPicker(start: Uri? = null, onAdded: (String) -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val internal = remember { Environment.getExternalStorageDirectory().path }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val grant = { runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        val romsPath = Library.romsDir.path
        if (LibraryRoots.isRomsTree(uri.authority, runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull())) {
            grant()
            Prefs.setRomsTree(ctx, uri)
            Sounds.confirm()
            Toasts.show(
                if (treePath(uri) == internal) "Emulators can open games anywhere on internal storage now. Pick your games folder too if it isn't ROMs"
                else "ROMs is always in. Emulators can open its games now",
            )
            onAdded(romsPath)
            return@rememberLauncherForActivityResult
        }
        val path = treePath(uri)
        if (path == null) {
            Toasts.show("That folder can't be used, try one on the device or SD card")
            Sounds.error()
            return@rememberLauncherForActivityResult
        }
        val name = LibraryRoots.label(path, internal)
        when (LibraryRoots.check(path, romsPath, SystemPrefs.data.roots)) {
            LibraryRoots.Verdict.IsRoms -> Toasts.show("That's the ROMs folder, which is always in")
            LibraryRoots.Verdict.InsideRoms -> Toasts.show("$name is inside the ROMs folder, so its games are already found")
            LibraryRoots.Verdict.AboveRoms -> Toasts.show("$name holds the ROMs folder, so its games would show twice. Pick the folder your games are in")
            LibraryRoots.Verdict.Already -> Toasts.show("$name is already in")
            LibraryRoots.Verdict.Ok -> {
                grant()
                SystemPrefs.update { it.copy(roots = it.roots + path, trees = it.trees + (path to uri.toString())) }
                Sounds.confirm()
                Toasts.show("Added $name, looking for games…")
                onAdded(path)
                scope.launch { reloadLibrary(ctx) }
                return@rememberLauncherForActivityResult
            }
        }
        Sounds.error()
    }
    return { runCatching { launcher.launch(start) }.onFailure { Toasts.show("No folder picker on this device") } }
}

/** One of a folder's systems: how many of its games are there, and its pack picture (null: Tandem draws one). */
private class FolderSystem(val platform: io.github.zahakj.tandem.Platform, val games: Int, val icon: File?)

/**
 * "1 game already in ROMs" when [path] holds copies of games another folder already gives (the same file name, see
 * LibraryRoots.shadowed), so a folder of copies doesn't look empty. Null with no copies, or in private mode, where
 * counting them could hint at hidden games.
 */
private fun copiesNote(path: String, internal: String): String? = copies(path, internal)?.let { (n, where) -> "${plural(n, "game")} already in $where" }

/** How many of [path]'s games are copies of ones another folder gives, and that folder's name ("other folders" for several). */
private fun copies(path: String, internal: String): Pair<Int, String>? {
    if (io.github.zahakj.tandem.Privacy.enabled) return null
    val s = Library.shadowed[path] ?: return null
    val romsPath = Library.romsDir.path
    val where = s.listedFrom.singleOrNull()?.let { if (it == romsPath) "ROMs" else LibraryRoots.label(it, internal) } ?: "other folders"
    return s.count to where
}

/** How many systems a folder card shows as pictures before "+n". */
private const val CARD_SYSTEMS = 5

/**
 * One game folder as a card: its name and where it is, then the systems found in it as their pictures (most games
 * first) and how many games it holds right now.
 */
@Composable
private fun FolderCard(name: String, detail: String, games: String, color: Color, systems: List<FolderSystem>, focus: Modifier, onFocus: () -> Unit, empty: String = "No games found yet", onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().height(126.dp).then(focus), glow = color, shape = RoundedCornerShape(20.dp), onFocus = onFocus, entry = true, onClick = onClick) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.03f)))).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphChip(Glyph.Folder, color, size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                systems.take(CARD_SYSTEMS).forEach { s ->
                    MiniSystemArt(s, 34.dp)
                    Spacer(Modifier.width(6.dp))
                }
                if (systems.size > CARD_SYSTEMS) {
                    Text("+${systems.size - CARD_SYSTEMS}", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
                }
                if (systems.isEmpty()) Text(empty, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Pill(games, color)
            }
        }
    }
}

/** A system's picture, small: the pack's icon, or Tandem's drawing without its label. */
@Composable
private fun MiniSystemArt(s: FolderSystem, size: Dp) {
    val icon = s.icon
    if (icon != null) {
        AsyncImage(rememberSizedRequest(icon, tilePx(size)), s.platform.name, Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)), contentScale = ContentScale.Crop)
    } else {
        DrawnIcon(s.platform.shortName.ifBlank { s.platform.name }, Devices.of(s.platform.id), SystemColors.of(s.platform.id), size)
    }
}

/** The card that adds a folder: outlined, with a plus, like an empty place waiting for one. */
@Composable
private fun AddCard(focus: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    FocusCard(Modifier.fillMaxWidth().height(126.dp).then(focus), glow = Palette.Mint, shape = shape, onFocus = onFocus, onClick = onClick) {
        Column(
            Modifier.fillMaxSize().border(1.5.dp, Palette.Mint.copy(alpha = 0.45f), shape).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            GlyphChip(Glyph.Plus, Palette.Mint, size = 40.dp, round = true)
            Spacer(Modifier.height(8.dp))
            Text("Pick your games folder…", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("On the device or an SD card, laid out any way", style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The side panel for the highlighted folder: where it is, whether it is there, and its games by system. */
@Composable
private fun RootDetails(path: String, games: Int, internal: String) {
    val libs = Libraries.all
    val there = remember(path, libs) { File(path).isDirectory }
    val bySystem = remember(path, libs, io.github.zahakj.tandem.Privacy.view) {
        libs.orEmpty().mapNotNull { lib ->
            val n = Collections.visible(lib.games).count { it.root?.path == path }
            if (n == 0) null else lib.platform.name to n
        }.sortedByDescending { it.second }
    }
    Column(Modifier.fillMaxSize()) {
        Text(LibraryRoots.label(path, internal), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(path, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Pill(if (!there) "Not there right now" else if (games == 0 && copies(path, internal) != null) "Copies only" else plural(games, "game"), if (there) Palette.Sky else Palette.Peach)
        Spacer(Modifier.height(10.dp))
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).fadingEdges(scroll).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                !there -> Text(
                    if (LibraryRoots.removable(path, internal)) "The SD card is out. Its games are hidden until it is back in, and come back by themselves."
                    else "The folder isn't there right now. Its games are hidden until it is back.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
                bySystem.isEmpty() && copies(path, internal) != null -> Text(
                    copies(path, internal)!!.let { (n, where) ->
                        if (n == 1) "The game here is already in $where: the same file, so it's listed once, from there. Nothing is lost; take the copy out of one of the folders if you like."
                        else "The $n games here are already in $where: the same files, so each is listed once, from there. Nothing is lost; take the copies out of one of the folders if you like."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
                bySystem.isEmpty() -> Text(
                    "No games found here yet. Games are found in folders named for their system (3DS, GBA, PS2…), and files only one system plays (.3ds, .nsp, .gba…) are found anywhere.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
                else -> {
                    for ((system, n) in bySystem) Row(Modifier.fillMaxWidth()) {
                        Text(system, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(plural(n, "game"), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1)
                    }
                    copiesNote(path, internal)?.let { note ->
                        Text("Plus $note, listed from there.", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Artwork is read from this folder's downloaded_media first, then from ROMs/downloaded_media. Y takes the folder out; its games' details are kept.",
                style = MaterialTheme.typography.labelMedium, color = Palette.Muted,
            )
        }
    }
}

@Composable
private fun FoldersHelp(extra: Int, romsGames: Int) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(72.dp, mood = 1)
        Spacer(Modifier.height(12.dp))
        Text("Game folders", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Point Tandem at the folder your games are in, on the device or an SD card, laid out any way: a folder per system, one system's folder, " +
                "or every kind together. Their games join the same systems. " +
                if (extra == 0) "Only ROMs is in so far, with ${plural(romsGames, "game")}." else "Right now ROMs and ${plural(extra, "more folder")} are in.",
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
        )
    }
}

/** Settings rows for this feature: the folder manager, and folder watching (FolderWatch.kt). */
@Composable
fun gameFoldersSettings(): SettingSection {
    val roots = SystemPrefs.data.roots
    val s = Store.settings
    return SettingSection(
        "Game folders",
        listOf(
            SettingRow(
                "Game folders", if (roots.isEmpty()) "ROMs only" else "ROMs + ${plural(roots.size, "folder")}", Palette.Mint, Glyph.Folder,
                help = "The folders your games are in: pick any folder, on the device or an SD card, laid out any way. ROMs, Tandem's own folder, is always included. " +
                    "Their games join the same systems, and a folder that isn't there (the card is out) is skipped until it is back.",
                opens = true, keywords = "sd card storage external roots folders paths directory more games second tree pick games folder roms",
            ) { Nav.go(Route.GameFolders) },
            toggle(
                "Watch game folders", Glyph.Eye, Palette.Mint,
                "Notice games copied in over USB or MTP, or an SD card put back, and show them a few seconds later without a rescan. " +
                    "Only while Tandem is on screen; nothing runs in the background.",
                s.watchFolders,
            ) { on -> Store.updateSettings { it.copy(watchFolders = on) } }
                .copy(keywords = "watch monitor automatic rescan refresh new games copied mtp usb inotify live"),
        ),
    )
}
