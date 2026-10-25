package io.github.zahakj.tandem

import android.content.Context
import android.util.Log
import io.github.zahakj.tandem.data.ScanCache
import io.github.zahakj.tandem.data.SystemPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A game system and how to start its emulator. Loaded from assets/platforms.json (or the user's copy). */
data class Platform(
    val id: String,
    val name: String,
    /** A few letters for tiles, e.g. "3DS". */
    val shortName: String,
    /** Folder names under ROMs, or absolute paths the user added in Systems. */
    val folders: List<String>,
    val extensions: Set<String>,
    val skipDirs: Set<String>,
    val artIds: List<String>,
    /** Every emulator that can run this system, installed or not, in platforms.json order. */
    val allPlayers: List<LaunchSpec>,
    /** Other names for this system, e.g. Cocoon's "vita" for "psvita". Lowercase. */
    val aliases: Set<String> = emptySet(),
    /** For systems imported from Cocoon/Daijisho files: which file names are games, when it isn't just extensions. */
    val accept: Regex? = null,
    /** Games that aren't files in a folder (Android games); null means scan [folders]. */
    val source: ((Platform) -> List<Game>)? = null,
    /** Art to use when a game has none of its own (Android games use their app icon). */
    val fallbackArt: ((Game) -> File?)? = null,
) {
    /** True for the Android Games system, whose games are installed apps. */
    val isApps: Boolean get() = id == AndroidApps.PLATFORM_ID

    /** Whether a file called [name] is a game for this system. */
    fun matches(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty() && ext in extensions) return true
        // Catch-all patterns from other frontends would otherwise pick up their own lists and notes.
        return ext !in NOT_GAMES && accept?.matches(name) == true
    }

    private companion object {
        val NOT_GAMES = setOf("xml", "txt", "nfo", "db", "ini", "cfg", "log", "md")
    }

    /**
     * Emulators that are installed right now, the system's chosen default first. Uninstalled ones are left out,
     * so menus only ever offer something that can actually start.
     */
    val players: List<LaunchSpec>
        get() {
            if (isApps) return allPlayers
            val installed = allPlayers.filter { it.component == LaunchSpec.ANY_APP || Emulators.isInstalled(it.packageName) }
            val preferred = SystemPrefs.config(id).player
            return installed.sortedBy { if (it.name == preferred) 0 else 1 }
        }

    /** The emulator for [game]: its own choice if that is installed (and takes this file), else the system default. */
    fun playerFor(game: Game): LaunchSpec? {
        val all = players
        val ps = all.filter { it.accepts(game.file.name) }.ifEmpty { all }
        return ps.firstOrNull { it.name == game.meta.emulator } ?: ps.firstOrNull()
    }
}

/** A typed intent extra from platforms.json. [values] hold templates like "{file.id}". */
data class Extra(val type: String, val values: List<String>)

/** Changes to a [LaunchSpec] for one kind of file: by extension, or "dir" for games that are folders. */
data class SpecPatch(val action: String?, val game: String?, val data: String?, val extras: Map<String, Extra>)

/**
 * How to hand a game to an emulator. See the comment at the top of platforms.json for every field.
 * [game] is "data", "data-file", "extra-uri:<key>", "extra-path:<key>" or "none".
 */
data class LaunchSpec(
    val name: String,
    /** "package/activity", or [ANY_APP] to open the file with whatever app handles it. */
    val component: String,
    val action: String?,
    val game: String,
    val extras: Map<String, Extra>,
    /** A template for the intent data, e.g. "gamenative://run?appid={file.id}". */
    val data: String? = null,
    /** A template for the MIME type, e.g. "{file.mime}". */
    val type: String? = null,
    val categories: List<String> = emptyList(),
    /** Extra Intent.FLAG_ACTIVITY_* flags, from imported "am start" lines. */
    val flags: Int = 0,
    val byExtension: Map<String, SpecPatch> = emptyMap(),
    /** Where to get the emulator: a web page (GitHub releases work with Obtainium) or null for the Play Store. */
    val store: String? = null,
    /** Only for files whose name matches (imported players); null takes every file of the system. */
    val accept: Regex? = null,
    /** Where this player came from when it isn't built in, e.g. "Cocoon". */
    val origin: String? = null,
) {
    val packageName: String get() = component.substringBefore('/')

    fun accepts(fileName: String): Boolean = accept?.matches(fileName) ?: true

    /** This spec with the changes for [file] applied (a folder game, or a particular extension). */
    fun forFile(file: File): LaunchSpec {
        val patch = (if (file.isDirectory) byExtension["dir"] else null) ?: byExtension[file.extension.lowercase()] ?: return this
        return copy(
            action = patch.action ?: action,
            game = patch.game ?: game,
            data = patch.data ?: data,
            extras = extras + patch.extras,
        )
    }

    companion object {
        const val ANY_APP = "*"
    }
}

object Platforms {
    private const val TAG = "Tandem"
    const val RETROARCH = "com.retroarch.aarch64"
    const val RETROARCH_ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture"

    private var builtIn: Pair<Long, List<Platform>>? = null

    /** Enabled systems, ready to scan. Call off the main thread. */
    fun load(ctx: Context): List<Platform> = all(ctx).filter { SystemPrefs.config(it.id).enabled }

    /**
     * Every known system, enabled or not: Tandem's own list (or the user's copy), plus systems and emulators from
     * Cocoon/Daijisho platform files in ROMs/platforms, plus Android Games, with the user's extra folders added.
     * Call off the main thread.
     */
    fun all(ctx: Context): List<Platform> {
        SystemPrefs.init(ctx)
        ScanCache.init(ctx)
        Emulators.refresh(ctx)
        // A launch shim is a helper app with no icon of its own that only starts an emulator. Installed packages
        // without a launcher entry are treated as one, and imported players that go through them are skipped.
        val shims = HashMap<String, Boolean>()
        val isShim = { pkg: String ->
            shims.getOrPut(pkg) { Emulators.isInstalled(pkg) && !AndroidApps.isLaunchable(ctx, pkg) }
        }
        val merged = CocoonPlatforms.merge(builtIn(ctx), CocoonPlatforms.read(), isShim)
        val emulators = merged.flatMap { p -> p.allPlayers.map { it.packageName } }.toSet()
        return (merged + AndroidApps.platform(ctx, emulators)).distinctBy { it.id }.map { p ->
            val extra = SystemPrefs.config(p.id).folders
            if (extra.isEmpty()) p else p.copy(folders = (p.folders + extra).distinct())
        }
    }

    /** platforms.json, parsed once (again only when the user's copy changes). */
    private fun builtIn(ctx: Context): List<Platform> {
        // The user's own copy wins, so systems and emulators can be changed without rebuilding the app.
        val custom = File(Library.romsDir, "tandem/platforms.json")
        val stamp = custom.lastModified()
        builtIn?.let { (s, list) -> if (s == stamp) return list }
        val asset = { ctx.assets.open("platforms.json").bufferedReader().use { it.readText() } }
        val text = runCatching { if (custom.isFile) custom.readText() else null }.getOrNull() ?: asset()
        val list = runCatching { JSONObject(text).getJSONArray("platforms") }.getOrElse {
            // A typo in the user's copy shouldn't leave them with nothing; fall back to the built-in list.
            Log.w(TAG, "Couldn't read ${custom.path}, using the built-in systems", it)
            JSONObject(asset()).getJSONArray("platforms")
        }
        val parsed = (0 until list.length()).mapNotNull { i ->
            runCatching { parse(list.getJSONObject(i)) }.onFailure { Log.w(TAG, "Skipping system #$i", it) }.getOrNull()
        }
        builtIn = stamp to parsed
        return parsed
    }

    private fun parse(o: JSONObject): Platform {
        // "players" is a list; a single "launch" object is accepted too.
        val specs = o.optJSONArray("players")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            ?: listOfNotNull(o.optJSONObject("launch"))
        val id = o.getString("id")
        return Platform(
            id = id,
            name = o.getString("name"),
            shortName = o.optString("short", o.getString("name")),
            folders = o.optJSONArray("folders").strings().ifEmpty { listOf(id) },
            extensions = o.optJSONArray("extensions").strings().map { it.lowercase() }.toSet(),
            skipDirs = o.optJSONArray("skipDirs").strings().map { it.lowercase() }.toSet(),
            artIds = o.optJSONArray("art").strings().ifEmpty { listOf(id) },
            allPlayers = specs.mapNotNull { s -> runCatching { parseSpec(s) }.onFailure { Log.w(TAG, "Skipping a player of $id", it) }.getOrNull() },
            aliases = o.optJSONArray("aliases").strings().map { it.lowercase() }.toSet(),
        )
    }

    /** The RetroArch player for [core] (e.g. "mgba"), in RetroArch build [pkg]. */
    fun retroArch(name: String, core: String, pkg: String = RETROARCH, extra: Map<String, Extra> = emptyMap()) = LaunchSpec(
        name = name,
        component = "$pkg/$RETROARCH_ACTIVITY",
        action = "android.intent.action.MAIN",
        game = "extra-path:ROM",
        extras = mapOf(
            "LIBRETRO" to "/data/data/{package}/cores/${core}_libretro_android.so",
            "CONFIGFILE" to "{storage}/Android/data/{package}/files/retroarch.cfg",
            "DATADIR" to "/data/data/{package}",
            "SDCARD" to "{storage}",
            "EXTERNAL" to "{storage}/Android/data/{package}/files",
        ).mapValues { Extra("string", listOf(it.value)) } + extra,
        store = "https://www.retroarch.com/?page=platforms",
    )

    private fun parseSpec(l: JSONObject): LaunchSpec {
        // {"core": "mgba"} is shorthand for RetroArch with that core.
        val core = l.optString("core").takeIf { it.isNotEmpty() }
        if (core != null) {
            return retroArch(l.optString("name", "RetroArch · $core"), core, l.optString("package", RETROARCH), extras(l.optJSONObject("extras")))
                .let { s -> l.optString("store").takeIf { it.isNotEmpty() }?.let { s.copy(store = it) } ?: s }
        }
        val component = l.getString("component")
        val patches = l.optJSONObject("byExtension")
        return LaunchSpec(
            name = l.optString("name", component.substringBefore('/')),
            component = component,
            action = if (l.has("action")) l.optString("action").ifEmpty { null } else "android.intent.action.VIEW",
            game = l.optString("game", "data"),
            extras = extras(l.optJSONObject("extras")),
            data = l.optString("data").takeIf { it.isNotEmpty() },
            type = l.optString("type").takeIf { it.isNotEmpty() },
            categories = l.optJSONArray("categories").strings(),
            byExtension = patches?.keys()?.asSequence()?.associate { key ->
                val p = patches.getJSONObject(key)
                key.lowercase() to SpecPatch(
                    action = p.optString("action").takeIf { it.isNotEmpty() },
                    game = p.optString("game").takeIf { it.isNotEmpty() },
                    data = p.optString("data").takeIf { it.isNotEmpty() },
                    extras = extras(p.optJSONObject("extras")),
                )
            } ?: emptyMap(),
            store = l.optString("store").takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Extras keep their JSON type: a string, a list of strings (string array), true/false, or a number. A templated
     * number or other type is written as {"int": "{file.id}"}; also "long", "bool", "float", "uri", "string", "strings".
     */
    private fun extras(o: JSONObject?): Map<String, Extra> {
        if (o == null) return emptyMap()
        return o.keys().asSequence().associateWith { key ->
            when (val v = o.get(key)) {
                is JSONArray -> Extra("strings", v.strings())
                is Boolean -> Extra("bool", listOf(v.toString()))
                is Int -> Extra("int", listOf(v.toString()))
                is Long -> Extra("long", listOf(v.toString()))
                is Number -> Extra("float", listOf(v.toString()))
                is JSONObject -> {
                    val type = v.keys().asSequence().first()
                    val inner = v.get(type)
                    Extra(type, if (inner is JSONArray) inner.strings() else listOf(inner.toString()))
                }
                else -> Extra("string", listOf(v.toString()))
            }
        }
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
