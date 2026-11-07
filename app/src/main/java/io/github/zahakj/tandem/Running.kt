package io.github.zahakj.tandem

import android.content.Context
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

    /**
     * One look. [tasks] is null when the dump didn't work; then only processes are known. [started]: when each
     * process started (wall clock, to the second), for the ones `ps` gave a usable age.
     */
    class Snapshot(
        val names: Set<String>,
        val tasks: Map<String, List<RunningTasks.Activity>>?,
        val started: Map<String, Long> = emptyMap(),
    ) {
        fun alive(pkg: String): Boolean = has(names, pkg)
        /** Without the dump, as before: the process is all there is to go on. */
        fun open(pkg: String): Boolean = alive(pkg) && (tasks?.let { RunningTasks.hasTask(it, pkg) } ?: true)
        fun playing(pkg: String): Boolean = alive(pkg) && (tasks?.let { RunningTasks.inGame(it, pkg) } ?: true)
        /** [pkg]'s process started well after [at] (a launch time): whatever Tandem started then is gone. */
        fun restartedSince(pkg: String, at: Long): Boolean = restartedSince(started[pkg], at)

        /**
         * A Windows game (Wine shows its processes as "A:\Game\game.exe") started well after [at]: the PC app that
         * Tandem started a game in has since been used to open another one, in the same process.
         */
        fun newWindowsGameSince(at: Long): Boolean = started.any { (name, t) ->
            windowsGame.matches(name) && !name.startsWith("C:\\windows\\", ignoreCase = true) && t > at + EXE_SLACK_MS
        }
    }

    /** Slack between Tandem starting a game and its emulator's process coming up. */
    private const val START_SLACK_MS = 30_000L
    /** Wine and a game's own launcher can take a while before the game itself starts. */
    private const val EXE_SLACK_MS = 3 * 60_000L
    private val windowsGame = Regex("""^[A-Za-z]:\\.+\.exe$""", RegexOption.IGNORE_CASE)

    /**
     * True when a process that started at [processStart] can't be the one Tandem started at [launchedAt]: it came up
     * later. Unknown times (null, or no launch recorded) never count as a restart. Plain, for tests.
     */
    fun restartedSince(processStart: Long?, launchedAt: Long): Boolean =
        processStart != null && launchedAt > 0 && processStart > launchedAt + START_SLACK_MS

    /**
     * Whether the game open in [pkg] is still the one Tandem started there at [launchedAt] (0: none on record, as after
     * Tandem closed it). Not when there is no record, when the app's process came up later (reopened elsewhere), or,
     * for a Windows game ([windows]), when another Windows game started later in the same app. Without [snap] the
     * record is all there is. Plain, for tests.
     */
    fun sameGame(snap: Snapshot?, pkg: String, launchedAt: Long, windows: Boolean): Boolean {
        if (launchedAt <= 0) return false
        snap ?: return true
        return !snap.restartedSince(pkg, launchedAt) && !(windows && snap.newWindowsGameSince(launchedAt))
    }

    /** "1-02:03:04", "02:03:04" or "03:04" (`ps -o ETIME`) in seconds, or null. Plain, for tests. */
    fun elapsedSeconds(etime: String): Long? {
        val days = etime.substringBefore('-', "0").toLongOrNull() ?: return null
        val parts = etime.substringAfter('-').split(':').map { it.toLongOrNull() ?: return null }
        if (parts.size !in 2..3) return null
        return days * 86_400 + parts.fold(0L) { acc, p -> acc * 60 + p }
    }

    /**
     * `ps -A -o ETIME,NAME` read into process names and start times (wall clock at [now]). Plain, for tests. A line
     * without a usable age (the header) still gives a name, as plain `ps -o NAME` did.
     */
    fun parsePs(out: String, now: Long): Pair<Set<String>, Map<String, Long>> {
        val names = HashSet<String>()
        val started = HashMap<String, Long>()
        for (raw in out.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val age = elapsedSeconds(line.substringBefore(' '))
            val name = if (age == null) line else line.substringAfter(' ').trim()
            if (name.isEmpty()) continue
            names += name
            if (age != null) started[name] = now - age * 1000
        }
        return names to started
    }

    private var cached: Snapshot? = null
    private var cachedAt = 0L

    /** What runs, or null when Shizuku can't tell. Blocking: call off the main thread when you can. */
    @Synchronized
    fun snapshot(maxAgeMs: Long = FRESH_MS): Snapshot? {
        val now = SystemClock.elapsedRealtime()
        cached?.let { if (now - cachedAt <= maxAgeMs) return it }
        val out = Shell.run("sh", "-c", "ps -A -o ETIME,NAME; echo $SPLIT; dumpsys activity activities") ?: return null.also { cached = null }
        val (names, started) = parsePs(out.substringBefore(SPLIT), System.currentTimeMillis())
        if (names.isEmpty()) return null.also { cached = null }
        val dump = if (SPLIT in out) out.substringAfter(SPLIT) else null
        return Snapshot(names, RunningTasks.parse(dump), started).also { cached = it; cachedAt = now }
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
            val since = prefs.getLong(pkg, 0)
            // Opened outside Tandem: some other game, from when its process came up.
            if (!stillOurs(ctx, pkg, snap, byPath)) RunningGame(pkg, appLabel(ctx, pkg), null, snap.started[pkg] ?: since)
            else RunningGame(pkg, appLabel(ctx, pkg), lastGame(ctx, pkg, byPath), since)
        }.sortedByDescending { it.since }
        canSee = true
    }

    /**
     * The Tandem game last started in [pkg], if it is still in the library and its file still exists: a game since
     * deleted (or a library entry left behind for one) is never named or pictured.
     */
    private fun lastGame(ctx: Context, pkg: String, byPath: Map<String, Game>): Game? =
        Launcher.lastGamePath(ctx, pkg)?.let(byPath::get)?.takeIf { it.file.exists() }

    /**
     * Whether the game [pkg] has open is still the one Tandem last started in it (see [Processes.sameGame]).
     * [byPath]: the library by file, when the caller already has it.
     */
    fun stillOurs(ctx: Context, pkg: String, snap: Processes.Snapshot?, byPath: Map<String, Game>? = null): Boolean {
        val path = Launcher.lastGamePath(ctx, pkg)
        val game = path?.let { p -> byPath?.get(p) ?: Libraries.games.firstOrNull { it.file.path == p } }
        val since = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(pkg, 0)
        return Processes.sameGame(snap, pkg, since, windows = game?.platform?.id == "windows")
    }

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
            .map { CloseCheck.Slot(it, if (stillOurs(ctx, it, snap)) Launcher.lastGamePath(ctx, it) else null) }
        val closing = CloseCheck.wouldClose(slots, pkg, path, anyApp, closePreviousOn())
        if (closing.isEmpty()) return emptyList()
        val byPath = Libraries.games.associateBy { it.file.path }
        // Named from the slot's own path, null for an emulator opened outside Tandem, as in [refresh].
        return closing.map { RunningGame(it.pkg, appLabel(ctx, it.pkg), CloseCheck.gameFor(it, byPath)?.takeIf { g -> g.file.exists() }, prefs.getLong(it.pkg, 0)) }
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
        SecondaryHome.beforeLeave(ctx, r.pkg, game = true)
        // Its own task where one is known (GameResume.kt); the launcher activity could open a new one and lose the game.
        val app = ctx.applicationContext
        Thread({
            val target = GameResume.target(Processes.snapshot(maxAgeMs = 1500)?.tasks, r.pkg)
            if (target != null && GameResume.bringBack(app, target)) return@Thread
            GameResume.launcherIntent(ctx, r.pkg)?.let { runCatching { ctx.startActivity(it) } }
        }, "tandem-resume").start()
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
