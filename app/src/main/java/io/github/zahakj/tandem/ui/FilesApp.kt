package io.github.zahakj.tandem.ui

import android.os.StatFs
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.Tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Files: a simple browser for the ROMs folder. Folders first with their size (measured off the main thread, then
 * kept), files with theirs; A opens a folder, Y deletes the highlighted file or folder after a confirm, B goes up.
 * The ROMs folder itself can't be deleted. Deleting something rescans the library.
 */

object FilesState {
    /** Folders opened inside ROMs, deepest last. */
    val stack = mutableStateListOf<File>()
    /** Bumped after a delete, so listings and sizes are read again. */
    var version by mutableIntStateOf(0)
    val sizes = mutableStateMapOf<String, Long>()

    val current: File get() = stack.lastOrNull() ?: Library.romsDir
}

private fun systemColorFor(f: File): Color {
    val rel = f.relativeToOrNull(Library.romsDir)?.path?.substringBefore('/') ?: return Palette.Muted
    return Libraries.all?.firstOrNull { it.platform.folders.any { d -> d.equals(rel, true) } }?.let { SystemColors.of(it.platform.id) } ?: Palette.Lavender
}

@Composable
fun FilesScreen() {
    val ctx = LocalContext.current
    val app = BuiltInApps.byId("files")!!
    val dir = FilesState.current
    val route = "${Route.Tool("files").key}:${dir.path}"
    val entries by produceState<List<File>?>(null, dir, FilesState.version) {
        value = withContext(Dispatchers.IO) {
            dir.listFiles().orEmpty().filter { !it.name.startsWith(".") }.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }
    // Folder sizes, one at a time in the background, kept between visits.
    val list = entries
    androidx.compose.runtime.LaunchedEffect(list) {
        for (f in list.orEmpty().filter { it.isDirectory && it.path !in FilesState.sizes }) {
            val size = withContext(Dispatchers.IO) { runCatching { f.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L) }
            FilesState.sizes[f.path] = size
        }
    }
    val free = remember(FilesState.version) { runCatching { StatFs(Library.romsDir.path).availableBytes }.getOrDefault(-1L) }
    val keys = list.orEmpty().map { it.path }
    val target = rememberFocusTarget(route, keys)
    BackHandler(enabled = FilesState.stack.isNotEmpty() && toolActive("files")) {
        val left = FilesState.stack.removeAt(FilesState.stack.lastIndex)
        FocusMemory.remember("${Route.Tool("files").key}:${FilesState.current.path}", left.path)
        Nav.focusTick++
    }
    InterceptButtons(toolActive("files")) { b ->
        if (b != Button.Y) return@InterceptButtons false
        val f = list.orEmpty().firstOrNull { it.path == FocusMemory.get(route) } ?: return@InterceptButtons true
        val size = if (f.isDirectory) FilesState.sizes[f.path] else f.length()
        val sized = size?.let { Formatter.formatShortFileSize(Tx.context, it) }
        val detail = when {
            f.isDirectory && sized != null -> Tx.s(R.string.apps_files_delete_folder_size, f.name, sized)
            f.isDirectory -> Tx.s(R.string.apps_files_delete_folder, f.name)
            sized != null -> Tx.s(R.string.apps_files_delete_file_size, f.name, sized)
            else -> Tx.s(R.string.apps_files_delete_file, f.name)
        }
        Confirm.hold(Tx.s(R.string.apps_files_delete_ask, f.name), detail, Tx.s(R.string.apps_files_delete)) {
            inBackground({ f.deleteRecursively() }) {
                FilesState.sizes.remove(f.path)
                FilesState.sizes.keys.filter { f.path.startsWith(it) }.forEach { FilesState.sizes.remove(it) }
                FilesState.version++
                Toasts.show(Tx.s(R.string.apps_files_deleted, f.name))
                LibraryScan.rescan(ctx)
            }
        }
        true
    }
    val crumb = dir.relativeToOrNull(Library.romsDir.parentFile ?: Library.romsDir)?.path ?: dir.path
    // Choosing a picture (an app's icon): A on a picture uses it; leaving Files gives up.
    val picking = ImagePick.request != null
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ImagePick.cancel() } }
    ToolScaffold(app, if (picking) ImagePick.request?.title else if (free >= 0) freeText(free) else null, hints = {
        val pick = ImagePick.request
        ButtonHint("A", if (picking && pick != null) Tx.s(R.string.apps_files_open_use, pick.what) else Tx.s(R.string.apps_files_open), Palette.Mint)
        ButtonHint("Y", Tx.s(R.string.apps_files_delete), Palette.Peach)
        ButtonHint("B", if (FilesState.stack.isEmpty()) Tx.s(R.string.common_back) else Tx.s(R.string.apps_files_up), Palette.Lavender)
    }) {
        Column(Modifier.fillMaxSize()) {
            Text(
                crumb.replace("/", "  ›  "), Modifier.padding(horizontal = FocusRoomSide, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            when {
                list == null -> PipNote(Tx.s(R.string.apps_files_looking), Tx.s(R.string.apps_files_reading, dir.name), mood = 1)
                list.isEmpty() -> EmptyNote(Tx.s(R.string.apps_files_empty, dir.name), if (FilesState.stack.isEmpty()) Tx.s(R.string.apps_files_go_back) else Tx.s(R.string.apps_files_up_folder), Modifier.takesFocus(route, "empty", true)) {
                    if (FilesState.stack.isEmpty()) Nav.back() else {
                        val left = FilesState.stack.removeAt(FilesState.stack.lastIndex)
                        FocusMemory.remember("${Route.Tool("files").key}:${FilesState.current.path}", left.path)
                        Nav.focusTick++
                    }
                }
                else -> {
                    val state = rememberRouteListState(route)
                    KeepInView(state, keys.indexOf(target))
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f).fadingEdges(state), state = state,
                        contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom), verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(list, key = { it.path }) { f ->
                            val size = if (f.isDirectory) FilesState.sizes[f.path] else f.length()
                            val color = if (f.isDirectory) systemColorFor(f) else Palette.Sky
                            ToolRow(
                                f.name,
                                if (f.isDirectory) size?.let { Tx.s(R.string.apps_files_folder_size, Formatter.formatShortFileSize(Tx.context, it)) } ?: Tx.s(R.string.apps_files_folder_measuring)
                                else f.extension.uppercase().ifEmpty { Tx.s(R.string.apps_files_file) },
                                color, Modifier.takesFocus(route, f.path, f.path == target), height = 56.dp,
                                tag = if (!f.isDirectory) Formatter.formatShortFileSize(Tx.context, f.length()) else null,
                                onFocus = { Selection.current = Selected.Group(f.name, if (f.isDirectory) Tx.s(R.string.apps_files_folder) else Tx.s(R.string.apps_files_file), emptyList(), color) },
                                onClick = {
                                    if (f.isDirectory) { FilesState.stack.add(f); Nav.focusTick++ }
                                    else if (picking && ImagePick.accepts(f)) ImagePick.pick(f)
                                    else Toasts.show(Tx.s(R.string.apps_files_said, f.name, Formatter.formatFileSize(Tx.context, f.length())))
                                },
                            ) {
                                TandemIconTile(if (f.isDirectory) TIcon.Files else TIcon.Diamond, color)
                            }
                        }
                    }
                }
            }
        }
    }
}
