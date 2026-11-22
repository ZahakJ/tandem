package io.github.zahakj.tandem

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.ui.inBackground
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Peanuts Native (github.com/igawa6/peanuts-native): Switch games rebuilt to run natively on the handheld, each
 * built once on the device from the user's own dump, with Eden Duo's companions on the second screen. A Switch game
 * that Peanuts has built plays there by default ([playerFor]); one it can build offers it in the game's menu
 * ([build]); every other Switch game goes to its emulator as before.
 *
 * What Peanuts knows is read through its control command (peanuts/docs/CONTROL.md): `list` writes the library to
 * its ctl folder, and `build` starts a build. The command is guarded by the DUMP permission, which adb's shell holds,
 * so both go through Shizuku; without Shizuku Tandem knows nothing of Peanuts and plays games as before.
 *
 * There may be two of it: the release, and a build of it with more game profiles beside it under its own package
 * ([PACKAGES]). Each keeps its own library, builds and saves; a game plays in the one that has built it, and is
 * built in the one that can build it, the fuller build first.
 */
object PeanutsNative {
    const val PKG = "dev.nxnative.android.peanuts"
    const val PLUS = "dev.nxnative.android.peanuts.plus"
    /** Every Peanuts app Tandem knows; a game ready in both plays in the later (fuller, newer) one. */
    val PACKAGES = listOf(PKG, PLUS)
    const val PLAYER = "Peanuts Native"

    private fun ctl(pkg: String) = "$pkg/dev.peanuts.ctl.Command"
    private fun libraryFile(pkg: String) = "/sdcard/Android/data/$pkg/files/ctl/library.json"
    private fun statusFile(pkg: String) = "/sdcard/Android/data/$pkg/files/ctl/status.json"

    fun isPeanuts(pkg: String?): Boolean = pkg in PACKAGES

    /** One game in Peanuts' library. [state]: `ready`, `build`, `building` or `not_in_app` (no profile for it). */
    data class Entry(val titleId: String, val name: String, val state: String, val companion: Boolean)

    /** Each Peanuts app's games by the path of their file in the user's game folder, as last read. */
    var libraries by mutableStateOf<Map<String, Map<String, Entry>>>(emptyMap())
        private set

    /** The game being built, and how far along ("Importing · 31 %"), while one is. */
    var building by mutableStateOf<Pair<String, String>?>(null)
        private set

    private var readAt = 0L

    fun installed(): Boolean = PACKAGES.any(Emulators::isInstalled)

    /** [pkg]'s row for [game]. */
    fun of(game: Game, pkg: String): Entry? = libraries[pkg]?.let { match(it, game.file.path, game.title) }

    /**
     * [game]'s row: in the Peanuts app that has built it, else in the one that can build it, else any. A row is found
     * by the game's file, else by the folder Peanuts imported it into (its `.nup` sits in a folder named after the
     * dump, beside it: `switch/<Name>.nsp` becomes `switch/<Name>/<Name>.nup`), else by its name.
     */
    fun of(game: Game): Entry? = appFor(game)?.let { of(game, it) }

    /** The Peanuts app [game] belongs to: the fullest one it is ready in, else the fullest one that can build it. */
    fun appFor(game: Game): String? {
        val rows = PACKAGES.mapNotNull { p -> of(game, p)?.let { p to it } }
        return rows.lastOrNull { it.second.state == "ready" }?.first
            ?: rows.lastOrNull { it.second.state == "build" || it.second.state == "building" }?.first
            ?: rows.firstOrNull()?.first
    }

    internal fun match(lib: Map<String, Entry>, path: String, title: String): Entry? {
        lib[path]?.let { return it }
        val dir = path.substringBeforeLast('/')
        val stem = path.substringAfterLast('/').substringBeforeLast('.')
        lib.entries.firstOrNull { (p, _) -> p.substringBeforeLast('/') == "$dir/$stem" }?.let { return it.value }
        val name = simple(title)
        return lib.values.firstOrNull { simple(it.name) == name }
    }

    /** "Fire Emblem: Three Houses" and "Fire Emblem Three Houses" are the same game. */
    private fun simple(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** Built and ready to play natively. */
    fun ready(game: Game): Boolean = of(game)?.state == "ready"

    /** Peanuts can build it (it has a profile for this game and version). */
    fun buildable(game: Game): Boolean = of(game)?.state == "build"

    /**
     * The player for [game] when Peanuts should have it: a built game nobody chose another emulator for, or one whose
     * chosen player is Peanuts. Null leaves the choice to the system's emulators.
     */
    fun playerFor(game: Game, players: List<LaunchSpec>): LaunchSpec? {
        if (game.platform.id != "switch") return null
        val app = appFor(game) ?: return null
        val peanuts = players.firstOrNull { it.packageName == app } ?: return null
        val chosen = game.meta.emulator
        return when {
            !ready(game) -> null
            chosen == null || chosen == PLAYER -> peanuts
            else -> null
        }
    }

    /** Reads Peanuts' library again, in the background, at most every [minGapMs]. */
    fun refresh(minGapMs: Long = 30_000L) {
        // The installed list may not be read yet at startup: then Peanuts is simply asked (a missing app answers nothing).
        if ((Emulators.installed.isNotEmpty() && !installed()) || !Shell.ready()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - readAt < minGapMs) return
        readAt = now
        val apps = PACKAGES.filter { Emulators.installed.isEmpty() || Emulators.isInstalled(it) }
        val read = HashMap<String, Map<String, Entry>>()
        var job: Pair<String, String>? = null
        inBackground({
            for (pkg in apps) {
                Shell.run("am", "start", "-n", ctl(pkg), "-e", "cmd", "list")
                Thread.sleep(1500)
                Shell.run("cat", libraryFile(pkg))?.let(::parseLibrary)?.takeIf { it.isNotEmpty() }?.let { read[pkg] = it }
                if (job == null) job = Shell.run("cat", statusFile(pkg))?.let(::parseBuild)
            }
        }) {
            Log.i("Tandem", "Peanuts Native: ${read.values.sumOf { l -> l.count { it.value.state == "ready" } }} games built")
            if (read.isNotEmpty()) libraries = libraries + read
            building = job
        }
    }

    /** Starts building [game] in Peanuts (5 to 40 minutes on the Thor); it shows its progress in its own app. */
    fun build(ctx: Context, game: Game) {
        val pkg = appFor(game) ?: return
        val id = of(game, pkg)?.titleId ?: return
        val token = "tandem${System.currentTimeMillis()}"
        var answer: Pair<Boolean, String>? = null
        inBackground({
            // A restarted Peanuts knows no game until it has looked again.
            Shell.run("am", "start", "-n", ctl(pkg), "-e", "cmd", "scan")
            Thread.sleep(12_000)
            Shell.run("am", "start", "-n", ctl(pkg), "-e", "cmd", "build", "-e", "title_id", id, "-e", "id", token)
            repeat(10) {
                Thread.sleep(500)
                answer = Shell.run("cat", statusFile(pkg))?.let { parseAnswer(it, token) }
                if (answer != null) return@inBackground
            }
        }) {
            val (ok, why) = answer ?: (true to "")
            Log.i("Tandem", "Peanuts build $id: $ok $why")
            io.github.zahakj.tandem.ui.Toasts.show(
                if (ok) Tx.s(R.string.play_peanuts_building, game.title)
                else Tx.s(R.string.play_peanuts_cant, why.substringBefore(':').ifEmpty { Tx.s(R.string.play_peanuts_see_app) }),
                6000,
            )
            readAt = 0L
            refresh()
        }
    }

    private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    internal fun parseLibrary(text: String): Map<String, Entry> = runCatching {
        val rows = Json.parseToJsonElement(text).jsonObject["library"]?.jsonArray ?: return emptyMap()
        buildMap {
            for (row in rows) {
                val r = row.jsonObject
                val path = r.str("game").takeIf { it.isNotEmpty() } ?: continue
                put(path, Entry(r.str("title_id"), r.str("name"), r.str("state"), r["has_companion"]?.jsonPrimitive?.booleanOrNull == true))
            }
        }
    }.getOrDefault(emptyMap())

    /** Peanuts' answer to the command sent with [token]: whether it was taken, and why not. Null while it is pending. */
    internal fun parseAnswer(text: String, token: String): Pair<Boolean, String>? = runCatching {
        val r = Json.parseToJsonElement(text).jsonObject["last_result"] as? JsonObject ?: return null
        if (r.str("id") != token || r["pending"]?.jsonPrimitive?.booleanOrNull == true) return null
        (r["ok"]?.jsonPrimitive?.booleanOrNull == true) to r.str("message")
    }.getOrNull()

    internal fun parseBuild(text: String): Pair<String, String>? = runCatching {
        val b = Json.parseToJsonElement(text).jsonObject["build"] as? JsonObject ?: return null
        if (b["running"]?.jsonPrimitive?.booleanOrNull != true) return null
        b.str("name") to "${b.str("step").replaceFirstChar { it.uppercase() }} · ${b["percent"]?.jsonPrimitive?.intOrNull ?: 0} %"
    }.getOrNull()
}
