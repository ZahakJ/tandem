package io.github.zahakj.tandem.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * A shared look: one `.tandem` file anyone can import.
 *
 * A `.tandem` file is a theme pack zip (docs/THEMES.md) with one more file, `look.json`:
 *
 *   look.json                          what this file is ([LookManifest]): name, author, the look settings
 *   theme.json                         the theme, in Tandem's pack format: colours, shape, motion, wallpapers
 *   preview.png                        a picture of the look, for galleries
 *   wallpapers/top.webp, bottom.webp   the wallpapers, when they are pictures
 *   systems/<id>/icon|hero|logo.png    system art, when the look uses a pack's art
 *   collections/<kind>/icon|hero.png   collection art (favorites, most_played, newly_added, recent)
 *
 * Because it is a pack, the old way in (Theme packs › Import a .zip) still installs it as a theme. Share my look
 * also applies `look.json`: the layout, highlight, sound pack and the rest.
 *
 * What goes in is allow-listed twice: [SharedLook] has a field for each look setting that may travel and nothing
 * else (never games, play stats, hidden games, PINs, accounts, keys, folder paths or device ids), and [LookFile]
 * writes and reads only the paths [LookFile.allowed] names. Plain Kotlin, no Android: LookFileTest checks it.
 */

/**
 * The look settings a shared look carries. Every field is optional: null leaves the setting as it is. Built from
 * [Look] field by field ([of]); there is no copy of the whole settings anywhere in a file.
 */
@Serializable
data class SharedLook(
    // Colour and highlight, over the theme.
    val accent: String? = null,
    val highlightColor: String? = null,
    val highlight: String? = null,
    val highlightThickness: Float? = null,
    val focusScale: Float? = null,
    val corner: Float? = null,
    val material: String? = null,
    val iconStyle: String? = null,
    // Motion.
    val motion: String? = null,
    val swapStyle: String? = null,
    val floating: Boolean? = null,
    val intensity: Int? = null,
    // Wallpaper: "theme" (the theme's own, in the file), "hero" or a backdrop id.
    val wallTop: String? = null,
    val wallBottom: String? = null,
    val wallDim: Float? = null,
    // Home and layout.
    val homeLayout: String? = null,
    val homeSections: List<String>? = null,
    val systemTiles: String? = null,
    val spotlightRail: String? = null,
    val spotlightBackdrop: String? = null,
    val spotlightMotion: Boolean? = null,
    val orbitGroups: String? = null,
    val orbitContinue: Boolean? = null,
    val gameView: String? = null,
    val folderPopup: Boolean? = null,
    val fullscreenFolders: Boolean? = null,
    val cardShape: String? = null,
    val shelfLook: String? = null,
    val gridSize: String? = null,
    val gridHeroSplit: Float? = null,
    val gridAcross: Int? = null,
    val gridRows: Int? = null,
    val gridScroll: String? = null,
    val gridRoundness: Float? = null,
    val gridGap: Float? = null,
    val gridLabels: Boolean? = null,
    val gridDetails: String? = null,
    val gridFolders: String? = null,
    val dockStyle: String? = null,
    val dockTop: Boolean? = null,
    val dockAutoHide: Boolean? = null,
    val dockBackground: Boolean? = null,
    val dockCount: Int? = null,
    val dockIconShape: String? = null,
    // The other screen's art.
    val bottomMode: String? = null,
    val heroSplit: Float? = null,
    val heroLogoScale: Float? = null,
    val heroFolderLogoScale: Float? = null,
    val heroGradient: String? = null,
    val kenBurns: Boolean? = null,
    // Sound: one of Tandem's own packs and soundtracks (recorded sounds and music files stay on the handheld).
    val soundPack: String? = null,
    val soundtrack: String? = null,
) {
    /** This look over [look]: the fields it has replace [look]'s, the rest stay. Call on a [sanitized] look. */
    fun applyTo(look: Look): Look = look.copy(
        accent = if (accent != null) accent.takeIf { it != NO_ACCENT } else look.accent,
        highlightColor = highlightColor ?: look.highlightColor,
        highlight = highlight ?: look.highlight,
        highlightThickness = highlightThickness ?: look.highlightThickness,
        focusScale = focusScale ?: look.focusScale,
        corner = corner ?: look.corner,
        material = material ?: look.material,
        iconStyle = iconStyle ?: look.iconStyle,
        motion = motion ?: look.motion,
        swapStyle = swapStyle ?: look.swapStyle,
        wallTop = wallTop ?: look.wallTop,
        wallBottom = wallBottom ?: look.wallBottom,
        wallDim = wallDim ?: look.wallDim,
        homeLayout = homeLayout ?: look.homeLayout,
        homeSections = homeSections ?: look.homeSections,
        systemTiles = systemTiles ?: look.systemTiles,
        spotlightRail = spotlightRail ?: look.spotlightRail,
        spotlightBackdrop = spotlightBackdrop ?: look.spotlightBackdrop,
        spotlightMotion = spotlightMotion ?: look.spotlightMotion,
        orbitGroups = orbitGroups ?: look.orbitGroups,
        orbitContinue = orbitContinue ?: look.orbitContinue,
        gameView = gameView ?: look.gameView,
        folderPopup = folderPopup ?: look.folderPopup,
        fullscreenFolders = fullscreenFolders ?: look.fullscreenFolders,
        cardShape = cardShape ?: look.cardShape,
        shelfLook = shelfLook ?: look.shelfLook,
        gridSize = gridSize ?: look.gridSize,
        gridHeroSplit = gridHeroSplit ?: look.gridHeroSplit,
        gridAcross = gridAcross ?: look.gridAcross,
        gridRows = gridRows ?: look.gridRows,
        gridScroll = gridScroll ?: look.gridScroll,
        gridRoundness = gridRoundness ?: look.gridRoundness,
        gridGap = gridGap ?: look.gridGap,
        gridLabels = gridLabels ?: look.gridLabels,
        gridDetails = gridDetails ?: look.gridDetails,
        gridFolders = gridFolders ?: look.gridFolders,
        dockStyle = dockStyle ?: look.dockStyle,
        dockTop = dockTop ?: look.dockTop,
        dockAutoHide = dockAutoHide ?: look.dockAutoHide,
        dockBackground = dockBackground ?: look.dockBackground,
        dockCount = dockCount ?: look.dockCount,
        dockIconShape = dockIconShape ?: look.dockIconShape,
        heroSplit = heroSplit ?: look.heroSplit,
        heroLogoScale = heroLogoScale ?: look.heroLogoScale,
        heroFolderLogoScale = heroFolderLogoScale ?: look.heroFolderLogoScale,
        heroGradient = heroGradient ?: look.heroGradient,
        kenBurns = kenBurns ?: look.kenBurns,
        // A pack of Tandem's own replaces recorded sounds, which are files on the sharer's handheld.
        soundPack = soundPack ?: look.soundPack,
        soundFiles = if (soundPack != null) "none" else look.soundFiles,
        soundtrack = soundtrack ?: look.soundtrack,
    )

    /**
     * Only values Tandem knows: anything else (a mistyped id, a number out of range, a path) becomes null, which
     * leaves that setting as it is. Every imported look goes through this.
     */
    fun sanitized(): SharedLook = SharedLook(
        accent = accent?.takeIf { it == NO_ACCENT || HEX.matches(it) },
        highlightColor = highlightColor.oneOf("item", "accent"),
        highlight = highlight.oneOf(*Ids.highlight),
        highlightThickness = highlightThickness.within(0f, 10f),
        focusScale = focusScale.within(1f, 1.3f),
        corner = corner.within(0f, 60f),
        material = material.oneOf(*Ids.material),
        iconStyle = iconStyle.oneOf(*Ids.iconStyle),
        motion = motion.oneOf(*Ids.motion),
        swapStyle = swapStyle.oneOf("scatter", "push", "flip", "depth", "fade", "instant"),
        floating = floating,
        intensity = intensity.within(0, 2),
        wallTop = wallTop.oneOf("theme", "hero", *Ids.backdrop),
        wallBottom = wallBottom.oneOf("theme", "hero", *Ids.backdrop),
        wallDim = wallDim.within(0f, 0.9f),
        homeLayout = homeLayout.oneOf("shelves", "grid", "carousel", "list", "console", "orbit", "spotlight"),
        homeSections = homeSections?.takeIf { s -> s.isNotEmpty() && s.size <= 3 && s.distinct().size == s.size && s.all { it in HOME_SECTIONS } },
        systemTiles = systemTiles.oneOf("pastel", "solid", "glass", "mono"),
        spotlightRail = spotlightRail.oneOf("left", "right"),
        spotlightBackdrop = spotlightBackdrop.oneOf("art", "blur", "dim"),
        spotlightMotion = spotlightMotion,
        orbitGroups = orbitGroups.oneOf("all", "games", "systems"),
        orbitContinue = orbitContinue,
        gameView = gameView.oneOf("grid", "list", "carousel"),
        folderPopup = folderPopup,
        fullscreenFolders = fullscreenFolders,
        cardShape = cardShape.oneOf("rounded", "square", "circle", "portrait", "landscape"),
        shelfLook = shelfLook.oneOf("wood", "matte", "open"),
        gridSize = gridSize?.takeIf { it in setOf("small", "medium", "large", "huge") || COLS.matches(it) },
        gridHeroSplit = gridHeroSplit.within(0f, 0.7f),
        gridAcross = gridAcross.within(3, 12),
        gridRows = gridRows.within(0, 5),
        gridScroll = gridScroll.oneOf("horizontal", "paged", "vertical"),
        gridRoundness = gridRoundness.within(0f, 0.5f),
        gridGap = gridGap.within(0f, 1f),
        gridLabels = gridLabels,
        gridDetails = gridDetails.oneOf("spotlight", "classic"),
        gridFolders = gridFolders.oneOf("popup", "fullscreen"),
        dockStyle = dockStyle.oneOf("bar", "shelf", "minimal"),
        dockTop = dockTop,
        dockAutoHide = dockAutoHide,
        dockBackground = dockBackground,
        dockCount = dockCount.within(1, 16),
        dockIconShape = dockIconShape.oneOf("rounded", "circle", "square"),
        bottomMode = bottomMode.oneOf("details", "art", "both", "clock"),
        heroSplit = heroSplit.within(0.2f, 0.8f),
        heroLogoScale = heroLogoScale.within(0.2f, 1f),
        heroFolderLogoScale = heroFolderLogoScale.within(0f, 1f),
        heroGradient = heroGradient.oneOf("fade", "strong", "vignette", "none"),
        kenBurns = kenBurns,
        soundPack = soundPack.oneOf(*Ids.soundPack),
        soundtrack = soundtrack.oneOf("calm", "meadow"),
    )

    companion object {
        /** [accent] for "the theme's own accent", which null can't say (null leaves the accent as it is). */
        const val NO_ACCENT = "theme"
        private val HEX = Regex("#[0-9A-Fa-f]{6}")
        private val COLS = Regex("cols:([2-9]|1[0-2])")
        private val HOME_SECTIONS = setOf("continue", "systems", "collections")

        /**
         * The look settings of [look], [settings] and [extras] that may travel. [wallTop] and [wallBottom] are
         * worked out by the caller ("theme" when the picture is in the file), never a path.
         */
        fun of(look: Look, settings: Settings, extras: ExtrasSettings, wallTop: String, wallBottom: String): SharedLook = SharedLook(
            accent = look.accent ?: NO_ACCENT,
            highlightColor = look.highlightColor,
            highlight = look.highlight,
            highlightThickness = look.highlightThickness,
            focusScale = look.focusScale,
            corner = look.corner,
            material = look.material,
            iconStyle = look.iconStyle,
            motion = look.motion,
            swapStyle = look.swapStyle,
            floating = extras.floating,
            intensity = extras.intensity,
            wallTop = wallTop,
            wallBottom = wallBottom,
            wallDim = look.wallDim,
            homeLayout = look.homeLayout,
            homeSections = look.homeSections,
            systemTiles = look.systemTiles,
            spotlightRail = look.spotlightRail,
            spotlightBackdrop = look.spotlightBackdrop,
            spotlightMotion = look.spotlightMotion,
            orbitGroups = look.orbitGroups,
            orbitContinue = look.orbitContinue,
            gameView = look.gameView,
            folderPopup = look.folderPopup,
            fullscreenFolders = look.fullscreenFolders,
            cardShape = look.cardShape,
            shelfLook = look.shelfLook,
            gridSize = look.gridSize,
            gridHeroSplit = look.gridHeroSplit,
            gridAcross = look.gridAcross,
            gridRows = look.gridRows,
            gridScroll = look.gridScroll,
            gridRoundness = look.gridRoundness,
            gridGap = look.gridGap,
            gridLabels = look.gridLabels,
            gridDetails = look.gridDetails,
            gridFolders = look.gridFolders,
            dockStyle = look.dockStyle,
            dockTop = look.dockTop,
            dockAutoHide = look.dockAutoHide,
            dockBackground = look.dockBackground,
            dockCount = look.dockCount,
            dockIconShape = look.dockIconShape,
            bottomMode = settings.bottomMode,
            heroSplit = look.heroSplit,
            heroLogoScale = look.heroLogoScale,
            heroFolderLogoScale = look.heroFolderLogoScale,
            heroGradient = look.heroGradient,
            kenBurns = look.kenBurns,
            soundPack = look.soundPack,
            soundtrack = look.soundtrack,
        ).sanitized()
    }
}

/**
 * The ids the UI's enums use (HighlightStyle, BackdropStyle, SoundPack…), copied here so this file needs nothing from
 * the UI; LookFileTest checks they match the enums.
 */
object Ids {
    val highlight = arrayOf("glow", "outline", "lift", "pulse")
    val material = arrayOf("solid", "frosted", "outlined")
    val motion = arrayOf("bouncy", "smooth", "minimal", "off")
    val iconStyle = arrayOf("bubble", "neon", "pixel", "flat", "plastic", "chrome", "lcd", "sticker", "rune")
    val backdrop = arrayOf(
        "blobs", "starfield", "waves", "synthwave", "bubbles", "petals", "fireflies", "pixels", "lcd", "cubes", "towers",
        "marquee", "shell", "disc", "visor", "road", "shooter", "farm", "meadow", "torch", "rainbow", "plain",
    )
    val soundPack = arrayOf("modern", "playful", "chip", "bubbly", "minimal", "studio", "glass", "air", "thock")
}

private fun String?.oneOf(vararg ids: String): String? = this?.takeIf { it in ids }
private fun Float?.within(lo: Float, hi: Float): Float? = this?.takeIf { !it.isNaN() && it in lo..hi }
private fun Int?.within(lo: Int, hi: Int): Int? = this?.takeIf { it in lo..hi }

/** `look.json`: what a `.tandem` file is, who made it, and the look settings it applies. */
@Serializable
data class LookManifest(
    val format: String = LookFile.FORMAT,
    val version: Int = LookFile.VERSION,
    val name: String = "",
    val author: String = "",
    val description: String = "",
    /** The day it was made, "2026-11-22" (no time). */
    val created: String = "",
    /** The Tandem version that made it. */
    val app: String = "",
    /** What it changes, ids of [LookPart]. */
    val parts: List<String> = emptyList(),
    val look: SharedLook = SharedLook(),
) {
    /** Names and text trimmed to size, without control characters; the look [SharedLook.sanitized]. */
    fun sanitized(): LookManifest = copy(
        name = clean(name, 60), author = clean(author, 40), description = clean(description, 300, lines = true),
        created = created.takeIf { DATE.matches(it) }.orEmpty(), app = clean(app, 20),
        parts = parts.filter { p -> LookPart.entries.any { it.id == p } }.distinct(),
        look = look.sanitized(),
    )

    companion object {
        private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")

        fun clean(s: String, max: Int, lines: Boolean = false): String =
            s.filter { c -> (lines && c == '\n') || !Character.isISOControl(c) }
                .filter { Character.getType(it) != Character.FORMAT.toInt() || it == '‌' || it == '‍' }
                .trim().take(max).trim()
    }
}

/** The parts of a look, as `look.json` lists them for the preview. */
enum class LookPart(val id: String) {
    Colours("colours"), Wallpaper("wallpaper"), Icons("icons"), Collections("collections"), Heroes("heroes"),
    Layout("layout"), Motion("motion"), Sounds("sounds"),
}

/** Why a file couldn't be read. The UI says it in words. */
enum class LookProblem { NotALook, NewerVersion, TooBig, TooManyFiles, BadPath, BadImage, ImageTooLarge, BadJson, Unreadable, Mismatch }

class LookFileException(val problem: LookProblem, message: String? = null) : IOException(message ?: problem.name)

/** Writing and reading `.tandem` files. */
object LookFile {
    const val FORMAT = "tandem-look"
    const val VERSION = 1
    const val EXTENSION = "tandem"
    const val MANIFEST = "look.json"
    const val THEME = "theme.json"

    /** The whole file, as downloaded or picked. */
    const val MAX_FILE = 24L shl 20
    /** Everything in it, unpacked. */
    const val MAX_UNPACKED = 48L shl 20
    const val MAX_ENTRIES = 1200
    const val MAX_IMAGE = 8L shl 20
    const val MAX_JSON = 256L shl 10
    /** Largest picture side, and most pixels: a 4K wallpaper fits, a decoding bomb doesn't. */
    const val MAX_SIDE = 4096
    const val MAX_PIXELS = 4096L * 4096L

    private const val IMAGE = "(png|jpg|jpeg|webp)"
    private val ALLOWED = listOf(
        Regex("look\\.json"), Regex("theme\\.json"),
        Regex("preview\\.$IMAGE"),
        Regex("wallpapers/(top|bottom)\\.$IMAGE"),
        Regex("systems/[a-z0-9_]{1,48}/(icon|hero|logo)\\.$IMAGE"),
        Regex("collections/(favorites|most_played|newly_added|recent)/(icon|hero)\\.$IMAGE"),
    )

    /** True for a path a `.tandem` file may hold. Nothing else is written or read. */
    fun allowed(path: String): Boolean = ALLOWED.any { it.matches(path) }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val pretty = Json(json) { prettyPrint = true }

    /** look.json's text: [LookManifest.format] and [LookManifest.version] first and always, unset fields left out. */
    fun encode(manifest: LookManifest): String {
        val fields = json.encodeToJsonElement(LookManifest.serializer(), manifest) as JsonObject
        val head = mapOf("format" to JsonPrimitive(manifest.format), "version" to JsonPrimitive(manifest.version))
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(head + fields.filterKeys { it !in head }))
    }

    /** A fixed time for every entry, so the same look makes the same bytes (and the same sha256). */
    private const val ENTRY_TIME = 315_532_800_000L + 12 * 3_600_000L

    /**
     * Writes a `.tandem` file: [manifest], [themeJson] and [files] (path in the file to its source). Throws for a
     * path [allowed] doesn't name: only allow-listed things are ever written.
     */
    fun write(out: OutputStream, manifest: LookManifest, themeJson: String, files: Map<String, File>) {
        for (path in files.keys) require(allowed(path) && path != MANIFEST && path != THEME) { "not allowed in a look: $path" }
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(path: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(path).apply { time = ENTRY_TIME })
                zip.write(bytes)
                zip.closeEntry()
            }
            put(MANIFEST, encode(manifest).toByteArray())
            put(THEME, themeJson.toByteArray())
            for ((path, f) in files.toSortedMap()) put(path, f.readBytes())
        }
    }

    /** A `.tandem` unpacked into [dir], checked. */
    class Unpacked(val dir: File, val manifest: LookManifest, val theme: JsonObject) {
        fun file(path: String): File = File(dir, path)
        /** Paths of the pictures in it. */
        val images: List<String> get() = dir.walkTopDown().filter { it.isFile && it.extension.lowercase() != "json" }
            .map { it.relativeTo(dir).invariantSeparatorsPath }.sorted().toList()
    }

    /**
     * Unpacks [input] into [dir] (which must be empty or missing) and checks everything: at most [MAX_FILE] read,
     * [MAX_ENTRIES] files, [MAX_UNPACKED] in all, only [allowed] paths (no `..`, no absolute paths, no links),
     * JSON under [MAX_JSON], pictures under [MAX_IMAGE] that really are PNG, JPEG or WebP no bigger than
     * [MAX_SIDE], and a `look.json` of this format and a version this Tandem knows. [checkImage] can look closer
     * (Android decodes the bounds). On any failure [dir] is deleted and a [LookFileException] says why.
     */
    fun read(input: InputStream, dir: File, checkImage: (File) -> Boolean = { true }): Unpacked {
        try {
            if (dir.exists() && dir.list().orEmpty().isNotEmpty()) throw IOException("not empty")
            dir.mkdirs()
            val base = dir.canonicalFile
            var total = 0L
            var count = 0
            val seen = HashSet<String>()
            ZipInputStream(Capped(input, MAX_FILE)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    if (name.contains('\\') || name.startsWith("/") || name.split('/').any { it == ".." || it == "." }) throw LookFileException(LookProblem.BadPath, name)
                    if (e.isDirectory) continue
                    // Clutter some zip tools add: skipped, never written.
                    if (name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")) continue
                    if (!allowed(name) || !seen.add(name)) throw LookFileException(LookProblem.BadPath, name)
                    if (++count > MAX_ENTRIES) throw LookFileException(LookProblem.TooManyFiles)
                    val out = File(base, name)
                    if (!out.canonicalPath.startsWith(base.path + File.separator)) throw LookFileException(LookProblem.BadPath, name)
                    out.parentFile?.mkdirs()
                    val cap = if (name.endsWith(".json")) MAX_JSON else MAX_IMAGE
                    var n = 0L
                    out.outputStream().use { o ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            val r = zip.read(buf)
                            if (r < 0) break
                            n += r; total += r
                            if (n > cap) throw LookFileException(if (cap == MAX_IMAGE) LookProblem.ImageTooLarge else LookProblem.TooBig, name)
                            if (total > MAX_UNPACKED) throw LookFileException(LookProblem.TooBig)
                            o.write(buf, 0, r)
                        }
                    }
                }
            }
            val manifestFile = File(base, MANIFEST)
            if (!manifestFile.isFile) throw LookFileException(LookProblem.NotALook)
            val raw = runCatching { json.parseToJsonElement(manifestFile.readText()) as JsonObject }.getOrElse { throw LookFileException(LookProblem.BadJson) }
            if ((raw["format"] as? JsonPrimitive)?.content != FORMAT) throw LookFileException(LookProblem.NotALook)
            val version = (raw["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: throw LookFileException(LookProblem.NotALook)
            if (version > VERSION) throw LookFileException(LookProblem.NewerVersion)
            if (version < 1) throw LookFileException(LookProblem.NotALook)
            val manifest = runCatching { json.decodeFromJsonElement(LookManifest.serializer(), raw) }.getOrElse { throw LookFileException(LookProblem.BadJson) }.sanitized()
            val themeFile = File(base, THEME)
            if (!themeFile.isFile) throw LookFileException(LookProblem.NotALook)
            val theme = runCatching { json.parseToJsonElement(themeFile.readText()) as JsonObject }.getOrElse { throw LookFileException(LookProblem.BadJson) }
            // Write the cleaned manifest back, so what is installed is what was checked.
            manifestFile.writeText(encode(manifest))
            val unpacked = Unpacked(base, manifest, theme)
            for (path in unpacked.images) {
                val f = File(base, path)
                val size = ImageSniff.size(f) ?: throw LookFileException(LookProblem.BadImage, path)
                if (!ImageSniff.matches(size.type, f.extension)) throw LookFileException(LookProblem.BadImage, path)
                if (size.width > MAX_SIDE || size.height > MAX_SIDE || size.width.toLong() * size.height > MAX_PIXELS) {
                    throw LookFileException(LookProblem.ImageTooLarge, path)
                }
                if (!checkImage(f)) throw LookFileException(LookProblem.BadImage, path)
            }
            return unpacked
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw when (e) {
                is LookFileException -> e
                is java.util.zip.ZipException -> LookFileException(LookProblem.NotALook, e.message)
                else -> LookFileException(LookProblem.Unreadable, e.message)
            }
        }
    }

    /** The file's SHA-256, lowercase hex. */
    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun sha256(input: InputStream): String {
        val d = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            d.update(buf, 0, n)
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    /** Throws [LookProblem.Mismatch] unless [file]'s SHA-256 is [expected]. */
    fun verify(file: File, expected: String) {
        if (!sha256(file).equals(expected.trim(), ignoreCase = true)) throw LookFileException(LookProblem.Mismatch)
    }

    /** Stops reading past [max] bytes: a huge file fails early instead of filling the storage. */
    private class Capped(input: InputStream, private val max: Long) : FilterInputStream(input) {
        private var read = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count(it.toLong()) }
        private fun count(n: Long) { read += n; if (read > max) throw LookFileException(LookProblem.TooBig) }
    }
}

/**
 * A picture's kind and size from its first bytes, without decoding it: PNG (IHDR), JPEG (the first frame header)
 * and WebP (VP8, VP8L, VP8X). Null for anything else.
 */
object ImageSniff {
    data class Size(val type: String, val width: Int, val height: Int)

    fun matches(type: String, extension: String): Boolean = when (extension.lowercase()) {
        "png" -> type == "png"
        "jpg", "jpeg" -> type == "jpeg"
        "webp" -> type == "webp"
        else -> false
    }

    fun size(f: File): Size? = runCatching { f.inputStream().buffered().use { size(it) } }.getOrNull()

    fun size(input: InputStream): Size? {
        val head = ByteArray(30)
        // Not readNBytes: Android has it only from API 33.
        var n = 0
        while (n < head.size) {
            val r = input.read(head, n, head.size - n)
            if (r < 0) break
            n += r
        }
        fun u8(i: Int) = head[i].toInt() and 0xFF
        fun be32(i: Int) = (u8(i) shl 24) or (u8(i + 1) shl 16) or (u8(i + 2) shl 8) or u8(i + 3)
        fun le16(i: Int) = u8(i) or (u8(i + 1) shl 8)
        fun le24(i: Int) = u8(i) or (u8(i + 1) shl 8) or (u8(i + 2) shl 16)
        if (n >= 24 && u8(0) == 0x89 && head[1] == 'P'.code.toByte() && head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte() &&
            String(head, 12, 4, Charsets.US_ASCII) == "IHDR"
        ) return Size("png", be32(16), be32(20)).takeIf { it.width > 0 && it.height > 0 }
        if (n >= 30 && String(head, 0, 4, Charsets.US_ASCII) == "RIFF" && String(head, 8, 4, Charsets.US_ASCII) == "WEBP") {
            return when (String(head, 12, 4, Charsets.US_ASCII)) {
                "VP8 " -> Size("webp", le16(26) and 0x3FFF, le16(28) and 0x3FFF)
                "VP8L" -> if (u8(20) != 0x2F) null else {
                    val bits = u8(21) or (u8(22) shl 8) or (u8(23) shl 16) or (u8(24) shl 24)
                    Size("webp", (bits and 0x3FFF) + 1, ((bits ushr 14) and 0x3FFF) + 1)
                }
                "VP8X" -> Size("webp", le24(24) + 1, le24(27) + 1)
                else -> null
            }?.takeIf { it.width > 0 && it.height > 0 }
        }
        if (n >= 4 && u8(0) == 0xFF && u8(1) == 0xD8) return jpeg(head, n, input)
        return null
    }

    /** Walks the JPEG markers to the first frame header (SOF0…SOF15, not DHT, JPG or DAC). */
    private fun jpeg(head: ByteArray, n: Int, rest: InputStream): Size? {
        val input = java.io.SequenceInputStream(java.io.ByteArrayInputStream(head, 2, n - 2), rest)
        fun byte(): Int = input.read().also { if (it < 0) throw IOException("end") }
        var steps = 0
        while (steps++ < 256) {
            var b = byte()
            if (b != 0xFF) return null
            while (b == 0xFF) b = byte()
            val marker = b
            if (marker == 0xD9 || marker == 0xDA) return null
            if (marker in 0xD0..0xD7 || marker == 0x01) continue
            val len = (byte() shl 8) or byte()
            if (len < 2) return null
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                byte() // precision
                val h = (byte() shl 8) or byte()
                val w = (byte() shl 8) or byte()
                return Size("jpeg", w, h).takeIf { w > 0 && h > 0 }
            }
            var skip = (len - 2).toLong()
            while (skip > 0) {
                val s = input.skip(skip)
                if (s <= 0) { byte(); skip-- } else skip -= s
            }
        }
        return null
    }
}
