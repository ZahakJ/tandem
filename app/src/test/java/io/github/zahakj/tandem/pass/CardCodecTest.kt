package io.github.zahakj.tandem.pass

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Characters and cards: encoding, byte budgets, signing, and what a card that shouldn't count gets. */
class CardCodecTest {
    private val now = 1_795_000_000_000L
    private val alice = PassIdentity(Random(11).nextBytes(32))
    private val bob = PassIdentity(Random(12).nextBytes(32))
    private val nonce = Random(13).nextBytes(CardCodec.NONCE_BYTES)

    private fun card(id: PassIdentity = alice, name: String = "Alice", time: Long = now, fav: String? = "Kirby's Dream Land") = PassCard(
        id.publicKey, time, AvatarSpec(shape = 3, skin = 7, hair = 12, hairColor = 9, headwear = 6, glasses = 2, theme = 8), name, 5, 42, fav, "Tetris",
    )

    // --- The character ---------------------------------------------------------------------------------------

    @Test fun avatarRoundTripsAndFitsItsBudget() {
        val rnd = Random(3)
        repeat(200) {
            val s = AvatarSpec.random(rnd)
            val b = AvatarCodec.encode(s)
            assertEquals(s, AvatarCodec.decode(b))
            assertEquals(18, b.size)
            assertTrue(b.size <= AvatarCodec.MAX_BYTES)
        }
    }

    @Test fun cardsFromBeforeTheWardrobeStillRead() {
        // A 0.10 card: version, then the fifteen parts it knew (no face details, no beard).
        val old = byteArrayOf(1, 3, 7, 2, 4, 1, 0, 1, 12, 9, 6, 4, 8, 2, 1, 5)
        val s = AvatarCodec.decode(old)!!
        assertEquals(AvatarSpec(shape = 3, skin = 7, eyes = 2, eyeColor = 4, brows = 1, mouth = 0, cheeks = 1, hair = 12, hairColor = 9,
            headwear = 6, top = 4, topColor = 8, glasses = 2, earrings = 1, theme = 5), s)
        assertEquals(0, s.faceExtra)
        assertEquals(0, s.beard)
        // Choices from a newer Tandem, past what this one has, read as the first choice, the new parts too.
        val newer = AvatarCodec.encode(AvatarSpec(faceExtra = 3, beard = 2)).also {
            it[1 + AvatarPart.FaceExtra.ordinal] = 99
            it[1 + AvatarPart.Beard.ordinal] = (-1).toByte()
            it[1 + AvatarPart.Headwear.ordinal] = 200.toByte()
        }
        val n = AvatarCodec.decode(newer)!!
        assertEquals(0, n.faceExtra)
        assertEquals(0, n.beard)
        assertEquals(0, n.headwear)
        // And the wardrobe's own choices make the trip.
        val worn = AvatarSpec(hair = 33, headwear = 36, top = 37, eyes = 19, eyeColor = 15, glasses = 14, earrings = 9, faceExtra = 7, beard = 4)
        assertEquals(worn, AvatarCodec.decode(AvatarCodec.encode(worn)))
    }

    @Test fun randomCrowdsAreMostlyEverydayWear() {
        val rnd = Random(5)
        val crowd = List(2000) { AvatarSpec.random(rnd) }
        val costumes = crowd.count { it.top in AvatarSpec.COSTUME_TOPS }
        val hatted = crowd.count { it.headwear != 0 }
        val dressedUp = crowd.count { it.headwear in AvatarSpec.COSTUME_HATS }
        assertTrue("$costumes", costumes in 60..260)
        assertTrue("$hatted", hatted in 600..1000)
        assertTrue("$dressedUp", dressedUp < hatted / 4)
        // Every part still turns up somewhere.
        for (p in AvatarPart.entries) assertTrue(p.name, crowd.map { it[p] }.toSet().size > 1)
    }

    @Test fun avatarReadsOlderAndNewerEncodings() {
        val s = AvatarSpec(shape = 4, skin = 9, eyes = 3)
        val full = AvatarCodec.encode(s)
        // An older Tandem's shorter encoding: the parts it lacked are left at their defaults.
        val short = full.copyOf(4)
        assertEquals(AvatarSpec(shape = 4, skin = 9, eyes = 3), AvatarCodec.decode(short))
        // A newer one: a later version byte and parts this Tandem doesn't know are ignored.
        val longer = full + byteArrayOf(7, 7, 7)
        longer[0] = 2
        assertEquals(s, AvatarCodec.decode(longer))
        // A choice this Tandem doesn't have reads as the first one.
        val odd = full.copyOf().also { it[1 + AvatarPart.Hair.ordinal] = 200.toByte() }
        assertEquals(0, AvatarCodec.decode(odd)!!.hair)
        assertNull(AvatarCodec.decode(ByteArray(0)))
        assertNull(AvatarCodec.decode(ByteArray(3)))
    }

    @Test fun avatarStepsWrapAround() {
        val s = AvatarSpec()
        assertEquals(AvatarPart.Hair.count - 1, s.with(AvatarPart.Hair, -1).hair)
        assertEquals(0, s.with(AvatarPart.Hair, AvatarPart.Hair.count).hair)
    }

    // --- The card --------------------------------------------------------------------------------------------

    @Test fun signedCardOpens() {
        val wire = CardCodec.seal(card(), alice, nonce)
        val c = CardCodec.open(wire, nonce, now + 5_000)
        assertTrue(c is CardCheck.Good)
        val got = (c as CardCheck.Good).card
        assertArrayEquals(alice.publicKey, got.publicKey)
        assertEquals("Alice", got.name)
        assertEquals(5, got.greeting)
        assertEquals(42, got.passes)
        assertEquals("Kirby's Dream Land", got.favorite)
        assertEquals("Tetris", got.recent)
        assertEquals(card().avatar, got.avatar)
    }

    @Test fun cardFitsOneAttributeAtItsLongest() {
        val long = PassCard(
            alice.publicKey, now, AvatarSpec(), "ＷＷＷＷＷＷＷＷＷＷＷＷＷＷ", 1, Int.MAX_VALUE,
            "😀".repeat(40), "漢".repeat(60),
        )
        val wire = CardCodec.seal(long, alice, nonce)
        assertTrue("${wire.size}", wire.size <= CardCodec.MAX_WIRE)
        assertTrue(PassProtocol.HELLO_HEAD + wire.size <= PassProtocol.MAX_VALUE)
        val got = (CardCodec.open(wire, nonce, now) as CardCheck.Good).card
        assertEquals(12, got.name.codePointCount(0, got.name.length))
        assertTrue(got.favorite!!.toByteArray().size <= CardCodec.MAX_TITLE_BYTES)
    }

    @Test fun replayToAnotherNonceIsRefused() {
        val wire = CardCodec.seal(card(), alice, nonce)
        val other = Random(99).nextBytes(CardCodec.NONCE_BYTES)
        assertEquals(CardProblem.Signature, (CardCodec.open(wire, other, now) as CardCheck.Bad).problem)
    }

    @Test fun forgedCardsAreRefused() {
        // Bob's key on Alice's signature: a card claiming to be someone else.
        val body = CardCodec.body(card())
        val sig = alice.sign(CardCodec.message(body, nonce))
        val forged = CardCodec.body(card(bob)) + sig
        assertEquals(CardProblem.Signature, (CardCodec.open(forged, nonce, now) as CardCheck.Bad).problem)
        // A changed name under a good signature.
        val wire = CardCodec.seal(card(), alice, nonce)
        val nameAt = 1 + 32 + 8 + 1 + AvatarCodec.SIZE + 1
        val bent = wire.copyOf().also { it[nameAt] = 'E'.code.toByte() }
        assertEquals(CardProblem.Signature, (CardCodec.open(bent, nonce, now) as CardCheck.Bad).problem)
        // Garbage, truncation and extra bytes never crash.
        assertTrue(CardCodec.open(ByteArray(10), nonce, now) is CardCheck.Bad)
        assertTrue(CardCodec.open(wire.copyOf(wire.size - 70), nonce, now) is CardCheck.Bad)
        assertTrue(CardCodec.open(ByteArray(600), nonce, now) is CardCheck.Bad)
    }

    @Test fun staleOrFutureCardsAreRefused() {
        val old = CardCodec.seal(card(time = now - CardCodec.MAX_SKEW_MS - 1), alice, nonce)
        assertEquals(CardProblem.Clock, (CardCodec.open(old, nonce, now) as CardCheck.Bad).problem)
        val ahead = CardCodec.seal(card(time = now + CardCodec.MAX_SKEW_MS + 1), alice, nonce)
        assertEquals(CardProblem.Clock, (CardCodec.open(ahead, nonce, now) as CardCheck.Bad).problem)
    }

    @Test fun ownCardIsRefused() {
        val wire = CardCodec.seal(card(), alice, nonce)
        assertEquals(CardProblem.Self, (CardCodec.open(wire, nonce, now, alice.publicKey) as CardCheck.Bad).problem)
    }

    @Test fun extensionsAreSignedAndKept() {
        val c = PassCard(alice.publicKey, now, AvatarSpec(), "Al", 1, 0, extensions = listOf(CardExtension(9, byteArrayOf(1, 2, 3))))
        val wire = CardCodec.seal(c, alice, nonce)
        val got = (CardCodec.open(wire, nonce, now) as CardCheck.Good).card
        assertEquals(9, got.extensions.single().type)
        assertArrayEquals(byteArrayOf(1, 2, 3), got.extensions.single().data)
    }

    @Test fun namesAreCleaned() {
        assertEquals("Ali ce", CardCodec.cleanName("  Ali ‮\u0000 ce\n "))
        assertEquals("ABCDEFGHIJKL", CardCodec.cleanName("ABCDEFGHIJKLMNOP"))
        assertEquals("", CardCodec.cleanName("​‏"))
        assertNotNull(CardCodec.parse(CardCodec.body(card(name = "‮evil"))))
        assertEquals("evil", CardCodec.parse(CardCodec.body(card(name = "‮evil")))!!.name)
    }
}
