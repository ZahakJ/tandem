package io.github.zahakj.tandem.pass

import kotlinx.serialization.Serializable

/** Your own card: what Me (the character maker) saves, and what Tandem Pass signs and sends. */
@Serializable
data class MeCard(
    val avatar: AvatarSpec = AvatarSpec(),
    val name: String = "",
    val greeting: Int = Greetings.DEFAULT,
    /** The library key of the favourite game shown on the card (its title only goes out), or null for none. */
    val favorite: String? = null,
    /** Share the game played last (its title) on the card. Off unless the owner turns it on. */
    val shareRecent: Boolean = false,
)

/**
 * Someone met through Tandem Pass, as the Plaza keeps them: their latest card (no signature kept: it was checked
 * when it came in, over a nonce only that connection knew), and the story of your passes.
 */
@Serializable
data class Visitor(
    /** Their public key, hex: who they are, whatever name they give. */
    val key: String,
    val name: String,
    val greeting: Int,
    val avatar: AvatarSpec,
    val favorite: String? = null,
    val recent: String? = null,
    /** How many passes they have, by their own count. */
    val theirPasses: Int = 0,
    val firstMet: Long,
    val lastMet: Long,
    /** Passes with them that counted (one per [PassBook.REPASS_MS] at most). */
    val times: Int = 1,
    val countedAt: Long = firstMet,
    /** Not looked at on the Plaza since their last counted pass: the New filter and the tile's badge. */
    val fresh: Boolean = true,
    /** Hidden from the Plaza until the next pass that counts. */
    val hidden: Boolean = false,
    /** You waved back: they see it the next time you pass. */
    val waved: Boolean = false,
    /** Your wave reached them. */
    val waveSent: Boolean = false,
    /** They waved at you on their last pass. */
    val wavedAtMe: Boolean = false,
)

/** plaza.json: Tandem Pass's switches, your card, the people you met and the keys you blocked. */
@Serializable
data class PlazaData(
    val v: Int = PassBook.VERSION,
    /** Tandem Pass itself. Off until the owner turns it on (children-safe default). */
    val on: Boolean = false,
    /** Keep passing while a game plays, with a notification. Off by default: passing pauses in games. */
    val whilePlaying: Boolean = false,
    /** Me has been made (the first-time setup is done). */
    val made: Boolean = false,
    val me: MeCard = MeCard(),
    /** Your pass counter: passes that counted, repeats included. It goes out on your card. */
    val passes: Int = 0,
    val visitors: List<Visitor> = emptyList(),
    /** Blocked public keys (hex): never shown, their cards refused and never answered. */
    val blocked: List<String> = emptyList(),
)

enum class PassOutcome { New, Again, Refreshed, Blocked }

/** The Plaza's book-keeping, pure (PassBookTest): who came, how often, waves, hiding and blocking. */
object PassBook {
    const val VERSION = 1
    /** Passing the same person again counts once per six hours (a family on one sofa doesn't count all day). */
    const val REPASS_MS = 6L * 60 * 60 * 1000
    /** The most visitors kept; past it the ones met longest ago (and already seen) go first. */
    const val MAX_VISITORS = 300

    fun isBlocked(d: PlazaData, key: String): Boolean = key in d.blocked

    /** A checked [card] arrived at [now]: who it is to us now, and the book with them in it. */
    fun record(d: PlazaData, card: PassCard, now: Long): Pair<PlazaData, PassOutcome> {
        val key = card.keyId
        if (isBlocked(d, key)) return d to PassOutcome.Blocked
        val old = d.visitors.firstOrNull { it.key == key }
        if (old == null) {
            val v = Visitor(
                key, card.name, Greetings.known(card.greeting), card.avatar, card.favorite, card.recent, card.passes,
                firstMet = now, lastMet = now, countedAt = now,
            )
            return trim(d.copy(visitors = listOf(v) + d.visitors, passes = d.passes + 1)) to PassOutcome.New
        }
        val counts = now - old.countedAt >= REPASS_MS
        val updated = old.copy(
            name = card.name, greeting = Greetings.known(card.greeting), avatar = card.avatar, favorite = card.favorite,
            recent = card.recent, theirPasses = card.passes, lastMet = maxOf(old.lastMet, now),
            times = if (counts) old.times + 1 else old.times,
            countedAt = if (counts) now else old.countedAt,
            fresh = old.fresh || counts,
            hidden = old.hidden && !counts,
        )
        val rest = d.visitors.filter { it.key != key }
        return d.copy(visitors = listOf(updated) + rest, passes = if (counts) d.passes + 1 else d.passes) to
            (if (counts) PassOutcome.Again else PassOutcome.Refreshed)
    }

    private fun trim(d: PlazaData): PlazaData {
        if (d.visitors.size <= MAX_VISITORS) return d
        val drop = d.visitors.sortedWith(compareBy<Visitor>({ it.fresh }, { it.lastMet })).take(d.visitors.size - MAX_VISITORS).map { it.key }.toSet()
        return d.copy(visitors = d.visitors.filter { it.key !in drop })
    }

    /** Whether we tell [key] that we waved (only while they aren't hidden or blocked). */
    fun waveFor(d: PlazaData, key: String): Boolean = d.visitors.firstOrNull { it.key == key }?.let { it.waved && !it.hidden } ?: false

    /** After an exchange with [key]: our wave (if any) was delivered, and theirs is [theirWave]. */
    fun waves(d: PlazaData, key: String, sentOurs: Boolean, theirWave: Boolean): PlazaData = edit(d, key) {
        it.copy(waveSent = it.waveSent || (sentOurs && it.waved), wavedAtMe = theirWave, fresh = it.fresh || (theirWave && !it.wavedAtMe))
    }

    fun setWave(d: PlazaData, key: String, on: Boolean): PlazaData = edit(d, key) { it.copy(waved = on, waveSent = if (on) it.waveSent else false) }

    fun hide(d: PlazaData, key: String): PlazaData = edit(d, key) { it.copy(hidden = true, fresh = false) }

    fun unhide(d: PlazaData, key: String): PlazaData = edit(d, key) { it.copy(hidden = false) }

    /** Gone from the Plaza for good: the visitor is forgotten and the key refused from now on. */
    fun block(d: PlazaData, key: String): PlazaData =
        d.copy(visitors = d.visitors.filter { it.key != key }, blocked = (d.blocked + key).distinct())

    /** Undo for a block just made: [was] comes back as it was. */
    fun unblock(d: PlazaData, key: String, was: Visitor?): PlazaData =
        d.copy(blocked = d.blocked - key, visitors = if (was != null && d.visitors.none { it.key == key }) listOf(was) + d.visitors else d.visitors)

    fun unblockAll(d: PlazaData): PlazaData = d.copy(blocked = emptyList())

    /** Everyone looked at: the New filter empties and the badge goes. */
    fun seen(d: PlazaData): PlazaData = if (d.visitors.none { it.fresh }) d else d.copy(visitors = d.visitors.map { if (it.fresh) it.copy(fresh = false) else it })

    fun seen(d: PlazaData, key: String): PlazaData = edit(d, key) { if (it.fresh) it.copy(fresh = false) else it }

    /** Clear Plaza: everyone met is forgotten (blocks and your card stay, and so does your pass counter). */
    fun clear(d: PlazaData): PlazaData = d.copy(visitors = emptyList())

    /** Who the Plaza shows: not hidden, the latest pass first; [onlyNew] keeps the ones not looked at yet. */
    fun shown(d: PlazaData, onlyNew: Boolean): List<Visitor> =
        d.visitors.filter { !it.hidden && (!onlyNew || it.fresh) }.sortedByDescending { it.lastMet }

    /** The tile's and Start's badge: visitors not looked at yet. */
    fun newCount(d: PlazaData): Int = d.visitors.count { it.fresh && !it.hidden }

    private fun edit(d: PlazaData, key: String, change: (Visitor) -> Visitor): PlazaData {
        if (d.visitors.none { it.key == key }) return d
        return d.copy(visitors = d.visitors.map { if (it.key == key) change(it) else it })
    }
}
