package io.github.zahakj.tandem.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.concurrent.Executors

/**
 * A folder the user made on the icon grid. [items] are tile keys ("s:n3ds", "app:<package>", "game:<key>",
 * "folder:<id>"...): shortcuts, so a tile can be in several folders and on the home grid too, and a folder can hold
 * folders (GridTree). [art] names the smart-folder art it wears from the icon pack ("emulators", "android_app"...);
 * without it the folder's name picks the art (see FolderArtNames), and with no pack art at all Tandem draws the folder.
 */
@Serializable
data class HomeFolder(
    val id: String, val name: String, val items: List<String> = emptyList(), val color: Int = 0, val art: String? = null,
    /** Kept out of sight like a hidden game; the systems and games inside are hidden everywhere too. */
    val hidden: Boolean = false,
    /** The columns [items]' slots were placed for, as in [IconGridConfig.lanes]. "" in [items] is an empty slot. */
    val lanes: Int = 0,
    /** How it shows what is inside ([FolderSort]); [items] stays the manual order underneath. */
    val sort: String = FolderSort.MANUAL,
    /** Set for a smart folder, which fills itself with the games the rule matches and ignores [items]; null: a manual folder. */
    val rule: SmartRule? = null,
    /** The screen games opened from this folder start on ([FolderScreens]); null: each game's own choice. */
    val openOn: String? = null,
) {
    val smart: Boolean get() = rule != null
}

/**
 * How the user arranged the icon grid home. Tile keys:
 * `s:<system>`, `col:<collection>`, `genres`, `genre:<name>`, `app:<package>`, `game:<game key>`, `folder:<id>`, `allapps`.
 */
@Serializable
data class IconGridConfig(
    /**
     * Tiles in their slots, in reading order ([GridSlots]); "" is an empty slot. Tiles that aren't listed (a new
     * system) follow in the default order. Saved before slots, it is the packed order: the same layout.
     */
    val order: List<String> = emptyList(),
    /** Tiles taken off the home grid (systems and collections show by default). */
    val hidden: List<String> = emptyList(),
    /** Tiles the user put on the home grid that aren't there by default: apps, games, single genres. */
    val added: List<String> = emptyList(),
    val folders: List<HomeFolder> = emptyList(),
    /** How many lanes (rows of a sideways grid, columns of an up-and-down one) [order] was placed for; 0: packed. */
    val lanes: Int = 0,
    /** Designed looks (Y › Design folder…) by tile key; see [FolderStyle]. Tiles without one follow the theme. */
    val styles: Map<String, FolderStyle> = emptyMap(),
    /** How many cells the widgets on the grid (`widget:<id>`) cover; a widget not listed covers one. */
    val spans: Map<String, GridSpan> = emptyMap(),
    /** 0: from before folder shortcuts (moved over as it is read, GridTree.migrate); [GridTree.SHORTCUTS] since. */
    val version: Int = 0,
)

/** A widget's size on the icon grid: [w] columns by [h] rows. */
@Serializable
data class GridSpan(val w: Int = 1, val h: Int = 1) {
    val label: String get() = "$w×$h"

    companion object {
        val ONE = GridSpan()
        /** Every size a widget can take on the grid, smallest first. */
        val ALL = listOf(GridSpan(1, 1), GridSpan(2, 1), GridSpan(1, 2), GridSpan(2, 2))
    }
}

/**
 * A grid as it shows now: its [slots] (with gaps, and keys that aren't showing), the keys [present] on it, the
 * [lanes] it has and the [ext]ents of its widgets in slot terms. Edits made on screen are saved from this, so they
 * land where they were made.
 */
class GridShown(val slots: List<String>, val present: Set<String>, val lanes: Int, val ext: Map<String, GridSlots.Extent> = emptyMap(), val page: Int = 0) {
    /** The first free slot from [from] for a one-cell tile (never a cell a widget covers). */
    fun firstFree(from: Int): Int = GridSlots.firstFit(slots, lanes, ext, from, GridSlots.ONE, present, page)
}

/**
 * One step Undo can take back: the grid as it was before [label] happened, the widgets on it then (their sizes
 * and options), and the games and systems the step hid, to bring back.
 */
@Serializable
data class GridUndo(
    val label: String,
    val grid: IconGridConfig,
    val widgets: List<Widget> = emptyList(),
    val games: List<String> = emptyList(),
    val systems: List<String> = emptyList(),
)

/** The icon grid's arrangement, as `icon-grid.json` in the app's files folder, kept like [WidgetStore]. */
object IconGridStore {
    private val writer = Executors.newSingleThreadExecutor()
    private var dir: File? = null

    var config by mutableStateOf(IconGridConfig(version = GridTree.SHORTCUTS))
        private set

    fun init(ctx: Context) {
        if (dir != null) return
        val d = ctx.applicationContext.filesDir
        dir = d
        config = runCatching { GridJson.decode(File(d, "icon-grid.json").readText()) }.getOrDefault(IconGridConfig(version = GridTree.SHORTCUTS))
        writer.execute { loadUndo() }
    }

    /** Reads icon-grid.json again (after a restore). Blocking. The undo steps go: they were for the old arrangement. */
    fun reload() {
        val d = dir ?: return
        val c = runCatching { GridJson.decode(File(d, "icon-grid.json").readText()) }.getOrDefault(IconGridConfig(version = GridTree.SHORTCUTS))
        synchronized(this) { config = c }
        clearUndo()
    }

    fun update(change: (IconGridConfig) -> IconGridConfig) {
        val snapshot = synchronized(this) { keepHiddenPlaces(config, GridTree.migrate(change(config))).also { config = it } }
        val d = dir ?: return
        val text = GridJson.encode(snapshot)
        writer.execute {
            val tmp = File(d, "icon-grid.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(d, "icon-grid.json"))
        }
    }

    /**
     * Hidden folders are left out of the grid while tucked away, so an order saved meanwhile (Tidy up, a sort) may
     * not list them; they go back to their old slot when it is still free, else after the last one, instead of being
     * forgotten. Only folders that sit on the home grid.
     */
    private fun keepHiddenPlaces(old: IconGridConfig, new: IconGridConfig): IconGridConfig {
        val lost = new.folders.filter { it.hidden && GridTree.parentOf(new, it.id) == null }.map { "folder:${it.id}" }.filter { it !in new.order && it in old.order }
        if (lost.isEmpty()) return new
        val order = new.order.toMutableList()
        for (k in lost) {
            val at = old.order.indexOf(k)
            when {
                at >= order.size -> { while (order.size < at) order.add(GridSlots.EMPTY); order.add(k) }
                order[at].isEmpty() -> order[at] = k
                else -> order.add(k)
            }
        }
        return new.copy(order = order)
    }

    fun folder(id: String): HomeFolder? = config.folders.firstOrNull { it.id == id }

    /** A folder holding [key], if any (a tile may be in several). */
    fun folderOf(key: String): HomeFolder? = config.folders.firstOrNull { key in it.items }

    /** The folder holding folder [id], or null when it sits on the home grid. */
    fun parentOf(id: String): HomeFolder? = GridTree.parentOf(config, id)

    /** Every key sitting inside some folder. */
    fun inFolders(): Set<String> = config.folders.flatMapTo(HashSet()) { f -> f.items.filter { it.isNotEmpty() } }

    fun newFolderId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) + (100..999).random()

    /** Puts [key] on the home grid (or back on it). Not for folders. */
    fun add(key: String, default: Boolean) = update { c -> GridTree.onToHome(c, key, default) }

    /** Puts [key] on the home grid in slot [at] of the grid as shown. Not for folders. */
    fun addAt(key: String, default: Boolean, shown: GridShown, at: Int) = update { c ->
        GridTree.onToHome(c, key, default).copy(
            order = GridSlots.settle(GridSlots.putAt(shown.slots, key, at), shown.lanes, shown.ext, shown.page),
            lanes = shown.lanes,
        )
    }

    /**
     * Puts [keys] (games, apps: nothing shown by default) on the home grid as [shown]: the first in slot [at] (or the
     * first free slot after it), the rest in the free slots after that, in order. Returns the first key.
     */
    fun addAllAt(keys: List<String>, shown: GridShown, at: Int): String? {
        if (keys.isEmpty()) return null
        update { c ->
            var slots = shown.slots
            var from = at
            val here = shown.present + keys
            for (k in keys) {
                val slot = GridSlots.firstFit(slots, shown.lanes, shown.ext, from, GridSlots.ONE, here, shown.page)
                slots = GridSlots.putAt(slots, k, slot)
                from = slot + 1
            }
            c.copy(hidden = c.hidden - keys.toSet(), added = c.added + keys.filter { it !in c.added }, order = slots, lanes = shown.lanes)
        }
        return keys.first()
    }

    /** Like [addAllAt], into folder [folderId] as [shown]: shortcuts, so they stay wherever else they are. */
    fun addAllToFolder(keys: List<String>, folderId: String, shown: GridShown, at: Int): String? {
        if (keys.isEmpty() || folder(folderId)?.smart == true) return null
        update { c ->
            val f = GridTree.folder(c, folderId) ?: return@update c
            val fresh = keys.filter { it !in f.items }
            if (f.sort != FolderSort.MANUAL) return@update c.copy(folders = c.folders.map { if (it.id == folderId) it.copy(items = GridSlots.trim(it.items) + fresh) else it })
            var slots = shown.slots
            var from = at
            val here = shown.present + fresh
            for (k in fresh) {
                val slot = GridSlots.firstFit(slots, shown.lanes, emptyMap(), from, GridSlots.ONE, here)
                slots = GridSlots.putAt(slots, k, slot)
                from = slot + 1
            }
            c.copy(folders = c.folders.map { if (it.id == folderId) it.copy(items = GridSlots.trim(slots), lanes = shown.lanes) else it })
        }
        return keys.first()
    }

    /**
     * Puts the widget [key] (`widget:<id>`) on the home grid in slot [at] of the grid as shown, covering [span]
     * ([ext] in slot terms). Tiles in the cells it covers move to the first free slots after it.
     */
    fun addWidgetAt(key: String, span: GridSpan, ext: GridSlots.Extent, shown: GridShown, at: Int) = update { c ->
        c.copy(
            added = if (key in c.added) c.added else c.added + key,
            order = GridSlots.settle(GridSlots.putAt(shown.slots, key, at), shown.lanes, shown.ext + (key to ext), shown.page),
            lanes = shown.lanes,
            spans = if (span == GridSpan.ONE) c.spans - key else c.spans + (key to span),
        )
    }

    /** [key]'s new size on the grid, with the grid's slots as they are after the change. */
    fun setSpan(key: String, span: GridSpan, slots: List<String>, lanes: Int) = update { c ->
        c.copy(order = GridSlots.trim(slots), lanes = lanes, spans = if (span == GridSpan.ONE) c.spans - key else c.spans + (key to span))
    }

    /** Takes [key] off the home grid and out of every folder, leaving gaps. Default tiles are remembered as hidden. */
    fun remove(key: String, default: Boolean) = update { c ->
        c.copy(
            hidden = if (default && key !in c.hidden) c.hidden + key else c.hidden,
            added = c.added - key,
            order = GridSlots.clear(c.order, key),
            folders = c.folders.map { f -> if (key in f.items) f.copy(items = GridSlots.clear(f.items, key)) else f },
            spans = c.spans - key,
        )
    }

    /** Takes [key] off the home grid only, leaving a gap; the folders it is in keep it. */
    fun removeFromHome(key: String) = update { c -> GridTree.offHome(c, key) }

    /** Takes [key] out of folder [id] only. */
    fun removeFromFolder(key: String, id: String) = update { c -> GridTree.removeMany(c, listOf(key), id) }

    /**
     * A new folder holding [first] (when given), placed where [first] was on the home grid, else in the first free
     * slot. [current] is the home grid as shown. Returns its id.
     */
    fun createFolder(name: String, first: String?, current: GridShown, color: Int): String =
        createFolder(name, listOfNotNull(first), null, current, color)

    /** A new folder from [keys] on grid [from] (GridTree.createFolder). Returns its id. */
    fun createFolder(name: String, keys: List<String>, from: String?, home: GridShown, color: Int, move: Boolean = true, fromShown: GridShown? = null): String {
        val id = newFolderId()
        update { c -> GridTree.createFolder(c, id, name, color, keys, from, home, move, fromShown) }
        return id
    }

    /** A new, empty folder in slot [at] of the home grid, or of folder [into], as shown. Returns its id. */
    fun createFolderAt(name: String, current: GridShown, at: Int, color: Int, into: String? = null): String {
        val id = newFolderId()
        update { c ->
            val made = HomeFolder(id, name, color = color)
            if (into == null) c.copy(order = GridSlots.putAt(current.slots, "folder:$id", at), lanes = current.lanes, folders = c.folders + made)
            else GridTree.addInto(c.copy(folders = c.folders + made), "folder:$id", into, at, current)
        }
        return id
    }

    /** Adds [key] to folder [to] as a shortcut: wherever else it is, it stays. A folder moves instead. */
    fun addToFolder(key: String, to: String, at: Int = -1, shown: GridShown? = null) = update { c -> GridTree.addInto(c, key, to, at, shown) }

    /**
     * Moves [key] from [from] (a folder id; null: the home grid, where it leaves a gap) into folder [to]: in slot [at]
     * of the folder as [shown], or after its last thing. Other folders keep it. False when it can't go there.
     */
    fun moveIntoFolder(key: String, from: String?, to: String, at: Int = -1, shown: GridShown? = null): Boolean {
        // A smart folder fills itself; nothing is put in by hand (GridTree.canNest says no).
        if (!GridTree.canNest(config, key, to)) return false
        update { c -> GridTree.moveInto(c, key, from, to, at, shown) ?: c }
        return true
    }

    /** Takes [key] out of folder [from], up a level (GridTree.takeOut). */
    fun takeOut(key: String, from: String, home: GridShown, default: Boolean, at: Int = -1) = update { c -> GridTree.takeOut(c, key, from, home, default, at) }

    /** A new smart folder [id] following [rule], in the first free slot of the home grid as shown ([current]). */
    fun createSmartFolder(id: String, name: String, rule: SmartRule, openOn: String?, current: GridShown, color: Int) = update { c ->
        c.copy(
            order = GridSlots.putAt(current.slots, "folder:$id", current.firstFree(0)),
            lanes = current.lanes,
            folders = c.folders + HomeFolder(id, name, color = color, rule = rule, openOn = openOn),
        )
    }

    /**
     * Folder [id] follows [rule] from now on, under [name]. A manual folder becoming smart puts what was inside back
     * on the home grid ([current], as shown), in the free slots after the folder.
     */
    fun setRule(id: String, name: String, rule: SmartRule, openOn: String?, current: GridShown) = update { c ->
        val f = c.folders.firstOrNull { it.id == id } ?: return@update c
        // Shortcuts already on the home grid stay where they are there; the rest go back on it.
        val released = f.items.filter { it.isNotEmpty() && it !in current.present }
        val folders = c.folders.map { if (it.id == id) it.copy(name = name, rule = rule, openOn = openOn, items = emptyList(), lanes = 0) else it }
        if (released.isEmpty()) return@update c.copy(folders = folders)
        var slots = current.slots
        var from = slots.indexOf("folder:$id") + 1
        val here = current.present + released
        for (k in released) {
            val slot = GridSlots.firstFit(slots, current.lanes, current.ext, from, GridSlots.ONE, here, current.page)
            slots = GridSlots.putAt(slots, k, slot)
            from = slot + 1
        }
        val back = released.filter { GridTree.folderId(it) == null }
        c.copy(
            order = GridSlots.trim(slots), lanes = current.lanes, folders = folders,
            hidden = c.hidden - back.toSet(), added = c.added + back.filter { it !in c.added },
        )
    }

    /**
     * Smart folder [id] becomes a manual one holding [keys] (its games as they are now), as shortcuts: they stay
     * wherever else they are too. Returns how many went in.
     */
    fun freezeFolder(id: String, keys: List<String>): Int {
        val items = keys.filter { it.isNotEmpty() }.distinct()
        update { c -> c.copy(folders = c.folders.map { if (it.id == id) it.copy(rule = null, items = items, lanes = 0) else it }) }
        return items.size
    }

    /** Where games opened from folder [id] start ([FolderScreens]). */
    fun setOpenOn(id: String, openOn: String?) = update { c -> c.copy(folders = c.folders.map { if (it.id == id) it.copy(openOn = openOn) else it }) }

    fun renameFolder(id: String, name: String) = update { c -> c.copy(folders = c.folders.map { if (it.id == id) it.copy(name = name) else it }) }

    /** Deletes a folder, keeping what it holds a level up or not (GridTree.deleteFolder). */
    fun deleteFolder(id: String, home: GridShown, keep: Boolean = true, defaults: Set<String> = emptySet()) =
        update { c -> GridTree.deleteFolder(c, id, home, keep, defaults) }

    /** How folder [id] orders what it holds ([FolderSort]); its manual order is kept underneath. */
    fun setFolderSort(id: String, sort: String) = update { c -> c.copy(folders = c.folders.map { if (it.id == id) it.copy(sort = sort) else it }) }

    /** [keys] on grid [folder] (null: home) moved to slot [at] of [shown] and the free slots after it. */
    fun moveMany(keys: List<String>, folder: String?, shown: GridShown, at: Int) = update { c ->
        if (folder == null) GridTree.moveManyHome(c, keys, shown, at) else GridTree.moveManyInFolder(c, folder, keys, shown, at)
    }

    /** [keys] from grid [from] into folder [to], moved or added as shortcuts. */
    fun putManyInFolder(keys: List<String>, from: String?, to: String, move: Boolean) = update { c -> GridTree.intoFolder(c, keys, from, to, move) }

    /** [keys] off grid [from] (null: the home grid; else that folder only). */
    fun removeMany(keys: List<String>, from: String?) = update { c -> GridTree.removeMany(c, keys, from) }

    /** Saves [key]'s designed look (a default style removes it). */
    fun setStyle(key: String, style: FolderStyle) = update { it.withStyle(key, style) }

    fun resetStyle(key: String) = update { it.resetStyle(key) }

    fun setOrder(order: List<String>) = update { it.copy(order = order) }

    /** The home grid's slots, placed for [lanes]. */
    fun setSlots(slots: List<String>, lanes: Int) = update { it.copy(order = GridSlots.trim(slots), lanes = lanes) }

    /** A folder's slots, placed for [lanes] (unchanged when negative). */
    fun setFolderItems(id: String, items: List<String>, lanes: Int = -1) = update { c ->
        c.copy(folders = c.folders.map { if (it.id == id) it.copy(items = GridSlots.trim(items), lanes = if (lanes >= 0) lanes else it.lanes) else it })
    }

    /**
     * Paste: [key] into slot [at] of [to] (a folder id, or null for the home grid) as [shown], out of [from] (a folder
     * id or null), where it leaves a gap. [default]: it shows on the home grid by default.
     */
    fun place(key: String, from: String?, to: String?, shown: GridShown, at: Int, default: Boolean = false) {
        when {
            to != null -> moveIntoFolder(key, from, to, at, shown)
            from != null -> takeOut(key, from, shown, default, at)
            else -> setSlots(GridSlots.settle(GridSlots.putAt(shown.slots, key, at), shown.lanes, shown.ext, shown.page), shown.lanes)
        }
    }

    // --- Undo --------------------------------------------------------------------------------------------------------

    /** How many steps Undo keeps. */
    const val UNDO_MAX = 20

    private const val UNDO_FILE = "icon-grid-undo.json"

    /** Oldest first. Guarded by itself. */
    private val undos = ArrayList<GridUndo>()
    private var undoLoaded = false

    /** What Undo would take back now ("Removed Mario"), or null when there is nothing to undo. */
    var undoLabel by mutableStateOf<String?>(null)
        private set

    val canUndo: Boolean get() = undoLabel != null

    private fun gridWidgets(): List<Widget> = WidgetStore.widgets.filter { it.place == WidgetPlace.Grid }

    /**
     * Runs [change] as one step Undo can take back. [games] and [systems] are the ones it hides, so Undo can bring
     * them back. True when it changed anything (nothing is kept when it didn't).
     */
    fun undoable(label: String, games: List<String> = emptyList(), systems: List<String> = emptyList(), change: () -> Unit): Boolean {
        val before = GridUndo(label, config, gridWidgets(), games, systems)
        change()
        if (config == before.grid && gridWidgets() == before.widgets && games.isEmpty() && systems.isEmpty()) return false
        push(before)
        return true
    }

    private fun push(u: GridUndo) {
        synchronized(undos) {
            undos.add(u)
            while (undos.size > UNDO_MAX) undos.removeAt(0)
        }
        undoLabel = u.label
        saveUndo()
    }

    /**
     * Takes back the last step: the grid, and the widgets on it, as they were. Returns the step (its games and
     * systems still to bring back), or null when there was nothing to undo.
     */
    fun undo(): GridUndo? {
        val u = synchronized(undos) { undos.removeLastOrNull().also { undoLabel = undos.lastOrNull()?.label } } ?: return null
        update { u.grid }
        val now = gridWidgets()
        if (now != u.widgets) {
            val saved = u.widgets.associateBy { it.id }
            val onGrid = u.grid.added.toHashSet()
            WidgetStore.updateWidgets { list ->
                val kept = list.filter { it.place != WidgetPlace.Grid || "widget:${it.id}" in onGrid }.map { saved[it.id] ?: it }
                kept + u.widgets.filter { w -> kept.none { it.id == w.id } }
            }
        }
        for (g in u.games) Store.update(g) { it.copy(hidden = false) }
        saveUndo()
        return u
    }

    private fun clearUndo() {
        synchronized(undos) { undos.clear() }
        undoLabel = null
        saveUndo()
    }

    private fun saveUndo() {
        val d = dir ?: return
        writer.execute {
            // Read here, after the saved steps were loaded (same thread), so none are lost.
            val list = synchronized(undos) { undos.toList() }
            runCatching {
                val f = File(d, UNDO_FILE)
                if (list.isEmpty()) { f.delete(); return@runCatching }
                val tmp = File(d, "$UNDO_FILE.tmp")
                tmp.writeText(GridJson.json.encodeToString(ListSerializer(GridUndo.serializer()), list))
                tmp.renameTo(f)
            }
        }
    }

    /** Reads the saved steps (on the writer thread, after start). Steps taken meanwhile go after them. */
    private fun loadUndo() {
        val d = dir ?: return
        val read = runCatching {
            GridJson.json.decodeFromString(ListSerializer(GridUndo.serializer()), File(d, UNDO_FILE).readText())
        }.getOrDefault(emptyList())
        synchronized(undos) {
            if (undoLoaded) return
            undoLoaded = true
            val merged = (read + undos).takeLast(UNDO_MAX)
            undos.clear()
            undos.addAll(merged)
            undoLabel = undos.lastOrNull()?.label
        }
    }

    /** Tests: keep files in [d] (as [init] does with the app's files folder) and read them now. */
    internal fun useDir(d: File?) {
        dir = d
        synchronized(undos) { undos.clear(); undoLoaded = false }
        undoLabel = null
        config = IconGridConfig(version = GridTree.SHORTCUTS)
        if (d != null) {
            config = runCatching { GridJson.decode(File(d, "icon-grid.json").readText()) }.getOrDefault(config)
            loadUndo()
        }
    }

    /** Tests: waits for the files to be written. */
    internal fun flush() { writer.submit {}.get() }
}
