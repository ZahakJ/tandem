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
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
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
        val what = if (f.isDirectory) "the folder ${f.name} and everything in it" else f.name
        val size = if (f.isDirectory) FilesState.sizes[f.path] else f.length()
        Confirm.hold(
            "Delete ${f.name}?",
            "This deletes $what${size?.let { " (" + Formatter.formatShortFileSize(ctx, it) + ")" } ?: ""} from your storage. This can't be undone.",
            "Delete",
        ) {
            inBackground({ f.deleteRecursively() }) {
                FilesState.sizes.remove(f.path)
                FilesState.sizes.keys.filter { f.path.startsWith(it) }.forEach { FilesState.sizes.remove(it) }
                FilesState.version++
                Toasts.show("Deleted ${f.name}")
                LibraryScan.rescan(ctx)
            }
        }
        true
    }
    val crumb = dir.relativeToOrNull(Library.romsDir.parentFile ?: Library.romsDir)?.path ?: dir.path
    // Choosing a picture (an app's icon): A on a picture uses it; leaving Files gives up.
    val picking = ImagePick.request != null
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ImagePick.cancel() } }
    ToolScaffold(app, if (picking) ImagePick.request?.title else if (free >= 0) "${Formatter.formatShortFileSize(ctx, free)} free" else null, hints = {
        ButtonHint("A", if (picking) "Open · use ${ImagePick.request?.what ?: "picture"}" else "Open", Palette.Mint)
        ButtonHint("Y", "Delete", Palette.Peach)
        ButtonHint("B", if (FilesState.stack.isEmpty()) "Back" else "Up", Palette.Lavender)
    }) {
        Column(Modifier.fillMaxSize()) {
            Text(
                crumb.replace("/", "  ›  "), Modifier.padding(horizontal = FocusRoomSide, vertical = 6.dp),
                style = MaterialTheme.typography.titleSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            when {
                list == null -> PipNote("Looking…", "Reading ${dir.name}.", mood = 1)
                list.isEmpty() -> EmptyNote("Nothing in ${dir.name} yet", if (FilesState.stack.isEmpty()) "Go back" else "Up a folder", Modifier.takesFocus(route, "empty", true)) {
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
                                if (f.isDirectory) (size?.let { Formatter.formatShortFileSize(ctx, it) } ?: "Measuring…") + " · folder"
                                else f.extension.uppercase().ifEmpty { "File" },
                                color, Modifier.takesFocus(route, f.path, f.path == target), height = 56.dp,
                                tag = if (!f.isDirectory) Formatter.formatShortFileSize(ctx, f.length()) else null,
                                onFocus = { Selection.current = Selected.Group(f.name, if (f.isDirectory) "Folder" else "File", emptyList(), color) },
                                onClick = {
                                    if (f.isDirectory) { FilesState.stack.add(f); Nav.focusTick++ }
                                    else if (picking && ImagePick.accepts(f)) ImagePick.pick(f)
                                    else Toasts.show("${f.name} · ${Formatter.formatFileSize(ctx, f.length())}")
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
