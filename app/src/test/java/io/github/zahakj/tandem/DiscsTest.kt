package io.github.zahakj.tandem

import io.github.zahakj.tandem.data.GameMeta
import io.github.zahakj.tandem.data.ScanCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Multi-disc games as one tile (Discs.kt): disc marks, playlists, the walk, launching and moving records over. */
class DiscsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val psx = Platform(
        "psx", "PlayStation", "PS1", listOf("psx"), setOf("cue", "chd", "pbp", "m3u", "iso"), emptySet(), listOf("psx"), emptyList(),
    )
    private val psp = Platform("psp", "PSP", "PSP", listOf("psp"), setOf("iso", "cso", "chd"), emptySet(), listOf("psp"), emptyList())

    private fun file(path: String, text: String = "x") = File(tmp.root, path).apply { parentFile.mkdirs(); writeText(text) }

    private val finder by lazy {
        GameFinder(listOf(psx, psp)) { d ->
            val all = d.listFiles().orEmpty().sortedBy { it.name }
            ScanCache.Listing(d.lastModified(), all.filter { it.isFile }.map { it.name }, all.filter { it.isDirectory }.map { it.name })
        }
    }

    private fun games(system: String = "psx"): List<Game> =
        finder.find(File(tmp.root, "ROMs"), null, main = true).games[system].orEmpty()

    private fun rel(f: File) = f.relativeTo(File(tmp.root, "ROMs")).path

    // --- Disc marks -------------------------------------------------------------------------------------------------

    @Test fun discMarksAreRead() {
        assertEquals("Final Fantasy VII (USA)" to 2, Discs.discOf("Final Fantasy VII (USA) (Disc 2)"))
        assertEquals("Final Fantasy VII (USA)" to 1, Discs.discOf("Final Fantasy VII (Disc 1) (USA)"))
        assertEquals("Metal Gear Solid" to 2, Discs.discOf("Metal Gear Solid [CD 2]"))
        assertEquals("Riven" to 3, Discs.discOf("Riven (Disk 03 of 5)"))
        assertEquals("Panzer Dragoon Saga" to 4, Discs.discOf("Panzer Dragoon Saga CD4"))
        assertEquals("Xenogears" to 2, Discs.discOf("Xenogears_Disc_2"))
    }

    @Test fun namesWithoutADiscAreNoDiscs() {
        assertNull(Discs.discOf("Ridge Racer"))
        assertNull(Discs.discOf("Disco Elysium"))
        assertNull(Discs.discOf("Wipeout 2097"))
        // Amiga CD32 is a system, not disc 32.
        assertNull(Discs.discOf("Alien Breed (CD32)"))
        assertNull(Discs.discOf("(Disc 1)"))
    }

    @Test fun playlistsSkipCommentsAndBlankLines() {
        assertEquals(listOf("A (Disc 1).chd", "sub/A (Disc 2).chd"), Discs.entries("﻿#EXTM3U\n\nA (Disc 1).chd\r\nsub\\A (Disc 2).chd\n# note\n"))
    }

    // --- The walk ---------------------------------------------------------------------------------------------------

    @Test fun aPlaylistIsOneGameAndItsDiscsAreNone() {
        file("ROMs/psx/Final Fantasy VII (USA).m3u", "Final Fantasy VII (USA) (Disc 1).chd\nFinal Fantasy VII (USA) (Disc 2).chd\nFinal Fantasy VII (USA) (Disc 3).chd\n")
        for (n in 1..3) file("ROMs/psx/Final Fantasy VII (USA) (Disc $n).chd")
        file("ROMs/psx/Crash Bandicoot.chd")
        val gs = games()
        assertEquals(listOf("psx/Crash Bandicoot.chd", "psx/Final Fantasy VII (USA).m3u"), gs.map { rel(it.file) })
        val ff = gs.single { it.file.extension == "m3u" }
        assertEquals("Final Fantasy VII", ff.fileTitle)
        assertEquals("psx/Final Fantasy VII (USA).m3u", ff.key)
        assertEquals((1..3).map { "psx/Final Fantasy VII (USA) (Disc $it).chd" }, ff.discs.map(::rel))
    }

    @Test fun discsAPlaylistKeepsInASubfolderAreSkippedThere() {
        file("ROMs/psx/Riven.m3u", "discs/Riven (Disc 1).chd\ndiscs/Riven (Disc 2).chd\n")
        file("ROMs/psx/discs/Riven (Disc 1).chd"); file("ROMs/psx/discs/Riven (Disc 2).chd"); file("ROMs/psx/discs/Other.chd")
        assertEquals(listOf("psx/Riven.m3u", "psx/discs/Other.chd"), games().map { rel(it.file) })
    }

    @Test fun discsWithoutAPlaylistAreOneGameOnDiscOne() {
        file("ROMs/psx/Metal Gear Solid (USA) (Disc 2).chd"); file("ROMs/psx/Metal Gear Solid (USA) (Disc 1).chd")
        file("ROMs/psx/Parasite Eve (Disc 1).cue"); file("ROMs/psx/Parasite Eve (Disc 2).cue")
        val gs = games()
        assertEquals(listOf("psx/Metal Gear Solid (USA) (Disc 1).chd", "psx/Parasite Eve (Disc 1).cue"), gs.map { rel(it.file) })
        val mgs = gs.first()
        assertEquals("Metal Gear Solid", mgs.fileTitle)
        // The key stays disc 1's, as it always was.
        assertEquals("psx/Metal Gear Solid (USA) (Disc 1).chd", mgs.key)
        assertEquals(listOf(1, 2), mgs.discs.map { Discs.discOf(it.nameWithoutExtension)!!.second })
    }

    @Test fun onlySiblingsOfOneKindAreGrouped() {
        // Disc 2 in another folder, another kind of file, or a lone disc 1: each stays a game of its own.
        file("ROMs/psx/A (Disc 1).chd"); file("ROMs/psx/Other/A (Disc 2).chd")
        file("ROMs/psx/B (Disc 1).chd"); file("ROMs/psx/B (Disc 2).cue")
        file("ROMs/psx/C (Disc 1).chd")
        val gs = games()
        assertEquals(5, gs.size)
        assertTrue(gs.all { it.discs.isEmpty() })
    }

    @Test fun aPlaylistThatListsNothingStaysAGame() {
        file("ROMs/psx/Empty.m3u", "# nothing\n"); file("ROMs/psx/D (Disc 1).chd"); file("ROMs/psx/D (Disc 2).chd")
        val gs = games()
        assertEquals(listOf("psx/D (Disc 1).chd", "psx/Empty.m3u"), gs.map { rel(it.file) })
        assertEquals(2, gs.first().discs.size)
    }

    @Test fun aSystemWithoutPlaylistsStillGroupsByName() {
        file("ROMs/psp/Star Ocean (Disc 1).iso"); file("ROMs/psp/Star Ocean (Disc 2).iso")
        val g = games("psp").single()
        assertEquals("psp/Star Ocean (Disc 1).iso", g.key)
        assertEquals(2, g.discs.size)
    }

    // --- Launching --------------------------------------------------------------------------------------------------

    private fun spec(firstDisc: Boolean = false, accept: Regex? = null) =
        LaunchSpec("E", "e.mu/.Main", "android.intent.action.VIEW", "data", emptyMap(), accept = accept, firstDisc = firstDisc)

    @Test fun playersThatCantOpenAPlaylistGetDiscOne() {
        val discs = listOf(File("/r/psx/A (Disc 1).chd"), File("/r/psx/A (Disc 2).chd"))
        val g = Game(psx, File("/r/psx/A.m3u"), "A", null, discs = discs)
        assertNull(Discs.launchFile(g, spec()))
        assertEquals(discs[0], Discs.launchFile(g, spec(firstDisc = true)))
        // A player listing its kinds of file without .m3u gets disc 1 too.
        assertEquals(discs[0], Discs.launchFile(g, spec(accept = Regex(".*\\.(chd|iso)", RegexOption.IGNORE_CASE))))
        // Discs grouped by name already start on disc 1.
        assertNull(Discs.launchFile(Game(psx, discs[0], "A", null, discs = discs), spec(firstDisc = true)))
        assertNull(Discs.launchFile(Game(psx, File("/r/psx/B.chd"), "B", null), spec(firstDisc = true)))
    }

    // --- Playlists Tandem writes ------------------------------------------------------------------------------------

    @Test fun aPlaylistIsNamedForTheDiscsAndListsThemInOrder() {
        val discs = listOf(File("/r/psx/MGS (USA) (Disc 1).chd"), File("/r/psx/MGS (USA) (Disc 2).chd"))
        val g = Game(psx, discs[0], "MGS", null, discs = discs)
        val m3u = Discs.playlistFor(g)!!
        assertEquals(File("/r/psx/MGS (USA).m3u"), m3u)
        assertEquals("${Discs.MADE_BY}\nMGS (USA) (Disc 1).chd\nMGS (USA) (Disc 2).chd\n", Discs.playlistText(m3u, discs))
        assertEquals(listOf("MGS (USA) (Disc 1).chd", "MGS (USA) (Disc 2).chd"), Discs.entries(Discs.playlistText(m3u, discs)))
        // A playlist game needs none.
        assertNull(Discs.playlistFor(Game(psx, m3u, "MGS", null, discs = discs)))
    }

    @Test fun aWrittenPlaylistMakesTheDiscsOneGameByIt() {
        file("ROMs/psx/E (Disc 1).chd"); file("ROMs/psx/E (Disc 2).chd")
        val before = games().single()
        val m3u = Discs.playlistFor(before)!!
        m3u.writeText(Discs.playlistText(m3u, before.discs))
        val after = games().single()
        assertEquals("psx/E.m3u", after.key)
        assertEquals(before.discs, after.discs)
        // Its records come from disc 1's key (and disc 2's, if it had any).
        assertEquals(listOf("psx/E (Disc 1).chd", "psx/E (Disc 2).chd"), Discs.formerKeys(after))
        // Deleting it again (undo) gives them back to disc 1.
        m3u.delete()
        assertEquals(listOf("psx/E (Disc 2).chd", "psx/E.m3u"), Discs.formerKeys(games().single()))
    }

    // --- Moving records over ----------------------------------------------------------------------------------------

    @Test fun recordsAreMergedOnce() {
        val discs = (1..3).map { File("/r/psx/F (Disc $it).chd") }
        val g = Game(psx, File("/r/psx/F.m3u"), "F", null, discs = discs)
        val meta = mapOf(
            "psx/F.m3u" to GameMeta(firstSeen = 500, title = "Final Fantasy"),
            "psx/F (Disc 1).chd" to GameMeta(playSeconds = 3600, playCount = 2, lastPlayed = 100, favorite = true, firstSeen = 50, year = 1997),
            "psx/F (Disc 2).chd" to GameMeta(playSeconds = 1800, playCount = 1, lastPlayed = 300, hidden = true, title = "FF disc 2"),
        )
        val moves = Discs.moves(listOf(g), meta, sessionKeys = setOf("psx/F (Disc 3).chd"))
        val (merged, former) = moves.getValue("psx/F.m3u")
        assertEquals(listOf("psx/F (Disc 1).chd", "psx/F (Disc 2).chd", "psx/F (Disc 3).chd"), former)
        merged!!
        assertEquals(5400L, merged.playSeconds)
        assertEquals(3, merged.playCount)
        assertEquals(300L, merged.lastPlayed)
        assertEquals(50L, merged.firstSeen)
        assertTrue(merged.favorite)
        // The game's own details win; a disc's fill only what it lacks.
        assertEquals("Final Fantasy", merged.title)
        assertEquals(1997, merged.year)
        // One disc tile hidden to tidy the duplicates doesn't hide the game.
        assertFalse(merged.hidden)
        // Once merged (the discs' records gone), nothing moves again.
        val after = mapOf("psx/F.m3u" to merged)
        assertTrue(Discs.moves(listOf(g), after, emptySet()).isEmpty())
    }

    @Test fun aGameWithNoRecordOfItsOwnIsHiddenOnlyIfEveryDiscWas() {
        assertTrue(Discs.merge(null, listOf(GameMeta(hidden = true), GameMeta(hidden = true))).hidden)
        assertFalse(Discs.merge(null, listOf(GameMeta(hidden = true), GameMeta())).hidden)
        assertTrue(Discs.merge(GameMeta(hidden = true), listOf(GameMeta())).hidden)
    }

    @Test fun aKeyAnotherGameStillHasKeepsItsRecord() {
        // A playlist that couldn't be read just now is still a game: its record isn't taken by the discs.
        val discs = listOf(File("/r/psx/G (Disc 1).chd"), File("/r/psx/G (Disc 2).chd"))
        val grouped = Game(psx, discs[0], "G", null, discs = discs)
        val playlist = Game(psx, File("/r/psx/G.m3u"), "G", null)
        val meta = mapOf("psx/G.m3u" to GameMeta(playSeconds = 60), "psx/G (Disc 1).chd" to GameMeta())
        assertTrue(Discs.moves(listOf(grouped, playlist), meta, emptySet()).isEmpty())
    }
}
