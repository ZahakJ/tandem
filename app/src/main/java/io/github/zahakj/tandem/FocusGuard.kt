package io.github.zahakj.tandem

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Window
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Keeps the controller with Tandem while Tandem owns both screens.
 *
 * Android keeps one focused window per display and sends keys to the "top focused" display. When the app in front
 * on the top screen goes away (a game crashes, say), Android can move that focus to the bottom display, to another
 * app's window that sits there underneath Tandem's bottom screen (an app opened down there earlier through Android's
 * own app list). Tandem's bottom screen is a presentation that never takes focus, so it covers that window but can't
 * take the keys from it: the library shows but nothing moves, and the bottom screen shows Android's navigation bar
 * (the bars follow the focused window of each display). Home did nothing, since Tandem was already in front.
 *
 * Whenever the library is in front ([MainActivity] resumed, its window focus lost, or Home), this checks which window
 * holds the focus on the bottom display and, if it is another app's, brings Tandem's bottom-screen home
 * ([SecondaryHomeActivity]) forward there: it takes the focus, hides the bars and hands every key to the library.
 *
 * The decisions are plain functions over the text of `dumpsys window`, tested in FocusGuardTest.
 */
object FocusGuard {
    private const val TAG = "Tandem"

    /** Which window holds the focus on each display ([byDisplay], by owner: a package, or a system window's title) and which display gets the keys ([top]). */
    data class Focus(val top: Int?, val byDisplay: Map<Int, String?>)

    private val displayLine = Regex("""^Display: mDisplayId=(\d+)""")
    private val focusLine = Regex("""^mCurrentFocus=(.*)$""")
    private val topLine = Regex("""mTopFocusedDisplayId=(-?\d+)""")
    private val windowOwner = Regex("""Window\{\S+ u\d+ ([^\s/}]+)""")

    /** The shell command whose output [parse] reads: small, so it is cheap to ask. */
    const val COMMAND = "dumpsys window displays | grep -E 'Display: mDisplayId=|mCurrentFocus='; " +
        "dumpsys window windows | grep mTopFocusedDisplayId"

    /** Reads [COMMAND]'s output. Null when it lists no display (the dump didn't work). */
    fun parse(out: String?): Focus? {
        out ?: return null
        var top: Int? = null
        var display: Int? = null
        val focus = LinkedHashMap<Int, String?>()
        for (raw in out.lineSequence()) {
            val line = raw.trim()
            val d = displayLine.find(line)?.groupValues?.get(1)?.toInt()
            if (d != null) {
                display = d
                focus.putIfAbsent(d, null)
                continue
            }
            val t = topLine.find(line)
            if (t != null) {
                top = t.groupValues[1].toInt()
                continue
            }
            val at = display ?: continue
            focusLine.find(line)?.let { focus[at] = owner(it.groupValues[1]) }
        }
        return if (focus.isEmpty()) null else Focus(top, focus)
    }

    /** "Window{6788ae8 u0 com.discord/com.discord.main.MainActivity}" -> "com.discord"; "null" -> null. */
    fun owner(window: String): String? = windowOwner.find(window)?.groupValues?.get(1)

    /** Packages whose windows may hold the focus anywhere: the system's own (the shade, a power menu). */
    private val SYSTEM = setOf("android", "com.android.systemui")

    /**
     * Whether [owner] is another app's window. System windows are titled without a package ("NotificationShade",
     * "StatusBar") and are never in the way.
     */
    fun isForeign(owner: String?, own: String): Boolean =
        owner != null && '.' in owner && owner != own && !owner.startsWith("$own.") && owner !in SYSTEM

    enum class Fix {
        None,
        /** Bring Tandem's bottom-screen home forward on the bottom display, so it takes the focus there. */
        CoverBottom,
    }

    /**
     * With Tandem in front, its bottom screen showing on display [bottom] ([ownsBottom]: not single-screen mode, and
     * nothing Tandem opened down there on purpose): fix it when another app's window holds the focus on the bottom
     * display, or when that display gets the keys and nothing there holds the focus.
     */
    fun decide(focus: Focus?, own: String, bottom: Int?, ownsBottom: Boolean): Fix {
        if (focus == null || bottom == null || !ownsBottom) return Fix.None
        if (bottom !in focus.byDisplay) return Fix.None
        val there = focus.byDisplay[bottom]
        return when {
            isForeign(there, own) -> Fix.CoverBottom
            there == null && focus.top == bottom -> Fix.CoverBottom
            else -> Fix.None
        }
    }

    /**
     * Without Shizuku Tandem can't see other apps' windows; only Home is taken as the sign something is wrong: Home
     * pressed while the library is in front, and neither the library nor Tandem's bottom-screen home has the focus
     * (Tandem has no other window that takes it). Never during Tandem's screensaver, which holds the focus itself.
     */
    fun decideBlind(home: Boolean, libraryFocused: Boolean, bottomHomeFocused: Boolean, dreaming: Boolean, ownsBottom: Boolean): Fix =
        if (home && ownsBottom && !libraryFocused && !bottomHomeFocused && !dreaming) Fix.CoverBottom else Fix.None

    // ---- On the device ----

    /**
     * Set when Tandem opens an app or game on the bottom screen itself: that app's focus down there is wanted. Cleared
     * by Home, and when Tandem leaves the front.
     */
    @Volatile var bottomAppStarted = false

    /** Whether Tandem's bottom-screen home ([SecondaryHomeActivity]) holds the window focus. */
    @Volatile var bottomHomeFocused = false

    private val main by lazy { Handler(Looper.getMainLooper()) }
    @Volatile private var lastFix = 0L
    private const val MIN_GAP_MS = 1_500L

    /** Whether Tandem owns the bottom screen right now: its presentation shows there, and nothing was opened there on purpose. */
    private fun ownsBottom(): Boolean =
        !io.github.zahakj.tandem.data.SystemPrefs.data.singleScreen && io.github.zahakj.tandem.ui.ScreenRoles.bottomShowing && !bottomAppStarted

    /**
     * Checks, off the main thread, that the library in [a] can get the controller, and fixes it if not. [home]: Home
     * was pressed (the blind check applies too). Does nothing unless [a] is still resumed when the answer comes.
     */
    fun check(a: MainActivity, home: Boolean) {
        if (!a.isResumedNow() || GameCompanion.recentlyShown()) return
        val libraryFocused = a.hasWindowFocus()
        val app = a.applicationContext
        Thread({
            val fix = if (Shell.ready()) {
                val focus = parse(Shell.run("sh", "-c", COMMAND))
                val bottom = LaunchDisplay.secondary(app)?.displayId
                decide(focus, app.packageName, bottom, ownsBottom()).also {
                    if (it != Fix.None) Log.w(TAG, "Another app holds the controls on the bottom screen: $focus")
                }
            } else {
                decideBlind(home, libraryFocused, bottomHomeFocused, Dreaming.active, ownsBottom())
            }
            if (fix == Fix.None) return@Thread
            main.post {
                val now = SystemClock.elapsedRealtime()
                if (!a.isResumedNow() || !ownsBottom() || GameCompanion.recentlyShown() || now - lastFix < MIN_GAP_MS) return@post
                val display = LaunchDisplay.secondary(a) ?: return@post
                lastFix = now
                SecondaryHome.reclaim(a, display.displayId)
                a.controlsReclaimed()
            }
        }, "focus-guard").start()
    }
}

/** Tandem's screens never show Android's status or navigation bar; a swipe shows them for a moment (immersive sticky). */
object SystemBars {
    fun hide(window: Window?) {
        val w = window ?: return
        runCatching {
            WindowInsetsControllerCompat(w, w.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
