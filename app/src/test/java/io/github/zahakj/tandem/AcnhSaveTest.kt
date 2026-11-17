package io.github.zahakj.tandem

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Vectors from the Python save tool (acnh/save.py), so both read and write the same bytes. */
class AcnhSaveTest {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
    private val header = hex("a54dca182530bb1d6d132cded6237b2ed91e3f721fcb1971174494d6493c9d5c3460be31201e69fedaa0eee8b9997f5c7c2999fdafe593253cd654af4dfad71427a0aeb3fee9232f8af2211f9ee491c5b10becb5563bfc1e6f93427ecbc8fe2955e5cd8e46dc8ed4b7c2764d2a5a4d767706f85d8690024ad6bda3401be9c8cbccc935f6cd1f61226ae15338ae1a34004d33ba0d246ac04c81b1baf23e3bf9eef5f79f2b4934af87f5520b69b94b0d982e85bb55b672a872637acd7466fcb60e0e8ff18463b0e4b2ba29703474f064ac68f700f5b02b3dc666f45bdeaa2ccaedcd2b5157410e4dee4af2b34f430a073447de636c0e806c957ba684d6431fb5ead7424d09e15d024c5848f23d1fa6f7361d7f618d1532e70e20e2a6668de7f47e8467e546d53ec8e2a1257bdb256c9b3e4fbb498146ef7030cbf9537252dcceadd764b6a32fbb09adeae109c4a997203975352b878b145c8a42d884cf4cfda72d8e1d5dd92589082d852a7122873ee805add58942167a385286195c679f9c6994e45b8ab1098012070961f37de436ddfdc99d6e75af6547cfb11b42072482dc531c2bc3907c9617eb5e5089e40186baa8a57d119e6fb65d00abc32af38e667f022e872d49cc15c90b999b772b4fc7a6fd4c914a16db4708752b0f1544b835c0e719097dfa8701e9232f21f2812687786976ebfcc327f5931765274ba9829b4406f61ff889326ffa9492edeeee3c669f2bf20894ea27e689c66b6b262e4886b8438f39ba76fef8c90c5101fbe6cf9a48d5b0c0a13da900a6adcb3d64069481be21c9c727b8db8c188f341a924c7f88dfa161bfdb0ecc682919d2e64692f8194157f1d4af90988285cf7a9af7c93d5552266afe70e7aae6da47627c2e59af2ea37abc84670ad3c4d36bc08aad1fff8eb8406e2f8a7fc4cce4dd9f0b4110d9f2fa0025c8efe57f37724f4d37ea2b14004077139b4180df3932249962c6857200059aeb8ea17cf3787e0ed29d1c0b63ffd7298374d9bd74fc11add7b9ca6503952269fd669f6376ee71879737fd5f72f8d51c4ac91b6d0c48d41a1e5ec9e6a0392854")
    private val body = ByteArray(64) { it.toByte() }

    @Test fun encryptsAsThePythonToolDoes() {
        val c = AcnhSave.crypt(header, body)
        assertArrayEquals(hex("a2bd31c769d96eafa1382b812ac315cc6272d6df410fac3e5ffed35eb10a24c3bc6669275379f4c2fd732d777bff9ad45f1e8e02f689c4d2f12513ef0a455580"), c)
        assertArrayEquals(body, AcnhSave.crypt(header, c))
    }

    @Test fun hashesAsThePythonToolDoes() {
        for ((n, want) in listOf(0 to 0L, 1 to 1364076727L, 2 to 821347078L, 3 to 2247144487L, 4 to 4106284089L, 7 to 3598304188L, 64 to 2303633163L, )) assertEquals("murmur3 of $n bytes", want, AcnhSave.murmur3(body, 0, n))
    }

    @Test fun anAccountIdIsLittleEndian() {
        assertArrayEquals(hex("0F0E0D0C0B0A09080706050403020100"), AcnhSave.uid("000102030405060708090A0B0C0D0E0F"))
    }
}
