package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The updater's pure parts (Updates.kt): comparing versions, reading GitHub's release and SHA256SUMS, the notes. */
class UpdatesTest {
    private val hash = "3f".repeat(32)

    private fun release(tag: String = "v0.4.1", draft: Boolean = false, pre: Boolean = false, apk: String = "tandem-0.4.1.apk") = """
        {"tag_name":"$tag","draft":$draft,"prerelease":$pre,"body":"## New\n- Updates","assets":[
          {"name":"$apk","browser_download_url":"https://github.com/ZahakJ/tandem/releases/download/$tag/$apk"},
          {"name":"SHA256SUMS","browser_download_url":"https://github.com/ZahakJ/tandem/releases/download/$tag/SHA256SUMS"}
        ]}
    """.trimIndent()

    @Test fun versionsCompareByNumberNotText() {
        assertTrue(Releases.compare("0.10.0", "0.9.0") > 0)
        assertTrue(Releases.compare("v0.4.1", "0.4.0") > 0)
        assertTrue(Releases.compare("0.4.0", "0.4.1") < 0)
        assertTrue(Releases.compare("1.0.0", "0.99.99") > 0)
    }

    @Test fun missingNumbersCountAsZero() {
        assertEquals(0, Releases.compare("0.4", "0.4.0"))
        assertEquals(0, Releases.compare("v0.4.0", "0.4.0"))
        assertTrue(Releases.compare("0.4.0.1", "0.4") > 0)
    }

    @Test fun aPreReleaseComesBeforeItsRelease() {
        assertTrue(Releases.compare("0.5.0-rc1", "0.5.0") < 0)
        assertTrue(Releases.compare("0.5.0-rc1", "0.4.9") > 0)
        assertTrue(Releases.compare("0.5.0-rc2", "0.5.0-rc1") > 0)
        // Build metadata doesn't count.
        assertEquals(0, Releases.compare("0.5.0+abc", "0.5.0"))
    }

    @Test fun releaseReadsVersionNotesAndFiles() {
        val r = Releases.parse(release())!!
        assertEquals("0.4.1", r.version)
        assertEquals("tandem-0.4.1.apk", r.apkName)
        assertTrue(r.apkUrl.endsWith("/v0.4.1/tandem-0.4.1.apk"))
        assertTrue(r.sumsUrl.endsWith("/v0.4.1/SHA256SUMS"))
        assertEquals("## New\n- Updates", r.notes)
    }

    @Test fun draftsPreReleasesAndIncompleteReleasesAreSkipped() {
        assertNull(Releases.parse(release(draft = true)))
        assertNull(Releases.parse(release(pre = true)))
        // The APK must be the one named for the tag.
        assertNull(Releases.parse(release(apk = "tandem-0.4.0.apk")))
        assertNull(Releases.parse("""{"tag_name":"v0.4.1","assets":[]}"""))
        assertNull(Releases.parse("""{"message":"Not Found"}"""))
        assertNull(Releases.parse("not json"))
    }

    @Test fun sumsFindTheApkLine() {
        val sums = "${"aa".repeat(32)}  tandem-0.4.0.apk\n$hash  tandem-0.4.1.apk\n"
        assertEquals(hash, Releases.sumFor(sums, "tandem-0.4.1.apk"))
        // Binary-mode lines (`*name`), upper case, Windows line ends.
        assertEquals(hash, Releases.sumFor("${hash.uppercase()} *tandem-0.4.1.apk\r\n", "tandem-0.4.1.apk"))
    }

    @Test fun sumsWithoutTheFileOrWithABadHashAreNull() {
        assertNull(Releases.sumFor("$hash  tandem-0.4.0.apk", "tandem-0.4.1.apk"))
        assertNull(Releases.sumFor("abc123  tandem-0.4.1.apk", "tandem-0.4.1.apk"))
        assertNull(Releases.sumFor("$hash  tandem-0.4.1.apk.sig", "tandem-0.4.1.apk"))
        assertNull(Releases.sumFor("", "tandem-0.4.1.apk"))
    }

    @Test fun notesBecomePlainText() {
        val md = "## What's new\r\n\r\n- **Updates** from [GitHub](https://github.com)\n* `pm install` with Shizuku\n\n\n\nThanks!"
        assertEquals("What's new\n\n• Updates from GitHub\n• pm install with Shizuku\n\nThanks!", Releases.plainNotes(md))
    }

    @Test fun releaseReadsScreenshotsSizeAndDate() {
        val text = """
            {"tag_name":"v0.9.0","published_at":"2026-11-26T15:21:10Z","body":"","assets":[
              {"name":"tandem-0.9.0.apk","size":25165824,"browser_download_url":"https://github.com/ZahakJ/tandem/releases/download/v0.9.0/tandem-0.9.0.apk"},
              {"name":"SHA256SUMS","browser_download_url":"https://github.com/ZahakJ/tandem/releases/download/v0.9.0/SHA256SUMS"},
              {"name":"whatsnew-2-stage.png","browser_download_url":"https://github.com/ZahakJ/tandem/releases/download/v0.9.0/whatsnew-2-stage.png"},
              {"name":"whatsnew-1-lift.webp","browser_download_url":"https://github.com/ZahakJ/tandem/releases/download/v0.9.0/whatsnew-1-lift.webp"},
              {"name":"whatsnew-notes.txt","browser_download_url":"https://github.com/ZahakJ/tandem/releases/download/v0.9.0/whatsnew-notes.txt"},
              {"name":"screenshot.webp","browser_download_url":"https://github.com/ZahakJ/tandem/releases/download/v0.9.0/screenshot.webp"}
            ]}
        """.trimIndent()
        val r = Releases.parse(text)!!
        assertEquals(listOf("whatsnew-1-lift.webp", "whatsnew-2-stage.png"), r.images.map { it.substringAfterLast('/') })
        assertEquals(25165824L, r.apkSize)
        assertEquals("2026-11-26T15:21:10Z", r.published)
        // A release kept by an older Tandem (without these) still reads.
        val old = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(Release.serializer(), """{"version":"0.8.1","notes":"","apkName":"a","apkUrl":"b","sumsUrl":"c"}""")
        assertTrue(old.images.isEmpty())
        assertEquals(0L, old.apkSize)
    }

    @Test fun notesSplitIntoPartsByBoldLinesAndHeadings() {
        val md = """
            **Editing the grid**
            - Y on a tile lifts it in place with Open and Edit. Hold Y to pick it up and move it; X marks more.
            - Upload your own icon, hero or logo, **animated GIF** and WebP included.

            **Layouts**
            - Start › Layout tries every layout live on your own home: L1/R1 to switch, A to keep, B to cancel.

            ## Also:
            * Thock: a new sound pack with deep, muted keyboard thocks.
            * A light buzz on every D-pad move (Settings › Controls › Buttons).
        """.trimIndent()
        val s = Releases.sections(md)
        assertEquals(listOf("Editing the grid", "Layouts", "Also"), s.map { it.title })
        assertEquals(2, s[0].items.size)
        assertEquals("Upload your own icon, hero or logo, animated GIF and WebP included.", s[0].items[1])
        assertEquals(listOf("Thock: a new sound pack with deep, muted keyboard thocks.", "A light buzz on every D-pad move (Settings › Controls › Buttons)."), s[2].items)
    }

    @Test fun notesKeepLeadingTextAndContinuationsAndDropTheRest() {
        val md = "A big one.\r\n\r\n<!-- hidden\nnote -->\n![shot](https://x/y.png)\n---\n" +
            "### Fixes\n1. The `dock` no longer\n   jumps after [a rescan](https://x).\n2) _Faster_ start\n\n**Empty**\n\nThanks!"
        val s = Releases.sections(md)
        assertEquals("", s[0].title)
        assertEquals(listOf("A big one."), s[0].items)
        assertEquals("Fixes", s[1].title)
        assertEquals(listOf("The dock no longer jumps after a rescan.", "Faster start"), s[1].items)
        // A bold line with nothing under it but a paragraph: the paragraph is its point.
        assertEquals("Empty", s[2].title)
        assertEquals(listOf("Thanks!"), s[2].items)
        assertEquals(3, s.size)
        assertTrue(Releases.sections("").isEmpty())
        assertTrue(Releases.sections("**Only a title**").isEmpty())
    }

    @Test fun signerMustMatchOrBeInTheRotationHistory() {
        assertTrue(Releases.sameSigner(setOf("a"), setOf("a"), setOf("a")))
        assertTrue(Releases.sameSigner(setOf("a"), setOf("b"), setOf("a", "b")))
        assertFalse(Releases.sameSigner(setOf("debug"), setOf("release"), setOf("release")))
        assertFalse(Releases.sameSigner(emptySet(), emptySet(), emptySet()))
    }
}
