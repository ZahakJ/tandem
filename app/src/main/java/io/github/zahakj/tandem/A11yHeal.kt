package io.github.zahakj.tandem

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/**
 * Keeps the Controller Home button working (ControllerHomeService.kt). Android sometimes switches an accessibility
 * service off by itself (after an update of Tandem, a crash, or a reboot on some builds). If the user had switched
 * it on before (remembered each time it connects) and Tandem is the default Home, Tandem switches it back on through
 * Shizuku, keeping every other service in the list as it was.
 *
 * It never does that after the user turned it off on purpose: opening Android's Accessibility settings from
 * Tandem's own row (Settings › Controls) with it on and coming back with it off counts as that, until the user
 * switches it on again.
 */
object A11yHeal {
    private const val PREFS = "controller-home"
    private const val EVER = "ever_enabled"
    private const val USER_OFF = "user_off"
    private const val MIN_GAP_MS = 60_000L

    @Volatile private var lastCheck = 0L
    @Volatile private var logged = false
    /** Tandem opened Android's Accessibility settings while the service was on. */
    @Volatile private var openedWhileOn = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The service connected: the user wants it, so forget any earlier "turned off on purpose". */
    fun connected(ctx: Context) {
        val p = prefs(ctx)
        if (!p.getBoolean(EVER, false) || p.getBoolean(USER_OFF, false)) p.edit().putBoolean(EVER, true).putBoolean(USER_OFF, false).apply()
    }

    /** Tandem's settings row is about to open Android's Accessibility settings. */
    fun openingSettings(ctx: Context) {
        openedWhileOn = ControllerHomeService.enabled(ctx)
    }

    /** Back in Tandem's settings: a service that was on and now is off was switched off by the user. */
    fun backFromSettings(ctx: Context) {
        if (!openedWhileOn) return
        openedWhileOn = false
        if (!ControllerHomeService.enabled(ctx)) {
            prefs(ctx).edit().putBoolean(USER_OFF, true).apply()
            Log.i("Tandem", "Controller Home button switched off in Accessibility settings; leaving it off")
        }
    }

    /** The list of enabled services with [me] (package/class) added once, the others kept in their order. Pure. */
    fun withService(list: String?, me: String): String {
        val mine = full(me)
        val others = list.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() && full(it) != mine }
        return (others + me).joinToString(":")
    }

    /** "pkg/.Cls" written out as "pkg/pkg.Cls", so both forms of one service compare equal. */
    private fun full(name: String): String {
        val pkg = name.substringBefore('/')
        val cls = name.substringAfter('/', "")
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else name
    }

    /** Checks, and switches the service back on when it should be. Blocking (Shizuku): call off the main thread. */
    fun check(ctx: Context) {
        val now = SystemClock.elapsedRealtime()
        if (lastCheck != 0L && now - lastCheck < MIN_GAP_MS) return
        lastCheck = now
        val p = prefs(ctx)
        if (!p.getBoolean(EVER, false) || p.getBoolean(USER_OFF, false)) return
        if (ControllerHomeService.enabled(ctx)) return
        if (!runCatching { io.github.zahakj.tandem.ui.HomeMode.isDefault(ctx) }.getOrDefault(false)) return
        if (!Shell.waitReady(3000)) return
        val me = ComponentName(ctx, ControllerHomeService::class.java).flattenToString()
        val list = withService(Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), me)
        val ok = Shell.run("settings", "put", "secure", "enabled_accessibility_services", list) != null &&
            Shell.run("settings", "put", "secure", "accessibility_enabled", "1") != null
        if (!logged) {
            logged = true
            Log.i("Tandem", if (ok) "Controller Home button was switched off; switched it back on" else "Controller Home button is off and Shizuku couldn't switch it on")
        }
    }
}
