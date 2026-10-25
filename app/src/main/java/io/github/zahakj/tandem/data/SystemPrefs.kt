package io.github.zahakj.tandem.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/** The user's choices for one system. */
@Serializable
data class SystemConfig(
    val enabled: Boolean = true,
    /** Name of the default emulator; null means the first installed one from platforms.json. */
    val player: String? = null,
    /** Start this system's games on the second (bottom) screen. */
    val bottomScreen: Boolean = false,
    /** The bottom screen while one of its games plays on the top one: "art", "tandem" or "off"; null = default (GameCompanion). */
    val companion: String? = null,
    /** Extra game folders, as absolute paths. */
    val folders: List<String> = emptyList(),
)

@Serializable
data class SystemsData(
    val systems: Map<String, SystemConfig> = emptyMap(),
    /** Never use the second screen for games, even when there is one. */
    val singleScreen: Boolean = false,
    val launchAnimation: Boolean = true,
    /** Apps the user added to Android Games, and detected games they took out. */
    val gameApps: Set<String> = emptySet(),
    val notGameApps: Set<String> = emptySet(),
    /** Folder path -> document tree link the user granted, so games there can be shared with emulators. */
    val trees: Map<String, String> = emptyMap(),
)

/** Systems, emulators and launch choices, kept in systems.json next to Tandem's other data. */
object SystemPrefs {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null

    var data by mutableStateOf(SystemsData())
        private set

    fun init(ctx: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            val f = File(ctx.applicationContext.filesDir, "systems.json")
            data = runCatching { json.decodeFromString<SystemsData>(f.readText()) }.getOrDefault(SystemsData())
            file = f
        }
    }

    /** Reads systems.json again (after a restore). Blocking. */
    fun reload() {
        val f = file ?: return
        val d = runCatching { json.decodeFromString<SystemsData>(f.readText()) }.getOrDefault(SystemsData())
        synchronized(this) { data = d }
    }

    fun config(id: String): SystemConfig = data.systems[id] ?: SystemConfig()

    fun update(change: (SystemsData) -> SystemsData) {
        synchronized(this) { data = change(data) }
        val snapshot = data
        val target = file ?: return
        writer.execute {
            val tmp = File(target.path + ".tmp")
            tmp.writeText(json.encodeToString(snapshot))
            tmp.renameTo(target)
        }
    }

    fun updateSystem(id: String, change: (SystemConfig) -> SystemConfig) =
        update { d -> d.copy(systems = d.systems + (id to change(d.systems[id] ?: SystemConfig()))) }
}
