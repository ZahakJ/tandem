package io.github.zahakj.tandem

import androidx.compose.ui.graphics.Color
import io.github.zahakj.tandem.data.GameMeta
import io.github.zahakj.tandem.data.HomeFolder
import io.github.zahakj.tandem.data.IconGridStore
import io.github.zahakj.tandem.data.SmartGame
import io.github.zahakj.tandem.data.SmartRule
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.styleOf
import io.github.zahakj.tandem.ui.Palette
import io.github.zahakj.tandem.ui.folderColor
import java.util.concurrent.ConcurrentHashMap

/** A list of games built from play data or metadata rather than from folders. */
@androidx.compose.runtime.Immutable
data class GameCollection(val id: String, val title: String, val games: List<Game>, val color: Color)

/** True once a game has any play history, from Tandem or imported from another frontend. */
val GameMeta.everPlayed: Boolean get() = playCount > 0 || playSeconds > 0 || lastPlayed > 0

object Collections {
    fun visible(games: List<Game>): List<Game> =
        if (Privacy.showHidden) games else games.filter { Privacy.canShow(it) }

    /**
     * Everything below is derived from the library, the details and the hidden-games switch, which change rarely
     * (a scan, a favourite, a finished play session). Each list is computed once per change and shared, so opening a
     * screen or a folder doesn't filter and sort the whole library again on the main thread. Reading them still
     * reads the Compose state they come from, so screens recompose when it changes. [warm] fills them off the main
     * thread.
     */
    private class Memo(val libs: Any?, val meta: Any?, val hidden: Any) {
        val all: List<Game> by lazy { visible(Libraries.games) }
        val continuePlaying by lazy { all.filter { it.meta.lastPlayed > 0 }.sortedByDescending { it.meta.lastPlayed }.take(15) }
        val favorites by lazy { byTitle(all.filter { it.meta.favorite }) }
        val mostPlayed by lazy { all.filter { it.meta.playSeconds > 0 }.sortedByDescending { it.meta.playSeconds } }
        val neverPlayed by lazy { byTitle(all.filter { !it.meta.everPlayed }) }
        val recentlyAdded by lazy { all.sortedByDescending { it.meta.firstSeen }.take(30) }
        val genres: Map<String, List<Game>> by lazy { computeGenres(all) }
        val everyOne: List<GameCollection> by lazy {
            listOf(
                GameCollection("fav", "Favorites", favorites, Palette.Peach),
                GameCollection("most", "Most played", mostPlayed, Palette.Butter),
                GameCollection("added", "Recently added", recentlyAdded, Palette.Sky),
                GameCollection("unplayed", "Never played", neverPlayed, Palette.Lavender),
            )
        }
        val home: List<GameCollection> by lazy { everyOne.filter { it.games.isNotEmpty() } }
        /** Every genre name a smart folder can pick, read as [computeGenres] reads them (single games too). */
        val genreNames: List<String> by lazy {
            val tidied = HashMap<String, String>()
            all.flatMapTo(sortedSetOf(String.CASE_INSENSITIVE_ORDER)) { g -> genresOf(g, tidied) }.toList()
        }

        /** Smart folders' games by folder id and rule: the folders' own, and the editor's previews. */
        private val smart = ConcurrentHashMap<Pair<String, SmartRule>, List<Game>>()

        fun smart(id: String, rule: SmartRule): List<Game> = smart[id to rule] ?: computeSmart(all, id, rule).also {
            // Previews add one entry per edit; never let them pile up.
            if (smart.size > 48) smart.clear()
            smart[id to rule] = it
        }
    }

    @Volatile private var memo: Memo? = null

    private fun memo(): Memo {
        val libs = Libraries.all
        val meta = Store.meta
        val hidden = Privacy.view
        val m = memo
        if (m != null && m.libs === libs && m.meta === meta && m.hidden == hidden) return m
        return Memo(libs, meta, hidden).also { memo = it }
    }

    /** Computes every list now (call it off the main thread after a scan or a change), so screens find them ready. */
    fun warm() {
        val m = memo()
        m.continuePlaying; m.home; m.genres
        for (f in IconGridStore.config.folders) f.rule?.let { m.smart(f.id, it) }
        m.genreNames
    }

    /** Sorts by title, lowercasing each title once instead of on every comparison. */
    private fun byTitle(games: List<Game>): List<Game> =
        games.map { it.title.lowercase() to it }.sortedBy { it.first }.map { it.second }

    /** Every game that may be shown (hidden ones only while they are revealed). Same as visible(Libraries.games). */
    fun visibleGames(): List<Game> = memo().all

    fun continuePlaying() = memo().continuePlaying
    fun favorites() = memo().favorites
    fun mostPlayed() = memo().mostPlayed
    fun neverPlayed() = memo().neverPlayed
    fun recentlyAdded() = memo().recentlyAdded

    /**
     * Genres across every system. A game's genres come from its metadata; games without any use the subfolder
     * they sit in, so folders like "RPG & Strategy" work as genres too.
     */
    fun genres(): Map<String, List<Game>> = memo().genres

    private fun computeGenres(all: List<Game>): Map<String, List<Game>> {
        val out = sortedMapOf<String, MutableList<Game>>(String.CASE_INSENSITIVE_ORDER)
        val tidied = HashMap<String, String>()
        for (g in all) {
            for (n in genresOf(g, tidied).distinct()) out.getOrPut(n) { mutableListOf() } += g
        }
        return out.filterValues { it.size >= 2 }.mapValues { (_, v) -> byTitle(v) }
    }

    private fun genresOf(g: Game, tidied: HashMap<String, String>): List<String> =
        g.meta.genres.map { tidied.getOrPut(it) { tidyGenre(it) } }.ifEmpty { listOfNotNull(g.group) }

    /** Random picks in smart folders change once per run of the app, not on every rescan. */
    private val sessionSeed = kotlin.random.Random.nextLong()

    private fun computeSmart(all: List<Game>, id: String, rule: SmartRule): List<Game> {
        val tidied = HashMap<String, String>()
        val players = HashMap<String, List<LaunchSpec>>()
        return rule.apply(all, sessionSeed xor id.hashCode().toLong()) { g ->
            val m = g.meta
            SmartGame(
                g.key, g.title, g.platform.id, genresOf(g, tidied), m.year, m.favorite, m.everPlayed,
                // Only looked up when the rule asks: it reads the installed emulators.
                if (rule.emulator != null) emulatorOf(g, players) else null,
                m.lastPlayed, m.playSeconds, m.firstSeen,
            )
        }
    }

    /** The package of the emulator [g] starts with, picked as [Platform.playerFor] does; null for Android games. */
    private fun emulatorOf(g: Game, players: HashMap<String, List<LaunchSpec>>): String? {
        if (g.platform.isApps) return null
        val all = players.getOrPut(g.platform.id) { g.platform.players }
        val ps = all.filter { it.accepts(g.file.name) }.ifEmpty { all }
        val p = ps.firstOrNull { it.name == g.meta.emulator } ?: ps.firstOrNull() ?: return null
        return p.packageName.takeIf { p.component != LaunchSpec.ANY_APP && it.isNotBlank() }
    }

    /** The games smart folder [f] holds now (hidden ones only while revealed); empty for a manual folder. */
    fun smart(f: HomeFolder): List<Game> = f.rule?.let { memo().smart(f.id, it) }.orEmpty()

    /** The games [rule] would give folder [id], worked out once per rule and library change. Call it off the main thread. */
    fun smart(id: String, rule: SmartRule): List<Game> = memo().smart(id, rule)

    /** Every genre name in the library, single games included. */
    fun genreNames(): List<String> = memo().genreNames

    private val Spaces = Regex("""\s+""")

    /** "  action  adventure" -> "Action adventure"; "RPG" stays "RPG". */
    fun tidyGenre(s: String) = s.trim().replace(Spaces, " ").replaceFirstChar { it.uppercase() }

    /** Every collection, empty or not. */
    private fun everyOne(): List<GameCollection> = memo().everyOne

    /** The collections shown on the home screen, in order. Empty ones are left out. */
    fun home(): List<GameCollection> = memo().home

    /** Collection ids for smart folders: "smart:<folder id>". */
    const val SMART = "smart:"

    /** A collection by id, even when it has become empty (so its screen can say so instead of going blank). */
    fun byId(id: String): GameCollection? = when {
        id.startsWith("genre:") -> id.removePrefix("genre:").let { g -> GameCollection(id, g, genres()[g].orEmpty(), Palette.forName(g)) }
        id == "continue" -> GameCollection(id, "Continue playing", continuePlaying(), Palette.Mint)
        id.startsWith(SMART) -> IconGridStore.folder(id.removePrefix(SMART))?.takeIf { Privacy.canShowHomeFolder(it) }?.let { f ->
            GameCollection(id, f.name, smart(f), IconGridStore.config.styleOf("folder:${f.id}").color?.let { Color(it) } ?: folderColor(f.color))
        }
        else -> everyOne().firstOrNull { it.id == id }
    }
}
