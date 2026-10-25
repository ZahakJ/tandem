package io.github.zahakj.tandem.data

import io.github.zahakj.tandem.Library
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar
import java.util.TimeZone

// ---------------------------------------------------------------------------------------------------------------
// IGDB (through Twitch)
// ---------------------------------------------------------------------------------------------------------------

/** What IGDB knows about a game. Picture fields are IGDB image ids. */
data class IgdbGame(
    val name: String,
    val summary: String?,
    val year: Int?,
    val genres: List<String>,
    val developer: String?,
    val publisher: String?,
    val rating: Float?,
    val players: String?,
    val cover: String?,
    val artworks: List<String>,
    val screenshots: List<String>,
) {
    companion object {
        fun image(id: String, size: String) = "https://images.igdb.com/igdb/image/upload/t_$size/$id.jpg"
    }
}

/**
 * IGDB's API v4 with the user's own Twitch application (client id + secret, client-credentials sign-in). The access
 * token lives in memory only, for the run. IGDB allows 4 requests a second; the caller paces requests.
 */
class Igdb(private val clientId: String, private val secret: String) {
    @Volatile private var token: String? = null

    /** Gets an app access token from Twitch. Throws [SourceDown] when the id or secret is wrong. */
    fun signIn() {
        val body = "client_id=${Net.enc(clientId)}&client_secret=${Net.enc(secret)}&grant_type=client_credentials"
        try {
            val o = JSONObject(Net.post("https://id.twitch.tv/oauth2/token", body, contentType = "application/x-www-form-urlencoded"))
            token = o.optString("access_token").takeIf { it.isNotBlank() } ?: throw SourceDown("Twitch didn't hand out an IGDB pass")
        } catch (e: HttpError) {
            if (e.code in 400..403) throw SourceDown("Twitch didn't accept the IGDB client ID or secret") else throw e
        } catch (e: JSONException) {
            throw IOException("Twitch sent something unexpected")
        }
    }

    private fun query(endpoint: String, body: String): JSONArray {
        var retried = false
        while (true) {
            val t = token ?: run { signIn(); token!! }
            try {
                val headers = mapOf("Client-ID" to clientId, "Authorization" to "Bearer $t", "Accept" to "application/json")
                return JSONArray(Net.post("https://api.igdb.com/v4/$endpoint", body, headers, contentType = "text/plain"))
            } catch (e: HttpError) {
                when {
                    e.code == 401 && !retried -> { token = null; retried = true }
                    e.code == 429 && !retried -> { Thread.sleep(1200); retried = true }
                    e.code == 401 || e.code == 403 -> throw SourceDown("IGDB turned the request away; check the client ID and secret")
                    else -> throw e
                }
            } catch (e: JSONException) {
                throw IOException("IGDB sent something unexpected")
            }
        }
    }

    /** The closest game to [title] on IGDB [platform] (or any platform when null), if it's close enough. */
    fun search(title: String, platform: Int?): IgdbGame? {
        val clean = Library.cleanTitle(title).replace("\"", "").replace("\\", "").trim()
        if (clean.isEmpty()) return null
        val q = buildString {
            append("search \"").append(clean).append("\"; ")
            append("fields name,summary,first_release_date,total_rating,genres.name,")
            append("involved_companies.company.name,involved_companies.developer,involved_companies.publisher,")
            append("cover.image_id,artworks.image_id,screenshots.image_id,multiplayer_modes.offlinemax,game_modes.name; ")
            if (platform != null) append("where platforms = ($platform); ")
            append("limit 10;")
        }
        val hits = query("games", q).objects()
        val best = hits.maxByOrNull { Match.similarity(it.optString("name"), clean) } ?: return null
        if (Match.similarity(best.optString("name"), clean) < 0.75f) return null
        return parse(best)
    }

    private fun parse(j: JSONObject): IgdbGame {
        val companies = j.optJSONArray("involved_companies").objects()
        fun company(role: String) = companies.firstOrNull { it.optBoolean(role) }?.optJSONObject("company")?.optString("name")?.takeIf { it.isNotBlank() }
        val year = j.optLong("first_release_date", 0L).takeIf { it > 0 }?.let { s ->
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = s * 1000 }.get(Calendar.YEAR)
        }
        val maxOffline = j.optJSONArray("multiplayer_modes").objects().maxOfOrNull { it.optInt("offlinemax", 0) } ?: 0
        val modes = j.optJSONArray("game_modes").objects().map { it.optString("name") }
        val players = when {
            maxOffline > 1 -> "1-$maxOffline"
            modes == listOf("Single player") -> "1"
            else -> null
        }
        return IgdbGame(
            name = j.optString("name"),
            summary = j.optString("summary").takeIf { it.isNotBlank() },
            year = year,
            genres = j.optJSONArray("genres").objects().mapNotNull { it.optString("name").takeIf(String::isNotBlank) }.take(3),
            developer = company("developer"),
            publisher = company("publisher"),
            rating = j.optDouble("total_rating", -1.0).takeIf { it >= 0 }?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) },
            players = players,
            cover = j.optJSONObject("cover")?.optString("image_id")?.takeIf { it.isNotBlank() },
            artworks = j.optJSONArray("artworks").objects().mapNotNull { it.optString("image_id").takeIf(String::isNotBlank) },
            screenshots = j.optJSONArray("screenshots").objects().mapNotNull { it.optString("image_id").takeIf(String::isNotBlank) },
        )
    }
}

// ---------------------------------------------------------------------------------------------------------------
// HowLongToBeat
// ---------------------------------------------------------------------------------------------------------------

data class HltbGame(val name: String, val time: TimeToBeat)

/**
 * HowLongToBeat has no official API. This uses the same search its website uses: ask for a short-lived search
 * token, then post the search with it. The site changes this now and then; when the usual address stops working
 * Tandem looks for the new one in the site's own scripts once, and otherwise gives up quietly for the run
 * ([SourceDown]) instead of failing the scrape.
 */
class Hltb {
    private val base = "https://howlongtobeat.com"
    @Volatile private var token: String? = null
    @Volatile private var initPath = "/api/search/site/init"
    @Volatile private var searchPath = "/api/search/site"
    private var discovered = false
    private val headers = mapOf("Referer" to "$base/", "Origin" to base, "Accept" to "application/json")

    private fun init() {
        val o = JSONObject(Net.text("$base$initPath?t=${System.currentTimeMillis()}", headers))
        token = o.optString("token").takeIf { it.isNotBlank() } ?: throw IOException("no token")
    }

    /** Finds the search addresses in the site's scripts, for when they move. */
    private fun discover(): Boolean {
        if (discovered) return false
        discovered = true
        return runCatching {
            val html = Net.text("$base/", headers)
            val chunks = Regex("""/_next/static/chunks/[A-Za-z0-9_\-./]+\.js""").findAll(html).map { it.value }.distinct().take(20)
            for (c in chunks) {
                val js = runCatching { Net.text(base + c, headers) }.getOrNull() ?: continue
                val m = Regex("""fetch\(`(/api/[A-Za-z0-9_/]+)/init\?t=""").find(js) ?: continue
                initPath = m.groupValues[1] + "/init"
                searchPath = m.groupValues[1]
                return@runCatching true
            }
            false
        }.getOrDefault(false)
    }

    private fun body(title: String): String = JSONObject().apply {
        put("searchType", "games")
        put("searchTerms", JSONArray(title.split(' ').filter { it.isNotBlank() }))
        put("searchPage", 1)
        put("size", 20)
        put("searchOptions", JSONObject().apply {
            put("games", JSONObject().apply {
                put("userId", 0); put("platform", ""); put("sortCategory", "popular"); put("rangeCategory", "main")
                put("rangeTime", JSONObject().apply { put("min", JSONObject.NULL); put("max", JSONObject.NULL) })
                put("gameplay", JSONObject().apply { put("perspective", ""); put("flow", ""); put("genre", ""); put("difficulty", "") })
                put("rangeYear", JSONObject().apply { put("min", ""); put("max", "") })
                put("modifier", "")
            })
            put("users", JSONObject().apply { put("sortCategory", "postcount") })
            put("lists", JSONObject().apply { put("sortCategory", "follows") })
            put("filter", ""); put("sort", 0); put("randomizer", 0)
        })
        put("useCache", true)
    }.toString()

    fun search(title: String): HltbGame? {
        val clean = Library.cleanTitle(title).replace(Regex("[^\\p{L}\\p{N}' ]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return null
        var attempt = 0
        while (true) {
            attempt++
            try {
                if (token == null) init()
                val res = JSONObject(Net.post(base + searchPath, body(clean), headers + ("x-auth-token" to token!!)))
                return pick(res.optJSONArray("data").objects(), clean)
            } catch (e: HttpError) {
                token = null
                when {
                    e.code == 429 -> throw SourceDown("HowLongToBeat asked Tandem to slow down; skipping it for now")
                    attempt < 2 && e.code == 403 -> Unit
                    attempt < 3 && e.code == 404 && discover() -> Unit
                    else -> throw SourceDown("HowLongToBeat changed how its search works, so I skipped it")
                }
            } catch (e: JSONException) {
                token = null
                if (attempt >= 2 || !discover()) throw SourceDown("HowLongToBeat changed how its search works, so I skipped it")
            } catch (e: IOException) {
                if (attempt >= 2) throw e
                token = null
            }
        }
    }

    private fun pick(items: List<JSONObject>, title: String): HltbGame? {
        val best = items.maxByOrNull { o ->
            Match.similarity(o.optString("game_name"), title) + (if (o.optString("game_type") == "game") 0.05f else 0f) +
                (if (Match.key(o.optString("game_name")) == Match.key(title)) 0.1f else 0f)
        } ?: return null
        if (Match.similarity(best.optString("game_name"), title) < 0.75f) return null
        val t = TimeToBeat(best.optInt("comp_main"), best.optInt("comp_plus"), best.optInt("comp_100"))
        return if (t.any) HltbGame(best.optString("game_name"), t) else null
    }
}
