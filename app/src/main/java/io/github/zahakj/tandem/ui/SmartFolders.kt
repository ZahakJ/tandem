package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.data.FolderScreens
import io.github.zahakj.tandem.data.HomeFolder
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.SmartRule


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
        val count = Tx.p(R.plurals.grid_games, games.size)
        return GridItem(
            "folder:${f.id}", TileKind.Folder, f.name, Tx.s(R.string.grid_type_smart_folder), count, color, TileArt.Counted(base, games.size), games,
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
        if (rule.systems.isNotEmpty()) parts += if (rule.systems.size <= 3) Tx.list(rule.systems) { systemName(it, short = true) } else Tx.p(R.plurals.grid_systems, rule.systems.size)
        if (rule.genres.isNotEmpty()) parts += if (rule.genres.size <= 2) Tx.list(rule.genres) else Tx.p(R.plurals.grid_genres, rule.genres.size)
        if (rule.pattern.isNotEmpty()) parts += if (rule.regex) "/${rule.pattern}/" else Tx.s(R.string.grid_smart_pattern, rule.pattern)
        yearsLabel(rule)?.let { parts += it }
        if (rule.favorites) parts += Tx.s(R.string.grid_smart_favorites)
        if (rule.played != SmartRule.PLAYED_ANY) parts += SmartRule.playedLabel(rule.played)
        rule.emulator?.let { parts += emulatorName(it) }
        rule.ratingFrom?.let { parts += starsAtLeast(it) }
        rule.ratingTo?.let { parts += starsAtMost(it) }
        if (rule.developer.isNotBlank()) parts += rule.developer.trim()
        if (rule.publisher.isNotBlank()) parts += rule.publisher.trim()
        rule.playedMoreHours?.let { parts += Tx.p(R.plurals.grid_smart_more_hours, it) }
        rule.playedLessHours?.let { parts += Tx.p(R.plurals.grid_smart_less_hours, it) }
        lastPlayedLabel(rule.lastPlayed)?.let { parts += it }
        if (rule.random > 0) parts += Tx.s(R.string.grid_smart_random, rule.random)
        if (parts.size > 1 && rule.match == SmartRule.MATCH_ANY) return Tx.s(R.string.grid_smart_any_of, parts.joinToString(" · "))
        return parts.ifEmpty { listOf(Tx.s(R.string.grid_smart_every_game)) }.joinToString(" · ")
    }

    /** All rules or any rule ([SmartRule.match]). */
    fun combineLabel(m: String) = Tx.s(if (m == SmartRule.MATCH_ANY) R.string.grid_form_combine_any else R.string.grid_form_combine_all)

    /** "4.5" or "4": a rating (0..1) as stars out of five. */
    private fun stars(r: Float): String {
        val s = Math.round(r * 10f) / 2f
        return if (s % 1f == 0f) "${s.toInt()}" else String.format(io.github.zahakj.tandem.Lang.locale, "%.1f", s)
    }

    fun starsAtLeast(r: Float) = Tx.s(R.string.grid_form_stars_at_least, stars(r))
    fun starsAtMost(r: Float) = Tx.s(R.string.grid_form_stars_at_most, stars(r))

    /** [SmartRule.lastPlayed] in words ("In the last 30 days"); null for any. */
    fun lastPlayedLabel(v: String): String? {
        val (kind, days) = SmartRule(lastPlayed = v).lastPlayedRule() ?: return null
        return when (kind) {
            SmartRule.LAST_WITHIN -> Tx.p(R.plurals.grid_form_last_within, days)
            SmartRule.LAST_NOT_WITHIN -> Tx.p(R.plurals.grid_form_last_not_within, days)
            else -> Tx.s(R.string.grid_form_last_never)
        }
    }

    fun yearsLabel(rule: SmartRule): String? = when {
        rule.yearFrom != null && rule.yearTo != null -> if (rule.yearFrom == rule.yearTo) "${rule.yearFrom}" else "${rule.yearFrom}–${rule.yearTo}"
        rule.yearFrom != null -> Tx.s(R.string.grid_smart_years_from, "${rule.yearFrom}")
        rule.yearTo != null -> Tx.s(R.string.grid_smart_years_to, "${rule.yearTo}")
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
        if (!spec.routeKey.startsWith(prefix) || spec.compact) return spec
        val smartIds = IconGridStore.config.folders.filter { it.smart }.mapTo(HashSet()) { it.id }
        val page = spec.page?.takeIf { smartIds.isNotEmpty() }?.let { p -> MenuPage(p.title, p.actions.filter { it.id !in smartIds }, p.onBack) } ?: spec.page
        val key = spec.routeKey.removePrefix(prefix)
        val f = key.takeIf { it.startsWith("folder:") }?.let { IconGridStore.folder(it.removePrefix("folder:")) }
        if (f == null) return if (page === spec.page) spec else MenuSpec(spec.routeKey, spec.title, spec.subtitle, spec.color, spec.actions, page, spec.aspect, art = spec.art)
        val all = FolderScreens.ALL
        fun stepScreen(by: Int) = IconGridStore.setOpenOn(f.id, all[Math.floorMod(all.indexOf(f.openOn) + by, all.size)])
        val screen = MenuAction(
            "screen", Tx.s(R.string.grid_smart_open_on), Glyph.Screens, Palette.Sky, value = FolderScreens.label(f.openOn), step = ::stepScreen,
            choices = all.map { s ->
                MenuChoice(FolderScreens.label(s), s == f.openOn, sub = if (s == null) Tx.s(R.string.grid_smart_open_own) else null) { IconGridStore.setOpenOn(f.id, s) }
            },
        ) { stepScreen(1) }
        val added = if (f.smart) listOf(
            MenuAction("rules", Tx.s(R.string.grid_smart_edit_rules), Glyph.Sparkle, Palette.Mint) { IconGrid.closeMenu(); SmartFolderForm.edit(f.id) },
            screen,
            MenuAction("freeze", Tx.s(R.string.grid_smart_freeze), Glyph.Folder, Palette.Butter, more = true) {
                IconGrid.closeMenu()
                val games = Collections.smart(f)
                val kept = IconGridStore.freezeFolder(f.id, games.map { "game:${it.key}" })
                Toasts.show(
                    if (kept == games.size) Tx.p(R.plurals.grid_smart_frozen, kept, kept, f.name)
                    else Tx.p(R.plurals.grid_smart_frozen_some, games.size - kept, games.size - kept, f.name),
                )
            },
        ) else listOf(
            screen,
            MenuAction("smart", Tx.s(R.string.grid_smart_make), Glyph.Sparkle, Palette.Lavender, more = true) { IconGrid.closeMenu(); SmartFolderForm.edit(f.id) },
        )
        // After Open (and Move), before the rest.
        val at = spec.actions.indexOfFirst { it.id == "move" }.let { if (it >= 0) it + 1 else spec.actions.indexOfFirst { a -> a.id == "open" } + 1 }
        val actions = spec.actions.toMutableList().apply { addAll(at.coerceIn(0, size), added) }
        return MenuSpec(spec.routeKey, spec.title, spec.subtitle, spec.color, actions, page, spec.aspect, art = spec.art)
    }
}
