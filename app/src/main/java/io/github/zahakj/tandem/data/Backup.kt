package io.github.zahakj.tandem.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/*
 * Versioned backups: snapshots of Tandem's own files in ROMs/tandem/backup/<yyyy-MM-dd_HH-mm-ss>/, each with a
 * manifest.json saying what is inside. Everything stays on the device's own storage.
 *
 * The files are grouped in four categories, chosen separately for backing up and for restoring. settings.json is
 * shared: its look fields belong to Look, its hidden folders to Game data, and a snapshot only holds the fields of
 * the categories it was made with. Scraping and RetroAchievements keys are left out unless asked for; restoring a
 * snapshot without them keeps the keys already on the device. Private mode is never turned off by a restore.
 *
 * [BackupFiles] is plain file work (tested in BackupTest); [Backups] connects it to the stores.
 */

enum class BackupCategory(val id: String, val label: String, val blurb: String) {
    Games("games", "Game data", "Favourites, details, play time, sessions, journal and hidden folders"),
    Look("look", "Look", "Theme, colours, wallpapers, sounds and motion"),
    Layout("layout", "Layout & folders", "Icon grid, folders, dock and widgets"),
    Systems("systems", "Systems & emulators", "Emulator choices, system folders, controllers and scraping"),
    ;

    companion object {
        fun of(id: String): BackupCategory? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
data class BackupCounts(val games: Int = 0, val tiles: Int = 0, val folders: Int = 0, val widgets: Int = 0, val sessions: Int = 0)

@Serializable
data class BackupManifest(
    val format: Int = 1,
    /** When it was made, ms. */
    val created: Long = 0,
    /** Made by Tandem before a big change (restore, Cocoon import, Quick look, reset), not by hand. */
    val automatic: Boolean = false,
    /** What it was made before ("Quick look"), for automatic ones. */
    val reason: String? = null,
    /** Tandem's version name. */
    val app: String = "",
    /** [BackupCategory.id]s it holds. */
    val categories: List<String> = emptyList(),
    val counts: BackupCounts = BackupCounts(),
    val files: List<String> = emptyList(),
    /** Whether account keys and logins were included. */
    val keys: Boolean = false,
) {
    val holds: Set<BackupCategory> get() = categories.mapNotNullTo(LinkedHashSet()) { BackupCategory.of(it) }
}

/** A snapshot on disk. [legacy] is the old single-slot backup (library.json and settings.json at the top). */
class BackupSnapshot(val dir: File, val manifest: BackupManifest, val legacy: Boolean = false) {
    val id: String get() = if (legacy) "" else dir.name
}

/** How restoring one category went. */
sealed interface RestoreOutcome {
    data object Restored : RestoreOutcome
    /** The snapshot holds nothing for it. */
    data object NotInBackup : RestoreOutcome
    data class Failed(val reason: String) : RestoreOutcome
}

class RestoreResult(val outcomes: Map<BackupCategory, RestoreOutcome>) {
    val restored: List<BackupCategory> get() = outcomes.filterValues { it == RestoreOutcome.Restored }.keys.toList()
    val failed: Map<BackupCategory, String> get() = outcomes.mapNotNull { (c, o) -> (o as? RestoreOutcome.Failed)?.let { c to it.reason } }.toMap()
    val missing: List<BackupCategory> get() = outcomes.filterValues { it == RestoreOutcome.NotInBackup }.keys.toList()

    /** One line for a toast. */
    fun message(): String {
        val parts = ArrayList<String>()
        if (restored.isNotEmpty()) parts += "Restored " + restored.joinToString(", ") { it.label }
        failed.forEach { (c, why) -> parts += "${c.label} failed: $why" }
        if (missing.isNotEmpty()) parts += missing.joinToString(", ") { it.label } + " not in this backup"
        return parts.joinToString(" · ").ifEmpty { "Nothing to restore" }
    }
}

/** The files, the categories they belong to, and the snapshot folder work. No Android here. */
object BackupFiles {
    const val MANIFEST = "manifest.json"
    const val KEEP_AUTOMATIC = 5
    /** Written by the app-preferences store when it has anything to keep. */
    const val APP_PREFS = "app-prefs.json"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = false }
    private val nameFormat get() = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    /** A file (or, with [fields], some top-level fields of it) that belongs to a category. */
    class Part(val file: String, val fields: Set<String>? = null)

    private val lookFields = setOf("look", "reduceMotion", "bottomMode", "showTitles", "sort")
    private val gameFields = setOf("hiddenFolders")

    val parts: Map<BackupCategory, List<Part>> = mapOf(
        BackupCategory.Games to listOf(
            Part("library.json"), Part("sessions.json"), Part("journal.json"), Part("achievements.json"), Part("settings.json", gameFields),
        ),
        BackupCategory.Look to listOf(Part("settings.json", lookFields), Part("extras.json")),
        BackupCategory.Layout to listOf(Part("icon-grid.json"), Part("home.json"), Part("widgets.json")),
        BackupCategory.Systems to listOf(Part("systems.json"), Part("controllers.json"), Part("scraping.json"), Part(APP_PREFS)),
    )

    /** Every file a snapshot can hold. */
    val allFiles: List<String> = parts.values.flatten().map { it.file }.distinct()

    /** Account keys and logins: left out unless asked for, and kept from the device when a snapshot has none. */
    val secrets: Map<String, Set<String>> = mapOf(
        "scraping.json" to setOf("ssUser", "ssPassword", "ssDevId", "ssDevPassword", "sgdbKey", "igdbClientId", "igdbSecret", "ssVerifiedUser"),
        "achievements.json" to setOf("key"),
    )

    /** The categories that use [file]. */
    fun categoriesOf(file: String): Set<BackupCategory> = parts.filterValues { list -> list.any { it.file == file } }.keys

    /**
     * Writes a snapshot of [categories] under [root]. [sources] give each file's current text (null: there is none),
     * and are only asked for the files the categories need. Written into a hidden folder and renamed when complete,
     * so a half-written snapshot never shows up.
     */
    fun write(
        root: File,
        sources: Map<String, () -> String?>,
        categories: Set<BackupCategory>,
        automatic: Boolean,
        reason: String? = null,
        app: String = "",
        withKeys: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): BackupSnapshot {
        require(categories.isNotEmpty()) { "Nothing chosen" }
        root.mkdirs()
        val name = freeName(root, nameFormat.format(Date(now)))
        val temp = File(root, ".$name.partial")
        temp.deleteRecursively()
        if (!temp.mkdirs()) error("Couldn't write to ${root.name}")
        try {
            val texts = LinkedHashMap<String, String>()
            for (file in allFiles) {
                val uses = categoriesOf(file).filter { it in categories }
                if (uses.isEmpty()) continue
                val raw = sources[file]?.invoke() ?: continue
                texts[file] = trim(file, raw, categories, withKeys)
            }
            texts.forEach { (file, text) -> atomicWrite(File(temp, file), text) }
            val manifest = BackupManifest(
                created = now, automatic = automatic, reason = reason, app = app,
                categories = BackupCategory.entries.filter { it in categories }.map { it.id },
                counts = counts(texts), files = texts.keys.toList(), keys = withKeys,
            )
            atomicWrite(File(temp, MANIFEST), json.encodeToString(manifest))
            val dir = File(root, name)
            if (!temp.renameTo(dir)) error("Couldn't finish the backup")
            return BackupSnapshot(dir, manifest)
        } catch (e: Exception) {
            temp.deleteRecursively()
            throw e
        }
    }

    /** "2026-10-24_14-03-00", or with "-2", "-3"... when two snapshots fall in the same second. */
    private fun freeName(root: File, base: String): String {
        var name = base
        var n = 2
        while (File(root, name).exists()) name = "$base-${n++}"
        return name
    }

    /** What of [raw] goes into a snapshot of [categories]: shared files lose other categories' fields, keys go unless asked. */
    private fun trim(file: String, raw: String, categories: Set<BackupCategory>, withKeys: Boolean): String {
        val drop = HashSet<String>()
        for ((cat, list) in parts) {
            if (cat in categories) continue
            list.filter { it.file == file }.forEach { p -> p.fields?.let(drop::addAll) }
        }
        if (!withKeys) secrets[file]?.let(drop::addAll)
        if (drop.isEmpty()) return raw
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return raw
        return json.encodeToString(JsonObject(obj.filterKeys { it !in drop }))
    }

    /** Counts for the manifest, read from the files' JSON. */
    fun counts(texts: Map<String, String>): BackupCounts {
        fun el(name: String): JsonElement? = texts[name]?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
        val games = (el("library.json") as? JsonObject)?.size ?: 0
        val sessions = (el("sessions.json") as? JsonArray)?.size ?: 0
        val grid = el("icon-grid.json") as? JsonObject
        val folders = (grid?.get("folders") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun strings(e: JsonElement?) = (e as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
        val tiles = (strings(grid?.get("order")) + strings(grid?.get("added")) + folders.flatMap { strings(it["items"]) })
            .filter { !it.startsWith("folder:") }.distinct().size
        val widgets = el("widgets.json")?.let { w -> (w as? JsonObject)?.get("widgets")?.let { (it as? JsonArray)?.size } ?: DEFAULT_WIDGETS }
            ?: 0
        return BackupCounts(games, tiles, folders.size, widgets, sessions)
    }

    /** WidgetStore.DEFAULT_WIDGETS.size: a widgets.json without a list shows the defaults. */
    private const val DEFAULT_WIDGETS = 3

    /** Snapshots under [root], newest first, with the old single-slot backup (if any) last. */
    fun list(root: File): List<BackupSnapshot> {
        val found = root.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .mapNotNull { d ->
                val m = runCatching { json.decodeFromString<BackupManifest>(File(d, MANIFEST).readText()) }.getOrNull() ?: return@mapNotNull null
                BackupSnapshot(d, m)
            }
            .sortedWith(compareByDescending<BackupSnapshot> { it.manifest.created }.thenByDescending { it.dir.name })
        return found + listOfNotNull(legacy(root))
    }

    /** The old Back up Tandem slot: library.json and settings.json straight in [root]. */
    private fun legacy(root: File): BackupSnapshot? {
        val lib = File(root, "library.json").takeIf { it.isFile } ?: return null
        val texts = mapOf("library.json" to runCatching { lib.readText() }.getOrDefault(""))
        val files = listOf("library.json", "settings.json").filter { File(root, it).isFile }
        val cats = if ("settings.json" in files) listOf(BackupCategory.Games.id, BackupCategory.Look.id) else listOf(BackupCategory.Games.id)
        return BackupSnapshot(root, BackupManifest(created = lib.lastModified(), categories = cats, counts = counts(texts), files = files), legacy = true)
    }

    /** Deletes old automatic snapshots beyond [keep]; manual ones always stay. Returns how many went. */
    fun prune(root: File, keep: Int = KEEP_AUTOMATIC): Int {
        val old = list(root).filter { it.manifest.automatic && !it.legacy }.drop(keep)
        old.forEach { delete(it) }
        return old.size
    }

    fun delete(s: BackupSnapshot): Boolean =
        if (s.legacy) listOf("library.json", "settings.json").map { File(s.dir, it).delete() }.any { it }
        else s.dir.deleteRecursively()

    /**
     * Restores [categories] from [s] into [filesDir]. Each category stands alone: its files are read and checked
     * first ([check] throws for a file its store can't read), then written, then [reload] makes its stores read
     * them again. A failure in one category leaves the others to go ahead.
     */
    fun restore(
        s: BackupSnapshot,
        filesDir: File,
        categories: Set<BackupCategory>,
        check: (file: String, text: String) -> Unit = { _, _ -> },
        reload: (BackupCategory) -> Unit = {},
    ): RestoreResult {
        val out = LinkedHashMap<BackupCategory, RestoreOutcome>()
        for (cat in BackupCategory.entries.filter { it in categories }) {
            out[cat] = runCatching {
                val present = parts.getValue(cat).filter { File(s.dir, it.file).isFile }
                if (present.isEmpty()) return@runCatching RestoreOutcome.NotInBackup
                val texts = present.map { p ->
                    val text = File(s.dir, p.file).readText()
                    val merged = merge(p, text, File(filesDir, p.file))
                    runCatching { check(p.file, merged) }.getOrElse { throw IllegalStateException("couldn't read ${p.file}") }
                    p.file to merged
                }
                filesDir.mkdirs()
                texts.forEach { (file, text) -> atomicWrite(File(filesDir, file), text) }
                reload(cat)
                RestoreOutcome.Restored
            }.getOrElse { RestoreOutcome.Failed(it.message?.takeIf { m -> m.isNotBlank() } ?: "couldn't restore") }
        }
        return RestoreResult(out)
    }

    /** The text to put in place for [p]: the snapshot's, with the device's keys kept and private mode left on. */
    private fun merge(p: Part, text: String, current: File): String {
        val backup = runCatching { json.parseToJsonElement(text) }.getOrElse { throw IllegalStateException("couldn't read ${p.file}") }
        val keys = secrets[p.file]
        val guard = p.file == "home.json"
        if (p.fields == null && keys == null && !guard) return text
        val now = runCatching { json.parseToJsonElement(current.readText()).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
        val from = backup as? JsonObject ?: throw IllegalStateException("couldn't read ${p.file}")
        val result = LinkedHashMap<String, JsonElement>()
        if (p.fields != null) {
            // Only these fields come from the snapshot; a field it doesn't have was at its default then.
            result.putAll(now.filterKeys { it !in p.fields })
            p.fields.forEach { f -> from[f]?.let { result[f] = it } }
        } else {
            result.putAll(from)
        }
        keys?.forEach { k -> if (k !in from) now[k]?.let { result[k] = it } }
        if (guard && (now["privateMode"] as? JsonPrimitive)?.booleanOrNull == true) result["privateMode"] = JsonPrimitive(true)
        return json.encodeToString(JsonObject(result))
    }

    fun atomicWrite(file: File, text: String) {
        val tmp = File(file.path + ".bak-tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            error("Couldn't write ${file.name}")
        }
    }

    // ---- Words ----

    /** "1,204 games · 38 tiles · 6 folders · 2 days ago". Zero counts are left out. */
    fun summary(m: BackupManifest, now: Long = System.currentTimeMillis()): String {
        val c = m.counts
        return listOfNotNull(
            count(c.games, "game"), count(c.tiles, "tile"), count(c.folders, "folder"), count(c.widgets, "widget"), ago(m.created, now),
        ).joinToString(" · ")
    }

    private fun count(n: Int, word: String): String? = if (n <= 0) null else "%,d %s".format(Locale.US, n, if (n == 1) word else word + "s")

    /** "just now", "5 minutes ago", "3 hours ago", "yesterday", "2 days ago", then the date. */
    fun ago(then: Long, now: Long): String {
        val min = (now - then).coerceAtLeast(0) / 60_000
        return when {
            min < 1 -> "just now"
            min < 60 -> if (min == 1L) "a minute ago" else "$min minutes ago"
            min < 24 * 60 -> (min / 60).let { if (it == 1L) "an hour ago" else "$it hours ago" }
            min < 2 * 24 * 60 -> "yesterday"
            min < 30 * 24 * 60 -> "${min / (24 * 60)} days ago"
            else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(then))
        }
    }

    /** "24 Oct 2026, 14:03". */
    fun date(m: BackupManifest): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(m.created))
}

/**
 * Backups for the app: the stores' current state as files, the snapshot folder, automatic snapshots before big
 * changes, and reloading the stores after a restore. Nothing here logs game titles.
 */
object Backups {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "tandem-backup")
    }

    val root: File get() = File(io.github.zahakj.tandem.Library.romsDir, "tandem/backup")

    /**
     * The stores' state right now, encoded later (on a background thread): the values are taken here, so a change
     * made right after this call isn't in the snapshot.
     */
    fun capture(ctx: Context): Map<String, () -> String?> {
        initAll(ctx)
        val meta = Store.meta
        val settings = Store.settings
        val home = HomeStore.settings
        val sessions = HomeStore.sessions
        val ra = HomeStore.ra
        val widgets = WidgetStore.config
        val grid = IconGridStore.config
        val systems = SystemPrefs.data
        val controllers = ControllerStore.settings
        val extras = ExtrasStore.settings
        val notes = ExtrasStore.notes
        val scrape = ScrapeConfig.settings
        val appPrefs = AppPrefs.data
        return mapOf(
            "library.json" to { json.encodeToString(meta) },
            "settings.json" to { json.encodeToString(settings) },
            "home.json" to { json.encodeToString(home) },
            "sessions.json" to { json.encodeToString(sessions) },
            "achievements.json" to { json.encodeToString(ra) },
            "widgets.json" to { json.encodeToString(widgets) },
            "icon-grid.json" to { json.encodeToString(grid) },
            "systems.json" to { json.encodeToString(systems) },
            "controllers.json" to { json.encodeToString(controllers) },
            "extras.json" to { json.encodeToString(extras) },
            "journal.json" to { json.encodeToString(notes) },
            "scraping.json" to { json.encodeToString(scrape) },
            BackupFiles.APP_PREFS to { AppPrefs.encode(appPrefs) },
        )
    }

    private fun initAll(ctx: Context) {
        Store.init(ctx); HomeStore.init(ctx); WidgetStore.init(ctx); IconGridStore.init(ctx); SystemPrefs.init(ctx)
        ControllerStore.init(ctx); ExtrasStore.init(ctx); ScrapeConfig.init(ctx); AppPrefs.init(ctx)
    }

    fun appVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()

    /** A manual snapshot; blocking, so call it off the main thread with a [capture] taken before. */
    fun backUp(ctx: Context, sources: Map<String, () -> String?>, categories: Set<BackupCategory>, withKeys: Boolean): BackupSnapshot =
        BackupFiles.write(root, sources, categories, automatic = false, app = appVersion(ctx), withKeys = withKeys)

    /** Blocking automatic snapshot of everything, then old automatic ones are pruned. Null when it couldn't be written. */
    fun autoNow(ctx: Context, sources: Map<String, () -> String?>, reason: String): BackupSnapshot? = runCatching {
        BackupFiles.write(root, sources, BackupCategory.entries.toSet(), automatic = true, reason = reason, app = appVersion(ctx))
            .also { BackupFiles.prune(root) }
    }.onFailure { android.util.Log.w("Tandem", "Automatic backup failed: ${it.javaClass.simpleName}") }.getOrNull()

    /** An automatic snapshot before a big change ("Quick look"): the state is taken now, the files written in the background. */
    fun auto(ctx: Context, reason: String) {
        val app = ctx.applicationContext
        val sources = runCatching { capture(app) }.getOrNull() ?: return
        writer.execute { autoNow(app, sources, reason) }
    }

    /** Blocking: the snapshots, newest first. */
    fun list(): List<BackupSnapshot> = runCatching { BackupFiles.list(root) }.getOrDefault(emptyList())

    /** Blocking: puts [categories] of [s] back, each on its own, and has the stores read their files again. */
    fun restore(ctx: Context, s: BackupSnapshot, categories: Set<BackupCategory>): RestoreResult {
        initAll(ctx)
        return BackupFiles.restore(s, ctx.applicationContext.filesDir, categories, ::check) { cat ->
            when (cat) {
                BackupCategory.Games -> { Store.reload(); HomeStore.reload(); ExtrasStore.reload() }
                BackupCategory.Look -> { Store.reload(); ExtrasStore.reload() }
                BackupCategory.Layout -> { IconGridStore.reload(); HomeStore.reload(); WidgetStore.reload() }
                BackupCategory.Systems -> { SystemPrefs.reload(); AppPrefs.reload(); ControllerStore.reload(); ScrapeConfig.reload() }
            }
        }
    }

    /** Throws when a store couldn't read [text] (it would quietly start over with defaults). */
    private fun check(file: String, text: String) {
        when (file) {
            "library.json" -> json.decodeFromString<Map<String, GameMeta>>(text)
            "settings.json" -> json.decodeFromString<Settings>(text)
            "home.json" -> json.decodeFromString<HomeSettings>(text)
            "sessions.json" -> json.decodeFromString<List<PlaySession>>(text)
            "achievements.json" -> json.decodeFromString<RaAccount>(text)
            "widgets.json" -> json.decodeFromString<WidgetConfig>(text)
            "icon-grid.json" -> GridJson.decode(text)
            "systems.json" -> json.decodeFromString<SystemsData>(text)
            "controllers.json" -> json.decodeFromString<ControllerSettings>(text)
            "extras.json" -> json.decodeFromString<ExtrasSettings>(text)
            "journal.json" -> json.decodeFromString<JournalNotes>(text)
            "scraping.json" -> Json { ignoreUnknownKeys = true; coerceInputValues = true }.decodeFromString<ScrapeSettings>(text)
            BackupFiles.APP_PREFS -> json.decodeFromString<AppsData>(text)
            else -> json.parseToJsonElement(text)
        }
    }
}
