package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeanutsNativeTest {
    @Test fun readsTheLibraryByGameFile() {
        val text = """{"library":[
            {"title_id":"0100A6301214E000","name":"Fire Emblem Engage","state":"ready","game":"/sdcard/ROMs/switch/FE.nsp","has_companion":true},
            {"title_id":"0100300012F2A000","name":"Advance Wars","state":"not_in_app","game":"/sdcard/ROMs/switch/AW.nsp","has_companion":false},
            {"title_id":"x","name":"No file","state":"build","game":""}]}"""
        val lib = PeanutsNative.parseLibrary(text)
        assertEquals(2, lib.size)
        assertEquals("ready", lib.getValue("/sdcard/ROMs/switch/FE.nsp").state)
        assertEquals(true, lib.getValue("/sdcard/ROMs/switch/FE.nsp").companion)
    }

    @Test fun aRunningBuildShowsItsStep() {
        assertEquals("Fire Emblem Engage" to "Import · 31 %", PeanutsNative.parseBuild("""{"build":{"running":true,"name":"Fire Emblem Engage","step":"import","percent":31}}"""))
        assertNull(PeanutsNative.parseBuild("""{"build":null}"""))
        assertNull(PeanutsNative.parseBuild("not json"))
    }

    @Test fun findsAnImportedGameByItsFolderOrName() {
        val e = PeanutsNative.Entry("id", "Fire Emblem: Three Houses", "ready", true)
        val lib = mapOf("/s/switch/FE3H dump/FE3H dump.nup" to e)
        assertEquals(e, PeanutsNative.match(lib, "/s/switch/FE3H dump.nsp", "Something else"))
        assertEquals(e, PeanutsNative.match(lib, "/s/switch/other.nsp", "Fire Emblem Three Houses"))
        assertNull(PeanutsNative.match(lib, "/s/switch/other.nsp", "Celeste"))
    }

    @Test fun readsTheAnswerToItsOwnCommand() {
        val text = """{"last_result":{"id":"t1","ok":false,"pending":false,"message":"This version is not one of this app's versions"}}"""
        assertEquals(false to "This version is not one of this app's versions", PeanutsNative.parseAnswer(text, "t1"))
        assertNull(PeanutsNative.parseAnswer(text, "t2"))
    }
}
