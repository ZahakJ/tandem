package io.github.zahakj.tandem

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A RetroAchievements game's achievements as Tandem lists them (Y › Achievements, and the in-game page on the bottom
 * screen): reading RetroAchievements' answers, the filters and sorts, and the community comments (hints) fetched for
 * one achievement when asked. Pure, apart from the fetch handed to [Hints], so it is unit tested.
 */
object RaList {
    /** What the list shows: every achievement, the ones still to get, the ones got, or the ones that can be missed. */
    enum class Filter { All, Locked, Unlocked, Missable }

    /** The set's own order, the rarest first, or the most points first. */
    enum class Sort { Default, Rarity, Points }

    /** RetroAchievements' type for an achievement that can be missed for good in a playthrough. */
    const val MISSABLE = "missable"

    fun filter(list: List<RetroAchievements.Achievement>, f: Filter): List<RetroAchievements.Achievement> = when (f) {
        Filter.All -> list
        Filter.Locked -> list.filter { !it.earned }
        Filter.Unlocked -> list.filter { it.earned }
        Filter.Missable -> list.filter { it.type == MISSABLE }
    }

    /** [list] in [s] order; ties keep the set's order. Rarity: fewest players first, unknown rarity last. */
    fun sort(list: List<RetroAchievements.Achievement>, s: Sort, players: Int): List<RetroAchievements.Achievement> {
        val byOrder = compareBy<RetroAchievements.Achievement>({ it.order }, { it.id })
        return when (s) {
            Sort.Default -> list.sortedWith(byOrder)
            Sort.Points -> list.sortedWith(compareByDescending<RetroAchievements.Achievement> { it.points }.then(byOrder))
            Sort.Rarity -> list.sortedWith(compareBy<RetroAchievements.Achievement> { share(it, players) ?: 2f }.then(byOrder))
        }
    }

    /** What a list shows: [list] filtered, then sorted. */
    fun shown(list: List<RetroAchievements.Achievement>, f: Filter, s: Sort, players: Int) = sort(filter(list, f), s, players)

    /** The share of the game's players who have [a] (0..1), or null when RetroAchievements didn't say. */
    fun share(a: RetroAchievements.Achievement, players: Int): Float? =
        if (players <= 0) null else (a.awarded.toFloat() / players).coerceIn(0f, 1f)

    /** How many of [list] pass each filter, for the chips. */
    fun counts(list: List<RetroAchievements.Achievement>): Map<Filter, Int> = Filter.entries.associateWith { filter(list, it).size }

    /** The next filter after [f], [by] steps along (wrapping), for L1 / R1. */
    fun step(f: Filter, by: Int): Filter = Filter.entries[Math.floorMod(f.ordinal + by, Filter.entries.size)]

    fun next(s: Sort): Sort = Sort.entries[(s.ordinal + 1) % Sort.entries.size]

    // --- Reading RetroAchievements -----------------------------------------------------------------------------------

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * API_GetGameInfoAndUserProgress for game [id]: the set, the user's progress and each achievement's type and how
     * many have it. Null when it doesn't read. "Achievements" is an object keyed by id, or an empty array.
     */
    fun parseProgress(id: Int, text: String, badgeBase: String): RetroAchievements.Progress? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        val items = when (val a = o["Achievements"]) {
            is JsonObject -> a.values.mapNotNull { it as? JsonObject }
            is JsonArray -> a.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
        val list = items.map { x ->
            val earnedAt = x["DateEarned"].str().orEmpty()
            val hardAt = x["DateEarnedHardcore"].str().orEmpty()
            RetroAchievements.Achievement(
                title = x["Title"].str().orEmpty(),
                description = x["Description"].str().orEmpty(),
                points = x["Points"].int(),
                badgeUrl = "$badgeBase/Badge/${x["BadgeName"].str().orEmpty()}.png",
                earned = earnedAt.isNotBlank() || hardAt.isNotBlank(),
                earnedHardcore = hardAt.isNotBlank(),
                order = x["DisplayOrder"].int(),
                id = x["ID"].int(),
                type = x["type"].str().orEmpty().lowercase(),
                awarded = x["NumAwarded"].int(),
                awardedHardcore = x["NumAwardedHardcore"].int(),
                earnedAt = hardAt.ifBlank { earnedAt },
            )
        }.sortedWith(compareBy({ it.order }, { it.id }))
        val earned = list.filter { it.earned }
        RetroAchievements.Progress(
            raId = id,
            title = o["Title"].str().orEmpty(),
            total = o["NumAchievements"].int().takeIf { it > 0 } ?: list.size,
            earned = o["NumAwardedToUser"].int().takeIf { it > 0 } ?: earned.size,
            earnedHardcore = earned.any { it.earnedHardcore },
            achievements = list,
            players = o["NumDistinctPlayers"].int().takeIf { it > 0 } ?: o["NumDistinctPlayersCasual"].int(),
            points = list.sumOf { it.points },
            earnedPoints = earned.sumOf { it.points },
            hardcoreCount = earned.count { it.earnedHardcore },
            hardcorePoints = earned.filter { it.earnedHardcore }.sumOf { it.points },
        )
    }.getOrNull()

    /** One comment on an achievement: who wrote it, when (RetroAchievements' text), and what. */
    data class Comment(val user: String, val at: String, val text: String)

    /** The comments loaded so far for one achievement, and how many there are in all. */
    data class Page(val comments: List<Comment>, val total: Int) {
        val more: Boolean get() = comments.size < total
    }

    /**
     * API_GetComments for an achievement: its comments, oldest first, without RetroAchievements' own notes (the
     * "Server" user logs edits). Null when it doesn't read.
     */
    fun parseComments(text: String): Page? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        val results = (o["Results"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val all = results.map { r -> Comment(r["User"].str().orEmpty(), r["Submitted"].str().orEmpty(), unescape(r["CommentText"].str().orEmpty()).trim()) }
        val kept = all.filter { it.text.isNotBlank() && !it.user.equals("Server", ignoreCase = true) }
        // The total counts the dropped notes too; take them off so "More" doesn't promise what never comes.
        val total = (o["Total"].int().takeIf { it > 0 } ?: all.size) - (all.size - kept.size)
        Page(kept, total.coerceAtLeast(kept.size))
    }.getOrNull()

    private fun unescape(s: String): String =
        s.replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonElement?.int(): Int = str()?.toDoubleOrNull()?.toInt() ?: 0

    // --- Hints, on demand ----------------------------------------------------------------------------------------------

    /**
     * Community hints for achievements: an achievement's comments are fetched only when [load] is called for it (A or
     * a tap on that achievement), a page at a time ([more]), and kept for the session. Nothing else here fetches, so
     * opening a list, scrolling it or showing a game never asks for comments. [fetch] gets (achievement id, offset,
     * count) and answers with RetroAchievements' text, or null when it can't (signed out, private mode, offline).
     */
    class Hints(private val pageSize: Int = 20, private val fetch: suspend (id: Int, offset: Int, count: Int) -> String?) {
        private val pages = HashMap<Int, Page>()

        /** What is already loaded for [id]; never fetches. */
        @Synchronized fun cached(id: Int): Page? = pages[id]

        /** The first page of [id]'s comments: the one loaded before, or fetched now. Null when it couldn't be fetched. */
        suspend fun load(id: Int): Page? {
            cached(id)?.let { return it }
            val page = fetch(id, 0, pageSize)?.let(::parseComments) ?: return null
            synchronized(this) { pages[id] = page }
            return page
        }

        /** The next page of [id]'s comments added to what was loaded; what was loaded when there's no more or it fails. */
        suspend fun more(id: Int): Page? {
            val have = cached(id) ?: return load(id)
            if (!have.more) return have
            val next = fetch(id, have.comments.size, pageSize)?.let(::parseComments) ?: return have
            val merged = Page((have.comments + next.comments).distinct(), maxOf(have.total, next.total))
            // A page that adds nothing ends the list, so More can't spin.
            val done = if (merged.comments.size == have.comments.size) merged.copy(total = merged.comments.size) else merged
            synchronized(this) { pages[id] = done }
            return done
        }

        @Synchronized fun clear() = pages.clear()
    }
}
