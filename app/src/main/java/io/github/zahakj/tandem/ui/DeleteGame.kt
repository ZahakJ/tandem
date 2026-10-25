package io.github.zahakj.tandem.ui

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.AndroidApps
import io.github.zahakj.tandem.DeleteFile
import io.github.zahakj.tandem.DeletePlanner
import io.github.zahakj.tandem.FileChecks
import io.github.zahakj.tandem.FilePlan
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.PlatformLibrary
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.ExtrasStore
import io.github.zahakj.tandem.data.GameMeta
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.WidgetStore
import java.io.File

/*
 * Y › More… › Delete game…: removes a game from storage and from Tandem, after a popup that lists exactly what goes,
 * with sizes, and Delete held until its ring fills (HoldConfirm.kt). The file rules (what belongs to a game, what
 * is never touched) are in GameDelete.kt. Games whose real install lives in another app (PC games, Vita and PS3 shortcuts) only lose
 * Tandem's shortcut, and the popup says so; Android games are uninstalled through Android's own dialog.
 * Hidden games never show up by name in a toast or the log while private mode keeps them hidden.
 */

/** What the delete popup is about, worked out off the main thread before it shows sizes. */
class DeleteRequest(
    val game: Game,
    /** Null while the files are still being looked at. */
    val files: FilePlan? = null,
    val art: List<DeleteFile> = emptyList(),
    /** True when another game of the same name shares the art, so it stays. */
    val artShared: Boolean = false,
) {
    val isApp: Boolean get() = game.platform.isApps
}

object DeleteGame {
    private const val ROUTE = "deletegame"

    var request by mutableStateOf<DeleteRequest?>(null)
        private set
    var withArt by mutableStateOf(true)
    var keepStats by mutableStateOf(false)
    var busy by mutableStateOf(false)
        private set


    /** An Android game handed to the system uninstaller, checked when Tandem comes back. */
    private var uninstalling: Pair<Game, Boolean>? = null

    /** Extensions of small shortcut files whose game is installed in another app. */
    private val SHORTCUTS = setOf(
        "pcgame", "steam", "epic", "gog", "amazon", "customgame", "steamappid", "epicgame", "desktop", "lnk",
        "dpt", "psvita", "ps3", "ps3titleid",
    )

    /** What a toast may call [g]: its title, unless private mode is keeping it hidden. */
    fun nameOf(g: Game): String = if (Privacy.enabled && Privacy.isHidden(g)) "a hidden game" else g.title

    /** True when [g] is a stand-in for a game installed in another app (a file, not a folder game). */
    fun isShortcut(g: Game): Boolean =
        !g.platform.isApps && !g.file.isDirectory && (g.platform.id == "windows" || g.file.extension.lowercase() in SHORTCUTS)

    /** Where a shortcut's real game lives, for the popup. */
    fun installedIn(g: Game): String = when (g.platform.id) {
        "windows" -> "GameNative, Winlator or DroidDeck (Steam games)"
        else -> g.platform.playerFor(g)?.name ?: g.platform.allPlayers.firstOrNull()?.name ?: "its emulator"
    }

    /** From the game menu. Refuses a game that is running (offering to close it) or a hidden game that isn't showing. */
    fun ask(ctx: Context, g: Game) {
        Nav.closeMenus()
        if (!Privacy.canShow(g)) {
            Toasts.show("Show hidden games first to delete one"); return
        }
        val running = RunningGames.list.firstOrNull { it.game?.key == g.key }
        if (running != null) {
            Confirm.ask(
                "Close it first?",
                "${nameOf(g)} is running. Tandem can close it, then delete it. Progress you haven't saved is lost.",
                "Close game",
            ) {
                closeRunning(ctx, running) {
                    if (RunningGames.list.none { it.pkg == running.pkg }) ask(ctx, g)
                }
            }
            return
        }
        withArt = true
        keepStats = false
        FocusMemory.forget(ROUTE)
        FocusMemory.remember(ROUTE, "no")
        request = DeleteRequest(g)
        if (g.platform.isApps) return
        var planned: DeleteRequest? = null
        inBackground({ planned = plan(g) }) {
            if (request?.game?.key == g.key) planned?.let { request = it }
        }
    }

    fun close() {
        if (busy) return
        request = null
        Nav.focusTick++
    }

    // --- Working out what goes ----------------------------------------------------------------------------------

    private val mediaRoot: File get() = File(Library.romsDir, "downloaded_media")

    /** Every folder a game may be deleted from: the ROMs folder and each system's folders (some live elsewhere). */
    private fun roots(): List<File> =
        (listOf(Library.romsDir) + Libraries.all.orEmpty().flatMap { lib -> lib.platform.folders.map { Library.folder(it) } }).distinct()

    /** Folders that never go as a whole: the systems' folders and Tandem's own ones. */
    private fun guarded(): List<File> =
        Libraries.all.orEmpty().flatMap { lib -> lib.platform.folders.map { Library.folder(it) } } +
            listOf(mediaRoot, File(Library.romsDir, "tandem"))

    /** Blocking. */
    private fun plan(g: Game): DeleteRequest {
        val others = Libraries.games.filter { it.key != g.key }
        val files = DeletePlanner.gameFiles(g.file, roots(), guarded(), others.map { it.file })
        val artIds = g.platform.artIds.toSet()
        val shared = others.filter { o -> o.platform.artIds.any { it in artIds } }.mapTo(HashSet()) { it.stem.lowercase() }
        val artDirs = g.platform.artIds.map { File(mediaRoot, it) }
        val art = DeletePlanner.artFiles(g.stem, artDirs, mediaRoot, shared)
        return DeleteRequest(g, files, art, artShared = g.stem.lowercase() in shared)
    }

    // --- Doing it -----------------------------------------------------------------------------------------------

    /** Delete held until its ring filled. */
    fun run(ctx: Context) {
        val r = request ?: return
        if (busy || r.files?.ok != true) return
        val g = r.game
        val art = withArt
        val keep = keepStats
        busy = true
        var result: io.github.zahakj.tandem.DeleteResult? = null
        var refused: String? = null
        inBackground({
            // Looked at again right before deleting, in case anything changed while the popup was open.
            val fresh = plan(g)
            val files = fresh.files
            if (files == null || !files.ok) { refused = files?.refused ?: "Couldn't look at its files"; return@inBackground }
            val all = files.files + if (art) fresh.art else emptyList()
            result = DeletePlanner.delete(all, roots() + mediaRoot)
        }) {
            busy = false
            request = null
            refused?.let { Toasts.show("Nothing deleted. $it"); Nav.focusTick++; return@inBackground }
            val res = result ?: return@inBackground
            val name = nameOf(g)
            val hidden = Privacy.isHidden(g)
            forget(g, keep)
            if (!hidden) Log.i("Tandem", "Deleted ${g.key}: ${res.deleted} items, ${res.freed} bytes, ${res.failed.size} failed")
            val freed = Formatter.formatShortFileSize(ctx, res.freed)
            Toasts.show(
                if (res.failed.isEmpty()) "Deleted $name ($freed freed)"
                else "Deleted $name ($freed freed), but ${io.github.zahakj.tandem.plural(res.failed.size, "file")} couldn't be removed",
            )
            LibraryScan.request(ctx, announce = false, onlyIfChanged = false)
        }
    }

    /** Hands an Android game to Android's uninstall dialog; Tandem forgets it once the app is really gone. */
    fun uninstall(ctx: Context) {
        val r = request ?: return
        val pkg = AndroidApps.packageOf(r.game)
        uninstalling = r.game to keepStats
        request = null
        val ok = runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!ok) { uninstalling = null; Toasts.show("Android wouldn't open its uninstall screen"); Nav.focusTick++ }
    }

    /** Back in Tandem: an app sent to the uninstaller that is now gone is forgotten. */
    fun afterResume(ctx: Context) {
        val (g, keep) = uninstalling ?: return
        uninstalling = null
        val pkg = AndroidApps.packageOf(g)
        val gone = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.isFailure
        if (!gone) return
        val name = nameOf(g)
        forget(g, keep)
        Toasts.show("Uninstalled $name")
    }

    /**
     * Forgets [g] everywhere Tandem keeps it: details and favourite, Up next, the home grid, hidden-folder memory,
     * achievements match, Journal notes and play history (unless [keepStats]), and takes it out of the library in
     * memory so focus moves to its neighbour now; the rescan after confirms it.
     */
    private fun forget(g: Game, keepStats: Boolean) {
        val key = g.key
        val meta = g.meta
        if (keepStats && (meta.playCount > 0 || meta.playSeconds > 0 || meta.lastPlayed > 0)) {
            // Stats keep the title and the hidden mark, so a hidden game's old sessions stay out of sight.
            Store.update(key) { GameMeta(title = g.title, hidden = meta.hidden, playCount = meta.playCount, playSeconds = meta.playSeconds, lastPlayed = meta.lastPlayed, firstSeen = meta.firstSeen) }
        } else {
            Store.remove(key)
        }
        if (!keepStats) {
            HomeStore.forgetGame(key)
            ExtrasStore.updateNotes { n -> n.copy(games = n.games - key, sessions = n.sessions.filterKeys { !it.startsWith("$key@") }) }
        }
        if (HomeStore.ra.matches.containsKey(key)) HomeStore.updateRa { it.copy(matches = it.matches - key) }
        if (WidgetStore.config.upNext.any { it.key == key }) WidgetStore.update { c -> c.copy(upNext = c.upNext.filter { it.key != key }) }
        val tile = "game:$key"
        if (tile in IconGridStore.config.added || tile in IconGridStore.inFolders() || tile in IconGridStore.config.order) IconGridStore.remove(tile, default = false)
        if (Store.settings.hiddenFolders.any { key in it.known }) {
            Store.updateSettings { s -> s.copy(hiddenFolders = s.hiddenFolders.map { if (key in it.known) it.copy(known = it.known - key) else it }) }
        }
        Libraries.all = Libraries.all?.let { libs ->
            libs.mapNotNull { lib ->
                if (lib.platform.id != g.platform.id) lib
                else lib.games.filter { it.key != key }.takeIf { it.isNotEmpty() }?.let { PlatformLibrary(lib.platform, it, lib.art) }
            }
        }
        if ((Selection.current as? Selected.GameItem)?.game?.key == key) Selection.current = Selected.Home
        if (Nav.menuFor?.key == key) Nav.menuFor = null
        FileChecks.clear()
        Nav.focusTick++
    }
}

private fun size(ctx: Context, bytes: Long): String = Formatter.formatShortFileSize(ctx, bytes)

/** Rendered once in the app root, below the confirm popup. */
@Composable
fun DeleteGameOverlay() {
    val ctx = LocalContext.current
    val req = DeleteGame.request
    val shown = rememberLatest(req)
    InterceptButtons(req != null) { true }
    OverlayPanel(req != null, onClose = { DeleteGame.close() }) {
        val r = shown ?: return@OverlayPanel
        val g = r.game
        val files = r.files
        val ready = r.isApp || files != null
        val canDelete = r.isApp || files?.ok == true
        val hasArt = r.art.isNotEmpty()
        val keys = listOfNotNull("art".takeIf { hasArt && canDelete }, "stats".takeIf { canDelete }, "no", "yes".takeIf { canDelete && ready })
        val target = rememberFocusTarget("deletegame", keys)
        PanelSurface(Modifier.width(500.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pip(48.dp, mood = 2)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (r.isApp) "Uninstall ${g.title}?" else "Delete ${g.title}?",
                        style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(g.platform.name, files?.takeIf { it.ok }?.let { size(ctx, it.bytes + if (DeleteGame.withArt) r.art.sumOf { a -> a.bytes } else 0) + " freed" })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.padding(top = 6.dp))
            val scroll = rememberScrollState()
            Column(
                Modifier.weight(1f, fill = false).fadingEdges(scroll).verticalScroll(scroll).padding(vertical = FocusRoom / 2),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (r.isApp) {
                    Note(
                        "Android removes the app and everything it keeps, saves included, after asking you once more. " +
                            "Tandem then forgets it: favourite, Up next, home grid and hidden lists.",
                    )
                } else if (files == null) {
                    Note("Looking at its files…")
                } else if (!files.ok) {
                    Note(files.refused.orEmpty() + ". Nothing will be deleted.", Palette.Peach)
                } else {
                    Heading("Game files", size(ctx, files.bytes))
                    val root = Library.romsDir.parentFile ?: Library.romsDir
                    val names = files.files.map { it.file.relativeToOrNull(root)?.path ?: it.file.path }
                    names.take(5).forEach { Note(it, maxLines = 1) }
                    if (names.size > 5) Note("and ${names.size - 5} more", maxLines = 1)
                    if (DeleteGame.isShortcut(g)) {
                        Note(
                            "This is only Tandem's shortcut. The game itself is installed in ${DeleteGame.installedIn(g)}: " +
                                "uninstall it there to free its space.",
                            Palette.Butter,
                        )
                    }
                    if (files.kept.isNotEmpty()) Note("Kept, another game uses it: ${files.kept.joinToString { it.name }}", maxLines = 2)
                    if (hasArt) {
                        MenuRow(
                            "Downloaded art (${r.art.size})",
                            if (DeleteGame.withArt) "Delete · ${size(ctx, r.art.sumOf { it.bytes })}" else "Keep",
                            "art", "deletegame", target, Palette.Butter,
                        ) { DeleteGame.withArt = !DeleteGame.withArt }
                    } else {
                        Note(if (r.artShared) "Downloaded art stays: another game of the same name uses it." else "No downloaded art.")
                    }
                    Note("Saves stay in the emulator: Tandem doesn't know where each one keeps them.")
                }
                if (canDelete) {
                    Note("Tandem forgets the game: favourite, Up next, home grid, collections and hidden lists.")
                    MenuRow("Keep play stats", onOff(DeleteGame.keepStats), "stats", "deletegame", target, Palette.Lavender) {
                        DeleteGame.keepStats = !DeleteGame.keepStats
                    }
                }
            }
            if (canDelete) {
                Text(
                    if (r.isApp) "Android asks before it uninstalls." else "This can't be undone.",
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Peach, textAlign = TextAlign.Center,
                )
            }
            Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton(if (canDelete) "Cancel" else "OK", Palette.Mint, Modifier.takesFocus("deletegame", "no", target == "no")) { DeleteGame.close() }
                if (canDelete) {
                    val yes = Modifier.takesFocus("deletegame", "yes", target == "yes").graphicsLayer { alpha = if (ready) 1f else 0.35f }
                    // Android asks again before it uninstalls, so an app needs no hold; deleting files does.
                    if (r.isApp) PillButton("Uninstall app", Palette.Peach, yes) { DeleteGame.uninstall(ctx) }
                    else HoldButton(if (DeleteGame.busy) "Deleting…" else "Delete", Palette.Peach, yes, hint = holdHint("delete it")) {
                        if (ready && !DeleteGame.busy) DeleteGame.run(ctx)
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = TextInset / 2), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Pill(value, Palette.Peach)
    }
}

@Composable
private fun Note(text: String, color: androidx.compose.ui.graphics.Color = Palette.Muted, maxLines: Int = 3) {
    Text(
        text, Modifier.fillMaxWidth().padding(horizontal = TextInset / 2),
        style = MaterialTheme.typography.bodySmall, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}
