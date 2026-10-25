package io.github.zahakj.tandem

import android.content.Intent
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Reads the Cocoon / Daijisho platform files the user already has (ROMs/platforms/<name>.json) and turns their
 * players into Tandem players at runtime, so anything those frontends can launch, Tandem can too.
 *
 * A file looks like {"platform": {"uniqueId", "name", "shortname", "acceptedFilenameRegex"}, "playerList": [{"name",
 * "acceptedFilenameRegex", "amStartArguments": "-n pkg/.Activity -a action -d {file.uri} --es key value ..."}]}.
 * The "am start" line is parsed into an intent description; its templates ({file.uri}, {file.path}, {file.name},
 * {file.mime}, {tags.<name>}) are the same ones Tandem understands.
 *
 * Players for a system Tandem already knows are added to that system (labelled with where they came from);
 * unknown systems become new systems with folders named after the platform's id and short name.
 */
object CocoonPlatforms {
    private const val TAG = "Tandem"
    const val ORIGIN = "Cocoon"

    /** One platform file, parsed. */
    data class Imported(
        val id: String,
        val name: String,
        val shortName: String,
        val players: List<LaunchSpec>,
        val accept: Regex?,
    )

    private val cache = HashMap<String, Pair<Long, Imported?>>()

    /** Folders that may hold platform files. */
    private val dirs get() = listOf(File(Library.romsDir, "platforms"))

    /** Every platform file, parsed; unchanged files are reused from memory. Call off the main thread. */
    fun read(): List<Imported> = synchronized(cache) {
        val files = dirs.flatMap { d -> d.listFiles { f -> f.isFile && f.name.endsWith(".json", ignoreCase = true) }.orEmpty().toList() }
        val seen = files.map { it.path }.toSet()
        cache.keys.retainAll(seen)
        files.sortedBy { it.name.lowercase() }.mapNotNull { f ->
            val stamp = f.lastModified()
            cache[f.path]?.takeIf { it.first == stamp }?.let { return@mapNotNull it.second }
            val parsed = runCatching { parse(JSONObject(f.readText())) }
                .onFailure { Log.w(TAG, "Couldn't read platform file ${f.name}", it) }.getOrNull()
            cache[f.path] = stamp to parsed
            parsed
        }
    }

    /**
     * [builtIn] with the imported players added, plus imported systems Tandem doesn't have. Players whose package
     * [isShim] are left out: launch shims wrap an emulator only to add resume or to close the previous game, which
     * Tandem does itself.
     */
    fun merge(builtIn: List<Platform>, imported: List<Imported>, isShim: (String) -> Boolean = { false }): List<Platform> {
        if (imported.isEmpty()) return builtIn
        val out = builtIn.toMutableList()
        for (found in imported) {
            val imp = found.copy(players = found.players.filterNot { isShim(it.packageName) })
            val keys = setOf(norm(imp.id), norm(imp.shortName))
            val i = out.indexOfFirst { p ->
                (setOf(p.id) + p.aliases + p.folders.filterNot { it.startsWith("/") }).any { norm(it) in keys }
            }
            if (i >= 0) {
                val p = out[i]
                val fresh = imp.players.filter { new -> p.allPlayers.none { it.sameLaunch(new) } }
                    .map { new -> if (p.allPlayers.any { it.name == new.name }) new.copy(name = "${new.name} ($ORIGIN)") else new }
                out[i] = p.copy(allPlayers = p.allPlayers + fresh)
            } else {
                val exts = imp.players.flatMap { extensionsIn(it.accept) }.toSet()
                // When every player lists its extensions, those are enough; otherwise match names with the regexes.
                val needsRegex = imp.players.any { it.accept != null && extensionsIn(it.accept).isEmpty() }
                val regex = if (needsRegex) imp.players.mapNotNull { it.accept?.pattern }.distinct()
                    .joinToString("|") { "(?:$it)" }.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() } else null
                out += Platform(
                    id = imp.id,
                    name = imp.name,
                    shortName = imp.shortName.ifEmpty { imp.name },
                    folders = listOf(imp.id, imp.shortName).filter { it.isNotEmpty() }.distinct(),
                    extensions = exts,
                    skipDirs = emptySet(),
                    artIds = listOf(imp.id, imp.shortName).filter { it.isNotEmpty() }.distinct(),
                    allPlayers = imp.players,
                    accept = regex,
                )
            }
        }
        return out
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun LaunchSpec.sameLaunch(o: LaunchSpec) =
        component == o.component && action == o.action && game == o.game && data == o.data && extras == o.extras

    /** "^(.*)\\.(?:dpt|psvita)$" -> [dpt, psvita]; empty when the pattern isn't a plain extension list. */
    private fun extensionsIn(r: Regex?): List<String> {
        val m = r?.pattern?.let { Regex("""\\\.\(\?:([A-Za-z0-9_|]+)\)\$?""").find(it) } ?: return emptyList()
        return m.groupValues[1].split('|').map { it.lowercase() }.filter { it.isNotEmpty() }
    }

    private fun parse(o: JSONObject): Imported? {
        val p = o.optJSONObject("platform") ?: return null
        val id = p.optString("uniqueId").ifEmpty { return null }.lowercase()
        val list = o.optJSONArray("playerList")
        val players = (0 until (list?.length() ?: 0)).mapNotNull { i ->
            val pl = list!!.getJSONObject(i)
            val name = pl.optString("name").ifEmpty { "Player ${i + 1}" }
            runCatching { parseAmStart(name, pl.optString("amStartArguments")) }
                .onFailure { Log.w(TAG, "Couldn't read player $name of $id", it) }.getOrNull()
                ?.copy(accept = regex(pl.optString("acceptedFilenameRegex")))
        }
        return Imported(
            id = id,
            name = p.optString("name").ifEmpty { id },
            shortName = p.optString("shortname").ifEmpty { id },
            players = players,
            accept = regex(p.optString("acceptedFilenameRegex")),
        )
    }

    private fun regex(s: String): Regex? = s.takeIf { it.isNotBlank() }?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }

    /**
     * Parses an "am start" argument line into a [LaunchSpec]. Supported: -n, -a, -d, -t, -c, -f, -e/--es, --esa,
     * --ez, --ei, --el, --ef, --eu, --esn and the --activity-* flags. Anything else is skipped.
     */
    fun parseAmStart(name: String, line: String): LaunchSpec? {
        val t = tokenize(line)
        var component: String? = null
        var action: String? = null
        var data: String? = null
        var type: String? = null
        var flags = 0
        val categories = mutableListOf<String>()
        val extras = LinkedHashMap<String, Extra>()
        var i = 0
        fun next(): String? = t.getOrNull(++i)
        while (i < t.size) {
            when (val opt = t[i]) {
                "-n" -> component = next()
                "-a" -> action = next()
                "-d" -> data = next()
                "-t" -> type = next()
                "-c" -> next()?.let { categories += it }
                "-f" -> next()?.let { f -> flags = flags or (if (f.startsWith("0x")) f.drop(2).toLong(16).toInt() else f.toIntOrNull() ?: 0) }
                "-e", "--es" -> { val k = next(); val v = next(); if (k != null && v != null) extras[k] = Extra("string", listOf(v)) }
                "--esn" -> next()?.let { extras[it] = Extra("string", listOf("")) }
                "--esa" -> { val k = next(); val v = next(); if (k != null && v != null) extras[k] = Extra("strings", v.split(',')) }
                "--ez" -> { val k = next(); val v = next(); if (k != null && v != null) extras[k] = Extra("bool", listOf((v == "1" || v.equals("true", true)).toString())) }
                "--ei" -> { val k = next(); val v = next(); if (k != null && v != null) extras[k] = Extra("int", listOf(v)) }
                "--el" -> { val k = next(); val v = next(); if (k != null && v != null) extras[k] = Extra("long", listOf(v)) }
                "--ef" -> { val k = next(); val v = next(); if (k != null && v != null) extras[k] = Extra("float", listOf(v)) }
                "--eu" -> { val k = next(); val v = next(); if (k != null && v != null) extras[k] = Extra("uri", listOf(v)) }
                "--activity-clear-task" -> flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TASK
                "--activity-clear-top" -> flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TOP
                "--activity-no-history" -> flags = flags or Intent.FLAG_ACTIVITY_NO_HISTORY
                "--activity-single-top" -> flags = flags or Intent.FLAG_ACTIVITY_SINGLE_TOP
                "--activity-reorder-to-front" -> flags = flags or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                "--activity-new-task" -> flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
                "--activity-multiple-task" -> flags = flags or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                else -> if (!opt.startsWith("-")) Log.i(TAG, "Ignoring '$opt' in $name")
            }
            i++
        }
        val comp = component ?: return null
        // "pkg/.Activity" is short for "pkg/pkg.Activity".
        val pkg = comp.substringBefore('/')
        val full = comp.substringAfter('/', "").let { if (it.startsWith(".")) "$pkg/$pkg$it" else comp }
        // Daijisho lets RetroArch players name just the core ("-e LIBRETRO mgba").
        extras["LIBRETRO"]?.values?.singleOrNull()?.let { core ->
            if (!core.contains('/')) extras["LIBRETRO"] = Extra("string", listOf("/data/data/{package}/cores/${core.removeSuffix("_libretro_android.so")}_libretro_android.so"))
        }
        return LaunchSpec(
            name = name,
            component = full,
            action = action,
            game = "none",
            extras = extras,
            data = data,
            type = type,
            categories = categories,
            flags = flags,
            origin = ORIGIN,
        )
    }

    /** Splits on spaces and new lines; quotes group words and are removed ("-r","{x}" becomes -r,{x}). */
    private fun tokenize(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var inToken = false
        for (c in s) {
            when {
                quote != null -> if (c == quote) quote = null else cur.append(c)
                c == '"' || c == '\'' -> { quote = c; inToken = true }
                c.isWhitespace() -> if (inToken) { out += cur.toString(); cur.clear(); inToken = false }
                else -> { cur.append(c); inToken = true }
            }
        }
        if (inToken) out += cur.toString()
        return out
    }
}
