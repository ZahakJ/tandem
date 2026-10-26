package io.github.zahakj.tandem.data

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Discord cover address: only credential-free addresses pass, and GameMeta's new field on disk. */
class CoverUrlTest {
    @Test fun plainCdnAddressesPass() {
        val libretro = "https://thumbnails.libretro.com/Nintendo%20-%20Super%20Nintendo%20Entertainment%20System/Named_Boxarts/Super%20Mario%20World%20(USA).png"
        assertEquals(libretro, CoverUrl.safe(libretro))
        assertEquals("https://cdn2.steamgriddb.com/grid/abc123.png", CoverUrl.safe("https://cdn2.steamgriddb.com/grid/abc123.png"))
        assertEquals("https://images.launchbox-app.com/1234-5678.jpg", CoverUrl.safe(" https://images.launchbox-app.com/1234-5678.jpg "))
        assertEquals("https://images.igdb.com/igdb/image/upload/t_720p/co1abc.jpg", CoverUrl.safe("https://images.igdb.com/igdb/image/upload/t_720p/co1abc.jpg"))
    }

    @Test fun screenScraperIsNeverSent() {
        val ss = "https://neoclone.screenscraper.fr/api2/mediaJeu.php?devid=me&devpassword=secret&softname=Tandem&ssid=user&sspassword=pw&systemeid=4&jeuid=3&media=box-2D(us)"
        assertNull(CoverUrl.safe(ss))
        assertNull(CoverUrl.safe("https://screenscraper.fr/image.php?gameid=3"))
        assertNull(CoverUrl.safe("https://www.screenscraper.fr/medias/1/box.png"))
    }

    @Test fun keyLikeParametersAreRejected() {
        for (q in listOf("key=abc", "api_key=abc", "apikey=abc", "token=abc", "access_token=abc", "client_secret=x", "ssid=u",
            "sspassword=p", "devid=d", "devpassword=d", "X-Amz-Signature=s", "sig=s", "auth=a", "user=u", "w=1&Password=p", "a%5Fkey=x")) {
            assertNull(q, CoverUrl.safe("https://cdn.example.com/cover.png?$q"))
        }
    }

    @Test fun otherQueriesAndFragmentsAreStripped() {
        assertEquals("https://cdn.example.com/cover.png", CoverUrl.safe("https://cdn.example.com/cover.png?width=512&v=3"))
        assertEquals("https://cdn.example.com/cover.png", CoverUrl.safe("https://cdn.example.com/cover.png#top"))
    }

    @Test fun onlyPlainHttpsAddresses() {
        assertNull(CoverUrl.safe(null))
        assertNull(CoverUrl.safe(""))
        assertNull(CoverUrl.safe("http://thumbnails.libretro.com/a/b.png"))
        assertNull(CoverUrl.safe("file:///sdcard/ROMs/downloaded_media/snes/icon/a.png"))
        assertNull(CoverUrl.safe("/sdcard/ROMs/a.png"))
        assertNull(CoverUrl.safe("https://user:pass@cdn.example.com/a.png"))
        assertNull(CoverUrl.safe("https://cdn.example.com/"))
        assertNull(CoverUrl.safe("https://cdn.example.com/a b.png"))
        assertNull(CoverUrl.safe("https://cdn.example.com/" + "a".repeat(CoverUrl.MAX)))
    }

    // --- GameMeta.coverUrl on disk ---------------------------------------------------------------------------------

    @Test fun oldLibraryReadsWithoutCover() {
        val old = """{"snes/Mario.sfc":{"title":"Mario","box":"/sdcard/ROMs/downloaded_media/snes/icon/Mario.png"}}"""
        val m = Store.json.decodeFromString<Map<String, GameMeta>>(old).getValue("snes/Mario.sfc")
        assertEquals("/sdcard/ROMs/downloaded_media/snes/icon/Mario.png", m.box)
        assertNull(m.coverUrl)
    }

    @Test fun coverIsWrittenOnlyWhenKnownAndRoundTrips() {
        assertEquals("""{"title":"Mario"}""", Store.json.encodeToString(GameMeta(title = "Mario")))
        val m = GameMeta(title = "Mario", coverUrl = "https://cdn2.steamgriddb.com/grid/a.png")
        val text = Store.json.encodeToString(m)
        assertEquals("""{"title":"Mario","coverUrl":"https://cdn2.steamgriddb.com/grid/a.png"}""", text)
        assertEquals(m, Store.json.decodeFromString<GameMeta>(text))
    }
}
