package io.github.zahakj.tandem

import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Animal Crossing: New Horizons 3.0.x keeps the island's owner as a user account ID inside main.dat, so a save moved
 * between two emulators with different accounts says its resident is "not linked to any user account" (SaveSync).
 * [swapUser] puts the other account's ID in its place. main.dat is AES-CTR with a key and counter taken from
 * mainHeader.dat, and murmur3 hashes guard its regions (the layout NHSE documents: MainSaveOffsets30, REV_300).
 */
object AcnhSave {
    const val TITLE = "01006F8002326000"
    private const val MAIN_SIZE = 0x9B0E90
    private val HASHES = listOf(
        0x000110 to 0x1e339c, 0x1e34b0 to 0x3d089c, 0x5b3e60 to 0x037acc, 0x5eb930 to 0x03ce5c,
        0x6288a0 to 0x037acc, 0x660370 to 0x03ce5c, 0x69d2e0 to 0x037acc, 0x6d4db0 to 0x03ce5c,
        0x711d20 to 0x037acc, 0x7497f0 to 0x03ce5c, 0x786760 to 0x037acc, 0x7be230 to 0x03ce5c,
        0x7fb1a0 to 0x037acc, 0x832c70 to 0x03ce5c, 0x86fbe0 to 0x037acc, 0x8a76b0 to 0x03ce5c,
        0x8e4620 to 0x037acc, 0x91c0f0 to 0x03ce5c, 0x958f50 to 0x057f3c,
    )

    /**
     * In the save folder [dir] (holding main.dat and mainHeader.dat), replaces the account [from] with [to] (32 hex
     * digits each, as Eden names its user folders). Refuses (throws, leaving the file as it was) unless the save is
     * 3.0.x, its hashes check, and [from] is there exactly once. The file keeps its time, so the copy reads as the
     * same age as the save it came from.
     */
    fun swapUser(dir: File, from: String, to: String) {
        val header = File(dir, "mainHeader.dat").readBytes()
        val main = File(dir, "main.dat")
        checkRevision(header)
        val raw = main.readBytes()
        require(raw.size == MAIN_SIZE) { "main.dat is ${raw.size} bytes" }
        val plain = crypt(header, raw)
        require(hashesOk(plain)) { "main.dat hashes don't check" }
        val a = uid(from); val b = uid(to)
        val at = find(plain, a)
        require(at >= 0 && find(plain, a, at + 1) < 0) { "the account isn't in main.dat exactly once" }
        b.copyInto(plain, at)
        fixHashes(plain)
        val time = main.lastModified()
        val tmp = File(dir, "main.dat.tmp")
        tmp.writeBytes(crypt(header, plain))
        check(tmp.renameTo(main)) { "couldn't replace main.dat" }
        main.setLastModified(time)
    }

    /** A user account ID as the save holds it: the 128-bit number, little-endian. */
    fun uid(hex: String): ByteArray {
        require(hex.length == 32) { "an account ID is 32 hex digits" }
        return ByteArray(16) { i -> hex.substring(30 - 2 * i, 32 - 2 * i).toInt(16).toByte() }
    }

    private fun checkRevision(header: ByteArray) {
        val major = le32(header, 0); val minor = le32(header, 4)
        val rev = (header[14].toInt() and 0xFF) or ((header[15].toInt() and 0xFF) shl 8)
        require(major == 0xA0002L && minor == 0xA0028L && rev in 31..34) { "not a 3.0.x save" }
    }

    /** Encrypts or decrypts (the same thing in CTR) [body] with the key and counter [header] gives. */
    fun crypt(header: ByteArray, body: ByteArray): ByteArray {
        val data = LongArray(128) { le32(header, 0x100 + it * 4) }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(param(data, 0), "AES"), IvParameterSpec(param(data, 2)))
        return cipher.doFinal(body)
    }

    private fun param(data: LongArray, index: Int): ByteArray {
        val rand = SeadRandom(data[(data[index] and 0x7F).toInt()])
        val prms = data[(data[index + 1] and 0x7F).toInt()] and 0x7F
        repeat(((prms and 0xF) + 1).toInt()) { rand.u64() }
        return ByteArray(16) { (rand.u32() ushr 24).toByte() }
    }

    private class SeadRandom(seed: Long) {
        private val s = LongArray(4)
        init {
            var x = seed and M32
            for (i in 0 until 4) { x = (0x6C078965L * (x xor (x ushr 30)) + i + 1) and M32; s[i] = x }
        }
        fun u32(): Long {
            val n = (s[0] xor (s[0] shl 11)) and M32
            s[0] = s[1]; s[1] = s[2]; s[2] = s[3]
            s[3] = (n xor (n ushr 8) xor s[3] xor (s[3] ushr 19)) and M32
            return s[3]
        }
        fun u64(): Long = (u32() shl 32) or u32()
    }

    fun murmur3(data: ByteArray, from: Int = 0, length: Int = data.size): Long {
        val c1 = 0xcc9e2d51L; val c2 = 0x1b873593L
        var h = 0L
        val blocks = length / 4
        for (i in 0 until blocks) {
            var k = le32(data, from + i * 4)
            k = (k * c1) and M32; k = ((k shl 15) or (k ushr 17)) and M32; k = (k * c2) and M32
            h = h xor k; h = ((h shl 13) or (h ushr 19)) and M32; h = (h * 5 + 0xe6546b64L) and M32
        }
        // Bytes past the last whole word are left out, as the save's own hashes do (every region is whole words).
        h = h xor length.toLong()
        h = h xor (h ushr 16); h = (h * 0x85ebca6bL) and M32
        h = h xor (h ushr 13); h = (h * 0xc2b2ae35L) and M32
        return h xor (h ushr 16)
    }

    private fun hashesOk(d: ByteArray) = HASHES.all { (at, n) -> le32(d, at) == murmur3(d, at + 4, n) }

    private fun fixHashes(d: ByteArray) = HASHES.forEach { (at, n) ->
        val h = murmur3(d, at + 4, n)
        for (i in 0 until 4) d[at + i] = (h ushr (8 * i)).toByte()
    }

    private fun find(hay: ByteArray, needle: ByteArray, from: Int = 0): Int {
        outer@ for (i in from..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private fun le32(b: ByteArray, at: Int): Long =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)

    private const val M32 = 0xFFFFFFFFL
}
