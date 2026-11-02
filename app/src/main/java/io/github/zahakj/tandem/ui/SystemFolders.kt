package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.data.GridTree
import io.github.zahakj.tandem.data.HomeFolder
import io.github.zahakj.tandem.data.IconGridStore

/*
 * Systems arranged like folders (HomeFolder.system): the system's tile on the icon grid opens a folder panel instead
 * of the plain list. Whatever was placed by hand (games moved about, empty spots, folders made inside, a genre of
 * its own) comes first; every other game of the system follows, by name, so a game copied in later still shows up.
 * Nothing on disk moves: the folder only holds shortcuts.
 */
object SystemFolders {
    /** Every game of [f]'s system not placed in it or in a folder inside it, as tile keys, by name. */
    fun rest(f: HomeFolder): List<String> {
        val id = f.system ?: return emptyList()
        val lib = Libraries.byId(id) ?: return emptyList()
        val c = IconGridStore.config
        val placed = HashSet(f.items)
        for (d in GridTree.descendants(c, f.id)) GridTree.folder(c, d)?.let { placed += it.items }
        return Collections.visible(lib.games).sortedBy { it.title.lowercase() }.map { "game:${it.key}" }.filter { it !in placed }
    }

    /** What folder [f] shows, in its own order: its things, then (a system's) the rest of the system's games. */
    fun contents(f: HomeFolder, catalog: GridCatalog): List<String> {
        val own = f.items.filter { it.isNotEmpty() && catalog.item(it) != null }
        return if (f.system == null) own else own + rest(f).filter { catalog.item(it) != null }
    }

    /** Opens system [platformId]: its folder when it is arranged as one, else the plain list. */
    fun open(platformId: String) {
        val f = IconGridStore.systemFolder(platformId)
        if (f == null) { openFolder(Route.System(platformId)); return }
        FocusMemory.forget(IconGrid.folderRoute(f.id))
        IconGrid.openFolder = f.id
    }

    /** System [platformId] arranged like a folder from now on, and opened as one. */
    fun arrange(platformId: String) {
        val lib = Libraries.byId(platformId) ?: return
        GridUndoUi.run("Arranged ${lib.platform.name}") {
            IconGridStore.arrangeSystem(platformId, lib.platform.name, IconGridStore.config.folders.size)
        }
        FolderPopup.close()
        open(platformId)
        Sounds.confirm()
        Toasts.show("${lib.platform.name} is arranged like a folder now. Y on an empty spot adds folders and widgets", 4500)
    }

    /** System [platformId] back to its plain list; the folders made inside it go to the home grid. */
    fun unarrange(platformId: String) {
        val f = IconGridStore.systemFolder(platformId) ?: return
        val subs = GridTree.subfolders(IconGridStore.config, f.id).size
        if (IconGrid.openFolder == f.id) IconGrid.openFolder = null
        GridUndoUi.run("${f.name} as a list") { IconGridStore.unarrangeSystem(platformId, homeShown()) }
        Nav.focusTick++
        Toasts.show(if (subs == 0) "${f.name} shows as a plain list again" else "${f.name} shows as a list again. Its folders moved to home")
    }
}
