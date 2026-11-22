package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
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
    // Getters, not lists kept: the labels are in the language of the moment.
    val sizes: List<Pair<String, String>>
        get() = listOf(
            "small" to Tx.s(R.string.home_grid_size_small), "medium" to Tx.s(R.string.home_grid_size_medium),
            "large" to Tx.s(R.string.home_grid_size_large), "huge" to Tx.s(R.string.home_grid_size_huge),
        ) + (4..8).map { n -> "cols:$n" to Tx.p(R.plurals.home_grid_size_columns, n) }
    val shapes: List<Pair<String, String>>
        get() = listOf(
            "rounded" to Tx.s(R.string.home_card_shape_rounded), "square" to Tx.s(R.string.home_card_shape_square),
            "circle" to Tx.s(R.string.home_card_shape_circle), "portrait" to Tx.s(R.string.home_card_shape_portrait),
            "landscape" to Tx.s(R.string.home_card_shape_landscape),
        )
    val tileStyles: List<Pair<String, String>>
        get() = listOf(
            "pastel" to Tx.s(R.string.home_tile_style_pastel), "solid" to Tx.s(R.string.home_tile_style_solid),
            "glass" to Tx.s(R.string.home_tile_style_glass), "mono" to Tx.s(R.string.home_tile_style_mono),
        )

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
