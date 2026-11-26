package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Where Settings rows go (SettingsCatalog.kt). Rows are built in Compose, so this reads the feature files instead:
 * every row a settings feature builds with a fixed label must be placed by [SettingsPlan] or dropped on purpose, so a
 * new row can't end up in a leftover pile, and nothing is placed twice. Subpages must each be reached from one row.
 */
class SettingsPlanTest {
    /** The files that build Settings rows (the feature side of [settingsCatalog]). */
    private val features = listOf(
        "Appearance", "AchievementsUi", "CocoonSetup", "ControllerSettings", "DeckPanel", "DiscordSettings", "Flair", "GameFoldersScreen", "HomeMode",
        "Perf", "PerformanceSettings", "ScrapeScreen", "ScreenOffSettings", "Settings", "SoundSettings", "SteamUi", "SystemsScreen", "Widgets",
        "SettingsCatalog", "Orbit", "SpotlightHome", "StageHome", "LanguageSettings", "SystemArtUi",
    )

    /** Rows that aren't picked by label: galleries, and groups taken whole ([SettingsPlan.ALL], the theme pack's audio). */
    private val notPicked = setOf(
        "Theme packs", "Theme pack music", "Theme pack sounds",
        "No other controllers", "Controller in games", "Always use a connected pad",
    )

    private fun ui(name: String): String {
        val dirs = listOf("src/main/java/io/github/zahakj/tandem/ui", "app/src/main/java/io/github/zahakj/tandem/ui")
        return dirs.map { File(it, "$name.kt") }.first { it.isFile }.readText()
    }

    private val rowLabel = Regex("""(?:SettingRow|toggle|choice|number)\(\s*"([^"]+)"""")

    /**
     * Rows and groups whose shown text comes from the string resources carry their English name as `en = "…"`
     * (SettingRow.en, SettingSection.en; docs/TRANSLATING.md). A group's goes on its `SettingSection(` line.
     */
    private val englishName = Regex("""\ben = "([^"]+)"""")
    private val sectionTitle = Regex("""SettingSection\(\s*"([^"]+)"""")
    private val sectionEnglish = Regex("""SettingSection\([^\n]*\ben = "([^"]+)"""")

    private fun groupsIn(src: String) = (sectionTitle.findAll(src) + sectionEnglish.findAll(src)).map { it.groupValues[1] }.toSet()

    private fun rowsIn(src: String) =
        rowLabel.findAll(src).map { it.groupValues[1] }.toSet() + (englishName.findAll(src).map { it.groupValues[1] }.toSet() - groupsIn(src))

    /** Every pick of every page and subpage, the rows opening subpages and the groups taken whole left out. */
    private val rowPicks = SettingsPlan.pages.values.flatten().flatMap { it.main }
        .filter { it.section != SettingsPlan.LINK && it.label != SettingsPlan.ALL }

    @Test fun everySettingsRowIsPlaced() {
        val placed = rowPicks.map { it.label }.toSet() + SettingsPlan.dropped.map { it.label }
        val built = features.flatMap { f -> rowsIn(ui(f)) }.toSet() - notPicked
        val missing = built - placed
        assertTrue("Rows no page places (add them to SettingsPlan, or drop them): $missing", missing.isEmpty())
    }

    @Test fun everyPickNamesAGroupThatExists() {
        val groups = features.flatMap { f -> groupsIn(ui(f)) }.toSet() +
            setOf(HomeButtonGroup, ControllersGroup, PadLinkGroup)
        val picks = SettingsPlan.pages.values.flatten().flatMap { it.main }.filter { it.section != SettingsPlan.LINK } + SettingsPlan.dropped
        val unknown = picks.map { it.section }.toSet() - groups
        assertTrue("Picks from groups no feature builds: $unknown", unknown.isEmpty())
    }

    @Test fun nothingIsPlacedTwice() {
        val picks = rowPicks + SettingsPlan.dropped
        val twice = picks.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("Placed twice: $twice", twice.isEmpty())
    }

    @Test fun sevenPagesInTileOrderThenSubpages() {
        assertEquals(SettingsCategories.ids, SettingsPlan.pages.keys.take(7))
        assertEquals(7, SettingsCategories.ids.size)
        assertEquals(SettingsPlan.subs.map { it.id }.toSet(), SettingsPlan.pages.keys.drop(7).toSet())
    }

    @Test fun everySubpageOpensFromOneRowOnItsPage() {
        for (sub in SettingsPlan.subs) {
            val links = SettingsPlan.pages.flatMap { (page, groups) -> groups.flatMap { it.main }.filter { it.section == SettingsPlan.LINK && it.label == sub.id }.map { page } }
            assertEquals("Rows opening ${sub.id}", listOf(sub.parent), links)
            assertTrue("${sub.parent} is one of the seven pages", sub.parent in SettingsCategories.ids)
            assertTrue("A key for the row opening ${sub.id}", SettingsPlan.linkKey(sub.id) != null)
        }
        val links = SettingsPlan.pages.values.flatten().flatMap { it.main }.filter { it.section == SettingsPlan.LINK }.map { it.label }
        assertTrue("Rows opening subpages that don't exist: ${links - SettingsPlan.subs.map { it.id }.toSet()}", links.all { SettingsPlan.sub(it) != null })
    }

    /** No page reads as a long list: a subpage takes a group's rarer rows instead (one row on the page). */
    @Test fun pagesStayShort() {
        for ((id, groups) in SettingsPlan.pages) {
            // The most a page can show with any one layout (layout-only rows of the others never show together).
            val most = listOf("grid", "orbit", "spotlight", "stage", "rows").maxOf { layout ->
                // About Tandem stands in for Updates in builds without the updater, so never both.
                groups.flatMap { it.main }.filter { it.label != "About Tandem" }.count { pk ->
                    when (pk.layout) {
                        null -> true; "grid" -> layout == "grid"; "nogrid" -> layout != "grid"; "orbit" -> layout == "orbit"
                        "spotlight" -> layout == "spotlight"; "stage" -> layout == "stage"; "other" -> layout == "orbit" || layout == "stage" || layout == "rows"; else -> layout == "rows"
                    }
                }
            }
            assertTrue("$id shows $most rows; keep pages to twelve at most", most <= 12)
        }
    }
}
