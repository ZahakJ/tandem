package io.github.zahakj.tandem

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.Display

/**
 * Bringing a game that is still running back to the front.
 *
 * Starting the emulator's launcher activity (what Android's own launcher does) isn't safe for this: Android looks
 * for a task whose root matches that intent, and a game Tandem started opened in a task rooted at the emulator's
 * game activity. Eden then got its menu in a new task, Android hid the game's task behind it, and the game was
 * destroyed. So the game's own task is moved to the front, by its id, from `dumpsys activity activities`
 * ([RunningTasks]); it stays on the screen it is on. The launcher activity is only started when no such task is
 * found (no Shizuku, or the dump didn't list one).
 *
 * [target] is plain, for tests (GameResumeTest).
 */
object GameResume {
    /** The task a game plays in, and the display it is on. */
    data class Target(val task: Int, val display: Int)

    /** The task [pkg]'s game plays in, or null when there is none (or the dump didn't give its id). */
    fun target(tasks: Map<String, List<RunningTasks.Activity>>?, pkg: String): Target? {
        val a = tasks?.let { RunningTasks.game(it, pkg) }?.takeIf { it.task >= 0 } ?: return null
        return Target(a.task, a.display.takeIf { it >= 0 } ?: Display.DEFAULT_DISPLAY)
    }

    /** True when [target] is on the second screen (not the main display). */
    fun onBottom(target: Target): Boolean = target.display != Display.DEFAULT_DISPLAY

    /**
     * Moves [target]'s task to the front of its display, as it is (REORDER_TASKS, a normal permission; Tandem is in
     * front when it asks). False when Android refused. Blocking (a binder call).
     */
    fun bringBack(ctx: Context, target: Target): Boolean = try {
        ctx.getSystemService(ActivityManager::class.java).moveTaskToFront(target.task, 0)
        true
    } catch (e: RuntimeException) {
        Log.w("Tandem", "Couldn't bring task ${target.task} to the front", e)
        false
    }

    /** The emulator's launcher activity, for when no task of its game is known: Android brings up what it finds. */
    fun launcherIntent(ctx: Context, pkg: String): Intent? = ctx.packageManager.getLaunchIntentForPackage(pkg)?.apply {
        // The same flags the Android launcher uses: brings an existing task forward untouched.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        setPackage(null)
    }
}
