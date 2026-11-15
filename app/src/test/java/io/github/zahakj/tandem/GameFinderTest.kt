package io.github.zahakj.tandem

import io.github.zahakj.tandem.data.ScanCache
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Finding games in a folder of any shape (GameFinder.kt): system names, kinds of file only one system has, and the walk. */
class GameFinderTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Every system in Tandem's own platforms.json, read the way Platforms.parse reads it (folders, extensions, aliases). */
    private val platforms: List<Platform> by lazy {
        val root = Json.parseToJsonElement(File("src/main/assets/platforms.json").readText()).jsonObject
        root.getValue("platforms").jsonArray.map { it.jsonObject }.map { o ->
            fun list(key: String) = (o[key] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
            val id = o.getValue("id").jsonPrimitive.content
            val name = o.getValue("name").jsonPrimitive.content
            Platform(
                id = id, name = name, shortName = (o["short"] ?: o.getValue("name")).jsonPrimitive.content,
                folders = list("folders").ifEmpty { listOf(id) }, extensions = list("extensions").map { it.lowercase() }.toSet(),
                skipDirs = list("skipDirs").map { it.lowercase() }.toSet(), artIds = listOf(id), allPlayers = emptyList(),
                aliases = list("aliases").map { it.lowercase() }.toSet(),
            )
        }
    }
    private val names by lazy { SystemNames(platforms) }

    private fun system(folder: String): String? = names.of(folder)?.id

    // --- Names -----------------------------------------------------------------------------------------------------

    @Test fun platformsJsonNamesAreFound() {
        assertEquals("n3ds", system("n3ds"))
        assertEquals("n3ds", system("3ds"))
        assertEquals("gc", system("gamecube"))
        assertEquals("psx", system("psx"))
        assertEquals("psx", system("ps1"))
    }

    @Test fun caseSpacesAndPunctuationDontCount() {
        for (n in listOf("3DS", "N3DS", "Nintendo 3DS", "nintendo-3ds", "Nintendo_3DS", "NINTENDO 3DS")) assertEquals(n, "n3ds", system(n))
        for (n in listOf("Switch", "Nintendo Switch", "SWITCH")) assertEquals(n, "switch", system(n))
        for (n in listOf("GBA", "Game Boy Advance", "gameboy advance")) assertEquals(n, "gba", system(n))
        for (n in listOf("PS2", "PlayStation 2", "playstation-2")) assertEquals(n, "ps2", system(n))
        for (n in listOf("PS1", "PlayStation", "psx", "PlayStation 1", "PSOne")) assertEquals(n, "psx", system(n))
        assertEquals("wiiu", system("Wii U"))
        assertEquals("snes", system("Super Nintendo"))
        assertEquals("snes", system("Super Famicom"))
        assertEquals("gameandwatch", system("Game & Watch"))
        assertEquals("pokemini", system("Pokémon Mini"))
    }

    @Test fun trailingRomsAndGamesAreLeftOff() {
        assertEquals("n3ds", system("3DS ROMs"))
        assertEquals("n3ds", system("3ds_roms"))
        assertEquals("gba", system("GBA Games"))
        assertEquals("ps2", system("PS2 ISOs"))
        assertEquals("snes", system("SNES roms games"))
    }

    @Test fun makersAndBracketedNotesAreLeftOff() {
        assertEquals("ps2", system("Sony - PlayStation 2"))
        assertEquals("gba", system("Nintendo - Game Boy Advance"))
        assertEquals("n3ds", system("Nintendo - Nintendo 3DS (Digital)"))
        assertEquals("genesis", system("Sega Mega Drive"))
        assertEquals("n64", system("Nintendo 64"))
    }

    @Test fun otherNamesMeanNoSystem() {
        for (n in listOf("ROMs", "roms", "Games", "My stuff", "Favourites", "Downloads", "", "Nintendo", "tandem")) assertNull(n, system(n))
    }

    @Test fun keysTryTheNameThenWithoutSuffixesThenWithoutTheMaker() {
        assertEquals(listOf("nintendo3dsroms", "nintendo3ds", "3dsroms", "3ds"), SystemNames.keys("Nintendo 3DS ROMs"))
        assertEquals(emptyList<String>(), SystemNames.keys("(Japan)"))
    }

    // --- Kinds of file ---------------------------------------------------------------------------------------------

    @Test fun anExtensionOnlyOneSystemListsIsThatSystems() {
        val loose = SystemNames.looseExtensions(platforms).mapValues { it.value.id }
        for (e in listOf("3ds", "cia", "cci")) assertEquals(e, "n3ds", loose[e])
        assertEquals("nds", loose["nds"])
        for (e in listOf("nsp", "xci")) assertEquals(e, "switch", loose[e])
        for (e in listOf("wua", "wux")) assertEquals(e, "wiiu", loose[e])
        for (e in listOf("smc", "sfc")) assertEquals(e, "snes", loose[e])
        for (e in listOf("n64", "z64", "v64")) assertEquals(e, "n64", loose[e])
        assertEquals("gba", loose["gba"])
        assertEquals("gbc", loose["gbc"])
        assertEquals("nes", loose["nes"])
    }

    @Test fun aSharedExtensionGoesToTheSystemNamedLikeIt() {
        val loose = SystemNames.looseExtensions(platforms).mapValues { it.value.id }
        // .gb is listed by Game Boy and Game Boy Color, .ngp by Neo Geo Pocket and Neo Geo Pocket Color.
        assertEquals("gb", loose["gb"])
        assertEquals("ngp", loose["ngp"])
        // .md is a Genesis game, and a text file too often to count anywhere but a Genesis folder.
        assertNull(loose["md"])
        // .pce is listed by PC Engine and SuperGrafx, neither named pce in platforms.json: nobody's.
        assertNull(loose["pce"])
    }

    @Test fun everydayKindsAreNeverLoose() {
        val loose = SystemNames.looseExtensions(platforms)
        for (e in listOf("iso", "bin", "cue", "chd", "zip", "7z", "png", "pdf", "mp4", "json", "exe", "elf", "app", "m3u")) assertNull(e, loose[e])
    }

    @Test fun aSystemWithItsOwnSourceIsLeftOut() {
        val apps = Platform("android", "Android", "Android", listOf("android"), setOf("apk2"), emptySet(), listOf("android"), emptyList(), source = { emptyList() })
        assertNull(SystemNames.looseExtensions(listOf(apps))["apk2"])
    }

    // --- The walk --------------------------------------------------------------------------------------------------

    private fun file(path: String) = File(tmp.root, path).apply { parentFile.mkdirs(); writeText("x") }

    /** Lists a folder the way the scan cache does, in name order so tests don't depend on the file system's. */
    private val finder by lazy {
        GameFinder(platforms) { d ->
            val all = d.listFiles().orEmpty().sortedBy { it.name }
            ScanCache.Listing(d.lastModified(), all.filter { it.isFile }.map { it.name }, all.filter { it.isDirectory }.map { it.name })
        }
    }

    private fun found(root: File, main: Boolean = false): Map<String, List<String>> =
        finder.find(root, if (main) null else root, main).games.mapValues { (_, gs) -> gs.map { it.file.relativeTo(root).path } }

    @Test fun aFolderOfSystemFoldersIsReadAsEver() {
        file("ROMs/n3ds/A.3ds"); file("ROMs/n3ds/Puzzle/B.cia"); file("ROMs/gba/C.gba"); file("ROMs/gba/D.zip")
        file("ROMs/n3ds/sdmc/Nintendo 3DS/save.3ds")
        val games = found(File(tmp.root, "ROMs"), main = true)
        assertEquals(listOf("n3ds/A.3ds", "n3ds/Puzzle/B.cia"), games["n3ds"])
        // A zip counts inside a system's folder.
        assertEquals(listOf("gba/C.gba", "gba/D.zip"), games["gba"])
    }

    @Test fun systemFoldersMayHaveHumanNames() {
        file("Games/Nintendo 3DS/A.3ds"); file("Games/PlayStation 2/B.iso"); file("Games/SNES ROMs/C.sfc")
        val games = found(File(tmp.root, "Games"))
        assertEquals(listOf("Nintendo 3DS/A.3ds"), games["n3ds"])
        assertEquals(listOf("PlayStation 2/B.iso"), games["ps2"])
        assertEquals(listOf("SNES ROMs/C.sfc"), games["snes"])
    }

    @Test fun oneSystemsOwnFolderPickedDirectly() {
        file("3DS/A.3ds"); file("3DS/B.cci"); file("3DS/RPG/C.cia"); file("3DS/notes.txt")
        val found = finder.find(File(tmp.root, "3DS"), File(tmp.root, "3DS"), main = false)
        assertEquals(listOf("n3ds"), found.games.keys.toList())
        assertEquals(listOf("A.3ds", "B.cci", "RPG/C.cia"), found.games.getValue("n3ds").map { it.file.relativeTo(File(tmp.root, "3DS")).path })
        assertEquals(listOf("RPG"), found.games.getValue("n3ds").mapNotNull { it.group })
        assertEquals(listOf(File(tmp.root, "3DS")), found.folders["n3ds"])
    }

    @Test fun theRomsFolderIsNeverOneSystemsFolder() {
        file("GBA/A.zip")
        // Picked, a folder named GBA is the GBA's; as the main folder it is only walked for system folders.
        assertEquals(listOf("A.zip"), found(File(tmp.root, "GBA"))["gba"])
        assertEquals(emptyMap<String, List<String>>(), found(File(tmp.root, "GBA"), main = true))
    }

    @Test fun aMixedFolderSortsFilesByKind() {
        file("Mixed/A.3ds"); file("Mixed/B.nsp"); file("Mixed/C.gba"); file("Mixed/Old/D.sfc"); file("Mixed/E.iso"); file("Mixed/F.zip")
        file("Mixed/cover.png"); file("Mixed/Old/G.gb")
        val games = found(File(tmp.root, "Mixed"))
        assertEquals(listOf("A.3ds"), games["n3ds"])
        assertEquals(listOf("B.nsp"), games["switch"])
        assertEquals(listOf("C.gba"), games["gba"])
        assertEquals(listOf("Old/D.sfc"), games["snes"])
        assertEquals(listOf("Old/G.gb"), games["gb"])
        // Disc images, archives and pictures mean no system on their own.
        assertTrue(games.values.flatten().none { it.endsWith(".iso") || it.endsWith(".zip") || it.endsWith(".png") })
    }

    @Test fun aMixedFoldersSubfolderIsTheGamesFolder() {
        file("Mixed/A.3ds"); file("Mixed/Favourites/B.3ds")
        val games = finder.find(File(tmp.root, "Mixed"), File(tmp.root, "Mixed"), main = false).games.getValue("n3ds")
        assertEquals(listOf(null, "Favourites"), games.map { it.group })
    }

    @Test fun ambiguousFilesCountInsideASystemFolderDeeperDown() {
        file("Mixed/Sony/PS2/A.iso"); file("Mixed/Sony/B.iso")
        val games = found(File(tmp.root, "Mixed"))
        assertEquals(listOf("Sony/PS2/A.iso"), games["ps2"])
        assertTrue(games.values.flatten().none { it == "Sony/B.iso" })
    }

    @Test fun noFileIsFoundTwice() {
        // A .3ds in a 3DS folder is the folder's, not found again loose; one in the GBA folder is nobody's.
        file("Mixed/3DS/A.3ds"); file("Mixed/GBA/B.3ds"); file("Mixed/GBA/C.gba")
        val found = finder.find(File(tmp.root, "Mixed"), File(tmp.root, "Mixed"), main = false)
        assertEquals(listOf("A.3ds"), found.games["n3ds"]?.map { it.file.name })
        assertEquals(listOf("C.gba"), found.games["gba"]?.map { it.file.name })
        val all = found.games.values.flatten().map { it.file.path }
        assertEquals(all.distinct(), all)
    }

    @Test fun platformsJsonFoldersComeFirstThenOtherNamesThenLooseFiles() {
        // Same file name three times: the first one found wins the key, as before (n3ds/ before 3ds/).
        file("ROMs/3ds/A.3ds"); file("ROMs/n3ds/A.3ds"); file("ROMs/Nintendo 3DS/A.3ds"); file("ROMs/Other/A.3ds")
        val games = found(File(tmp.root, "ROMs"), main = true).getValue("n3ds")
        assertEquals(listOf("n3ds/A.3ds", "3ds/A.3ds", "Nintendo 3DS/A.3ds", "Other/A.3ds"), games)
    }

    @Test fun tandemsOwnFoldersAreNeverWalkedForLooseFiles() {
        file("ROMs/tandem/backup/A.3ds"); file("ROMs/downloaded_media/n3ds/covers/A.png"); file("ROMs/manuals/gba/B.gba")
        assertEquals(emptyMap<String, List<String>>(), found(File(tmp.root, "ROMs"), main = true))
    }

    @Test fun aFolderTwoSystemsListIsBoths() {
        // An imported system can share a built-in one's folder; each takes its own kinds of file from it, as before.
        val a = Platform("arcade", "Arcade", "Arcade", listOf("arcade"), setOf("zip"), emptySet(), listOf("arcade"), emptyList())
        val b = Platform("arcade2", "Arcade Two", "Arc2", listOf("arcade"), setOf("chd"), emptySet(), listOf("arcade2"), emptyList())
        file("ROMs/arcade/A.zip"); file("ROMs/arcade/B.chd")
        val own = GameFinder(listOf(a, b)) { d ->
            val all = d.listFiles().orEmpty().sortedBy { it.name }
            ScanCache.Listing(d.lastModified(), all.filter { it.isFile }.map { it.name }, all.filter { it.isDirectory }.map { it.name })
        }
        val games = own.find(File(tmp.root, "ROMs"), null, main = true).games.mapValues { (_, gs) -> gs.map { it.file.name } }
        assertEquals(mapOf("arcade" to listOf("A.zip"), "arcade2" to listOf("B.chd")), games)
    }

    @Test fun aMissingFolderHasNoGames() {
        assertEquals(emptyMap<String, List<String>>(), found(File(tmp.root, "nowhere"), main = true))
    }

    @Test fun gameKeysStayTheSystemAndFileName() {
        file("Mixed/Deep/A (USA).3ds")
        val g = finder.find(File(tmp.root, "Mixed"), File(tmp.root, "Mixed"), main = false).games.getValue("n3ds").single()
        assertEquals("n3ds/A (USA).3ds", g.key)
        assertEquals("A", g.fileTitle)
        assertEquals(File(tmp.root, "Mixed"), g.root)
    }

    @Test fun noTwoSystemsShareAName() {
        // A clash would make one system unreachable by that name.
        val seen = HashMap<String, String>()
        for (p in platforms) for (n in (listOf(p.id, p.shortName, p.name) + p.folders + p.aliases + SystemNames.NICKNAMES[p.id].orEmpty()).map(SystemNames::squash).toSet()) {
            val other = seen.put(n, p.id)
            assertTrue("$n is ${p.id} and $other", other == null || other == p.id)
        }
    }
}
