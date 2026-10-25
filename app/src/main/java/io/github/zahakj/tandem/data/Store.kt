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

/** Everything Tandem knows about one game beyond its file. Art paths are absolute file paths. */
@Serializable
data class GameMeta(
    val title: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val developer: String? = null,
    val publisher: String? = null,
    val year: Int? = null,
    val players: String? = null,
    /** 0..1 */
    val rating: Float? = null,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    /** Emulator component to use instead of the system's default. */
    val emulator: String? = null,
    /** Screen to start on: "top", "bottom", or null for the system's choice. */
    val screen: String? = null,
    /** The bottom screen while this game plays: "art", "tandem", "off", or null for the system's choice (GameCompanion). */
    val companion: String? = null,
    val box: String? = null,
    val hero: String? = null,
    val logo: String? = null,
    val screenshot: String? = null,
    /** Title-screen picture (scraped into downloaded_media/<system>/screenshot_title). */
    val titleScreen: String? = null,
    /** Short video clip (downloaded_media/<system>/videos), for the bottom screen. */
    val video: String? = null,
    /** How long the game takes, from HowLongToBeat. */
    val timeToBeat: TimeToBeat? = null,
    /** Fields the user set by hand (see [MetaField]); scraping never overwrites these. */
    val lockedFields: Set<String> = emptySet(),
    val playCount: Int = 0,
    val playSeconds: Long = 0,
    val lastPlayed: Long = 0,
    val firstSeen: Long = 0,
    /** A jingle picked for this game (absolute path, normally in downloaded_media/<system>/jingles); see [JingleLookup]. */
    val jingle: String? = null,
    /** No hover jingle for this game, even when its jingles folder has one. */
    val jingleOff: Boolean = false,
    /** An Android app started on the other screen with this game (a guide, a map, a music player); see CompanionApps. */
    val companionApp: String? = null,
    /** Close [companionApp] when Tandem is back from the game (true), or leave it running. */
    val companionAppClose: Boolean = true,
    /** RetroAchievements game id chosen by hand: null finds it automatically, 0 means no achievements. */
    val raId: Int? = null,
    /** This game's logo size and place over its art; null is Tandem's own. */
    val logoFit: LogoFit? = null,
)

/** HowLongToBeat's averages, in seconds; 0 when nobody has logged that one yet. */
@Serializable
data class TimeToBeat(val main: Int = 0, val extra: Int = 0, val complete: Int = 0) {
    val any: Boolean get() = main > 0 || extra > 0 || complete > 0

    companion object {
        /** "12 h", "12½ h", "45 min". */
        fun hours(seconds: Int): String {
            if (seconds < 3600) return "${maxOf(1, seconds / 60)} min"
            val halves = Math.round(seconds / 1800.0).toInt()
            return "${halves / 2}${if (halves % 2 == 1) "½" else ""} h"
        }
    }
}

enum class SortOrder(val label: String) {
    Name("Name"), Recent("Recently played"), MostPlayed("Most played"), Year("Release year"), Rating("Rating"), Added("Recently added")
}

@Serializable
data class Settings(
    val sort: SortOrder = SortOrder.Name,
    val showHidden: Boolean = false,
    val showTitles: Boolean = false,
    val autoImportDone: Boolean = false,
    /** Bottom screen while browsing: "details", "art" (animated art only), "both" (art with info on top) or "clock". */
    val bottomMode: String = "details",
    val reduceMotion: Boolean = false,
    /** The first-run welcome (layout, Cocoon things, resume, Home screen) has been shown. */
    val welcomeDone: Boolean = false,
    /** "Import my Cocoon setup" ran once (by itself on the first start that found a Cocoon backup, or by hand). */
    val cocoonSetupDone: Boolean = false,
    /** Themes, layout, wallpapers, prompts and sound; see Look.kt. */
    val look: Look = Look(),
    /** Folders and whole systems hidden like hidden games (see HiddenFolders.kt). */
    val hiddenFolders: List<HiddenFolder> = emptyList(),
)

/**
 * A ROM folder, or a whole system ([folder] null), kept out of sight like a hidden game. [paths] are where its games
 * live, relative to the ROMs folder ("snes/RPG") or absolute for folders outside it: everything under them is
 * hidden, at any depth, including games copied in later. [known] are a few of its games' keys, which stay the same
 * when the folder is renamed, so a renamed folder can be found again. Only paths and keys: never titles or art.
 */
@Serializable
data class HiddenFolder(
    val system: String,
    val folder: String? = null,
    val paths: List<String> = emptyList(),
    val known: List<String> = emptyList(),
)

/**
 * Tandem's own data, as plain JSON in the app's files folder. Keys are "<system>/<file name>", so moving a game
 * between subfolders keeps its details.
 */
object Store {
    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    // Below the UI threads: encoding a big library is never worth a dropped frame.
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "tandem-store")
    }
    private lateinit var metaFile: File
    private lateinit var settingsFile: File

    // Compose state, so any screen showing a game's details updates as soon as they change.
    var meta by mutableStateOf<Map<String, GameMeta>>(emptyMap())
        private set
    var settings by mutableStateOf(Settings())
        private set

    fun init(ctx: Context) {
        if (::metaFile.isInitialized) return
        metaFile = File(ctx.filesDir, "library.json")
        settingsFile = File(ctx.filesDir, "settings.json")
        reload()
    }

    /** Reads library.json and settings.json again (after a restore). Blocking. */
    fun reload() {
        if (!::metaFile.isInitialized) return
        val m = runCatching { json.decodeFromString<Map<String, GameMeta>>(metaFile.readText()) }.getOrDefault(emptyMap())
        val s = runCatching { json.decodeFromString<Settings>(settingsFile.readText()) }.getOrDefault(Settings())
        synchronized(this) { meta = m; settings = s }
    }

    /** Shared "nothing known" value, so looking up a game without details doesn't allocate. */
    private val NoMeta = GameMeta()

    fun get(key: String): GameMeta = meta[key] ?: NoMeta

    fun update(key: String, change: (GameMeta) -> GameMeta) {
        synchronized(this) { meta = meta + (key to change(meta[key] ?: GameMeta())) }
        saveMeta()
    }

    /** Forgets everything about one game (it was deleted). */
    fun remove(key: String) {
        synchronized(this) { if (key !in meta) return; meta = meta - key }
        saveMeta()
    }

    fun updateAll(changes: Map<String, (GameMeta) -> GameMeta>) {
        if (changes.isEmpty()) return
        synchronized(this) { meta = meta.toMutableMap().also { m -> changes.forEach { (k, f) -> m[k] = f(m[k] ?: GameMeta()) } } }
        saveMeta()
    }

    fun updateSettings(change: (Settings) -> Settings) {
        synchronized(this) { settings = change(settings) }
        val snapshot = settings
        writer.execute { atomicWrite(settingsFile, json.encodeToString(snapshot)) }
    }

    private val metaQueued = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Writes the details in the background. Changes that come in while a write is already waiting share it (the
     * write takes the latest), so a burst of updates encodes the whole library once, not once per change.
     */
    private fun saveMeta() {
        if (!metaQueued.compareAndSet(false, true)) return
        writer.execute {
            metaQueued.set(false)
            atomicWrite(metaFile, json.encodeToString(meta))
        }
    }

    private fun atomicWrite(file: File, text: String) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }
}
