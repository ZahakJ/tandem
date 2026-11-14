package io.github.zahakj.tandem.data

import kotlin.math.ceil

/*
 * Android app widgets hosted among Tandem's own (ui/AppWidgets.kt): how a provider's minimum size turns into one of
 * Tandem's sizes or a span of grid cells, what a hosted widget remembers in its options, and how the picker orders
 * the providers. Pure Kotlin, so it runs in unit tests.
 */

/** Fitting a provider's size wishes (dp) to Tandem's cells. */
object AppWidgetFit {
    /**
     * How many cells of [cellDp], [gapDp] apart, it takes to cover [minDp]: the gaps between the cells count, so two
     * cells cover two tiles and the gap. Never fewer than one, never more than [max]. A couple of dp are forgiven,
     * since providers declare sizes for other launchers' cells.
     */
    fun cells(minDp: Float, cellDp: Float, gapDp: Float, max: Int): Int {
        val top = max.coerceAtLeast(1)
        if (cellDp <= 0f) return 1
        val n = ceil((minDp + gapDp - SLACK) / (cellDp + gapDp)).toInt()
        return n.coerceIn(1, top)
    }

    private const val SLACK = 2f

    /**
     * The size among [sizes] for a widget that needs [cols] x [rows] cells, given each size's [footprint] in cells: the
     * smallest that covers it. When none does (a 2 x 2 wish where the row is one cell high), the one that falls
     * shortest, covering the width first, then the smallest of those.
     */
    fun sizeFor(cols: Int, rows: Int, sizes: List<WidgetSize>, footprint: (WidgetSize) -> Pair<Int, Int>): WidgetSize {
        fun shortfall(s: WidgetSize): Int { val (w, h) = footprint(s); return (cols - w).coerceAtLeast(0) + (rows - h).coerceAtLeast(0) }
        fun widthMissed(s: WidgetSize): Int = if (footprint(s).first >= cols) 0 else 1
        fun area(s: WidgetSize): Int { val (w, h) = footprint(s); return w * h }
        return sizes.minWithOrNull(compareBy(::shortfall, ::widthMissed, ::area)) ?: WidgetSize.Medium
    }

    /** The cells a widget wanting [minW] x [minH] dp covers on the icon grid ([tileDp] tiles, [gapDp] apart): 1 or 2 each way. */
    fun spanFor(minW: Float, minH: Float, tileDp: Float, gapDp: Float): GridSpan =
        GridSpan(cells(minW, tileDp, gapDp, 2), cells(minH, tileDp, gapDp, 2))
}

/**
 * What a hosted Android widget keeps in its [Widget.options]: the id Android gave it (the host owns that id until it
 * is given back), the provider it was made from and the provider's name, so the widget can be named and its app
 * recognised even after the app is gone.
 */
object AppWidgetRef {
    /** The widget kind every hosted Android widget is ([Widget.type]). */
    const val TYPE = "android"
    const val ID = "appWidgetId"
    const val PROVIDER = "provider"
    const val LABEL = "label"

    fun options(appWidgetId: Int, provider: String, label: String): Map<String, String> =
        mapOf(ID to appWidgetId.toString(), PROVIDER to provider, LABEL to label)

    /** Android's id for [w], or null when [w] isn't a hosted widget (or was saved without one). */
    fun id(w: Widget): Int? = if (w.type == TYPE) w.options[ID]?.trim()?.toIntOrNull() else null

    /** The provider as "package/class", as saved. */
    fun provider(w: Widget): String? = w.options[PROVIDER]?.takeIf { w.type == TYPE && it.isNotBlank() }

    fun label(w: Widget): String? = w.options[LABEL]?.takeIf { w.type == TYPE && it.isNotBlank() }

    /** The package of a "package/class" provider name. */
    fun packageOf(provider: String): String = provider.substringBefore('/')
}

/** A widget provider as the picker lists it. [provider] is "package/class"; sizes are dp. */
data class AppWidgetEntry(
    val provider: String,
    val pkg: String,
    val app: String,
    val label: String,
    val minW: Int,
    val minH: Int,
    /** It has a set-up screen of its own, shown when it is added. */
    val configures: Boolean,
)

/** One app's widgets in the picker. */
data class AppWidgetGroup(val pkg: String, val app: String, val entries: List<AppWidgetEntry>)

object AppWidgetGroups {
    /** [entries] by app, apps A to Z and each app's widgets A to Z (case ignored; ties keep the given order). */
    fun group(entries: List<AppWidgetEntry>): List<AppWidgetGroup> =
        entries.groupBy { it.pkg }.map { (pkg, list) ->
            AppWidgetGroup(pkg, list.first().app, list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }))
        }.sortedWith(compareBy<AppWidgetGroup, String>(String.CASE_INSENSITIVE_ORDER) { it.app }.thenBy { it.pkg })
}
