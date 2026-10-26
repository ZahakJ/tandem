package io.github.zahakj.tandem

import android.app.Activity
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import com.discord.socialsdk.DiscordSocialSdkInit

/** Builds with Discord's Social SDK in third_party/discord (see app/build.gradle.kts). */
fun discordBackend(host: DiscordSdk.Host): DiscordSdk = SdkBackend(host)

/**
 * Drives the SDK through Tandem's small JNI bridge (src/discord/cpp). Everything, the SDK's callbacks included, runs on
 * one background thread, started the first time it is needed. It ticks the SDK (`RunCallbacks`) only while something
 * is under way: 10 times a second while waiting for an answer, once a second while a status shows, never when idle.
 * After play stops the status is cleared and the connection kept for [LINGER_MS], for a quick next game.
 */
private class SdkBackend(private val host: DiscordSdk.Host) : DiscordSdk, DiscordNative.Events {
    companion object {
        private const val TAG = "Tandem"
        private const val LINGER_MS = 60_000L
        /** A sign-in left open in Discord or the browser stops being waited for. */
        private const val AUTHORIZE_WAIT_MS = 10 * 60_000L
        // Client::Status in discordpp.h.
        private const val DISCONNECTED = 0
        private const val READY = 3
    }

    override val present = true

    private val handler by lazy {
        Handler(HandlerThread("tandem-discord", Process.THREAD_PRIORITY_BACKGROUND).apply { start() }.looper)
    }

    // Owned by the backend's thread.
    private var loaded = false
    private var status = DISCONNECTED
    private var access: String? = null
    private var accessUntil = 0L
    private var wanted: DiscordActivity? = null
    /** Pictures Discord refused; an update with one of them goes without a picture. */
    private val refused = HashSet<String>()
    /** The picture of the last update sent. */
    private var sentImage: String? = null
    /** Requests whose answer hasn't come yet (sign-in, token, connecting, a status update, a revoke). */
    private var waiting = 0
    private var authorizing = false
    private var refreshing = false
    private var connecting = false
    private var retried = false
    private var ticking = false

    override fun authorize(activity: Activity) = post {
        if (authorizing || !load(activity)) return@post
        authorizing = true
        waiting++
        DiscordNative.authorize()
        handler.postDelayed(giveUpAuthorize, AUTHORIZE_WAIT_MS)
        tick()
    }

    override fun show(activity: DiscordActivity?) = post {
        wanted = activity
        handler.removeCallbacks(hangUp)
        if (activity == null) {
            if (loaded && status != DISCONNECTED) {
                DiscordNative.clear()
                handler.postDelayed(hangUp, LINGER_MS)
            }
            return@post
        }
        if (!load(host.activity() ?: return@post)) return@post
        retried = false
        when {
            status == READY -> send(activity)
            status == DISCONNECTED && !connecting -> connect()
            // Connecting: the status update follows Ready.
        }
        tick()
    }

    override fun unlink(refreshToken: String?) = post {
        wanted = null
        handler.removeCallbacks(hangUp)
        access = null
        if (loaded && status != DISCONNECTED) {
            DiscordNative.clear()
            DiscordNative.disconnect()
        }
        if (refreshToken != null && (loaded || host.activity()?.let { load(it) } == true)) {
            waiting++
            DiscordNative.revoke(refreshToken)
        }
        if (loaded) tick()
    }

    private fun post(block: () -> Unit) { handler.post(block) }

    /** Loads the SDK and the bridge and creates the client, once. The SDK wants an activity for its Android setup. */
    private fun load(activity: Activity): Boolean {
        if (loaded) return true
        return try {
            DiscordSocialSdkInit.setEngineActivity(activity)
            System.loadLibrary("tandem_discord")
            DiscordNative.events = this
            DiscordNative.create(host.appId)
            loaded = true
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Discord SDK unavailable", t)
            false
        }
    }

    private fun connect() {
        val token = access
        if (token != null && System.currentTimeMillis() < accessUntil) {
            connecting = true
            DiscordNative.connect(token, true)
        } else refreshToken()
    }

    private fun refreshToken() {
        if (refreshing) return
        val token = host.refreshToken() ?: return
        refreshing = true
        waiting++
        DiscordNative.refresh(token)
    }

    private fun send(a: DiscordActivity) {
        waiting++
        // Discord takes 2 to 128 characters per text; a shorter one is left out.
        fun fit(s: String) = s.trim().take(128).takeIf { it.length >= 2 }
        sentImage = a.image.takeUnless { it in refused }
        DiscordNative.update(
            fit(a.title)?.toByteArray(), fit(a.system)?.toByteArray(), a.startSec, sentImage?.toByteArray(),
            fit(listOf(a.title, a.system).filter { it.isNotBlank() }.joinToString(" · "))?.toByteArray(),
        )
    }

    private val hangUp = Runnable { if (wanted == null && status != DISCONNECTED) DiscordNative.disconnect() }

    private val giveUpAuthorize = Runnable {
        if (!authorizing) return@Runnable
        authorizing = false
        waiting = (waiting - 1).coerceAtLeast(0)
    }

    private val tickTask = object : Runnable {
        override fun run() {
            DiscordNative.tick()
            val busy = waiting > 0 || connecting || (status != DISCONNECTED && status != READY)
            if (busy || status == READY) handler.postDelayed(this, if (busy) 100 else 1000)
            else ticking = false
        }
    }

    private fun tick() {
        if (ticking) return
        ticking = true
        handler.post(tickTask)
    }

    // DiscordNative.Events: called during DiscordNative.tick, so on the backend's thread.

    override fun onTokens(ok: Boolean, access: String, refresh: String, expiresIn: Int, error: String, rejected: Boolean) {
        waiting = (waiting - 1).coerceAtLeast(0)
        val fromSignIn = authorizing
        authorizing = false
        refreshing = false
        if (fromSignIn) handler.removeCallbacks(giveUpAuthorize)
        if (!ok) {
            Log.w(TAG, "Discord token request failed: $error")
            when {
                fromSignIn -> host.linkFailed(error)
                rejected -> { wanted = null; host.forget() }
            }
            return
        }
        this.access = access
        accessUntil = System.currentTimeMillis() + expiresIn * 1000L - 60_000L
        host.keep(refresh, linked = fromSignIn)
        when {
            wanted == null -> Unit
            status == DISCONNECTED -> { connecting = true; DiscordNative.connect(access, true) }
            // Connected already (a token about to expire): just hand over the new one.
            else -> DiscordNative.connect(access, false)
        }
    }

    override fun onAuthorizeFailed(error: String) {
        waiting = (waiting - 1).coerceAtLeast(0)
        authorizing = false
        handler.removeCallbacks(giveUpAuthorize)
        host.linkFailed(error)
    }

    override fun onStatus(status: Int, error: Int, detail: Int) {
        this.status = status
        connecting = false
        Log.i(TAG, "Discord status $status" + if (error != 0) " (error $error, $detail)" else "")
        val a = wanted
        when {
            status == READY && a != null -> send(a)
            status == READY -> handler.postDelayed(hangUp, LINGER_MS)
            // Closed with an error while a status should show (often an expired token): one fresh token and retry.
            status == DISCONNECTED && error != 0 && a != null && !retried -> {
                retried = true
                access = null
                refreshToken()
            }
        }
    }

    override fun onTokenExpiring() = refreshToken()

    override fun onDone(ok: Boolean, error: String) {
        waiting = (waiting - 1).coerceAtLeast(0)
        if (!ok) Log.w(TAG, "Discord request failed: $error")
        // Discord turned the picture down: show the status without one rather than not at all.
        if (!ok && error.contains("image", ignoreCase = true)) sentImage?.let { if (refused.add(it)) wanted?.let(::send) }
    }
}

/** The JNI bridge (src/discord/cpp/discord_bridge.cpp). Texts go to it as UTF-8 bytes. */
internal object DiscordNative {
    interface Events {
        fun onTokens(ok: Boolean, access: String, refresh: String, expiresIn: Int, error: String, rejected: Boolean)
        fun onAuthorizeFailed(error: String)
        fun onStatus(status: Int, error: Int, detail: Int)
        fun onTokenExpiring()
        fun onDone(ok: Boolean, error: String)
    }

    @Volatile var events: Events? = null

    @JvmStatic external fun create(appId: Long)
    @JvmStatic external fun tick()
    @JvmStatic external fun authorize()
    @JvmStatic external fun refresh(refreshToken: String)
    /** Hands the client [accessToken], then connects when [connect]. */
    @JvmStatic external fun connect(accessToken: String, connect: Boolean)
    @JvmStatic external fun update(name: ByteArray?, details: ByteArray?, startSec: Long, image: ByteArray?, imageText: ByteArray?)
    @JvmStatic external fun clear()
    @JvmStatic external fun disconnect()
    @JvmStatic external fun revoke(token: String)

    // Called from the bridge, with texts as UTF-8 bytes.
    @JvmStatic fun tokens(ok: Boolean, access: ByteArray, refresh: ByteArray, expiresIn: Int, error: ByteArray, rejected: Boolean) =
        events?.onTokens(ok, String(access), String(refresh), expiresIn, String(error), rejected) ?: Unit
    @JvmStatic fun authorizeFailed(error: ByteArray) = events?.onAuthorizeFailed(String(error)) ?: Unit
    @JvmStatic fun status(status: Int, error: Int, detail: Int) = events?.onStatus(status, error, detail) ?: Unit
    @JvmStatic fun tokenExpiring() = events?.onTokenExpiring() ?: Unit
    @JvmStatic fun done(ok: Boolean, error: ByteArray) = events?.onDone(ok, String(error)) ?: Unit
}
