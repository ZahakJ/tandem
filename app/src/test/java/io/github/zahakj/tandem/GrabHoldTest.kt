package io.github.zahakj.tandem

import io.github.zahakj.tandem.ChordEngine.Out
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holding Options on the icon grid picks a tile up (Command.Grab, Bindings.withGrab): a quick press is still the
 * menu, a hold of [Bindings.GRAB_MS] is the pick-up, and nothing changes anywhere else.
 */
class GrabHoldTest {
    private val defaults = Bindings.defaults
    private fun fire(vararg c: Command) = Out.Fire(c.toList())
    private fun engine(armed: Boolean, map: Map<Command, Binding?> = defaults) =
        ChordEngine({ Bindings.withGrab(map, armed) }, holdMs = Bindings.HOLD_MS)

    @Test fun grabRidesOnOptionsOnlyWhileTheGridIsArmed() {
        assertNull(defaults[Command.Grab])
        assertSame(defaults, Bindings.withGrab(defaults, armed = false))
        assertEquals(Binding.hold(Pad.Y), Bindings.withGrab(defaults, armed = true)[Command.Grab])
    }

    @Test fun grabFollowsOptionsWhenTheButtonsAreSwapped() {
        val xy = Bindings.swap(defaults, Command.Options, Command.Action, true)
        assertEquals(Binding.hold(Pad.X), Bindings.withGrab(xy, armed = true)[Command.Grab])
    }

    @Test fun noGrabWhenOptionsIsAChordOrAHoldOrSomethingElseHoldsThatButton() {
        val chord = defaults + (Command.Options to Binding.of(Pad.L1, Pad.Y))
        assertNull(Bindings.withGrab(chord, armed = true)[Command.Grab])
        val held = defaults + (Command.Options to Binding.hold(Pad.Y))
        assertNull(Bindings.withGrab(held, armed = true)[Command.Grab])
        val taken = defaults + (Command.Favourite to Binding.hold(Pad.Y))
        assertNull(Bindings.withGrab(taken, armed = true)[Command.Grab])
    }

    @Test fun elsewhereOptionsStillFiresOnTheWayDown() {
        val e = engine(armed = false)
        assertEquals(listOf(fire(Command.Options)), e.down(Pad.Y, 0))
        assertEquals(emptyList<Out>(), e.up(Pad.Y, 900))
    }

    @Test fun onTheGridAQuickPressIsTheMenuOnRelease() {
        val e = engine(armed = true)
        assertEquals(emptyList<Out>(), e.down(Pad.Y, 0))
        // Still under the hold time: the menu, the moment the button comes up.
        assertEquals(listOf(fire(Command.Options)), e.up(Pad.Y, Bindings.GRAB_MS - 1))
        assertNull(e.deadline)
    }

    @Test fun onTheGridAHoldPicksUpAtFourHundredMilliseconds() {
        val e = engine(armed = true)
        e.down(Pad.Y, 1000)
        assertEquals(1000 + Bindings.GRAB_MS, e.deadline)
        assertTrue(Bindings.GRAB_MS in 300L..500L)
        assertEquals(emptyList<Out>(), e.tick(1000 + Bindings.GRAB_MS - 1))
        assertEquals(listOf(fire(Command.Grab)), e.tick(1000 + Bindings.GRAB_MS))
        // Letting go afterwards does nothing more: no menu on top of the pick-up.
        assertEquals(emptyList<Out>(), e.up(Pad.Y, 1600))
    }

    @Test fun theChargeIsVisibleWhileHeldAndGoneAfter() {
        val e = engine(armed = true)
        assertNull(e.charging())
        e.down(Pad.Y, 200)
        val (c, span) = e.charging()!!
        assertEquals(Command.Grab, c)
        assertEquals(200L..(200 + Bindings.GRAB_MS), span)
        e.up(Pad.Y, 300)
        assertNull(e.charging())
    }

    @Test fun otherHoldsKeepTheirOwnTime() {
        val e = engine(armed = true)
        e.down(Pad.Select, 0)
        assertEquals(Bindings.HOLD_MS, e.deadline)
    }

    @Test fun grabIsNotOnTheRebindPageAndHasItsOwnLogicalButton() {
        assertFalse(Command.Grab.rebindable)
        assertEquals(Button.Grab, Command.Grab.carrier)
        assertEquals(Command.Grab, Button.Grab.command)
        assertTrue(Command.entries.filter { it != Command.Grab }.all { it.rebindable })
    }

    @Test fun grabIsNeverStored() {
        assertTrue(Command.Grab.name !in Bindings.stored(defaults))
        assertEquals(0, Bindings.changed(defaults))
    }
}
