package io.github.zahakj.tandem

import io.github.zahakj.tandem.ChordEngine.Out
import io.github.zahakj.tandem.ui.Prompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which button does what (Bindings.kt): storing, conflicts and swaps, presses into commands, capture and labels. */
class BindingsTest {
    private val defaults = Bindings.defaults

    @Test fun bindingsSurviveTheirWayThroughControllersJson() {
        for (b in listOf(Binding.of(Pad.A), Binding.of(Pad.Select, Pad.R1, Pad.L1), Binding.hold(Pad.Start), Binding.of(Pad.Home))) {
            assertEquals(b, Binding.parse(b.serialize()))
        }
        assertEquals("L1+R1+Select", Binding.of(Pad.Select, Pad.R1, Pad.L1).serialize())
        assertEquals("hold:Start", Binding.hold(Pad.Start).serialize())
        assertNull(Binding.parse("Q"))
        assertNull(Binding.parse("hold:L1+R1"))
        assertNull(Binding.parse(""))
    }

    @Test fun onlyChangesAreStoredAndBadEntriesFallBack() {
        assertEquals(emptyMap<String, String>(), Bindings.stored(defaults))
        val changed = defaults + (Command.Favourite to Binding.of(Pad.L3)) + (Command.Surprise to null)
        val stored = Bindings.stored(changed)
        assertEquals(mapOf("Favourite" to "L3", "Surprise" to ""), stored)
        assertEquals(changed, Bindings.resolve(stored))
        // Something unreadable, or a command that no longer exists, leaves the defaults.
        assertEquals(defaults, Bindings.resolve(mapOf("Options" to "Nonsense", "Gone" to "A")))
        assertEquals(2, Bindings.changed(changed))
    }

    @Test fun sidePanelAndPageDownShareR2WithoutClashing() {
        assertEquals(listOf(Command.PageDown, Command.SidePanel), Bindings.commands(defaults, Binding.of(Pad.R2)))
        assertEquals(emptyList<Command>(), Bindings.conflicts(defaults, Command.SidePanel, Binding.of(Pad.R2)))
        // Anything else wanting R2 takes it from both.
        assertEquals(listOf(Command.PageDown, Command.SidePanel), Bindings.conflicts(defaults, Command.Favourite, Binding.of(Pad.R2)))
    }

    @Test fun aFreeButtonIsSimplyTaken() {
        val plan = Bindings.plan(defaults, Command.Favourite, Binding.of(Pad.L3))
        assertEquals(Binding.of(Pad.L3), (plan as Bindings.Plan.Done).map[Command.Favourite])
    }

    @Test fun aTakenButtonIsOfferedAsASwap() {
        val plan = Bindings.plan(defaults, Command.Options, Binding.of(Pad.X)) as Bindings.Plan.Swap
        assertEquals(listOf(Command.Action), plan.others)
        assertEquals(Binding.of(Pad.X), plan.map[Command.Options])
        assertEquals(Binding.of(Pad.Y), plan.map[Command.Action])
    }

    @Test fun theSameBindingChangesNothing() {
        assertEquals(Bindings.Plan.Same, Bindings.plan(defaults, Command.Open, Binding.of(Pad.A)))
    }

    @Test fun openAndBackNeedASingleButtonThatNothingWaitsOn() {
        assertTrue(Bindings.plan(defaults, Command.Open, Binding.of(Pad.A, Pad.B)) is Bindings.Plan.Refused)
        assertTrue(Bindings.plan(defaults, Command.Back, Binding.hold(Pad.B)) is Bindings.Plan.Refused)
        // L1 is part of the hidden-games chord, whose shoulders wait for the third button.
        assertTrue(Bindings.plan(defaults, Command.Open, Binding.of(Pad.L1)) is Bindings.Plan.Refused)
        // And A can't join a chord while it opens things.
        assertTrue(Bindings.plan(defaults, Command.Favourite, Binding.of(Pad.A, Pad.R3)) is Bindings.Plan.Refused)
    }

    @Test fun openAndBackAreNeverLeftWithoutAButton() {
        // Favourite has no button to give Open in exchange.
        assertTrue(Bindings.plan(defaults, Command.Favourite, Binding.of(Pad.A)) is Bindings.Plan.Refused)
        assertTrue(Bindings.plan(defaults, Command.Back, null) is Bindings.Plan.Refused)
        // A command that has one can swap with Open.
        val swap = Bindings.plan(defaults, Command.Options, Binding.of(Pad.A)) as Bindings.Plan.Swap
        assertEquals(Binding.of(Pad.Y), swap.map[Command.Open])
    }

    @Test fun aSwapTheOtherCommandCantUseIsRefused() {
        // Open would get the hidden-games chord.
        assertTrue(Bindings.plan(defaults, Command.Hidden, Binding.of(Pad.A)) is Bindings.Plan.Refused)
    }

    @Test fun chordsInsideOtherChordsAreRefused() {
        assertTrue(Bindings.plan(defaults, Command.Favourite, Binding.of(Pad.L1, Pad.R1)) is Bindings.Plan.Refused)
        assertTrue(Bindings.plan(defaults, Command.Favourite, Binding.of(Pad.L1, Pad.R1, Pad.Select, Pad.Start)) is Bindings.Plan.Refused)
        assertTrue(Bindings.plan(defaults, Command.Favourite, Binding.of(Pad.L1, Pad.R3)) is Bindings.Plan.Done)
    }

    @Test fun takingAButtonAwayLeavesNothingBound() {
        val m = (Bindings.plan(defaults, Command.Surprise, null) as Bindings.Plan.Done).map
        assertNull(m[Command.Surprise])
    }

    @Test fun resetPutsACommandBackSwappingIfNeeded() {
        val moved = (Bindings.plan(defaults, Command.Options, Binding.of(Pad.X)) as Bindings.Plan.Swap).map
        val back = Bindings.resetPlan(moved, Command.Options) as Bindings.Plan.Swap
        assertEquals(defaults, back.map)
    }

    @Test fun theOldSwapSettingsAreBindings() {
        val ab = Bindings.swap(defaults, Command.Open, Command.Back, true)
        assertEquals(Binding.of(Pad.B), ab[Command.Open])
        assertEquals(Binding.of(Pad.A), ab[Command.Back])
        assertTrue(Bindings.swapped(ab, Command.Open, Command.Back))
        assertEquals(defaults, Bindings.swap(ab, Command.Open, Command.Back, false))
        val xy = Bindings.swap(defaults, Command.Options, Command.Action, true)
        assertEquals(Binding.of(Pad.X), xy[Command.Options])
        assertEquals(Binding.of(Pad.Y), xy[Command.Action])
    }

    @Test fun everyButtonCommandArrivesAsItsOwnLogicalButton() {
        for (c in Command.entries.filter { it.kind == Command.Kind.Button }) {
            val carrier = c.carrier
            assertNotNull(carrier)
            assertEquals(c, carrier!!.command)
        }
    }

    // The engine: presses, holds and chords into commands.

    private fun engine(map: Map<Command, Binding?> = defaults) = ChordEngine({ map }, holdMs = 500)
    private fun fire(vararg c: Command) = Out.Fire(c.toList())

    @Test fun aPlainButtonFiresOnTheWayDown() {
        val e = engine()
        assertEquals(listOf(fire(Command.Options)), e.down(Pad.Y, 0))
        assertEquals(emptyList<Out>(), e.up(Pad.Y, 50))
    }

    @Test fun openAndBackPassTheirKeysThrough() {
        val e = engine()
        assertEquals(listOf(Out.Pass(Command.Open, Pad.A, true)), e.down(Pad.A, 0))
        assertEquals(listOf(Out.Pass(Command.Open, Pad.A, false)), e.up(Pad.A, 900))
        val swapped = engine(Bindings.swap(defaults, Command.Open, Command.Back, true))
        assertEquals(listOf(Out.Pass(Command.Back, Pad.A, true)), swapped.down(Pad.A, 0))
    }

    @Test fun selectTapsOnReleaseAndHoldsForTheSwitcher() {
        val e = engine()
        assertEquals(emptyList<Out>(), e.down(Pad.Select, 0))
        assertEquals(listOf(fire(Command.Surprise)), e.up(Pad.Select, 100))
        e.down(Pad.Select, 1000)
        assertEquals(1500L, e.deadline)
        assertEquals(listOf(fire(Command.Switcher)), e.tick(1500))
        assertEquals(emptyList<Out>(), e.up(Pad.Select, 1700))
    }

    @Test fun selectAndStartTogetherOpenTheSwitcherAndNeitherTaps() {
        val e = engine()
        e.down(Pad.Select, 0)
        assertEquals(listOf(fire(Command.SwitcherChord)), e.down(Pad.Start, 30))
        assertEquals(emptyList<Out>(), e.tick(600))
        assertEquals(emptyList<Out>(), e.up(Pad.Start, 700))
        assertEquals(emptyList<Out>(), e.up(Pad.Select, 710))
    }

    @Test fun theHiddenChordFiresOnceAndTheShouldersDontPage() {
        val e = engine()
        assertEquals(emptyList<Out>(), e.down(Pad.L1, 0))
        assertEquals(emptyList<Out>(), e.down(Pad.R1, 10))
        assertEquals(listOf(fire(Command.Hidden)), e.down(Pad.Select, 20))
        for (p in listOf(Pad.Select, Pad.R1, Pad.L1)) assertEquals(emptyList<Out>(), e.up(p, 100))
        // A shoulder alone still pages, when it is let go.
        e.down(Pad.R1, 200)
        assertEquals(listOf(fire(Command.Next)), e.up(Pad.R1, 260))
    }

    @Test fun sharedButtonsFireTogether() {
        assertEquals(listOf(fire(Command.PageDown, Command.SidePanel)), engine().down(Pad.R2, 0))
    }

    @Test fun anUnboundButtonGoesOnAsItIs() {
        val e = engine()
        assertEquals(listOf(Out.Unbound(Pad.L3, true)), e.down(Pad.L3, 0))
        assertEquals(listOf(Out.Unbound(Pad.L3, false)), e.up(Pad.L3, 20))
    }

    @Test fun aReboundCommandMovesWithItsButton() {
        val map = (Bindings.plan(defaults, Command.Favourite, Binding.of(Pad.L3, Pad.R3)) as Bindings.Plan.Done).map
        val e = engine(map)
        e.down(Pad.L3, 0)
        assertEquals(listOf(fire(Command.Favourite)), e.down(Pad.R3, 10))
        assertEquals(emptyList<Out>(), e.up(Pad.L3, 50))
        assertEquals(emptyList<Out>(), e.up(Pad.R3, 60))
        // L3 alone, now part of a chord, waits for its release, and is unbound on its own.
        assertEquals(emptyList<Out>(), e.down(Pad.L3, 100))
        assertEquals(emptyList<Out>(), e.up(Pad.L3, 150))
    }

    @Test fun theControllerHomeButtonPassesThrough() {
        assertEquals(listOf(Out.Pass(Command.ControllerHome, Pad.Home, true)), engine().down(Pad.Home, 0))
    }

    // Capture on the rebind page.

    @Test fun captureMakesButtonsChordsAndHolds() {
        val c = ChordCapture(holdMs = 500)
        c.down(Pad.X, 0)
        assertEquals(Binding.of(Pad.X), c.up(Pad.X, 100))
        c.down(Pad.L1, 200)
        c.down(Pad.R2, 230)
        assertEquals(listOf(Pad.L1, Pad.R2), c.sofar)
        assertNull(c.up(Pad.R2, 300))
        assertEquals(Binding.of(Pad.L1, Pad.R2), c.up(Pad.L1, 320))
        c.down(Pad.Start, 1000)
        assertEquals(Binding.hold(Pad.Start), c.up(Pad.Start, 1600))
    }

    // Labels.

    @Test fun bindingsReadInThePromptStyle() {
        val hidden = Binding.of(Pad.L1, Pad.R1, Pad.Select)
        assertEquals("L1+R1+Select", Prompts.bindingText(hidden, "xbox"))
        assertEquals("L1+R1+−", Prompts.bindingText(hidden, "nintendo"))
        assertEquals("Hold +", Prompts.bindingText(Binding.hold(Pad.Start), "nintendo"))
        assertEquals("Hold Select", Binding.hold(Pad.Select).label)
    }

    @Test fun hintNamesStandForCommands() {
        assertEquals(Command.Open, Prompts.commandOf("A"))
        assertEquals(Command.Back, Prompts.commandOf("B"))
        assertEquals(Command.Options, Prompts.commandOf("Y"))
        assertEquals(Command.PageDown, Prompts.commandOf("R2"))
        assertEquals(Command.Favourite, Prompts.commandOf("Favourite"))
        assertNull(Prompts.commandOf("L"))
    }
}
