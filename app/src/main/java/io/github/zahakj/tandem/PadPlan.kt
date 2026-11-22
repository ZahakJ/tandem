package io.github.zahakj.tandem

/**
 * Which controller drives the built-in one (controller passthrough, PadLink.kt), as a pure state machine so it is
 * tested on the JVM. PadLink feeds it events and carries out the difference: starting or stopping the helper,
 * showing the offer and the notices.
 *
 * - A connected pad is offered ("Play with <name>") while a game is in front, whether it connected during the game or
 *   before it started, unless it was turned down since it connected.
 *   With "Always use a connected controller" ([auto]) it is taken at once instead, at any time.
 * - The pad in use going away (unplugged, switched off, out of range) brings back the built-in controls, or with
 *   [auto] the next connected pad.
 * - Picking the built-in controls by hand counts as turning down every pad connected now, so [auto] doesn't take one
 *   straight back; a pad that reconnects is new again.
 * - A pad the helper couldn't take ([Event.Failed]) is left alone until it reconnects, so [auto] never loops on it.
 */
object PadPlan {
    data class Pad(val descriptor: String, val name: String)

    data class State(
        val pads: List<Pad> = emptyList(),
        /** The pad driving the built-in controller, or null for the built-in controls alone. */
        val linked: String? = null,
        /** The pad being offered right now. */
        val offer: String? = null,
        val declined: Set<String> = emptySet(),
        val failed: Set<String> = emptySet(),
    ) {
        fun pad(descriptor: String?): Pad? = pads.firstOrNull { it.descriptor == descriptor }
    }

    sealed class Event {
        /** The external controllers connected now, whether a game is in front, and the auto setting. */
        data class Devices(val pads: List<Pad>, val inGame: Boolean, val auto: Boolean) : Event()
        /** The user picked a pad (or null: the built-in controls), in Quick settings or Settings. */
        data class Choose(val descriptor: String?) : Event()
        object Accept : Event()
        /** The offer was turned down or ran out. */
        object Decline : Event()
        /** The helper stopped forwarding [descriptor] by itself: it disconnected, or the helper failed. */
        data class Gone(val descriptor: String) : Event()
        data class Failed(val descriptor: String) : Event()
    }

    /** What a step has to say. Plain data, so it is tested on the JVM; [text] is it in Tandem's language. */
    sealed class Notice {
        abstract val text: String

        /** The pad in use went, or the built-in controls were picked. */
        data object BuiltIn : Notice() { override val text: String get() = Tx.s(R.string.controls_padlink_builtin) }
        data class Playing(val name: String) : Notice() { override val text: String get() = Tx.s(R.string.controls_padlink_playing, name) }
        /** The helper couldn't take [name] (null: a pad no longer listed). */
        data class Failed(val name: String?) : Notice() {
            override val text: String get() = name?.let { Tx.s(R.string.controls_padlink_failed, it) } ?: Tx.s(R.string.controls_padlink_failed_unknown)
        }
    }

    data class Step(val state: State, val notice: Notice? = null)

    /** The choice after [current] in Built-in, then each connected pad, round again. */
    fun next(state: State): String? {
        val order = listOf<String?>(null) + state.pads.map { it.descriptor }
        return order[(order.indexOf(state.linked).coerceAtLeast(0) + 1) % order.size]
    }

    fun reduce(s: State, e: Event): Step = when (e) {
        is Event.Devices -> devices(s, e)
        is Event.Choose -> {
            val pad = s.pad(e.descriptor)
            if (pad == null) Step(s.copy(linked = null, offer = null, declined = s.declined + s.pads.map { it.descriptor }), if (s.linked != null) Notice.BuiltIn else null)
            else Step(s.copy(linked = pad.descriptor, offer = null, failed = s.failed - pad.descriptor), if (s.linked != pad.descriptor) playing(pad) else null)
        }
        Event.Accept -> {
            val pad = s.pad(s.offer)
            if (pad == null || s.linked != null) Step(s.copy(offer = null))
            else Step(s.copy(linked = pad.descriptor, offer = null), playing(pad))
        }
        Event.Decline -> Step(s.copy(offer = null, declined = s.declined + listOfNotNull(s.offer)))
        is Event.Gone -> if (s.linked != e.descriptor) Step(s) else Step(s.copy(linked = null), Notice.BuiltIn)
        is Event.Failed -> {
            if (s.linked != e.descriptor) Step(s.copy(failed = s.failed + e.descriptor))
            else Step(s.copy(linked = null, failed = s.failed + e.descriptor), Notice.Failed(s.pad(e.descriptor)?.name))
        }
    }

    private fun devices(s: State, e: Event.Devices): Step {
        val now = e.pads.map { it.descriptor }.toSet()
        val fresh = e.pads.filter { it.descriptor !in s.pads.map { p -> p.descriptor } }
        var state = s.copy(
            pads = e.pads,
            offer = s.offer?.takeIf { it in now },
            declined = s.declined intersect now,
            failed = s.failed intersect now,
        )
        var notice: Notice? = null
        if (state.linked != null && state.linked !in now) {
            state = state.copy(linked = null)
            notice = Notice.BuiltIn
        }
        if (state.linked == null) {
            val free = { p: Pad -> p.descriptor !in state.declined && p.descriptor !in state.failed }
            if (e.auto) {
                val pick = (fresh + e.pads).firstOrNull(free)
                if (pick != null) return Step(state.copy(linked = pick.descriptor, offer = null), playing(pick))
            } else if (e.inGame && state.offer == null) {
                // A pad that just connected first; else one connected before the game started.
                (fresh + e.pads).firstOrNull(free)?.let { state = state.copy(offer = it.descriptor) }
            }
        }
        if (!e.inGame) state = state.copy(offer = null)
        return Step(state, notice)
    }

    private fun playing(pad: Pad) = Notice.Playing(pad.name)
}
