package io.github.zahakj.tandem

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.HomeStore

/** An emulator that is running right now, and the Tandem game last started in it (null if it was opened elsewhere). */
data class RunningGame(val pkg: String, val appName: String, val game: Game?, val since: Long)

/**
 * What runs right now, from one Shizuku call: process names (`ps`) and the activity tasks (`dumpsys activity
 * activities`, see [RunningTasks]). Asking costs a little Shizuku work, so answers are reused for a few seconds, and
 * nothing here ever runs on its own: callers ask only while Tandem is in front, never while a game is.
 *
 * A process alone doesn't mean a game is open (Android keeps processes cached after their task is gone), so:
 * - [alive]: the process exists. Enough to force-stop it quietly, never to call it a running game.
 * - [open]: alive, with a task holding an activity that isn't finishing. An Android app that is "running".
 * - [playing]: open, with a game in it rather than the emulator's menu. A running game.
 */
object Processes {
    private const val FRESH_MS = 4000L
    private const val SPLIT = "--tandem-tasks--"

    /** One look. [tasks] is null when the dump didn't work; then only processes are known. */
    class Snapshot(val names: Set<String>, val tasks: Map<String, List<RunningTasks.Activity>>?) {
        fun alive(pkg: String): Boolean = has(names, pkg)
        /** Without the dump, as before: the process is all there is to go on. */
        fun open(pkg: String): Boolean = alive(pkg) && (tasks?.let { RunningTasks.hasTask(it, pkg) } ?: true)
        fun playing(pkg: String): Boolean = alive(pkg) && (tasks?.let { RunningTasks.inGame(it, pkg) } ?: true)
    }

    private var cached: Snapshot? = null
    private var cachedAt = 0L

    /** What runs, or null when Shizuku can't tell. Blocking: call off the main thread when you can. */
    @Synchronized
    fun snapshot(maxAgeMs: Long = FRESH_MS): Snapshot? {
        val now = SystemClock.elapsedRealtime()
        cached?.let { if (now - cachedAt <= maxAgeMs) return it }
        val out = Shell.run("sh", "-c", "ps -A -o NAME; echo $SPLIT; dumpsys activity activities") ?: return null.also { cached = null }
        val names = out.substringBefore(SPLIT).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
        if (names.isEmpty()) return null.also { cached = null }
        val dump = if (SPLIT in out) out.substringAfter(SPLIT) else null
        return Snapshot(names, RunningTasks.parse(dump)).also { cached = it; cachedAt = now }
    }

    /** Process names, or null when Shizuku can't tell. Blocking. */
    fun names(maxAgeMs: Long = FRESH_MS): Set<String>? = snapshot(maxAgeMs)?.names

    @Synchronized
    fun forget() { cached = null }

    /** Also matches extra processes such as "pkg:EmulationProcess". */
    fun has(names: Set<String>, pkg: String): Boolean = pkg in names || names.any { it.startsWith("$pkg:") }

    /** The process exists (maybe only cached). Null when Shizuku can't tell. */
    fun alive(pkg: String, maxAgeMs: Long = FRESH_MS): Boolean? = snapshot(maxAgeMs)?.alive(pkg)

    /** [pkg] is open in a task (see [Snapshot.open]). Null when Shizuku can't tell. */
    fun open(pkg: String, maxAgeMs: Long = FRESH_MS): Boolean? = snapshot(maxAgeMs)?.open(pkg)

    /** [pkg] has a game open (see [Snapshot.playing]). Null when Shizuku can't tell. */
    fun playing(pkg: String, maxAgeMs: Long = FRESH_MS): Boolean? = snapshot(maxAgeMs)?.playing(pkg)
}

/**
 * Running games and emulators, for the game switcher and Now Playing. Built from what Shizuku sees (an emulator with
 * a game open, [Processes.Snapshot.playing]) and the record of which game Tandem last started in each emulator.
 */
object RunningGames {
    private const val PREFS = "running_since"

    var list by mutableStateOf<List<RunningGame>>(emptyList())
        private set
    /** False when Shizuku isn't available, so Tandem can't see other apps. */
    var canSee by mutableStateOf(false)
        private set

    /** Every emulator package Tandem knows, from the systems in the library. */
    fun emulatorPackages(): Set<String> =
        Libraries.all.orEmpty().flatMap { it.platform.players }.map { it.component.substringBefore('/') }.toSet()

    /** Updates [list]. Blocking: call from a background thread, and only while Tandem is in front. */
    fun refresh(ctx: Context, maxAgeMs: Long = 4000L) {
        val snap = Processes.snapshot(maxAgeMs)
        if (snap == null) {
            canSee = false
            list = emptyList()
            return
        }
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val running = emulatorPackages().filter { snap.playing(it) }
        val byPath = if (running.isEmpty()) emptyMap() else Libraries.games.associateBy { it.file.path }
        list = running.map { pkg ->
            RunningGame(pkg, appLabel(ctx, pkg), lastGame(ctx, pkg, byPath), prefs.getLong(pkg, 0))
        }.sortedByDescending { it.since }
        canSee = true
    }

    /**
     * The Tandem game last started in [pkg], if it is still in the library and its file still exists: a game since
     * deleted (or a library entry left behind for one) is never named or pictured.
     */
    private fun lastGame(ctx: Context, pkg: String, byPath: Map<String, Game>): Game? =
        Launcher.lastGamePath(ctx, pkg)?.let(byPath::get)?.takeIf { it.file.exists() }

    /** Remembers when a game was started fresh in [pkg], for "played this session". */
    fun onStarted(ctx: Context, pkg: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(pkg, System.currentTimeMillis()).apply()
        Processes.forget()
    }

    /** The "close the previous game" setting; it only does anything when Shizuku is available. */
    fun closePreviousOn(): Boolean = HomeStore.settings.closePrevious ?: true

    /** The "ask before closing a game" setting: Tandem asks before a launch would close a running game. */
    fun askBeforeClosingOn(): Boolean = HomeStore.settings.askBeforeClosing

    /**
     * The running games that starting the game at [path] in [pkg] would close (see [CloseCheck]), or null when Tandem
     * can't see other apps. Only games really open count ([Processes.Snapshot.playing]); an emulator whose process is
     * merely cached is closed quietly by the launch, without asking. When Android's task list can't be read, nothing
     * is asked, rather than a false alarm. Blocking (asks Shizuku): call off the main thread.
     */
    fun wouldClose(ctx: Context, pkg: String, path: String, anyApp: Boolean): List<RunningGame>? {
        // As fresh as the answer the launch itself will use.
        val snap = Processes.snapshot(maxAgeMs = 1500) ?: return null
        if (snap.tasks == null) return emptyList()
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val slots = (emulatorPackages() + pkg).filter { snap.playing(it) }
            .sortedByDescending { prefs.getLong(it, 0) }
            .map { CloseCheck.Slot(it, Launcher.lastGamePath(ctx, it)) }
        val closing = CloseCheck.wouldClose(slots, pkg, path, anyApp, closePreviousOn())
        if (closing.isEmpty()) return emptyList()
        val byPath = Libraries.games.associateBy { it.file.path }
        return closing.map { RunningGame(it.pkg, appLabel(ctx, it.pkg), lastGame(ctx, it.pkg, byPath), prefs.getLong(it.pkg, 0)) }
    }

    /**
     * Closes every other emulator Tandem knows about, so only the game being started runs. With this on, helper apps
     * that close the previous game aren't needed. An emulator whose process is only cached (no game open) is closed
     * too, quietly: it was never asked about.
     */
    fun closeOthers(ctx: Context, keep: String) {
        // The launcher has just listed processes; that answer is fresh enough.
        val names = Processes.names(maxAgeMs = 1500) ?: return
        for (pkg in emulatorPackages() - keep) {
            if (Processes.has(names, pkg)) forceStop(ctx, pkg)
        }
    }

    /** Force-stops [pkg]. Blocking. True when Shizuku ran the command. */
    fun close(ctx: Context, pkg: String): Boolean {
        val ok = forceStop(ctx, pkg)
        if (ok) list = list.filter { it.pkg != pkg }
        return ok
    }

    fun closeAll(ctx: Context): Int = list.count { close(ctx, it.pkg) }

    private fun forceStop(ctx: Context, pkg: String): Boolean {
        val ok = Shell.run("am", "force-stop", pkg) != null
        if (ok) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(pkg).apply()
            Log.i("Tandem", "Closed $pkg")
        }
        Processes.forget()
        return ok
    }

    /** Brings a running game back. Games Tandem started go through the launcher, which resumes them. */
    fun resume(ctx: Context, r: RunningGame) {
        val game = r.game
        if (game != null) {
            Launcher.launch(ctx, game)
            return
        }
        val front = ctx.packageManager.getLaunchIntentForPackage(r.pkg) ?: return
        front.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        front.setPackage(null)
        SecondaryHome.beforeLeave(ctx, r.pkg, game = true)
        runCatching { ctx.startActivity(front) }
    }

    /** Seconds played since the game was started this time, from the play history; null if unknown. */
    fun sessionSeconds(r: RunningGame): Long? {
        val game = r.game ?: return null
        if (r.since <= 0) return null
        return HomeStore.sessions.asReversed().asSequence()
            .takeWhile { it.end >= r.since }
            .filter { it.k == game.key && it.s >= r.since - 60_000 }
            .sumOf { it.d }
    }

    /** What an app is called on screen: the name the user gave it, else its own. */
    fun appLabel(ctx: Context, pkg: String): String = io.github.zahakj.tandem.ui.Dock.builtIn(pkg)?.title ?: io.github.zahakj.tandem.data.AppPrefs.name(pkg) ?: runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))
}
