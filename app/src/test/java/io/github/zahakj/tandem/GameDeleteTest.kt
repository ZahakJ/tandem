package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/** Which files deleting a game removes (GameDelete.kt), on real files in a temporary folder. */
class GameDeleteTest {
    @get:Rule val tmp = TemporaryFolder()

    private val roms: File by lazy { tmp.newFolder("ROMs") }
    private val psx: File by lazy { File(roms, "psx").apply { mkdirs() } }
    private val systems: List<File> get() = listOf(psx, File(roms, "ps3"), File(roms, "snes")).onEach { it.mkdirs() }

    private fun file(dir: File, name: String, text: String = "x".repeat(10)): File =
        File(dir, name).apply { parentFile.mkdirs(); writeText(text) }

    private fun plan(target: File, others: List<File> = emptyList()) =
        DeletePlanner.gameFiles(target, listOf(roms), systems, others)

    private fun names(p: FilePlan) = p.files.map { it.file.relativeTo(roms).path }.toSet()

    @Test fun singleFile() {
        val rom = file(File(roms, "snes"), "Mario.sfc", "1234")
        val p = plan(rom)
        assertTrue(p.ok)
        assertEquals(setOf("snes/Mario.sfc"), names(p))
        assertEquals(4L, p.bytes)
    }

    @Test fun cueTakesItsTracks() {
        file(psx, "Game (Track 1).bin", "a".repeat(100))
        file(psx, "game (track 2).bin", "b".repeat(50))
        file(psx, "Unrelated.bin")
        val cue = file(
            psx, "Game.cue",
            "FILE \"Game (Track 1).bin\" BINARY\n  TRACK 01 MODE2/2352\nFILE \"Game (Track 2).bin\" BINARY\n  TRACK 02 AUDIO\n",
        )
        val p = plan(cue)
        assertTrue(p.ok)
        // The second track is found ignoring case.
        assertEquals(setOf("psx/Game.cue", "psx/Game (Track 1).bin", "psx/game (track 2).bin"), names(p))
        assertEquals(cue.length() + 150, p.bytes)
    }

    @Test fun m3uTakesDiscsAndTheirTracks() {
        val discs = File(psx, ".discs")
        file(discs, "FF7 (Disc 1).bin"); file(discs, "FF7 (Disc 2).bin")
        file(discs, "FF7 (Disc 1).cue", "FILE \"FF7 (Disc 1).bin\" BINARY\n")
        file(discs, "FF7 (Disc 2).cue", "FILE \"FF7 (Disc 2).bin\" BINARY\n")
        val m3u = file(psx, "FF7.m3u", "# discs\n.discs/FF7 (Disc 1).cue\n.discs\\FF7 (Disc 2).cue\n")
        val p = plan(m3u)
        assertTrue(p.ok)
        assertEquals(
            setOf("psx/FF7.m3u", "psx/.discs/FF7 (Disc 1).cue", "psx/.discs/FF7 (Disc 1).bin", "psx/.discs/FF7 (Disc 2).cue", "psx/.discs/FF7 (Disc 2).bin"),
            names(p),
        )
    }

    @Test fun partSharedWithAnotherPlaylistStays() {
        file(psx, "Shared.chd"); file(psx, "Own.chd")
        val a = file(psx, "A.m3u", "Shared.chd\nOwn.chd\n")
        file(psx, "B.m3u", "Shared.chd\n")
        val p = plan(a)
        assertTrue(p.ok)
        assertEquals(setOf("psx/A.m3u", "psx/Own.chd"), names(p))
        assertEquals(listOf("Shared.chd"), p.kept.map { it.name })
    }

    @Test fun gdiTracks() {
        val dc = File(roms, "dreamcast/Sonic").apply { mkdirs() }
        file(dc, "track01.bin"); file(dc, "track 02.raw"); file(dc, "track03.bin")
        val gdi = file(dc, "Sonic.gdi", "3\n1 0 4 2352 track01.bin 0\n2 756 0 2352 \"track 02.raw\" 0\n3 45000 4 2352 track03.bin 0\n")
        val p = DeletePlanner.gameFiles(gdi, listOf(roms), listOf(File(roms, "dreamcast")), emptyList())
        assertEquals(setOf("dreamcast/Sonic/Sonic.gdi", "dreamcast/Sonic/track01.bin", "dreamcast/Sonic/track 02.raw", "dreamcast/Sonic/track03.bin"), names(p))
    }

    @Test fun folderGame() {
        val game = File(roms, "ps3/Demon's Souls.ps3")
        file(File(game, "PS3_GAME/USRDIR"), "EBOOT.BIN", "e".repeat(300))
        file(game, "PS3_DISC.SFB", "s".repeat(20))
        val p = plan(game)
        assertTrue(p.ok)
        assertEquals(1, p.files.size)
        assertTrue(p.files.single().isDir)
        assertEquals(320L, p.bytes)
    }

    @Test fun folderHoldingOtherGamesIsRefused() {
        val folder = File(roms, "ps3/Collection.ps3").apply { mkdirs() }
        val inner = file(folder, "Other.iso")
        assertFalse(plan(folder, others = listOf(inner)).ok)
    }

    @Test fun systemFolderIsRefused() {
        // A system folder named like a game (a folder game would be "ps3"), or the ROMs folder itself.
        assertFalse(plan(File(roms, "ps3")).ok)
        assertFalse(DeletePlanner.gameFiles(roms, listOf(roms), systems, emptyList()).ok)
        // A folder that has a system folder inside it.
        val outer = File(roms, "extra.ps3").apply { mkdirs() }
        val sys = File(outer, "snes").apply { mkdirs() }
        assertFalse(DeletePlanner.gameFiles(outer, listOf(roms), listOf(sys), emptyList()).ok)
    }

    @Test fun pathOutsideTheRootIsRefused() {
        val elsewhere = tmp.newFolder("Downloads")
        val rom = file(elsewhere, "Mario.sfc")
        val p = plan(rom)
        assertFalse(p.ok)
        assertNotNull(p.refused)
        // Going up out of the root by "..".
        assertFalse(plan(File(roms, "../Downloads/Mario.sfc")).ok)
    }

    @Test fun cueListingATrackOutsideTheRootIsRefused() {
        val elsewhere = tmp.newFolder("Elsewhere")
        file(elsewhere, "secret.bin")
        val cue = file(psx, "Sneaky.cue", "FILE \"../../Elsewhere/secret.bin\" BINARY\n")
        val p = plan(cue)
        assertFalse(p.ok)
        assertTrue(File(elsewhere, "secret.bin").exists())
    }

    @Test fun linkOutsideTheRootIsRefused() {
        val elsewhere = tmp.newFolder("Real")
        val real = file(elsewhere, "Game.iso")
        val link = File(psx, "Game.iso").toPath()
        Files.createSymbolicLink(link, real.toPath())
        assertFalse(plan(link.toFile()).ok)

        // Inside a folder game, too.
        val game = File(roms, "ps3/Linked.ps3").apply { mkdirs() }
        Files.createSymbolicLink(File(game, "data").toPath(), elsewhere.toPath())
        assertFalse(plan(game).ok)
    }

    @Test fun deleteNeverFollowsLinks() {
        val keep = tmp.newFolder("Keep")
        val precious = file(keep, "precious.sav")
        val game = File(roms, "ps3/Game.ps3").apply { mkdirs() }
        file(game, "EBOOT.BIN")
        // A link inside the root that points inside the root is allowed: it is removed as a link.
        val other = File(roms, "ps3/Other").apply { mkdirs() }
        val otherFile = file(other, "other.bin")
        Files.createSymbolicLink(File(game, "shared").toPath(), other.toPath())
        val p = plan(game)
        assertTrue(p.refused, p.ok)
        val r = DeletePlanner.delete(p.files, listOf(roms))
        assertTrue(r.failed.isEmpty())
        assertFalse(game.exists())
        assertTrue(otherFile.exists())
        assertTrue(precious.exists())
    }

    @Test fun deleteRechecksTheRoot() {
        val elsewhere = tmp.newFolder("Other")
        val f = file(elsewhere, "x.bin")
        val r = DeletePlanner.delete(listOf(DeleteFile(f, 10)), listOf(roms))
        assertEquals(listOf(f), r.failed)
        assertTrue(f.exists())
    }

    @Test fun artByName() {
        val media = File(roms, "downloaded_media")
        val box = file(File(media, "psx/covers"), "FF7.png")
        file(File(media, "psx/screenshots"), "FF7__2.jpg")
        file(File(media, "psx/videos"), "FF7.mp4")
        file(File(media, "psx/covers"), "FF8.png")
        file(File(media, "psx/covers"), "FF7 Remake.png")
        val art = DeletePlanner.artFiles("FF7", listOf(File(media, "psx")), media, emptySet())
        assertEquals(setOf("covers/FF7.png", "screenshots/FF7__2.jpg", "videos/FF7.mp4"), art.map { it.file.relativeTo(File(media, "psx")).path }.toSet())
        assertTrue(box.exists())
        // Another game with the same name shows the same art: it stays.
        assertTrue(DeletePlanner.artFiles("FF7", listOf(File(media, "psx")), media, setOf("ff7")).isEmpty())
    }

    @Test fun shortcutIsJustTheFile() {
        val pc = File(roms, "windows").apply { mkdirs() }
        val shortcut = file(pc, "Hades.pcgame", "1145360")
        file(pc, "Hades.desktop")
        val p = DeletePlanner.gameFiles(shortcut, listOf(roms), listOf(pc), emptyList())
        assertEquals(setOf("windows/Hades.pcgame"), names(p))
        assertNull(p.refused)
    }
}
