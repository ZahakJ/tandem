package io.github.zahakj.tandem.audio

import kotlin.math.abs

/** Note names, chord symbols and voice leading. Used only while songs are built, never in the audio loop. */
object Theory {
    private val pcs = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

    /** "C4" = 60, "F#5", "Bb3". */
    fun midi(name: String): Int {
        require(name.isNotEmpty()) { "Empty note" }
        var pc = pcs[name[0].uppercaseChar()] ?: error("Bad note $name")
        var i = 1
        while (i < name.length && (name[i] == '#' || name[i] == 'b')) { pc += if (name[i] == '#') 1 else -1; i++ }
        val octave = name.substring(i).toIntOrNull() ?: error("Bad octave in $name")
        return 12 * (octave + 1) + pc
    }

    private fun pitchClass(s: String): Pair<Int, Int> {
        var pc = pcs[s[0].uppercaseChar()] ?: error("Bad chord root $s")
        var i = 1
        while (i < s.length && (s[i] == '#' || s[i] == 'b')) { pc += if (s[i] == '#') 1 else -1; i++ }
        return ((pc % 12) + 12) % 12 to i
    }

    private val qualities = mapOf(
        "" to intArrayOf(0, 4, 7), "m" to intArrayOf(0, 3, 7), "5" to intArrayOf(0, 7),
        "7" to intArrayOf(0, 4, 7, 10), "maj7" to intArrayOf(0, 4, 7, 11), "m7" to intArrayOf(0, 3, 7, 10),
        "m9" to intArrayOf(0, 3, 7, 10, 14), "maj9" to intArrayOf(0, 4, 7, 11, 14), "9" to intArrayOf(0, 4, 7, 10, 14),
        "13" to intArrayOf(0, 4, 10, 14, 21), "add9" to intArrayOf(0, 4, 7, 14), "madd9" to intArrayOf(0, 3, 7, 14),
        "6" to intArrayOf(0, 4, 7, 9), "m6" to intArrayOf(0, 3, 7, 9), "69" to intArrayOf(0, 4, 7, 9, 14),
        "sus2" to intArrayOf(0, 2, 7), "sus4" to intArrayOf(0, 5, 7), "7sus4" to intArrayOf(0, 5, 7, 10),
        "dim" to intArrayOf(0, 3, 6), "m7b5" to intArrayOf(0, 3, 6, 10), "7b9" to intArrayOf(0, 4, 7, 10, 13),
        "m11" to intArrayOf(0, 3, 7, 10, 14, 17), "maj7#11" to intArrayOf(0, 4, 7, 11, 18), "aug" to intArrayOf(0, 4, 8),
    )

    /** A chord: root pitch class, intervals above it, and the bass pitch class (differs for slash chords). */
    class Chord(val root: Int, val intervals: IntArray, val bass: Int, val name: String) {
        val third: Int get() = intervals.firstOrNull { it == 3 || it == 4 } ?: intervals.getOrElse(1) { 7 }
        val fifth: Int get() = intervals.firstOrNull { it in 6..8 } ?: 7
        val seventh: Int get() = intervals.firstOrNull { it == 10 || it == 11 || it == 9 } ?: 12
        /** Pitch classes of every chord tone, root first. */
        val tones: List<Int> get() = intervals.map { (root + it) % 12 }
    }

    fun chord(symbol: String): Chord {
        val (root, n) = pitchClass(symbol)
        val slash = symbol.indexOf('/', n)
        val q = if (slash >= 0) symbol.substring(n, slash) else symbol.substring(n)
        val iv = qualities[q] ?: error("Unknown chord quality '$q' in $symbol")
        val bass = if (slash >= 0) pitchClass(symbol.substring(slash + 1)).first else root
        return Chord(root, iv, bass, symbol)
    }

    /** The lowest note with pitch class [pc] at or above [lo]. */
    fun atOrAbove(pc: Int, lo: Int): Int { var m = lo - ((lo - pc) % 12 + 12) % 12; if (m < lo) m += 12; return m }

    /**
     * Voices [c] between [lo] and [hi] as close to [prev] as possible (smooth voice leading). Big chords drop the
     * root (the bass plays it) and keep at most [maxNotes].
     */
    fun voice(c: Chord, lo: Int, hi: Int, prev: IntArray?, maxNotes: Int = 4, open: Boolean = false): IntArray {
        var pcsList = c.intervals.map { (c.root + it) % 12 }.distinct()
        // Open (orchestral) voicing: a major seventh chord leaves its root to the bass, so the root never sits a
        // semitone above the seventh inside the chord.
        if (open && 11 in c.intervals && pcsList.size > 3) pcsList = pcsList.drop(1)
        if (pcsList.size > maxNotes) {
            // Keep the colour: drop the root first, then the fifth.
            pcsList = pcsList.drop(1)
            if (pcsList.size > maxNotes) pcsList = pcsList.filter { it != (c.root + 7) % 12 }
            pcsList = pcsList.take(maxNotes)
        }
        val n = pcsList.size
        var best: IntArray? = null
        var bestCost = Int.MAX_VALUE
        val target = prev ?: IntArray(n) { lo + (hi - lo) * (it + 1) / (n + 1) }
        // Every inversion, in every octave that fits.
        for (inv in 0 until n) {
            val order = List(n) { pcsList[(inv + it) % n] }
            var base = atOrAbove(order[0], lo)
            while (base <= hi) {
                val v = IntArray(n)
                var last = base - 1
                for (k in 0 until n) { var m = atOrAbove(order[k], last + 1); v[k] = m; last = m }
                if (v.last() <= hi) {
                    v.sort()
                    var cost = cost(v, target)
                    // Open voicing also avoids minor seconds between neighbouring voices.
                    if (open) for (k in 1 until n) if (v[k] - v[k - 1] == 1) cost += 40
                    if (cost < bestCost) { bestCost = cost; best = v }
                }
                base += 12
            }
        }
        return best ?: IntArray(n) { atOrAbove(pcsList[it], lo) }.also { it.sort() }
    }

    private fun cost(a: IntArray, b: IntArray): Int {
        var c = 0
        val m = minOf(a.size, b.size)
        for (i in 0 until m) c += abs(a[i] - b[i])
        // Mismatched sizes: compare the outer voices too.
        c += abs(a.last() - b.last()) * (if (a.size != b.size) 1 else 0)
        // Prefer voicings centred where the last one was.
        c += abs(a.sum() / a.size - b.sum() / b.size)
        return c
    }
}
