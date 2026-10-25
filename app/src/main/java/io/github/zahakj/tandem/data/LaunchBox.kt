package io.github.zahakj.tandem.data

import org.xmlpull.v1.XmlPullParser
import java.io.BufferedWriter
import java.io.File
import java.text.Normalizer

/*
 * The LaunchBox Games Database, offline.
 *
 * LaunchBox publishes its whole games database as one file (Metadata.zip, about 100 MB, holding a ~500 MB
 * Metadata.xml). Tandem reads that XML once as it downloads, streaming, and keeps a small index of just the systems
 * the user has, one folder per platform:
 *
 *   names.tsv   id, match key, 1 for the main name / 0 for an alternate name
 *   games.tsv   id, name, year, max players, developer, publisher, genres, rating (0..5), video URL
 *   text.tsv    id, overview
 *   images.tsv  id, type code (see [LbArt]), region, file name on images.launchbox-app.com
 *
 * Looking games up then needs no network at all, and nothing about the user's games ever leaves the device.
 * This file is plain Kotlin (no Android types) so the JVM unit tests can parse a sample of the XML.
 */

/** Picture types Tandem keeps from LaunchBox, with a one-letter code for the index. */
enum class LbArt(val code: Char, val type: String) {
    BoxFront('B', "Box - Front"),
    BoxRebuilt('R', "Box - Front - Reconstructed"),
    BoxFanart('F', "Fanart - Box - Front"),
    Logo('L', "Clear Logo"),
    Background('H', "Fanart - Background"),
    Title('T', "Screenshot - Game Title"),
    Gameplay('S', "Screenshot - Gameplay");

    companion object {
        private val byType = entries.associateBy { it.type }
        private val byCode = entries.associateBy { it.code }
        fun ofType(t: String?): LbArt? = t?.let { byType[it] }
        fun ofCode(c: Char): LbArt? = byCode[c]
    }
}

data class LbImage(val art: LbArt, val region: String, val file: String) {
    val url: String get() = "https://images.launchbox-app.com/$file"
}

/** One game from the index, with everything Tandem uses. */
data class LbGame(
    val id: Int,
    val name: String,
    val year: Int?,
    val players: String?,
    val developer: String?,
    val publisher: String?,
    val genres: List<String>,
    /** 0..1 (LaunchBox's community rating is 0..5). */
    val rating: Float?,
    val overview: String?,
    /** A direct video file (Steam trailers); YouTube links are left out because they can't be saved. */
    val video: String?,
    val images: List<LbImage>,
)

/** What to look up: [key] identifies the caller's game, [platform] is a LaunchBox platform name. */
data class LbQuery(val key: String, val platform: String, val titles: List<String>)

/**
 * Title matching keys: "The Legend of Zelda: Breath of the Wild (USA) [v1.6]" -> "legend of zelda breath of wild".
 * Punctuation, accents, tags in brackets, dump leftovers ("v1.2.0", "+ Update", "NSP") don't count, and Roman
 * numerals from II up read as numbers, so "Hades II" and "Hades 2" are the same key.
 */
object LbNames {
    private val brackets = Regex("""\s*[(\[{][^)\]}]*[)\]}]""")
    private val marks = Regex("\\p{M}+")
    private val nonWord = Regex("[^a-z0-9]+")
    private val stop = setOf("the", "a", "an", "of")
    /** Dump-name leftovers: "+ 3 DLC", version numbers, "Update", container formats. Lowercased input. */
    private val dumpNoise = Regex(
        """\+\s*\d*\s*(dlcs?|updates?)(?![a-z0-9])|(?<![a-z0-9])(v\d+(\.\d+)*|updates?|dlcs?|nsp|nsz|xci|xcz)(?![a-z0-9])""",
    )
    /** Edition words a file name may add or leave out ("Deluxe", "Game of the Year Edition"…), for [loose]. */
    private val editions = setOf(
        "edition", "deluxe", "definitive", "complete", "remastered", "remaster", "goty", "special", "anniversary",
        "enhanced", "hd", "collectors",
    )
    private val editionPhrases = listOf("game of year", "directors cut", "for nintendo switch", "nintendo switch")
    // Single letters (I, V, X) stay letters: "Pokémon X" and "Mega Man X" aren't numbered.
    private val roman = mapOf(
        "ii" to "2", "iii" to "3", "iv" to "4", "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9",
        "xi" to "11", "xii" to "12", "xiii" to "13", "xiv" to "14", "xv" to "15", "xvi" to "16",
    )

    fun tokens(s: String): List<String> {
        val noTags = s.replace(brackets, " ").ifBlank { s }
        val plain = Normalizer.normalize(noTags, Normalizer.Form.NFD).replace(marks, "").lowercase()
        val clean = plain.replace(dumpNoise, " ").ifBlank { plain }
        return clean.replace("&", " and ").replace("'", "").replace("’", "").replace(nonWord, " ").trim()
            .split(' ').filter { it.isNotEmpty() && it !in stop }.map { roman[it] ?: it }
    }

    fun key(s: String): String = tokens(s).joinToString(" ")

    /**
     * A key from an index written by an older Tandem, brought up to date with [key]'s rules that work word by word
     * (numerals), so a new Tandem still finds those games without downloading the database again.
     */
    fun rekey(stored: String): String =
        stored.split(' ').filter { it.isNotEmpty() && it !in stop }.joinToString(" ") { roman[it] ?: it }

    /**
     * [key] without edition words, for a second try when no exact name matched: "Celeste" finds
     * "Celeste: Definitive Edition" and the other way round. Null when nothing is left (the title was only those words).
     */
    fun loose(key: String): String? {
        var k = " $key "
        for (p in editionPhrases) k = k.replace(" $p ", " ")
        return k.trim().split(' ').filter { it.isNotEmpty() && it !in editions }.joinToString(" ").takeIf { it.isNotEmpty() }
    }

    /** Dice similarity of two token sets, 0..1. */
    fun dice(a: Set<String>, b: Set<String>): Float =
        if (a.isEmpty() || b.isEmpty()) 0f else 2f * a.count { it in b } / (a.size + b.size)
}

internal object Tsv {
    fun esc(s: String?): String {
        if (s == null) return ""
        if (s.none { it == '\t' || it == '\n' || it == '\r' || it == '\\' }) return s
        val b = StringBuilder(s.length + 8)
        for (c in s) when (c) {
            '\\' -> b.append("\\\\")
            '\t' -> b.append("\\t")
            '\n' -> b.append("\\n")
            '\r' -> {}
            else -> b.append(c)
        }
        return b.toString()
    }

    fun unesc(s: String): String {
        if ('\\' !in s) return s
        val b = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 't' -> b.append('\t'); 'n' -> b.append('\n'); else -> b.append(s[i + 1]) }
                i += 2
            } else { b.append(c); i++ }
        }
        return b.toString()
    }

    fun split(line: String): List<String> = line.split('\t')
}

data class LbBuildStats(val games: Int, val images: Int, val platforms: Set<String>)

/**
 * Turns a streaming Metadata.xml into the index in [outDir], keeping only games on [wanted] platforms.
 * Runs in constant memory apart from an id -> platform table; [tick] is called now and then (for progress and
 * to let the caller cancel by throwing).
 */
class LbIndexBuilder(private val outDir: File, private val wanted: Set<String>) {
    private class Slot(val dir: File) {
        private val writers = HashMap<String, BufferedWriter>()
        fun out(name: String): BufferedWriter = writers.getOrPut(name) { dir.mkdirs(); File(dir, name).bufferedWriter(bufferSize = 64 * 1024) }
        fun close() = writers.values.forEach { runCatching { it.close() } }
    }

    private val platforms = wanted.toList()
    private val slots = arrayOfNulls<Slot>(platforms.size)

    /** Per database id: 0 not seen yet, -1 on a platform we skip, n = platform n-1. */
    private var owner = ShortArray(1 shl 19)
    private var games = 0
    private var images = 0
    private val pending = ArrayList<Pair<Int, String>>()

    private fun slot(i: Int): Slot = slots[i] ?: Slot(File(outDir, dirName(platforms[i]))).also { slots[i] = it }

    private fun setOwner(id: Int, v: Short) {
        if (id < 0) return
        if (id >= owner.size) owner = owner.copyOf(maxOf(id + 1, owner.size * 2))
        owner[id] = v
    }

    private fun ownerOf(id: Int): Short = if (id in owner.indices) owner[id] else 0

    // Pictures already kept for the current game, per type, so a game with 60 screenshots doesn't bloat the index.
    private var lastImageId = -1
    private val perType = IntArray(LbArt.entries.size)

    fun parse(p: XmlPullParser, tick: () -> Unit = {}): LbBuildStats {
        val fields = HashMap<String, String>()
        var record: String? = null
        var field: String? = null
        val text = StringBuilder()
        var n = 0
        try {
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.depth) {
                        2 -> { record = p.name; fields.clear() }
                        // Only the fields the index keeps: the text of the others is never even made into strings,
                        // which saves most of the garbage half a gigabyte of XML would make.
                        3 -> { field = p.name.takeIf { it in KEPT }; text.setLength(0) }
                    }
                    XmlPullParser.TEXT, XmlPullParser.ENTITY_REF, XmlPullParser.CDSECT ->
                        if (field != null && p.depth == 3) p.text?.let { text.append(it) }
                    XmlPullParser.END_TAG -> when (p.depth) {
                        3 -> { field?.let { fields[it] = text.toString().trim() }; field = null }
                        2 -> {
                            when (record) {
                                "Game" -> game(fields)
                                "GameAlternateName" -> alternate(fields)
                                "GameImage" -> image(fields)
                            }
                            record = null
                            if (++n % 2000 == 0) tick()
                        }
                    }
                }
                ev = p.next()
            }
            // Names and pictures that came before their game (the dump lists games first, but be safe).
            for ((id, line) in pending) {
                val o = ownerOf(id)
                if (o > 0) slot(o - 1).out(if (line[0] == 'A') "names.tsv" else "images.tsv").apply { write(line.substring(1)); newLine() }
            }
        } finally {
            slots.forEach { it?.close() }
        }
        val present = platforms.filterIndexed { i, _ -> slots[i] != null }.toSet()
        return LbBuildStats(games, images, present)
    }

    private fun game(f: Map<String, String>) {
        val id = f["DatabaseID"]?.toIntOrNull() ?: return
        val name = f["Name"]?.takeIf { it.isNotEmpty() } ?: return
        val platform = f["Platform"]
        val i = platforms.indexOf(platform)
        if (i < 0) { setOwner(id, -1); return }
        setOwner(id, (i + 1).toShort())
        val s = slot(i)
        games++
        val year = f["ReleaseYear"]?.toIntOrNull() ?: f["ReleaseDate"]?.take(4)?.toIntOrNull()
        val video = f["VideoURL"]?.takeIf { v -> v.startsWith("http") && !v.contains("youtu") }
        s.out("games.tsv").apply {
            write(listOf(
                id.toString(), Tsv.esc(name), year?.toString().orEmpty(), Tsv.esc(f["MaxPlayers"]), Tsv.esc(f["Developer"]),
                Tsv.esc(f["Publisher"]), Tsv.esc(f["Genres"]), Tsv.esc(f["CommunityRating"]), Tsv.esc(video),
            ).joinToString("\t"))
            newLine()
        }
        s.out("names.tsv").apply { write("$id\t${LbNames.key(name)}\t1"); newLine() }
        f["Overview"]?.takeIf { it.isNotBlank() }?.let { s.out("text.tsv").apply { write("$id\t${Tsv.esc(it)}"); newLine() } }
    }

    private fun alternate(f: Map<String, String>) {
        val id = f["DatabaseID"]?.toIntOrNull() ?: return
        val key = f["AlternateName"]?.let(LbNames::key)?.takeIf { it.isNotEmpty() } ?: return
        val line = "$id\t$key\t0"
        when (val o = ownerOf(id)) {
            0.toShort() -> pending += id to "A$line"
            (-1).toShort() -> {}
            else -> slot(o - 1).out("names.tsv").apply { write(line); newLine() }
        }
    }

    private fun image(f: Map<String, String>) {
        val id = f["DatabaseID"]?.toIntOrNull() ?: return
        val art = LbArt.ofType(f["Type"]) ?: return
        val file = f["FileName"]?.takeIf { it.isNotEmpty() && '\t' !in it } ?: return
        if (id != lastImageId) { lastImageId = id; perType.fill(0) }
        if (++perType[art.ordinal] > 4) return
        val line = "$id\t${art.code}\t${Tsv.esc(f["Region"])}\t$file"
        when (val o = ownerOf(id)) {
            0.toShort() -> pending += id to "I$line"
            (-1).toShort() -> {}
            else -> { slot(o - 1).out("images.tsv").apply { write(line); newLine() }; images++ }
        }
    }

    companion object {
        /** Fields of Metadata.xml records that [game], [alternate] and [image] read. */
        private val KEPT = hashSetOf(
            "DatabaseID", "Name", "Platform", "ReleaseYear", "ReleaseDate", "MaxPlayers", "Developer", "Publisher", "Genres",
            "CommunityRating", "VideoURL", "Overview", "AlternateName", "Type", "FileName", "Region",
        )

        /** A platform's folder name in the index. */
        fun dirName(platform: String): String = platform.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}

/** Reads an index written by [LbIndexBuilder]. Every call reads from disk: call off the main thread. */
class LbIndex(private val dir: File) {
    fun has(platform: String): Boolean = File(dir, "${LbIndexBuilder.dirName(platform)}/names.tsv").isFile

    private class Name(val id: Int, val tokens: Set<String>, val main: Boolean)

    /** Finds the best game for each query. Queries on platforms the index doesn't have are skipped. */
    fun find(queries: List<LbQuery>): Map<String, LbGame> {
        val out = HashMap<String, LbGame>()
        for ((platform, qs) in queries.groupBy { it.platform }) {
            val pdir = File(dir, LbIndexBuilder.dirName(platform))
            val namesFile = File(pdir, "names.tsv")
            if (!namesFile.isFile) continue
            val byKey = HashMap<String, MutableList<Name>>()
            val all = ArrayList<Name>()
            namesFile.forEachLine { line ->
                val c = Tsv.split(line)
                if (c.size < 3) return@forEachLine
                val id = c[0].toIntOrNull() ?: return@forEachLine
                val key = LbNames.rekey(c[1])
                val n = Name(id, key.split(' ').filter(String::isNotEmpty).toSet(), c[2] == "1")
                byKey.getOrPut(key) { mutableListOf() } += n
                all += n
            }
            var byWord: HashMap<String, MutableList<Name>>? = null
            var byLoose: HashMap<String, MutableList<Name>>? = null
            val matched = HashMap<String, Int>()
            for (q in qs) {
                val keys = q.titles.map(LbNames::key).filter { it.isNotEmpty() }.distinct()
                val exact = keys.flatMap { byKey[it].orEmpty() }
                val pick = exact.firstOrNull { it.main } ?: exact.firstOrNull() ?: run {
                    // The same name give or take edition words ("Deluxe", "Definitive Edition").
                    val loose = byLoose ?: HashMap<String, MutableList<Name>>().also { m ->
                        for ((k, ns) in byKey) LbNames.loose(k)?.let { l -> m.getOrPut(l) { mutableListOf() } += ns }
                        byLoose = m
                    }
                    val near = keys.mapNotNull(LbNames::loose).flatMap { loose[it].orEmpty() }
                    near.firstOrNull { it.main } ?: near.firstOrNull()
                } ?: run {
                    // No exact name: the closest title by shared words, if it's close enough.
                    val words = byWord ?: HashMap<String, MutableList<Name>>().also { m ->
                        for (n in all) for (t in n.tokens) m.getOrPut(t) { mutableListOf() } += n
                        byWord = m
                    }
                    var best: Name? = null
                    var bestScore = 0f
                    for (k in keys) {
                        val t = k.split(' ').toSet()
                        for (n in t.flatMap { words[it].orEmpty() }.distinct()) {
                            val s = LbNames.dice(t, n.tokens) + if (n.main) 0.001f else 0f
                            if (s > bestScore) { bestScore = s; best = n }
                        }
                    }
                    best?.takeIf { bestScore >= 0.8f }
                }
                if (pick != null) matched[q.key] = pick.id
            }
            if (matched.isEmpty()) continue
            val ids = matched.values.toHashSet()
            val rows = HashMap<Int, List<String>>()
            File(pdir, "games.tsv").takeIf { it.isFile }?.forEachLine { line ->
                val id = line.substringBefore('\t').toIntOrNull()
                if (id != null && id in ids) rows[id] = Tsv.split(line)
            }
            val texts = HashMap<Int, String>()
            File(pdir, "text.tsv").takeIf { it.isFile }?.forEachLine { line ->
                val id = line.substringBefore('\t').toIntOrNull()
                if (id != null && id in ids) texts[id] = Tsv.unesc(line.substringAfter('\t'))
            }
            val pics = HashMap<Int, MutableList<LbImage>>()
            File(pdir, "images.tsv").takeIf { it.isFile }?.forEachLine { line ->
                val id = line.substringBefore('\t').toIntOrNull()
                if (id == null || id !in ids) return@forEachLine
                val c = Tsv.split(line)
                if (c.size < 4) return@forEachLine
                val art = c[1].firstOrNull()?.let(LbArt::ofCode) ?: return@forEachLine
                pics.getOrPut(id) { mutableListOf() } += LbImage(art, Tsv.unesc(c[2]), c[3])
            }
            for ((key, id) in matched) {
                val r = rows[id] ?: continue
                fun col(i: Int) = r.getOrNull(i)?.let(Tsv::unesc)?.takeIf { it.isNotBlank() }
                out[key] = LbGame(
                    id = id,
                    name = col(1) ?: continue,
                    year = col(2)?.toIntOrNull(),
                    players = col(3)?.takeIf { it != "0" },
                    developer = col(4),
                    publisher = col(5),
                    genres = col(6)?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                    rating = col(7)?.toFloatOrNull()?.let { (it / 5f).coerceIn(0f, 1f) },
                    overview = texts[id],
                    video = col(8),
                    images = pics[id].orEmpty(),
                )
            }
        }
        return out
    }

    companion object {
        /** LaunchBox region names for each of Tandem's regions, best first. */
        fun regions(pref: String): List<String> = when (pref) {
            "us" -> listOf("North America", "United States", "Canada")
            "eu" -> listOf("Europe", "United Kingdom", "Germany", "France", "Spain", "Italy", "Australia", "The Netherlands")
            "jp" -> listOf("Japan")
            else -> listOf("World")
        }

        /** The best picture of [arts] (in order of preference) for a region code ("us", "eu", "jp", "wor"). */
        fun pick(g: LbGame, arts: List<LbArt>, region: String): List<LbImage> {
            val order = regions(region) + listOf("World", "") + listOf("North America", "United States", "Europe", "United Kingdom")
            val out = ArrayList<LbImage>()
            for (a in arts) {
                val of = g.images.filter { it.art == a }
                out += of.sortedBy { img -> order.indexOf(img.region).let { if (it < 0) order.size else it } }
            }
            return out
        }
    }
}
