package io.github.zahakj.tandem

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

/*
 * The Deck's way into the game (ui/DeckPanel.kt): keys and mouse events for the game on the top screen, from a
 * window on the bottom one that never has the focus. Android lets only the shell inject input, so both ways go
 * through Shizuku:
 *
 * - [BinderDeck], the direct path: the input manager's own binder (android.hardware.input.IInputManager), reached
 *   with Shizuku's identity through ShizukuBinderWrapper, given KeyEvents and MotionEvents built here. A key lands
 *   in a few milliseconds and the pointer moves as the finger does. The interface is hidden API, reached by
 *   reflection, which Android may refuse: then the shell path is used.
 * - [ShellDeck], the fallback: one `sh` kept running through Shizuku, fed `input -d 0 ...` lines (DeckWire.kt).
 *   Each line starts a process, so a key takes a few hundred milliseconds and the pointer can't be moved: a click
 *   taps where the Deck's own cursor points.
 *
 * [DeckSession] picks the path when the Deck opens and keeps the pointer and sticky modifiers for the session.
 */

/** One way of putting keys and mouse events into the game. Called on the Deck's own thread. */
interface DeckOutput {
    /** What the status chip says: "Direct" or "Shell". */
    val name: String

    /** A key going [down] or up, with the modifiers' [meta] state; [repeat] counts the repeats of a held key. */
    fun key(code: Int, down: Boolean, meta: Int, repeat: Int)

    /** The pointer is at ([x], [y]) on the game's screen, with the mouse [buttons] (MotionEvent.BUTTON_* bits) held. */
    fun move(x: Int, y: Int, buttons: Int)

    /** Mouse [button] went [down] or up at ([x], [y]); [buttons] is what is held after this change. */
    fun button(button: Int, down: Boolean, x: Int, y: Int, buttons: Int)

    /** The wheel turned [notches] at ([x], [y]): positive for a finger moving down the pad. */
    fun scroll(x: Int, y: Int, notches: Int)

    fun close()
}

/** Parcel round trips that set what the public event constructors can't (see DeckParcel). */
private object DeckEvents {
    fun onDisplay(ev: KeyEvent, displayId: Int): KeyEvent {
        val bytes = marshal(ev)
        if (!DeckParcel.keyDisplay(bytes, ev.deviceId, ev.source, ev.action, ev.keyCode, displayId)) return ev
        return unmarshal(bytes) { KeyEvent.CREATOR.createFromParcel(it) } ?: ev
    }

    /** [ev] with its display and action button set; the original is recycled when a copy replaces it. */
    fun mouse(ev: MotionEvent, displayId: Int, actionButton: Int): MotionEvent {
        val bytes = marshal(ev)
        if (!DeckParcel.motionFields(bytes, ev.pointerCount, ev.source, ev.action, ev.buttonState, displayId, actionButton)) return ev
        val copy = unmarshal(bytes) { MotionEvent.CREATOR.createFromParcel(it) } ?: return ev
        ev.recycle()
        return copy
    }

    private fun marshal(p: Parcelable): ByteArray {
        val parcel = Parcel.obtain()
        try {
            p.writeToParcel(parcel, 0)
            return parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }

    private fun <T> unmarshal(bytes: ByteArray, read: (Parcel) -> T): T? {
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            return runCatching { read(parcel) }.getOrNull()
        } finally {
            parcel.recycle()
        }
    }
}

/** The direct path: the input manager's binder with Shizuku's identity. */
class BinderDeck private constructor(private val inject: (InputEvent) -> Boolean) : DeckOutput {
    override val name = "Direct"

    /** When each held key went down, for the events' down time. */
    private val downAt = HashMap<Int, Long>()
    private var mouseDownAt = 0L

    override fun key(code: Int, down: Boolean, meta: Int, repeat: Int) {
        val now = SystemClock.uptimeMillis()
        if (down && repeat == 0) downAt[code] = now
        val downTime = downAt[code] ?: now
        if (!down) downAt.remove(code)
        val ev = KeyEvent(downTime, now, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code, repeat, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)
        inject(DeckEvents.onDisplay(ev, DeckSession.DISPLAY))
    }

    override fun move(x: Int, y: Int, buttons: Int) {
        send(if (buttons != 0) MotionEvent.ACTION_MOVE else MotionEvent.ACTION_HOVER_MOVE, x, y, buttons)
    }

    override fun button(button: Int, down: Boolean, x: Int, y: Int, buttons: Int) {
        // As a real mouse arrives: the pointer goes down with the button, then the button press; the release the
        // other way round. ACTION_BUTTON_PRESS carries which button, which Winlator reads.
        if (down) {
            mouseDownAt = SystemClock.uptimeMillis()
            send(MotionEvent.ACTION_DOWN, x, y, buttons)
            send(MotionEvent.ACTION_BUTTON_PRESS, x, y, buttons, actionButton = button)
        } else {
            send(MotionEvent.ACTION_BUTTON_RELEASE, x, y, buttons, actionButton = button)
            if (buttons == 0) send(MotionEvent.ACTION_UP, x, y, 0)
        }
    }

    override fun scroll(x: Int, y: Int, notches: Int) {
        // The wheel's axis counts up for the wheel rolled away (content moves down the screen); a finger moving down
        // the pad pulls content down with it, like touch, so the sign flips.
        send(MotionEvent.ACTION_SCROLL, x, y, 0, vscroll = -notches.toFloat())
    }

    private fun send(action: Int, x: Int, y: Int, buttons: Int, actionButton: Int = 0, vscroll: Float = 0f): Boolean {
        val now = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply {
                this.x = x.toFloat(); this.y = y.toFloat()
                pressure = if (buttons != 0) 1f else 0f; size = 1f
                if (vscroll != 0f) setAxisValue(MotionEvent.AXIS_VSCROLL, vscroll)
            },
        )
        val ev = MotionEvent.obtain(if (buttons != 0 || action == MotionEvent.ACTION_BUTTON_RELEASE) mouseDownAt else now, now, action, 1, props, coords, 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        val sent = DeckEvents.mouse(ev, DeckSession.DISPLAY, actionButton)
        val ok = inject(sent)
        sent.recycle()
        return ok
    }

    override fun close() {}

    companion object {
        /** IInputManager.INJECT_INPUT_EVENT_MODE_ASYNC: don't wait for the game to take the event. */
        private const val MODE_ASYNC = 0

        /**
         * Connects, or returns null when Android refuses the hidden interface (then the shell path is used). Checks
         * itself by moving the pointer to ([x], [y]), which the Deck is about to do anyway. Blocking.
         */
        fun open(x: Int, y: Int): BinderDeck? = runCatching {
            val raw = SystemServiceHelper.getSystemService("input") ?: return null
            val wrapped: IBinder = ShizukuBinderWrapper(raw)
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            val manager = method(stub, "asInterface", IBinder::class.java).invoke(null, wrapped) ?: return null
            val inject = method(Class.forName("android.hardware.input.IInputManager"), "injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType!!)
            val deck = BinderDeck { ev -> inject.invoke(manager, ev, MODE_ASYNC) as Boolean }
            // A refused injection throws (SecurityException, wrapped) or answers false: either way, the shell path.
            if (deck.send(MotionEvent.ACTION_HOVER_MOVE, x, y, 0)) deck else null
        }.onFailure { Log.i("Tandem", "Deck: no direct injection, using the shell (${it.cause ?: it})") }.getOrNull()

        /**
         * A method of a hidden class. Hidden-API enforcement hides it from getMethod; asking Class itself through
         * reflection is let through on some builds, so that is tried second.
         */
        private fun method(cls: Class<*>, name: String, vararg params: Class<*>): Method =
            try {
                cls.getMethod(name, *params)
            } catch (e: NoSuchMethodException) {
                val meta = Class::class.java.getMethod("getMethod", String::class.java, emptyArray<Class<*>>().javaClass)
                meta.invoke(cls, *arrayOf<Any?>(name, params)) as Method
            }
    }
}

/** The fallback: one shell through Shizuku, one `input` line per key or click (DeckShell). */
class ShellDeck private constructor(private val process: Process, private val height: Int) : DeckOutput {
    override val name = "Shell"
    private val stdin = process.outputStream.bufferedWriter()

    init {
        // Whatever `input` prints (usage errors, mostly) goes to the log instead of filling the pipe.
        for ((stream, what) in listOf(process.inputStream to "out", process.errorStream to "err")) {
            Thread({
                runCatching { stream.bufferedReader().forEachLine { if (it.isNotBlank()) Log.i("Tandem", "Deck shell $what: $it") } }
            }, "tandem-deck-$what").apply { isDaemon = true }.start()
        }
    }

    private fun line(s: String?) {
        s ?: return
        synchronized(stdin) {
            runCatching { stdin.write(s); stdin.write("\n"); stdin.flush() }.onFailure { Log.w("Tandem", "Deck shell is gone", it) }
        }
    }

    // A key is typed once on the press (the line presses and releases it); repeats would only queue up behind the
    // slow processes.
    override fun key(code: Int, down: Boolean, meta: Int, repeat: Int) {
        if (down && repeat == 0) line(DeckShell.key(code, meta))
    }

    /** `input` can't move a pointer: the Deck shows its own cursor instead, and clicks land where it points. */
    override fun move(x: Int, y: Int, buttons: Int) {}

    override fun button(button: Int, down: Boolean, x: Int, y: Int, buttons: Int) {
        if (down) return
        line(if (button == MotionEvent.BUTTON_SECONDARY) DeckShell.hold(x, y) else DeckShell.tap(x, y))
    }

    override fun scroll(x: Int, y: Int, notches: Int) = line(DeckShell.scroll(x, y, notches, height))

    override fun close() {
        synchronized(stdin) { runCatching { stdin.write("exit\n"); stdin.flush() } }
        Shell.release(process)
    }

    companion object {
        fun open(height: Int): ShellDeck? = Shell.spawn(listOf("sh"))?.let { ShellDeck(it, height) }
    }
}

/**
 * The Deck while it is open: which path is in use, the pointer's position and the sticky modifiers. The UI calls
 * it on the main thread; everything that leaves the device runs on the Deck's own thread, in order, so a slow
 * injection never holds a touch up.
 */
object DeckSession {
    /** The game's screen: Android's default display, the top one on the Thor. */
    const val DISPLAY = Display.DEFAULT_DISPLAY

    enum class Status { Closed, Connecting, Direct, Shell, NoShizuku }

    var status by mutableStateOf(Status.Closed)
        private set

    /** The pointer on the game's screen, as a fraction of its width and height, for the pad's cursor mark. */
    var cursorX by mutableFloatStateOf(0.5f)
        private set
    var cursorY by mutableFloatStateOf(0.5f)
        private set

    /** Bumped whenever a modifier changes, so the keys showing them recompose. */
    var modifierTick by mutableIntStateOf(0)
        private set

    val modifiers = DeckModifiers()

    private var pointer = DeckPointer(1920, 1080)
    @Volatile private var output: DeckOutput? = null
    private var buttons = 0
    private var token = 0

    /** Which keys are held, with the modifiers and meta state they went down with. */
    private val held = HashMap<Int, Pair<List<DeckModifiers.Mod>, Int>>()

    private val worker by lazy { Handler(HandlerThread("tandem-deck").apply { start() }.looper) }

    /** Opens the Deck for the panel that shows it; the number returned closes it again ([close]). */
    fun open(ctx: Context): Int {
        val t = ++token
        val app = ctx.applicationContext
        modifiers.clear(); modifierTick++
        held.clear(); buttons = 0
        status = Status.Connecting
        worker.post { connect(app, t) }
        return t
    }

    /** Closes the Deck opened as [t]; a newer opening (another face of the companion came and went) stays. */
    fun close(t: Int) {
        if (t != token) return
        token++
        status = Status.Closed
        worker.post { output?.close(); output = null }
    }

    private fun connect(app: Context, t: Int) {
        output?.close(); output = null
        val (w, h) = displaySize(app)
        pointer = DeckPointer(w, h, pointer.fx.coerceAtMost((w - 1).toFloat()), pointer.fy.coerceAtMost((h - 1).toFloat()))
        val out = if (!Shell.ready()) null else BinderDeck.open(pointer.x, pointer.y) ?: ShellDeck.open(h)
        if (t != token) { out?.close(); return }
        output = out
        status = when (out) { null -> Status.NoShizuku; is BinderDeck -> Status.Direct; else -> Status.Shell }
        cursorX = pointer.fx / w; cursorY = pointer.fy / h
        Log.i("Tandem", "Deck open: ${out?.name ?: "no Shizuku"}, screen ${w}x$h")
    }

    private fun displaySize(app: Context): Pair<Int, Int> = runCatching {
        val d = app.getSystemService(DisplayManager::class.java).getDisplay(DISPLAY)
        val p = Point()
        @Suppress("DEPRECATION") d.getRealSize(p)
        if (p.x > 0 && p.y > 0) p.x to p.y else null
    }.getOrNull() ?: (1920 to 1080)

    // ---- Keys (main thread) ----

    /** A key is pressed: the armed modifiers go down with it. */
    fun keyDown(key: DeckKey) {
        val mods = modifiers.take()
        if (mods.isNotEmpty()) modifierTick++
        val meta = DeckModifiers.meta(mods)
        held[key.code] = mods to meta
        val code = key.code
        worker.post {
            val out = output ?: return@post
            for (m in mods) out.key(m.code, true, meta, 0)
            out.key(code, true, meta, 0)
        }
    }

    /** Repeat number [n] of a held key. */
    fun keyRepeat(key: DeckKey, n: Int) {
        val meta = held[key.code]?.second ?: 0
        val code = key.code
        worker.post { output?.key(code, true, meta, n) }
    }

    fun keyUp(key: DeckKey) {
        val (mods, meta) = held.remove(key.code) ?: (emptyList<DeckModifiers.Mod>() to 0)
        val code = key.code
        worker.post {
            val out = output ?: return@post
            out.key(code, false, meta, 0)
            for (m in mods.asReversed()) out.key(m.code, false, 0, 0)
        }
    }

    fun modifierTap(m: DeckModifiers.Mod) { modifiers.tap(m); modifierTick++ }

    fun modifierHold(m: DeckModifiers.Mod) { modifiers.hold(m); modifierTick++ }

    // ---- Pointer (main thread) ----

    /** The finger moved ([dx], [dy]) pad pixels over [dtMs]. */
    fun moveBy(dx: Float, dy: Float, dtMs: Long, sensitivity: Float) {
        if (!pointer.move(dx, dy, dtMs, sensitivity)) return
        cursorX = pointer.fx / pointer.width; cursorY = pointer.fy / pointer.height
        val x = pointer.x; val y = pointer.y; val b = buttons
        worker.post { output?.move(x, y, b) }
    }

    fun mouseDown(m: DeckMouse) {
        buttons = buttons or m.button
        val x = pointer.x; val y = pointer.y; val b = buttons
        worker.post { output?.button(m.button, true, x, y, b) }
    }

    fun mouseUp(m: DeckMouse) {
        buttons = buttons and m.button.inv()
        val x = pointer.x; val y = pointer.y; val b = buttons
        worker.post { output?.button(m.button, false, x, y, b) }
    }

    /** A tap on the pad: a click, down and up a moment apart so the game sees both. */
    fun click(c: DeckTap.Click) {
        val m = when (c) { DeckTap.Click.Left -> DeckMouse.Left; DeckTap.Click.Right -> DeckMouse.Right; DeckTap.Click.None -> return }
        mouseDown(m)
        val x = pointer.x; val y = pointer.y
        val after = buttons and m.button.inv()
        buttons = after
        worker.postDelayed({ output?.button(m.button, false, x, y, after) }, CLICK_MS)
    }

    fun scroll(notches: Int) {
        if (notches == 0) return
        val x = pointer.x; val y = pointer.y
        worker.post { output?.scroll(x, y, notches) }
    }

    /** How long a tap's click is held. */
    private const val CLICK_MS = 40L
}
