package io.github.zahakj.tandem.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * The Settings home drawn at the Thor's top screen (1920×1080 px at 369 dpi, 832×468 dp) in English, French and
 * Arabic, into PNGs. Only with `-Ptandem.shots[=folder]`. The top bar is private to App.kt, so its 52 dp stay empty.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-land-mdpi")
class SettingsHomeShot {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(tag: String, name: String, screen: @androidx.compose.runtime.Composable () -> Unit = { SettingsScreen() }) {
        Store.init(ApplicationProvider.getApplicationContext())
        Lang.choose(tag)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(369f / 160f, 1f)) {
                TandemTheme { Box(Modifier.fillMaxSize().padding(top = TopBarHeight)) { screen() } }
            }
        }
        rule.mainClock.advanceTimeBy(4000)
        rule.waitForIdle()
        // Drawn straight from the window's views: Compose's own capture waits for a frame Robolectric never draws.
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        val dir = File(System.getProperty("tandem.shots") ?: "build/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun english() = shoot("en", "settings-home-en")
    @Test fun french() = shoot("fr", "settings-home-fr")
    @Test fun arabic() = shoot("ar", "settings-home-ar")
    @Test fun japanese() = shoot("ja", "settings-home-ja")
    @Test fun chinese() = shoot("zh-CN", "settings-home-zh")

    /** What's new in 0.10 draws the Settings home small with the same tiles. */
    @Test fun whatsNew() = shoot("en", "whatsnew-settings-en") {
        Box(Modifier.padding(80.dp)) { io.github.zahakj.tandem.ui.whatsnew.SettingsVisual() }
    }
}
