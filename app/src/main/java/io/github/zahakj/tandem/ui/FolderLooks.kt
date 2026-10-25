package io.github.zahakj.tandem.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import io.github.zahakj.tandem.AndroidApps
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.data.ArtColor
import io.github.zahakj.tandem.data.FolderStyle
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.styleOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Folder looks: what a folder, smart folder or system tile wears once the user designed it (FolderDesign.kt), and
 * the art Tandem draws for the built-in folders (Apps, Android Games, Emulators) when the theme pack has none.
 * Everything Tandem makes here is drawn once into a bitmap per look and size (FolderArtCache) and kept; a frame only
 * draws the bitmap.
 */

/** Art Tandem draws once into a bitmap. [key] names the picture (the cache key); [color] shows until it is ready. */
sealed interface ArtSpec {
    val color: Color
    val key: String get() = toString()
}

/** A glyph on a two-tone colour tile with a faint big echo of itself: Tandem's designed folder art. */
data class GlyphArt(val icon: TIcon, override val color: Color, val second: Color, val inset: Float = 0f) : ArtSpec
data class EmojiArt(val emoji: String, override val color: Color, val inset: Float = 0f) : ArtSpec
/** One picture (a cover, an app icon "pkg:<package>", a file) fitted whole inside the tile, with an optional frame over it. */
data class CoverArt(val src: String, override val color: Color, val frame: String? = null, val inset: Float = 0f) : ArtSpec
/** Up to four pictures in a 2x2 mosaic. */
data class MosaicArt(val srcs: List<String>, override val color: Color, val inset: Float = 0f) : ArtSpec
/** Up to three covers fanned out like a hand of cards. */
data class FanArt(val srcs: List<String>, override val color: Color, val inset: Float = 0f) : ArtSpec
/** A picture the user chose: edge to edge when it is square, otherwise whole on the colour. */
data class PictureArt(val src: String, override val color: Color, val inset: Float = 0f) : ArtSpec

/** The built-in folders that get Tandem's designed art when the pack has none. */
enum class BuiltInFolder(val icon: TIcon) { Apps(TIcon.Apps), AndroidGames(TIcon.Controller), Emulators(TIcon.Emulator) }

object FolderLooks {
    /** Tiles that can be designed: home folders, systems, collections and genres. */
    fun designable(item: GridItem): Boolean = item.kind in setOf(TileKind.Folder, TileKind.System, TileKind.Collection, TileKind.Genres, TileKind.Genre)

    /** Which built-in folder [item] is, if any. A home folder holding only apps counts as an Apps folder. */
    fun builtIn(item: GridItem): BuiltInFolder? = when (item.kind) {
        TileKind.System -> if (item.platformId == AndroidApps.PLATFORM_ID) BuiltInFolder.AndroidGames else null
        TileKind.Folder -> {
            val names = item.packNames.map { PackArt.norm(it) }
            when {
                names.any { it.startsWith("emulator") } -> BuiltInFolder.Emulators
                names.any { it == "androidgames" } -> BuiltInFolder.AndroidGames
                names.any { it == "androidapp" || it == "androidapps" || it == "apps" } -> BuiltInFolder.Apps
                item.games.isEmpty() && (item.art.bare as? TileArt.Folder)?.inside?.isNotEmpty() == true -> BuiltInFolder.Apps
                else -> null
            }
        }
        else -> null
    }

    /** The theme's two colours for a built-in folder, read from the theme being shown (so a theme change repaints it). */
    fun builtInColors(kind: BuiltInFolder): Pair<Color, Color> {
        val t = Themes.target
        return when (kind) {
            BuiltInFolder.Apps -> t.accent to t.sky
            BuiltInFolder.AndroidGames -> t.mint to t.accent
            BuiltInFolder.Emulators -> t.lavender to t.peach
        }
    }

    /** A catalog tile with its stored style applied (and the built-in folders' own art). Unchanged when neither applies. */
    fun styled(item: GridItem?): GridItem? {
        if (item == null || !designable(item)) return item
        val style = IconGridStore.config.styleOf(item.key)
        if (style.isDefault && builtIn(item) == null) return item
        return restyle(item, style)
    }

    /** [plain] (a tile as the catalog made it) wearing [style]. Used for the grid and for live previews. */
    fun restyle(plain: GridItem, style: FolderStyle): GridItem {
        val kind = builtIn(plain)
        val packArt = plain.art.bare is TileArt.Picture
        val auto = when {
            style.color != null -> Color(style.color)
            kind != null && !packArt -> builtInColors(kind).first
            else -> plain.color
        }
        val inset = if (style.shape == FolderStyle.SHAPE_CIRCLE) 0.13f else 0f
        // A smart folder's count stays on whatever icon it wears.
        val art = iconArt(plain, style, auto, kind, inset).let { a -> (plain.art as? TileArt.Counted)?.copy(base = a) ?: a }
        return GridItem(
            plain.key, plain.kind, plain.title, plain.type, plain.detail, auto, art, plain.games, plain.selected, plain.default, plain.open,
            plain.game, plain.pkg, plain.platformId, plain.folderId, plain.collectionId, plain.blurb, plain.builtIn, plain.packNames, plain.hidden,
            style = style, plain = plain,
        )
    }

    private fun iconArt(plain: GridItem, style: FolderStyle, color: Color, kind: BuiltInFolder?, inset: Float): TileArt {
        val deep = lerp(color, Color.Black, 0.3f)
        fun auto(): TileArt {
            val a = plain.art.bare
            return when {
                a is TileArt.Picture -> if (inset > 0f) TileArt.Rendered(PictureArt(a.file.path, color, inset)) else a
                kind != null -> {
                    val (c1, c2) = builtInColors(kind)
                    TileArt.Rendered(GlyphArt(kind.icon, if (style.color != null) color else c1, if (style.color != null) deep else c2, inset))
                }
                style.color == null -> a
                a is TileArt.Folder -> a.copy(color = color)
                a is TileArt.Tandem -> a.copy(color = color)
                a is TileArt.Monogram -> a.copy(color = color)
                a is TileArt.Symbol -> a.copy(color = color)
                else -> a
            }
        }
        return when (style.icon) {
            FolderStyle.ICON_GAME -> source(plain, style.iconRef)?.let { src ->
                val frame = if (style.frame) gameOf(plain, style.iconRef)?.let { ThemePacks.coverFrame(it.platform.id)?.second?.path } else null
                TileArt.Rendered(CoverArt(src, color, frame, inset))
            } ?: auto()
            FolderStyle.ICON_MOSAIC -> sources(plain, 4).takeIf { it.isNotEmpty() }?.let { TileArt.Rendered(MosaicArt(it, color, inset)) } ?: auto()
            FolderStyle.ICON_FAN -> sources(plain, 3).takeIf { it.isNotEmpty() }?.let { TileArt.Rendered(FanArt(it, color, inset)) } ?: auto()
            FolderStyle.ICON_GLYPH -> glyph(style.glyph)?.let { TileArt.Rendered(GlyphArt(it, color, deep, inset)) } ?: auto()
            FolderStyle.ICON_EMOJI -> style.emoji?.takeIf { it.isNotBlank() }?.let { TileArt.Rendered(EmojiArt(it, color, inset)) } ?: auto()
            FolderStyle.ICON_FILE -> style.iconFile?.let { TileArt.Rendered(PictureArt(it, color, inset)) } ?: auto()
            else -> auto()
        }
    }

    fun glyph(name: String?): TIcon? = name?.let { n -> TIcon.entries.firstOrNull { it.name == n } }

    /** The game behind a tile key inside the folder ("game:<key>"), if it is one of the folder's games. */
    fun gameOf(plain: GridItem, ref: String?): Game? {
        val k = ref?.removePrefix("game:") ?: return null
        return plain.games.firstOrNull { it.key == k } ?: Libraries.byKey(k)
    }

    /** A picture source for a tile key: a game's cover, or an app's icon. */
    fun source(plain: GridItem, ref: String?): String? = when {
        ref == null -> null
        ref.startsWith("app:") -> "pkg:" + ref.removePrefix("app:")
        else -> gameOf(plain, ref)?.let { g -> Libraries.of(g)?.box(g)?.path }
    }

    /**
     * The folder's games, strongest first: favourites, then the most played, then the latest played. A folder's
     * "auto" picks (its default hero, a new folder's cover) come from the top of this list.
     */
    fun strongGames(games: List<Game>): List<Game> =
        games.sortedWith(compareByDescending<Game> { it.meta.favorite }.thenByDescending { it.meta.playSeconds }.thenByDescending { it.meta.lastPlayed })

    /** Pictures for a mosaic or fan: the strongest games' covers, else what the folder shows inside (app icons too). */
    fun sources(plain: GridItem, n: Int): List<String> {
        val covers = strongGames(plain.games).asSequence().mapNotNull { g -> Libraries.of(g)?.box(g)?.path }.distinct().take(n).toList()
        if (covers.size >= n || plain.kind != TileKind.Folder) return covers
        val inside = (plain.art.bare as? TileArt.Folder)?.inside.orEmpty().mapNotNull {
            when (it) {
                is TileArt.App -> "pkg:" + it.pkg
                is TileArt.Picture -> it.file.path
                is TileArt.GameBox -> Libraries.of(it.game)?.box(it.game)?.path
                else -> null
            }
        }
        return (covers + inside).distinct().take(n)
    }

    /** The icon's own picture, for "Colour from the art". */
    fun artSource(item: GridItem): String? = when (val a = item.art.bare) {
        is TileArt.Picture -> a.file.path
        is TileArt.Rendered -> when (val s = a.spec) {
            is CoverArt -> s.src
            is MosaicArt -> s.srcs.firstOrNull()
            is FanArt -> s.srcs.firstOrNull()
            is PictureArt -> s.src
            else -> null
        }
        is TileArt.App -> "pkg:" + a.pkg
        else -> null
    }

    // --- The other screen ------------------------------------------------------------------------------------------

    /** True when the style (or a built-in folder) decides the hero, rather than the tile's usual pick. */
    fun decidesHero(item: GridItem): Boolean = (item.style?.hero ?: FolderStyle.HERO_AUTO) != FolderStyle.HERO_AUTO || builtIn(item.plain ?: item) != null

    /** The pack's own wide art for the tile, if any. */
    private fun packHero(item: GridItem): File? =
        item.platformId?.let { IconPacks.systemHero(it) } ?: item.collectionId?.let { IconPacks.collectionHero(it) } ?: IconPacks.folderHero(item.packNames)

    /** A game's hero (or a screenshot when it has none). */
    fun gameHero(g: Game): File? = Libraries.of(g)?.let { it.hero(g) ?: it.screenshot(g) }

    /**
     * The hero on the other screen: the user's pick, else the pack's; a home folder of games falls back to its
     * strongest game's hero, while built-in folders show their designed sweep instead of some app's picture.
     */
    fun hero(item: GridItem): File? {
        val style = item.style ?: FolderStyle.DEFAULT
        return when (style.hero) {
            FolderStyle.HERO_SWEEP -> null
            FolderStyle.HERO_FILE -> style.heroFile?.let(::File)?.takeIf { it.isFile }
            FolderStyle.HERO_GAME -> gameOf(item, style.heroRef)?.let(::gameHero)
            else -> packHero(item) ?: if (item.kind == TileKind.Folder && builtIn(item.plain ?: item) == null) {
                strongGames(item.games).asSequence().take(40).mapNotNull(::gameHero).firstOrNull()
            } else null
        }
    }

    /** The logo over the hero: the user's picture or game logo, else the pack's. Null with a text title. */
    fun logo(item: GridItem): File? {
        val style = item.style ?: FolderStyle.DEFAULT
        return when (style.logo) {
            FolderStyle.LOGO_TEXT -> null
            FolderStyle.LOGO_FILE -> style.logoFile?.let(::File)?.takeIf { it.isFile }
            FolderStyle.LOGO_GAME -> gameOf(item, style.logoRef)?.let { g -> Libraries.of(g)?.logo(g) }
            else -> item.platformId?.let { IconPacks.systemLogo(it) } ?: IconPacks.folderLogo(item.packNames)
        }
    }

    /** The glyph drawn big in a colour sweep when there is no hero: a built-in folder's, or a chosen glyph. */
    fun sweepGlyph(item: GridItem): TIcon? {
        val style = item.style
        if (style?.icon == FolderStyle.ICON_GLYPH) glyph(style.glyph)?.let { return it }
        if (item.art.bare is TileArt.Picture) return null
        return builtIn(item.plain ?: item)?.icon
    }

    /** The tile's shape: the theme's rounding ([corner]), or the style's rounded square or circle. */
    fun shape(style: FolderStyle?, size: Dp, corner: Dp): Shape = when (style?.shape) {
        FolderStyle.SHAPE_CIRCLE -> CircleShape
        FolderStyle.SHAPE_ROUNDED -> RoundedCornerShape(size * 0.24f)
        else -> RoundedCornerShape(corner)
    }

    /** The same as a corner radius, for rings drawn around the tile. */
    fun cornerOf(style: FolderStyle?, size: Dp, corner: Dp): Dp = when (style?.shape) {
        FolderStyle.SHAPE_CIRCLE -> size / 2
        FolderStyle.SHAPE_ROUNDED -> size * 0.24f
        else -> corner
    }
}

// --- Drawing -------------------------------------------------------------------------------------------------------

/** Draws [spec] from its cached bitmap, made in the background the first time (its colour shows meanwhile). */
@Composable
fun RenderedArt(spec: ArtSpec, size: Dp) {
    val ctx = LocalContext.current
    val px = tilePx(size).coerceIn(64, FolderArtCache.MAX_PX)
    val bmp by produceState(FolderArtCache.cached(spec, px) ?: FolderArtCache.any(spec), spec, px) {
        val hit = FolderArtCache.cached(spec, px)
        value = hit ?: withContext(Dispatchers.IO) { FolderArtCache.render(ctx, spec, px) } ?: value
    }
    val b = bmp
    if (b != null) {
        Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    } else {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(lerp(spec.color, Color.White, 0.16f), lerp(spec.color, Color.Black, 0.22f)))))
    }
}

/** A glyph drawn big and faint, for a colour sweep without a hero (built-in folders, glyph folders). */
fun Modifier.glyphEcho(icon: TIcon?): Modifier = if (icon == null) this else drawWithCache {
    val s = size.minDimension * 0.92f
    onDrawBehind {
        translate(size.width - s * 0.82f, (size.height - s) / 2f) {
            rotate(-10f, Offset(s / 2f, s / 2f)) { drawTIcon(icon, s, Color.White.copy(alpha = 0.16f), Color.Transparent) }
        }
    }
}

/** Bitmaps of [ArtSpec]s, one per look (the largest size asked for so far serves the smaller ones). */
object FolderArtCache {
    const val MAX_PX = 640
    private val cache = object : LruCache<String, ImageBitmap>(28 shl 20) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** A bitmap at least about [px] big. */
    fun cached(spec: ArtSpec, px: Int): ImageBitmap? = cache.get(spec.key)?.takeIf { it.width * 4 >= px * 3 }

    /** Any size, to stand in while a bigger one is drawn. */
    fun any(spec: ArtSpec): ImageBitmap? = cache.get(spec.key)

    /** Blocking: draws [spec] at [px] and keeps it. */
    fun render(ctx: Context, spec: ArtSpec, px: Int): ImageBitmap? = runCatching {
        val bmp = ImageBitmap(px, px)
        val canvas = androidx.compose.ui.graphics.Canvas(bmp)
        val area = Size(px.toFloat(), px.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, area) { paint(ctx, spec, px) }
        bmp.also { cache.put(spec.key, it) }
    }.getOrNull()

    /** The main colour of a picture source (a file or "pkg:<package>"), for "Colour from the art". Blocking. */
    fun dominant(ctx: Context, src: String): Int? {
        val b = load(ctx, src, 48) ?: return null
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return ArtColor.dominant(px)
    }

    /** A picture source decoded to about [px] on its longer side (never the full file). */
    private fun load(ctx: Context, src: String, px: Int): Bitmap? {
        if (src.startsWith("pkg:")) return AppTileIcons.load(ctx, src.removePrefix("pkg:"), px.coerceAtLeast(64))?.asAndroidBitmap()
        val f = File(src)
        if (!f.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
    }

    private fun DrawScope.paint(ctx: Context, spec: ArtSpec, px: Int) {
        val w = size.width
        when (spec) {
            is GlyphArt -> {
                base(spec.color, spec.second)
                // A big faint echo of the glyph, tilted, off to one side.
                val e = w * 1.05f
                translate(w * 0.36f, w * 0.3f) { rotate(-12f, Offset(e / 2f, e / 2f)) { drawTIcon(spec.icon, e, Color.White.copy(alpha = 0.1f), Color.Transparent) } }
                val mid = lerp(spec.color, spec.second, 0.5f)
                val dark = mid.luminance() > 0.62f
                val ink = if (dark) lerp(mid, Color.Black, 0.72f) else Color.White
                val s = w * (0.5f - spec.inset * 0.6f)
                val at = (w - s) / 2f
                translate(at, at + w * 0.03f) { drawTIcon(spec.icon, s, lerp(spec.second, Color.Black, 0.45f).copy(alpha = 0.45f), lerp(spec.color, spec.second, 0.5f)) }
                translate(at, at) { drawTIcon(spec.icon, s, ink, mid) }
            }
            is EmojiArt -> {
                base(spec.color, lerp(spec.color, Color.Black, 0.3f))
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = w * (0.5f - spec.inset * 0.5f); textAlign = android.graphics.Paint.Align.CENTER
                    setShadowLayer(w * 0.03f, 0f, w * 0.02f, android.graphics.Color.argb(90, 0, 0, 0))
                }
                val fm = p.fontMetrics
                drawContext.canvas.nativeCanvas.drawText(spec.emoji, w / 2f, w / 2f - (fm.ascent + fm.descent) / 2f, p)
            }
            is CoverArt -> {
                val b = load(ctx, spec.src, px) ?: run { base(spec.color, lerp(spec.color, Color.Black, 0.3f)); return }
                // The cover blurred (drawn tiny, then stretched) behind it, tinted with the folder colour.
                base(spec.color, lerp(spec.color, Color.Black, 0.3f))
                val tiny = Bitmap.createScaledBitmap(b, 8, 8, true).asImageBitmap()
                drawImage(tiny, dstOffset = androidx.compose.ui.unit.IntOffset.Zero, dstSize = androidx.compose.ui.unit.IntSize(px, px), alpha = 0.55f)
                val pad = w * (0.1f + spec.inset)
                val r = fit(b.width, b.height, Rect(pad, pad, w - pad, w - pad))
                picture(b.asImageBitmap(), r, w * 0.05f)
                spec.frame?.let { f -> load(ctx, f, px)?.let { fr -> drawImage(fr.asImageBitmap(), dstOffset = r.topLeft.int(), dstSize = r.size.int()) } }
            }
            is MosaicArt -> {
                base(spec.color, lerp(spec.color, Color.Black, 0.3f))
                val m = w * (0.1f + spec.inset * 0.8f)
                val gap = w * 0.045f
                val cell = (w - m * 2 - gap) / 2f
                spec.srcs.take(4).forEachIndexed { i, src ->
                    val b = load(ctx, src, (cell * 1.2f).toInt()) ?: return@forEachIndexed
                    val x = m + (i % 2) * (cell + gap)
                    val y = m + (i / 2) * (cell + gap)
                    picture(b.asImageBitmap(), Rect(x, y, x + cell, y + cell), cell * 0.16f, crop = true)
                }
            }
            is FanArt -> {
                base(spec.color, lerp(spec.color, Color.Black, 0.3f))
                val n = spec.srcs.size.coerceAtMost(3)
                val h = w * (0.56f - spec.inset * 0.5f)
                spec.srcs.take(3).forEachIndexed { i, src ->
                    val b = load(ctx, src, h.toInt()) ?: return@forEachIndexed
                    val pos = i - (n - 1) / 2f
                    val cw = (h * b.width / b.height.coerceAtLeast(1)).coerceAtMost(h)
                    val cx = w / 2f + pos * w * 0.17f
                    val top = (w - h) / 2f + kotlin.math.abs(pos) * w * 0.03f
                    rotate(pos * 11f, Offset(cx, top + h)) { picture(b.asImageBitmap(), Rect(cx - cw / 2f, top, cx + cw / 2f, top + h), w * 0.04f, crop = true) }
                }
            }
            is PictureArt -> {
                val b = load(ctx, spec.src, px) ?: run { base(spec.color, lerp(spec.color, Color.Black, 0.3f)); return }
                val square = b.width.toFloat() / b.height.coerceAtLeast(1) in 0.92f..1.08f
                if (square && spec.inset == 0f) {
                    picture(b.asImageBitmap(), Rect(0f, 0f, w, w), 0f, crop = true)
                } else {
                    base(spec.color, lerp(spec.color, Color.Black, 0.3f))
                    val pad = w * (0.08f + spec.inset)
                    picture(b.asImageBitmap(), fit(b.width, b.height, Rect(pad, pad, w - pad, w - pad)), w * 0.04f)
                }
            }
        }
    }

    /** Tandem's tile base: a diagonal two-tone gradient with a soft light in the upper left. */
    private fun DrawScope.base(a: Color, b: Color) {
        val w = size.width
        drawRect(Brush.linearGradient(listOf(lerp(a, Color.White, 0.18f), a, b), start = Offset.Zero, end = Offset(w * 0.8f, w)))
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent), center = Offset(w * 0.18f, w * 0.08f), radius = w * 0.8f))
    }

    /** [img] in [r] (cropped to fill it, or stretched to it when it was fitted already) with rounded corners and a soft shadow. */
    private fun DrawScope.picture(img: ImageBitmap, r: Rect, radius: Float, crop: Boolean = false) {
        val shadow = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r.translate(0f, size.width * 0.015f), radius, radius)) }
        drawPath(shadow, Color.Black.copy(alpha = 0.28f))
        val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r, radius, radius)) }
        clipPath(clip) {
            if (crop) {
                val scale = maxOf(r.width / img.width, r.height / img.height)
                val sw = (r.width / scale).toInt().coerceAtLeast(1)
                val sh = (r.height / scale).toInt().coerceAtLeast(1)
                val sx = (img.width - sw) / 2
                val sy = (img.height - sh) / 2
                drawImage(img, androidx.compose.ui.unit.IntOffset(sx, sy), androidx.compose.ui.unit.IntSize(sw, sh), r.topLeft.int(), r.size.int())
            } else {
                drawImage(img, dstOffset = r.topLeft.int(), dstSize = r.size.int())
            }
        }
    }

    /** The largest rectangle of [w]:[h] that fits in [box], centred. */
    private fun fit(w: Int, h: Int, box: Rect): Rect {
        val s = minOf(box.width / w.coerceAtLeast(1), box.height / h.coerceAtLeast(1))
        val fw = w * s
        val fh = h * s
        return Rect(box.center.x - fw / 2f, box.center.y - fh / 2f, box.center.x + fw / 2f, box.center.y + fh / 2f)
    }

    private fun Offset.int() = androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())
    private fun Size.int() = androidx.compose.ui.unit.IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
}
