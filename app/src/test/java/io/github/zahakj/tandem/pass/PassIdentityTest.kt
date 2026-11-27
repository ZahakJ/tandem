package io.github.zahakj.tandem.pass

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/** The identity's Concord forms (PassIdentity.kt): safety number and libp2p PeerID. */
class PassIdentityTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")

    @Test fun base32IsRfc4648WithoutPadding() {
        assertEquals("MZXW6YTBOI", PassIdentity.base32("foobar".toByteArray()))
        assertEquals("MY", PassIdentity.base32("f".toByteArray()))
    }

    @Test fun base58IsBitcoins() {
        assertEquals("2NEpo7TZRRrLZSi2U", PassIdentity.base58("Hello World!".toByteArray()))
        assertEquals("11", PassIdentity.base58(byteArrayOf(0, 0)))
    }

    @Test fun fingerprintIsConcordsSafetyNumber() {
        val id = PassIdentity(seed)
        val fp = id.fingerprint
        // 160 bits in 32 base32 characters, grouped in fours.
        assertEquals(8, fp.split(' ').size)
        assertTrue(fp.split(' ').all { it.length == 4 && it.all { c -> c in 'A'..'Z' || c in '2'..'7' } })
        val sum = MessageDigest.getInstance("SHA-256").digest(id.publicKey).copyOfRange(0, 20)
        assertEquals(PassIdentity.base32(sum), fp.replace(" ", ""))
    }

    @Test fun peerIdIsLibp2pIdentityMultihash() {
        val id = PassIdentity(seed)
        val p = id.peerId
        assertTrue(p, p.startsWith("12D3KooW"))
        assertEquals(52, p.length)
    }

    @Test fun keyIdRoundTrips() {
        val id = PassIdentity(seed)
        val k = PassIdentity.keyId(id.publicKey)
        assertEquals("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", k)
        assertArrayEquals(id.publicKey, PassIdentity.keyBytes(k))
        assertNull(PassIdentity.keyBytes("abc"))
    }
}
