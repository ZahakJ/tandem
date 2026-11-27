package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.ui.IdleBudget.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The idle budget (IdleBudget.kt): how Tandem settles when nobody is touching it, and when it checks again. */
class IdleBudgetTest {
    @Test
    fun stagesFollowTimeSinceInput() {
        assertEquals(Stage.Active, IdleBudget.stage(0))
        assertEquals(Stage.Active, IdleBudget.stage(IdleBudget.CALM_AFTER_MS))
        assertEquals(Stage.Calm, IdleBudget.stage(IdleBudget.CALM_AFTER_MS + 1))
        assertEquals(Stage.Slow, IdleBudget.stage(IdleBudget.SLOW_AFTER_MS + 1))
        assertEquals(Stage.Settled, IdleBudget.stage(IdleBudget.SETTLE_AFTER_MS + 1))
        assertEquals(Stage.Resting, IdleBudget.stage(IdleBudget.REST_AFTER_MS + 1))
        assertEquals(Stage.Resting, IdleBudget.stage(24 * 3_600_000L))
    }

    @Test
    fun settlesWithinHalfAMinute() {
        // The owner's budget: after a short idle, no ambient frames at all.
        assertTrue(IdleBudget.SETTLE_AFTER_MS <= 30_000L)
        assertNull(IdleBudget.tickMs(IdleBudget.stage(IdleBudget.SETTLE_AFTER_MS + 1)))
        assertNull(IdleBudget.tickMs(Stage.Resting))
    }

    @Test
    fun ticksGetRarerAsItCalms() {
        assertEquals(0L, IdleBudget.tickMs(Stage.Active))
        val calm = IdleBudget.tickMs(Stage.Calm)!!
        val slow = IdleBudget.tickMs(Stage.Slow)!!
        assertTrue(calm in 1..40)
        assertTrue(slow > calm)
    }

    @Test
    fun aScreensaverSceneNeverSettles() {
        assertEquals(Stage.Slow, IdleBudget.stage(IdleBudget.SETTLE_AFTER_MS + 1, screensaver = true))
        assertEquals(Stage.Slow, IdleBudget.stage(10 * IdleBudget.REST_AFTER_MS, screensaver = true))
        assertEquals(Stage.Active, IdleBudget.stage(0, screensaver = true))
        assertEquals(IdleBudget.SCREENSAVER_RECHECK_MS, IdleBudget.nextChangeMs(10 * IdleBudget.REST_AFTER_MS, screensaver = true))
    }

    @Test
    fun timerLandsJustPastTheNextStage() {
        // Fresh input: the timer is due when settling begins.
        val first = IdleBudget.nextChangeMs(0)!!
        assertEquals(Stage.Settled, IdleBudget.stage(first))
        assertEquals(Stage.Slow, IdleBudget.stage(first - 1))
        // Part way: only what is left.
        assertEquals(IdleBudget.SETTLE_AFTER_MS - 5_000L + 1, IdleBudget.nextChangeMs(5_000L))
        // Settled: next is resting.
        val toRest = IdleBudget.nextChangeMs(IdleBudget.SETTLE_AFTER_MS + 1)!!
        assertEquals(Stage.Resting, IdleBudget.stage(IdleBudget.SETTLE_AFTER_MS + 1 + toRest))
        // Resting: nothing more to wait for.
        assertNull(IdleBudget.nextChangeMs(IdleBudget.REST_AFTER_MS + 1))
    }

    @Test
    fun walkingTheTimerVisitsEveryLateStage() {
        // Following the timer from fresh input, never skipping past a stage it is meant to catch.
        var t = 0L
        val seen = ArrayList<Stage>()
        while (true) {
            val next = IdleBudget.nextChangeMs(t) ?: break
            t += next
            seen += IdleBudget.stage(t)
        }
        assertEquals(listOf(Stage.Settled, Stage.Resting), seen)
    }
}
