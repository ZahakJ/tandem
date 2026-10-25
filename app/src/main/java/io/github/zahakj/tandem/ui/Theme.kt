package io.github.zahakj.tandem.ui

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.zahakj.tandem.data.Look
import io.github.zahakj.tandem.data.Store
import java.io.File

/** How the highlighted card is marked. */
enum class HighlightStyle(val id: String, val label: String) {
    Glow("glow", "Glow"),
    Outline("outline", "Outline"),
    Lift("lift", "Lift"),
    Pulse("pulse", "Pulse");

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } }
}

/** What cards and panels are made of. */
enum class SurfaceMaterial(val id: String, val label: String) {
    Solid("solid", "Solid"),
    Frosted("frosted", "Frosted glass"),
    Outlined("outlined", "Outlined");

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } }
}

/** How things move. */
enum class AnimationStyle(val id: String, val label: String) {
    Bouncy("bouncy", "Bouncy"),
    Smooth("smooth", "Smooth"),
    Minimal("minimal", "Minimal"),
    Off("off", "Off");

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } }
}

/** The animated (or still) pictures drawn behind everything. */
enum class BackdropStyle(val id: String, val label: String) {
    Blobs("blobs", "Pastel blobs"),
    Starfield("starfield", "Starfield"),
    Waves("waves", "Waves"),
    Synthwave("synthwave", "Synthwave"),
    Bubbles("bubbles", "Bubbles"),
    Petals("petals", "Petals"),
    Fireflies("fireflies", "Fireflies"),
    Pixels("pixels", "8-bit evening"),
    Lcd("lcd", "Pocket LCD"),
    Cubes("cubes", "Floating cubes"),
    Towers("towers", "Boot towers"),
    Marquee("marquee", "Neon marquee"),
    Shell("shell", "Clear shell"),
    Disc("disc", "Spinning disc"),
    Visor("visor", "Red wireframe"),
    Road("road", "Sunset road"),
    Shooter("shooter", "Space shooter"),
    Farm("farm", "Cozy fields"),
    Meadow("meadow", "Tall grass"),
    Torch("torch", "Torchlit wall"),
    Rainbow("rainbow", "Rainbow road"),
    Plain("plain", "Plain");

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } }
}

/**
 * How system tiles are drawn when no theme pack brings icons: the short name in a themed treatment over a simple
 * device silhouette. See SystemIcon.
 */
enum class IconStyle(val id: String, val label: String) {
    Bubble("bubble", "Glossy bubble"),
    Neon("neon", "Neon outline"),
    Pixel("pixel", "Pixel"),
    Flat("flat", "Flat"),
    Plastic("plastic", "Clear plastic"),
    Chrome("chrome", "Chrome"),
    Lcd("lcd", "LCD"),
    Sticker("sticker", "Sticker"),
    Rune("rune", "Carved");

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } }
}

/** A finishing effect drawn over the whole screen. */
enum class ScreenOverlay { None, Scanlines, DotMatrix }

/**
 * A complete theme: colours, shapes and motion. Built-in themes live in [BuiltInThemes]; theme packs are read from
 * the ROMs folder by [ThemePacks]. The user's own overrides (accent, corners and so on) are applied on top by
 * [Themes.resolve].
 */
data class ThemeSpec(
    val id: String,
    val name: String,
    val tagline: String,
    val dark: Boolean = true,
    val background: Color,
    val backgroundDeep: Color,
    val card: Color,
    val cardHigh: Color,
    val text: Color,
    val muted: Color,
    /** Text drawn on top of a bright accent tile. */
    val onAccent: Color,
    val mint: Color,
    val peach: Color,
    val lavender: Color,
    val sky: Color,
    val butter: Color,
    /** The main accent: toggles, the accent highlight, links. */
    val accent: Color = mint,
    /** Colour of the shadow glow when everything glows in the accent colour; defaults to the accent. */
    val glow: Color = accent,
    /** Card corner radius in dp. */
    val corner: Float = 18f,
    /** How much a focused card grows (1.1 = 10 %). Capped for large items so nothing spills over. */
    val focusScale: Float = 1.1f,
    val highlight: HighlightStyle = HighlightStyle.Glow,
    val highlightThickness: Float = 4f,
    val material: SurfaceMaterial = SurfaceMaterial.Solid,
    val animation: AnimationStyle = AnimationStyle.Bouncy,
    val backdrop: BackdropStyle = BackdropStyle.Blobs,
    val overlay: ScreenOverlay = ScreenOverlay.None,
    /** Picture or video wallpapers that come with a theme pack (absolute files). */
    val wallpaperTop: File? = null,
    val wallpaperBottom: File? = null,
    /** Where it came from: "Built in", or the pack's author. */
    val source: String = "Built in",
    /** Grid size multiplier from a theme pack's icon_scale. */
    val iconScale: Float = 1f,
    val heroLogoScale: Float? = null,
    val heroGradient: String? = null,
    /** How system tiles are drawn without pack art. */
    val iconStyle: IconStyle = IconStyle.Bubble,
    /** For packs: the built-in theme it builds on (used when its colours are switched off). */
    val baseId: String? = null,
    /** For packs: it brings system or collection art. */
    val hasArt: Boolean = false,
) {
    val accents: List<Color> get() = listOf(mint, peach, lavender, sky, butter)

    /** Colours blend smoothly; everything else switches at once. */
    fun lerpTo(to: ThemeSpec, t: Float): ThemeSpec = to.copy(
        background = lerp(background, to.background, t),
        backgroundDeep = lerp(backgroundDeep, to.backgroundDeep, t),
        card = lerp(card, to.card, t),
        cardHigh = lerp(cardHigh, to.cardHigh, t),
        text = lerp(text, to.text, t),
        muted = lerp(muted, to.muted, t),
        onAccent = lerp(onAccent, to.onAccent, t),
        mint = lerp(mint, to.mint, t),
        peach = lerp(peach, to.peach, t),
        lavender = lerp(lavender, to.lavender, t),
        sky = lerp(sky, to.sky, t),
        butter = lerp(butter, to.butter, t),
        accent = lerp(accent, to.accent, t),
        glow = lerp(glow, to.glow, t),
    )
}

private fun c(hex: Long) = Color(0xFF000000 or hex)

/** The themes that ship with Tandem. The first one is the default and is exactly the original Tandem look. */
object BuiltInThemes {
    val PastelNight = ThemeSpec(
        id = "pastel-night", name = "Pastel Night", tagline = "Soft night sky, candy colours",
        background = c(0x13121C), backgroundDeep = c(0x0B0A12), card = c(0x211F2E), cardHigh = c(0x2C2940),
        text = c(0xF6F3FF), muted = c(0xA7A2BF), onAccent = c(0x0B0A12),
        mint = c(0x8EECC8), peach = c(0xFFB4A2), lavender = c(0xB8A8FF), sky = c(0x8FD3FF), butter = c(0xFFE49A),
    )

    /** Old theme ids that were folded into newer themes. */
    val renamed = mapOf("sunset-arcade" to "sunset-racer", "ember" to "dungeon")

    /** The game-flavoured themes, each with its own backdrop and icon style. Original art only, no trademarks. */
    private val games: List<ThemeSpec> = listOf(
        ThemeSpec(
            id = "crt", name = "Cartridge", tagline = "8-bit evenings on a humming CRT",
            background = c(0x1A1C2C), backgroundDeep = c(0x0F1020), card = c(0x29366F), cardHigh = c(0x34458A),
            text = c(0xF4F4F4), muted = c(0xA8C4D4), onAccent = c(0x1A1C2C),
            mint = c(0x38B764), peach = c(0xEF7D57), lavender = c(0xF07A94), sky = c(0x41A6F6), butter = c(0xFFCD75),
            accent = c(0xFFCD75), corner = 2f, focusScale = 1.06f, highlight = HighlightStyle.Outline, highlightThickness = 3f,
            animation = AnimationStyle.Minimal, backdrop = BackdropStyle.Pixels, overlay = ScreenOverlay.Scanlines,
            iconStyle = IconStyle.Pixel,
        ),
        ThemeSpec(
            id = "dmg", name = "Handheld Green", tagline = "Four shades of green on a dot-matrix screen",
            dark = false,
            background = c(0x9BBC0F), backgroundDeep = c(0x96B70F), card = c(0xA9C81C), cardHigh = c(0xB4D12E),
            text = c(0x0F380F), muted = c(0x1C481C), onAccent = c(0x9BBC0F),
            mint = c(0x306230), peach = c(0x0F380F), lavender = c(0x3E6E24), sky = c(0x24502A), butter = c(0x50681A),
            accent = c(0x0F380F), corner = 3f, focusScale = 1.05f, highlight = HighlightStyle.Outline, highlightThickness = 3f,
            animation = AnimationStyle.Minimal, backdrop = BackdropStyle.Lcd, overlay = ScreenOverlay.DotMatrix,
            iconStyle = IconStyle.Lcd,
        ),
        ThemeSpec(
            id = "indigo-cube", name = "Indigo Cube", tagline = "Purple see-through plastic, little cubes",
            background = c(0x1B1236), backgroundDeep = c(0x0E0820), card = c(0x2C1F56), cardHigh = c(0x3A2A70),
            text = c(0xF1ECFF), muted = c(0xA99BD6), onAccent = c(0x140B2A),
            mint = c(0x7CE0C3), peach = c(0xFF9FB2), lavender = c(0x9C7BFF), sky = c(0x8AB4FF), butter = c(0xFFD978),
            accent = c(0x9C7BFF), corner = 14f, material = SurfaceMaterial.Frosted, backdrop = BackdropStyle.Cubes,
            iconStyle = IconStyle.Plastic,
        ),
        ThemeSpec(
            id = "neon-arcade", name = "Neon Arcade", tagline = "Marquee lights, tube signs and scanlines",
            background = c(0x0D0716), backgroundDeep = c(0x05020A), card = c(0x1A0F2B), cardHigh = c(0x26163D),
            text = c(0xFFF0FB), muted = c(0xB59BC9), onAccent = c(0x0D0716),
            mint = c(0x39FFC8), peach = c(0xFF6B6B), lavender = c(0xC77DFF), sky = c(0x3DDCFF), butter = c(0xFFE45C),
            accent = c(0xFF3FA4), corner = 10f, highlightThickness = 3f, material = SurfaceMaterial.Outlined,
            backdrop = BackdropStyle.Marquee, overlay = ScreenOverlay.Scanlines, iconStyle = IconStyle.Neon,
        ),
        ThemeSpec(
            id = "pocket-atomic", name = "Pocket Atomic", tagline = "A clear purple shell, circuits glowing inside",
            background = c(0x221436), backgroundDeep = c(0x140A24), card = c(0x34205A), cardHigh = c(0x432A72),
            text = c(0xF6EEFF), muted = c(0xB6A3D8), onAccent = c(0x1A0C2E),
            mint = c(0x8CF5C8), peach = c(0xFFA78C), lavender = c(0xC59BFF), sky = c(0x9CC6FF), butter = c(0xFFE08A),
            accent = c(0xB98CFF), corner = 20f, highlight = HighlightStyle.Pulse, material = SurfaceMaterial.Frosted,
            backdrop = BackdropStyle.Shell, iconStyle = IconStyle.Plastic,
        ),
        ThemeSpec(
            id = "midnight-disc", name = "Midnight Disc", tagline = "Rainbow sheen on a spinning disc",
            background = c(0x0B0E1A), backgroundDeep = c(0x05060D), card = c(0x161B2E), cardHigh = c(0x20283F),
            text = c(0xEEF3FF), muted = c(0x98A4C4), onAccent = c(0x0B0E1A),
            mint = c(0x8CFFE0), peach = c(0xFFB0D8), lavender = c(0xC3A6FF), sky = c(0x8FE3FF), butter = c(0xFFF1A8),
            accent = c(0x9FE8FF), corner = 24f, material = SurfaceMaterial.Frosted, animation = AnimationStyle.Smooth,
            backdrop = BackdropStyle.Disc, iconStyle = IconStyle.Chrome,
        ),
        ThemeSpec(
            id = "blue-boot", name = "Blue Console Boot", tagline = "Dreamy towers of blue cubes in the fog",
            background = c(0x06102A), backgroundDeep = c(0x020718), card = c(0x0E1E48), cardHigh = c(0x16295E),
            text = c(0xE8F1FF), muted = c(0x8FA6D4), onAccent = c(0x041030),
            mint = c(0x7FE8FF), peach = c(0xA9C8FF), lavender = c(0x7F9BFF), sky = c(0x4FA8FF), butter = c(0xD7E6FF),
            accent = c(0x4FA8FF), corner = 12f, animation = AnimationStyle.Smooth, material = SurfaceMaterial.Frosted,
            backdrop = BackdropStyle.Towers, iconStyle = IconStyle.Bubble,
        ),
        ThemeSpec(
            id = "crimson-visor", name = "Crimson Visor", tagline = "Red wireframes on pure black",
            background = c(0x000000), backgroundDeep = c(0x000000), card = c(0x140405), cardHigh = c(0x22080A),
            text = c(0xFFD9D6), muted = c(0xC8656A), onAccent = c(0x000000),
            mint = c(0xFF3B3B), peach = c(0xFF6A4D), lavender = c(0xE0284F), sky = c(0xFF8080), butter = c(0xFFB0A0),
            accent = c(0xFF2A2A), corner = 4f, focusScale = 1.06f, highlight = HighlightStyle.Outline, highlightThickness = 2f,
            material = SurfaceMaterial.Outlined, animation = AnimationStyle.Minimal, backdrop = BackdropStyle.Visor,
            iconStyle = IconStyle.Neon,
        ),
        ThemeSpec(
            id = "sunset-racer", name = "Sunset Racer", tagline = "Chrome, palms and a road into the sun",
            background = c(0x1A0B2E), backgroundDeep = c(0x0A0418), card = c(0x2B1449), cardHigh = c(0x3A1C61),
            text = c(0xFFF0F7), muted = c(0xC4A2D9), onAccent = c(0x1A0B2E),
            mint = c(0x4DF2D0), peach = c(0xFF8A5C), lavender = c(0xC66BFF), sky = c(0x3FC6FF), butter = c(0xFFD166),
            accent = c(0xFF4FA0), corner = 14f, backdrop = BackdropStyle.Road, iconStyle = IconStyle.Chrome,
        ),
        ThemeSpec(
            id = "star-shooter", name = "Starfield Shooter", tagline = "Warp stars, a squadron, pew pew",
            background = c(0x05070F), backgroundDeep = c(0x000105), card = c(0x0F1424), cardHigh = c(0x182038),
            text = c(0xEAF2FF), muted = c(0x8B98B8), onAccent = c(0x05070F),
            mint = c(0x5CFF9D), peach = c(0xFF7A59), lavender = c(0xA48CFF), sky = c(0x5CD2FF), butter = c(0xFFE85C),
            accent = c(0x5CFF9D), corner = 6f, focusScale = 1.06f, highlight = HighlightStyle.Outline, highlightThickness = 3f,
            animation = AnimationStyle.Minimal, backdrop = BackdropStyle.Shooter, iconStyle = IconStyle.Pixel,
        ),
        ThemeSpec(
            id = "cozy-farm", name = "Cozy Farm", tagline = "Warm fields, puffy clouds, slow days",
            dark = false,
            background = c(0xF7EBD3), backgroundDeep = c(0xEADBB8), card = c(0xFFF8EA), cardHigh = c(0xFFFCF4),
            text = c(0x4A3424), muted = c(0x765C46), onAccent = c(0xFFFFFF),
            mint = c(0x3F822F), peach = c(0xD8693C), lavender = c(0x8A68BF), sky = c(0x3D8CC4), butter = c(0xA87A0E),
            accent = c(0x3F822F), corner = 22f, backdrop = BackdropStyle.Farm, iconStyle = IconStyle.Sticker,
        ),
        ThemeSpec(
            id = "monster-catcher", name = "Monster Catcher", tagline = "Something is rustling in the tall grass",
            background = c(0x15261A), backgroundDeep = c(0x0B160E), card = c(0x203A27), cardHigh = c(0x2A4A32),
            text = c(0xF4FFF2), muted = c(0xA6C2A8), onAccent = c(0x0B160E),
            mint = c(0x7EE07A), peach = c(0xFF6B5B), lavender = c(0xC7A1FF), sky = c(0x6CCBFF), butter = c(0xFFD65C),
            accent = c(0xFF5B4D), corner = 20f, backdrop = BackdropStyle.Meadow, iconStyle = IconStyle.Bubble,
        ),
        ThemeSpec(
            id = "dungeon", name = "Dungeon", tagline = "Torchlight on old stone",
            background = c(0x17110E), backgroundDeep = c(0x0B0807), card = c(0x2A201B), cardHigh = c(0x372A23),
            text = c(0xF5E6CF), muted = c(0xB59C80), onAccent = c(0x1A0F08),
            mint = c(0x9CCB8A), peach = c(0xFF8A3D), lavender = c(0xB08AD9), sky = c(0x7FB8C9), butter = c(0xF3C969),
            accent = c(0xF3A23D), corner = 6f, animation = AnimationStyle.Smooth, backdrop = BackdropStyle.Torch,
            iconStyle = IconStyle.Rune,
        ),
        ThemeSpec(
            id = "kart-rainbow", name = "Kart Rainbow", tagline = "A striped road through the stars",
            background = c(0x120B2E), backgroundDeep = c(0x070418), card = c(0x221852), cardHigh = c(0x2F2268),
            text = c(0xFFFFFF), muted = c(0xB8B0E0), onAccent = c(0x120B2E),
            mint = c(0x6BEA8C), peach = c(0xFF5D73), lavender = c(0xB07CFF), sky = c(0x5CC8FF), butter = c(0xFFE15C),
            accent = c(0xFFE15C), corner = 24f, focusScale = 1.12f, highlight = HighlightStyle.Pulse,
            backdrop = BackdropStyle.Rainbow, iconStyle = IconStyle.Bubble,
        ),
    )

    /** Quieter everyday themes. */
    private val calm: List<ThemeSpec> = listOf(
        ThemeSpec(
            id = "oled", name = "OLED Black", tagline = "True black, saves battery",
            background = c(0x000000), backgroundDeep = c(0x000000), card = c(0x111114), cardHigh = c(0x1C1C21),
            text = c(0xF5F5F7), muted = c(0x9A9AA6), onAccent = c(0x000000),
            mint = c(0x7CF2C4), peach = c(0xFF9F8A), lavender = c(0xA99BFF), sky = c(0x7CCBFF), butter = c(0xFFE07A),
            corner = 16f, backdrop = BackdropStyle.Plain, highlight = HighlightStyle.Outline, highlightThickness = 3f,
            iconStyle = IconStyle.Flat,
        ),
        ThemeSpec(
            id = "sakura", name = "Sakura", tagline = "Cherry blossoms at dusk",
            background = c(0x1F1219), backgroundDeep = c(0x13090F), card = c(0x301C27), cardHigh = c(0x3D2532),
            text = c(0xFFF0F5), muted = c(0xC9A3B4), onAccent = c(0x1F0B15),
            mint = c(0xB5EAD7), peach = c(0xFFB7C5), lavender = c(0xE2B6FF), sky = c(0xA8D8FF), butter = c(0xFFE5A8),
            accent = c(0xFFB7C5), corner = 22f, backdrop = BackdropStyle.Petals, material = SurfaceMaterial.Frosted,
            iconStyle = IconStyle.Sticker,
        ),
        ThemeSpec(
            id = "mint-soda", name = "Mint Soda", tagline = "Fizzy, fresh and cool",
            background = c(0x0E1B1B), backgroundDeep = c(0x071212), card = c(0x16292A), cardHigh = c(0x1F3738),
            text = c(0xEFFFFB), muted = c(0x9CC5BE), onAccent = c(0x062020),
            mint = c(0x7FFFD4), peach = c(0xFFB09C), lavender = c(0xB3B8FF), sky = c(0x7FE0FF), butter = c(0xF4FF9E),
            accent = c(0x7FFFD4), corner = 20f, backdrop = BackdropStyle.Bubbles, material = SurfaceMaterial.Frosted,
        ),
        ThemeSpec(
            id = "ocean", name = "Ocean", tagline = "Deep blue, gentle tides",
            background = c(0x0B1A2B), backgroundDeep = c(0x040D18), card = c(0x12283F), cardHigh = c(0x1A3552),
            text = c(0xEAF6FF), muted = c(0x93B4CC), onAccent = c(0x041322),
            mint = c(0x6EE7C8), peach = c(0xFFAB91), lavender = c(0x9FA8FF), sky = c(0x64C8FF), butter = c(0xFFE08A),
            accent = c(0x64C8FF), corner = 20f, backdrop = BackdropStyle.Waves, material = SurfaceMaterial.Frosted,
            animation = AnimationStyle.Smooth,
        ),
        ThemeSpec(
            id = "forest", name = "Forest", tagline = "Moss, fireflies and quiet",
            background = c(0x101A12), backgroundDeep = c(0x08100A), card = c(0x1A2A1D), cardHigh = c(0x243828),
            text = c(0xF1F8EC), muted = c(0xA3B89C), onAccent = c(0x0A140C),
            mint = c(0x9BE38F), peach = c(0xF2B48C), lavender = c(0xC3B1E1), sky = c(0x9AD1D4), butter = c(0xE9D97A),
            accent = c(0x9BE38F), corner = 16f, backdrop = BackdropStyle.Fireflies, animation = AnimationStyle.Smooth,
            iconStyle = IconStyle.Flat,
        ),
        ThemeSpec(
            id = "lavender-dream", name = "Lavender Dream", tagline = "Dreamy, floaty, extra soft",
            background = c(0x1B1730), backgroundDeep = c(0x0F0C1F), card = c(0x29234A), cardHigh = c(0x352D5E),
            text = c(0xF7F3FF), muted = c(0xB5ACD9), onAccent = c(0x150F2B),
            mint = c(0xA0F0E0), peach = c(0xFFC6D9), lavender = c(0xCDB4FF), sky = c(0xA9C7FF), butter = c(0xFFF0B3),
            accent = c(0xCDB4FF), corner = 26f, focusScale = 1.12f, material = SurfaceMaterial.Frosted,
            iconStyle = IconStyle.Plastic,
        ),
        ThemeSpec(
            id = "paper", name = "Paper", tagline = "Bright, calm, daylight friendly",
            dark = false,
            background = c(0xF6F1E7), backgroundDeep = c(0xEAE2D1), card = c(0xFFFFFF), cardHigh = c(0xFFFFFF),
            text = c(0x2A2433), muted = c(0x6A6175), onAccent = c(0xFFFFFF),
            mint = c(0x1F9E74), peach = c(0xE4684A), lavender = c(0x7559E8), sky = c(0x2387D1), butter = c(0xC08A00),
            accent = c(0x7559E8), backdrop = BackdropStyle.Blobs, animation = AnimationStyle.Smooth, iconStyle = IconStyle.Flat,
        ),
        ThemeSpec(
            id = "cotton-candy", name = "Cotton Candy", tagline = "Light pastels, sugar rush",
            dark = false,
            background = c(0xFFF3FA), backgroundDeep = c(0xF8E3F1), card = c(0xFFFFFF), cardHigh = c(0xFFFFFF),
            text = c(0x3A2A44), muted = c(0x75607F), onAccent = c(0xFFFFFF),
            mint = c(0x1F9A7A), peach = c(0xE85566), lavender = c(0x9B7BFF), sky = c(0x2A8BC8), butter = c(0xA87A00),
            accent = c(0xCC3F55), corner = 24f, focusScale = 1.12f, backdrop = BackdropStyle.Bubbles,
            iconStyle = IconStyle.Sticker,
        ),
        ThemeSpec(
            id = "high-contrast", name = "High Contrast", tagline = "Maximum clarity",
            background = c(0x000000), backgroundDeep = c(0x000000), card = c(0x141414), cardHigh = c(0x2A2A2A),
            text = c(0xFFFFFF), muted = c(0xE6E6E6), onAccent = c(0x000000),
            mint = c(0x00FF9C), peach = c(0xFF8A4A), lavender = c(0xD9A6FF), sky = c(0x00E5FF), butter = c(0xFFE600),
            accent = c(0xFFE600), corner = 12f, focusScale = 1.06f, highlight = HighlightStyle.Outline, highlightThickness = 6f,
            material = SurfaceMaterial.Outlined, animation = AnimationStyle.Minimal, backdrop = BackdropStyle.Plain,
            iconStyle = IconStyle.Flat,
        ),
    )

    val gameIds: Set<String> = games.map { it.id }.toSet()

    val list: List<ThemeSpec> = listOf(PastelNight) + games + calm
}

/** Picks colours for the accent picker. */
val AccentSwatches: List<Pair<String, Color>> = listOf(
    "Mint" to c(0x8EECC8), "Peach" to c(0xFFB4A2), "Lavender" to c(0xB8A8FF), "Sky" to c(0x8FD3FF),
    "Butter" to c(0xFFE49A), "Sakura" to c(0xFFB7C5), "Coral" to c(0xFF7A6B), "Hot pink" to c(0xFF5DA2),
    "Grape" to c(0x9B6BFF), "Ocean" to c(0x3EA8FF), "Teal" to c(0x2ED3B7), "Lime" to c(0xB8F25C),
    "Gold" to c(0xFFC531), "Snow" to c(0xF2F2F7),
)

/** The live theme. Everything reads it through [Palette], so a change shows up everywhere at once. */
object Themes {
    /** Theme packs found in the ROMs folder (see [ThemePacks.packs] for their art and details). */
    val packs: List<ThemeSpec> get() = ThemePacks.packs.map { it.spec }

    /** What is on screen right now; blends towards [target] when the theme changes. */
    var live by mutableStateOf(BuiltInThemes.PastelNight)
        private set

    val all: List<ThemeSpec> get() = BuiltInThemes.list + packs

    fun byId(id: String): ThemeSpec? = (BuiltInThemes.renamed[id] ?: id).let { i -> all.firstOrNull { it.id == i } }

    /** A theme shown for a moment without being chosen (Developer › Tour every theme); null normally. */
    var preview by mutableStateOf<String?>(null)

    /** The chosen theme with the user's overrides on top. */
    val target: ThemeSpec get() = Store.settings.look.let { look -> resolve(preview?.let { look.copy(theme = it, accent = null) } ?: look) }

    /** A pack theme with the parts the user switched off taken from the theme it builds on. */
    fun withParts(spec: ThemeSpec, look: Look): ThemeSpec {
        val parts = look.packParts[spec.id] ?: return spec
        val base = BuiltInThemes.byIdOrDefault(spec.baseId ?: return spec)
        var out = spec
        if (!parts.colors) out = base.copy(
            id = spec.id, name = spec.name, tagline = spec.tagline, source = spec.source, baseId = spec.baseId,
            hasArt = spec.hasArt, wallpaperTop = spec.wallpaperTop, wallpaperBottom = spec.wallpaperBottom,
            backdrop = spec.backdrop, iconScale = spec.iconScale, heroLogoScale = spec.heroLogoScale,
            heroGradient = spec.heroGradient,
        )
        if (!parts.wallpaper) out = out.copy(
            wallpaperTop = null, wallpaperBottom = null,
            backdrop = if (spec.backdrop == BackdropStyle.Plain) base.backdrop.takeIf { it != BackdropStyle.Plain } ?: BackdropStyle.Blobs else spec.backdrop,
        )
        return out
    }

    fun resolve(look: Look): ThemeSpec {
        val base = withParts(byId(look.theme) ?: BuiltInThemes.PastelNight, look)
        val accent = look.accent?.let(::parseColor)
        return base.copy(
            iconStyle = IconStyle.of(look.iconStyle) ?: base.iconStyle,
            accent = accent ?: base.accent,
            glow = accent ?: base.glow,
            highlight = HighlightStyle.of(look.highlight) ?: base.highlight,
            highlightThickness = look.highlightThickness ?: base.highlightThickness,
            focusScale = look.focusScale ?: base.focusScale,
            corner = look.corner ?: base.corner,
            material = SurfaceMaterial.of(look.material) ?: base.material,
            animation = when {
                Store.settings.reduceMotion -> AnimationStyle.Off
                else -> AnimationStyle.of(look.motion) ?: base.animation
            },
        )
    }

    /** Call once at start, after [Store.init], so the first frame is already in the right theme. */
    fun init(ctx: Context) {
        ThemePacks.reload()
        live = target
        powerManager = ctx.getSystemService(PowerManager::class.java)
        initialized = true
    }

    /** True once [init] ran in this process (the bottom-screen home skips a second, costly theme pack reload). */
    @Volatile var initialized = false
        private set

    private var powerManager: PowerManager? = null

    /** True when backdrops should hold still: reduce motion, animations off, or battery saver. */
    val still: Boolean
        get() = live.animation == AnimationStyle.Off || powerSave()

    private var powerSaveAt = 0L
    private var powerSaveCached = false

    /** Battery saver, asked at most every few seconds (it is a system call). */
    private fun powerSave(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - powerSaveAt > 5_000) { powerSaveAt = now; powerSaveCached = powerManager?.isPowerSaveMode == true }
        return powerSaveCached
    }

    /**
     * Blends [live] towards [target] whenever the theme or an override changes. Placed once, in the top screen's
     * root; the bottom screen just reads [live].
     */
    @Composable
    fun Driver() {
        val target = target
        LaunchedEffect(target) {
            val from = live
            if (from == target) return@LaunchedEffect
            if (target.animation == AnimationStyle.Off) { live = target; return@LaunchedEffect }
            val p = Animatable(0f)
            p.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) { live = from.lerpTo(target, value) }
            live = target
        }
    }
}

/** "#RRGGBB", "#AARRGGBB" or "rgba(r,g,b,a)"; null if it isn't a colour. */
fun parseColor(text: String): Color? {
    val s = text.trim()
    return runCatching {
        if (s.startsWith("rgb", ignoreCase = true)) {
            val n = s.substringAfter('(').substringBefore(')').split(',').map { it.trim().toFloat() }
            Color(n[0] / 255f, n[1] / 255f, n[2] / 255f, n.getOrElse(3) { 1f }.coerceIn(0f, 1f))
        } else {
            val hex = s.removePrefix("#")
            when (hex.length) {
                6 -> Color(0xFF000000 or hex.toLong(16))
                8 -> Color(hex.toLong(16))
                3 -> Color(0xFF000000 or hex.map { "$it$it" }.joinToString("").toLong(16))
                else -> null
            }
        }
    }.getOrNull()
}

fun Color.toHex(): String = "#%06X".format(0xFFFFFF and (
    ((red * 255).toInt() shl 16) or ((green * 255).toInt() shl 8) or (blue * 255).toInt()))

/** Animation specs that follow the theme's [AnimationStyle]. */
object Motion {
    val style: AnimationStyle get() = Themes.live.animation

    /** Focus grow/shrink. */
    fun <T> focus(): FiniteAnimationSpec<T> = when (style) {
        AnimationStyle.Bouncy -> spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
        AnimationStyle.Smooth -> spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
        AnimationStyle.Minimal -> tween(110)
        AnimationStyle.Off -> snap()
    }

    /** Gentle follow-through (lift, glow). */
    fun <T> soft(): FiniteAnimationSpec<T> = when (style) {
        AnimationStyle.Bouncy, AnimationStyle.Smooth -> spring(stiffness = Spring.StiffnessLow)
        AnimationStyle.Minimal -> tween(140)
        AnimationStyle.Off -> snap()
    }

    /** Bigger moves: screens and panels sliding in. */
    fun <T> move(): FiniteAnimationSpec<T> = when (style) {
        AnimationStyle.Bouncy -> spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
        AnimationStyle.Smooth -> spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)
        AnimationStyle.Minimal -> tween(160)
        AnimationStyle.Off -> snap()
    }

    fun <T> fade(ms: Int): FiniteAnimationSpec<T> = if (style == AnimationStyle.Off) snap() else tween(ms)

    /**
     * Screen changes (Home ↔ Settings, Settings pages, folders as pages): one length and one curve everywhere, so
     * every transition feels the same. Material's emphasized curves: arrivals decelerate, departures accelerate and
     * finish a little sooner, so the two never fight. Tweens, not springs: a screen change never overshoots or
     * drags on, whatever the animation style.
     */
    val screenMs: Int get() = when (style) {
        AnimationStyle.Minimal -> 160
        AnimationStyle.Off -> 0
        else -> 220
    }

    fun <T> screenIn(): FiniteAnimationSpec<T> = if (style == AnimationStyle.Off) snap() else tween(screenMs, easing = EmphasizedDecelerate)

    fun <T> screenOut(): FiniteAnimationSpec<T> = if (style == AnimationStyle.Off) snap() else tween(screenMs * 3 / 4, easing = EmphasizedAccelerate)

    val EmphasizedDecelerate = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = androidx.compose.animation.core.CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}
