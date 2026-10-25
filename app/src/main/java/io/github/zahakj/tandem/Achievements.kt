package io.github.zahakj.tandem

import android.content.Context
import android.util.Log
import io.github.zahakj.tandem.data.HomeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * RetroAchievements, through its Web API with the user's own username and Web API key.
 *
 * Privacy: nothing here touches the network unless the user has signed in and left achievements on, and hidden
 * games are never hashed, matched or looked up. Only the console's public game list and the user's own progress
 * are requested; no game names or files are sent.
 */
object RetroAchievements {
    private const val API = "https://retroachievements.org/API/"
    const val MEDIA = "https://media.retroachievements.org"
    private const val TAG = "Tandem"
    private const val LIST_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val PROGRESS_TTL_MS = 10 * 60 * 1000L

    data class Profile(val user: String, val picUrl: String, val points: Int, val softPoints: Int, val memberSince: String, val richPresence: String, val motto: String)
    data class Earned(val title: String, val description: String, val points: Int, val badgeUrl: String, val game: String, val console: String, val date: String, val hardcore: Boolean)
    data class Achievement(val title: String, val description: String, val points: Int, val badgeUrl: String, val earned: Boolean, val earnedHardcore: Boolean, val order: Int)
    data class Progress(val raId: Int, val title: String, val total: Int, val earned: Int, val earnedHardcore: Boolean, val achievements: List<Achievement>)
    private data class RaGame(val id: Int, val title: String, val hashes: Set<String>)

    private var filesDir: File? = null
    private val listLock = Mutex()
    private val consoleLists = HashMap<Int, List<RaGame>>()
    private val progressCache = HashMap<Int, Pair<Long, Progress>>()

    fun init(ctx: Context) { filesDir = ctx.applicationContext.filesDir }

    val enabled: Boolean get() = HomeStore.ra.let { it.enabled && it.user.isNotBlank() && it.key.isNotBlank() }

    /** Progress may have changed while a game was being played. */
    @Synchronized
    fun invalidate() { progressCache.clear() }

    /** RetroAchievements console ids for Tandem's (ES-DE style) system ids. Systems RA doesn't support are missing. */
    fun consoleId(platformId: String): Int? = when (platformId.lowercase()) {
        "megadrive", "genesis", "md" -> 1
        "n64" -> 2
        "snes", "sfc" -> 3
        "gb" -> 4
        "gba" -> 5
        "gbc" -> 6
        "nes", "famicom", "fds" -> 7
        "pcengine", "tg16" -> 8
        "segacd", "megacd" -> 9
        "sega32x", "32x" -> 10
        "mastersystem", "sms" -> 11
        "psx", "ps1" -> 12
        "atarilynx", "lynx" -> 13
        "ngp", "ngpc" -> 14
        "gamegear", "gg" -> 15
        "gc", "gamecube" -> 16
        "nds" -> 18
        "wii" -> 19
        "ps2" -> 21
        "pokemini" -> 24
        "atari2600" -> 25
        "arcade", "mame", "fbneo" -> 27
        "virtualboy" -> 28
        "saturn" -> 39
        "dreamcast" -> 40
        "psp" -> 41
        "atari7800" -> 51
        "wonderswan", "wonderswancolor" -> 53
        "n3ds", "3ds" -> 62
        else -> null
    }

    private suspend fun call(endpoint: String, params: Map<String, String>, key: String = HomeStore.ra.key): String? =
        withContext(Dispatchers.IO) {
            val query = (params + mapOf("y" to key)).entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
            runCatching {
                val conn = URL("$API$endpoint?$query").openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("User-Agent", "Tandem/${BuildConfigLite.VERSION} (Android game frontend)")
                try {
                    if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            }.onFailure { Log.w(TAG, "RetroAchievements $endpoint failed: ${it.message}") }.getOrNull()
        }

    private fun media(path: String): String = if (path.startsWith("http")) path else MEDIA + path

    /** Checks a username and key; the profile when they work. */
    suspend fun verify(user: String, key: String): Profile? =
        call("API_GetUserProfile.php", mapOf("u" to user), key)?.let(::parseProfile)

    suspend fun profile(): Profile? {
        if (!enabled) return null
        return call("API_GetUserProfile.php", mapOf("u" to HomeStore.ra.user))?.let(::parseProfile)
    }

    private fun parseProfile(text: String): Profile? = runCatching {
        val o = JSONObject(text)
        val user = o.optString("User").ifBlank { return null }
        Profile(
            user = user,
            picUrl = media(o.optString("UserPic").ifBlank { "/UserPic/$user.png" }),
            points = o.optInt("TotalPoints"),
            softPoints = o.optInt("TotalSoftcorePoints"),
            memberSince = o.optString("MemberSince").substringBefore(' '),
            richPresence = o.optString("RichPresenceMsg"),
            motto = o.optString("Motto"),
        )
    }.getOrNull()

    /** Achievements earned in the last 30 days, newest first. */
    suspend fun recent(): List<Earned> {
        if (!enabled) return emptyList()
        val text = call("API_GetUserRecentAchievements.php", mapOf("u" to HomeStore.ra.user, "m" to "${60 * 24 * 30}")) ?: return emptyList()
        return runCatching {
            val a = JSONArray(text)
            (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
                Earned(
                    title = o.optString("Title"),
                    description = o.optString("Description"),
                    points = o.optInt("Points"),
                    badgeUrl = media(o.optString("BadgeURL").ifBlank { "/Badge/${o.optString("BadgeName")}.png" }),
                    game = o.optString("GameTitle"),
                    console = o.optString("ConsoleName"),
                    date = o.optString("Date"),
                    hardcore = o.optInt("HardcoreMode") == 1,
                )
            }.sortedByDescending { it.date }.take(60)
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun cachedProgress(game: Game): Progress? {
        val id = (game.meta.raId ?: HomeStore.ra.matches[game.key])?.takeIf { it > 0 } ?: return null
        return progressCache[id]?.second
    }

    /** What [game] is matched to without asking anyone: the id chosen in Edit details, else the remembered match. */
    fun knownMatch(game: Game): Int? = game.meta.raId ?: HomeStore.ra.matches[game.key]

    /** A RetroAchievements game, as Edit details offers it. */
    data class Candidate(val id: Int, val title: String, val byHash: Boolean)

    /**
     * Candidates for [game] from its console's game list (Edit details, only when the user opens the matcher): the
     * file's hash match first, then the closest titles. Null when signed out, the game is hidden, or the system has
     * no achievements. The list is the one already cached for a week, fetched only if missing.
     */
    suspend fun candidates(game: Game, limit: Int = 8): List<Candidate>? {
        if (!enabled || Privacy.isHidden(game)) return null
        val console = consoleId(game.platform.id) ?: return null
        val list = consoleGames(console) ?: return null
        val hash = withContext(Dispatchers.IO) { runCatching { RaHash.compute(game.file, console) }.getOrNull() }
        val byHash = hash?.let { h -> list.filter { h in it.hashes } }.orEmpty()
        val ranked = withContext(Dispatchers.Default) { rank(listOf(game.title, game.fileTitle), list.map { it.id to it.title }, limit) }
        return (byHash.map { Candidate(it.id, it.title, true) } + ranked.map { (id, t) -> Candidate(id, t, false) })
            .distinctBy { it.id }.take(limit)
    }

    /** The title of RetroAchievements game [id] in [game]'s console list, if that list is loaded or cached. */
    suspend fun titleOf(game: Game, id: Int): String? {
        if (!enabled || Privacy.isHidden(game) || id <= 0) return null
        val console = consoleId(game.platform.id) ?: return null
        return consoleGames(console)?.firstOrNull { it.id == id }?.title
    }

    /**
     * The closest of [games] (id to title) to any of [wanted], best first: an exact match of the tidied title, then
     * the share of words in common. Plain, for tests.
     */
    fun rank(wanted: List<String>, games: List<Pair<Int, String>>, limit: Int): List<Pair<Int, String>> {
        val keys = wanted.map(::norm).toSet()
        val words = wanted.flatMap(::words).toSet()
        if (words.isEmpty()) return emptyList()
        return games.asSequence()
            .filter { !it.second.startsWith("~") }
            .map { g ->
                val w = words(g.second)
                val common = w.count { it in words }
                val score = if (norm(g.second) in keys) 2.0 else common.toDouble() / maxOf(w.size, words.size).coerceAtLeast(1)
                g to score
            }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(limit).map { it.first }.toList()
    }

    private fun words(title: String): Set<String> =
        title.lowercase().replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").replace("&", "and")
            .split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() && it != "the" }.toSet()

    /** The user's progress in [game], or null when it isn't on RetroAchievements (or achievements are off). */
    suspend fun progress(game: Game): Progress? {
        if (!enabled || Privacy.isHidden(game)) return null
        val id = gameId(game) ?: return null
        synchronized(this) {
            progressCache[id]?.let { (at, p) -> if (System.currentTimeMillis() - at < PROGRESS_TTL_MS) return p }
        }
        val text = call("API_GetGameInfoAndUserProgress.php", mapOf("g" to "$id", "u" to HomeStore.ra.user)) ?: return null
        val p = runCatching {
            val o = JSONObject(text)
            val list = o.optJSONObject("Achievements")?.let { a -> a.keys().asSequence().mapNotNull { a.optJSONObject(it) }.toList() } ?: emptyList()
            val achievements = list.map { x ->
                Achievement(
                    title = x.optString("Title"),
                    description = x.optString("Description"),
                    points = x.optInt("Points"),
                    badgeUrl = "$MEDIA/Badge/${x.optString("BadgeName")}.png",
                    earned = x.optString("DateEarned").isNotBlank() && !x.isNull("DateEarned"),
                    earnedHardcore = x.optString("DateEarnedHardcore").isNotBlank() && !x.isNull("DateEarnedHardcore"),
                    order = x.optInt("DisplayOrder"),
                )
            }.sortedBy { it.order }
            Progress(
                raId = id,
                title = o.optString("Title"),
                total = o.optInt("NumAchievements", achievements.size),
                earned = o.optInt("NumAwardedToUser"),
                earnedHardcore = o.optInt("NumAwardedToUserHardcore") > 0,
                achievements = achievements,
            )
        }.getOrNull() ?: return null
        synchronized(this) { progressCache[id] = System.currentTimeMillis() to p }
        return p
    }

    /** Finds the RetroAchievements game for [game]: by file hash first, then by title. Remembered once found. */
    private suspend fun gameId(game: Game): Int? {
        if (Privacy.isHidden(game)) return null
        // Chosen by hand in Edit details: that game, or (0) none at all.
        game.meta.raId?.let { return it.takeIf { id -> id > 0 } }
        HomeStore.ra.matches[game.key]?.let { return it.takeIf { id -> id > 0 } }
        val console = consoleId(game.platform.id) ?: return null
        val list = consoleGames(console) ?: return null
        val hash = withContext(Dispatchers.IO) { runCatching { RaHash.compute(game.file, console) }.getOrNull() }
        val wanted = setOf(norm(game.title), norm(game.fileTitle))
        val match = hash?.let { h -> list.firstOrNull { h in it.hashes } }
            ?: list.firstOrNull { !it.title.startsWith("~") && norm(it.title) in wanted }
        HomeStore.updateRa { it.copy(matches = it.matches + (game.key to (match?.id ?: 0))) }
        return match?.id
    }

    /** "Legend of Zelda, The: The Minish Cap" and "The Legend of Zelda - The Minish Cap" both become one key. */
    private fun norm(title: String): String {
        val t = title.lowercase().replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").replace("&", "and")
        val words = t.split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() && it != "the" }
        return words.joinToString("")
    }

    /** The console's games that have achievements, with their hashes. Cached on disk for a week. */
    private suspend fun consoleGames(console: Int): List<RaGame>? = listLock.withLock {
        consoleLists[console]?.let { return@withLock it }
        val cache = filesDir?.let { File(it, "ra/console-$console.json") }
        val fresh = cache?.takeIf { it.isFile && System.currentTimeMillis() - it.lastModified() < LIST_MAX_AGE_MS }?.readText()
        val text = fresh ?: call("API_GetGameList.php", mapOf("i" to "$console", "h" to "1", "f" to "1"))?.also { t ->
            if (cache != null && t.trimStart().startsWith("[")) withContext(Dispatchers.IO) { cache.parentFile?.mkdirs(); cache.writeText(t) }
        } ?: cache?.takeIf { it.isFile }?.readText() ?: return@withLock null
        val list = withContext(Dispatchers.Default) {
            runCatching {
                val a = JSONArray(text)
                (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
                    val h = o.optJSONArray("Hashes")
                    RaGame(o.optInt("ID"), o.optString("Title"), if (h == null) emptySet() else (0 until h.length()).map { h.getString(it).lowercase() }.toSet())
                }
            }.getOrNull()
        } ?: return@withLock null
        consoleLists[console] = list
        list
    }

    /** Forget every match, so games are looked up again (after renaming or replacing files). */
    fun forgetMatches() {
        HomeStore.updateRa { it.copy(matches = emptyMap()) }
        invalidate()
    }
}

/** The app version for the User-Agent, without depending on generated BuildConfig. */
private object BuildConfigLite { const val VERSION = "0.2" }

/**
 * RetroAchievements' game hashes, for the systems where they're simple enough to compute here (an MD5 of the
 * ROM, sometimes minus a header, and a special recipe for DS). Other systems are matched by title instead.
 */
object RaHash {
    private const val MAX_BYTES = 64L * 1024 * 1024

    fun compute(file: File, console: Int): String? {
        if (console == 18) return if (file.extension.equals("nds", true)) nds(file) else null
        if (console !in setOf(1, 2, 3, 4, 5, 6, 7, 10, 11, 13, 14, 15, 24, 25, 28, 51, 53)) return null
        if (file.extension.equals("zip", true)) {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().filter { !it.isDirectory }.toList()
                val entry = entries.singleOrNull() ?: return null
                if (entry.size > MAX_BYTES) return null
                return zip.getInputStream(entry).use { md5Rom(it.readBytes(), console) }
            }
        }
        if (file.length() > MAX_BYTES) return null
        return md5Rom(file.readBytes(), console)
    }

    private fun md5Rom(bytes: ByteArray, console: Int): String {
        var data = bytes
        var skip = 0
        when (console) {
            7 -> if (data.startsWith("NES\u001a") || data.startsWith("FDS\u001a")) skip = 16
            3 -> if (data.size % 8192 == 512) skip = 512
            13 -> if (data.startsWith("LYNX\u0000")) skip = 64
            51 -> if (data.size > 128 && String(data, 1, 9, Charsets.ISO_8859_1) == "ATARI7800") skip = 128
            2 -> data = n64BigEndian(data)
        }
        val md = MessageDigest.getInstance("MD5")
        md.update(data, skip, data.size - skip)
        return md.digest().hex()
    }

    private fun ByteArray.startsWith(s: String): Boolean =
        size >= s.length && s.indices.all { this[it] == s[it].code.toByte() }

    /** N64 dumps come in three byte orders; RetroAchievements hashes the big-endian (.z64) one. */
    private fun n64BigEndian(d: ByteArray): ByteArray {
        if (d.size < 4) return d
        val b0 = d[0].toInt() and 0xff
        return when (b0) {
            0x37 -> d.copyOf().also { o -> var i = 0; while (i + 1 < o.size) { val t = o[i]; o[i] = o[i + 1]; o[i + 1] = t; i += 2 } }
            0x40 -> d.copyOf().also { o -> var i = 0; while (i + 3 < o.size) { o[i] = d[i + 3]; o[i + 1] = d[i + 2]; o[i + 2] = d[i + 1]; o[i + 3] = d[i]; i += 4 } }
            else -> d
        }
    }

    /** DS: the header, both ARM binaries and the icon/title block, the way RetroAchievements' rcheevos does it. */
    private fun nds(file: File): String? = RandomAccessFile(file, "r").use { f ->
        var offset = 0L
        var header = ByteArray(0x200)
        f.readFully(header)
        // Some flash-cart dumps carry an extra 512-byte header first.
        if (header[0] == 0x2E.toByte() && header[1] == 0.toByte() && header[2] == 0.toByte() && header[3] == 0xEA.toByte() &&
            header[0xB0] == 0x44.toByte() && header[0xB1] == 0x46.toByte() && header[0xB2] == 0x96.toByte() && header[0xB3] == 0.toByte()
        ) {
            offset = 0x200
            header = ByteArray(0x200)
            f.seek(offset)
            f.readFully(header)
        }
        fun u32(at: Int): Long = (header[at].toLong() and 0xff) or ((header[at + 1].toLong() and 0xff) shl 8) or
            ((header[at + 2].toLong() and 0xff) shl 16) or ((header[at + 3].toLong() and 0xff) shl 24)
        val arm9Addr = u32(0x20); val arm9Size = u32(0x2C)
        val arm7Addr = u32(0x30); val arm7Size = u32(0x3C)
        val iconAddr = u32(0x68)
        if (arm9Size + arm7Size > 16L * 1024 * 1024) return@use null
        val md = MessageDigest.getInstance("MD5")
        md.update(header, 0, 0x160)
        fun append(at: Long, size: Int) {
            val buf = ByteArray(size)
            f.seek(at + offset)
            var read = 0
            while (read < size) { val n = f.read(buf, read, size - read); if (n <= 0) break; read += n }
            md.update(buf)
        }
        append(arm9Addr, arm9Size.toInt())
        append(arm7Addr, arm7Size.toInt())
        append(iconAddr, 0xA00)
        md.digest().hex()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

}
