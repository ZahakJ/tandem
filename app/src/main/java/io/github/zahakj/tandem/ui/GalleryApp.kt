package io.github.zahakj.tandem.ui

import android.os.Environment
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Button
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Gallery: screenshots from the ROMs folder, emulators' own screenshot folders and Android's, grouped by the game
 * each one belongs to (matched by file or folder name). A grid of thumbnails, a full-screen viewer that steps with
 * the D-pad, and delete with a confirm.
 */

/** One screenshot. */
class Shot(val file: File, val time: Long, val source: String, val game: Game?)

object Gallery {
    /** Where screenshots usually land, relative to the shared storage root. Missing folders are skipped. */
    private val FOLDERS = listOf(
        "ROMs/screenshots" to "Tandem", "RetroArch/screenshots" to "RetroArch", "Pictures/Screenshots" to "Android",
        "DCIM/Screenshots" to "Android", "Pictures/RetroArch" to "RetroArch", "PSP/SCREENSHOT" to "PPSSPP",
        "citra-emu/screenshots" to "Citra", "lime3ds-emu/screenshots" to "Lime3DS", "Azahar/screenshots" to "Azahar",
        "duckstation/screenshots" to "DuckStation", "AetherSX2/snaps" to "AetherSX2", "NetherSX2/snaps" to "NetherSX2",
        "dolphin-mmjr/ScreenShots" to "Dolphin", "Dolphin/ScreenShots" to "Dolphin", "mupen64plus/screenshots" to "Mupen64",
        "Pictures/Dolphin" to "Dolphin", "Pictures/Eden" to "Eden", "Pictures/yuzu" to "Yuzu", "Pictures/Citron" to "Citron",
        "Pictures/Winlator" to "Winlator", "melonDS/screenshots" to "melonDS", "Pictures/Vita3K" to "Vita3K",
    )
    private val IMAGE = setOf("png", "jpg", "jpeg", "webp")
    private const val MAX = 3000

    /** Scans every folder (blocking: call off the main thread). Newest first. */
    fun scan(): List<Shot> {
        val root = Environment.getExternalStorageDirectory()
        val out = ArrayList<Pair<File, String>>()
        val seen = HashSet<String>()
        for ((rel, label) in FOLDERS) {
            val dir = File(root, rel)
            if (!dir.isDirectory) continue
            dir.walkTopDown().maxDepth(3).filter { it.isFile && it.extension.lowercase() in IMAGE }.forEach { f ->
                if (out.size < MAX && seen.add(f.canonicalPath)) out += f to label
            }
        }
        // Matched against every game, so screenshots of hidden games can be left out too (unless they are showing).
        val matcher = Matcher(Libraries.games)
        return out.map { (f, label) -> Shot(f, f.lastModified(), label, matcher.match(f)) }
            .filter { it.game == null || io.github.zahakj.tandem.Privacy.canShow(it.game) }.sortedByDescending { it.time }
    }

    /** Matches a screenshot's name (or its folder's) to a game: the longest game title or file name it starts with. */
    private class Matcher(games: List<Game>) {
        private val names: List<Pair<String, Game>> = games.flatMap { g -> listOf(normalize(g.title) to g, normalize(g.stem) to g) }
            .filter { it.first.length >= 4 }.sortedByDescending { it.first.length }

        fun match(f: File): Game? {
            val stem = normalize(f.nameWithoutExtension)
            val parent = normalize(f.parentFile?.name.orEmpty())
            for ((n, g) in names) if (stem.startsWith(n) || parent == n) return g
            return null
        }
    }

    var version by mutableIntStateOf(0)
}

@Composable
fun GalleryScreen() {
    val app = BuiltInApps.byId("gallery")!!
    val route = Route.Tool("gallery").key
    val shots by produceState<List<Shot>?>(null, Gallery.version, Libraries.all, io.github.zahakj.tandem.Privacy.view) { value = withContext(Dispatchers.IO) { Gallery.scan() } }
    var group by remember { mutableStateOf("all") }
    var viewing by remember { mutableStateOf<Int?>(null) }
    val list = shots
    val groups = remember(list) {
        val byGame = list.orEmpty().filter { it.game != null }.groupBy { it.game!!.key }
        listOf("all" to "All · ${list.orEmpty().size}") +
            byGame.entries.sortedByDescending { it.value.size }.take(10).map { (k, v) -> "g:$k" to "${v.first().game!!.title} · ${v.size}" } +
            listOfNotNull(("other" to "Other").takeIf { list.orEmpty().any { it.game == null } })
    }
    val shown = remember(list, group) {
        when {
            group == "all" -> list.orEmpty()
            group == "other" -> list.orEmpty().filter { it.game == null }
            else -> list.orEmpty().filter { it.game?.key == group.removePrefix("g:") }
        }
    }
    val keys = groups.map { "chip:${it.first}" } + shown.map { it.file.path }
    val target = rememberFocusTarget(route, keys)
    InterceptButtons(toolActive("gallery") && viewing == null) { b ->
        val at = FocusMemory.get(route)
        val i = shown.indexOfFirst { it.file.path == at }
        if (b == Button.Y && i >= 0) { deleteShot(shown[i]); true } else false
    }
    // Choosing a picture (an app's icon): A uses the screenshot; leaving the Gallery gives up.
    val picking = ImagePick.request != null
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ImagePick.cancel() } }
    ToolScaffold(app, if (picking) ImagePick.request?.title else list?.let { plural(it.size, "screenshot") }, hints = {
        ButtonHint("A", if (picking) "Use picture" else "View", Palette.Mint)
        ButtonHint("Y", "Delete", Palette.Peach)
        ButtonHint("B", "Back", Palette.Lavender)
    }) {
        when {
            list == null -> PipNote("Looking for screenshots…", "Checking ROMs/\u2060screenshots, RetroArch, Pictures and DCIM.", mood = 1, modifier = Modifier.align(Alignment.Center))
            list.isEmpty() -> EmptyNote(
                "No screenshots yet: take one in your emulator, or put pictures in ROMs/\u2060screenshots", "Look again",
                Modifier.takesFocus(route, "empty", true), Modifier.align(Alignment.Center),
            ) { Gallery.version++ }
            else -> Column(Modifier.fillMaxSize()) {
                ToolChips(route, groups, group, target, app.color) { group = it }
                val state = rememberRouteGridState("$route:$group")
                val idx = shown.indexOfFirst { it.file.path == target }
                if (idx >= 0) KeepInView(state, idx)
                LazyVerticalGrid(
                    GridCells.Adaptive(150.dp), Modifier.fillMaxWidth().weight(1f).fadingEdges(state), state = state,
                    contentPadding = PaddingValues(horizontal = FocusRoomSide, vertical = FocusRoom),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(shown, key = { _, s -> s.file.path }) { i, s ->
                        FocusCard(
                            Modifier.aspectRatio(16f / 10f).takesFocus(route, s.file.path, s.file.path == target), glow = app.color,
                            shape = RoundedCornerShape(14.dp),
                            onFocus = { Selection.current = s.game?.let { Selected.GameItem(it) } ?: Selected.Group(s.source, "Screenshot", emptyList(), app.color) },
                            entry = true,
                            onClick = { if (picking) ImagePick.pick(s.file) else viewing = i },
                        ) {
                            AsyncImage(rememberSizedRequest(s.file, 320), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Text(
                                s.game?.title ?: s.source, Modifier.align(Alignment.BottomStart).padding(6.dp).darkChip().padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    ShotViewer(shown, viewing, onIndex = { viewing = it }, onClose = { viewing?.let { shown.getOrNull(it) }?.let { FocusMemory.remember(route, it.file.path) }; viewing = null; Nav.focusTick++ })
}

private fun deleteShot(s: Shot, after: () -> Unit = {}) {
    Confirm.hold("Delete this screenshot?", "${s.file.name} will be deleted from ${s.source}. This can't be undone.", "Delete") {
        if (s.file.delete()) { Toasts.show("Screenshot deleted"); Gallery.version++; after() } else Toasts.show("Couldn't delete it")
    }
}

/** Full-screen viewer: left and right step, Y deletes, B closes. */
@Composable
private fun ShotViewer(shots: List<Shot>, index: Int?, onIndex: (Int) -> Unit, onClose: () -> Unit) {
    val open = index != null && shots.isNotEmpty()
    val shown = rememberLatest(index)
    PanelGuard(open)
    InterceptButtons(open) { b ->
        if (b == Button.Y) shots.getOrNull(index ?: -1)?.let { deleteShot(it) { onClose() } }
        true
    }
    OverlayPanel(open, onClose = onClose) {
        val i = (shown ?: 0).coerceIn(0, (shots.size - 1).coerceAtLeast(0))
        val s = shots.getOrNull(i) ?: return@OverlayPanel
        FocusCard(
            Modifier.fillMaxSize().takesFocus("shotviewer", "img", true).onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key == Key.DirectionLeft || e.key == Key.DirectionRight
                when (e.key) {
                    Key.DirectionLeft -> { if (i > 0) { onIndex(i - 1); Sounds.move() }; true }
                    Key.DirectionRight -> { if (i < shots.size - 1) { onIndex(i + 1); Sounds.move() }; true }
                    else -> false
                }
            },
            glow = Palette.Text, shape = RoundedCornerShape(24.dp), onClick = {},
        ) {
            Box(Modifier.fillMaxSize().background(Palette.NightDeep)) {
                AsyncImage(rememberSizedRequest(s.file, 1600), null, Modifier.fillMaxSize().riseIn(s.file.path, 0, 10.dp, 0.97f), contentScale = ContentScale.Fit)
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(12.dp).darkChip().padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        listOfNotNull(s.game?.title, s.source, DateUtils.getRelativeTimeSpanString(s.time).toString()).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("${i + 1} / ${shots.size}", style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1)
                    ButtonHint("◀▶", "", Palette.Sky)
                    ButtonHint("Y", "Delete", Palette.Peach)
                }
            }
        }
    }
}
