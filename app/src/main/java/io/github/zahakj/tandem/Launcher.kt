package io.github.zahakj.tandem

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import io.github.zahakj.tandem.data.SystemPrefs
import io.github.zahakj.tandem.ui.LaunchFx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File

/**
 * Starts games, and brings a game back instead of restarting it when it is still running in its emulator.
 *
 * Android does not tell apps which other apps are running, so the resume check goes through Shizuku when it is
 * available. Without Shizuku every launch starts the game fresh, like other frontends do.
 */
object Launcher {
    private const val TAG = "Tandem"
    private const val PREFS = "last_played"
    private val scope = MainScope()

    /** Everything needed to start one game, worked out before the launch animation plays. */
    class Plan(val game: Game, val spec: LaunchSpec, val intent: Intent?, val uri: Uri?, val bottom: Boolean)

    /** True while [launch] asks Shizuku whether the launch would close a running game; further presses wait. */
    private var checking = false
    /** The "can't check for running games" note shows once per run of Tandem, not on every launch. */
    private var toldCantSee = false

    /**
     * Starts [game]: checks it can start, plays the launch animation (when the UI is showing and motion is on), then
     * opens the emulator. Problems are explained by Pip instead of a bare toast.
     *
     * Every launch in Tandem comes through here (grids, lists, search, collections, Up next, the switcher, the Start
     * panel, widgets). When starting [game] would close a game still running, Tandem asks first (ui/CloseAsk.kt);
     * the answer starts it with [proceed] or resumes the running one instead. The same game still running resumes
     * without asking, as does everything when Tandem can't see other apps (no Shizuku).
     */
    fun launch(ctx: Context, game: Game, bottom: Boolean? = null) {
        SystemPrefs.init(ctx)
        when (val prepared = prepare(ctx, game)) {
            is LaunchProblem -> LaunchFx.problem = prepared
            is Plan -> {
                // [bottom]: this once on the bottom (true) or top screen, whatever the game's own choice.
                val p = if (bottom == null || bottom == prepared.bottom) prepared
                else Plan(prepared.game, prepared.spec, prepared.intent, prepared.uri, bottom)
                if (LaunchFx.busy || LaunchFx.closeAsk != null || checking) return
                // Android apps never close anything (Tandem only brings them up), and asking may be switched off.
                if (game.platform.isApps || !RunningGames.askBeforeClosingOn()) { proceed(ctx, p); return }
                checking = true
                scope.launch {
                    val closing = try {
                        withContext(Dispatchers.IO) { RunningGames.wouldClose(ctx, p.spec.packageName, game.file.path, p.spec.component == LaunchSpec.ANY_APP) }
                    } finally {
                        checking = false
                    }
                    when {
                        closing == null -> {
                            if (!toldCantSee) { toldCantSee = true; io.github.zahakj.tandem.ui.Toasts.show("Can't check for running games without Shizuku") }
                            proceed(ctx, p)
                        }
                        closing.isEmpty() -> proceed(ctx, p)
                        else -> LaunchFx.askToClose(ctx, p, closing)
                    }
                }
            }
        }
    }

    /** Starts [p] without asking: the launch sound, then the animation (or straight away with motion off). */
    fun proceed(ctx: Context, p: Plan) {
        if (LaunchFx.busy) return
        io.github.zahakj.tandem.ui.Sounds.launch(p.game.platform.id)
        if (LaunchFx.canAnimate()) LaunchFx.play(ctx, p)
        // No animation: still keep the Shizuku checks off the main thread.
        else scope.launch { withContext(Dispatchers.IO) { start(ctx, p) }?.let { LaunchFx.problem = it } }
    }

    /** A [Plan], or the [LaunchProblem] that stops [game] from starting. */
    fun prepare(ctx: Context, game: Game): Any {
        val bottom = LaunchDisplay.wantsBottom(game)
        if (game.platform.isApps) {
            val pkg = AndroidApps.packageOf(game)
            if (!AndroidApps.isLaunchable(ctx, pkg)) return LaunchProblem.AppMissing(game)
            return Plan(game, game.platform.allPlayers.first(), null, null, bottom)
        }
        val chosen = game.platform.playerFor(game) ?: return LaunchProblem.NoEmulator(game, game.platform.allPlayers.firstOrNull())
        if (!game.file.exists()) return LaunchProblem.FileMissing(game)
        val spec = chosen.forFile(game.file)
        val uri = documentUri(ctx, game.file) ?: Uri.fromFile(game.file)
        val intent = try {
            LaunchIntents.build(spec, game, uri)
        } catch (e: BadShortcutException) {
            return LaunchProblem.BadShortcut(game, e.message.orEmpty())
        } catch (e: IllegalArgumentException) {
            return LaunchProblem.WontOpen(game, spec, e.message)
        }
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        return Plan(game, spec, intent, uri, bottom)
    }

    /** Opens the emulator for [plan]. Returns a problem if it couldn't. Blocking (asks Shizuku): call off the main thread. */
    fun start(ctx: Context, plan: Plan): LaunchProblem? {
        val game = plan.game
        val options = LaunchDisplay.optionsFor(ctx, plan.bottom)
        // The bottom screen's details panel would sit on top of a game started down there.
        if (options != null) (ctx as? MainActivity)?.let { a -> a.runOnUiThread { a.hideBottomScreen() } }
        // A game left running on the other screen never sent Tandem to the background, so close its session here.
        onMain { Sessions.finish(ctx) }

        if (game.platform.isApps) {
            val pkg = AndroidApps.packageOf(game)
            val wasRunning = Processes.open(pkg, maxAgeMs = 1500) == true
            // Tandem's bottom-screen home first (when none is alive), so the companion and the game land above it.
            SecondaryHome.beforeLeave(ctx, pkg, game = true)
            // The bottom screen while it plays (GameCompanion.kt), opened first so the app, started last, keeps the controller.
            GameCompanion.show(ctx, game, pkg, bottom = options != null)
            CompanionApps.startFor(ctx, game, pkg, bottom = options != null)
            if (!AndroidApps.open(ctx, pkg, options)) { GameCompanion.dismiss(); CompanionApps.release(); return LaunchProblem.AppMissing(game) }
            onMain { Sessions.start(ctx, game, newPlay = !wasRunning) }
            return null
        }

        val spec = plan.spec
        val intent = plan.intent ?: return LaunchProblem.WontOpen(game, spec, null)
        val pkg = spec.packageName
        val key = "$pkg|${game.file.path}"
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val anyApp = spec.component == LaunchSpec.ANY_APP
        // Tandem's bottom-screen home first (when none is alive), so the companion and the game land above it.
        SecondaryHome.beforeLeave(ctx, if (anyApp) null else pkg, game = true)
        val snap = Processes.snapshot(maxAgeMs = 1500)
        // Alive: the emulator's process exists, maybe only cached (its task gone, or back at its menu). Enough to close
        // it before starting; only a game still open in it is resumed.
        val running = if (anyApp) false else snap?.alive(pkg)
        val playing = !anyApp && snap?.playing(pkg) == true

        if (playing && prefs.getString(pkg, null) == key) {
            val front = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (front != null) {
                // The same flags the Android launcher uses: brings the existing task forward untouched.
                front.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                front.setPackage(null)
                try {
                    // Brought back, not started: the companion's "Playing" carries on from this session's time.
                    val earlier = RunningGames.list.firstOrNull { it.pkg == pkg }?.let { RunningGames.sessionSeconds(it) } ?: 0L
                    GameCompanion.show(ctx, game, pkg, bottom = options != null, earlierMs = earlier * 1000)
                    CompanionApps.startFor(ctx, game, pkg, bottom = options != null)
                    ctx.startActivity(front, options ?: io.github.zahakj.tandem.ui.ScreenRoles.gameLaunchOptions(ctx))
                    onMain { Sessions.start(ctx, game, newPlay = false) }
                    Log.i(TAG, "Resumed $key")
                    return null
                } catch (e: Exception) {
                    Log.w(TAG, "Resume failed for $key, starting fresh", e)
                }
            }
        }
        // Only one game at a time, if that setting is on (no need for helper apps that close the previous game).
        if (running != null && RunningGames.closePreviousOn()) RunningGames.closeOthers(ctx, keep = pkg)
        // A different game is open in this emulator; some (Eden) ignore a new game while one is running.
        if (running == true) Shell.run("am", "force-stop", pkg)

        val uri = plan.uri
        if (uri?.scheme == "content" && !anyApp) {
            try {
                ctx.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not share $uri with $pkg", e)
            }
        }
        // The bottom screen while the game plays (GameCompanion.kt). Opened before the game, so the game, started last,
        // is the one with the controller.
        GameCompanion.show(ctx, game, if (anyApp) null else pkg, bottom = options != null)
        CompanionApps.startFor(ctx, game, if (anyApp) null else pkg, bottom = options != null)
        return try {
            ctx.startActivity(intent, options ?: io.github.zahakj.tandem.ui.ScreenRoles.gameLaunchOptions(ctx))
            if (!anyApp) {
                prefs.edit().putString(pkg, key).apply()
                RunningGames.onStarted(ctx, pkg)
            }
            onMain { Sessions.start(ctx, game, newPlay = true) }
            Log.i(TAG, "Started $key with ${spec.name} on ${if (options != null) "bottom" else "main"} screen")
            null
        } catch (e: ActivityNotFoundException) {
            GameCompanion.dismiss()
            CompanionApps.release()
            Log.e(TAG, "No activity for ${spec.component}", e)
            if (Emulators.isInstalled(pkg)) LaunchProblem.WontOpen(game, spec, null) else LaunchProblem.NoEmulator(game, spec)
        } catch (e: SecurityException) {
            GameCompanion.dismiss()
            CompanionApps.release()
            Log.e(TAG, "Launch failed for $key", e)
            LaunchProblem.WontOpen(game, spec, e.message)
        } catch (e: IllegalArgumentException) {
            // Thrown for an unusable launch display, among others.
            GameCompanion.dismiss()
            CompanionApps.release()
            Log.e(TAG, "Launch failed for $key", e)
            LaunchProblem.WontOpen(game, spec, e.message)
        }
    }

    /** Play tracking and focus memory belong to the UI thread; [start] runs in the background. */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
    }

    /** True when [game] is the last one started in its emulator and that emulator is still running. */
    fun canResume(ctx: Context, game: Game): Boolean {
        if (game.platform.isApps) return Processes.open(AndroidApps.packageOf(game)) == true
        val pkg = game.platform.playerFor(game)?.packageName ?: return false
        val last = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(pkg, null)
        // A few seconds' old answer is fine here, and keeps browsing from asking Shizuku on every game.
        return last == "$pkg|${game.file.path}" && Processes.playing(pkg) == true
    }

    /** The file of the game Tandem last started in emulator [pkg], if any. */
    fun lastGamePath(ctx: Context, pkg: String): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(pkg, null)?.substringAfter('|', "")?.ifEmpty { null }

    /**
     * Emulators that only open shared links (Eden, Cemu, melonDS) get a document link inside a folder the user gave
     * Tandem access to (the ROMs folder, or one added in Systems); access to that can be passed on, unlike a plain
     * file path.
     */
    private fun documentUri(ctx: Context, file: File): Uri? {
        // Folders added with the folder picker come with their own access.
        for ((path, tree) in SystemPrefs.data.trees) {
            if (!file.path.startsWith("$path/")) continue
            val treeUri = Uri.parse(tree)
            val base = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: continue
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, base.trimEnd('/') + "/" + file.path.removePrefix("$path/"))
        }
        val root = Environment.getExternalStorageDirectory().path
        val romsTree = Prefs.romsTree(ctx) ?: return null
        if (!file.path.startsWith("$root/")) return null
        return DocumentsContract.buildDocumentUriUsingTree(romsTree, "primary:" + file.path.removePrefix("$root/"))
    }
}


/**
 * Play time: a session starts when a game is launched and ends when Tandem is shown again. Sessions longer than
 * [MAX_SESSION_MS] are capped, in case the device slept with the game open.
 */
object Sessions {
    private const val PREFS = "session"
    private const val MAX_SESSION_MS = 6 * 60 * 60 * 1000L

    /**
     * Notes that [game] started. Nothing is counted yet: a launch only becomes a play (count, "last played",
     * Continue playing) once it lasted a little while, see [finish]. Which game is open in which emulator, for
     * resuming, is kept separately by the launcher.
     */
    fun start(ctx: Context, game: Game, newPlay: Boolean) {
        val now = System.currentTimeMillis()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("key", game.key).putLong("start", now).putBoolean("new", newPlay).apply()
    }

    /** Called when Tandem comes back to the front. */
    fun finish(ctx: Context) { take(ctx)?.invoke() }

    /** The key of the game whose session is still open (started, and Tandem hasn't come back since), if any. */
    fun openKey(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("key", null)

    /**
     * Ends the session now (so a game started right after starts a new one) and returns the recording of it, to run
     * when convenient: recording changes the game's details, which recomposes the screens showing them, so coming
     * back to Tandem runs it after the first frames. Null when there is nothing to record.
     */
    fun take(ctx: Context): (() -> Unit)? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = prefs.getString("key", null) ?: return null
        val start = prefs.getLong("start", 0)
        val newPlay = prefs.getBoolean("new", true)
        val now = System.currentTimeMillis()
        val elapsed = (now - start).coerceIn(0, MAX_SESSION_MS)
        prefs.edit().clear().apply()
        // Under 10 seconds is a failed start or an accidental tap, not play: no play time, count or "last played".
        if (elapsed < 10_000) return null
        return {
            io.github.zahakj.tandem.data.Store.update(key) {
                it.copy(playSeconds = it.playSeconds + elapsed / 1000, lastPlayed = now, playCount = it.playCount + if (newPlay) 1 else 0)
            }
            io.github.zahakj.tandem.data.HomeStore.addSession(key, start, elapsed / 1000)
        }
    }
}

object Shell {
    fun ready(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    /**
     * Waits up to [timeoutMs] for Shizuku in a process that has only just started (its binder arrives a moment
     * after). Blocking: call off the main thread.
     */
    fun waitReady(timeoutMs: Long): Boolean {
        val end = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (ready()) return true
            if (android.os.SystemClock.elapsedRealtime() >= end) return false
            Thread.sleep(250)
        }
    }

    /** Runs one shell command through Shizuku and returns its output, or null when Shizuku isn't available. */
    fun run(vararg cmd: String): String? {
        if (!ready()) return null
        return try {
            // newProcess is private in Shizuku 13, but still the simplest way to run a single command.
            val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            val p = m.invoke(null, cmd, null, null) as Process
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out
        } catch (t: Throwable) {
            Log.w("Tandem", "Shizuku command failed: ${cmd.joinToString(" ")}", t)
            null
        }
    }
}

object Prefs {
    private const val NAME = "settings"

    fun romsTree(ctx: Context): Uri? =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString("roms_tree", null)?.let(Uri::parse)

    fun setRomsTree(ctx: Context, uri: Uri) =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putString("roms_tree", uri.toString()).apply()
}
