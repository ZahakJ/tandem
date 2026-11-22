package io.github.zahakj.tandem

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The bottom screen going dark while a game plays (GameCompanion.kt, ui/CompanionScreen.kt).
 *
 * "Screen off" is the biggest battery saving the bottom screen offers: the Thor's lower panel is an OLED, so a pure
 * black picture is pixels that emit nothing, and a panel that never redraws costs the CPU nothing either. It comes
 * as a mode of its own (the panel is dark from the moment the game starts; a tap lights it for a while) and as an
 * idle timer, "Off after", that turns the art or the companion dark after some minutes without a touch.
 *
 * What Android 13 offers for a second built-in display, and why this is pixels-off rather than display-off:
 * - A window's `WindowManager.LayoutParams.screenBrightness` is not per display on Android 13. The window manager
 *   keeps one brightness override for the whole device (`RootWindowContainer.handleNotObscuredLocked` takes the
 *   first on-screen window that sets one, on any display) and the power manager hands it to every display group.
 *   Set on the companion's window it would dim the top screen, where the game is, to its minimum. It only became
 *   per display in Android 15, so it isn't used here.
 * - `cmd display` on Android 13 has `set-brightness`, but only for the default display; `enable-display` /
 *   `disable-display` arrived in Android 14, for connected displays. Nothing in `cmd display`, `wm`, `svc` or
 *   `settings` turns one built-in display off while the other stays on, because both sit in the same power group
 *   (`dumpsys display`: `displayGroupId 0` on both), and a power group sleeps as a whole.
 * - The Thor's own Settings app has a second-screen switch (com.odin.settings), through a vendor path. [logFacts]
 *   writes what `dumpsys display` and the display-related settings keys say to logcat the first time a dark panel
 *   comes up with Shizuku, so that path can be found and taken up later without guessing.
 *
 * So on the Thor, Screen off is a black, still window. The brightness setting plays no part in what a black OLED
 * pixel costs, so nothing is lost by leaving it alone, and the game's screen keeps its brightness.
 *
 * The timer is a coroutine delay inside the companion while it is resumed: no wake lock, no alarm, nothing that
 * keeps the handheld from sleeping; a dark panel schedules nothing at all until it is touched.
 *
 * [show], [nextChangeMs] and [displayFacts] are plain, for tests (ScreenOffTest).
 */
object ScreenOff {
    /** The "Off after" choices, in minutes of no touch; 0 is never. */
    val OFF_AFTER_MINUTES = listOf(0, 1, 2, 5, 10)

    /** A minute without a touch dims the art or the companion. */
    const val DIM_AFTER_MS = 60_000L

    /** How long a touch lights a Screen off panel when Off after is Never. */
    const val LIT_MS = 60_000L

    /** What the panel shows: its face, bright; the face dimmed, drifting against burn-in; or nothing, black. */
    enum class Show { Bright, Dim, Dark }

    /** "Never" or "After 5 min", for the setting row. */
    fun offAfterLabel(minutes: Int): String =
        if (minutes <= 0) Tx.s(R.string.screens_off_never) else Tx.p(R.plurals.screens_off_after_minutes, minutes)

    /**
     * What the panel shows now. [dark]: the mode is Screen off. [offAfterMs]: the Off after setting, 0 for never.
     * [sinceTouchMs]: since the panel was last touched (or lit by Home bringing the game back), null when it hasn't
     * been since it came up. [sinceShownMs]: since it came up for this game. [reading]: a note is open; reading it,
     * or typing, isn't touching here, and a note never goes dark under the reader.
     */
    fun show(dark: Boolean, offAfterMs: Long, sinceTouchMs: Long?, sinceShownMs: Long, reading: Boolean): Show {
        if (reading) return Show.Bright
        if (dark) {
            val since = sinceTouchMs ?: return Show.Dark
            return if (since >= litFor(offAfterMs)) Show.Dark else Show.Bright
        }
        val idle = sinceTouchMs ?: sinceShownMs
        if (offAfterMs > 0 && idle >= offAfterMs) return Show.Dark
        return if (idle >= DIM_AFTER_MS) Show.Dim else Show.Bright
    }

    /** In how many ms [show] changes by itself; null when it won't until someone touches the panel. */
    fun nextChangeMs(dark: Boolean, offAfterMs: Long, sinceTouchMs: Long?, sinceShownMs: Long, reading: Boolean): Long? {
        if (reading) return null
        if (dark) {
            val since = sinceTouchMs ?: return null
            val lit = litFor(offAfterMs)
            return if (since < lit) lit - since else null
        }
        val idle = sinceTouchMs ?: sinceShownMs
        return listOfNotNull(DIM_AFTER_MS, offAfterMs.takeIf { it > 0 }).filter { it > idle }.minOrNull()?.let { it - idle }
    }

    /** A touch on a dimmed or dark panel only wakes it; it never also presses what happens to be under the finger. */
    fun touchOnlyWakes(show: Show): Boolean = show != Show.Bright

    /** How long a touch keeps a Screen off panel lit: the Off after time when there is one, else [LIT_MS]. */
    private fun litFor(offAfterMs: Long): Long = if (offAfterMs > 0) offAfterMs else LIT_MS

    /**
     * What `dumpsys display` says about one logical display: which power group it is in ([groupId]; 0 is the
     * default group, the one the top screen is in), whether its device asked for a group of its own ([ownGroup]),
     * its power [state] ("ON", "OFF", "DOZE") and the brightness its own controller is holding, 0..1.
     */
    data class DisplayFacts(val groupId: Int?, val ownGroup: Boolean, val state: String?, val brightness: Float?)

    private val groupRe = Regex("""displayGroupId (\d+)""")
    private val stateRe = Regex("""\bstate (\w+)""")
    private val deviceRe = Regex("""mPrimaryDisplayDevice=(.+)""")
    private val brightnessRe = Regex("""mBrightnessState=([0-9.]+)""")

    /**
     * The facts for logical display [displayId] in [dump] (`dumpsys display`), or null when it isn't listed. The
     * logical display's block starts at "Display <id>:" and runs to the next "Display <n>:" or a blank line; the
     * device's block (under "Display Devices:") is the one whose quoted name is the display's primary device.
     */
    fun displayFacts(dump: String, displayId: Int): DisplayFacts? {
        val lines = dump.lines()
        val start = lines.indexOfFirst { it.trim() == "Display $displayId:" }
        if (start < 0) return null
        val block = lines.drop(start + 1).takeWhile { l -> l.isNotBlank() && !Regex("""^\s*Display \d+:$""").matches(l) }
        val info = block.firstOrNull { "mBaseDisplayInfo=" in it } ?: block.joinToString("\n")
        val groupId = groupRe.find(info)?.groupValues?.get(1)?.toIntOrNull()
        val state = stateRe.find(info)?.groupValues?.get(1)
        val device = block.firstNotNullOfOrNull { deviceRe.find(it)?.groupValues?.get(1)?.trim() }
        var ownGroup = false
        var brightness: Float? = null
        if (device != null) {
            val at = lines.indexOfFirst { "DisplayDeviceInfo{\"$device\"" in it }
            if (at >= 0) {
                ownGroup = "FLAG_OWN_DISPLAY_GROUP" in lines[at]
                val deviceBlock = lines.drop(at + 1).takeWhile { !it.contains("DisplayDeviceInfo{") && it.isNotBlank() }
                brightness = deviceBlock.firstNotNullOfOrNull { brightnessRe.find(it)?.groupValues?.get(1)?.toFloatOrNull() }
            }
        }
        return DisplayFacts(groupId, ownGroup, state, brightness)
    }

    /** Settings keys that could be a vendor's second-screen switch, worth a look in the log. */
    private val vendorKey = Regex("""(?i)(second|sub_?screen|dual|screen_?2|screen_?b|lcd_?1|panel_?1|display_?1|aux)""")

    private val probed = AtomicBoolean(false)

    /**
     * Once per process, when a dark panel first comes up with Shizuku ready: logs the bottom display's group, state
     * and brightness and any display-related vendor settings keys, so a real display-off path for this device can
     * be found from `adb logcat -s Tandem`. Off the main thread; changes nothing.
     */
    fun logFacts(ctx: Context) {
        if (!probed.compareAndSet(false, true)) return
        val app = ctx.applicationContext
        Thread({
            if (!Shell.ready()) { probed.set(false); return@Thread }
            val display = LaunchDisplay.secondary(app)?.displayId ?: return@Thread
            val facts = Shell.run("dumpsys", "display")?.let { displayFacts(it, display) }
            Log.i("Tandem", "Screen off: display $display is " + (facts?.let {
                "in power group ${it.groupId ?: "?"}" + (if (it.ownGroup) " (its own)" else "") + ", state ${it.state ?: "?"}, brightness ${it.brightness ?: "?"}"
            } ?: "not in dumpsys display") + "; its panel goes black (no per-display off on this Android)")
            val keys = listOf("system", "global", "secure").flatMap { table ->
                Shell.run("settings", "list", table).orEmpty().lineSequence().filter { vendorKey.containsMatchIn(it.substringBefore('=')) }.map { "$table/$it" }.toList()
            }
            if (keys.isNotEmpty()) Log.i("Tandem", "Screen off: display-related settings keys: " + keys.joinToString(", "))
        }, "tandem-screen-off-facts").start()
    }
}
