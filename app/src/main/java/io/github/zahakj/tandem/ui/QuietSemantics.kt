package io.github.zahakj.tandem.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager

/**
 * Compose keeps an accessibility tree up to date, and sends events about it, for every frame that changes it as soon
 * as any accessibility service is on. On the AYN Thor two always are, the AYN Game Assistant and Tandem's own
 * Controller Home button, and neither can read window content, so that work goes nowhere: it was about a quarter of
 * the main thread's time when coming back to the home and while the D-pad is held.
 *
 * So while no enabled service can read window content and touch exploration is off, each of Tandem's Compose windows
 * is told that no service is on, and it skips the work. A screen reader, Switch Access or anything else that reads
 * the screen turns it straight back on: the check runs again whenever accessibility changes.
 */
object QuietSemantics {
    private val delegateField by lazy {
        runCatching {
            Class.forName("androidx.compose.ui.platform.AndroidComposeView").getDeclaredField("composeAccessibilityDelegate").apply { isAccessible = true }
        }.onFailure { Log.w("Tandem", "Compose accessibility delegate not found", it) }.getOrNull()
    }
    private val servicesField by lazy {
        runCatching {
            Class.forName("androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat").getDeclaredField("enabledServices").apply { isAccessible = true }
        }.onFailure { Log.w("Tandem", "Compose accessibility services list not found", it) }.getOrNull()
    }

    /** Whether some enabled service needs Compose's accessibility tree. Plain, for tests. */
    fun needed(touchExploration: Boolean, capabilities: List<Int>): Boolean =
        touchExploration || capabilities.any { it and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0 }

    /** Keeps the Compose windows under [root] quiet while they are attached (see above). */
    fun watch(root: View) {
        val am = root.context.getSystemService(AccessibilityManager::class.java) ?: return
        // Posted, so it runs after Compose's own listeners have refreshed their list.
        val apply = Runnable { apply(root, am) }
        val state = AccessibilityManager.AccessibilityStateChangeListener { root.post(apply) }
        val touch = AccessibilityManager.TouchExplorationStateChangeListener { root.post(apply) }
        val services: Any? = if (Build.VERSION.SDK_INT >= 33) AccessibilityManager.AccessibilityServicesStateChangeListener { root.post(apply) } else null
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                am.addAccessibilityStateChangeListener(state)
                am.addTouchExplorationStateChangeListener(touch)
                if (Build.VERSION.SDK_INT >= 33) am.addAccessibilityServicesStateChangeListener(services as AccessibilityManager.AccessibilityServicesStateChangeListener)
                v.post(apply)
            }

            override fun onViewDetachedFromWindow(v: View) {
                am.removeAccessibilityStateChangeListener(state)
                am.removeTouchExplorationStateChangeListener(touch)
                if (Build.VERSION.SDK_INT >= 33) am.removeAccessibilityServicesStateChangeListener(services as AccessibilityManager.AccessibilityServicesStateChangeListener)
            }
        }
        root.addOnAttachStateChangeListener(listener)
        if (root.isAttachedToWindow) listener.onViewAttachedToWindow(root)
    }

    private fun apply(root: View, am: AccessibilityManager) {
        val df = delegateField ?: return
        val sf = servicesField ?: return
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val list = if (needed(am.isTouchExplorationEnabled, enabled.map { it.capabilities })) enabled else emptyList()
        composeViews(root) { v -> runCatching { df.get(v)?.let { sf.set(it, list) } } }
    }

    private fun composeViews(v: View, each: (View) -> Unit) {
        if (v.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") { each(v); return }
        if (v is ViewGroup) for (i in 0 until v.childCount) composeViews(v.getChildAt(i), each)
    }
}
