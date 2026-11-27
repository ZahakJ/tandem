package io.github.zahakj.tandem.ui.whatsnew

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import io.github.zahakj.tandem.Lang
import io.github.zahakj.tandem.data.Store
import io.github.zahakj.tandem.ui.Palette
import io.github.zahakj.tandem.ui.TandemTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The 0.11 deck's pictures, each at the design size (360 × 232 dp) at twice its pixels, on the deck's card colour,
 * at two moments of its loop, in English and Arabic. Only with `-Ptandem.shots[=folder]`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-land-mdpi")
class WhatsNew011Shot {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(id: String, tag: String = "en") {
        Store.init(ApplicationProvider.getApplicationContext())
        Lang.choose(tag)
        val slide = Decks.of("0.11.0")!!.slides.first { it.id == id }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                // In a box of the window's size: the root is held to the whole window, its child can be smaller.
                TandemTheme { Box(Modifier.fillMaxSize()) { Box(Modifier.size(DesignW, DesignH).background(Palette.Card)) { Arrivals { slide.visual() } } } }
            }
        }
        val dir = File(System.getProperty("tandem.shots") ?: "build/shots").apply { mkdirs() }
        for ((n, ms) in listOf(1 to 2600L, 2 to 2400L)) {
            rule.mainClock.advanceTimeBy(ms)
            rule.waitForIdle()
            val view = rule.activity.window.decorView
            val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(full))
            val w = (DesignW.value * 2).toInt()
            val h = (DesignH.value * 2).toInt()
            // The picture sits at the window's top start corner.
            val x = if (tag == "ar") full.width - w else 0
            val bmp = Bitmap.createBitmap(full, x.coerceAtLeast(0), 0, w, h)
            File(dir, "wn011-$id-$tag-$n.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun pass() = shoot("pass")
    @Test fun plaza() = shoot("plaza")
    @Test fun me() = shoot("me")
    @Test fun grooves() = shoot("grooves")
    @Test fun glyphs() = shoot("glyphs")
    @Test fun switch() = shoot("switch")
    @Test fun steady() = shoot("steady")
    @Test fun passArabic() = shoot("pass", "ar")
    @Test fun meArabic() = shoot("me", "ar")
    @Test fun glyphsArabic() = shoot("glyphs", "ar")
    @Test fun switchArabic() = shoot("switch", "ar")
    @Test fun plazaArabic() = shoot("plaza", "ar")
    @Test fun steadyArabic() = shoot("steady", "ar")
}

