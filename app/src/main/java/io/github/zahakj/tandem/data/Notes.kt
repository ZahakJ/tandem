package io.github.zahakj.tandem.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * Notes (ui/NotesApp.kt): plain Markdown files the user owns, so they outlive Tandem and open anywhere.
 *
 *   ROMs/notes/<notebook>/<name>.md            the note
 *   ROMs/notes/<notebook>/<name>.assets/       its screenshots, sketches and pictures, linked from the note
 *   ROMs/notes/<notebook>/.game                the game a notebook belongs to (its key), if any
 *
 * A notebook is a folder. A game's notebook is named after the game and found by its `.game` file, so renaming the
 * game or the folder keeps them together. "General" holds notes about no game in particular. Everything here is plain
 * (java.io only), for tests (NotesTest).
 */

/** A folder of notes. [gameKey] is the game it belongs to, if any. */
data class Notebook(val dir: File, val gameKey: String?) {
    val name: String get() = dir.name
}

/** A note as the lists show it: [text] is the start of the file, for the preview and search. */
class NoteEntry(val file: File, val book: Notebook, val modified: Long, val text: String) {
    val title: String get() = file.nameWithoutExtension
    val summary: NoteSummary by lazy { NoteDoc.summary(text) }
}

object NoteFiles {
    const val EXT = "md"
    const val GENERAL = "General"
    const val GAME_FILE = ".game"
    private const val ASSETS = ".assets"
    /** How much of each note the lists read, for previews and search. */
    private const val READ_MAX = 64 * 1024
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val bad = Regex("""[\\/:*?"<>|\u0000-\u001f\u007f]""")

    /** [name] as a file or folder name: no characters a file system trips over, no leading dots. Empty when nothing is left. */
    fun safeName(name: String): String =
        name.replace(bad, " ").replace(Regex("""\s+"""), " ").trim().trimStart('.').trim().take(80).trimEnd('.', ' ')

    /** A note name in [dir] from [base] that no note (or its attachments folder) uses yet: "Glyphs", "Glyphs 2"... */
    fun uniqueName(dir: File, base: String, except: File? = null): String {
        val clean = safeName(base).ifEmpty { "Note" }
        fun taken(n: String) =
            File(dir, "$n.$EXT").let { it.exists() && it != except } || (File(dir, "$n$ASSETS").exists() && except?.nameWithoutExtension != n)
        if (!taken(clean)) return clean
        var i = 2
        while (taken("$clean $i")) i++
        return "$clean $i"
    }

    /** The game key written in a notebook's `.game` file, if it has one. */
    fun gameOf(dir: File): String? =
        runCatching { File(dir, GAME_FILE).takeIf { it.isFile }?.readLines()?.firstOrNull()?.trim() }.getOrNull()?.ifEmpty { null }

    /** Every notebook under [root], General first, then by name. */
    fun notebooks(root: File): List<Notebook> =
        root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }.orEmpty()
            .map { Notebook(it, gameOf(it)) }
            .sortedWith(compareBy({ it.name != GENERAL }, { it.name.lowercase() }))

    /** [key]'s notebook, if it has one yet. */
    fun bookFor(root: File, key: String): Notebook? = notebooks(root).firstOrNull { it.gameKey == key }

    /**
     * The folder a new notebook for a game would get: its title, or the title with the system when another game
     * already has that name ("Tetris (GB)"), then numbered. A folder of that name the user made by hand, with no
     * `.game` file yet, is taken over.
     */
    fun folderFor(root: File, key: String, title: String, system: String): File {
        val base = safeName(title).ifEmpty { safeName(key.substringAfterLast('/')).ifEmpty { "Game" } }
        val names = sequence {
            yield(base)
            val withSystem = safeName("$base ($system)")
            yield(withSystem)
            var i = 2
            while (true) yield("$withSystem $i".also { i++ })
        }
        return names.map { File(root, it) }.first { dir ->
            if (dir.name.equals(GENERAL, ignoreCase = true)) return@first false
            !dir.exists() || (dir.isDirectory && gameOf(dir).let { it == null || it == key })
        }
    }

    /** [key]'s notebook, made now if it has none (with its `.game` file). */
    fun ensureBook(root: File, key: String, title: String, system: String): Notebook {
        bookFor(root, key)?.let { return it }
        val dir = folderFor(root, key, title, system)
        dir.mkdirs()
        File(dir, GAME_FILE).writeText("$key\n$title\n")
        return Notebook(dir, key)
    }

    /** The General notebook (made now if it isn't there). */
    fun general(root: File): Notebook = Notebook(File(root, GENERAL).apply { mkdirs() }, null)

    /** A notebook of the user's own, named [name]; null when the name is empty or taken. */
    fun newBook(root: File, name: String): Notebook? {
        val clean = safeName(name).ifEmpty { return null }
        val dir = File(root, clean)
        if (dir.exists() || !dir.mkdirs()) return null
        return Notebook(dir, null)
    }

    /** The notes in [book], newest first. Reads the start of each (blocking). */
    fun notes(book: Notebook): List<NoteEntry> =
        book.dir.listFiles()?.filter { it.isFile && it.extension.equals(EXT, ignoreCase = true) && !it.name.startsWith(".") }.orEmpty()
            .map { NoteEntry(it, book, it.lastModified(), readStart(it)) }
            .sortedByDescending { it.modified }

    private fun readStart(f: File): String = runCatching {
        f.inputStream().use { s ->
            val buf = ByteArray(READ_MAX)
            var n = 0
            while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r <= 0) break; n += r }
            String(buf, 0, n, Charsets.UTF_8)
        }
    }.getOrDefault("")

    /** A new, empty note in [book] named from [base]. */
    fun create(book: Notebook, base: String, text: String = ""): File {
        book.dir.mkdirs()
        return File(book.dir, "${uniqueName(book.dir, base)}.$EXT").apply { writeText(text) }
    }

    /** The folder [note]'s attachments go in. */
    fun assetsDir(note: File): File = File(note.parentFile, note.nameWithoutExtension + ASSETS)

    /** A new attachment file for [note]: "[prefix]-2026-11-03_14-02-11.[ext]", numbered if two land in the same second. */
    fun newAsset(note: File, prefix: String, ext: String, now: LocalDateTime = LocalDateTime.now()): File {
        val dir = assetsDir(note)
        val base = "$prefix-${stamp.format(now)}"
        var f = File(dir, "$base.$ext")
        var i = 2
        while (f.exists() || sidecar(f).exists()) { f = File(dir, "$base-$i.$ext"); i++ }
        return f
    }

    /** Where a drawing's strokes are kept, beside its picture, so it can be edited again. */
    fun sidecar(png: File): File = File(png.parentFile, png.nameWithoutExtension + ".strokes.json")

    /** The Markdown link from [note] to [asset]: relative, so the notebook can move. */
    fun link(note: File, asset: File): String {
        val dir = note.parentFile ?: return NoteDoc.encodePath(asset.path)
        val rel = asset.relativeToOrNull(dir)?.invariantSeparatorsPath ?: asset.path
        return NoteDoc.encodePath(rel)
    }

    /** The file a link in [note] points at; null for web addresses. */
    fun resolve(note: File, link: String): File? {
        val path = NoteDoc.decodePath(link.trim().removePrefix("<").removeSuffix(">"))
        if (path.isEmpty() || "://" in path || path.startsWith("data:")) return null
        val f = File(path)
        return if (f.isAbsolute) f else File(note.parentFile, path).normalize()
    }

    /** Renames [note] (its attachments folder too, and the links to it). Returns the note's new file. */
    fun rename(note: File, name: String): File {
        val dir = note.parentFile ?: return note
        val clean = uniqueName(dir, name, except = note)
        if (clean == note.nameWithoutExtension) return note
        val to = File(dir, "$clean.$EXT")
        val oldAssets = assetsDir(note)
        if (!note.renameTo(to)) return note
        if (oldAssets.isDirectory && oldAssets.renameTo(assetsDir(to))) {
            to.writeText(NoteDoc.relink(to.readText(), note.nameWithoutExtension, clean))
        }
        return to
    }

    /** Moves [note] and its attachments to [book]. Returns the note's new file, or null when it couldn't. */
    fun move(note: File, book: Notebook): File? {
        if (note.parentFile == book.dir) return note
        book.dir.mkdirs()
        val name = uniqueName(book.dir, note.nameWithoutExtension)
        val to = File(book.dir, "$name.$EXT")
        val assets = assetsDir(note)
        if (!note.renameTo(to) && !(runCatching { note.copyTo(to) }.isSuccess && note.delete())) return null
        if (assets.isDirectory) {
            val target = assetsDir(to)
            if (!assets.renameTo(target)) { assets.copyRecursively(target, overwrite = false); assets.deleteRecursively() }
            if (name != note.nameWithoutExtension) to.writeText(NoteDoc.relink(to.readText(), note.nameWithoutExtension, name))
        }
        return to
    }

    /** Deletes [note] and its attachments folder. */
    fun delete(note: File): Boolean {
        assetsDir(note).takeIf { it.isDirectory }?.deleteRecursively()
        return note.delete() || !note.exists()
    }

    /** True when [book] holds nothing but its `.game` file. */
    fun isEmpty(book: Notebook): Boolean = book.dir.listFiles()?.all { it.name == GAME_FILE } ?: true
}

/** What a note holds, for its line in a list. */
data class NoteSummary(val tasks: Int, val done: Int, val pictures: Int, val rows: Int, val firstLine: String)

/**
 * A note's Markdown as blocks, the way the editor works on it: paragraphs (any Markdown, kept as written), checklist
 * items, pictures on a line of their own, and tables (a glossary is a table). Writing the blocks back gives the same
 * Markdown, tidied: one blank line between blocks, none between checklist items.
 */
object NoteDoc {
    sealed interface Block {
        data class Text(val text: String) : Block
        data class Task(val text: String, val done: Boolean) : Block
        /** [path] as linked (decoded), relative to the note. */
        data class Picture(val path: String, val caption: String) : Block
        data class Table(val header: List<String>, val rows: List<List<String>>) : Block
    }

    private val task = Regex("""^\s{0,3}[-*+]\s+\[([ xX])]\s?(.*)$""")
    private val picture = Regex("""^\s{0,3}!\[([^\]]*)]\(\s*(<[^>]*>|[^)\s]+)(?:\s+"[^"]*")?\s*\)\s*$""")
    private val separator = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")
    private val cellPicture = Regex("""!\[([^\]]*)]\(\s*(<[^>]*>|[^)\s]+)\s*\)""")

    fun parse(md: String): List<Block> {
        val lines = md.replace("\r\n", "\n").split('\n')
        val out = ArrayList<Block>()
        val para = ArrayList<String>()
        var fence: String? = null
        fun flush() {
            if (para.any { it.isNotBlank() }) out += Block.Text(para.joinToString("\n").trimEnd())
            para.clear()
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            if (fence != null) {
                para += line
                if (trimmed.startsWith(fence)) fence = null
                i++; continue
            }
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                para += line; fence = trimmed.take(3); i++; continue
            }
            if (line.isBlank()) { flush(); i++; continue }
            val t = task.matchEntire(line)
            if (t != null) {
                flush(); out += Block.Task(t.groupValues[2].trim(), t.groupValues[1] != " "); i++; continue
            }
            val p = picture.matchEntire(line)
            if (p != null) {
                flush(); out += Block.Picture(unwrap(p.groupValues[2]), p.groupValues[1].trim()); i++; continue
            }
            if (trimmed.startsWith("|") && i + 1 < lines.size && separator.matches(lines[i + 1]) && '-' in lines[i + 1]) {
                flush()
                val header = cells(line)
                val rows = ArrayList<List<String>>()
                i += 2
                while (i < lines.size && lines[i].trimStart().startsWith("|")) { rows += cells(lines[i]); i++ }
                out += Block.Table(header, rows)
                continue
            }
            para += line
            i++
        }
        flush()
        return out
    }

    fun write(blocks: List<Block>): String {
        if (blocks.isEmpty()) return ""
        val sb = StringBuilder()
        blocks.forEachIndexed { i, b ->
            if (i > 0) sb.append(if (b is Block.Task && blocks[i - 1] is Block.Task) "\n" else "\n\n")
            when (b) {
                is Block.Text -> sb.append(b.text.trimEnd())
                is Block.Task -> sb.append(if (b.done) "- [x] " else "- [ ] ").append(oneLine(b.text))
                is Block.Picture -> sb.append("![").append(oneLine(b.caption).replace("[", "").replace("]", "")).append("](").append(encodePath(b.path)).append(")")
                is Block.Table -> {
                    val cols = maxOf(1, b.header.size, b.rows.maxOfOrNull { it.size } ?: 0)
                    fun row(r: List<String>) = (0 until cols).joinToString(" | ", "| ", " |") { escapeCell(r.getOrElse(it) { "" }) }
                    sb.append(row(b.header)).append('\n').append((0 until cols).joinToString(" | ", "| ", " |") { "---" })
                    for (r in b.rows) sb.append('\n').append(row(r))
                }
            }
        }
        return sb.append('\n').toString()
    }

    /** [blocks] with [block] put in after index [after] (-1: first, past the end: last). */
    fun insert(blocks: List<Block>, after: Int, block: Block): List<Block> =
        blocks.toMutableList().apply { add((after + 1).coerceIn(0, size), block) }

    /** [blocks] with block [i] moved [by] places, stopping at the ends. */
    fun move(blocks: List<Block>, i: Int, by: Int): List<Block> {
        if (i !in blocks.indices) return blocks
        val j = (i + by).coerceIn(0, blocks.size - 1)
        return if (j == i) blocks else blocks.toMutableList().apply { add(j, removeAt(i)) }
    }

    /** The headings a new glossary gets when none are given (the app passes them in its language: NotesUi.glossaryHeader). */
    val GLOSSARY = listOf("Symbol", "Meaning")

    /**
     * A new, empty glossary row: in the table at or before block [near] (else the last table), or in a new
     * Symbol | Meaning table after [near]. Returns the blocks, the table's index and the row's.
     */
    fun addRow(blocks: List<Block>, near: Int, header: List<String> = GLOSSARY): Triple<List<Block>, Int, Int> {
        val at = (near.coerceAtMost(blocks.size - 1) downTo 0).firstOrNull { blocks[it] is Block.Table }
            ?: blocks.indices.lastOrNull { blocks[it] is Block.Table }
        if (at == null) {
            val table = Block.Table(header, listOf(header.map { "" }))
            val i = (near + 1).coerceIn(0, blocks.size)
            return Triple(insert(blocks, near, table), i, 0)
        }
        val t = blocks[at] as Block.Table
        val row = List(maxOf(1, t.header.size)) { "" }
        return Triple(blocks.toMutableList().apply { set(at, t.copy(rows = t.rows + listOf(row))) }, at, t.rows.size)
    }

    /** The cells of a table line, unescaped. */
    fun cells(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|") && !s.endsWith("\\|")) s = s.dropLast(1)
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var k = 0
        while (k < s.length) {
            val c = s[k]
            if (c == '\\' && k + 1 < s.length && s[k + 1] == '|') { cur.append('|'); k += 2; continue }
            if (c == '|') { out += cur.toString().trim(); cur.clear() } else cur.append(c)
            k++
        }
        out += cur.toString().trim()
        return out
    }

    private fun escapeCell(s: String) = oneLine(s).replace("|", "\\|")
    private fun oneLine(s: String) = s.replace(Regex("""\s*\n\s*"""), " ").trim()
    private fun unwrap(link: String) = decodePath(link.trim().removePrefix("<").removeSuffix(">"))

    /** A picture in a table cell (a drawn symbol): its path (decoded), or null when the cell is text. */
    fun cellPicture(cell: String): String? = cellPicture.find(cell)?.groupValues?.get(2)?.let { unwrap(it) }

    /** A cell holding just the picture at [path]. */
    fun pictureCell(path: String): String = "![](${encodePath(path)})"

    /** A path as it goes in a link: spaces, brackets and percent signs escaped (other characters stay readable). */
    fun encodePath(path: String): String = buildString {
        for (c in path) when (c) {
            ' ' -> append("%20"); '(' -> append("%28"); ')' -> append("%29"); '%' -> append("%25")
            '<' -> append("%3C"); '>' -> append("%3E"); '[' -> append("%5B"); ']' -> append("%5D")
            else -> append(c)
        }
    }

    /** Undoes percent escapes (UTF-8); anything that isn't a valid escape stays as written. */
    fun decodePath(link: String): String {
        if ('%' !in link) return link
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < link.length) {
            val c = link[i]
            val hex = if (c == '%' && i + 2 < link.length && link[i + 1].isHex() && link[i + 2].isHex()) link.substring(i + 1, i + 3).toInt(16) else null
            if (hex != null) { bytes.write(hex); i += 3 } else { bytes.write(c.toString().toByteArray(Charsets.UTF_8)); i++ }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    /** [md] with links into "[old].assets/" pointing at "[new].assets/" instead (a renamed note). */
    fun relink(md: String, old: String, new: String): String {
        var out = md
        for (from in listOf(old, encodePath(old)).distinct()) out = out.replace("]($from.assets/", "](${encodePath(new)}.assets/")
        return out.replace("](<$old.assets/", "](<$new.assets/")
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    fun summary(md: String): NoteSummary {
        val blocks = parse(md)
        val tasks = blocks.filterIsInstance<Block.Task>()
        val firstLine = blocks.firstNotNullOfOrNull { b ->
            when (b) {
                is Block.Text -> b.text.lineSequence().map { plain(it) }.firstOrNull { it.isNotBlank() }
                else -> null
            }
        }.orEmpty()
        return NoteSummary(
            tasks.size, tasks.count { it.done },
            blocks.count { it is Block.Picture } + blocks.filterIsInstance<Block.Table>().sumOf { t -> t.rows.sumOf { r -> r.count { cellPicture(it) != null } } },
            blocks.filterIsInstance<Block.Table>().sumOf { it.rows.size }, firstLine.take(160),
        )
    }

    /** A line of Markdown as plain text: no heading marks, bullets, emphasis or link targets. */
    fun plain(line: String): String = line.trim()
        .replace(Regex("""^#{1,6}\s+"""), "").replace(Regex("""^>\s?"""), "").replace(Regex("""^([-*+]|\d+[.)])\s+"""), "")
        .replace(Regex("""!?\[([^\]]*)]\([^)]*\)"""), "$1").replace(Regex("""(\*\*|__|\*|_|`|~~)"""), "").trim()

    /** One styled run in a line of text ([Inline.parse]). */
    enum class Style { Bold, Italic, Code, Strike, Link }
    data class Span(val start: Int, val end: Int, val style: Style)
    /** A line of Markdown as shown: [text] without the marks, and the styled runs in it. */
    data class Inline(val text: String, val spans: List<Span>) {
        companion object {
            /** **bold**, *italic* / _italic_, `code`, ~~struck~~ and [links](...) (their text, underlined). */
            fun parse(s: String): Inline {
                val out = StringBuilder()
                val spans = ArrayList<Span>()
                var i = 0
                fun closing(mark: String, from: Int): Int = s.indexOf(mark, from).takeIf { it > from } ?: -1
                while (i < s.length) {
                    val c = s[i]
                    if (c == '\\' && i + 1 < s.length && s[i + 1] in "\\`*_~[]()#!|") { out.append(s[i + 1]); i += 2; continue }
                    val mark = when {
                        s.startsWith("**", i) -> "**"; s.startsWith("__", i) -> "__"; s.startsWith("~~", i) -> "~~"
                        c == '`' -> "`"; c == '*' -> "*"; c == '_' && (i == 0 || !s[i - 1].isLetterOrDigit()) -> "_"
                        else -> null
                    }
                    if (mark != null) {
                        val end = closing(mark, i + mark.length)
                        if (end > 0) {
                            val inner = s.substring(i + mark.length, end)
                            val style = when (mark) { "**", "__" -> Style.Bold; "~~" -> Style.Strike; "`" -> Style.Code; else -> Style.Italic }
                            val start = out.length
                            if (style == Style.Code) out.append(inner) else {
                                val nested = parse(inner)
                                nested.spans.forEach { spans += Span(it.start + start, it.end + start, it.style) }
                                out.append(nested.text)
                            }
                            spans += Span(start, out.length, style)
                            i = end + mark.length
                            continue
                        }
                    }
                    if (c == '[' || (c == '!' && s.startsWith("[", i + 1))) {
                        val open = if (c == '!') i + 1 else i
                        val close = s.indexOf("](", open)
                        val shut = if (close > 0) s.indexOf(')', close) else -1
                        if (close > 0 && shut > 0) {
                            val start = out.length
                            out.append(s.substring(open + 1, close))
                            spans += Span(start, out.length, Style.Link)
                            i = shut + 1
                            continue
                        }
                    }
                    out.append(c)
                    i++
                }
                return Inline(out.toString(), spans.sortedBy { it.start })
            }
        }
    }
}
