package io.github.zahakj.tandem.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.zahakj.tandem.Platform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Drawn system and collection icons. Every system gets a simple, original silhouette of its kind of device (a
 * clamshell handheld, a console, a disc...) and its short name, both in the theme's [IconStyle]. When the chosen
 * theme pack brings its own icons, those are shown instead (see [ThemePacks.systemIcon]).
 */
enum class Device { Clamshell, Landscape, Brick, Hybrid, Console, Pad, Disc, Cartridge, Computer, Phone, Arcade, Screen, Book, Heart, Trophy, Star, Clock, Folder }

object Devices {
    private val byId: Map<String, Device> = buildMap {
        fun put(d: Device, vararg ids: String) = ids.forEach { put(it, d) }
        put(Device.Clamshell, "nds", "n3ds", "gameandwatch")
        put(Device.Landscape, "gba", "psp", "psvita", "gamegear", "atarilynx", "ngp", "ngpc", "wonderswan", "wonderswancolor", "supervision", "pokemini", "megaduck", "arduboy")
        put(Device.Brick, "gb", "gbc", "virtualboy")
        put(Device.Hybrid, "switch")
        put(Device.Console, "nes", "snes", "n64", "genesis", "mastersystem", "sg1000", "sega32x", "fds", "atari2600", "atari5200", "atari7800", "atarijaguar",
            "colecovision", "intellivision", "vectrex", "channelf", "odyssey2", "vc4000", "arcadia", "pcengine", "supergrafx", "uzebox")
        put(Device.Pad, "ps2", "ps3", "xbox", "xbox360", "wii", "wiiu", "gc", "dreamcast", "pcfx")
        put(Device.Disc, "psx", "segacd", "saturn", "pcenginecd", "neogeocd", "3do", "cdimono1")
        put(Device.Arcade, "arcade", "neogeo", "naomi", "atomiswave", "model3", "stv", "cannonball")
        put(Device.Computer, "windows", "dos", "amiga", "c64", "pet", "plus4", "vic20", "amstradcpc", "msx", "zxspectrum", "zx81", "x1", "x68000",
            "bbcmicro", "apple2", "pc88", "pc98", "atarist", "scummvm", "flash")
        put(Device.Phone, "android", "j2me", "palm", "ngage")
        put(Device.Cartridge, "pico8", "tic80", "wasm4", "lowresnx", "chip8", "ports", "doom", "quake", "quake2", "cavestory", "flashback", "easyrpg")
        put(Device.Screen, "moonlight", "videos")
        put(Device.Book, "books")
    }

    fun of(platformId: String): Device = byId[platformId.lowercase()] ?: Device.Pad

    /** The emblem for a collection id (Tandem's or Cocoon's names). */
    fun collection(id: String): Device = when (id) {
        "fav", "favorites" -> Device.Heart
        "most", "most_played" -> Device.Trophy
        "added", "newly_added", "recently_added" -> Device.Star
        "continue", "recent", "recently_played" -> Device.Clock
        else -> Device.Folder
    }
}

/**
 * A system's icon at [size]: the theme pack's own icon if it has one, otherwise a drawn one in the theme's icon
 * style. Use it anywhere a system needs a picture.
 */
@Composable
fun SystemIcon(platform: Platform, size: Dp, modifier: Modifier = Modifier, usePack: Boolean = true) {
    val pack = if (usePack) ThemePacks.systemIcon(platform.id) else null
    if (pack != null) {
        PackIcon(pack, size, modifier)
    } else {
        DrawnIcon(platform.shortName.ifBlank { platform.name }, Devices.of(platform.id), Palette.forName(platform.id), size, modifier)
    }
}

/** A collection's icon ("fav", "most", "added", "continue", or Cocoon's names), from the pack or drawn. */
@Composable
fun CollectionIcon(id: String, label: String, color: Color, size: Dp, modifier: Modifier = Modifier, usePack: Boolean = true) {
    val pack = if (usePack) ThemePacks.collectionIcon(id) else null
    if (pack != null) PackIcon(pack, size, modifier)
    else DrawnIcon(label, Devices.collection(id), color, size, modifier)
}

@Composable
private fun PackIcon(file: java.io.File, size: Dp, modifier: Modifier) {
    // At most a fifth of the side: pack icons often draw their own frame close to their edges, and a rounder clip
    // cut that frame off at the corners.
    AsyncImage(
        file, null, modifier.size(size).clip(RoundedCornerShape(size * (Themes.live.corner / 104f).coerceIn(0.04f, 0.2f))),
        contentScale = ContentScale.Crop,
    )
}

/**
 * The drawn icon: [device] in [color] with [label] under it, in [style] and the colours of [theme]. Previews pass
 * another theme to show how it would look.
 */
@Composable
fun DrawnIcon(
    label: String,
    device: Device,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    style: IconStyle = Themes.live.iconStyle,
    theme: ThemeSpec = Themes.live,
    showLabel: Boolean = size >= 40.dp,
) {
    val radius = size * (theme.corner / 104f).coerceIn(0.04f, 0.3f)
    val text = label.take(10)
    Box(modifier.size(size).clip(RoundedCornerShape(radius))) {
        val pixels = if (style == IconStyle.Pixel) rememberPixelDevice(device) else null
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size
            drawTile(style, color, theme, s)
            val area = if (showLabel) Rect4(s.width * 0.18f, s.height * 0.1f, s.width * 0.64f, s.height * 0.5f)
            else Rect4(s.width * 0.14f, s.height * 0.14f, s.width * 0.72f, s.height * 0.72f)
            if (pixels != null) {
                drawPixelDevice(pixels, area, lerp(color, Color.White, 0.25f), theme)
            } else {
                drawDeviceStyled(style, device, area, color, theme)
            }
        }
        if (showLabel) {
            val fontSize = (size.value * when {
                text.length <= 3 -> 0.22f
                text.length <= 5 -> 0.17f
                text.length <= 7 -> 0.13f
                else -> 0.105f
            }).sp
            Text(
                text,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 4.dp, vertical = size * 0.07f),
                style = labelStyle(style, color, theme).copy(fontSize = fontSize),
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
            )
        }
    }
}

private data class Rect4(val x: Float, val y: Float, val w: Float, val h: Float)

private fun labelStyle(style: IconStyle, color: Color, theme: ThemeSpec): TextStyle {
    val base = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
    return when (style) {
        IconStyle.Bubble -> base.copy(color = Color.White, shadow = Shadow(lerp(color, Color.Black, 0.55f).copy(alpha = 0.7f), Offset(0f, 3f), 6f))
        IconStyle.Neon -> base.copy(color = lerp(color, Color.White, 0.55f), shadow = Shadow(color, Offset.Zero, 18f), fontWeight = FontWeight.ExtraBold)
        IconStyle.Pixel -> base.copy(fontFamily = FontFamily.Monospace, color = lerp(color, Color.White, 0.3f), shadow = Shadow(Color.Black, Offset(3f, 3f), 0f), letterSpacing = 0.sp)
        IconStyle.Flat -> base.copy(color = readableOn(color))
        IconStyle.Plastic -> base.copy(color = Color.White, shadow = Shadow(lerp(color, Color.Black, 0.5f), Offset(0f, 2f), 4f))
        IconStyle.Chrome -> base.copy(
            brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFB8C0CC), Color(0xFF6B7380), Color(0xFFE8ECF2))),
            shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 3f), 4f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        )
        IconStyle.Lcd -> base.copy(fontFamily = FontFamily.Monospace, color = lcdInk(theme), letterSpacing = 0.sp)
        IconStyle.Sticker -> base.copy(color = Color.White, shadow = Shadow(lerp(color, Color.Black, 0.35f), Offset(2f, 2f), 1f))
        IconStyle.Rune -> base.copy(fontFamily = FontFamily.Serif, color = Color(0xFFF3C969), shadow = Shadow(Color(0xFFFF8A3D), Offset.Zero, 12f), letterSpacing = 1.5.sp)
    }
}

/** Black or white, whichever reads better on [bg]. */
fun readableOn(bg: Color): Color = if (bg.luminance() > 0.45f) Color(0xFF14121C) else Color.White

/** WCAG contrast ratio between two colours (1 to 21). */
fun contrast(a: Color, b: Color): Float {
    val la = a.luminance(); val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

private fun lcdInk(theme: ThemeSpec) = lerp(Color(0xFF1E3A1E), theme.text, 0.1f)
private fun lcdGlass(color: Color) = lerp(Color(0xFFA8B98A), color, 0.18f)

// ---- Tiles ----

private fun DrawScope.drawTile(style: IconStyle, color: Color, theme: ThemeSpec, s: Size) {
    val w = s.width; val h = s.height
    when (style) {
        IconStyle.Bubble -> {
            drawRect(Brush.linearGradient(listOf(lerp(color, Color.White, 0.18f), color, lerp(color, Color.Black, 0.35f)), Offset.Zero, Offset(w * 0.4f, h)))
            drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0.04f)), 0f, h * 0.45f), Offset(w * 0.08f, h * 0.03f), Size(w * 0.84f, h * 0.42f))
        }
        IconStyle.Neon -> {
            drawRect(Brush.verticalGradient(listOf(lerp(theme.backgroundDeep, color, 0.12f), theme.backgroundDeep)))
            // Faint scanlines.
            var y = 0f
            while (y < h) { drawRect(color, Offset(0f, y), Size(w, 1f), alpha = 0.06f); y += 4f }
            drawRect(color, Offset(0f, 0f), s, style = Stroke(w * 0.05f), alpha = 0.25f)
        }
        IconStyle.Pixel -> {
            drawRect(lerp(theme.backgroundDeep, color, 0.22f))
            val cell = w / 12f
            for (i in 0 until 12) for (j in 0 until 12) if ((i + j) % 2 == 0) drawRect(Color.White, Offset(i * cell, j * cell), Size(cell, cell), alpha = 0.035f)
            drawRect(Color.Black, Offset(0f, h - cell * 0.6f), Size(w, cell * 0.6f), alpha = 0.25f)
        }
        IconStyle.Flat -> drawRect(color)
        IconStyle.Plastic -> {
            drawRect(lerp(theme.card, color, 0.5f))
            drawRect(color, alpha = 0.35f)
            // Parts inside the clear shell.
            val ink = lerp(color, Color.Black, 0.4f)
            for (k in 0..4) drawLine(ink, Offset(w * (0.1f + k * 0.2f), h * 0.6f), Offset(w * (0.1f + k * 0.2f), h * 0.95f), w * 0.012f, alpha = 0.35f)
            drawCircle(ink, w * 0.06f, Offset(w * 0.82f, h * 0.18f), alpha = 0.3f)
            drawPath(Path().apply { moveTo(0f, h * 0.35f); lineTo(w * 0.55f, 0f); lineTo(w * 0.75f, 0f); lineTo(0f, h * 0.5f); close() }, Color.White, alpha = 0.18f)
        }
        IconStyle.Chrome -> {
            drawRect(Brush.verticalGradient(listOf(lerp(theme.backgroundDeep, color, 0.35f), theme.backgroundDeep, lerp(theme.backgroundDeep, color, 0.2f))))
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent)), Offset(0f, h * 0.56f), Offset(w, h * 0.56f), h * 0.008f)
        }
        IconStyle.Lcd -> {
            drawRect(lcdGlass(color))
            val step = w / 28f
            var x = 0f
            while (x < w) { drawRect(Color.Black, Offset(x, 0f), Size(0.7f, h), alpha = 0.06f); x += step }
            var y = 0f
            while (y < h) { drawRect(Color.Black, Offset(0f, y), Size(w, 0.7f), alpha = 0.06f); y += step }
            drawRect(Color.Black, style = Stroke(w * 0.04f), alpha = 0.18f)
        }
        IconStyle.Sticker -> {
            drawRect(Brush.linearGradient(listOf(lerp(color, theme.card, 0.25f), lerp(color, theme.backgroundDeep, 0.55f)), Offset.Zero, Offset(w, h)))
            // Halftone dots in a corner.
            val step = w / 10f
            for (i in 0 until 10) for (j in 0 until 10) {
                val f = ((i + j) / 18f)
                drawCircle(Color.White, step * 0.22f * f, Offset((i + 0.5f) * step, (j + 0.5f) * step), alpha = 0.12f)
            }
        }
        IconStyle.Rune -> {
            drawRect(Brush.radialGradient(listOf(Color(0xFF4A3F38), Color(0xFF241D1A)), Offset(w * 0.5f, h * 0.35f), w * 0.9f))
            // Bricks.
            val bh = h / 6f
            for (r in 0 until 6) {
                drawLine(Color.Black, Offset(0f, r * bh), Offset(w, r * bh), 1.5f, alpha = 0.35f)
                val off = if (r % 2 == 0) 0f else w / 6f
                var x = off
                while (x < w) { drawLine(Color.Black, Offset(x, r * bh), Offset(x, (r + 1) * bh), 1.5f, alpha = 0.3f); x += w / 3f }
            }
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), Offset(w * 0.5f, h * 0.38f), w * 0.5f), w * 0.5f, Offset(w * 0.5f, h * 0.38f))
        }
    }
}

// ---- Silhouettes ----

/** Draws one silhouette in [area]: the body with [body], the screens and buttons cut out in [detail]. */
private class Pen(val d: DrawScope, val a: Rect4, val body: Brush, val detail: Color, val style: DrawStyle, val detailStyle: DrawStyle = style) {
    fun x(f: Float) = a.x + a.w * f
    fun y(f: Float) = a.y + a.h * f
    private fun u(f: Float) = minOf(a.w, a.h) * f

    fun box(l: Float, t: Float, r: Float, b: Float, round: Float, part: Boolean = false) =
        if (part) d.drawRoundRect(detail, Offset(x(l), y(t)), Size(x(r) - x(l), y(b) - y(t)), CornerRadius(u(round)), style = detailStyle)
        else d.drawRoundRect(body, Offset(x(l), y(t)), Size(x(r) - x(l), y(b) - y(t)), CornerRadius(u(round)), style = style)

    fun dot(cx: Float, cy: Float, r: Float, part: Boolean = true) =
        if (part) d.drawCircle(detail, u(r), Offset(x(cx), y(cy)), style = detailStyle)
        else d.drawCircle(body, u(r), Offset(x(cx), y(cy)), style = style)

    /** A plus-shaped direction pad. */
    fun dpad(cx: Float, cy: Float, r: Float) {
        val arm = u(r); val th = arm * 0.38f
        d.drawRoundRect(detail, Offset(x(cx) - arm, y(cy) - th), Size(arm * 2, th * 2), CornerRadius(th * 0.4f), style = detailStyle)
        d.drawRoundRect(detail, Offset(x(cx) - th, y(cy) - arm), Size(th * 2, arm * 2), CornerRadius(th * 0.4f), style = detailStyle)
    }

    fun path(part: Boolean = false, build: Path.() -> Unit) {
        val p = Path().apply(build)
        if (part) d.drawPath(p, detail, style = detailStyle) else d.drawPath(p, body, style = style)
    }
}

private fun Pen.device(device: Device) {
    when (device) {
        Device.Clamshell -> {
            box(0.16f, 0.02f, 0.84f, 0.47f, 0.08f)
            box(0.24f, 0.08f, 0.76f, 0.41f, 0.03f, part = true)
            box(0.12f, 0.53f, 0.88f, 0.98f, 0.08f)
            box(0.32f, 0.59f, 0.68f, 0.9f, 0.03f, part = true)
            dpad(0.21f, 0.7f, 0.06f)
            dot(0.8f, 0.66f, 0.03f); dot(0.8f, 0.78f, 0.03f)
        }
        Device.Landscape -> {
            box(0.02f, 0.22f, 0.98f, 0.8f, 0.2f)
            box(0.28f, 0.3f, 0.72f, 0.72f, 0.03f, part = true)
            dpad(0.15f, 0.5f, 0.08f)
            dot(0.84f, 0.44f, 0.045f); dot(0.9f, 0.56f, 0.045f)
        }
        Device.Brick -> {
            path {
                moveTo(x(0.2f), y(0.06f)); quadraticTo(x(0.2f), y(0f), x(0.27f), y(0f))
                lineTo(x(0.73f), y(0f)); quadraticTo(x(0.8f), y(0f), x(0.8f), y(0.06f))
                lineTo(x(0.8f), y(0.84f)); quadraticTo(x(0.8f), y(1f), x(0.64f), y(1f))
                lineTo(x(0.27f), y(1f)); quadraticTo(x(0.2f), y(1f), x(0.2f), y(0.94f)); close()
            }
            box(0.28f, 0.08f, 0.72f, 0.46f, 0.03f, part = true)
            dpad(0.36f, 0.66f, 0.07f)
            dot(0.62f, 0.68f, 0.035f); dot(0.7f, 0.62f, 0.035f)
            d.drawLine(detail, Offset(x(0.56f), y(0.92f)), Offset(x(0.66f), y(0.86f)), minOf(a.w, a.h) * 0.02f, cap = StrokeCap.Round)
        }
        Device.Hybrid -> {
            box(0.02f, 0.2f, 0.2f, 0.8f, 0.09f)
            box(0.8f, 0.2f, 0.98f, 0.8f, 0.09f)
            box(0.2f, 0.22f, 0.8f, 0.78f, 0.02f)
            box(0.25f, 0.27f, 0.75f, 0.73f, 0.02f, part = true)
            dot(0.11f, 0.38f, 0.035f); dot(0.89f, 0.56f, 0.035f)
            dot(0.89f, 0.38f, 0.02f); dot(0.11f, 0.6f, 0.02f)
        }
        Device.Console -> {
            box(0.3f, 0.12f, 0.7f, 0.42f, 0.03f)
            box(0.36f, 0.18f, 0.64f, 0.3f, 0.02f, part = true)
            box(0.04f, 0.38f, 0.96f, 0.86f, 0.08f)
            box(0.1f, 0.68f, 0.32f, 0.76f, 0.02f, part = true)
            dot(0.78f, 0.62f, 0.035f); dot(0.88f, 0.62f, 0.035f)
            box(0.38f, 0.48f, 0.62f, 0.54f, 0.02f, part = true)
        }
        Device.Pad -> {
            path {
                moveTo(x(0.3f), y(0.25f)); lineTo(x(0.7f), y(0.25f))
                cubicTo(x(0.92f), y(0.25f), x(1f), y(0.55f), x(0.98f), y(0.78f))
                cubicTo(x(0.96f), y(0.92f), x(0.82f), y(0.95f), x(0.74f), y(0.82f))
                lineTo(x(0.66f), y(0.7f)); lineTo(x(0.34f), y(0.7f)); lineTo(x(0.26f), y(0.82f))
                cubicTo(x(0.18f), y(0.95f), x(0.04f), y(0.92f), x(0.02f), y(0.78f))
                cubicTo(x(0f), y(0.55f), x(0.08f), y(0.25f), x(0.3f), y(0.25f)); close()
            }
            dpad(0.25f, 0.46f, 0.08f)
            dot(0.75f, 0.38f, 0.035f); dot(0.83f, 0.46f, 0.035f); dot(0.67f, 0.46f, 0.035f); dot(0.75f, 0.54f, 0.035f)
            dot(0.4f, 0.6f, 0.05f); dot(0.6f, 0.6f, 0.05f)
        }
        Device.Disc -> {
            dot(0.5f, 0.5f, 0.48f, part = false)
            dot(0.5f, 0.5f, 0.12f)
            d.drawArc(detail, 200f, 50f, false, Offset(x(0.5f) - minOf(a.w, a.h) * 0.34f, y(0.5f) - minOf(a.w, a.h) * 0.34f),
                Size(minOf(a.w, a.h) * 0.68f, minOf(a.w, a.h) * 0.68f), style = Stroke(minOf(a.w, a.h) * 0.04f, cap = StrokeCap.Round))
            d.drawArc(detail, 20f, 40f, false, Offset(x(0.5f) - minOf(a.w, a.h) * 0.26f, y(0.5f) - minOf(a.w, a.h) * 0.26f),
                Size(minOf(a.w, a.h) * 0.52f, minOf(a.w, a.h) * 0.52f), style = Stroke(minOf(a.w, a.h) * 0.03f, cap = StrokeCap.Round))
        }
        Device.Cartridge -> {
            path {
                moveTo(x(0.18f), y(0.02f)); lineTo(x(0.82f), y(0.02f)); lineTo(x(0.82f), y(0.9f))
                lineTo(x(0.74f), y(0.98f)); lineTo(x(0.26f), y(0.98f)); lineTo(x(0.18f), y(0.9f)); close()
            }
            box(0.27f, 0.2f, 0.73f, 0.66f, 0.03f, part = true)
            for (k in 0..4) box(0.3f + k * 0.09f, 0.08f, 0.35f + k * 0.09f, 0.14f, 0.01f, part = true)
            box(0.3f, 0.78f, 0.7f, 0.86f, 0.01f, part = true)
        }
        Device.Computer -> {
            box(0.12f, 0.02f, 0.88f, 0.6f, 0.05f)
            box(0.18f, 0.08f, 0.82f, 0.52f, 0.02f, part = true)
            box(0.42f, 0.6f, 0.58f, 0.7f, 0f)
            box(0.02f, 0.72f, 0.98f, 0.98f, 0.05f)
            for (r in 0..1) for (k in 0..7) box(0.07f + k * 0.112f, 0.77f + r * 0.1f, 0.15f + k * 0.112f, 0.84f + r * 0.1f, 0.01f, part = true)
        }
        Device.Phone -> {
            box(0.26f, 0f, 0.74f, 1f, 0.1f)
            box(0.3f, 0.07f, 0.7f, 0.86f, 0.03f, part = true)
            dot(0.5f, 0.93f, 0.03f)
        }
        Device.Arcade -> {
            path {
                moveTo(x(0.2f), y(0f)); lineTo(x(0.8f), y(0f)); lineTo(x(0.8f), y(0.12f)); lineTo(x(0.74f), y(0.18f))
                lineTo(x(0.74f), y(0.5f)); lineTo(x(0.86f), y(0.62f)); lineTo(x(0.86f), y(1f)); lineTo(x(0.14f), y(1f))
                lineTo(x(0.14f), y(0.62f)); lineTo(x(0.26f), y(0.5f)); lineTo(x(0.26f), y(0.18f)); lineTo(x(0.2f), y(0.12f)); close()
            }
            box(0.26f, 0.03f, 0.74f, 0.1f, 0.01f, part = true)
            box(0.31f, 0.2f, 0.69f, 0.48f, 0.02f, part = true)
            dot(0.36f, 0.6f, 0.04f); d.drawLine(detail, Offset(x(0.36f), y(0.6f)), Offset(x(0.36f), y(0.53f)), minOf(a.w, a.h) * 0.025f)
            dot(0.56f, 0.6f, 0.025f); dot(0.64f, 0.6f, 0.025f)
            box(0.38f, 0.78f, 0.62f, 0.86f, 0.01f, part = true)
        }
        Device.Screen -> {
            box(0.04f, 0.1f, 0.96f, 0.78f, 0.06f)
            box(0.1f, 0.16f, 0.9f, 0.72f, 0.02f, part = true)
            box(0.36f, 0.84f, 0.64f, 0.9f, 0.02f)
            path(part = true) { moveTo(x(0.44f), y(0.32f)); lineTo(x(0.62f), y(0.44f)); lineTo(x(0.44f), y(0.56f)); close() }
        }
        Device.Book -> {
            path {
                moveTo(x(0.5f), y(0.16f)); cubicTo(x(0.35f), y(0.04f), x(0.12f), y(0.06f), x(0.02f), y(0.12f))
                lineTo(x(0.02f), y(0.9f)); cubicTo(x(0.14f), y(0.84f), x(0.36f), y(0.84f), x(0.5f), y(0.96f))
                cubicTo(x(0.64f), y(0.84f), x(0.86f), y(0.84f), x(0.98f), y(0.9f)); lineTo(x(0.98f), y(0.12f))
                cubicTo(x(0.88f), y(0.06f), x(0.65f), y(0.04f), x(0.5f), y(0.16f)); close()
            }
            d.drawLine(detail, Offset(x(0.5f), y(0.18f)), Offset(x(0.5f), y(0.92f)), minOf(a.w, a.h) * 0.025f)
        }
        Device.Heart -> path {
            moveTo(x(0.5f), y(0.95f))
            cubicTo(x(0.05f), y(0.62f), x(-0.02f), y(0.3f), x(0.2f), y(0.14f))
            cubicTo(x(0.34f), y(0.04f), x(0.46f), y(0.12f), x(0.5f), y(0.24f))
            cubicTo(x(0.54f), y(0.12f), x(0.66f), y(0.04f), x(0.8f), y(0.14f))
            cubicTo(x(1.02f), y(0.3f), x(0.95f), y(0.62f), x(0.5f), y(0.95f)); close()
        }
        Device.Trophy -> {
            path {
                moveTo(x(0.22f), y(0.04f)); lineTo(x(0.78f), y(0.04f)); lineTo(x(0.76f), y(0.36f))
                cubicTo(x(0.74f), y(0.56f), x(0.6f), y(0.62f), x(0.56f), y(0.64f)); lineTo(x(0.56f), y(0.78f))
                lineTo(x(0.7f), y(0.86f)); lineTo(x(0.7f), y(0.96f)); lineTo(x(0.3f), y(0.96f)); lineTo(x(0.3f), y(0.86f))
                lineTo(x(0.44f), y(0.78f)); lineTo(x(0.44f), y(0.64f))
                cubicTo(x(0.4f), y(0.62f), x(0.26f), y(0.56f), x(0.24f), y(0.36f)); close()
            }
            val thick = Stroke(minOf(a.w, a.h) * 0.06f)
            d.drawArc(body, 90f, 180f, false, Offset(x(0.06f), y(0.1f)), Size(a.w * 0.24f, a.h * 0.26f), style = if (style is Fill) thick else style)
            d.drawArc(body, -90f, 180f, false, Offset(x(0.7f), y(0.1f)), Size(a.w * 0.24f, a.h * 0.26f), style = if (style is Fill) thick else style)
            path(part = true) { star(x(0.5f), y(0.28f), minOf(a.w, a.h) * 0.11f) }
        }
        Device.Star -> {
            path { star(x(0.5f), y(0.52f), minOf(a.w, a.h) * 0.48f) }
            dot(0.84f, 0.16f, 0.05f, part = false); dot(0.14f, 0.2f, 0.035f, part = false)
        }
        Device.Clock -> {
            dot(0.5f, 0.5f, 0.46f, part = false)
            dot(0.5f, 0.5f, 0.38f)
            d.drawLine(body, Offset(x(0.5f), y(0.5f)), Offset(x(0.5f), y(0.24f)), minOf(a.w, a.h) * 0.06f, cap = StrokeCap.Round)
            d.drawLine(body, Offset(x(0.5f), y(0.5f)), Offset(x(0.68f), y(0.6f)), minOf(a.w, a.h) * 0.06f, cap = StrokeCap.Round)
        }
        Device.Folder -> {
            box(0.04f, 0.12f, 0.46f, 0.3f, 0.04f)
            box(0.04f, 0.22f, 0.96f, 0.9f, 0.06f)
        }
    }
}

private fun Path.star(cx: Float, cy: Float, r: Float) {
    for (i in 0 until 10) {
        val ang = -PI / 2 + i * PI / 5
        val rr = if (i % 2 == 0) r else r * 0.45f
        val px = cx + (rr * cos(ang)).toFloat(); val py = cy + (rr * sin(ang)).toFloat()
        if (i == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

private fun DrawScope.drawDeviceStyled(style: IconStyle, device: Device, area: Rect4, color: Color, theme: ThemeSpec) {
    val m = minOf(area.w, area.h)
    when (style) {
        IconStyle.Bubble -> {
            Pen(this, area.shift(m * 0.03f), SolidColor(lerp(color, Color.Black, 0.45f).copy(alpha = 0.45f)), Color.Transparent, Fill).device(device)
            Pen(this, area, SolidColor(Color.White.copy(alpha = 0.95f)), lerp(color, Color.Black, 0.25f), Fill).device(device)
        }
        IconStyle.Neon -> {
            val bright = lerp(color, Color.White, 0.35f)
            for ((wid, al) in listOf(0.09f to 0.12f, 0.05f to 0.25f)) {
                val st = Stroke(m * wid, cap = StrokeCap.Round, join = StrokeJoin.Round)
                Pen(this, area, SolidColor(color.copy(alpha = al)), color.copy(alpha = al), st).device(device)
            }
            val st = Stroke(m * 0.022f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            Pen(this, area, SolidColor(bright), bright, st).device(device)
        }
        IconStyle.Flat -> {
            val ink = readableOn(color)
            Pen(this, area, SolidColor(ink.copy(alpha = 0.9f)), color, Fill).device(device)
        }
        IconStyle.Plastic -> {
            Pen(this, area, SolidColor(Color.White.copy(alpha = 0.55f)), lerp(color, Color.Black, 0.3f).copy(alpha = 0.7f), Fill).device(device)
            Pen(this, area, SolidColor(Color.White.copy(alpha = 0.9f)), Color.White.copy(alpha = 0.4f), Stroke(m * 0.015f)).device(device)
        }
        IconStyle.Chrome -> {
            val metal = Brush.verticalGradient(
                listOf(Color.White, Color(0xFFC9D0DA), Color(0xFF7D8696), Color(0xFFEDF1F6), Color(0xFF9AA3B2)),
                startY = area.y, endY = area.y + area.h,
            )
            Pen(this, area.shift(m * 0.035f), SolidColor(Color.Black.copy(alpha = 0.45f)), Color.Transparent, Fill).device(device)
            Pen(this, area, metal, lerp(theme.backgroundDeep, color, 0.5f), Fill).device(device)
        }
        IconStyle.Lcd -> Pen(this, area, SolidColor(lcdInk(theme)), lcdGlass(color), Fill).device(device)
        IconStyle.Sticker -> {
            val st = Stroke(m * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            Pen(this, area.shift(m * 0.04f), SolidColor(Color.Black.copy(alpha = 0.3f)), Color.Black.copy(alpha = 0.3f), st).device(device)
            Pen(this, area, SolidColor(Color.White), Color.White, st).device(device)
            Pen(this, area, SolidColor(lerp(color, Color.White, 0.15f)), lerp(color, Color.Black, 0.45f), Fill).device(device)
        }
        IconStyle.Rune -> {
            // Carved: a dark cut with a light lower edge.
            Pen(this, area.shift(m * 0.025f), SolidColor(Color(0xFFF3C969).copy(alpha = 0.35f)), Color.Transparent, Fill).device(device)
            Pen(this, area, SolidColor(Color(0xFF15100E)), Color(0xFF6B5A4E), Fill).device(device)
        }
        IconStyle.Pixel -> Unit
    }
}

private fun Rect4.shift(by: Float) = copy(x = x + by * 0.3f, y = y + by)

// ---- Pixel style: the silhouette drawn small, then scaled up without smoothing ----

private const val PIXELS = 22

@Composable
private fun rememberPixelDevice(device: Device): ImageBitmap = remember(device) {
    val bmp = ImageBitmap(PIXELS, PIXELS)
    val canvas = androidx.compose.ui.graphics.Canvas(bmp)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(PIXELS.toFloat(), PIXELS.toFloat())) {
        // White body, black details: tinted when drawn.
        Pen(this, Rect4(0f, 0f, PIXELS.toFloat(), PIXELS.toFloat()), SolidColor(Color.White), Color.Black, Fill).device(device)
    }
    bmp
}

private fun DrawScope.drawPixelDevice(bmp: ImageBitmap, area: Rect4, color: Color, theme: ThemeSpec) {
    val side = minOf(area.w, area.h)
    val off = androidx.compose.ui.unit.IntOffset((area.x + (area.w - side) / 2).toInt(), (area.y + (area.h - side) / 2).toInt())
    val dst = IntSize(side.toInt(), side.toInt())
    // Hard shadow, then the sprite tinted in the item colour.
    drawImage(bmp, dstOffset = off + androidx.compose.ui.unit.IntOffset((side / PIXELS).toInt(), (side / PIXELS).toInt()), dstSize = dst,
        filterQuality = FilterQuality.None, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black.copy(alpha = 0.5f)))
    drawImage(bmp, dstOffset = off, dstSize = dst, filterQuality = FilterQuality.None,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.lighting(color, Color.Black))
}

