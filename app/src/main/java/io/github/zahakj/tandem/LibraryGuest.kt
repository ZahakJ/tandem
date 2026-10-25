package io.github.zahakj.tandem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.ref.WeakReference

/**
 * Tandem's library on the bottom screen while another app has the top one: holding Back to move an app up is a swap,
 * and the library is what lands on the bottom screen, fully usable.
 *
 * Why it is drawn by the bottom-screen home ([SecondaryHomeActivity]) and not by moving [MainActivity] there: the
 * library activity is the Home task of the top display, and Android keeps one Home task per display area
 * (TaskDisplayArea refuses a second root home task, and the bottom display already has its own, the bottom-screen
 * home), so `am display move-stack` can't take it down there; and its own bottom-screen window is a presentation that
 * goes when the activity stops. So the bottom-screen home, which is already on show there whenever an app has the top
 * screen, composes the same [io.github.zahakj.tandem.ui.LibraryRoot] (the same state, the same process) with the
 * stopped library activity as its context, the way the swapped screens do.
 *
 * - The controller: keys that reach the bottom-screen home go through [MainActivity.dispatchKeyEvent] (A, B, the
 *   swaps, chords) and on to it ([MainActivity.guestKey]). It has them once the user touches the bottom screen or
 *   Android gives that display the focus.
 * - Buttons (X, Y, Start...) reach only this composition while it is up, never the stopped top one ([accepts]).
 * - Cheap: it is composed under the bottom-screen home's own lifecycle, so its motion and videos stop when that
 *   screen is covered or off, and it starts no scan of its own (the top library's watchers keep running).
 * - It goes as soon as Tandem is back in front ([SecondaryHome.tandemInFront]): Home, a game, holding Back again.
 */
object LibraryGuest {
    /** The library activity while it is stopped behind another app, alive (so its library can be borrowed). */
    var idleMain by mutableStateOf<WeakReference<MainActivity>?>(null)
        private set

    @Volatile private var hostRef: WeakReference<SecondaryHomeActivity>? = null

    /** The bottom-screen home showing the library right now, if any. */
    val host: SecondaryHomeActivity? get() = hostRef?.get()

    /** True while the bottom screen shows the library and an app has the top one. */
    val up: Boolean get() = host != null

    /** The frame clock of the library's composition down there: [Buttons] presses go only to collectors on it. */
    @Volatile private var clock: Any? = null

    fun mainStopped(a: MainActivity) { idleMain = WeakReference(a) }

    /**
     * The library activity is in front again: the library here stops taking keys and buttons at once. With
     * [keepShown] it stays drawn for now (Tandem's bottom screen is about to cover it); call again without it later.
     */
    fun mainStarted(keepShown: Boolean = false) {
        if (!keepShown) idleMain = null
        drop()
    }

    fun mainGone(a: MainActivity) {
        if (idleMain?.get() === a) idleMain = null
        drop()
    }

    internal fun attach(a: SecondaryHomeActivity, frameClock: Any?) {
        hostRef = WeakReference(a)
        clock = frameClock
    }

    internal fun detach(a: SecondaryHomeActivity) {
        if (host === a) drop()
    }

    private fun drop() {
        hostRef = null
        clock = null
    }

    /**
     * Whether the bottom-screen home shows the library: an ordinary app (not a game, not Tandem) is in front on the
     * top screen, the library activity is alive behind it, Tandem is the default Home and both screens are in use.
     * Otherwise it shows its own panel (Back to Tandem, Android apps). Plain, for tests.
     */
    fun wanted(front: SecondaryHome.Front?, mainIdle: Boolean, defaultHome: Boolean, singleScreen: Boolean): Boolean =
        front is SecondaryHome.Front.App && mainIdle && defaultHome && !singleScreen

    /**
     * Whether a [Buttons] collector running on [collectorClock] gets presses: every one while the library isn't
     * borrowed; only the borrowed one's while it is (the stopped top library keeps its collectors). Plain, for tests.
     */
    fun accepts(collectorClock: Any?, guestClock: Any? = clock): Boolean = guestClock == null || guestClock === collectorClock
}
