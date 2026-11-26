package io.github.zahakj.tandem

import android.os.Handler
import android.os.Looper
import io.github.zahakj.tandem.data.GameStatus
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.WeekBoard
import io.github.zahakj.tandem.data.WeekBoardResult
import io.github.zahakj.tandem.data.WeekGame
import io.github.zahakj.tandem.data.WeekPick
import io.github.zahakj.tandem.data.WeekSlot
import io.github.zahakj.tandem.data.WeekState
import io.github.zahakj.tandem.data.WeekStore
import io.github.zahakj.tandem.data.WidgetStore
import io.github.zahakj.tandem.ui.Palette
import java.time.ZoneId

/**
 * This week's board on the device: [WeekBoard] fed from the library, cached with a fingerprint of what it was built
 * from (the library, the games' details, what is hidden, the Up next list, This week's own file and the week), so
 * it is rebuilt on a new week, after a game (its play time changes the details), when games are hidden or added,
 * and otherwise read from memory. Hidden games never take part, revealed or not. Nothing here goes online.
 */
object ThisWeek {
    /** The collection's id (Collections.byId, routes, the icon grid's "col:week" tile). */
    const val ID = "week"

    val enabled: Boolean get() = WeekStore.state.on

    /** A built board and the inputs it is good for: the ones it was built from, and the same with what it showed noted. */
    private class Memo(val keys: List<List<Any?>>, val board: WeekBoardResult, val games: List<Game>) {
        fun fits(key: List<Any?>) = keys.any { same(it, key) }
    }

    /** Where This week's own state sits in a [Memo]'s key. */
    private const val STATE = 3

    @Volatile private var memo: Memo? = null
    private val main = Handler(Looper.getMainLooper())

    private fun zone(): ZoneId = ZoneId.systemDefault()

    /** This week's board: empty while the switch is off. Cheap after the first call of a week and library. */
    fun board(): WeekBoardResult = memo()?.board ?: WeekBoardResult.Empty

    /** The board's games, in its order (Up next, the pick of the week, then each row). */
    fun games(): List<Game> = memo()?.games.orEmpty()

    /** The pick [game] is on this week's board, or null. */
    fun pickOf(game: Game): WeekPick? = if (enabled) board().pickOf(game.key) else null

    private fun memo(): Memo? {
        val state = WeekStore.state
        if (!state.on) return null
        val now = System.currentTimeMillis()
        val key = listOf(Libraries.all, Store.meta, Privacy.view, state, WidgetStore.config.upNext, WeekBoard.weekOf(now, zone()))
        memo?.let { m -> if (m.fits(key)) return m }
        return synchronized(this) { memo?.takeIf { it.fits(key) } ?: build(key, state, now).also { memo = it } }
    }

    /** The library and the details are big: the same object or not. The rest are small values. */
    private fun same(a: List<Any?>, b: List<Any?>): Boolean =
        a[0] === b[0] && a[1] === b[1] && (2 until a.size).all { a[it] === b[it] || a[it] == b[it] }

    private fun build(key: List<Any?>, state: WeekState, now: Long): Memo {
        val all = Libraries.games
        val input = all.map(::weekGame)
        val upNext = WidgetStore.config.upNext.mapNotNull { it.key }
        val board = WeekBoard.build(input, now, zone(), state, upNext)
        val byKey = all.associateBy { it.key }
        val games = board.picks.mapNotNull { byKey[it.key] }
        // What showed this week sticks, and stays away for the next four: noted after this frame, never while composing.
        // Built from the state with it noted, the board is the same, so the cache is keyed as if it had been: noting
        // it doesn't build it again.
        val noted = WeekBoard.remember(state, board, now, zone())
        if (noted != state) main.post { WeekStore.update { WeekBoard.remember(it, board, now, zone()) } }
        return Memo(listOf(key, key.toMutableList().also { it[STATE] = noted }), board, games)
    }

    private fun weekGame(g: Game): WeekGame {
        val m = g.meta
        val ra = runCatching { RetroAchievements.cachedProgress(g) }.getOrNull()?.takeIf { it.total > 0 }
        val steam = if (ra == null) runCatching { SteamAchievements.cached(g) }.getOrNull()?.takeIf { !it.none } else null
        return WeekGame(
            key = g.key, title = g.title, platform = g.platform.id,
            // Games without genres count by the folder they sit in, as the Genres collection does.
            genres = m.genres.ifEmpty { listOfNotNull(g.group) },
            developer = m.developer, rating = m.rating, favorite = m.favorite,
            hidden = Privacy.isHidden(g),
            status = m.status, statusAt = m.statusAt, finishedAt = m.finishedAt,
            playSeconds = m.playSeconds, lastPlayed = m.lastPlayed, firstSeen = m.firstSeen,
            ttbMain = m.timeToBeat?.let { t -> t.main.takeIf { it > 0 } ?: t.extra } ?: 0,
            achEarned = ra?.earned ?: steam?.unlocked ?: -1, achTotal = ra?.total ?: steam?.total ?: 0,
        )
    }

    /** The "This week" collection, wherever collections show; null while switched off (or, unless [evenEmpty], empty). */
    fun collection(evenEmpty: Boolean = false): GameCollection? {
        if (!enabled) return null
        val games = games()
        if (games.isEmpty() && !evenEmpty) return null
        return GameCollection(ID, "This week", games, Palette.Lavender, R.string.week_title) // tx-ok: the name's English stand-in, shown from week_title
    }

    // ---------------------------------------------------------------------------------------------- feedback

    /** X on a row: draws it again, a little deeper down its ranking. */
    fun reroll(slot: WeekSlot) {
        val b = board()
        WeekStore.update { WeekBoard.reroll(it, b, slot) }
    }

    /** Y › Not for me: never suggested again (until Reset suggestions). */
    fun notForMe(g: Game) = WeekStore.update { WeekBoard.notForMe(it, g.key) }

    fun undoNotForMe(g: Game) = WeekStore.update { it.copy(notForMe = it.notForMe - g.key) }

    /**
     * Y › Played it elsewhere: Finished, without a play session (so no play time is made up), and the first finish
     * kept as Tandem keeps any. Returns the details before, for Undo.
     */
    fun playedElsewhere(g: Game): io.github.zahakj.tandem.data.GameMeta {
        val before = g.meta
        Store.update(g.key) { GameStatus.set(it, GameStatus.Finished, System.currentTimeMillis()) }
        return before
    }

    /** Settings › Reset suggestions: forgets not-for-me, what showed and the re-rolls. */
    fun reset() = WeekStore.update { WeekBoard.reset(it) }

    fun setOn(on: Boolean) = WeekStore.update { it.copy(on = on) }

    // ---------------------------------------------------------------------------------------------- Surprise me

    /** Board games Surprise me has landed on since Tandem started: each comes up once before the rest of the library. */
    private val offered = HashSet<String>()

    /**
     * What Select (Surprise me) lands on: a game from this week's board it hasn't offered yet, then a game you've
     * never played, then anything. Never a game said to be not for you, while anything else is left.
     */
    fun surprise(): Game? {
        val pool = Collections.visibleGames()
        if (enabled) {
            synchronized(offered) {
                games().filter { it.key !in offered && Privacy.canShow(it) }.randomOrNull()?.let { offered += it.key; return it }
            }
        }
        val no = WeekStore.state.notForMe
        val wanted = pool.filter { it.key !in no }.ifEmpty { pool }
        return wanted.filter { it.meta.playCount == 0 && it.meta.playSeconds == 0L }.ifEmpty { wanted }.randomOrNull()
    }
}
