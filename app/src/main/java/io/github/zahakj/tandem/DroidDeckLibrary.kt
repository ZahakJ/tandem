package io.github.zahakj.tandem

import kotlinx.serialization.Serializable
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
 * One game installed in DroidDeck, the same whichever way it was read. [art] (box art), [hero], [logo] and [icon]
 * are where its pictures are: paths in DroidDeck's private folder (read as root) or content links (the provider).
 */
@Serializable
data class DroidDeckGame(
    val source: String,
    /** DroidDeck's id, as a string (an unsigned 64-bit number); for a Steam game its Steam app id. */
    val appId: String,
    val name: String,
    /** Milliseconds since 1970; 0 when DroidDeck doesn't know. */
    val installedAt: Long = 0,
    val lastPlayed: Long = 0,
    val art: String? = null,
    val hero: String? = null,
    val logo: String? = null,
    val icon: String? = null,
) {
    /** Stable for as long as the game exists anywhere, whichever way it was read: Tandem's records hang off it. */
    val id: String get() = "droiddeck:$source:$appId"

    /** The name of the small file standing for the game in Tandem (see [DroidDeck]): its stem names its art too. */
    val stem: String get() = "droiddeck-$source-$appId"

    /**
     * Whether Tandem starts it: Steam games, which DroidDeck's player in platforms.json starts by app id (and Steam
     * art and achievements know). Games from DroidDeck's other stores are left out for now.
     */
    val launchable: Boolean get() = source == STEAM && DroidDeckLibrary.isId(appId)

    companion object {
        const val STEAM = "steam"
    }
}

/** Reading DroidDeck's library, and putting its games among the ones found in the game folders. No Android here. */
object DroidDeckLibrary {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The folder DroidDeck keeps everything in (`/data/data/...` and `/data/user/0/...` are the same place). */
    const val DATA = "/data/data/com.droiddeck.launcher"
    private const val USER_DATA = "/data/user/0/com.droiddeck.launcher"
    const val LIBRARY = "$DATA/files/library-cache.json"

    /** Whether [s] is a DroidDeck id: digits only (up to an unsigned 64-bit number), not 0. */
    fun isId(s: String): Boolean = s.length in 1..20 && s.all { it in '0'..'9' } && s.any { it != '0' }

    /** An id written as a number or a string ("413150", 413150, 413150.0) as the string both sources share. */
    fun normalId(raw: String?): String? {
        val t = raw?.trim()?.removeSuffix(".0") ?: return null
        val s = t.trimStart('0').ifEmpty { return null }
        return s.takeIf(::isId)
    }

    /** A source as both readers write it: lowercase, "steam" when none is given. */
    fun normalSource(raw: String?): String = raw?.trim()?.lowercase()?.ifEmpty { null } ?: DroidDeckGame.STEAM

    /**
     * The games in DroidDeck's `files/library-cache.json` (times in seconds there, made milliseconds). Entries without
     * an id are skipped, and anything that isn't the file Tandem expects gives an empty list, never an exception.
     * Picture paths outside DroidDeck's own folder, or with odd characters, are dropped: root copies them, so only
     * DroidDeck's own pictures may be named.
     */
    fun parse(text: String): List<DroidDeckGame> = parseOrNull(text).orEmpty()

    /** As [parse], but null when [text] isn't a library at all (a file caught half-written), so the last one stays. */
    fun parseOrNull(text: String): List<DroidDeckGame>? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val games = root["games"] as? JsonArray ?: return null
        return games.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val appId = normalId(o["appId"].str()) ?: normalId(o["gameId"].str()) ?: return@mapNotNull null
            DroidDeckGame(
                source = normalSource(o["source"].str()),
                appId = appId,
                name = o["name"].str()?.trim().orEmpty(),
                installedAt = seconds(o["installedAt"]),
                lastPlayed = seconds(o["lastPlayed"]),
                art = safePath(o["art"].str()),
                hero = safePath(o["hero"].str()),
                logo = safePath(o["logo"].str()),
                icon = safePath(o["icon"].str()),
            )
        }.distinctBy { it.id }
    }

    /** [path] when it is a picture in DroidDeck's own folder that a quoted shell argument can carry; else null. */
    fun safePath(path: String?): String? {
        val p = path?.trim() ?: return null
        if (!(p.startsWith("$DATA/") || p.startsWith("$USER_DATA/"))) return null
        if ("/../" in p || p.endsWith("/..") || p.any { it == '\'' || it == '\n' || it == '\u0000' }) return null
        return p
    }

    /** Where DroidDeck keeps SteamGridDB-style art for [appId]: grid-p (box), grid-hero, grid-logo, grid-header. */
    fun steamArt(appId: String, kind: String): String = "$DATA/files/steam-art/$appId/$kind"

    /** Where to look for a picture of each kind as root, best first: DroidDeck's SteamGridDB art, then Steam's own. */
    fun artCandidates(g: DroidDeckGame): Map<ArtKind, List<String>> = mapOf(
        ArtKind.Box to listOfNotNull(steamArt(g.appId, "grid-p"), g.art),
        ArtKind.Hero to listOfNotNull(steamArt(g.appId, "grid-hero"), g.hero),
        ArtKind.Logo to listOfNotNull(steamArt(g.appId, "grid-logo"), g.logo),
    )

    /** The kinds of picture Tandem keeps for a DroidDeck game, with the provider's name for each. */
    enum class ArtKind(val provider: String) {
        Box(DroidDeckContract.ART_CAPSULE), Hero(DroidDeckContract.ART_HERO), Logo(DroidDeckContract.ART_LOGO);

        /** Where the provider has this picture of [g]: its row's link, else the art path. */
        fun providerUri(g: DroidDeckGame): String = when (this) {
            Box -> g.art
            Hero -> g.hero
            Logo -> g.logo
        }?.takeIf { it.startsWith("content://") } ?: DroidDeckContract.artUri(g.appId, provider)
    }

    /**
     * The games of the Windows system: [scanned] (found in the game folders) and DroidDeck's [installed] games Tandem
     * can start, as [make] turns them into games. A DroidDeck game the folders already have a shortcut for (its
     * Steam app id, as [steamIdOf] reads it from a .steam or .steamappid file) is left out: the user's own tile
     * stays, with its name, art and records.
     */
    fun <G> merge(scanned: List<G>, installed: List<DroidDeckGame>, steamIdOf: (G) -> String?, make: (DroidDeckGame) -> G): List<G> {
        val shortcuts = scanned.mapNotNull { steamIdOf(it)?.let(::normalId) }.toSet()
        val extra = installed.filter { it.launchable && it.appId !in shortcuts }
        if (extra.isEmpty()) return scanned
        return scanned + extra.map(make)
    }

    /** "<size> <mtime> <path>" lines from droiddeck.sh stat, by path. */
    fun statLines(out: String): Map<String, String> = out.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(' ', limit = 3)
        if (parts.size == 3 && parts[0].all(Char::isDigit) && parts[1].all(Char::isDigit)) parts[2] to "${parts[0]}:${parts[1]}" else null
    }.toMap()

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun seconds(e: JsonElement?): Long =
        e.str()?.trim()?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }?.takeIf { it > 0 }?.times(1000) ?: 0
}
