package io.github.zahakj.tandem.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.Library
import io.github.zahakj.tandem.data.PackParts
import io.github.zahakj.tandem.data.Store
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Which format a pack's theme.json is written in. */
enum class PackFormat(val label: String) { Tandem("Tandem pack"), Cocoon("Cocoon pack") }

/**
 * An installed theme pack: its theme, the art it brings and where it came from.
 *
 * [removable] is true for packs in `ROMs/tandem/themes` (Tandem copied or imported them); packs Cocoon installed in
 * `ROMs/themes` belong to Cocoon and are never deleted by Tandem.
 */
data class ThemePack(
    val id: String,
    val dir: File,
    val format: PackFormat,
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val spec: ThemeSpec,
    val art: PackArt,
    val preview: File?,
    /** Asset packs this theme uses ("Example - Smart Folders by Someone"). */
    val credits: List<String>,
    val removable: Boolean,
) {
    val hasWallpaper: Boolean get() = spec.wallpaperTop != null || spec.wallpaperBottom != null

    /** "62 system icons · 60 heroes · wallpaper", for the pack list. */
    val contents: String
        get() = buildList {
            add("colours")
            if (art.iconCount > 0) add(plural(art.iconCount, "system icon"))
            if (art.heroCount > 0) add(plural(art.heroCount, "hero", "heroes"))
            if (art.collectionCount > 0) add(plural(art.collectionCount, "collection icon"))
            if (art.overlayCount > 0) add(plural(art.overlayCount, "cover frame"))
            if (spec.wallpaperTop != null && spec.wallpaperBottom != null) add("2 wallpapers")
            else if (hasWallpaper) add("wallpaper")
        }.joinToString(" · ")

    private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
}

/**
 * Art for systems and collections from a pack. Reads the Cocoon smart folder layout (`by_platform/<id>/icon.png`,
 * `favorites/icon.png`...) and Tandem's own (`systems/<id>/icon.png`, `collections/<kind>/icon.png`). Lookups are
 * cached, so asking for the same icon every frame costs a map read.
 */
class PackArt(
    /** Folders holding one folder per system. */
    private val systemRoots: List<File>,
    /** Folders holding favorites/, most_played/, newly_added/, recent/. */
    private val collectionRoots: List<File>,
    /** Folders holding one folder of cover frames (mask.png, overlay.png) per system. */
    private val overlayRoots: List<File>,
) {
    private fun index(roots: List<File>): Map<String, File> {
        val out = LinkedHashMap<String, File>()
        for (root in roots) for (d in root.listFiles().orEmpty()) {
            if (d.isDirectory) out.putIfAbsent(norm(d.name), d)
        }
        return out
    }

    private val systems: Map<String, File> by lazy { index(systemRoots) }
    private val collections: Map<String, File> by lazy { index(collectionRoots) }
    private val overlays: Map<String, File> by lazy { index(overlayRoots) }
    private val found = ConcurrentHashMap<String, Any>()
    private object None

    val isEmpty: Boolean get() = systemRoots.isEmpty() && collectionRoots.isEmpty() && overlayRoots.isEmpty()
    val iconCount: Int by lazy { systems.values.count { image(it, "icon") != null } }
    val heroCount: Int by lazy { systems.values.count { image(it, "hero") != null } }
    val collectionCount: Int by lazy { COLLECTIONS.count { collections[norm(it)]?.let { d -> image(d, "icon") } != null } }
    val overlayCount: Int by lazy { overlays.size }

    private fun cached(key: String, find: () -> File?): File? {
        val hit = found[key]
        if (hit != null) return hit as? File
        val f = find()
        found[key] = f ?: None
        return f
    }

    /** `icon`, `hero`, `logo`, `mask` or `overlay` for the first of [ids] the pack has. */
    fun system(ids: List<String>, kind: String): File? = cached("s:${ids.firstOrNull()}:$kind") {
        ids.asSequence().mapNotNull { systems[norm(it)] }.firstNotNullOfOrNull { image(it, kind) }
    }

    fun collection(kind: String, file: String = "icon"): File? = cached("c:$kind:$file") {
        collections[norm(kind)]?.let { image(it, file) }
    }

    /** A cover frame for a system's games: the mask the cover is cut to and the frame drawn over it. */
    fun overlay(ids: List<String>, kind: String): File? = cached("o:${ids.firstOrNull()}:$kind") {
        val dir = ids.asSequence().mapNotNull { overlays[norm(it)] }.firstOrNull() ?: return@cached null
        image(dir, kind) ?: if (kind == "overlay") {
            // Some packs name the frame after the system; take the one picture that isn't the mask.
            dir.listFiles().orEmpty().filter { it.isImage() && !it.nameWithoutExtension.contains("mask", true) }
                .singleOrNull()
        } else null
    }

    /** Every system icon in the pack, as (folder name, picture). Reads folders: call it off the main thread. */
    fun icons(): List<Pair<String, File>> = systems.values.mapNotNull { d -> image(d, "icon")?.let { d.name to it } }

    private fun image(dir: File, kind: String): File? =
        IMAGE_EXT.asSequence().map { File(dir, "$kind.$it") }.firstOrNull { it.isFile }

    companion object {
        val EMPTY = PackArt(emptyList(), emptyList(), emptyList())
        val COLLECTIONS = listOf("favorites", "most_played", "newly_added", "recent")
        private val IMAGE_EXT = listOf("png", "webp", "jpg", "jpeg")
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        private fun File.isImage() = isFile && extension.lowercase() in IMAGE_EXT
    }
}

/**
 * Theme packs from the ROMs folder:
 * - `ROMs/tandem/themes/<pack>/theme.json`: Tandem's own format, or a Cocoon pack imported into Tandem, and
 * - `ROMs/themes/<pack>/theme.json`: packs Cocoon downloaded, with their art in `ROMs/assets/` (see docs/THEMES.md).
 *
 * Packs add colours, shapes, wallpapers and art; anything missing falls back to a built-in base theme. The art is
 * read through [systemIcon], [systemHero], [systemLogo], [collectionIcon] and [coverFrame], which follow the chosen
 * pack and its [PackParts] switches.
 */
object ThemePacks {
    val tandemDir: File get() = File(Library.romsDir, "tandem/themes")
    val cocoonDir: File get() = File(Library.romsDir, "themes")
    val cocoonAssetsDir: File get() = File(Library.romsDir, "assets")

    val videoExtensions = setOf("mp4", "webm", "mkv", "3gp")
    val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    /** Every installed pack, Tandem's folder first. */
    var packs by mutableStateOf<List<ThemePack>>(emptyList())
        private set

    fun byId(id: String?): ThemePack? = id?.let { i -> packs.firstOrNull { it.id == i } }

    /** The most recently installed of [list] (by folder date), else the first. */
    fun newest(list: List<ThemePack>): ThemePack? = list.maxByOrNull { it.dir.lastModified() }

    /** The installed Cocoon pack to suggest when nothing names one. */
    fun newestCocoonPack(): ThemePack? = newest(packs.filter { it.format == PackFormat.Cocoon })

    /** Reads every pack again. Cheap: art folders are only listed when first used. Call off the main thread when you can. */
    fun reload() {
        packs = runCatching { load() }.getOrDefault(emptyList())
        assetPacks = runCatching { loadAssetPacks() }.getOrDefault(emptyList())
    }

    /** [reload] with the reading done off the main thread. */
    private var refreshedAt = 0L

    /** Skipped when the packs were read in the last half minute, unless [force]d (Look again, an import). */
    suspend fun refresh(force: Boolean = false) {
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && refreshedAt != 0L && now - refreshedAt < 30_000) return
        refreshedAt = now
        val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { load() }.getOrDefault(emptyList()).also { packs ->
                // Count and look up the art here, so the pack list and the tiles never list folders on the main thread.
                val ids = Libraries.all.orEmpty().map { it.platform.id }
                for (p in packs) { p.contents; ids.forEach { id -> p.art.system(systemIds(id), "icon") } }
            }
        }
        val assets = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { loadAssetPacks() }.getOrDefault(emptyList()).onEach { it.art.iconCount }
        }
        packs = list
        assetPacks = assets
    }

    fun load(): List<ThemePack> {
        val out = mutableListOf<ThemePack>()
        for (dir in tandemDir.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name.lowercase() }) {
            runCatching { parse(dir, "pack:${dir.name}", removable = true) }.getOrNull()?.let(out::add)
        }
        for (dir in cocoonDir.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name.lowercase() }) {
            runCatching { parse(dir, "cocoon:${dir.name}", removable = false) }.getOrNull()?.let(out::add)
        }
        return out
    }

    // ---- Art for the rest of the app ----

    /** A Cocoon smart-folder icon pack in ROMs/assets/smart_folders, usable on its own with any theme. */
    data class AssetPack(val dir: File, val id: String?, val name: String, val author: String, val art: PackArt) {
        /** The key its switches are stored under in Look.packParts. */
        val partsKey: String get() = "assets:${dir.name}"
    }

    /** Smart-folder packs found in ROMs/assets/smart_folders. */
    var assetPacks by mutableStateOf<List<AssetPack>>(emptyList())
        private set

    private fun loadAssetPacks(): List<AssetPack> =
        File(cocoonAssetsDir, "smart_folders").listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name.lowercase() }.mapNotNull { dir ->
            val byPlatform = findDir(dir, "by_platform", 3) ?: return@mapNotNull null
            val meta = assetMeta(dir)
            AssetPack(
                dir, meta?.optString("id")?.ifBlank { null }, meta?.optString("name")?.ifBlank { null } ?: dir.name,
                meta?.optString("author").orEmpty(), PackArt(listOf(byPlatform), listOfNotNull(byPlatform.parentFile), emptyList()),
            )
        }

    /** The switches for pack [id]; everything on unless the user turned it off. */
    fun parts(id: String): PackParts = Store.settings.look.packParts[id] ?: PackParts()

    /**
     * Where system and collection art comes from now, as (switches key, art), following Look.iconPack: "none" draws
     * everything; "auto" uses the theme pack's own art, or with a built-in theme the first smart-folder pack
     * installed; otherwise the chosen theme pack or smart-folder pack.
     */
    val artSource: Pair<String, PackArt>?
        get() {
            val look = Store.settings.look
            fun asset(p: AssetPack) = p.partsKey to p.art
            fun theme(p: ThemePack) = p.id to p.art
            return when (val choice = look.iconPack) {
                ICONS_DRAWN -> null
                "auto", "" -> {
                    val own = byId(look.theme)
                    when {
                        own == null -> assetPacks.firstOrNull()?.let(::asset)
                        own.art.isEmpty -> null
                        else -> theme(own)
                    }
                }
                else -> byId(choice)?.let(::theme) ?: assetPacks.firstOrNull { it.dir.name == choice }?.let(::asset)
            }
        }

    /** The theme pack whose art is shown now, if the art comes from one. */
    val artPack: ThemePack? get() = artSource?.first?.let { byId(it) }

    /** Everything "Icons from" can choose: (Look.iconPack value, label). */
    fun iconSources(): List<Pair<String, String>> =
        listOf("auto" to "Auto", ICONS_DRAWN to "Drawn by Tandem") +
            packs.filter { it.art.iconCount > 0 || it.art.collectionCount > 0 }.map { it.id to it.name } +
            assetPacks.map { it.dir.name to it.name }

    private fun art(part: (PackParts) -> Boolean): PackArt? {
        val (key, art) = artSource ?: return null
        return if (part(parts(key))) art else null
    }

    /** A collection's hero picture, from smart-folder packs that have one. */
    fun collectionHero(kind: String): File? = art { it.heroes }?.collection(COLLECTION_IDS[kind] ?: kind, "hero")

    /** Names a pack might use for a system: its id, aliases, folders and art ids, and well-known synonyms. */
    fun systemIds(platformId: String): List<String> {
        val p = Libraries.byId(platformId)?.platform
        val own = buildList {
            // Android Games: Cocoon packs draw it as by_platform/androidgames, apart from Apps (android_app).
            if (platformId == io.github.zahakj.tandem.AndroidApps.PLATFORM_ID) { add("androidgames"); add("android_games") }
            add(platformId)
            if (p != null) {
                addAll(p.aliases); addAll(p.artIds); addAll(p.folders.filterNot { it.startsWith("/") }); add(p.name)
            }
        }
        return (own + own.flatMap { SYNONYMS[PackArt.norm(it)].orEmpty() }).distinct()
    }

    /**
     * Smart-folder art that isn't one system: `by_platform/android_app`, `emulators`, `steam`... [names] are the
     * pack folder names to try, in order; [kind] is "icon", "hero" or "logo".
     */
    fun folderArt(names: List<String>, kind: String): File? =
        if (names.isEmpty()) null else art { if (kind == "icon") it.icons else it.heroes }?.system(names, kind)

    /** The pack's square icon for a system, or null to draw Tandem's own. */
    fun systemIcon(platformId: String): File? = art { it.icons }?.system(systemIds(platformId), "icon")

    /** A 16:9 hero picture for a system. */
    fun systemHero(id: String): File? = art { it.heroes }?.system(systemIds(id), "hero")

    /** A wide logo for a system (shown over its hero). */
    fun systemLogo(id: String): File? = art { it.heroes }?.system(systemIds(id), "logo")

    /** An icon for a collection: "favorites", "most_played", "newly_added" or "recent" (Tandem's own ids work too). */
    fun collectionIcon(kind: String): File? = art { it.icons }?.collection(COLLECTION_IDS[kind] ?: kind)

    /** A frame drawn over a system's game covers, and the mask the cover is cut to. */
    fun coverFrame(platformId: String): Pair<File?, File>? {
        val a = art { it.frames } ?: return null
        val ids = systemIds(platformId)
        val overlay = a.overlay(ids, "overlay") ?: return null
        return a.overlay(ids, "mask") to overlay
    }

    const val ICONS_DRAWN = "none"

    private val COLLECTION_IDS = mapOf(
        "fav" to "favorites", "most" to "most_played", "added" to "newly_added", "continue" to "recent",
        "recently_played" to "recent", "recently_added" to "newly_added",
    )

    /** Names other frontends use for the same system. Keys are normalised. */
    private val SYNONYMS: Map<String, List<String>> = mapOf(
        "n3ds" to listOf("3ds", "nintendo3ds"), "nds" to listOf("ds", "nintendods"), "switch" to listOf("nsw", "ns"),
        "psx" to listOf("ps1", "playstation", "psone"), "ps2" to listOf("playstation2"), "psp" to listOf("playstationportable"),
        "psvita" to listOf("vita", "psv"), "genesis" to listOf("megadrive", "md"), "megadrive" to listOf("genesis"),
        "gc" to listOf("gamecube", "ngc"), "mastersystem" to listOf("sms"), "gamegear" to listOf("gg"),
        "sega32x" to listOf("32x"), "segacd" to listOf("megacd", "scd"), "dreamcast" to listOf("dc"),
        "pcengine" to listOf("tg16", "turbografx16", "pce"), "pcenginecd" to listOf("tgcd", "turbografxcd", "pcecd"),
        "windows" to listOf("pc", "steam", "win"), "android" to listOf("androidgames", "android_games"),
        "arcade" to listOf("mame", "fbneo", "fba"), "atarilynx" to listOf("lynx"), "atarijaguar" to listOf("jaguar"),
        "wonderswancolor" to listOf("wsc"), "wonderswan" to listOf("ws"), "ngpc" to listOf("neogeopocketcolor"),
        "ngp" to listOf("neogeopocket"), "virtualboy" to listOf("vb"), "gameandwatch" to listOf("gw", "gamewatch"),
        "n64" to listOf("nintendo64"), "snes" to listOf("sfc", "superfamicom", "supernintendo"),
        "nes" to listOf("famicom", "fc"), "fds" to listOf("famicomdisksystem"), "gba" to listOf("gameboyadvance"),
        "gbc" to listOf("gameboycolor"), "gb" to listOf("gameboy"), "wiiu" to listOf("wii_u"),
        "xbox360" to listOf("x360"), "zxspectrum" to listOf("zx", "spectrum"), "amstradcpc" to listOf("cpc"),
        "colecovision" to listOf("coleco"), "intellivision" to listOf("intv"), "scummvm" to listOf("scumm"),
        "dos" to listOf("msdos", "pc_dos", "dosbox"), "pico8" to listOf("pico-8"), "moonlight" to listOf("streaming"),
    )

    // ---- Reading packs ----

    private fun json(dir: File): JSONObject? = File(dir, "theme.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }

    private fun media(dir: File, name: String?): File? {
        if (name.isNullOrBlank() || name.contains("..")) return null
        fun ok(f: File) = f.isFile && f.extension.lowercase() in videoExtensions + imageExtensions
        // Cocoon packs name the file ("main.mp4") but keep it in a wallpapers/ folder next to theme.json.
        listOf(File(dir, name), File(dir, "wallpapers/$name"), File(dir, "wallpaper/$name")).firstOrNull(::ok)?.let { return it }
        val base = File(name).name
        return dir.walkTopDown().maxDepth(3).firstOrNull { it.name.equals(base, ignoreCase = true) && ok(it) }
    }

    private fun base(mode: String?): ThemeSpec = when (mode?.lowercase()) {
        "oled", "black", "amoled" -> BuiltInThemes.byIdOrDefault("oled")
        "light", "day" -> BuiltInThemes.byIdOrDefault("paper")
        else -> BuiltInThemes.PastelNight
    }

    private fun isCocoon(o: JSONObject) = o.has("color_scheme") || o.has("theme_mode") || o.has("wallpaper_main")

    private fun parse(dir: File, id: String, removable: Boolean): ThemePack? {
        val o = json(dir) ?: return null
        val cocoon = isCocoon(o)
        val spec = (if (cocoon) parseCocoon(dir, o) else parseTandem(dir, o)).copy(id = id)
        val (art, credits) = findArt(dir, o)
        val preview = listOf("preview.png", "preview.jpg", "preview.webp").map { File(dir, it) }.firstOrNull { it.isFile }
        return ThemePack(
            id = id, dir = dir, format = if (cocoon) PackFormat.Cocoon else PackFormat.Tandem,
            name = spec.name, author = o.optString("author").trim(),
            description = o.optString("description").trim(), version = o.optString("version").trim(),
            spec = spec.copy(hasArt = !art.isEmpty), art = art, preview = preview, credits = credits, removable = removable,
        )
    }

    /** Finds a pack's art: inside its own folder, and for Cocoon packs in the asset packs `asset_refs` names. */
    private fun findArt(dir: File, o: JSONObject): Pair<PackArt, List<String>> {
        val systems = mutableListOf<File>()
        val collections = mutableListOf<File>()
        val overlays = mutableListOf<File>()
        val credits = mutableListOf<String>()
        fun addSmartFolders(root: File) {
            val byPlatform = findDir(root, "by_platform", 3)
            if (byPlatform != null) { systems += byPlatform; collections += byPlatform.parentFile!! }
        }
        fun addOverlays(root: File) { overlays += findDir(root, "icon_overlays", 2) ?: root }

        // Tandem layout, and Cocoon asset folders copied into the pack by Import.
        File(dir, "systems").takeIf { it.isDirectory }?.let(systems::add)
        File(dir, "collections").takeIf { it.isDirectory }?.let(collections::add)
        File(dir, "overlays").takeIf { it.isDirectory }?.let(overlays::add)
        File(dir, "smart_folders").takeIf { it.isDirectory }?.let(::addSmartFolders)
        File(dir, "icon_overlays").takeIf { it.isDirectory }?.let(::addOverlays)

        // Cocoon: asset packs live in ROMs/assets/<category>/<folder>/ and are named by id in asset_refs.
        val refs = o.optJSONArray("asset_refs")
        if (refs != null) for (i in 0 until refs.length()) {
            val ref = refs.optJSONObject(i) ?: continue
            val category = ref.optString("category")
            val folder = findAssetPack(category, ref.optString("id"), ref.optString("name")) ?: continue
            when (category) {
                "smart_folders" -> addSmartFolders(folder)
                "icon_overlays" -> addOverlays(folder)
                else -> continue
            }
            val meta = assetMeta(folder)
            val name = meta?.optString("name")?.ifBlank { null } ?: ref.optString("name").ifBlank { folder.name }
            val author = meta?.optString("author")?.ifBlank { null }
            credits += if (author != null) "$name by $author" else name
        }
        return PackArt(systems.distinct(), collections.distinct(), overlays.distinct()) to credits
    }

    /** metadata.json of an asset pack, in its folder or one level down. */
    fun assetMeta(folder: File): JSONObject? {
        val f = File(folder, "metadata.json").takeIf { it.isFile }
            ?: folder.listFiles().orEmpty().filter { it.isDirectory }.map { File(it, "metadata.json") }.firstOrNull { it.isFile }
        return f?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
    }

    private fun findAssetPack(category: String, id: String, name: String): File? {
        if (category.isBlank() || category.contains('/') || category.contains("..")) return null
        val folders = File(cocoonAssetsDir, category).listFiles().orEmpty().filter { it.isDirectory }
        return folders.firstOrNull { assetMeta(it)?.optString("id")?.takeIf { s -> s.isNotBlank() } == id }
            ?: folders.firstOrNull { name.isNotBlank() && assetMeta(it)?.optString("name") == name }
            ?: folders.firstOrNull { it.name == id }
    }

    /** The first folder called [name] at most [depth] levels under [root] (including [root] itself). */
    fun findDir(root: File, name: String, depth: Int): File? {
        if (root.name.equals(name, ignoreCase = true) && root.isDirectory) return root
        if (depth <= 0) return null
        val kids = root.listFiles().orEmpty().filter { it.isDirectory }
        kids.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        return kids.asSequence().mapNotNull { findDir(it, name, depth - 1) }.firstOrNull()
    }

    /** Tandem's format. Every key is optional. */
    private fun parseTandem(dir: File, o: JSONObject): ThemeSpec {
        val b = o.optString("base").takeIf { it.isNotBlank() }?.let { id -> BuiltInThemes.list.firstOrNull { it.id == id } }
            ?: base(o.optString("mode"))
        val col = o.optJSONObject("colors") ?: JSONObject()
        fun color(key: String, fallback: Color) = col.optString(key).takeIf { it.isNotBlank() }?.let(::parseColor) ?: fallback
        val shape = o.optJSONObject("shape") ?: JSONObject()
        val wall = o.optJSONObject("wallpaper") ?: JSONObject()
        val accent = color("accent", b.accent)
        val dark = when (o.optString("mode").lowercase()) { "light" -> false; "dark", "oled" -> true; else -> b.dark }
        return b.copy(
            name = o.optString("name").ifBlank { dir.name },
            tagline = o.optString("description").ifBlank { "Theme pack" }.lineSequence().first().take(60),
            source = o.optString("author").ifBlank { "Theme pack" },
            baseId = b.id,
            dark = dark,
            background = color("background", b.background),
            backgroundDeep = color("background_deep", color("background", b.backgroundDeep)),
            card = color("card", b.card),
            cardHigh = color("card_high", b.cardHigh),
            text = color("text", b.text),
            muted = color("muted", b.muted),
            onAccent = color("on_accent", b.onAccent),
            mint = color("mint", b.mint),
            peach = color("peach", b.peach),
            lavender = color("lavender", b.lavender),
            sky = color("sky", b.sky),
            butter = color("butter", b.butter),
            accent = accent,
            glow = color("glow", accent),
            corner = shape.optDouble("corner", b.corner.toDouble()).toFloat().coerceIn(0f, 60f),
            focusScale = shape.optDouble("focus_scale", b.focusScale.toDouble()).toFloat().coerceIn(1f, 1.3f),
            highlight = HighlightStyle.of(shape.optString("highlight")) ?: b.highlight,
            highlightThickness = shape.optDouble("highlight_thickness", b.highlightThickness.toDouble()).toFloat().coerceIn(0f, 10f),
            material = SurfaceMaterial.of(o.optString("material")) ?: b.material,
            animation = AnimationStyle.of(o.optString("motion")) ?: b.animation,
            backdrop = BackdropStyle.of(o.optString("backdrop")) ?: b.backdrop,
            iconStyle = IconStyle.of(o.optString("icon_style")) ?: b.iconStyle,
            overlay = when (o.optString("overlay").lowercase()) {
                "scanlines" -> ScreenOverlay.Scanlines
                "dot_matrix", "dotmatrix" -> ScreenOverlay.DotMatrix
                "none" -> ScreenOverlay.None
                else -> b.overlay
            },
            wallpaperTop = media(dir, wall.optString("top")),
            wallpaperBottom = media(dir, wall.optString("bottom")),
        )
    }

    /**
     * The Cocoon format: `theme_mode`, a `color_scheme` of gradient pairs, a few `settings`, and
     * `wallpaper_main` (top screen) / `wallpaper_external` (bottom screen) files next to theme.json.
     */
    private fun parseCocoon(dir: File, o: JSONObject): ThemeSpec {
        val b = base(o.optString("theme_mode"))
        val cs = o.optJSONObject("color_scheme") ?: JSONObject()
        fun color(key: String): Color? = cs.optString(key).takeIf { it.isNotBlank() }?.let(::parseColor)
        val s = o.optJSONObject("settings") ?: JSONObject()
        val bg = color("background_gradient_start") ?: b.background
        val bgEnd = color("background_gradient_end") ?: bg
        val card = color("card_gradient_start") ?: color("tile_background") ?: b.card
        val text = color("text_primary") ?: b.text
        val accentStart = color("accent_gradient_start") ?: b.accent
        val accentEnd = color("accent_gradient_end") ?: b.sky
        // accent_glow often comes with a low alpha meant for a soft halo; use it opaque for our glow colour.
        val glow = color("accent_glow")?.copy(alpha = 1f) ?: accentStart
        val dark = (bg.red + bg.green + bg.blue) / 3f < 0.5f
        val roundness = s.optDouble("icon_roundness", Double.NaN)
        return b.copy(
            name = o.optString("name").ifBlank { dir.name },
            tagline = o.optString("description").ifBlank { "Cocoon theme" }.lineSequence().first().trim().take(60),
            source = o.optString("author").ifBlank { "Cocoon theme" },
            baseId = b.id,
            dark = dark,
            background = bg,
            backgroundDeep = bgEnd,
            card = card,
            // A raised card: a touch closer to the text colour than the card itself.
            cardHigh = lerp(card, text, 0.07f),
            text = text,
            muted = color("text_secondary") ?: b.muted,
            onAccent = if (dark) bg else Color.White,
            mint = color("success") ?: b.mint,
            butter = color("warning") ?: b.butter,
            lavender = accentStart,
            sky = accentEnd,
            accent = accentStart,
            glow = glow,
            // Cocoon's roundness is a share of the icon size; Tandem's cards are about 104 dp.
            corner = if (roundness.isNaN()) b.corner else (roundness * 104).toFloat().coerceIn(0f, 52f),
            focusScale = s.optDouble("hover_scale", b.focusScale.toDouble()).toFloat().coerceIn(1f, 1.3f),
            iconScale = s.optDouble("icon_scale", 1.0).toFloat().coerceIn(0.6f, 1.6f),
            heroLogoScale = s.optDouble("hero_logo_scale", Double.NaN).takeIf { !it.isNaN() }?.toFloat()?.coerceIn(0.2f, 1f),
            heroGradient = when (s.optString("hero_gradient_style").uppercase()) {
                "NONE" -> "none"; "STRONG", "DARK" -> "strong"; "VIGNETTE" -> "vignette"; "" -> null; else -> "fade"
            },
            backdrop = if (o.has("wallpaper_main") || guessWallpaper(dir, "main") != null) BackdropStyle.Plain else b.backdrop,
            wallpaperTop = media(dir, o.optString("wallpaper_main")) ?: guessWallpaper(dir, "main"),
            wallpaperBottom = media(dir, o.optString("wallpaper_external")) ?: guessWallpaper(dir, "external"),
        )
    }
}

fun BuiltInThemes.byIdOrDefault(id: String) = (renamed[id] ?: id).let { i -> list.firstOrNull { it.id == i } } ?: PastelNight

/** A Cocoon pack's wallpaper by its usual name ("main" top, "external" bottom) in the pack or its wallpapers/ folder. */
private fun guessWallpaper(dir: File, stem: String): File? =
    listOf(dir, File(dir, "wallpapers"), File(dir, "wallpaper")).asSequence()
        .flatMap { it.listFiles().orEmpty().asSequence() }
        .firstOrNull { it.isFile && it.nameWithoutExtension.equals(stem, true) && it.extension.lowercase() in ThemePacks.videoExtensions + ThemePacks.imageExtensions }
