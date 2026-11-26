package io.github.zahakj.tandem

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

/** Something a group tile stands for, so Y can open a menu for it. */
sealed interface GroupTarget {
    data class System(val platformId: String, val folder: String? = null) : GroupTarget
    data class Collection(val id: String) : GroupTarget
    data object Genres : GroupTarget
}

/** What is highlighted on the top screen; the bottom screen shows details for it. */
sealed interface Selected {
    data object Home : Selected
    data class System(val library: PlatformLibrary) : Selected
    /** A subfolder, genre or other collection of games. [target] is what Y opens a menu for. */
    data class Group(val title: String, val subtitle: String, val games: List<Game>, val color: Color, val target: GroupTarget? = null) : Selected
    data class GameItem(val game: Game) : Selected
    /** An app in the dock: an Android package, one of Tandem's own ("tandem:settings"), or All apps (Dock.ALL_APPS). */
    data class App(val pkg: String) : Selected
    /** A row on the settings screen, so the bottom screen can explain it. */
    data class Setting(val title: String, val value: String, val detail: String?, val color: Color, val action: String = Tx.s(R.string.menus_selected_change)) : Selected
}

object Selection {
    private var state by mutableStateOf<Selected>(Selected.Home)

    /** When [current] was last set (uptime ms), so a screen can tell whether anything on it took focus. */
    var setAt = 0L
        private set

    var current: Selected
        get() = state
        set(value) { state = value; setAt = SystemClock.uptimeMillis() }

    /** While the D-pad is held, the details catch up once the highlight has rested this long (ms). */
    const val SETTLE_MS = 150L

    /** True while a D-pad direction is held down (key repeats arriving). */
    var held = false
        private set
    private var waiting: (() -> Unit)? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val flush = Runnable { runWaiting() }

    /**
     * Shows what a newly highlighted item is ([update] sets [current] and anything that goes with it). At once,
     * except while the D-pad is held: the highlight still moves on every repeat, but the details it drives (the
     * other screen's Spotlight, the name by Pip, a hero wallpaper) wait until the highlight rests ([SETTLE_MS], or
     * the D-pad is let go) and then show the item it stopped on, instead of starting a transition for every item
     * passed on the way (each one is a crossfade, a new picture and a logo popping in, on both screens' main thread).
     */
    fun follow(update: () -> Unit) {
        if (!held) {
            main.removeCallbacks(flush)
            waiting = null
            update()
            return
        }
        waiting = update
        main.removeCallbacks(flush)
        main.postDelayed(flush, SETTLE_MS)
    }

    /** The D-pad is held down (key repeats arrive), or was let go: then the details catch up at once. */
    fun holding(on: Boolean) {
        if (held == on) return
        held = on
        if (!on) runWaiting()
    }

    private fun runWaiting() {
        main.removeCallbacks(flush)
        val w = waiting ?: return
        waiting = null
        w()
    }
}

/**
 * Logical controller buttons, named after the button each command sits on by default: a screen that checks
 * `b == Button.Y` means "the options button", whatever Options is bound to (Bindings.kt). A and B are handled as
 * select and back. The last four are commands with no button of their own until the user gives them one; a
 * screen handles them only where they make sense.
 */
enum class Button {
    X, Y, Start, Select, L1, R1, L2, R2, SidePanel, Favourite, Screenshot, Clip,

    /** Options held on the icon grid: pick the highlighted tile up (Command.Grab). */
    Grab;

    /** The command this button stands for when pressed on its own (a tapped hint, a direct [Buttons.press]). */
    val command: Command get() = when (this) {
        X -> Command.Action
        Y -> Command.Options
        Start -> Command.StartMenu
        Select -> Command.Surprise
        L1 -> Command.Previous
        R1 -> Command.Next
        L2 -> Command.PageUp
        R2 -> Command.PageDown
        SidePanel -> Command.SidePanel
        Favourite -> Command.Favourite
        Screenshot -> Command.Screenshot
        Clip -> Command.Clip
        Grab -> Command.Grab
    }
}

/** The Tandem screen the user is looking at; any other (a stale copy in another task) ignores buttons. */
object ActiveHost {
    @Volatile var token: Any? = null
}

object Buttons {
    private val presses = MutableSharedFlow<Button>(extraBufferCapacity = 8)

    /**
     * Every press, for the screens' collectors. While the library is drawn on the bottom screen behind another app
     * ([LibraryGuest]), only collectors in that composition get them (told apart by their composition's frame
     * clock): the stopped top library keeps its collectors and would act on the same press.
     */
    val events: Flow<Button> = flow {
        val clock = currentCoroutineContext()[MonotonicFrameClock]
        presses.collect { if (LibraryGuest.accepts(clock)) emit(it) }
    }
    private val handlers = ArrayList<(Button) -> Boolean>()
    /** Handlers that go before every one in [handlers], however late those were added ([intercept]'s front). */
    private val fronts = ArrayList<(Button) -> Boolean>()

    /**
     * Popups that own some buttons (the keyboard's X and Start, for example) register here. The newest handler
     * sees a press first; when it returns true the press stops there and never reaches [events], so the screen
     * underneath can't act on the same press.
     *
     * A mode over screens that change while it lasts (Start › Layout's try-on: each layout put on adds its own
     * handlers, newer than the mode's) registers with [front]: front handlers see every press before the others,
     * whenever those were added.
     */
    fun intercept(handler: (Button) -> Boolean, owner: Any? = null, front: Boolean = false): () -> Unit {
        val h: (Button) -> Boolean = { b -> (owner == null || owner === ActiveHost.token) && handler(b) }
        val list = if (front) fronts else handlers
        list += h
        return { list -= h }
    }

    /**
     * The commands behind the press being handled right now (Bindings.kt). Usually just the button's own; two
     * commands that share a button (Page down and Side panel on R2) arrive as one press that carries both, and a
     * screen asks [has] to tell which of them its button means here.
     */
    var commands: Set<Command> = emptySet()
        private set

    /** Whether the press being handled means [c]. Only meaningful inside a handler or collector. */
    fun has(c: Command): Boolean = c in commands

    /**
     * The press being handled is a held shoulder repeating (ChordEngine.REPEATS), not a press of its own. A screen
     * where a repeat would do harm (a zoom that lays the whole grid out again, a switch that flips back) ignores it.
     */
    var repeat: Boolean = false
        private set

    /** Called on the main thread for every press. [commands] are the commands this press means, see [has]. */
    fun press(b: Button, commands: Set<Command> = setOf(b.command), repeat: Boolean = false) {
        // Collectors run a little later, on the main dispatcher, so the set stays until the next press.
        this.commands = commands
        this.repeat = repeat
        for (h in fronts.asReversed() + handlers.asReversed()) if (h(b)) return
        presses.tryEmit(b)
    }
}

/** The scanned library, shared by all screens. */
object Libraries {
    var all by mutableStateOf<List<PlatformLibrary>?>(null)

    fun of(game: Game): PlatformLibrary? = all?.firstOrNull { it.platform.id == game.platform.id }
    fun byId(id: String): PlatformLibrary? = all?.firstOrNull { it.platform.id == id }
    /** Every game of every system. Built once per scan, not on every call. */
    val games: List<Game>
        get() {
            val libs = all
            val cached = gamesCache
            if (cached != null && cached.first === libs) return cached.second
            return libs.orEmpty().flatMap { it.games }.also { gamesCache = libs to it }
        }

    @Volatile private var gamesCache: Pair<List<PlatformLibrary>?, List<Game>>? = null

    /** A game by its key, from the current scan. */
    fun byKey(key: String): Game? {
        val libs = all
        val cached = keysCache
        val map = if (cached != null && cached.first === libs) cached.second else games.associateBy { it.key }.also { keysCache = libs to it }
        return map[key]
    }

    @Volatile private var keysCache: Pair<List<PlatformLibrary>?, Map<String, Game>>? = null
}

/** "1 game", "2 games". */
fun plural(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}" // tx-ok: the English-only helper itself, removed once no file uses it
