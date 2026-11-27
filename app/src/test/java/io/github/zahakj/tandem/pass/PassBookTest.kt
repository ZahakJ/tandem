package io.github.zahakj.tandem.pass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The Plaza's book (PassBook.kt): de-duplication by key, counting repeats, waves, hiding and blocking. */
class PassBookTest {
    private val t0 = 1_795_000_000_000L
    private val hour = 60L * 60 * 1000
    private val ann = Random(1).nextBytes(32)
    private val ben = Random(2).nextBytes(32)

    private fun card(key: ByteArray, name: String = "Ann", at: Long = t0, fav: String? = null) = PassCard(key, at, AvatarSpec(), name, 3, 10, fav)

    private fun PlazaData.meet(key: ByteArray, at: Long, name: String = "Ann") = PassBook.record(this, card(key, name, at), at)

    @Test fun samePersonIsOneVisitorWhateverTheirName() {
        var d = PlazaData()
        val (d1, o1) = d.meet(ann, t0)
        assertEquals(PassOutcome.New, o1)
        val (d2, o2) = d1.meet(ann, t0 + 10 * 60 * 1000, name = "Annie")
        assertEquals(PassOutcome.Refreshed, o2)
        d = d2
        assertEquals(1, d.visitors.size)
        assertEquals("Annie", d.visitors.single().name)
        assertEquals(1, d.visitors.single().times)
        assertEquals(1, d.passes)
    }

    @Test fun repeatsCountOncePerWindow() {
        var d = PlazaData()
        d = d.meet(ann, t0).first
        d = d.meet(ann, t0 + 2 * hour).first
        assertEquals(1, d.visitors.single().times)
        val (d2, o) = d.meet(ann, t0 + PassBook.REPASS_MS)
        assertEquals(PassOutcome.Again, o)
        assertEquals(2, d2.visitors.single().times)
        assertEquals(2, d2.passes)
        // The window runs from the last pass that counted, not the last contact.
        val d3 = d2.meet(ann, t0 + PassBook.REPASS_MS + 5 * hour).first
        assertEquals(2, d3.visitors.single().times)
        assertEquals(3, d3.meet(ann, t0 + 2 * PassBook.REPASS_MS).first.visitors.single().times)
    }

    @Test fun newestFirstAndNewFilter() {
        var d = PlazaData()
        d = d.meet(ann, t0).first
        d = d.meet(ben, t0 + hour, "Ben").first
        assertEquals(listOf("Ben", "Ann"), PassBook.shown(d, false).map { it.name })
        assertEquals(2, PassBook.newCount(d))
        d = PassBook.seen(d, PassIdentity.keyId(ben))
        assertEquals(listOf("Ann"), PassBook.shown(d, true).map { it.name })
        d = PassBook.seen(d)
        assertEquals(0, PassBook.newCount(d))
        // A pass that counts makes them new again.
        d = d.meet(ann, t0 + PassBook.REPASS_MS).first
        assertEquals(1, PassBook.newCount(d))
    }

    @Test fun hiddenUntilTheNextPassThatCounts() {
        val key = PassIdentity.keyId(ann)
        var d = PlazaData().meet(ann, t0).first
        d = PassBook.hide(d, key)
        assertTrue(PassBook.shown(d, false).isEmpty())
        d = d.meet(ann, t0 + hour).first
        assertTrue(PassBook.shown(d, false).isEmpty())
        d = d.meet(ann, t0 + PassBook.REPASS_MS).first
        assertEquals(1, PassBook.shown(d, false).size)
    }

    @Test fun blockedKeysAreForgottenAndRefused() {
        val key = PassIdentity.keyId(ann)
        var d = PlazaData().meet(ann, t0).first
        val was = d.visitors.single()
        d = PassBook.block(d, key)
        assertTrue(d.visitors.isEmpty())
        assertTrue(PassBook.isBlocked(d, key))
        val (d2, o) = d.meet(ann, t0 + PassBook.REPASS_MS)
        assertEquals(PassOutcome.Blocked, o)
        assertTrue(d2.visitors.isEmpty())
        assertEquals(1, d2.passes)
        // A new name doesn't get round it: the key is who they are.
        assertEquals(PassOutcome.Blocked, d.meet(ann, t0 + 3 * PassBook.REPASS_MS, "Someone").second)
        // Undo puts them back as they were.
        val back = PassBook.unblock(d, key, was)
        assertFalse(PassBook.isBlocked(back, key))
        assertEquals(was, back.visitors.single())
    }

    @Test fun wavesTravelOnTheNextPass() {
        val key = PassIdentity.keyId(ann)
        var d = PlazaData().meet(ann, t0).first
        assertFalse(PassBook.waveFor(d, key))
        d = PassBook.setWave(d, key, true)
        assertTrue(PassBook.waveFor(d, key))
        d = PassBook.waves(d, key, sentOurs = true, theirWave = true)
        val v = d.visitors.single()
        assertTrue(v.waveSent)
        assertTrue(v.wavedAtMe)
        // Hidden people aren't waved at.
        assertFalse(PassBook.waveFor(PassBook.hide(d, key), key))
    }

    @Test fun clearKeepsBlocksAndTheCounter() {
        var d = PlazaData().meet(ann, t0).first.meet(ben, t0, "Ben").first
        d = PassBook.block(d, PassIdentity.keyId(ben))
        d = PassBook.clear(d)
        assertTrue(d.visitors.isEmpty())
        assertEquals(1, d.blocked.size)
        assertEquals(2, d.passes)
    }

    @Test fun theBookStaysBounded() {
        var d = PlazaData()
        val rnd = Random(5)
        repeat(PassBook.MAX_VISITORS + 20) { i -> d = PassBook.record(d, card(rnd.nextBytes(32), "P$i", t0 + i), t0 + i).first }
        assertEquals(PassBook.MAX_VISITORS, d.visitors.size)
        // The latest are kept.
        assertTrue(d.visitors.any { it.name == "P${PassBook.MAX_VISITORS + 19}" })
    }
}
