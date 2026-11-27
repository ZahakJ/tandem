package io.github.zahakj.tandem.pass

import java.io.ByteArrayOutputStream

/**
 * What two Tandems hand each other when they pass: a character, a name, a preset greeting, a couple of game titles
 * if the owner shares them, how many people they have met, and the public key that signs it all.
 *
 * [extensions] are typed blobs a newer Tandem may add (a Concord address, a mailbox key, a device certificate for
 * chat). They are inside the signature, kept when read, and ignored by a Tandem that doesn't know their type, so
 * chat can be added without a new card version or a migration.
 */
class PassCard(
    val publicKey: ByteArray,
    /** When it was signed, ms since 1970 (UTC). */
    val time: Long,
    val avatar: AvatarSpec,
    val name: String,
    val greeting: Int,
    /** How many different people the owner has passed (their own count, as they tell it). */
    val passes: Int,
    /** A favourite game's title, if the owner shares one. Titles only: never a file, system or play time. */
    val favorite: String? = null,
    /** What they played last, if the owner shares it. */
    val recent: String? = null,
    val extensions: List<CardExtension> = emptyList(),
) {
    val keyId: String get() = PassIdentity.keyId(publicKey)
}

class CardExtension(val type: Int, val data: ByteArray)

/** Why a card wasn't taken. */
enum class CardProblem { Malformed, Version, Signature, Clock, Nonce, Self }

sealed interface CardCheck {
    class Good(val card: PassCard) : CardCheck
    class Bad(val problem: CardProblem) : CardCheck
}

/**
 * The card's bytes, how it is signed and how it is checked.
 *
 * Body (version 1), every number big-endian:
 * ```
 * u8  version            1
 * 32  public key         Ed25519
 * u64 time               ms since 1970
 * u8  n, n bytes         character (AvatarCodec, ≤ 32)
 * u8  n, n bytes         name, UTF-8 (≤ 12 characters, ≤ 48 bytes)
 * u8                     greeting number (Greetings)
 * u32                    people passed
 * u8  n, n bytes         favourite game title, UTF-8 (≤ 64 bytes; 0 = not shared)
 * u8  n, n bytes         recently played title, UTF-8 (≤ 64 bytes; 0 = not shared)
 * u8  count, then each:  u8 type, u8 n, n bytes   (extensions)
 * ```
 * The wire form is the body and a 64-byte Ed25519 signature over
 * `"tandem-pass/card/v1" 0x00 ‖ u16 body length ‖ body ‖ the receiver's 16-byte nonce`: domain-separated and
 * length-prefixed as Concord signs its certificates, and bound to a nonce the receiver made for this one
 * connection, so a card recorded once can't be played to anyone again. The time is checked too (within
 * [MAX_SKEW_MS]: handheld clocks drift, so it is loose; the nonce is what stops replays).
 */
object CardCodec {
    const val VERSION = 1
    const val DOMAIN = "tandem-pass/card/v1"
    const val NONCE_BYTES = 16
    const val MAX_NAME_CHARS = 12
    const val MAX_NAME_BYTES = 48
    const val MAX_TITLE_CHARS = 48
    const val MAX_TITLE_BYTES = 64
    /** The most a card's body may hold, extensions included. */
    const val MAX_BODY = 420
    /** A body and its signature fit one BLE attribute (512 bytes) with room for the protocol's byte. */
    const val MAX_WIRE = MAX_BODY + Ed25519.SIGNATURE_BYTES
    const val MAX_SKEW_MS = 2L * 24 * 60 * 60 * 1000

    fun body(card: PassCard): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(VERSION)
        require(card.publicKey.size == Ed25519.PUBLIC_BYTES)
        out.write(card.publicKey)
        for (i in 7 downTo 0) out.write((card.time ushr (8 * i)).toInt() and 0xff)
        blob(out, AvatarCodec.encode(card.avatar))
        blob(out, utf8(cleanName(card.name), MAX_NAME_BYTES))
        out.write(Greetings.known(card.greeting))
        val passes = card.passes.coerceAtLeast(0)
        for (i in 3 downTo 0) out.write((passes ushr (8 * i)) and 0xff)
        blob(out, card.favorite?.let { utf8(cleanTitle(it), MAX_TITLE_BYTES) } ?: ByteArray(0))
        blob(out, card.recent?.let { utf8(cleanTitle(it), MAX_TITLE_BYTES) } ?: ByteArray(0))
        out.write(card.extensions.size.coerceAtMost(255))
        for (e in card.extensions.take(255)) { out.write(e.type and 0xff); blob(out, e.data) }
        val b = out.toByteArray()
        require(b.size <= MAX_BODY) { "card too big" }
        return b
    }

    private fun blob(out: ByteArrayOutputStream, data: ByteArray) {
        require(data.size <= 255)
        out.write(data.size)
        out.write(data)
    }

    /** What is signed for [body] towards a receiver that made [nonce]. */
    fun message(body: ByteArray, nonce: ByteArray): ByteArray {
        val d = DOMAIN.toByteArray(Charsets.UTF_8)
        return d + byteArrayOf(0, (body.size ushr 8).toByte(), body.size.toByte()) + body + nonce
    }

    /** [card] signed by [identity] for the receiver whose nonce is [nonce]: the bytes to send. */
    fun seal(card: PassCard, identity: PassIdentity, nonce: ByteArray): ByteArray {
        require(nonce.size == NONCE_BYTES)
        require(card.publicKey.contentEquals(identity.publicKey))
        val b = body(card)
        return b + identity.sign(message(b, nonce))
    }

    /**
     * Reads and checks a card sent to us: it must parse, be signed by the key it carries over [nonce] (ours, made for
     * this connection), be within the clock's slack of [now], and not be [self].
     */
    fun open(wire: ByteArray, nonce: ByteArray, now: Long, self: ByteArray? = null): CardCheck {
        if (wire.size <= Ed25519.SIGNATURE_BYTES || wire.size > MAX_WIRE) return CardCheck.Bad(CardProblem.Malformed)
        val body = wire.copyOfRange(0, wire.size - Ed25519.SIGNATURE_BYTES)
        val sig = wire.copyOfRange(wire.size - Ed25519.SIGNATURE_BYTES, wire.size)
        if (body[0].toInt() != VERSION) return CardCheck.Bad(CardProblem.Version)
        val card = parse(body) ?: return CardCheck.Bad(CardProblem.Malformed)
        if (nonce.size != NONCE_BYTES) return CardCheck.Bad(CardProblem.Nonce)
        if (!Ed25519.verify(card.publicKey, message(body, nonce), sig)) return CardCheck.Bad(CardProblem.Signature)
        if (kotlin.math.abs(now - card.time) > MAX_SKEW_MS) return CardCheck.Bad(CardProblem.Clock)
        if (self != null && self.contentEquals(card.publicKey)) return CardCheck.Bad(CardProblem.Self)
        return CardCheck.Good(card)
    }

    /** The card in a body, or null when anything is out of bounds. Text is cleaned as it is read. */
    fun parse(body: ByteArray): PassCard? = runCatching {
        if (body.size > MAX_BODY) return null
        val r = Reader(body)
        if (r.u8() != VERSION) return null
        val key = r.bytes(Ed25519.PUBLIC_BYTES)
        var time = 0L
        repeat(8) { time = (time shl 8) or r.u8().toLong() }
        val avatar = AvatarCodec.decode(r.blob(AvatarCodec.MAX_BYTES)) ?: return null
        val name = cleanName(String(r.blob(MAX_NAME_BYTES), Charsets.UTF_8))
        val greeting = r.u8()
        var passes = 0L
        repeat(4) { passes = (passes shl 8) or r.u8().toLong() }
        val fav = cleanTitle(String(r.blob(MAX_TITLE_BYTES), Charsets.UTF_8)).ifEmpty { null }
        val recent = cleanTitle(String(r.blob(MAX_TITLE_BYTES), Charsets.UTF_8)).ifEmpty { null }
        val n = r.u8()
        val ext = List(n) { CardExtension(r.u8(), r.blob(255)) }
        if (!r.done) return null
        PassCard(key, time, avatar, name, greeting, passes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), fav, recent, ext)
    }.getOrNull()

    private class Reader(val b: ByteArray) {
        var at = 0
        val done: Boolean get() = at == b.size
        fun u8(): Int { if (at >= b.size) throw IndexOutOfBoundsException(); return b[at++].toInt() and 0xff }
        fun bytes(n: Int): ByteArray { if (at + n > b.size) throw IndexOutOfBoundsException(); return b.copyOfRange(at, at + n).also { at += n } }
        fun blob(max: Int): ByteArray { val n = u8(); if (n > max) throw IllegalArgumentException(); return bytes(n) }
    }

    /** [text] as UTF-8, cut at a character boundary to at most [max] bytes. */
    fun utf8(text: String, max: Int): ByteArray {
        var t = text
        while (t.toByteArray(Charsets.UTF_8).size > max) t = t.dropLastCodePoint()
        return t.toByteArray(Charsets.UTF_8)
    }

    /** A name as it may show: no control or direction characters, spaces tidied, at most twelve characters. */
    fun cleanName(text: String): String = clean(text, MAX_NAME_CHARS)

    fun cleanTitle(text: String): String = clean(text, MAX_TITLE_CHARS)

    private fun clean(text: String, chars: Int): String {
        val sb = StringBuilder()
        var i = 0
        var count = 0
        var space = false
        while (i < text.length && count < chars) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            val type = Character.getType(cp)
            // Controls, formatting (direction overrides, zero widths), unassigned and private-use characters go.
            if (type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt() || type == Character.UNASSIGNED.toInt() ||
                type == Character.PRIVATE_USE.toInt() || type == Character.SURROGATE.toInt()
            ) continue
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) { space = sb.isNotEmpty(); continue }
            if (space) { sb.append(' '); count++; space = false; if (count >= chars) break }
            sb.appendCodePoint(cp)
            count++
        }
        return sb.toString()
    }

    private fun String.dropLastCodePoint(): String {
        if (isEmpty()) return this
        val cp = codePointBefore(length)
        return substring(0, length - Character.charCount(cp))
    }
}
