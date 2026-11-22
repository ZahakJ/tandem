package io.github.zahakj.tandem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CjkBreaksTest {
    private fun cut(text: String) = CjkBreaks.split(text)?.let { text.substring(0, it) + " / " + text.substring(it) }

    @Test fun japaneseBreaksAfterAParticle() {
        // Seen on the Thor: 「見た目と雰囲 / 気」 and 「ライブラリとアー / ト」.
        assertEquals("見た目と / 雰囲気", cut("見た目と雰囲気"))
        assertEquals("ライブラリと / アート", cut("ライブラリとアート"))
        assertEquals("テーマの / デフォルト", cut("テーマのデフォルト"))
    }

    @Test fun japaneseWithoutAParticleBreaksBetweenScripts() {
        assertEquals("アート / 表示", cut("アート表示"))
    }

    @Test fun chineseBreaksBetweenTwoCharacterWords() {
        assertEquals("固定 / 屏幕", cut("固定屏幕"))
        assertEquals("应用打开 / 屏幕", cut("应用打开屏幕"))
    }

    @Test fun otherTextIsLeftToTheUsualWrapping() {
        assertNull(CjkBreaks.split("Look & feel"))
        assertNull(CjkBreaks.split("الإحصاءات"))
        assertNull(CjkBreaks.split("短い"))
    }
}
