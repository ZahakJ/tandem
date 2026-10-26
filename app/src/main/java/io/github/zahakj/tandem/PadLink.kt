package io.github.zahakj.tandem

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.ControllerStore
import io.github.zahakj.tandem.ui.Toasts
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Controller passthrough: an external controller plays as the handheld's built-in one. Emulators bind player 1 to
 * the built-in controller (Eden by its GUID, RetroArch by its port), so instead of asking each of them to switch,
 * Tandem's small helper (src/padlink/padlink.c) grabs the external pad's input node, so nothing else sees it, and
 * writes its buttons and sticks, translated by [PadMap], into the built-in controller's node. Every game then gets
 * them from the device it already listens to, at once and mid-game; the built-in controls keep working alongside.
 *
 * Writing to an input node needs the `input` group: the helper runs through Shizuku (the shell user has it) or root
 * (`su`). Android's own SELinux policy lets the shell neither run a file from an app's library folder nor write to an
 * input node, so Shizuku works only where SELinux is permissive (the AYN Thor's firmware); anywhere else the helper
 * fails to start that way and root takes over. Without either, or a build without the helper, the feature stays
 * hidden.
 *
 * Which pad is in use is [PadPlan]'s state; this object carries it out. Helper work (dumpsys, starting and stopping
 * it) runs on one background thread; the state lives on the main thread.
 */
object PadLink {
    private const val TAG = "TandemPad"
    /** How long after a launch the game is taken to be in front (Tandem no longer on top). */
    private const val GAME_SETTLE_MS = 2_000L
    private const val HELPER = "libpadlink.so"
    /** How long a "Play with <name>" offer waits for A or Start. */
    const val OFFER_MS = 12_000L

    var state by mutableStateOf(PadPlan.State())
        private set

    /** The pad on offer, for the companion's card (null: none). */
    val offer: PadPlan.Pad? get() = state.pad(state.offer)

    /** The pad in use, or null for the built-in controls. */
    val linked: PadPlan.Pad? get() = state.pad(state.linked)

    /** A short message for the bottom screen during a game ([notice] then clears itself). */
    var notice by mutableStateOf<String?>(null)
        private set

    private var app: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "tandem-padlink") }

    /** Whether this build has the helper and the device has a built-in controller to drive. */
    fun supported(ctx: Context): Boolean =
        File(ctx.applicationInfo.nativeLibraryDir, HELPER).isFile && builtIn() != null

    /** Supported, and Shizuku or root is there to run the helper. Cheap: no command is run. */
    fun available(ctx: Context): Boolean = supported(ctx) && (Shell.ready() || Root.present())

    /** Why it isn't available, in one line (Settings shows it). */
    fun whyNot(ctx: Context): String = when {
        !File(ctx.applicationInfo.nativeLibraryDir, HELPER).isFile -> "This build of Tandem has no controller helper."
        builtIn() == null -> "This device has no built-in controller to stand in for."
        else -> "Needs Shizuku or root: a game only reads the built-in controller, and only they can feed it."
    }

    private fun builtIn(): InputDevice? = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
        .firstOrNull { Gamepads.isPad(it) && Gamepads.isBuiltIn(it) }

    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        devicesChanged()
    }

    /** Controllers came or went (Gamepads), or the auto setting changed. */
    fun devicesChanged() {
        val ctx = app ?: return
        if (!available(ctx)) {
            if (state.linked != null || state.offer != null) dispatch(PadPlan.Event.Choose(null))
            return
        }
        val pads = Gamepads.connected().map { PadPlan.Pad(it.descriptor, it.name.orEmpty().ifBlank { "Controller" }) }
        dispatch(PadPlan.Event.Devices(pads, inGame(ctx), ControllerStore.settings.usePads))
    }

    /** The user picked a pad, or null for the built-in controls. */
    fun choose(descriptor: String?) {
        devicesChanged()
        dispatch(PadPlan.Event.Choose(descriptor))
    }

    /** The next choice, for the Controller tile: Built-in, then each connected pad. */
    fun cycle() {
        devicesChanged()
        if (state.pads.isEmpty()) { show("Connect a controller first"); return }
        dispatch(PadPlan.Event.Choose(PadPlan.next(state)))
    }

    /** A game just started (Sessions): a pad connected before it is offered once the game is in front. */
    fun gameStarted() {
        main.postDelayed({ devicesChanged() }, GAME_SETTLE_MS)
    }

    fun accept() = dispatch(PadPlan.Event.Accept)
    fun decline() = dispatch(PadPlan.Event.Decline)

    /** A game is in front: Tandem isn't, and a play session or the bottom-screen companion is open. */
    private fun inGame(ctx: Context): Boolean =
        !Gamepads.tandemTop && (GameCompanion.up() || Sessions.openKey(ctx) != null)

    private val expire = Runnable { if (state.offer != null) decline() }

    private fun dispatch(e: PadPlan.Event) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post { dispatch(e) }; return }
        val before = state
        val step = PadPlan.reduce(before, e)
        state = step.state
        if (state.linked != before.linked || state.offer != before.offer) Log.i(TAG, "${e::class.simpleName}: linked ${before.linked?.take(6)} -> ${state.linked?.take(6)}, offer ${before.offer?.take(6)} -> ${state.offer?.take(6)}")
        step.notice?.let { show(it) }
        if (state.offer != before.offer) {
            main.removeCallbacks(expire)
            if (state.offer != null) {
                main.postDelayed(expire, OFFER_MS)
                offer?.let { if (GameCompanion.up()) notice = null else systemToast("Press A or Start on ${it.name} to play with it") }
            }
        }
        val want = state.linked?.let { Want(it, link = true) } ?: state.offer?.let { Want(it, link = false) }
        if (want != wanted) {
            wanted = want
            worker.execute { reconcile() }
        }
    }

    // --- Messages -------------------------------------------------------------------------------------------------

    private val clearNotice = Runnable { notice = null }

    private fun show(text: String) {
        when {
            Gamepads.tandemTop -> Toasts.show(text)
            GameCompanion.up() -> {
                notice = text
                main.removeCallbacks(clearNotice)
                main.postDelayed(clearNotice, Toasts.DEFAULT_MS)
            }
            else -> systemToast(text)
        }
    }

    /** No Tandem window to show it in (the game has both screens): Android's own toast, over the game. */
    private fun systemToast(text: String) {
        val ctx = app ?: return
        runCatching { android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_LONG).show() }
    }

    // --- The helper (worker thread only) --------------------------------------------------------------------------

    private data class Want(val pad: String, val link: Boolean)

    @Volatile private var wanted: Want? = null
    private var running: Helper? = null

    /** Makes the running helper match [wanted]: nothing, watching a pad, or linking it. */
    private fun reconcile() {
        val want = wanted
        val have = running
        if (have != null && have.alive() && want != null && have.pad == want.pad) {
            if (want.link && !have.link) { have.send('l'); have.link = true }
            else if (!want.link && have.link) { have.send('w'); have.link = false }
            return
        }
        if (have != null) Log.i(TAG, "Helper restart: alive=${have.alive()} same=${have.pad == want?.pad} want=$want")
        have?.stop()
        running = null
        if (want == null) return
        val h = start(want)
        if (h == null) {
            main.post { if (wanted == want) { wanted = null; dispatch(PadPlan.Event.Failed(want.pad)) } }
            return
        }
        running = h
    }

    /** Runs a short command through Shizuku, else root; its output or null. */
    private fun command(vararg cmd: String): String? = Shell.run(*cmd) ?: Root.run(cmd.joinToString(" "))

    private fun readLayout(path: String?): KeyLayout {
        if (path == null) return KeyLayout(emptyMap(), emptyMap())
        val text = runCatching { File(path).readText() }.getOrNull() ?: command("cat", path).orEmpty()
        return KeyLayout.parse(text)
    }

    private fun start(want: Want): Helper? {
        val ctx = app ?: return null
        val target = builtIn() ?: return null
        val nodes = InputNodes.parse(command("dumpsys", "input") ?: return null)
        val from = nodes.firstOrNull { it.descriptor == want.pad } ?: return null.also { Log.w(TAG, "No input node for the pad") }
        val to = nodes.firstOrNull { it.descriptor == target.descriptor } ?: return null.also { Log.w(TAG, "No input node for ${target.name}") }
        val nintendo = want.pad in ControllerStore.nintendo
        val source = readLayout(from.keyLayout ?: "/system/usr/keylayout/Generic.kl")
        val rules = PadMap.build(source, readLayout(to.keyLayout), nintendo).map { it.arg }
        val accept = PadMap.acceptKeys(source, nintendo).joinToString(",")
        val exe = File(ctx.applicationInfo.nativeLibraryDir, HELPER).path
        val args = listOf(exe, from.path, to.path, if (want.link) "link" else "watch") + rules + listOfNotNull(accept.ifEmpty { null }?.let { "A$it" })
        // Shizuku first (allowed already, no prompt), root when Shizuku can't (or isn't there).
        for (via in listOf("shizuku", "root")) {
            val p = (if (via == "shizuku") Shell.spawn(args) else Root.spawn(args)) ?: continue
            val h = Helper(want.pad, p, want.link)
            // The first time, the root app asks the user: give them time to answer.
            if (h.awaitReady(if (via == "root") 30 else 3)) {
                Log.i(TAG, "Helper up through $via: ${from.name} to ${to.name}, ${if (want.link) "linked" else "watching"}")
                return h
            }
            h.stop()
        }
        return null
    }

    /** One running helper and the thread that reads what it says. */
    private class Helper(val pad: String, val process: Process, @Volatile var link: Boolean) {
        private val input = process.outputStream
        private val ready = java.util.concurrent.CountDownLatch(1)
        @Volatile private var stopping = false
        @Volatile private var failed = false
        /** It said "ready": from then on its errors and its end are the pad's (before, start() tries the next way). */
        @Volatile private var up = false

        init {
            Thread({ read() }, "tandem-padlink-out").start()
        }

        fun awaitReady(seconds: Long): Boolean = ready.await(seconds, TimeUnit.SECONDS) && !failed

        fun alive(): Boolean = runCatching { process.exitValue(); false }.getOrDefault(true)

        fun send(c: Char) {
            runCatching { input.write(c.code); input.flush() }
        }

        /** Asks it to let go (it puts every button and stick back first), then makes sure it is gone. */
        fun stop() {
            stopping = true
            send('q')
            runCatching { input.close() }
            val done = runCatching { process.waitFor(800, TimeUnit.MILLISECONDS) }.getOrDefault(false)
            if (!done) process.destroy()
        }

        private fun read() {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (line != "ready") Log.i(TAG, "Helper said: $line")
                    when {
                        line == "ready" -> { up = true; ready.countDown() }
                        line == "accept" -> main.post { if (wanted?.pad == pad) accept() }
                        line == "gone" -> main.post { if (wanted?.pad == pad) { wanted = null; dispatch(PadPlan.Event.Gone(pad)) } }
                        line.startsWith("error") -> {
                            Log.w(TAG, "Helper: $line")
                            failed = true
                            ready.countDown()
                            if (up) main.post { if (wanted?.pad == pad) { wanted = null; dispatch(PadPlan.Event.Failed(pad)) } }
                        }
                    }
                }
            }
            Log.i(TAG, "Helper ended (${if (stopping) "asked to stop" else "by itself"})")
            ready.countDown()
            // Ended without saying why (killed, or it couldn't run at all): the pad is free again.
            if (up && !stopping && !failed) {
                failed = true
                main.post { if (wanted?.pad == pad && link) { wanted = null; dispatch(PadPlan.Event.Failed(pad)) } }
            }
        }
    }
}

/** Root through `su` (Magisk and the like), for when Shizuku isn't running. Asks the root app the first time. */
object Root {
    private val places = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "/su/bin/su")

    fun present(): Boolean = places.any { File(it).exists() }

    fun spawn(cmd: List<String>): Process? = if (!present()) null else runCatching {
        ProcessBuilder("su", "-c", cmd.joinToString(" ")).start()
    }.getOrNull()

    fun run(cmd: String): String? {
        val p = spawn(listOf(cmd)) ?: return null
        return runCatching {
            p.outputStream.close()
            val out = p.inputStream.bufferedReader().readText()
            if (p.waitFor() == 0) out else null
        }.getOrNull()
    }
}
