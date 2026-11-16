package io.github.zahakj.tandem.romm

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

class RommCipherTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun encryptedCredentialsRoundTripWithoutPlaintextInEnvelope() {
        val key = key()
        val plain = "{\"token\":\"synthetic-private-token\",\"server\":\"https://fixture.example.test\"}".toByteArray()
        val encrypted = RommCipher.encrypt(plain, key)
        assertArrayEquals(plain, RommCipher.decrypt(encrypted, key))
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("synthetic-private-token"))
    }

    @Test fun eachWriteHasAFreshIv() {
        val key = key()
        assertFalse(RommCipher.encrypt(byteArrayOf(42), key).contentEquals(RommCipher.encrypt(byteArrayOf(42), key)))
    }

    @Test fun tamperingWithCiphertextOrIvFailsAuthentication() {
        val key = key()
        for (index in listOf(1, 13, 29)) {
            val encrypted = RommCipher.encrypt("synthetic-token".toByteArray(), key)
            encrypted[index] = (encrypted[index].toInt() xor 1).toByte()
            assertThrows(AEADBadTagException::class.java) { RommCipher.decrypt(encrypted, key) }
        }
    }

    @Test fun aDifferentKeyCannotReadTheConfiguration() {
        val encrypted = RommCipher.encrypt("synthetic-token".toByteArray(), key())
        assertThrows(AEADBadTagException::class.java) { RommCipher.decrypt(encrypted, key()) }
    }

    @Test fun malformedOrUnknownEnvelopeIsRejected() {
        for (bytes in listOf(byteArrayOf(), byteArrayOf(1, 2), ByteArray(29) { 2 })) {
            assertThrows(IllegalArgumentException::class.java) { RommCipher.decrypt(bytes, key()) }
        }
    }
}
