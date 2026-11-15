package io.github.zahakj.tandem.data

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * How each system's games really came packaged, for the Shelf view: the case's size and plastic, the printed cover's
 * shape, and the band with the system's logo that runs across the cover's top and the spine's head. North American
 * releases are the default; where Europe or Japan differ in a way a shelf shows, the region setting picks.
 *
 * Sizes are height × width × thickness of the case (or cardboard box), in millimetres. Sources:
 *   keep cases, DS/3DS, PSP, Vita, Switch:  https://en.wikipedia.org/wiki/Keep_case (table of game cases, sleeve sizes)
 *   Switch insert (208 × 161 mm, 10 mm red spine, #E60012): https://miketendo64.com/2018/09/29/guide-make-your-own-alternate-switch-box-art-covers/
 *   SNES / N64 boxes (7 × 5 × 1.3 in):      https://www.target.com/p/old-skool-game-box-protector-for-snes-nintendo-64-20-pack/-/A-93857139
 *   NES boxes (5.1 × 7.1 × 1.4 in):         https://www.newegg.com/p/1W7-00T2-00WZ2
 *   Game Boy / GBC / GBA boxes (5 × 5 × 1 in): https://displaygeek.com/products/video-game-case-for-game-boy-gbc-gba-virtual-boy-game-boxes-50mm-thick-uv-scratch-resistant
 *   PAL cases and spine marks:              https://en.wikipedia.org/wiki/Video_game_packaging
 * The bands (colour, side, logo) are as the retail covers print them; none of the logos are copied, each is a plain
 * mark drawn by the Shelf view (text, or a few shapes).
 */

/** Which edge of the front cover the system's band runs along. */
enum class BandSide { Top, Left, Right }

/** A system's logo on its band, beyond the plain wordmark: a few shapes the Shelf view draws itself. */
enum class BandMark { None, Switch, Cube, Grid, Swirl, PlayStation }

/**
 * One system's retail packaging. [plastic] is the case's colour, null for a cardboard box (no rim of plastic round
 * the print). [frontBand] is how much of the cover the band takes (of its height when on top, of its width when at
 * the left); [spineBand] how much of the spine's height its head takes. Colours are ARGB.
 */
class PackSpec(
    val key: String,
    val heightMm: Float,
    val widthMm: Float,
    val spineMm: Float,
    val plastic: Long?,
    val band: Long,
    val bandInk: Long,
    val wordmark: String,
    val spineMark: String,
    val side: BandSide = BandSide.Top,
    val frontBand: Float = 0.1f,
    val spineBand: Float = 0.12f,
    val mark: BandMark = BandMark.None,
    /** The printed cover's own width over height, when it isn't the case's (a jewel case's square booklet). */
    cover: Float? = null,
) {
    /** The printed front's width over its height. */
    val coverRatio: Float = cover ?: (widthMm / heightMm)
    /** The spine's width over the case's height. */
    val spineRatio: Float get() = spineMm / heightMm
    val cardboard: Boolean get() = plastic == null
}

object Packaging {
    private const val RED = 0xFFE60012
    private const val WHITE = 0xFFF4F4F1
    private const val BLACK = 0xFF15161A
    private const val CLEAR = 0xFFD9DEE3
    private const val XBOX_GREEN = 0xFF2E8B2E

    // Keep cases (DVD size, 190 × 135 × 14, printed sleeve 183 × 129.5) and Blu-ray size (171 × 135 × 12).
    private fun dvd(key: String, plastic: Long, band: Long, ink: Long, word: String, spine: String, mark: BandMark = BandMark.None) =
        PackSpec(key, 190f, 135f, 14f, plastic, band, ink, word, spine, frontBand = 0.085f, spineBand = 0.1f, mark = mark, cover = 129.5f / 183f)
    private fun bluRay(key: String, plastic: Long, band: Long, ink: Long, word: String, spine: String) =
        PackSpec(key, 171f, 135f, 12f, plastic, band, ink, word, spine, frontBand = 0.085f, spineBand = 0.1f, cover = 0.79f)
    // A CD jewel case (125 × 142 × 10.4): the booklet behind its lid is square, 120 × 120.
    private fun jewel(key: String, band: Long, ink: Long, word: String, spine: String, side: BandSide, mark: BandMark) =
        PackSpec(key, 125f, 142f, 10.4f, CLEAR, band, ink, word, spine, side, frontBand = if (side == BandSide.Left) 0.11f else 0.1f, spineBand = 0.16f, mark = mark, cover = 1f)
    // A cardboard box: the art printed round it, the logo strip along the top of the front.
    private fun box(key: String, h: Float, w: Float, d: Float, band: Long, ink: Long, word: String, spine: String, frontBand: Float = 0.08f) =
        PackSpec(key, h, w, d, null, band, ink, word, spine, frontBand = frontBand, spineBand = 0.12f)

    private val specs: Map<String, PackSpec> = listOf(
        // Switch: red case (170 × 104 × 10, insert front 99 × 161). The cover's top is a red band with the white
        // Nintendo Switch logo at the left; the spine's head is red with the logo mark in a 22 mm square, the title
        // runs down the art, the publisher's logo at the foot. ESRB (NA) / PEGI (EU) sits in the cover's lower left.
        PackSpec("switch", 170f, 104f, 10f, RED, RED, 0xFFFFFFFF, "NINTENDO SWITCH", "", frontBand = 0.1f, spineBand = 0.13f, mark = BandMark.Switch, cover = 99f / 161f),
        // 3DS: white case (135 × 122 × 12). White band across the cover's top with "NINTENDO 3DS" (the 3DS in red),
        // the same white head on the spine with the red 3DS mark, the art and title below it.
        // The 3DS front carries its logo down a white strip along the RIGHT edge ("NINTENDO 3DS", reading top to bottom),
        // not across the top.
        PackSpec("n3ds", 135f, 122f, 12f, WHITE, WHITE, 0xFFD7182A, "NINTENDO 3DS", "3DS", side = BandSide.Right, frontBand = 0.085f, spineBand = 0.12f, cover = 0.88f),
        // DS: black case in North America and Japan (135 × 122 × 15), clear in Europe. A silver-white band across
        // the top with the "NINTENDO DS" logo in black; the spine's head the same.
        PackSpec("nds", 135f, 122f, 15f, BLACK, 0xFFE9EAEC, 0xFF1B1C1F, "NINTENDO DS", "DS", frontBand = 0.1f, spineBand = 0.12f, cover = 0.88f),
        // Wii: white keep case. White band along the top with the grey Wii logo; white spine head.
        dvd("wii", WHITE, WHITE, 0xFF8A9096, "Wii", "Wii"),
        // Wii U: bright blue keep case, a blue band with the white Wii U logo, blue spine head.
        dvd("wiiu", 0xFF0F9AD6, 0xFF0F9AD6, 0xFFFFFFFF, "Wii U", "Wii U"),
        // GameCube: black keep case with a memory card slot. A black band along the top with the purple cube and
        // "NINTENDO GAMECUBE"; the cube on the spine's black head.
        dvd("gc", BLACK, BLACK, 0xFFFFFFFF, "NINTENDO GAMECUBE", "", BandMark.Cube),
        // PS2: black keep case (Europe's later ones dark blue). A black band along the top with the PlayStation 2
        // logo; black spine head with the logo across it.
        dvd("ps2", BLACK, BLACK, 0xFFFFFFFF, "PlayStation 2", "PS2"),
        // PS3: clear Blu-ray case. Black band along the top with the PS3 logo (the first year's were a silver
        // "PLAYSTATION 3" band); black spine head.
        bluRay("ps3", CLEAR, BLACK, 0xFFFFFFFF, "PS3", "PS3"),
        // PS4: translucent blue Blu-ray case. A blue band with the white PS4 logo; blue spine head.
        bluRay("ps4", 0xFF1F5FB0, 0xFF0A47A8, 0xFFFFFFFF, "PS4", "PS4"),
        // PS5: translucent blue-white case, a white band with the black PS5 logo; white spine head.
        bluRay("ps5", 0xFFD6E2EE, 0xFFFFFFFF, 0xFF111111, "PS5", "PS5"),
        // PSP: clear UMD case (168 × 99 × 14). A black band along the top with the PSP logo; black spine head.
        PackSpec("psp", 168f, 99f, 14f, CLEAR, BLACK, 0xFFFFFFFF, "PSP", "PSP", frontBand = 0.09f, spineBand = 0.11f),
        // Vita: blue case (135 × 105 × 12). A blue band along the top with the white PS Vita logo; blue spine head.
        PackSpec("psvita", 135f, 105f, 12f, 0xFF1D57B8, 0xFF0D4BB0, 0xFFFFFFFF, "PS VITA", "VITA", frontBand = 0.1f, spineBand = 0.12f),
        // PlayStation: clear jewel case. North America's covers have a black strip down the left with the
        // PlayStation logo; the case's spine card is black at the head with the logo.
        jewel("psx", BLACK, 0xFFFFFFFF, "PlayStation", "PS", BandSide.Left, BandMark.PlayStation),
        // Xbox: lime green keep case, a green band along the top with the XBOX logo, green spine head.
        dvd("xbox", 0xFF3E9C35, 0xFF3E9C35, 0xFFFFFFFF, "XBOX", "XBOX"),
        // Xbox 360: translucent lime green keep case; the band along the top green with the white XBOX 360 logo
        // (the launch years' band was silver-white with a green logo), green spine head.
        dvd("xbox360", 0xFF5DB83A, 0xFF4AA83A, 0xFFFFFFFF, "XBOX 360", "360"),
        // Xbox One: translucent green Blu-ray case, a green band with the white XBOX ONE logo.
        bluRay("xboxone", XBOX_GREEN, 0xFF107C10, 0xFFFFFFFF, "XBOX ONE", "ONE"),
        // Xbox Series X|S: green Blu-ray case, a black band with the white XBOX SERIES X logo.
        bluRay("xboxseries", XBOX_GREEN, BLACK, 0xFFFFFFFF, "XBOX SERIES X", "X|S"),
        // Genesis: black plastic clamshell (about 185 × 135 × 18). The early covers' band is Sega's black and red
        // grid along the top with "SEGA GENESIS"; the spine is black with the grid at its head.
        PackSpec("genesis", 185f, 135f, 18f, BLACK, BLACK, 0xFFFFFFFF, "SEGA GENESIS", "GENESIS", frontBand = 0.1f, spineBand = 0.12f, mark = BandMark.Grid),
        // Sega CD: the tall plastic case of the Genesis's size, black band with "SEGA CD".
        PackSpec("segacd", 185f, 135f, 18f, BLACK, BLACK, 0xFFFFFFFF, "SEGA CD", "SEGA CD", frontBand = 0.1f, spineBand = 0.12f),
        // Saturn: North America's taller CD case, a black band along the top with the SEGA SATURN logo. (Japan's
        // were square jewel cases.)
        PackSpec("saturn", 185f, 135f, 14f, BLACK, BLACK, 0xFFFFFFFF, "SEGA SATURN", "SATURN", frontBand = 0.1f, spineBand = 0.12f),
        // Dreamcast: clear jewel case; the cover's top strip white with the orange swirl and "Dreamcast" (Japan's
        // are white with an orange spine).
        jewel("dreamcast", WHITE, 0xFFE8641E, "Dreamcast", "DC", BandSide.Top, BandMark.Swirl),
        // NES: a portrait cardboard box, 5.1 × 7.1 × 1.4 in, its art round the spine with the title.
        box("nes", 180f, 130f, 35f, BLACK, 0xFFFFFFFF, "Nintendo Entertainment System", "NES", frontBand = 0.06f),
        // SNES and N64: landscape cardboard boxes, 7 × 5 × 1.3 in.
        box("snes", 127f, 178f, 33f, 0xFF4A4A52, 0xFFFFFFFF, "SUPER NINTENDO", "SNES"),
        box("n64", 127f, 178f, 33f, BLACK, 0xFFFFFFFF, "NINTENDO 64", "N64"),
        // Game Boy, Game Boy Color, Game Boy Advance: nearly square boxes, 5 × 5 × 1 in.
        box("gb", 127f, 127f, 25f, 0xFF5B5E66, 0xFFFFFFFF, "GAME BOY", "GB"),
        box("gbc", 127f, 127f, 25f, 0xFF5B5E66, 0xFFFFFFFF, "GAME BOY COLOR", "GBC"),
        box("gba", 127f, 127f, 25f, 0xFF2B2D72, 0xFFFFFFFF, "GAME BOY ADVANCE", "GBA"),
    ).associateBy { it.key }

    /** Other names a system goes by (its art folders, its ROM folders, Cocoon's ids), to its key above. */
    private val names = mapOf(
        "3ds" to "n3ds", "nds" to "nds", "ds" to "nds", "wii" to "wii", "wiiu" to "wiiu", "gamecube" to "gc", "ngc" to "gc",
        "ps1" to "psx", "playstation" to "psx", "vita" to "psvita", "psv" to "psvita", "xboxseriesx" to "xboxseries",
        "xsx" to "xboxseries", "famicom" to "nes", "fc" to "nes", "sfc" to "snes", "superfamicom" to "snes", "megadrive" to "genesis",
        "md" to "genesis", "megacd" to "segacd", "ss" to "saturn", "dc" to "dreamcast",
    )

    /**
     * The packaging for a game whose system goes by [ids], or null for a system without a retail box of its own (a
     * PC, an arcade, an app). Europe's DS cases are clear, and its and Japan's Genesis is the Mega Drive.
     */
    fun of(ids: List<String>, region: Region = Region.US): PackSpec? {
        val spec = ids.asSequence().map { it.lowercase() }.firstNotNullOfOrNull { specs[it] ?: names[it]?.let(specs::get) } ?: return null
        return when {
            spec.key == "nds" && region == Region.EU -> PackSpec("nds", 135f, 122f, 20f, CLEAR, spec.band, spec.bandInk, spec.wordmark, spec.spineMark, frontBand = 0.1f, spineBand = 0.12f, cover = 0.88f)
            spec.key == "genesis" && (region == Region.EU || region == Region.JP) ->
                PackSpec("genesis", 185f, 135f, 18f, BLACK, BLACK, 0xFFFFFFFF, "MEGA DRIVE", "MEGA DRIVE", frontBand = 0.1f, spineBand = 0.12f, mark = BandMark.Grid)
            else -> spec
        }
    }

    /** Every system's packaging, for the tests. */
    internal val all: Collection<PackSpec> get() = specs.values

    /**
     * True when a picture of [aspect] (width over height) is a real box scan for [spec], so it is shown as it is
     * rather than given the system's band. A picture from the physical-box folder always is ([fromScans]). Otherwise
     * it has to be within 6 % of the real cover's shape: real scans are (a SteamGridDB 2 : 3 grid is 8 % off a Switch
     * box and gets its band; a square store icon is far off any keep case). A square jewel case can't tell a scan
     * from a square icon, so square art on a PlayStation or Dreamcast shelf is taken as it is.
     */
    fun isScan(aspect: Float?, spec: PackSpec?, fromScans: Boolean = false): Boolean {
        if (fromScans) return true
        if (spec == null || aspect == null || aspect <= 0f) return true
        return abs(aspect / spec.coverRatio - 1f) <= SCAN_TOLERANCE
    }

    const val SCAN_TOLERANCE = 0.06f

    /**
     * True when a picture ([argb], [w] by [h] pixels, a small decode) already has the system's band printed along its
     * edge, as a real box scan does: most of the strip where the band goes is the band's colour. This catches real
     * scans whatever their shape (scraped 3DS fronts often include a sliver of spine and are wider than the case),
     * so they never get a second band.
     */
    fun hasBand(argb: IntArray, w: Int, h: Int, spec: PackSpec): Boolean {
        if (w < 8 || h < 8 || argb.size < w * h) return false
        val depth = when (spec.side) { BandSide.Top -> h; else -> w }
        val strip = (depth * spec.frontBand * 0.6f).roundToInt().coerceAtLeast(1)
        val band = spec.band.toInt()
        var near = 0
        var all = 0
        fun close(c: Int): Boolean {
            val dr = abs(((c shr 16) and 0xFF) - ((band shr 16) and 0xFF))
            val dg = abs(((c shr 8) and 0xFF) - ((band shr 8) and 0xFF))
            val db = abs((c and 0xFF) - (band and 0xFF))
            return maxOf(dr, dg, db) <= 48
        }
        for (y in 0 until h) for (x in 0 until w) {
            val inStrip = when (spec.side) {
                BandSide.Top -> y in 1..strip
                BandSide.Left -> x in 1..strip
                BandSide.Right -> x in (w - 1 - strip) until (w - 1)
            }
            if (!inStrip) continue
            all++
            if (close(argb[y * w + x])) near++
        }
        // The logo is printed on the band, so a fair part of it is the logo's ink.
        return all > 0 && near >= all * 0.55f
    }

    /**
     * Where the spine is in a full wraparound scan (ScreenScraper's "box-texture"): back,
     * spine and front side by side, the fronts as wide as the scan is tall times the cover's shape, the spine what is
     * left in the middle. The middle holds whichever way the box opens (Japan's fronts are on the left). Null when the
     * picture isn't a wraparound for this box (what is left over is not a plausible spine).
     */
    fun spineIn(width: Int, height: Int, spec: PackSpec): IntRange? {
        if (width <= 0 || height <= 0) return null
        val front = height * spec.coverRatio
        val spine = width - 2f * front
        // A real spine is between a fiftieth of the box's height (a Switch case is 6 %) and a third (an N64 box, 26 %).
        if (spine < height * 0.02f || spine > height * 0.35f) return null
        val start = front.roundToInt()
        val end = (width - front).roundToInt()
        return if (end > start) start until end else null
    }

    /** Where the front is in a wraparound scan: the right part, or the left for a box that opens right to left. */
    fun frontIn(width: Int, height: Int, spec: PackSpec, rightToLeft: Boolean): IntRange? {
        spineIn(width, height, spec) ?: return null
        val front = (height * spec.coverRatio).roundToInt().coerceAtMost(width)
        return if (rightToLeft) 0 until front else (width - front) until width
    }

    /** True for a picture shaped like a lone spine: many times taller than wide, or (stored on its side) wider than tall. */
    fun isSpineShaped(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val thin = minOf(width, height).toFloat() / maxOf(width, height)
        return thin <= 0.4f
    }
}
