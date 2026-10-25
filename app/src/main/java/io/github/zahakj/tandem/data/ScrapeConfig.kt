package io.github.zahakj.tandem.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * A kind of downloaded picture or clip. [dir] is the folder under ROMs/downloaded_media/<system>/ (the layout
 * [io.github.zahakj.tandem.ArtIndex] reads), [maxPx] the longest side it is scaled down to before saving.
 */
enum class MediaKind(val field: String, val dir: String, val label: String, val maxPx: Int) {
    Box("box", "icon", "Box art", 640),
    Hero("hero", "hero", "Hero / fanart", 1280),
    Logo("logo", "logo", "Logo", 800),
    Title("titleScreen", "screenshot_title", "Title screen", 960),
    Gameplay("screenshot", "screenshot_gameplay", "Gameplay screenshot", 960),
    Video("video", "videos", "Video snap", 0);

    fun get(m: GameMeta): String? = when (this) {
        Box -> m.box; Hero -> m.hero; Logo -> m.logo; Title -> m.titleScreen; Gameplay -> m.screenshot; Video -> m.video
    }

    fun set(m: GameMeta, path: String?): GameMeta = when (this) {
        Box -> m.copy(box = path); Hero -> m.copy(hero = path); Logo -> m.copy(logo = path)
        Title -> m.copy(titleScreen = path); Gameplay -> m.copy(screenshot = path); Video -> m.copy(video = path)
    }
}

/** Text fields of [GameMeta] that scraping fills and the user can edit and lock. */
object MetaField {
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val GENRES = "genres"
    const val DEVELOPER = "developer"
    const val PUBLISHER = "publisher"
    const val YEAR = "year"
    const val PLAYERS = "players"
    const val RATING = "rating"
    const val TIME = "timeToBeat"
}

/** The kinds of game details a scrape can fill, each made of one or more [MetaField]s. */
enum class MetaType(val label: String, val detail: String, val fields: List<String>) {
    Name("Name", "The game's proper name instead of the file name", listOf(MetaField.TITLE)),
    Description("Description", "A few paragraphs about the game", listOf(MetaField.DESCRIPTION)),
    Genres("Genres", "Sorts games into the Genres lists", listOf(MetaField.GENRES)),
    Credits("Developer & publisher", "Who made it and who released it", listOf(MetaField.DEVELOPER, MetaField.PUBLISHER)),
    Year("Release year", "For sorting by year", listOf(MetaField.YEAR)),
    Players("Players", "How many can play at once", listOf(MetaField.PLAYERS)),
    Rating("Rating", "Community rating, as stars", listOf(MetaField.RATING)),
    TimeToBeat("Time to beat", "Main story, extras and 100% (HowLongToBeat)", listOf(MetaField.TIME));

    /** True when the game has nothing for this type yet. */
    fun missing(m: GameMeta): Boolean = when (this) {
        Name -> m.title == null
        Description -> m.description.isNullOrBlank()
        Genres -> m.genres.isEmpty()
        Credits -> m.developer == null && m.publisher == null
        Year -> m.year == null
        Players -> m.players == null
        Rating -> m.rating == null
        TimeToBeat -> m.timeToBeat == null
    }
}

enum class ScrapeMode(val label: String, val detail: String) {
    // "Missing" and "Refresh" are what older scraping.json files stored, so they keep their meaning.
    Basics("Missing basics", "Only games without box art or a description, and only those two"),
    Missing("Missing selected", "Fill in what you picked that a game doesn't have yet"),
    Refresh("Force all", "Fetch everything you picked again and replace it"),
}

/**
 * Where scraping looks, in the order the user can change. Each source says what it can give, so a scrape asks the
 * next one only for what the ones before couldn't fill.
 */
enum class ScrapeSource(
    val label: String,
    /** One line on what it gives. */
    val gives: String,
    val kinds: Set<MediaKind>,
    val details: Set<MetaType>,
) {
    ScreenScraper(
        "ScreenScraper", "Matches the file itself: box art, logos, fanart, screenshots, videos and details",
        MediaKind.entries.toSet(), MetaType.entries.toSet() - MetaType.TimeToBeat,
    ),
    LaunchBox(
        "LaunchBox", "A big free games database, kept on this device: box art, logos, fanart, screenshots and details",
        MediaKind.entries.toSet(), MetaType.entries.toSet() - MetaType.TimeToBeat,
    ),
    Igdb(
        "IGDB", "Twitch's games database: covers, artwork, screenshots and details, great for newer games",
        setOf(MediaKind.Box, MediaKind.Hero, MediaKind.Gameplay), MetaType.entries.toSet() - MetaType.TimeToBeat,
    ),
    SteamGridDb(
        "SteamGridDB", "Community-made covers, heroes and logos, the best for PC and Switch games",
        setOf(MediaKind.Box, MediaKind.Hero, MediaKind.Logo), emptySet(),
    ),
    Libretro(
        "libretro thumbnails", "Box art, title screens and screenshots for classic systems",
        setOf(MediaKind.Box, MediaKind.Title, MediaKind.Gameplay), emptySet(),
    ),
    Hltb(
        "HowLongToBeat", "How long a game takes: main story, main + extras and 100%",
        emptySet(), setOf(MetaType.TimeToBeat),
    );

    companion object {
        val defaultOrder = listOf(ScreenScraper, LaunchBox, Igdb, SteamGridDb, Libretro, Hltb)
    }
}

/** Region codes as ScreenScraper uses them, with the No-Intro tag libretro file names carry. */
enum class Region(val code: String, val label: String, val tag: String) {
    US("us", "USA", "USA"), EU("eu", "Europe", "Europe"), JP("jp", "Japan", "Japan"), World("wor", "World", "World");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: US
        fun fromLocale(): Region = when (Locale.getDefault().country.uppercase()) {
            "US", "CA", "MX", "BR" -> US
            "JP" -> JP
            "" -> US
            else -> EU
        }
    }
}

val LANGUAGES = linkedMapOf(
    "en" to "English", "fr" to "Français", "de" to "Deutsch", "es" to "Español", "it" to "Italiano",
    "pt" to "Português", "nl" to "Nederlands", "ja" to "日本語",
)

@Serializable
data class ScrapeSettings(
    val ssUser: String = "",
    val ssPassword: String = "",
    /** ScreenScraper developer credentials. The user registers their own; see docs/SCRAPING.md. */
    val ssDevId: String = "",
    val ssDevPassword: String = "",
    val sgdbKey: String = "",
    /** IGDB goes through Twitch: the user's own application's client id and secret (dev.twitch.tv). */
    val igdbClientId: String = "",
    val igdbSecret: String = "",
    val region: String = "",
    val language: String = "",
    val kinds: Set<MediaKind> = setOf(MediaKind.Box, MediaKind.Logo, MediaKind.Hero, MediaKind.Gameplay),
    val metadata: Boolean = true,
    val details: Set<MetaType> = MetaType.entries.toSet(),
    val mode: ScrapeMode = ScrapeMode.Missing,
    /** Lets a scrape replace fields the user edited or chose by hand. Off unless the user turns it on. */
    val replaceLocked: Boolean = false,
    /** Sources in the order they are asked. Sources missing here (added in a newer Tandem) go at the end. */
    val order: List<ScrapeSource> = ScrapeSource.defaultOrder,
    /** Sources the user turned off. HowLongToBeat starts off: it is an unofficial lookup of a website. */
    val off: Set<ScrapeSource> = setOf(ScrapeSource.Hltb),
    /** Filled after a successful ScreenScraper sign-in, to show in settings. */
    val ssVerifiedUser: String = "",
    val ssThreads: Int = 0,
) {
    val hasScreenScraper get() = ssDevId.isNotBlank() && ssDevPassword.isNotBlank()
    val hasSteamGridDb get() = sgdbKey.isNotBlank()
    val hasIgdb get() = igdbClientId.isNotBlank() && igdbSecret.isNotBlank()
    val regionPref: Region get() = if (region.isBlank()) Region.fromLocale() else Region.of(region)
    val languagePref: String get() = language.ifBlank { Locale.getDefault().language.takeIf { it in LANGUAGES } ?: "en" }

    val sourceOrder: List<ScrapeSource> get() = (order + ScrapeSource.defaultOrder).distinct()

    fun isOn(s: ScrapeSource) = s !in off

    /** True when [s] has what it needs to be asked (keys entered). LaunchBox also needs its download. */
    fun configured(s: ScrapeSource): Boolean = when (s) {
        ScrapeSource.ScreenScraper -> hasScreenScraper
        ScrapeSource.Igdb -> hasIgdb
        ScrapeSource.SteamGridDb -> hasSteamGridDb
        ScrapeSource.LaunchBox, ScrapeSource.Libretro, ScrapeSource.Hltb -> true
    }

    /** Sources a scrape will ask, in order. */
    val active: List<ScrapeSource> get() = sourceOrder.filter { isOn(it) && configured(it) }

    /** Moves [s] one place earlier (-1) or later (+1) in the order. */
    fun moved(s: ScrapeSource, by: Int): ScrapeSettings {
        val list = sourceOrder.toMutableList()
        val i = list.indexOf(s)
        val j = (i + by).coerceIn(0, list.size - 1)
        if (i < 0 || i == j) return this
        list.removeAt(i)
        list.add(j, s)
        return copy(order = list)
    }
}

/**
 * Scraping settings, including account details, kept in their own file inside the app's private storage.
 * Backups in ROMs/tandem (shared storage) leave the account details out unless the user asks for them (Backup.kt).
 */
object ScrapeConfig {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; coerceInputValues = true }
    private val writer = Executors.newSingleThreadExecutor()
    private var file: File? = null

    var settings by mutableStateOf(ScrapeSettings())
        private set

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "scraping.json")
        file = f
        settings = runCatching { json.decodeFromString<ScrapeSettings>(f.readText()) }.getOrDefault(ScrapeSettings())
        Media.init(ctx)
        LaunchBoxDb.init(ctx)
    }

    /** Reads scraping.json again (after a restore). Blocking. */
    fun reload() {
        val f = file ?: return
        val s = runCatching { json.decodeFromString<ScrapeSettings>(f.readText()) }.getOrDefault(ScrapeSettings())
        synchronized(this) { settings = s }
    }

    fun update(change: (ScrapeSettings) -> ScrapeSettings) {
        synchronized(this) { settings = change(settings) }
        val snapshot = settings
        val f = file ?: return
        writer.execute {
            val tmp = File(f.path + ".tmp")
            tmp.writeText(json.encodeToString(snapshot))
            tmp.renameTo(f)
        }
    }
}
