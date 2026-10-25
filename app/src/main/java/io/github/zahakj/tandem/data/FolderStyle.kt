package io.github.zahakj.tandem.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * How the user designed a folder (Y › Design folder…): its icon, colour, hero, title and shape. Kept per tile key
 * in [IconGridConfig.styles] ("folder:<id>", "s:<system>", "col:<id>", "genres", "genre:<name>"), so a home folder
 * keeps its look when renamed. Every field left at its default follows the theme; a tile with no entry at all is
 * exactly as before. Pure data: no Android, so it is unit tested.
 */
@Serializable
data class FolderStyle(
    /** [ICON_AUTO], [ICON_GAME], [ICON_MOSAIC], [ICON_FAN], [ICON_GLYPH], [ICON_EMOJI] or [ICON_FILE]. */
    val icon: String = ICON_AUTO,
    /** For [ICON_GAME]: the tile key of the game or app whose cover or icon is shown ("game:<key>", "app:<pkg>"). */
    val iconRef: String? = null,
    /** For [ICON_GLYPH]: one of Tandem's drawn icons, by name (TIcon). */
    val glyph: String? = null,
    /** For [ICON_EMOJI]. */
    val emoji: String? = null,
    /** For [ICON_FILE]: a picture on the device. */
    val iconFile: String? = null,
    /** The tile, ring and Spotlight colour (ARGB); null follows the theme or the folder's own colour. */
    val color: Int? = null,
    /** [color] was taken from the icon's art, and is taken again when the icon changes. */
    val colorFromArt: Boolean = false,
    /** [HERO_AUTO], [HERO_GAME], [HERO_FILE] or [HERO_SWEEP]. */
    val hero: String = HERO_AUTO,
    /** For [HERO_GAME]: the game's key. */
    val heroRef: String? = null,
    val heroFile: String? = null,
    /** [LOGO_AUTO], [LOGO_TEXT], [LOGO_FILE] or [LOGO_GAME]. */
    val logo: String = LOGO_AUTO,
    val logoRef: String? = null,
    val logoFile: String? = null,
    /** For [LOGO_TEXT]: 600, 800 or 900. */
    val textWeight: Int = 800,
    /** For [LOGO_TEXT]: 0 small, 1 medium, 2 large. */
    val textSize: Int = 1,
    /** [SHAPE_THEME], [SHAPE_ROUNDED] or [SHAPE_CIRCLE]. */
    val shape: String = SHAPE_THEME,
    /** Game covers in the icon wear the theme's cover frame, when the theme has one. */
    val frame: Boolean = true,
) {
    /** Nothing changed from the theme: the same as having no style at all. */
    val isDefault: Boolean get() = this == DEFAULT

    /** A short stable key for everything that changes the icon's picture (bitmap caches use it). */
    fun iconHash(): String = listOf(icon, iconRef, glyph, emoji, iconFile, color, shape, frame).joinToString("|").hashCode().toUInt().toString(36)

    companion object {
        const val ICON_AUTO = "auto"
        const val ICON_GAME = "game"
        const val ICON_MOSAIC = "mosaic"
        const val ICON_FAN = "fan"
        const val ICON_GLYPH = "glyph"
        const val ICON_EMOJI = "emoji"
        const val ICON_FILE = "file"
        const val HERO_AUTO = "auto"
        const val HERO_GAME = "game"
        const val HERO_FILE = "file"
        const val HERO_SWEEP = "sweep"
        const val LOGO_AUTO = "auto"
        const val LOGO_TEXT = "text"
        const val LOGO_FILE = "file"
        const val LOGO_GAME = "game"
        const val SHAPE_THEME = "theme"
        const val SHAPE_ROUNDED = "rounded"
        const val SHAPE_CIRCLE = "circle"
        val DEFAULT = FolderStyle()
    }
}

/** A tile's style, or the default (theme) look when it has none. Folders saved before styles existed have none. */
fun IconGridConfig.styleOf(key: String): FolderStyle = styles[key] ?: FolderStyle.DEFAULT

/** [key] wearing [style]; a default style is removed rather than kept, so a reset leaves no trace. */
fun IconGridConfig.withStyle(key: String, style: FolderStyle): IconGridConfig =
    copy(styles = if (style.isDefault) styles - key else styles + (key to style))

/** Back to the theme's look. */
fun IconGridConfig.resetStyle(key: String): IconGridConfig = copy(styles = styles - key)

/** Styles of folders that no longer exist are dropped (systems, collections and genres keep theirs). */
fun IconGridConfig.pruneStyles(): IconGridConfig {
    val ids = folders.mapTo(HashSet()) { "folder:${it.id}" }
    val kept = styles.filterKeys { !it.startsWith("folder:") || it in ids }
    return if (kept.size == styles.size) this else copy(styles = kept)
}

/** How icon-grid.json is read and written. */
object GridJson {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    /** Older files are moved over to folder shortcuts as they are read (GridTree.migrate). */
    fun decode(text: String): IconGridConfig = GridTree.migrate(runCatching { json.decodeFromString<IconGridConfig>(text) }.getOrDefault(IconGridConfig()))
    fun encode(config: IconGridConfig): String = json.encodeToString(config)
}

/**
 * The main colour of a picture, for a folder's tile, ring and Spotlight sweep: the most common lively hue (pixels
 * weighted by how colourful they are), averaged over that hue, then kept in a range that reads well under white
 * text. Greys, near-black, near-white and see-through pixels don't count; a picture with no colour at all gives
 * null. Pure, on ARGB ints.
 */
object ArtColor {
    private const val BUCKETS = 24

    fun dominant(pixels: IntArray): Int? {
        val weight = FloatArray(BUCKETS)
        val r = FloatArray(BUCKETS)
        val g = FloatArray(BUCKETS)
        val b = FloatArray(BUCKETS)
        val hsv = FloatArray(3)
        for (p in pixels) {
            if ((p ushr 24) < 160) continue
            val pr = (p shr 16) and 0xff
            val pg = (p shr 8) and 0xff
            val pb = p and 0xff
            toHsv(pr, pg, pb, hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (s < 0.22f || v < 0.18f) continue
            // Lively, mid-bright pixels count most; pale or dim ones a little.
            val w = s * (1f - kotlin.math.abs(v - 0.7f))
            val i = ((hsv[0] / 360f) * BUCKETS).toInt().coerceIn(0, BUCKETS - 1)
            weight[i] += w; r[i] += pr * w; g[i] += pg * w; b[i] += pb * w
        }
        // Neighbouring buckets count together, so a hue on a bucket edge isn't split in two.
        var best = -1
        var bestScore = 0f
        for (i in 0 until BUCKETS) {
            val score = weight[i] + 0.5f * (weight[(i + 1) % BUCKETS] + weight[(i + BUCKETS - 1) % BUCKETS])
            if (score > bestScore) { bestScore = score; best = i }
        }
        if (best < 0 || weight[best] <= 0f) return null
        val w = weight[best]
        return tileable(argb((r[best] / w).toInt(), (g[best] / w).toInt(), (b[best] / w).toInt()))
    }

    /** [c] nudged into a range that works as a tile colour: not washed out, not too dark, not glaring. */
    fun tileable(c: Int): Int {
        val hsv = FloatArray(3)
        toHsv((c shr 16) and 0xff, (c shr 8) and 0xff, c and 0xff, hsv)
        return fromHsv(hsv[0], hsv[1].coerceIn(0.38f, 0.82f), hsv[2].coerceIn(0.5f, 0.86f))
    }

    fun argb(r: Int, g: Int, b: Int): Int = (0xff shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun toHsv(r: Int, g: Int, b: Int, out: FloatArray) {
        val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == rf -> 60f * (((gf - bf) / d).mod(6f))
            max == gf -> 60f * ((bf - rf) / d + 2f)
            else -> 60f * ((rf - gf) / d + 4f)
        }
        out[0] = if (h < 0f) h + 360f else h
        out[1] = if (max == 0f) 0f else d / max
        out[2] = max
    }

    fun fromHsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f).mod(2f) - 1f))
        val m = v - c
        val (rf, gf, bf) = when ((h / 60f).toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(((rf + m) * 255f + 0.5f).toInt(), ((gf + m) * 255f + 0.5f).toInt(), ((bf + m) * 255f + 0.5f).toInt())
    }
}
