package io.github.zahakj.tandem

import android.content.Context
import android.util.Log
import io.github.zahakj.tandem.data.BackupFiles
import io.github.zahakj.tandem.data.HomeStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/*
 * Private mode keeps Tandem off the network, and can take the whole handheld offline while a hidden game plays.
 *
 * - [NetGate]: while private mode is on, every request Tandem itself makes (scraping and its downloads, the LaunchBox
 *   database, RetroAchievements, cover lookups, remote pictures) stops before it leaves the device. Nothing is kept to
 *   send later. The Discord status already shows nothing in private mode (DiscordPresence.show).
 * - [Offline]: with Settings › Play & stats › Go offline while a hidden game plays, starting a hidden game (only
 *   possible once the PIN revealed it) turns airplane mode on through Shizuku, and Wi-Fi off when Android keeps it on
 *   in airplane mode. It fails closed: when the handheld can't be confirmed offline (no Shizuku, a radio that won't
 *   switch) the game doesn't start, and Pip offers to start it anyway. What Tandem changed is written down at once
 *   (`offline.json`, in the no-backup folder, with the emulator's package), and put back when the game's session
 *   ends: Tandem comes back, the exit watch sees the game gone, or Tandem restarts with no game open. With Stay
 *   offline until the game closes (on by default), a session ending while the emulator is still running (left in the
 *   background, where it could still sync) keeps the handheld offline until its process is gone, checked every
 *   [RECHECK_S] seconds, or until hidden games are tucked away again. Airplane mode the user had on already is never
 *   turned off. Emulators' own network features are outside Tandem: only airplane mode stops those.
 *
 * [OfflinePlan] is plain, for tests (OfflineTest).
 */

/** Thrown instead of going online while private mode is on. */
class PrivateOffline : IOException("Private mode is on, so Tandem stays offline")

object NetGate {
    /** True while nothing may go online. */
    val closed: Boolean get() = HomeStore.settings.privateMode

    /** Throws [PrivateOffline] while [closed]. Every request Tandem makes calls this first. */
    fun check() { if (closed) throw PrivateOffline() }
}

/** What Tandem changed to go offline, so it can be put back, and the app the hidden game plays in ([pkg]). */
@Serializable
data class OfflineChanges(val airplane: Boolean = false, val wifi: Boolean = false, val pkg: String? = null) {
    val any: Boolean get() = airplane || wifi
}

/** The handheld's radios. Getters return null when they couldn't tell; setters return whether it worked. */
interface Radios {
    fun airplane(): Boolean?
    fun setAirplane(on: Boolean): Boolean
    fun wifi(): Boolean?
    fun setWifi(on: Boolean): Boolean
}

object OfflinePlan {
    /**
     * Goes offline, given what was already changed ([had]). Airplane mode the user turned on is left alone (and so
     * never turned off later); Wi-Fi still on in airplane mode goes off. Already offline for an earlier game: as is.
     */
    fun begin(had: OfflineChanges, r: Radios): OfflineChanges {
        if (had.any) return had
        var c = had
        when (r.airplane()) {
            null -> return had
            false -> if (r.setAirplane(true)) c = c.copy(airplane = true)
            true -> Unit
        }
        if (r.wifi() == true && r.setWifi(false)) c = c.copy(wifi = true)
        return c
    }

    /** Puts back what [had] says was changed. Returns what is still to put back (a step that failed, to try again). */
    fun end(had: OfflineChanges, r: Radios): OfflineChanges {
        var c = had
        if (c.airplane && r.setAirplane(false)) c = c.copy(airplane = false)
        // Wi-Fi after airplane mode: turned on during it, Android could turn it off again.
        if (c.wifi && !c.airplane && r.setWifi(true)) c = c.copy(wifi = false)
        return c
    }

    /** Offline as wanted: airplane mode on and Wi-Fi off. */
    fun isOffline(r: Radios): Boolean = r.airplane() == true && r.wifi() == false

    /**
     * Whether a session that ended still keeps the handheld offline: [untilClosed] is on and the hidden game's app
     * ([had].pkg) is still running ([alive]). When Shizuku can't tell (null) it stays offline: never online by guess.
     */
    fun hold(had: OfflineChanges, untilClosed: Boolean, alive: Boolean?): Boolean =
        untilClosed && had.any && had.pkg != null && alive != false
}

/** The radios through Shizuku's shell (Android 13's `cmd connectivity` and `cmd wifi`). Blocking. */
object ShellRadios : Radios {
    override fun airplane(): Boolean? = when (Shell.run("cmd", "connectivity", "airplane-mode")?.trim()) {
        "enabled" -> true
        "disabled" -> false
        else -> null
    }

    override fun setAirplane(on: Boolean): Boolean {
        Shell.run("cmd", "connectivity", "airplane-mode", if (on) "enable" else "disable") ?: return false
        return airplane() == on
    }

    override fun wifi(): Boolean? {
        val out = Shell.run("cmd", "wifi", "status") ?: return null
        return when {
            "Wifi is enabled" in out -> true
            "Wifi is disabled" in out -> false
            else -> null
        }
    }

    override fun setWifi(on: Boolean): Boolean {
        val out = Shell.run("cmd", "wifi", "set-wifi-enabled", if (on) "enabled" else "disabled") ?: return false
        // Quiet when it worked.
        return !out.contains("exception", ignoreCase = true) && !out.contains("error", ignoreCase = true)
    }
}

object Offline {
    const val FILE = "offline.json"
    private const val TAG = "Tandem"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    /** How often a hidden game's app left running is looked for, while it keeps the handheld offline. */
    const val RECHECK_S = 20L

    /** Every radio change runs here, one at a time and in order. */
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "tandem-offline") }
    @Volatile private var recheck: ScheduledFuture<*>? = null
    /** The game "Start anyway" was chosen for: its next launch skips the offline check once. */
    @Volatile private var anyway: String? = null
    @Volatile private var file: File? = null

    private fun file(ctx: Context): File =
        file ?: File(ctx.applicationContext.noBackupFilesDir, FILE).also { file = it }

    private fun load(f: File): OfflineChanges = runCatching { json.decodeFromString<OfflineChanges>(f.readText()) }.getOrDefault(OfflineChanges())

    private fun save(f: File, c: OfflineChanges) {
        runCatching { if (c.any) BackupFiles.atomicWrite(f, json.encodeToString(c)) else f.delete() }
    }

    /** Whether starting [game] takes the handheld offline. */
    fun wanted(game: Game): Boolean = Privacy.enabled && HomeStore.settings.offlineHidden && Privacy.isHidden(game)

    /**
     * A launch is under way: the session it ends (Launcher.start ends the last one on the main thread, a moment later)
     * must not bring the radios back for the game now starting. Cleared once its session starts, or the launch fails.
     */
    @Volatile private var launching = false

    /** The next launch of [game] goes ahead even if the handheld can't be confirmed offline (Pip's Start anyway). */
    fun allowOnce(game: Game) { anyway = game.key }

    /**
     * Before [game] starts, in [pkg]. Blocking (Shizuku), so a hidden game never starts online: call it from the
     * launch thread. Waits at most a few seconds for the radios. Returns false when the game wants the handheld
     * offline and it couldn't be confirmed, so it mustn't start (unless [allowOnce]). Any other game puts back what an
     * earlier hidden one changed, unless that one's app is still running ([hold]).
     */
    fun beforeLaunch(ctx: Context, game: Game, pkg: String?): Boolean {
        launching = true
        val f = file(ctx)
        val skip = anyway == game.key
        anyway = null
        if (!wanted(game)) { worker.execute { restore(f) }; return true }
        val task = worker.submit<Boolean> {
            cancelRecheck()
            if (!Shell.ready()) { Log.w(TAG, "Can't go offline for a hidden game: Shizuku isn't available"); return@submit false }
            val c = OfflinePlan.begin(load(f), ShellRadios).let { if (it.any) it.copy(pkg = pkg) else it }
            save(f, c)
            val ok = OfflinePlan.isOffline(ShellRadios)
            Log.i(TAG, if (!ok) "Couldn't confirm offline for a hidden game" else "Offline for a hidden game" + if (c.any) "" else " (it already was)")
            ok
        }
        val ok = runCatching { task.get(8, TimeUnit.SECONDS) }.getOrDefault(false)
        if (!ok && skip) Log.w(TAG, "Starting a hidden game without confirming offline, as chosen")
        return ok || skip
    }

    /** The game's session began (Sessions.start). */
    fun started() { launching = false }

    /** The launch didn't happen: back as before. */
    fun launchFailed(ctx: Context) {
        launching = false
        gameEnded(ctx)
    }

    /** The game's session is over: puts the radios back as they were. Any thread; returns at once. */
    fun gameEnded(ctx: Context) {
        if (launching) return
        val f = file(ctx)
        worker.execute { restore(f) }
    }

    /** Hidden games were tucked away again: back online now, even with the hidden game's app still running. */
    fun tuckedAway() {
        val f = file ?: return
        worker.execute { restore(f, force = true) }
    }

    /** On [worker]. Unless [force], a hidden game's app still running keeps the handheld offline ([OfflinePlan.hold]). */
    private fun restore(f: File, force: Boolean = false) {
        cancelRecheck()
        val had = load(f)
        if (!had.any) return
        val ready = Shell.waitReady(3_000)
        if (!force) {
            val alive = if (ready && had.pkg != null) Processes.alive(had.pkg, maxAgeMs = 0) else null
            if (OfflinePlan.hold(had, HomeStore.settings.offlineUntilClosed, alive)) {
                Log.i(TAG, "Staying offline: the hidden game's app is still running")
                recheck = worker.schedule({ restore(f) }, RECHECK_S, TimeUnit.SECONDS)
                return
            }
        }
        if (!ready) return
        val left = OfflinePlan.end(had, ShellRadios)
        save(f, left)
        Log.i(TAG, if (left.any) "Couldn't put the radios back yet" else "Back online after a hidden game")
    }

    private fun cancelRecheck() { recheck?.cancel(false); recheck = null }

    /** At startup: changes left from before a restart are put back unless a game is still being played. */
    fun startup(ctx: Context) {
        file(ctx)
        if (Sessions.openKey(ctx) == null) gameEnded(ctx)
    }
}
