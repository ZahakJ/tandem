package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.GameStatus
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.playStatus

/*
 * A game's status on screen: its colour and mark, the Status page of the Y menus, and setting it with Undo.
 */

val GameStatus.color: Color
    get() = when (this) {
        GameStatus.Backlog -> Palette.Sky
        GameStatus.Playing -> Palette.Mint
        GameStatus.Finished -> Palette.Butter
        GameStatus.Completed -> Palette.Peach
        GameStatus.Shelved -> Palette.Muted
        GameStatus.Endless -> Palette.Lavender
    }

/** A one-character mark for chips and tile badges. */
val GameStatus.mark: String
    get() = when (this) {
        GameStatus.Backlog -> "☰"
        GameStatus.Playing -> "▶"
        GameStatus.Finished -> "✓"
        GameStatus.Completed -> "★"
        GameStatus.Shelved -> "❚❚"
        GameStatus.Endless -> "∞"
    }

/** "✓ Finished". */
val GameStatus.chip: String get() = "$mark $label"

object StatusUi {
    const val PAGE = "Status"

    /** Opens the Status page of the Y menu at [routeKey] with the highlight on [g]'s status. */
    fun focusPage(routeKey: String, g: Game) = FocusMemory.remember("$routeKey:page:$PAGE", "st:" + (g.meta.playStatus?.id ?: "none"))

    /** The game whose Status page is open in the game menu (Overlays.kt), or null. */
    var pageFor by mutableStateOf<String?>(null)

    /** Sets [g]'s status to [to] (null: none), says so, and offers Undo. */
    fun set(g: Game, to: GameStatus?) {
        val before = g.meta
        if (before.playStatus == to) return
        Store.update(g.key) { GameStatus.set(it, to, System.currentTimeMillis()) }
        if (to?.beaten == true) Sounds.favorite() else Sounds.toggle(to != null)
        val said = when (to) {
            null -> Tx.s(R.string.screens_status_cleared)
            GameStatus.Finished -> Tx.s(R.string.screens_status_finished, g.title)
            GameStatus.Completed -> Tx.s(R.string.screens_status_completed, g.title)
            else -> Tx.s(R.string.screens_status_set, g.title, to.label)
        }
        // Undo puts back exactly what was there, the first-finished date included.
        Undo.offer(said) { Store.update(g.key) { it.copy(status = before.status, statusAt = before.statusAt, finishedAt = before.finishedAt) } }
        Trophies.check()
    }

    /** The Status row of a Y menu: the current status as its value; [open] shows the page. */
    fun action(g: Game, open: () -> Unit): MenuAction {
        val s = g.meta.playStatus
        return MenuAction(
            "status", Tx.s(R.string.screens_status), Glyph.Trophy, s?.color ?: Palette.Sky, value = s?.chip ?: Tx.s(R.string.screens_status_none),
            help = Tx.s(R.string.screens_status_help),
        ) { open() }
    }

    /** The Status page: every status, the current one ticked, and None. [done] closes the menu. */
    fun page(g: Game, onBack: () -> Unit, done: () -> Unit): MenuPage {
        val now = g.meta.playStatus
        val rows = GameStatus.entries.map { s ->
            MenuAction("st:${s.id}", s.label, color = s.color, value = if (s == now) "✓" else null, sub = s.blurb, art = { StatusMarkArt(s) }) { set(g, s); done() }
        } + MenuAction("st:none", Tx.s(R.string.screens_status_no_status), Glyph.Close, Palette.Lavender, value = if (now == null) "✓" else null) { set(g, null); done() }
        return MenuPage(PAGE, rows, onBack)
    }
}

/** A status's mark on a soft disc of its colour, filling the space it is given (menu rows, badges). */
@Composable
fun StatusMarkArt(s: GameStatus) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(s.color.copy(alpha = 0.95f), s.color.copy(alpha = 0.6f)))), contentAlignment = Alignment.Center) {
        Text(s.mark, style = MaterialTheme.typography.titleMedium, color = Palette.NightDeep, maxLines = 1)
    }
}

/**
 * Play time for a chip: "12 h of ~40 h" while a game with a known HowLongToBeat main story is under way, else just
 * the time ("12 h"); null under a minute.
 */
fun playProgress(m: io.github.zahakj.tandem.data.GameMeta): String? {
    if (m.playSeconds < 60) return null
    val s = m.playStatus
    val main = m.timeToBeat?.main?.takeIf { it > 0 && s != GameStatus.Endless && s?.beaten != true }
    val played = formatPlayTime(m.playSeconds)
    return main?.let { Tx.s(R.string.screens_status_progress, played, io.github.zahakj.tandem.data.TimeToBeat.hours(it)) } ?: played
}

/**
 * "🏆 23/60" from achievement progress already fetched for [g] (never fetches); null when there is none. A Steam
 * game counts its Steam achievements, every other its RetroAchievements: a game never has both.
 */
fun trophyChip(g: Game): String? {
    if (io.github.zahakj.tandem.Privacy.isHidden(g)) return null
    io.github.zahakj.tandem.SteamAchievements.cached(g)?.takeIf { it.total > 0 }?.let { return "🏆 ${it.unlocked}/${it.total}" }
    if (!io.github.zahakj.tandem.RetroAchievements.enabled) return null
    val p = io.github.zahakj.tandem.RetroAchievements.cachedProgress(g)?.takeIf { it.total > 0 } ?: return null
    return "🏆 ${p.earned}/${p.total}"
}
