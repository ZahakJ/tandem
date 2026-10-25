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

/** Which screen an app opens on. */
object AppScreen {
    const val AUTO = "auto"
    const val TOP = "top"
    const val BOTTOM = "bottom"
    val ALL = listOf(AUTO, TOP, BOTTOM)

    fun label(screen: String): String = when (screen) { TOP -> "Top"; BOTTOM -> "Bottom"; else -> "Auto" }

    /** The next choice after [screen], for a row that A steps through. */
    fun next(screen: String): String = ALL[(ALL.indexOf(screen).coerceAtLeast(0) + 1) % ALL.size]
}

/** The user's choices for one Android app. Everything defaults to "as Android says". */
@Serializable
data class AppPref(
    /** "top" or "bottom"; null follows the default for apps ([AppsData.defaultScreen]). */
    val screen: String? = null,
    /** Shown instead of the app's own name. */
    val name: String? = null,
    /** A picture (absolute path) shown instead of the app's icon. */
    val art: String? = null,
    /** true: in Android Games; false: kept out even though it says it is a game; null: as detected. */
    val game: Boolean? = null,
    /** The system id the app is filed under as a game; null for Android Games. */
    val platform: String? = null,
    /** Left out of the All apps drawer (Show hidden apps brings it back). */
    val hidden: Boolean = false,
) {
    val isDefault: Boolean get() = this == AppPref()
}

@Serializable
data class AppsData(
    /** Format version; older files are brought up to date by [AppPrefs.migrate]. */
    val version: Int = 0,
    /** Package name -> choices. Packages with nothing changed are left out. */
    val apps: Map<String, AppPref> = emptyMap(),
    /** Where apps without a choice of their own open: "auto", "top" or "bottom". */
    val defaultScreen: String = AppScreen.AUTO,
    /** Holding Back moves the app in front to the other screen (needs the Controller Home button service and Shizuku). */
    val holdBackMoves: Boolean = true,
    /** The same for games; off, a game in front is never moved by holding Back. */
    val moveGames: Boolean = false,
) {
    fun of(pkg: String): AppPref = apps[pkg] ?: NONE

    /** Where [pkg] opens: its own choice, else the default. "auto" lets Tandem decide. */
    fun screenOf(pkg: String): String = apps[pkg]?.screen ?: defaultScreen

    fun with(pkg: String, change: (AppPref) -> AppPref): AppsData {
        val next = change(of(pkg))
        return copy(apps = if (next.isDefault) apps - pkg else apps + (pkg to next))
    }
}

/** Shared "nothing chosen" value, so looking up an app without choices doesn't allocate. */
private val NONE = AppPref()

/**
 * Per-app choices (screen, name, icon, game, hidden) as `app-prefs.json` in the app's files folder, kept the same way as
 * [SystemPrefs]: Compose state in memory, written atomically on a background thread.
 */
object AppPrefs {
    const val FILE = BackupFiles.APP_PREFS
    const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null

    var data by mutableStateOf(AppsData(version = VERSION))
        private set

    /** Safe to call from anywhere, any number of times; reads the file once. */
    fun init(ctx: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            SystemPrefs.init(ctx)
            val f = File(ctx.applicationContext.filesDir, FILE)
            file = f
            reload()
        }
    }

    /** Reads the file again (after a restore); an older file is brought up to date and written back. */
    fun reload() {
        val f = file ?: return
        val stored = decode(f.readTextOrNull())
        val now = migrate(stored, SystemPrefs.data.gameApps, SystemPrefs.data.notGameApps)
        data = now
        if (stored.version != now.version) save(now)
    }

    fun of(pkg: String): AppPref = data.of(pkg)
    fun name(pkg: String): String? = data.apps[pkg]?.name
    fun art(pkg: String): String? = data.apps[pkg]?.art
    fun screen(pkg: String): String = data.screenOf(pkg)

    fun update(pkg: String, change: (AppPref) -> AppPref) = updateAll { it.with(pkg, change) }

    fun updateAll(change: (AppsData) -> AppsData) {
        val snapshot = synchronized(this) { change(data).also { data = it } }
        save(snapshot)
    }

    private fun save(snapshot: AppsData) {
        val target = file ?: return
        val text = encode(snapshot)
        writer.execute {
            val tmp = File(target.path + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(target)
        }
    }

    // ---- File format (no Android, so it can be tested) ----

    fun encode(d: AppsData): String = json.encodeToString(d)

    /** The data in [text]; defaults (version 0) when there is none or it can't be read. */
    fun decode(text: String?): AppsData =
        text?.let { runCatching { json.decodeFromString<AppsData>(it) }.getOrNull() } ?: AppsData()

    /**
     * Brings [d] up to [VERSION]. Version 0 (no file yet): Android Games additions and removals used to live in
     * systems.json ([gameApps], [notGameApps]); they become each app's [AppPref.game], unless the app already says.
     * Unknown screens fall back to auto.
     */
    fun migrate(d: AppsData, gameApps: Set<String>, notGameApps: Set<String>): AppsData {
        var out = d
        if (out.version < 1) {
            for (pkg in gameApps) if (out.apps[pkg]?.game == null) out = out.with(pkg) { it.copy(game = true) }
            for (pkg in notGameApps - gameApps) if (out.apps[pkg]?.game == null) out = out.with(pkg) { it.copy(game = false) }
        }
        val screens = setOf(AppScreen.TOP, AppScreen.BOTTOM)
        if (out.apps.values.any { it.screen != null && it.screen !in screens }) {
            out = out.copy(apps = out.apps.mapValues { (_, p) -> if (p.screen != null && p.screen !in screens) p.copy(screen = null) else p }.filterValues { !it.isDefault })
        }
        if (out.defaultScreen !in AppScreen.ALL) out = out.copy(defaultScreen = AppScreen.AUTO)
        return out.copy(version = VERSION)
    }

    private fun File.readTextOrNull(): String? = runCatching { if (isFile) readText() else null }.getOrNull()
}
