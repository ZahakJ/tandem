package io.github.zahakj.tandem.pass

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Who you are to Tandem Pass: one Ed25519 keypair from a 32-byte seed, made on the device the first time Tandem Pass
 * is used (IdentityStore keeps the seed, sealed by an Android keystore key, out of every backup).
 *
 * It is a Concord identity from the start, so chat can come later without moving anyone's key (docs/guide/plaza.md):
 *
 * - The seed is a Concord **account seed**: the same 32 bytes Concord's `identity.FromSeed` turns into the same key
 *   (`ed25519.NewKeyFromSeed`). Handing the seed to Concord (or Concord's seed to Tandem) gives one identity.
 * - [fingerprint] is Concord's safety number: base32 (RFC 4648, upper case, no padding) of the first 20 bytes of
 *   SHA-256 of the raw public key, in groups of four.
 * - [peerId] is the libp2p PeerID Concord dials: the public key as a libp2p protobuf (`08 01 12 20` + key) in an
 *   identity multihash (`00 24`), base58btc. It starts "12D3KooW".
 *
 * Concord's per-device key (its device seed and DeviceCert) is not made here: Tandem's card speaks for the account,
 * and a later chat build adds its device key beside it, certified by this seed, as Concord's linking does.
 */
class PassIdentity(private val seed: ByteArray) {
    init { require(seed.size == Ed25519.SEED_BYTES) }

    val publicKey: ByteArray = Ed25519.publicKey(seed)

    fun sign(message: ByteArray): ByteArray = Ed25519.sign(seed, message)

    val fingerprint: String get() = fingerprintOf(publicKey)
    val peerId: String get() = peerIdOf(publicKey)

    companion object {
        /** Concord's safety number for [publicKey]: 32 base32 characters in groups of four. */
        fun fingerprintOf(publicKey: ByteArray): String {
            val sum = MessageDigest.getInstance("SHA-256").digest(publicKey).copyOfRange(0, 20)
            return base32(sum).chunked(4).joinToString(" ")
        }

        /** The libp2p PeerID of an Ed25519 [publicKey], as Concord shows and dials it. */
        fun peerIdOf(publicKey: ByteArray): String {
            require(publicKey.size == Ed25519.PUBLIC_BYTES)
            val proto = byteArrayOf(0x08, 0x01, 0x12, 0x20) + publicKey
            return base58(byteArrayOf(0x00, proto.size.toByte()) + proto)
        }

        /** The public key as Tandem stores it (visitors, blocks): lower-case hex. */
        fun keyId(publicKey: ByteArray): String = buildString(publicKey.size * 2) {
            for (b in publicKey) { val v = b.toInt() and 0xff; append(HEX[v shr 4]); append(HEX[v and 15]) }
        }

        private const val HEX = "0123456789abcdef"

        fun keyBytes(id: String): ByteArray? = runCatching {
            if (id.length != Ed25519.PUBLIC_BYTES * 2) return null
            ByteArray(id.length / 2) { i -> id.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }.getOrNull()

        private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        internal fun base32(data: ByteArray): String {
            val out = StringBuilder()
            var buffer = 0
            var bits = 0
            for (b in data) {
                buffer = (buffer shl 8) or (b.toInt() and 0xff)
                bits += 8
                while (bits >= 5) {
                    out.append(B32[(buffer shr (bits - 5)) and 31])
                    bits -= 5
                }
            }
            if (bits > 0) out.append(B32[(buffer shl (5 - bits)) and 31])
            return out.toString()
        }

        private const val B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

        internal fun base58(data: ByteArray): String {
            var n = BigInteger(1, data)
            val radix = BigInteger.valueOf(58)
            val out = StringBuilder()
            while (n.signum() > 0) {
                val (q, r) = n.divideAndRemainder(radix)
                out.append(B58[r.toInt()])
                n = q
            }
            for (b in data) if (b.toInt() == 0) out.append('1') else break
            return out.reverse().toString()
        }
    }
}
