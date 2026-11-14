package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fitting an Android widget provider's minimum size to Tandem's cells, what a hosted widget remembers, and how the
 * picker orders providers. The home row's cells are 136 x 108 dp, 14 apart; the bottom screen's about 212 x 170, 12
 * apart.
 */
class AppWidgetFitTest {
    private val sizes = WidgetSize.entries

    /** The home row's footprints (ui/Widgets.kt): Small 1 x 1, Medium 2 x 1, Wide 3 x 1, Tall 1 x 2. */
    private fun top(s: WidgetSize) = when (s) { WidgetSize.Small -> 1 to 1; WidgetSize.Medium -> 2 to 1; WidgetSize.Wide -> 3 to 1; WidgetSize.Tall -> 1 to 2 }

    /** The bottom screen's: Wide is a 2 x 2 card there. */
    private fun bottom(s: WidgetSize) = when (s) { WidgetSize.Small -> 1 to 1; WidgetSize.Medium -> 2 to 1; WidgetSize.Wide -> 2 to 2; WidgetSize.Tall -> 1 to 2 }

    @Test
    fun cellsCountTheGapsBetweenThem() {
        // Fits in one cell; a hair over still does (providers size for other launchers' cells).
        assertEquals(1, AppWidgetFit.cells(110f, 136f, 14f, 3))
        assertEquals(1, AppWidgetFit.cells(137f, 136f, 14f, 3))
        // Two cells cover two tiles and the gap between them: 286 dp.
        assertEquals(2, AppWidgetFit.cells(250f, 136f, 14f, 3))
        assertEquals(2, AppWidgetFit.cells(286f, 136f, 14f, 3))
        assertEquals(3, AppWidgetFit.cells(300f, 136f, 14f, 3))
        // Never more than the row has, never fewer than one.
        assertEquals(3, AppWidgetFit.cells(1000f, 136f, 14f, 3))
        assertEquals(1, AppWidgetFit.cells(0f, 136f, 14f, 3))
        assertEquals(1, AppWidgetFit.cells(-5f, 136f, 14f, 3))
        assertEquals(1, AppWidgetFit.cells(500f, 0f, 14f, 3))
    }

    @Test
    fun theSmallestSizeThatCoversTheWishWins() {
        assertEquals(WidgetSize.Small, AppWidgetFit.sizeFor(1, 1, sizes, ::top))
        assertEquals(WidgetSize.Medium, AppWidgetFit.sizeFor(2, 1, sizes, ::top))
        assertEquals(WidgetSize.Wide, AppWidgetFit.sizeFor(3, 1, sizes, ::top))
        assertEquals(WidgetSize.Tall, AppWidgetFit.sizeFor(1, 2, sizes, ::top))
        // On the bottom screen a 2 x 2 wish gets the big card.
        assertEquals(WidgetSize.Wide, AppWidgetFit.sizeFor(2, 2, sizes, ::bottom))
        assertEquals(WidgetSize.Tall, AppWidgetFit.sizeFor(1, 2, sizes, ::bottom))
    }

    @Test
    fun whenNothingCoversTheWishTheWidthComesFirst() {
        // The home row has no 2 x 2: the widget gets its width (Medium) and lives with one row.
        assertEquals(WidgetSize.Medium, AppWidgetFit.sizeFor(2, 2, sizes, ::top))
        // 3 x 2: Wide covers the width and falls one row short, the least of all.
        assertEquals(WidgetSize.Wide, AppWidgetFit.sizeFor(3, 2, sizes, ::top))
        // A kind offering fewer sizes picks among those.
        assertEquals(WidgetSize.Small, AppWidgetFit.sizeFor(2, 1, listOf(WidgetSize.Small, WidgetSize.Tall), ::top))
    }

    @Test
    fun gridSpansStopAtTwoCellsEachWay() {
        // 96 dp tiles, 10 apart: a 180 x 40 wish is 2 x 1, a 250 x 250 one is capped at 2 x 2, a tiny one is 1 x 1.
        assertEquals(GridSpan(2, 1), AppWidgetFit.spanFor(180f, 40f, 96f, 10f))
        assertEquals(GridSpan(2, 2), AppWidgetFit.spanFor(250f, 250f, 96f, 10f))
        assertEquals(GridSpan(1, 1), AppWidgetFit.spanFor(40f, 40f, 96f, 10f))
        assertEquals(GridSpan(1, 2), AppWidgetFit.spanFor(90f, 150f, 96f, 10f))
    }

    @Test
    fun aHostedWidgetRemembersItsIdProviderAndName() {
        val opts = AppWidgetRef.options(42, "com.weather/.Clock", "Weather clock")
        val w = Widget("android-1", AppWidgetRef.TYPE, WidgetSize.Medium, WidgetPlace.Home, opts)
        assertEquals(42, AppWidgetRef.id(w))
        assertEquals("com.weather/.Clock", AppWidgetRef.provider(w))
        assertEquals("Weather clock", AppWidgetRef.label(w))
        assertEquals("com.weather", AppWidgetRef.packageOf("com.weather/.Clock"))
        // Another kind with the same option keys is not an Android widget.
        val clock = Widget("clock-1", "clock", options = opts)
        assertNull(AppWidgetRef.id(clock))
        assertNull(AppWidgetRef.label(clock))
        // Saved without an id (or with a broken one): nothing to host.
        assertNull(AppWidgetRef.id(Widget("android-2", AppWidgetRef.TYPE)))
        assertNull(AppWidgetRef.id(Widget("android-3", AppWidgetRef.TYPE, options = mapOf(AppWidgetRef.ID to "x"))))
    }

    @Test
    fun optionsSurviveTheWidgetJson() {
        val w = Widget("android-1", AppWidgetRef.TYPE, WidgetSize.Tall, WidgetPlace.Grid, AppWidgetRef.options(7, "a/.B", "Notes"))
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val back = json.decodeFromString(Widget.serializer(), json.encodeToString(Widget.serializer(), w))
        assertEquals(w, back)
        assertEquals(7, AppWidgetRef.id(back))
    }

    @Test
    fun thePickerGroupsByAppInAlphabeticalOrder() {
        val entries = listOf(
            AppWidgetEntry("z/.B", "z", "Zebra", "beta", 100, 100, false),
            AppWidgetEntry("a/.Y", "a", "apple", "Yearly", 100, 100, true),
            AppWidgetEntry("z/.A", "z", "Zebra", "Alpha", 100, 100, false),
            AppWidgetEntry("m/.M", "m", "Mango", "Mango", 100, 100, false),
        )
        val groups = AppWidgetGroups.group(entries)
        assertEquals(listOf("apple", "Mango", "Zebra"), groups.map { it.app })
        assertEquals(listOf("Alpha", "beta"), groups.last().entries.map { it.label })
        assertEquals("a/.Y", groups.first().entries.single().provider)
        assertEquals(emptyList<AppWidgetGroup>(), AppWidgetGroups.group(emptyList()))
    }
}
