package io.github.zahakj.tandem

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import java.util.Locale

/*
 * The Steam Web API, the pure part: the addresses asked for, how their answers are read, how the three answers
 * about one game become one record, and which unlocks are new since the last look. Nothing here touches Android
 * or the network, so it runs in plain unit tests; SteamAchievements.kt does the fetching, caching and timing.
 */

/** One achievement of a Steam game: what the game's schema says about it and where the player stands with it. */
@Serializable
data class SteamAchievement(
    /** The schema's `apiname`, stable per game. */
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String = "",
    /** The grey icon Steam shows while it is locked. */
    val iconGray: String = "",
    /** Steam keeps a hidden achievement's description to itself until it is unlocked. */
    val hidden: Boolean = false,
    val unlocked: Boolean = false,
    /** When it was unlocked, in ms since the epoch; 0 while locked or when Steam didn't say. */
    val unlockedAt: Long = 0,
    /** Share of the game's players who have it, 0..100; below zero when not known. */
    val percent: Float = -1f,
) {
    /** The icon to draw: the coloured one once unlocked, else the grey one (the coloured one when there is no grey). */
    val shownIcon: String get() = if (unlocked || iconGray.isBlank()) icon else iconGray
}

/** Everything known about one game's achievements, as cached on disk. [fetchedAt] in ms; [achievements] in Steam's order. */
@Serializable
data class SteamGameAchievements(
    val appId: Int,
    val gameName: String,
    val fetchedAt: Long,
    val achievements: List<SteamAchievement>,
) {
    val total: Int get() = achievements.size
    val unlocked: Int get() = achievements.count { it.unlocked }
    /** The game has no achievements at all (looked up and found none). */
    val none: Boolean get() = achievements.isEmpty()
    val fraction: Float get() = if (total == 0) 0f else unlocked / total.toFloat()
}

object SteamApi {
    const val HOST = "https://api.steampowered.com"

    private val json = Json { ignoreUnknownKeys = true }

    /** ISteamUser/ResolveVanityURL: the account id behind a profile name (steamcommunity.com/id/<name>). */
    fun vanityUrl(key: String, name: String): String = "$HOST/ISteamUser/ResolveVanityURL/v1/?key=${enc(key)}&vanityurl=${enc(name)}"

    /** ISteamUser/GetPlayerSummaries: the account's public name and picture; also checks the key works. */
    fun summariesUrl(key: String, steamId: String): String = "$HOST/ISteamUser/GetPlayerSummaries/v2/?key=${enc(key)}&steamids=${enc(steamId)}"

    /** ISteamUserStats/GetPlayerAchievements: which of a game's achievements this account has, and when. */
    fun playerUrl(key: String, steamId: String, appId: Int): String =
        "$HOST/ISteamUserStats/GetPlayerAchievements/v1/?key=${enc(key)}&steamid=${enc(steamId)}&appid=$appId&l=english"

    /** ISteamUserStats/GetSchemaForGame: a game's achievements, their names, descriptions and icons. */
    fun schemaUrl(key: String, appId: Int): String = "$HOST/ISteamUserStats/GetSchemaForGame/v2/?key=${enc(key)}&appid=$appId&l=english"

    /** ISteamUserStats/GetGlobalAchievementPercentagesForApp: how many players have each one. Public: takes no key. */
    fun percentagesUrl(appId: Int): String = "$HOST/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/?gameid=$appId"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    // --- The account ---------------------------------------------------------------------------------------------

    /** What the user typed for their account: a SteamID64 or a profile name still to be resolved. */
    data class AccountInput(val steamId: String? = null, val vanity: String? = null)

    private const val STEAM64_BASE = 76561197960265728L

    /**
     * Reads a SteamID64 (`76561198000000000`), a profile address (`steamcommunity.com/profiles/<id>` or `/id/<name>`),
     * the older `STEAM_0:1:12345` and `[U:1:24691]` forms, or a plain profile name. Null for nothing usable.
     */
    fun parseAccount(input: String): AccountInput? {
        val t = input.trim().trimEnd('/')
        if (t.isEmpty()) return null
        Regex("""steamcommunity\.com/profiles/(\d{17})""").find(t)?.let { return AccountInput(steamId = it.groupValues[1]) }
        Regex("""steamcommunity\.com/id/([^/?#\s]+)""").find(t)?.let { return AccountInput(vanity = it.groupValues[1]) }
        if (Regex("""\d{17}""").matches(t)) return AccountInput(steamId = t)
        Regex("""STEAM_[0-5]:([01]):(\d+)""", RegexOption.IGNORE_CASE).matchEntire(t)?.let {
            return AccountInput(steamId = (STEAM64_BASE + it.groupValues[2].toLong() * 2 + it.groupValues[1].toLong()).toString())
        }
        Regex("""\[?U:1:(\d+)]?""", RegexOption.IGNORE_CASE).matchEntire(t)?.let {
            return AccountInput(steamId = (STEAM64_BASE + it.groupValues[1].toLong()).toString())
        }
        if (t.any { it.isWhitespace() || it == '/' }) return null
        return AccountInput(vanity = t)
    }

    /** The SteamID64 from a ResolveVanityURL answer, or null when Steam found no such name. */
    fun parseVanity(text: String): String? = runCatching {
        val r = parse(text).obj()?.get("response").obj() ?: return null
        if (r["success"].num()?.toInt() != 1) return null
        r["steamid"].str()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    data class Profile(val steamId: String, val name: String, val avatar: String)

    /** The first player of a GetPlayerSummaries answer. */
    fun parseSummaries(text: String): Profile? = runCatching {
        val p = parse(text).obj()?.get("response").obj()?.get("players").arr()?.firstOrNull().obj() ?: return null
        val id = p["steamid"].str()?.takeIf { it.isNotBlank() } ?: return null
        Profile(id, p["personaname"].str().orEmpty(), p["avatarfull"].str() ?: p["avatarmedium"].str() ?: p["avatar"].str().orEmpty())
    }.getOrNull()

    // --- One game ------------------------------------------------------------------------------------------------

    /** The player's side: the game's name and, for each unlocked achievement, when (ms; 0 when Steam didn't say). */
    data class PlayerState(val gameName: String, val unlocked: Map<String, Long>)

    /** One achievement as the game's schema describes it. */
    data class SchemaEntry(val id: String, val name: String, val description: String, val icon: String, val iconGray: String, val hidden: Boolean)

    /** Reads GetPlayerAchievements. Null when the answer isn't one (`success: false`, or not JSON). */
    fun parsePlayerAchievements(text: String): PlayerState? = runCatching {
        val ps = parse(text).obj()?.get("playerstats").obj() ?: return null
        if ((ps["success"] as? JsonPrimitive)?.contentOrNull == "false") return null
        val unlocked = LinkedHashMap<String, Long>()
        for (e in ps["achievements"].arr().orEmpty()) {
            val o = e.obj() ?: continue
            val id = o["apiname"].str() ?: continue
            if (o["achieved"].num()?.toInt() == 1) unlocked[id] = (o["unlocktime"].num()?.toLong() ?: 0L) * 1000
        }
        PlayerState(ps["gameName"].str().orEmpty(), unlocked)
    }.getOrNull()

    /** Reads GetSchemaForGame. Empty for a game without achievements (`{"game":{}}`), null when it isn't an answer. */
    fun parseSchema(text: String): List<SchemaEntry>? = runCatching {
        val game = parse(text).obj()?.get("game").obj() ?: return null
        val list = game["availableGameStats"].obj()?.get("achievements").arr() ?: return emptyList()
        list.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o["name"].str() ?: return@mapNotNull null
            SchemaEntry(
                id = id,
                name = o["displayName"].str()?.ifBlank { null } ?: id,
                description = o["description"].str().orEmpty(),
                icon = o["icon"].str().orEmpty(),
                iconGray = o["icongray"].str().orEmpty(),
                hidden = o["hidden"].num()?.toInt() == 1,
            )
        }
    }.getOrNull()

    /** Reads GetGlobalAchievementPercentagesForApp: achievement id to the share of players who have it. */
    fun parsePercentages(text: String): Map<String, Float> = runCatching {
        val list = parse(text).obj()?.get("achievementpercentages").obj()?.get("achievements").arr() ?: return emptyMap()
        list.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o["name"].str() ?: return@mapNotNull null
            val p = o["percent"].num() ?: return@mapNotNull null
            id to p.toFloat()
        }.toMap()
    }.getOrDefault(emptyMap())

    /**
     * The three answers as one record, in the schema's order. [player] null means Steam had no player stats for
     * the game (it has none, or they aren't public): every achievement is then locked.
     */
    fun merge(appId: Int, schema: List<SchemaEntry>, player: PlayerState?, percents: Map<String, Float>, now: Long): SteamGameAchievements =
        SteamGameAchievements(
            appId = appId,
            gameName = player?.gameName.orEmpty(),
            fetchedAt = now,
            achievements = schema.map { s ->
                val at = player?.unlocked?.get(s.id)
                SteamAchievement(
                    id = s.id, name = s.name, description = s.description, icon = s.icon, iconGray = s.iconGray, hidden = s.hidden,
                    unlocked = at != null, unlockedAt = at ?: 0L, percent = percents[s.id] ?: -1f,
                )
            },
        )

    /**
     * What was unlocked since [before], oldest unlock first. A game never looked at before has nothing "new": the
     * first look just records what is there, so nobody is greeted by a hundred toasts after signing in.
     */
    fun newUnlocks(before: SteamGameAchievements?, after: SteamGameAchievements): List<SteamAchievement> {
        if (before == null) return emptyList()
        val had = before.achievements.filter { it.unlocked }.mapTo(HashSet()) { it.id }
        return after.achievements.filter { it.unlocked && it.id !in had }.sortedBy { it.unlockedAt }
    }

    /** Unlocked ones first, newest unlock first; then the locked ones, the most commonly earned (the next to get) first. */
    fun displayOrder(list: List<SteamAchievement>): List<SteamAchievement> =
        list.filter { it.unlocked }.sortedByDescending { it.unlockedAt } +
            list.filter { !it.unlocked }.sortedByDescending { if (it.percent < 0) -1f else it.percent }

    // --- Words ---------------------------------------------------------------------------------------------------

    /** "2.3 %", "41 %" or "under 1 %"; null when the share isn't known. */
    fun percentLabel(percent: Float): String? = when {
        percent < 0f -> null
        percent < 1f -> "under 1 %"
        percent < 10f -> String.format(Locale.US, "%.1f %%", percent)
        else -> "${percent.toInt()} %"
    }

    /** True for the ones few players have: worth a word when unlocked. */
    fun rare(percent: Float): Boolean = percent in 0f..10f

    /** A toast: what it says and the achievement's icon, when it has one. */
    data class Toast(val text: String, val icon: String?)

    /**
     * What Pip says when [unlocks] arrived during a session of [gameName]: one toast for each of the first [max],
     * with how rare it is, then one for the rest together.
     */
    fun announcement(gameName: String, unlocks: List<SteamAchievement>, max: Int = 3): List<Toast> {
        if (unlocks.isEmpty()) return emptyList()
        val shown = unlocks.take(max).map { a ->
            val rarity = percentLabel(a.percent)
            val tail = when {
                rarity == null -> if (gameName.isNotBlank()) "In $gameName." else ""
                rare(a.percent) -> "Only $rarity of players have it!"
                else -> "$rarity of players have it."
            }
            Toast(("You got “${a.name}”! $tail").trim(), a.icon.ifBlank { null })
        }
        val rest = unlocks.size - shown.size
        if (rest <= 0) return shown
        val where = if (gameName.isNotBlank()) " in $gameName" else ""
        return shown + Toast("…and ${plural(rest, "more achievement")}$where. What a session!", null)
    }

    private fun plural(n: Int, word: String): String = if (n == 1) "1 $word" else "$n ${word}s"

    /**
     * The Steam app id a library file stands for: a `.steam` or `.steamappid` shortcut's first line (a bare number,
     * `app_id=620` or `[name] 620`), or a `[steam] 620` line in a streamed game's `.moonlight` file. Sunshine's own
     * `[appid]` in those files is a CRC32 of the app's name, which no test can tell from a Steam id, so it never counts.
     */
    fun appId(extension: String, firstLine: String?, tags: Map<String, String>): Int? {
        val raw = when (extension.lowercase()) {
            "steam", "steamappid" -> GameFiles.idIn(firstLine)
            "moonlight" -> tags["steam"]?.trim()
            else -> null
        } ?: return null
        return raw.takeIf { it.isNotEmpty() && it.length <= 9 && it.all(Char::isDigit) }?.toInt()?.takeIf { it > 0 }
    }

    // --- JSON helpers --------------------------------------------------------------------------------------------

    private fun parse(text: String): JsonElement = json.parseToJsonElement(text)
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.num(): Double? = (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
}
