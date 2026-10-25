package io.github.zahakj.tandem.ui

import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Collections
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Privacy
import io.github.zahakj.tandem.RunningGames
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.plural
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

/*
 * Spotlight: the other screen in icon grid mode. The highlighted thing big and centred
 * over a sweep of its own colour (or its icon pack hero), with a rounded name card under it; a game fills the
 * screen with its art and logo. A small status pill sits top right and the buttons bottom right. Changes
 * cross-fade, with the background sliding a little further than the front for a touch of depth.
 */

object Spotlight {
    /** Spotlight is used in icon grid mode (unless the classic details were chosen) for games, systems and folders. */
    fun wanted(s: Selected): Boolean =
        IconGrid.enabled && Store.settings.look.gridDetails == "spotlight" &&
            (s is Selected.GameItem || s is Selected.System || s is Selected.Group || s is Selected.App)
}

/** What the spotlight shows. */
private class Spot(
    val key: String,
    val title: String,
    val type: String,
    val detail: String,
    val color: Color,
    val art: TileArt?,
    val hero: File?,
    val logo: File?,
    val game: Game? = null,
    val games: List<Game> = emptyList(),
    /** A folder of games without an icon of its own: shown as a card with a fan of covers. */
    val coverCard: Boolean = false,
    /** A line about it under the name ("Games you've hearted"). */
    val blurb: String = "",
    /** An app in the dock (its package, or "tandem:…"), shown by AppDetails. */
    val app: String? = null,
    /** An empty spot on the icon grid: a calm card saying what A and Y do. */
    val empty: Boolean = false,
    /** A designed folder's look (FolderLooks.kt): its shape and text title. */
    val style: io.github.zahakj.tandem.data.FolderStyle? = null,
    /** Drawn big and faint in the colour sweep when there is no hero (built-in and glyph folders). */
    val glyph: TIcon? = null,
)

private fun spotFor(s: Selected): Spot? {
    // A tile from the icon grid knows what it is and its own art. Matched by value as well as identity (the grid is
    // rebuilt when the library or arrangement changes), and looked up in the grid's catalog when the last focused
    // tile isn't it, so a built-in app or an app never falls through to the generic folder look below.
    val tile = IconGrid.spot?.takeIf { it.selected === s || it.selected == s } ?: IconGrid.menuData?.first?.find(s)
    tile?.let { item ->
        if (item.kind == TileKind.Empty) return Spot(item.key, item.title, "", "", item.color, null, null, null, blurb = item.blurb, empty = true)
        val g = item.game
        if (g != null) return Spot(item.key, g.title, item.type, g.platform.name, item.color, item.art, null, null, game = g)
        // The designed look decides the hero and logo; built-in folders get their designed sweep, never an app's art.
        return Spot(
            item.key, item.title, item.type, item.detail, item.color, item.art, FolderLooks.hero(item), FolderLooks.logo(item),
            games = item.games, blurb = item.blurb, style = item.style, glyph = FolderLooks.sweepGlyph(item),
        )
    }
    return when (s) {
        is Selected.GameItem -> Spot("g:${s.game.key}", s.game.title, "Game", s.game.platform.name, SystemColors.of(s.game.platform.id), null, null, null, game = s.game)
        is Selected.System -> {
            val p = s.library.platform
            val color = SystemColors.of(p.id)
            val games = Collections.visible(s.library.games)
            Spot(
                "s:${p.id}", p.name, "System", plural(games.size, "game"), color,
                IconPacks.systemIcon(p.id)?.let { TileArt.Picture(it) } ?: TileArt.Monogram(p.shortName, color),
                IconPacks.systemHero(p.id), IconPacks.systemLogo(p.id), games = games,
            )
        }
        // Anything else that is a group (a subfolder, a genre, an app in the drawer): named for what it is.
        is Selected.Group -> {
            val type = when (val t = s.target) {
                is io.github.zahakj.tandem.GroupTarget.System -> "Folder"
                is io.github.zahakj.tandem.GroupTarget.Collection -> if (t.id.startsWith("genre:")) "Genre" else "Collection"
                io.github.zahakj.tandem.GroupTarget.Genres -> "Collection"
                null -> if (s.games.isNotEmpty()) "Folder" else ""
            }
            Spot(
                "grp:${s.title}:${s.subtitle}", s.title, type, s.subtitle, s.color, null, null, null, games = s.games, coverCard = true,
            )
        }
        is Selected.App -> Spot("app:${s.pkg}", s.pkg, "App", "", appTint(s.pkg), null, null, null, app = s.pkg)
        else -> null
    }
}

/** How long an arriving item's logo, icon and words wait, so the leaving item's are gone first. */
private const val FrontGapMs = 120

/** [onStatusPill] hears how far down the status pill reaches, so the now-playing banner can sit below it. */
@Composable
fun SpotlightView(selected: Selected, onStatusPill: (Dp) -> Unit = {}) {
    val meta = Store.meta
    val pack = IconPacks.version
    val spot = remember(selected, IconGrid.spot, meta, pack, Privacy.view) { spotFor(selected) } ?: return
    // No backdrop of its own: the wallpaper shows wherever the item's picture leaves room, and through the dip.
    Box(Modifier.fillMaxSize().clipToBounds()) {
        AnimatedContent(
            spot, Modifier.fillMaxSize(), contentKey = { it.key },
            // Two pictures are never on screen together: the old one is gone (110 ms) before the new one fades in.
            transitionSpec = {
                if (Motion.style == AnimationStyle.Off) fadeIn(snap()) togetherWith fadeOut(snap())
                else (fadeIn(tween(200, delayMillis = 100)) togetherWith fadeOut(tween(110))).apply { targetContentZIndex = 1f }
            },
            label = "spotlight",
        ) { s ->
            val still = Motion.style == AnimationStyle.Off
            // -1 arriving, 0 shown, 1 leaving: the background moves further than the front.
            val shift = transition.animateFloat({ tween(360) }, label = "parallax") { st ->
                if (still) 0f else when (st) { EnterExitState.PreEnter -> 1f; EnterExitState.Visible -> 0f; EnterExitState.PostExit -> -1f }
            }
            // Logos, icons and words never overlap: the leaving item's go first (120 ms) and the arriving item's only
            // come in once they're gone, while the two backgrounds crossfade underneath.
            val front = transition.animateFloat(
                {
                    when {
                        still -> snap()
                        targetState == EnterExitState.Visible -> tween(200, delayMillis = FrontGapMs)
                        else -> tween(120)
                    }
                },
                label = "front",
            ) { st -> if (st == EnterExitState.Visible) 1f else 0f }
            when {
                s.game != null -> GameSpot(s, s.game, shift::value, front::value)
                s.empty -> EmptySpotCard(front::value)
                // Below the status pill (and the now-playing banner under it), like the icon in ItemSpot.
                s.app != null -> AppDetails(s.app, chrome = false, shift = shift::value, topClear = 14.dp + 34.dp + 6.dp + bannerClear() - 22.dp, front = front::value)
                else -> ItemSpot(s, shift::value, front::value)
            }
        }
        val slot = LocalScreenSlot.current
        val density = LocalDensity.current
        val px = density.density
        StatusPill(
            Modifier.align(Alignment.TopEnd).padding(14.dp).onSizeChanged { with(density) { onStatusPill(14.dp + it.height.toDp()) } }
                .swapArrive(slot, 250f, 0f, -SwapFx.flow(slot) * 14f * px),
        )
        SpotHints(spot, Modifier.align(Alignment.BottomEnd).padding(14.dp).swapArrive(slot, 280f, 0f, 14f * px))
    }
}

/** A system, folder, collection or app: its colour sweep (or pack hero), the big icon and its name card. */
@Composable
private fun ItemSpot(s: Spot, shift: () -> Float, front: () -> Float) {
    val slot = LocalScreenSlot.current
    val px = LocalDensity.current.density
    val flow = SwapFx.flow(slot)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = minOf(maxWidth, maxHeight)
        val landscape = maxWidth > maxHeight * 1.2f
        val cardMax = maxWidth * 0.72f
        Box(Modifier.fillMaxSize().swapSweep(slot).parallaxFill(shift, 0.14f)) {
            if (s.hero != null) {
                KenBurns(rememberSizedRequest(s.hero, 1280), Modifier.fillMaxSize(), moving = Store.settings.look.kenBurns && Flair.floating)
            } else {
                ColorSweep(s.color, Modifier.fillMaxSize().drift(s.key.hashCode(), 14.dp).glyphEcho(s.glyph))
            }
        }
        // Clear of the status pill and the now-playing banner under it (top) and the button hints (bottom), with the
        // name card's height taken off first, so the icon shrinks before anything overlaps.
        val top = 14.dp + 34.dp + 6.dp + bannerClear()
        val bottom = 14.dp + SpotHintsHeight + 8.dp
        val nameH = if (s.blurb.isNotBlank()) 118.dp else 80.dp
        val roomForArt = (maxHeight - top - bottom - nameH - 18.dp).coerceAtLeast(80.dp)
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = top, bottom = bottom).graphicsLayer { translationX = shift() * size.width * 0.05f; alpha = front() },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            val wanted = side * if (landscape) 0.46f else 0.5f
            val big = minOf(wanted, if (s.coverCard || s.art == null) roomForArt / 1.12f else roomForArt)
            val textTitle = s.style?.logo == io.github.zahakj.tandem.data.FolderStyle.LOGO_TEXT
            when {
                textTitle -> FolderTitle(
                    s.title, s.style ?: io.github.zahakj.tandem.data.FolderStyle.DEFAULT,
                    Modifier.swapArrive(slot, 110f, 0f, -flow * 40f * px, 0.5f, 120f).riseIn(s.key, 0).floating(s.key.hashCode()).widthIn(max = cardMax).heightIn(max = big),
                )
                s.hero != null && s.logo != null -> AsyncImage(
                    rememberSizedRequest(s.logo, 900), s.title,
                    Modifier.swapArrive(slot, 110f, 0f, -flow * 40f * px, 0.5f, 120f).riseIn(s.key, 0).floating(s.key.hashCode()).fillMaxWidth(Store.settings.look.heroFolderLogoScale.takeIf { it > 0f } ?: 0.72f).heightIn(max = big * 0.7f), contentScale = ContentScale.Fit,
                )
                s.coverCard || s.art == null -> Box(Modifier.swapArrive(slot, 110f, 0f, -flow * 40f * px, 0.5f, 120f).riseIn(s.key, 0).floating(s.key.hashCode(), bob = 5.dp)) { CoverCard(s, big * 1.12f) }
                else -> Box(Modifier.swapArrive(slot, 110f, 0f, -flow * 40f * px, 0.5f, 120f).riseIn(s.key, 0).floating(s.key.hashCode())) { BigIcon(s.art, s.color, big, s.style) }
            }
            Spacer(Modifier.height(18.dp))
            NameCard(s.title, listOf(s.type, s.detail).filter { it.isNotBlank() }.joinToString(" · "), Modifier.widthIn(min = 220.dp, max = cardMax).swapArrive(slot, 200f, 0f, 36f * px, 0.96f).riseIn(s.key, 1, 28.dp), s.blurb)
        }
    }
}

/**
 * An empty spot on the grid: no colour sweep, only the wallpaper, a small soft "+" and a quiet line saying what A
 * does there. The same for every spot, so moving between them changes nothing here.
 */
@Composable
private fun EmptySpotCard(front: () -> Float) {
    val carrying = IconGrid.moving?.carried == true
    val line = Palette.Text
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top = 14.dp + 34.dp + 6.dp + bannerClear()
        val bottom = 14.dp + SpotHintsHeight + 8.dp
        val cardMax = maxWidth * 0.8f
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = top, bottom = bottom).graphicsLayer { alpha = front() },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(44.dp).drawWithCache {
                    val w = 2.dp.toPx()
                    val arm = size.minDimension * 0.32f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    onDrawBehind {
                        drawCircle(line, radius = size.minDimension / 2f, alpha = 0.06f)
                        drawLine(line.copy(alpha = 0.45f), c - Offset(arm, 0f), c + Offset(arm, 0f), w, androidx.compose.ui.graphics.StrokeCap.Round)
                        drawLine(line.copy(alpha = 0.45f), c - Offset(0f, arm), c + Offset(0f, arm), w, androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                },
            )
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.widthIn(max = cardMax).background(Palette.Card.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Empty spot", style = MaterialTheme.typography.titleMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (carrying) "A pastes the tile you're carrying here" else "A adds a folder, game, app or widget here",
                    style = MaterialTheme.typography.bodySmall, color = Palette.Muted.copy(alpha = 0.8f), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}


/**
 * A game, like a title card: its art covering the whole screen (the best picture for this shape, slowly moving),
 * and in the lower left its animated logo (or its name), a row of chips and a few lines introducing it. The art fades
 * into the system's deepened colour towards that corner, so the words read on any picture without a dark band. Each
 * piece arrives in turn: the art, the logo popping in, then the chips and the words rising after it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameSpot(s: Spot, game: Game, shift: () -> Float, front: () -> Float) {
    val slot = LocalScreenSlot.current
    val px = LocalDensity.current.density
    val resumable = RunningGames.list.any { it.game?.key == game.key }
    val meta = game.meta
    val scope = rememberCoroutineScope()
    // What the art fades into: the system's own colour, deepened. It comes up with the picture, so until the picture
    // is in, the last game's art stays in view instead of a block of colour.
    val under = remember(game.key) { Animatable(0f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight * 1.3f
        Box(Modifier.fillMaxSize().swapSweep(slot).graphicsLayer { alpha = under.value }.background(lerp(s.color, Palette.Night, 0.84f)))
        // Once the art and the colour under it are in, nothing of the wallpaper shows: a video there holds its frame.
        val opaque by remember(game.key) { androidx.compose.runtime.derivedStateOf { under.value >= 0.999f } }
        WallpaperCover.Cover(slot, opaque)
        HeroArt(
            game, Modifier.fillMaxSize().swapSweep(slot).parallaxFill(shift, 0.1f).introFade(wide),
            onShown = { scope.launch { under.animateTo(1f, tween(160)) } },
        )
        // Below the status pill (and the now-playing banner under it), above the button hints.
        val top = 14.dp + 34.dp + 6.dp + bannerClear()
        val bottom = 14.dp + SpotHintsHeight + 10.dp
        val textW = if (wide) minOf(maxWidth * 0.58f, 540.dp) else maxWidth - 44.dp
        val logoBox = maxHeight - top - bottom - (if (wide) 150.dp else 190.dp)
        Column(
            Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = top, bottom = bottom)
                .graphicsLayer { translationX = shift() * size.width * 0.04f; alpha = front() }
                .swapArrive(slot, 200f, 0f, 36f * px, 0.96f),
            verticalArrangement = Arrangement.Bottom,
        ) {
            AnimatedLogo(
                game, maxWidth = if (wide) minOf(textW * 0.8f, 400.dp) else minOf(textW * 0.85f, 360.dp),
                maxHeight = logoBox.coerceIn(70.dp, if (wide) 150.dp else 130.dp), entranceDelayMs = 120,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(
                Modifier.widthIn(max = textW).riseIn(game.key, delayMs = 300, distance = 14.dp, fromScale = 0.96f),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill(game.platform.shortName, lerp(s.color, Color.White, 0.35f), Modifier.darkChip())
                meta.year?.let { Pill("$it", Palette.Butter, Modifier.darkChip()) }
                meta.genres.firstOrNull()?.let { Pill(Collections.tidyGenre(it), Palette.Lavender, Modifier.darkChip()) }
                if (resumable) Pill("● Running", Palette.Mint, Modifier.darkChip())
                else if (meta.playSeconds >= 60) Pill(formatPlayTime(meta.playSeconds), Palette.Mint, Modifier.darkChip())
                if (meta.favorite) Pill("♥", Palette.Peach, Modifier.darkChip())
                GameExtrasBadges(game, Modifier.darkChip())
            }
            val credits = remember(meta) { creditsLine(meta) }
            if (credits != null) {
                Text(
                    credits, Modifier.widthIn(max = textW).padding(top = 8.dp).riseIn(game.key, delayMs = 360, distance = 12.dp, fromScale = 0.97f),
                    style = MaterialTheme.typography.labelLarge.onArt(), color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val intro = remember(meta.description) { meta.description?.replace(Spaces, " ")?.trim()?.takeIf { it.isNotEmpty() } }
            if (intro != null) {
                Text(
                    intro, Modifier.widthIn(max = textW).padding(top = 6.dp).riseIn(game.key, delayMs = 420, distance = 12.dp, fromScale = 0.97f),
                    style = MaterialTheme.typography.bodyLarge.onArt(), color = Color.White.copy(alpha = 0.92f),
                    maxLines = if (wide) 3 else 4, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val Spaces = Regex("""\s+""")

/**
 * Fades the art into what is behind it towards the lower left, where the logo and the words sit: on a wide screen a
 * soft corner (the top and the right stay bright), on a narrow one along the bottom.
 */
private fun Modifier.introFade(wide: Boolean): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val brush = if (wide) {
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.3f), 0.45f to Color.Black.copy(alpha = 0.55f), 0.8f to Color.Black.copy(alpha = 0.92f), 1f to Color.Black,
                center = Offset(0f, size.height), radius = maxOf(size.width * 0.82f, size.height * 1.2f),
            )
        } else {
            Brush.verticalGradient(0f to Color.Black, 0.38f to Color.Black, 0.75f to Color.Black.copy(alpha = 0.5f), 1f to Color.Black.copy(alpha = 0.32f))
        }
        drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
    }

/**
 * The item's colour swept in from the right in three soft bands, with a faint dot grid on the left (drawn once per
 * size and colour; nothing is built per frame).
 */
@Composable
fun ColorSweep(color: Color, modifier: Modifier = Modifier) {
    val dotGap = with(LocalDensity.current) { 16.dp.toPx() }
    val dotR = with(LocalDensity.current) { 1.3.dp.toPx() }
    Box(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            fun band(dx: Float): Path = Path().apply {
                moveTo(w * (0.6f + dx), 0f)
                cubicTo(w * (0.36f + dx), h * 0.3f, w * (0.74f + dx), h * 0.58f, w * (0.5f + dx), h)
                lineTo(w, h); lineTo(w, 0f); close()
            }
            val b1 = band(0f)
            val b2 = band(0.12f)
            val b3 = band(0.24f)
            val c1 = lerp(color, Color.Black, 0.08f)
            val c2 = lerp(color, Color.White, 0.16f)
            val c3 = lerp(color, Color.White, 0.32f)
            val dots = Path()
            var y = dotGap
            while (y < h) {
                var x = dotGap
                while (x < w * 0.5f) {
                    dots.addOval(androidx.compose.ui.geometry.Rect(Offset(x, y), dotR))
                    x += dotGap
                }
                y += dotGap
            }
            val dotBrush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), center = Offset(w * 0.22f, h * 0.35f), radius = w * 0.4f,
            )
            onDrawBehind {
                drawPath(dots, dotBrush)
                drawPath(b1, c1)
                drawPath(b2, c2.copy(alpha = 0.9f))
                drawPath(b3, c3.copy(alpha = 0.85f))
            }
        },
    )
}

/** The icon, big, with a soft shadow in its own colour. */
@Composable
private fun BigIcon(art: TileArt, color: Color, size: Dp, style: io.github.zahakj.tandem.data.FolderStyle? = null) {
    val shape = FolderLooks.shape(style, size, size * Store.settings.look.gridRoundness.coerceIn(0.08f, 0.5f))
    Box(
        Modifier.size(size).graphicsLayer {
            this.shape = shape; clip = true; shadowElevation = 22f * density
            spotShadowColor = Color.Black; ambientShadowColor = color
        },
    ) { TileArtView(art, size) }
}

/** A folder of games: its colour and a fan of covers, as one big rounded card (its name is on the card below). */
@Composable
private fun CoverCard(s: Spot, size: Dp) {
    val covers = remember(s.key, Store.meta) { coversOf(s.games, 3) }
    val deep = lerp(s.color, Palette.Night, 0.5f)
    Box(
        Modifier.size(size).graphicsLayer { shape = RoundedCornerShape(size * 0.16f); clip = true; shadowElevation = 20f * density }
            .background(Brush.verticalGradient(0f to lerp(s.color, Color.White, 0.12f), 1f to lerp(s.color, deep, 0.7f))),
    ) {
        if (covers.isEmpty()) {
            // No covers to fan out: its initials on its colour (never a bare folder symbol).
            val initials = s.title.split(' ', '-', '_').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
            TileArtView(TileArt.Monogram(initials, s.color), size)
        } else {
            // Art only: the name and count are on the name card under it, once.
            CoverFan(covers, spread = 1.3f, modifier = Modifier.fillMaxSize().padding(horizontal = size * 0.16f, vertical = size * 0.2f))
        }
    }
}

/** The rounded name card under the icon. */
@Composable
private fun NameCard(title: String, detail: String, modifier: Modifier, blurb: String = "") {
    Column(
        modifier.graphicsLayer { shape = RoundedCornerShape(22.dp); clip = true; shadowElevation = 12f * density }
            .background(Palette.Card.copy(alpha = 0.94f)).padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.labelLarge, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (blurb.isNotBlank()) Text(blurb, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Text.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Battery and time, in a small pill. */
@Composable
private fun StatusPill(modifier: Modifier) {
    val ctx = LocalContext.current
    val now by rememberMinute()
    val battery by produceState(-1) {
        while (true) {
            value = ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(60_000)
        }
    }
    val look = Store.settings.look
    val time = Clock.format(now)
    // Pip always shows, even with no battery or clock.
    Row(
        modifier.background(Palette.Card.copy(alpha = 0.9f), RoundedCornerShape(50)).padding(start = 9.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Tandem's logo: Pip, blinking and glancing the way you move.
        Pip(30.dp, bounce = false)
        if (battery >= 0 && look.battery) {
            BatteryIcon(battery, if (battery <= 20) Palette.Peach else Palette.Mint, Modifier.size(18.dp))
            Text("$battery%", style = MaterialTheme.typography.titleSmall, color = if (battery <= 20) Palette.Peach else Palette.Mint, maxLines = 1)
        }
        if (time != null) Text(time, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/** Room under the status pill for the now-playing banner while it shows (it does for anything but a game). */
private fun bannerClear(): Dp = if (nowPlaying() != null) NowPlayingBannerHeight + 6.dp else 0.dp

/** The height of the button hints bar. */
private val SpotHintsHeight = 36.dp

/**
 * Slides a full-screen background layer sideways by [share] of its width while [shift] (-1..1) runs, and zooms it
 * just enough that its edges never come into view.
 */
private fun Modifier.parallaxFill(shift: () -> Float, share: Float): Modifier = graphicsLayer {
    val v = shift()
    translationX = v * size.width * share
    val s = 1f + 2f * share * kotlin.math.abs(v)
    scaleX = s; scaleY = s
}

/** What the buttons do for this item. */
@Composable
private fun SpotHints(s: Spot, modifier: Modifier) {
    Row(
        modifier.heightIn(min = SpotHintsHeight).background(Palette.Card.copy(alpha = 0.9f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (s.app != null) {
            AppHints(s.app)
        } else if (s.empty) {
            ButtonHint("A", if (IconGrid.moving?.carried == true) "Paste" else "Add here", Palette.Mint)
            ButtonHint("Y", "Options", Palette.Butter)
            if (ScreenRoles.xSwaps()) ButtonHint("X", "Swap", Palette.Sky)
        } else {
            val resumable = s.game != null && RunningGames.list.any { it.game?.key == s.game.key }
            ButtonHint("A", if (s.game == null) "Open" else if (resumable) "Resume" else "Play", Palette.Mint)
            ButtonHint("Y", "Options", Palette.Butter)
            if (ScreenRoles.xSwaps()) ButtonHint("X", "Swap", Palette.Sky)
        }
    }
}
