package io.github.zahakj.tandem

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A game's own second screen: Eden Duo (Eden with per-game companions: maps, pockets, units) draws one on the
 * bottom screen. Tandem launches Switch games there with it hidden, so the game opens as usual with Tandem's
 * companion below, and offers it on demand ([show]); its "Hide" handle hands the screen back. Eden Duo reports
 * changes through a ContentObserver on its status, watched while the companion screen is up for one of its games.
 */
object GameScreens {
    const val PKG = "io.github.zahakj.edenduo"
    private val control: Uri = Uri.parse("content://$PKG.control")
    private val statusUri: Uri = Uri.parse("content://$PKG.control/status")
    private const val PERMISSION = "$PKG.permission.CONTROL_COMPANION"

    /** The running game has a companion ready to show (booted, installed, for this build). */
    var available by mutableStateOf(false)
        private set

    /** It is on the bottom screen now. */
    var shown by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null

    /** Whether [pkg] (a running game's app) is Eden Duo. */
    fun isFor(pkg: String?): Boolean = pkg == PKG

    /** Starts following the game's companion (call while the companion screen is up for an Eden Duo game). */
    fun watch(ctx: Context) {
        if (observer != null) return
        val app = ctx.applicationContext
        val o = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) = refresh(app)
        }
        runCatching { app.contentResolver.registerContentObserver(statusUri, false, o) }.onSuccess { observer = o }
        refresh(app)
    }

    fun stop(ctx: Context) {
        observer?.let { o -> runCatching { ctx.applicationContext.contentResolver.unregisterContentObserver(o) } }
        observer = null
        available = false
        shown = false
    }

    /** Puts the game's companion on the bottom screen, over Tandem's. */
    fun show(ctx: Context) = command(ctx.applicationContext, "show")

    fun hide(ctx: Context) = command(ctx.applicationContext, "hide")

    private fun refresh(app: Context) = command(app, "status")

    private fun command(app: Context, method: String) {
        Thread({
            if (!allowed(app)) return@Thread
            val b = runCatching { app.contentResolver.call(control, method, null, null) }.getOrNull()
            main.post { apply(b) }
        }, "tandem-game-screen").start()
    }

    private fun apply(b: Bundle?) {
        available = b?.getBoolean("companion_available") == true || b?.getBoolean("companion_shown") == true
        shown = b?.getBoolean("companion_shown") == true
    }

    /** Eden Duo's control permission: asked once, granted through Shizuku so no dialog interrupts the game. Blocking. */
    private fun allowed(app: Context): Boolean {
        if (app.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) return true
        if (Shell.ready()) Shell.run("pm", "grant", app.packageName, PERMISSION)
        return app.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}
