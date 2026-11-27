package io.github.zahakj.tandem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Screensaver
import io.github.zahakj.tandem.data.Store

/** Rows the wall has; each drifts the other way from the one above it. */
private const val Rows = 3

/** One row's full turn (its covers once across), in ms: slow enough to read the covers going by. */
private const val TurnMs = 90_000

/**
 * A folder's games as a wall of their covers, tilted, in rows drifting past in turn (FolderStyle.HERO_WALL): what kind
 * of games are inside, at a glance. Tinted towards [color] so the folder's icon and name read over it. Still when not
 * [moving]. Each row is its covers twice over, slid by one set's width, so it never runs out.
 *
 * It runs on its own steady clock at 20 fps, not the ambient one: that stops while a folder panel is open, which is
 * just when a folder's folders are highlighted. It still rests with everything else, in the background or after a
 * few idle minutes.
 */
@Composable
fun CoverWall(games: List<Game>, color: Color, modifier: Modifier = Modifier, moving: Boolean = true) {
    val covers = remember(games) {
        FolderLooks.strongGames(games).asSequence().mapNotNull { g -> Libraries.of(g)?.box(g) }.distinct().take(36).toList()
    }
    val clock = rememberSteadyClock(moving && !Store.settings.reduceMotion && rememberResumed() && !Screensaver.active && !Ambient.settled, fps = 20)
    BoxWithConstraints(modifier.clipToBounds().background(lerp(color, Palette.Night, 0.7f))) {
        if (covers.isNotEmpty()) {
            // Tilted, the wall needs to reach past the corners.
            val cell = maxHeight * 1.3f / Rows - 12.dp
            val gap = 12.dp
            val perRow = maxOf(((maxWidth * 1.7f) / (cell + gap)).toInt() + 1, (covers.size + Rows - 1) / Rows)
            val px = with(androidx.compose.ui.platform.LocalDensity.current) { (cell + gap).toPx() }
            val req = tilePx(cell)
            Column(
                Modifier.align(Alignment.Center).requiredWidth(maxWidth * 1.5f).graphicsLayer { rotationZ = -8f },
                verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for (r in 0 until Rows) {
                    val row = List(perRow) { i -> covers[(r * perRow + i * (Rows + 1) + r) % covers.size] }
                    val span = perRow * px
                    val dir = if (r % 2 == 0) -1f else 1f
                    Row(
                        Modifier.requiredWidth((cell + gap) * perRow * 2).graphicsLayer {
                            val t = clock.loop(TurnMs, offset = r * 0.37f)
                            translationX = dir * t * span + (if (dir > 0) -span / 2f else span / 2f)
                        },
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        for (f in row + row) {
                            AsyncImage(
                                rememberSizedRequest(f, req), null,
                                Modifier.size(cell).clip(RoundedCornerShape(cell * 0.08f)), contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
            }
        }
        // The folder's colour over it, deepest where the icon and name card sit.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    0f to lerp(color, Palette.Night, 0.55f).copy(alpha = 0.82f),
                    0.7f to lerp(color, Palette.Night, 0.45f).copy(alpha = 0.55f),
                    1f to lerp(color, Palette.Night, 0.3f).copy(alpha = 0.35f),
                ),
            ),
        )
    }
}
