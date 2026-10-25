package io.github.zahakj.tandem.data

import io.github.zahakj.tandem.Privacy

import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Library
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** Game details from one source; anything may be missing. */
data class Details(
    val name: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val developer: String? = null,
    val publisher: String? = null,
    val year: Int? = null,
    val players: String? = null,
    val rating: Float? = null,
    val time: TimeToBeat? = null,
) {
    fun has(t: MetaType): Boolean = when (t) {
        MetaType.Name -> name != null
        MetaType.Description -> !description.isNullOrBlank()
        MetaType.Genres -> genres.isNotEmpty()
        MetaType.Credits -> developer != null || publisher != null
        MetaType.Year -> year != null
        MetaType.Players -> players != null
        MetaType.Rating -> rating != null
        MetaType.TimeToBeat -> time?.any == true
    }

    /** These details with [t] taken from [other]. */
    fun with(t: MetaType, other: Details): Details = when (t) {
        MetaType.Name -> copy(name = other.name)
        MetaType.Description -> copy(description = other.description)
        MetaType.Genres -> copy(genres = other.genres)
        MetaType.Credits -> copy(developer = other.developer, publisher = other.publisher)
        MetaType.Year -> copy(year = other.year)
        MetaType.Players -> copy(players = other.players)
        MetaType.Rating -> copy(rating = other.rating)
        MetaType.TimeToBeat -> copy(time = other.time)
    }
}

/** What one source found for one game: its details, and picture/clip addresses per kind (best first). */
class Hit(val details: Details, val media: suspend (MediaKind) -> List<String>)

fun SsGame.toDetails() = Details(name, description, genres, developer, publisher, year, players, rating)

fun LbGame.toDetails() = Details(name, overview, genres.take(3), developer, publisher, year, players, rating)

fun IgdbGame.toDetails() = Details(name, summary, genres, developer, publisher, year, players, rating)

/** LaunchBox picture addresses for a kind, best first. */
fun LbGame.urls(k: MediaKind, region: Region): List<String> = when (k) {
    MediaKind.Box -> LbIndex.pick(this, listOf(LbArt.BoxFront, LbArt.BoxRebuilt, LbArt.BoxFanart), region.code)
    MediaKind.Hero -> LbIndex.pick(this, listOf(LbArt.Background), region.code)
    MediaKind.Logo -> LbIndex.pick(this, listOf(LbArt.Logo), region.code)
    MediaKind.Title -> LbIndex.pick(this, listOf(LbArt.Title), region.code)
    MediaKind.Gameplay -> LbIndex.pick(this, listOf(LbArt.Gameplay), region.code)
    MediaKind.Video -> emptyList()
}.map { it.url } + if (k == MediaKind.Video) listOfNotNull(video?.replace("movie_max.", "movie480.")) else emptyList()

fun IgdbGame.urls(k: MediaKind): List<String> = when (k) {
    MediaKind.Box -> listOfNotNull(cover?.let { IgdbGame.image(it, "720p") })
    MediaKind.Hero -> artworks.take(3).map { IgdbGame.image(it, "1080p") }
    MediaKind.Gameplay -> screenshots.take(3).map { IgdbGame.image(it, "1080p") }
    else -> emptyList()
}

/**
 * The sources of one scrape run, each with its own pace, and switched off for the rest of the run when it says
 * so ([SourceDown]). [note] tells the user why.
 */
class RunSources(
    val order: List<ScrapeSource>,
    private val options: ScrapeOptions,
    private val libretro: Libretro,
    ss: ScreenScraper?,
    ssThreads: Int,
    private val launchBox: Map<String, LbGame>,
    igdb: Igdb?,
    sgdb: SteamGridDb?,
    hltb: Hltb?,
    private val note: (String) -> Unit,
) {
    @Volatile private var ss = ss
    @Volatile private var igdb = igdb
    @Volatile private var sgdb = sgdb
    @Volatile private var hltb = hltb
    private val ssGate = Semaphore(ssThreads.coerceAtLeast(1))
    private val igdbGate = Semaphore(2)
    private val sgdbGate = Semaphore(2)
    private val hltbGate = Semaphore(1)
    private val down = ConcurrentHashMap.newKeySet<ScrapeSource>()

    /** Sources to try for [kind] on [game]: SteamGridDB first for PC and Switch heroes and logos. */
    fun orderFor(kind: MediaKind, game: Game): List<ScrapeSource> {
        val list = order.filter { kind in it.kinds }
        if ((kind == MediaKind.Hero || kind == MediaKind.Logo) && ScrapeSource.SteamGridDb in list && ScrapeSystems.prefersSteamGridDb(game.systemIds)) {
            return listOf(ScrapeSource.SteamGridDb) + (list - ScrapeSource.SteamGridDb)
        }
        return list
    }

    private suspend fun <T> guarded(src: ScrapeSource, gate: Semaphore?, pause: Long, block: () -> T): T? {
        if (src in down) return null
        return try {
            if (gate == null) runInterruptible { block() } else gate.withPermit { runInterruptible { block() }.also { if (pause > 0) delay(pause) } }
        } catch (e: SourceDown) {
            if (down.add(src)) note(e.message ?: "${src.label} stopped answering")
            null
        } catch (e: Exception) {
            rethrowCancel(e)
            // One failed lookup isn't worth stopping for; the other sources still get a try.
            null
        }
    }

    /** Looks [game] up in [src]. Null when the source isn't there or doesn't know the game. */
    suspend fun lookup(src: ScrapeSource, game: Game): Hit? {
        if (Privacy.isHidden(game)) return null
        val region = options.region
        return when (src) {
            ScrapeSource.ScreenScraper -> {
                val s = ss ?: return null
                val info = guarded(src, ssGate, 300) {
                    // Stay polite: ScreenScraper is run by volunteers.
                    s.lookup(game, if (ScrapeSystems.screenScraper(game) != null) Hashes.of(game.file) else null, options.language, region)
                } ?: return null
                Hit(info.toDetails()) { k -> listOfNotNull(ScreenScraper.pick(info, k, region)?.url) }
            }
            ScrapeSource.LaunchBox -> launchBox[game.key]?.let { lb -> Hit(lb.toDetails()) { k -> lb.urls(k, region) } }
            ScrapeSource.Igdb -> {
                val i = igdb ?: return null
                val g = guarded(src, igdbGate, 260) { i.search(game.title, ScrapeSystems.igdb(game)) } ?: return null
                Hit(g.toDetails()) { k -> g.urls(k) }
            }
            ScrapeSource.SteamGridDb -> {
                val s = sgdb ?: return null
                val id = guarded(src, sgdbGate, 0) { s.find(game.title)?.first } ?: return null
                Hit(Details()) { k -> guarded(src, sgdbGate, 0) { s.candidates(id, k) }.orEmpty().map { it.url } }
            }
            ScrapeSource.Libretro -> Hit(Details()) { k ->
                if (!libretro.supports(k)) emptyList()
                else listOfNotNull(guarded(src, null, 0) { libretro.find(game, k, region) })
            }
            ScrapeSource.Hltb -> {
                val h = hltb ?: return null
                val g = guarded(src, hltbGate, 700) { h.search(game.title) } ?: return null
                Hit(Details(time = g.time)) { emptyList() }
            }
        }
    }

    companion object {
        /** LaunchBox queries for [games]: their titles, file titles and cleaned file names. */
        fun launchBoxQueries(games: List<Game>): List<LbQuery> = games.filter { !Privacy.isHidden(it) }.mapNotNull { g ->
            val platform = ScrapeSystems.launchBox(g) ?: return@mapNotNull null
            LbQuery(g.key, platform, listOf(g.title, g.fileTitle, Library.cleanTitle(g.stem)).distinct())
        }
    }
}
