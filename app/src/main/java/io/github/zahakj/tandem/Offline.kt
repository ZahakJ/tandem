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
import java.util.concurrent.TimeUnit

/*
 * Private mode keeps Tandem off the network, and can take the whole handheld offline while a hidden game plays.
 *
 * - [NetGate]: while private mode is on, every request Tandem itself makes (scraping and its downloads, the LaunchBox
 *   database, RetroAchievements, cover lookups, remote pictures) stops before it leaves the device. Nothing is kept to
 *   send later. The Discord status already shows nothing in private mode (DiscordPresence.show).
 * - [Offline]: with Settings › Play & stats › Go offline while a hidden game plays, starting a hidden game (only
 *   possible once the PIN revealed it) turns airplane mode on through Shizuku, and Wi-Fi off when Android keeps it on
 *   in airplane mode. What Tandem changed is written down at once (`offline.json`, in the no-backup folder), and put
 *   back when the game's session ends: Tandem comes back, the exit watch sees the game gone, or Tandem restarts with
 *   no game open. Airplane mode the user had on already is never turned off. Emulators' own network features are
 *   outside Tandem: only airplane mode stops those, and only while it is on.
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

/** What Tandem changed to go offline, so it can be put back. */
@Serializable
data class OfflineChanges(val airplane: Boolean = false, val wifi: Boolean = false) {
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
    /** Every radio change runs here, one at a time and in order. */
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "tandem-offline") }
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

    /**
     * Before [game] starts. Blocking (Shizuku), so a hidden game never starts online: call it from the launch thread.
     * Waits at most a few seconds for the radios. Any other game puts back what an earlier hidden one changed.
     */
    fun beforeLaunch(ctx: Context, game: Game) {
        launching = true
        val f = file(ctx)
        if (!wanted(game)) { worker.execute { restore(f) }; return }
        val task = worker.submit {
            if (!Shell.ready()) { Log.w(TAG, "Can't go offline for a hidden game: Shizuku isn't available"); return@submit }
            val c = OfflinePlan.begin(load(f), ShellRadios)
            save(f, c)
            Log.i(TAG, "Offline for a hidden game" + if (c.any) "" else " (it already was)")
        }
        runCatching { task.get(8, TimeUnit.SECONDS) }
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

    /** On [worker]. */
    private fun restore(f: File) {
        val had = load(f)
        if (!had.any || !Shell.waitReady(3_000)) return
        val left = OfflinePlan.end(had, ShellRadios)
        save(f, left)
        Log.i(TAG, if (left.any) "Couldn't put the radios back yet" else "Back online after a hidden game")
    }

    /** At startup: changes left from before a restart are put back unless a game is still being played. */
    fun startup(ctx: Context) {
        if (Sessions.openKey(ctx) == null) gameEnded(ctx)
    }
}
