package io.github.zahakj.tandem.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG contrast for every built-in theme, worked out from the colour tokens in Theme.kt:
 * - body text ([ThemeSpec.text]) and quieter detail text ([ThemeSpec.muted]) on every surface: AA, 4.5:1;
 * - accent colours used as text (pills, values, headings: bold, short) on cards: 3:1, AA for large or bold text;
 * - [ThemeSpec.onAccent] on the main accent (a selected chip, a bright tile): 4.5:1.
 */
class ThemeContrastTest {
    private fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun lum(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    private fun contrast(a: Color, b: Color): Double {
        val x = lum(a); val y = lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private fun failures(t: ThemeSpec): List<String> {
        val out = ArrayList<String>()
        val surfaces = listOf("background" to t.background, "backgroundDeep" to t.backgroundDeep, "card" to t.card, "cardHigh" to t.cardHigh)
        for ((sn, s) in surfaces) {
            contrast(t.text, s).let { if (it < 4.5) out += "text on $sn %.2f".format(it) }
            contrast(t.muted, s).let { if (it < 4.5) out += "muted on $sn %.2f".format(it) }
        }
        val accents = listOf("mint" to t.mint, "peach" to t.peach, "lavender" to t.lavender, "sky" to t.sky, "butter" to t.butter, "accent" to t.accent)
        for ((an, a) in accents) for ((sn, s) in surfaces.filter { it.first.startsWith("card") }) {
            contrast(a, s).let { if (it < 3.0) out += "$an on $sn %.2f".format(it) }
        }
        contrast(t.onAccent, t.accent).let { if (it < 4.5) out += "onAccent on accent %.2f".format(it) }
        return out
    }

    @Test fun everyBuiltInThemeIsReadable() {
        assertTrue("expected 24 built-in themes, found ${BuiltInThemes.list.size}", BuiltInThemes.list.size == 24)
        val bad = BuiltInThemes.list.mapNotNull { t -> failures(t).takeIf { it.isNotEmpty() }?.let { "${t.id}: ${it.joinToString()}" } }
        assertTrue("Contrast below WCAG AA:\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
