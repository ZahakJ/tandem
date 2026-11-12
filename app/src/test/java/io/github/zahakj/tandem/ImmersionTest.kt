package io.github.zahakj.tandem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Immersion mode (Immersion.kt): the Do Not Disturb plan on a fake filter and policy. */
class ImmersionTest {
    private val theirs = QuietPolicy(categories = 0xff, callSenders = 1, messageSenders = 1, suppressedEffects = 0, conversationSenders = 1)

    /** A filter and policy that do what they are told, unless [broken]; [log] lists every change. */
    private class Fake(var filter: Int?, var policy: QuietPolicy?, var broken: Boolean = false) : Quiet {
        val log = ArrayList<String>()
        override fun filter() = filter
        override fun setFilter(filter: Int): Boolean {
            if (broken) return false
            log += "filter $filter"; this.filter = filter; return true
        }
        override fun policy() = policy
        override fun setPolicy(p: QuietPolicy): Boolean {
            if (broken) return false
            log += "policy ${if (p == ImmersionPlan.quiet) "quiet" else "theirs"}"; policy = p; return true
        }
    }

    @Test fun goesQuietAndComesBack() {
        val q = Fake(ImmersionPlan.ALL, theirs)
        val had = ImmersionPlan.begin(null, q)
        assertEquals(QuietChanges(ImmersionPlan.ALL, theirs), had)
        assertEquals(ImmersionPlan.PRIORITY, q.filter)
        assertEquals(ImmersionPlan.quiet, q.policy)
        assertNull(ImmersionPlan.end(had!!, q))
        assertEquals(ImmersionPlan.ALL, q.filter)
        assertEquals(theirs, q.policy)
        assertEquals(listOf("policy quiet", "filter ${ImmersionPlan.PRIORITY}", "filter ${ImmersionPlan.ALL}", "policy theirs"), q.log)
    }

    @Test fun leavesTheUsersOwnDoNotDisturbAlone() {
        val q = Fake(ImmersionPlan.PRIORITY, theirs)
        assertNull(ImmersionPlan.begin(null, q))
        assertEquals(emptyList<String>(), q.log)
        assertNull(ImmersionPlan.begin(null, Fake(null, theirs)))
    }

    @Test fun aSecondGameChangesNothingMore() {
        val had = QuietChanges(ImmersionPlan.ALL, theirs)
        val q = Fake(ImmersionPlan.PRIORITY, ImmersionPlan.quiet)
        assertEquals(had, ImmersionPlan.begin(had, q))
        assertEquals(emptyList<String>(), q.log)
    }

    @Test fun aFilterTheUserChangedMidGameStays() {
        val had = QuietChanges(ImmersionPlan.ALL, theirs)
        // Turned Do Not Disturb off during the game: only the policy goes back.
        val q = Fake(ImmersionPlan.ALL, ImmersionPlan.quiet)
        assertNull(ImmersionPlan.end(had, q))
        assertEquals(listOf("policy theirs"), q.log)
    }

    @Test fun aFailedChangeIsKeptToTryAgain() {
        val had = QuietChanges(ImmersionPlan.ALL, theirs)
        val q = Fake(ImmersionPlan.PRIORITY, ImmersionPlan.quiet, broken = true)
        assertEquals(had, ImmersionPlan.end(had, q))
        // A filter that won't go quiet leaves the policy as it was, with nothing to put back.
        val half = object : Quiet by Fake(ImmersionPlan.ALL, theirs) {
            var p: QuietPolicy? = theirs
            override fun policy() = p
            override fun setPolicy(p: QuietPolicy) = run { this.p = p; true }
            override fun setFilter(filter: Int) = false
        }
        assertNull(ImmersionPlan.begin(null, half))
        assertEquals(theirs, half.p)
    }
}
