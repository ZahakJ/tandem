package io.github.zahakj.tandem.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** This week's board (WeekBoard.kt) on a made-up library: slots, the weekly draw, history, diversity, privacy, feedback. */
class WeekBoardTest {
    private val zone = ZoneOffset.UTC
    private val day = WeekBoard.DAY
    private val hour = 3600L
    /** Thursday 26 November 2026, noon: ISO week 2026-W48. */
    private val now = LocalDate.of(2026, 11, 26).atTime(12, 0).toInstant(zone).toEpochMilli()
    private val state = WeekState(salt = 42)

    private fun g(
        key: String, platform: String = "snes", genres: List<String> = listOf("Platform"), rating: Float? = 0.7f,
        status: String? = null, statusAt: Long = 0, finishedAt: Long = 0, play: Long = 0, last: Long = 0, seen: Long = now - 400 * day,
        ttb: Int = 0, dev: String? = null, earned: Int = -1, total: Int = 0, hidden: Boolean = false, title: String = key, favorite: Boolean = false,
    ) = WeekGame(
        "$platform/$key", title, platform, genres, dev, rating, favorite, hidden, status, statusAt, finishedAt, play, last, seen, ttb, earned, total,
    )

    /** A library with something for every slot. */
    private val library: List<WeekGame> = buildList {
        // What you played: lots of RPGs, a finished Supergiant game.
        add(g("ff6", genres = listOf("Role-Playing"), play = 40 * hour, last = now - 20 * day, status = "finished", statusAt = now - 20 * day, finishedAt = now - 20 * day, dev = "Square"))
        add(g("hades", "switch", listOf("Action RPG", "Roguelike"), status = "completed", play = 60 * hour, last = now - 30 * day, statusAt = now - 30 * day, finishedAt = now - 30 * day, dev = "Supergiant Games", title = "Hades"))
        add(g("chrono", genres = listOf("RPG"), play = 25 * hour, last = now - 60 * day, status = "playing", statusAt = now - 70 * day, dev = "Square", ttb = 26 * 3600))
        // Being played, left a few weeks ago.
        add(g("zelda", "n64", listOf("Adventure"), play = 12 * hour, last = now - 25 * day, status = "playing", statusAt = now - 40 * day, ttb = 30 * 3600, earned = 10, total = 40))
        // Almost there by achievements.
        add(g("sonic", "md", listOf("Platform"), play = 5 * hour, last = now - 10 * day, earned = 18, total = 20))
        // Achievement hunt.
        add(g("mario", "gba", listOf("Platform"), play = 3 * hour, last = now - day, earned = 5, total = 30))
        // Not started: RPGs (your taste), shorts, gems, Supergiant's others.
        for (i in 1..12) add(g("rpg$i", if (i % 2 == 0) "psx" else "snes", listOf("Role-Playing"), rating = 0.6f + i * 0.02f))
        for (i in 1..6) add(g("short$i", "gb", listOf("Puzzle"), rating = 0.75f, ttb = (1 + i % 3) * 3600))
        add(g("bastion", "pc", listOf("Action RPG"), dev = "Supergiant Games", title = "Bastion", rating = 0.85f))
        add(g("transistor", "pc", listOf("Action RPG"), dev = "Supergiant Games", title = "Transistor", rating = 0.8f))
        for (i in 1..6) add(g("race$i", "n64", listOf("Racing"), rating = 0.95f))
        for (i in 1..5) add(g("fight$i", "arcade", listOf("Fighting"), rating = 0.5f))
        // Waiting a long time.
        add(g("shelved1", "ps2", listOf("Adventure"), status = "shelved", statusAt = now - 200 * day, play = 2 * hour, last = now - 200 * day))
        add(g("backlog1", "ps2", listOf("Shooter"), status = "backlog", statusAt = now - 100 * day))
        // New.
        add(g("new1", "gba", listOf("Puzzle"), seen = now - 3 * day))
        // Hidden: never on the board, whatever it is.
        add(g("secret", "snes", listOf("Role-Playing"), rating = 1f, hidden = true))
    }

    /** Better candidates for the hero than the game a small test is about, so it is left for its own row. */
    private val heroes = (1..6).map { g("hero$it", "psx", listOf("RPG"), rating = 1f) }

    private fun build(games: List<WeekGame> = library, at: Long = now, s: WeekState = state, upNext: List<String> = emptyList()) =
        WeekBoard.build(games, at, zone, s, upNext)

    private fun row(b: WeekBoardResult, slot: WeekSlot) = b.rows.firstOrNull { it.slot == slot }

    @Test fun weekKeysAreIso() {
        assertEquals("2026-W48", WeekBoard.weekOf(now, zone))
        assertEquals("2026-W53", WeekBoard.weekOf(LocalDate.of(2027, 1, 1)))
        assertEquals("2027-W01", WeekBoard.weekOf(LocalDate.of(2027, 1, 4)))
        assertEquals(listOf("2026-W47", "2026-W46"), WeekBoard.weeksBefore(now, zone, 2))
    }

    @Test fun everySlotFindsItsGames() {
        val b = build()
        val pick = row(b, WeekSlot.Pick)!!.picks.single()
        val rpgs = library.filter { it.playSeconds == 0L && it.genres.any { g -> "rpg" in Genres.buckets(g) } && !it.hidden }.map { it.key }
        assertTrue("the hero is an RPG you haven't started: ${pick.key}", pick.key in rpgs)
        assertTrue(pick.why is WeekWhy.Taste)
        assertEquals("Role-Playing", (pick.why as WeekWhy.Taste).genre)

        val resume = row(b, WeekSlot.Resume)!!.picks.map { it.key }
        assertTrue("n64/zelda" in resume)
        assertFalse("finished games don't come back: $resume", "snes/ff6" in resume)

        val almost = row(b, WeekSlot.Almost)!!.picks
        assertEquals(WeekWhy.AchLeft(18, 20), almost.first { it.key == "md/sonic" }.why)
        assertTrue("an hour left of Chrono by HowLongToBeat", almost.any { it.key == "snes/chrono" && it.why == WeekWhy.TimeLeft(3600) })

        val short = row(b, WeekSlot.Short)!!.picks
        assertTrue(short.all { it.key.startsWith("gb/short") && (it.why as WeekWhy.Short).seconds <= 3 * 3600 })

        val because = row(b, WeekSlot.Because)!!
        assertEquals("Hades", because.subject)
        // Supergiant's other games (one of them may be the hero instead) first: the studio beats a newer genre match.
        // Hades' genre fills the rest of the row.
        val studio = setOf("pc/bastion", "pc/transistor") - pick.key
        assertEquals(studio, because.picks.take(studio.size).map { it.key }.toSet())
        assertEquals(WeekWhy.Studio("Hades", "Supergiant Games"), because.picks.first().why)
        assertEquals(WeekSlot.Because.size, because.picks.size)
        assertTrue(because.picks.drop(studio.size).all { (it.why as WeekWhy.SameGenre).finished == "Hades" })

        assertEquals(listOf("gba/mario"), row(b, WeekSlot.Hunt)!!.picks.map { it.key })
        assertTrue(row(b, WeekSlot.Gem)!!.picks.all { it.key.startsWith("n64/race") })
        val shelf = row(b, WeekSlot.Shelf)!!.picks.associate { it.key to it.why }
        assertEquals(WeekWhy.Shelved(now - 200 * day), shelf["ps2/shelved1"])
        assertEquals(WeekWhy.Backlogged(now - 100 * day), shelf["ps2/backlog1"])
        assertEquals(WeekWhy.Added(3), row(b, WeekSlot.New)!!.picks.single().why)
    }

    @Test fun aGameIsOnTheBoardOnce() {
        val keys = build().picks.map { it.key }
        assertEquals(keys.distinct(), keys)
    }

    @Test fun picksComeWithFacts() {
        val zelda = build().pickOf("n64/zelda")!!
        assertTrue(zelda.facts.contains(WeekFact.TimeLeft(18 * 3600)))
        assertTrue(zelda.facts.contains(WeekFact.Achievements(10, 40)))
    }

    @Test fun theBoardStaysTheSameAllWeek() {
        val monday = LocalDate.of(2026, 11, 23).atTime(8, 0).toInstant(zone).toEpochMilli()
        val a = build(at = monday)
        val b = build(at = monday + 5 * day)
        assertEquals(a.week, b.week)
        assertEquals(a.picks.map { it.key }, b.picks.map { it.key })
    }

    @Test fun aNewWeekDrawsAgain() {
        val weeks = (0 until 6).map { build(at = now + it * 7 * day).picks.filter { p -> p.slot != WeekSlot.Pick }.map { p -> p.key }.toSet() }
        assertTrue("the board changes from week to week", weeks.zipWithNext().any { (x, y) -> x != y })
    }

    @Test fun theSaltMakesItYourOwn() {
        val boards = (1L..8L).map { s -> build(s = WeekState(salt = s)).picks.map { it.key } }.toSet()
        assertTrue(boards.size > 1)
    }

    @Test fun recentPicksStayAwayForFourWeeks() {
        val first = build()
        val hero = row(first, WeekSlot.Pick)!!.picks.single().key
        val remembered = WeekBoard.remember(state, first, now, zone)
        val nextWeek = build(at = now + 7 * day, s = remembered)
        assertNotEquals(hero, row(nextWeek, WeekSlot.Pick)!!.picks.single().key)
        // Six racing gems, four shown: next week the two not shown lead, and last week's best fill the row.
        val gems = row(first, WeekSlot.Gem)!!.picks.map { it.key }.toSet()
        val next = row(nextWeek, WeekSlot.Gem)!!.picks.map { it.key }
        assertEquals(gems.size, next.size)
        assertTrue(next.take(2).none { it in gems })
    }

    @Test fun historyIsAvoidedOnlyWhileSomethingElseIsLeft() {
        // One short game in the library: shown last week, it still fills its slot.
        val one = listOf(g("tiny", "gb", listOf("Puzzle"), ttb = 3600), g("played", play = 3 * hour, last = now - 5 * day)) + heroes
        val last = WeekState(salt = 1, shown = listOf(WeekShown("2026-W47", "short", "gb/tiny")))
        assertEquals(listOf("gb/tiny"), row(build(one, s = last), WeekSlot.Short)!!.picks.map { it.key })
    }

    @Test fun historyOlderThanEightWeeksIsDropped() {
        val old = WeekState(shown = listOf(WeekShown("2026-W30", "pick", "x"), WeekShown("2026-W45", "pick", "y")))
        val s = WeekBoard.remember(old, build(), now, zone)
        assertFalse(s.shown.any { it.week == "2026-W30" })
        assertTrue(s.shown.any { it.week == "2026-W45" })
        assertTrue(s.shown.any { it.week == "2026-W48" })
        assertEquals(s, WeekBoard.remember(s, build(s = s), now, zone))
    }

    @Test fun picksStickWhileTheyStillQualify() {
        val first = build()
        val s = WeekBoard.remember(state, first, now, zone)
        // A new game arrives mid-week: the hero and the rows already shown stay.
        val more = library + g("rpg99", "snes", listOf("Role-Playing"), rating = 1f)
        val later = build(more, at = now + day, s = s)
        assertEquals(row(first, WeekSlot.Pick)!!.picks, row(later, WeekSlot.Pick)!!.picks)
        // The hero is started: it leaves the slot, and another takes its place.
        val hero = row(first, WeekSlot.Pick)!!.picks.single().key
        val played = library.map { if (it.key == hero) it.copy(playSeconds = 2 * hour, lastPlayed = now, status = "playing") else it }
        assertNotEquals(hero, row(build(played, at = now + day, s = s), WeekSlot.Pick)!!.picks.single().key)
    }

    @Test fun oneSystemOrGenreDoesNotFillTheBoard() {
        // Fifteen equally good SNES RPGs and a few others: the genre row still mixes systems once diversity weighs in.
        val lib = buildList {
            add(g("played", genres = listOf("RPG"), play = 30 * hour, last = now - 3 * day, status = "finished", statusAt = now - 3 * day))
            for (i in 1..15) add(g("snes$i", "snes", listOf("RPG"), rating = 0.8f))
            for (i in 1..4) add(g("psx$i", "psx", listOf("RPG"), rating = 0.8f))
            for (i in 1..4) add(g("gba$i", "gba", listOf("RPG"), rating = 0.8f))
        }
        val genre = row(build(lib), WeekSlot.Genre)!!.picks
        val systems = genre.groupingBy { it.key.substringBefore('/') }.eachCount()
        assertTrue("systems in the genre row: $systems", systems.size >= 2)
        assertTrue("no system takes the whole row: $systems", systems.values.all { it < genre.size })
    }

    @Test fun hiddenGamesNeverShow() {
        val b = build(upNext = listOf("snes/secret"))
        assertNull(b.pickOf("snes/secret"))
        // Not even as a taste signal: a hidden game played for hours doesn't make its genre yours.
        val lib = listOf(
            g("secretplay", "snes", listOf("Horror"), play = 100 * hour, last = now - day, hidden = true),
        ) + (1..5).map { g("horror$it", "psx", listOf("Horror"), rating = 0.8f) }
        assertNull(row(build(lib), WeekSlot.Genre))
        val pick = row(build(lib), WeekSlot.Pick)!!.picks.single()
        assertFalse(pick.why is WeekWhy.Taste)
    }

    @Test fun notForMeIsNeverSuggestedAgain() {
        val b = build()
        val hero = row(b, WeekSlot.Pick)!!.picks.single().key
        val s = WeekBoard.notForMe(state, hero)
        for (w in 0 until 10) assertNull(build(at = now + w * 7 * day, s = s, upNext = listOf(hero)).pickOf(hero))
    }

    @Test fun playedElsewhereLeavesTheBoardAndFeedsTheTaste() {
        val hero = row(build(), WeekSlot.Pick)!!.picks.single().key
        val finished = library.map { if (it.key == hero) it.copy(status = "finished", statusAt = now, finishedAt = now) else it }
        val after = build(finished)
        assertNull(after.pickOf(hero))
        assertTrue(WeekBoard.Taste(finished, now).genre.getValue(Genres.buckets("Role-Playing").first()) > 0.0)
        assertTrue(WeekBoard.Taste.weight(finished.first { it.key == hero }, now) > 0.0)
    }

    @Test fun upNextLeadsTheBoard() {
        val b = build(upNext = listOf("pc/bastion", "gb/short1"))
        assertEquals(WeekSlot.UpNext, b.rows.first().slot)
        assertEquals(listOf("pc/bastion", "gb/short1"), b.rows.first().picks.map { it.key })
        // Not twice.
        assertEquals(1, b.picks.count { it.key == "pc/bastion" })
    }

    @Test fun rerollDrawsSomethingElseFromDeeperDown() {
        val b = build()
        val s0 = WeekBoard.remember(state, b, now, zone)
        val before = row(b, WeekSlot.Pick)!!.picks.single().key
        val s1 = WeekBoard.reroll(s0, b, WeekSlot.Pick)
        assertEquals(1, s1.rerolls["pick"])
        val after = build(s = s1)
        val rerolled = row(after, WeekSlot.Pick)!!.picks.single().key
        assertNotEquals(before, rerolled)
        // Other rows stay as they were.
        assertEquals(row(b, WeekSlot.Gem), row(after, WeekSlot.Gem))
        // Re-rolls belong to their week.
        assertNull(WeekBoard.remember(s1, build(at = now + 7 * day, s = s1), now + 7 * day, zone).rerolls["pick"])
    }

    /** A made-up library of [n] games from [seed], with something for most slots. */
    private fun randomLibrary(seed: Int, n: Int = 240): List<WeekGame> {
        val genres = listOf("RPG", "Action", "Platform", "Puzzle", "Racing", "Shooter", "Sports", "Strategy", "Fighting", "Adventure")
        val systems = listOf("snes", "gba", "psx", "n64", "md", "ps2", "switch", "gb")
        val r = kotlin.random.Random(seed)
        return (1..n).map { i ->
            val played = r.nextInt(4) == 0
            val beaten = played && r.nextInt(3) == 0
            g(
                "g$i", systems[r.nextInt(systems.size)], listOf(genres[r.nextInt(genres.size)]), rating = if (r.nextInt(5) > 0) r.nextFloat() else null,
                play = if (played) r.nextLong(1, 40) * hour else 0, last = if (played) now - r.nextLong(1, 150) * day else 0,
                status = if (beaten) "finished" else if (played) listOf(null, "playing", "shelved").random(r) else listOf(null, null, "backlog").random(r),
                statusAt = now - r.nextLong(1, 300) * day, finishedAt = if (beaten) now - r.nextLong(1, 120) * day else 0,
                ttb = if (r.nextInt(3) == 0) r.nextInt(1, 30) * 3600 else 0, seen = now - r.nextLong(1, 600) * day,
                dev = "Studio ${r.nextInt(40)}", earned = if (played && r.nextBoolean()) r.nextInt(1, 30) else -1, total = 30,
                title = "${listOf("Mega", "Star", "Dragon", "Iron", "Moon")[r.nextInt(5)]} Quest ${r.nextInt(4)}",
            )
        }
    }

    /** A row as the screen sees it: its slot, its heading's subject and its games, in order. */
    private fun look(r: WeekRow) = Triple(r.slot, r.subject, r.picks.map { it.key })

    private fun assertOnce(b: WeekBoardResult) {
        val keys = b.picks.map { it.key }
        assertEquals("a game shows twice: ${keys.groupBy { it }.filter { it.value.size > 1 }.keys}", keys.size, keys.toSet().size)
    }

    @Test fun aRerollChangesOnlyItsOwnRow() {
        var rerolled = 0
        for (seed in 1..8) {
            val lib = randomLibrary(seed)
            val upNext = lib.filter { !it.beaten }.take(2).map { it.key }
            for (noted in listOf(true, false)) {
                val b = build(lib, upNext = upNext)
                // Noted (as the device does right after the board shows) or not yet: the rest of the board stays.
                val s0 = if (noted) WeekBoard.remember(state, b, now, zone) else state
                for (row in b.rows.filter { it.slot != WeekSlot.UpNext }) {
                    val after = build(lib, s = WeekBoard.reroll(s0, b, row.slot), upNext = upNext)
                    val others = { x: WeekBoardResult -> x.rows.filter { it.slot != row.slot }.map(::look) }
                    assertEquals("seed $seed, re-roll ${row.slot}: the other rows changed", others(b), others(after))
                    assertOnce(after)
                    val mine = row(after, row.slot)?.picks?.map { it.key }.orEmpty()
                    if (mine.isNotEmpty() && mine != row.picks.map { it.key }) rerolled++
                    // Nothing it draws was elsewhere on the board.
                    val elsewhere = b.rows.filter { it.slot != row.slot }.flatMap { r -> r.picks.map { it.key } }.toSet()
                    assertTrue("seed $seed, ${row.slot} drew a game from another row", mine.none { it in elsewhere })
                }
            }
        }
        assertTrue("re-rolls drew something new ($rerolled times)", rerolled > 50)
    }

    @Test fun rerollingAgainAndAgainKeepsTheRestAndRunsOutGently() {
        for (seed in 1..4) {
            val lib = randomLibrary(seed, 120)
            var b = build(lib)
            var s = WeekBoard.remember(state, b, now, zone)
            val first = b
            val slot = b.rows.first { it.slot != WeekSlot.Pick && it.slot != WeekSlot.UpNext }.slot
            val seen = HashSet<String>()
            repeat(25) {
                seen += row(b, slot)!!.picks.map { it.key }
                s = WeekBoard.reroll(s, b, slot)
                b = build(lib, s = s)
                s = WeekBoard.remember(s, b, now, zone)
                assertOnce(b)
                assertEquals("seed $seed, re-roll $it of $slot", first.rows.filter { it.slot != slot }.map(::look), b.rows.filter { it.slot != slot }.map(::look))
                // The row never goes away: once its games run out it shows ones it showed before.
                assertNotNull("seed $seed: $slot went away", row(b, slot))
            }
            assertTrue(seen.size > row(first, slot)!!.picks.size)
        }
    }

    /**
     * A library the way a handheld has one: [n] games over [systems] systems, a quarter played, a few finished lately
     * (series and studios to follow up), HowLongToBeat and ratings for most.
     */
    private fun realLibrary(seed: Int, n: Int, systems: Int): List<WeekGame> {
        val genres = listOf("RPG", "Action", "Platform", "Puzzle", "Racing", "Shooter", "Sports", "Strategy", "Fighting", "Adventure", "Action RPG", "Simulation")
        val sys = listOf("snes", "gba", "psx", "n64", "md", "ps2", "switch", "gb", "nds", "3ds", "psp", "gc").take(systems)
        val series = listOf("Mega Man", "Pokemon", "Fire Emblem", "Final Fantasy", "Zelda", "Metroid", "Castlevania", "Mario Kart", "Tales of", "Kirby")
        val r = kotlin.random.Random(seed)
        return (1..n).map { i ->
            val played = r.nextInt(4) == 0
            val beaten = played && r.nextInt(3) == 0
            val s = series[r.nextInt(series.size)]
            g(
                "g$i", sys[r.nextInt(sys.size)], listOf(genres[r.nextInt(genres.size)]), rating = if (r.nextInt(6) > 0) 0.4f + r.nextFloat() * 0.6f else null,
                play = if (played) r.nextLong(1, 60) * hour else 0, last = if (played) now - r.nextLong(1, 150) * day else 0,
                status = if (beaten) listOf("finished", "completed").random(r) else if (played) listOf(null, "playing", "shelved").random(r) else listOf(null, null, "backlog").random(r),
                statusAt = now - r.nextLong(1, 300) * day, finishedAt = if (beaten) now - r.nextLong(1, 120) * day else 0,
                ttb = if (r.nextInt(3) > 0) r.nextInt(1, 40) * 3600 else 0, seen = now - r.nextLong(1, 700) * day,
                dev = "Studio ${r.nextInt(25)}", earned = if (played && r.nextBoolean()) r.nextInt(1, 30) else -1, total = 30,
                title = if (r.nextInt(3) == 0) "$s ${r.nextInt(1, 8)}" else "Game $i",
            )
        }
    }

    /**
     * X on any row, again and again, on libraries of every size: the row keeps as many covers as it had (it never
     * condenses to one), or more for a row with a subject that moves to a fuller one, and they are all different.
     */
    @Test fun aRerollKeepsTheRowFull() {
        val shapes = listOf(50 to 3, 50 to 10, 200 to 2, 200 to 8, 2000 to 4, 2000 to 12)
        var checked = 0
        for ((n, systems) in shapes) for (seed in 1..3) {
            val lib = realLibrary(seed * 31 + n, n, systems)
            val first = build(lib)
            for (start in first.rows.filter { it.slot != WeekSlot.UpNext }) {
                val slot = start.slot
                var b = first
                var s = WeekBoard.remember(state, b, now, zone)
                repeat(8) { i ->
                    val before = row(b, slot)!!.picks.map { it.key }
                    s = WeekBoard.reroll(s, b, slot)
                    b = build(lib, s = s)
                    s = WeekBoard.remember(s, b, now, zone)
                    val after = row(b, slot)?.picks?.map { it.key }
                    val where = "$n games on $systems systems (seed $seed), re-roll ${i + 1} of $slot"
                    assertNotNull("$where: the row went away", after)
                    // As many as before (a genre row may move to a genre with more).
                    assertTrue("$where: $before became $after", after!!.size >= before.size)
                    if (slot != WeekSlot.Explore && slot != WeekSlot.Genre && slot != WeekSlot.Because) assertEquals("$where: $before became $after", before.size, after.size)
                    assertEquals("$where: a game twice in the row", after.size, after.toSet().size)
                    assertOnce(b)
                    checked++
                }
            }
        }
        assertTrue(checked > 300)
    }

    /** While a row has enough games it hasn't shown (any row, on a big library), a re-roll changes every cover in it. */
    @Test fun aRerollReplacesEveryCoverWhileThereAreEnough() {
        for ((n, systems) in listOf(2000 to 4, 2000 to 12)) for (seed in 1..3) {
            val lib = realLibrary(seed * 7 + n, n, systems)
            val b = build(lib)
            val s = WeekBoard.remember(state, b, now, zone)
            for (r in b.rows.filter { it.slot != WeekSlot.UpNext }) {
                val before = r.picks.map { it.key }.toSet()
                val after = row(build(lib, s = WeekBoard.reroll(s, b, r.slot)), r.slot)!!.picks.map { it.key }
                assertEquals("$n games, $systems systems (seed $seed): ${r.slot} kept ${after.filter { it in before }}", 0, after.count { it in before })
            }
        }
    }

    @Test fun theHeroStaysUnlessItIsTheOneRerolled() {
        val b = build()
        val s0 = WeekBoard.remember(state, b, now, zone)
        val hero = row(b, WeekSlot.Pick)!!.picks.single().key
        for (slot in b.rows.map { it.slot }.filter { it != WeekSlot.Pick }) {
            assertEquals(hero, row(build(s = WeekBoard.reroll(s0, b, slot)), WeekSlot.Pick)!!.picks.single().key)
        }
    }

    @Test fun aNewWeekRefreshesEveryRowAfterRerolls() {
        val lib = randomLibrary(3)
        var b = build(lib)
        var s = WeekBoard.remember(state, b, now, zone)
        for (r in b.rows) { s = WeekBoard.reroll(s, b, r.slot); b = build(lib, s = s); s = WeekBoard.remember(s, b, now, zone) }
        val next = now + 7 * day
        val nb = build(lib, at = next, s = s)
        val ns = WeekBoard.remember(s, nb, next, zone)
        assertTrue(ns.rerolls.isEmpty() && ns.skipped.isEmpty())
        assertEquals("2026-W49", nb.week)
        // Last week's picks stay away while anything else is left: no row is last week's again.
        for (r in nb.rows) assertNotEquals("${r.slot} is last week's", row(b, r.slot)?.picks?.map { it.key }, r.picks.map { it.key })
    }

    @Test fun rerollingARowWithFewGamesStillShowsSomething() {
        val lib = listOf(g("tiny", "gb", listOf("Puzzle"), ttb = 3600), g("played", play = 3 * hour, last = now - 5 * day)) + heroes
        val b = build(lib)
        val s = WeekBoard.reroll(WeekBoard.remember(state, b, now, zone), b, WeekSlot.Short)
        assertEquals(listOf("gb/tiny"), row(build(lib, s = s), WeekSlot.Short)!!.picks.map { it.key })
    }

    @Test fun resetForgetsFeedbackButKeepsTheSwitchAndSalt() {
        val s = WeekState(on = false, salt = 9, notForMe = setOf("a"), shown = listOf(WeekShown("2026-W48", "pick", "b")), week = "2026-W48", rerolls = mapOf("pick" to 2), skipped = setOf("c"))
        assertEquals(WeekState(on = false, salt = 9), WeekBoard.reset(s))
    }

    @Test fun aFreshLibraryStillGetsABoard() {
        // Just scanned: nothing played, everything new. No "New" row (it would be everything), but a pick and genres.
        val lib = (1..30).map { g("g$it", listOf("snes", "gba", "psx")[it % 3], listOf(listOf("RPG"), listOf("Puzzle"), listOf("Racing"))[it % 3], rating = 0.5f + (it % 10) * 0.05f, seen = now - day) }
        val b = build(lib)
        assertNotNull(row(b, WeekSlot.Pick))
        assertEquals(WeekWhy.TopRated, row(b, WeekSlot.Pick)!!.picks.single().why)
        assertNull(row(b, WeekSlot.New))
        assertNull(row(b, WeekSlot.Genre))
        val explore = row(b, WeekSlot.Explore)!!
        assertTrue((explore.picks.first().why as WeekWhy.NewGenre).never)
    }

    @Test fun unratedGamesStillCompete() {
        val lib = listOf(g("unrated", "gb", listOf("Puzzle"), rating = null, ttb = 2 * 3600), g("played", play = 3 * hour, last = now - 5 * day)) + heroes
        assertEquals("gb/unrated", row(build(lib), WeekSlot.Short)!!.picks.single().key)
    }

    @Test fun aBigLibraryIsQuick() {
        val genres = listOf("RPG", "Action", "Platform", "Puzzle", "Racing", "Shooter", "Sports", "Strategy", "Fighting", "Adventure") + (1..200).map { "Folder $it" }
        val systems = listOf("snes", "gba", "psx", "n64", "md", "ps2", "switch", "gb")
        val r = kotlin.random.Random(7)
        val lib = (1..10_000).map { i ->
            val played = r.nextInt(10) == 0
            g(
                "g$i", systems[i % systems.size], listOf(genres[r.nextInt(genres.size)]), rating = if (r.nextBoolean()) r.nextFloat() else null,
                play = if (played) r.nextLong(1, 50) * hour else 0, last = if (played) now - r.nextLong(1, 200) * day else 0,
                status = if (played) listOf(null, "playing", "finished", "shelved").random(r) else listOf(null, "backlog").random(r),
                statusAt = now - r.nextLong(1, 400) * day, finishedAt = if (played) now - r.nextLong(1, 100) * day else 0,
                ttb = if (r.nextInt(3) == 0) r.nextInt(1, 40) * 3600 else 0, seen = now - r.nextLong(1, 800) * day,
                dev = "Studio ${r.nextInt(300)}", earned = if (played && r.nextBoolean()) r.nextInt(0, 30) else -1, total = 30,
            )
        }
        build(lib) // warm up
        // The best of three: a busy machine (parallel builds) slows one run, not the code.
        var ms = Long.MAX_VALUE
        var b = build(lib)
        repeat(3) {
            val start = System.nanoTime()
            b = build(lib)
            ms = minOf(ms, (System.nanoTime() - start) / 1_000_000)
        }
        println("WeekBoard: 10000 games in $ms ms, ${b.picks.size} picks")
        assertTrue("built in $ms ms", ms < 1500)
        assertTrue(b.picks.size >= 20)
    }

    /**
     * A re-roll on the device builds the board for the re-rolled state off the main thread and saves that state with
     * the board noted in one step (ThisWeek.reroll). That is right only if noting it changes nothing: the board built
     * from the noted state is the same, and building it on another thread is too.
     */
    @Test fun aRerolledBoardIsTheSameNotedAndOffThread() {
        val pool = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            var s = WeekBoard.remember(state, build(), now, zone)
            var b = build(s = s)
            for (i in 0 until 30) {
                val slot = b.rows.filter { it.slot != WeekSlot.UpNext }.let { it[i % it.size] }.slot
                val next = WeekBoard.reroll(s, b, slot)
                val here = build(s = next)
                val elsewhere = pool.submit<WeekBoardResult> { build(s = next) }.get()
                assertEquals(here, elsewhere)
                val noted = WeekBoard.remember(next, here, now, zone)
                assertEquals("noting the board doesn't change it (re-roll $i, $slot)", here, build(s = noted))
                s = noted
                b = here
            }
        } finally {
            pool.shutdown()
        }
    }

    /** What a WeekGame works out once (its buckets, studio, series) is what the board used to ask for each time. */
    @Test fun aGameKeepsWhatItWorksOut() {
        for (w in library + heroes + g("odd", genres = listOf("RPG & Strategy", "Point & Click"), dev = "  ", title = "Mega Man X: Part 2")) {
            assertEquals(Genres.bucketsOf(w.genres), WeekBoard.bucketsOf(w))
            assertEquals(w.developer?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }, w.studio)
            assertEquals(WeekBoard.seriesOf(w.title), w.series)
            assertEquals(GameStatus.entries.firstOrNull { it.id == w.status }, w.gameStatus)
        }
        // A changed copy works them out again; equality is about the game's values only.
        val a = g("x", genres = listOf("Racing"), dev = "Nintendo", status = "playing")
        val b = a.copy(genres = listOf("Puzzle"), developer = "Sega", status = "finished", title = "Sonic 2")
        assertEquals(listOf("puzzle"), b.buckets)
        assertEquals("sega", b.studio)
        assertEquals("sonic", b.series)
        assertEquals(GameStatus.Finished, b.gameStatus)
        assertEquals(a, a.copy())
    }

    @Test fun genresFallIntoBuckets() {
        assertEquals("rpg", Genres.buckets("Action RPG").first())
        assertEquals("rpg", Genres.buckets("Role-Playing").first())
        assertEquals("racing", Genres.buckets("Racing").first())
        assertEquals("adventure", Genres.buckets("Action-Adventure").first())
        assertEquals("my folder", Genres.buckets(" My Folder ").first())
    }

    @Test fun seriesComeFromTitles() {
        assertEquals("mega man", WeekBoard.seriesOf("Mega Man 2"))
        assertEquals("mega man", WeekBoard.seriesOf("Mega Man X"))
        assertEquals("final fantasy", WeekBoard.seriesOf("Final Fantasy VI"))
        assertEquals("metroid", WeekBoard.seriesOf("Metroid: Zero Mission"))
        assertNull(WeekBoard.seriesOf("Ys"))
    }
}
