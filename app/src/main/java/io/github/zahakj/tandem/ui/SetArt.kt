package io.github.zahakj.tandem.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.zahakj.tandem.AndroidApps
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
import io.github.zahakj.tandem.data.ArtSets
import io.github.zahakj.tandem.data.FolderStyle
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.styleOf
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Tandem's art sets: an icon, a hero and a logo for every system, all drawn here in code (never a copied logo, box or
 * console photo: a silhouette of the kind of device Tandem draws itself, the system's colours and its plain name in a
 * font the phone already has). Four styles:
 *
 * - Pastel: a soft colour block with confetti and a white device sticker; the name as a fat outlined sticker.
 * - Neon: a dark arcade night with scanlines, a sunset over a glowing grid floor and the device in light tubes.
 * - Paper: a cartridge with a paper label; the hero is a printed box with stripes, halftone and an ink stamp.
 * - Mono: a thin outline of the device in the theme's accent on the theme's own background; the name spaced out.
 *
 * The pictures are drawn once per set, system and theme tone into cacheDir/art-sets (PNG icons and logos, WebP heroes)
 * on one background thread and handed to the rest of Tandem as files, so every place that shows a pack's art (the
 * grid, Spotlight, the bottom screen, the Systems page...) shows a set the same way. A missing picture is simply drawn
 * again. Which set a system wears is decided in data/ArtSets.kt.
 */

/** A set, as the pickers name it. */
enum class ArtSet(val id: String, @StringRes private val nameRes: Int) {
    Pastel(ArtSets.PASTEL, R.string.look_art_set_pastel),
    Neon(ArtSets.NEON, R.string.look_art_set_neon),
    Paper(ArtSets.PAPER, R.string.look_art_set_paper),
    Mono(ArtSets.MONO, R.string.look_art_set_mono);

    val label: String get() = Tx.s(nameRes)

    companion object {
        fun of(id: String?): ArtSet? = entries.firstOrNull { it.id == id }
    }
}

/** What a set draws for one system: its names, the kind of device, and two colours from its family. */
internal class SetSystem(val id: String, val name: String, val short: String, val device: Device, val color: Color, val second: Color) {
    val seed: Int get() = id.hashCode()

    companion object {
        /** [short] stands in for the names of a system that isn't in the library (previews). */
        fun of(id: String, short: String? = null): SetSystem {
            val p = Libraries.byId(id)?.platform
            val name = p?.name ?: short ?: id.uppercase(Locale.ROOT)
            val shortName = p?.shortName?.ifBlank { null } ?: short ?: name
            val device = if (id == AndroidApps.PLATFORM_ID) Device.Phone else Devices.of(id)
            val color = SystemColors.of(id)
            return SetSystem(id, name, shortName, device, color, familyColor(id, device, color))
        }

        /** A second colour shared by a family of systems (one maker's, or one kind of device's). */
        private val families: Map<String, Long> = mapOf(
            "Nintendo" to 0xFFFF7A7A, "Sony" to 0xFF5D8BFF, "Sega" to 0xFF35C8F2, "Microsoft" to 0xFF6BD66F, "Atari" to 0xFFFF9A4D,
            "NEC" to 0xFFFFB84D, "SNK" to 0xFFFFD84D, "Bandai" to 0xFFB98CFF, "Commodore" to 0xFF7FD6C9, "Arcade" to 0xFFFF5FA8,
            "PC" to 0xFF56C7F5, "Android" to 0xFF7BE08F, "Panasonic" to 0xFFFF8C69, "Sinclair" to 0xFFFFC857, "Amstrad" to 0xFF4FD1B5,
            "Mattel" to 0xFFE6B450, "Coleco" to 0xFF7F9CF5, "Sharp" to 0xFFFF6F91, "Philips" to 0xFF6FA8FF,
        )

        private fun familyColor(id: String, device: Device, color: Color): Color {
            SystemMakers.of(id)?.let { m -> families[m]?.let { return lerp(Color(it), color, 0.2f) } }
            return when (device) {
                Device.Computer -> Color(0xFF7FD6C9)
                Device.Phone -> Color(0xFF7BE08F)
                Device.Cartridge -> Color(0xFFFF7FBF)
                Device.Arcade -> Color(0xFFFF5FA8)
                Device.Screen, Device.Book -> Color(0xFFB9A6FF)
                else -> lerp(color, Color(0xFFB9A6FF), 0.5f)
            }
        }
    }
}

/** The theme a set adapts to: light or dark, and (for Mono) its own colours. */
internal data class SetTone(val dark: Boolean, val bg: Color, val ink: Color, val tint: Color) {
    companion object {
        fun of(t: ThemeSpec) = SetTone(t.background.luminance() < 0.45f, t.background, t.text, t.accent)
    }
}

/** The fonts the sets write names in: Tandem's own rounded one and Android's serif, condensed and light. */
internal class SetFonts private constructor(ctx: Context) {
    val round: Typeface = runCatching { Typeface.create(ctx.resources.getFont(R.font.nunito), 900, false) }.getOrDefault(Typeface.DEFAULT_BOLD)
    val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC)
    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

    companion object {
        @Volatile private var one: SetFonts? = null
        fun get(ctx: Context): SetFonts = one ?: SetFonts(ctx.applicationContext).also { one = it }
    }
}

/** The drawn pictures as files: looked up (cheaply) by the rest of Tandem, drawn in the background when missing. */
object SetArt {
    /** Raised whenever the drawing changes, so old pictures are never shown again. */
    private const val VERSION = 1

    enum class Kind(val ext: String) { Icon("png"), Hero("webp"), Logo("png") }

    const val ICON_PX = 384
    const val HERO_W = 1600
    const val HERO_H = 900

    /** Bumped (on the main thread, a little after a batch) when new pictures are ready: screens look again. */
    var revision by mutableIntStateOf(0)
        private set

    private val ready = ConcurrentHashMap<String, Boolean>()
    private val pending: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "art-sets").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val bump = Runnable { revision++ }

    @Volatile private var toneFor: Pair<Any?, SetTone>? = null

    /** The tone of the theme chosen now (not the one blending towards it), kept while the look is the same. */
    private fun tone(): SetTone {
        val look = Store.settings.look
        toneFor?.let { (k, t) -> if (k === look) return t }
        return SetTone.of(Themes.target).also { toneFor = look to it }
    }

    private fun root(ctx: Context) = File(ctx.cacheDir, "art-sets")

    /** The folder for [set] in [tone]: a new one whenever the tone (or, for Mono, the theme's colours) changes. */
    private fun variant(set: String, tone: SetTone): String {
        val parts = if (set == ArtSets.MONO) listOf(VERSION, tone.dark, tone.bg.toArgb(), tone.ink.toArgb(), tone.tint.toArgb()) else listOf(VERSION, tone.dark)
        return (if (tone.dark) "d" else "l") + parts.hashCode().toUInt().toString(36)
    }

    private fun fileFor(ctx: Context, set: String, tone: SetTone, sys: SetSystem, kind: Kind): File {
        // A logo carries its text, which follows the language (Android games): the file is named after it.
        val tag = if (kind == Kind.Logo) "-" + logoText(set, sys).hashCode().toUInt().toString(36) else ""
        // A system id from someone's own platforms.json could hold anything: only safe characters reach the file name.
        val id = sys.id.lowercase(Locale.ROOT).map { if (it in 'a'..'z' || it in '0'..'9' || it == '_') it else '_' }.joinToString("").take(48)
            .let { if (it == sys.id) it else it + "_" + sys.id.hashCode().toUInt().toString(36) }
        return File(root(ctx), "$set/${variant(set, tone)}/$id-${kind.name.lowercase(Locale.ROOT)}$tag.${kind.ext}")
    }

    /**
     * [set]'s picture of [kind] for system [platformId], when it is drawn already; otherwise null, and it is drawn in
     * the background ([revision] changes when it is ready).
     */
    fun file(set: String, platformId: String, kind: Kind): File? {
        if (!ArtSets.isSet(set)) return null
        val ctx = runCatching { Tx.context }.getOrNull() ?: return null
        val tone = tone()
        val sys = SetSystem.of(platformId)
        val f = fileFor(ctx, set, tone, sys, kind)
        if (ready[f.path] == true) return f
        if (f.isFile) { ready[f.path] = true; return f }
        enqueue(ctx, set, tone, sys, kind, f)
        // Read, so a screen that asked while the picture was missing composes again once it is drawn.
        revision
        return null
    }

    private fun enqueue(ctx: Context, set: String, tone: SetTone, sys: SetSystem, kind: Kind, f: File) {
        if (!pending.add(f.path)) return
        worker.execute {
            try {
                if (!f.isFile) render(ctx, set, tone, sys, kind, f)
                if (f.isFile) ready[f.path] = true
            } finally {
                pending.remove(f.path)
                main.removeCallbacks(bump)
                main.postDelayed(bump, 250)
            }
        }
    }

    /** The set [platformId] wears now: its own choice ([style], else its saved one) over the global set. */
    fun setOf(platformId: String, style: FolderStyle? = null): String? =
        ArtSets.setFor((style ?: IconGridStore.config.styleOf("s:$platformId")).artSet, Store.settings.look.artSet)

    /**
     * Draws ahead every picture the library's systems will ask for (icons first, then logos, then heroes), and
     * drops the pictures of other tones of the sets in use. Cheap when they are drawn already.
     */
    fun warmLibrary() {
        val ids = Libraries.all.orEmpty().map { it.platform.id }
        val wanted = ids.mapNotNull { id -> setOf(id)?.let { id to it } }
        if (wanted.isEmpty()) return
        for (kind in Kind.entries) for ((id, set) in wanted) file(set, id, kind)
        val ctx = runCatching { Tx.context }.getOrNull() ?: return
        val tone = tone()
        val keep = wanted.map { it.second }.distinct().associateWith { variant(it, tone) }
        worker.execute {
            for ((set, v) in keep) File(root(ctx), set).listFiles().orEmpty().filter { it.isDirectory && it.name != v }.forEach { it.deleteRecursively() }
        }
    }

    /** Draws ahead every set's pictures for one system (its artwork studio is open). */
    fun warmSystem(platformId: String) {
        for (kind in Kind.entries) for (set in ArtSets.ids) file(set, platformId, kind)
    }

    private fun render(ctx: Context, set: String, tone: SetTone, sys: SetSystem, kind: Kind, out: File) {
        val fonts = SetFonts.get(ctx)
        val (w, h) = when (kind) {
            Kind.Icon -> ICON_PX to ICON_PX
            Kind.Hero -> HERO_W to HERO_H
            Kind.Logo -> logoSize(set, sys, fonts)
        }
        val bmp = ImageBitmap(w, h)
        val canvas = androidx.compose.ui.graphics.Canvas(bmp)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            when (kind) {
                Kind.Icon -> paintSetIcon(set, sys, tone, fonts)
                Kind.Hero -> paintSetHero(set, sys, tone, fonts)
                Kind.Logo -> paintSetLogo(set, sys, tone, fonts)
            }
        }
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".part")
        val ok = runCatching {
            tmp.outputStream().use { o ->
                val format = if (kind == Kind.Hero) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.PNG
                bmp.asAndroidBitmap().compress(format, if (kind == Kind.Hero) 88 else 100, o)
            }
        }.getOrDefault(false)
        if (ok) tmp.renameTo(out) else tmp.delete()
    }
}

// --- Previews (drawn live, for the pickers) ---------------------------------------------------------------------

/** [set]'s icon for system [id], drawn straight onto the screen. */
@Composable
fun SetIconPreview(set: String, id: String, short: String? = null, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val fonts = remember { SetFonts.get(ctx) }
    val tone = SetTone.of(Themes.target)
    val sys = remember(id, short, io.github.zahakj.tandem.Lang.revision.intValue) { SetSystem.of(id, short) }
    Canvas(modifier.fillMaxSize()) { paintSetIcon(set, sys, tone, fonts) }
}

/** [set]'s hero for system [id] with its logo over it, as the other screen shows them. */
@Composable
fun SetHeroPreview(set: String, id: String, short: String? = null, modifier: Modifier = Modifier, logo: Boolean = true) {
    val ctx = LocalContext.current
    val fonts = remember { SetFonts.get(ctx) }
    val tone = SetTone.of(Themes.target)
    val sys = remember(id, short, io.github.zahakj.tandem.Lang.revision.intValue) { SetSystem.of(id, short) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { paintSetHero(set, sys, tone, fonts) }
        if (logo) {
            val (lw, lh) = remember(set, sys) { logoSize(set, sys, fonts) }
            Canvas(Modifier.fillMaxWidth(0.62f).aspectRatio(lw.toFloat() / lh)) { paintSetLogo(set, sys, tone, fonts) }
        }
    }
}

/** [set]'s logo for system [id] alone, as the title over a hero. */
@Composable
fun SetLogoPreview(set: String, id: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val fonts = remember { SetFonts.get(ctx) }
    val tone = SetTone.of(Themes.target)
    val sys = remember(id, io.github.zahakj.tandem.Lang.revision.intValue) { SetSystem.of(id) }
    val (lw, lh) = remember(set, sys) { logoSize(set, sys, fonts) }
    Canvas(modifier.aspectRatio(lw.toFloat() / lh)) { paintSetLogo(set, sys, tone, fonts) }
}

// --- Drawing ------------------------------------------------------------------------------------------------------

internal fun DrawScope.paintSetIcon(set: String, s: SetSystem, t: SetTone, f: SetFonts) = when (set) {
    ArtSets.NEON -> neonIcon(s, t, f)
    ArtSets.PAPER -> paperIcon(s, t, f)
    ArtSets.MONO -> monoIcon(s, t, f)
    else -> pastelIcon(s, t, f)
}

internal fun DrawScope.paintSetHero(set: String, s: SetSystem, t: SetTone, f: SetFonts) = when (set) {
    ArtSets.NEON -> neonHero(s, t)
    ArtSets.PAPER -> paperHero(s, t)
    ArtSets.MONO -> monoHero(s, t)
    else -> pastelHero(s, t)
}

/** The text a set's logo writes: the system's name (spaced capitals for Mono). */
internal fun logoText(set: String, s: SetSystem): String = if (set == ArtSets.MONO) s.name.uppercase(Locale.ROOT) else s.name

private const val LOGO_FONT = 150f

/** How big a set's logo bitmap is: as wide as its name needs, never wider than a hero. */
internal fun logoSize(set: String, s: SetSystem, f: SetFonts): Pair<Int, Int> {
    val p = logoPaint(set, f, LOGO_FONT)
    val tw = p.measureText(logoText(set, s))
    val pad = LOGO_FONT * if (set == ArtSets.PAPER) 0.9f else 0.45f
    val w = (tw + pad * 2).coerceIn(LOGO_FONT * 2.4f, 1800f)
    val h = LOGO_FONT * if (set == ArtSets.PAPER) 1.9f else 1.7f
    return w.toInt() to h.toInt()
}

private fun logoPaint(set: String, f: SetFonts, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    textAlign = Paint.Align.CENTER
    when (set) {
        ArtSets.NEON -> { typeface = f.condensed; letterSpacing = 0.02f }
        ArtSets.PAPER -> { typeface = f.serif; letterSpacing = 0.03f }
        ArtSets.MONO -> { typeface = f.light; letterSpacing = 0.22f }
        else -> { typeface = f.round; letterSpacing = 0.01f }
    }
}

internal fun DrawScope.paintSetLogo(set: String, s: SetSystem, t: SetTone, f: SetFonts) {
    val w = size.width
    val h = size.height
    val text = logoText(set, s)
    val ratio = if (set == ArtSets.PAPER) 1.9f else 1.7f
    val p = logoPaint(set, f, h / ratio)
    val pad = p.textSize * if (set == ArtSets.PAPER) 0.9f else 0.45f
    p.fit(text, w - pad * 2)
    val fs = p.textSize
    val cx = w / 2f
    val cy = h / 2f
    val canvas = drawContext.canvas.nativeCanvas
    fun draw(paint: Paint) { val fm = paint.fontMetrics; canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, paint) }
    when (set) {
        ArtSets.NEON -> {
            val glow = Paint(p).apply { style = Paint.Style.STROKE; strokeWidth = fs * 0.1f; strokeJoin = Paint.Join.ROUND; color = s.color.toArgb(); setShadowLayer(fs * 0.28f, 0f, 0f, s.color.toArgb()) }
            draw(glow)
            val tube = Paint(p).apply { style = Paint.Style.STROKE; strokeWidth = fs * 0.04f; strokeJoin = Paint.Join.ROUND; color = lerp(s.color, Color.White, 0.75f).toArgb() }
            draw(tube)
        }
        ArtSets.PAPER -> {
            val paper = Color(0xFFF7F0DE)
            val ink = Color(0xFF2B2522)
            val r = h * 0.14f
            drawRoundRect(Color.Black.copy(alpha = 0.22f), Offset(h * 0.06f, h * 0.1f), Size(w - h * 0.1f, h * 0.86f), CornerRadius(r))
            drawRoundRect(paper, Offset(h * 0.03f, h * 0.04f), Size(w - h * 0.1f, h * 0.86f), CornerRadius(r))
            val tab = h * 0.2f
            drawRect(s.color, Offset(h * 0.03f + r * 0.4f, h * 0.04f), Size(tab, h * 0.86f))
            drawRect(s.second, Offset(h * 0.03f + r * 0.4f + tab, h * 0.04f), Size(tab * 0.3f, h * 0.86f))
            drawRect(s.color, Offset(w - h * 0.07f - r * 0.4f - tab, h * 0.04f), Size(tab, h * 0.86f))
            drawRoundRect(ink, Offset(h * 0.03f, h * 0.04f), Size(w - h * 0.1f, h * 0.86f), CornerRadius(r), style = Stroke(h * 0.025f))
            p.color = ink.toArgb()
            canvas.drawText(text, cx + tab * 0.15f, h * 0.47f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2f, p)
        }
        ArtSets.MONO -> {
            p.color = t.ink.toArgb()
            draw(p)
            val y = cy + fs * 0.62f
            drawLine(t.tint, Offset(cx - fs * 0.6f, y), Offset(cx + fs * 0.6f, y), fs * 0.035f, StrokeCap.Round)
        }
        else -> {
            val deep = lerp(s.color, Color.Black, 0.45f)
            val shadow = Paint(p).apply { style = Paint.Style.FILL_AND_STROKE; strokeWidth = fs * 0.2f; strokeJoin = Paint.Join.ROUND; color = deep.copy(alpha = 0.45f).toArgb() }
            canvas.drawText(text, cx, cy + fs * 0.07f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2f, shadow)
            val edge = Paint(p).apply { style = Paint.Style.FILL_AND_STROKE; strokeWidth = fs * 0.18f; strokeJoin = Paint.Join.ROUND; color = deep.toArgb() }
            draw(edge)
            p.color = android.graphics.Color.WHITE
            draw(p)
        }
    }
}

// --- Helpers ---

private fun Paint.fit(s: String, maxW: Float) {
    val w = measureText(s)
    if (w > maxW && w > 0f) textSize *= maxW / w
}

/** [text] centred on ([cx], [cy]) in [p], shrunk to fit [maxW]. */
private fun DrawScope.label(text: String, cx: Float, cy: Float, maxW: Float, p: Paint) {
    p.fit(text, maxW)
    val fm = p.fontMetrics
    drawContext.canvas.nativeCanvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, p)
}

/** A label size that suits [text]'s length on an icon [w] wide. */
private fun labelSize(text: String, w: Float) = w * when {
    text.length <= 3 -> 0.2f
    text.length <= 5 -> 0.16f
    text.length <= 7 -> 0.13f
    else -> 0.11f
}

private fun DrawScope.device(d: Device, x: Float, y: Float, side: Float, body: Color, detail: Color, stroke: Float = 0f) {
    val style = if (stroke > 0f) Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round) else androidx.compose.ui.graphics.drawscope.Fill
    drawDeviceShape(d, x, y, side, side, SolidColor(body), detail, style)
}

/** A neon tube: the outline drawn wide and faint, then thin and bright. */
private fun DrawScope.neonDevice(d: Device, x: Float, y: Float, side: Float, c: Color) {
    for ((wid, al) in listOf(0.1f to 0.1f, 0.055f to 0.22f)) device(d, x, y, side, c.copy(alpha = al), c.copy(alpha = al), side * wid)
    val bright = lerp(c, Color.White, 0.55f)
    device(d, x, y, side, bright, bright, side * 0.022f)
}

private fun DrawScope.confetti(seed: Int, n: Int, c: Color, alpha: Float, rMin: Float, rMax: Float) {
    val rnd = Random(seed)
    repeat(n) {
        val r = rMin + rnd.nextFloat() * (rMax - rMin)
        drawCircle(c.copy(alpha = alpha * (0.5f + rnd.nextFloat() * 0.5f)), r, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height))
    }
}

private fun DrawScope.sparkle(c: Color, cx: Float, cy: Float, r: Float) {
    val p = Path()
    for (i in 0 until 8) {
        val a = -PI / 2 + i * PI / 4
        val rr = if (i % 2 == 0) r else r * 0.28f
        val x = cx + (rr * cos(a)).toFloat()
        val y = cy + (rr * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, c)
}

private fun DrawScope.scanlines(step: Float, c: Color, alpha: Float) {
    var y = 0f
    while (y < size.height) { drawRect(c.copy(alpha = alpha), Offset(0f, y), Size(size.width, step * 0.45f)); y += step }
}

/** Fine paper grain: tiny dots, fewer on a small preview. */
private fun DrawScope.grain(seed: Int, ink: Color, alpha: Float, x: Float = 0f, y: Float = 0f, w: Float = size.width, h: Float = size.height) {
    val rnd = Random(seed)
    val n = ((w * h) / 360f).toInt().coerceIn(40, 4000)
    val r = (size.minDimension / 900f).coerceAtLeast(0.6f)
    repeat(n) { drawCircle(ink.copy(alpha = alpha * rnd.nextFloat()), r * (0.6f + rnd.nextFloat()), Offset(x + rnd.nextFloat() * w, y + rnd.nextFloat() * h)) }
}

// --- Pastel ---

private fun pastelColors(s: SetSystem, t: SetTone): Pair<Color, Color> =
    if (t.dark) lerp(s.color, Color.White, 0.12f) to lerp(lerp(s.second, Color(0xFF1C1830), 0.3f), s.color, 0.2f)
    else lerp(s.color, Color.White, 0.5f) to lerp(s.second, Color.White, 0.45f)

private fun DrawScope.pastelIcon(s: SetSystem, t: SetTone, f: SetFonts) {
    val w = size.width
    val h = size.height
    val (a, b) = pastelColors(s, t)
    drawRect(Brush.linearGradient(listOf(a, b), Offset.Zero, Offset(w, h)))
    drawCircle(Color.White.copy(alpha = 0.22f), w * 0.42f, Offset(w * 0.88f, h * 0.06f))
    drawCircle(Color.White.copy(alpha = 0.12f), w * 0.26f, Offset(w * 0.04f, h * 0.98f))
    confetti(s.seed, 8, Color.White, 0.6f, w * 0.01f, w * 0.022f)
    val deep = lerp(s.color, Color.Black, if (t.dark) 0.55f else 0.5f)
    val side = w * 0.5f
    val x = (w - side) / 2f
    val y = h * 0.1f
    device(s.device, x, y + w * 0.03f, side, deep.copy(alpha = 0.3f), Color.Transparent)
    device(s.device, x, y, side, Color.White, lerp(a, s.color, 0.7f))
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = f.round; textAlign = Paint.Align.CENTER; textSize = labelSize(s.short, w)
        color = (if (t.dark) Color.White else deep).toArgb()
        if (t.dark) setShadowLayer(w * 0.02f, 0f, w * 0.01f, deep.toArgb())
    }
    label(s.short.take(12), w / 2f, h * 0.79f, w * 0.84f, p)
}

private fun DrawScope.pastelHero(s: SetSystem, t: SetTone) {
    val w = size.width
    val h = size.height
    val (a, b) = pastelColors(s, t)
    drawRect(Brush.linearGradient(listOf(lerp(a, Color.White, 0.1f), a, b), Offset.Zero, Offset(w, h)))
    drawCircle(lerp(s.second, Color.White, 0.35f).copy(alpha = 0.35f), h * 0.42f, Offset(w * 0.14f, h * 0.18f))
    drawCircle(lerp(s.color, Color.White, 0.25f).copy(alpha = 0.3f), h * 0.58f, Offset(w * 0.92f, h * 0.98f))
    drawCircle(Color.White.copy(alpha = 0.14f), h * 0.3f, Offset(w * 0.55f, -h * 0.04f))
    // A soft dot pattern.
    val step = h / 22f
    var yy = step / 2f
    var row = 0
    while (yy < h) {
        var xx = if (row % 2 == 0) step / 2f else step
        while (xx < w) { drawCircle(Color.White.copy(alpha = 0.1f), step * 0.09f, Offset(xx, yy)); xx += step }
        yy += step; row++
    }
    confetti(s.seed, 26, Color.White, 0.55f, h * 0.006f, h * 0.014f)
    val rnd = Random(s.seed * 31)
    repeat(6) { sparkle(Color.White.copy(alpha = 0.75f), rnd.nextFloat() * w, rnd.nextFloat() * h, h * (0.02f + rnd.nextFloat() * 0.025f)) }
    val deep = lerp(s.color, Color.Black, 0.5f)
    // A faint echo, tilted, low on the left; the big sticker on the right.
    val small = h * 0.36f
    rotate(12f, Offset(w * 0.12f + small / 2f, h * 0.66f + small / 2f)) { device(s.device, w * 0.06f, h * 0.6f, small, Color.White.copy(alpha = 0.3f), Color.Transparent) }
    val side = h * 0.76f
    val x = w * 0.62f
    val y = h * 0.13f
    rotate(-8f, Offset(x + side / 2f, y + side / 2f)) {
        device(s.device, x, y + h * 0.03f, side, deep.copy(alpha = 0.22f), Color.Transparent)
        device(s.device, x, y, side, Color.White.copy(alpha = 0.92f), lerp(a, s.color, 0.7f))
    }
}

// --- Neon ---

private fun night(t: SetTone) = if (t.dark) Color(0xFF0A0918) else Color(0xFF17132E)

private fun DrawScope.neonIcon(s: SetSystem, t: SetTone, f: SetFonts) {
    val w = size.width
    val h = size.height
    val n = night(t)
    drawRect(Brush.verticalGradient(listOf(n, lerp(n, s.color, 0.24f))))
    // A little grid floor.
    val horizon = h * 0.7f
    val line = s.second.copy(alpha = 0.32f)
    for (i in 1..5) { val k = i / 5f; val yy = horizon + (h - horizon) * k * k; drawLine(line, Offset(0f, yy), Offset(w, yy), w * 0.005f) }
    for (i in -4..4) drawLine(line, Offset(w / 2f, horizon), Offset(w / 2f + i * w * 0.32f, h), w * 0.005f)
    scanlines(h / 90f, Color.Black, 0.22f)
    val inset = w * 0.05f
    drawRoundRect(s.second.copy(alpha = 0.14f), Offset(inset, inset), Size(w - inset * 2, h - inset * 2), CornerRadius(w * 0.09f), style = Stroke(w * 0.04f))
    drawRoundRect(lerp(s.second, Color.White, 0.3f).copy(alpha = 0.6f), Offset(inset, inset), Size(w - inset * 2, h - inset * 2), CornerRadius(w * 0.09f), style = Stroke(w * 0.01f))
    val side = w * 0.48f
    neonDevice(s.device, (w - side) / 2f, h * 0.13f, side, s.color)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = f.condensed; textAlign = Paint.Align.CENTER; textSize = labelSize(s.short, w) * 1.05f
        color = lerp(s.color, Color.White, 0.65f).toArgb(); setShadowLayer(w * 0.035f, 0f, 0f, s.color.toArgb())
    }
    label(s.short.take(12), w / 2f, h * 0.79f, w * 0.8f, p)
}

private fun DrawScope.neonHero(s: SetSystem, t: SetTone) {
    val w = size.width
    val h = size.height
    val n = night(t)
    val horizon = h * 0.64f
    val sky = Brush.verticalGradient(listOf(n, lerp(n, Color(0xFF3A1458), 0.6f), lerp(n, s.color, 0.35f)), 0f, horizon)
    drawRect(sky, Offset.Zero, Size(w, horizon))
    // Stars.
    val rnd = Random(s.seed)
    repeat(70) { drawCircle(Color.White.copy(alpha = 0.2f + rnd.nextFloat() * 0.6f), h * (0.0015f + rnd.nextFloat() * 0.003f), Offset(rnd.nextFloat() * w, rnd.nextFloat() * horizon * 0.8f)) }
    // The sun, striped low down, sitting on the horizon to the right.
    val sun = Offset(w * 0.76f, horizon - h * 0.02f)
    val r = h * 0.27f
    drawCircle(s.color.copy(alpha = 0.18f), r * 1.35f, sun)
    drawCircle(Brush.verticalGradient(listOf(lerp(s.second, Color(0xFFFFE08A), 0.5f), s.color, lerp(s.color, Color(0xFFFF3D8B), 0.5f)), sun.y - r, sun.y + r), r, sun)
    for (i in 0 until 6) {
        val yy = sun.y - r * 0.15f + i * r * 0.2f
        drawRect(sky, Offset(sun.x - r * 1.1f, yy), Size(r * 2.2f, r * (0.03f + i * 0.022f)))
    }
    // The floor and its grid, running to a point under the sun.
    drawRect(Brush.verticalGradient(listOf(lerp(n, s.color, 0.12f), n), horizon, h), Offset(0f, horizon), Size(w, h - horizon))
    val grid = s.second
    val vanish = Offset(w * 0.5f, horizon)
    for (pass in 0..1) {
        val (wid, al) = if (pass == 0) h * 0.012f to 0.12f else h * 0.003f to 0.75f
        for (i in 0..9) { val k = i / 9f; val yy = horizon + (h - horizon) * k * k; drawLine(grid.copy(alpha = al), Offset(0f, yy), Offset(w, yy), wid) }
        for (i in -12..12) drawLine(grid.copy(alpha = al), vanish, Offset(vanish.x + i * w * 0.12f, h), wid)
    }
    drawLine(lerp(s.color, Color.White, 0.4f), Offset(0f, horizon), Offset(w, horizon), h * 0.004f)
    // The device standing on the floor at the left, in light tubes.
    val side = h * 0.48f
    neonDevice(s.device, w * 0.07f, horizon - side * 0.96f, side, s.second)
    scanlines(h / 180f, Color.Black, 0.18f)
}

// --- Paper ---

private val PaperCream = Color(0xFFF6EFDD)
private val PaperInk = Color(0xFF2B2522)

private fun DrawScope.paperIcon(s: SetSystem, t: SetTone, f: SetFonts) {
    val w = size.width
    val h = size.height
    val shell = if (t.dark) Color(0xFF3B3842) else Color(0xFFCBC5BB)
    drawRect(Brush.verticalGradient(listOf(lerp(shell, Color.White, 0.12f), shell, lerp(shell, Color.Black, 0.12f))))
    val groove = lerp(shell, Color.Black, 0.2f)
    for (k in 0..4) drawRoundRect(groove, Offset(w * (0.17f + k * 0.14f), h * 0.05f), Size(w * 0.08f, h * 0.11f), CornerRadius(w * 0.02f))
    // The label.
    val lx = w * 0.1f
    val ly = h * 0.22f
    val lw = w * 0.8f
    val lh = h * 0.6f
    drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(lx, ly + h * 0.015f), Size(lw, lh), CornerRadius(w * 0.04f))
    drawRoundRect(PaperCream, Offset(lx, ly), Size(lw, lh), CornerRadius(w * 0.04f))
    drawRect(s.color, Offset(lx, ly + h * 0.03f), Size(lw, h * 0.11f))
    drawRect(s.second, Offset(lx, ly + h * 0.15f), Size(lw, h * 0.025f))
    grain(s.seed, PaperInk, 0.12f, lx, ly, lw, lh)
    val side = w * 0.26f
    device(s.device, (w - side) / 2f, h * 0.41f, side, lerp(s.color, PaperInk, 0.6f), PaperCream)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = f.serif; textAlign = Paint.Align.CENTER; textSize = labelSize(s.short, w) * 0.85f; color = PaperInk.toArgb(); letterSpacing = 0.03f }
    label(s.short.take(12), w / 2f, h * 0.74f, lw * 0.86f, p)
    // The edge connector.
    for (k in 0..6) drawRect(groove, Offset(w * (0.2f + k * 0.09f), h * 0.88f), Size(w * 0.045f, h * 0.07f))
}

private fun DrawScope.paperHero(s: SetSystem, t: SetTone) {
    val w = size.width
    val h = size.height
    val paper = if (t.dark) Color(0xFF2A2621) else Color(0xFFF3EBD9)
    val ink = if (t.dark) Color(0xFFEDE3CF) else PaperInk
    drawRect(paper)
    grain(s.seed, ink, 0.08f)
    // Printed stripes running across, like an old box.
    val bands = listOf(s.color, s.second, lerp(s.color, Color(0xFFFFD26B), 0.5f), ink)
    rotate(-18f, Offset(w / 2f, h / 2f)) {
        bands.forEachIndexed { i, c -> drawRect(c.copy(alpha = if (c == ink) 0.85f else 1f), Offset(-w * 0.3f, h * (0.66f + i * 0.085f)), Size(w * 1.6f, h * 0.06f)) }
    }
    // Halftone behind the stamp.
    val centre = Offset(w * 0.8f, h * 0.38f)
    val r = h * 0.4f
    val step = h / 36f
    var yy = centre.y - r
    while (yy < centre.y + r) {
        var xx = centre.x - r
        while (xx < centre.x + r) {
            val d = (Offset(xx, yy) - centre).getDistance() / r
            if (d < 1f) drawCircle(s.color.copy(alpha = 0.55f), step * 0.42f * (1f - d), Offset(xx, yy))
            xx += step
        }
        yy += step
    }
    val side = h * 0.52f
    device(s.device, w * 0.8f - side / 2f, h * 0.38f - side / 2f, side, ink.copy(alpha = 0.92f), paper)
    // A printed border.
    val m = h * 0.04f
    drawRect(ink.copy(alpha = 0.35f), Offset(m, m), Size(w - m * 2, h - m * 2), style = Stroke(h * 0.004f))
    drawRect(ink.copy(alpha = 0.2f), Offset(m * 1.5f, m * 1.5f), Size(w - m * 3, h - m * 3), style = Stroke(h * 0.002f))
}

// --- Mono ---

private fun DrawScope.monoIcon(s: SetSystem, t: SetTone, f: SetFonts) {
    val w = size.width
    val h = size.height
    drawRect(lerp(t.bg, t.ink, 0.05f))
    val side = w * 0.5f
    device(s.device, (w - side) / 2f, h * 0.13f, side, t.tint, t.tint, w * 0.024f)
    drawLine(t.ink.copy(alpha = 0.25f), Offset(w * 0.32f, h * 0.71f), Offset(w * 0.68f, h * 0.71f), w * 0.006f, StrokeCap.Round)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = f.light; textAlign = Paint.Align.CENTER; textSize = labelSize(s.short, w) * 0.82f
        color = t.ink.copy(alpha = 0.9f).toArgb(); letterSpacing = 0.14f
    }
    label(s.short.take(12).uppercase(Locale.ROOT), w / 2f, h * 0.83f, w * 0.84f, p)
}

private fun DrawScope.monoHero(s: SetSystem, t: SetTone) {
    val w = size.width
    val h = size.height
    drawRect(t.bg)
    drawRect(Brush.radialGradient(listOf(lerp(t.bg, t.tint, 0.1f), t.bg), Offset(w * 0.72f, h * 0.42f), w * 0.6f))
    val step = h * 0.045f
    var yy = step
    while (yy < h) {
        var xx = step
        while (xx < w) { drawCircle(t.ink.copy(alpha = 0.09f), h * 0.0035f, Offset(xx, yy)); xx += step }
        yy += step
    }
    val side = h * 0.82f
    val x = w * 0.58f
    val y = h * 0.09f
    device(s.device, x - w * 0.02f, y + h * 0.025f, side, t.ink.copy(alpha = 0.14f), t.ink.copy(alpha = 0.14f), w * 0.004f)
    device(s.device, x, y, side, t.tint, t.tint, w * 0.005f)
    for (ly in listOf(0.1f, 0.9f)) drawLine(t.ink.copy(alpha = 0.14f), Offset(w * 0.04f, h * ly), Offset(w * 0.96f, h * ly), h * 0.002f)
    for (k in 0..10) drawLine(t.ink.copy(alpha = 0.2f), Offset(w * (0.04f + k * 0.012f), h * 0.9f), Offset(w * (0.04f + k * 0.012f), h * (if (k % 5 == 0) 0.87f else 0.885f)), h * 0.002f)
}

// --- The set picker's miniature -------------------------------------------------------------------------------------

/**
 * [set] on a few of the user's systems: one hero with its logo and four icons, for Look & feel › Art set. "" (no
 * set) shows the art Icons from gives instead.
 */
@Composable
fun ArtSetScene(set: String, modifier: Modifier = Modifier) {
    val systems = previewSystems(4)
    val on = ArtSets.isSet(set)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val (first, firstShort) = systems.first()
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            if (on) {
                SetHeroPreview(set, first, firstShort, Modifier.fillMaxSize())
            } else {
                val hero = ThemePacks.systemHero(first, FolderStyle(artSet = ArtSets.PACK))
                if (hero != null) AsyncImage(hero, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else ColorSweep(Palette.forName(first), Modifier.fillMaxSize())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id, short) in systems.take(4)) {
                BoxWithConstraints(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))) {
                    val pack = if (on) null else ThemePacks.packSystemIcon(id)
                    when {
                        on -> SetIconPreview(set, id, short)
                        pack != null -> AsyncImage(pack, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else -> DrawnIcon(short, Devices.of(id), Palette.forName(id), maxWidth)
                    }
                }
            }
        }
    }
}
