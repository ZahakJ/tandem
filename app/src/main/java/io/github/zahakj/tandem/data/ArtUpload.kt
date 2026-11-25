package io.github.zahakj.tandem.data

/*
 * Pictures the user brings in for a tile's icon, hero or logo (the artwork studio's Upload your own): what is
 * accepted, how big, and which ones move. Worked out from the first bytes and the size, before anything is decoded;
 * plain Kotlin, so it is unit tested. The copying and the scaling down are in ui/AnimatedArt.kt.
 */
object ArtUpload {
    /** What a file is, by its first bytes. */
    enum class Kind(val extension: String, val animated: Boolean) {
        Png("png", false), Jpeg("jpg", false), Webp("webp", false), AnimatedWebp("webp", true), Gif("gif", true), Heif("heic", false),
    }

    /** Why a file was turned away. */
    enum class Problem { NotAPicture, TooBig, TooLarge, Empty }

    /** The most a still picture may weigh before it is scaled down (it is re-encoded smaller anyway). */
    const val MAX_STILL_BYTES = 24L shl 20

    /** The most an animation may weigh: it is kept as it is (frames aren't re-encoded), so it must already be small. */
    const val MAX_ANIMATED_BYTES = 8L shl 20

    /** The longest side an animation may have: decoding bigger ones on every frame would cost too much. */
    const val MAX_ANIMATED_SIDE = 1024

    /** Still pictures are scaled down to this longest side for an icon or a logo... */
    const val ICON_SIDE = 768

    /** ...and to this for a hero, which fills a screen. */
    const val HERO_SIDE = 1920

    /** How many bytes [kind] needs to see. */
    const val HEAD = 32

    /** The kind of picture [head] (the file's first bytes) starts, or null when it isn't one Tandem takes. */
    fun kind(head: ByteArray): Kind? {
        fun u8(i: Int) = if (i < head.size) head[i].toInt() and 0xFF else -1
        fun ascii(at: Int, s: String) = s.indices.all { u8(at + it) == s[it].code }
        return when {
            u8(0) == 0x89 && ascii(1, "PNG") -> Kind.Png
            u8(0) == 0xFF && u8(1) == 0xD8 && u8(2) == 0xFF -> Kind.Jpeg
            ascii(0, "GIF87a") || ascii(0, "GIF89a") -> Kind.Gif
            ascii(0, "RIFF") && ascii(8, "WEBP") ->
                // An extended WebP says in its flags whether it is animated (bit 1 of the byte after "VP8X" and its size).
                if (ascii(12, "VP8X") && (u8(20) and 0x02) != 0) Kind.AnimatedWebp else Kind.Webp
            ascii(4, "ftyp") && (ascii(8, "heic") || ascii(8, "heix") || ascii(8, "mif1") || ascii(8, "msf1") || ascii(8, "avif")) -> Kind.Heif
            else -> null
        }
    }

    /** The picture's width and height from a GIF's header (the logical screen), or null. */
    fun gifSize(head: ByteArray): Pair<Int, Int>? {
        if (head.size < 10 || kind(head) != Kind.Gif) return null
        fun le16(i: Int) = (head[i].toInt() and 0xFF) or ((head[i + 1].toInt() and 0xFF) shl 8)
        return (le16(6) to le16(8)).takeIf { it.first > 0 && it.second > 0 }
    }

    /**
     * Whether a picture of [kind], [bytes] long and [width] by [height] (0 when not known yet) may come in. Still
     * pictures of any size are fine up to [MAX_STILL_BYTES] (they are scaled down); animations must be small already.
     */
    fun check(kind: Kind?, bytes: Long, width: Int = 0, height: Int = 0): Problem? = when {
        bytes <= 0L -> Problem.Empty
        kind == null -> Problem.NotAPicture
        kind.animated && bytes > MAX_ANIMATED_BYTES -> Problem.TooBig
        !kind.animated && bytes > MAX_STILL_BYTES -> Problem.TooBig
        kind.animated && maxOf(width, height) > MAX_ANIMATED_SIDE -> Problem.TooLarge
        else -> null
    }

    /** The size a still picture is scaled to so its longest side is at most [max] (never scaled up). */
    fun fit(width: Int, height: Int, max: Int): Pair<Int, Int> {
        val long = maxOf(width, height)
        if (long <= max || long <= 0) return width to height
        val s = max.toFloat() / long
        return Math.round(width * s).coerceAtLeast(1) to Math.round(height * s).coerceAtLeast(1)
    }

    /** A file name for [tileKey]'s [slot] ("icon", "hero", "logo"), safe on any file system. */
    fun fileName(tileKey: String, slot: String, kind: Kind, stamp: Long): String =
        tileKey.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60) + "-" + slot + "-" + (stamp % 1_000_000) + "." + kind.extension

}
