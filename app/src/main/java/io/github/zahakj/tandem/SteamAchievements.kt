package io.github.zahakj.tandem

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.zahakj.tandem.data.HttpError
import io.github.zahakj.tandem.data.Net
import io.github.zahakj.tandem.data.SteamAccount
import io.github.zahakj.tandem.data.SteamStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Steam achievements for the library's Steam games (`.steam` shortcuts, and streamed games whose `.moonlight` file
 * names a `[steam]` id), through the Steam Web API with the user's own key and account.
 *
 * Privacy: nothing goes out without a key and an account, nothing in private mode (Net checks it), and hidden games
 * are never looked up. Steam receives the key, the account's SteamID64 and the app ids of the games asked about;
 * only the public rarity endpoint is asked without the key.
 *
 * Every game's record is cached in `filesDir/steam/<app id>.json` and kept in [games], so screens show the last
 * look at once. A game highlighted on the bottom screen is looked at again when its record is older than
 * [BROWSE_MAX_AGE_MS]; after a session, when it is older than [SESSION_MAX_AGE_MS] (and what is new is announced);
 * the whole library only on demand ([refreshAll]). The icons come through the picture loader's own disk cache.
 */
object SteamAchievements {
    private const val TAG = "Tandem"
    const val BROWSE_MAX_AGE_MS = 10 * 60_000L
    const val SESSION_MAX_AGE_MS = 2 * 60_000L
    /** A game found to have no achievements is asked about again only this rarely (or on demand). */
    private const val NONE_MAX_AGE_MS = 24 * 60 * 60_000L

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private var dir: File? = null
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "tandem-steam").apply { isDaemon = true }
    }
    private val inFlight = Collections.synchronizedSet(HashSet<Int>())
    /** Game key to the app id its file holds (0 for none), with the file's modification time the answer was read at. */
    private val ids = ConcurrentHashMap<String, Pair<Long, Int>>()

    /** What is known per app id. Compose state, so every screen follows a new look. */
    val games = mutableStateMapOf<Int, SteamGameAchievements>()

    /** True while Look up achievements goes through the library. */
    var refreshing by mutableStateOf(false)
        private set

    /** Why the last look failed in a way the user can fix (a refused key, a private profile); null when all is well. */
    var problem by mutableStateOf<String?>(null)
        private set

    val enabled: Boolean get() = SteamStore.account.signedIn

    fun init(ctx: Context) {
        if (dir != null) return
        val d = File(ctx.applicationContext.filesDir, "steam")
        dir = d
        worker.execute {
            d.listFiles { f -> f.extension == "json" }?.forEach { f ->
                runCatching { json.decodeFromString<SteamGameAchievements>(f.readText()) }.getOrNull()?.let { games[it.appId] = it }
            }
            loaded = true
        }
    }

    /** The Steam app id [game]'s file stands for, or null. Read once per file (and again when the file changes). */
    fun appIdOf(game: Game): Int? {
        val ext = game.file.extension.lowercase()
        if (ext != "steam" && ext != "steamappid" && ext != "moonlight") return null
        val stamp = game.file.lastModified()
        ids[game.key]?.let { (at, id) -> if (at == stamp) return id.takeIf { it > 0 } }
        val id = SteamApi.appId(ext, GameFiles.firstLine(game.file), if (ext == "moonlight") GameFiles.tags(game.file) else emptyMap()) ?: 0
        ids[game.key] = stamp to id
        return id.takeIf { it > 0 }
    }

    /** The last look at [game]'s achievements, without asking anyone. Null when signed out, hidden or not a Steam game. */
    fun cached(game: Game): SteamGameAchievements? {
        if (!enabled || Privacy.isHidden(game)) return null
        return appIdOf(game)?.let { games[it] }
    }

    /** True when [record] should be looked at again for a reader who accepts [maxAgeMs] of age. */
    fun stale(record: SteamGameAchievements, maxAgeMs: Long): Boolean =
        System.currentTimeMillis() - record.fetchedAt > (if (record.none) maxOf(maxAgeMs, NONE_MAX_AGE_MS) else maxAgeMs)

    /**
     * [game]'s achievements for the bottom screen: the cached record while it is fresh enough, else a new look
     * (suspending). Falls back to the cached record when Steam can't be reached.
     */
    suspend fun progress(game: Game, maxAgeMs: Long = BROWSE_MAX_AGE_MS): SteamGameAchievements? {
        val have = cached(game)
        if (!enabled || Privacy.isHidden(game) || NetGate.closed) return have
        val id = appIdOf(game) ?: return null
        if (have != null && !stale(have, maxAgeMs)) return have
        return withContext(Dispatchers.IO) { fetch(id, have) } ?: have
    }

    /** One game, now (X in its achievements list). [done] on the main thread with the new record, or null. */
    fun lookUp(game: Game, done: (SteamGameAchievements?) -> Unit) {
        if (!enabled || Privacy.isHidden(game) || NetGate.closed) { done(null); return }
        val id = appIdOf(game) ?: run { done(null); return }
        worker.execute {
            val r = fetch(id, games[id])
            Handler(Looper.getMainLooper()).post { done(r) }
        }
    }

    /**
     * Right after a session of the game with [key] ended (Sessions.take): look again in the background, compare
     * with what was known, and have Pip announce what was unlocked.
     *
     * The game is marked first, and whichever look at it comes next announces (see [fetch]): coming back also
     * shows the game on the bottom screen, whose own look may well beat this one, and a mark means the unlocks are
     * told either way. The look itself waits until the last one is [SESSION_MAX_AGE_MS] old, so a quick session
     * right after browsing doesn't ask Steam twice in a minute. After a restart the library may not be scanned yet:
     * the game is looked for again a few times.
     */
    fun afterSession(key: String, tries: Int = 0) {
        if (!enabled || NetGate.closed) return
        val game = Libraries.byKey(key)
        if (game == null) {
            if (tries < 6) main.postDelayed({ afterSession(key, tries + 1) }, 5_000)
            return
        }
        if (Privacy.isHidden(game)) return
        val markedAt = System.currentTimeMillis()
        worker.execute {
            val id = appIdOf(game) ?: return@execute
            announce[id] = markedAt
            val last = games[id]?.fetchedAt ?: 0L
            val wait = (last + SESSION_MAX_AGE_MS - System.currentTimeMillis()).coerceIn(0, SESSION_MAX_AGE_MS)
            main.postDelayed({ worker.execute { lookAfterSession(id, markedAt) } }, wait)
        }
    }

    /** The look [afterSession] scheduled, unless another look has already announced (or is about to). */
    private fun lookAfterSession(id: Int, markedAt: Long) {
        // Another look under way (the bottom screen's) started before the mark or after it; wait for it either way.
        var waited = 0
        while (id in inFlight && waited < 30_000) { Thread.sleep(250); waited += 250 }
        if (announce[id] != markedAt) return
        fetch(id, games[id])
    }

    /** Game ids with a session just ended, to the time it ended: the next look at each says what was unlocked. */
    private val announce = ConcurrentHashMap<Int, Long>()
    private val main = Handler(Looper.getMainLooper())
    /** True once the cached records are read: before that, "nothing known" isn't yet true. */
    @Volatile private var loaded = false

    /**
     * Every Steam game in the library, now: Look up achievements, and the first look after signing in. [done] runs on
     * the main thread with how many games were looked up, how many achievements they have unlocked in all, and how
     * many games couldn't be looked up.
     */
    fun refreshAll(done: (looked: Int, unlocked: Int, failed: Int) -> Unit = { _, _, _ -> }) {
        if (!enabled || NetGate.closed || refreshing) return
        refreshing = true
        worker.execute {
            var looked = 0
            var unlocked = 0
            var failed = 0
            try {
                // Two shortcuts to one app share a record: counted once.
                val seen = HashSet<Int>()
                for (g in Libraries.games) {
                    if (NetGate.closed) break
                    if (Privacy.isHidden(g)) continue
                    val id = appIdOf(g) ?: continue
                    if (!seen.add(id)) continue
                    val r = fetch(id, games[id])
                    if (r == null) failed++ else { looked++; unlocked += r.unlocked }
                }
            } finally {
                refreshing = false
            }
            Handler(Looper.getMainLooper()).post { done(looked, unlocked, failed) }
        }
    }

    /** Forgets the account and every record: they describe that account's progress. */
    fun signOut() {
        SteamStore.update { SteamAccount() }
        games.clear()
        announce.clear()
        problem = null
        worker.execute { dir?.listFiles()?.forEach { it.delete() } }
    }

    /**
     * Blocking: the three requests about [appId], merged, saved and remembered. Null when a request failed (the
     * record from [before] stays as it was) or another thread is already on this game.
     */
    private fun fetch(appId: Int, before: SteamGameAchievements?): SteamGameAchievements? {
        val acc = SteamStore.account
        if (!acc.signedIn || NetGate.closed) return null
        if (!inFlight.add(appId)) return null
        val startedAt = System.currentTimeMillis()
        try {
            // The player's side first: it also says when the key or the profile's privacy stands in the way.
            val player = try {
                SteamApi.parsePlayerAchievements(Net.text(SteamApi.playerUrl(acc.key, acc.id, appId)))
            } catch (e: HttpError) {
                when (e.code) {
                    // "Requested app has no stats": a game without achievements, or one Steam has no stats for.
                    400 -> null
                    401 -> { problem = "Steam didn't accept the API key. Check it in Settings › Library & art › Accounts"; return null }
                    403 -> { problem = "Steam says your game details are private. In Steam: Profile › Edit › Privacy › Game details › Public"; return null }
                    else -> { Log.w(TAG, "Steam achievements $appId: HTTP ${e.code}"); return null }
                }
            }
            val schema = try {
                SteamApi.parseSchema(Net.text(SteamApi.schemaUrl(acc.key, appId))) ?: return null
            } catch (e: HttpError) {
                if (e.code == 400) emptyList() else { Log.w(TAG, "Steam schema $appId: HTTP ${e.code}"); return null }
            }
            // Rarity is a public extra: when it can't be had this time, the shares from the last look stay.
            val percents = if (schema.isEmpty()) emptyMap() else runCatching { SteamApi.parsePercentages(Net.text(SteamApi.percentagesUrl(appId))) }.getOrDefault(emptyMap())
                .ifEmpty { before?.achievements?.associate { it.id to it.percent }.orEmpty() }
            val merged = SteamApi.merge(appId, schema, player, percents, System.currentTimeMillis())
            val record = if (merged.gameName.isBlank() && before != null) merged.copy(gameName = before.gameName) else merged
            problem = null
            // A session of this game ended before this look began: what is new since the last record is announced.
            // A look that began before then may have missed the unlocks, so the mark stays for the next one.
            val marked = announce[appId]
            if (marked != null && marked <= startedAt && loaded) {
                announce.remove(appId, marked)
                val fresh = SteamApi.newUnlocks(games[appId], record)
                if (fresh.isNotEmpty()) {
                    val title = record.gameName.ifBlank { Libraries.games.firstOrNull { appIdOf(it) == appId }?.title.orEmpty() }
                    val toasts = SteamApi.announcement(title, fresh)
                    main.post { io.github.zahakj.tandem.ui.SteamUnlocks.show(toasts) }
                }
            }
            games[appId] = record
            dir?.let { d -> runCatching { d.mkdirs(); File(d, "$appId.json").writeText(json.encodeToString(record)) } }
            return record
        } catch (e: PrivateOffline) {
            return null
        } catch (e: Exception) {
            Log.w(TAG, "Steam achievements $appId failed: ${e.message}")
            return null
        } finally {
            inFlight.remove(appId)
        }
    }
}
