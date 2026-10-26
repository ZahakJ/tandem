package io.github.zahakj.tandem

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Home while a game runs under another app: the game comes back first. A game is playing (its companion is up and
 * its play session open) and another app covers it: on the bottom screen over the companion (Discord, a browser),
 * or on the top screen over the game. The first Home then brings the game's companion back on the bottom screen and
 * the game back in front on its own screen (by its task, [GameResume]), so the game keeps the controller. A second
 * Home within [SECOND_PRESS_MS] goes to Tandem as always. With no game running, or the game in front, Home is Home.
 *
 * Tandem's library activity is already on its way to the front when the Home intent arrives; [MainActivity] then
 * leaves its return work (play session, bottom screen, focus checks) until it knows: it is stopped again at once when
 * the game comes back, and if the game can't be found (no Shizuku, it just closed) the return happens as usual.
 *
 * [decide] is plain, for tests (HomeBackTest).
 */
object HomeBack {
    const val SECOND_PRESS_MS = 3_000L
    /** The companion was covered at least this long before the press: not by the Home press itself. */
    const val SETTLED_MS = 800L

    /** When the companion stopped under another window (elapsedRealtime), 0 while it shows. */
    @Volatile var coveredAt = 0L
    /** The last look (GameExit) found another app in front of the game on the main display. */
    @Volatile var gameAway = false
    @Volatile private var lastAt = -SECOND_PRESS_MS

    /**
     * Whether Home should bring the game back. [coveredForMs]: how long the companion has been covered (null: it
     * shows). [sinceLastMs]: since Home last brought a game back.
     */
    fun decide(companionUp: Boolean, sessionOpen: Boolean, coveredForMs: Long?, gameAway: Boolean, sinceLastMs: Long): Boolean =
        companionUp && sessionOpen && sinceLastMs >= SECOND_PRESS_MS &&
            ((coveredForMs != null && coveredForMs >= SETTLED_MS) || gameAway)

    /** Home brought a game back less than [SECOND_PRESS_MS] ago: the next Home goes to Tandem. */
    fun justBack(): Boolean = SystemClock.elapsedRealtime() - lastAt < SECOND_PRESS_MS

    /** Whether [owner], the app holding the focus on the companion's screen, covers it: another app, not the game. */
    fun coveredBy(owner: String?, own: String, game: String): Boolean = FocusGuard.isForeign(owner, own) && owner != game

    /**
     * A Home press bringing Tandem back from [a]'s background, or landing on the bottom-screen home (SecondaryHome).
     * True when the game is being brought back instead (on a background thread); [failed] then runs on the main thread
     * if it couldn't be. The companion's own record of being covered can miss an app opened before it came up (a
     * resume under Discord), so without it the bottom screen's focus is looked at too.
     */
    fun tryBack(a: Context, fromBottom: Boolean = false, failed: () -> Unit): Boolean {
        val now = SystemClock.elapsedRealtime()
        val pkg = GameCompanion.gamePackage() ?: return false
        val covered = coveredAt.takeIf { it > 0 }?.let { now - it }
        // The companion being up for a game is enough: its play session ends whenever Tandem's home is visited, while
        // the game runs on in the emulator. Whether the game is really still there is checked below (its task).
        val open = Sessions.openKey(a) != null || GameCompanion.up()
        // Home landing on the bottom screen means an app had it: the companion never takes the focus. Checking the
        // bottom screen's focus now would find Tandem's own bottom-screen home, which that Home press just opened.
        val sure = decide(true, open, covered, gameAway, now - lastAt) || (fromBottom && open && now - lastAt >= SECOND_PRESS_MS)
        val maybe = !sure && open && now - lastAt >= SECOND_PRESS_MS && Shell.ready()
        if (!sure && !maybe) return false
        lastAt = now
        // Pending dismissals and the focus checks leave the companion alone: it is coming back.
        GameCompanion.keepForGame()
        val app = a.applicationContext
        Thread({
            val back = sure || run {
                val bottom = LaunchDisplay.secondary(app)?.displayId
                val owner = FocusGuard.parse(Shell.run("sh", "-c", FocusGuard.COMMAND))?.byDisplay?.get(bottom)
                coveredBy(owner, app.packageName, pkg)
            }
            val target = if (back) GameResume.target(Processes.snapshot(maxAgeMs = 0)?.tasks, pkg) else null
            // The companion first, then the game, which ends up with the focus (the companion never takes it).
            val ok = target != null && GameCompanion.toFront(app) && GameResume.bringBack(app, target)
            Log.i("Tandem", "Home with $pkg " + if (ok) "covered: back to the game" else "running: to Tandem")
            if (ok) gameAway = false else { if (!back) lastAt = -SECOND_PRESS_MS; Handler(Looper.getMainLooper()).post(failed) }
        }, "tandem-home-back").start()
        return true
    }
}
