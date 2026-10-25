package io.github.zahakj.tandem.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.zahakj.tandem.Emulators
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.LaunchSpec
import io.github.zahakj.tandem.MainActivity
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.data.HomeFolder
import io.github.zahakj.tandem.data.HomeStore
import io.github.zahakj.tandem.data.IconGridConfig
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Look
import io.github.zahakj.tandem.data.PackParts
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * "Import my Cocoon setup": brings the home, theme and appearance from your Cocoon backup into Tandem.
 *
 * Reads the newest complete backup in ROMs/backups (one with cocoon_backup_incomplete.marker is skipped):
 * - categories/layout/grid_v1.json: the home grid's placements, filled column by column (col, then row) at Cocoon's
 *   zoom (rows). Smart folders by platform become Tandem's system tiles, Android Games its Android system, Emulators
 *   a folder of the installed emulators, manual folders Tandem folders (with their apps and games), Cocoon's own
 *   "pod" apps Tandem's built-in ones (Silk → Themes, Log → Journal, Picnic → Gallery, Settings → Settings; the
 *   others have no twin and are left out), and apps app tiles. A manual folder that is really one system (Cocoon's
 *   "Nintendo 3DS" folder with All 3DS Games and genre folders inside) becomes that system's tile.
 * - categories/appearance/appearance_prefs.json: the theme pack (colours, both video wallpapers, icons, heroes,
 *   logos, cover frames) and the appearance values Tandem has twins for (icon roundness, highlight thickness, hover
 *   scale, hero logo sizes, hero fade, OLED).
 * The dock isn't in the backup, so the current dock stays (and is shown).
 *
 * Importing again gives the same result. The arrangement, look and dock from before the first import are kept in
 * cocoon-undo.json, so "Undo Cocoon import" puts everything back.
 */

@Serializable
private data class CocoonUndo(
    val grid: IconGridConfig,
    val look: Look,
    val dock: List<String>? = null,
    val showDock: Boolean = true,
    /** The arrangement the import made, so a second import keeps the first "before" instead of saving its own result. */
    val imported: IconGridConfig? = null,
)

object CocoonSetup {
    private const val MARKER = "cocoon_backup_incomplete.marker"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** True while an import is being read. */
    var busy by mutableStateOf(false)
        private set

    /** An import can be undone (cocoon-undo.json exists). */
    var canUndo by mutableStateOf(false)
        private set

    /** Cocoon's built-in apps and their Tandem twins. */
    private val Pods = mapOf("silk" to "tandem:themes", "log" to "tandem:journal", "picnic" to "tandem:gallery", "settings" to "tandem:settings")

    /** Cocoon's folder colours and the nearest of Tandem's folder colours (see folderColor). */
    private val FolderTints = mapOf("stormy" to 0, "slate" to 0, "wisteria" to 1, "mint" to 2, "sunflower" to 6, "coral" to 4, "breeze" to 5)

    private fun undoFile(ctx: Context) = File(ctx.applicationContext.filesDir, "cocoon-undo.json")

    fun init(ctx: Context) { canUndo = undoFile(ctx).isFile }

    /** The newest complete Cocoon backup, or null. */
    fun newestBackup(): File? = File(Library.romsDir, "backups").listFiles().orEmpty()
        .filter { d ->
            d.isDirectory && d.name.startsWith("cocoon_backup_") && !File(d, MARKER).exists() &&
                d.listFiles().orEmpty().none { it.name.endsWith("incomplete.marker") } &&
                (File(d, "categories/layout/grid_v1.json").isFile || File(d, "categories/appearance/appearance_prefs.json").isFile)
        }
        .maxByOrNull { it.name }

    /** "22 Oct 2026" from a backup folder's name (cocoon_backup_2026-10-22_09-01-59). */
    fun backupDate(dir: File): String = dir.name.removePrefix("cocoon_backup_").substringBefore('_')

    /** What an import would do, read from the backup. */
    class Plan(
        val backup: File,
        val order: List<String>,
        val folders: List<HomeFolder>,
        val rows: Int,
        val paged: Boolean,
        val prefs: Map<String, Any?>,
        val skipped: List<String>,
    )

    // ---- Reading the backup (blocking: call off the main thread) ----

    private fun str(o: JSONObject?, key: String): String? = if (o == null || o.isNull(key)) null else o.optString(key).ifBlank { null }

    private class Entry(val type: String, val ref: JSONObject)

    fun plan(ctx: Context): Plan? {
        val dir = newestBackup() ?: return null
        val gridFile = File(dir, "categories/layout/grid_v1.json")
        val g = runCatching { JSONObject(gridFile.readText()) }.getOrNull()
        val prefs = readPrefs(File(dir, "categories/appearance/appearance_prefs.json"))
        if (g == null) return Plan(dir, emptyList(), emptyList(), 3, false, prefs, emptyList())

        val folders = HashMap<Long, JSONObject>()
        g.optJSONArray("folders")?.objects()?.forEach { folders[it.optLong("id")] = it }
        val placements = g.optJSONArray("placements")?.objects().orEmpty()
        val membership = g.optJSONArray("membership")?.objects().orEmpty()
        // A screen's items: its placements in Cocoon's order (column by column), else its membership order.
        fun itemsOf(screen: String): List<Entry> {
            val placed = placements.filter { it.optString("screen") == screen }
                .sortedWith(compareBy({ it.optInt("col") }, { it.optInt("row") }))
            val list = placed.ifEmpty { membership.filter { it.optString("screen") == screen }.sortedBy { it.optInt("sort_order") } }
            return list.mapNotNull { o -> o.optJSONObject("ref")?.let { Entry(o.optString("item_type").ifBlank { it.optString("type") }, it) } }
        }

        val skipped = ArrayList<String>()
        val made = ArrayList<HomeFolder>()
        val emulators by lazy { emulatorApps(ctx) }

        fun game(path: String): String? {
            val sys = path.substringBefore('/', "")
            val file = path.substringAfterLast('/')
            if (sys.isEmpty() || file.isEmpty()) return null
            return "game:${platformForFolder(sys)}/$file"
        }

        // A game or app: its tile key, or null.
        fun item(ref: JSONObject): String? {
            val pkg = str(ref, "package")
            val path = str(ref, "path").orEmpty()
            return when {
                pkg != null -> "app:$pkg"
                path.startsWith("android_app/") -> "app:${path.removePrefix("android_app/")}"
                path.startsWith("pod/") -> Pods[path.removePrefix("pod/").lowercase()] ?: null.also { skipped += path.removePrefix("pod/") }
                path.isNotEmpty() -> game(path)
                else -> null
            }
        }

        // A folder: a tile key (system, collection) or a Tandem folder made from it, or null.
        fun folder(id: Long, depth: Int): Any? {
            val f = folders[id] ?: return null
            val name = f.optString("name").ifBlank { "Folder" }
            val color = FolderTints[f.optString("icon_type").lowercase()] ?: 0
            if (f.optBoolean("is_smart_folder")) {
                val query = str(f, "smart_folder_query")
                return when (str(f, "smart_folder_type")?.uppercase()) {
                    "BY_PLATFORM" -> query?.let { "s:${platformId(it)}" }
                    "ANDROID_GAMES" -> "s:${io.github.zahakj.tandem.AndroidApps.PLATFORM_ID}"
                    "EMULATORS" -> HomeFolder("cocoon-$id", name, emulators, color, art = "emulators")
                    "FAVORITES" -> "col:fav"
                    "MOST_PLAYED" -> "col:most"
                    "RECENTLY_ADDED", "NEWLY_ADDED" -> "col:added"
                    "RECENTLY_PLAYED", "RECENT" -> "col:continue"
                    "UNPLAYED" -> "col:unplayed"
                    "GENRE", "BY_GENRE" -> query?.let { "genre:$it" }
                    else -> null.also { skipped += name }
                }
            }
            if (depth > 4) return null
            val kids = itemsOf("folder_$id")
            // A folder that is really one system (a smart folder of it, plus genre folders of its games): its tile.
            val smartSystems = kids.filter { it.type == "FOLDER" }.mapNotNull { k ->
                folders[k.ref.optLong("backup_id")]?.takeIf { it.optBoolean("is_smart_folder") && str(it, "smart_folder_type") == "BY_PLATFORM" }
            }.mapNotNull { str(it, "smart_folder_query") }.map(::platformId).distinct()
            val hasApps = kids.any { it.type != "FOLDER" && (str(it.ref, "package") != null || str(it.ref, "path")?.startsWith("pod/") == true) }
            if (smartSystems.size == 1 && !hasApps) return "s:${smartSystems[0]}"
            val items = LinkedHashSet<String>()
            for (k in kids) {
                if (k.type == "FOLDER") {
                    when (val sub = folder(k.ref.optLong("backup_id"), depth + 1)) {
                        is String -> items += sub
                        is HomeFolder -> items += sub.items
                    }
                } else item(k.ref)?.let(items::add)
            }
            return HomeFolder("cocoon-$id", name, items.toList(), color)
        }

        val order = LinkedHashSet<String>()
        for (e in itemsOf("home")) {
            if (e.type == "FOLDER") {
                when (val got = folder(e.ref.optLong("backup_id"), 0)) {
                    is String -> order += got
                    is HomeFolder -> { made += got; order += "folder:${got.id}" }
                }
            } else item(e.ref)?.let(order::add)
        }
        val p = g.optJSONObject("prefs")
        val zoom = p?.optInt("home_zoom_level", 3)?.takeIf { it > 0 }
            ?: g.optJSONArray("layouts")?.objects()?.firstOrNull { it.optString("screen") == "home" }?.optInt("zoom", 3) ?: 3
        // A thing shows once on the home grid, even if Cocoon also has it in a folder.
        val inFolders = made.flatMap { it.items }.toSet()
        val home = order.filter { it.startsWith("folder:") || it !in inFolders }
        return Plan(dir, home, made, zoom, p?.optBoolean("page_mode_enabled") == true, prefs, skipped.distinct())
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    /** Cocoon's appearance settings, by key. */
    private fun readPrefs(f: File): Map<String, Any?> {
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return emptyMap()
        val out = HashMap<String, Any?>()
        for (file in o.optJSONArray("preferenceFiles")?.objects().orEmpty()) {
            for (e in file.optJSONArray("entries")?.objects().orEmpty()) {
                val key = e.optString("key")
                if (key.isBlank()) continue
                out[key] = when (e.optString("type").uppercase()) {
                    "FLOAT", "DOUBLE" -> e.optDouble("floatValue", e.optDouble("doubleValue", Double.NaN)).takeIf { !it.isNaN() }?.toFloat()
                    "INT", "INTEGER" -> e.optInt("intValue")
                    "LONG" -> e.optLong("longValue")
                    "BOOLEAN" -> e.optBoolean("booleanValue")
                    else -> str(e, "stringValue")
                }
            }
        }
        return out
    }

    /** Tandem's id for Cocoon's platform name ("gb", "psvita", "n3ds"...). */
    private fun platformId(q: String): String {
        val libs = Libraries.all.orEmpty()
        libs.firstOrNull { it.platform.id.equals(q, true) }?.let { return it.platform.id }
        val n = PackArt.norm(q)
        libs.firstOrNull { lib -> ThemePacks.systemIds(lib.platform.id).any { PackArt.norm(it) == n } }?.let { return it.platform.id }
        return q.lowercase()
    }

    /** The system a ROMs folder belongs to. */
    private fun platformForFolder(sys: String): String =
        Libraries.all.orEmpty().firstOrNull { lib -> lib.platform.folders.any { it.equals(sys, true) } }?.platform?.id ?: platformId(sys)

    /** Installed emulators as app tiles (Cocoon's Emulators smart folder), by name. */
    private fun emulatorApps(ctx: Context): List<String> {
        Emulators.refresh(ctx)
        return Libraries.all.orEmpty().flatMap { it.platform.allPlayers }
            .filter { it.component != LaunchSpec.ANY_APP }
            .map { it.packageName }
            .filter { it.isNotBlank() && Emulators.isInstalled(it) }
            .distinct()
            .filter { Dock.launchable(ctx, it) }
            .sortedBy { RunningGames.appLabel(ctx, it).lowercase() }
            .map { "app:$it" }
    }

    /** The theme pack the backup names, else the most recently installed Cocoon pack. */
    private fun packFor(prefs: Map<String, Any?>): ThemePack? {
        val ids = listOf("custom_theme_colors_id", "custom_theme_wallpapers_id", "custom_theme_sf_icons_id").mapNotNull { prefs[it] as? String }
        for (id in ids) {
            ThemePacks.packs.firstOrNull { it.dir.name == id || it.id == "cocoon:$id" || it.id == "pack:$id" }?.let { return it }
        }
        return ThemePacks.newestCocoonPack()
    }

    // ---- Applying ----

    /**
     * Imports the newest Cocoon backup: layout, dock, theme and appearance. [auto] is the quiet first-start import
     * (it says how to undo it). Safe to call again; the result is the same.
     */
    fun run(ctx: Context, auto: Boolean = false, done: (Boolean) -> Unit = {}) {
        if (busy) return
        busy = true
        MainScope().launch {
            val plan = runCatching {
                withContext(Dispatchers.IO) {
                    ThemePacks.refresh(force = true)
                    plan(ctx)
                }
            }.getOrNull()
            busy = false
            if (plan == null) {
                if (!auto) Toasts.show("I couldn't find a Cocoon backup in ROMs/backups. Make one in Cocoon's settings first")
                done(false)
                return@launch
            }
            io.github.zahakj.tandem.data.Backups.auto(ctx, "Cocoon import")
            apply(ctx, plan)
            if (auto) Toasts.show("Your Cocoon setup is here ✨ Settings › Library › Undo Cocoon import puts it back")
            else Toasts.show(
                "Your Cocoon home and theme are in ✨" + if (plan.skipped.isNotEmpty()) " (no twin for ${plan.skipped.joinToString(", ")})" else "",
                6000, Toasts.Action("Undo") { GridUndoUi.undo() },
            )
            done(true)
        }
    }

    /** Runs the import once by itself, on the first start with a library and a Cocoon backup. */
    fun autoImport(ctx: Context) {
        if (Store.settings.cocoonSetupDone || Libraries.all == null || busy) return
        MainScope().launch {
            val found = withContext(Dispatchers.IO) { newestBackup() != null }
            if (!found) { Store.updateSettings { it.copy(cocoonSetupDone = true) }; return@launch }
            run(ctx, auto = true)
        }
    }

    private fun apply(ctx: Context, plan: Plan) {
        val defaults = GridCatalog(ctx, Libraries.all.orEmpty()).defaults
        val inFolders = plan.folders.flatMap { it.items }.toSet()
        val grid = if (plan.order.isEmpty()) IconGridStore.config else IconGridConfig(
            order = plan.order,
            // Only what the backup's home shows goes on the grid (Y › Add to home brings any of the rest back).
            hidden = defaults.filter { it !in plan.order && it !in inFolders },
            added = (plan.order + inFolders).filter { it !in defaults && !it.startsWith("folder:") }.distinct(),
            folders = plan.folders,
        )
        // Keep what was there before the first import, however often this runs.
        val old = readUndo(ctx)
        val keepOld = old != null && (IconGridStore.config == old.imported || IconGridStore.config == grid)
        writeUndo(
            ctx,
            if (keepOld) old!!.copy(imported = grid)
            else CocoonUndo(IconGridStore.config, Store.settings.look, HomeStore.settings.dock, HomeStore.settings.showDock, grid),
        )
        // One step the grid's Undo can take back (the arrangement; Undo Cocoon import in Settings puts the look back too).
        IconGridStore.undoable("Imported the Cocoon home") { IconGridStore.update { grid } }

        val prefs = plan.prefs
        val pack = packFor(prefs)
        if (pack != null) applyTheme(pack.id)
        else if ((prefs["theme_mode"] as? String).equals("OLED", true)) applyTheme("oled")
        fun f(key: String) = (prefs[key] as? Number)?.toFloat()
        Store.updateSettings { s ->
            val l = s.look
            var look = l.copy(
                homeLayout = "grid", gridScroll = if (plan.paged) "paged" else "horizontal", gridRows = plan.rows.coerceIn(2, 5),
                gridHeroSplit = 0f, gridLabels = false, gridDetails = "spotlight", gridFolders = "popup", xButton = "swap-grid",
                dockStyle = "bar", iconPack = "auto", wallTop = "theme", wallBottom = "theme", wallDim = 0.12f,
                gridRoundness = f("icon_roundness")?.coerceIn(0f, 0.5f) ?: l.gridRoundness,
                // Cocoon's thickness is a scale of its default ring (Tandem's default is 4).
                highlightThickness = f("highlight_thickness_scale")?.let { (4f * it).coerceIn(1f, 8f) } ?: l.highlightThickness,
                focusScale = f("icon_hover_scale")?.coerceIn(1f, 1.3f) ?: l.focusScale,
                heroLogoScale = f("hero_logo_scale")?.coerceIn(0.3f, 1f) ?: l.heroLogoScale,
                heroFolderLogoScale = f("hero_folder_logo_scale")?.coerceIn(0.2f, 1f) ?: l.heroFolderLogoScale,
                heroGradient = when ((prefs["hero_gradient_style"] as? String)?.uppercase()) {
                    "NONE" -> "none"; "STRONG", "DARK" -> "strong"; "VIGNETTE" -> "vignette"; null -> l.heroGradient; else -> "fade"
                },
                swapScreens = (prefs["swap_screens"] as? Boolean) ?: l.swapScreens,
                accent = if (prefs["theme_accent_override_enabled"] == true) null else l.accent,
            )
            // Everything the pack brings, cover frames included (Cocoon uses its icon overlays).
            if (pack != null) look = look.copy(packParts = look.packParts + (pack.id to PackParts(frames = true)))
            s.copy(look = look, cocoonSetupDone = true)
        }
        HomeStore.updateSettings { it.copy(showDock = true) }
        (ctx as? MainActivity)?.applyScreenRoles()
        Nav.focusTick++
    }

    /** Puts back the arrangement, look and dock from before the first import. */
    fun undo(ctx: Context) {
        val u = readUndo(ctx) ?: run { Toasts.show("There's no Cocoon import to undo"); return }
        IconGridStore.update { u.grid }
        Store.updateSettings { it.copy(look = u.look) }
        HomeStore.updateSettings { it.copy(dock = u.dock, showDock = u.showDock) }
        undoFile(ctx).delete()
        canUndo = false
        (ctx as? MainActivity)?.applyScreenRoles()
        Nav.focusTick++
        Toasts.show("Back to how it was before the Cocoon import")
    }

    private fun readUndo(ctx: Context): CocoonUndo? =
        runCatching { json.decodeFromString<CocoonUndo>(undoFile(ctx).readText()) }.getOrNull()

    private fun writeUndo(ctx: Context, u: CocoonUndo) {
        runCatching { undoFile(ctx).writeText(json.encodeToString(u)) }
        canUndo = true
    }
}

/** Settings rows: import the Cocoon setup again, or undo it. */
@Composable
fun cocoonSetupSettings(): SettingSection {
    val ctx = LocalContext.current
    val backup = Facts.run { ensure(ctx); cocoonBackup }
    val rows = buildList {
        add(
            SettingRow(
                "Import my Cocoon setup",
                when { CocoonSetup.busy -> "Reading…"; backup == null -> "No backup found"; else -> "Backup of " + CocoonSetup.backupDate(backup) },
                Palette.Sky, Glyph.Download,
                "Your Cocoon home in Tandem: the same tiles and folders in the same order, Cocoon's rows, and your theme pack with both video wallpapers, icons, heroes and logos. Reads the newest backup in ROMs/backups. You can undo it.",
                keywords = "cocoon backup import layout grid dock theme restore bring over",
                action = "Import",
            ) { CocoonSetup.run(ctx) },
        )
        if (CocoonSetup.canUndo) add(
            SettingRow(
                "Undo Cocoon import", "", Palette.Peach, Glyph.Restore,
                "Puts back the home grid, look and dock you had before importing your Cocoon setup.",
                keywords = "cocoon undo revert", action = "Undo",
            ) { CocoonSetup.undo(ctx) },
        )
    }
    return SettingSection("From Cocoon", rows, Glyph.Download)
}
