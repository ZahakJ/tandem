package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.Privacy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.plural
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.PlatformLibrary
import io.github.zahakj.tandem.data.Store
import java.io.File

/** Box art, or a soft gradient with the title when a game has no art yet. */
@Composable
fun GameArt(game: Game, modifier: Modifier = Modifier) {
    val art = Libraries.of(game)?.box(game)
    Box(modifier.fillMaxSize()) {
        if (art != null) {
            AsyncImage(art, game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val c = Palette.forName(game.platform.id)
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.copy(alpha = 0.55f), Palette.CardHigh))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    game.title, Modifier.padding(10.dp), style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // A theme pack's cover frame for this system (Cocoon icon overlays), when switched on in Theme packs.
        if (art != null) ThemePacks.coverFrame(game.platform.id)?.let { (_, frame) ->
            AsyncImage(frame, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        val meta = game.meta
        if (meta.favorite) Badge("♥", Palette.Peach, Modifier.align(Alignment.TopEnd))
        if (Privacy.isHidden(game)) Badge(HiddenMark, Palette.Muted, Modifier.align(Alignment.TopStart))
    }
}

/** The mark on hidden games and folders while they are revealed, and how far a hidden folder fades. */
const val HiddenMark = "⦸"
const val HiddenAlpha = 0.55f

/** The hidden mark, for tiles drawn elsewhere (the icon grid). */
@Composable
fun HiddenBadge(modifier: Modifier) = Badge(HiddenMark, Palette.Muted, modifier)

@Composable
private fun Badge(symbol: String, color: Color, modifier: Modifier) {
    Box(
        modifier.padding(6.dp).size(20.dp).clip(CircleShape).background(Palette.NightDeep.copy(alpha = 0.75f)),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = color, fontSize = 12.sp) }
}

@Composable
fun GameCard(
    game: Game,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    showTitle: Boolean = Store.settings.showTitles,
) {
    Column(modifier) {
        FocusCard(
            Modifier.fillMaxWidth().weight(1f), shape = Grid.gameShape,
            glow = Palette.forName(game.platform.id), onFocus = onFocus, entry = true, onClick = onClick,
        ) { GameArt(game) }
        if (showTitle) {
            Text(
                game.title, Modifier.fillMaxWidth().padding(top = 5.dp), style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = Palette.Muted,
            )
        }
    }
}

/**
 * A folder or collection: a pastel tile in its own colour, with a little fan of the covers inside and its name
 * in big friendly letters, so each one is easy to tell apart.
 */
@Composable
fun GroupCard(
    title: String,
    games: List<Game>,
    color: Color,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    /** The line under the name; the number of games when not given. */
    subtitle: String? = null,
    /** A theme pack's icon for this group (it carries its own name): shown instead of the drawn tile. */
    art: File? = null,
    /** A hidden folder, showing while hidden games are revealed: dimmed, with the hidden mark. */
    hidden: Boolean = false,
) {
    val faded = if (hidden) modifier.alpha(HiddenAlpha) else modifier
    if (art != null) {
        FocusCard(faded, glow = color, onFocus = onFocus, entry = true, onClick = onClick) {
            AsyncImage(art, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(
                subtitle ?: plural(games.size, "game"), Modifier.align(Alignment.BottomEnd).padding(8.dp).darkChip().padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1,
            )
            if (hidden) Badge(HiddenMark, Palette.Muted, Modifier.align(Alignment.TopStart))
        }
        return
    }
    // The tile is one piece of colour from top to bottom: a bright pastel wash behind the covers, deepening into a
    // richer shade of the same colour where the name sits. The covers keep to the top ~55% and the name sits under
    // them on the tile's own colour (never over the art): two lines at most, shrinking a little before it ellipsizes.
    val deep = androidx.compose.ui.graphics.lerp(color, Palette.Night, 0.62f)
    // Only the first three covers are needed; don't look up art for every game in a big collection.
    val covers = remember(games, Store.meta) { coversOf(games, 3) }
    FocusCard(faded, glow = color, onFocus = onFocus, entry = true, onClick = onClick) { focused ->
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0.95f), 0.45f to androidx.compose.ui.graphics.lerp(color, deep, 0.45f),
                    0.6f to deep, 1f to deep,
                ),
            ),
        )
        // A soft highlight in the top-left corner gives the tile some depth.
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = 420f)))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val inset = (w * 0.085f).coerceIn(8.dp, 16.dp)
            val titleSize = (w.value * 0.12f).coerceIn(13f, 21f)
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().weight(0.55f).padding(top = inset * 0.9f, start = inset, end = inset, bottom = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (covers.isEmpty()) {
                        GlyphIcon(Glyph.Folder, Color.White.copy(alpha = 0.85f), Modifier.fillMaxHeight(0.7f).aspectRatio(1f))
                    } else {
                        CoverFan(covers, spread = if (focused) 1.2f else 0.95f, modifier = Modifier.fillMaxSize())
                    }
                }
                Column(
                    Modifier.fillMaxWidth().weight(0.45f).padding(start = inset, end = inset * 0.7f, bottom = inset * 0.75f),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    ShrinkText(
                        title, MaterialTheme.typography.titleMedium.copy(fontSize = titleSize.sp, lineHeight = (titleSize * 1.18f).sp),
                        Modifier.fillMaxWidth().weight(1f, fill = false), maxLines = 2, color = Color.White,
                    )
                    Text(
                        subtitle ?: plural(games.size, "game"), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = if (w < 150.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.lerp(color, Color.White, 0.6f),
                    )
                }
            }
        }
        if (hidden) Badge(HiddenMark, Palette.Muted, Modifier.align(Alignment.TopStart))
    }
}

/**
 * Text that shrinks a little (down to [minScale] of [style]'s size) to fit [maxLines] lines and the height it is
 * given before it ellipsizes, and doesn't break a word in the middle while a smaller size would avoid it.
 */
@Composable
fun ShrinkText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    minScale: Float = 0.78f,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val maxH = constraints.maxHeight
        val fitted = remember(text, style, maxW, maxH, maxLines) {
            if (maxW == Constraints.Infinity) return@remember style
            val words = text.split(' ').filter { it.isNotBlank() }
            var scale = 1f
            var chosen = style
            while (true) {
                val st = style.copy(
                    fontSize = style.fontSize * scale,
                    lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
                )
                chosen = st
                val r = measurer.measure(text, st, overflow = TextOverflow.Ellipsis, maxLines = maxLines, constraints = Constraints(maxWidth = maxW))
                val wordsFit = words.all { measurer.measure(it, st, maxLines = 1).size.width <= maxW }
                if (!r.hasVisualOverflow && r.size.height <= maxH && wordsFit) break
                if (scale <= minScale) break
                scale = (scale - 0.06f).coerceAtLeast(minScale)
            }
            chosen
        }
        Text(text, style = fitted, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
    }
}

/** Box art for up to [n] of [games], stopping as soon as it has enough. */
fun coversOf(games: List<Game>, n: Int): List<File> =
    games.asSequence().mapNotNull { g -> Libraries.of(g)?.box(g) }.take(n).toList()

/** Up to three covers fanned out like a hand of cards. Fans out further while the card is highlighted. */
@Composable
fun CoverFan(covers: List<File>, spread: Float, modifier: Modifier = Modifier) {
    val angle by animateFloatAsState(spread, spring(dampingRatio = 0.5f), label = "fan")
    Box(modifier, contentAlignment = Alignment.Center) {
        val n = covers.size
        covers.forEachIndexed { i, cover ->
            val pos = i - (n - 1) / 2f
            AsyncImage(
                cover, null,
                Modifier.fillMaxHeight().aspectRatio(1f)
                    .offset(x = (pos * 22 * angle).dp, y = (kotlin.math.abs(pos) * 4).dp)
                    .rotate(pos * 9f * angle)
                    .shadow(6.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** A system on the home screen: its colour, its name and a fan of its games. */
@Composable
fun SystemCard(
    lib: PlatformLibrary,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    /** The games that may be shown (hidden ones left out). */
    games: List<Game> = Collections.visible(lib.games),
) {
    val color = Palette.forName(lib.platform.id)
    val covers = remember(games, Store.meta) { games.asSequence().mapNotNull { lib.box(it) }.take(3).toList() }
    // Tile style from Appearance: pastel gradient (the original), flat colour, glass with a colour stripe, or minimal.
    val tiles = Store.settings.look.systemTiles
    val ink = if (tiles == "pastel" || tiles == "solid") Palette.OnAccent else Palette.Text
    // A hidden system, showing while hidden games are revealed: dimmed, with the hidden mark.
    val hidden = io.github.zahakj.tandem.HiddenFolders.isHidden(lib.platform.id, null)
    FocusCard(if (hidden) modifier.alpha(HiddenAlpha) else modifier, glow = color, onFocus = onFocus, entry = true, onClick = onClick) { focused ->
        when (tiles) {
            "solid" -> Box(Modifier.fillMaxSize().background(color))
            "glass" -> {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color.copy(alpha = 0.32f), Palette.Card.copy(alpha = 0.55f)))))
                Box(Modifier.fillMaxHeight().width(6.dp).background(color))
            }
            "mono" -> {}
            else -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.35f), Palette.Card))))
        }
        // The system's own icon (the theme pack's, or drawn in the theme's icon style), never a game's or app's icon.
        // Without a pack, a little fan of the system's covers sits beside the name.
        val fan = ThemePacks.systemIcon(lib.platform.id) == null && !lib.platform.isApps && covers.isNotEmpty()
        Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(Modifier.fillMaxHeight().aspectRatio(1f)) { SystemIcon(lib.platform, maxHeight) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(lib.platform.name, style = MaterialTheme.typography.titleMedium, color = if (tiles == "mono") color else ink, maxLines = if (fan) 1 else 2, overflow = TextOverflow.Ellipsis)
                Text(plural(games.size, "game"), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f), maxLines = 1)
                if (fan) {
                    CoverFan(covers, spread = if (focused) 1.2f else 0.8f, modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 4.dp))
                }
            }
        }
        if (hidden) Badge(HiddenMark, Palette.Muted, Modifier.align(Alignment.TopEnd))
    }
}

/** A small rounded section heading, e.g. "Continue playing". */
@Composable
fun SectionTitle(text: String, color: Color = Palette.Text, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, Modifier.padding(start = 7.dp), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
