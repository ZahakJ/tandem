package io.github.zahakj.tandem.pass

import java.util.UUID

/**
 * Tandem Pass over Bluetooth LE: what is on the air, and the four steps of an exchange, as pure Kotlin. PassRadio
 * (the Android side) only moves these bytes; everything here is tested in PassProtocolTest.
 *
 * **Advertising.** A connectable advertisement holding only [SERVICE] (128-bit, 21 bytes with the flags; no name, no
 * transmit power), and a scan response with [SERVICE]'s service data: [VERSION] and an 8-byte random [Beacon]
 * token, made fresh at every rotation (PassPlanner) and never derived from the identity, so nothing on the air ties
 * one rotation to the next. Android gives the advertisement a random private address of its own; Tandem restarts
 * advertising at every rotation, which gets a new one on most handhelds too.
 *
 * **Exchange** (GATT, the finder connects; who finds whom is [PassPlanner.initiates]):
 * 1. read [NONCE]: the other side's `version ‖ 16-byte nonce ‖ 32-byte public key`, the nonce fresh for this
 *    connection; a finder that has that key blocked stops here, and sends nothing of its own;
 * 2. write [HELLO]: `version ‖ my 16-byte nonce ‖ my 8-byte beacon token ‖ my card` (the card signed over their
 *    nonce; the token tells them not to connect back to the beacon they would find next);
 * 3. read [CARD]: `their card ‖ wave byte` (signed over my nonce), sent only once my card checked out;
 * 4. write [WAVE]: my wave byte for them. Then the finder disconnects.
 *
 * A wave byte is 1 when its sender waved back at the reader on the Plaza (shown to them as "waved at you"). Waves ride
 * the connection whose cards both checked out, so they are never shown for a key that didn't prove itself.
 */
object PassProtocol {
    const val VERSION = 1
    val SERVICE: UUID = UUID.fromString("5d9a3b70-2c41-4e8f-a6d2-7b1e0f94c3a8")
    val NONCE: UUID = UUID.fromString("5d9a3b71-2c41-4e8f-a6d2-7b1e0f94c3a8")
    val HELLO: UUID = UUID.fromString("5d9a3b72-2c41-4e8f-a6d2-7b1e0f94c3a8")
    val CARD: UUID = UUID.fromString("5d9a3b73-2c41-4e8f-a6d2-7b1e0f94c3a8")
    val WAVE: UUID = UUID.fromString("5d9a3b74-2c41-4e8f-a6d2-7b1e0f94c3a8")

    /** The largest attribute value BLE allows; every message here fits one. */
    const val MAX_VALUE = 512

    fun nonceMessage(nonce: ByteArray, key: ByteArray): ByteArray = byteArrayOf(VERSION.toByte()) + nonce + key

    private const val NONCE_MSG = 1 + CardCodec.NONCE_BYTES + Ed25519.PUBLIC_BYTES

    /** The nonce in a [NONCE] read, or null for another version or a wrong size. */
    fun readNonce(msg: ByteArray): ByteArray? =
        msg.takeIf { it.size == NONCE_MSG && it[0].toInt() == VERSION }?.copyOfRange(1, 1 + CardCodec.NONCE_BYTES)

    /** The public key in a [NONCE] read (unproven: the card that follows proves it), or null. */
    fun readKey(msg: ByteArray): ByteArray? =
        msg.takeIf { it.size == NONCE_MSG && it[0].toInt() == VERSION }?.copyOfRange(1 + CardCodec.NONCE_BYTES, msg.size)

    fun hello(myNonce: ByteArray, myToken: ByteArray, card: ByteArray): ByteArray = byteArrayOf(VERSION.toByte()) + myNonce + myToken + card

    /** Where the card starts in a [HELLO]. */
    const val HELLO_HEAD = 1 + CardCodec.NONCE_BYTES + Beacon.TOKEN_BYTES

    fun card(card: ByteArray, wave: Boolean): ByteArray = card + byteArrayOf(if (wave) 1 else 0)
}

/** The 8-byte token in the scan response. */
object Beacon {
    const val TOKEN_BYTES = 8

    fun data(token: ByteArray): ByteArray = byteArrayOf(PassProtocol.VERSION.toByte()) + token

    /** The token in a scan response's service data, or null when it is another version or shape. */
    fun token(serviceData: ByteArray?): ByteArray? =
        serviceData?.takeIf { it.size == 1 + TOKEN_BYTES && it[0].toInt() == PassProtocol.VERSION }?.copyOfRange(1, serviceData.size)

    fun hex(token: ByteArray): String = PassIdentity.keyId(token)
}

/** How an exchange ended, for either side. */
sealed interface PassResult {
    /** Both cards checked out: [card] is theirs, [waved] whether they waved at us. */
    class Met(val card: PassCard, val waved: Boolean) : PassResult
    class Refused(val problem: CardProblem) : PassResult
}

/**
 * The side that was found (the GATT server) for one connection. It answers [NONCE] with its own [nonce], checks the
 * [HELLO], and only then gives its card ([cardFor] signs it over the finder's nonce) and takes a wave.
 */
class ServerSession(
    private val nonce: ByteArray,
    private val self: ByteArray,
    private val cardFor: (theirNonce: ByteArray) -> ByteArray,
    private val waveFor: (PassCard) -> Boolean,
    private val blocked: (String) -> Boolean,
) {
    val nonceMessage: ByteArray = PassProtocol.nonceMessage(nonce, self)
    var peer: PassCard? = null
        private set
    private var reply: ByteArray? = null
    var theirWave: Boolean = false
        private set
    var problem: CardProblem? = null
        private set
    /** The finder's beacon token, from its hello. */
    var peerToken: ByteArray? = null
        private set

    /** Step 2. Returns what came of it; a blocked key is told nothing more. */
    fun onHello(msg: ByteArray, now: Long): PassResult {
        if (msg.isEmpty() || msg[0].toInt() != PassProtocol.VERSION) return refuse(CardProblem.Version)
        if (msg.size <= PassProtocol.HELLO_HEAD) return refuse(CardProblem.Malformed)
        val theirNonce = msg.copyOfRange(1, 1 + CardCodec.NONCE_BYTES)
        peerToken = msg.copyOfRange(1 + CardCodec.NONCE_BYTES, PassProtocol.HELLO_HEAD)
        val wire = msg.copyOfRange(PassProtocol.HELLO_HEAD, msg.size)
        return when (val c = CardCodec.open(wire, nonce, now, self)) {
            is CardCheck.Bad -> refuse(c.problem)
            is CardCheck.Good -> {
                peer = c.card
                if (!blocked(c.card.keyId)) reply = PassProtocol.card(cardFor(theirNonce), waveFor(c.card))
                PassResult.Met(c.card, false)
            }
        }
    }

    private fun refuse(p: CardProblem): PassResult { problem = p; return PassResult.Refused(p) }

    /** Step 3: our card for them, made once (a long read asks again at each offset), or null before a good hello. */
    fun cardMessage(): ByteArray? = reply

    /** Step 4. True when the wave was taken (after a good hello). */
    fun onWave(msg: ByteArray): Boolean {
        if (peer == null || msg.size != 1) return false
        theirWave = msg[0].toInt() == 1
        return true
    }
}

/** The side that found the other (the GATT client) for one connection. */
class ClientSession(
    private val nonce: ByteArray,
    private val self: ByteArray,
    private val token: ByteArray,
    private val cardFor: (theirNonce: ByteArray) -> ByteArray,
    private val blocked: (String) -> Boolean = { false },
) {
    var peer: PassCard? = null
        private set
    /** The key their nonce read announced; their card must be signed by it. */
    private var announced: ByteArray? = null

    /**
     * Step 1 → 2: the [HELLO] to write after reading their nonce, or null when it isn't one this Tandem speaks, or the
     * key it announces is one we blocked (they get nothing of ours, not even the card).
     */
    fun hello(nonceMsg: ByteArray): ByteArray? {
        val theirs = PassProtocol.readNonce(nonceMsg) ?: return null
        val key = PassProtocol.readKey(nonceMsg) ?: return null
        if (blocked(PassIdentity.keyId(key))) return null
        announced = key
        return PassProtocol.hello(nonce, token, cardFor(theirs))
    }

    /** Step 3: their card, checked against our nonce and the key they announced. */
    fun onCard(msg: ByteArray, now: Long): PassResult {
        if (msg.size < 2) return PassResult.Refused(CardProblem.Malformed)
        val wire = msg.copyOfRange(0, msg.size - 1)
        return when (val c = CardCodec.open(wire, nonce, now, self)) {
            is CardCheck.Bad -> PassResult.Refused(c.problem)
            is CardCheck.Good -> if (announced?.contentEquals(c.card.publicKey) != true) PassResult.Refused(CardProblem.Signature)
            else { peer = c.card; PassResult.Met(c.card, msg.last().toInt() == 1) }
        }
    }

    /** Step 4. */
    fun wave(waved: Boolean): ByteArray = byteArrayOf(if (waved) 1 else 0)
}
