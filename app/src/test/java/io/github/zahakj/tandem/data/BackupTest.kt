package io.github.zahakj.tandem.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Versioned backups (Backup.kt): manifest, categories, retention and restoring category by category, on temp folders. */
class BackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private val root: File by lazy { tmp.newFolder("backup") }
    private val files: File by lazy { tmp.newFolder("files") }
    private val all = BackupCategory.entries.toSet()

    private val texts = mapOf(
        "library.json" to """{"snes/Mario.sfc":{"favorite":true},"snes/Zelda.sfc":{},"gb/Tetris.gb":{"hidden":true}}""",
        "settings.json" to """{"sort":"Recent","look":{"theme":"oled"},"hiddenFolders":[{"system":"snes","folder":"RPG"}],"welcomeDone":true}""",
        "sessions.json" to """[{"k":"snes/Mario.sfc","s":1,"d":60},{"k":"gb/Tetris.gb","s":2,"d":30}]""",
        "journal.json" to """{"games":{"snes/Mario.sfc":"note"}}""",
        "achievements.json" to """{"user":"me","key":"ra-secret","enabled":true}""",
        "extras.json" to """{"intensity":2}""",
        "icon-grid.json" to """{"order":["s:snes","folder:a","app:x"],"added":["app:x","game:gb/Tetris.gb"],"folders":[{"id":"a","name":"A","items":["s:gb","s:nes"]},{"id":"b","name":"B"}]}""",
        "home.json" to """{"dock":["a","b"]}""",
        "widgets.json" to """{"widgets":[{"id":"w","type":"clock"},{"id":"v","type":"stats"}]}""",
        "systems.json" to """{"singleScreen":true}""",
        "controllers.json" to """{"holdHome":"recents"}""",
        "scraping.json" to """{"ssUser":"u","ssPassword":"p","sgdbKey":"k","region":"eu"}""",
    )

    private fun sources(m: Map<String, String> = texts): Map<String, () -> String?> = m.mapValues { (_, v) -> { v } }

    private fun obj(f: File): JsonObject = Json.parseToJsonElement(f.readText()).jsonObject

    @Test fun manifestHoldsCategoriesCountsAndVersion() {
        val s = BackupFiles.write(root, sources(), all, automatic = false, app = "0.2.0", now = 1_700_000_000_000)
        assertTrue(s.dir.name.matches(Regex("""\d{4}-\d\d-\d\d_\d\d-\d\d-\d\d""")))
        val m = BackupFiles.list(root).single().manifest
        assertEquals(listOf("games", "look", "layout", "systems"), m.categories)
        assertEquals("0.2.0", m.app)
        assertFalse(m.automatic)
        assertEquals(BackupCounts(games = 3, tiles = 5, folders = 2, widgets = 2, sessions = 2), m.counts)
        assertEquals(texts.keys, m.files.toSet())
        assertTrue(File(s.dir, BackupFiles.MANIFEST).isFile)
        // No temporary files left behind.
        assertTrue(root.listFiles()!!.none { it.name.startsWith(".") })
        assertTrue(s.dir.listFiles()!!.none { it.name.endsWith("tmp") })
        assertEquals("3 games · 5 tiles · 2 folders · 2 widgets · 2 days ago", BackupFiles.summary(m, m.created + 2 * 86_400_000L + 1000))
    }

    @Test fun keysAreLeftOutUnlessAsked() {
        val without = BackupFiles.write(root, sources(), all, automatic = false, now = 1000)
        val scrape = obj(File(without.dir, "scraping.json"))
        assertFalse("sgdbKey" in scrape || "ssPassword" in scrape || "ssUser" in scrape)
        assertEquals("\"eu\"", scrape["region"].toString())
        assertFalse("key" in obj(File(without.dir, "achievements.json")))
        assertFalse(BackupFiles.list(root).first().manifest.keys)

        val with = BackupFiles.write(root, sources(), all, automatic = false, withKeys = true, now = 2000)
        assertTrue("sgdbKey" in obj(File(with.dir, "scraping.json")))
        assertTrue(BackupFiles.list(root).first().manifest.keys)
    }

    @Test fun discordTokenIsNeverBackedUp() {
        // DiscordPresence keeps its token in noBackupFilesDir/discord.json; a backup must never pick it up.
        assertFalse("discord.json" in BackupFiles.allFiles)
    }

    @Test fun privateModePinAndOfflineStateAreNeverBackedUp() {
        // Both live in noBackupFilesDir (PrivacyPin.kt, Offline.kt): not even a backup with keys may hold them.
        val pin = io.github.zahakj.tandem.PinVault.FILE
        val offline = io.github.zahakj.tandem.Offline.FILE
        assertFalse(pin in BackupFiles.allFiles || offline in BackupFiles.allFiles)
        val s = BackupFiles.write(root, sources(texts + (pin to """{"hash":"h"}""") + (offline to """{"airplane":true}""")), all, automatic = false, withKeys = true)
        assertFalse(File(s.dir, pin).exists() || File(s.dir, offline).exists())
        assertFalse(pin in BackupFiles.list(root).single().manifest.files)
    }

    @Test fun onlyChosenCategoriesAreWritten() {
        val s = BackupFiles.write(root, sources(), setOf(BackupCategory.Look), automatic = false)
        assertEquals(setOf("settings.json", "extras.json", BackupFiles.MANIFEST), s.dir.list()!!.toSet())
        val settings = obj(File(s.dir, "settings.json"))
        // Hidden folders belong to Game data: not in a Look-only backup.
        assertFalse("hiddenFolders" in settings)
        assertTrue("look" in settings && "sort" in settings && "welcomeDone" in settings)
        assertEquals(setOf(BackupCategory.Look), BackupFiles.list(root).single().manifest.holds)

        val games = BackupFiles.write(root, sources(), setOf(BackupCategory.Games), automatic = false, now = 5000)
        val gs = obj(File(games.dir, "settings.json"))
        assertTrue("hiddenFolders" in gs)
        assertFalse("look" in gs || "sort" in gs)
    }

    @Test fun missingFilesAreSkipped() {
        val s = BackupFiles.write(root, sources() + (BackupFiles.APP_PREFS to { null }), all, automatic = false)
        assertFalse(File(s.dir, BackupFiles.APP_PREFS).exists())
        val with = BackupFiles.write(root, sources() + (BackupFiles.APP_PREFS to { "{\"a\":1}" }), all, automatic = false, now = 9000)
        assertTrue(File(with.dir, BackupFiles.APP_PREFS).isFile)
    }

    @Test fun keepsFiveAutomaticAndEveryManual() {
        val day = 86_400_000L
        repeat(7) { i -> BackupFiles.write(root, sources(), all, automatic = true, reason = "Quick look", now = 1_700_000_000_000 + i * day) }
        repeat(2) { i -> BackupFiles.write(root, sources(), all, automatic = false, now = 1_600_000_000_000 + i * day) }
        assertEquals(2, BackupFiles.prune(root))
        val left = BackupFiles.list(root)
        assertEquals(5, left.count { it.manifest.automatic })
        assertEquals(2, left.count { !it.manifest.automatic })
        // Newest first; the two oldest automatic ones went.
        assertEquals(left.map { it.manifest.created }.sortedDescending(), left.map { it.manifest.created })
        assertEquals(1_700_000_000_000 + 2 * day, left.filter { it.manifest.automatic }.minOf { it.manifest.created })
    }

    @Test fun sameSecondGetsItsOwnFolder() {
        val a = BackupFiles.write(root, sources(), all, automatic = true, now = 1000)
        val b = BackupFiles.write(root, sources(), all, automatic = false, now = 1000)
        assertTrue(a.dir != b.dir)
        assertEquals(2, BackupFiles.list(root).size)
        assertTrue(BackupFiles.delete(b))
        assertEquals(listOf(a.dir), BackupFiles.list(root).map { it.dir })
    }

    @Test fun partialFailureRestoresTheRest() {
        val s = BackupFiles.write(root, sources(), setOf(BackupCategory.Games, BackupCategory.Look, BackupCategory.Layout), automatic = false)
        File(s.dir, "library.json").writeText("{not json")
        File(files, "library.json").writeText("""{"keep":{}}""")
        File(files, "icon-grid.json").writeText("""{"order":["old"]}""")
        File(files, "systems.json").writeText("""{"launchAnimation":false}""")
        val reloaded = ArrayList<BackupCategory>()
        val r = BackupFiles.restore(s, files, all, reload = { reloaded += it })
        assertTrue(r.outcomes[BackupCategory.Games] is RestoreOutcome.Failed)
        assertEquals(RestoreOutcome.Restored, r.outcomes[BackupCategory.Look])
        assertEquals(RestoreOutcome.Restored, r.outcomes[BackupCategory.Layout])
        assertEquals(RestoreOutcome.NotInBackup, r.outcomes[BackupCategory.Systems])
        assertEquals(listOf(BackupCategory.Look, BackupCategory.Layout), reloaded)
        // The failed category wrote nothing, not even its other files.
        assertEquals("""{"keep":{}}""", File(files, "library.json").readText())
        assertFalse(File(files, "sessions.json").exists())
        assertEquals(texts["icon-grid.json"], File(files, "icon-grid.json").readText())
        assertEquals("""{"launchAnimation":false}""", File(files, "systems.json").readText())
        val msg = r.message()
        assertTrue(msg, msg.contains("Restored Look, Layout & folders") && msg.contains("Game data failed") && msg.contains("Systems & emulators not in this backup"))
    }

    @Test fun aFailedCheckStopsOnlyItsCategory() {
        val s = BackupFiles.write(root, sources(), all, automatic = false)
        val r = BackupFiles.restore(s, files, all, check = { file, _ -> require(file != "controllers.json") })
        assertTrue(r.outcomes[BackupCategory.Systems] is RestoreOutcome.Failed)
        assertEquals(3, r.restored.size)
        assertFalse(File(files, "systems.json").exists())
    }

    @Test fun restoreMergesSharedFilesAndKeepsKeysAndPrivateMode() {
        val s = BackupFiles.write(root, sources(), all, automatic = false)
        File(files, "settings.json").writeText("""{"sort":"Name","showHidden":false,"hiddenFolders":[],"autoImportDone":true}""")
        File(files, "scraping.json").writeText("""{"sgdbKey":"mine","region":"us"}""")
        File(files, "achievements.json").writeText("""{"user":"me","key":"device-key"}""")
        File(files, "home.json").writeText("""{"privateMode":true}""")
        BackupFiles.restore(s, files, setOf(BackupCategory.Look, BackupCategory.Systems, BackupCategory.Layout, BackupCategory.Games))

        val settings = obj(File(files, "settings.json"))
        assertEquals("\"Recent\"", settings["sort"].toString())
        assertTrue("look" in settings)
        // Fields of no category stay as they are on the device.
        assertEquals("true", settings["autoImportDone"].toString())
        assertFalse("welcomeDone" in settings)
        assertEquals("false", settings["showHidden"].toString())
        assertEquals(1, (settings["hiddenFolders"] as kotlinx.serialization.json.JsonArray).size)

        val scrape = obj(File(files, "scraping.json"))
        assertEquals("\"mine\"", scrape["sgdbKey"].toString())
        assertEquals("\"eu\"", scrape["region"].toString())
        assertEquals("\"device-key\"", obj(File(files, "achievements.json"))["key"].toString())
        // Private mode is never switched off by a restore.
        assertEquals("true", obj(File(files, "home.json"))["privateMode"].toString())
    }

    @Test fun onlyLookLeavesHiddenFoldersAlone() {
        val s = BackupFiles.write(root, sources(), all, automatic = false)
        File(files, "settings.json").writeText("""{"hiddenFolders":[{"system":"gb"},{"system":"nes"}]}""")
        BackupFiles.restore(s, files, setOf(BackupCategory.Look))
        val settings = obj(File(files, "settings.json"))
        assertEquals(2, (settings["hiddenFolders"] as kotlinx.serialization.json.JsonArray).size)
        assertTrue("look" in settings)
    }

    @Test fun oldSingleSlotBackupIsListedLast() {
        File(root, "library.json").writeText(texts.getValue("library.json"))
        File(root, "settings.json").writeText(texts.getValue("settings.json"))
        BackupFiles.write(root, sources(), all, automatic = false)
        val list = BackupFiles.list(root)
        assertEquals(2, list.size)
        val old = list.last()
        assertTrue(old.legacy)
        assertEquals(setOf(BackupCategory.Games, BackupCategory.Look), old.manifest.holds)
        assertEquals(3, old.manifest.counts.games)
        val r = BackupFiles.restore(old, files, all)
        assertEquals(listOf(BackupCategory.Games, BackupCategory.Look), r.restored)
        assertEquals(BackupFiles.prune(root), 0)
        assertTrue(BackupFiles.delete(old))
        assertEquals(1, BackupFiles.list(root).size)
        assertTrue(File(root, list.first().dir.name).isDirectory)
    }

    @Test fun agoReadsNaturally() {
        val now = 10L * 86_400_000L
        assertEquals("just now", BackupFiles.ago(now - 10_000, now))
        assertEquals("5 minutes ago", BackupFiles.ago(now - 5 * 60_000, now))
        assertEquals("an hour ago", BackupFiles.ago(now - 61 * 60_000, now))
        assertEquals("yesterday", BackupFiles.ago(now - 30 * 3_600_000L, now))
        assertEquals("3 days ago", BackupFiles.ago(now - 3 * 86_400_000L, now))
    }
}
