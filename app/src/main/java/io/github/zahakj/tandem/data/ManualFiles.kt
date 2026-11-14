package io.github.zahakj.tandem.data

/*
 * The plain parts of game manuals (Manuals.kt, ui/ManualReader.kt): which files count as a manual and which game
 * they belong to, the order of the pages in a zip or a folder, and the small Markdown subset text manuals are read
 * with. No Android here, so all of it is tested on a desktop JVM (ManualFilesTest).
 */

/** What a manual file is, by its extension. */
enum class ManualKind(val extensions: Set<String>, val label: String) {
    /** A PDF, rendered page by page. */
    Pdf(setOf("pdf"), "PDF"),
    /** A zip of pictures (comic-book archives are zips too), one picture per page in natural name order. */
    Zip(setOf("cbz", "zip"), "Pictures"),
    /** One picture: a one-page manual, a map, a reference card. */
    Image(setOf("png", "jpg", "jpeg", "webp", "gif", "bmp"), "Picture"),
    /** Plain text or Markdown: a guide, a walkthrough, a FAQ. */
    Text(setOf("txt", "md", "markdown"), "Text");

    companion object {
        fun of(name: String): ManualKind? {
            val ext = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions }
        }

        /** True for a picture file (the pages inside a zip or a folder). */
        fun isImage(name: String): Boolean = of(name) == Image
    }
}

/**
 * Which game a manual belongs to: by its name, the way artwork is matched (ArtIndex). "Pokemon Red.pdf" belongs to
 * "Pokemon Red.gb"; anything after a double underscore is ignored, so "Pokemon Red__scan.pdf" does too. A folder of
 * pictures is named like the game with no extension.
 */
object ManualMatch {
    /** The lower-cased game stem a manual file (or folder) name stands for. */
    fun stem(name: String): String {
        val cut = name.indexOf("__")
        val base = when {
            cut > 0 -> name.substring(0, cut)
            ManualKind.of(name) != null -> name.substringBeforeLast('.')
            else -> name
        }
        return base.trim().lowercase()
    }

    /** The stem a game file has, for the lookup side: its name without the extension, lower-cased. */
    fun gameStem(fileName: String): String = fileName.substringBeforeLast('.').lowercase()

    /**
     * Among files that all belong to the same game, the one to read: a PDF before a zip of pictures, before text,
     * before a single picture; a folder of pictures comes last. Among equals the first found stays.
     */
    fun preferred(current: ManualKind?, candidate: ManualKind?): Boolean = rank(candidate) < rank(current)

    private fun rank(k: ManualKind?): Int = when (k) {
        ManualKind.Pdf -> 0
        ManualKind.Zip -> 1
        ManualKind.Text -> 2
        ManualKind.Image -> 3
        null -> 4
    }
}

/** The order of pages given as files: how people number scans ("page2" before "page10"). */
object PageOrder {
    /**
     * The picture entries of a zip (or the files of a folder) in reading order. Folders, hidden files, macOS resource
     * forks and anything that isn't a picture are left out; the rest sorts naturally, so numbers compare as numbers
     * and case doesn't matter.
     */
    fun natural(names: List<String>): List<String> =
        names.filter { n ->
            !n.endsWith("/") && !n.startsWith("__MACOSX") && !n.substringAfterLast('/').startsWith(".") && ManualKind.isImage(n)
        }.sortedWith(::compare)

    /** Natural comparison of two names: runs of digits by value, everything else letter by letter, ignoring case. */
    fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val ia = i
                val jb = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(ia, i).trimStart('0')
                val nb = b.substring(jb, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                // Equal values: fewer leading zeros first, so "1" comes before "01".
                if (i - ia != j - jb) return (i - ia) - (j - jb)
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}

/**
 * Text manuals, read as pages. A simple Markdown subset is understood: `#` headings (and lines underlined with `===`
 * or `---`), paragraphs (lines run together until a blank line), `-`/`*` bullets and `1.` numbers (indented lines
 * continue an item), `>` quotes, fenced or indented code (kept as written, for maps and tables drawn in text) and
 * `---` rules. The inline marks (bold, italic, code, links) are left in the text; the reader draws them. Plain `.txt`
 * guides fit the same rules well enough to read.
 */
object ManualText {
    sealed interface Block {
        data class Heading(val level: Int, val text: String) : Block
        data class Paragraph(val text: String) : Block
        /** A list item; [number] is its label ("1.", "a)") or null for a bullet. [indent] is its nesting, from 0. */
        data class Item(val text: String, val indent: Int = 0, val number: String? = null) : Block
        data class Quote(val text: String) : Block
        /** Preformatted lines, as written. */
        data class Code(val text: String) : Block
        data object Rule : Block
    }

    /** A page of a text manual: its title (the heading it starts with, or "Page n") and its blocks. */
    data class Page(val title: String, val blocks: List<Block>)

    private val heading = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val bullet = Regex("""^(\s*)[-*+•]\s+(.*)$""")
    private val number = Regex("""^(\s*)(\d{1,3}[.)]|[a-zA-Z][.)])\s+(.*)$""")
    private val rule = Regex("""^\s*([-*_=#~])(\s*\1){2,}\s*$""")

    fun parse(text: String): List<Block> {
        val out = ArrayList<Block>()
        val para = ArrayList<String>()
        val code = ArrayList<String>()
        var fence: String? = null
        var indentedCode = false
        var quote: StringBuilder? = null

        fun flushPara() {
            if (para.isNotEmpty()) { out += Block.Paragraph(para.joinToString(" ")); para.clear() }
        }
        fun flushCode() {
            if (code.isNotEmpty()) { out += Block.Code(code.joinToString("\n").trimEnd()); code.clear() }
            indentedCode = false
        }
        fun flushQuote() {
            quote?.let { out += Block.Quote(it.toString().trim()) }
            quote = null
        }
        fun flushAll() { flushPara(); flushCode(); flushQuote() }

        val lines = text.lines()
        var i = 0
        var blankBefore = true
        while (i < lines.size) {
            val raw = lines[i].trimEnd()
            val t = raw.trimStart()
            val afterBlank = blankBefore
            blankBefore = t.isEmpty()
            i++
            // Inside a fence everything is code until the closing fence.
            val open = fence
            if (open != null) {
                if (t.startsWith(open)) { flushCode(); fence = null } else code += raw
                continue
            }
            if (t.startsWith("```") || t.startsWith("~~~")) {
                flushAll()
                fence = t.substring(0, 3)
                continue
            }
            // Four spaces (or a tab) in from the margin: preformatted, like Markdown. A blank line inside keeps it going
            // only while the next line is indented too, so a map drawn in text stays one block. Indented lines right
            // under a list item belong to the list (a nested item, or the item going on), not to a code block.
            val indented = raw.startsWith("    ") || raw.startsWith("\t")
            val inList = !indentedCode && !afterBlank && para.isEmpty() && quote == null && out.lastOrNull() is Block.Item && t.isNotEmpty()
            if (indented && !inList && para.isEmpty() && quote == null && (indentedCode || t.isNotEmpty())) {
                if (!indentedCode) flushAll()
                indentedCode = true
                code += if (raw.startsWith("\t")) raw.substring(1) else raw.substring(4)
                continue
            }
            if (indentedCode) {
                if (t.isEmpty() && i < lines.size && (lines[i].startsWith("    ") || lines[i].startsWith("\t"))) { code += ""; continue }
                flushCode()
            }
            if (t.isEmpty()) { flushAll(); continue }

            val h = heading.matchEntire(t)
            if (h != null) {
                flushAll()
                out += Block.Heading(h.groupValues[1].length, h.groupValues[2])
                continue
            }
            // A line of = or - under a single line of text is a heading (setext). A rule on its own is a rule.
            if (rule.matches(t)) {
                val under = t[0]
                if (para.size == 1 && (under == '=' || under == '-') && t.length >= 3) {
                    val title = para.removeAt(0)
                    out += Block.Heading(if (under == '=') 1 else 2, title)
                } else {
                    flushAll()
                    out += Block.Rule
                }
                continue
            }
            if (t.startsWith(">")) {
                flushPara(); flushCode()
                val q = quote ?: StringBuilder().also { quote = it }
                if (q.isNotEmpty()) q.append(' ')
                q.append(t.removePrefix(">").trim())
                continue
            }
            flushQuote()
            val b = bullet.matchEntire(raw)
            val n = number.matchEntire(raw)
            when {
                b != null -> { flushPara(); flushCode(); out += Block.Item(b.groupValues[2].trim(), b.groupValues[1].length / 2) }
                n != null -> { flushPara(); flushCode(); out += Block.Item(n.groupValues[3].trim(), n.groupValues[1].length / 2, n.groupValues[2]) }
                // An indented line right after an item continues it.
                inList && raw.startsWith(" ") -> {
                    val last = out.removeAt(out.size - 1) as Block.Item
                    out += last.copy(text = last.text + " " + t)
                }
                else -> para += t
            }
        }
        flushAll()
        return out
    }

    /** A page longer than this (in characters of text) is split further at a block boundary. */
    const val PAGE_CHARS = 5_000

    /**
     * The blocks as pages: one per chapter, split before each heading of the manual's top level (and the level under
     * it when the top level is `#`, so a title followed by `##` chapters gives a title page and a page per chapter).
     * Whatever comes before the first heading is a page of its own. Pages with more than [PAGE_CHARS] of text are cut
     * into parts at a block boundary, so a plain guide with no headings is still read a screen or two at a time.
     */
    fun pages(blocks: List<Block>): List<Page> {
        if (blocks.isEmpty()) return listOf(Page("Page 1", emptyList()))
        val top = blocks.filterIsInstance<Block.Heading>().minOfOrNull { it.level }
        val splitAt = when (top) { null -> 0; 1 -> 2; else -> top }
        val chapters = ArrayList<MutableList<Block>>()
        for (b in blocks) {
            if (b is Block.Heading && b.level <= splitAt || chapters.isEmpty()) chapters.add(ArrayList())
            chapters.last() += b
        }
        val out = ArrayList<Page>()
        for (chapter in chapters) {
            val title = (chapter.firstOrNull() as? Block.Heading)?.text?.takeIf { it.isNotBlank() }
            var part = ArrayList<Block>()
            var chars = 0
            var parts = 0
            fun flush() {
                if (part.isEmpty()) return
                parts++
                val name = title ?: "Page ${out.size + 1}"
                out += Page(if (parts > 1) "$name ($parts)" else name, part)
                part = ArrayList()
                chars = 0
            }
            for (b in chapter) {
                val n = length(b)
                if (part.isNotEmpty() && chars + n > PAGE_CHARS) flush()
                part += b
                chars += n
            }
            flush()
        }
        return out
    }

    private fun length(b: Block): Int = when (b) {
        is Block.Heading -> b.text.length + 40
        is Block.Paragraph -> b.text.length
        is Block.Item -> b.text.length + 10
        is Block.Quote -> b.text.length
        is Block.Code -> b.text.length + b.text.count { it == '\n' } * 30
        Block.Rule -> 20
    }
}
