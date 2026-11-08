package io.github.zahakj.tandem.data

import kotlinx.serialization.Serializable
import java.io.File

/**
 * One game's logo size and place over its art, set in Edit details. [scale] is a percentage ([MIN]..[MAX]),
 * [anchor] one of [ANCHORS], and [dx] / [dy] nudge steps of [NUDGE_SHARE] of the space the logo sits in.
 */
@Serializable
data class LogoFit(val scale: Int = 100, val anchor: String = AUTO, val dx: Int = 0, val dy: Int = 0) {
    val isDefault: Boolean get() = scale == 100 && anchor == AUTO && dx == 0 && dy == 0

    /** Clamped to the allowed ranges (a hand-edited or old file can hold anything). */
    fun tidy(): LogoFit = LogoFit(
        scale.coerceIn(MIN, MAX), anchor.takeIf { a -> ANCHORS.any { it.first == a } } ?: AUTO,
        dx.coerceIn(-NUDGE_MAX, NUDGE_MAX), dy.coerceIn(-NUDGE_MAX, NUDGE_MAX),
    )

    companion object {
        const val MIN = 50
        const val MAX = 150
        const val STEP = 10
        const val NUDGE_MAX = 10
        const val NUDGE_SHARE = 0.025f
        const val AUTO = "auto"

        val ANCHORS = listOf(
            AUTO to "Auto", "left" to "Left", "center" to "Centre", "right" to "Right", "top" to "Top", "bottom" to "Bottom",
        )

        fun label(anchor: String): String = ANCHORS.firstOrNull { it.first == anchor }?.second ?: "Auto"

        /** The anchor after [anchor], for a row that cycles. */
        fun nextAnchor(anchor: String): String = ANCHORS[(ANCHORS.indexOfFirst { it.first == anchor } + 1) % ANCHORS.size].first
    }
}

/**
 * Where a logo goes with a [LogoFit]: a scale about its centre and a translation, worked out from where its layout
 * put it ([x], [y], [w], [h]) inside its parent ([parentW] x [parentH]). Plain maths, so it is tested on its own
 * (GameExtrasTest) and applied in a graphics layer (no layout changes).
 */
object LogoPlace {
    data class Result(val scale: Float, val dx: Float, val dy: Float) {
        companion object { val NONE = Result(1f, 0f, 0f) }
    }

    fun place(fit: LogoFit?, parentW: Float, parentH: Float, x: Float, y: Float, w: Float, h: Float, margin: Float): Result {
        if (fit == null || fit.isDefault) return Result.NONE
        val f = fit.tidy()
        val s = f.scale / 100f
        val sw = w * s
        val sh = h * s
        val cx = x + w / 2f
        val cy = y + h / 2f
        var tx = when (f.anchor) {
            "left" -> margin + sw / 2f
            "center" -> parentW / 2f
            "right" -> parentW - margin - sw / 2f
            else -> cx
        }
        var ty = when (f.anchor) {
            "top" -> margin + sh / 2f
            "bottom" -> parentH - margin - sh / 2f
            else -> cy
        }
        tx += f.dx * LogoFit.NUDGE_SHARE * parentW
        ty += f.dy * LogoFit.NUDGE_SHARE * parentH
        // Kept inside the parent (less a margin); the logo's own place always counts as inside, so a logo its layout
        // put near an edge doesn't jump when only its size changes.
        tx = tx.coerceIn(minOf(margin + sw / 2f, cx), maxOf(parentW - margin - sw / 2f, cx))
        ty = ty.coerceIn(minOf(margin + sh / 2f, cy), maxOf(parentH - margin - sh / 2f, cy))
        return Result(s, tx - cx, ty - cy)
    }
}

