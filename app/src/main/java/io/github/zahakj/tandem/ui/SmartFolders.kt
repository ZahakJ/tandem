package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.data.FolderScreens
import io.github.zahakj.tandem.data.HomeFolder
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.SmartRule
import io.github.zahakj.tandem.plural

/*
 * Smart folders: home folders that fill themselves from a rule (HomeFolder.rule, data/SmartRule.kt). Their games are
 * worked out in Collections, cached per library, details and hidden-games change. Here they become a tile (from
 * GridCatalog.folder), open as a collection ("smart:<id>") in the folder popup or page, and get their rows in the
 * tile's Y menu. Rules are edited in SmartFolderForm.kt.
 */

object SmartFolders {
    /** The icon-grid tile for smart folder [f]: its games, a live count on the icon, and the usual folder look. */
    fun tile(f: HomeFolder): GridItem {
        val color = folderColor(f.color)
        val games = Collections.smart(f)
        val names = FolderArtNames.of(f)
        val base = IconPacks.folderIcon(names)?.let { TileArt.Picture(it) }
            ?: TileArt.Folder(color, Glyph.Sparkle, games.take(12).map { TileArt.GameBox(it) }, f.name.trim().take(1).uppercase().ifEmpty { null })
        val count = plural(games.size, "game")
        return GridItem(
            "folder:${f.id}", TileKind.Folder, f.name, "Smart folder", count, color, TileArt.Counted(base, games.size), games,
            Selected.Group(f.name, count, games, color), default = false,
            open = { openFolder(route(f.id)) }, folderId = f.id,
            blurb = describe(f.rule ?: SmartRule()), packNames = names, hidden = f.hidden,
        )
    }

    fun route(id: String) = Route.Collection(Collections.SMART + id)

    /** The smart folder a collection route shows, if it is one. */
    fun folderOf(r: Route?): HomeFolder? =
        (r as? Route.Collection)?.id?.takeIf { it.startsWith(Collections.SMART) }?.let { IconGridStore.folder(it.removePrefix(Collections.SMART)) }

    /** The grid tile a smart folder's popup grows out of, and whose icon its header shows. */
    fun tileKey(r: Route): String? = folderOf(r)?.let { "folder:${it.id}" }

    fun art(r: Route): TileArt? = tileKey(r)?.let { IconGrid.menuData?.first?.item(it)?.art }

    /** The rule in a few words: "3DS · RPG · Unplayed · Random 10". */
    fun describe(rule: SmartRule): String {
        val parts = ArrayList<String>()
        if (rule.systems.isNotEmpty()) parts += if (rule.systems.size <= 3) rule.systems.joinToString(", ") { systemName(it, short = true) } else plural(rule.systems.size, "system")
        if (rule.genres.isNotEmpty()) parts += if (rule.genres.size <= 2) rule.genres.joinToString(", ") else plural(rule.genres.size, "genre")
        if (rule.pattern.isNotEmpty()) parts += if (rule.regex) "/${rule.pattern}/" else "“${rule.pattern}”"
        yearsLabel(rule)?.let { parts += it }
        if (rule.favorites) parts += "Favorites"
        if (rule.played != SmartRule.PLAYED_ANY) parts += SmartRule.playedLabel(rule.played)
        rule.emulator?.let { parts += emulatorName(it) }
        if (rule.random > 0) parts += "Random ${rule.random}"
        return parts.ifEmpty { listOf("Every game") }.joinToString(" · ")
    }

    fun yearsLabel(rule: SmartRule): String? = when {
        rule.yearFrom != null && rule.yearTo != null -> if (rule.yearFrom == rule.yearTo) "${rule.yearFrom}" else "${rule.yearFrom}–${rule.yearTo}"
        rule.yearFrom != null -> "From ${rule.yearFrom}"
        rule.yearTo != null -> "Up to ${rule.yearTo}"
        else -> null
    }

    fun systemName(id: String, short: Boolean = false): String =
        Libraries.byId(id)?.platform?.let { if (short) it.shortName else it.name } ?: id

    /** An emulator app's name from platforms.json ("RetroArch", not one of its cores), else its package. */
    fun emulatorName(pkg: String): String =
        Libraries.all.orEmpty().firstNotNullOfOrNull { lib -> lib.platform.allPlayers.firstOrNull { it.packageName == pkg }?.name?.let(::appName) } ?: pkg

    /** "RetroArch · mGBA" -> "RetroArch". */
    fun appName(playerName: String): String = playerName.substringBefore(" · ").trim()

    /**
     * The screen [game] starts on when opened from inside a folder that chose one ([HomeFolder.openOn]): a smart
     * folder's popup or page, or a manual folder open on the grid. Null elsewhere.
     */
    fun screenFor(game: Game): String? {
        (folderOf(FolderPopup.route) ?: folderOf(Nav.route))?.let { f ->
            return f.openOn?.takeIf { Collections.smart(f).any { it.key == game.key } }
        }
        val open = IconGrid.openFolder?.let { IconGridStore.folder(it) } ?: return null
        return open.openOn?.takeIf { "game:${game.key}" in open.items }
    }

    /**
     * Adds the smart-folder rows to a grid tile's Y menu: Edit rules, Open games on and Convert to manual folder on
     * a smart folder; Make this a smart folder and Open games on on a manual one. "Put in a folder" stops offering
     * smart folders, which fill themselves.
     */
    fun extendMenu(spec: MenuSpec): MenuSpec {
        val prefix = IconGrid.MENU_ROUTE + ":"
        if (!spec.routeKey.startsWith(prefix)) return spec
        val smartIds = IconGridStore.config.folders.filter { it.smart }.mapTo(HashSet()) { it.id }
        val page = spec.page?.takeIf { smartIds.isNotEmpty() }?.let { p -> MenuPage(p.title, p.actions.filter { it.id !in smartIds }, p.onBack) } ?: spec.page
        val key = spec.routeKey.removePrefix(prefix)
        val f = key.takeIf { it.startsWith("folder:") }?.let { IconGridStore.folder(it.removePrefix("folder:")) }
        if (f == null) return if (page === spec.page) spec else MenuSpec(spec.routeKey, spec.title, spec.subtitle, spec.color, spec.actions, page, spec.aspect, spec.art)
        val screen = MenuAction("screen", "Open games on", Glyph.Screens, Palette.Sky, value = FolderScreens.label(f.openOn)) {
            val all = FolderScreens.ALL
            IconGridStore.setOpenOn(f.id, all[(all.indexOf(f.openOn) + 1) % all.size])
        }
        val added = if (f.smart) listOf(
            MenuAction("rules", "Edit rules…", Glyph.Sparkle, Palette.Mint) { IconGrid.closeMenu(); SmartFolderForm.edit(f.id) },
            screen,
            MenuAction("freeze", "Convert to manual folder", Glyph.Folder, Palette.Butter, more = true) {
                IconGrid.closeMenu()
                val games = Collections.smart(f)
                val kept = IconGridStore.freezeFolder(f.id, games.map { "game:${it.key}" })
                Toasts.show(
                    if (kept == games.size) "${f.name} is a manual folder now, with ${plural(kept, "game")}"
                    else "${f.name} is a manual folder now. ${plural(games.size - kept, "game")} already elsewhere stayed put",
                )
            },
        ) else listOf(
            screen,
            MenuAction("smart", "Make this a smart folder", Glyph.Sparkle, Palette.Lavender, more = true) { IconGrid.closeMenu(); SmartFolderForm.edit(f.id) },
        )
        // After Open (and Move), before the rest.
        val at = spec.actions.indexOfFirst { it.id == "move" }.let { if (it >= 0) it + 1 else spec.actions.indexOfFirst { a -> a.id == "open" } + 1 }
        val actions = spec.actions.toMutableList().apply { addAll(at.coerceIn(0, size), added) }
        return MenuSpec(spec.routeKey, spec.title, spec.subtitle, spec.color, actions, page, spec.aspect, spec.art)
    }
}
