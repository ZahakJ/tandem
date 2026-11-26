package io.github.zahakj.tandem.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Real system art (SystemArt.kt): where each system is in each source, reading what the sources answer (made-up
 * answers in their documented shapes; no real picture or logo anywhere in the tests), and which picture a system shows.
 */
class SystemArtTest {
    private fun o(id: String) = WebOption.parse(id)!!

    // ---- Platform mapping ---------------------------------------------------------------------------------------

    @Test fun systemsMapToEachSource() {
        assertEquals("Q19610114", ScrapeSystems.wikidata(listOf("switch")))
        assertEquals(225, ScrapeSystems.screenScraper(listOf("switch")))
        assertEquals(130, ScrapeSystems.igdb(listOf("switch")))
        assertNull(ScrapeSystems.libretro(listOf("switch")))
        assertEquals("Nintendo - Game Boy", ScrapeSystems.libretro(listOf("gb")))
        assertEquals("Q186437", ScrapeSystems.wikidata(listOf("gb")))
    }

    @Test fun aliasesFindTheSameSystem() {
        // Cocoon's and ES-DE's folder names, and LaunchBox's spelling of the Vita.
        assertEquals(ScrapeSystems.wikidata(listOf("psvita")), ScrapeSystems.wikidata(listOf("vita")))
        assertEquals("Sony Playstation Vita", ScrapeSystems.launchBox(listOf("vita")))
        assertEquals(ScrapeSystems.wikidata(listOf("n3ds")), ScrapeSystems.wikidata(listOf("3ds")))
        assertEquals(ScrapeSystems.wikidata(listOf("genesis")), ScrapeSystems.wikidata(listOf("megadrive")))
        assertEquals(ScrapeSystems.wikidata(listOf("gc")), ScrapeSystems.wikidata(listOf("gamecube")))
        // The first id a system goes by that is known wins: its own, then its art and ROM folders.
        assertEquals("Q170323", ScrapeSystems.wikidata(listOf("my_ds_folder", "nds")))
    }

    @Test fun everyWikidataIdIsWellFormed() {
        val ids = File(assets(), "platforms.json").readText().let { Json.parseToJsonElement(it) as JsonObject }
            .let { (it["platforms"] as JsonArray).map { p -> (p as JsonObject)["id"]!!.jsonPrimitive.content } }
        var withArt = 0
        for (id in ids) {
            val q = ScrapeSystems.wikidata(listOf(id))
            if (q != null) assertTrue("$id: $q", SystemArt.isWikidataId(q))
            if (q != null || ScrapeSystems.libretro(listOf(id)) != null || ScrapeSystems.screenScraper(listOf(id)) != null) withArt++
        }
        // Nearly every console and computer has a source; ports, videos and books have none.
        assertTrue("only $withArt of ${ids.size}", withArt >= 80)
    }

    private fun assets(): File = listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.isDirectory }

    // ---- Options --------------------------------------------------------------------------------------------------

    @Test fun optionsAreNamedAfterTheirFiles() {
        assertEquals(WebOption(WebSource.Wikimedia, WebKind.Photo), WebOption.ofFile("wiki-photo.jpg"))
        assertEquals(WebOption(WebSource.ScreenScraper, WebKind.Logo), WebOption.ofFile("ss-logo.png"))
        assertEquals("ra-icon", WebOption(WebSource.RetroArch, WebKind.Icon).id)
        assertNull(WebOption.ofFile("credits.json"))
        assertNull(WebOption.ofFile(".wiki-photo.jpg.part"))
        assertNull(WebOption.ofFile("nope-photo.jpg"))
        assertNull(WebOption.ofFile("wiki-selfie.jpg"))
        assertNull(WebOption.parse("../../x"))
    }

    @Test fun eachSlotTakesOnlyWhatFitsIt() {
        val all = WebKind.entries.flatMap { k -> WebSource.entries.map { WebOption(it, k) } }
        // A title floats over a hero: logos and icons only.
        assertEquals(setOf(WebKind.Logo, WebKind.Icon), SystemArt.forSlot(ArtSlot.Logo, all).map { it.kind }.toSet())
        // An icon is never a cut-down backdrop.
        val icons = SystemArt.forSlot(ArtSlot.Icon, all).map { it.kind }.toSet()
        assertFalse(WebKind.Fanart in icons || WebKind.Background in icons || WebKind.Banner in icons)
        // Best first: by kind, then ScreenScraper before Wikimedia.
        val hero = SystemArt.forSlot(ArtSlot.Hero, listOf(o("wiki-photo"), o("ss-photo"), o("ss-fanart")))
        assertEquals(listOf("ss-fanart", "ss-photo", "wiki-photo"), hero.map { it.id })
    }

    @Test fun stylesPickWhatTheySay() {
        val have = listOf(o("wiki-photo"), o("wiki-logo"), o("ss-fanart"), o("ss-logo"), o("ra-icon"))
        assertEquals("wiki-photo", SystemArt.styleChoice(SystemArt.PHOTOS, ArtSlot.Icon, have)?.id)
        assertEquals("wiki-photo", SystemArt.styleChoice(SystemArt.PHOTOS, ArtSlot.Hero, have)?.id)
        assertEquals("ss-logo", SystemArt.styleChoice(SystemArt.LOGOS, ArtSlot.Icon, have)?.id)
        assertEquals("ss-fanart", SystemArt.styleChoice(SystemArt.FANART, ArtSlot.Hero, have)?.id)
        assertEquals("ss-logo", SystemArt.styleChoice(SystemArt.FANART, ArtSlot.Logo, have)?.id)
        // Nothing that fits: null, so the set or the pack shows.
        assertNull(SystemArt.styleChoice(SystemArt.PHOTOS, ArtSlot.Icon, listOf(o("ss-fanart"))))
        assertNull(SystemArt.styleChoice("glitter", ArtSlot.Icon, have))
        assertNull(SystemArt.styleChoice(null, ArtSlot.Icon, have))
    }

    // ---- Precedence -----------------------------------------------------------------------------------------------

    @Test fun aSystemsOwnPickBeatsEverything() {
        val have = listOf(o("wiki-photo"), o("ss-logo"))
        assertEquals("ss-logo", SystemArt.choose(ArtSlot.Icon, "ss-logo", ArtSets.NEON, SystemArt.PHOTOS, have)?.id)
        // A pick whose picture was cleared away: as if there were none.
        assertEquals("wiki-photo", SystemArt.choose(ArtSlot.Icon, "ss-photo", null, SystemArt.PHOTOS, have)?.id)
        // A pick that doesn't suit the slot (a photo as a title) is ignored.
        assertNull(SystemArt.choose(ArtSlot.Logo, "wiki-photo", null, null, have))
    }

    @Test fun aSystemsOwnSetOrPackBeatsRealArtForEverySystem() {
        val have = listOf(o("wiki-photo"))
        assertNull(SystemArt.choose(ArtSlot.Icon, null, ArtSets.PAPER, SystemArt.PHOTOS, have))
        assertNull(SystemArt.choose(ArtSlot.Icon, null, ArtSets.PACK, SystemArt.PHOTOS, have))
        assertEquals("wiki-photo", SystemArt.choose(ArtSlot.Icon, null, null, SystemArt.PHOTOS, have)?.id)
        // An unknown set id (a newer Tandem) doesn't block it.
        assertEquals("wiki-photo", SystemArt.choose(ArtSlot.Icon, null, "sparkly", SystemArt.PHOTOS, have)?.id)
    }

    @Test fun theWholeOrderOfPrecedence() {
        val up = "/storage/emulated/0/ROMs/tandem/art/folders/s_gba-icon.png"
        // Uploads > the system's web pick > its own set > web for every system > the global set > pack > drawn.
        assertEquals(ArtSource.Upload, ArtSets.pick(up, ArtSets.NEON, ArtSets.PAPER, true, webMine = "wiki-photo", webGlobal = "ss-logo").source)
        assertEquals(ArtPick(ArtSource.Web, web = "wiki-photo"), ArtSets.pick(null, ArtSets.NEON, ArtSets.PAPER, true, webMine = "wiki-photo", webGlobal = "ss-logo"))
        assertEquals(ArtPick(ArtSource.Set, set = ArtSets.NEON), ArtSets.pick(null, ArtSets.NEON, ArtSets.PAPER, true, webGlobal = "ss-logo"))
        assertEquals(ArtPick(ArtSource.Web, web = "ss-logo"), ArtSets.pick(null, null, ArtSets.PAPER, true, webGlobal = "ss-logo"))
        assertEquals(ArtPick(ArtSource.Set, set = ArtSets.PAPER), ArtSets.pick(null, null, ArtSets.PAPER, true))
        assertEquals(ArtPick(ArtSource.Pack), ArtSets.pick(null, ArtSets.PACK, ArtSets.PAPER, true, webGlobal = null))
        assertEquals(ArtPick(ArtSource.Drawn), ArtSets.pick(null, null, null, false))
    }

    @Test fun picksLiveInTheSystemsStyleAndSurviveTheRoundTrip() {
        val st = SystemArt.withChoice(FolderStyle(icon = FolderStyle.ICON_FILE, iconFile = "/a/icon.png", hero = FolderStyle.HERO_FILE, heroFile = "/a/hero.png"), ArtSlot.Icon, "wiki-photo")
        assertEquals(FolderStyle.ICON_AUTO, st.icon)
        assertNull(st.iconFile)
        assertEquals("wiki-photo", SystemArt.mine(st, ArtSlot.Icon))
        // The other slots keep what they had.
        assertEquals("/a/hero.png", st.heroFile)
        assertNull(SystemArt.mine(st.copy(webHero = "ss-fanart"), ArtSlot.Hero))
        val config = IconGridConfig(version = GridTree.SHORTCUTS).withStyle("s:gba", st)
        assertEquals("wiki-photo", GridJson.decode(GridJson.encode(config)).styleOf("s:gba").webIcon)
        // Older files have none, and a newer one's unknown fields are ignored.
        assertNull(GridJson.decode("""{"styles":{"s:gba":{"artSet":"neon","sparkle":1}}}""").styleOf("s:gba").webIcon)
        // The icon's cache key follows the pick.
        assertFalse(st.iconHash() == st.copy(webIcon = "ss-photo").iconHash())
    }

    @Test fun folderNamesAreSafe() {
        assertEquals("gba", SystemArt.folderName("gba"))
        assertEquals("psvita", SystemArt.folderName("PSVita"))
        val odd = SystemArt.folderName("../../etc")
        assertFalse(odd.contains('/') || odd.contains('.'))
        assertFalse(SystemArt.folderName("a b") == SystemArt.folderName("a_b"))
    }

    // ---- Wikidata and Commons -------------------------------------------------------------------------------------

    @Test fun onlyWellFormedWikidataIdsAreSent() {
        val q = SystemArt.wikidataQuery(listOf("Q42", "Q7", "Q42", "Q1 } DELETE {", "q5", "Q0"))!!
        assertTrue(q.contains("wd:Q7 ") && q.contains("wd:Q42 "))
        assertEquals(1, Regex("wd:Q42").findAll(q).count())
        assertFalse(q.contains("DELETE") || q.contains("q5") || q.contains("Q0"))
        assertNull(SystemArt.wikidataQuery(listOf("nope")))
    }

    @Test fun readsWikidataAnswers() {
        val text = """{"head":{"vars":["i","p","g"]},"results":{"bindings":[
            {"i":{"type":"uri","value":"http://www.wikidata.org/entity/Q100"},
             "p":{"type":"uri","value":"http://commons.wikimedia.org/wiki/Special:FilePath/Test%20Console%20Front.jpg"},
             "g":{"type":"uri","value":"http://commons.wikimedia.org/wiki/Special:FilePath/Test%2BLogo.svg"}},
            {"i":{"type":"uri","value":"http://www.wikidata.org/entity/Q100"},
             "p":{"type":"uri","value":"http://commons.wikimedia.org/wiki/Special:FilePath/Second.jpg"}},
            {"i":{"type":"uri","value":"http://www.wikidata.org/entity/Q200"},
             "g":{"type":"uri","value":"http://commons.wikimedia.org/wiki/Special:FilePath/Only%20Logo.svg"}},
            {"i":{"type":"uri","value":"http://www.wikidata.org/entity/Q300"}}
        ]}}"""
        val got = SystemArt.wikidataImages(text)
        assertEquals("Test Console Front.jpg" to "Test+Logo.svg", got["Q100"])
        assertEquals(null to "Only Logo.svg", got["Q200"])
        assertFalse("Q300" in got)
        assertTrue(SystemArt.wikidataImages("not json").isEmpty())
    }

    @Test fun readsCommonsAnswers() {
        assertEquals("File:Test console.jpg", SystemArt.commonsTitle("test_console.jpg"))
        val text = """{"batchcomplete":true,"query":{
            "normalized":[{"fromencoded":false,"from":"File:Test_console.jpg","to":"File:Test console.jpg"}],
            "pages":[
              {"ns":6,"title":"File:Missing.jpg","missing":true},
              {"ns":6,"title":"File:Test console.jpg","imageinfo":[{
                "thumburl":"https://upload.example.org/thumb/1280px-Test_console.jpg","url":"https://upload.example.org/Test_console.jpg",
                "descriptionurl":"https://commons.example.org/wiki/File:Test_console.jpg",
                "extmetadata":{"Artist":{"value":"<a href=\"//x\">Jane &amp; Co</a>"},"LicenseShortName":{"value":"Public domain"}}}]},
              {"ns":6,"title":"File:Plain.svg","imageinfo":[{"thumburl":"http://insecure.example.org/x.png"}]}
            ]}}"""
        val got = SystemArt.commonsImages(text)
        val img = got.getValue("File:Test console.jpg")
        assertEquals("https://upload.example.org/thumb/1280px-Test_console.jpg", img.url)
        assertEquals("Public domain", img.licence)
        assertEquals("Jane & Co", img.author)
        assertEquals(img, got["File:Test_console.jpg"])
        // Plain http is never fetched.
        assertFalse("File:Plain.svg" in got)
        assertFalse("File:Missing.jpg" in got)
    }

    // ---- ScreenScraper --------------------------------------------------------------------------------------------

    @Test fun readsScreenScraperSystems() {
        val text = """{"header":{},"response":{"systemes":[
            {"id":12,"noms":{"nom_eu":"Test Boy"},"medias":[
              {"type":"wheel","parent":"systeme","url":"https://example.org/media?id=12&media=wheel(wor)","region":"wor","format":"png"},
              {"type":"wheel","parent":"systeme","url":"https://example.org/media?id=12&media=wheel(jp)","region":"jp","format":"png"},
              {"type":"photo","parent":"systeme","url":"https://example.org/media?id=12&media=photo(us)","region":"us","format":"png"},
              {"type":"fanart","parent":"systeme","url":"https://example.org/media?id=12&media=fanart","format":"jpg"},
              {"type":"controller","parent":"systeme","url":"https://example.org/media?id=12&media=controller(eu)","region":"eu","format":"png"},
              {"type":"bezel-16-9","parent":"systeme","url":"https://example.org/media?id=12&media=bezel","region":"wor","format":"png"},
              {"type":"photo","parent":"systeme","url":"ftp://bad","region":"wor"}
            ]},
            {"id":"13","medias":[]},
            {"noms":{}}
        ]}}"""
        val systems = SystemArt.ssSystems(text)
        assertEquals(setOf(12, 13), systems.keys)
        val pick = SystemArt.ssPick(systems.getValue(12), "jp")
        assertEquals("https://example.org/media?id=12&media=wheel(jp)", pick[WebKind.Logo])
        assertEquals("https://example.org/media?id=12&media=photo(us)", pick[WebKind.Photo])
        assertEquals("https://example.org/media?id=12&media=fanart", pick[WebKind.Fanart])
        assertEquals("https://example.org/media?id=12&media=controller(eu)", pick[WebKind.Controller])
        assertFalse(WebKind.Banner in pick)
        // World first when the region asked for has none.
        assertEquals("https://example.org/media?id=12&media=wheel(wor)", SystemArt.ssPick(systems.getValue(12), "eu")[WebKind.Logo])
        assertTrue(SystemArt.ssSystems("""{"response":{}}""").isEmpty())
    }

    // ---- IGDB -----------------------------------------------------------------------------------------------------

    @Test fun readsIgdbPlatformLogos() {
        val text = """[{"id":130,"platform_logo":{"id":1,"image_id":"abc123"}},{"id":4},{"id":5,"platform_logo":{"image_id":"../x"}}]"""
        assertEquals(mapOf(130 to "abc123"), SystemArt.igdbLogos(text))
        assertEquals("https://images.igdb.com/igdb/image/upload/t_720p/abc123.png", SystemArt.igdbLogoUrl("abc123"))
    }

    // ---- RetroArch and credits ------------------------------------------------------------------------------------

    @Test fun retroArchIconsAreNamedAfterLibretrosSystems() {
        assertEquals(
            "https://raw.githubusercontent.com/libretro/retroarch-assets/master/xmb/systematic/png/Nintendo%20-%20Game%20Boy.png",
            SystemArt.retroArchIcon("Nintendo - Game Boy"),
        )
    }

    @Test fun creditsRoundTrip() {
        val c = mapOf("wiki-photo" to WebCredit("Wikimedia Commons", "CC BY-SA 4.0", "Jane", "https://commons.example.org/x"), "ra-icon" to WebCredit("RetroArch", "CC BY 4.0"))
        assertEquals(c, SystemArt.decodeCredits(SystemArt.encodeCredits(c)))
        assertTrue(SystemArt.decodeCredits("garbage").isEmpty())
    }
}
