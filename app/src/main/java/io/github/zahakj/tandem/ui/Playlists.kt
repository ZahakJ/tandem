package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.runtime.Composable
import io.github.zahakj.tandem.Discs
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import java.io.File

/*
 * Settings › Library & art › Make playlists (.m3u): for the multi-disc games Tandem found by their discs' names
 * (Discs.kt), it writes the .m3u playlist emulators use to change discs, next to the discs. It moves and renames
 * nothing. It says first what it will write, and its Undo (or deleting the .m3u by hand) takes it back; each file
 * starts with "# Made by Tandem".
 */
object Playlists {
    /** A playlist to write: the game it is for and where it goes. */
    class Plan(val game: Game, val file: File)

    /** The disc sets without a playlist, in systems that take .m3u files (elsewhere it would be no game). */
    fun plans(games: List<Game> = Libraries.games): List<Plan> = games.mapNotNull { g ->
        if (g.discs.size < 2 || Discs.PLAYLIST !in g.platform.extensions) return@mapNotNull null
        val f = Discs.playlistFor(g) ?: return@mapNotNull null
        Plan(g, f).takeIf { !f.exists() }
    }.sortedBy { it.game.title.lowercase() }

    /** A asks, showing what it will write; yes writes them, with an Undo. */
    fun ask(ctx: Context) {
        val plans = plans()
        if (plans.isEmpty()) { Toasts.show(Tx.s(R.string.library_playlists_nothing)); return }
        // Hidden games are written too, but never named.
        val named = plans.filter { !io.github.zahakj.tandem.Privacy.isHidden(it.game) }
        val shown = named.take(3)
        val lines = shown.map { p ->
            Tx.s(R.string.library_playlists_line, p.game.title, Tx.p(R.plurals.library_playlists_discs, p.game.discs.size), p.file.name)
        } + listOfNotNull((plans.size - shown.size).takeIf { it > 0 }?.let { Tx.p(R.plurals.library_playlists_more, it) })
        Confirm.ask(
            Tx.p(R.plurals.library_playlists_ask, plans.size), lines.joinToString("\n"), Tx.s(R.string.library_playlists_yes),
        ) { write(ctx, plans) }
    }

    private fun write(ctx: Context, plans: List<Plan>) = Thread({
        val made = plans.mapNotNull { p ->
            runCatching {
                if (p.file.exists()) null
                else p.file.also { it.writeText(Discs.playlistText(it, p.game.discs)) }
            }.onFailure { android.util.Log.w("Tandem", "Couldn't write a playlist", it) }.getOrNull()
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (made.isEmpty()) { Toasts.show(Tx.s(R.string.library_playlists_failed)); return@post }
            LibraryScan.request(ctx, announce = false, onlyIfChanged = false)
            Undo.offer(Tx.p(R.plurals.library_playlists_done, made.size)) { undo(ctx, made) }
        }
    }, "tandem-playlists").start()

    /** Deletes the playlists [made], only while they still are Tandem's own (unchanged first line). */
    private fun undo(ctx: Context, made: List<File>) = Thread({
        for (f in made) runCatching { if (f.isFile && f.useLines { it.firstOrNull() } == Discs.MADE_BY) f.delete() }
        android.os.Handler(android.os.Looper.getMainLooper()).post { LibraryScan.request(ctx, announce = false, onlyIfChanged = false) }
    }, "tandem-playlists").start()
}

/** The Library page's row: how many multi-disc games have no playlist yet, and A to make them. */
@Composable
fun playlistsRow(ctx: Context): SettingRow {
    val count = androidx.compose.runtime.remember(Libraries.all) { Playlists.plans().size }
    return SettingRow(
        Tx.s(R.string.library_playlists_row),
        if (count == 0) Tx.s(R.string.library_playlists_none) else Tx.p(R.plurals.library_playlists_count, count),
        Palette.Sky, Glyph.Layers,
        help = Tx.s(R.string.library_playlists_help),
        keywords = "m3u playlist multi disc discs cd disk multidisc change disc swap psx ps1 ps2 saturn sega cd dreamcast tidy",
        en = "Make playlists",
    ) { Playlists.ask(ctx) }
}
