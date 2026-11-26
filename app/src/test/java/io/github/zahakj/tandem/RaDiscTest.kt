package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.Deflater

/**
 * RetroAchievements' disc hashes (RaDisc.kt), on small made-up discs: each expected hash is worked out here from
 * rcheevos' recipe (what goes into the MD5, in order), and the same disc as an .iso, a raw .bin, a .cue and a .cso
 * must give the same hash.
 */
class RaDiscTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun md5(vararg parts: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        parts.forEach { md.update(it) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** A disc of 2048-byte sectors, filled in by sector. */
    private class Iso(sectors: Int) {
        val data = ByteArray(sectors * 2048)
        fun sector(n: Int): ByteArray = data.copyOfRange(n * 2048, (n + 1) * 2048)
        fun put(sector: Int, at: Int, bytes: ByteArray) = System.arraycopy(bytes, 0, data, sector * 2048 + at, bytes.size)
        fun fill(sector: Int, count: Int, seed: Int) {
            for (i in 0 until count * 2048) data[sector * 2048 + i] = ((i * 31 + seed * 7 + i / 2048) and 0xff).toByte()
        }

        /** The volume descriptor, with the root directory at [root]. */
        fun volume(root: Int) {
            put(16, 0, byteArrayOf(1) + "CD001".toByteArray())
            put(16, 128, le16(2048))
            put(16, 156, record("\u0000", root, 2048, dir = true))
        }

        /** A directory at [sector] holding [entries]: name to (sector, size, is a directory). */
        fun dir(sector: Int, entries: List<Triple<String, Pair<Int, Int>, Boolean>>) {
            var at = 0
            for (r in listOf(record("\u0000", sector, 2048, true), record("\u0001", sector, 2048, true)) + entries.map { (n, s, d) -> record(n, s.first, s.second, d) }) {
                put(sector, at, r)
                at += r.size
            }
        }

        companion object {
            fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
            fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            fun record(name: String, lba: Int, size: Int, dir: Boolean): ByteArray {
                val n = name.toByteArray(Charsets.ISO_8859_1)
                val len = 33 + n.size + (if (n.size % 2 == 0) 1 else 0)
                val r = ByteArray(len)
                r[0] = len.toByte()
                System.arraycopy(le32(lba), 0, r, 2, 4)
                System.arraycopy(le32(size), 0, r, 10, 4)
                r[25] = if (dir) 2 else 0
                r[32] = n.size.toByte()
                System.arraycopy(n, 0, r, 33, n.size)
                return r
            }
        }
    }

    /** The same sectors as raw 2352-byte mode 2 sectors (sync, header, subheader, data, error codes left blank). */
    private fun raw(iso: Iso, mode: Int = 2): ByteArray {
        val out = ByteArrayOutputStream()
        val sync = byteArrayOf(0) + ByteArray(10) { -1 } + byteArrayOf(0)
        for (n in 0 until iso.data.size / 2048) {
            val s = ByteArray(2352)
            System.arraycopy(sync, 0, s, 0, 12)
            s[15] = mode.toByte()
            System.arraycopy(iso.sector(n), 0, s, if (mode == 2) 24 else 16, 2048)
            out.write(s)
        }
        return out.toByteArray()
    }

    /** A .cso (version 1): odd blocks stored plain, even ones deflated. */
    private fun cso(iso: Iso): ByteArray {
        val blocks = iso.data.size / 2048
        val body = ByteArrayOutputStream()
        val index = IntArray(blocks + 1)
        val start = 24 + (blocks + 1) * 4
        for (b in 0 until blocks) {
            val plain = b % 2 == 1
            index[b] = (start + body.size()) or (if (plain) 0x80000000.toInt() else 0)
            if (plain) body.write(iso.sector(b)) else {
                val d = Deflater(9, true)
                d.setInput(iso.sector(b)); d.finish()
                val buf = ByteArray(4096)
                val n = d.deflate(buf); d.end()
                body.write(buf, 0, n)
            }
        }
        index[blocks] = start + body.size()
        val out = ByteArrayOutputStream()
        out.write("CISO".toByteArray()); out.write(Iso.le32(24)); out.write(Iso.le32(iso.data.size)); out.write(Iso.le32(0))
        out.write(Iso.le32(2048)); out.write(byteArrayOf(1, 0, 0, 0))
        index.forEach { out.write(Iso.le32(it)) }
        out.write(body.toByteArray())
        return out.toByteArray()
    }

    private fun file(name: String, bytes: ByteArray): File = File(tmp.root, name).also { it.writeBytes(bytes) }

    /** A PlayStation disc: SYSTEM.CNF names SLUS_000.01, a PS-X EXE whose header says 2048 bytes follow it. */
    private fun psxDisc(): Iso {
        val iso = Iso(30)
        iso.volume(20)
        val cnf = "BOOT = cdrom:\\SLUS_000.01;1\r\nTCB = 4\r\nEVENT = 10\r\n".toByteArray()
        iso.dir(20, listOf(Triple("SYSTEM.CNF;1", 22 to cnf.size, false), Triple("SLUS_000.01;1", 23 to 6000, false)))
        iso.put(22, 0, cnf)
        iso.fill(23, 4, 1)
        iso.put(23, 0, "PS-X EXE".toByteArray())
        iso.put(23, 28, Iso.le32(2048))
        return iso
    }

    @Test fun playStationHashesTheBootExecutableInEveryImageKind() {
        val iso = psxDisc()
        // The name, then the 2 KB header and the 2048 bytes the header says follow it (not the 6000 of the entry).
        val want = md5("SLUS_000.01".toByteArray(), iso.sector(23), iso.sector(24))
        assertEquals(want, RaDisc.hash(file("game.iso", iso.data), RaDisc.PSX))
        assertEquals(want, RaDisc.hash(file("game.bin", raw(iso)), RaDisc.PSX))
        assertEquals(want, RaDisc.hash(file("mode1.img", raw(iso, mode = 1)), RaDisc.PSX))
        file("Game (Track 1).bin", raw(iso))
        file("Game (Track 2).bin", ByteArray(2352 * 10))
        val cue = file(
            "Game.cue",
            "FILE \"Game (Track 1).bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\nFILE \"Game (Track 2).bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 00 00:00:00\n    INDEX 01 00:02:00\n".toByteArray(),
        )
        assertEquals(want, RaDisc.hash(cue, RaDisc.PSX))
        assertEquals(want, RaDisc.hash(file("game.cso", cso(iso)), RaDisc.PSX))
    }

    @Test fun playStationFallsBackToPsxExe() {
        val iso = Iso(30)
        iso.volume(20)
        iso.dir(20, listOf(Triple("PSX.EXE;1", 23 to 3000, false)))
        iso.fill(23, 2, 2)
        // No PS-X EXE mark: the directory's size, 3000: the first sector whole, then 952 bytes.
        val want = md5("PSX.EXE".toByteArray(), iso.sector(23), iso.sector(24).copyOf(952))
        assertEquals(want, RaDisc.hash(file("old.iso", iso.data), RaDisc.PSX))
    }

    @Test fun playStation2HashesBoot2() {
        val iso = Iso(40)
        iso.volume(20)
        val cnf = "BOOT2 = cdrom0:\\SLUS_200.02;1\nVER = 1.00\nVMODE = NTSC\n".toByteArray()
        iso.dir(20, listOf(Triple("SLUS_200.02;1", 25 to 5000, false), Triple("SYSTEM.CNF;1", 22 to cnf.size, false)))
        iso.put(22, 0, cnf)
        iso.fill(25, 3, 3)
        iso.put(25, 0, byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))
        val want = md5("SLUS_200.02".toByteArray(), iso.sector(25), iso.sector(26), iso.sector(27).copyOf(5000 - 4096))
        assertEquals(want, RaDisc.hash(file("ps2.iso", iso.data), RaDisc.PS2))
        // A PlayStation reads BOOT, which a PS2 disc doesn't have, nor PSX.EXE.
        assertNull(RaDisc.hash(file("ps2b.iso", iso.data), RaDisc.PSX))
    }

    @Test fun pspHashesParamSfoThenEboot() {
        val iso = Iso(50)
        iso.volume(20)
        iso.dir(20, listOf(Triple("PSP_GAME", 21 to 2048, true)))
        iso.dir(21, listOf(Triple("PARAM.SFO;1", 30 to 100, false), Triple("SYSDIR", 22 to 2048, true)))
        iso.dir(22, listOf(Triple("EBOOT.BIN;1", 31 to 5000, false)))
        iso.fill(30, 4, 4)
        // PARAM.SFO's first sector goes in whole (rcheevos never cuts the first one), then EBOOT.BIN.
        val want = md5(iso.sector(30), iso.sector(31), iso.sector(32), iso.sector(33).copyOf(5000 - 4096))
        assertEquals(want, RaDisc.hash(file("psp.iso", iso.data), RaDisc.PSP))
        assertEquals(want, RaDisc.hash(file("psp.cso", cso(iso)), RaDisc.PSP))
    }

    @Test fun segaDiscsHashTheirFirst512Bytes() {
        val iso = Iso(4)
        iso.fill(0, 1, 5)
        iso.put(0, 0, "SEGADISCSYSTEM  ".toByteArray())
        val want = md5(iso.sector(0).copyOf(512))
        assertEquals(want, RaDisc.hash(file("scd.bin", raw(iso, mode = 1)), RaDisc.SEGA_CD))
        iso.put(0, 0, "SEGA SEGASATURN ".toByteArray())
        assertEquals(md5(iso.sector(0).copyOf(512)), RaDisc.hash(file("sat.iso", iso.data), RaDisc.SATURN))
        iso.put(0, 0, "NOT A SEGA DISC!".toByteArray())
        assertNull(RaDisc.hash(file("other.iso", iso.data), RaDisc.SATURN))
    }

    @Test fun pcEngineCdHashesTheBootHeaderOfTheFirstDataTrack() {
        val data = Iso(10)
        data.fill(0, 10, 6)
        val header = ByteArray(128)
        header[2] = 2 // the program starts at the track's sector 2
        header[3] = 3 // and is 3 sectors long
        "PC Engine CD-ROM SYSTEM".toByteArray().copyInto(header, 32)
        "MADE UP TITLE ABCDEFGH".toByteArray().copyInto(header, 106)
        data.put(1, 0, header)
        val want = md5(data.sector(1).copyOfRange(106, 128), data.sector(2), data.sector(3), data.sector(4))
        file("pce (Track 01).bin", ByteArray(2352 * 20))
        file("pce (Track 02).bin", ByteArray(2352 * 2) + raw(data, mode = 1))
        val cue = file(
            "pce.cue",
            "FILE \"pce (Track 01).bin\" BINARY\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\nFILE \"pce (Track 02).bin\" BINARY\n  TRACK 02 MODE1/2352\n    INDEX 00 00:00:00\n    INDEX 01 00:00:02\n".toByteArray(),
        )
        assertEquals(want, RaDisc.hash(cue, RaDisc.PC_ENGINE))
    }

    @Test fun cueSheetsRead() {
        val t = RaDisc.parseCue("REM a comment\nFILE \"a b.bin\" BINARY\r\n  TRACK 01 MODE1/2352\r\n    INDEX 01 00:00:00\r\n  TRACK 02 AUDIO\r\n    INDEX 00 01:00:00\r\n    INDEX 01 01:02:10\r\n")
        assertEquals(2, t.size)
        assertEquals(RaDisc.CueTrack(1, "a b.bin", "MODE1/2352", 0), t[0])
        assertEquals((62 * 75) + 10, t[1].index1)
        assertEquals(16, t[0].header)
        assertEquals(true, t[1].audio)
    }

    @Test fun bootLinesRead() {
        assertEquals("SLUS_007.24", RaDisc.bootName("BOOT=cdrom:\\SLUS_007.24;1\n", "BOOT", "cdrom:"))
        assertEquals("DIR\\GAME.EXE", RaDisc.bootName("TCB=4\nBOOT = cdrom:\\\\DIR\\GAME.EXE;1\n", "BOOT", "cdrom:"))
        assertNull(RaDisc.bootName("BOOT2 = cdrom0:\\SLPS_250.01;1\n", "BOOT", "cdrom:"))
        assertEquals("SLPS_250.01", RaDisc.bootName("BOOT2 = cdrom0:\\SLPS_250.01;1\n", "BOOT2", "cdrom0:"))
    }

    @Test fun aMultiDiscGameIsHashedByItsFirstDisc() {
        val psx = Platform("psx", "PlayStation", "PS1", listOf("psx"), setOf("cue", "m3u", "iso"), emptySet(), listOf("psx"), emptyList())
        val iso = psxDisc()
        val d1 = file("FF (Disc 1).iso", iso.data)
        val d2 = file("FF (Disc 2).iso", ByteArray(2048 * 20))
        val want = RaDisc.hash(d1, RaDisc.PSX)
        assertNotNull(want)
        // Grouped by name: disc 1 is the game's file; with a playlist, the playlist is, and disc 1 is hashed.
        assertEquals(d1, RaHash.fileFor(Game(psx, d1, "FF", null, discs = listOf(d1, d2))))
        val m3u = file("FF.m3u", "FF (Disc 1).iso\nFF (Disc 2).iso\n".toByteArray())
        assertEquals(want, RaHash.forGame(Game(psx, m3u, "FF", null, discs = listOf(d1, d2)), RaDisc.PSX))
        // A playlist standing alone (its discs not grouped): its first entry.
        assertEquals(d1, RaHash.fileFor(Game(psx, m3u, "FF", null)))
    }

    @Test fun whatIsNotADiscHashesNothing() {
        assertNull(RaDisc.hash(file("game.chd", ByteArray(4096)), RaDisc.PSX))
        assertNull(RaDisc.hash(file("blank.iso", ByteArray(2048 * 20)), RaDisc.PSX))
        assertNotNull(RaDisc.open(file("blank2.iso", ByteArray(2048 * 20)), false))
    }
}
