package io.github.zahakj.tandem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.zahakj.tandem.data.HomeStore

/**
 * Games paused while the handheld sleeps. An emulator left open keeps its audio output running even with the screen
 * off (Azahar does), and Android stays awake while any app's audio is open: about 1.25 % an hour on the Thor
 * instead of a fraction of that. A minute after the screen goes off, the processes of the games still open are
 * stopped (SIGSTOP, as Android's own freezer does to cached apps), so their audio goes quiet and the device can
 * suspend; the screen coming on lets them carry on (SIGCONT) exactly where they were.
 *
 * Needs a Shizuku that runs as root (it can signal other apps' processes); without it nothing happens. The frozen
 * process ids are kept in preferences, so a Tandem restart thaws anything a previous process left stopped.
 */
object SleepFreeze {
    private const val TAG = "Tandem"
    private const val PREFS = "sleep_freeze"
    private const val KEY = "pids"
    private const val AFTER_MS = 60_000L

    private val main by lazy { Handler(Looper.getMainLooper()) }
    private var app: Context? = null
    private var receiver: BroadcastReceiver? = null

    private val freeze by lazy { Runnable { app?.let { ctx -> Thread({ freezeNow(ctx) }, "tandem-freeze").start() } } }

    /** Starts watching the screen; thaws anything left frozen. Main thread. */
    fun start(ctx: Context) {
        if (receiver != null) return
        val appCtx = ctx.applicationContext
        app = appCtx
        Thread({ thawNow(appCtx) }, "tandem-thaw").start()
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> if (HomeStore.settings.freezeAsleep) main.postDelayed(freeze, AFTER_MS)
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        main.removeCallbacks(freeze)
                        Thread({ thawNow(appCtx) }, "tandem-thaw").start()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        runCatching { appCtx.registerReceiver(receiver, filter) }
    }

    fun stop() {
        main.removeCallbacks(freeze)
        receiver?.let { r -> runCatching { app?.unregisterReceiver(r) } }
        receiver = null
    }

    /** The process ids of [pkg] and its sub-processes (`pkg:name`), from `ps -A -o PID,NAME`. Plain, for tests. */
    fun pidsOf(ps: String?, pkg: String): List<Int> = ps.orEmpty().lineSequence().mapNotNull { line ->
        val w = line.trim().split(Regex("\\s+"))
        val name = w.getOrNull(1) ?: return@mapNotNull null
        if (name == pkg || name.startsWith("$pkg:")) w[0].toIntOrNull() else null
    }.toList()

    private fun freezeNow(ctx: Context) {
        if (!Shell.ready()) return
        val snap = Processes.snapshot(maxAgeMs = 0) ?: return
        val games = RunningGames.emulatorPackages().filter { it != ctx.packageName && snap.playing(it) }
        if (games.isEmpty()) return
        val ps = Shell.run("ps", "-A", "-o", "PID,NAME")
        val pids = games.flatMap { pidsOf(ps, it) }
        if (pids.isEmpty()) return
        save(ctx, pids)
        Shell.run("kill", "-STOP", *pids.map(Int::toString).toTypedArray())
        Log.i(TAG, "Asleep: froze ${games.joinToString()} (${pids.size} processes)")
    }

    private fun thawNow(ctx: Context) {
        val pids = load(ctx)
        if (pids.isEmpty()) return
        if (!Shell.waitReady(5_000)) return
        Shell.run("kill", "-CONT", *pids.map(Int::toString).toTypedArray())
        save(ctx, emptyList())
        Log.i(TAG, "Awake: thawed ${pids.size} processes")
    }

    private fun save(ctx: Context, pids: List<Int>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, pids.joinToString(",")).commit()
    }

    private fun load(ctx: Context): List<Int> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
}
