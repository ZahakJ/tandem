package io.github.zahakj.tandem

/*
 * What's new (ui/whatsnew/): the rules for when the deck opens by itself, plain Kotlin so WhatsNewTest covers them.
 *
 * - A deck belongs to a minor version (x.Y.0). A patch (x.Y.z) shows its minor's deck and never one of its own.
 * - It opens once, on the first start of a new minor version: Settings.whatsNewSeen remembers the newest deck seen.
 * - A fresh install isn't an update: its welcome is its tour, so the deck is marked seen without showing.
 * - Someone who skipped versions (0.7 → 0.9) sees only the deck of the version they now have.
 * - Settings › Play & system › Show what's new off: nothing opens, and the version is marked seen, so turning it back
 *   on later doesn't bring up an old deck.
 */
object WhatsNewRules {
    /** "0.9.3" → "0.9.0", "v0.10.0-rc1" → "0.10.0", "1" → "1.0.0": the minor version a build belongs to. */
    fun minorOf(version: String): String {
        val clean = version.trim().removePrefix("v").removePrefix("V").substringBefore('+').substringBefore('-')
        val n = clean.split('.').map { it.trim().toIntOrNull() ?: 0 }
        return "${n.getOrElse(0) { 0 }}.${n.getOrElse(1) { 0 }}.0"
    }

    /** True for a minor version itself (x.Y.0), the only versions that may have a deck. */
    fun isMinor(version: String): Boolean = Releases.compare(version, minorOf(version)) == 0

    sealed interface Decision {
        /** Open the deck of [version]. */
        data class Show(val version: String) : Decision
        /** Nothing to show, but remember [version] as seen. */
        data class MarkSeen(val version: String) : Decision
        /** Nothing to do. */
        data object Stay : Decision
    }

    /**
     * What to do on this start. [current] is this build's version, [seen] the newest deck seen (null: none yet),
     * [welcomed] whether the first-run welcome has been done (false: a fresh install), [enabled] the Show what's new
     * switch, [decks] the versions that have a deck.
     */
    fun decide(current: String, seen: String?, welcomed: Boolean, enabled: Boolean, decks: Collection<String>): Decision {
        val minor = minorOf(current)
        if (seen != null && Releases.compare(seen, minor) >= 0) return Decision.Stay
        if (!welcomed || !enabled) return Decision.MarkSeen(minor)
        if (decks.none { Releases.compare(it, minor) == 0 }) return Decision.Stay
        return Decision.Show(minor)
    }
}
