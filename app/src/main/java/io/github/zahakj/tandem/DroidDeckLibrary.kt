package io.github.zahakj.tandem

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Everything Tandem knows about talking to DroidDeck from outside, in one place, so a change upstream only changes
 * this object: how to start a game (in DroidDeck since its PR #2872, see its docs/frontend-integration.md; the
 * player in platforms.json sends exactly this), and the read-only library provider proposed upstream (not in
 * DroidDeck 0.7.1). [DroidDeck] reads the provider whenever it resolves, and DroidDeck's files as root otherwise.
 */
object DroidDeckContract {
    const val PACKAGE = "com.droiddeck.launcher"

    /**
     * Starting a game: this activity (never .MainActivity, a launcher-icon alias DroidDeck turns off with its light
     * icon), [LAUNCH_ACTION], the DroidDeck id as the string extra [EXTRA_APP_ID] (ids are unsigned 64-bit), and
     * optionally [EXTRA_GAME_SOURCE]. DroidDeck rescans its library and starts the game if it finds it.
     */
    const val LAUNCH_ACTIVITY = "com.droiddeck.launcher.FrontEndActivity"
    const val LAUNCH_ACTION = "com.droiddeck.launcher.LAUNCH_GAME"
    const val EXTRA_APP_ID = "app_id"
    const val EXTRA_GAME_SOURCE = "game_source"

    const val AUTHORITY = "com.droiddeck.launcher.library"
    /** A normal permission DroidDeck declares; Tandem asks for it in its manifest. */
    const val PERMISSION = "com.droiddeck.launcher.permission.READ_LIBRARY"
    /** content://AUTHORITY/games: every installed game that isn't hidden, sorted by name. Notified on every change. */
    const val GAMES_PATH = "games"
    /** DroidDeck's id, a string: the same value the launch extra takes. */
    const val COL_APP_ID = "app_id"
    /** steam, added, epic, gog, amazon, xbox, ubisoft, ea, rockstar, battlenet, itch, gamejolt. */
    const val COL_SOURCE = "source"
    const val COL_NAME = "name"
    const val COL_STEAM_APP_ID = "steam_app_id"
    /** The store's own id of a store game (Epic's app name, GOG's product id...), or null. */
    const val COL_STORE_ID = "store_id"
    /** Milliseconds since 1970, or null. */
    const val COL_INSTALLED_AT = "installed_at"
    const val COL_LAST_PLAYED = "last_played"
    /** Content links to each picture ending in `?v=<mtime>` (so the link changes with the picture), or null. */
    const val COL_CAPSULE_URI = "capsule_uri"
    const val COL_HERO_URI = "hero_uri"
    const val COL_LOGO_URI = "logo_uri"
    const val COL_ICON_URI = "icon_uri"

    /** Picture kinds of content://AUTHORITY/art/<app_id>/<kind>, for a row without its *_uri. */
    const val ART_CAPSULE = "capsule"
    const val ART_HERO = "hero"
    const val ART_LOGO = "logo"
    const val ART_ICON = "icon"

    fun gamesUri(): String = "content://$AUTHORITY/$GAMES_PATH"
    fun artUri(appId: String, kind: String): String = "content://$AUTHORITY/art/$appId/$kind"
}

/**
 * Reading DroidDeck's library: its Steam games and its other stores' (Epic, GOG and the rest, and games added by
 * hand). No Android here.
 */
object DroidDeckLibrary {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The folder DroidDeck keeps everything in (`/data/data/...` and `/data/user/0/...` are the same place). */
    const val DATA = "/data/data/com.droiddeck.launcher"
    private const val USER_DATA = "/data/user/0/com.droiddeck.launcher"
    const val LIBRARY = "$DATA/files/library-cache.json"

    /** Entries of DroidDeck's list that aren't games it starts by id: its Epic launcher, Xbox cloud games, collections. */
    private val NOT_GAMES = setOf("epic-launcher", "battlenet-launcher", "xbox-cloud", "collection")

    /** Whether [s] is a DroidDeck id: digits only (up to an unsigned 64-bit number), not 0. */
    fun isId(s: String): Boolean = s.length in 1..20 && s.all { it in '0'..'9' } && s.any { it != '0' } &&
        (s.length < 20 || s.toULongOrNull() != null)

    /** An id written as a number or a string ("413150", 413150, 413150.0) as the string both sources share. */
    fun normalId(raw: String?): String? {
        val t = raw?.trim()?.removeSuffix(".0") ?: return null
        val s = t.trimStart('0').ifEmpty { return null }
        return s.takeIf(::isId)
    }

    /** A source as both readers write it: lowercase, "steam" when none is given. */
    fun normalSource(raw: String?): String = raw?.trim()?.lowercase()?.ifEmpty { null } ?: AppGame.STEAM

    /**
     * The games in DroidDeck's `files/library-cache.json`. A Steam game is known by its app id; any other (a store's, or
     * one added by hand) by its 64-bit game id printed unsigned, which is what DroidDeck's launch takes, with the
     * store's own id beside it. Entries without an id are skipped, and anything that isn't the file Tandem expects
     * gives an empty list, never an exception. Picture paths outside DroidDeck's folder and shared storage, or with
     * odd characters, are dropped: root copies them.
     */
    fun parse(text: String): List<AppGame> = parseOrNull(text).orEmpty()

    /** As [parse], but null when [text] isn't a library at all (a file caught half-written), so the last one stays. */
    fun parseOrNull(text: String): List<AppGame>? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val games = root["games"] as? JsonArray ?: return null
        return games.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val library = o["library"].str()?.trim()?.lowercase()
            if (library in NOT_GAMES) return@mapNotNull null
            val source = o["source"].str()?.trim()?.lowercase()?.ifEmpty { null }
                ?: if (library == "added") "added" else AppGame.STEAM
            val appId = if (source == AppGame.STEAM) {
                normalId(o["appId"].str()) ?: normalId(o["gameId"].str())
            } else {
                AppLibrary.unsignedId(o["gameId"].str())
            } ?: return@mapNotNull null
            AppGame(
                app = AppLibrary.DROIDDECK,
                source = source,
                appId = appId,
                name = o["name"].str()?.trim().orEmpty(),
                installedAt = millis(o["installedAt"]),
                lastPlayed = millis(o["lastPlayed"]),
                art = safePath(o["art"].str()),
                hero = safePath(o["hero"].str()),
                logo = safePath(o["logo"].str()),
                icon = safePath(o["icon"].str()),
                storeId = if (source == AppGame.STEAM) appId else o["storeId"].str()?.trim()?.ifEmpty { null },
            )
        }.distinctBy { it.id }
    }

    /**
     * [path] when it is a picture root may copy, in a quoted shell argument: in DroidDeck's own folder, or on shared
     * storage (where DroidDeck installs store games, and which Tandem may read anyway); else null.
     */
    fun safePath(path: String?): String? {
        val p = path?.trim() ?: return null
        if (SAFE_ROOTS.none { p.startsWith(it) }) return null
        if ("/../" in p || "/./" in p || p.endsWith("/..") || p.any { it == '\'' || it == '\n' || it == '\u0000' }) return null
        return p
    }

    private val SAFE_ROOTS = listOf("$DATA/", "$USER_DATA/", "/storage/", "/sdcard/", "/mnt/media_rw/")

    /** Where DroidDeck keeps SteamGridDB-style art for [appId]: grid-p (box), grid-hero, grid-logo, grid-header. */
    fun steamArt(appId: String, kind: String): String = "$DATA/files/steam-art/$appId/$kind"

    /**
     * Where to look for a picture of each kind as root, best first: for a Steam game DroidDeck's SteamGridDB art, then
     * Steam's own; for another its own picture (DroidDeck's art cache, the game's folder or the store's).
     */
    fun artCandidates(g: AppGame): Map<ArtKind, List<String>> {
        val steam = g.source == AppGame.STEAM
        return mapOf(
            ArtKind.Box to listOfNotNull(steamArt(g.appId, "grid-p").takeIf { steam }, g.art),
            ArtKind.Hero to listOfNotNull(steamArt(g.appId, "grid-hero").takeIf { steam }, g.hero),
            ArtKind.Logo to listOfNotNull(steamArt(g.appId, "grid-logo").takeIf { steam }, g.logo),
        )
    }

    /** The kinds of picture Tandem keeps for a game of an app, with DroidDeck's provider's name for each. */
    enum class ArtKind(val provider: String) {
        Box(DroidDeckContract.ART_CAPSULE), Hero(DroidDeckContract.ART_HERO), Logo(DroidDeckContract.ART_LOGO);

        /** [g]'s picture of this kind, as its record has it. */
        fun of(g: AppGame): String? = when (this) {
            Box -> g.art
            Hero -> g.hero
            Logo -> g.logo
        }

        /** Where the provider has this picture of [g]: its row's link, else the art path. */
        fun providerUri(g: AppGame): String = of(g)?.takeIf { it.startsWith("content://") } ?: DroidDeckContract.artUri(g.appId, provider)
    }

    /** "<size> <mtime> <path>" lines from the root script's stat, by path. */
    fun statLines(out: String): Map<String, String> = out.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(' ', limit = 3)
        if (parts.size == 3 && parts[0].all(Char::isDigit) && parts[1].all(Char::isDigit)) parts[2] to "${parts[0]}:${parts[1]}" else null
    }.toMap()

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

    /** A time in seconds or milliseconds (DroidDeck writes both) as milliseconds; 0 when there is none. */
    private fun millis(e: JsonElement?): Long = millis(e.str()?.trim()?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() } ?: 0)

    /** Seconds or milliseconds since 1970 as milliseconds (anything before 1973 in milliseconds is taken for seconds). */
    fun millis(t: Long): Long = when {
        t <= 0 -> 0
        t < 100_000_000_000L -> t * 1000
        else -> t
    }
}
