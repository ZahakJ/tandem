package io.github.zahakj.tandem.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.data.Store

/** Grid size, card shape and the other layout choices from Appearance, in one place for all screens. */
object Grid {
    val sizes = listOf(
        "small" to "Small", "medium" to "Medium", "large" to "Large", "huge" to "Huge",
        "cols:4" to "4 columns", "cols:5" to "5 columns", "cols:6" to "6 columns", "cols:7" to "7 columns", "cols:8" to "8 columns",
    )
    val shapes = listOf(
        "rounded" to "Rounded", "square" to "Square", "circle" to "Circle", "portrait" to "Box art (tall)", "landscape" to "Wide",
    )
    val tileStyles = listOf("pastel" to "Pastel", "solid" to "Solid colour", "glass" to "Glass", "mono" to "Minimal")

    fun label(list: List<Pair<String, String>>, id: String) = list.firstOrNull { it.first == id }?.second ?: id

    private val look get() = Store.settings.look

    /** The target width of one game card. */
    val cell: Dp
        get() = when (look.gridSize) {
            "small" -> 84.dp
            "large" -> 132.dp
            "huge" -> 164.dp
            else -> 104.dp
        } * Themes.live.iconScale

    fun cells(): GridCells = look.gridSize.removePrefix("cols:").toIntOrNull()?.let { GridCells.Fixed(it.coerceIn(2, 12)) }
        ?: GridCells.Adaptive(cell)

    /** Width / height of a game card (with room for the title underneath when titles are shown). */
    fun aspect(showTitles: Boolean): Float {
        val art = when (look.cardShape) { "portrait" -> 0.7f; "landscape" -> 1.45f; else -> 1f }
        return if (showTitles) art * 0.82f else art
    }

    /** The shape for game art cards, or the theme's standard card shape. */
    val gameShape: Shape
        get() = when (look.cardShape) {
            "circle" -> CircleShape
            "square" -> RoundedCornerShape(3.dp)
            else -> Palette.cardShape
        }

    /** A game card in a horizontal row on the home screen. */
    val homeCard: DpSize
        get() {
            val base = cell.coerceIn(88.dp, 132.dp)
            return when (look.cardShape) {
                "portrait" -> DpSize(base * 0.78f, base * 1.12f)
                "landscape" -> DpSize(base * 1.5f, base)
                else -> DpSize(base, base)
            }
        }
}
