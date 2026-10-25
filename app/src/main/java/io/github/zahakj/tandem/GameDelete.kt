package io.github.zahakj.tandem

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/*
 * Deleting a game from storage: which files go (DeletePlanner.gameFiles, DeletePlanner.artFiles) and the deleting
 * itself (DeletePlanner.delete). Free of Android, so the file-set rules are unit tested (GameDeleteTest).
 *
 * The rules, all of them about not deleting anything the user didn't mean:
 * - Only ever inside the library roots (the ROMs folder and the systems' own folders) or, for art, the media
 *   folder. Every path is checked where it really is (canonical parent), never by its name alone.
 * - A symbolic link that points outside the roots stops the whole delete. Links are never followed while deleting:
 *   a link inside a folder game is removed as a link, so its target is untouched.
 * - Never a root, a system folder, or a folder that holds other games (or a system folder) inside it.
 * - Multi-file games take their parts with them: a .cue its tracks, a .gdi its tracks, an .m3u its discs (and
 *   their tracks), a .ccd its .img/.sub, an .mds its .mdf. A part another sheet in the same folder still uses
 *   (two playlists sharing a disc) stays.
 */

/** One thing on storage a delete removes: a file, or a folder game with everything in it. */
data class DeleteFile(val file: File, val bytes: Long, val isDir: Boolean = false)

/** What deleting a game's files would remove, or why it can't be done ([refused]). [kept] are parts another game still uses. */
class FilePlan(val files: List<DeleteFile>, val kept: List<File> = emptyList(), val refused: String? = null) {
    val bytes: Long get() = files.sumOf { it.bytes }
    val ok: Boolean get() = refused == null

    companion object {
        fun refuse(why: String) = FilePlan(emptyList(), refused = why)
    }
}

/** What a delete actually did. */
data class DeleteResult(val freed: Long, val deleted: Int, val failed: List<File>)

object DeletePlanner {
    /** Files that list other files a game needs. */
    private val SHEETS = setOf("cue", "gdi", "m3u", "m3u8", "ccd", "mds")

    /** How far sheets are followed (an .m3u lists .cue files, which list .bin files). */
    private const val MAX_DEPTH = 3

    /** Sheets bigger than this aren't sheets; they are never read. */
    private const val MAX_SHEET = 256 * 1024L

    /**
     * Every file that deleting the game at [target] removes. [roots] are the library roots; [protectedDirs] the
     * system folders (and anything else that must never go as a whole); [otherGames] the files and folders of every
     * other game in the library, so a folder holding one is refused.
     */
    fun gameFiles(target: File, roots: List<File>, protectedDirs: List<File>, otherGames: Collection<File>): FilePlan {
        val rootsC = roots.mapNotNull { canon(it) }.distinct()
        if (rootsC.isEmpty()) return FilePlan.refuse("Tandem doesn't know your game folders yet")
        if (!exists(target)) return FilePlan.refuse("The game's file isn't there any more")
        val at = located(target) ?: return FilePlan.refuse("Tandem couldn't work out where the game is")
        if (!inside(at, rootsC)) return FilePlan.refuse("It isn't inside your game folders, so Tandem leaves it alone")
        if (isLink(target)) {
            val to = canon(target)
            if (to == null || !inside(to, rootsC)) return FilePlan.refuse("It's a link to a place outside your game folders, so Tandem leaves it alone")
        }
        val guarded = (protectedDirs.mapNotNull { located(it) } + protectedDirs.mapNotNull { canon(it) } + rootsC).toSet()

        if (isRealDir(target)) {
            if (guarded.any { it == at || isUnder(it, at) }) return FilePlan.refuse("That's a whole system folder, so Tandem won't delete it")
            val others = otherGames.mapNotNull { located(it) }.filter { it != at && isUnder(it, at) }
            if (others.isNotEmpty()) return FilePlan.refuse("Its folder holds other games too, so Tandem won't delete it")
            val (bytes, outside) = walkDir(target, rootsC)
            if (outside != null) return FilePlan.refuse("${outside.name} inside it links to a place outside your game folders, so Tandem leaves it alone")
            return FilePlan(listOf(DeleteFile(target.absoluteFile, bytes, isDir = true)))
        }

        // A file game, with whatever its sheets list.
        val found = LinkedHashMap<File, DeleteFile>()
        found[at] = DeleteFile(target.absoluteFile, sizeOf(target))
        var refused: String? = null
        fun visit(sheet: File, depth: Int) {
            if (depth > MAX_DEPTH || refused != null) return
            for (part in partsOf(sheet)) {
                if (!exists(part) || isRealDir(part)) continue
                val loc = located(part) ?: continue
                if (loc in found) continue
                if (!inside(loc, rootsC)) { refused = "${part.name} is outside your game folders, so Tandem leaves it alone"; return }
                if (isLink(part) && canon(part)?.let { inside(it, rootsC) } != true) {
                    refused = "${part.name} links to a place outside your game folders, so Tandem leaves it alone"; return
                }
                found[loc] = DeleteFile(part.absoluteFile, sizeOf(part))
                if (part.extension.lowercase() in SHEETS) visit(part, depth + 1)
            }
        }
        if (target.extension.lowercase() in SHEETS) visit(target, 1)
        refused?.let { return FilePlan.refuse(it) }

        // Parts another sheet in the same folders still uses stay (and so does what those parts list in turn).
        val used = usedElsewhere(found.keys, at)
        val kept = found.keys.filter { it != at && it in used }
        return FilePlan(found.filterKeys { it !in kept }.values.toList(), kept.mapNotNull { found[it]?.file })
    }

    /**
     * The downloaded art of a game whose file is called [stem]: every file in `<media>/<art id>/<slot>/` named
     * after it (with anything after "__" ignored, as [ArtIndex] reads them). [artDirs] are `<media>/<art id>`.
     * Nothing when another game in the same art folders has the same name ([sharedStems], lowercase), since it
     * shows the same pictures.
     */
    fun artFiles(stem: String, artDirs: List<File>, mediaRoot: File, sharedStems: Set<String>): List<DeleteFile> {
        val want = stem.lowercase()
        if (want in sharedStems) return emptyList()
        val root = canon(mediaRoot) ?: return emptyList()
        val out = LinkedHashMap<File, DeleteFile>()
        for (dir in artDirs) {
            val slots = dir.listFiles()?.filter { isRealDir(it) } ?: continue
            for (slot in slots) for (f in slot.listFiles().orEmpty()) {
                if (artKey(f.name) != want || isRealDir(f)) continue
                val loc = located(f) ?: continue
                if (!inside(loc, listOf(root))) continue
                // A link to somewhere else is only ever removed as a link (see delete), but leave links out anyway.
                if (isLink(f)) continue
                out.putIfAbsent(loc, DeleteFile(f.absoluteFile, sizeOf(f)))
            }
        }
        return out.values.toList()
    }

    /** "Game__2.png" -> "game", "Game.png" -> "game". */
    fun artKey(name: String): String {
        val cut = name.indexOf("__")
        return (if (cut > 0) name.substring(0, cut) else name.substringBeforeLast('.')).lowercase()
    }

    /**
     * Deletes [files], checking each one is still inside [roots] first. Folders are walked without following links,
     * so a link inside one is removed as a link. Blocking.
     */
    fun delete(files: List<DeleteFile>, roots: List<File>): DeleteResult {
        val rootsC = roots.mapNotNull { canon(it) }
        var freed = 0L
        var deleted = 0
        val failed = mutableListOf<File>()
        for (d in files) {
            val loc = located(d.file)
            if (loc == null || !inside(loc, rootsC)) { failed += d.file; continue }
            if (!exists(d.file)) continue
            try {
                if (isRealDir(d.file)) deleteTree(d.file.toPath()) else Files.delete(d.file.toPath())
                freed += d.bytes
                deleted++
            } catch (e: IOException) {
                failed += d.file
            } catch (e: SecurityException) {
                failed += d.file
            }
        }
        return DeleteResult(freed, deleted, failed)
    }

    // --- Sheets ---------------------------------------------------------------------------------------------------

    private val CUE_FILE = Regex("""^\s*FILE\s+(?:"([^"]+)"|(\S+))""", RegexOption.IGNORE_CASE)

    /** The files a sheet lists, resolved next to it. Unreadable or oversized sheets list nothing. */
    fun partsOf(sheet: File): List<File> {
        val dir = sheet.absoluteFile.parentFile ?: return emptyList()
        val ext = sheet.extension.lowercase()
        // Image pairs are by name, not listed inside.
        when (ext) {
            "ccd" -> return listOf("img", "sub").mapNotNull { e -> sibling(dir, sheet.nameWithoutExtension + "." + e) }
            "mds" -> return listOfNotNull(sibling(dir, sheet.nameWithoutExtension + ".mdf"))
        }
        if (!sheet.isFile || sheet.length() > MAX_SHEET) return emptyList()
        val lines = runCatching { sheet.readLines() }.getOrDefault(emptyList()).map { it.trim().removePrefix("﻿") }
        val names = when (ext) {
            "cue" -> lines.mapNotNull { l -> CUE_FILE.find(l)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } } }
            "gdi" -> lines.drop(1).mapNotNull { gdiTrack(it) }
            "m3u", "m3u8" -> lines.filter { it.isNotEmpty() && !it.startsWith("#") }
            else -> emptyList()
        }
        return names.mapNotNull { n -> sibling(dir, n.replace('\\', '/')) }.distinct()
    }

    /** "3 45000 4 2352 \"track 03.bin\" 0" -> "track 03.bin". */
    private fun gdiTrack(line: String): String? {
        val tokens = Regex(""""([^"]*)"|(\S+)""").findAll(line).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
        return tokens.getOrNull(4)?.takeIf { it.isNotBlank() }
    }

    /** [name] next to [dir] (it may go into subfolders, or up with ".."); matched ignoring case when not found as is. */
    private fun sibling(dir: File, name: String): File? {
        if (name.isBlank()) return null
        val f = (if (name.startsWith("/")) File(name) else File(dir, name)).normalize()
        if (exists(f)) return f
        val parent = f.parentFile ?: return null
        return parent.listFiles()?.firstOrNull { it.name.equals(f.name, ignoreCase = true) }
    }

    /**
     * The parts other sheets in the same folders still need: the sheets there that aren't [target] and aren't
     * going themselves, followed through to everything they list.
     */
    private fun usedElsewhere(found: Set<File>, target: File): Set<File> {
        val dirs = found.mapNotNull { it.parentFile }.toSet()
        val start = dirs.flatMap { d -> d.listFiles().orEmpty().toList() }
            .filter { it.isFile && it.extension.lowercase() in SHEETS }
            .mapNotNull { located(it) }
            .filter { it !in found }
        val seen = HashSet<File>()
        val queue = ArrayDeque(start.map { it to 1 })
        while (queue.isNotEmpty()) {
            val (sheet, depth) = queue.removeFirst()
            if (depth > MAX_DEPTH) continue
            for (p in partsOf(sheet)) {
                val loc = located(p) ?: continue
                if (loc == target || !seen.add(loc)) continue
                if (loc.extension.lowercase() in SHEETS) queue.addLast(loc to depth + 1)
            }
        }
        return seen
    }

    // --- Paths ----------------------------------------------------------------------------------------------------

    private fun canon(f: File): File? = runCatching { f.canonicalFile }.getOrNull()

    /** Where the entry itself is: its folder resolved, its own name as is (so a link is judged where it sits). */
    private fun located(f: File): File? {
        val abs = f.absoluteFile.normalize()
        val parent = abs.parentFile ?: return canon(abs)
        return canon(parent)?.let { File(it, abs.name) }
    }

    /** Strictly inside one of [roots] (a root itself isn't inside). */
    private fun inside(f: File, roots: List<File>): Boolean = roots.any { isUnder(f, it) }

    private fun isUnder(f: File, dir: File): Boolean = f.path.startsWith(dir.path.trimEnd('/') + "/")

    private fun isLink(f: File): Boolean = Files.isSymbolicLink(f.toPath())

    private fun exists(f: File): Boolean = Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS)

    /** A real folder: not a link to one. */
    private fun isRealDir(f: File): Boolean = Files.isDirectory(f.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun sizeOf(f: File): Long =
        if (isLink(f)) 0L else runCatching { Files.size(f.toPath()) }.getOrDefault(0L)

    /** The bytes in a folder, and the first link inside it pointing outside [roots] (null when there is none). */
    private fun walkDir(dir: File, roots: List<File>): Pair<Long, File?> {
        var bytes = 0L
        var outside: File? = null
        Files.walkFileTree(dir.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isSymbolicLink) {
                    val to = canon(file.toFile())
                    if (to == null || !inside(to, roots)) { outside = file.toFile(); return FileVisitResult.TERMINATE }
                } else if (attrs.isRegularFile) bytes += attrs.size()
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return bytes to outside
    }

    /** Deletes a folder bottom-up without following links (walkFileTree reports a link as a file). */
    private fun deleteTree(root: Path) {
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file); return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir); return FileVisitResult.CONTINUE
            }
        })
    }
}
