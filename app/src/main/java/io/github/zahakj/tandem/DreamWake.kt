package io.github.zahakj.tandem

/**
 * What a controller key or stick move does while Tandem's screensaver ([TandemDream]) may be running, without
 * Android (see [Dreaming], which feeds it from every window of Tandem's that can receive input).
 *
 * While the dream runs, any key and any real stick move wakes it and is used up. The rest of that press (its
 * repeats and its release, which arrive at the library once the dream has gone) is swallowed too, so the press
 * that woke the screensaver never also opens or starts something. A release that never comes (it went to another
 * window) stops being waited for after [holdMs]; a stick that woke the dream is ignored until it is let go, or for
 * [holdMs] at most.
 */
class WakeGate(private val holdMs: Long = 2_000L) {
    enum class Verdict {
        /** Wake the dream; the event is used up. */
        Wake,
        /** The rest of the press that woke the dream: used up, nothing else happens. */
        Swallow,
        /** An ordinary event: handle it as usual. */
        Pass,
    }

    /** Keys pressed while dreaming whose release hasn't arrived yet, with when they were last seen. */
    private val held = HashMap<Int, Long>()
    /** When the stick that woke the dream last moved, or -1 when it isn't being ignored. */
    private var stickSince = -1L

    fun onKey(dreaming: Boolean, down: Boolean, code: Int, now: Long): Verdict {
        held.values.removeAll { now - it > holdMs }
        if (dreaming) {
            if (down) held[code] = now else held.remove(code)
            return Verdict.Wake
        }
        if (code !in held) return Verdict.Pass
        if (down) held[code] = now else held.remove(code)
        return Verdict.Swallow
    }

    /** [moved]: some stick axis is well off centre (a drifting stick never counts). */
    fun onStick(dreaming: Boolean, moved: Boolean, now: Long): Verdict {
        if (dreaming) {
            if (!moved) return Verdict.Swallow
            stickSince = now
            return Verdict.Wake
        }
        if (stickSince < 0) return Verdict.Pass
        if (!moved || now - stickSince > holdMs) {
            stickSince = -1L
            return if (moved) Verdict.Pass else Verdict.Swallow
        }
        return Verdict.Swallow
    }

    /** Whether anything from a press that woke the dream is still being swallowed. */
    val swallowing: Boolean get() = held.isNotEmpty() || stickSince >= 0

    companion object {
        /**
         * Whether the library's bottom screen stays up while Tandem's activity stops. Only when the stop is the
         * screensaver covering the top screen: then the bottom screen is the window this device hands the controller
         * to (it routes controller input to the presentation of the app behind the dream), and it wakes the dream.
         * Every other stop (a game, another app, the screen going off) takes it down as always.
         */
        fun keepBottomScreen(dreaming: Boolean, bottomShowing: Boolean, singleScreen: Boolean): Boolean =
            dreaming && bottomShowing && !singleScreen
    }
}
