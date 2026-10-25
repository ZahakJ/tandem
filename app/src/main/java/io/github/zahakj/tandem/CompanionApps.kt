package io.github.zahakj.tandem

import android.app.ActivityOptions
import android.content.Context
import android.util.Log

/**
 * A game's companion app (Edit details): an Android app, such as a guide, a map or a music player, started on the
 * bottom screen with the game. It takes the place Tandem's own bottom-screen companion ([GameCompanion]) would have,
 * so it follows the same rules: only with two screens (not in single screen mode), only when the game itself starts
 * on the top screen, and not when the bottom screen is left to the emulator. Tandem's companion still opens
 * underneath it and keeps watching for the game's end, so closing the app never uncovers Android's own launcher.
 *
 * When Tandem is back from the game the app is closed (force-stopped through Shizuku; without Shizuku it is left
 * alone) or left running, as the game's row says. Tandem's bottom screen covers it again either way.
 */
object CompanionApps {
    private data class Up(val pkg: String, val close: Boolean)

    @Volatile private var up: Up? = null

    /** True while a companion app opened with the running game has the bottom screen. */
    val covering: Boolean get() = up != null

    /**
     * Whether [game]'s companion app [app] should start: plain rules, for tests. [emulator] is the package the game
     * runs in (never started twice), [bottom] the game starts on the bottom screen, [mode] its [GameCompanion] mode.
     */
    fun wanted(app: String?, emulator: String?, bottom: Boolean, dualScreen: Boolean, mode: String): Boolean =
        !app.isNullOrBlank() && app != emulator && !bottom && dualScreen && mode != GameCompanion.OFF

    /**
     * Starts [game]'s companion app on the bottom screen, if it has one and the rules allow it. Call after
     * [GameCompanion.show] and before the game, so the game, started last, keeps the controller. Any thread.
     */
    fun startFor(ctx: Context, game: Game, emulator: String?, bottom: Boolean) {
        val meta = game.meta
        val app = meta.companionApp
        if (!wanted(app, emulator, bottom, LaunchDisplay.dualScreen(ctx), GameCompanion.modeFor(game, emulator))) return
        app ?: return
        val display = LaunchDisplay.secondary(ctx) ?: return
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId).toBundle()
        if (AndroidApps.open(ctx, app, options)) {
            up = Up(app, meta.companionAppClose)
            Log.i("Tandem", "Started companion app $app with ${game.key}")
        }
    }

    /** Tandem is back (or the game didn't start): closes the companion app when asked to, else leaves it running. */
    fun release() {
        val u = up ?: return
        up = null
        if (!u.close) return
        Thread({ if (Shell.run("am", "force-stop", u.pkg) != null) Log.i("Tandem", "Closed companion app ${u.pkg}") }, "companion-app").start()
    }
}
