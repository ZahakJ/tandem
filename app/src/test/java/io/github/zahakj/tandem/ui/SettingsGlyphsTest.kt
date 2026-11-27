package io.github.zahakj.tandem.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every Settings row has a glyph of its own on its page ([SettingsPlan.glyphs]): read the way SettingsPlanTest reads
 * the plan, a page as it shows with each home layout (rows of the other layouts never show beside it).
 */
class SettingsGlyphsTest {
    private val layouts = listOf("grid", "orbit", "spotlight", "stage", "rows")

    private fun shows(layout: String?, home: String) = when (layout) {
        null -> true; "grid" -> home == "grid"; "nogrid" -> home != "grid"; "orbit" -> home == "orbit"
        "spotlight" -> home == "spotlight"; "stage" -> home == "stage"; "other" -> home != "grid" && home != "spotlight"
        else -> home == "rows"
    }

    /** Rows of groups a page takes whole, when the plan knows them (the rest are connected controllers). */
    private val whole = mapOf(
        PadLinkGroup to listOf("Controller in games", "Always use a connected pad"),
        "Theme pack" to listOf("Theme pack music", "Theme pack sounds"),
    )

    /** Every row a page shows with home layout [home], as (label, glyph). */
    private fun rows(page: String, home: String): List<Pair<String, Glyph?>> {
        val picks = SettingsPlan.pages.getValue(page).flatMap { it.main }.filter { shows(it.layout, home) }
        val lead = if (page == "sound") whole.getValue("Theme pack").map { it to SettingsPlan.glyph("Theme pack", it) } else emptyList()
        return lead + picks.flatMap { pk ->
            when {
                pk.section == SettingsPlan.LINK -> listOf(pk.label to SettingsPlan.sub(pk.label)?.icon)
                pk.label == SettingsPlan.ALL -> whole[pk.section].orEmpty().map { it to SettingsPlan.glyph(pk.section, it) }
                else -> listOf(pk.label to SettingsPlan.glyph(pk.section, pk.label))
            }
        }
    }

    @Test fun everyRowHasAGlyph() {
        val missing = SettingsPlan.pages.keys.flatMap { page -> layouts.flatMap { rows(page, it) } }.filter { it.second == null }.map { it.first }.toSet()
        assertTrue("Rows without a glyph in SettingsPlan.glyphs: $missing", missing.isEmpty())
    }

    @Test fun noTwoRowsOnAPageShareAGlyph() {
        val clashes = SettingsPlan.pages.keys.flatMap { page ->
            layouts.flatMap { home ->
                rows(page, home).filter { it.second != null }.groupBy { it.second }.filter { it.value.size > 1 }
                    .map { (g, rs) -> "$page: $g on ${rs.map { it.first }}" }
            }
        }.toSet()
        assertTrue("Rows sharing a glyph on one page:\n" + clashes.joinToString("\n"), clashes.isEmpty())
    }

    @Test fun everyGlyphInThePlanIsUsedByARow() {
        val labels = SettingsPlan.pages.values.flatten().flatMap { it.main }.map { it.label }.toSet() + whole.values.flatten()
        val stale = SettingsPlan.glyphs.keys.filter { it.substringAfterLast('/') !in labels }
        assertTrue("Glyphs for rows no page has: $stale", stale.isEmpty())
    }
}
