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

/** Settings for running games, Home-screen mode, the screensaver and privacy. Kept apart from the library settings. */
@Serializable
data class HomeSettings(
    /** Close the previous emulator when a different game starts. null means "on" (it needs Shizuku either way). */
    val closePrevious: Boolean? = null,
    /** Ask first when starting a game would close one still running (ui/CloseAsk.kt). Needs Shizuku to see them. */
    val askBeforeClosing: Boolean = true,
    /** Package names pinned to the dock, in order. null until the user changes it, so a sensible default shows. */
    val dock: List<String>? = null,
    val showDock: Boolean = true,
    /** Minutes of no input before the screensaver; 0 = off. */
    val screensaverMinutes: Int = 0,
    /** Hidden games can only be revealed with L1 + R1 + Select, and the Settings toggle is ignored. */
    val privateMode: Boolean = false,
    /** Games still open are frozen a minute after the screen goes off, so the handheld can really sleep (SleepFreeze.kt). */
    val freezeAsleep: Boolean = true,
    /** In private mode, airplane mode goes on while a hidden game plays (Offline.kt). Needs Shizuku. */
    val offlineHidden: Boolean = false,
)

/** One stretch of play: game key, start time (ms) and length in seconds. Short names keep the file small. */
@Serializable
data class PlaySession(val k: String, val s: Long, val d: Long) {
    val end: Long get() = s + d * 1000
}

/** RetroAchievements sign-in and which RetroAchievements game each Tandem game matched (0 = no match). */
@Serializable
data class RaAccount(
    val user: String = "",
    val key: String = "",
    val enabled: Boolean = false,
    val matches: Map<String, Int> = emptyMap(),
)

/**
 * Play history, home settings and the RetroAchievements account, as JSON in the app's files folder, the same way
 * [Store] keeps the library: Compose state in memory, written atomically on a background thread.
 */
object HomeStore {
    private const val MAX_SESSIONS = 5000
    /** Two stints of the same game this close together (popping out to Tandem and back) count as one session. */
    private const val MERGE_GAP_MS = 5 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor()
    private var dir: File? = null

    var settings by mutableStateOf(HomeSettings())
        private set
    var sessions by mutableStateOf<List<PlaySession>>(emptyList())
        private set
    var ra by mutableStateOf(RaAccount())
        private set

    fun init(ctx: Context) {
        if (dir != null) return
        val d = ctx.applicationContext.filesDir
        dir = d
        reload()
    }

    /** Reads home.json, sessions.json and achievements.json again (after a restore). Blocking. */
    fun reload() {
        val d = dir ?: return
        val h = runCatching { json.decodeFromString<HomeSettings>(File(d, "home.json").readText()) }.getOrDefault(HomeSettings())
        val s = runCatching { json.decodeFromString<List<PlaySession>>(File(d, "sessions.json").readText()) }.getOrDefault(emptyList())
        val r = runCatching { json.decodeFromString<RaAccount>(File(d, "achievements.json").readText()) }.getOrDefault(RaAccount())
        synchronized(this) { settings = h; sessions = s; ra = r }
    }

    fun updateSettings(change: (HomeSettings) -> HomeSettings) {
        val snapshot = synchronized(this) { change(settings).also { settings = it } }
        write("home.json", json.encodeToString(snapshot))
    }

    fun updateRa(change: (RaAccount) -> RaAccount) {
        val snapshot = synchronized(this) { change(ra).also { ra = it } }
        write("achievements.json", json.encodeToString(snapshot))
    }

    fun addSession(key: String, start: Long, seconds: Long) {
        val snapshot = synchronized(this) {
            val last = sessions.lastOrNull()
            val next = if (last != null && last.k == key && start - last.end in 0..MERGE_GAP_MS) {
                sessions.dropLast(1) + last.copy(d = last.d + seconds)
            } else {
                sessions + PlaySession(key, start, seconds)
            }
            next.takeLast(MAX_SESSIONS).also { sessions = it }
        }
        write("sessions.json", json.encodeToString(snapshot))
    }

    /** Forgets a game's sessions, when its play history is reset. */
    fun forgetGame(key: String) {
        val snapshot = synchronized(this) { sessions.filter { it.k != key }.also { sessions = it } }
        write("sessions.json", json.encodeToString(snapshot))
    }

    /**
     * Changes the play history by hand (Stats › Sessions): [change] gets every session, oldest first. Each game whose
     * sessions changed gets its play time, play count and last played worked out again ([PlayHistory.recompute]).
     * A game Tandem no longer keeps a record of (deleted without its stats) is left without one.
     */
    fun editSessions(change: (List<PlaySession>) -> List<PlaySession>) {
        val (before, after) = synchronized(this) {
            val old = sessions
            old to PlayHistory.ordered(change(PlayHistory.ordered(old))).takeLast(MAX_SESSIONS).also { sessions = it }
        }
        write("sessions.json", json.encodeToString(after))
        val keys = PlayHistory.changedKeys(before, after).filter { it in Store.meta }
        val old = before.groupBy { it.k }
        val now = after.groupBy { it.k }
        Store.updateAll(keys.associateWith { k -> { m: GameMeta -> PlayHistory.recompute(m, old[k].orEmpty(), now[k].orEmpty()) } })
    }

    private fun write(name: String, text: String) {
        val d = dir ?: return
        writer.execute {
            val file = File(d, name)
            val tmp = File(d, "$name.tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
        }
    }
}
