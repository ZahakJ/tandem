package io.github.zahakj.tandem

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import io.github.zahakj.tandem.ui.ThemeSpec

/**
 * The colour Tandem's windows show before their first frame: the middle of the last theme's backdrop gradient.
 *
 * - The starting window Android draws before Tandem's process runs is `@style/Tandem.Start` (no icon, no bars). On
 *   Android 13+ Tandem picks, for the next start, the `Tandem.Start.*` style closest to the theme in use
 *   ([remember], via `SplashScreen.setSplashScreenTheme`); Android keeps that choice across reboots.
 * - Its own windows (MainActivity, the bottom-screen home) paint the exact colour from a tiny preference first thing
 *   in onCreate ([paintSaved]), before any store or theme is loaded.
 */
object StartLook {
    private const val PREFS = "start_look"
    private const val KEY_COLOR = "backdrop"
    private const val KEY_STYLE = "style"
    /** Pastel Night, the default theme. */
    const val DEFAULT = 0xFF0F0E17.toInt()

    /** The starting-window styles and their colours (res/values/colors.xml), the default first. */
    private val STYLES = intArrayOf(
        R.style.Tandem_Start, R.style.Tandem_Start_Black, R.style.Tandem_Start_Navy, R.style.Tandem_Start_Violet,
        R.style.Tandem_Start_Slate, R.style.Tandem_Start_Green, R.style.Tandem_Start_Warm, R.style.Tandem_Start_Cream,
        R.style.Tandem_Start_Blush, R.style.Tandem_Start_Lime,
    )
    val COLORS = intArrayOf(
        DEFAULT, 0xFF000000.toInt(), 0xFF06101F.toInt(), 0xFF140B27.toInt(), 0xFF141626.toInt(), 0xFF0D1811.toInt(),
        0xFF150D0F.toInt(), 0xFFF0E6D1.toInt(), 0xFFFBEBF5.toInt(), 0xFF93B40F.toInt(),
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The colour saved by the last run (the default theme's on a first run). */
    fun saved(ctx: Context): Int = runCatching { prefs(ctx).getInt(KEY_COLOR, DEFAULT) }.getOrDefault(DEFAULT)

    /** Paints [window] in the saved colour; call first thing in onCreate. */
    fun paintSaved(window: Window, ctx: Context) = paint(window, saved(ctx))

    fun paint(window: Window, color: Int) = window.setBackgroundDrawable(ColorDrawable(color))

    /** The colour a theme's backdrop shows in the middle of its gradient. */
    fun colorOf(theme: ThemeSpec): Int = lerp(theme.background, theme.backgroundDeep, 0.5f).toArgb()

    /**
     * Remembers [theme]'s colour for the next start: the preference, and on Android 13+ the starting window's style.
     * Does nothing when neither changed (no disk write, no system call).
     */
    fun remember(activity: Activity, theme: ThemeSpec) {
        val color = colorOf(theme)
        val p = prefs(activity)
        if (p.getInt(KEY_COLOR, DEFAULT) != color) p.edit().putInt(KEY_COLOR, color).apply()
        if (Build.VERSION.SDK_INT < 33) return
        val index = nearest(color, COLORS)
        if (p.getInt(KEY_STYLE, 0) == index) return
        runCatching { activity.splashScreen.setSplashScreenTheme(STYLES[index]) }
            .onSuccess { p.edit().putInt(KEY_STYLE, index).apply() }
    }

    /** The index of the colour in [palette] closest to [color] (plain RGB distance, weighted for the eye). Plain, for tests. */
    fun nearest(color: Int, palette: IntArray): Int {
        var best = 0
        var bestD = Long.MAX_VALUE
        for (i in palette.indices) {
            val c = palette[i]
            val dr = ((color shr 16) and 0xFF) - ((c shr 16) and 0xFF)
            val dg = ((color shr 8) and 0xFF) - ((c shr 8) and 0xFF)
            val db = (color and 0xFF) - (c and 0xFF)
            val d = 3L * dr * dr + 4L * dg * dg + 2L * db * db
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }
}
