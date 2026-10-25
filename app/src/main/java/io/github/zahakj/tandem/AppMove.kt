package io.github.zahakj.tandem

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.widget.Toast
import io.github.zahakj.tandem.data.AppPrefs
import io.github.zahakj.tandem.data.AppScreen

/**
 * Moving an app that is already open to the other screen: holding Back (ControllerHomeService), "Move here" on the
 * bottom-screen home, and "Open on the other screen" for an app that is running on the wrong one. Android starts a
 * running app where it already is, so this moves its task with Shizuku (`am display move-stack`).
 *
 * After a move the app opens on that screen from then on (its saved screen); holding Back again soon after moves it
 * back and puts the old choice back.
 *
 * Moving up is a swap: whatever was on the top screen goes to the bottom one. Another app is moved down; Tandem's
 * library (the Home task, which can't leave its display) is shown down there by the bottom-screen home instead
 * ([LibraryGuest]). Moving down brings back whatever is under the app on top, usually the library.
 *
 * The dump parsing is plain functions, unit-tested without Android (AppMoveTest).
 */
object AppMove {
    /** A root task in `dumpsys activity activities`: its id, display, type, the package of its top activity, and the ids of the tasks inside it. */
    data class Task(val id: Int, val display: Int, val type: String, val pkg: String?, val leaves: Set<Int>, val top: Boolean)

    private val displayLine = Regex("""^Display #(\d+)""")
    private val taskLine = Regex("""^\* Task\{\S+ #(\d+) type=(\w+)""")
    private val histLine = Regex("""^\* Hist\s+#\d+:\s*ActivityRecord\{\S+ u\d+ ([A-Za-z0-9_.]+)/\S+ t(\d+)""")
    private val topResumed = Regex("""^topResumedActivity=ActivityRecord\{\S+ u\d+ ([A-Za-z0-9_.]+)/\S+ t(\d+)""")

    /** Every root task, display by display, top first; [Task.top] marks the first on its display. */
    fun tasks(dump: String): List<Task> {
        val out = ArrayList<Task>()
        var display = -1
        var rootIndent = -1
        var cur: Task? = null
        var firstOnDisplay = true
        fun finish() { cur?.let { out += it }; cur = null }
        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            val indent = raw.length - raw.trimStart().length
            displayLine.find(line)?.let { m ->
                finish()
                display = m.groupValues[1].toInt()
                rootIndent = -1
                firstOnDisplay = true
                return@let
            }
            if (line.startsWith("Display #")) continue
            val t = taskLine.find(line)
            if (t != null && display >= 0) {
                if (cur == null || indent <= rootIndent) {
                    finish()
                    rootIndent = indent
                    cur = Task(t.groupValues[1].toInt(), display, t.groupValues[2], null, emptySet(), firstOnDisplay)
                    firstOnDisplay = false
                }
                continue
            }
            val h = histLine.find(line)
            if (h != null) {
                val c = cur ?: continue
                cur = c.copy(pkg = c.pkg ?: h.groupValues[1], leaves = c.leaves + h.groupValues[2].toInt())
            }
        }
        finish()
        return out
    }

    /** The root task with input focus (the global topResumedActivity, listed last). */
    fun focused(dump: String): Task? {
        val leaf = dump.lineSequence().map { it.trim() }.mapNotNull { topResumed.find(it) }.lastOrNull()?.groupValues?.get(2)?.toIntOrNull()
            ?: return null
        return tasks(dump).firstOrNull { leaf in it.leaves || it.id == leaf }
    }

    /**
     * What holding Back moves: the focused app, or when focus is on a home screen (Tandem, the bottom-screen home),
     * the app in front on the other screen. Only an ordinary app in front of its screen; [skip] leaves out Tandem
     * and, unless allowed, games.
     */
    fun pick(dump: String, skip: (String) -> Boolean): Task? {
        val all = tasks(dump)
        fun ok(t: Task) = t.top && t.type == "standard" && t.pkg != null && !skip(t.pkg)
        val f = focused(dump)?.let { f -> all.firstOrNull { it.id == f.id } }
        if (f != null && ok(f)) return f
        return all.firstOrNull { ok(it) && it.display != f?.display }
    }

    /**
     * Moving [up] from the bottom screen to the top one swaps the screens: what is in front on the top screen goes
     * down. This is that app, or null when it is Tandem (the bottom-screen home then shows the library, see
     * [LibraryGuest]) or something [skip] leaves alone (a game, unless allowed).
     */
    fun swapPartner(dump: String, up: Task, skip: (String) -> Boolean): Task? =
        tasks(dump).firstOrNull { it.display == Display.DEFAULT_DISPLAY && it.top }
            ?.takeIf { it.id != up.id && it.type == "standard" && it.pkg != null && !skip(it.pkg) }

    /** The root task that holds [pkg], if it is open anywhere. */
    fun taskOf(dump: String, pkg: String): Task? = tasks(dump).firstOrNull { it.type == "standard" && it.pkg == pkg }

    // ---- Moving (Shizuku; blocking, so off the main thread) ----

    /** The last move, for undo: the app, its saved screen before, when, and the app that went down to make room. */
    private class Last(val pkg: String, val screen: String?, val at: Long, val partner: String? = null)
    @Volatile private var last: Last? = null
    private const val UNDO_MS = 10_000L
    private val main by lazy { Handler(Looper.getMainLooper()) }

    private fun dump(): String? = Shell.run("dumpsys", "activity", "activities")

    /** True when [pkg] is a game Tandem knows of: an emulator, an Android game, or what it started as a game. */
    fun isGame(pkg: String): Boolean =
        AndroidApps.isEmulator(pkg) ||
            Libraries.byId(AndroidApps.PLATFORM_ID)?.games?.any { AndroidApps.packageOf(it) == pkg } == true ||
            (SecondaryHome.front as? SecondaryHome.Front.Game)?.pkg == pkg ||
            RunningGames.list.any { it.pkg == pkg }

    @Volatile private var movedDownAt = 0L

    /** True just after an app moved to the bottom screen: Tandem coming back on top leaves the bottom screen to it. */
    fun justMovedDown(): Boolean = SystemClock.uptimeMillis() - movedDownAt < 2_500

    /** Takes Tandem's own bottom screen (details or swapped library) down, so the app moved there is in front. */
    private fun clearBottom() {
        movedDownAt = SystemClock.uptimeMillis()
        main.post { MainActivity.live?.get()?.hideBottomScreen() }
    }

    /** Moves [task] to display [to]. True when Android took it. */
    private fun moveTask(task: Task, to: Int): Boolean {
        if (to != Display.DEFAULT_DISPLAY) clearBottom()
        val out = Shell.run("am", "display", "move-stack", task.id.toString(), to.toString()) ?: return false
        val failed = listOf("Exception", "Error", "Unknown", "not found").any { out.contains(it, ignoreCase = true) }
        if (failed) Log.w("Tandem", "Moving task ${task.id} to display $to: $out")
        return !failed
    }

    /** Brings [pkg]'s task to the front on display [displayId], so it has the controller again. */
    private fun focus(ctx: Context, pkg: String, displayId: Int) {
        val intent = io.github.zahakj.tandem.ui.Dock.launchIntent(ctx, pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) ?: return
        val options = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
        runCatching { ctx.startActivity(intent, options) }
    }

    /**
     * Holding Back: moves the app in front to the other screen and remembers that screen for it. Held again within
     * a few seconds, the same app goes back and its old choice returns. Blocking.
     */
    fun moveFront(ctx: Context): Boolean {
        val app = ctx.applicationContext
        AppPrefs.init(app)
        val prefs = AppPrefs.data
        if (!prefs.holdBackMoves || !LaunchDisplay.dualScreen(app)) return false
        val secondary = LaunchDisplay.secondary(app)?.displayId ?: return false
        val text = dump() ?: run { toast(app, "Moving apps between screens needs Shizuku"); return false }
        val skip = { pkg: String -> pkg == app.packageName || (!prefs.moveGames && isGame(pkg)) }
        val task = pick(text, skip) ?: return false
        val pkg = task.pkg ?: return false
        val toBottom = task.display == Display.DEFAULT_DISPLAY
        val to = if (toBottom) secondary else Display.DEFAULT_DISPLAY
        // Up: it is a swap. Another app on top goes down (Tandem on top: the bottom-screen home shows the library).
        val partner = if (toBottom) null else swapPartner(text, task, skip)
        if (!moveTask(task, to)) { toast(app, "Android wouldn't move that app"); return false }
        if (!toBottom) SecondaryHome.beforeLeave(app, pkg)
        if (partner != null) moveTask(partner, secondary)
        val undo = last?.takeIf { it.pkg == pkg && SystemClock.uptimeMillis() - it.at < UNDO_MS }
        // Undoing a swap with another app: the app that went down to make room goes back up.
        if (undo?.partner != null && toBottom) {
            dump()?.let { taskOf(it, undo.partner) }?.takeIf { it.display == secondary }?.let { back ->
                if (moveTask(back, Display.DEFAULT_DISPLAY)) SecondaryHome.beforeLeave(app, undo.partner)
            }
        }
        focus(app, pkg, to)
        val label = RunningGames.appLabel(app, pkg)
        if (undo != null) {
            last = null
            AppPrefs.update(pkg) { it.copy(screen = undo.screen) }
            toast(app, "Moved back. $label opens as before")
        } else {
            last = Last(pkg, AppPrefs.of(pkg).screen, SystemClock.uptimeMillis(), partner?.pkg)
            AppPrefs.update(pkg) { it.copy(screen = if (toBottom) AppScreen.BOTTOM else AppScreen.TOP) }
            toast(app, "Moved to the ${if (toBottom) "bottom" else "top"} screen. $label opens there from now on (hold Back again to undo)")
        }
        return true
    }

    /** "Move here" on the bottom-screen home: [pkg] moves to the bottom screen and stays there next time. Blocking. */
    fun moveToBottom(ctx: Context, pkg: String): Boolean {
        val app = ctx.applicationContext
        val secondary = LaunchDisplay.secondary(app)?.displayId ?: return false
        val text = dump() ?: run { toast(app, "Moving apps between screens needs Shizuku"); return false }
        val task = taskOf(text, pkg) ?: run { toast(app, "${RunningGames.appLabel(app, pkg)} isn't open"); return false }
        if (task.display != secondary && !moveTask(task, secondary)) { toast(app, "Android wouldn't move that app"); return false }
        focus(app, pkg, secondary)
        last = Last(pkg, AppPrefs.of(pkg).screen, SystemClock.uptimeMillis())
        AppPrefs.update(pkg) { it.copy(screen = AppScreen.BOTTOM) }
        toast(app, "${RunningGames.appLabel(app, pkg)} opens on the bottom screen from now on")
        return true
    }

    /**
     * "Back to Tandem" on the bottom-screen home's own panel while [pkg] is open on the top screen: the app moves down
     * to the bottom screen (Shizuku) and Tandem comes back on top, so the app stays open and in sight; its saved
     * screen doesn't change. Without Shizuku Tandem just comes up over it, and the app stays open behind (Recents).
     * It never closes the app. Blocking.
     */
    fun tandemOnTop(ctx: Context, pkg: String?) {
        val app = ctx.applicationContext
        val secondary = LaunchDisplay.secondary(app)?.displayId
        if (pkg != null && secondary != null && LaunchDisplay.dualScreen(app) && Shell.ready()) {
            val task = dump()?.let { taskOf(it, pkg) }
            if (task != null && task.display == Display.DEFAULT_DISPLAY) moveTask(task, secondary)
        }
        main.post { GameCompanion.backToTandem(app) }
    }

    /**
     * After [pkg] was opened on a chosen screen: if it was already running on the other one (Android brings a running
     * app up where it is), moves it over. Quietly does nothing without Shizuku. Blocking.
     */
    fun ensureOn(ctx: Context, pkg: String, bottom: Boolean) {
        val app = ctx.applicationContext
        if (!LaunchDisplay.dualScreen(app) || !Shell.ready()) return
        val to = LaunchDisplay.displayId(app, bottom) ?: return
        Thread.sleep(500)
        val task = dump()?.let { taskOf(it, pkg) } ?: return
        if (task.display != to && moveTask(task, to)) focus(app, pkg, to)
    }

    private fun toast(ctx: Context, text: String) {
        main.post { runCatching { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() } }
    }
}
