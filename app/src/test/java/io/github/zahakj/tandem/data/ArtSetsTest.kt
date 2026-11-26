package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tandem's art sets (ArtSets.kt): which art a system shows, and how the choices are kept and shared. */
class ArtSetsTest {
    @Test fun noSetAnywhereLeavesThePackOrTheDrawnIcon() {
        assertEquals(ArtPick(ArtSource.Pack), ArtSets.pick(null, null, null, packHas = true))
        assertEquals(ArtPick(ArtSource.Drawn), ArtSets.pick(null, null, null, packHas = false))
    }

    @Test fun theGlobalSetBeatsThePack() {
        assertEquals(ArtPick(ArtSource.Set, set = ArtSets.NEON), ArtSets.pick(null, null, ArtSets.NEON, packHas = true))
    }

    @Test fun aSystemsOwnSetBeatsTheGlobalOne() {
        assertEquals(ArtPick(ArtSource.Set, set = ArtSets.PAPER), ArtSets.pick(null, ArtSets.PAPER, ArtSets.NEON, packHas = true))
        assertEquals(ArtSets.MONO, ArtSets.setFor(ArtSets.MONO, null))
    }

    @Test fun packOnOneSystemTurnsTheGlobalSetOffThere() {
        assertNull(ArtSets.setFor(ArtSets.PACK, ArtSets.PASTEL))
        assertEquals(ArtPick(ArtSource.Pack), ArtSets.pick(null, ArtSets.PACK, ArtSets.PASTEL, packHas = true))
        assertEquals(ArtPick(ArtSource.Drawn), ArtSets.pick(null, ArtSets.PACK, ArtSets.PASTEL, packHas = false))
    }

    @Test fun anUploadBeatsEverySet() {
        val up = "/storage/emulated/0/ROMs/tandem/art/folders/s_gba-hero.png"
        assertEquals(ArtPick(ArtSource.Upload, file = up), ArtSets.pick(up, ArtSets.PAPER, ArtSets.NEON, packHas = true))
        assertEquals(ArtPick(ArtSource.Upload, file = up), ArtSets.pick(up, null, null, packHas = false))
    }

    @Test fun unknownSetIdsAreIgnored() {
        // A set from a newer Tandem, or a hand-edited file: as if nothing were chosen at that level.
        assertEquals(ArtSets.NEON, ArtSets.setFor("sparkly", ArtSets.NEON))
        assertNull(ArtSets.setFor(null, "sparkly"))
        assertFalse(ArtSets.isSet(ArtSets.PACK))
        assertTrue(ArtSets.isChoice(ArtSets.PACK))
    }

    @Test fun uploadsCountOnlyWhenTheirSlotShowsThem() {
        val st = FolderStyle(icon = FolderStyle.ICON_FILE, iconFile = "/a/icon.png", heroFile = "/a/hero.png", logo = FolderStyle.LOGO_FILE, logoFile = "/a/logo.png")
        assertEquals("/a/icon.png", ArtSets.upload(st, "icon"))
        // A hero file kept from before, with the hero set back to auto: not shown, so the set's hero is.
        assertNull(ArtSets.upload(st, "hero"))
        assertEquals("/a/logo.png", ArtSets.upload(st, "logo"))
        assertNull(ArtSets.upload(null, "icon"))
    }

    /** A system's set lives in its tile's style in icon-grid.json and survives the round trip; older files have none. */
    @Test fun aSystemsSetIsKeptWithItsStyle() {
        val config = IconGridConfig(version = GridTree.SHORTCUTS)
            .withStyle("s:gba", FolderStyle(artSet = ArtSets.NEON))
            .withStyle("s:psx", FolderStyle(artSet = ArtSets.PACK, shape = FolderStyle.SHAPE_CIRCLE))
        val back = GridJson.decode(GridJson.encode(config))
        assertEquals(ArtSets.NEON, back.styleOf("s:gba").artSet)
        assertEquals(ArtSets.PACK, back.styleOf("s:psx").artSet)
        assertFalse(back.styleOf("s:gba").isDefault)
        assertNull(GridJson.decode("""{"styles":{"s:gba":{"shape":"rounded"}}}""").styleOf("s:gba").artSet)
        // The set is part of what the icon's bitmap cache keys on.
        assertFalse(FolderStyle(artSet = ArtSets.NEON).iconHash() == FolderStyle(artSet = ArtSets.PAPER).iconHash())
    }

    @Test fun theGlobalSetIsKeptInTheLookAndOldSettingsHaveNone() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val look = Look(artSet = ArtSets.PAPER)
        assertEquals(ArtSets.PAPER, json.decodeFromString(Look.serializer(), json.encodeToString(Look.serializer(), look)).artSet)
        assertNull(json.decodeFromString(Look.serializer(), """{"theme":"paper"}""").artSet)
    }

    @Test fun perSystemSetsAreReadFromSystemTilesOnly() {
        val styles = mapOf(
            "s:gba" to FolderStyle(artSet = ArtSets.NEON),
            "s:snes" to FolderStyle(shape = FolderStyle.SHAPE_CIRCLE),
            "folder:x" to FolderStyle(artSet = ArtSets.MONO),
            "s:psx" to FolderStyle(artSet = "sparkly"),
        )
        assertEquals(mapOf("gba" to ArtSets.NEON), ArtSets.perSystem(styles))
    }

    @Test fun applyingPerSystemSetsKeepsTheRestOfEachStyle() {
        val styles = mapOf("s:gba" to FolderStyle(shape = FolderStyle.SHAPE_CIRCLE, iconFile = "/mine.png", icon = FolderStyle.ICON_FILE))
        val out = ArtSets.applyPerSystem(styles, mapOf("gba" to ArtSets.PAPER, "nes" to ArtSets.PACK))
        assertEquals(FolderStyle.SHAPE_CIRCLE, out["s:gba"]?.shape)
        assertEquals("/mine.png", out["s:gba"]?.iconFile)
        assertEquals(ArtSets.PAPER, out["s:gba"]?.artSet)
        assertEquals(ArtSets.PACK, out["s:nes"]?.artSet)
    }

    @Test fun sharedLooksCarrySetIdsAndNothingElse() {
        val clean = SharedLook(
            artSet = "sparkly",
            systemArtSets = mapOf("gba" to ArtSets.NEON, "../etc" to ArtSets.MONO, "snes" to "/sdcard/x.png", "psx" to ArtSets.PACK),
        ).sanitized()
        assertNull(clean.artSet)
        assertEquals(mapOf("gba" to ArtSets.NEON, "psx" to ArtSets.PACK), clean.systemArtSets)
        // "off" turns a set off on import; a missing field leaves the setting alone.
        assertNull(SharedLook(artSet = ArtSets.OFF).sanitized().applyTo(Look(artSet = ArtSets.NEON)).artSet)
        assertEquals(ArtSets.NEON, SharedLook().applyTo(Look(artSet = ArtSets.NEON)).artSet)
        assertEquals(ArtSets.MONO, SharedLook(artSet = ArtSets.MONO).sanitized().applyTo(Look()).artSet)
    }

    @Test fun exportingWritesTheSetsInUse() {
        val shared = SharedLook.of(Look(), Settings(), ExtrasSettings(), "theme", "theme", systemSets = mapOf("gba" to ArtSets.PAPER))
        assertEquals(ArtSets.OFF, shared.artSet)
        assertEquals(mapOf("gba" to ArtSets.PAPER), shared.systemArtSets)
        assertEquals(ArtSets.NEON, SharedLook.of(Look(artSet = ArtSets.NEON), Settings(), ExtrasSettings(), "theme", "theme").artSet)
    }
}
