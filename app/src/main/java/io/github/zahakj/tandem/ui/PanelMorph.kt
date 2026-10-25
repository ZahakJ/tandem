package io.github.zahakj.tandem.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.zahakj.tandem.data.Store

/*
 * A popup that grows out of the tile it was opened from, like a shared element: the panel's rounded outline
 * stretches from the tile's place on screen to its own, its content scales up from the tile and fades in, and the
 * tile's own art zooms into the panel as it fades away. Closing runs it backwards, back into the tile. Drawn in
 * graphics layers from one progress value, so nothing recomposes per frame. Used by OverlayPanel (Overlays.kt),
 * which skips it with Reduce motion.
 */

/** Where a popup grows from: [from] is the tile's bounds in the window (null when it isn't on screen). */
class PanelMorph(
    val from: () -> Rect?,
    /** The tile's picture, shown zooming into the panel at the start. */
    val art: TileArt?,
    val color: Color,
    /** The tile's corner as a share of its side. */
    val cornerShare: Float,
)

/** A morph from the icon grid's home tile [key], or null when that tile isn't on screen. */
fun tileMorph(key: String?, art: TileArt?, color: Color): PanelMorph? {
    if (key == null) return null
    SwapFx.tiles[key]?.takeIf { it.isAttached } ?: return null
    return PanelMorph(
        from = { SwapFx.tiles[key]?.takeIf { it.isAttached }?.boundsInWindow() },
        art = art, color = color,
        cornerShare = Store.settings.look.gridRoundness.coerceIn(0f, 0.5f),
    )
}

/** The morph worked out when a popup opens ([visible] turns true), kept while it closes. */
@Composable
fun rememberMorph(visible: Boolean, make: () -> PanelMorph?): PanelMorph? {
    val last = remember { arrayOfNulls<PanelMorph>(1) }
    return remember(visible) { if (visible) make().also { last[0] = it } else last[0] }
}

private class MorphShape(val rect: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(lerpF(a.left, b.left, t), lerpF(a.top, b.top, t), lerpF(a.right, b.right, t), lerpF(a.bottom, b.bottom, t))

/** The panel at [progress] (0 at the tile, 1 in place); [content] fills it. */
@Composable
fun MorphBox(morph: PanelMorph, progress: () -> Float, content: @Composable () -> Unit) {
    var self by remember { mutableStateOf<Rect?>(null) }
    // Where the tile was when the popup opened; looked up again as it closes, in case the grid moved meanwhile.
    var origin by remember { mutableStateOf(morph.from()) }
    val closing by remember { derivedStateOf { progress() < 0.98f } }
    if (closing) morph.from()?.let { if (it != origin) origin = it }
    val density = LocalDensity.current
    val panelCorner = with(density) { 30.dp.toPx() }
    val shadow = with(density) { 24.dp.toPx() }
    // The tile's rect inside this box, or (no tile on screen) a smaller copy of the panel in its middle.
    fun startRect(): Rect? {
        val me = self ?: return null
        val o = origin ?: return Rect(me.width * 0.08f, me.height * 0.08f, me.width * 0.92f, me.height * 0.92f)
        return o.translate(-me.left, -me.top)
    }
    val tileSide = origin?.width ?: 0f
    val showArt by remember { derivedStateOf { progress() < 0.6f } }
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { val b = it.boundsInWindow(); if (b != self) self = b }
            .graphicsLayer {
                val q = progress().coerceIn(0f, 1f)
                val start = startRect()
                if (start == null || q >= 0.999f) {
                    clip = false
                    shadowElevation = 0f
                    alpha = if (start == null) 0f else 1f
                    return@graphicsLayer
                }
                val full = Rect(0f, 0f, size.width, size.height)
                val r = lerpRect(start, full, q)
                shape = MorphShape(r, lerpF(start.width * morph.cornerShare, panelCorner, q))
                clip = true
                // The panel's own shadow is clipped while it grows; this one stands in for it.
                shadowElevation = shadow * q
                alpha = 1f
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val q = progress().coerceIn(0f, 1f)
                val start = startRect() ?: return@graphicsLayer
                if (q >= 0.999f) { alpha = 1f; scaleX = 1f; scaleY = 1f; translationX = 0f; translationY = 0f; return@graphicsLayer }
                val r = lerpRect(start, Rect(0f, 0f, size.width, size.height), q)
                // Scaled to cover the outline (which crops it), centred on it.
                val s = maxOf(r.width / size.width, r.height / size.height).coerceAtMost(1.02f)
                scaleX = s; scaleY = s
                translationX = r.center.x - size.width / 2f
                translationY = r.center.y - size.height / 2f
                alpha = ((q - 0.12f) / 0.45f).coerceIn(0f, 1f)
            },
        ) { content() }
        val art = morph.art
        if (art != null && showArt && tileSide > 0f) {
            val sideDp = with(density) { tileSide.toDp() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                Box(
                    Modifier.requiredSize(sideDp).graphicsLayer {
                        val q = progress().coerceIn(0f, 1f)
                        val start = startRect() ?: return@graphicsLayer
                        val me = self ?: return@graphicsLayer
                        val r = lerpRect(start, Rect(0f, 0f, me.width, me.height), q)
                        val s = maxOf(r.width, r.height) / tileSide
                        scaleX = s; scaleY = s
                        translationX = r.center.x - tileSide / 2f
                        translationY = r.center.y - tileSide / 2f
                        alpha = (1f - q / 0.5f).coerceIn(0f, 1f)
                    },
                ) { TileArtView(art, sideDp) }
            }
        }
    }
}
