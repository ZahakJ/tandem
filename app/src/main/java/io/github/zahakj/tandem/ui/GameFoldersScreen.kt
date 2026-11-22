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
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
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
        Undo.offer(Tx.s(R.string.library_folders_took_out, name)) {
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
            item(key = "h:where", span = { GridItemSpan(maxLineSpan) }) { SettingsHeader(Tx.s(R.string.library_folders_where)) }
            item(key = MAIN) {
                val n = counts[romsPath] ?: 0
                FolderCard(
                    "ROMs", Tx.s(R.string.library_folders_roms_detail), Tx.p(R.plurals.library_games, n), Palette.Mint, systemsIn[romsPath].orEmpty(), // tx-ok: a folder's name
                    Modifier.takesFocus(route, MAIN, target == MAIN),
                    onFocus = {
                        FocusMemory.remember(route, MAIN); focused = MAIN
                        Selection.current = Selected.Setting("ROMs", Tx.p(R.plurals.library_games, n), Tx.s(R.string.library_folders_roms_help), Palette.Mint, Tx.s(R.string.library_folders_options)) // tx-ok: a folder's name
                    },
                ) { Toasts.show(Tx.s(R.string.library_folders_roms_always)) }
            }
            items(roots, key = { it }) { path ->
                val n = counts[path] ?: 0
                val there = remember(path, libs) { File(path).isDirectory }
                val name = LibraryRoots.label(path, internal)
                val color = if (there) Palette.Sky else Palette.Peach
                FolderCard(
                    name, LibraryRoots.shortPath(path, internal), folderCount(there, n, path, internal), color, systemsIn[path].orEmpty(),
                    Modifier.takesFocus(route, path, target == path),
                    onFocus = {
                        FocusMemory.remember(route, path); focused = path
                        Selection.current = Selected.Setting(
                            name, if (there) Tx.p(R.plurals.library_games, n) else Tx.s(R.string.library_folders_not_there),
                            Tx.s(if (there) R.string.library_folders_help_there else R.string.library_folders_help_gone),
                            color, Tx.s(R.string.library_folders_options),
                        )
                    },
                    empty = copiesNote(path, internal) ?: Tx.s(R.string.library_folders_no_games),
                ) { FocusMemory.forget("gamefolder"); open = path }
            }
            item(key = ADD) {
                AddCard(Modifier.takesFocus(route, ADD, target == ADD), onFocus = {
                    FocusMemory.remember(route, ADD); focused = ADD
                    Selection.current = Selected.Setting(Tx.s(R.string.library_folders_pick), Tx.s(R.string.library_folders_pick_value), Tx.s(R.string.library_folders_pick_help), Palette.Mint, Tx.s(R.string.library_folders_pick_action))
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
                PillButton(Tx.s(R.string.library_folders_rescan), Palette.Mint, Modifier.weight(1f).takesFocus(routeKey, "rescan", sheetTarget == "rescan"), fill = true, filled = true) {
                    open = null
                    Nav.focusTick++
                    Toasts.show(Tx.s(R.string.library_folders_looking))
                    rescan()
                }
                PillButton(Tx.s(R.string.library_folders_take_out), Palette.Peach, Modifier.weight(1f).takesFocus(routeKey, "remove", sheetTarget == "remove"), fill = true) {
                    open = null
                    remove(path)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                Tx.s(R.string.library_folders_take_out_note),
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
                Tx.s(if (treePath(uri) == internal) R.string.library_folders_roms_tree_internal
                else R.string.library_folders_roms_tree),
            )
            onAdded(romsPath)
            return@rememberLauncherForActivityResult
        }
        val path = treePath(uri)
        if (path == null) {
            Toasts.show(Tx.s(R.string.library_folder_unusable))
            Sounds.error()
            return@rememberLauncherForActivityResult
        }
        val name = LibraryRoots.label(path, internal)
        when (LibraryRoots.check(path, romsPath, SystemPrefs.data.roots)) {
            LibraryRoots.Verdict.IsRoms -> Toasts.show(Tx.s(R.string.library_folders_is_roms))
            LibraryRoots.Verdict.InsideRoms -> Toasts.show(Tx.s(R.string.library_folders_inside_roms, name))
            LibraryRoots.Verdict.AboveRoms -> Toasts.show(Tx.s(R.string.library_folders_above_roms, name))
            LibraryRoots.Verdict.Already -> Toasts.show(Tx.s(R.string.library_folders_already, name))
            LibraryRoots.Verdict.Ok -> {
                grant()
                SystemPrefs.update { it.copy(roots = it.roots + path, trees = it.trees + (path to uri.toString())) }
                Sounds.confirm()
                Toasts.show(Tx.s(R.string.library_folder_added, name))
                onAdded(path)
                scope.launch { reloadLibrary(ctx) }
                return@rememberLauncherForActivityResult
            }
        }
        Sounds.error()
    }
    return { runCatching { launcher.launch(start) }.onFailure { Toasts.show(Tx.s(R.string.library_folders_no_picker)) } }
}

/** One of a folder's systems: how many of its games are there, and its pack picture (null: Tandem draws one). */
private class FolderSystem(val platform: io.github.zahakj.tandem.Platform, val games: Int, val icon: File?)

/**
 * "1 game already in ROMs" when [path] holds copies of games another folder already gives (the same file name, see
 * LibraryRoots.shadowed), so a folder of copies doesn't look empty. Null with no copies, or in private mode, where
 * counting them could hint at hidden games.
 */
private fun copiesNote(path: String, internal: String): String? = copies(path, internal)?.let { (n, where) -> Tx.p(R.plurals.library_folders_copies, n, n, where) }

/** How many of [path]'s games are copies of ones another folder gives, and that folder's name ("other folders" for several). */
private fun copies(path: String, internal: String): Pair<Int, String>? {
    if (io.github.zahakj.tandem.Privacy.enabled) return null
    val s = Library.shadowed[path] ?: return null
    val romsPath = Library.romsDir.path
    val where = s.listedFrom.singleOrNull()?.let { if (it == romsPath) "ROMs" else LibraryRoots.label(it, internal) } ?: Tx.s(R.string.library_folders_other)
    return s.count to where
}

/** How many systems a folder card shows as pictures before "+n". */
private const val CARD_SYSTEMS = 5

/**
 * One game folder as a card: its name and where it is, then the systems found in it as their pictures (most games
 * first) and how many games it holds right now.
 */
@Composable
private fun FolderCard(name: String, detail: String, games: String, color: Color, systems: List<FolderSystem>, focus: Modifier, onFocus: () -> Unit, empty: String? = null, onClick: () -> Unit) {
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
                if (systems.isEmpty()) Text(empty ?: Tx.s(R.string.library_folders_no_games), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text(Tx.s(R.string.library_folders_pick_card), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Tx.s(R.string.library_folders_pick_card_detail), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Pill(folderCount(there, games, path, internal), if (there) Palette.Sky else Palette.Peach)
        Spacer(Modifier.height(10.dp))
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).fadingEdges(scroll).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                !there -> Text(
                    Tx.s(if (LibraryRoots.removable(path, internal)) R.string.library_folders_sd_out
                    else R.string.library_folders_gone),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
                bySystem.isEmpty() && copies(path, internal) != null -> Text(
                    copies(path, internal)!!.let { (n, where) ->
                        if (n == 1) Tx.s(R.string.library_folders_copy_one, where)
                        else Tx.p(R.plurals.library_folders_copy_many, n, n, where)
                    },
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
                bySystem.isEmpty() -> Text(
                    Tx.s(R.string.library_folders_none_here),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
                else -> {
                    for ((system, n) in bySystem) Row(Modifier.fillMaxWidth()) {
                        Text(system, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Tx.p(R.plurals.library_games, n), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, maxLines = 1)
                    }
                    copiesNote(path, internal)?.let { note ->
                        Text(Tx.s(R.string.library_folders_plus_copies, note), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                Tx.s(R.string.library_folders_art_note),
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
        Text(Tx.s(R.string.library_folders_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            if (extra == 0) Tx.p(R.plurals.library_folders_intro_roms, romsGames) else Tx.p(R.plurals.library_folders_intro_more, extra),
            style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
        )
    }
}

/** A folder's pill: how many games it holds, or that it isn't there, or that its games are all copies. */
private fun folderCount(there: Boolean, n: Int, path: String, internal: String): String = when {
    !there -> Tx.s(R.string.library_folders_not_there)
    n == 0 && copies(path, internal) != null -> Tx.s(R.string.library_folders_copies_only)
    else -> Tx.p(R.plurals.library_games, n)
}

/** Settings rows for this feature: the folder manager, and folder watching (FolderWatch.kt). */
@Composable
fun gameFoldersSettings(): SettingSection {
    val roots = SystemPrefs.data.roots
    val s = Store.settings
    return SettingSection(en = "Game folders",
        title = Tx.s(R.string.library_folders_title),
        rows = listOf(
            SettingRow(
                Tx.s(R.string.library_folders_title),
                if (roots.isEmpty()) Tx.s(R.string.library_folders_roms_only) else Tx.p(R.plurals.library_folders_roms_plus, roots.size),
                Palette.Mint, Glyph.Folder,
                help = Tx.s(R.string.library_folders_help),
                opens = true, keywords = "sd card storage external roots folders paths directory more games second tree pick games folder roms",
                en = "Game folders",
            ) { Nav.go(Route.GameFolders) },
            toggle(
                Tx.s(R.string.library_folders_watch), Glyph.Eye, Palette.Mint,
                Tx.s(R.string.library_folders_watch_help),
                s.watchFolders, en = "Watch game folders",
            ) { on -> Store.updateSettings { it.copy(watchFolders = on) } }
                .copy(keywords = "watch monitor automatic rescan refresh new games copied mtp usb inotify live"),
        ),
    )
}
