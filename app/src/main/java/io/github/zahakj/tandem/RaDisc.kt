package io.github.zahakj.tandem

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.Inflater

/**
 * RetroAchievements' hashes for disc games, computed on the device the way rcheevos' rc_hash does them, so a disc is
 * matched by its contents instead of its title. Only a few sectors are read (a boot file at most, capped like
 * rcheevos at 64 MB); nothing is sent but the hash, compared with RetroAchievements' public list on the device.
 *
 * - PlayStation: the boot executable named in SYSTEM.CNF ("BOOT = cdrom:\SLUS_123.45;1", else PSX.EXE): its name,
 *   then its contents (the PS-X EXE header's size plus the 2 KB header).
 * - PlayStation 2: the same with "BOOT2 = cdrom0:\…" and the size in the file's directory entry.
 * - PSP: PSP_GAME\PARAM.SFO, then PSP_GAME\SYSDIR\EBOOT.BIN.
 * - Sega CD and Saturn: the first 512 bytes of the disc (its "SEGADISCSYSTEM" / "SEGA SEGASATURN" header).
 * - PC Engine CD: the first data track's boot header: the disc's title and the boot program's sectors.
 *
 * Images: .cue (with its .bin files), .iso, a raw .bin / .img on its own (2352-byte sectors, told by their sync
 * pattern), .cso (zlib blocks). Not CHD, PBP or GDI (Dreamcast): those games keep being matched by title.
 */
object RaDisc {
    /** The consoles (RetroAchievements ids) hashed here. */
    const val PSX = 12
    const val PS2 = 21
    const val PSP = 41
    const val SEGA_CD = 9
    const val SATURN = 39
    const val PC_ENGINE = 8
    val CONSOLES = setOf(PSX, PS2, PSP, SEGA_CD, SATURN)

    /** Bumped when more discs can be hashed, so games once found to have no match are looked up again. */
    const val REVISION = 1

    /** The disc image kinds read here. */
    val IMAGES = setOf("cue", "iso", "bin", "img", "cso")

    private const val SECTOR = 2048
    private const val MAX_BYTES = 64 * 1024 * 1024

    /** Whether [file] (by its kind) is a disc image this reads. */
    fun isImage(file: File) = file.extension.lowercase() in IMAGES

    /** The hash of the disc [file] for [console], or null when it isn't a disc this can read or not that console's. */
    fun hash(file: File, console: Int): String? {
        if (!isImage(file) || !file.isFile) return null
        val firstData = console == PC_ENGINE
        return open(file, firstData)?.use { t ->
            when (console) {
                PSX -> psx(t)
                PS2 -> ps2(t)
                PSP -> psp(t)
                SEGA_CD, SATURN -> sega(t)
                PC_ENGINE -> pceCd(t)
                else -> null
            }
        }
    }

    // --- Tracks --------------------------------------------------------------------------------------------------------

    /** A disc track read as 2048-byte data sectors, numbered as on the disc: [first] is the track's own first one. */
    abstract class Track : Closeable {
        abstract val first: Int
        /** Up to [len] bytes of sector [sector]'s data into [buf]; how many were read (0 past the end). */
        abstract fun read(sector: Int, buf: ByteArray, len: Int = SECTOR): Int
    }

    /** A track in a plain file: sector n's data is at [offset] + (n − [first]) × [size] + [header]. */
    class FileTrack(file: File, private val offset: Long, private val size: Int, private val header: Int, override val first: Int) : Track() {
        private val raf = RandomAccessFile(file, "r")
        override fun read(sector: Int, buf: ByteArray, len: Int): Int {
            if (sector < first) return 0
            val at = offset + (sector - first).toLong() * size + header
            if (at >= raf.length()) return 0
            raf.seek(at)
            var n = 0
            val want = minOf(len, SECTOR, buf.size)
            while (n < want) { val r = raf.read(buf, n, want - n); if (r <= 0) break; n += r }
            return n
        }
        override fun close() = raf.close()
    }

    /** A .cso (compressed ISO, version 1): blocks of 2048 bytes, each stored or raw-deflated. */
    class CsoTrack(file: File) : Track() {
        private val raf = RandomAccessFile(file, "r")
        private val block: Int
        private val align: Int
        private val blocks: Int
        override val first = 0

        init {
            val h = ByteArray(24)
            raf.readFully(h)
            require(String(h, 0, 4, Charsets.ISO_8859_1) == "CISO") { "not a CSO" }
            val total = le64(h, 8)
            block = le32(h, 16).toInt()
            align = h[21].toInt() and 0xff
            require(block == SECTOR && (h[20].toInt() and 0xff) <= 1) { "unsupported CSO" }
            blocks = ((total + block - 1) / block).toInt()
        }

        private fun index(i: Int): Long {
            raf.seek(24L + i * 4L)
            val b = ByteArray(4)
            raf.readFully(b)
            return le32(b, 0)
        }

        override fun read(sector: Int, buf: ByteArray, len: Int): Int {
            if (sector < 0 || sector >= blocks) return 0
            val a = index(sector)
            val b = index(sector + 1)
            val plain = a and 0x80000000L != 0L
            val start = (a and 0x7fffffffL) shl align
            val end = (b and 0x7fffffffL) shl align
            val stored = ByteArray((end - start).toInt().coerceIn(0, SECTOR * 2))
            raf.seek(start)
            raf.readFully(stored)
            val data = if (plain) stored else ByteArray(SECTOR).also { out ->
                val inf = Inflater(true)
                try { inf.setInput(stored); inf.inflate(out) } finally { inf.end() }
            }
            val n = minOf(len, SECTOR, buf.size, data.size)
            System.arraycopy(data, 0, buf, 0, n)
            return n
        }

        override fun close() = raf.close()
    }

    /** A track of a .cue sheet, as read from it: its file, mode, and where its INDEX 01 starts in the file (frames). */
    data class CueTrack(val number: Int, val file: String, val mode: String, val index1: Int) {
        val audio: Boolean get() = mode.equals("AUDIO", ignoreCase = true)
        /** Bytes per sector in the file, and where the data starts in one. */
        val size: Int get() = when {
            mode.endsWith("/2048") -> 2048
            mode.endsWith("/2336") -> 2336
            else -> 2352
        }
        val header: Int get() = when {
            mode.endsWith("/2048") -> 0
            mode.endsWith("/2336") -> 8
            mode.startsWith("MODE2", ignoreCase = true) -> 24
            audio -> 0
            else -> 16
        }
    }

    /** The tracks of a .cue sheet's [text], in order. */
    fun parseCue(text: String): List<CueTrack> {
        val out = ArrayList<CueTrack>()
        var file: String? = null
        var number = 0
        var mode = ""
        var index1: Int? = null
        fun flush() { if (file != null && number > 0 && index1 != null) out += CueTrack(number, file!!, mode, index1!!) }
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val word = line.substringBefore(' ').uppercase()
            when (word) {
                "FILE" -> {
                    flush(); number = 0; index1 = null
                    file = if (line.contains('"')) line.substringAfter('"').substringBeforeLast('"') else line.split(Regex("\\s+")).getOrNull(1)
                }
                "TRACK" -> {
                    flush(); index1 = null
                    val parts = line.split(Regex("\\s+"))
                    number = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    mode = parts.getOrNull(2).orEmpty().uppercase()
                }
                "INDEX" -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.getOrNull(1)?.toIntOrNull() == 1) index1 = msf(parts.getOrNull(2).orEmpty())
                }
            }
        }
        flush()
        return out
    }

    /** "mm:ss:ff" as frames (75 a second). */
    private fun msf(s: String): Int? {
        val p = s.split(':').map { it.toIntOrNull() ?: return null }
        if (p.size != 3) return null
        return (p[0] * 60 + p[1]) * 75 + p[2]
    }

    /** The track to hash in [file]: the first, or with [firstData] the first that isn't audio. */
    fun open(file: File, firstData: Boolean): Track? = runCatching {
        when (file.extension.lowercase()) {
            "cue" -> openCue(file, firstData)
            "cso" -> CsoTrack(file)
            "iso" -> FileTrack(file, 0, SECTOR, 0, 0)
            else -> raw(file)
        }
    }.getOrNull()

    private fun openCue(cue: File, firstData: Boolean): Track? {
        if (cue.length() > 256 * 1024) return null
        val tracks = parseCue(cue.readText())
        val dir = cue.parentFile
        val pick = (if (firstData) tracks.firstOrNull { !it.audio } else tracks.firstOrNull()) ?: return null
        // Where the track starts on the disc: every earlier file's sectors, then its INDEX 01 in its own file.
        var before = 0L
        for (name in tracks.map { it.file }.distinct()) {
            if (name == pick.file) break
            val size = tracks.first { it.file == name }.size
            before += File(dir, name).length() / size
        }
        val bin = File(dir, pick.file).takeIf { it.isFile } ?: return null
        val offset = pick.index1.toLong() * pick.size
        val header = sniffHeader(bin, offset, pick.size) ?: pick.header
        return FileTrack(bin, offset, pick.size, header, (before + pick.index1).toInt())
    }

    /** A .bin or .img on its own: raw 2352-byte sectors when they start with the sync pattern, else plain 2048. */
    private fun raw(file: File): Track {
        val header = sniffHeader(file, 0, 2352)
        return if (header != null) FileTrack(file, 0, 2352, header, 0) else FileTrack(file, 0, SECTOR, 0, 0)
    }

    private val SYNC = byteArrayOf(0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0)

    /** For raw sectors at [offset]: 16 (mode 1) or 24 (mode 2) when the sector has a sync pattern, else null. */
    private fun sniffHeader(file: File, offset: Long, size: Int): Int? {
        if (size != 2352) return null
        RandomAccessFile(file, "r").use { f ->
            if (f.length() < offset + 16) return null
            val b = ByteArray(16)
            f.seek(offset)
            f.readFully(b)
            if (!(0 until 12).all { b[it] == SYNC[it] }) return null
            return if (b[15].toInt() == 2) 24 else 16
        }
    }

    // --- ISO 9660 ------------------------------------------------------------------------------------------------------

    /**
     * The first sector and size of [path] (backslashes between folders) on [t]'s ISO 9660 file system, as rcheevos
     * finds it: a name matches without case, with or without its ";1". Null when it isn't there.
     */
    fun find(t: Track, path: String): Pair<Int, Long>? {
        var p = path.removePrefix("\\")
        val buf = ByteArray(SECTOR)
        var sector: Int
        var sectors: Int
        val slash = p.lastIndexOf('\\')
        if (slash >= 0) {
            sector = find(t, p.substring(0, slash))?.first ?: return null
            sectors = 0
            p = p.substring(slash + 1)
        } else {
            if (t.read(t.first + 16, buf, 256) < 256) return null
            sector = u24(buf, 156 + 2)
            val block = u16(buf, 128)
            sectors = if (block == 0) 1 else (le32(buf, 156 + 10) / block).toInt()
        }
        if (t.read(sector, buf) <= 0) return null
        val name = p.toByteArray(Charsets.ISO_8859_1)
        var at = 0
        while (true) {
            if (at >= SECTOR || buf[at].toInt() == 0) {
                // The end of this sector's records: a directory spanning more sectors goes on in the next one.
                if (sectors > 1) {
                    sectors--
                    sector++
                    if (t.read(sector, buf) > 0) { at = 0; continue }
                }
                return null
            }
            val len = buf[at].toInt() and 0xff
            if (at + 33 + name.size <= SECTOR) {
                val nameLen = buf[at + 32].toInt() and 0xff
                val ends = nameLen == name.size || (at + 33 + name.size < SECTOR && buf[at + 33 + name.size].toInt() == ';'.code)
                if (ends && name.indices.all { i -> buf[at + 33 + i].toInt().toChar().lowercaseChar() == name[i].toInt().toChar().lowercaseChar() }) {
                    return u24(buf, at + 2) to le32(buf, at + 10)
                }
            }
            if (len == 0) return null
            at += len
        }
    }

    /**
     * rcheevos' rc_hash_cd_file: [size] bytes of the file at [sector] into [md]. The first sector always goes in whole,
     * then whole sectors while 2 KB or more is left, then the rest.
     */
    private fun appendFile(md: MessageDigest, t: Track, start: Int, total: Long): Boolean {
        val buf = ByteArray(SECTOR)
        var sector = start
        var n = t.read(sector, buf)
        if (n < SECTOR) return false
        var size = minOf(total, MAX_BYTES.toLong())
        while (n > 0) {
            md.update(buf, 0, n)
            if (size <= n) break
            size -= n
            sector++
            n = t.read(sector, buf, if (size >= SECTOR) SECTOR else size.toInt())
        }
        return true
    }

    /** The boot file SYSTEM.CNF names under [key] ("BOOT", "BOOT2"), its [prefix] ("cdrom:") and leading "\" taken off. */
    fun bootName(cnf: String, key: String, prefix: String): String? {
        for (line in cnf.split('\n')) {
            if (!line.startsWith(key)) continue
            var rest = line.substring(key.length).trimStart()
            if (!rest.startsWith("=")) continue
            rest = rest.substring(1).trimStart()
            if (rest.startsWith(prefix)) rest = rest.substring(prefix.length)
            rest = rest.trimStart('\\')
            val end = rest.indexOfFirst { it.isWhitespace() || it == ';' }.let { if (it < 0) rest.length else it }
            return rest.substring(0, end).take(63)
        }
        return null
    }

    private fun bootFile(t: Track, key: String, prefix: String): Pair<String, Pair<Int, Long>>? {
        val (cnfAt, _) = find(t, "SYSTEM.CNF") ?: return null
        val buf = ByteArray(SECTOR)
        val n = t.read(cnfAt, buf, SECTOR - 1)
        val text = String(buf, 0, n, Charsets.ISO_8859_1).substringBefore('\u0000')
        val name = bootName(text, key, prefix) ?: return null
        return name to (find(t, name) ?: return null)
    }

    fun psx(t: Track): String? {
        val (name, at) = bootFile(t, "BOOT", "cdrom:") ?: find(t, "PSX.EXE")?.let { "PSX.EXE" to it } ?: return null
        val head = ByteArray(32)
        if (t.read(at.first, head, 32) < 32) return null
        // The PS-X EXE header gives the program's size without itself; the 2 KB header is hashed too.
        val size = if (String(head, 0, 7, Charsets.ISO_8859_1) == "PS-X EX") le32(head, 28) + 2048 else at.second
        val md = MessageDigest.getInstance("MD5")
        md.update(name.toByteArray(Charsets.ISO_8859_1))
        return if (appendFile(md, t, at.first, size)) md.digest().hex() else null
    }

    fun ps2(t: Track): String? {
        val (name, at) = bootFile(t, "BOOT2", "cdrom0:") ?: return null
        val md = MessageDigest.getInstance("MD5")
        md.update(name.toByteArray(Charsets.ISO_8859_1))
        return if (appendFile(md, t, at.first, at.second)) md.digest().hex() else null
    }

    fun psp(t: Track): String? {
        val sfo = find(t, "PSP_GAME\\PARAM.SFO") ?: return null
        val eboot = find(t, "PSP_GAME\\SYSDIR\\EBOOT.BIN") ?: return null
        val md = MessageDigest.getInstance("MD5")
        if (!appendFile(md, t, sfo.first, sfo.second)) return null
        if (!appendFile(md, t, eboot.first, eboot.second)) return null
        return md.digest().hex()
    }

    fun sega(t: Track): String? {
        val buf = ByteArray(512)
        if (t.read(t.first, buf, 512) < 512) return null
        val head = String(buf, 0, 16, Charsets.ISO_8859_1)
        if (head != "SEGADISCSYSTEM  " && head != "SEGA SEGASATURN ") return null
        return MessageDigest.getInstance("MD5").digest(buf).hex()
    }

    fun pceCd(t: Track): String? {
        val buf = ByteArray(SECTOR)
        if (t.read(t.first + 1, buf, 128) < 128) return null
        val md = MessageDigest.getInstance("MD5")
        if (String(buf, 32, 23, Charsets.ISO_8859_1) == "PC Engine CD-ROM SYSTEM") {
            // The disc's title (the header's last 22 bytes), then the boot program's sectors.
            md.update(buf, 106, 22)
            var sector = u24be(buf, 0) + t.first
            var count = buf[3].toInt() and 0xff
            val data = ByteArray(SECTOR)
            while (count > 0) {
                java.util.Arrays.fill(data, 0)
                t.read(sector, data)
                md.update(data)
                sector++
                count--
            }
            return md.digest().hex()
        }
        // GameExpress discs: a plain file system with a BOOT.BIN.
        val (start, size) = find(t, "BOOT.BIN") ?: return null
        if (size >= MAX_BYTES) return null
        var left = size
        var sector = start
        val data = ByteArray(SECTOR)
        while (left > SECTOR) { t.read(sector, data); md.update(data); sector++; left -= SECTOR }
        if (left > 0) { val n = t.read(sector, data, left.toInt()); md.update(data, 0, n) }
        return md.digest().hex()
    }

    // --- Bytes ---------------------------------------------------------------------------------------------------------

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xff) or ((b[at + 1].toInt() and 0xff) shl 8)
    private fun u24(b: ByteArray, at: Int): Int = u16(b, at) or ((b[at + 2].toInt() and 0xff) shl 16)
    private fun u24be(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xff) shl 16) or ((b[at + 1].toInt() and 0xff) shl 8) or (b[at + 2].toInt() and 0xff)
    private fun le32(b: ByteArray, at: Int): Long = (u24(b, at).toLong()) or ((b[at + 3].toLong() and 0xff) shl 24)
    private fun le64(b: ByteArray, at: Int): Long = le32(b, at) or (le32(b, at + 4) shl 32)
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}
