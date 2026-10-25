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

/**
 * Settings for motion flair (floating titles), the Recently played strip and the built-in apps, kept apart from the
 * library settings so they never collide with theme changes.
 */
@Serializable
data class ExtrasSettings(
    /** Logos, icons and titles float gently and rise in when they change. */
    val floating: Boolean = true,
    /** 0 = subtle, 1 = normal, 2 = lively. */
    val intensity: Int = 1,
    /** The icon grid's Recently played strip: "button" (L2 shows it), "always" or "off". */
    val recentStrip: String = "button",
    /** Show a name under the highlighted icon when icon labels are off. */
    val focusLabels: Boolean = true,
    /** Coming Home while a game runs lands on the Resume page left of the icon grid. */
    val resumeLanding: Boolean = true,
)

/** Notes the Journal keeps: per session ("<game key>@<start ms>") and per game (game key). */
@Serializable
data class JournalNotes(
    val sessions: Map<String, String> = emptyMap(),
    val games: Map<String, String> = emptyMap(),
)

/** `extras.json` (settings) and `journal.json` (notes) in the app's files folder, written like [HomeStore]. */
object ExtrasStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor()
    private var dir: File? = null

    var settings by mutableStateOf(ExtrasSettings())
        private set
    var notes by mutableStateOf(JournalNotes())
        private set

    fun init(ctx: Context) {
        if (dir != null) return
        val d = ctx.applicationContext.filesDir
        dir = d
        reload()
    }

    /** Reads extras.json and journal.json again (after a restore). Blocking. */
    fun reload() {
        val d = dir ?: return
        val s = runCatching { json.decodeFromString<ExtrasSettings>(File(d, "extras.json").readText()) }.getOrDefault(ExtrasSettings())
        val n = runCatching { json.decodeFromString<JournalNotes>(File(d, "journal.json").readText()) }.getOrDefault(JournalNotes())
        synchronized(this) { settings = s; notes = n }
    }

    fun update(change: (ExtrasSettings) -> ExtrasSettings) {
        val snapshot = synchronized(this) { change(settings).also { settings = it } }
        write("extras.json", json.encodeToString(snapshot))
    }

    fun updateNotes(change: (JournalNotes) -> JournalNotes) {
        val snapshot = synchronized(this) { change(notes).also { notes = it } }
        write("journal.json", json.encodeToString(snapshot))
    }

    private fun write(name: String, text: String) {
        val d = dir ?: return
        writer.execute {
            val tmp = File(d, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(d, name))
        }
    }
}
