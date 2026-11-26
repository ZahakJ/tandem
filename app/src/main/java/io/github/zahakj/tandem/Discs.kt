package io.github.zahakj.tandem

import io.github.zahakj.tandem.data.GameMeta
import java.io.File

/**
 * Games on several discs, shown as one tile. In a system's folder ([GameFinder.walk]):
 * - an `.m3u` playlist is the game, and the discs it lists are no games of their own;
 * - without one, files whose names differ only in a disc mark ("(Disc 2)", "[CD 2]", "Disk 2 of 3", " CD2" at the
 *   end) in the same folder, of the same kind, are one game: disc 1 is its file.
 * The game's key stays what it was: the playlist's, or disc 1's ([Game.key]). Records the other discs had of their
 * own (play time, favourite, sessions) are moved over once ([merge], applied by [Library.scan]).
 *
 * Launching ([launchFile]): emulators that take a playlist get the `.m3u`; those that can't open one handed over as a
 * shared document ("firstDisc" in platforms.json: Dolphin, ARMSX2, NetherSX2) get disc 1, and swap discs from their
 * own menus. Pure apart from reading playlists.
 */
object Discs {
    const val PLAYLIST = "m3u"

    /** "(Disc 2)", "[CD 2]", "(Disk 02 of 3)": the mark in brackets, anywhere in the name. */
    private val BRACKETED = Regex("""\s*[(\[]\s*(?:disc|disk|cd)\s*[-_]?\s*0*(\d{1,2})(?:\s*(?:of|/)\s*\d{1,2})?\s*[)\]]""", RegexOption.IGNORE_CASE)

    /** "Game CD2", "Game_Disc_2", "Game - Disk 2": the mark without brackets, only at the very end of the name. */
    private val TRAILING = Regex("""[\s_-]+(?:disc|disk|cd)[\s_-]*0*(\d{1,2})$""", RegexOption.IGNORE_CASE)

    /** Disc numbers above this are something else (the "CD32" in an Amiga CD32 name). */
    private const val MOST = 20

    /** A file name's stem without its disc mark, and the disc's number; null when it names no disc. */
    fun discOf(stem: String): Pair<String, Int>? {
        val m = BRACKETED.findAll(stem).lastOrNull() ?: TRAILING.find(stem)
        val n = m?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (n < 1 || n > MOST) return null
        val base = (stem.removeRange(m.range)).replace(Regex("""\s{2,}"""), " ").trim()
        return if (base.isEmpty()) null else base to n
    }

    /** The entries of a playlist's text, in order: every line but blanks and comments, with "\" read as "/". */
    fun entries(text: String): List<String> =
        text.lineSequence().map { it.trim().removePrefix("﻿") }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.replace('\\', '/') }.toList()

    /** The files a playlist [m3u] lists, resolved next to it (an entry may be a full path or in a subfolder). */
    fun resolve(m3u: File, entries: List<String>): List<File> {
        val dir = m3u.parentFile ?: return emptyList()
        return entries.map { e -> if (e.startsWith("/")) File(e) else File(dir, e) }.map { File(it.path.replace("/./", "/")) }.distinct()
    }

    /** Reads a playlist; a missing, unreadable or oversized one lists nothing. */
    fun read(m3u: File): List<String> = runCatching {
        if (!m3u.isFile || m3u.length() > MAX_PLAYLIST) emptyList() else entries(m3u.readText())
    }.getOrDefault(emptyList())

    private const val MAX_PLAYLIST = 64 * 1024L

    /** One game made of several files: [file] stands for it, [discs] in order. */
    class Group(val file: File, val title: String, val discs: List<File>)

    /** What one folder's game files come to: games standing alone, multi-disc games, and files hidden inside one. */
    class Sorted(val single: List<File>, val groups: List<Group>, val hidden: Set<File>)

    /**
     * Sorts the game files [files] (all in [dir], in listing order) into single games and multi-disc ones.
     * [readPlaylist] gives a playlist's entries. A playlist that lists nothing stays a game of its own (its discs
     * are then grouped by their names, if they can be). [hidden] also holds files in subfolders a playlist lists,
     * for the walk to skip there.
     */
    fun sort(dir: File, files: List<String>, readPlaylist: (File) -> List<String>, title: (String) -> String): Sorted {
        val hidden = LinkedHashSet<File>()
        val groups = ArrayList<Group>()
        val byFile = LinkedHashMap<String, Group>()
        for (name in files) {
            if (!name.substringAfterLast('.', "").equals(PLAYLIST, ignoreCase = true)) continue
            val m3u = File(dir, name)
            val discs = resolve(m3u, readPlaylist(m3u)).filter { it != m3u }
            if (discs.isEmpty()) continue
            hidden += discs
            val g = Group(m3u, title(m3u.nameWithoutExtension), discs)
            byFile[name] = g
        }
        val left = files.filter { it !in byFile && File(dir, it) !in hidden }
        // Disc sets by name: same folder, same kind of file, same name around the mark.
        val sets = LinkedHashMap<Pair<String, String>, MutableList<Pair<Int, String>>>()
        for (name in left) {
            val stem = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext == PLAYLIST) continue
            val (base, n) = discOf(stem) ?: continue
            sets.getOrPut(base.lowercase() to ext) { mutableListOf() } += n to name
        }
        val grouped = HashMap<String, Group>()
        for ((key, members) in sets) {
            if (members.map { it.first }.distinct().size < 2) continue
            val ordered = members.sortedWith(compareBy({ it.first }, { it.second }))
            val discs = ordered.map { File(dir, it.second) }
            val base = discOf(ordered.first().second.substringBeforeLast('.'))?.first ?: key.first
            val g = Group(discs.first(), title(base), discs)
            grouped[ordered.first().second] = g
            for ((_, n) in ordered.drop(1)) hidden += File(dir, n)
        }
        val single = ArrayList<File>()
        for (name in files) {
            val f = File(dir, name)
            when {
                name in byFile -> groups += byFile.getValue(name)
                name in grouped -> groups += grouped.getValue(name)
                f !in hidden -> single += f
            }
        }
        return Sorted(single, groups, hidden)
    }

    /** The playlist Tandem would write for a set of discs found by name ("Final Fantasy VII (USA).m3u"), next to them. */
    fun playlistFor(g: Game): File? {
        val first = g.discs.firstOrNull() ?: return null
        if (g.file.extension.equals(PLAYLIST, ignoreCase = true)) return null
        val base = discOf(first.nameWithoutExtension)?.first ?: return null
        return File(first.parentFile, "$base.$PLAYLIST")
    }

    /** The first line of a playlist Tandem wrote: a comment emulators skip, so it can be told from the user's own. */
    const val MADE_BY = "# Made by Tandem"

    /** A playlist's text for [discs], each named relative to [m3u]'s folder. */
    fun playlistText(m3u: File, discs: List<File>): String {
        val dir = m3u.parentFile
        return buildString {
            appendLine(MADE_BY)
            for (d in discs) appendLine(if (d.parentFile == dir) d.name else d.relativeTo(dir ?: File("/")).path)
        }
    }

    /**
     * The file to hand [spec] for [game]: disc 1 instead of the playlist, for an emulator that can't open one
     * ([LaunchSpec.firstDisc], or one that doesn't take .m3u files); null to launch the game's own file.
     */
    fun launchFile(game: Game, spec: LaunchSpec): File? {
        if (game.discs.isEmpty() || !game.file.extension.equals(PLAYLIST, ignoreCase = true)) return null
        if (!spec.firstDisc && spec.accepts(game.file.name)) return null
        return game.discs.firstOrNull()
    }

    // --- Moving records over --------------------------------------------------------------------------------------

    /** The keys whose records belong to multi-disc game [g] now: each disc's, and for discs grouped by name, the playlist's. */
    fun formerKeys(g: Game): List<String> {
        if (g.discs.isEmpty()) return emptyList()
        val own = g.discs.map { "${g.platform.id}/${it.name}" } +
            listOfNotNull(playlistFor(g)?.let { "${g.platform.id}/${it.name}" })
        return own.distinct().filter { it != g.key }
    }

    /**
     * [into] (the game's own record, or null) with [parts] (the discs' records) folded in: play counted once for all,
     * the last time played, the first time seen, a favourite if any was. Its own details win; a part's fill only what
     * it lacks. Hidden only if it was, or, with no record of its own, if every part was (a disc tile hidden to tidy
     * the duplicates doesn't hide the game).
     */
    fun merge(into: GameMeta?, parts: List<GameMeta>): GameMeta {
        var m = into ?: GameMeta()
        for (p in parts) {
            m = m.copy(
                title = m.title ?: p.title, description = m.description ?: p.description,
                genres = m.genres.ifEmpty { p.genres }, developer = m.developer ?: p.developer, publisher = m.publisher ?: p.publisher,
                year = m.year ?: p.year, players = m.players ?: p.players, rating = m.rating ?: p.rating,
                emulator = m.emulator ?: p.emulator, screen = m.screen ?: p.screen, companion = m.companion ?: p.companion,
                box = m.box ?: p.box, hero = m.hero ?: p.hero, logo = m.logo ?: p.logo, screenshot = m.screenshot ?: p.screenshot,
                titleScreen = m.titleScreen ?: p.titleScreen, video = m.video ?: p.video, timeToBeat = m.timeToBeat ?: p.timeToBeat,
                favorite = m.favorite || p.favorite,
                playCount = m.playCount + p.playCount, playSeconds = m.playSeconds + p.playSeconds,
                lastPlayed = maxOf(m.lastPlayed, p.lastPlayed),
                firstSeen = listOf(m.firstSeen, p.firstSeen).filter { it > 0 }.minOrNull() ?: 0L,
                raId = m.raId ?: p.raId, coverUrl = m.coverUrl ?: p.coverUrl,
                status = m.status ?: p.status, statusAt = if (m.status != null) m.statusAt else p.statusAt,
                finishedAt = listOf(m.finishedAt, p.finishedAt).filter { it > 0 }.minOrNull() ?: 0L,
                perf = m.perf ?: p.perf, manual = m.manual ?: p.manual,
                companionApp = m.companionApp ?: p.companionApp,
                lockedFields = m.lockedFields + p.lockedFields,
            )
        }
        val hidden = into?.hidden ?: (parts.isNotEmpty() && parts.all { it.hidden })
        return m.copy(hidden = hidden)
    }

    /**
     * What moving records over changes: per game key, its merged record and the keys folded into it. Only games one
     * of whose former keys still has a record ([meta]) or a session ([sessionKeys]), so a scan after the first does
     * nothing.
     */
    fun moves(games: List<Game>, meta: Map<String, GameMeta>, sessionKeys: Set<String>): Map<String, Pair<GameMeta?, List<String>>> {
        val out = LinkedHashMap<String, Pair<GameMeta?, List<String>>>()
        // A key some game still has (a playlist that couldn't be read just now) keeps its record.
        val live = games.mapTo(HashSet()) { it.key }
        for (g in games) {
            val former = formerKeys(g).filter { it !in live && (it in meta || it in sessionKeys) }
            if (former.isEmpty()) continue
            val parts = former.mapNotNull { meta[it] }
            out[g.key] = (if (parts.isEmpty()) null else merge(meta[g.key], parts)) to former
        }
        return out
    }
}
