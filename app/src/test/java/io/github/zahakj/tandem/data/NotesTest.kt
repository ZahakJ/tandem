package io.github.zahakj.tandem.data

import io.github.zahakj.tandem.data.NoteDoc.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime

/** Notes on disk (Notes.kt): names, per-game notebooks, attachments and links, and the Markdown blocks. */
class NotesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun root() = tmp.newFolder("notes")

    // --- Names ---------------------------------------------------------------------------------------------------

    @Test fun namesAreSafe() {
        assertEquals("Chants of Sennaar", NoteFiles.safeName("Chants of Sennaar"))
        assertEquals("Zelda Link s Awakening", NoteFiles.safeName("Zelda: Link's Awakening".replace("'", "/")))
        assertEquals("hidden", NoteFiles.safeName("..hidden"))
        assertEquals("a b", NoteFiles.safeName(" a\n\tb. "))
        assertEquals("", NoteFiles.safeName(" /?* "))
        assertEquals(80, NoteFiles.safeName("x".repeat(200)).length)
    }

    @Test fun namesAreUnique() {
        val dir = tmp.newFolder("book")
        assertEquals("Glyphs", NoteFiles.uniqueName(dir, "Glyphs"))
        File(dir, "Glyphs.md").writeText("")
        assertEquals("Glyphs 2", NoteFiles.uniqueName(dir, "Glyphs"))
        // An attachments folder left behind counts as taken too.
        File(dir, "Glyphs 2.assets").mkdirs()
        assertEquals("Glyphs 3", NoteFiles.uniqueName(dir, "Glyphs"))
        // A note keeps its own name.
        assertEquals("Glyphs", NoteFiles.uniqueName(dir, "Glyphs", except = File(dir, "Glyphs.md")))
        assertEquals("Note", NoteFiles.uniqueName(dir, "///"))
    }

    // --- Notebooks -----------------------------------------------------------------------------------------------

    @Test fun gamesGetTheirOwnNotebook() {
        val root = root()
        assertNull(NoteFiles.bookFor(root, "windows/Chants.exe"))
        val book = NoteFiles.ensureBook(root, "windows/Chants.exe", "Chants of Sennaar", "PC")
        assertEquals("Chants of Sennaar", book.name)
        assertEquals("windows/Chants.exe", NoteFiles.gameOf(book.dir))
        // Found again by its .game file, even after the folder is renamed.
        val moved = File(root, "Sennaar notes")
        assertTrue(book.dir.renameTo(moved))
        assertEquals(moved, NoteFiles.bookFor(root, "windows/Chants.exe")?.dir)
        assertEquals(moved, NoteFiles.ensureBook(root, "windows/Chants.exe", "Chants of Sennaar", "PC").dir)
    }

    @Test fun sameTitlesGetTheSystem() {
        val root = root()
        val gb = NoteFiles.ensureBook(root, "gb/Tetris.gb", "Tetris", "GB")
        val nes = NoteFiles.ensureBook(root, "nes/Tetris.nes", "Tetris", "NES")
        val nes2 = NoteFiles.ensureBook(root, "nes/Tetris (Rev 1).nes", "Tetris", "NES")
        assertEquals(listOf("Tetris", "Tetris (NES)", "Tetris (NES) 2"), listOf(gb.name, nes.name, nes2.name))
    }

    @Test fun aFolderMadeByHandIsTakenOver() {
        val root = root()
        File(root, "Celeste").mkdirs()
        File(root, "Celeste/Strawberries.md").writeText("- [ ] 1A")
        val book = NoteFiles.ensureBook(root, "switch/Celeste.nsp", "Celeste", "Switch")
        assertEquals("Celeste", book.name)
        assertEquals(1, NoteFiles.notes(book).size)
    }

    @Test fun noGameTakesGeneral() {
        val root = root()
        NoteFiles.general(root)
        assertEquals("General (PC)", NoteFiles.ensureBook(root, "windows/General.exe", "General", "PC").name)
    }

    @Test fun notebooksListGeneralFirst() {
        val root = root()
        NoteFiles.ensureBook(root, "gb/Alleyway.gb", "Alleyway", "GB")
        NoteFiles.newBook(root, "Recipes")
        NoteFiles.general(root)
        File(root, ".trash").mkdirs()
        assertEquals(listOf("General", "Alleyway", "Recipes"), NoteFiles.notebooks(root).map { it.name })
        assertNull(NoteFiles.newBook(root, "Recipes"))
        assertNull(NoteFiles.newBook(root, " :: "))
    }

    @Test fun notesAreNewestFirst() {
        val book = NoteFiles.general(root())
        val a = NoteFiles.create(book, "Old", "first")
        val b = NoteFiles.create(book, "New", "# Second\nmore")
        File(book.dir, "picture.png").writeText("")
        a.setLastModified(1_000_000L)
        b.setLastModified(2_000_000L)
        val notes = NoteFiles.notes(book)
        assertEquals(listOf("New", "Old"), notes.map { it.title })
        assertEquals("Second", notes[0].summary.firstLine)
    }

    // --- Attachments and links -------------------------------------------------------------------------------------

    @Test fun attachmentsGoBesideTheNote() {
        val book = NoteFiles.general(root())
        val note = NoteFiles.create(book, "Glyph meanings")
        val at = LocalDateTime.of(2026, 11, 3, 14, 2, 11)
        val shot = NoteFiles.newAsset(note, "shot", "png", at)
        assertEquals(File(book.dir, "Glyph meanings.assets/shot-2026-11-03_14-02-11.png"), shot)
        shot.parentFile!!.mkdirs(); shot.writeText("png")
        assertEquals("shot-2026-11-03_14-02-11-2.png", NoteFiles.newAsset(note, "shot", "png", at).name)
        assertEquals(File(shot.parentFile, "shot-2026-11-03_14-02-11.strokes.json"), NoteFiles.sidecar(shot))
        val link = NoteFiles.link(note, shot)
        assertEquals("Glyph%20meanings.assets/shot-2026-11-03_14-02-11.png", link)
        assertEquals(shot, NoteFiles.resolve(note, link))
        assertNull(NoteFiles.resolve(note, "https://example.com/a.png"))
        assertEquals(File("/sdcard/a b.png"), NoteFiles.resolve(note, "/sdcard/a%20b.png"))
    }

    @Test fun pathsEncodeAndDecode() {
        assertEquals("a%20(b)%25.png".replace("(", "%28").replace(")", "%29"), NoteDoc.encodePath("a (b)%.png"))
        assertEquals("a (b)%.png", NoteDoc.decodePath(NoteDoc.encodePath("a (b)%.png")))
        assertEquals("Sénnaar ✓.png", NoteDoc.decodePath("S%C3%A9nnaar%20✓.png"))
        assertEquals("100%+1", NoteDoc.decodePath("100%+1"))
        assertEquals("50%", NoteDoc.decodePath("50%"))
    }

    @Test fun renamingMovesAttachmentsAndLinks() {
        val book = NoteFiles.general(root())
        val note = NoteFiles.create(book, "Glyphs")
        val shot = NoteFiles.newAsset(note, "shot", "png").apply { parentFile!!.mkdirs(); writeText("png") }
        note.writeText(NoteDoc.write(listOf(Block.Picture(NoteDoc.decodePath(NoteFiles.link(note, shot)), "Door"),
            Block.Table(listOf("Symbol", "Meaning"), listOf(listOf(NoteDoc.pictureCell("Glyphs.assets/${shot.name}"), "door"))))))
        val renamed = NoteFiles.rename(note, "Temple glyphs")
        assertEquals("Temple glyphs.md", renamed.name)
        assertFalse(note.exists())
        assertTrue(File(book.dir, "Temple glyphs.assets/${shot.name}").isFile)
        val blocks = NoteDoc.parse(renamed.readText())
        assertEquals(File(book.dir, "Temple glyphs.assets/${shot.name}"), NoteFiles.resolve(renamed, (blocks[0] as Block.Picture).path))
        val cell = (blocks[1] as Block.Table).rows[0][0]
        assertEquals("Temple glyphs.assets/${shot.name}", NoteDoc.cellPicture(cell))
    }

    @Test fun movingKeepsAttachments() {
        val root = root()
        val general = NoteFiles.general(root)
        val game = NoteFiles.ensureBook(root, "windows/Chants.exe", "Chants of Sennaar", "PC")
        NoteFiles.create(game, "Glyphs")
        val note = NoteFiles.create(general, "Glyphs", "![x](Glyphs.assets/a.png)\n")
        File(NoteFiles.assetsDir(note), "a.png").apply { parentFile!!.mkdirs(); writeText("png") }
        val moved = NoteFiles.move(note, game)!!
        assertEquals("Glyphs 2.md", moved.name)
        assertTrue(File(game.dir, "Glyphs 2.assets/a.png").isFile)
        assertEquals("![x](Glyphs%202.assets/a.png)\n", moved.readText())
        assertTrue(NoteFiles.delete(moved))
        assertFalse(File(game.dir, "Glyphs 2.assets").exists())
        assertFalse(NoteFiles.isEmpty(game))
        assertTrue(NoteFiles.isEmpty(general))
    }

    // --- Markdown blocks -------------------------------------------------------------------------------------------

    @Test fun blocksParse() {
        val md = """
            # Temple

            The priests' words.
            Second line.

            - [ ] Find the bard
            * [x] Open the door
            ![The door](Notes.assets/shot%201.png)

            | Symbol | Meaning |
            | --- | :-: |
            | ![](Notes.assets/g1.png) | door \| gate |
            | x | |

            ```
            code

            block
            ```
        """.trimIndent()
        val b = NoteDoc.parse(md)
        assertEquals(Block.Text("# Temple"), b[0])
        assertEquals(Block.Text("The priests' words.\nSecond line."), b[1])
        assertEquals(Block.Task("Find the bard", false), b[2])
        assertEquals(Block.Task("Open the door", true), b[3])
        assertEquals(Block.Picture("Notes.assets/shot 1.png", "The door"), b[4])
        val t = b[5] as Block.Table
        assertEquals(listOf("Symbol", "Meaning"), t.header)
        assertEquals(listOf("![](Notes.assets/g1.png)", "door | gate"), t.rows[0])
        assertEquals(listOf("x", ""), t.rows[1])
        assertEquals(Block.Text("```\ncode\n\nblock\n```"), b[6])
        assertEquals(7, b.size)
    }

    @Test fun blocksWriteBack() {
        val blocks = listOf(
            Block.Text("# Temple"), Block.Task("a", false), Block.Task("b", true), Block.Picture("N.assets/s 1.png", "Door [1]"),
            Block.Table(listOf("Symbol", "Meaning"), listOf(listOf("x|y"), listOf("p", "q", "extra"))),
        )
        val md = NoteDoc.write(blocks)
        assertEquals(
            "# Temple\n\n- [ ] a\n- [x] b\n\n![Door 1](N.assets/s%201.png)\n\n" +
                "| Symbol | Meaning |  |\n| --- | --- | --- |\n| x\\|y |  |  |\n| p | q | extra |\n",
            md,
        )
        val again = NoteDoc.parse(md)
        assertEquals(Block.Picture("N.assets/s 1.png", "Door 1"), again[3])
        assertEquals(listOf("x|y", "", ""), (again[4] as Block.Table).rows[0])
        assertEquals("", NoteDoc.write(emptyList()))
    }

    @Test fun plainMarkdownSurvivesARoundTrip() {
        val md = "# Notes\n\nSome **bold** text\nand a list:\n- one\n- two\n\n> a quote\n"
        assertEquals(md, NoteDoc.write(NoteDoc.parse(md)))
    }

    @Test fun editing() {
        val a = Block.Text("a"); val b = Block.Task("b", false); val c = Block.Text("c")
        assertEquals(listOf(c, a, b), NoteDoc.insert(listOf(a, b), -1, c))
        assertEquals(listOf(a, c, b), NoteDoc.insert(listOf(a, b), 0, c))
        assertEquals(listOf(a, b, c), NoteDoc.insert(listOf(a, b), 9, c))
        assertEquals(listOf(b, a, c), NoteDoc.move(listOf(a, b, c), 1, -1))
        assertEquals(listOf(a, b, c), NoteDoc.move(listOf(a, b, c), 2, 1))
        assertEquals(listOf(a, b, c), NoteDoc.move(listOf(a, b, c), 5, 1))
    }

    @Test fun glossaryRows() {
        val a = Block.Text("a")
        // No table yet: a new glossary after the highlighted block.
        val (first, t, r) = NoteDoc.addRow(listOf(a, a), 0)
        assertEquals(Block.Table(listOf("Symbol", "Meaning"), listOf(listOf("", ""))), first[1])
        assertEquals(1 to 0, t to r)
        // Then rows join the table at or before the highlight, else the last one.
        val (second, t2, r2) = NoteDoc.addRow(first, 2)
        assertEquals(2, (second[1] as Block.Table).rows.size)
        assertEquals(1 to 1, t2 to r2)
        val (third, t3, _) = NoteDoc.addRow(second, 0)
        assertEquals(1, t3)
        assertEquals(3, (third[1] as Block.Table).rows.size)
        assertEquals(1, NoteDoc.addRow(emptyList(), -1).first.size)
    }

    @Test fun summaries() {
        val s = NoteDoc.summary("## The *bard*\n\n- [x] a\n- [ ] b\n\n![](p.png)\n\n| S | M |\n|---|---|\n| ![](g.png) | door |\n| k | key |\n")
        assertEquals(NoteSummary(tasks = 2, done = 1, pictures = 2, rows = 2, firstLine = "The bard"), s)
        assertEquals("link", NoteDoc.plain("- [link](http://x)"))
    }

    @Test fun inlineStyles() {
        val i = NoteDoc.Inline.parse("a **bold *and* it** `co*de` ~~x~~ [site](http://x) snake_case \\*")
        assertEquals("a bold and it co*de x site snake_case *", i.text)
        val styles = i.spans.map { i.text.substring(it.start, it.end) to it.style }
        assertTrue(("bold and it" to NoteDoc.Style.Bold) in styles)
        assertTrue(("and" to NoteDoc.Style.Italic) in styles)
        assertTrue(("co*de" to NoteDoc.Style.Code) in styles)
        assertTrue(("x" to NoteDoc.Style.Strike) in styles)
        assertTrue(("site" to NoteDoc.Style.Link) in styles)
        assertEquals("2 * 3", NoteDoc.Inline.parse("2 * 3").text)
    }
}
