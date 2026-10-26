package io.github.zahakj.tandem.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** What a square icon on the icon grid shows. */
sealed interface TileArt {
    /** A picture from an icon pack (or anything square), cropped to the tile. */
    data class Picture(val file: File) : TileArt
    /** Tandem's own system icon: the system's colour and its short name. */
    data class Monogram(val text: String, val color: Color) : TileArt
    /** A drawn symbol on a colour tile, for collections, genres and the like. */
    data class Symbol(val glyph: Glyph, val color: Color) : TileArt
    data class App(val pkg: String) : TileArt
    data class GameBox(val game: Game) : TileArt
    /** A folder: its colour, and a peek at up to four things inside. */
    data class Folder(val color: Color, val glyph: Glyph, val inside: List<TileArt>, val letter: String? = null) : TileArt
    /** One of Tandem's own drawn icons (TandemIcons.kt). */
    data class Tandem(val icon: TIcon, val color: Color) : TileArt
    /** Art Tandem draws once into a cached bitmap: designed and built-in folders (FolderLooks.kt). */
    data class Rendered(val spec: ArtSpec) : TileArt
    /** [base] with a live count in its corner: a smart folder's games. */
    data class Counted(val base: TileArt, val count: Int) : TileArt
}

/** The art without a count on it. */
val TileArt.bare: TileArt get() = if (this is TileArt.Counted) base else this

/** Colours for well-known systems (close to their own branding, a touch softer), so each icon is recognisable. */
object SystemColors {
    private val known: Map<String, Long> = mapOf(
        "n3ds" to 0xE8475A, "nds" to 0x7C8CF0, "switch" to 0xE8463C, "wiiu" to 0x16A3D4, "wii" to 0x47B8EA, "gc" to 0x6F5BD8,
        "n64" to 0x2FA25A, "snes" to 0x8061C2, "nes" to 0xD2453C, "fds" to 0xE0663A, "gba" to 0x5B45D6, "gbc" to 0xF0B42A,
        "gb" to 0x86B04E, "virtualboy" to 0xC93A3A, "pokemini" to 0xF2C230, "gameandwatch" to 0xD9A23A,
        "psx" to 0x7F8FD8, "ps2" to 0x2F5FD0, "ps3" to 0x4F6BC4, "psp" to 0x4C70B8, "psvita" to 0x2A7CD6,
        "genesis" to 0x2B78D0, "mastersystem" to 0xD94848, "gamegear" to 0x4458C8, "sg1000" to 0x4C8ED8, "sega32x" to 0xE8A33A,
        "segacd" to 0x2A9AA8, "saturn" to 0x5865C9, "dreamcast" to 0xF07F2E, "naomi" to 0xE88A3C, "atomiswave" to 0x3A9AD9,
        "xbox" to 0x2E9E3E, "xbox360" to 0x63BF3A, "windows" to 0x2B8BE6, "dos" to 0x3E9C6A, "arcade" to 0xF2803A,
        "neogeo" to 0xE0A72E, "neogeocd" to 0xD6922E, "ngp" to 0x2DB2C6, "ngpc" to 0x2BA6D8, "pcengine" to 0xEF7A30,
        "pcenginecd" to 0xE9673A, "supergrafx" to 0xC95ED8, "atari2600" to 0xD9583A, "atari5200" to 0xC4503C,
        "atari7800" to 0xB9483A, "atarijaguar" to 0xD23A3A, "atarilynx" to 0xE6A23A, "3do" to 0xD24A4A,
        "amiga" to 0xE2533F, "c64" to 0x8A6BD0, "msx" to 0x3A86D9, "zxspectrum" to 0xEC6A3A, "wonderswan" to 0x9A5AD0,
        "wonderswancolor" to 0x7E5AD8, "android" to 0x3DBF7C, "colecovision" to 0x5A7AD0, "intellivision" to 0xC79A3A,
        "vectrex" to 0x3A9AD0, "amstradcpc" to 0x3AB0A0, "pcfx" to 0xB04AD0,
    )

    /** The colour for a system id: its own when known, otherwise a pleasant hue picked from the name. */
    fun of(id: String): Color {
        known[id]?.let { return Color(0xFF000000 or it) }
        val h = ((id.hashCode() and 0x7fffffff) % 360).toFloat()
        return Color.hsv(h, 0.52f, 0.86f)
    }
}

/** Size of an image request for a tile [size] on screen: rounded up so nearby sizes share the cache. */
@Composable
fun tilePx(size: Dp): Int {
    val px = with(LocalDensity.current) { size.toPx() }
    return ((px / 64f).toInt() + 1) * 64
}

/** A sized, remembered image request (decoded at the size it is shown, not the file's full size). */
@Composable
fun rememberSizedRequest(file: File, px: Int): ImageRequest {
    val ctx = LocalContext.current
    return remember(file, px) { sizedRequest(ctx, file, px) }
}

/**
 * Tandem's one image loader. Its memory cache is 15 % of the app's memory class (Coil's default is 25 %), which still
 * holds every tile of a big grid at its drawn size plus a few heroes; anything else decodes again from the disk
 * cache. Installed before anything loads a picture, by whichever of Tandem's screens starts first.
 */
object Images {
    @Volatile private var installed = false

    fun install(ctx: android.content.Context) {
        if (installed) return
        installed = true
        val app = ctx.applicationContext
        coil.Coil.setImageLoader {
            coil.ImageLoader.Builder(app)
                .memoryCache { coil.memory.MemoryCache.Builder(app).maxSizePercent(0.15).build() }
                // Private mode: pictures from the web come from the disk cache only (Offline.kt).
                .components { add(OfflineImages) }
                .build()
        }
    }
}

/** In private mode, remote pictures are never fetched: only what is already in the disk cache shows. */
private object OfflineImages : coil.intercept.Interceptor {
    override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
        val r = chain.request
        // Addresses as text, Uri or HttpUrl all read as "http…".
        val remote = r.data.let { it !is java.io.File && it.toString().startsWith("http", ignoreCase = true) }
        if (!remote || !io.github.zahakj.tandem.NetGate.closed) return chain.proceed(r)
        return chain.proceed(r.newBuilder().networkCachePolicy(coil.request.CachePolicy.DISABLED).build())
    }
}

/**
 * A request for [file] at [px]. Every size of one file shares one memory-cache entry: a larger decode serves a
 * smaller tile at once, and while a larger one decodes (L1 / R1 zooming in) the smaller one stands in instead of a
 * blank tile, so a zoom never blanks the grid. Also used to decode tiles ahead (IconGrid), so those hits match.
 */
fun sizedRequest(ctx: android.content.Context, file: File, px: Int): ImageRequest {
    val key = coil.memory.MemoryCache.Key("sized:" + file.path)
    return ImageRequest.Builder(ctx).data(file).size(px).precision(coil.size.Precision.INEXACT)
        .memoryCacheKey(key).placeholderMemoryCacheKey(key).crossfade(160).build()
}

/** Draws [art] filling its box (the caller clips it to the tile shape). [size] is the tile's size on screen. */
@Composable
fun TileArtView(art: TileArt, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        when (art) {
            is TileArt.Picture -> AsyncImage(rememberSizedRequest(art.file, tilePx(size)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            is TileArt.Monogram -> Monogram(art.text, art.color, size)
            is TileArt.Symbol -> SymbolTile(art.glyph, art.color, size)
            is TileArt.App -> AppTileIcon(art.pkg, size)
            is TileArt.GameBox -> GameBoxArt(art.game, size)
            is TileArt.Folder -> FolderArt(art, size)
            is TileArt.Tandem -> TandemIconTile(art.icon, art.color)
            is TileArt.Rendered -> RenderedArt(art.spec, size)
            is TileArt.Counted -> {
                TileArtView(art.base, size)
                if (size >= 40.dp) CountChip(art.count, size, Modifier.align(Alignment.BottomEnd))
            }
        }
    }
}

/** A small count in a tile's corner, sized with the tile. */
@Composable
private fun CountChip(count: Int, size: Dp, modifier: Modifier) {
    val sp = with(LocalDensity.current) { (size.value * 0.15f).coerceAtLeast(9f).dp.toSp() }
    Text(
        if (count > 999) "999+" else count.toString(),
        modifier.padding(size * 0.07f).darkChip().padding(horizontal = size * 0.07f, vertical = size * 0.015f),
        color = Color.White, maxLines = 1,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = sp, lineHeight = sp, fontWeight = FontWeight.Black),
    )
}

/** A colour tile with a soft light in one corner and a glossy edge; the base for Tandem's own icons. */
private fun Modifier.tileGradient(color: Color): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val base = Brush.linearGradient(
        listOf(lerp(color, Color.White, 0.16f), color, lerp(color, Color.Black, 0.26f)),
        start = Offset(0f, 0f), end = Offset(w, h),
    )
    val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent), center = Offset(w * 0.18f, h * 0.1f), radius = w * 0.75f)
    val rim = Color.White.copy(alpha = 0.16f)
    val stroke = Stroke(max(1f, w * 0.012f))
    onDrawBehind {
        drawRect(base)
        drawRect(glow)
        val s = stroke.width
        drawRoundRect(rim, Offset(s / 2, s / 2), Size(w - s, h - s), CornerRadius(w * 0.2f), style = stroke)
    }
}

/** Tandem's own system icon: the short name in big rounded letters on the system's colour. */
@Composable
fun Monogram(text: String, color: Color, size: Dp) {
    val words = text.trim().split(' ').filter { it.isNotEmpty() }
    // Long names with a space go on two lines, so they can stay big.
    val lines = if (text.length > 6 && words.size >= 2) listOf(words.dropLast(1).joinToString(" "), words.last()) else listOf(text)
    val longest = lines.maxOf { it.length }.coerceAtLeast(2)
    val density = LocalDensity.current
    val fontDp = min(size.value * 0.36f, size.value * 0.84f / (0.64f * longest)) * if (lines.size > 1) 0.92f else 1f
    val fontSp = with(density) { fontDp.dp.toSp() }
    val ink = Color.White
    Box(Modifier.fillMaxSize().tileGradient(color), contentAlignment = Alignment.Center) {
        // A big faint letter behind, for depth.
        Text(
            text.take(1), Modifier.align(Alignment.BottomEnd).padding(end = size * 0.04f),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = with(density) { (size.value * 0.95f).dp.toSp() }, fontWeight = FontWeight.Black, lineHeight = with(density) { (size.value * 0.95f).dp.toSp() }),
            color = Color.White.copy(alpha = 0.10f), maxLines = 1,
        )
        Text(
            lines.joinToString("\n"), Modifier.padding(horizontal = size * 0.06f),
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = fontSp, lineHeight = fontSp * 1.02f, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp,
                shadow = Shadow(lerp(color, Color.Black, 0.55f).copy(alpha = 0.55f), Offset(0f, fontDp * 0.12f * density.density), fontDp * 0.25f * density.density),
            ),
            color = ink, textAlign = TextAlign.Center, maxLines = lines.size, overflow = TextOverflow.Clip,
        )
    }
}

/** A white symbol on a colour tile with a faint grid pattern, like an app icon. */
@Composable
fun SymbolTile(glyph: Glyph, color: Color, size: Dp) {
    Box(
        Modifier.fillMaxSize().tileGradient(color).drawBehind {
            // A soft 3x3 pattern of lighter squares.
            val cell = this.size.width / 3f
            val c = Color.White.copy(alpha = 0.07f)
            for (i in 0 until 3) for (j in 0 until 3) if ((i + j) % 2 == 0) drawRect(c, Offset(i * cell, j * cell), Size(cell, cell))
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size * 0.62f).clip(RoundedCornerShape(size * 0.18f)).background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, Color.White, Modifier.padding(size * 0.1f))
        }
    }
}

/** Box art for a game, or its name on its system's colour. */
@Composable
fun GameBoxArt(game: Game, size: Dp) {
    val box = remember(game.key, game.meta.box) { Libraries.of(game)?.box(game) }
    if (box != null) {
        AsyncImage(rememberSizedRequest(box, tilePx(size)), game.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxSize().tileGradient(SystemColors.of(game.platform.id)), contentAlignment = Alignment.Center) {
            Text(
                game.title, Modifier.padding(size * 0.08f), color = Color.White, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = with(LocalDensity.current) { (size.value * 0.13f).coerceAtLeast(9f).dp.toSp() }),
                maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A folder Tandem draws (when the icon pack has no art for it): a full-bleed tile in the folder's colour with a
 * light, a folder tab peeking over a glassy pocket, and a 2x2 mosaic of what's inside. It lives a little: with more
 * than four things inside, one cell at a time cross-fades to the next thing, and now and then a soft sheen sweeps
 * across. Both hold still with Reduce motion, animations Off or battery saver, and while the screen isn't showing.
 * An empty folder shows its initial over empty slots, never a bare folder symbol.
 */
@Composable
fun FolderArt(art: TileArt.Folder, size: Dp) {
    val color = art.color
    val live = Flair.floating && rememberResumed() && size >= 40.dp
    val seed = ((color.hashCode() xor art.inside.size * 7919) and 0x7fffffff) % 1000 / 1000f
    // The sheen runs on the ambient clock's lively time (Ambient.kt), read only while drawing; it rests when idle.
    val sheening = rememberAmbient(live, livelyOnly = true)
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val w = this.size.width
            val h = this.size.height
            val base = Brush.linearGradient(
                listOf(lerp(color, Color.White, 0.2f), color, lerp(color, Color.Black, 0.36f)),
                start = Offset(0f, 0f), end = Offset(w * 0.75f, h),
            )
            val light = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), center = Offset(w * 0.18f, h * 0.04f), radius = w * 0.85f)
            val tabColor = lerp(color, Color.White, 0.42f).copy(alpha = 0.6f)
            val pocketTop = h * 0.19f
            val pocket = Brush.verticalGradient(
                listOf(lerp(color, Color.White, 0.34f).copy(alpha = 0.5f), lerp(color, Color.White, 0.08f).copy(alpha = 0.26f)),
                startY = pocketTop, endY = h,
            )
            val rim = Color.White.copy(alpha = 0.42f)
            val pr = w * 0.13f
            onDrawWithContent {
                drawRect(base)
                drawRect(light)
                drawRoundRect(tabColor, Offset(w * 0.09f, h * 0.075f), Size(w * 0.4f, h * 0.2f), CornerRadius(w * 0.06f))
                drawRoundRect(pocket, Offset(w * 0.06f, pocketTop), Size(w * 0.88f, h * 0.75f), CornerRadius(pr))
                drawRoundRect(rim, Offset(w * 0.06f + pr * 0.6f, pocketTop), Size(w * 0.88f - pr * 1.2f, max(1f, h * 0.012f)), CornerRadius(h * 0.01f))
                drawContent()
                // The sheen: a soft diagonal band, crossing in the first fifth of each turn, then resting.
                if (sheening && Ambient.lively) {
                    val t = Ambient.loop(7000, seed, lively = true)
                    if (t < 0.2f) {
                        val x = -w * 0.6f + (w * 2.2f) * (t / 0.2f)
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                                start = Offset(x - w * 0.28f, 0f), end = Offset(x + w * 0.28f, h * 0.55f),
                            ),
                        )
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().padding(top = size * 0.26f, bottom = size * 0.1f, start = size * 0.12f, end = size * 0.12f), contentAlignment = Alignment.Center) {
            val cell = size * 0.3f
            val gap = size * 0.045f
            val shown = rememberMosaic(art.inside, live, seed)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in 0 until 2) Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until 2) {
                        val a = shown.getOrNull(row * 2 + col)
                        Box(
                            Modifier.size(cell).graphicsLayer {
                                shape = RoundedCornerShape(cell * 0.26f); clip = true
                                if (a != null && size >= 64.dp) shadowElevation = 3f * density
                            }.background(Color.White.copy(alpha = if (a != null) 0.2f else 0.13f)),
                        ) {
                            if (a != null) {
                                androidx.compose.animation.Crossfade(a, label = "folder-cell", animationSpec = tween(520)) { x -> TileArtView(x, cell) }
                            }
                        }
                    }
                }
            }
            if (art.inside.isEmpty() && art.letter != null) {
                // An empty folder shows its initial, so two empty folders never look the same.
                Text(
                    art.letter, color = Color.White, maxLines = 1,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = with(LocalDensity.current) { (size.value * 0.4f).dp.toSp() }, fontWeight = FontWeight.Black,
                        shadow = Shadow(lerp(color, Color.Black, 0.5f).copy(alpha = 0.5f), Offset(0f, with(LocalDensity.current) { (size * 0.02f).toPx() }), with(LocalDensity.current) { (size * 0.06f).toPx() }),
                    ),
                )
            }
        }
    }
}

/**
 * The four things a folder's mosaic shows. With more than four inside and [live], every few seconds one cell (in
 * turn) moves on to the next thing not already showing.
 */
@Composable
private fun rememberMosaic(inside: List<TileArt>, live: Boolean, seed: Float): List<TileArt> {
    val shown = remember(inside) { androidx.compose.runtime.mutableStateListOf<TileArt>().apply { addAll(inside.take(4)) } }
    if (live && inside.size > 4) {
        androidx.compose.runtime.LaunchedEffect(inside) {
            var next = 4
            var cell = 0
            kotlinx.coroutines.delay(1800L + (seed * 2400).toLong())
            while (true) {
                // Rests while Tandem is idle or something covers the grid (a cross-fade is 30 frames of drawing).
                androidx.compose.runtime.snapshotFlow { Ambient.lively && !Nav.overlayOpen && Layers.top == null }.first { it }
                var tries = 0
                while (inside[next % inside.size] in shown && tries < inside.size) { next++; tries++ }
                shown[cell] = inside[next % inside.size]
                next++
                cell = (cell + 1) % 4
                kotlinx.coroutines.delay(2800L)
            }
        }
    }
    return shown
}

/**
 * App icons drawn full-bleed at the size they're shown: adaptive icons fill the tile edge to edge (the way
 * launchers show them), older icons sit on a tile of their own main colour.
 */
object AppTileIcons {
    private val cache = LruCache<String, ImageBitmap>(80)

    fun cached(pkg: String, px: Int): ImageBitmap? = cache.get("$pkg@$px")

    /** Blocking; call off the main thread. */
    fun load(ctx: Context, pkg: String, px: Int): ImageBitmap? {
        val key = "$pkg@$px"
        cache.get(key)?.let { return it }
        val d = runCatching { ctx.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return null
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (d is AdaptiveIconDrawable) {
            // The layers are 108 units with the visible 72 in the middle: draw them half as big again, centred.
            val inset = px / 4
            d.background?.let { it.setBounds(-inset, -inset, px + inset, px + inset); it.draw(c) }
            d.foreground?.let { it.setBounds(-inset, -inset, px + inset, px + inset); it.draw(c) }
        } else {
            val probe = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 24, 24)
            d.draw(Canvas(probe))
            c.drawColor(averageColor(probe))
            probe.recycle()
            val pad = (px * 0.14f).toInt()
            d.setBounds(pad, pad, px - pad, px - pad)
            d.draw(c)
        }
        return bmp.asImageBitmap().also { cache.put(key, it) }
    }

    /** The main colour of an icon (opaque pixels only), softened, for the tile behind an old-style icon. */
    private fun averageColor(b: Bitmap): Int {
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (y in 0 until b.height) for (x in 0 until b.width) {
            val p = b.getPixel(x, y)
            if ((p ushr 24) < 200) continue
            r += (p shr 16) and 0xff; g += (p shr 8) and 0xff; bl += p and 0xff; n++
        }
        if (n == 0L) return 0xFF3A3F55.toInt()
        val c = Color((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
        // Not too dark and not too bright, so the icon stands out on it.
        val soft = if ((c.red + c.green + c.blue) / 3f > 0.75f) lerp(c, Color.Black, 0.18f) else lerp(c, Color.White, 0.12f)
        return android.graphics.Color.argb(255, (soft.red * 255).toInt(), (soft.green * 255).toInt(), (soft.blue * 255).toInt())
    }
}

@Composable
fun AppTileIcon(pkg: String, size: Dp) {
    val px = tilePx(size).coerceAtMost(384)
    val own = io.github.zahakj.tandem.data.AppPrefs.art(pkg)
    if (own != null) CustomAppArt(own, px, Modifier.fillMaxSize()) { DrawnAppTileIcon(pkg, px) } else DrawnAppTileIcon(pkg, px)
}

@Composable
private fun DrawnAppTileIcon(pkg: String, px: Int) {
    val ctx = LocalContext.current
    val icon by produceState(AppTileIcons.cached(pkg, px), pkg, px) {
        if (value == null) value = withContext(Dispatchers.IO) { AppTileIcons.load(ctx, pkg, px) }
    }
    icon?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
}

/** The picture the user chose for an app (Choose icon), cropped to fill; [fallback] when it can't be read. */
@Composable
fun CustomAppArt(path: String, px: Int, modifier: Modifier, fallback: @Composable () -> Unit) {
    var failed by remember(path) { androidx.compose.runtime.mutableStateOf(false) }
    if (failed) { fallback(); return }
    AsyncImage(rememberSizedRequest(java.io.File(path), px), null, modifier, contentScale = ContentScale.Crop, onError = { failed = true })
}
