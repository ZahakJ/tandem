package io.github.zahakj.tandem.pass

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Ed25519 signatures (RFC 8032), in plain Kotlin: Android has them only from 13 on, and Tandem runs on 11. Written
 * straight from the RFC's reference (section 5.1, extended coordinates over BigInteger), so it is short and easy to
 * check rather than fast: a signature takes a few tens of milliseconds, and Tandem Pass makes a handful an hour.
 * Checked against the RFC's test vectors and the JDK's own Ed25519 in Ed25519Test.
 *
 * Keys are the RFC's: a 32-byte secret seed (Go's `ed25519.NewKeyFromSeed`, Concord's account seed), a 32-byte
 * public key and 64-byte signatures. Secret values are not handled in constant time; nothing here runs where an
 * attacker can time it closely (one signature per pass, over Bluetooth).
 */
object Ed25519 {
    const val SEED_BYTES = 32
    const val PUBLIC_BYTES = 32
    const val SIGNATURE_BYTES = 64

    private val TWO: BigInteger = BigInteger.valueOf(2)
    private val P: BigInteger = BigInteger.ONE.shiftLeft(255) - BigInteger.valueOf(19)
    private val L: BigInteger = BigInteger.ONE.shiftLeft(252) + BigInteger("27742317777372353535851937790883648493")
    private val D: BigInteger = m(BigInteger.valueOf(-121665) * inv(BigInteger.valueOf(121666)))
    private val TWO_D: BigInteger = m(D * TWO)
    private val SQRT_M1: BigInteger = TWO.modPow((P - BigInteger.ONE).shiftRight(2), P)

    /** A point in extended coordinates (X:Y:Z:T), x = X/Z, y = Y/Z, x·y = T/Z. */
    private class Point(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)

    private val ZERO = Point(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
    private val G: Point = run {
        val y = m(BigInteger.valueOf(4) * inv(BigInteger.valueOf(5)))
        val x = recoverX(y, 0)!!
        Point(x, y, BigInteger.ONE, m(x * y))
    }

    /** Reduced mod p, never negative (Kotlin's % on BigInteger is a remainder). */
    private fun m(v: BigInteger): BigInteger = v.mod(P)

    private fun inv(v: BigInteger): BigInteger = m(v).modPow(P - TWO, P)

    private fun add(a: Point, b: Point): Point {
        val ea = m((a.y - a.x) * (b.y - b.x))
        val eb = m((a.y + a.x) * (b.y + b.x))
        val c = m(m(a.t * TWO_D) * b.t)
        val d = m(m(a.z * TWO) * b.z)
        val e = m(eb - ea)
        val f = m(d - c)
        val g = m(d + c)
        val h = m(eb + ea)
        return Point(m(e * f), m(g * h), m(f * g), m(e * h))
    }

    private fun mul(scalar: BigInteger, point: Point): Point {
        var q = ZERO
        var p = point
        var s = scalar
        while (s.signum() > 0) {
            if (s.testBit(0)) q = add(q, p)
            p = add(p, p)
            s = s.shiftRight(1)
        }
        return q
    }

    private fun same(a: Point, b: Point): Boolean =
        m(a.x * b.z - b.x * a.z).signum() == 0 && m(a.y * b.z - b.y * a.z).signum() == 0

    private fun recoverX(y: BigInteger, sign: Int): BigInteger? {
        if (y >= P) return null
        val y2 = m(y * y)
        val x2 = m(m(y2 - BigInteger.ONE) * inv(D * y2 + BigInteger.ONE))
        if (x2.signum() == 0) return if (sign != 0) null else BigInteger.ZERO
        var x = x2.modPow((P + BigInteger.valueOf(3)).shiftRight(3), P)
        if (m(x * x - x2).signum() != 0) x = m(x * SQRT_M1)
        if (m(x * x - x2).signum() != 0) return null
        if ((if (x.testBit(0)) 1 else 0) != sign) x = P - x
        return x
    }

    private fun compress(p: Point): ByteArray {
        val zi = inv(p.z)
        val x = m(p.x * zi)
        val y = m(p.y * zi)
        return le32(if (x.testBit(0)) y.setBit(255) else y)
    }

    private fun decompress(b: ByteArray): Point? {
        if (b.size != 32) return null
        val raw = leInt(b)
        val sign = if (raw.testBit(255)) 1 else 0
        val y = raw.clearBit(255)
        val x = recoverX(y, sign) ?: return null
        return Point(x, y, BigInteger.ONE, m(x * y))
    }

    private fun le32(v: BigInteger): ByteArray {
        val be = v.toByteArray()
        val out = ByteArray(32)
        for (i in 0 until minOf(32, be.size)) out[i] = be[be.size - 1 - i]
        return out
    }

    private fun leInt(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

    private fun sha512(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-512")
        for (p in parts) md.update(p)
        return md.digest()
    }

    private fun sha512ModL(vararg parts: ByteArray): BigInteger = leInt(sha512(*parts)).mod(L)

    private fun expand(seed: ByteArray): Pair<BigInteger, ByteArray> {
        require(seed.size == SEED_BYTES) { "seed must be 32 bytes" }
        val h = sha512(seed)
        // Clamp: clear the three low bits and everything from bit 255, set bit 254.
        val a = leInt(h.copyOfRange(0, 32)).and(BigInteger.ONE.shiftLeft(254) - BigInteger.valueOf(8)).setBit(254)
        return a to h.copyOfRange(32, 64)
    }

    /** The public key of a 32-byte [seed]. */
    fun publicKey(seed: ByteArray): ByteArray = compress(mul(expand(seed).first, G))

    /** Signs [message] with [seed]: the 64-byte signature (deterministic, as the RFC says). */
    fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        val (a, prefix) = expand(seed)
        val pub = compress(mul(a, G))
        val r = sha512ModL(prefix, message)
        val rs = compress(mul(r, G))
        val h = sha512ModL(rs, pub, message)
        val s = (r + h * a).mod(L)
        return rs + le32(s)
    }

    /** True when [signature] is [publicKey]'s over [message]. Malformed keys or signatures are simply false. */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != PUBLIC_BYTES || signature.size != SIGNATURE_BYTES) return false
        val a = decompress(publicKey) ?: return false
        val rs = signature.copyOfRange(0, 32)
        val r = decompress(rs) ?: return false
        val s = leInt(signature.copyOfRange(32, 64))
        if (s >= L) return false
        val h = sha512ModL(rs, publicKey, message)
        return same(mul(s, G), add(r, mul(h, a)))
    }
}
