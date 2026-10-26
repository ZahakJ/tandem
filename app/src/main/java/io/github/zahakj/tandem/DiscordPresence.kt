package io.github.zahakj.tandem

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.BackupFiles
import io.github.zahakj.tandem.data.Covers
import io.github.zahakj.tandem.ui.Toasts
import org.json.JSONObject
import java.io.File

/** What Discord shows while a game plays: "Playing [title]", the [system] under it, the time since [startSec]. */
data class DiscordActivity(val title: String, val system: String, val startSec: Long, val image: String)

/**
 * The Discord Social SDK side, one per build: src/discord/java with the SDK, src/nodiscord/java without it (see
 * app/build.gradle.kts). Every call returns at once; the work runs on the backend's own thread.
 */
interface DiscordSdk {
    /** False in builds without the SDK: nothing happens and the setting doesn't show. */
    val present: Boolean

    /** Opens Discord (or a browser) to authorize Tandem; [Host.keep] with linked = true follows, or [Host.linkFailed]. */
    fun authorize(activity: Activity)

    /** Shows [activity] as the user's status, connecting first if needed, or clears it (null) and hangs up soon after. */
    fun show(activity: DiscordActivity?)

    /** Clears the status, disconnects and revokes [refreshToken] with Discord. */
    fun unlink(refreshToken: String?)

    /** What the backend asks of the app. Called on the backend's thread. */
    interface Host {
        val appId: Long
        /** An activity for the SDK's Android setup and sign-in, or null when Tandem has none. */
        fun activity(): Activity?
        fun refreshToken(): String?
        /** A new refresh token to keep; [linked] when it comes from the user authorizing just now. */
        fun keep(refreshToken: String, linked: Boolean)
        fun linkFailed(reason: String)
        /** Discord no longer accepts the refresh token (revoked from Discord, expired): forget it. */
        fun forget()
    }
}

/**
 * Discord status: while a game plays, the user's Discord status says "Playing <game>" with its system and the time
 * played, and it clears when play stops. Off until the user connects their Discord account (Settings › Art &
 * accounts › Discord), which goes through Discord's own authorization (OAuth2 with PKCE, as a public client): Tandem
 * never sees a password or a user token. Once connected, the quick panel's Discord tile pauses it ([setShown])
 * without disconnecting.
 *
 * Linked means a refresh token in `discord.json` in [Context.getNoBackupFilesDir]: never in Tandem's backups (Backup.kt
 * lists its files), Android's backups or any export. Hidden games, games in hidden folders or systems, and anything
 * while private mode is on are never sent. Play follows the play session (Sessions in Launcher.kt): [playing] when it
 * starts, [stopped] when Tandem comes back or [GameExit] sees the game gone. Without a link nothing runs at all.
 */
object DiscordPresence : DiscordSdk.Host {
    private const val TAG = "Tandem"
    private const val FILE = "discord.json"
    /**
     * The picture when no cover address is known ([Covers]): the Tandem icon from the project site. Discord takes an
     * https address in place of an uploaded asset.
     */
    private const val ART = "https://zahakj.github.io/tandem/assets/brand/icon-512.png"

    override val appId: Long = BuildConfig.DISCORD_APP_ID
    private val sdk: DiscordSdk by lazy { discordBackend(this) }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var app: Context? = null

    /** This build can show a Discord status: the SDK is compiled in and the application ID is set. */
    val available: Boolean get() = appId != 0L && sdk.present

    /** A Discord account is connected. Read by [refresh] off the main thread; false until then. */
    var linked by mutableStateOf(false)
        private set
    /** The status is shown while playing (the quick panel's tile); kept in `discord.json` with the token. */
    var shown by mutableStateOf(true)
        private set
    @Volatile private var checked = false
    /** The game of the play session going on, and when it began (main thread). */
    private var current: Game? = null
    private var currentStartMs = 0L
    /** A status was sent for the current play session (main thread). */
    private var showing = false

    private fun file(ctx: Context): File = File(ctx.noBackupFilesDir, FILE)

    /** Reads whether an account is linked. A file read: call off the main thread (Settings does, when it opens). */
    fun refresh(ctx: Context) {
        if (!available) return
        app = ctx.applicationContext
        val f = file(ctx)
        linked = f.isFile
        if (linked) shown = runCatching { JSONObject(f.readText()).optBoolean("show", true) }.getOrDefault(true)
        checked = true
    }

    /** A play session of [game] began at [startMs]. Main thread; costs one file check per process when unlinked. */
    fun playing(ctx: Context, game: Game, startMs: Long) {
        if (!available) return
        app = ctx.applicationContext
        if (!checked) refresh(ctx)
        if (!linked) return
        current = game
        currentStartMs = startMs
        show()
    }

    /** The play session ended (or the game is gone). Main thread. */
    fun stopped() {
        current = null
        hide()
    }

    /** Shows the status while playing, or pauses it (clearing it at once); the connection to the account stays. */
    fun setShown(ctx: Context, on: Boolean) {
        if (!available || !linked) return
        app = ctx.applicationContext
        shown = on
        if (on) show() else hide()
        val f = file(ctx)
        Thread({ read(f)?.let { write(f, it, on) } }, "tandem-discord-save").start()
    }

    private fun show() {
        val game = current ?: return
        if (!shown || Privacy.enabled || Privacy.isHidden(game)) { hide(); return }
        showing = true
        val cover = Covers.known(game)
        sdk.show(DiscordActivity(game.title, game.platform.name, currentStartMs / 1000, cover ?: ART))
        // No cover yet: the icon shows meanwhile, and the cover replaces it once found (show checks privacy again).
        if (cover == null) app?.let { ctx -> Covers.resolve(ctx, game) { main.post { if (current === game && showing) show() } } }
    }

    private fun hide() {
        if (!showing) return
        showing = false
        sdk.show(null)
    }

    /** Starts the authorization in Discord or a browser. */
    fun link(activity: Activity) {
        if (!available) return
        app = activity.applicationContext
        sdk.authorize(activity)
    }

    /** Disconnects: clears the status, forgets the tokens and revokes them with Discord. */
    fun unlink(ctx: Context) {
        if (!available) return
        app = ctx.applicationContext
        showing = false
        linked = false
        val f = file(ctx)
        Thread({
            val token = read(f)
            f.delete()
            sdk.unlink(token)
        }, "tandem-discord-unlink").start()
    }

    override fun activity(): Activity? = MainActivity.live?.get()

    override fun refreshToken(): String? = app?.let { read(file(it)) }

    override fun keep(refreshToken: String, linked: Boolean) {
        val ctx = app ?: return
        val f = file(ctx)
        // A refresh that lands after Disconnect must not link the account again.
        if (!linked && !f.isFile) return
        write(f, refreshToken, if (linked) true else shown)
        if (linked) main.post {
            this.linked = true
            shown = true
            checked = true
            Toasts.show("Discord connected. Your status shows what you play")
        }
    }

    override fun linkFailed(reason: String) {
        Log.w(TAG, "Discord authorization failed: $reason")
        main.post { Toasts.show(if (reason == "canceled") "Discord wasn't connected" else "Couldn't connect to Discord. Try again in a moment") }
    }

    override fun forget() {
        Log.w(TAG, "Discord no longer accepts Tandem's token: disconnected")
        app?.let { file(it).delete() }
        main.post { linked = false; showing = false }
    }

    @Synchronized
    private fun write(f: File, refreshToken: String, show: Boolean) =
        BackupFiles.atomicWrite(f, JSONObject().put("refresh", refreshToken).put("show", show).toString())

    private fun read(f: File): String? =
        runCatching { JSONObject(f.readText()).optString("refresh").ifEmpty { null } }.getOrNull()
}
