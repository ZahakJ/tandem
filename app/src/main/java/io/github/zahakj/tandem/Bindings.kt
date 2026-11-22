package io.github.zahakj.tandem

import android.view.KeyEvent
import androidx.annotation.StringRes

/*
 * Which controller button does what in Tandem (Settings › Controls › Buttons › Rebind buttons).
 *
 * A [Command] is something Tandem does on a button: open, back, the options menu, the Start panel, a page down, the
 * hidden-games chord. Each has a default [Binding] (one button, several pressed together, or one held) and the user
 * may change it. [Bindings] is the pure resolver: what a command is bound to, which commands a press means, whether a
 * new binding is allowed, and how a clash is settled by swapping. [ChordEngine] turns the physical presses and
 * releases into commands. Everything here is plain Kotlin, tested on the JVM; Chords.kt feeds it key events and
 * carries out what it says.
 *
 * The code elsewhere keeps asking for logical buttons (`b == Button.Y` means "the options button"): a press of
 * whatever the Options command is bound to arrives there as Button.Y, so every screen honours a rebinding without
 * knowing about it.
 */

/** A controller button a command can be bound to, as the controller sends it (after a pad's own Nintendo swap). */
enum class Pad(val keyCode: Int) {
    A(KeyEvent.KEYCODE_BUTTON_A), B(KeyEvent.KEYCODE_BUTTON_B), X(KeyEvent.KEYCODE_BUTTON_X), Y(KeyEvent.KEYCODE_BUTTON_Y),
    L1(KeyEvent.KEYCODE_BUTTON_L1), R1(KeyEvent.KEYCODE_BUTTON_R1), L2(KeyEvent.KEYCODE_BUTTON_L2), R2(KeyEvent.KEYCODE_BUTTON_R2),
    L3(KeyEvent.KEYCODE_BUTTON_THUMBL), R3(KeyEvent.KEYCODE_BUTTON_THUMBR),
    Select(KeyEvent.KEYCODE_BUTTON_SELECT), Start(KeyEvent.KEYCODE_BUTTON_START),
    /** A controller's Home (guide) button, which X-input pads send as BUTTON_MODE. */
    Home(KeyEvent.KEYCODE_BUTTON_MODE);

    companion object {
        /** The pad for a key code; some pads send Start as Menu. Null for keys that aren't buttons (the D-pad). */
        fun of(keyCode: Int): Pad? = if (keyCode == KeyEvent.KEYCODE_MENU) Start else entries.firstOrNull { it.keyCode == keyCode }

        fun named(name: String): Pad? = entries.firstOrNull { it.name == name }
    }
}

/** One button, several together (a chord), or one held for a moment ([hold], one button only). */
data class Binding(val pads: Set<Pad>, val hold: Boolean = false) {
    val single: Pad? get() = pads.singleOrNull()
    val chord: Boolean get() = pads.size > 1

    /**
     * "L1+R1+Select", "Hold Select"; the pads in controller order whatever order they were pressed in. English, for
     * logs and tests: [shown] is the same in Tandem's language.
     */
    val label: String get() = (if (hold) "Hold " else "") + pads.sortedBy { it.ordinal }.joinToString("+") { it.name }

    /** [label] in Tandem's language, for sentences people read. */
    val shown: String get() = pads.sortedBy { it.ordinal }.joinToString("+") { it.name }.let { if (hold) Tx.s(R.string.controls_binding_hold, it) else it }

    /** For controllers.json: "hold:Select", "L1+R1+Select". */
    fun serialize(): String = (if (hold) "hold:" else "") + pads.sortedBy { it.ordinal }.joinToString("+") { it.name }

    companion object {
        fun of(vararg pads: Pad) = Binding(pads.toSet())
        fun hold(pad: Pad) = Binding(setOf(pad), hold = true)

        /** The reverse of [serialize]; null for anything it doesn't understand, so a bad entry falls back to the default. */
        fun parse(s: String): Binding? {
            val hold = s.startsWith("hold:")
            val pads = s.removePrefix("hold:").split('+').map { Pad.named(it.trim()) ?: return null }.toSet()
            if (pads.isEmpty() || (hold && pads.size != 1)) return null
            return Binding(pads, hold)
        }
    }
}

/** Everything Tandem does on a button, with its default. [help] is what the rebind page says about it. */
enum class Command(@StringRes private val labelRes: Int, val default: Binding?, @StringRes private val helpRes: Int) {
    Open(R.string.controls_command_open, Binding.of(Pad.A), R.string.controls_command_open_help),
    Back(R.string.controls_command_back, Binding.of(Pad.B), R.string.controls_command_back_help),
    Options(R.string.controls_command_options, Binding.of(Pad.Y), R.string.controls_command_options_help),
    Action(R.string.controls_command_action, Binding.of(Pad.X), R.string.controls_command_action_help),
    StartMenu(R.string.controls_command_start, Binding.of(Pad.Start), R.string.controls_command_start_help),
    QuickPanel(R.string.controls_command_quick, Binding.hold(Pad.Start), R.string.controls_command_quick_help),
    Switcher(R.string.controls_command_switcher, Binding.hold(Pad.Select), R.string.controls_command_switcher_help),
    SwitcherChord(R.string.controls_command_switcher_chord, Binding.of(Pad.Select, Pad.Start), R.string.controls_command_switcher_chord_help),
    Surprise(R.string.controls_command_surprise, Binding.of(Pad.Select), R.string.controls_command_surprise_help),
    Hidden(R.string.controls_command_hidden, Binding.of(Pad.L1, Pad.R1, Pad.Select), R.string.controls_command_hidden_help),
    Previous(R.string.controls_command_previous, Binding.of(Pad.L1), R.string.controls_command_previous_help),
    Next(R.string.controls_command_next, Binding.of(Pad.R1), R.string.controls_command_next_help),
    PageUp(R.string.controls_command_page_up, Binding.of(Pad.L2), R.string.controls_command_page_up_help),
    PageDown(R.string.controls_command_page_down, Binding.of(Pad.R2), R.string.controls_command_page_down_help),
    SidePanel(R.string.controls_command_side_panel, Binding.of(Pad.R2), R.string.controls_command_side_panel_help),
    Favourite(R.string.controls_command_favourite, null, R.string.controls_command_favourite_help),
    Screenshot(R.string.controls_command_screenshot, null, R.string.controls_command_screenshot_help),
    Clip(R.string.controls_command_clip, null, R.string.controls_command_clip_help),
    ControllerHome(R.string.controls_command_home, Binding.of(Pad.Home), R.string.controls_command_home_help);

    val label: String get() = Tx.s(labelRes)
    val help: String get() = Tx.s(helpRes)

    /** How the command reaches the rest of Tandem. */
    enum class Kind {
        /** The button's own down and up go through, as a key (A selects, B goes back, the Home button taps or holds). */
        Pass,
        /** One press arrives in [Buttons] as a logical [Button]. */
        Button,
        /** Run straight away by Chords (a panel, the private-mode switch). */
        Direct,
    }

    val kind: Kind get() = when (this) {
        Open, Back, ControllerHome -> Kind.Pass
        QuickPanel, Switcher, SwitcherChord, Hidden -> Kind.Direct
        else -> Kind.Button
    }

    /** The logical button a [Kind.Button] command arrives as. Looked up, not stored: the two enums refer to each other. */
    val carrier: Button? get() = when (this) {
        Options -> Button.Y
        Action -> Button.X
        StartMenu -> Button.Start
        Surprise -> Button.Select
        Previous -> Button.L1
        Next -> Button.R1
        PageUp -> Button.L2
        PageDown -> Button.R2
        SidePanel -> Button.SidePanel
        Favourite -> Button.Favourite
        Screenshot -> Button.Screenshot
        Clip -> Button.Clip
        else -> null
    }

    companion object {
        fun named(name: String): Command? = entries.firstOrNull { it.name == name }
    }
}

/** The pure rules: what is bound to what, what may be, and how a clash is settled. A map's null means "no button". */
object Bindings {
    /** How long a button is held before a hold binding fires (and a tap binding on the same button doesn't). */
    const val HOLD_MS = 550L

    val defaults: Map<Command, Binding?> = Command.entries.associateWith { it.default }

    /** The live map from what controllers.json stores: only the commands that differ from their default ("" is unbound). */
    fun resolve(stored: Map<String, String>): Map<Command, Binding?> = Command.entries.associateWith { c ->
        val s = stored[c.name] ?: return@associateWith c.default
        if (s.isEmpty()) null else Binding.parse(s) ?: c.default
    }

    /** What to store for [map]: the differences from the defaults, so a new default reaches everyone who hasn't changed it. */
    fun stored(map: Map<Command, Binding?>): Map<String, String> =
        Command.entries.filter { map[it] != it.default }.associate { it.name to (map[it]?.serialize() ?: "") }

    /** Every command bound to exactly [b], in command order. */
    fun commands(map: Map<Command, Binding?>, b: Binding): List<Command> = Command.entries.filter { map[it] == b }

    /** How many commands are not on their default. */
    fun changed(map: Map<Command, Binding?>): Int = Command.entries.count { map[it] != it.default }

    /**
     * Commands that share a button by design (Side panel and Page down both start on R2: one acts on the home screen,
     * the other everywhere else), so neither clashes with the other.
     */
    fun companions(c: Command): Set<Command> =
        if (c.default == null) emptySet() else Command.entries.filter { it != c && it.default == c.default }.toSet()

    /** The commands [b] would take the button from, were it given to [c]. */
    fun conflicts(map: Map<Command, Binding?>, c: Command, b: Binding): List<Command> =
        commands(map, b).filter { it != c && it !in companions(c) }

    /**
     * Why [b] can't be [c]'s binding, as a sentence made when it is read, or null when it can. A [Command.Kind.Pass]
     * command needs its button's own down and up (A is held to select a tile, the Home button is tapped or held), so
     * it takes one button that no chord or hold waits on; and a chord inside another chord would fire both.
     */
    fun refusal(map: Map<Command, Binding?>, c: Command, b: Binding): (() -> String)? {
        if (b.hold && b.chord) return { Tx.s(R.string.controls_refused_hold_chord) }
        val others = Command.entries.filter { it != c }
        if (c.kind == Command.Kind.Pass) {
            if (b.hold || b.chord) return { Tx.s(R.string.controls_refused_single, c.label) }
            val pad = b.single!!
            others.firstOrNull { o -> map[o]?.let { (it.hold || it.chord) && pad in it.pads } == true }
                ?.let { o -> return { Tx.s(R.string.controls_refused_part_of, pad.name, o.label, map[o]!!.shown) } }
        } else if (b.hold || b.chord) {
            for (pad in b.pads) {
                others.firstOrNull { it.kind == Command.Kind.Pass && map[it]?.single == pad }
                    ?.let { o -> return { Tx.s(R.string.controls_refused_is_pass, pad.name, o.label) } }
            }
            if (b.chord) {
                others.filter { it !in companions(c) }.firstOrNull { o ->
                    val ob = map[o] ?: return@firstOrNull false
                    ob.chord && !ob.hold && ob.pads != b.pads && (ob.pads.containsAll(b.pads) || b.pads.containsAll(ob.pads))
                }?.let { o -> return { Tx.s(R.string.controls_refused_overlaps, b.shown, o.label, map[o]!!.shown) } }
            }
        }
        return null
    }

    /**
     * [c] takes [b]; whatever held [b] (see [conflicts]) gets [c]'s old binding in exchange, so two commands trade
     * buttons and nothing is left pressing two things at once. A command whose old binding was nothing leaves the
     * other without a button. Call [refusal] first.
     */
    fun rebind(map: Map<Command, Binding?>, c: Command, b: Binding?): Map<Command, Binding?> {
        val old = map[c]
        val out = map.toMutableMap()
        if (b != null) for (other in conflicts(map, c, b)) out[other] = old
        out[c] = b
        return out
    }

    /** Swap A and B / Swap X and Y as bindings: on, each has the other's default; off, both their own. */
    fun swapped(map: Map<Command, Binding?>, a: Command, b: Command): Boolean = map[a] == b.default && map[b] == a.default

    fun swap(map: Map<Command, Binding?>, a: Command, b: Command, on: Boolean): Map<Command, Binding?> {
        val first = rebind(map, a, if (on) b.default else a.default)
        return rebind(first, b, if (on) a.default else b.default)
    }

    /**
     * Buttons whose tap must wait for the release: the ones a hold or a chord waits on. Pressed on its own and let
     * go, such a button does its tap then; held, or joined by another button, it is a modifier instead.
     */
    fun deferred(map: Map<Command, Binding?>): Set<Pad> =
        map.values.filterNotNull().filter { it.hold || it.chord }.flatMap { it.pads }.toSet()

    /** Commands that must always have a button: without Open and Back nothing else could be reached to fix it. */
    val essential: Set<Command> = setOf(Command.Open, Command.Back)

    /** What giving a command a new binding would do, worked out before anything is stored ([plan]). */
    sealed interface Plan {
        /** Nothing else had it: store [map]. */
        data class Done(val map: Map<Command, Binding?>) : Plan
        /** [others] had it and would take the command's old binding (or none) in exchange: ask, then store [map]. */
        data class Swap(val others: List<Command>, val map: Map<Command, Binding?>) : Plan
        /** It can't be: [why], in a sentence (made when read, so it is in the language of the moment). */
        class Refused(private val reason: () -> String) : Plan {
            val why: String get() = reason()
        }
        /** It is the binding the command already has. */
        data object Same : Plan
    }

    /**
     * Whether [c] may take [b] (null: no button), and how: on its own, by swapping with whatever holds it, or not at
     * all. A swap is refused when the other command couldn't use [c]'s old binding (Open given a chord) or when Open or
     * Back would be left with no button.
     */
    fun plan(map: Map<Command, Binding?>, c: Command, b: Binding?): Plan {
        if (map[c] == b) return Plan.Same
        if (b == null) return if (c in essential) Plan.Refused { Tx.s(R.string.controls_refused_essential, c.label) } else Plan.Done(map + (c to null))
        refusal(map, c, b)?.let { return Plan.Refused(it) }
        val others = conflicts(map, c, b)
        val out = rebind(map, c, b)
        for (o in others) {
            val given = out[o]
            if (given == null) {
                if (o in essential) return Plan.Refused { Tx.s(R.string.controls_refused_essential_swap, b.shown, o.label) }
            } else {
                refusal(out, o, given)?.let { why -> return Plan.Refused { Tx.s(R.string.controls_refused_exchange, o.label, given.shown, why()) } }
            }
        }
        return if (others.isEmpty()) Plan.Done(out) else Plan.Swap(others, out)
    }

    /** [c] back on its default (Reset on its row), swapping with whatever took that binding meanwhile. */
    fun resetPlan(map: Map<Command, Binding?>, c: Command): Plan = plan(map, c, c.default)
}

/**
 * Turns button presses into commands, with the rules Tandem has always had for Select and Start, now for any button
 * a hold or chord waits on: such a button fires its tap when it is let go on its own; held for [holdMs] it fires its
 * hold; pressed with others it fires the chord when the last one goes down, and a button held while another is
 * pressed is a modifier, not a tap. Any other button fires on the way down. Buttons of a [Command.Kind.Pass] command
 * are never waited on: their down and up are handed on as they come.
 *
 * Pure: the host calls [down], [up] and, at [deadline], [tick], and carries out the [Out]s.
 */
class ChordEngine(private val bindings: () -> Map<Command, Binding?>, private val holdMs: Long = Bindings.HOLD_MS) {
    sealed interface Out {
        /** The commands bound to one binding, as one press. */
        data class Fire(val commands: List<Command>) : Out
        /** A pass-through command's button went down or up: hand this event on as that command. */
        data class Pass(val command: Command, val pad: Pad, val down: Boolean) : Out
        /** Nothing is bound to the button: the event goes on as it is. */
        data class Unbound(val pad: Pad, val down: Boolean) : Out
    }

    private val held = LinkedHashSet<Pad>()
    /** Held buttons whose tap is spent (fired, or turned into a modifier). */
    private val used = HashSet<Pad>()
    /** Held buttons whose down went out as [Out.Pass] or [Out.Unbound], so their up must follow. */
    private val forwarded = HashSet<Pad>()
    private var holdPad: Pad? = null

    /** When [tick] should next be called (the host's clock), or null. */
    var deadline: Long? = null
        private set

    private fun tapOf(map: Map<Command, Binding?>, pad: Pad) = Bindings.commands(map, Binding.of(pad))
    private fun holdOf(map: Map<Command, Binding?>, pad: Pad) = Bindings.commands(map, Binding.hold(pad))

    /** The pass-through command on [pad] alone, if any (its repeats go through too). */
    fun passOf(pad: Pad): Command? = tapOf(bindings(), pad).firstOrNull { it.kind == Command.Kind.Pass }

    fun down(pad: Pad, now: Long): List<Out> {
        if (pad in held) return emptyList()
        val map = bindings()
        val deferred = Bindings.deferred(map)
        // Another button joins: whatever was held becomes a modifier, and no hold is running any more.
        for (p in held) if (p in deferred) used += p
        holdPad = null
        deadline = null
        held += pad
        if (held.size > 1) {
            val chord = Bindings.commands(map, Binding(held.toSet()))
            if (chord.isNotEmpty()) {
                used += held
                return listOf(Out.Fire(chord))
            }
        }
        if (pad in deferred) {
            if (holdOf(map, pad).isNotEmpty()) { holdPad = pad; deadline = now + holdMs }
            return emptyList()
        }
        val tap = tapOf(map, pad)
        val pass = tap.firstOrNull { it.kind == Command.Kind.Pass }
        used += pad
        return when {
            pass != null -> { forwarded += pad; listOf(Out.Pass(pass, pad, true)) }
            tap.isNotEmpty() -> listOf(Out.Fire(tap))
            else -> { forwarded += pad; listOf(Out.Unbound(pad, true)) }
        }
    }

    fun up(pad: Pad, now: Long): List<Out> {
        val map = bindings()
        val wasHeld = held.remove(pad)
        if (holdPad == pad) { holdPad = null; deadline = null }
        val tap = tapOf(map, pad)
        val pass = tap.firstOrNull { it.kind == Command.Kind.Pass }
        val out = when {
            // A down that went out is followed by its up, so no key stays pressed.
            forwarded.remove(pad) -> listOf(if (pass != null) Out.Pass(pass, pad, false) else Out.Unbound(pad, false))
            // The down was lost (a reset while the button was held): a pass-through key still gets its release.
            !wasHeld -> if (pass != null) listOf(Out.Pass(pass, pad, false)) else emptyList()
            pad in used -> emptyList()
            tap.isNotEmpty() -> listOf(Out.Fire(tap))
            else -> emptyList()
        }
        used -= pad
        return out
    }

    /** The clock reached [deadline]: a held button fires its hold. */
    fun tick(now: Long): List<Out> {
        val at = deadline ?: return emptyList()
        if (now < at) return emptyList()
        val pad = holdPad
        holdPad = null
        deadline = null
        if (pad == null || pad !in held || pad in used) return emptyList()
        used += pad
        val hold = holdOf(bindings(), pad)
        return if (hold.isEmpty()) emptyList() else listOf(Out.Fire(hold))
    }

    /** Forget held buttons; their release may never arrive once Tandem is in the background. */
    fun reset() {
        held.clear()
        used.clear()
        forwarded.clear()
        holdPad = null
        deadline = null
    }
}

/**
 * Listening for a new binding on the rebind page: every button pressed until all are let go makes the binding, and
 * one button held for [holdMs] on its own is a hold. Pure, like the engine.
 */
class ChordCapture(private val holdMs: Long = Bindings.HOLD_MS) {
    private val pressed = LinkedHashSet<Pad>()
    private val held = HashSet<Pad>()
    private var since = 0L

    /** The buttons pressed so far in this attempt, in order, for the popup to show. */
    val sofar: List<Pad> get() = pressed.toList()

    fun down(pad: Pad, now: Long) {
        if (held.isEmpty()) { pressed.clear(); since = now }
        pressed += pad
        held += pad
    }

    /** The binding, once the last button is let go; null while some are still down. */
    fun up(pad: Pad, now: Long): Binding? {
        if (!held.remove(pad) || held.isNotEmpty()) return null
        val b = Binding(pressed.toSet(), hold = pressed.size == 1 && now - since >= holdMs)
        pressed.clear()
        return b
    }

    fun reset() { pressed.clear(); held.clear() }
}
