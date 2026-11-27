package io.github.zahakj.tandem.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
 * Every glyph on a contact sheet, at a Settings row's badge size on the Thor (30 dp at 369 dpi) and four times that,
 * and a few Settings pages as they draw. Only with `-Ptandem.shots[=folder]`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-land-mdpi")
class SettingsGlyphShot {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val dir get() = File(System.getProperty("tandem.shots") ?: "build/shots").apply { mkdirs() }
    private val tints = listOf(Palette.Mint, Palette.Lavender, Palette.Peach, Palette.Butter, Palette.Sky)

    /** [glyphs] in a grid of [cols], each a badge [chip] px across with its name under it. */
    private fun sheet(name: String, glyphs: List<Glyph>, chip: Int, cols: Int) {
        val cellW = maxOf(chip + 40, 230); val cellH = chip + 56
        val rows = (glyphs.size + cols - 1) / cols
        val bmp = Bitmap.createBitmap(cols * cellW + 40, rows * cellH + 40, Bitmap.Config.ARGB_8888)
        val canvas = androidx.compose.ui.graphics.Canvas(bmp.asImageBitmap())
        val native = canvas.nativeCanvas
        native.drawColor(Palette.Card.toArgb())
        val text = android.graphics.Paint().apply { color = android.graphics.Color.LTGRAY; textSize = 22f; isAntiAlias = true }
        glyphs.forEachIndexed { i, g ->
            val x = 20f + (i % cols) * cellW + (cellW - chip) / 2f; val y = 20f + (i / cols) * cellH
            val tint = tints[i % tints.size]
            native.save(); native.translate(x, y)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(chip.toFloat(), chip.toFloat())) {
                drawRoundRect(tint.copy(alpha = 0.18f), cornerRadius = CornerRadius(chip * 0.32f))
            }
            val pad = chip * 0.2f
            native.translate(pad, pad)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(chip - 2 * pad, chip - 2 * pad)) { drawGlyph(g, tint) }
            native.restore()
            val w = text.measureText(g.name)
            native.drawText(g.name, 20f + (i % cols) * cellW + (cellW - w) / 2f, y + chip + 32f, text)
        }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 30 dp at 369 dpi. */
    private val badge = Math.round(30 * 369f / 160f)

    @Test fun contactSheet() {
        val all = Glyph.entries
        sheet("glyphs-badge", all, badge, 9)
        all.chunked(40).forEachIndexed { i, part -> sheet("glyphs-4x-${i + 1}", part, badge * 4, 8) }
    }

    private fun page(id: String) {
        Store.init(ApplicationProvider.getApplicationContext())
        Lang.choose("en")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(369f / 160f, 1f)) {
                TandemTheme { Box(Modifier.fillMaxSize().padding(top = TopBarHeight)) { SettingsPage(id) } }
            }
        }
        rule.mainClock.advanceTimeBy(4000)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        File(dir, "page-${id.replace('/', '-')}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun play() = page("play")
    @Test fun pass() = page("play/pass")
    @Test fun controls() = page("controls")
    @Test fun screens() = page("screens")
    @Test fun sound() = page("sound")

    @Test fun inventory() {
        Store.init(ApplicationProvider.getApplicationContext())
        Lang.choose("en")
        var out = ""
        rule.setContent {
            out = settingsCatalog().joinToString("\n") { c ->
                "## ${c.id} [${c.icon}]\n" + c.sections.joinToString("\n") { s -> "  # ${s.en}\n" + s.rows.joinToString("\n") { "    ${it.en} -> ${it.icon}" } }
            }
        }
        rule.waitForIdle()
        File(dir, "inventory-after.txt").writeText(out)
    }
}
