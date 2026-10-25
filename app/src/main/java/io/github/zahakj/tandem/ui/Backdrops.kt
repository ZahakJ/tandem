package io.github.zahakj.tandem.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.Selected
import io.github.zahakj.tandem.Selection
import io.github.zahakj.tandem.data.Store
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Which physical screen a wallpaper is for. */
enum class ScreenSlot { Top, Bottom }

/** Length of one full backdrop loop. Every motion repeats a whole number of times per loop, so there is no jump. */
private const val LOOP_MS = 140_000
private const val TAU = (2 * PI).toFloat()

/** Folder for the user's own wallpapers (pictures and videos). */
val wallpaperDir: File get() = File(Library.romsDir, "tandem/wallpapers")

fun wallpaperFiles(): List<File> = wallpaperDir.listFiles().orEmpty()
    .filter { it.isFile && it.extension.lowercase() in ThemePacks.imageExtensions + ThemePacks.videoExtensions }
    .sortedBy { it.name.lowercase() }

/** The wallpaper for one screen, as chosen in Appearance: theme default, a backdrop style, game art, or a file. */
@Composable
fun Wallpaper(slot: ScreenSlot, tint: Color, modifier: Modifier = Modifier) {
    val look = Store.settings.look
    val theme = Themes.live
    // Resolve to something stable to cross-fade between.
    val key = remember(slot, look.wallTop, look.wallBottom, theme) { wallpaperKey(slot, look, theme) }
    // Both screens calm down inside Settings: a still, softened, dimmed picture instead of motion between the rows
    // (and nothing decoding or animating behind them), and the same full-bleed wash behind the details screen's
    // greeting and setting help, so neither shows a busy wallpaper half covered.
    val calm = Nav.inSettings
    // The same video on both screens is decoded once: the bottom screen shows a still frame of it.
    val shared = slot == ScreenSlot.Bottom && key.startsWith("file:") && isVideoKey(key) &&
        remember(look.wallTop, theme) { wallpaperKey(ScreenSlot.Top, look, theme) } == key
    Crossfade(key, modifier.fillMaxSize(), animationSpec = Motion.fade(600), label = "wallpaper") { k ->
        when {
            k.startsWith("file:") -> MediaWallpaper(File(k.removePrefix("file:")), look.wallDim, calm, shared, slot)
            k == "hero" -> { HeroWallpaper(tint, theme.backdrop, calm); CalmVeil(calm) }
            else -> {
                // Under a popup that previews on the other screen (the folder designer), this screen's backdrop rests.
                val under = Ambient.livePreview && (slot == ScreenSlot.Bottom) == ScreenRoles.libraryOnBottom
                Backdrop(BackdropStyle.of(k) ?: theme.backdrop, tint, moving = !calm && !under)
                CalmVeil(calm)
            }
        }
    }
}

/** What a screen's wallpaper setting comes to: "file:<path>", "hero" or a backdrop style id. Reads one stat. */
internal fun wallpaperKey(slot: ScreenSlot, look: io.github.zahakj.tandem.data.Look, theme: ThemeSpec): String {
    val choice = if (slot == ScreenSlot.Top) look.wallTop else look.wallBottom
    val packFile = if (slot == ScreenSlot.Top) theme.wallpaperTop else theme.wallpaperBottom
    return when {
        choice == "theme" && packFile != null -> "file:${packFile.path}"
        choice == "theme" -> theme.backdrop.id
        choice.startsWith("file:") && !File(choice.removePrefix("file:")).isFile -> theme.backdrop.id
        else -> choice
    }
}

internal fun isVideoKey(key: String) = key.substringAfterLast('.').lowercase() in ThemePacks.videoExtensions

/**
 * Something covers the screens (a popup, a panel, the launch, the screensaver), nobody has used Tandem for minutes
 * ([Ambient.resting]), or something opaque fills this screen ([WallpaperCover]): wallpaper videos hold their frame.
 */
@Composable
private fun wallpaperCovered(slot: ScreenSlot): Boolean =
    Nav.overlayOpen || Layers.top != null || io.github.zahakj.tandem.Screensaver.active || Ambient.resting || WallpaperCover.covers(slot)

/**
 * Screens whose wallpaper is hidden right now under something opaque that fills them (Spotlight's game art, once it
 * is in): a video wallpaper there holds its frame instead of decoding pictures nobody sees.
 */
object WallpaperCover {
    private val count = androidx.compose.runtime.mutableStateListOf(0, 0)

    fun covers(slot: ScreenSlot): Boolean = count[slot.ordinal] > 0

    /** Marks [slot] covered while this is composed and [on]. */
    @Composable
    fun Cover(slot: ScreenSlot, on: Boolean) {
        if (!on) return
        androidx.compose.runtime.DisposableEffect(slot) {
            count[slot.ordinal]++
            onDispose { count[slot.ordinal]-- }
        }
    }
}

/** The dim that settles over a drawn backdrop inside Settings (the backdrop itself stops moving). */
@Composable
private fun CalmVeil(calm: Boolean) {
    val a = animateFloatAsState(if (calm) 1f else 0f, Motion.fade(260), label = "calm-veil")
    val ink = Palette.Night
    Spacer(Modifier.fillMaxSize().drawBehind { val v = a.value; if (v > 0.001f) drawRect(ink, alpha = 0.45f * v) })
}

/**
 * A picture or a muted looping video, darkened a little so text on top stays readable. [calm] (Settings) lays a
 * softened, dimmed still of it on top and pauses the video; [shared] shows a still frame instead of decoding a video
 * the other screen already plays.
 */
@Composable
private fun MediaWallpaper(file: File, dim: Float, calm: Boolean, shared: Boolean, slot: ScreenSlot) {
    val video = file.extension.lowercase() in ThemePacks.videoExtensions
    Box(Modifier.fillMaxSize().background(Palette.NightDeep)) {
        val held = calm || wallpaperCovered(slot)
        // Held for a while (covered by Spotlight's art, inside Settings, resting after minutes of no input): the
        // player and its decoder's buffers (tens of MB for a 1080p video) go, and a still frame stands in. Coming
        // back, the still stays over the video until its first frame is in.
        val released by androidx.compose.runtime.produceState(false, held) {
            if (!held) { value = false; return@produceState }
            kotlinx.coroutines.delay(RELEASE_VIDEO_AFTER_MS)
            value = true
        }
        var still by androidx.compose.runtime.remember(file) { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(released) { if (released) still = true }
        when {
            video && shared -> WallpaperStill(file, soft = false)
            video -> {
                if (!released) LoopingVideo(file, Modifier.fillMaxSize(), paused = held, surface = true, onReady = { if (it) still = false })
                if (still) WallpaperStill(file, soft = false)
            }
            else -> AsyncImage(file, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Palette.Night.copy(alpha = dim.coerceIn(0f, 0.9f))))
        // Made ahead (a few seconds after the wallpaper shows), so opening Settings never waits for it.
        val a = animateFloatAsState(if (calm) 1f else 0f, Motion.fade(260), label = "calm-still")
        val ink = Palette.Night
        WallpaperStill(file, soft = true, alpha = { a.value }, delayMs = 3000)
        Spacer(Modifier.fillMaxSize().drawBehind { val v = a.value; if (v > 0.001f) drawRect(ink, alpha = 0.5f * v) })
    }
}

/** How long a wallpaper video holds its frame before its player is let go (see MediaWallpaper). */
private const val RELEASE_VIDEO_AFTER_MS = 4_000L

/** The highlighted game's art, heavily blurred, behind everything. Falls back to [fallback] when there's no art. */
@Composable
private fun HeroWallpaper(tint: Color, fallback: BackdropStyle, calm: Boolean) {
    val ctx = LocalContext.current
    val art: File? = when (val s = Selection.current) {
        is Selected.GameItem -> Libraries.of(s.game)?.let { lib -> lib.hero(s.game) ?: lib.screenshot(s.game) ?: lib.box(s.game) }
        is Selected.System -> io.github.zahakj.tandem.Collections.visible(s.library.games).firstNotNullOfOrNull { s.library.hero(it) ?: s.library.box(it) }
        is Selected.Group -> s.games.firstNotNullOfOrNull { g -> Libraries.of(g)?.let { it.hero(g) ?: it.box(g) } }
        else -> null
    }
    Box(Modifier.fillMaxSize()) {
        // Hidden under the (opaque, cropped) art when there is some: then it holds still, and so it does inside Settings.
        Backdrop(fallback, tint, moving = art == null && !calm)
        Crossfade(art, Modifier.fillMaxSize(), animationSpec = Motion.fade(700), label = "hero-wall") { file ->
            if (file != null) {
                // A small decode is plenty for a blurred picture, and keeps memory low.
                AsyncImage(
                    ImageRequest.Builder(ctx).data(file).size(480).build(), null,
                    Modifier.fillMaxSize().graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }.blur(28.dp),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        // Calmed with the highlighted item's own colour, deepened, not a grey veil that turns the lower half into a band.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(lerp(tint, Palette.Night, 0.78f).copy(alpha = 0.55f), lerp(tint, Palette.NightDeep, 0.84f).copy(alpha = 0.7f))),
            ),
        )
    }
}

/**
 * A drawn backdrop. All motion is read inside the draw lambda (no recomposition per frame), shapes and brushes are
 * built once per size, and nothing moves when animations are off, reduce motion is on, or battery saver is on.
 */
@Composable
fun Backdrop(style: BackdropStyle, tint: Color, modifier: Modifier = Modifier, theme: ThemeSpec = Themes.live, moving: Boolean = true) {
    // On the shared ambient clock (Ambient.kt): read only in the draw lambda, ~30 fps once idle, stopped while the
    // screen is hidden or covered.
    val live = rememberAmbient(!(Themes.still || !moving))
    val clock: () -> Float = if (live) AmbientLoop else StillClock
    val tintState = animateColorAsState(tint, Motion.fade(900), label = "tint")
    val sceneCache = remember(style, theme) { arrayOfNulls<Scene>(1) }
    Spacer(
        modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val bg = Brush.verticalGradient(listOf(theme.background, theme.backgroundDeep))
            val vignette = if (theme.dark) Brush.radialGradient(
                listOf(Color.Transparent, theme.backgroundDeep.copy(alpha = 0.55f)),
                center = Offset(size.width / 2, size.height / 2), radius = size.maxDimension * 0.75f,
            ) else null
            // Reused across recompositions (a new tint, say); rebuilt only for a new size, style or theme.
            val scene = sceneCache[0]?.takeIf { it.size == size && it.density == density }
                ?: Scene(style, size, theme, density).also { sceneCache[0] = it }
            onDrawBehind {
                drawRect(bg)
                scene.draw(this, clock(), tintState.value)
                if (vignette != null) drawRect(vignette)
            }
        },
    )
}

private val AmbientLoop: () -> Float = { Ambient.loop(LOOP_MS) }
private val StillClock: () -> Float = { 0.31f }

/** Precomputed pieces for one backdrop at one size. */
private class Scene(val style: BackdropStyle, val size: Size, val theme: ThemeSpec, val density: Float) {
    private val rnd = Random(42)
    private val w = size.width
    private val h = size.height
    private val path = Path()

    // A soft round glow centred on (0,0): drawn with translate, so one shader serves every blob/firefly.
    private fun glow(color: Color, radius: Float, alpha: Float) =
        Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), Offset.Zero, radius)

    private val blobRadius = size.minDimension * 0.75f
    private val peachBlob = glow(theme.peach, blobRadius, 0.16f)
    private val skyBlob = glow(theme.sky, blobRadius, 0.16f)
    private var tintColor: Color = Color.Unspecified
    private var tintBlob: Brush = peachBlob

    private fun tintBrush(c: Color): Brush {
        if (c != tintColor) { tintColor = c; tintBlob = glow(c, blobRadius, 0.16f) }
        return tintBlob
    }

    // Stars: x, y, radius, phase, layer.
    private val stars = Array(90) { i -> floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), (0.6f + rnd.nextFloat() * 1.4f) * density, rnd.nextFloat() * TAU, (i % 3).toFloat()) }
    private val sparkles = Array(18) { i -> floatArrayOf(((i * 97) % 100) / 100f, ((i * 53 + 11) % 100) / 100f, 1.6f + (i % 3), i.toFloat()) }
    private val nebulaA = glow(theme.lavender, size.minDimension * 0.9f, 0.13f)
    private val nebulaB = glow(theme.sky, size.minDimension * 0.7f, 0.10f)

    // Bubbles / petals / fireflies: x, y, size, speed (whole loops), wobble phase, colour index.
    private val floaters = Array(26) {
        floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), (2 + rnd.nextInt(5)).toFloat(), rnd.nextFloat() * TAU, rnd.nextInt(5).toFloat())
    }
    private val fireGlow = glow(theme.butter, 22f * density, 0.55f)
    private val fireGlowMint = glow(theme.mint, 22f * density, 0.45f)
    private val bubbleStroke = Stroke(1.5f * density)

    // Synthwave.
    private val horizon = h * 0.6f
    private val sunR = h * 0.2f
    private val sunCenter = Offset(w / 2f, horizon - sunR * 0.25f)
    private val sun = Brush.verticalGradient(
        listOf(theme.butter, theme.peach, theme.accent), startY = sunCenter.y - sunR, endY = sunCenter.y + sunR,
    )
    private val sky = Brush.verticalGradient(
        listOf(Color.Transparent, lerp(theme.background, theme.accent, 0.22f)), startY = 0f, endY = horizon,
    )
    private val sunGlow = glow(theme.peach, sunR * 2.2f, 0.25f)
    private val horizonGlow = Brush.verticalGradient(
        listOf(Color.Transparent, theme.accent.copy(alpha = 0.45f), Color.Transparent), startY = horizon - 18 * density, endY = horizon + 18 * density,
    )
    private val gridStroke = 1.4f * density

    fun draw(d: DrawScope, t: Float, tint: Color) = when (style) {
        BackdropStyle.Blobs -> d.blobs(t, tint)
        BackdropStyle.Starfield -> d.starfield(t)
        BackdropStyle.Waves -> d.waves(t, tint)
        BackdropStyle.Synthwave -> d.synthwave(t, tint)
        BackdropStyle.Bubbles -> d.bubbles(t)
        BackdropStyle.Petals -> d.petals(t)
        BackdropStyle.Fireflies -> d.fireflies(t)
        BackdropStyle.Plain -> d.plain(tint)
        else -> game.draw(d, t, tint)
    }

    /** The game-flavoured backdrops live in GameBackdrops.kt. */
    private val game by lazy { GameScene(style, size, theme, density) }

    private fun DrawScope.blobs(t: Float, tint: Color) {
        val p = t * TAU
        // Same drift as the original backdrop (a 28 s cycle), in whole cycles per loop.
        fun blob(brush: Brush, x: Float, offset: Float) {
            val cx = w * (x + 0.08f * cos(p * 5 + offset))
            val cy = h * (0.5f + 0.25f * sin(p * 3 + offset))
            translate(cx, cy) { drawCircle(brush, blobRadius, Offset.Zero) }
        }
        blob(tintBrush(tint), 0.18f, 0f)
        blob(peachBlob, 0.82f, 2.1f)
        blob(skyBlob, 0.55f, 4.2f)
        sparkle(p * 15)
    }

    private fun DrawScope.sparkle(phase: Float) {
        for (s in sparkles) {
            val a = (0.5f + 0.5f * sin(phase + s[3])) * 0.35f
            drawCircle(theme.text, s[2], Offset(s[0] * w, s[1] * h), alpha = a)
        }
    }

    private fun DrawScope.plain(tint: Color) {
        translate(w * 0.2f, h * 0.25f) { drawCircle(tintBrush(tint), blobRadius, Offset.Zero, alpha = 0.6f) }
    }

    private fun DrawScope.starfield(t: Float) {
        translate(w * 0.8f, h * 0.2f) { drawCircle(nebulaA, size.minDimension * 0.9f, Offset.Zero) }
        translate(w * 0.15f, h * 0.85f) { drawCircle(nebulaB, size.minDimension * 0.7f, Offset.Zero) }
        for (s in stars) {
            val layer = s[4]
            // Far stars drift slowly, near ones faster: 2, 4 and 6 screen widths per loop.
            val x = frac(s[0] - t * (2 + 2 * layer)) * w
            val y = s[1] * h
            val tw = 0.55f + 0.45f * sin(t * TAU * 20 + s[3])
            drawCircle(theme.text, s[2] * (1 + layer * 0.35f), Offset(x, y), alpha = (0.25f + layer * 0.2f) * tw)
        }
    }

    private fun DrawScope.waves(t: Float, tint: Color) {
        translate(w * 0.75f, h * 0.15f) { drawCircle(nebulaA, size.minDimension * 0.9f, Offset.Zero) }
        sparkle(t * TAU * 15)
        val colors = listOf(tint, theme.sky, theme.lavender)
        for (k in 0..2) {
            val base = h * (0.58f + 0.11f * k)
            val amp = h * (0.045f - 0.008f * k)
            val freq = 1.2f + 0.45f * k
            val speed = (3 + 2 * k) * (if (k % 2 == 0) 1 else -1)
            path.rewind()
            path.moveTo(0f, h)
            var x = 0f
            val step = 14f * density
            while (x <= w + step) {
                val u = x / w
                val y = base + amp * sin(TAU * (u * freq) + t * TAU * speed + k) + amp * 0.35f * sin(TAU * u * freq * 2.3f - t * TAU * (speed + 1))
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(w, h)
            path.close()
            drawPath(path, colors[k], alpha = 0.13f - 0.02f * k)
        }
    }

    private fun DrawScope.synthwave(t: Float, tint: Color) {
        drawRect(sky, size = Size(w, horizon))
        sparkle(t * TAU * 15)
        translate(sunCenter.x, sunCenter.y) { drawCircle(sunGlow, sunR * 2.2f, Offset.Zero) }
        drawCircle(sun, sunR, sunCenter)
        // Stripes cut through the lower half of the sun, getting thicker towards the horizon.
        var y = sunCenter.y + sunR * 0.05f
        var band = 2f * density
        while (y < sunCenter.y + sunR) {
            drawRect(theme.background, Offset(sunCenter.x - sunR, y), Size(sunR * 2, band))
            y += band + 7f * density
            band += 1.6f * density
        }
        drawRect(theme.backgroundDeep, Offset(0f, horizon), Size(w, h - horizon))
        drawRect(horizonGlow, Offset(0f, horizon - 18 * density), Size(w, 36 * density))
        val line = lerp(tint, theme.accent, 0.5f)
        // Horizontal lines rush towards you; one line spacing every ~2 s.
        val n = 14
        val shift = frac(t * 70f)
        for (i in 0 until n) {
            val z = (i + shift) / n
            val yy = horizon + (h - horizon) * z * z
            drawLine(line, Offset(0f, yy), Offset(w, yy), gridStroke, alpha = 0.15f + 0.6f * z)
        }
        for (j in -10..10) {
            val top = Offset(w / 2f + j * w * 0.02f, horizon)
            val bottom = Offset(w / 2f + j * w * 0.13f, h)
            drawLine(line, top, bottom, gridStroke, alpha = 0.45f, cap = StrokeCap.Round)
        }
    }

    private fun DrawScope.bubbles(t: Float) {
        for (f in floaters) {
            val r = (8f + f[2] * 26f) * density
            val y = h + r - frac(f[1] + t * f[3]) * (h + 2 * r)
            val x = f[0] * w + 18f * density * sin(t * TAU * 9 + f[4])
            val c = theme.accents[f[5].toInt()]
            drawCircle(c, r, Offset(x, y), alpha = 0.06f)
            drawCircle(c, r, Offset(x, y), alpha = 0.28f, style = bubbleStroke)
            drawCircle(theme.text, r * 0.22f, Offset(x - r * 0.35f, y - r * 0.4f), alpha = 0.22f)
        }
    }

    private fun DrawScope.petals(t: Float) {
        translate(w * 0.8f, h * 0.2f) { drawCircle(nebulaA, size.minDimension * 0.9f, Offset.Zero) }
        for (f in floaters) {
            val s = (7f + f[2] * 7f) * density
            val y = frac(f[1] + t * f[3]) * (h + 4 * s) - 2 * s
            val x = frac(f[0] + t * 2) * (w + 4 * s) - 2 * s + 30f * density * sin(t * TAU * 11 + f[4])
            val angle = (t * 360f * (f[3] + 4) + f[4] * 57f)
            val c = if (f[5] < 3f) theme.peach else theme.lavender
            rotate(angle, Offset(x, y)) {
                drawOval(c, Offset(x - s, y - s * 0.55f), Size(s * 2, s * 1.1f), alpha = 0.55f)
            }
        }
    }

    private fun DrawScope.fireflies(t: Float) {
        translate(w * 0.5f, h * 1.1f) { drawCircle(nebulaB, size.minDimension * 0.7f, Offset.Zero) }
        for ((i, f) in floaters.withIndex()) {
            val x = w * (f[0] + 0.05f * sin(t * TAU * f[3] + f[4]))
            val y = h * (0.2f + 0.75f * f[1] + 0.04f * cos(t * TAU * (f[3] + 1) + f[4] * 1.3f))
            val glowAmt = (0.5f + 0.5f * sin(t * TAU * (12 + i % 5) + f[4])).let { it * it }
            translate(x, y) {
                drawCircle(if (i % 3 == 0) fireGlowMint else fireGlow, 22f * density, Offset.Zero, alpha = glowAmt)
                drawCircle(theme.butter, 1.8f * density, Offset.Zero, alpha = 0.4f + 0.6f * glowAmt)
            }
        }
    }

    private fun frac(v: Float): Float = v - kotlin.math.floor(v)
}

/** Scanlines or a dot-matrix grid over the whole screen, for themes that want one. Drawn once, never animated. */
@Composable
fun ThemeOverlay(modifier: Modifier = Modifier) {
    val overlay = Themes.live.overlay
    if (overlay == ScreenOverlay.None) return
    val ink = Themes.live.backgroundDeep
    Spacer(
        modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val step = 3f * density
            onDrawWithContent {
                when (overlay) {
                    ScreenOverlay.Scanlines -> {
                        var y = 0f
                        while (y < size.height) { drawRect(Color.Black, Offset(0f, y), Size(size.width, density), alpha = 0.16f); y += step }
                    }
                    ScreenOverlay.DotMatrix -> {
                        var y = 0f
                        while (y < size.height) { drawRect(ink, Offset(0f, y), Size(size.width, 0.6f * density), alpha = 0.18f); y += step }
                        var x = 0f
                        while (x < size.width) { drawRect(ink, Offset(x, 0f), Size(0.6f * density, size.height), alpha = 0.18f); x += step }
                    }
                    ScreenOverlay.None -> {}
                }
            }
        },
    )
}
