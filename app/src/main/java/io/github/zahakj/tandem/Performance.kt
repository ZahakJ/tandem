package io.github.zahakj.tandem

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.BackupFiles
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.Store
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/*
 * Performance profiles on the AYN Thor: the performance mode, fan mode and refresh rate a game plays with.
 *
 * AYN's control centre keeps its modes in Android's system settings (`performance_mode`, `fan_mode`,
 * `peak_refresh_rate` / `min_refresh_rate`), and a system service applies each one a moment after it changes. Tandem
 * writes the same keys with the WRITE_SETTINGS permission it already asks for (Quick settings' brightness): at
 * Sessions.start the game's profile (its own, else Settings › Play & system › Performance & sleep › Performance mode, Fan mode and Refresh rate)
 * goes in, and at Sessions.take what was there before comes back. What was changed is written down first
 * (`performance.json`, in the no-backup folder), so a Tandem restart puts it back unless a game is still being played,
 * and a mode the user changed themselves mid-game is left as they set it: only values still Tandem's go back.
 *
 * Devices without a `performance_mode` setting aren't AYN devices: the rows hide behind a note and the keys are never
 * written. AYN's custom fan speed (fan mode 4) lives in AYN's own app, so Tandem offers Off to Sport and leaves a
 * custom speed as it finds it.
 *
 * [PerformancePlan] is plain, for tests (PerformanceTest).
 */

/**
 * A profile: what to set while a game plays. Null leaves that one as it is (Keep). In a game's own profile null means
 * "use the default" instead, and [KEEP] says Keep outright; [resolve] turns the two into one profile.
 */
@Serializable
data class PerfProfile(val mode: Int? = null, val fan: Int? = null, val hz: Int? = null) {
    val any: Boolean get() = mode != null || fan != null || hz != null

    companion object {
        /** In a game's own profile: keep this one as it is, whatever the default says. */
        const val KEEP = -1

        /** The profile for a game: its own choices ([over], null fields use the default) over the global [base]. */
        fun resolve(over: PerfProfile?, base: PerfProfile): PerfProfile {
            fun pick(o: Int?, b: Int?): Int? = (o ?: b)?.takeIf { it != KEEP }
            return PerfProfile(pick(over?.mode, base.mode), pick(over?.fan, base.fan), pick(over?.hz, base.hz))
        }
    }
}

/** One value Tandem changed: what it was ([from]) and what Tandem wrote ([to]). */
@Serializable
data class PerfChange(val from: Int, val to: Int)

/** What Tandem changed, to put it back, and the profile it was for ([wanted], shown while the game plays). */
@Serializable
data class PerfChanges(val mode: PerfChange? = null, val fan: PerfChange? = null, val hz: PerfChange? = null, val wanted: PerfProfile? = null) {
    val any: Boolean get() = mode != null || fan != null || hz != null
}

/** AYN's modes, as far as Tandem reads and sets them. Getters return null when a value can't be read; setters return whether it worked. */
interface PerfDevice {
    /** The performance mode, or null when the setting doesn't exist (not an AYN device). */
    fun mode(): Int?
    fun setMode(v: Int): Boolean
    fun fan(): Int?
    fun setFan(v: Int): Boolean
    /** The refresh rate in Hz, rounded. */
    fun hz(): Int?
    fun setHz(v: Int): Boolean
    /** The refresh rates the display really has, rounded, lowest first. */
    fun supportedHz(): List<Int>
}

object PerformancePlan {
    /** AYN's performance modes, each with its name's string. */
    val modes = listOf(0 to R.string.play_perf_standard, 1 to R.string.play_perf_performance, 2 to R.string.play_perf_high)
    /** The fan modes. 4 (Custom) is AYN's own, with its speed in AYN's app, so it is never offered or written. */
    val fans = listOf(0 to R.string.play_perf_fan_off, 1 to R.string.play_perf_fan_quiet, 2 to R.string.play_perf_fan_smart, 3 to R.string.play_perf_fan_sport)
    const val FAN_CUSTOM = 4

    /** An AYN device: the performance mode setting exists. */
    fun isAyn(d: PerfDevice): Boolean = d.mode() != null

    /**
     * Puts [want] in, given what was already changed ([had], a restore that failed earlier, or an earlier game).
     * A value already as wanted is left alone and not written down, so it is never "put back". A custom fan speed is
     * left alone. A refresh rate the display doesn't have is skipped. On a device that isn't an AYN nothing is
     * written. Returns what is to put back later.
     */
    fun begin(had: PerfChanges, want: PerfProfile, d: PerfDevice): PerfChanges {
        if (!isAyn(d)) return had
        var c = had.copy(wanted = want.takeIf { it.any })
        want.mode?.let { v -> c = c.copy(mode = change(c.mode, v, d.mode(), d::setMode)) }
        want.fan?.let { v ->
            val now = d.fan()
            if (now != FAN_CUSTOM && v in fans.map { it.first }) c = c.copy(fan = change(c.fan, v, now, d::setFan))
        }
        want.hz?.let { v -> if (v in d.supportedHz()) c = c.copy(hz = change(c.hz, v, d.hz(), d::setHz)) }
        return c
    }

    /** One value: written when it differs, with the original kept from an earlier change when there was one. */
    private fun change(had: PerfChange?, want: Int, now: Int?, set: (Int) -> Boolean): PerfChange? {
        now ?: return had
        if (now == want) return had?.copy(to = want)
        if (!set(want)) return had
        return PerfChange(had?.from ?: now, want)
    }

    /**
     * Puts back what [had] says was changed. A value that is no longer what Tandem wrote was changed by the user
     * mid-game and stays theirs. Returns what is still to put back (a step that failed, to try again); the profile
     * shown while playing is gone either way.
     */
    fun end(had: PerfChanges, d: PerfDevice): PerfChanges {
        var c = had.copy(wanted = null)
        c.mode?.let { if (back(it, d.mode(), d::setMode)) c = c.copy(mode = null) }
        c.fan?.let { if (back(it, d.fan(), d::setFan)) c = c.copy(fan = null) }
        c.hz?.let { if (back(it, d.hz(), d::setHz)) c = c.copy(hz = null) }
        return c
    }

    /** True when there is nothing left to do for this change: put back, or the user's now. */
    private fun back(ch: PerfChange, now: Int?, set: (Int) -> Boolean): Boolean {
        now ?: return true
        if (now != ch.to) return true
        return set(ch.from)
    }

    fun modeName(v: Int): String = modes.firstOrNull { it.first == v }?.let { Tx.s(it.second) } ?: Tx.s(R.string.play_perf_mode_n, v)
    fun fanName(v: Int): String = fans.firstOrNull { it.first == v }?.let { Tx.s(it.second) } ?: Tx.s(R.string.play_perf_fan_n, v)
    fun hzName(v: Int): String = Tx.s(R.string.play_perf_hz, v)

    /** "High performance · Sport · 120 Hz": what [p] sets, or null when it keeps everything. The names can be given (tests). */
    fun label(
        p: PerfProfile?, mode: (Int) -> String = ::modeName, fan: (Int) -> String = ::fanName, hz: (Int) -> String = ::hzName,
    ): String? {
        p ?: return null
        val parts = listOfNotNull(p.mode?.let(mode), p.fan?.let(fan), p.hz?.let(hz))
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

/**
 * AYN's settings through Android's system settings. Reading needs nothing. Writing is the catch: the settings
 * provider lets an app with WRITE_SETTINGS change only Android's own handful of system keys; a vendor key like
 * `performance_mode` (and `peak_refresh_rate` with it) is refused with an exception however the permission stands
 * (seen on the Thor, `enforceRestrictedSystemSettingsMutationForCallingPackage`). So each write tries the API first
 * and then goes through Shizuku's shell (`settings put system …`, which the shell user may do), and counts only when
 * the value reads back as written.
 */
private class AynDevice(private val ctx: Context) : PerfDevice {
    private val cr = ctx.contentResolver

    private fun int(key: String): Int? = try { Settings.System.getInt(cr, key) } catch (e: Settings.SettingNotFoundException) { null } catch (t: Throwable) { null }

    /** Writes [value] (an int or a float as text) by the API, else through Shizuku, and checks it took. */
    private fun write(key: String, value: String, api: () -> Boolean, readsBack: () -> Boolean): Boolean {
        if (Settings.System.canWrite(ctx) && runCatching { api() }.getOrDefault(false) && readsBack()) return true
        if (!Shell.ready()) return false
        Shell.run("settings", "put", "system", key, value) ?: return false
        return readsBack()
    }

    private fun put(key: String, v: Int): Boolean = write(key, v.toString(), { Settings.System.putInt(cr, key, v) }) { int(key) == v }

    override fun mode(): Int? = int(Performance.KEY_MODE)
    override fun setMode(v: Int): Boolean = put(Performance.KEY_MODE, v)
    override fun fan(): Int? = int(Performance.KEY_FAN)
    override fun setFan(v: Int): Boolean = put(Performance.KEY_FAN, v)

    override fun hz(): Int? = try { Settings.System.getFloat(cr, Performance.KEY_PEAK).roundToInt() } catch (t: Throwable) { null }

    /** Both bounds to the display's own value for that rate (60.000004, not 60), so the panel really takes it. */
    override fun setHz(v: Int): Boolean {
        val real = modes().firstOrNull { it.refreshRate.roundToInt() == v }?.refreshRate ?: return false
        val peak = write(Performance.KEY_PEAK, real.toString(), { Settings.System.putFloat(cr, Performance.KEY_PEAK, real) }) { hz() == v }
        val min = write(Performance.KEY_MIN, real.toString(), { Settings.System.putFloat(cr, Performance.KEY_MIN, real) }) {
            runCatching { Settings.System.getFloat(cr, Performance.KEY_MIN).roundToInt() == v }.getOrDefault(false)
        }
        return peak && min
    }

    override fun supportedHz(): List<Int> = modes().map { it.refreshRate.roundToInt() }.distinct().sorted()

    private fun modes(): List<Display.Mode> =
        runCatching { ctx.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.supportedModes?.toList() }.getOrNull().orEmpty()
}

object Performance {
    const val FILE = "performance.json"
    const val KEY_MODE = "performance_mode"
    const val KEY_FAN = "fan_mode"
    const val KEY_PEAK = "peak_refresh_rate"
    const val KEY_MIN = "min_refresh_rate"
    private const val TAG = "Tandem"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    /** Every change runs here, one at a time and in order. */
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "tandem-performance") }
    /**
     * AYN's own assistant applies its per-app modes the moment a game comes to the front, over anything written at
     * that same moment (seen on the Thor: a write at the session start was back to the old value a second later,
     * a write six seconds in stayed). So the profile is written a little after the start, and asserted once more
     * a few seconds later in case the assistant was still slower.
     */
    private const val SETTLE_MS = 3_000L
    private const val REASSERT_MS = 9_000L
    /** Bumped by every start and end, so a pending write for a session that is over does nothing. */
    @Volatile private var generation = 0
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var file: File? = null

    /** The profile in force for the game being played, for the bottom screen; null when none. Main thread. */
    var active by mutableStateOf<PerfProfile?>(null)
        private set

    /** Whether this is an AYN device (the performance mode setting exists); null until asked. Cheap, cached. */
    private var ayn: Boolean? = null

    private fun device(ctx: Context): PerfDevice = AynDevice(ctx.applicationContext)

    fun available(ctx: Context): Boolean = ayn ?: PerformancePlan.isAyn(device(ctx)).also { ayn = it }

    /** Android lets Tandem write system settings (the same permission Quick settings' brightness uses). */
    /** Whether a profile can be written: through Shizuku (what AYN's keys need in practice), or the settings permission. */
    fun canWrite(ctx: Context): Boolean = Shell.ready() || Settings.System.canWrite(ctx)

    /** Android's page where the user allows Tandem to change system settings. */
    fun settingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The refresh rates the display has, for the rows to offer. The panel doesn't change, so it is asked once. */
    private var hz: List<Int>? = null
    fun supportedHz(ctx: Context): List<Int> = hz ?: device(ctx).supportedHz().also { hz = it }

    /** The profile [game] plays with: its own choices over the global one. */
    fun profileFor(game: Game): PerfProfile = PerfProfile.resolve(Store.get(game.key).perf, HomeStore.settings.perf)

    private fun file(ctx: Context): File = file ?: File(ctx.applicationContext.noBackupFilesDir, FILE).also { file = it }

    private fun load(f: File): PerfChanges = runCatching { json.decodeFromString<PerfChanges>(f.readText()) }.getOrDefault(PerfChanges())

    private fun save(f: File, c: PerfChanges) {
        runCatching { if (c.any || c.wanted != null) BackupFiles.atomicWrite(f, json.encodeToString(c)) else f.delete() }
        main.post { active = c.wanted }
    }

    /** The game's session began (Sessions.start): its profile goes in. Any thread; returns at once. */
    fun started(ctx: Context, game: Game) {
        val app = ctx.applicationContext
        val want = profileFor(game)
        val f = file(ctx)
        val gen = ++generation
        fun apply(again: Boolean) {
            if (gen != generation) return
            val d = device(app)
            if (!PerformancePlan.isAyn(d)) return
            if (want.any && !canWrite(app)) { Log.i(TAG, "Performance profile needs Shizuku (or system settings access)"); return }
            val c = PerformancePlan.begin(load(f), want, d)
            save(f, c)
            if (want.any) Log.i(TAG, (if (again) "Performance profile asserted again: " else "Performance profile for the game: ") + PerformancePlan.label(want))
        }
        worker.schedule({ apply(false) }, SETTLE_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        worker.schedule({ apply(true) }, REASSERT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /** The game's session is over: the modes back as they were. Any thread; returns at once. */
    fun gameEnded(ctx: Context) {
        val app = ctx.applicationContext
        val f = file(ctx)
        generation++
        worker.execute {
            val had = load(f)
            if (!had.any && had.wanted == null) return@execute
            val left = PerformancePlan.end(had, device(app))
            save(f, left)
            Log.i(TAG, if (left.any) "Couldn't put the performance modes back yet" else "Performance modes back as they were")
        }
    }

    /**
     * At startup: changes left from before a restart are put back unless a game is still being played, in which case
     * its profile shows again. The companion calls this too, for a process Android started afresh mid-game.
     */
    fun startup(ctx: Context) {
        val f = file(ctx)
        if (Sessions.openKey(ctx) == null) gameEnded(ctx)
        else worker.execute { val c = load(f); main.post { active = c.wanted } }
    }
}
