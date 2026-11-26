package io.github.zahakj.tandem.data

import io.github.zahakj.tandem.audio.SoundPack
import io.github.zahakj.tandem.ui.AnimationStyle
import io.github.zahakj.tandem.ui.BackdropStyle
import io.github.zahakj.tandem.ui.HighlightStyle
import io.github.zahakj.tandem.ui.IconStyle
import io.github.zahakj.tandem.ui.SurfaceMaterial
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Share my look's file: what goes in (only look settings), what comes out, and files made to cause harm. */
class LookFileTest {
    @get:Rule val tmp = TemporaryFolder()

    // ---- Privacy ----

    /** Every private thing a full setup can hold, each with a word that must never show up in a look file. */
    private val secrets = listOf(
        "SECRET-THEME-FOLDER", "SECRET-PACK", "SECRET-PHOTO", "SECRET-SOUNDS", "SECRET-HIDDEN-FOLDER", "SECRET-GAME-KEY",
        "SECRET-SYSTEM", "/storage/emulated/0",
    )

    private fun privateSettings(): Settings = Settings(
        sort = SortOrder.MostPlayed,
        showHidden = true,
        showTitles = true,
        autoImportDone = true,
        bottomMode = "art",
        reduceMotion = true,
        welcomeDone = true,
        cocoonSetupDone = true,
        look = Look(
            theme = "pack:SECRET-THEME-FOLDER",
            accent = "#12AB34",
            highlight = "outline",
            highlightThickness = 5f,
            focusScale = 1.2f,
            corner = 22f,
            material = "frosted",
            motion = "smooth",
            homeLayout = "grid",
            gridAcross = 9,
            wallTop = "file:/storage/emulated/0/ROMs/tandem/wallpapers/SECRET-PHOTO.jpg",
            wallBottom = "file:/storage/emulated/0/DCIM/SECRET-PHOTO-2.jpg",
            iconPack = "pack:SECRET-PACK",
            packParts = mapOf("pack:SECRET-PACK" to PackParts(colors = false), "cocoon:SECRET-THEME-FOLDER" to PackParts()),
            soundFiles = "SECRET-SOUNDS",
            soundPack = "chip",
            prompts = "playstation",
            swapAB = true,
            singleScreen = true,
            swapScreens = true,
            iconStyle = "neon",
        ),
        hiddenFolders = listOf(HiddenFolder("SECRET-SYSTEM", "SECRET-HIDDEN-FOLDER", listOf("/storage/emulated/0/SECRET-HIDDEN-FOLDER"), listOf("SECRET-GAME-KEY"))),
        batterySaver = BatteryBefore(reduceMotion = true, videoSnaps = false, kenBurns = false, music = false, motion = "SECRET-SOUNDS"),
        watchFolders = false,
        checkUpdates = false,
    )

    private fun export(s: Settings = privateSettings(), extras: ExtrasSettings = ExtrasSettings(floating = false, intensity = 2)): ByteArray {
        val look = SharedLook.of(s.look, s, extras, wallTop = "theme", wallBottom = "starfield")
        val manifest = LookManifest(name = "Mint dream", author = "Pip", created = "2026-11-22", parts = listOf("colours", "layout"), look = look)
        val out = ByteArrayOutputStream()
        LookFile.write(out, manifest, """{"name":"Mint dream","base":"pastel-night"}""", emptyMap())
        return out.toByteArray()
    }

    private fun unzip(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                out[e.name] = String(z.readBytes())
            }
        }
        return out
    }

    @Test fun aFullyPopulatedSetupExportsNoPrivateField() {
        val files = unzip(export())
        assertEquals(setOf(LookFile.MANIFEST, LookFile.THEME), files.keys)
        val all = files.values.joinToString("\n")
        for (s in secrets) assertFalse("$s leaked into the look file", all.contains(s))
        // Settings that aren't the look stay home too, whatever their value.
        for (key in listOf("theme", "iconPack", "packParts", "soundFiles", "hiddenFolders", "showHidden", "batterySaver", "prompts", "swapAB", "singleScreen", "swapScreens", "reduceMotion", "sort", "watchFolders")) {
            assertFalse("$key is in look.json", files.getValue(LookFile.MANIFEST).contains("\"$key\":"))
        }
    }

    @Test fun lookJsonHoldsOnlyAllowListedKeys() {
        val manifest = Json.parseToJsonElement(unzip(export()).getValue(LookFile.MANIFEST)) as JsonObject
        assertEquals(setOf("format", "version", "name", "author", "created", "parts", "look"), manifest.keys)
        val look = manifest["look"] as JsonObject
        val allowed = SharedLook.serializer().descriptor.let { d -> (0 until d.elementsCount).map { d.getElementName(it) }.toSet() }
        assertTrue("unexpected keys: ${look.keys - allowed}", allowed.containsAll(look.keys))
    }

    /**
     * The allow-list itself: adding a field to what a look carries means adding it here, on purpose. Each is a look
     * setting (a [Look] field, or bottomMode, floating, intensity), never anything about games, accounts or files.
     */
    @Test fun theAllowListIsExactlyTheLookSettings() {
        val d = SharedLook.serializer().descriptor
        val fields = (0 until d.elementsCount).map { d.getElementName(it) }.toSet()
        val expected = setOf(
            "accent", "highlightColor", "highlight", "highlightThickness", "focusScale", "corner", "material", "iconStyle",
            "artSet", "systemArtSets", "motion", "swapStyle", "floating", "intensity", "wallTop", "wallBottom", "wallDim",
            "homeLayout", "homeSections", "systemTiles", "spotlightRail", "spotlightBackdrop", "spotlightMotion", "orbitGroups",
            "orbitContinue", "gameView", "folderPopup", "fullscreenFolders", "cardShape", "shelfLook", "gridSize", "gridHeroSplit",
            "gridAcross", "gridRows", "gridScroll", "gridRoundness", "gridGap", "gridLabels", "gridDetails", "gridFolders",
            "dockStyle", "dockTop", "dockAutoHide", "dockBackground", "dockCount", "dockIconShape",
            "bottomMode", "heroSplit", "heroLogoScale", "heroFolderLogoScale", "heroGradient", "kenBurns", "soundPack", "soundtrack",
        )
        assertEquals(expected, fields)
        val lookFields = Look.serializer().descriptor.let { l -> (0 until l.elementsCount).map { l.getElementName(it) }.toSet() }
        // systemArtSets: each system's own set, kept in icon-grid.json (FolderStyle.artSet), by id only.
        assertTrue((fields - setOf("bottomMode", "floating", "intensity", "systemArtSets")).all { it in lookFields })
        for (never in listOf("theme", "iconPack", "packParts", "soundFiles", "hiddenFolders", "showHidden")) assertFalse(never in fields)
    }

    /**
     * The grid's Icon roundness travels with a look; a tile's own corners, artwork and uploaded pictures are the user's
     * (icon-grid.json), never in a shared look.
     */
    @Test fun gridRoundnessTravelsButTilesOwnLooksNeverDo() {
        val d = SharedLook.serializer().descriptor
        val fields = (0 until d.elementsCount).map { d.getElementName(it) }.toSet()
        assertTrue("gridRoundness" in fields)
        for (never in listOf("styles", "corners", "iconFile", "heroFile", "logoFile", "folders", "animatedArt", "feelMoves")) assertFalse(never in fields)
        val shared = SharedLook.of(Look(gridRoundness = 0.35f), Settings(), ExtrasSettings(), "theme", "theme")
        assertEquals(0.35f, shared.gridRoundness!!, 0f)
        assertEquals(0.5f, SharedLook(gridRoundness = 0.5f).sanitized().gridRoundness!!, 0f)
        assertNull(SharedLook(gridRoundness = 0.9f).sanitized().gridRoundness)
        // Nothing under art/ (where uploads live) is ever written into a .tandem file.
        assertFalse(LookFile.allowed("art/folders/folder_x-icon-1.gif"))
        assertFalse(LookFile.allowed("systems/snes/icon.gif"))
    }

    @Test fun wallpaperPathsNeverTravel() {
        val l = SharedLook(wallTop = "file:/storage/emulated/0/x.jpg", wallBottom = "../x").sanitized()
        assertEquals(null, l.wallTop)
        assertEquals(null, l.wallBottom)
    }

    // ---- Round trip ----

    @Test fun roundTrip() {
        val s = privateSettings()
        val bytes = export(s)
        val dir = File(tmp.root, "in")
        val got = LookFile.read(ByteArrayInputStream(bytes), dir)
        assertEquals("Mint dream", got.manifest.name)
        assertEquals("Pip", got.manifest.author)
        assertEquals(listOf("colours", "layout"), got.manifest.parts)
        val applied = got.manifest.look.applyTo(Look())
        assertEquals("#12AB34", applied.accent)
        assertEquals("outline", applied.highlight)
        assertEquals(5f, applied.highlightThickness)
        assertEquals("grid", applied.homeLayout)
        assertEquals(9, applied.gridAcross)
        assertEquals("chip", applied.soundPack)
        assertEquals("none", applied.soundFiles)
        assertEquals("theme", applied.wallTop)
        assertEquals("starfield", applied.wallBottom)
        assertEquals("neon", applied.iconStyle)
        // What isn't the look stays as the importer has it.
        assertEquals(Look().theme, applied.theme)
        assertEquals(Look().prompts, applied.prompts)
        assertEquals(false, got.manifest.look.floating)
        assertEquals(2, got.manifest.look.intensity)
        assertEquals("art", got.manifest.look.bottomMode)
        assertEquals("pastel-night", (got.theme["base"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test fun theThemesOwnAccentTravelsToo() {
        val s = privateSettings().let { it.copy(look = it.look.copy(accent = null)) }
        val got = LookFile.read(ByteArrayInputStream(export(s)), File(tmp.root, "in"))
        assertEquals(null, got.manifest.look.applyTo(Look(accent = "#FFFFFF")).accent)
    }

    @Test fun sameLookSameBytes() {
        assertTrue(export().contentEquals(export()))
    }

    @Test fun picturesRoundTrip() {
        val icon = File(tmp.root, "icon.png").apply { writeBytes(png(64, 64)) }
        val out = ByteArrayOutputStream()
        LookFile.write(out, LookManifest(name = "x"), "{}", mapOf("systems/snes/icon.png" to icon, "wallpapers/top.png" to icon))
        val got = LookFile.read(ByteArrayInputStream(out.toByteArray()), File(tmp.root, "in"))
        assertEquals(listOf("systems/snes/icon.png", "wallpapers/top.png"), got.images)
    }

    @Test fun writeRefusesPathsOutsideTheAllowList() {
        val f = File(tmp.root, "x.png").apply { writeBytes(png(4, 4)) }
        for (bad in listOf("library.json", "../x.png", "systems/SNES Games/icon.png", "music/song.mp3", "settings.json")) {
            try {
                LookFile.write(ByteArrayOutputStream(), LookManifest(), "{}", mapOf(bad to f))
                fail("$bad was written")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // ---- Files made to cause harm ----

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    private val manifest = """{"format":"tandem-look","version":1,"name":"x"}""".toByteArray()
    private val theme = "{}".toByteArray()

    private fun problem(bytes: ByteArray): LookProblem? {
        val dir = File(tmp.root, "in-${System.nanoTime()}")
        return try {
            LookFile.read(ByteArrayInputStream(bytes), dir)
            null
        } catch (e: LookFileException) {
            assertFalse("a refused file leaves nothing behind", dir.exists())
            e.problem
        }
    }

    @Test fun zipSlipIsRefused() {
        for (evil in listOf("../evil.png", "systems/../../evil.png", "/etc/evil.json", "systems\\..\\evil.png", "./look.json")) {
            assertEquals(evil, LookProblem.BadPath, problem(zip("look.json" to manifest, "theme.json" to theme, evil to png(4, 4))))
        }
        assertFalse(File(tmp.root, "evil.png").exists())
    }

    @Test fun unknownFilesAreRefused() {
        for (other in listOf("run.sh", "classes.dex", "systems/snes/icon.svg", "music/song.mp3", "library.json")) {
            assertEquals(other, LookProblem.BadPath, problem(zip("look.json" to manifest, "theme.json" to theme, other to byteArrayOf(1, 2, 3))))
        }
    }

    @Test fun duplicateEntriesAreRefused() {
        // ZipOutputStream won't write two of a name; the bytes are patched to rename the second.
        val bytes = zip("look.json" to manifest, "theme.json" to theme, "preview.png" to png(4, 4), "previex.png" to png(4, 4))
        val s = String(bytes, Charsets.ISO_8859_1).replace("previex.png", "preview.png").toByteArray(Charsets.ISO_8859_1)
        assertEquals(LookProblem.BadPath, problem(s))
    }

    @Test fun oversizePicturesAreRefused() {
        val big = png(64, 64) + ByteArray(LookFile.MAX_IMAGE.toInt())
        assertEquals(LookProblem.ImageTooLarge, problem(zip("look.json" to manifest, "theme.json" to theme, "preview.png" to big)))
        // Small on disk, huge once decoded.
        assertEquals(LookProblem.ImageTooLarge, problem(zip("look.json" to manifest, "theme.json" to theme, "preview.png" to png(20000, 20000))))
        assertEquals(LookProblem.ImageTooLarge, problem(zip("look.json" to manifest, "theme.json" to theme, "preview.png" to png(4096, 4097))))
    }

    @Test fun tooManyFilesOrTooMuchInAllIsRefused() {
        val many = (0 until LookFile.MAX_ENTRIES + 1).map { "systems/s$it/icon.png" to png(4, 4) }.toTypedArray()
        assertEquals(LookProblem.TooManyFiles, problem(zip("look.json" to manifest, "theme.json" to theme, *many)))
        // Seven pictures of 7.5 MB: each under the limit, together over it.
        val chunk = png(4, 4) + ByteArray((7.5 * (1 shl 20)).toInt())
        val heavy = (0 until 7).map { "systems/s$it/hero.png" to chunk }.toTypedArray()
        assertEquals(LookProblem.TooBig, problem(zip("look.json" to manifest, "theme.json" to theme, *heavy)))
    }

    @Test fun aHugeDownloadStopsEarly() {
        // Random bytes don't compress: a file over the cap is refused while it is still being read.
        val noise = ByteArray((LookFile.MAX_FILE + (1 shl 20)).toInt()).also { java.util.Random(1).nextBytes(it) }
        val stored = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                z.setLevel(0)
                z.putNextEntry(ZipEntry("look.json")); z.write(manifest); z.closeEntry()
                z.putNextEntry(ZipEntry("preview.png")); z.write(noise); z.closeEntry()
            }
        }.toByteArray()
        assertTrue(problem(stored) in setOf(LookProblem.TooBig, LookProblem.ImageTooLarge))
    }

    @Test fun fakePicturesAreRefused() {
        assertEquals(LookProblem.BadImage, problem(zip("look.json" to manifest, "theme.json" to theme, "preview.png" to "#!/bin/sh".toByteArray())))
        // A PNG named .jpg is not what it says.
        assertEquals(LookProblem.BadImage, problem(zip("look.json" to manifest, "theme.json" to theme, "preview.jpg" to png(4, 4))))
        // The platform's own check can refuse one too.
        val dir = File(tmp.root, "checked")
        try {
            LookFile.read(ByteArrayInputStream(zip("look.json" to manifest, "theme.json" to theme, "preview.png" to png(4, 4))), dir) { false }
            fail()
        } catch (e: LookFileException) { assertEquals(LookProblem.BadImage, e.problem) }
    }

    @Test fun versionsAndFormats() {
        assertEquals(LookProblem.NewerVersion, problem(zip("look.json" to """{"format":"tandem-look","version":2}""".toByteArray(), "theme.json" to theme)))
        assertEquals(LookProblem.NotALook, problem(zip("look.json" to """{"format":"other","version":1}""".toByteArray(), "theme.json" to theme)))
        assertEquals(LookProblem.NotALook, problem(zip("theme.json" to theme)))
        assertEquals(LookProblem.NotALook, problem(zip("look.json" to manifest)))
        assertEquals(LookProblem.BadJson, problem(zip("look.json" to manifest, "theme.json" to "not json".toByteArray())))
        assertEquals(LookProblem.NotALook, problem("plain text, not a zip".toByteArray()))
    }

    @Test fun importedValuesAreCleaned() {
        val m = """{"format":"tandem-look","version":1,"name":"  Night\u0000 ‮owl  ","author":"${"a".repeat(200)}",
            "look":{"homeLayout":"rm -rf","gridAcross":500,"accent":"red","corner":99,"homeSections":["continue","x"],"soundPack":"thock","wallTop":"hero"}}"""
        val got = LookFile.read(ByteArrayInputStream(zip("look.json" to m.toByteArray(), "theme.json" to theme)), File(tmp.root, "in"))
        assertEquals("Night owl", got.manifest.name)
        assertEquals(40, got.manifest.author.length)
        val l = got.manifest.look
        assertEquals(null, l.homeLayout)
        assertEquals(null, l.gridAcross)
        assertEquals(null, l.accent)
        assertEquals(null, l.homeSections)
        assertEquals("thock", l.soundPack)
        assertEquals("hero", l.wallTop)
    }

    @Test fun checksums() {
        val f = File(tmp.root, "a.tandem").apply { writeBytes(export()) }
        val sum = LookFile.sha256(f)
        LookFile.verify(f, sum.uppercase())
        try {
            LookFile.verify(f, "0".repeat(64))
            fail("a wrong checksum passed")
        } catch (e: LookFileException) { assertEquals(LookProblem.Mismatch, e.problem) }
    }

    // ---- Pictures ----

    @Test fun sniffsPictureSizes() {
        assertEquals(ImageSniff.Size("png", 640, 360), ImageSniff.size(ByteArrayInputStream(png(640, 360))))
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 4, 0, 0) +
            byteArrayOf(0xFF.toByte(), 0xC0.toByte(), 0, 11, 8, 0x01, 0x68, 0x02, 0x80.toByte(), 3, 0, 0, 0, 0)
        assertEquals(ImageSniff.Size("jpeg", 640, 360), ImageSniff.size(ByteArrayInputStream(jpeg)))
        val vp8x = "RIFF".toByteArray() + ByteArray(4) + "WEBPVP8X".toByteArray() + ByteArray(8) + byteArrayOf(0x7F, 0x02, 0, 0x67, 0x01, 0)
        assertEquals(ImageSniff.Size("webp", 640, 360), ImageSniff.size(ByteArrayInputStream(vp8x)))
        assertEquals(null, ImageSniff.size(ByteArrayInputStream("GIF89a.........................".toByteArray())))
    }

    /** The ids [Ids] copies from the UI's enums. */
    @Test fun idsMatchTheEnums() {
        assertEquals(HighlightStyle.entries.map { it.id }, Ids.highlight.toList())
        assertEquals(SurfaceMaterial.entries.map { it.id }, Ids.material.toList())
        assertEquals(AnimationStyle.entries.map { it.id }, Ids.motion.toList())
        assertEquals(IconStyle.entries.map { it.id }, Ids.iconStyle.toList())
        assertEquals(BackdropStyle.entries.map { it.id }, Ids.backdrop.toList())
        assertEquals(SoundPack.entries.map { it.id }, Ids.soundPack.toList())
    }

    companion object {
        /** The start of a PNG: enough for [ImageSniff]. */
        fun png(w: Int, h: Int): ByteArray {
            fun be(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
            return byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10) +
                be(13) + "IHDR".toByteArray() + be(w) + be(h) + byteArrayOf(8, 6, 0, 0, 0) + be(0)
        }
    }
}
