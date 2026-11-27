package io.github.zahakj.tandem.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.data.Store
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * This week's re-roll, frame by frame: three rows the way the board draws them (heading, covers with the highlight's
 * room, chips and names), the middle one dealt again ([Redeal]). Into `week-reroll-*.png` and a contact sheet. Only
 * with `-Ptandem.shots[=folder]`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-land-mdpi")
class WeekRerollShot {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val colors = listOf(Palette.Sky, Palette.Peach, Palette.Mint, Palette.Butter, Palette.Lavender)

    @Composable
    private fun Row(title: String, names: List<String>, color: Color, redeal: Redeal, id: Any, focus: FocusRequester?) {
        Column {
            SectionTitle(title, color, Modifier.padding(horizontal = 22.dp))
            val state = rememberLazyListState()
            PaddedScrolling {
                LazyRow(
                    Modifier.fadingEdges(state, horizontal = true), state = state,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = FocusRoom), horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(names, key = { _, n -> n }) { i, n ->
                        Column(Modifier.redealt(redeal, id, i).width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val c = colors[(n.hashCode() and 0xffff) % colors.size]
                            val requester = remember { FocusRequester() }
                            // The highlight on the row's first cover, as the board hands it on after a deal.
                            if (i == 0 && focus != null) LaunchedEffect(n) {
                                androidx.compose.runtime.withFrameNanos { }
                                runCatching { requester.requestFocus() }
                            }
                            FocusCard(Modifier.size(80.dp, 106.dp).focusRequester(requester), glow = c, onClick = {}) {
                                Box(Modifier.fillMaxSize().background(c.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                                    Text(n, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("2 h", style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
                            }
                            Text("Game $n", Modifier.fillMaxWidth().padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    @Test fun reroll() {
        Store.init(ApplicationProvider.getApplicationContext())
        Lang.choose("en")
        val redeal = Redeal()
        var deal by mutableIntStateOf(0)
        var middle by mutableStateOf(listOf("A1", "A2", "A3", "A4", "A5", "A6", "A7"))
        val focus = FocusRequester()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1.6f, 1f)) {
                TandemTheme {
                    Column(Modifier.fillMaxSize().padding(top = TopBarHeight)) {
                        Row("Pick up where you left off", listOf("R1", "R2", "R3", "R4", "R5", "R6", "R7"), Palette.Mint, redeal, "resume", null)
                        Row("Short tonight", middle, Palette.Sky, redeal, "short", focus)
                        Row("Hidden gems", listOf("G1", "G2", "G3", "G4", "G5", "G6", "G7"), Palette.Peach, redeal, "gem", null)
                    }
                    LaunchedEffect(deal) {
                        if (deal == 0) return@LaunchedEffect
                        redeal.run("short", still = false) {
                            middle = listOf("B1", "B2", "B3", "B4", "B5", "B6", "B7")
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        val dir = File(System.getProperty("tandem.shots") ?: "build/shots").apply { mkdirs() }
        val frames = ArrayList<Pair<Int, Bitmap>>()
        fun grab(ms: Int) {
            rule.waitForIdle()
            val view = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            File(dir, if (ms < 0) "week-reroll-before.png" else "week-reroll-%03d.png".format(ms)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            frames += ms to bmp
        }
        grab(-1)
        deal = 1
        var t = 0
        rule.mainClock.advanceTimeByFrame()
        for (at in listOf(0, 32, 64, 96, 112, 128, 160, 192, 224, 256, 288, 320, 368, 432, 560)) {
            if (at > t) { rule.mainClock.advanceTimeBy((at - t).toLong()); t = at }
            grab(at)
        }
        // A contact sheet: the start of each frame's board (where the covers move), half size, three across, with its time.
        val first = frames.first().second
        val cropW = (first.width * 0.5f).toInt()
        val cropH = (first.height * 0.86f).toInt()
        val w = cropW / 2
        val h = cropH / 2
        val cols = 3
        val sheet = Bitmap.createBitmap(w * cols, h * ((frames.size + cols - 1) / cols), Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(android.graphics.Color.BLACK)
        val paint = Paint().apply { color = android.graphics.Color.WHITE; textSize = 26f; isAntiAlias = true }
        frames.forEachIndexed { i, (ms, bmp) ->
            val x = (i % cols) * w
            val y = (i / cols) * h
            c.drawBitmap(bmp, android.graphics.Rect(0, 0, cropW, cropH), android.graphics.Rect(x, y, x + w, y + h), null)
            c.drawText(if (ms < 0) "before" else "$ms ms", x + 8f, y + 30f, paint)
        }
        File(dir, "week-reroll-sheet.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
