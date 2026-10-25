package io.github.zahakj.tandem.data

/**
 * The rules of home folders, on plain configs (IconGridStore applies them; GridTreeTest tests them).
 *
 * - **Shortcuts.** A folder's items are shortcuts: a game, app or system can sit in several folders and on the home
 *   grid at once. Being on the home grid is its own thing: a default tile (a system) is there unless it is in
 *   [IconGridConfig.hidden], anything else when it is in [IconGridConfig.added].
 * - **Nesting.** Folders can hold folders, [MAX_DEPTH] levels deep (Home › Games › RPGs). A folder has one place:
 *   on the home grid, or inside exactly one other folder.
 */
object GridTree {
    /** Configs from this version on hold shortcuts; older ones are moved over by [migrate]. */
    const val SHORTCUTS = 1

    /** Home is level 0, a folder on it level 1, a folder inside that level 2: the deepest. */
    const val MAX_DEPTH = 2

    private const val FOLDER = "folder:"

    fun folderKey(id: String): String = FOLDER + id

    fun folderId(key: String): String? = if (key.startsWith(FOLDER)) key.substring(FOLDER.length) else null

    /**
     * An arrangement from before shortcuts: what sat in a folder was off the home grid then, and it stays off. Things
     * shown by default are listed as hidden, added things are no longer added (they are still in their folder).
     */
    fun migrate(c: IconGridConfig): IconGridConfig {
        if (c.version >= SHORTCUTS) return c
        val inside = c.folders.flatMap { it.items }.filter { it.isNotEmpty() && folderId(it) == null }.toSet()
        if (inside.isEmpty()) return c.copy(version = SHORTCUTS)
        val hidden = c.hidden.toHashSet()
        return c.copy(
            version = SHORTCUTS,
            hidden = c.hidden + inside.filter { it !in hidden },
            added = c.added.filter { it !in inside },
        )
    }

    fun folder(c: IconGridConfig, id: String): HomeFolder? = c.folders.firstOrNull { it.id == id }

    /** The folder holding folder [id], or null when it sits on the home grid. */
    fun parentOf(c: IconGridConfig, id: String): HomeFolder? {
        val key = folderKey(id)
        return c.folders.firstOrNull { it.id != id && key in it.items }
    }

    /** The folders from the home grid down to [id], [id] last. */
    fun trail(c: IconGridConfig, id: String): List<HomeFolder> {
        val out = ArrayList<HomeFolder>()
        var at: HomeFolder? = folder(c, id)
        val seen = HashSet<String>()
        while (at != null && seen.add(at.id)) {
            out.add(0, at)
            at = parentOf(c, at.id)
        }
        return out
    }

    /** 1 for a folder on the home grid, 2 for one inside it. */
    fun depthOf(c: IconGridConfig, id: String): Int = trail(c, id).size.coerceAtLeast(1)

    /** The folders right inside [id]. */
    fun subfolders(c: IconGridConfig, id: String): List<String> =
        folder(c, id)?.items.orEmpty().mapNotNull { folderId(it) }.filter { s -> s != id && c.folders.any { it.id == s } }

    /** How many levels of folders [id] holds below it: 0 with none. */
    fun heightOf(c: IconGridConfig, id: String, seen: MutableSet<String> = HashSet()): Int {
        if (!seen.add(id)) return 0
        return subfolders(c, id).maxOfOrNull { 1 + heightOf(c, it, seen) } ?: 0
    }

    /** Every folder inside [id], at any depth. */
    fun descendants(c: IconGridConfig, id: String): Set<String> {
        val out = LinkedHashSet<String>()
        val todo = ArrayDeque(subfolders(c, id))
        while (todo.isNotEmpty()) {
            val s = todo.removeFirst()
            if (s == id || !out.add(s)) continue
            todo.addAll(subfolders(c, s))
        }
        return out
    }

    /** Whether folder [into] can take a new folder of its own. Null is the home grid, which always can. */
    fun canHoldFolder(c: IconGridConfig, into: String?): Boolean = into == null || folder(c, into)?.smart != true && depthOf(c, into) < MAX_DEPTH

    /**
     * Whether [key] can go into folder [into]. Anything but a folder can; a folder only when it wouldn't end up inside
     * itself (a cycle) and no deeper than [MAX_DEPTH], subfolders counted.
     */
    fun canNest(c: IconGridConfig, key: String, into: String): Boolean {
        // A smart folder fills itself: nothing goes in by hand.
        if (folder(c, into)?.takeIf { !it.smart } == null) return false
        val id = folderId(key) ?: return true
        if (id == into || into in descendants(c, id)) return false
        return depthOf(c, into) + 1 + heightOf(c, id) <= MAX_DEPTH
    }

    /** What folder [id] holds, its subfolders' contents (and their keys) included. */
    fun deepItems(c: IconGridConfig, id: String): Set<String> = deepItems(c.folders.associateBy { it.id }, id)

    private fun deepItems(byId: Map<String, HomeFolder>, id: String): Set<String> {
        val out = LinkedHashSet<String>()
        val seen = HashSet<String>()
        val todo = ArrayDeque(listOf(id))
        while (todo.isNotEmpty()) {
            val f = byId[todo.removeFirst()] ?: continue
            if (!seen.add(f.id)) continue
            for (k in f.items) {
                if (k.isEmpty()) continue
                out.add(k)
                folderId(k)?.let { todo.add(it) }
            }
        }
        return out
    }

    /** The keys hidden folders hide everywhere: everything inside them, subfolders included. [skip]: folder ids left out. */
    fun hiddenTiles(folders: List<HomeFolder>, skip: Set<String> = emptySet()): Set<String> {
        val hidden = folders.filter { it.hidden && it.id !in skip }
        if (hidden.isEmpty()) return emptySet()
        val byId = folders.associateBy { it.id }
        return hidden.flatMapTo(HashSet()) { deepItems(byId, it.id) }
    }

    // --- The home grid ---------------------------------------------------------------------------------------------

    /** Whether [key] is on the home grid ([default]: it shows there unless hidden, like a system). */
    fun onHome(c: IconGridConfig, key: String, default: Boolean): Boolean {
        folderId(key)?.let { id -> return folder(c, id) != null && parentOf(c, id) == null }
        return if (default) key !in c.hidden else key in c.added
    }

    /** [key] off the home grid, leaving a gap; its folders keep it. Not for folders. */
    fun offHome(c: IconGridConfig, key: String): IconGridConfig = c.copy(
        hidden = if (key in c.added || key in c.hidden) c.hidden else c.hidden + key,
        added = c.added - key,
        order = GridSlots.clear(c.order, key),
        spans = c.spans - key,
    )

    /** [key] counted as on the home grid again (where it goes is up to the caller). */
    fun onToHome(c: IconGridConfig, key: String, default: Boolean): IconGridConfig = if (folderId(key) != null) c else c.copy(
        hidden = c.hidden - key,
        added = if (default || key in c.added) c.added else c.added + key,
    )

    private fun clearIn(c: IconGridConfig, folderId: String, key: String): IconGridConfig =
        c.copy(folders = c.folders.map { if (it.id == folderId && key in it.items) it.copy(items = GridSlots.clear(it.items, key)) else it })

    // --- Into folders ----------------------------------------------------------------------------------------------

    /**
     * [key] put in folder [to], wherever else it is staying put (a shortcut). In slot [at] of the folder as [shown],
     * else after its last thing; a sorted folder adds it to the end of its own order. A folder key moves instead: it
     * leaves the home grid or the folder it was in. Unchanged when it can't go there ([canNest]).
     */
    fun addInto(c: IconGridConfig, key: String, to: String, at: Int = -1, shown: GridShown? = null): IconGridConfig {
        if (!canNest(c, key, to)) return c
        val base = if (folderId(key) == null) c else c.copy(
            order = GridSlots.clear(c.order, key),
            folders = c.folders.map { if (it.id != to && key in it.items) it.copy(items = GridSlots.clear(it.items, key)) else it },
        )
        return base.copy(folders = base.folders.map { f ->
            when {
                f.id != to -> f
                shown != null && at >= 0 && f.sort == FolderSort.MANUAL -> f.copy(items = GridSlots.putAt(shown.slots, key, at), lanes = shown.lanes)
                key in f.items -> f
                else -> f.copy(items = GridSlots.trim(f.items) + key)
            }
        })
    }

    /**
     * [key] moved out of [from] (a folder id; null: the home grid) into folder [to], as [addInto]. Null when it can't
     * go there.
     */
    fun moveInto(c: IconGridConfig, key: String, from: String?, to: String, at: Int = -1, shown: GridShown? = null): IconGridConfig? {
        if (!canNest(c, key, to)) return null
        val out = when {
            folderId(key) != null -> c
            from == null -> offHome(c, key)
            from != to -> clearIn(c, from, key)
            else -> c
        }
        return addInto(out, key, to, at, shown)
    }

    /**
     * [key] taken out of folder [from], up a level: into the folder holding [from] (in the first free slot after
     * it), or onto the home grid as [home] shows it, in slot [at] or the first free slot after the folder. Already
     * there, it just leaves [from] (and moves to [at] when one is given).
     */
    fun takeOut(c: IconGridConfig, key: String, from: String, home: GridShown, default: Boolean, at: Int = -1): IconGridConfig {
        val parent = parentOf(c, from)
        val cleared = clearIn(c, from, key)
        if (parent != null) {
            if (key in parent.items) return cleared
            val slot = GridSlots.firstFree(parent.items, parent.items.indexOf(folderKey(from)) + 1)
            return cleared.copy(folders = cleared.folders.map {
                when {
                    it.id != parent.id -> it
                    it.sort != FolderSort.MANUAL -> it.copy(items = GridSlots.trim(it.items) + key)
                    else -> it.copy(items = GridSlots.putAt(it.items, key, slot))
                }
            })
        }
        val placed = onToHome(cleared, key, default)
        if (folderId(key) == null && onHome(c, key, default) && key in home.slots && at < 0) return placed
        val slot = if (at >= 0) at else home.firstFree(home.slots.indexOf(folderKey(from)) + 1)
        return placed.copy(order = GridSlots.putAt(home.slots, key, slot), lanes = home.lanes)
    }

    /**
     * A new folder [id] holding [keys] (in that order), made from grid [from] (a folder id, or null for the home
     * grid as [home] shows it; [fromShown] is the folder as shown). It goes where the first of them was ([move]) or
     * in the first free slot after it, inside [from] when that can hold a folder, else on the home grid. With [move]
     * the keys leave [from]; otherwise they stay there too. Folders that would end up too deep stay where they are.
     */
    fun createFolder(
        c: IconGridConfig, id: String, name: String, color: Int, keys: List<String>, from: String?, home: GridShown,
        move: Boolean = true, fromShown: GridShown? = null,
    ): IconGridConfig {
        val container = if (from != null && canHoldFolder(c, from)) from else null
        val depth = (container?.let { depthOf(c, it) } ?: 0) + 1
        val usable = keys.distinct().filter { k -> folderId(k)?.let { f -> f != id && depth + 1 + heightOf(c, f) <= MAX_DEPTH } ?: true }
        val key = folderKey(id)
        var n = c
        // Where the first key was, in the grid the folder goes on.
        val sameGrid = container == from
        val baseSlots = if (container == null) home.slots else (fromShown?.takeIf { sameGrid }?.slots ?: folder(c, container)?.items.orEmpty())
        val anchor = usable.firstOrNull()?.let { baseSlots.indexOf(it) }?.takeIf { it >= 0 && sameGrid }
        if (move) for (k in usable) {
            n = when {
                folderId(k) != null -> n
                from == null -> offHome(n, k)
                else -> clearIn(n, from, k)
            }
        }
        // Folder keys leave wherever they were (a folder has one place).
        val moved = usable.filter { folderId(it) != null }.toSet()
        if (moved.isNotEmpty()) n = n.copy(
            order = n.order.map { if (it in moved) GridSlots.EMPTY else it }.let(GridSlots::trim),
            folders = n.folders.map { f -> if (f.items.any { it in moved }) f.copy(items = GridSlots.trim(f.items.map { if (it in moved) GridSlots.EMPTY else it })) else f },
        )
        val taken = if (move && sameGrid) usable.toSet() else emptySet()
        val cleared = baseSlots.map { if (it in moved || it in taken) GridSlots.EMPTY else it }
        val made = HomeFolder(id, name, usable, color)
        if (container == null) {
            val after = when {
                anchor != null && move -> -1
                anchor != null -> anchor + 1
                from != null -> trail(c, from).firstOrNull()?.let { home.slots.indexOf(folderKey(it.id)) + 1 } ?: 0
                else -> 0
            }
            val shownNow = GridShown(cleared, home.present - moved - taken,home.lanes, home.ext, home.page)
            val slot = if (after < 0) anchor!! else shownNow.firstFree(after)
            return n.copy(order = GridSlots.putAt(cleared, key, slot), lanes = home.lanes, folders = n.folders + made)
        }
        val holder = folder(n, container)!!
        val slot = when {
            anchor != null && move -> anchor
            anchor != null -> GridSlots.firstFree(cleared, anchor + 1)
            else -> GridSlots.firstFree(cleared, 0)
        }
        val items = if (holder.sort == FolderSort.MANUAL) GridSlots.putAt(cleared, key, slot) else GridSlots.trim(holder.items) + key
        return n.copy(folders = n.folders.map { if (it.id == container) it.copy(items = items, lanes = fromShown?.takeIf { sameGrid }?.lanes ?: it.lanes) else it } + made)
    }

    /**
     * Deletes folder [id]. With [keep], what it holds moves up a level: into its place and the free slots after it,
     * on the home grid (as [home] shows it) or in the folder holding it; things already there stay where they are.
     * Without, the folder and every folder inside it go (their shortcuts with them; games themselves are untouched).
     * [defaults]: the keys the home grid shows by default.
     */
    fun deleteFolder(c: IconGridConfig, id: String, home: GridShown, keep: Boolean, defaults: Set<String> = emptySet()): IconGridConfig {
        val f = folder(c, id) ?: return c
        val key = folderKey(id)
        val parent = parentOf(c, id)
        if (!keep) {
            val gone = descendants(c, id) + id
            val left = c.folders.filter { it.id !in gone }.map { if (key in it.items) it.copy(items = GridSlots.clear(it.items, key)) else it }
            val order = if (parent == null) GridSlots.clear(home.slots, key) else c.order
            return c.copy(order = order, lanes = if (parent == null) home.lanes else c.lanes, folders = left, styles = c.styles - gone.map(::folderKey).toSet()).pruneStyles()
        }
        val inside = f.items.filter { it.isNotEmpty() }
        if (parent == null) {
            val order = home.slots.toMutableList()
            val here = HashSet(home.present - key)
            var at = order.indexOf(key).let { if (it >= 0) it else GridSlots.firstFit(order, home.lanes, home.ext, 0, GridSlots.ONE, here, home.page) }
            if (at < order.size) order[at] = GridSlots.EMPTY
            var n = c
            for (k in inside) {
                if (folderId(k) == null && onHome(n, k, k in defaults) && k in order) continue
                at = GridSlots.firstFit(order, home.lanes, home.ext, at, GridSlots.ONE, here, home.page)
                while (order.size <= at) order.add(GridSlots.EMPTY)
                order[at] = k
                here.add(k)
                n = onToHome(n, k, k in defaults)
            }
            return n.copy(order = GridSlots.trim(order), lanes = home.lanes, folders = n.folders.filter { it.id != id }, styles = n.styles - key).pruneStyles()
        }
        val slots = parent.items.toMutableList()
        var at = slots.indexOf(key)
        slots[at] = GridSlots.EMPTY
        for (k in inside) {
            if (k in slots) continue
            at = GridSlots.firstFree(slots, at)
            while (slots.size <= at) slots.add(GridSlots.EMPTY)
            slots[at] = k
        }
        return c.copy(
            folders = c.folders.filter { it.id != id }.map { if (it.id == parent.id) it.copy(items = GridSlots.trim(slots)) else it },
            styles = c.styles - key,
        ).pruneStyles()
    }

    // --- Several at once -------------------------------------------------------------------------------------------

    /** [keys] (on the home grid as [home] shows it) moved to slot [at] and the free slots after it, in reading order. */
    fun moveManyHome(c: IconGridConfig, keys: List<String>, home: GridShown, at: Int): IconGridConfig =
        c.copy(order = GridSlots.moveMany(home.slots, home.lanes, home.ext, keys, at, home.present, home.page), lanes = home.lanes)

    /** [keys] in folder [id] (as [shown]) moved to slot [at] and the free slots after it. */
    fun moveManyInFolder(c: IconGridConfig, id: String, keys: List<String>, shown: GridShown, at: Int): IconGridConfig =
        c.copy(folders = c.folders.map { if (it.id == id) it.copy(items = GridSlots.moveMany(shown.slots, shown.lanes, emptyMap(), keys, at, shown.present), lanes = shown.lanes) else it })

    /**
     * [keys] put in folder [to] from [from] (a folder id, or null for the home grid), in order: moved ([move]) or
     * added as shortcuts. Those that can't go there (a folder into itself, too deep) stay.
     */
    fun intoFolder(c: IconGridConfig, keys: List<String>, from: String?, to: String, move: Boolean): IconGridConfig {
        var n = c
        for (k in keys) {
            if (k == folderKey(to)) continue
            n = if (move || folderId(k) != null) moveInto(n, k, from, to) ?: n else addInto(n, k, to)
        }
        return n
    }

    /** [keys] off grid [from] (null: the home grid; else that folder only). Folders are left alone. */
    fun removeMany(c: IconGridConfig, keys: List<String>, from: String?): IconGridConfig {
        var n = c
        for (k in keys) {
            if (folderId(k) != null) continue
            n = if (from == null) offHome(n, k) else clearIn(n, from, k)
        }
        return n
    }
}

/**
 * How a folder orders what is inside: by hand ([MANUAL], the order kept underneath all the others), or by name,
 * play or system. Folders inside always come first in the sorted orders.
 */
object FolderSort {
    const val MANUAL = "manual"
    const val NAME = "name"
    const val RECENT = "recent"
    const val MOST = "most"
    const val ADDED = "added"
    const val SYSTEM = "system"

    val ALL: List<Pair<String, String>> = listOf(
        MANUAL to "Manual", NAME to "A–Z", RECENT to "Recently played", MOST to "Most played", ADDED to "Date added", SYSTEM to "System",
    )

    fun label(sort: String): String = ALL.firstOrNull { it.first == sort }?.second ?: "Manual"

    /** What sorting needs to know about a tile. [system]: its system's name ("" for things that have none). */
    class Facts(val title: String, val lastPlayed: Long = 0, val played: Long = 0, val added: Long = 0, val system: String = "", val folder: Boolean = false)

    /** [keys] (gaps dropped) in [sort]'s order; keys [facts] knows nothing of go last, in their own order. */
    fun apply(keys: List<String>, sort: String, facts: (String) -> Facts?): List<String> {
        val list = keys.filter { it.isNotEmpty() }.distinct()
        if (sort == MANUAL) return list
        val known = list.mapNotNull { k -> facts(k)?.let { k to it } }
        val seen = known.mapTo(HashSet()) { it.first }
        val unknown = list.filter { it !in seen }
        val byTitle = compareBy<Pair<String, Facts>> { it.second.title.lowercase() }
        val order: Comparator<Pair<String, Facts>> = when (sort) {
            NAME -> byTitle
            RECENT -> compareByDescending<Pair<String, Facts>> { it.second.lastPlayed }.then(byTitle)
            MOST -> compareByDescending<Pair<String, Facts>> { it.second.played }.then(byTitle)
            ADDED -> compareByDescending<Pair<String, Facts>> { it.second.added }.then(byTitle)
            SYSTEM -> compareBy<Pair<String, Facts>> { it.second.system.isEmpty() }.thenBy { it.second.system.lowercase() }.then(byTitle)
            else -> return list
        }
        return known.sortedWith(compareBy<Pair<String, Facts>> { !it.second.folder }.then(order)).map { it.first } + unknown
    }
}
