package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uploaded tile pictures (ArtUpload.kt): what is taken, which ones move, how big, and where they are named. */
class ArtUploadTest {
    private fun bytes(vararg parts: Any): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (p in parts) when (p) {
            is String -> out.write(p.toByteArray(Charsets.US_ASCII))
            is Int -> out.write(p)
            is ByteArray -> out.write(p)
        }
        return out.toByteArray().copyOf(ArtUpload.HEAD)
    }

    private val png = bytes(0x89, "PNG", 0x0D, 0x0A, 0x1A, 0x0A)
    private val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0)
    private fun gif(w: Int, h: Int) = bytes("GIF89a", w and 0xFF, w shr 8, h and 0xFF, h shr 8)
    private val stillWebp = bytes("RIFF", 0, 0, 0, 0, "WEBP", "VP8 ")
    private fun extendedWebp(flags: Int) = bytes("RIFF", 0, 0, 0, 0, "WEBP", "VP8X", 10, 0, 0, 0, flags)
    private val heic = bytes(0, 0, 0, 0x18, "ftypheic")

    @Test fun picturesAreKnownByTheirFirstBytes() {
        assertEquals(ArtUpload.Kind.Png, ArtUpload.kind(png))
        assertEquals(ArtUpload.Kind.Jpeg, ArtUpload.kind(jpeg))
        assertEquals(ArtUpload.Kind.Gif, ArtUpload.kind(gif(10, 10)))
        assertEquals(ArtUpload.Kind.Webp, ArtUpload.kind(stillWebp))
        assertEquals(ArtUpload.Kind.Heif, ArtUpload.kind(heic))
        assertNull(ArtUpload.kind(bytes("%PDF-1.7")))
        assertNull(ArtUpload.kind(ByteArray(0)))
    }

    @Test fun anAnimatedWebpSaysSoInItsFlags() {
        assertEquals(ArtUpload.Kind.AnimatedWebp, ArtUpload.kind(extendedWebp(0x02)))
        assertEquals(ArtUpload.Kind.Webp, ArtUpload.kind(extendedWebp(0x10)))
        assertTrue(ArtUpload.Kind.AnimatedWebp.animated)
        assertTrue(ArtUpload.Kind.Gif.animated)
        assertFalse(ArtUpload.Kind.Png.animated)
    }

    @Test fun aGifsSizeComesFromItsHeader() {
        assertEquals(320 to 200, ArtUpload.gifSize(gif(320, 200)))
        assertNull(ArtUpload.gifSize(png))
    }

    @Test fun stillPicturesOfAnySizeComeInAndAreScaledDown() {
        assertNull(ArtUpload.check(ArtUpload.Kind.Jpeg, 12L shl 20, 6000, 4000))
        assertEquals(ArtUpload.Problem.TooBig, ArtUpload.check(ArtUpload.Kind.Png, ArtUpload.MAX_STILL_BYTES + 1))
        assertEquals(768 to 512, ArtUpload.fit(6000, 4000, ArtUpload.ICON_SIDE))
        assertEquals(1080 to 1920, ArtUpload.fit(2160, 3840, ArtUpload.HERO_SIDE))
        // Never scaled up.
        assertEquals(200 to 100, ArtUpload.fit(200, 100, ArtUpload.ICON_SIDE))
    }

    @Test fun animationsMustAlreadyBeSmall() {
        assertNull(ArtUpload.check(ArtUpload.Kind.Gif, 3L shl 20, 480, 480))
        assertEquals(ArtUpload.Problem.TooBig, ArtUpload.check(ArtUpload.Kind.Gif, ArtUpload.MAX_ANIMATED_BYTES + 1, 100, 100))
        assertEquals(ArtUpload.Problem.TooLarge, ArtUpload.check(ArtUpload.Kind.AnimatedWebp, 1L shl 20, 2048, 1024))
    }

    @Test fun nothingThatIsntAPictureGetsIn() {
        assertEquals(ArtUpload.Problem.NotAPicture, ArtUpload.check(null, 1000))
        assertEquals(ArtUpload.Problem.Empty, ArtUpload.check(ArtUpload.Kind.Png, 0))
    }

    @Test fun uploadsAreNamedSafelyForTheirTileAndSlot() {
        val name = ArtUpload.fileName("folder:a b/../c", "icon", ArtUpload.Kind.Gif, 1_234_567_890L)
        assertEquals("folder_a_b_.._c-icon-567890.gif", name)
        assertFalse('/' in name)
    }
}
