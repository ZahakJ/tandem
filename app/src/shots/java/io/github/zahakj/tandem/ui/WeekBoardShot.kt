package io.github.zahakj.tandem.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.zahakj.tandem.ArtIndex
import io.github.zahakj.tandem.Game
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.Libraries
import io.github.zahakj.tandem.PlatformLibrary
import io.github.zahakj.tandem.Platforms
import io.github.zahakj.tandem.ThisWeek
import io.github.zahakj.tandem.data.GameMeta
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.data.TimeToBeat
import io.github.zahakj.tandem.data.WeekSlot
import io.github.zahakj.tandem.data.WeekStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * This week's board itself (WeekBoardScreen) on a made-up library of 300 games over eight systems, with drawn box and
 * hero art, at the Thor's top screen (1920×1080 px at 369 dpi): as a page and as a popup, in English and Arabic, and
 * a re-roll of a row with a highlighted card frame by frame, with the pixel rows that changed between the first and
 * last frame written down (only the re-rolled row's band may change). Only with `-Ptandem.shots[=folder]`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-land-mdpi")
class WeekBoardShot {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val dir by lazy { File(System.getProperty("tandem.shots") ?: "build/shots").apply { mkdirs() } }
    private val route = "col:week"

    private val words = listOf(
        "Crystal", "Shadow", "Iron", "Moon", "Star", "Dragon", "Echo", "Pixel", "River", "Ember", "Frost", "Neon", "Solar", "Velvet", "Thunder",
        "Lantern", "Hollow", "Silver", "Wild", "Paper",
    )
    private val nouns = listOf("Quest", "Saga", "Knights", "Racer", "Tactics", "Legends", "Odyssey", "Drift", "Kingdom", "Runner", "Chronicle", "Heroes")
    private val genres = listOf("Role-Playing", "Action", "Platform", "Puzzle", "Racing", "Shooter", "Sports", "Strategy", "Fighting", "Adventure")

    private fun art(name: String, w: Int, h: Int, seed: Int): String {
        val f = File(dir, "art/$name.png")
        if (f.isFile) return f.path
        f.parentFile!!.mkdirs()
        val r = Random(seed)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val a = android.graphics.Color.HSVToColor(floatArrayOf(r.nextFloat() * 360f, 0.55f, 0.85f))
        val b = android.graphics.Color.HSVToColor(floatArrayOf(r.nextFloat() * 360f, 0.7f, 0.35f))
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a, b, Shader.TileMode.CLAMP) })
        val p = Paint().apply { color = android.graphics.Color.argb(70, 255, 255, 255); isAntiAlias = true }
        repeat(5) { c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, (0.1f + r.nextFloat() * 0.3f) * w, p) }
        val t = Paint().apply { color = android.graphics.Color.WHITE; textSize = w / 8f; isAntiAlias = true; isFakeBoldText = true }
        name.split('_').forEachIndexed { i, s -> c.drawText(s, w * 0.08f, h * 0.2f + i * t.textSize * 1.2f, t) }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.path
    }

    /**
     * [perSystem] games on each of eight systems (300 by default): a quarter played, some finished lately,
     * HowLongToBeat and ratings for most, box art for all.
     */
    private fun library(perSystem: Int = 38) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Store.init(ctx)
        WeekStore.init(ctx)
        WeekStore.update { io.github.zahakj.tandem.data.WeekState(salt = 7) }
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val ids = listOf("snes", "gba", "psx", "n64", "ps2", "nds", "3ds", "psp")
        val platforms = Platforms.all(ctx).filter { it.id in ids }
        val r = Random(11)
        val metas = HashMap<String, (GameMeta) -> GameMeta>()
        val libs = platforms.map { pf ->
            val games = (1..perSystem).map { i ->
                val title = "${words[r.nextInt(words.size)]} ${nouns[r.nextInt(nouns.size)]}" + (if (r.nextInt(3) == 0) " ${r.nextInt(2, 5)}" else "")
                val g = Game(pf, File("/sdcard/ROMs/${pf.id}/${pf.id}-$i.bin"), title, null)
                val played = r.nextInt(4) == 0
                val beaten = played && r.nextInt(3) == 0
                val box = art(title.replace(' ', '_') + "_${pf.id}$i", 300, 300, i * 31 + pf.id.hashCode())
                val hero = art("hero_${pf.id}$i", 960, 540, i * 17 + pf.id.hashCode())
                val ttb = if (r.nextInt(3) > 0) r.nextInt(1, 30) * 3600 else 0
                val genre = genres[r.nextInt(genres.size)]
                val rating = if (r.nextInt(6) > 0) 0.45f + r.nextFloat() * 0.55f else null
                val playS = if (played) r.nextLong(1, 50) * 3600 else 0L
                val last = if (played) now - r.nextLong(1, 120) * day else 0L
                val status = if (beaten) "finished" else if (played) listOf(null, "playing", "shelved").random(r) else listOf(null, null, "backlog").random(r)
                val statusAt = now - r.nextLong(1, 300) * day
                val finishedAt = if (beaten) now - r.nextLong(1, 100) * day else 0L
                val seen = now - r.nextLong(1, 700) * day
                val dev = "Studio ${r.nextInt(20)}"
                metas[g.key] = { m ->
                    m.copy(
                        title = title, genres = listOf(genre), developer = dev, rating = rating, box = box, hero = hero, playSeconds = playS,
                        lastPlayed = last, status = status, statusAt = statusAt, finishedAt = finishedAt, firstSeen = seen,
                        timeToBeat = if (ttb > 0) TimeToBeat(main = ttb) else null, playCount = if (played) 3 else 0,
                        description = "A made-up game for the screenshots.",
                    )
                }
                g
            }
            PlatformLibrary(pf, games, ArtIndex(emptyList(), true, emptyList()))
        }
        Store.updateAll(metas)
        Libraries.all = libs
    }

    private fun content(popup: Boolean) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(369f / 160f, 1f)) {
                TandemTheme {
                    if (popup) {
                        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
                            FolderSheet(androidx.compose.ui.res.stringResource(io.github.zahakj.tandem.R.string.week_title), weekOfText(), Palette.Lavender, onBack = {}, actions = { WeekBoardHints() }) {
                                WeekBoardScreen(route, popup = true)
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().padding(top = TopBarHeight)) { WeekBoardScreen(route, popup = false) }
                    }
                }
            }
        }
    }

    private fun settle(ms: Long = 2500) {
        // Coil decodes the art on its own threads: let them finish between frames.
        repeat((ms / 250).toInt()) {
            rule.mainClock.advanceTimeBy(250)
            rule.waitForIdle()
            Thread.sleep(40)
        }
    }

    private fun grab(): Bitmap {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    private fun save(bmp: Bitmap, name: String) = File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

    /** The board opens at its top, the highlight on the first row's second card (or, [last], the last row's first). */
    private fun highlight(last: Boolean = false) {
        ScrollMemory.put(route, 0, 0)
        val rows = ThisWeek.board().rows
        for (r in rows) ScrollMemory.put("$route:${r.slot.id}", 0, 0)
        val row = if (last) rows.last() else rows.first { it.slot != WeekSlot.Pick && it.slot != WeekSlot.UpNext }
        FocusMemory.remember(route, "w:${row.picks[if (last) 0 else minOf(1, row.picks.lastIndex)].key}")
    }

    private fun board(tag: String, popup: Boolean, name: String, games: Int = 38, last: Boolean = false) {
        library(games)
        Lang.choose(tag)
        highlight(last)
        rule.mainClock.autoAdvance = false
        content(popup)
        settle()
        save(grab(), name)
        File(dir, "$name.txt").writeText(ThisWeek.board().rows.joinToString("\n") { r -> "${r.slot} ${r.subject.orEmpty()}: ${r.picks.size}" })
    }

    @Test fun pageEnglish() = board("en", false, "week-page-en")
    @Test fun popupEnglish() = board("en", true, "week-popup-en")
    @Test fun pageArabic() = board("ar", false, "week-page-ar")
    @Test fun popupJapanese() = board("ja", true, "week-popup-ja")
    /** Scrolled down to the last rows (a six-game genre row peeks its fifth card). */
    @Test fun pageScrolled() = board("en", false, "week-page-scrolled", last = true)
    /** A small library: rows of one or two games, whose cards have room for the whole reason. */
    @Test fun popupSmall() = board("en", true, "week-popup-small", games = 5)
    @Test fun popupSmallScrolled() = board("fr", true, "week-popup-small-fr", games = 5, last = true)

    /**
     * The second card of the second row highlighted, then X: frames of the deal, a contact sheet, and the pixel rows
     * that differ between the first and the last frame (they must all be in that row's band).
     */
    private fun reroll(popup: Boolean, name: String) {
        library()
        Lang.choose("en")
        val b = ThisWeek.board()
        val row = b.rows.filter { it.slot != WeekSlot.Pick && it.slot != WeekSlot.UpNext }.first()
        val held = row.picks[minOf(1, row.picks.lastIndex)]
        ScrollMemory.put(route, 0, 0)
        for (r in b.rows) ScrollMemory.put("$route:${r.slot.id}", 0, 0)
        FocusMemory.remember(route, "w:${held.key}")
        rule.mainClock.autoAdvance = false
        content(popup)
        settle()
        val frames = ArrayList<Pair<String, Bitmap>>()
        frames += "before" to grab()
        ThisWeek.reroll(row.slot)
        var t = 0
        for (at in listOf(16, 64, 112, 160, 224, 320, 480, 1000, 2500)) {
            rule.mainClock.advanceTimeBy((at - t).toLong()); t = at
            rule.waitForIdle()
            Thread.sleep(30)
            frames += "$at ms" to grab()
        }
        settle(1500)
        frames += "after" to grab()
        save(frames.first().second, "$name-before")
        save(frames.last().second, "$name-after")
        val first = frames.first().second
        val last = frames.last().second
        // Which pixel rows changed: one band (the re-rolled row) when nothing moved.
        val changed = (0 until first.height).filter { y -> (0 until first.width step 2).any { x -> first.getPixel(x, y) != last.getPixel(x, y) } }
        val bands = ArrayList<IntRange>()
        for (y in changed) if (bands.isNotEmpty() && y - bands.last().last <= 6) bands[bands.lastIndex] = bands.last().first..y else bands += y..y
        val keys = ThisWeek.board().rows.first { it.slot == row.slot }.picks.map { it.key }
        File(dir, "$name.txt").writeText(
            "re-rolled ${row.slot}: ${row.picks.map { it.key }} -> $keys\nheld ${held.key} -> now ${FocusMemory.get(route)} (holder ${FocusMemory.holder(route)})\n" +
                "changed pixel rows: $bands\n",
        )
        val w = first.width / 3
        val h = first.height / 3
        val cols = 3
        val sheet = Bitmap.createBitmap(w * cols, h * ((frames.size + cols - 1) / cols), Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(android.graphics.Color.BLACK)
        val paint = Paint().apply { color = android.graphics.Color.YELLOW; textSize = 30f; isAntiAlias = true }
        frames.forEachIndexed { i, (label, bmp) ->
            val x = (i % cols) * w
            val y = (i / cols) * h
            c.drawBitmap(bmp, android.graphics.Rect(0, 0, bmp.width, bmp.height), android.graphics.Rect(x, y, x + w, y + h), null)
            c.drawText(label, x + 10f, y + 34f, paint)
        }
        save(sheet, "$name-sheet")
    }

    @Test fun rerollPage() = reroll(false, "week-reroll-page")
    @Test fun rerollPopup() = reroll(true, "week-reroll-popup")
}
