package io.github.zahakj.tandem.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.Binding
import io.github.zahakj.tandem.Bindings
import io.github.zahakj.tandem.Command
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/** Per-controller choices and the controller Home button (see Gamepads.kt and ControllerHomeService.kt). */
@Serializable
data class ControllerSettings(
    /** Controllers (by [android.view.InputDevice.getDescriptor]) whose A/B and X/Y are swapped inside Tandem. */
    val nintendo: Set<String> = emptySet(),
    /** Names of those controllers, so Settings can still say which ones they are while they are away. */
    val names: Map<String, String> = emptyMap(),
    /** Holding a controller's Home (guide) button: "recents" (Android's recent apps) or "switcher" (Tandem's). */
    val holdHome: String = "switcher",
    /** Controller passthrough (PadLink.kt): a connected controller plays as the built-in one without asking. */
    val usePads: Boolean = false,
    /**
     * Rebound commands (Bindings.kt): command name to its binding ("L1+R1+Select", "hold:Select", "" for no button).
     * Only the commands that differ from their default are here (see [io.github.zahakj.tandem.Bindings.stored]).
     */
    val bindings: Map<String, String> = emptyMap(),
)

/** `controllers.json` in the app's files folder, written like [ExtrasStore]. Safe to init from the service too. */
object ControllerStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor()
    private var dir: File? = null

    var settings by mutableStateOf(ControllerSettings())
        private set

    /** Read on the main thread for every key, so kept as a plain set too. */
    @Volatile
    var nintendo: Set<String> = emptySet()
        private set

    /** What every command is bound to, read for every key (and from the service's key thread), so a plain field. */
    @Volatile
    var bindings: Map<Command, Binding?> = Bindings.defaults
        private set

    /** The same map as Compose state, so hints and the rebind page redraw when a binding changes. */
    var live by mutableStateOf(Bindings.defaults)
        private set

    fun binding(c: Command): Binding? = live[c]

    @Synchronized
    fun init(ctx: Context) {
        if (dir != null) return
        val d = ctx.applicationContext.filesDir
        dir = d
        reload()
    }

    /** Reads controllers.json again (after a restore). Blocking. */
    fun reload() {
        val d = dir ?: return
        val s = runCatching { json.decodeFromString<ControllerSettings>(File(d, "controllers.json").readText()) }.getOrDefault(ControllerSettings())
        synchronized(this) { apply(s) }
    }

    private fun apply(s: ControllerSettings) {
        settings = s
        nintendo = s.nintendo
        val map = Bindings.resolve(s.bindings)
        bindings = map
        live = map
    }

    /** Stores [map] (Bindings.kt) as the live bindings. */
    fun setBindings(map: Map<Command, Binding?>) = update { it.copy(bindings = Bindings.stored(map)) }

    fun update(change: (ControllerSettings) -> ControllerSettings) {
        val snapshot = synchronized(this) { change(settings).also { apply(it) } }
        val d = dir ?: return
        val text = json.encodeToString(snapshot)
        writer.execute {
            val tmp = File(d, "controllers.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(d, "controllers.json"))
        }
    }
}
