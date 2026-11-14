package io.github.zahakj.tandem.data

import io.github.zahakj.tandem.data.ManualText.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Game manuals without Android (ManualFiles.kt): which file is whose, page order, and the text subset. */
class ManualFilesTest {
    @Test fun aManualIsMatchedToItsGameByNameStem() {
        assertEquals("pokemon red", ManualMatch.stem("Pokemon Red.pdf"))
        assertEquals("pokemon red", ManualMatch.stem("Pokemon Red__scan.pdf"))
        assertEquals("pokemon red", ManualMatch.stem("Pokemon Red.cbz"))
        assertEquals(ManualMatch.stem("Pokemon Red.pdf"), ManualMatch.gameStem("Pokemon Red.gb"))
        // A folder of pictures carries no extension; a dot in the game's name stays part of the stem.
        assertEquals("zelda 1.1", ManualMatch.stem("Zelda 1.1"))
        assertEquals("zelda 1.1", ManualMatch.gameStem("Zelda 1.1.nes"))
        assertEquals("mario kart 7 (usa)", ManualMatch.stem("Mario Kart 7 (USA).md"))
    }

    @Test fun kindsComeFromTheExtension() {
        assertEquals(ManualKind.Pdf, ManualKind.of("Game.PDF"))
        assertEquals(ManualKind.Zip, ManualKind.of("Game.cbz"))
        assertEquals(ManualKind.Zip, ManualKind.of("Game.zip"))
        assertEquals(ManualKind.Text, ManualKind.of("Game.txt"))
        assertEquals(ManualKind.Text, ManualKind.of("Game.md"))
        assertEquals(ManualKind.Image, ManualKind.of("Game.jpg"))
        assertNull(ManualKind.of("Game.gb"))
        assertNull(ManualKind.of("Game"))
    }

    @Test fun aPdfBeatsAZipBeatsTextBeatsAPictureBeatsAFolder() {
        assertTrue(ManualMatch.preferred(null, ManualKind.Image))
        assertTrue(ManualMatch.preferred(ManualKind.Image, ManualKind.Text))
        assertTrue(ManualMatch.preferred(ManualKind.Text, ManualKind.Zip))
        assertTrue(ManualMatch.preferred(ManualKind.Zip, ManualKind.Pdf))
        assertFalse(ManualMatch.preferred(ManualKind.Pdf, ManualKind.Pdf))
        assertFalse(ManualMatch.preferred(ManualKind.Pdf, ManualKind.Zip))
    }

    @Test fun zipEntriesReadInNaturalOrderWithoutJunk() {
        val names = listOf(
            "page10.jpg", "page2.jpg", "Page1.jpg", "cover.png", "thumbs.db", "__MACOSX/._page1.jpg", "sub/", "sub/.hidden.png",
            "sub/page3.jpg", "notes.txt",
        )
        assertEquals(listOf("cover.png", "Page1.jpg", "page2.jpg", "page10.jpg", "sub/page3.jpg"), PageOrder.natural(names))
    }

    @Test fun naturalOrderComparesNumbersAsNumbers() {
        assertEquals(listOf("1.png", "01.png", "2.png", "10.png", "a.png", "B.png", "c1.png", "c2.png", "c10.png"),
            listOf("c10.png", "B.png", "10.png", "2.png", "c2.png", "a.png", "01.png", "1.png", "c1.png").sortedWith(PageOrder::compare))
        assertEquals(0, PageOrder.compare("Page1.jpg", "page1.jpg"))
    }

    @Test fun headingsParagraphsListsQuotesAndRules() {
        val md = """
            # Pokemon Red
            Catch them all. This is the
            manual.

            ## Controls
            - A: confirm
            - B: cancel
              and go back
            1. First
            2) Second

            > Save often.
            > Really.

            ---
            Done
        """.trimIndent()
        val blocks = ManualText.parse(md)
        assertEquals(
            listOf(
                Block.Heading(1, "Pokemon Red"),
                Block.Paragraph("Catch them all. This is the manual."),
                Block.Heading(2, "Controls"),
                Block.Item("A: confirm"),
                Block.Item("B: cancel and go back"),
                Block.Item("First", 0, "1."),
                Block.Item("Second", 0, "2)"),
                Block.Quote("Save often. Really."),
                Block.Rule,
                Block.Paragraph("Done"),
            ),
            blocks,
        )
    }

    @Test fun underlinedHeadingsAndIndentedTextAsInPlainGuides() {
        val txt = listOf(
            "ZELDA GUIDE", "===========", "", "Chapter 1", "---------", "Walk north.", "", "    +---+", "    | x |", "", "    +---+", "Then east.",
        ).joinToString("\n")
        val blocks = ManualText.parse(txt)
        assertEquals(
            listOf(
                Block.Heading(1, "ZELDA GUIDE"),
                Block.Heading(2, "Chapter 1"),
                Block.Paragraph("Walk north."),
                Block.Code("+---+\n| x |\n\n+---+"),
                Block.Paragraph("Then east."),
            ),
            blocks,
        )
    }

    @Test fun fencedCodeIsKeptAsWritten() {
        val blocks = ManualText.parse("Map:\n```\n# not a heading\n- not a bullet\n```\nAfter")
        assertEquals(listOf(Block.Paragraph("Map:"), Block.Code("# not a heading\n- not a bullet"), Block.Paragraph("After")), blocks)
    }

    @Test fun nestedBulletsKeepTheirIndent() {
        val blocks = ManualText.parse("- Items\n  - Potion\n    - Rare")
        assertEquals(listOf(Block.Item("Items", 0), Block.Item("Potion", 1), Block.Item("Rare", 2)), blocks)
    }

    @Test fun pagesSplitAtChapters() {
        val blocks = ManualText.parse("# Title\nIntro\n\n## One\nA\n\n### Detail\nB\n\n## Two\nC")
        val pages = ManualText.pages(blocks)
        assertEquals(listOf("Title", "One", "Two"), pages.map { it.title })
        assertEquals(listOf(Block.Heading(2, "One"), Block.Paragraph("A"), Block.Heading(3, "Detail"), Block.Paragraph("B")), pages[1].blocks)
        // Only ## chapters: each is a page, and the text before the first is a page of its own.
        val p2 = ManualText.pages(ManualText.parse("Preface\n\n## One\nA\n\n## Two\nB"))
        assertEquals(listOf("Page 1", "One", "Two"), p2.map { it.title })
    }

    @Test fun aLongChapterIsCutIntoPartsAtBlockBoundaries() {
        val para = "x".repeat(1200)
        val text = "# Long\n" + (1..10).joinToString("\n\n") { para }
        val pages = ManualText.pages(ManualText.parse(text))
        assertTrue(pages.size > 1)
        assertEquals("Long", pages[0].title)
        assertEquals("Long (2)", pages[1].title)
        // Every block lands once, whole.
        assertEquals(11, pages.sumOf { it.blocks.size })
        assertTrue(pages.all { p -> p.blocks.sumOf { (it as? Block.Paragraph)?.text?.length ?: 0 } <= ManualText.PAGE_CHARS })
    }

    @Test fun anEmptyFileIsOneEmptyPage() {
        assertEquals(listOf(ManualText.Page("Page 1", emptyList())), ManualText.pages(ManualText.parse("")))
        assertEquals(1, ManualText.pages(ManualText.parse("just a line")).size)
    }
}
