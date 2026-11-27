package io.github.zahakj.tandem.pass

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** A whole exchange between two Tandems, in memory: the bytes PassRadio would carry over GATT. */
class PassProtocolTest {
    private val now = 1_795_000_000_000L
    private val rnd = Random(21)
    private val alice = PassIdentity(rnd.nextBytes(32))
    private val bob = PassIdentity(rnd.nextBytes(32))

    private fun cardOf(id: PassIdentity, name: String) = PassCard(id.publicKey, now, AvatarSpec(), name, 2, 3)

    private fun server(blocked: Set<String> = emptySet(), waves: Boolean = false) = ServerSession(
        rnd.nextBytes(16), bob.publicKey, { n -> CardCodec.seal(cardOf(bob, "Bob"), bob, n) }, { waves }, { it in blocked },
    )

    private val aliceToken = rnd.nextBytes(Beacon.TOKEN_BYTES)
    private fun client() = ClientSession(rnd.nextBytes(16), alice.publicKey, aliceToken, { n -> CardCodec.seal(cardOf(alice, "Alice"), alice, n) })

    @Test fun twoTandemsMeet() {
        val s = server(waves = true)
        val c = client()
        val hello = c.hello(s.nonceMessage)!!
        assertTrue(hello.size <= PassProtocol.MAX_VALUE)
        val got = s.onHello(hello, now) as PassResult.Met
        assertEquals("Alice", got.card.name)
        assertArrayEquals(aliceToken, s.peerToken)
        val reply = s.cardMessage()!!
        val met = c.onCard(reply, now) as PassResult.Met
        assertEquals("Bob", met.card.name)
        assertTrue(met.waved)
        assertTrue(s.onWave(c.wave(true)))
        assertTrue(s.theirWave)
    }

    @Test fun nothingIsGivenBeforeAGoodHello() {
        val s = server()
        assertNull(s.cardMessage())
        assertFalse(s.onWave(byteArrayOf(1)))
        // A hello whose card was signed for another connection's nonce.
        val other = server()
        val c = client()
        val hello = c.hello(other.nonceMessage)!!
        val r = s.onHello(hello, now)
        assertTrue(r is PassResult.Refused)
        assertEquals(CardProblem.Signature, (r as PassResult.Refused).problem)
        assertNull(s.cardMessage())
    }

    @Test fun aBlockedKeyGetsNoCardBack() {
        val s = server(blocked = setOf(alice.keyIdOf()))
        val c = client()
        assertTrue(s.onHello(c.hello(s.nonceMessage)!!, now) is PassResult.Met)
        assertNull(s.cardMessage())
    }

    @Test fun aFinderSendsNothingToAKeyItBlocked() {
        val s = server()
        val c = ClientSession(rnd.nextBytes(16), alice.publicKey, aliceToken, { n -> CardCodec.seal(cardOf(alice, "Alice"), alice, n) }) {
            it == PassIdentity.keyId(bob.publicKey)
        }
        assertNull(c.hello(s.nonceMessage))
        assertArrayEquals(bob.publicKey, PassProtocol.readKey(s.nonceMessage))
    }

    @Test fun theCardMustComeFromTheKeyAnnounced() {
        // Bob's nonce read announces Bob, but the card that comes back is Carol's.
        val carol = PassIdentity(rnd.nextBytes(32))
        val s = server()
        val c = client()
        c.hello(s.nonceMessage)!!
        val theirs = PassProtocol.readNonce(s.nonceMessage)!!
        val forged = PassProtocol.card(CardCodec.seal(cardOf(carol, "Carol"), carol, theirs), false)
        assertTrue(c.onCard(forged, now) is PassResult.Refused)
    }

    @Test fun aReplayedServerCardIsRefused() {
        val s = server()
        val c1 = client()
        s.onHello(c1.hello(s.nonceMessage)!!, now)
        val recorded = s.cardMessage()!!
        // The recording, played to a second finder with its own nonce.
        val c2 = client()
        c2.hello(server().nonceMessage)
        assertTrue(c2.onCard(recorded, now) is PassResult.Refused)
        assertNotNull(c1.onCard(recorded, now) as? PassResult.Met)
    }

    @Test fun otherVersionsAreLeftAlone() {
        val c = client()
        assertNull(c.hello(byteArrayOf(2) + ByteArray(16)))
        assertNull(c.hello(ByteArray(3)))
        val s = server()
        assertEquals(CardProblem.Version, (s.onHello(byteArrayOf(9, 1, 2), now) as PassResult.Refused).problem)
        assertTrue(s.onHello(byteArrayOf(1, 1, 2), now) is PassResult.Refused)
    }

    @Test fun beaconData() {
        val t = Random(4).nextBytes(Beacon.TOKEN_BYTES)
        assertArrayEquals(t, Beacon.token(Beacon.data(t)))
        assertNull(Beacon.token(byteArrayOf(2) + t))
        assertNull(Beacon.token(null))
        // Scan response: length, type, 16-byte UUID and the data fit the legacy 31 bytes.
        assertTrue(2 + 16 + Beacon.data(t).size <= 31)
    }

    private fun PassIdentity.keyIdOf() = PassIdentity.keyId(publicKey)
}
