package io.github.zahakj.tandem.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gliding highlight (LiftMenu.kt): the drawing that reads [Glide.pill] must hear about the pill's first placing
 * and its stretch, or the first highlight of a list stays a small circle until the D-pad moves. "Hears" is what
 * Compose does for a draw: it redraws when a state object it read is among those a change applied.
 */
class GlideTest {
    /** What the last [draw] read. */
    private val reads = HashSet<Any>()

    /** Reads the pill as the drawing does, noting every state object it read. */
    private fun draw(g: Glide, rtl: Boolean = false): Rect? {
        reads.clear()
        var out: Rect? = null
        Snapshot.observe(readObserver = { reads.add(it) }) { out = g.pill(200f, 40f, rtl) }
        return out
    }

    /** Runs [change] and says whether it touched anything the last [draw] read (so the drawing would run again). */
    private fun redraws(change: suspend () -> Unit): Boolean {
        val changed = HashSet<Any>()
        val handle = Snapshot.registerApplyObserver { set, _ -> changed.addAll(set) }
        try {
            runBlocking { change() }
            Snapshot.sendApplyNotifications()
        } finally {
            handle.dispose()
        }
        return changed.any { it in reads }
    }

    /** A highlight as a list makes it: in a composition that has been applied, so its state is no longer brand new. */
    private fun fresh() = Glide().also { Snapshot.sendApplyNotifications() }

    @Test fun nothingShowsBeforeItIsPlaced() {
        assertNull(draw(fresh()))
    }

    @Test fun theFirstPlacingRedrawsAsACircle() {
        val g = fresh()
        assertNull(draw(g))
        // On the first row: the place doesn't change, only that there is a pill at all.
        assertTrue("placing the pill never redrew it", redraws { g.moveTo(0f, still = false) })
        val p = draw(g)!!
        assertEquals(0f, p.top, 0.01f)
        assertEquals(40f, p.width, 0.01f)
    }

    @Test fun theStretchRedrawsIntoTheWholeBar() {
        val g = fresh()
        runBlocking { g.moveTo(0f, still = false) }
        draw(g)
        assertTrue("the stretch never redrew the pill", redraws { g.open(still = true) })
        assertEquals(200f, draw(g)!!.width, 0.01f)
    }

    @Test fun stillShowsTheBarAtOnceAndMovesWithoutGliding() {
        val g = fresh()
        runBlocking { g.moveTo(10f, still = true) }
        assertEquals(200f, draw(g)!!.width, 0.01f)
        assertTrue(redraws { g.moveTo(90f, still = true) })
        assertEquals(90f, draw(g)!!.top, 0.01f)
    }

    @Test fun inRightToLeftTheCircleSitsAtTheStart() {
        val g = fresh()
        runBlocking { g.moveTo(0f, still = false) }
        val p = draw(g, rtl = true)!!
        assertEquals(160f, p.left, 0.01f)
        assertEquals(200f, p.right, 0.01f)
    }
}
