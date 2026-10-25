package io.github.zahakj.tandem.ui

import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Date

/** Bottom-screen styles, as offered in Appearance. Stored in [io.github.zahakj.tandem.data.Settings.bottomMode]. */
val BottomModes = listOf(
    "details" to "Details",
    "both" to "Art + info",
    "art" to "Animated art",
    "clock" to "Clock",
)

/** A video snap for [game], if one has been downloaded: `ROMs/downloaded_media/<system>/videos/<game>.mp4`. */
fun videoSnapFor(game: Game): File? {
    for (id in game.platform.artIds) {
        for (dir in listOf("videos", "video", "videosnaps")) {
            for (ext in listOf("mp4", "webm", "mkv")) {
                val f = File(Library.romsDir, "downloaded_media/$id/$dir/${game.stem}.$ext")
                if (f.isFile) return f
            }
        }
    }
    return null
}

/**
 * A game's moving art, always covering the whole space: the best of its pictures for this shape ([ArtPick]: key art,
 * else a title screen or gameplay shot, else the box), cropped on its busy part and slowly panning and zooming (Ken
 * Burns). After you rest on the game for a moment its video snap fades in on top. Waiting before the video starts
 * keeps scrolling smooth: nothing is decoded for games you only pass over.
 *
 * While the picture is being chosen or loaded nothing is drawn, so whatever was there before (the last game's art,
 * cross-fading out) stays in view instead of a blank flash; a backdrop in the system's colour fades in only if that
 * takes longer than a moment.
 */
@Composable
fun HeroArt(game: Game, modifier: Modifier = Modifier, onShown: (() -> Unit)? = null) {
    val look = Store.settings.look
    val snap by produceState<File?>(null, game, look.videoSnaps) {
        value = null
        if (!look.videoSnaps) return@produceState
        delay(Store.settings.look.videoDelayMs.toLong().coerceIn(150, 5000))
        value = withContext(Dispatchers.IO) { videoSnapFor(game) }
    }
    val cands = rememberBackdrops(game)
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = constraints.maxWidth.takeIf { constraints.hasBoundedWidth } ?: 1920
        val h = constraints.maxHeight.takeIf { constraints.hasBoundedHeight } ?: 1080
        if (cands.isEmpty()) {
            // No art at all: the system's colour and Pip, rather than an empty dark box.
            LaunchedEffect(game.key) { onShown?.invoke() }
            val c = Palette.forName(game.platform.id)
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c.copy(alpha = 0.55f), c.copy(alpha = 0.15f)))), contentAlignment = Alignment.Center) {
                Pip(96.dp, mood = 1)
            }
        } else {
            val choice = rememberArtChoice(cands, w, h)
            LaunchedEffect(w, h) { ArtPick.warmAll(w, h) }
            if (choice == null) {
                WaitingBackdrop(game, onShown)
            } else {
                KenBurns(rememberSizedRequest(choice.file, maxOf(w, h)), Modifier.fillMaxSize(), moving = look.kenBurns, alignment = choice.align, onShown = onShown)
            }
        }
        // After minutes without input it holds its frame, like every other motion (Ambient.resting).
        snap?.let { LoopingVideo(it, Modifier.fillMaxSize(), paused = Ambient.resting) }
    }
}

/** The system's colour, fading in only if the picture takes more than a moment to arrive. */
@Composable
private fun WaitingBackdrop(game: Game, onShown: (() -> Unit)?) {
    val shown = remember(game.key) { Animatable(0f) }
    LaunchedEffect(game.key) { delay(260); onShown?.invoke(); shown.animateTo(1f, tween(320)) }
    val c = Palette.forName(game.platform.id)
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = shown.value }
            .background(Brush.linearGradient(listOf(lerp(c, Palette.Night, 0.45f), lerp(c, Palette.Night, 0.8f)))),
    )
}

/** Fades all four edges of what it is drawn on into what is behind it; [edge] is the share of each side that fades. */
fun Modifier.softEdges(edge: Float = 0.1f): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, edge to Color.Black, 1f - edge to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, edge to Color.Black, 1f - edge to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

private val imageAspects = java.util.concurrent.ConcurrentHashMap<String, Float>()

/** A picture's width / height, read from its header off the main thread (and remembered); null until known. */
@Composable
fun rememberImageAspect(file: File): Float? {
    val key = file.path
    val aspect by produceState(imageAspects[key], key) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path, o)
                if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else 16f / 9f
            }.getOrDefault(16f / 9f)
        }.also { imageAspects[key] = it }
    }
    return aspect
}

/**
 * Fades whatever this is drawn on into the backdrop behind it (an alpha mask), instead of laying a dark band over
 * it. [stops] are (position, opacity) pairs from top to bottom.
 */
fun Modifier.fadeMask(vararg stops: Pair<Float, Float>): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(*stops.map { (at, a) -> at to Color.Black.copy(alpha = a) }.toTypedArray()),
            blendMode = BlendMode.DstIn,
        )
    }

/** Like [fadeMask], left to right: art fades out where text sits on it, instead of a dark band behind the text. */
fun Modifier.fadeMaskHorizontal(vararg stops: Pair<Float, Float>): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        drawRect(
            Brush.horizontalGradient(*stops.map { (at, a) -> at to Color.Black.copy(alpha = a) }.toTypedArray()),
            blendMode = BlendMode.DstIn,
        )
    }

/** The hero's fade into the background, in the user's chosen style, as a mask on the art itself. */
fun Modifier.heroFade(style: String = Hero.gradient): Modifier = when (style) {
    "none" -> this
    "strong" -> fadeMask(0.15f to 1f, 0.75f to 0.15f, 1f to 0f)
    "vignette" -> graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        drawRect(Brush.radialGradient(listOf(Color.Black, Color.Black, Color.Transparent), radius = size.maxDimension * 0.65f), blendMode = BlendMode.DstIn)
    }
    else -> fadeMask(0.45f to 1f, 1f to 0f)
}

/** Hero art that slowly drifts and zooms, like a living poster. [alignment] picks the part a crop keeps. */
@Composable
fun KenBurns(
    model: Any, modifier: Modifier = Modifier, moving: Boolean = true, alignment: Alignment = Alignment.Center, onShown: (() -> Unit)? = null,
) {
    // On the shared ambient clock (Ambient.kt), read only in the layer: ~30 fps once idle, stopped while covered.
    val animate = rememberAmbient(moving && !Themes.still && !io.github.zahakj.tandem.data.Store.settings.reduceMotion)
    // Each picture drifts its own way (left, right, up or down), a little further with livelier motion.
    val seed = (model.hashCode() and 0x7fffffff)
    val dirX = if (seed % 2 == 0) 1f else -1f
    val dirY = if ((seed / 2) % 3 == 0) 0f else if ((seed / 2) % 3 == 1) 1f else -1f
    val amount = Flair.amount.coerceIn(0.6f, 1.5f)
    AsyncImage(
        model, null,
        modifier.graphicsLayer {
            val v = if (animate) Ambient.pingPong(16_000) else 0.5f
            scaleX = 1.08f + v * 0.08f * amount; scaleY = scaleX
            translationX = (v - 0.5f) * 22f * amount * dirX * density
            translationY = (v - 0.5f) * 10f * amount * dirY * density
        },
        onSuccess = onShown?.let { f -> { _ -> f() } },
        alignment = alignment, contentScale = ContentScale.Crop,
    )
}

/** Kept for callers that still place a fade over the art; the fade is now [heroFade], a mask on the art itself. */
@Composable
@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun BoxScope.HeroFade(style: String = Hero.gradient) = Unit

/** The game's logo (or its name in big letters), sized by the "logo size" setting. */
@Composable
fun HeroLogo(game: Game, modifier: Modifier = Modifier, maxHeight: Int = 90, textColor: Color = Palette.Text, textFallback: Boolean = true) {
    val logo = Libraries.of(game)?.logo(game)
    val scale = Hero.logoScale
    // The game's own size and place from Edit details, on top of the setting.
    val placed = modifier.logoFit(game.meta.logoFit)
    if (logo != null) {
        AsyncImage(logo, game.title, placed.fillMaxWidth(scale).heightIn(max = (maxHeight * (0.6f + scale * 0.6f)).dp), contentScale = ContentScale.Fit)
    } else if (textFallback) {
        Text(
            // It sits on the art: a soft text shadow keeps it readable without a band behind it.
            game.title, placed.fillMaxWidth(scale.coerceAtLeast(0.8f)), style = MaterialTheme.typography.displaySmall.onArt(),
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, color = textColor,
        )
    }
}

/** Animated art only: the game's moving hero and logo, nothing else. */
@Composable
fun ArtOnly(game: Game, resumable: Boolean) {
    Box(Modifier.fillMaxSize()) {
        HeroArt(game, Modifier.fillMaxSize().fadeMask(0.6f to 1f, 1f to 0.25f))
        AnimatedLogo(game, maxWidth = 380.dp, maxHeight = 130.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp, start = 22.dp, end = 22.dp), textAlign = TextAlign.Center)
        if (resumable) Pill("● Running", Palette.Mint, Modifier.align(Alignment.TopEnd).padding(16.dp).darkChip())
    }
}

/** Animated art filling the screen, with the game's info on a glass card along the bottom. */
@Composable
fun ArtWithInfo(game: Game, resumable: Boolean, info: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        // The art fades away towards the bottom, so the info below sits on the backdrop, not on a dark band.
        HeroArt(game, Modifier.fillMaxSize().fadeMask(0.3f to 1f, 0.7f to 0.12f, 1f to 0f))
        if (resumable) Pill("● Running", Palette.Mint, Modifier.align(Alignment.TopEnd).padding(16.dp).darkChip())
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) { info() }
    }
}

/** A calm clock face for the bottom screen: big time, the date, battery, and what is highlighted up top. */
@Composable
fun ClockFace() {
    val ctx = LocalContext.current
    val now by rememberMinute()
    val battery by produceState(-1) {
        while (true) {
            value = ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(60_000)
        }
    }
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    val selected = when (val s = Selection.current) {
        is Selected.GameItem -> s.game.title
        is Selected.System -> s.library.platform.name
        is Selected.Group -> s.title
        is Selected.Setting -> s.title
        is Selected.App -> if (s.pkg == Dock.ALL_APPS) "All apps" else io.github.zahakj.tandem.RunningGames.appLabel(ctx, s.pkg)
        Selected.Home -> null
    }
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(Clock.always(now), style = MaterialTheme.typography.displaySmall.copy(fontSize = 76.sp, lineHeight = 80.sp))
        Text(
            android.text.format.DateFormat.format("EEEE, d MMMM", now).toString(),
            style = MaterialTheme.typography.titleLarge, color = Palette.Muted,
        )
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pip(34.dp, mood = if (hour in 0..5) 2 else 0)
            if (battery >= 0) Pill("$battery% battery", if (battery <= 20) Palette.Peach else Palette.Mint)
        }
        if (selected != null) {
            Spacer(Modifier.height(18.dp))
            Text(selected, style = MaterialTheme.typography.titleMedium, color = Palette.Text.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Hero settings. A theme pack's own choice applies until the user moves the setting off its default. */
object Hero {
    val gradient: String
        get() = Store.settings.look.heroGradient.let { mine -> if (mine == "fade") Themes.live.heroGradient ?: mine else mine }

    val logoScale: Float
        get() = Store.settings.look.heroLogoScale.let { mine -> if (mine == 0.7f) Themes.live.heroLogoScale ?: mine else mine }.coerceIn(0.3f, 1f)

    val gradients = listOf("fade" to "Soft fade", "strong" to "Strong fade", "vignette" to "Vignette", "none" to "None")
}
