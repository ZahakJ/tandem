package io.github.zahakj.tandem

import kotlinx.serialization.Serializable

/**
 * One game installed in a PC game app Tandem reads the library of (DroidDeck or GameNative), the same whichever app
 * and whichever way it was read. [art] (box art), [hero], [logo] and [icon] say where its pictures are: paths in the
 * app's private folder (read as root), content links (DroidDeck's provider) or, for GameNative, the web addresses it
 * shows them from (Tandem looks for them in GameNative's own picture cache, never online).
 */
@Serializable
data class AppGame(
    /** [AppLibrary.DROIDDECK] or [AppLibrary.GAMENATIVE]. */
    val app: String = AppLibrary.DROIDDECK,
    /** The store, lowercase: steam, epic, gog, amazon, xbox, ubisoft, ea, rockstar, battlenet, itch, gamejolt, added. */
    val source: String,
    /**
     * What the app's launch takes: a Steam game's app id in either app; for DroidDeck's other games its 64-bit game
     * id printed unsigned; for GameNative's its own number for the game (Epic's and Amazon's are GameNative's row ids).
     */
    val appId: String,
    val name: String,
    /** Milliseconds since 1970; 0 when the app doesn't say. */
    val installedAt: Long = 0,
    val lastPlayed: Long = 0,
    val art: String? = null,
    val hero: String? = null,
    val logo: String? = null,
    val icon: String? = null,
    /**
     * The store's own id, the same in every app: Epic's app name, GOG's product id, Amazon's product id (a Steam game's
     * is its app id). Null when the app didn't say (DroidDeck's provider), and then the name is matched instead.
     */
    val storeId: String? = null,
) {
    val id: String get() = "$app:$source:$appId"

    /** The name of the small file standing for the game in Tandem (see [AppLibraries]): its stem names its art too. */
    val stem: String get() = "$app-$source-$appId"

    /** Whether Tandem can start it in its app: a store the app is asked to start by id, and an id. */
    val launchable: Boolean
        get() = DroidDeckLibrary.isId(appId) && when (app) {
            AppLibrary.DROIDDECK -> source in AppLibrary.DROIDDECK_SOURCES
            AppLibrary.GAMENATIVE -> source in AppLibrary.GAMENATIVE_SOURCES
            else -> false
        }

    companion object {
        const val STEAM = "steam"
    }
}

/**
 * The games of DroidDeck and GameNative as Tandem shows them: one tile per game whichever apps have it, with a small
 * file of Tandem's own that tells each app its own id. No Android here.
 */
object AppLibrary {
    const val DROIDDECK = "droiddeck"
    const val GAMENATIVE = "gamenative"

    /** Apps in the order that breaks ties: which one names a game both have, and starts it when neither was played. */
    val APPS = listOf(DROIDDECK, GAMENATIVE)

    fun packageOf(app: String): String = when (app) {
        DROIDDECK -> DroidDeckContract.PACKAGE
        GAMENATIVE -> GameNativeLibrary.PACKAGE
        else -> ""
    }

    /** DroidDeck 0.7.1's stores (its Store enum), its user-added games, and Steam. */
    val DROIDDECK_SOURCES = setOf(
        AppGame.STEAM, "epic", "gog", "amazon", "xbox", "ubisoft", "ea", "rockstar", "battlenet", "itch", "gamejolt", "added",
    )

    /** GameNative's GameSource values Tandem reads (its custom games aren't in its database). */
    val GAMENATIVE_SOURCES = setOf(AppGame.STEAM, "epic", "gog", "amazon")

    /**
     * The extension of a game's file: the store's, as DroidDeck and GameNative write theirs for frontends, so a
     * player's per-extension settings fit (GameNative's game_source). A game added to DroidDeck by hand is ".droiddeck",
     * so nothing takes its 64-bit id for a Steam app id.
     */
    fun extension(source: String): String = if (source == "added") "droiddeck" else source

    /** The store an extension stands for, for a shortcut file of the user's; null for one that isn't a store's. */
    fun sourceOfExtension(ext: String): String? = when (val e = ext.lowercase()) {
        "steam", "steamappid" -> AppGame.STEAM
        "epicgame" -> "epic"
        "droiddeck" -> "added"
        in DROIDDECK_SOURCES -> e
        else -> null
    }

    /** A 64-bit id as DroidDeck prints it ([java.lang.Long.toUnsignedString]): 0 to 18446744073709551615. */
    fun unsigned(id: Long): String = java.lang.Long.toUnsignedString(id)

    /** An id written as a signed or unsigned 64-bit number (or a string of one), printed unsigned; null if it isn't one. */
    fun unsignedId(raw: String?): String? {
        val t = raw?.trim()?.removeSuffix(".0")?.ifEmpty { null } ?: return null
        val v = t.toLongOrNull()?.let(::unsigned) ?: t.toULongOrNull()?.toString() ?: return null
        return v.takeIf(DroidDeckLibrary::isId)
    }

    /**
     * Whether DroidDeck is told the store as `game_source`: only after 0.7.1 (versionCode 19), whose launch turns down
     * any source but STEAM, and finds a store game by its id alone when none is given.
     */
    fun droidDeckTakesSource(versionCode: Long): Boolean = versionCode > 19

    /** The `game_source` DroidDeck is sent for [g] when [droidDeckTakesSource]; never for Steam (the player sends it). */
    fun droidDeckSource(g: AppGame): String? = g.source.takeIf { it != AppGame.STEAM && it != "added" }?.uppercase()

    /** A name made comparable: lowercase letters and digits only, marks like ™ and punctuation gone. */
    fun normalTitle(name: String): String = buildString {
        for (c in name.lowercase()) if (c.isLetterOrDigit()) append(c)
    }

    /**
     * The same game in two apps, or in one app read two ways: a Steam game by its app id, another store's by the
     * store's own id. Null for a game with nothing to tell it by but its name.
     */
    fun identity(g: AppGame): String? = when {
        g.source == AppGame.STEAM -> "steam:${g.appId}"
        !g.storeId.isNullOrBlank() -> "${g.source}:${g.storeId.trim().lowercase()}"
        else -> null
    }

    /** One game as Tandem shows it: the games of each app that has it (one per app, in [APPS] order). */
    data class Tile(
        /** Its file's name without the extension; stays the same while any app has the game (see [tiles]). */
        val stem: String,
        val key: String,
        val games: List<AppGame>,
    ) {
        val source: String get() = games.first().source
        val name: String get() = games.firstNotNullOfOrNull { it.name.ifBlank { null } } ?: games.first().appId
        val fileName: String get() = "$stem.${extension(source)}"
        val apps: List<String> get() = games.map { it.app }
        fun of(app: String): AppGame? = games.firstOrNull { it.app == app }
    }

    /**
     * The tiles for [games]. The same game in both apps is one tile: matched by [identity], and only when one of them
     * has no store id, by name within the same store. Each tile keeps the stem it had before ([stems]: tile key to
     * stem), so its file's name, and Tandem's records hung on it, survive the other app getting or losing the game.
     * A new tile is named after its first app's game ("droiddeck-steam-413150", "gamenative-epic-202"). Returns the
     * tiles and the stems to keep (old ones stay, so a game that comes back gets its name back).
     */
    fun tiles(games: List<AppGame>, stems: Map<String, String> = emptyMap()): Pair<List<Tile>, Map<String, String>> {
        val ordered = games.filter { it.launchable }.distinctBy { it.id }.sortedBy { APPS.indexOf(it.app) }
        // Groups by key: the identity when there is one, else the app's own id.
        val groups = LinkedHashMap<String, MutableList<AppGame>>()
        val pending = ArrayList<AppGame>()
        for (g in ordered) {
            val idt = identity(g)
            if (idt == null) {
                pending += g
                continue
            }
            val list = groups.getOrPut(idt) { ArrayList() }
            if (list.none { it.app == g.app }) list += g
        }
        // Games with nothing but a name: matched with another app's game of the same store and name, else alone.
        for (g in pending) {
            val title = normalTitle(g.name)
            val match = if (title.isEmpty()) null else groups.entries.firstOrNull { (_, list) ->
                list.first().source == g.source && list.none { it.app == g.app } && list.any { normalTitle(it.name) == title }
            }
            if (match != null) {
                match.value += g
                match.value.sortBy { APPS.indexOf(it.app) }
            } else {
                groups["${g.app}:${g.source}:${g.appId}"] = arrayListOf(g)
            }
        }
        val kept = stems.toMutableMap()
        val used = HashSet<String>()
        val tiles = groups.map { (key, list) ->
            // A key the provider gave (no store id) and the one root gives (with it) are the same tile: any member's
            // own key that has a stem counts.
            val known = (listOf(key) + list.map { it.id }).firstNotNullOfOrNull { kept[it] }
            val stem = known?.takeIf { it !in used } ?: list.first().stem
            used += stem
            kept[key] = stem
            for (g in list) kept[g.id] = stem
            Tile(stem, key, list)
        }
        return tiles to kept
    }

    /**
     * The text of a tile's file. A Steam game's is its app id alone, which both apps take, and what Steam art and
     * achievements read. Another store's has a `[droiddeck] <id>` line and/or a `[gamenative] <id>` line, which the
     * players in platforms.json read (`{tags.droiddeck}`, `{tags.gamenative}`), and `[droiddeck_source] EPIC` when
     * DroidDeck [takesSource]. GameNative's line comes first, so a player that still reads the first line's id
     * (`{file.id}`, an older copy of platforms.json) gives GameNative its own.
     */
    fun stubText(tile: Tile, takesSource: Boolean): String {
        if (tile.source == AppGame.STEAM) return "${tile.games.first().appId}\n"
        return buildString {
            tile.of(GAMENATIVE)?.let { g -> append("[gamenative] ").append(g.appId).append('\n') }
            tile.of(DROIDDECK)?.let { g ->
                append("[droiddeck] ").append(g.appId).append('\n')
                if (takesSource) droidDeckSource(g)?.let { append("[droiddeck_source] ").append(it).append('\n') }
            }
        }
    }

    /**
     * The app a tile starts in when the user hasn't picked one (Y › More › Emulator): the one it was last played in
     * (each app's own record, so a game played from inside the app counts too), else the first in [APPS].
     */
    fun defaultApp(tile: Tile): String =
        tile.games.filter { it.lastPlayed > 0 }.maxByOrNull { it.lastPlayed }?.app ?: tile.games.first().app

    /** What a shortcut file of the user's stands for: a store, and the id inside it (any app's). */
    data class Ref(val source: String, val id: String)

    /** The [Ref] of a file with extension [ext] whose first line is [firstLine]; null for files no app's game can be. */
    fun refOf(ext: String, firstLine: String?): Ref? {
        val source = sourceOfExtension(ext) ?: return null
        val raw = GameFiles.idIn(firstLine) ?: return null
        val id = if (source == AppGame.STEAM) DroidDeckLibrary.normalId(raw) else raw.trim().takeIf(DroidDeckLibrary::isId)
        return id?.let { Ref(source, it) }
    }

    /** The tile a shortcut of the user's stands for: one with a game of the same store and the same id in any app. */
    fun tileFor(ref: Ref, tiles: List<Tile>): Tile? =
        tiles.firstOrNull { t -> t.games.any { it.source == ref.source && it.appId == ref.id } }

    /**
     * The games of the Windows system: [scanned] (found in the game folders) and the [tiles] Tandem can start, as [make]
     * turns them into games. A tile the folders already have a shortcut for ([refOf] its file, as [refOf] reads it) is
     * left out: the user's own tile stays, with its name, art and records.
     */
    fun <G> merge(scanned: List<G>, tiles: List<Tile>, refOf: (G) -> Ref?, make: (Tile) -> G): List<G> {
        if (tiles.isEmpty()) return scanned
        val taken = scanned.mapNotNull { g -> refOf(g)?.let { tileFor(it, tiles) }?.key }.toSet()
        val extra = tiles.filter { it.key !in taken }
        if (extra.isEmpty()) return scanned
        return scanned + extra.map(make)
    }
}
