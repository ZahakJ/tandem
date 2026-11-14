package io.github.zahakj.tandem

/*
 * The Deck (ui/DeckPanel.kt): a keyboard and trackpad on the bottom screen for PC games and streams playing on the
 * top one. This file is the plain-data side of the keyboard: which games get a Deck, the key codes, the layouts and
 * the sticky modifiers. Nothing here touches Android, so DeckKeysTest runs it on a desktop JVM.
 */

/** Which games get a Deck: the ones a keyboard and mouse make sense for. */
object DeckGames {
    /** The Windows system (ROMs/windows, pc, steam: GameNative, Winlator, DroidDeck and streamed .moonlight tiles). */
    const val SYSTEM = "windows"

    /** Apps that are a PC, whatever system started them: Winlator builds, GameNative and Moonlight's stream. */
    private val apps = listOf("com.winlator", "app.gamenative", "com.limelight")

    /** True when a game of system [platformId] running in [pkg] can use a keyboard and mouse. */
    fun suits(platformId: String?, pkg: String?): Boolean =
        platformId == SYSTEM || (pkg != null && apps.any { pkg == it || pkg.startsWith("$it.") })
}

/**
 * Android's key codes (android.view.KeyEvent.KEYCODE_*) and meta bits, written out so this file has no Android
 * dependency; DeckKeysTest checks every one against the SDK's value.
 */
object DeckCodes {
    const val UNKNOWN = 0
    const val DIGIT_0 = 7
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val A = 29
    const val COMMA = 55
    const val PERIOD = 56
    const val ALT_LEFT = 57
    const val SHIFT_LEFT = 59
    const val TAB = 61
    const val SPACE = 62
    const val ENTER = 66
    const val DEL = 67
    const val GRAVE = 68
    const val MINUS = 69
    const val EQUALS = 70
    const val LEFT_BRACKET = 71
    const val RIGHT_BRACKET = 72
    const val BACKSLASH = 73
    const val SEMICOLON = 74
    const val APOSTROPHE = 75
    const val SLASH = 76
    const val PAGE_UP = 92
    const val PAGE_DOWN = 93
    const val ESCAPE = 111
    const val FORWARD_DEL = 112
    const val CTRL_LEFT = 113
    const val CAPS_LOCK = 115
    const val MOVE_HOME = 122
    const val MOVE_END = 123
    const val INSERT = 124
    const val F1 = 131
    const val NUM_LOCK = 143
    const val NUMPAD_0 = 144
    const val NUMPAD_DIVIDE = 154
    const val NUMPAD_MULTIPLY = 155
    const val NUMPAD_SUBTRACT = 156
    const val NUMPAD_ADD = 157
    const val NUMPAD_DOT = 158
    const val NUMPAD_ENTER = 160

    const val META_SHIFT_ON = 0x1
    const val META_ALT_ON = 0x2
    const val META_ALT_LEFT_ON = 0x10
    const val META_SHIFT_LEFT_ON = 0x40
    const val META_CTRL_ON = 0x1000
    const val META_CTRL_LEFT_ON = 0x2000

    /** The code of letter [c] (any case). */
    fun letter(c: Char): Int = A + (c.lowercaseChar() - 'a')

    /** The code of digit [c] on the main row. */
    fun digit(c: Char): Int = DIGIT_0 + (c - '0')

    /** The code of function key [n] (1..12). */
    fun function(n: Int): Int = F1 + (n - 1)

    /** The code of numpad digit [n]. */
    fun numpad(n: Int): Int = NUMPAD_0 + n
}

/** What a key on the Deck does when pressed. */
enum class DeckKeyKind {
    /** Types a character or presses a plain key (Enter, Tab, arrows...); repeats while held. */
    Key,
    /** Shift, Ctrl or Alt: sticky, see [DeckModifiers]. */
    Modifier,
    /** A mouse button: held for as long as the finger is. */
    Mouse,
    /** Switches part of the layout (the numpad, the function row). */
    Toggle,
    /** Empty room between keys. */
    Gap,
}

/**
 * One key: its [label], Android key [code] (a [DeckMouse] button for mouse keys, 0 for toggles and gaps), its
 * [width] in key units and what it does. [shifted] is the label while Shift is on, for the symbol keys.
 */
data class DeckKey(
    val label: String,
    val code: Int,
    val width: Float = 1f,
    val kind: DeckKeyKind = DeckKeyKind.Key,
    val shifted: String? = null,
) {
    /** The label shown with Shift on: letters capitalise, symbol keys show their [shifted] twin. */
    fun labelWith(shift: Boolean): String = when {
        !shift -> label
        shifted != null -> shifted
        label.length == 1 && label[0].isLetter() -> label.uppercase()
        else -> label
    }

    /** Whether holding the key types it again and again (letters, Space, Backspace, arrows; never a modifier). */
    val repeats: Boolean get() = kind == DeckKeyKind.Key && code != DeckCodes.ESCAPE && code != DeckCodes.ENTER && code != DeckCodes.TAB
}

/** Mouse buttons, as negative pseudo key codes in a [DeckKey]; [button] is Android's MotionEvent.BUTTON_* bit. */
enum class DeckMouse(val code: Int, val button: Int, val label: String) {
    Left(-1, 1, "L click"),
    Right(-2, 2, "R click");

    companion object {
        fun forCode(code: Int): DeckMouse? = entries.firstOrNull { it.code == code }
    }
}

/** A keyboard layout: rows of keys, each row filling the width by its keys' [DeckKey.width]. */
data class DeckLayout(val id: String, val name: String, val rows: List<List<DeckKey>>) {
    /** Every real key (no gaps), for lookups. */
    val keys: List<DeckKey> get() = rows.flatten().filter { it.kind != DeckKeyKind.Gap }
}

/** The Deck's layouts: a full keyboard (with a numpad and a function row to switch in) and a compact one for games. */
object DeckLayouts {
    const val FULL = "full"
    const val GAME = "game"

    /** Toggle keys' codes: [DeckKey.code] of the switches in the full layout. */
    const val TOGGLE_NUMPAD = -10
    const val TOGGLE_FN = -11

    /** Settings' choices for the layout the Deck opens with. */
    val choices = listOf(FULL to "Full keyboard", GAME to "Game keys")

    /** Every row of the full keyboard is this wide, in key units, so the rows line up. */
    const val FULL_UNITS = 14.5f
    const val GAME_UNITS = 11f

    private fun k(label: String, code: Int, width: Float = 1f, shifted: String? = null) = DeckKey(label, code, width, DeckKeyKind.Key, shifted)
    private fun mod(label: String, code: Int, width: Float) = DeckKey(label, code, width, DeckKeyKind.Modifier)
    private fun mouse(m: DeckMouse, width: Float) = DeckKey(m.label, m.code, width, DeckKeyKind.Mouse)
    private fun toggle(label: String, code: Int, width: Float = 1f) = DeckKey(label, code, width, DeckKeyKind.Toggle)
    private fun gap(width: Float) = DeckKey("", DeckCodes.UNKNOWN, width, DeckKeyKind.Gap)
    private fun letters(s: String) = s.map { k(it.toString(), DeckCodes.letter(it)) }

    val shift = mod("Shift", DeckCodes.SHIFT_LEFT, 2f)
    val ctrl = mod("Ctrl", DeckCodes.CTRL_LEFT, 1.5f)
    val alt = mod("Alt", DeckCodes.ALT_LEFT, 1.5f)
    val esc = k("Esc", DeckCodes.ESCAPE)
    val tab = k("Tab", DeckCodes.TAB, 1.5f)
    val enter = k("Enter", DeckCodes.ENTER, 2f)
    val backspace = k("⌫", DeckCodes.DEL, 1.5f)
    val space = k("Space", DeckCodes.SPACE, 6.5f)
    val up = k("↑", DeckCodes.DPAD_UP)
    val down = k("↓", DeckCodes.DPAD_DOWN)
    val left = k("←", DeckCodes.DPAD_LEFT)
    val right = k("→", DeckCodes.DPAD_RIGHT)

    private val numberRow = listOf(k("`", DeckCodes.GRAVE, shifted = "~")) +
        "1234567890".zip("!@#$%^&*()").map { (d, s) -> k(d.toString(), DeckCodes.digit(d), shifted = s.toString()) } +
        listOf(k("-", DeckCodes.MINUS, shifted = "_"), k("=", DeckCodes.EQUALS, shifted = "+"), backspace)

    private val fnRow = listOf(esc.copy(width = 1.5f)) + (1..12).map { k("F$it", DeckCodes.function(it)) } + listOf(gap(1f))

    private val qwerty = listOf(
        listOf(tab) + letters("qwertyuiop") + listOf(k("[", DeckCodes.LEFT_BRACKET, shifted = "{"), k("]", DeckCodes.RIGHT_BRACKET, shifted = "}"), k("\\", DeckCodes.BACKSLASH, shifted = "|")),
        listOf(esc.copy(width = 1.5f)) + letters("asdfghjkl") + listOf(k(";", DeckCodes.SEMICOLON, shifted = ":"), k("'", DeckCodes.APOSTROPHE, shifted = "\""), enter),
        listOf(shift) + letters("zxcvbnm") + listOf(k(",", DeckCodes.COMMA, shifted = "<"), k(".", DeckCodes.PERIOD, shifted = ">"), k("/", DeckCodes.SLASH, shifted = "?"), up, k("Del", DeckCodes.FORWARD_DEL, 1.5f)),
        listOf(ctrl, alt, space, toggle("Fn", TOGGLE_FN), toggle("123", TOGGLE_NUMPAD), left, down, right),
    )

    /**
     * The numpad block, with the editing keys and Space beside it, in place of the letter rows. The number row above
     * keeps -, = and Backspace, so the numpad's own minus is the only key labelled "-" here.
     */
    private val numpad = run {
        fun n(d: Int) = k(d.toString(), DeckCodes.numpad(d), 1.5f)
        listOf(
            listOf(tab, k("Ins", DeckCodes.INSERT, 1.5f), k("Home", DeckCodes.MOVE_HOME, 1.5f), k("PgUp", DeckCodes.PAGE_UP, 1.5f), gap(0.5f), n(7), n(8), n(9), k("/", DeckCodes.NUMPAD_DIVIDE, 1.5f), gap(0.5f), k("Num", DeckCodes.NUM_LOCK, 1.5f)),
            listOf(esc.copy(width = 1.5f), k("Del", DeckCodes.FORWARD_DEL, 1.5f), k("End", DeckCodes.MOVE_END, 1.5f), k("PgDn", DeckCodes.PAGE_DOWN, 1.5f), gap(0.5f), n(4), n(5), n(6), k("*", DeckCodes.NUMPAD_MULTIPLY, 1.5f), gap(0.5f), k("Caps", DeckCodes.CAPS_LOCK, 1.5f)),
            listOf(shift.copy(width = 1.5f), space.copy(width = 4.5f), gap(0.5f), n(1), n(2), n(3), k("-", DeckCodes.NUMPAD_SUBTRACT, 1.5f), gap(0.5f), up, gap(0.5f)),
            listOf(ctrl, alt, toggle("Fn", TOGGLE_FN), toggle("abc", TOGGLE_NUMPAD), gap(0.5f), k("0", DeckCodes.numpad(0), 1.5f), k(".", DeckCodes.NUMPAD_DOT, 1.5f), k("+", DeckCodes.NUMPAD_ADD, 1.5f), k("Enter", DeckCodes.NUMPAD_ENTER, 1.5f), left, down, right),
        )
    }

    /** The full keyboard, with the [numpad] in place of the letters and/or the function row ([fn]) above. */
    fun full(numpad: Boolean = false, fn: Boolean = false): DeckLayout {
        val rows = (if (fn) listOf(fnRow) else emptyList()) + listOf(numberRow) + (if (numpad) this.numpad else qwerty)
        return DeckLayout(FULL, "Full keyboard", rows)
    }

    /** The compact layout for games: the WASD cluster, the keys games bind by habit, and both mouse buttons. */
    val game: DeckLayout = DeckLayout(
        GAME, "Game keys",
        listOf(
            listOf(esc) + "123456".map { k(it.toString(), DeckCodes.digit(it)) } + listOf(tab, enter.copy(width = 2.5f)),
            letters("qwer") + listOf(gap(0.5f), k("f", DeckCodes.letter('f')), gap(0.5f), mouse(DeckMouse.Left, 2.5f), mouse(DeckMouse.Right, 2.5f)),
            listOf(shift.copy(width = 1.5f)) + letters("asd") + listOf(gap(3f), up, gap(1f), backspace),
            listOf(ctrl, k("Space", DeckCodes.SPACE, 5f), left, down, right, gap(1.5f)),
        ),
    )

    /** The layout for [id] as it opens (letters, no function row). */
    fun of(id: String): DeckLayout = if (id == GAME) game else full()

    /** The code behind [label] in any layout, for tests and lookups; null for an unknown label. */
    fun codeOf(label: String): Int? =
        (full(numpad = false, fn = true).keys + full(numpad = true).keys + game.keys).firstOrNull { it.label == label }?.code
}

/**
 * Shift, Ctrl and Alt as sticky keys, since one finger can't hold a modifier while another taps: a tap arms the
 * modifier for the next key only, a hold locks it until it is tapped off. [take] hands the keyboard the modifiers
 * for a key press and clears the armed ones.
 */
class DeckModifiers {
    enum class Mod(val code: Int, val meta: Int) {
        Shift(DeckCodes.SHIFT_LEFT, DeckCodes.META_SHIFT_ON or DeckCodes.META_SHIFT_LEFT_ON),
        Ctrl(DeckCodes.CTRL_LEFT, DeckCodes.META_CTRL_ON or DeckCodes.META_CTRL_LEFT_ON),
        Alt(DeckCodes.ALT_LEFT, DeckCodes.META_ALT_ON or DeckCodes.META_ALT_LEFT_ON);

        companion object {
            fun forCode(code: Int): Mod? = entries.firstOrNull { it.code == code }
        }
    }

    enum class State { Off, Armed, Locked }

    private val states = HashMap<Mod, State>()

    fun state(m: Mod): State = states[m] ?: State.Off

    /** A tap: off goes armed, anything on goes off. */
    fun tap(m: Mod) {
        states[m] = if (state(m) == State.Off) State.Armed else State.Off
    }

    /** A hold: locked, until tapped off. */
    fun hold(m: Mod) {
        states[m] = State.Locked
    }

    /** Whatever is armed or locked, in a fixed order. */
    fun active(): List<Mod> = Mod.entries.filter { state(it) != State.Off }

    /** The modifiers for a key press: the active ones, with the armed ones used up. */
    fun take(): List<Mod> {
        val mods = active()
        for (m in mods) if (state(m) == State.Armed) states.remove(m)
        return mods
    }

    fun clear() = states.clear()

    companion object {
        /** The KeyEvent meta state for [mods]. */
        fun meta(mods: Collection<Mod>): Int = mods.fold(0) { acc, m -> acc or m.meta }

        /** The modifiers a meta state names (the reverse of [meta]). */
        fun fromMeta(meta: Int): List<Mod> = Mod.entries.filter { meta and it.meta != 0 }
    }
}
