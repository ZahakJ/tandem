package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Test

class A11yHealTest {
    private val me = "io.github.zahakj.tandem/io.github.zahakj.tandem.ControllerHomeService"

    @Test fun addsItselfAndKeepsTheOthers() {
        assertEquals("a.b/a.b.S:c.d/.T:$me", A11yHeal.withService("a.b/a.b.S:c.d/.T", me))
    }

    @Test fun emptyOrMissingListHoldsOnlyTandem() {
        assertEquals(me, A11yHeal.withService(null, me))
        assertEquals(me, A11yHeal.withService("", me))
    }

    @Test fun neverListsItselfTwiceInEitherForm() {
        val short = "io.github.zahakj.tandem/.ControllerHomeService"
        assertEquals("x.y/x.y.Z:$me", A11yHeal.withService("$short:x.y/x.y.Z", me))
        assertEquals("x.y/x.y.Z:$me", A11yHeal.withService("$me:x.y/x.y.Z:", me))
    }
}
