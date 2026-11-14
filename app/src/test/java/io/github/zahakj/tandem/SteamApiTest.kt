package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Steam Web API as SteamApi.kt reads it: the three answers about a game, the diff for new unlocks, and the account. */
class SteamApiTest {
    private val player = """
        {"playerstats":{"steamID":"76561198000000001","gameName":"Portal 2","achievements":[
          {"apiname":"ACH.SURVIVE_CONTAINER_RIDE","achieved":1,"unlocktime":1700000000},
          {"apiname":"ACH.WAKE_UP","achieved":1,"unlocktime":1700000100},
          {"apiname":"ACH.LASER_CHAINING","achieved":0,"unlocktime":0}
        ],"success":true}}
    """.trimIndent()

    private val schema = """
        {"game":{"gameName":"Portal 2","gameVersion":"123","availableGameStats":{"achievements":[
          {"name":"ACH.SURVIVE_CONTAINER_RIDE","defaultvalue":0,"displayName":"Wake Up Call","hidden":0,"description":"Survive the manual override","icon":"https://cdn.example/a.jpg","icongray":"https://cdn.example/a_gray.jpg"},
          {"name":"ACH.WAKE_UP","defaultvalue":0,"displayName":"You Monster","hidden":0,"description":"Reunite with GLaDOS","icon":"https://cdn.example/b.jpg","icongray":"https://cdn.example/b_gray.jpg"},
          {"name":"ACH.LASER_CHAINING","defaultvalue":0,"displayName":"Undiscouraged","hidden":1,"icon":"https://cdn.example/c.jpg","icongray":"https://cdn.example/c_gray.jpg"}
        ]}}}
    """.trimIndent()

    private val percents = """
        {"achievementpercentages":{"achievements":[
          {"name":"ACH.SURVIVE_CONTAINER_RIDE","percent":84.5},
          {"name":"ACH.WAKE_UP","percent":"62.099998474121094"},
          {"name":"ACH.LASER_CHAINING","percent":3.2}
        ]}}
    """.trimIndent()

    @Test fun playerAchievementsReadUnlocksAndTimes() {
        val p = SteamApi.parsePlayerAchievements(player)!!
        assertEquals("Portal 2", p.gameName)
        assertEquals(setOf("ACH.SURVIVE_CONTAINER_RIDE", "ACH.WAKE_UP"), p.unlocked.keys)
        assertEquals(1700000100_000L, p.unlocked["ACH.WAKE_UP"])
    }

    @Test fun playerAchievementsFailureIsNull() {
        assertNull(SteamApi.parsePlayerAchievements("""{"playerstats":{"error":"Requested app has no stats","success":false}}"""))
        assertNull(SteamApi.parsePlayerAchievements("not json"))
        assertNull(SteamApi.parsePlayerAchievements("""{"response":{}}"""))
    }

    @Test fun schemaReadsNamesIconsAndHiddenFlag() {
        val s = SteamApi.parseSchema(schema)!!
        assertEquals(3, s.size)
        assertEquals("Wake Up Call", s[0].name)
        assertEquals("Survive the manual override", s[0].description)
        assertEquals("https://cdn.example/a_gray.jpg", s[0].iconGray)
        assertFalse(s[0].hidden)
        // A hidden achievement comes without a description until it is unlocked.
        assertTrue(s[2].hidden)
        assertEquals("", s[2].description)
    }

    @Test fun schemaOfAGameWithoutAchievementsIsEmpty() {
        assertEquals(emptyList<SteamApi.SchemaEntry>(), SteamApi.parseSchema("""{"game":{}}"""))
        assertEquals(emptyList<SteamApi.SchemaEntry>(), SteamApi.parseSchema("""{"game":{"gameName":"X","availableGameStats":{"stats":[]}}}"""))
        assertNull(SteamApi.parseSchema("""{"nope":1}"""))
    }

    @Test fun percentagesReadNumbersAndStrings() {
        val p = SteamApi.parsePercentages(percents)
        assertEquals(84.5f, p["ACH.SURVIVE_CONTAINER_RIDE"]!!, 0.001f)
        assertEquals(62.1f, p["ACH.WAKE_UP"]!!, 0.001f)
        assertEquals(emptyMap<String, Float>(), SteamApi.parsePercentages("garbage"))
    }

    @Test fun mergeKeepsSchemaOrderAndJoinsTheThree() {
        val m = SteamApi.merge(620, SteamApi.parseSchema(schema)!!, SteamApi.parsePlayerAchievements(player), SteamApi.parsePercentages(percents), now = 42L)
        assertEquals(620, m.appId)
        assertEquals("Portal 2", m.gameName)
        assertEquals(42L, m.fetchedAt)
        assertEquals(3, m.total)
        assertEquals(2, m.unlocked)
        assertEquals(listOf("ACH.SURVIVE_CONTAINER_RIDE", "ACH.WAKE_UP", "ACH.LASER_CHAINING"), m.achievements.map { it.id })
        val locked = m.achievements[2]
        assertFalse(locked.unlocked)
        assertEquals(0L, locked.unlockedAt)
        assertEquals(3.2f, locked.percent, 0.001f)
        assertEquals("https://cdn.example/c_gray.jpg", locked.shownIcon)
        assertEquals("https://cdn.example/b.jpg", m.achievements[1].shownIcon)
    }

    @Test fun mergeWithoutPlayerStatsLocksEverything() {
        val m = SteamApi.merge(620, SteamApi.parseSchema(schema)!!, null, emptyMap(), 1L)
        assertEquals(0, m.unlocked)
        assertEquals("", m.gameName)
        assertTrue(m.achievements.all { it.percent < 0f })
    }

    @Test fun newUnlocksAreTheOnesNotUnlockedBefore() {
        val schemaList = SteamApi.parseSchema(schema)!!
        val before = SteamApi.merge(620, schemaList, SteamApi.PlayerState("Portal 2", mapOf("ACH.SURVIVE_CONTAINER_RIDE" to 1000L)), emptyMap(), 1L)
        val after = SteamApi.merge(620, schemaList, SteamApi.parsePlayerAchievements(player), emptyMap(), 2L)
        assertEquals(listOf("ACH.WAKE_UP"), SteamApi.newUnlocks(before, after).map { it.id })
        // Nothing changed: nothing new. Something re-locked: nothing new either.
        assertEquals(emptyList<SteamAchievement>(), SteamApi.newUnlocks(after, after))
        assertEquals(emptyList<SteamAchievement>(), SteamApi.newUnlocks(after, before))
    }

    @Test fun theFirstLookAnnouncesNothing() {
        val after = SteamApi.merge(620, SteamApi.parseSchema(schema)!!, SteamApi.parsePlayerAchievements(player), emptyMap(), 2L)
        assertEquals(emptyList<SteamAchievement>(), SteamApi.newUnlocks(null, after))
    }

    @Test fun newUnlocksComeOldestFirst() {
        val schemaList = SteamApi.parseSchema(schema)!!
        val before = SteamApi.merge(620, schemaList, SteamApi.PlayerState("Portal 2", emptyMap()), emptyMap(), 1L)
        val after = SteamApi.merge(620, schemaList, SteamApi.PlayerState("Portal 2", mapOf("ACH.WAKE_UP" to 5000L, "ACH.SURVIVE_CONTAINER_RIDE" to 9000L, "ACH.LASER_CHAINING" to 1000L)), emptyMap(), 2L)
        assertEquals(listOf("ACH.LASER_CHAINING", "ACH.WAKE_UP", "ACH.SURVIVE_CONTAINER_RIDE"), SteamApi.newUnlocks(before, after).map { it.id })
    }

    @Test fun announcementNamesAFewThenCountsTheRest() {
        val unlocks = (1..5).map { SteamAchievement(id = "a$it", name = "Thing $it", icon = "https://cdn.example/$it.jpg", unlocked = true, unlockedAt = it.toLong(), percent = if (it == 1) 2.5f else 40f) }
        val toasts = SteamApi.announcement("Portal 2", unlocks, max = 3)
        assertEquals(4, toasts.size)
        assertEquals("You got “Thing 1”! Only 2.5 % of players have it!", toasts[0].text)
        assertEquals("https://cdn.example/1.jpg", toasts[0].icon)
        assertEquals("You got “Thing 2”! 40 % of players have it.", toasts[1].text)
        assertEquals("…and 2 more achievements in Portal 2. What a session!", toasts[3].text)
        assertNull(toasts[3].icon)
        // Rarity unknown: the game's name stands in. One unlock: no summary.
        val one = SteamApi.announcement("Portal 2", listOf(SteamAchievement(id = "x", name = "Solo", unlocked = true)))
        assertEquals(listOf("You got “Solo”! In Portal 2."), one.map { it.text })
        assertEquals(emptyList<SteamApi.Toast>(), SteamApi.announcement("Portal 2", emptyList()))
    }

    @Test fun percentLabels() {
        assertNull(SteamApi.percentLabel(-1f))
        assertEquals("under 1 %", SteamApi.percentLabel(0.4f))
        assertEquals("3.2 %", SteamApi.percentLabel(3.2f))
        assertEquals("62 %", SteamApi.percentLabel(62.0999f))
        assertTrue(SteamApi.rare(3.2f))
        assertFalse(SteamApi.rare(10.1f))
        assertFalse(SteamApi.rare(-1f))
    }

    @Test fun displayOrderPutsUnlockedFirstThenTheMostCommon() {
        val list = listOf(
            SteamAchievement(id = "rare-locked", name = "", percent = 2f),
            SteamAchievement(id = "old-unlock", name = "", unlocked = true, unlockedAt = 10L),
            SteamAchievement(id = "common-locked", name = "", percent = 70f),
            SteamAchievement(id = "new-unlock", name = "", unlocked = true, unlockedAt = 20L),
            SteamAchievement(id = "unknown-locked", name = ""),
        )
        assertEquals(listOf("new-unlock", "old-unlock", "common-locked", "rare-locked", "unknown-locked"), SteamApi.displayOrder(list).map { it.id })
    }

    @Test fun accountInputForms() {
        assertEquals(SteamApi.AccountInput(steamId = "76561198000000001"), SteamApi.parseAccount(" 76561198000000001 "))
        assertEquals(SteamApi.AccountInput(steamId = "76561198000000001"), SteamApi.parseAccount("https://steamcommunity.com/profiles/76561198000000001/"))
        assertEquals(SteamApi.AccountInput(vanity = "gaben"), SteamApi.parseAccount("https://steamcommunity.com/id/gaben/"))
        assertEquals(SteamApi.AccountInput(vanity = "gaben"), SteamApi.parseAccount("steamcommunity.com/id/gaben?xml=1"))
        assertEquals(SteamApi.AccountInput(vanity = "gaben"), SteamApi.parseAccount("gaben"))
        // The older id forms map onto SteamID64 arithmetic.
        assertEquals(SteamApi.AccountInput(steamId = "76561197960287930"), SteamApi.parseAccount("STEAM_0:0:11101"))
        assertEquals(SteamApi.AccountInput(steamId = "76561197960287930"), SteamApi.parseAccount("[U:1:22202]"))
        assertNull(SteamApi.parseAccount(""))
        assertNull(SteamApi.parseAccount("not a name"))
    }

    @Test fun vanityAndSummaryAnswers() {
        assertEquals("76561197960287930", SteamApi.parseVanity("""{"response":{"steamid":"76561197960287930","success":1}}"""))
        assertNull(SteamApi.parseVanity("""{"response":{"success":42,"message":"No match"}}"""))
        assertNull(SteamApi.parseVanity("<html>Forbidden</html>"))
        val p = SteamApi.parseSummaries("""{"response":{"players":[{"steamid":"76561197960287930","personaname":"Rabscuttle","avatarfull":"https://avatars.example/full.jpg","avatar":"https://avatars.example/small.jpg"}]}}""")!!
        assertEquals("76561197960287930", p.steamId)
        assertEquals("Rabscuttle", p.name)
        assertEquals("https://avatars.example/full.jpg", p.avatar)
        assertNull(SteamApi.parseSummaries("""{"response":{"players":[]}}"""))
    }

    @Test fun appIdsComeFromSteamShortcutsAndMoonlightSteamTags() {
        assertEquals(620, SteamApi.appId("steam", "620", emptyMap()))
        assertEquals(620, SteamApi.appId("steamappid", "app_id=620", emptyMap()))
        assertEquals(620, SteamApi.appId("STEAM", "[steam_app_id] 620", emptyMap()))
        assertNull(SteamApi.appId("steam", "not-a-number", emptyMap()))
        assertNull(SteamApi.appId("steam", null, emptyMap()))
        assertNull(SteamApi.appId("steam", "0", emptyMap()))
        // Sunshine's [appid] is a CRC32, not a Steam id: only an explicit [steam] line counts.
        assertNull(SteamApi.appId("moonlight", "[uuid] abc", mapOf("uuid" to "abc", "appid" to "1234567890")))
        assertEquals(620, SteamApi.appId("moonlight", "[uuid] abc", mapOf("uuid" to "abc", "appid" to "1234567890", "steam" to "620")))
        assertNull(SteamApi.appId("epic", "620", emptyMap()))
    }

    @Test fun urlsCarryTheKeyOnlyWhereSteamNeedsIt() {
        assertEquals("https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/?gameid=620", SteamApi.percentagesUrl(620))
        assertEquals("https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/?key=K%26Y&steamid=1&appid=620&l=english", SteamApi.playerUrl("K&Y", "1", 620))
        assertEquals("https://api.steampowered.com/ISteamUser/ResolveVanityURL/v1/?key=K&vanityurl=ga+ben", SteamApi.vanityUrl("K", "ga ben"))
    }
}
