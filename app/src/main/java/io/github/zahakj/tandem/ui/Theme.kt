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

/**
 * The themes that ship with Tandem: Pastel Night (the default, exactly the original Tandem look), Paper (light) and OLED
 * Black. Everything else comes from theme packs; these three are also what packs build on.
 */
object BuiltInThemes {
    val PastelNight = ThemeSpec(
        id = "pastel-night", name = "Pastel Night", tagline = "Soft night sky, candy colours",
        background = c(0x13121C), backgroundDeep = c(0x0B0A12), card = c(0x211F2E), cardHigh = c(0x2C2940),
        text = c(0xF6F3FF), muted = c(0xA7A2BF), onAccent = c(0x0B0A12),
        mint = c(0x8EECC8), peach = c(0xFFB4A2), lavender = c(0xB8A8FF), sky = c(0x8FD3FF), butter = c(0xFFE49A),
    )

    /**
     * Theme ids that were folded into these three, so a saved theme keeps working: the 21 game-flavoured and
     * everyday themes Tandem used to ship (their backdrops and icon styles are still there as wallpapers and icon
     * styles), and the ids renamed before that.
     */
    val renamed = mapOf(
        "sunset-arcade" to "pastel-night", "ember" to "pastel-night",
        "crt" to "pastel-night", "indigo-cube" to "pastel-night", "neon-arcade" to "pastel-night", "pocket-atomic" to "pastel-night",
        "midnight-disc" to "pastel-night", "blue-boot" to "pastel-night", "sunset-racer" to "pastel-night", "monster-catcher" to "pastel-night",
        "dungeon" to "pastel-night", "kart-rainbow" to "pastel-night", "sakura" to "pastel-night", "mint-soda" to "pastel-night",
        "ocean" to "pastel-night", "forest" to "pastel-night", "lavender-dream" to "pastel-night",
        "crimson-visor" to "oled", "star-shooter" to "oled", "high-contrast" to "oled",
        "dmg" to "paper", "cozy-farm" to "paper", "cotton-candy" to "paper",
    )

    val Oled = ThemeSpec(
        id = "oled", name = "OLED Black", tagline = "True black, saves battery",
        background = c(0x000000), backgroundDeep = c(0x000000), card = c(0x111114), cardHigh = c(0x1C1C21),
        text = c(0xF5F5F7), muted = c(0x9A9AA6), onAccent = c(0x000000),
        mint = c(0x7CF2C4), peach = c(0xFF9F8A), lavender = c(0xA99BFF), sky = c(0x7CCBFF), butter = c(0xFFE07A),
        corner = 16f, backdrop = BackdropStyle.Plain, highlight = HighlightStyle.Outline, highlightThickness = 3f,
        iconStyle = IconStyle.Flat,
    )

    val Paper = ThemeSpec(
        id = "paper", name = "Paper", tagline = "Bright, calm, daylight friendly",
        dark = false,
        background = c(0xF6F1E7), backgroundDeep = c(0xEAE2D1), card = c(0xFFFFFF), cardHigh = c(0xFFFFFF),
        text = c(0x2A2433), muted = c(0x6A6175), onAccent = c(0xFFFFFF),
        mint = c(0x1F9E74), peach = c(0xE4684A), lavender = c(0x7559E8), sky = c(0x2387D1), butter = c(0xC08A00),
        accent = c(0x7559E8), backdrop = BackdropStyle.Blobs, animation = AnimationStyle.Smooth, iconStyle = IconStyle.Flat,
    )

    val list: List<ThemeSpec> = listOf(PastelNight, Paper, Oled)
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
        val accent = look.accent?.let(::parseColor)?.let { readableOn(it, base) }
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

    /**
     * [accent], shifted towards the theme's text colour until it stands out from the background (a contrast of 3:1),
     * so an accent picked on a dark theme (Snow, say) still shows on Paper, and a dark one on a black theme.
     */
    internal fun readableOn(accent: Color, theme: ThemeSpec): Color {
        fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
        fun contrast(a: Color, b: Color) = (maxOf(lum(a), lum(b)) + 0.05f) / (minOf(lum(a), lum(b)) + 0.05f)
        val bg = theme.background
        var out = accent
        var t = 0f
        while (contrast(out, bg) < 3f && t < 1f) { t += 0.1f; out = lerp(accent, theme.text, t) }
        return out
    }

    /** Call once at start, after [Store.init], so the first frame is already in the right theme. */
    fun init(ctx: Context) {
        migrate()
        ThemePacks.reload()
        live = target
        powerManager = ctx.getSystemService(PowerManager::class.java)
        initialized = true
    }

    /**
     * Settings saved by older versions: a theme that is no longer built in becomes the one it was folded into
     * ([BuiltInThemes.renamed]), and the old "theme" choice of recorded sounds becomes "none" (a theme pack's sounds
     * are its own part now).
     */
    private fun migrate() {
        val look = Store.settings.look
        val theme = BuiltInThemes.renamed[look.theme]
        if (theme == null && look.soundFiles != "theme") return
        Store.updateSettings { s -> s.copy(look = s.look.copy(theme = theme ?: s.look.theme, soundFiles = if (s.look.soundFiles == "theme") "none" else s.look.soundFiles)) }
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
