package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Where Settings rows go (SettingsCatalog.kt). Rows are built in Compose, so this reads the feature files instead:
 * every row a settings feature builds with a fixed label must be placed by [SettingsPlan] or dropped on purpose, so a
 * new row can't end up in a leftover pile, and nothing is placed twice.
 */
class SettingsPlanTest {
    /** The files that build Settings rows (the feature side of [settingsCatalog]). */
    private val features = listOf(
        "Appearance", "AchievementsUi", "CocoonSetup", "ControllerSettings", "DiscordSettings", "Flair", "HomeMode", "Perf",
        "ScrapeScreen", "Settings", "SoundSettings", "SystemsScreen", "Widgets", "SettingsCatalog",
    )

    /** Rows that aren't picked by label: galleries, and groups taken whole ([SettingsPlan.whole], the theme pack's audio, hidden). */
    private val notPicked = setOf(
        "Theme packs", "Hidden folders and games", "Theme pack music", "Theme pack sounds",
        "No other controllers", "Controller in games", "Always use a connected pad",
    )

    private fun ui(name: String): String {
        val dirs = listOf("src/main/java/io/github/zahakj/tandem/ui", "app/src/main/java/io/github/zahakj/tandem/ui")
        return dirs.map { File(it, "$name.kt") }.first { it.isFile }.readText()
    }

    private val rowLabel = Regex("""(?:SettingRow|toggle|choice|number)\(\s*"([^"]+)"""")

    @Test fun everySettingsRowIsPlaced() {
        val placed = SettingsPlan.pages.values.flatten().flatMap { it.main + it.more }.map { it.label }.toSet() +
            SettingsPlan.dropped.map { it.label }
        val built = features.flatMap { f -> rowLabel.findAll(ui(f)).map { it.groupValues[1] }.toList() }.toSet() - notPicked
        val missing = built - placed
        assertTrue("Rows no page places (add them to SettingsPlan, or drop them): $missing", missing.isEmpty())
    }

    @Test fun everyPickNamesAGroupThatExists() {
        val groups = features.flatMap { f -> Regex("""SettingSection\(\s*"([^"]+)"""").findAll(ui(f)).map { it.groupValues[1] }.toList() }.toSet() +
            setOf(HomeButtonGroup, ControllersGroup, PadLinkGroup)
        val picks = SettingsPlan.pages.values.flatten().flatMap { it.main + it.more } + SettingsPlan.dropped
        val unknown = picks.map { it.section }.toSet() - groups
        assertTrue("Picks from groups no feature builds: $unknown", unknown.isEmpty())
    }

    @Test fun nothingIsPlacedTwice() {
        val picks = SettingsPlan.pages.values.flatten().flatMap { it.main + it.more } + SettingsPlan.dropped
        val twice = picks.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("Placed twice: $twice", twice.isEmpty())
    }

    @Test fun sevenPagesInTileOrder() {
        assertEquals(SettingsCategories.ids, SettingsPlan.pages.keys.toList())
        assertEquals(7, SettingsCategories.ids.size)
    }
}
