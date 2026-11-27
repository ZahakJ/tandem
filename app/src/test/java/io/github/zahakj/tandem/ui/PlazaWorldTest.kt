package io.github.zahakj.tandem.ui

import io.github.zahakj.tandem.pass.AvatarSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** The Plaza's little world (PlazaWorld.kt): nobody walks through the fountain or anyone else, and nobody hides. */
class PlazaWorldTest {
    private fun crowd(n: Int, seed: Long = 5L): PlazaWorld {
        val r = Random(seed)
        val w = PlazaWorld(seed)
        w.sync(AvatarSpec(), List(n) { "v$it" }, List(n) { AvatarSpec.random(r) }, List(n) { it < 3 })
        return w
    }

    private fun ground(a: PlazaWorld.Walker, x: Float, z: Float): Float {
        val dx = a.x - x
        val dz = (a.z - z) * PlazaWorld.DEPTH
        return sqrt(dx * dx + dz * dz)
    }

    @Test fun aBusySquareNeverOverlaps() {
        for (seed in 1L..4L) {
            val w = crowd(14, seed)
            val o = PlazaWorld.OBSTACLES
            repeat(60 * 90) { frame ->
                w.step(1f / 60f)
                val people = w.walkers
                for (a in people) {
                    assertTrue("$seed/$frame ${a.key} lost", a.x.isFinite() && a.z.isFinite())
                    if (a.isMe) continue
                    val f = ground(a, o[0], o[1])
                    assertTrue("$seed/$frame ${a.key} in the fountain ($f)", f > o[2])
                    for (b in people) {
                        if (b === a || b.isMe || a.act == PlazaWorld.Act.Enter || b.act == PlazaWorld.Act.Enter) continue
                        assertTrue("$seed/$frame ${a.key} inside ${b.key}", ground(a, b.x, b.z) > PlazaWorld.BODY * 1.6f)
                    }
                }
            }
            // And everyone's still on the square.
            for (a in w.walkers) assertTrue(abs(a.x) <= 1f && a.z in 0f..1.05f)
        }
    }

    @Test fun theHighlightedOneStopsInSight() {
        val w = crowd(10)
        repeat(60 * 20) { w.step(1f / 60f) }
        for (v in w.walkers.filter { !it.isMe }) {
            w.focus(v.key)
            repeat(60 * 4) { w.step(1f / 60f) }
            assertTrue("${v.key} still walking", v.walk < 0.2f)
            assertTrue("${v.key} waves", v.wave > 0.8f)
            val hidden = abs(v.x - PlazaWorld.OWNER_X) < PlazaWorld.ME_COLUMN * 0.8f && v.z < PlazaWorld.OWNER_Z
            assertFalse("${v.key} behind the owner at ${v.x}, ${v.z}", hidden)
        }
        w.focus(null)
        repeat(60 * 3) { w.step(1f / 60f) }
        assertTrue(w.walkers.none { it.focused })
    }

    @Test fun newcomersWalkInFromTheSide() {
        val r = Random(3)
        val w = crowd(4)
        repeat(60) { w.step(1f / 60f) }
        w.sync(AvatarSpec(), List(5) { "v$it" }, List(5) { AvatarSpec.random(r) }, List(5) { false })
        val n = w.walkers.first { it.key == "v4" }
        assertTrue(abs(n.x) > 1f)
        repeat(60 * 15) { w.step(1f / 60f) }
        assertTrue("still off the square at ${n.x}", abs(n.x) < PlazaWorld.X_MAX + 0.05f)
    }

    @Test fun stillMeansStill() {
        val w = crowd(14)
        w.focus("v2")
        w.arrange()
        for (a in w.walkers) {
            assertTrue(a.walk == 0f && a.hop == 0f)
            if (!a.isMe) assertFalse("${a.key} hidden", abs(a.x - PlazaWorld.OWNER_X) < PlazaWorld.ME_COLUMN && a.z < PlazaWorld.OWNER_Z)
        }
    }
}
