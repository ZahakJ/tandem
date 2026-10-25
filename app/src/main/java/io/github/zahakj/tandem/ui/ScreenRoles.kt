package io.github.zahakj.tandem.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.SystemPrefs

/**
 * Which screen shows what. Normally the library is on the main (top) display and details are on the second
 * (bottom) display. Single-screen mode leaves the second display alone. Swapped mode puts the library on the
 * bottom screen and details on the top one.
 *
 * Swapping never moves the activity: the activity always lives on the top display (so games, Home and the
 * controller all keep working as usual) and the bottom display is always the [BottomScreen] presentation. What
 * swaps is the content: the presentation composes the library ([LibraryRoot]) and the activity composes the
 * details ([BottomContent]). Controller keys still arrive at the activity, which hands the D-pad and A on to the
 * presentation's view ([BottomScreen.forwardKey]); Back and the other buttons are handled where they always are.
 * It is instant, and if the bottom screen can't take the controls the screens swap back with a friendly note.
 */
object ScreenRoles {
    private const val TAG = "Tandem"

    /** True while the bottom-screen presentation is showing. */
    var bottomShowing by mutableStateOf(false)
        internal set

    /** When the screens last swapped (uptime ms), so the new content can fade in. */
    var swappedAt = 0L
        private set

    private fun secondary(ctx: Context): Display? =
        ctx.getSystemService(DisplayManager::class.java).getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()

    /** The library is on the bottom screen right now. */
    val libraryOnBottom: Boolean
        get() = bottomShowing && Store.settings.look.swapScreens && !SystemPrefs.data.singleScreen

    /** Whether X swaps the screens here (rather than opening search). Never while an app has the top screen (LibraryGuest). */
    fun xSwaps(): Boolean = if (io.github.zahakj.tandem.LibraryGuest.up) false else when (Store.settings.look.xButton) {
        "swap" -> true
        "search" -> false
        else -> IconGrid.enabled && Nav.route == Route.Home
    }

    /** X: swaps the screens when that's what X does here. Returns false when X should search instead. */
    fun onX(ctx: Context): Boolean {
        if (!xSwaps()) return false
        toggle(ctx)
        return true
    }

    /** Swaps the screens (or swaps them back), remembering the choice. */
    fun toggle(ctx: Context) {
        when {
            SystemPrefs.data.singleScreen -> Toasts.show("Single screen is on, so there's no other screen to swap with")
            !bottomShowing && secondary(ctx) == null -> Toasts.show("There's no second screen to swap with")
            !bottomShowing -> Toasts.show("The bottom screen is busy right now. Try again in a moment")
            SwapFx.busy -> Unit
            else -> {
                Sounds.swap()
                // The choreography (SwapFx.kt) takes both screens' pictures first, then swaps inside it.
                SwapFx.swap {
                    swappedAt = SystemClock.uptimeMillis()
                    Store.updateSettings { it.copy(look = it.look.copy(swapScreens = !it.look.swapScreens)) }
                }
            }
        }
    }

    /** The bottom screen couldn't take the controls: put everything back and say so. */
    fun swapFailed() {
        if (!Store.settings.look.swapScreens) return
        swappedAt = SystemClock.uptimeMillis()
        Store.updateSettings { it.copy(look = it.look.copy(swapScreens = false)) }
        Toasts.show("The bottom screen couldn't take the controls, so the screens stay as they were")
    }

    /**
     * Older versions moved the whole activity to the bottom display for swapped mode. If Tandem was left there,
     * bring it back to the main display. Returns true when the activity is being relaunched there.
     */
    fun ensureLibraryDisplay(activity: Activity): Boolean {
        val current = activity.display?.displayId ?: Display.DEFAULT_DISPLAY
        if (current == Display.DEFAULT_DISPLAY) return false
        return try {
            val intent = Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = android.app.ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()
            val app = activity.applicationContext
            activity.finishAndRemoveTask()
            app.startActivity(intent, options)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't move Tandem back to the main display", t)
            false
        }
    }

    /** Shows the bottom screen (details, or the library when swapped). Null in single-screen mode or with one display. */
    fun showDetails(activity: MainActivity): BottomScreen? {
        if (SystemPrefs.data.singleScreen) return null
        return BottomScreen.showOn(activity)
    }

    /** Games open on the main display; the activity always lives there, so nothing special is needed. */
    @Suppress("UNUSED_PARAMETER")
    fun gameLaunchOptions(ctx: Context): Bundle? = null
}

/**
 * What to do when the library on the bottom screen can't take a key: nothing there holds the focus, and asking for it
 * found nothing to focus. Checked [GRACE_MS] after such a key (so it is put right within a second) and once after
 * the bottom screen shows the library.
 */
object ControlsGuard {
    const val GRACE_MS = 700L

    enum class Fix {
        /** Fine now (the focus arrived, or the library isn't on the bottom screen any more). */
        None,
        /** No library and nothing loading it: load it (its splash has nothing to focus). */
        Load,
        /** Still loading: the library takes the focus by itself once it shows. */
        Wait,
        /** The library's home screen is there but the bottom screen can't hold the controls: swap back. */
        SwapBack,
    }

    /**
     * [onHome]: the library shows its home screen, which always has something to focus. Another screen may have
     * nothing to focus (B, which never goes through the bottom screen, still leads home), so it isn't swapped away.
     */
    fun decide(libraryOnBottom: Boolean, focused: Boolean, libraryLoaded: Boolean, scanning: Boolean, onHome: Boolean): Fix = when {
        !libraryOnBottom || focused -> Fix.None
        !libraryLoaded -> if (scanning) Fix.Wait else Fix.Load
        onHome -> Fix.SwapBack
        else -> Fix.None
    }
}

