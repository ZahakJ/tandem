package io.github.zahakj.tandem.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.Tx
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
enum class MediaKind(val field: String, val dir: String, @StringRes private val labelRes: Int, val maxPx: Int) {
    Box("box", "icon", R.string.scrape_kind_box, 640),
    Hero("hero", "hero", R.string.scrape_kind_hero, 1280),
    Logo("logo", "logo", R.string.scrape_kind_logo, 800),
    Title("titleScreen", "screenshot_title", R.string.scrape_kind_title, 960),
    Gameplay("screenshot", "screenshot_gameplay", R.string.scrape_kind_gameplay, 960),
    Video("video", "videos", R.string.scrape_kind_video, 0),

    /**
     * A scan of the real retail box's front, and of its spine, for the Shelf view only. Each has its own folder (the
     * names Skraper and Batocera use), so the box art the rest of Tandem shows (the "icon" folder) is never replaced.
     * Neither is kept in the game's details: the Shelf finds them in their folders.
     */
    Front("boxFront", "box2dfront", R.string.scrape_kind_front, 900),
    Spine("boxSpine", "box2dside", R.string.scrape_kind_spine, 900);

    val label: String get() = Tx.s(labelRes)

    /** True for the physical-box kinds, which only the Shelf view uses. */
    val packaging: Boolean get() = this == Front || this == Spine

    fun get(m: GameMeta): String? = when (this) {
        Box -> m.box; Hero -> m.hero; Logo -> m.logo; Title -> m.titleScreen; Gameplay -> m.screenshot; Video -> m.video
        Front, Spine -> null
    }

    fun set(m: GameMeta, path: String?): GameMeta = when (this) {
        Front, Spine -> m
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
enum class MetaType(@StringRes private val labelRes: Int, @StringRes private val detailRes: Int, val fields: List<String>) {
    Name(R.string.scrape_meta_name, R.string.scrape_meta_name_detail, listOf(MetaField.TITLE)),
    Description(R.string.scrape_meta_description, R.string.scrape_meta_description_detail, listOf(MetaField.DESCRIPTION)),
    Genres(R.string.scrape_meta_genres, R.string.scrape_meta_genres_detail, listOf(MetaField.GENRES)),
    Credits(R.string.scrape_meta_credits, R.string.scrape_meta_credits_detail, listOf(MetaField.DEVELOPER, MetaField.PUBLISHER)),
    Year(R.string.scrape_meta_year, R.string.scrape_meta_year_detail, listOf(MetaField.YEAR)),
    Players(R.string.scrape_meta_players, R.string.scrape_meta_players_detail, listOf(MetaField.PLAYERS)),
    Rating(R.string.scrape_meta_rating, R.string.scrape_meta_rating_detail, listOf(MetaField.RATING)),
    TimeToBeat(R.string.scrape_meta_time, R.string.scrape_meta_time_detail, listOf(MetaField.TIME));

    val label: String get() = Tx.s(labelRes)
    val detail: String get() = Tx.s(detailRes)

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

enum class ScrapeMode(@StringRes private val labelRes: Int, @StringRes private val detailRes: Int) {
    // "Missing" and "Refresh" are what older scraping.json files stored, so they keep their meaning.
    Basics(R.string.scrape_mode_basics, R.string.scrape_mode_basics_detail),
    Missing(R.string.scrape_mode_missing, R.string.scrape_mode_missing_detail),
    Refresh(R.string.scrape_mode_refresh, R.string.scrape_mode_refresh_detail);

    val label: String get() = Tx.s(labelRes)
    val detail: String get() = Tx.s(detailRes)
}

/**
 * Where scraping looks, in the order the user can change. Each source says what it can give, so a scrape asks the
 * next one only for what the ones before couldn't fill.
 */
enum class ScrapeSource(
    /** Its name, a brand that stays as it is in every language; null for libretro's, which has words in it. */
    private val brand: String?,
    @StringRes private val givesRes: Int,
    val kinds: Set<MediaKind>,
    val details: Set<MetaType>,
) {
    ScreenScraper(
        "ScreenScraper", R.string.scrape_source_ss_gives,
        MediaKind.entries.toSet(), MetaType.entries.toSet() - MetaType.TimeToBeat,
    ),
    LaunchBox(
        "LaunchBox", R.string.scrape_source_lb_gives,
        MediaKind.entries.toSet(), MetaType.entries.toSet() - MetaType.TimeToBeat,
    ),
    Igdb(
        "IGDB", R.string.scrape_source_igdb_gives,
        setOf(MediaKind.Box, MediaKind.Hero, MediaKind.Gameplay), MetaType.entries.toSet() - MetaType.TimeToBeat,
    ),
    SteamGridDb(
        "SteamGridDB", R.string.scrape_source_sgdb_gives,
        setOf(MediaKind.Box, MediaKind.Hero, MediaKind.Logo), emptySet(),
    ),
    Libretro(
        null, R.string.scrape_source_libretro_gives,
        setOf(MediaKind.Box, MediaKind.Front, MediaKind.Title, MediaKind.Gameplay), emptySet(),
    ),
    Hltb(
        "HowLongToBeat", R.string.scrape_source_hltb_gives,
        emptySet(), setOf(MetaType.TimeToBeat),
    );

    val label: String get() = brand ?: Tx.s(R.string.scrape_source_libretro)

    /** One line on what it gives. */
    val gives: String get() = Tx.s(givesRes)

    companion object {
        val defaultOrder = listOf(ScreenScraper, LaunchBox, Igdb, SteamGridDb, Libretro, Hltb)
    }
}

/** Region codes as ScreenScraper uses them, with the No-Intro tag libretro file names carry. */
enum class Region(val code: String, @StringRes private val labelRes: Int, val tag: String) {
    US("us", R.string.scrape_region_us, "USA"), EU("eu", R.string.scrape_region_eu, "Europe"),
    JP("jp", R.string.scrape_region_jp, "Japan"), World("wor", R.string.scrape_region_world, "World");

    val label: String get() = Tx.s(labelRes)

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

/** Languages for descriptions, each named in itself (never translated, so anyone finds their own). */
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
