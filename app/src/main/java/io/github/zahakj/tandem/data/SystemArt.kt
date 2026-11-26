package io.github.zahakj.tandem.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/*
 * Real system art: console photos, logos, fanart and the like, fetched from the web for the user's systems when they ask
 * (SystemArtFetch.kt) and chosen per system or for every system at once. This file is the plain part (no Android), so
 * SystemArtTest checks the platform mapping, the parsing of each source's answer and the order of precedence.
 *
 * Where it comes from:
 * - Wikimedia Commons, through Wikidata (no account): the console photo (Wikidata P18, mostly Evan-Amos's public-domain
 *   photos) and the logo (P154, SVGs Commons renders as see-through PNGs) of the system's Wikidata item
 *   (ScrapeSystems.wikidata).
 * - RetroArch's own system icons (no account, CC BY 4.0): the "systematic" XMB icon named after libretro's system name.
 * - ScreenScraper, when the user has set it up: the wheel, photo, illustration, controller, fanart, background and
 *   marquee its systemesListe gives each system.
 * - IGDB, when the user has set it up: the platform logo.
 *
 * Where it is kept: the pictures in ROMs/tandem/art/systems/<id>/<source>-<kind>.<ext> (listed in the downloaded-media
 * manifest, so Clear downloaded media removes them) with credits.json beside them; each system's choice per slot in its
 * tile's FolderStyle (webIcon, webHero, webLogo; icon-grid.json); the choice for every system in Look.systemArt. Neither
 * the pictures nor the choices ever travel in a shared look: they are third-party art and stay on the handheld.
 */

/** What a fetched picture shows. */
enum class WebKind(val id: String) {
    Photo("photo"), Logo("logo"), Icon("icon"), Illustration("illustration"), Controller("controller"),
    Fanart("fanart"), Background("background"), Banner("banner");

    companion object {
        fun of(id: String?): WebKind? = entries.firstOrNull { it.id == id }
    }
}

/** Where a picture came from; [label] is a name that stays as it is in every language. */
enum class WebSource(val id: String, val label: String) {
    ScreenScraper("ss", "ScreenScraper"), Wikimedia("wiki", "Wikimedia Commons"), Igdb("igdb", "IGDB"), RetroArch("ra", "RetroArch");

    companion object {
        fun of(id: String?): WebSource? = entries.firstOrNull { it.id == id }
    }
}

/** The three pictures a system wears. */
enum class ArtSlot { Icon, Hero, Logo }

/** One fetched picture of a system: kept as "<source>-<kind>.<ext>". */
data class WebOption(val source: WebSource, val kind: WebKind) {
    val id: String get() = "${source.id}-${kind.id}"

    companion object {
        fun parse(id: String?): WebOption? {
            val parts = id?.split('-', limit = 2)?.takeIf { it.size == 2 } ?: return null
            return WebOption(WebSource.of(parts[0]) ?: return null, WebKind.of(parts[1]) ?: return null)
        }

        /** The option a file in a system's folder holds ("ss-photo.jpg"), or null for anything else. */
        fun ofFile(name: String): WebOption? {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in EXTS || name.startsWith(".")) return null
            return parse(name.substringBeforeLast('.'))
        }

        val EXTS = setOf("png", "jpg", "webp")
    }
}

/** Who made a picture and under which licence, as far as the source says; shown beside it in the studio. */
@Serializable
data class WebCredit(val source: String, val licence: String? = null, val author: String? = null, val page: String? = null)

/** One medium ScreenScraper lists for a system. */
data class SsSystemMedia(val type: String, val region: String, val url: String)

/** A picture's address on Commons, with what the description page says about it. */
data class CommonsImage(val url: String, val licence: String?, val author: String?, val page: String?)

object SystemArt {
    const val PHOTOS = "photos"
    const val LOGOS = "logos"
    const val FANART = "fanart"

    /** The styles "Use real art for every system" offers, in order. */
    val styles: List<String> = listOf(PHOTOS, LOGOS, FANART)

    fun isStyle(id: String?): Boolean = id != null && id in styles

    /** Sources best first, when two give the same kind of picture. */
    private val sourceOrder = listOf(WebSource.ScreenScraper, WebSource.Wikimedia, WebSource.Igdb, WebSource.RetroArch)

    /**
     * The kinds of picture that make a good [slot], best first. A square icon takes what can be fitted on a colour
     * without cutting it (never a wide backdrop); a title takes only what floats over a hero (logos, icons).
     */
    fun kindsFor(slot: ArtSlot): List<WebKind> = when (slot) {
        ArtSlot.Icon -> listOf(WebKind.Photo, WebKind.Illustration, WebKind.Logo, WebKind.Icon, WebKind.Controller)
        ArtSlot.Hero -> listOf(WebKind.Fanart, WebKind.Background, WebKind.Photo, WebKind.Illustration, WebKind.Banner, WebKind.Controller, WebKind.Logo)
        ArtSlot.Logo -> listOf(WebKind.Logo, WebKind.Icon)
    }

    /** [options] that suit [slot], best first (by kind, then source). */
    fun forSlot(slot: ArtSlot, options: Collection<WebOption>): List<WebOption> {
        val kinds = kindsFor(slot)
        return options.filter { it.kind in kinds }.distinct()
            .sortedWith(compareBy({ kinds.indexOf(it.kind) }, { sourceOrder.indexOf(it.source) }))
    }

    /** What each style dresses every system's slots in, best first. */
    private fun styleKinds(style: String, slot: ArtSlot): List<WebKind> = when (style) {
        PHOTOS -> when (slot) {
            ArtSlot.Icon -> listOf(WebKind.Photo, WebKind.Illustration)
            ArtSlot.Hero -> listOf(WebKind.Photo, WebKind.Illustration, WebKind.Fanart, WebKind.Background)
            ArtSlot.Logo -> listOf(WebKind.Logo)
        }
        LOGOS -> when (slot) {
            ArtSlot.Icon -> listOf(WebKind.Logo, WebKind.Icon)
            ArtSlot.Hero -> listOf(WebKind.Fanart, WebKind.Background, WebKind.Photo)
            ArtSlot.Logo -> listOf(WebKind.Logo)
        }
        FANART -> when (slot) {
            ArtSlot.Icon -> listOf(WebKind.Photo, WebKind.Illustration, WebKind.Logo)
            ArtSlot.Hero -> listOf(WebKind.Fanart, WebKind.Background, WebKind.Illustration, WebKind.Photo)
            ArtSlot.Logo -> listOf(WebKind.Logo)
        }
        else -> emptyList()
    }

    /** The picture [style] gives a system's [slot] among those it has ([available]); null when it has none that fits. */
    fun styleChoice(style: String?, slot: ArtSlot, available: Collection<WebOption>): WebOption? {
        if (!isStyle(style)) return null
        for (k in styleKinds(style!!, slot)) {
            sourceOrder.firstNotNullOfOrNull { s -> available.firstOrNull { it.source == s && it.kind == k } }?.let { return it }
        }
        return null
    }

    /**
     * The web picture a system's [slot] shows, or null for none (its art set, the pack or the drawn art then). Strongest
     * first: the system's own pick ([mine]) when that picture is still there; nothing from the web when the system has
     * its own art set or keeps the pack's art ([perSystemSet]: a choice of its own beats a choice for all); else the
     * [global] style's pick. Uploads come before all of this (ArtSets.pick).
     */
    fun choose(slot: ArtSlot, mine: String?, perSystemSet: String?, global: String?, available: Collection<WebOption>): WebOption? {
        WebOption.parse(mine)?.takeIf { it in available && it.kind in kindsFor(slot) }?.let { return it }
        if (ArtSets.isChoice(perSystemSet)) return null
        return styleChoice(global, slot, available)
    }

    /** The system's own pick for [slot] in [style], when that slot follows its auto art (no upload, no game's art). */
    fun mine(style: FolderStyle?, slot: ArtSlot): String? = when (slot) {
        ArtSlot.Icon -> style?.webIcon?.takeIf { style.icon == FolderStyle.ICON_AUTO }
        ArtSlot.Hero -> style?.webHero?.takeIf { style.hero == FolderStyle.HERO_AUTO }
        ArtSlot.Logo -> style?.webLogo?.takeIf { style.logo == FolderStyle.LOGO_AUTO }
    }

    /** [style] showing web picture [option] in [slot] (null: none), that slot back on its auto art. */
    fun withChoice(style: FolderStyle, slot: ArtSlot, option: String?): FolderStyle = when (slot) {
        ArtSlot.Icon -> style.copy(icon = FolderStyle.ICON_AUTO, iconRef = null, iconFile = null, webIcon = option)
        ArtSlot.Hero -> style.copy(hero = FolderStyle.HERO_AUTO, heroRef = null, heroFile = null, webHero = option)
        ArtSlot.Logo -> style.copy(logo = FolderStyle.LOGO_AUTO, logoRef = null, logoFile = null, webLogo = option)
    }

    private val SYSTEM_ID = Regex("[a-z0-9_]{1,48}")

    /** A system's folder name under tandem/art/systems: its id when safe, else a safe stand-in that stays the same. */
    fun folderName(id: String): String {
        val low = id.lowercase()
        if (SYSTEM_ID.matches(low)) return low
        val safe = low.map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }.joinToString("").take(36)
        return safe + "_" + id.hashCode().toUInt().toString(36)
    }

    /** Where fetched system art lives, under ROMs: never written into a shared look (LookFile.localOnly). */
    const val ROOT = "tandem/art/systems"

    // ---- Wikimedia ------------------------------------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    /** Wikidata ids are "Q" and digits; nothing else is ever sent. */
    fun isWikidataId(id: String?): Boolean = id != null && Regex("Q[1-9][0-9]{0,10}").matches(id)

    /**
     * The one Wikidata query a fetch makes: each item's best-ranked image (P18, a photo of the console) and logo image
     * (P154). Only the item ids go in, and only well-formed ones.
     */
    fun wikidataQuery(ids: Collection<String>): String? {
        val ok = ids.filter(::isWikidataId).distinct().sorted()
        if (ok.isEmpty()) return null
        return "SELECT ?i ?p ?g WHERE { VALUES ?i { ${ok.joinToString(" ") { "wd:$it" }} } " +
            "OPTIONAL { ?i wdt:P18 ?p } OPTIONAL { ?i wdt:P154 ?g } }"
    }

    /** Each item's photo and logo file names from the query's answer (SPARQL JSON results); the first of several. */
    fun wikidataImages(text: String): Map<String, Pair<String?, String?>> {
        val rows = (parse(text)?.obj("results")?.get("bindings") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val out = LinkedHashMap<String, Pair<String?, String?>>()
        for (row in rows) {
            val q = row.obj("i")?.str("value")?.substringAfterLast('/')?.takeIf(::isWikidataId) ?: continue
            fun file(k: String) = row.obj(k)?.str("value")?.let(::commonsFile)
            val had = out[q]
            out[q] = (had?.first ?: file("p")) to (had?.second ?: file("g"))
        }
        return out.filterValues { it.first != null || it.second != null }
    }

    /** A Commons file name from Wikidata's address for it (".../Special:FilePath/Game-Boy-FL.jpg"). */
    private fun commonsFile(url: String): String? {
        val raw = url.substringAfter("Special:FilePath/", "").takeIf { it.isNotBlank() } ?: return null
        return runCatching { java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() && '/' !in it }
    }

    /** Commons's title for a file name: "File:" and the name with spaces, its first letter capital. */
    fun commonsTitle(file: String): String =
        "File:" + file.replace('_', ' ').trim().replaceFirstChar { it.uppercaseChar() }

    /** Each file's picture address (a PNG of an SVG, a scaled JPEG) and credit from an imageinfo answer, by title. */
    fun commonsImages(text: String): Map<String, CommonsImage> {
        val query = parse(text)?.obj("query") ?: return emptyMap()
        val renamed = (query["normalized"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .mapNotNull { n -> n.str("to")?.let { to -> n.str("from")?.let { it to to } } }
        val out = HashMap<String, CommonsImage>()
        val pages = when (val p = query["pages"]) {
            is JsonArray -> p.mapNotNull { it as? JsonObject }
            is JsonObject -> p.values.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
        for (page in pages) {
            val title = page.str("title") ?: continue
            val info = (page["imageinfo"] as? JsonArray)?.firstOrNull() as? JsonObject ?: continue
            val url = (info.str("thumburl") ?: info.str("url"))?.takeIf { it.startsWith("https://") } ?: continue
            val meta = info.obj("extmetadata")
            fun meta(k: String) = meta?.obj(k)?.str("value")?.let(::plain)?.takeIf { it.isNotBlank() }
            out[title] = CommonsImage(url, meta("LicenseShortName"), meta("Artist")?.take(80), info.str("descriptionurl"))
        }
        for ((from, to) in renamed) out[to]?.let { out[from] = it }
        return out
    }

    /** An HTML snippet (Commons's artist field) as plain words. */
    fun plain(html: String): String =
        html.replace(Regex("<[^>]*>"), " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
            .replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    // ---- RetroArch --------------------------------------------------------------------------------------------------

    /** RetroArch's coloured XMB icon for a libretro system name ("Nintendo - Game Boy"). */
    fun retroArchIcon(libretroName: String): String =
        "https://raw.githubusercontent.com/libretro/retroarch-assets/master/xmb/systematic/png/" +
            java.net.URLEncoder.encode("$libretroName.png", "UTF-8").replace("+", "%20")

    // ---- ScreenScraper ----------------------------------------------------------------------------------------------

    /** Every system's media from a systemesListe answer, by ScreenScraper system id. */
    fun ssSystems(text: String): Map<Int, List<SsSystemMedia>> {
        val response = parse(text)?.obj("response") ?: return emptyMap()
        val list = (response["systemes"] ?: response["systeme"]) as? JsonArray ?: return emptyMap()
        return list.mapNotNull { it as? JsonObject }.mapNotNull { s ->
            val id = s.str("id")?.toIntOrNull() ?: return@mapNotNull null
            val media = (s["medias"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { m ->
                val url = m.str("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return@mapNotNull null
                SsSystemMedia(m.str("type").orEmpty(), m.str("region").orEmpty(), url)
            }
            id to media
        }.toMap()
    }

    /** ScreenScraper's media types for each kind, best first. */
    fun ssTypes(kind: WebKind): List<String> = when (kind) {
        WebKind.Logo -> listOf("wheel-hd", "wheel")
        WebKind.Photo -> listOf("photo")
        WebKind.Illustration -> listOf("illustration")
        WebKind.Controller -> listOf("controller", "controleur")
        WebKind.Fanart -> listOf("fanart")
        WebKind.Background -> listOf("background", "wallpaper")
        WebKind.Banner -> listOf("screenmarquee")
        WebKind.Icon -> emptyList()
    }

    /** The address of the best medium of each kind, in the region asked for first ([region], then world, US, Europe, Japan). */
    fun ssPick(media: List<SsSystemMedia>, region: String): Map<WebKind, String> {
        val order = listOf(region, "wor", "us", "eu", "ss", "jp", "")
        val out = LinkedHashMap<WebKind, String>()
        for (k in WebKind.entries) for (t in ssTypes(k)) {
            val of = media.filter { it.type.equals(t, ignoreCase = true) }
            if (of.isEmpty()) continue
            out[k] = (order.firstNotNullOfOrNull { r -> of.firstOrNull { it.region == r } } ?: of.first()).url
            break
        }
        return out
    }

    // ---- IGDB -----------------------------------------------------------------------------------------------------

    /** Each platform's logo image id from an IGDB platforms answer. */
    fun igdbLogos(text: String): Map<Int, String> {
        val list = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonArray ?: return emptyMap()
        return list.mapNotNull { it as? JsonObject }.mapNotNull { p ->
            val id = (p["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val logo = p.obj("platform_logo")?.str("image_id")?.takeIf { Regex("[a-z0-9]{1,40}").matches(it) } ?: return@mapNotNull null
            id to logo
        }.toMap()
    }

    fun igdbLogoUrl(imageId: String) = "https://images.igdb.com/igdb/image/upload/t_720p/$imageId.png"

    // ---- Credits ----------------------------------------------------------------------------------------------------

    private val creditJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encodeCredits(credits: Map<String, WebCredit>): String =
        creditJson.encodeToString(kotlinx.serialization.builtins.MapSerializer(String.serializer(), WebCredit.serializer()), credits.toSortedMap())

    fun decodeCredits(text: String): Map<String, WebCredit> = runCatching {
        creditJson.decodeFromString(kotlinx.serialization.builtins.MapSerializer(String.serializer(), WebCredit.serializer()), text)
    }.getOrDefault(emptyMap())

    // ---- JSON helpers -----------------------------------------------------------------------------------------------

    private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.let { p ->
        if (p is kotlinx.serialization.json.JsonNull) null else p.content
    }
}
