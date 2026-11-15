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

    @Test fun signerMustMatchOrBeInTheRotationHistory() {
        assertTrue(Releases.sameSigner(setOf("a"), setOf("a"), setOf("a")))
        assertTrue(Releases.sameSigner(setOf("a"), setOf("b"), setOf("a", "b")))
        assertFalse(Releases.sameSigner(setOf("debug"), setOf("release"), setOf("release")))
        assertFalse(Releases.sameSigner(emptySet(), emptySet(), emptySet()))
    }
}
