package io.github.zahakj.tandem.ui

/**
 * How much decorative motion Tandem allows itself, by how long nobody has pressed, pushed or touched anything
 * (docs/PERFORMANCE.md, "Idle"). Plain arithmetic, no Android: [Ambient] runs it.
 *
 *  - [Stage.Active]: every vsync, the look as designed.
 *  - [Stage.Calm] after [CALM_AFTER_MS]: about 30 ticks a second; lively-only motion (Pip's bob) stands still.
 *  - [Stage.Slow] after [SLOW_AFTER_MS]: about 12 ticks a second.
 *  - [Stage.Settled] after [SETTLE_AFTER_MS]: no ambient motion at all. Everything holds the frame it is on (no jump),
 *    videos and animated art pause, Pip stops blinking and glancing. Only clocks (once a minute) still draw.
 *  - [Stage.Resting] after [REST_AFTER_MS]: the Plaza's crowd, which may keep milling about a while longer, stops too.
 *
 * A screensaver scene runs for hours on purpose: while one shows, idle time never gets past [Stage.Slow].
 */
object IdleBudget {
    const val CALM_AFTER_MS = 10_000L
    const val SLOW_AFTER_MS = 20_000L
    const val SETTLE_AFTER_MS = 30_000L
    const val REST_AFTER_MS = 3 * 60_000L

    /** While a screensaver shows, how often to look again (it ends with input, which wakes everything anyway). */
    const val SCREENSAVER_RECHECK_MS = 60_000L

    enum class Stage { Active, Calm, Slow, Settled, Resting }

    fun stage(sinceInputMs: Long, screensaver: Boolean = false): Stage {
        val s = when {
            sinceInputMs > REST_AFTER_MS -> Stage.Resting
            sinceInputMs > SETTLE_AFTER_MS -> Stage.Settled
            sinceInputMs > SLOW_AFTER_MS -> Stage.Slow
            sinceInputMs > CALM_AFTER_MS -> Stage.Calm
            else -> Stage.Active
        }
        return if (screensaver && s > Stage.Slow) Stage.Slow else s
    }

    /** Milliseconds between ambient ticks: 0 for every vsync, null for no ticks at all. */
    fun tickMs(stage: Stage): Long? = when (stage) {
        Stage.Active -> 0L
        Stage.Calm -> 28L
        Stage.Slow -> 80L
        Stage.Settled, Stage.Resting -> null
    }

    /**
     * How long until [Stage.Settled] or [Stage.Resting] next begins (the stages a timer must catch: the faster ones
     * are read on every tick), or null when nothing more is coming.
     */
    fun nextChangeMs(sinceInputMs: Long, screensaver: Boolean = false): Long? {
        if (screensaver) return SCREENSAVER_RECHECK_MS
        // +1: the stage begins once the time is past its threshold, not on it.
        for (t in longArrayOf(SETTLE_AFTER_MS, REST_AFTER_MS)) if (sinceInputMs <= t) return t - sinceInputMs + 1
        return null
    }
}
