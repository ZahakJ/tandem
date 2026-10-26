package io.github.zahakj.tandem

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Display

/**
 * Back to Tandem when the game ends. When the game Tandem started crashes or quits, Android shows whatever task was
 * underneath it: often Tandem, but sometimes an app opened earlier (a browser, Discord, Android's own launcher). The
 * companion ([CompanionActivity]) is up for the whole game, so while it is, it looks every few seconds (one Shizuku
 * `dumpsys`) at whether the game still has a task; once it is gone, Tandem comes forward ([GameCompanion.backToTandem])
 * and puts both screens back the way they were.
 *
 * Only when the game was the app in front the last time it looked: when the user switched to another app on purpose
 * (or went to Recents) while the game kept running, its later end leaves them where they are. Home is never in the
 * way: Tandem then comes back itself and closes the companion, which ends the watch.
 *
 * Whenever the game's task is gone (and it had one: an emulator still starting has none yet), the companion goes too,
 * even when Tandem isn't brought back: it would otherwise stay on the bottom screen for a game that no longer runs.
 *
 * [step] is plain, for tests (GameExitTest).
 */
object GameExit {
    const val INTERVAL_MS = 2_500L
    private const val SCREEN_OFF_INTERVAL_MS = 10_000L

    /**
     * What one look found: [seen] the game in front, [had] the game had a task at some look (both carried to the next
     * look), [back] time to bring Tandem back, [close] the game is gone and the companion should close without
     * bringing Tandem back.
     */
    data class Step(val seen: Boolean, val back: Boolean, val had: Boolean = false, val close: Boolean = false)

    /**
     * One look. [seen], [had]: from the last look. [tasks]: the tasks now (null when the dump didn't work: nothing
     * changes). [front]: the app in front on the main display, null if none. [frontIsGame]: [front] is an emulator or
     * game itself (one game handing over to another, which isn't an end).
     */
    fun step(
        seen: Boolean,
        game: String,
        tasks: Map<String, List<RunningTasks.Activity>>?,
        front: String?,
        own: String,
        frontIsGame: Boolean,
        had: Boolean = false,
    ): Step {
        tasks ?: return Step(seen, false, had)
        if (RunningTasks.hasTask(tasks, game)) {
            return when (front) {
                game -> Step(seen = true, back = false, had = true)
                // Nothing in front for a moment (a transition): as before.
                null -> Step(seen, false, had = true)
                // Another app in front, or Tandem: the user left the game on purpose.
                else -> Step(seen = false, back = false, had = true)
            }
        }
        val handover = front != null && frontIsGame
        val back = seen && front != own && !handover
        return Step(seen = false, back = back, had = had, close = had && !back && !handover)
    }

    /** Watches [game] for [companion]; [start] when it shows, [stop] when it goes. */
    class Watch(private val companion: CompanionActivity, private val game: String) {
        private val own = companion.packageName
        private val power = companion.getSystemService(android.os.PowerManager::class.java)
        private val main = Handler(Looper.getMainLooper())
        private var thread: HandlerThread? = null
        private var seen = false
        private var had = false

        fun start() {
            if (thread != null) return
            val t = HandlerThread("game-exit").also { it.start() }
            thread = t
            val handler = Handler(t.looper)
            val look = object : Runnable {
                override fun run() {
                    if (thread !== t) return
                    // Screen off: nobody can see what is in front, so no Shizuku call; look again once it is on.
                    if (!power.isInteractive) {
                        handler.postDelayed(this, SCREEN_OFF_INTERVAL_MS)
                        return
                    }
                    if (lookOnce()) return
                    handler.postDelayed(this, INTERVAL_MS)
                }
            }
            handler.postDelayed(look, INTERVAL_MS)
        }

        fun stop() {
            thread?.quitSafely()
            thread = null
        }

        /** True when the watch is over (Tandem is being brought back, or the companion closes). */
        private fun lookOnce(): Boolean {
            if (!Shell.ready()) return false
            val dump = Shell.run("dumpsys", "activity", "activities") ?: return false
            val tasks = RunningTasks.parse(dump)
            // Gone (quit or crashed), whether or not Tandem comes back for it: no more "Playing" on Discord, and back
            // online after a hidden game (Offline.kt).
            if (tasks != null && !RunningTasks.hasTask(tasks, game)) main.post { DiscordPresence.stopped(); Offline.gameEnded(companion) }
            val front = CompanionRestore.frontPackage(dump, Display.DEFAULT_DISPLAY)
            val r = step(seen, game, tasks, front, own, front != null && isGame(front), had)
            seen = r.seen
            had = r.had
            // Another app in front of the game on the main display: Home brings the game back first (HomeBack).
            if (tasks != null) HomeBack.gameAway = RunningTasks.hasTask(tasks, game) && front != null && front != game && front != own
            if (r.close) {
                Log.i("Tandem", "The game ($game) is gone with $front in front: closing its companion")
                main.post { if (!companion.isFinishing && !companion.isDestroyed) GameCompanion.closeFor(companion) }
                return true
            }
            if (!r.back) return false
            Log.i("Tandem", "The game ($game) ended with $front in front: back to Tandem")
            main.post {
                if (companion.isFinishing || companion.isDestroyed) return@post
                GameCompanion.backToTandem(companion)
            }
            return true
        }

        private fun isGame(pkg: String): Boolean =
            AndroidApps.isEmulator(pkg) || GameCompanion.usesBothScreens("", pkg) ||
                runCatching { pkg in RunningGames.emulatorPackages() }.getOrDefault(false)
    }
}
