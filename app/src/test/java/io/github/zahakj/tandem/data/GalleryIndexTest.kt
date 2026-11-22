package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The community gallery's index.json, search, and the links a QR code or a web page opens Tandem with. */
class GalleryIndexTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sha = "a".repeat(64)

    private fun entry(id: String, extra: String = "") = """
        {"id":"$id","name":"Look $id","author":"Pip","description":"Soft and minty","version":1,
         "preview":"looks/$id/preview.png","file":"looks/$id/$id.tandem","sha256":"$sha","size":12345,
         "tags":["Pastel","dark"],"added":"2026-11-0${id.length % 9}"$extra}
    """.trimIndent()

    private fun index(vararg entries: String) = """{"format":"tandem-gallery","version":1,"looks":[${entries.joinToString(",")}]}"""

    @Test fun parsesTheTemplatesEmptyIndex() {
        val text = listOf("tools/gallery-template/index.json", "../tools/gallery-template/index.json").map(::File).first { it.isFile }.readText()
        assertEquals(emptyList<GalleryEntry>(), GalleryIndex.parse(text))
    }

    @Test fun parsesEntries() {
        val got = GalleryIndex.parse(index(entry("mint"), entry("night-owl")))
        // Newest first: mint was added on the 4th, night-owl on the 0th.
        assertEquals(listOf("mint", "night-owl"), got.map { it.id })
        val mint = got.first()
        assertEquals("Look mint", mint.name)
        assertEquals("Pip", mint.author)
        assertEquals("looks/mint/mint.tandem", mint.file)
        assertEquals(12345L, mint.size)
        assertEquals(listOf("pastel", "dark"), mint.tags)
        assertTrue(mint.supported)
    }

    @Test fun leavesOutEntriesThatDontFit() {
        val bad = listOf(
            entry("../etc"),
            """{"id":"x","name":"n","file":"../../x.tandem","sha256":"$sha","size":10}""",
            entry("Upper"),
            """{"id":"nosha","name":"n","file":"looks/nosha/n.tandem","sha256":"xyz","size":1}""",
            """{"id":"huge","name":"n","file":"looks/huge/n.tandem","sha256":"$sha","size":${LookFile.MAX_FILE + 1}}""",
            """{"id":"elsewhere","name":"n","file":"looks/other/n.tandem","sha256":"$sha","size":10}""",
            """{"id":"noname","name":"  ","file":"looks/noname/n.tandem","sha256":"$sha","size":10}""",
            """{"id":"broken","name":42}""",
        )
        val got = GalleryIndex.parse(index(*(bad + entry("good")).toTypedArray()))
        assertEquals(listOf("good"), got.map { it.id })
    }

    @Test fun aPreviewOutsideItsFolderIsDropped() {
        val got = GalleryIndex.parse(index(entry("a").replace("looks/a/preview.png", "https://evil.example/p.png")))
        assertEquals("", got.single().preview)
    }

    @Test fun newerLooksAreMarked() {
        val got = GalleryIndex.parse(index(entry("next").replace("\"version\":1", "\"version\":9")))
        assertTrue(!got.single().supported)
    }

    @Test fun otherIndexesAreRefused() {
        fun problem(text: String) = try { GalleryIndex.parse(text); null } catch (e: LookFileException) { e.problem }
        assertEquals(LookProblem.NewerVersion, problem("""{"format":"tandem-gallery","version":2,"looks":[]}"""))
        assertEquals(LookProblem.NotALook, problem("""{"format":"something","version":1}"""))
        assertEquals(LookProblem.BadJson, problem("<html>404</html>"))
    }

    @Test fun searchAndTags() {
        val all = GalleryIndex.parse(index(entry("mint"), entry("night", ""","tags":["dark","retro"]""").replace(""""tags":["Pastel","dark"],""", "")))
        assertEquals(listOf("dark", "pastel", "retro"), GalleryIndex.tags(all))
        assertEquals(listOf("mint"), GalleryIndex.search(all, "", "pastel").map { it.id })
        assertEquals(listOf("night"), GalleryIndex.search(all, "look NIGHT", null).map { it.id })
        assertEquals(2, GalleryIndex.search(all, "pip", "dark").size)
        assertEquals(0, GalleryIndex.search(all, "zelda", null).size)
    }

    @Test fun links() {
        assertEquals(GalleryUrls.Link.Gallery("mint"), GalleryUrls.parse("tandem://look?id=mint"))
        assertEquals(GalleryUrls.Link.Gallery("mint"), GalleryUrls.parse(GalleryUrls.page("mint")))
        assertEquals(GalleryUrls.Link.Gallery("mint"), GalleryUrls.parse(GalleryUrls.deepLink("mint")))
        assertEquals(
            GalleryUrls.Link.Web("https://example.org/a%20b.tandem"),
            GalleryUrls.parse("tandem://look?url=https%3A%2F%2Fexample.org%2Fa%2520b.tandem"),
        )
        // Only https, never a login in the address, never another app's link.
        assertNull(GalleryUrls.parse("tandem://look?url=http%3A%2F%2Fexample.org%2Fa.tandem"))
        assertNull(GalleryUrls.parse("tandem://look?url=file%3A%2F%2F%2Fsdcard%2Fa.tandem"))
        assertNull(GalleryUrls.parse("tandem://look?url=https%3A%2F%2Fuser%3Apass%40example.org%2Fa.tandem"))
        assertNull(GalleryUrls.parse("tandem://look?id=..%2F..%2Fetc"))
        assertNull(GalleryUrls.parse("tandem://other?id=mint"))
        assertNull(GalleryUrls.parse("https://evil.example/tandem-gallery/look/?id=mint"))
        assertNull(GalleryUrls.parse("https://zahakj.github.io/tandem-gallery/look/?url=https%3A%2F%2Fexample.org%2Fa.tandem"))
        assertNull(GalleryUrls.parse("not a link"))
    }

    @Test fun addressesComeFromTheOneRepository() {
        assertEquals("https://raw.githubusercontent.com/ZahakJ/tandem-gallery/main/index.json", GalleryUrls.index)
        assertEquals("https://zahakj.github.io/tandem-gallery/look/?id=mint", GalleryUrls.page("mint"))
        val form = GalleryUrls.shareForm("Mint & night", "Pip", "Soft")
        assertTrue(form, form.startsWith("https://github.com/ZahakJ/tandem-gallery/issues/new?template=share-look.yml"))
        assertTrue(form, form.contains("name=Mint%20%26%20night"))
    }

    @Test fun downloadsAreHttpsOnly() {
        val d = LookDownloads(tmp.root, "test") { fail("the gate opens only for https") }
        for (url in listOf("http://example.org/a.tandem", "file:///sdcard/a.tandem", "ftp://x/a")) {
            try {
                d.file(url, File(tmp.root, "a"))
                fail("$url was fetched")
            } catch (e: LookFileException) { assertEquals(LookProblem.BadPath, e.problem) }
        }
    }

    @Test fun privateModeStopsDownloadsBeforeAnythingIsSent() {
        class Offline : java.io.IOException()
        val d = LookDownloads(tmp.root, "test") { throw Offline() }
        try {
            d.index()
            fail()
        } catch (_: Offline) {
        }
    }
}
