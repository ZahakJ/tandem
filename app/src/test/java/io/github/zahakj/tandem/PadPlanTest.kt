package io.github.zahakj.tandem

import io.github.zahakj.tandem.PadPlan.Event
import io.github.zahakj.tandem.PadPlan.Pad
import io.github.zahakj.tandem.PadPlan.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which controller plays as the built-in one (PadPlan.kt). */
class PadPlanTest {
    private val xbox = Pad("x", "Xbox Wireless Controller")
    private val pro = Pad("p", "Pro Controller")

    private fun run(vararg events: Event, from: State = State()): PadPlan.Step =
        events.fold(PadPlan.Step(from)) { step, e -> PadPlan.reduce(step.state, e) }

    private fun devices(vararg pads: Pad, inGame: Boolean = true, auto: Boolean = false) = Event.Devices(pads.toList(), inGame, auto)

    @Test fun aPadConnectingMidGameIsOfferedAndTakenOnA() {
        val offered = run(devices(), devices(xbox))
        assertEquals("x", offered.state.offer)
        assertNull(offered.state.linked)
        val taken = run(Event.Accept, from = offered.state)
        assertEquals("x", taken.state.linked)
        assertNull(taken.state.offer)
        assertEquals("Playing with Xbox Wireless Controller", taken.notice)
    }

    @Test fun aPadConnectedBeforeTheGameIsOfferedWhenTheGameStarts() {
        // Connected at the library, then a game started.
        val offered = run(devices(xbox, inGame = false), devices(xbox))
        assertEquals("x", offered.state.offer)
        assertEquals("x", run(Event.Accept, from = offered.state).state.linked)
    }

    @Test fun noOfferOutsideAGame() {
        assertNull(run(devices(), devices(xbox, inGame = false)).state.offer)
        // An offer still up when the game ends goes.
        assertNull(run(devices(), devices(xbox), devices(xbox, inGame = false)).state.offer)
    }

    @Test fun aTurnedDownPadIsNotOfferedAgainUntilItReconnects() {
        val declined = run(devices(), devices(xbox), Event.Decline)
        assertNull(declined.state.offer)
        assertNull(run(devices(xbox), from = declined.state).state.offer)
        // Away and back: offered again.
        assertEquals("x", run(devices(), devices(xbox), from = declined.state).state.offer)
    }

    @Test fun disconnectingGoesBackToTheBuiltInControls() {
        val playing = run(devices(), devices(xbox), Event.Accept).state
        val gone = run(devices(), from = playing)
        assertNull(gone.state.linked)
        assertEquals(PadPlan.BUILT_IN, gone.notice)
        // The helper noticing first says the same; the device list catching up then changes nothing more.
        val early = run(Event.Gone("x"), from = playing)
        assertNull(early.state.linked)
        assertEquals(PadPlan.BUILT_IN, early.notice)
        assertNull(run(devices(), from = early.state).notice)
    }

    @Test fun autoTakesAConnectedPadAtOnceAndTheNextOneWhenItGoes() {
        val auto = run(devices(auto = true, inGame = false), devices(xbox, auto = true, inGame = false))
        assertEquals("x", auto.state.linked)
        assertNull(auto.state.offer)
        assertEquals("Playing with Xbox Wireless Controller", auto.notice)
        val both = run(devices(xbox, pro, auto = true), from = auto.state)
        assertEquals("x", both.state.linked)
        val next = run(devices(pro, auto = true), from = both.state)
        assertEquals("p", next.state.linked)
    }

    @Test fun choosingBuiltInKeepsAutoFromTakingThePadBack() {
        val auto = run(devices(xbox, auto = true)).state
        assertEquals("x", auto.linked)
        val builtIn = run(Event.Choose(null), devices(xbox, auto = true), from = auto)
        assertNull(builtIn.state.linked)
        // Reconnected: it is new again, so auto takes it.
        assertEquals("x", run(devices(auto = true), devices(xbox, auto = true), from = builtIn.state).state.linked)
    }

    @Test fun aFailedPadIsLeftAloneUntilItReconnects() {
        val auto = run(devices(xbox, auto = true)).state
        val failed = run(Event.Failed("x"), from = auto)
        assertNull(failed.state.linked)
        assertEquals("Couldn't take over Xbox Wireless Controller", failed.notice)
        assertNull(run(devices(xbox, auto = true), from = failed.state).state.linked)
        // Picking it by hand tries again.
        assertEquals("x", run(Event.Choose("x"), from = failed.state).state.linked)
    }

    @Test fun theTileCyclesBuiltInThenEachPad() {
        var s = run(devices(xbox, pro, inGame = false)).state
        val seen = ArrayList<String?>()
        repeat(4) {
            s = PadPlan.reduce(s, Event.Choose(PadPlan.next(s))).state
            seen += s.linked
        }
        assertEquals(listOf("x", "p", null, "x"), seen)
    }

    @Test fun choosingAPadThatIsGoneMeansBuiltIn() {
        val s = run(devices(xbox), Event.Accept).state
        assertNull(run(Event.Choose("gone"), from = s).state.linked)
    }
}
