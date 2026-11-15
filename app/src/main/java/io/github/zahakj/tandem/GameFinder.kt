package io.github.zahakj.tandem

import io.github.zahakj.tandem.data.ScanCache
import java.io.File
import java.text.Normalizer

/**
 * Which system a folder's name means, generously: "3DS", "n3ds", "Nintendo 3DS", "nintendo-3ds" and "3DS ROMs" are
 * all the 3DS. Case, spaces, punctuation and accents don't count, nor do notes in brackets ("(Digital)"), a trailing
 * "roms" or "games", or a maker in front ("Sony - PlayStation 2", the way No-Intro names its sets). A system answers
 * to its id and folders from platforms.json, its aliases, its short and full names, and a few common nicknames
 * ([NICKNAMES]). Pure.
 */
class SystemNames(platforms: List<Platform>) {
    private val byName: Map<String, List<Platform>> = HashMap<String, List<Platform>>().also { m ->
        // A folder platforms.json gives several systems (an imported system next to a built-in one) is all of
        // theirs, as it always was. Otherwise earlier lists win a clash: what platforms.json says outright beats a
        // display name, which beats a nickname.
        for (p in platforms) for (n in listOf(p.id) + p.folders.filter { !it.startsWith("/") }) {
            val k = squash(n)
            if (k.isNotEmpty()) m[k] = (m[k].orEmpty() + p).distinctBy { it.id }
        }
        val lists: List<(Platform) -> Collection<String>> = listOf(
            { p -> p.aliases },
            { p -> listOf(p.shortName, p.name) },
            { p -> NICKNAMES[p.id].orEmpty() },
        )
        for (names in lists) for (p in platforms) for (n in names(p)) squash(n).takeIf { it.isNotEmpty() }?.let { m.putIfAbsent(it, listOf(p)) }
    }

    /** The system [folderName] means, or null when it means none. */
    fun of(folderName: String): Platform? = allOf(folderName).firstOrNull()

    /** Every system [folderName] means: one, or several when platforms.json gives them the same folder. */
    fun allOf(folderName: String): List<Platform> {
        for (k in keys(folderName)) byName[k]?.let { return it }
        return emptyList()
    }

    companion object {
        /** Words a folder name may end in that say nothing about the system: "3DS ROMs", "gba_games". */
        private val SUFFIXES = listOf("roms", "rom", "games", "game", "isos", "iso")
        /** Makers a name may start with ("Nintendo - Game Boy Advance"), tried only when the whole name means nothing. */
        private val MAKERS = listOf("nintendo", "sony", "sega", "microsoft", "nec", "snk", "bandai", "commodore", "philips", "sharp")
        private val BRACKETS = Regex("""[(\[][^)\]]*[)\]]""")
        private val MARKS = Regex("""\p{M}+""")

        /** Other names people give systems' folders, by system id, on top of what platforms.json lists. */
        val NICKNAMES = mapOf(
            "n3ds" to listOf("new 3DS", "New Nintendo 3DS"),
            "switch" to listOf("NSW"),
            "snes" to listOf("Super NES", "Super Famicom"),
            "gbc" to listOf("Game Boy Colour"),
            "psx" to listOf("PlayStation 1", "PSOne", "PS One"),
            "pcengine" to listOf("PCE", "TurboGrafx", "TurboGrafx 16"),
            "xbox360" to listOf("X360"),
            "zxspectrum" to listOf("Spectrum"),
        )

        /** A name with only its letters and digits, lowercase, accents dropped and "&" as "and": "Pokémon Mini" -> "pokemonmini". */
        fun squash(name: String): String =
            Normalizer.normalize(name.replace("&", "and"), Normalizer.Form.NFD).replace(MARKS, "").lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

        /** The ways a folder name is looked up, in order: as it is, without trailing "roms"/"games", then without a maker in front. */
        fun keys(name: String): List<String> {
            val out = LinkedHashSet<String>()
            var k = squash(name.replace(BRACKETS, " "))
            while (k.isNotEmpty()) {
                out += k
                val suffix = SUFFIXES.firstOrNull { k.length > it.length && k.endsWith(it) } ?: break
                k = k.removeSuffix(suffix)
            }
            for (each in out.toList()) MAKERS.firstOrNull { each.length > it.length && each.startsWith(it) }?.let { out += each.removePrefix(it) }
            return out.toList()
        }

        /**
         * Kinds of file that turn up in any folder (disc images, archives, pictures, documents, programs) or are only
         * part of a game. They are games only inside a folder named for a system, never on their own.
         */
        val EVERYDAY = setOf(
            "zip", "7z", "rar", "gz", "tar", "iso", "bin", "cue", "chd", "img", "mdf", "mds", "nrg", "isz", "ccd", "toc", "ecm",
            "cso", "zso", "m3u", "lst", "dat", "rom", "elf", "exe", "com", "bat", "sh", "app", "apk", "jar", "lnk", "desktop",
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "pdf", "txt", "md", "nfo", "xml", "json", "html", "htm", "ini", "cfg",
            "conf", "log", "db", "sav", "srm", "state", "mp3", "ogg", "wav", "flac", "mp4", "mkv", "webm", "avi", "mov", "m4v",
            "mpg", "mpeg", "epub", "cbz", "cbr", "mobi", "azw3", "djvu", "fb2", "hex", "o", "map", "game", "pdb", "prx", "nca",
            "nso", "rpx",
        )

        /**
         * The kinds of file that mean one system wherever they sit: an extension only one of [platforms] lists (.3ds,
         * .nsp, .gba), or, when several list it, the one whose own id or folder it is (.gb is the Game Boy's, though
         * the Game Boy Color plays it too). [EVERYDAY] kinds never count.
         */
        fun looseExtensions(platforms: List<Platform>): Map<String, Platform> {
            val owners = LinkedHashMap<String, MutableList<Platform>>()
            for (p in platforms) if (p.source == null) for (e in p.extensions) if (e !in EVERYDAY) owners.getOrPut(e) { mutableListOf() } += p
            return owners.mapNotNull { (ext, ps) ->
                val p = ps.distinctBy { it.id }.singleOrNull()
                    ?: ps.filter { o -> o.id == ext || o.folders.any { it.equals(ext, ignoreCase = true) } }.distinctBy { it.id }.singleOrNull()
                p?.let { ext to it }
            }.toMap()
        }
    }
}

/**
 * Finds the games in one game folder, whatever its shape (Library.scan does it for every root):
 * - a folder of system folders, the ROMs layout: each sub-folder whose name means a system ([SystemNames]) is that
 *   system's, walked as ever (its subfolders become folders in Tandem, emulator data is skipped);
 * - one system's own folder, picked directly: when the root's own name means a system, all of it is that system's;
 * - a mix: anything else is walked too, down to [MAX_DEPTH], and a file whose kind means one system
 *   ([SystemNames.looseExtensions]) is that system's game wherever it sits. A folder named for a system on the way
 *   is that system's folder, as at the top.
 * A file is only ever found once: a system's folder is walked for its system alone (or the systems platforms.json
 * gives that folder), never for loose files. Every folder is listed once through [list] (the scan cache),
 * however many systems there are. Pure apart from [list].
 */
class GameFinder(platforms: List<Platform>, private val list: (File) -> ScanCache.Listing) {
    private val files = platforms.filter { it.source == null }
    private val names = SystemNames(files)
    private val loose = SystemNames.looseExtensions(files)
    private val skip = files.flatMap { it.skipDirs }.toSet()

    /** One root's games per system id, each system's in the order the first one found wins, and each system's folders. */
    class Found(val games: Map<String, List<Game>>, val folders: Map<String, List<File>>)

    /** A folder found to be [platform]'s; [rank] orders a system's folders (platforms.json's own names first). */
    private class Claim(val platform: Platform, val dir: File, val rank: Int)

    /**
     * The games in [root]. [tag] is what found games carry as their root (null for the ROMs folder). The ROMs folder
     * ([main]) is never one system's folder itself, whatever its name.
     */
    fun find(root: File, tag: File?, main: Boolean): Found {
        val claims = mutableListOf<Claim>()
        val strays = mutableListOf<Game>()
        val own = if (main) emptyList() else names.allOf(root.name)
        if (own.isNotEmpty()) own.forEach { claims += Claim(it, root, 0) } else look(root, null, 0, tag, claims, strays)
        val games = LinkedHashMap<String, MutableList<Game>>()
        val folders = LinkedHashMap<String, MutableList<File>>()
        for (c in claims.sortedBy { it.rank }) {
            games.getOrPut(c.platform.id) { mutableListOf() } += walk(c.dir, c.platform, null, 0, tag)
            folders.getOrPut(c.platform.id) { mutableListOf() } += c.dir
        }
        for (g in strays) games.getOrPut(g.platform.id) { mutableListOf() } += g
        return Found(games, folders)
    }

    /**
     * Walks a folder that belongs to no system yet: sub-folders named for a system are claimed, files of a kind
     * only one system has are its games, and other sub-folders are walked on (their name becomes the games' folder).
     */
    private fun look(dir: File, group: String?, depth: Int, tag: File?, claims: MutableList<Claim>, strays: MutableList<Game>) {
        val listing = list(dir)
        for (name in listing.files) {
            if (name.startsWith(".")) continue
            val p = loose[name.substringAfterLast('.', "").lowercase()] ?: continue
            val f = File(dir, name)
            strays += Game(p, f, cleanTitle(f.nameWithoutExtension), group, tag)
        }
        for (name in listing.dirs) {
            if (name.startsWith(".") || name.lowercase() in OWN_DIRS) continue
            val f = File(dir, name)
            val systems = names.allOf(name)
            if (systems.isNotEmpty()) {
                // A top-level folder platforms.json names keeps its place (n3ds/ before 3ds/); any other comes after.
                for (system in systems) {
                    val exact = if (depth == 0) system.folders.indexOfFirst { it.equals(name, ignoreCase = true) } else -1
                    claims += Claim(system, f, if (exact >= 0) exact else LATER + claims.size)
                }
                continue
            }
            val gameDir = loose[f.extension.lowercase()]
            if (f.extension.isNotEmpty() && gameDir != null) {
                strays += Game(gameDir, f, cleanTitle(f.nameWithoutExtension), group, tag)
            } else if (depth < MAX_DEPTH && name.lowercase() !in skip) {
                look(f, group ?: name, depth + 1, tag, claims, strays)
            }
        }
    }

    /** The games in one of [platform]'s folders: its own files first, then its subfolders', as folders in Tandem. */
    fun walk(dir: File, platform: Platform, group: String?, depth: Int, tag: File?): List<Game> {
        val found = mutableListOf<Game>()
        val listing = list(dir)
        for (name in listing.files) {
            if (name.startsWith(".")) continue
            val f = File(dir, name)
            if (platform.matches(name)) found += Game(platform, f, cleanTitle(f.nameWithoutExtension), group, tag)
        }
        for (name in listing.dirs) {
            if (name.startsWith(".")) continue
            val f = File(dir, name)
            if (f.extension.isNotEmpty() && f.extension.lowercase() in platform.extensions) {
                // A folder named like a game file is one game (PS3 "Game.ps3" folders, for example).
                found += Game(platform, f, cleanTitle(f.nameWithoutExtension), group, tag)
            } else if (depth < MAX_DEPTH && name.lowercase() !in platform.skipDirs) {
                // Emulators keep their own data next to the games (Azahar's nand/sdmc, for example); skip it.
                found += walk(f, platform, group ?: name, depth + 1, tag)
            }
        }
        return found
    }

    companion object {
        const val MAX_DEPTH = 3
        private const val LATER = 1_000
        /** Tandem's own folders in ROMs and frontends' usual data folders: never games, so never walked for strays. */
        val OWN_DIRS = setOf(
            "tandem", "manuals", "notes", "screenshots", "clips", "downloaded_media", "backups", "backup", "platforms", "themes",
            "assets", "media", "gamelists", "bios", "saves", "states",
        )

        /** "Mario Kart 7 (USA) (En,Fr,Es)" -> "Mario Kart 7". */
        fun cleanTitle(stem: String): String =
            stem.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").trim().ifEmpty { stem }
    }
}
