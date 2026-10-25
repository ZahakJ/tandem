package io.github.zahakj.tandem.audio

/**
 * A song, written like a tiny tracker: instruments ([parts]), sections with chords and patterns, and a [form] that
 * strings sections together. [compile] turns it into a flat list of note events once; the player then only reads.
 *
 * Time is counted in 16th-note steps: 16 per bar in 4/4, 12 in 3/4 (see [beats]).
 */
class Song(
    val id: String,
    val title: String,
    val mood: String,
    val bpm: Double,
    val swing: Double = 0.0,
    val parts: List<Part>,
    val sections: Map<String, Section>,
    val form: List<String>,
    /** Index in [form] where playback jumps back to after the end (skips the intro). */
    val loopFrom: Int = 1,
    val mix: Mix = Mix(),
    /** Plays once and ends instead of looping (jingles). */
    val oneShot: Boolean = false,
    /** Beats (quarter notes) per bar: 4 for 4/4, 3 for a waltz. */
    val beats: Int = 4,
    /**
     * Rubato, 0..~0.1: each four-bar phrase leans forward through its middle and relaxes into its cadence, the way a
     * player breathes with the melody.
     */
    val rubato: Double = 0.0,
    /** Orchestral chord voicing: no root inside major seventh chords, no minor seconds between neighbouring voices. */
    val openVoicing: Boolean = false,
) {
    val stepsPerBar: Int get() = beats * 4
    val bars: Int get() = form.sumOf { sections.getValue(it).bars }
    val seconds: Double get() = bars * beats * 60.0 / bpm
}

/** Song-wide effects and mixing. */
data class Mix(
    val reverbSize: Double = 0.6,
    val reverbDamp: Double = 0.5,
    val reverbReturn: Float = 0.6f,
    /** Echo time in 16th steps (3 = dotted 8th). */
    val echoSteps: Double = 3.0,
    val echoFeedback: Float = 0.35f,
    val echoReturn: Float = 0.5f,
    val chorusRate: Double = 0.35,
    val chorusDepth: Double = 2.2,
    val chorusReturn: Float = 0.6f,
    /** Sidechain release in beats. */
    val duckRelease: Double = 0.45,
    /** Vinyl crackle and rain bed, 0..1. */
    val crackle: Double = 0.0,
    val rain: Double = 0.0,
    /** Tape wobble depth in cents. */
    val wow: Double = 0.0,
    /** Master low-pass for a dusty sound (Hz, 0 = off). */
    val lofi: Double = 0.0,
    val master: Float = 1f,
    /** Timing looseness in ms and velocity looseness (0..1). */
    val humanize: Double = 6.0,
    val velJitter: Float = 0.08f,
    /** Use the concert hall (8-line FDN) instead of the small reverb; [hallDecay] is its RT60 in seconds. */
    val hall: Boolean = false,
    val hallDecay: Double = 2.4,
    val preDelayMs: Double = 24.0,
    /** Gentle bus compression (glue), 0 off .. 1 about 2:1 from -24 dBFS. */
    val glue: Float = 0f,
    /** Tape warmth: soft saturation drive, 0 off .. ~0.5. */
    val tape: Float = 0f,
)

/** An instrument in a song: a synth [patch], or a drum [kit] when the part is percussion. */
class Part(val name: String, val patch: Patch? = null, val kit: Kit? = null, val alt: Patch? = null) {
    init { require(patch != null || kit != null) }
}

/** One compiled note. [chord] is set for chiptune arpeggio notes. Built once; never allocated while playing. */
class Ev(
    val start: Double,
    val len: Double,
    val part: Int,
    val note: Int,
    val vel: Float,
    val prob: Float = 1f,
    val group: Int = 0,
    val variant: Int = 0,
    val minLoop: Int = 0,
    val chord: IntArray? = null,
    /** Extra delay inside the step (strums), in steps. */
    val nudge: Double = 0.0,
)

/**
 * A compiled song: events sorted by time, plus where the loop starts and ends. [tempo] holds a tempo multiplier for
 * every step (rubato, ritardandos); [chords] the chord sounding at every step.
 */
class Timeline(
    val events: Array<Ev>, val loopStart: Double, val end: Double, val loopStartIndex: Int, val groupVariants: IntArray,
    val tempo: FloatArray, val chords: Array<Theory.Chord>,
)

/**
 * A section of a song. [tempo] scales its speed; [rit] slows the last [ritBars] bars down by up to that fraction
 * (0.3 = ending at 70 % speed), for a breath before what comes next.
 */
class Section(val bars: Int, val chords: String, val gens: List<Gen>, val tempo: Double = 1.0, val rit: Double = 0.0, val ritBars: Int = 1)

/** Something that writes events for a section: a melody, chords, bass line, arpeggio or drum pattern. */
sealed interface Gen

/** Explicit notes: one token per [unit] steps. "C5", "." rest, "-" hold, "C5+E5" together, "!" loud, "?" soft. */
class MelodyGen(val part: String, val notes: String, val unit: Double, val transpose: Int, val vel: Float, val minLoop: Int) : Gen

/** Chords held for their whole length, voiced smoothly between [lo] and [hi]. */
class PadGen(val part: String, val lo: Int, val hi: Int, val vel: Float, val maxNotes: Int) : Gen

/** Chord hits on a 16-step [rhythm] ("x" hit, "-" hold, "." rest), optionally strummed. */
class StabGen(val part: String, val rhythm: String, val lo: Int, val hi: Int, val vel: Float, val strum: Double, val maxNotes: Int) : Gen

/** A bass line from a 16-step rhythm: R root, 5 fifth, 8 octave, 3 third, 7 seventh, L fifth below, r soft root. */
class BassGen(val part: String, val rhythm: String, val lo: Int, val vel: Float) : Gen

/** An arpeggio over the chord tones: digits pick tones counted up from [lo], one token per [unit] steps. */
class ArpGen(val part: String, val pattern: String, val unit: Double, val lo: Int, val vel: Float, val minLoop: Int) : Gen

/**
 * Accompaniment figures (piano, harp, guitar): one token per [unit] steps, repeated over the section. "B" plays the
 * chord's bass note at or above [bassLo], "F" the fifth above it and "O" its octave; digits pick notes of the chord
 * voiced smoothly between [lo] and [hi] (higher digits go on into the next octave); "0+1+2" plays them together;
 * "." rests and "-" holds. A trailing "!" or "?" makes a token louder or softer.
 */
class BrokenGen(
    val part: String, val pattern: String, val unit: Double, val lo: Int, val hi: Int, val bassLo: Int,
    val vel: Float, val maxNotes: Int, val minLoop: Int,
) : Gen

/** NES-style arpeggio: one note that cycles through the chord tones, on a 16-step rhythm. */
class ChipChordGen(val part: String, val rhythm: String, val lo: Int, val vel: Float) : Gen

/** Drums: per-drum 16-step patterns, with optional fills for the last bar (one picked at random each time). */
class DrumGen(
    val part: String,
    val lines: Map<Drum, String>,
    val fills: List<Map<Drum, String>>,
    val crash: Boolean,
    val riser: Boolean,
) : Gen

// --- Building songs -------------------------------------------------------------------------------------------

class SongBuilder(private val id: String, private val title: String, private val mood: String, private val bpm: Double, private val swing: Double) {
    private val parts = ArrayList<Part>()
    private val sections = LinkedHashMap<String, Section>()
    private var form: List<String> = emptyList()
    private var loopFrom = 1
    var mix = Mix()
    var oneShot = false
    var beats = 4
    var rubato = 0.0
    var openVoicing = false

    fun part(name: String, patch: Patch, alt: Patch? = null) { parts += Part(name, patch, alt = alt) }
    fun drums(name: String, kit: Kit) { parts += Part(name, kit = kit) }

    fun section(name: String, bars: Int, chords: String, tempo: Double = 1.0, rit: Double = 0.0, ritBars: Int = 1, body: SectionBuilder.() -> Unit) {
        sections[name] = Section(bars, chords, SectionBuilder().apply(body).gens, tempo, rit, ritBars)
    }

    fun form(vararg names: String, loopFrom: Int = 1) { form = names.toList(); this.loopFrom = loopFrom }

    fun build() = Song(id, title, mood, bpm, swing, parts, sections, form, loopFrom, mix, oneShot, beats, rubato, openVoicing)
}

class SectionBuilder {
    val gens = ArrayList<Gen>()
    fun melody(part: String, notes: String, unit: Double = 2.0, transpose: Int = 0, vel: Float = 0.8f, minLoop: Int = 0) {
        gens += MelodyGen(part, notes, unit, transpose, vel, minLoop)
    }
    fun pad(part: String, lo: Int, hi: Int, vel: Float = 0.7f, maxNotes: Int = 4) { gens += PadGen(part, lo, hi, vel, maxNotes) }
    fun stabs(part: String, rhythm: String, lo: Int, hi: Int, vel: Float = 0.75f, strum: Double = 0.0, maxNotes: Int = 4) {
        gens += StabGen(part, rhythm, lo, hi, vel, strum, maxNotes)
    }
    fun bass(part: String, rhythm: String, lo: Int = 28, vel: Float = 0.85f) { gens += BassGen(part, rhythm, lo, vel) }
    fun arp(part: String, pattern: String, unit: Double = 1.0, lo: Int = 60, vel: Float = 0.6f, minLoop: Int = 0) {
        gens += ArpGen(part, pattern, unit, lo, vel, minLoop)
    }
    fun chipChords(part: String, rhythm: String, lo: Int = 60, vel: Float = 0.6f) { gens += ChipChordGen(part, rhythm, lo, vel) }
    fun broken(part: String, pattern: String, unit: Double = 2.0, lo: Int = 55, hi: Int = 72, bassLo: Int = 36, vel: Float = 0.6f, maxNotes: Int = 4, minLoop: Int = 0) {
        gens += BrokenGen(part, pattern, unit, lo, hi, bassLo, vel, maxNotes, minLoop)
    }
    fun drums(
        part: String = "drums",
        fills: List<Map<Drum, String>> = emptyList(),
        crash: Boolean = false,
        riser: Boolean = false,
        lines: Map<Drum, String>,
    ) { gens += DrumGen(part, lines, fills, crash, riser) }
}

fun song(id: String, title: String, mood: String, bpm: Double, swing: Double = 0.0, body: SongBuilder.() -> Unit): Song =
    SongBuilder(id, title, mood, bpm, swing).apply(body).build()

// --- Compiling --------------------------------------------------------------------------------------------------

object Compiler {
    private class ChordSpan(val start: Double, val len: Double, val chord: Theory.Chord)

    fun compile(song: Song): Timeline {
        val evs = ArrayList<Ev>()
        val partIndex = song.parts.withIndex().associate { it.value.name to it.index }
        val voicings = HashMap<String, IntArray>()
        var groupCount = 0
        val groupVariants = ArrayList<Int>()
        var bar = 0
        var loopStart = 0.0
        val spb = song.stepsPerBar
        val totalSteps = song.bars * spb
        val tempo = FloatArray(totalSteps + 1) { 1f }
        val stepChords = arrayOfNulls<Theory.Chord>(totalSteps)
        val changes = ArrayList<Double>()
        for ((fi, name) in song.form.withIndex()) {
            val sec = song.sections[name] ?: error("No section $name in ${song.id}")
            if (fi == song.loopFrom) loopStart = bar * spb.toDouble()
            val base = bar * spb.toDouble()
            val spans = chordSpans(sec, spb)
            fun chordAt(step: Double): Theory.Chord = (spans.lastOrNull { it.start <= step + 1e-6 } ?: spans.first()).chord
            val total = sec.bars * spb.toDouble()
            for (s in spans) changes += base + s.start
            for (s in 0 until sec.bars * spb) {
                stepChords[bar * spb + s] = chordAt(s.toDouble())
                // Phrase breathing: lean on through the middle of each four-bar phrase, relax into its end.
                val x = ((s / spb) % 4 + (s % spb).toDouble() / spb) / 4
                var m = sec.tempo * (1 + song.rubato * (kotlin.math.sin(Math.PI * x) - 0.5))
                val ritStart = (sec.bars - sec.ritBars) * spb
                if (sec.rit > 0 && s >= ritStart) {
                    val y = (s - ritStart + 1).toDouble() / (sec.ritBars * spb)
                    m *= 1 - sec.rit * y * y
                }
                tempo[bar * spb + s] = m.toFloat()
            }
            for (g in sec.gens) {
                when (g) {
                    is MelodyGen -> {
                        val p = partIndex.getValue(g.part)
                        val toks = tokens(g.notes)
                        var t = 0.0
                        var i = 0
                        var dyn = 1f
                        while (i < toks.size) {
                            val tok = toks[i]
                            // "@0.7": play what follows at 70 % of the melody's velocity (dynamics, swells).
                            if (tok.startsWith("@")) { dyn = tok.substring(1).toFloat(); i++; continue }
                            if (tok != "." && tok != "-") {
                                var len = g.unit
                                var j = i + 1
                                while (j < toks.size && toks[j] == "-") { len += g.unit; j++ }
                                for (n in tok.split('+')) {
                                    val (name0, v) = accent(n, g.vel * dyn)
                                    evs += Ev(base + t, len, p, Theory.midi(name0) + g.transpose, minOf(1f, v * phrasing(song, t, len)), minLoop = g.minLoop)
                                }
                            }
                            t += g.unit; i++
                        }
                        check(t <= total + 1e-6) { "${song.id}/$name: melody for ${g.part} is ${t / spb} bars, section has ${sec.bars}" }
                    }
                    is PadGen -> {
                        val p = partIndex.getValue(g.part)
                        for (s in spans) {
                            val v = Theory.voice(s.chord, g.lo, g.hi, voicings[g.part], g.maxNotes, song.openVoicing)
                            voicings[g.part] = v
                            for (m in v) evs += Ev(base + s.start, s.len, p, m, g.vel)
                        }
                    }
                    is StabGen -> {
                        val p = partIndex.getValue(g.part)
                        forRhythm(g.rhythm, sec.bars, spb) { at, len, _ ->
                            val c = chordAt(at)
                            val v = Theory.voice(c, g.lo, g.hi, voicings[g.part], g.maxNotes, song.openVoicing)
                            voicings[g.part] = v
                            for ((k, m) in v.withIndex()) evs += Ev(base + at, len, p, m, g.vel, nudge = k * g.strum)
                        }
                    }
                    is BassGen -> {
                        val p = partIndex.getValue(g.part)
                        forRhythm(g.rhythm, sec.bars, spb) { at, len, ch ->
                            val c = chordAt(at)
                            val root = Theory.atOrAbove(c.bass, g.lo)
                            val note = when (ch) {
                                'R', 'r' -> root
                                '5' -> Theory.atOrAbove((c.root + c.fifth) % 12, root)
                                '8' -> root + 12
                                '3' -> Theory.atOrAbove((c.root + c.third) % 12, root)
                                '7' -> Theory.atOrAbove((c.root + c.seventh) % 12, root)
                                'L' -> Theory.atOrAbove((c.root + c.fifth) % 12, root - 12)
                                else -> root
                            }
                            evs += Ev(base + at, len, p, note, if (ch == 'r') g.vel * 0.6f else g.vel)
                        }
                    }
                    is ArpGen -> {
                        val p = partIndex.getValue(g.part)
                        val toks = tokens(g.pattern)
                        var t = 0.0
                        var i = 0
                        while (t < total - 1e-6) {
                            val tok = toks[i % toks.size]
                            if (tok != "." && tok != "-") {
                                var len = g.unit
                                var j = i + 1
                                while (toks[j % toks.size] == "-" && len < 16) { len += g.unit; j++ }
                                val c = chordAt(t)
                                val tones = c.tones.distinct().map { Theory.atOrAbove(it, g.lo) }.sorted()
                                val (idxStr, v) = accent(tok, g.vel)
                                val idx = idxStr.toInt()
                                val m = tones[idx % tones.size] + 12 * (idx / tones.size)
                                evs += Ev(base + t, minOf(len, total - t), p, m, v, minLoop = g.minLoop)
                            }
                            t += g.unit; i++
                        }
                    }
                    is BrokenGen -> {
                        val p = partIndex.getValue(g.part)
                        val toks = tokens(g.pattern)
                        var t = 0.0
                        var i = 0
                        var lastChord: Theory.Chord? = null
                        var v = IntArray(0)
                        while (t < total - 1e-6) {
                            val tok = toks[i % toks.size]
                            if (tok != "." && tok != "-") {
                                var len = g.unit
                                var j = i + 1
                                while (toks[j % toks.size] == "-" && t + len < total - 1e-6) { len += g.unit; j++ }
                                val c = chordAt(t)
                                if (c !== lastChord) {
                                    v = Theory.voice(c, g.lo, g.hi, voicings[g.part], g.maxNotes, song.openVoicing)
                                    voicings[g.part] = v
                                    lastChord = c
                                }
                                val (body, vv) = accent(tok, g.vel)
                                val root = Theory.atOrAbove(c.bass, g.bassLo)
                                for (x in body.split('+')) {
                                    val m = when (x) {
                                        "B" -> root
                                        "F" -> Theory.atOrAbove((c.root + c.fifth) % 12, root + 1)
                                        "O" -> root + 12
                                        else -> { val k = x.toInt(); v[k % v.size] + 12 * (k / v.size) }
                                    }
                                    // A pianist leans on the bass and keeps the inner notes of a figure lighter.
                                    val weight = if (x == "B") 1.12f else if (x == "F" || x == "O") 0.95f else 0.88f
                                    evs += Ev(base + t, minOf(len, total - t), p, m, minOf(1f, vv * weight), minLoop = g.minLoop)
                                }
                            }
                            t += g.unit; i++
                        }
                    }
                    is ChipChordGen -> {
                        val p = partIndex.getValue(g.part)
                        forRhythm(g.rhythm, sec.bars, spb) { at, len, _ ->
                            val c = chordAt(at)
                            val tones = c.tones.distinct().take(4).map { Theory.atOrAbove(it, g.lo) }.sorted()
                            evs += Ev(base + at, len, p, tones[0], g.vel, chord = tones.toIntArray())
                        }
                    }
                    is DrumGen -> {
                        val p = partIndex.getValue(g.part)
                        val group = if (g.fills.isNotEmpty()) ++groupCount else 0
                        if (group > 0) groupVariants += g.fills.size + 1
                        for (b in 0 until sec.bars) {
                            val last = b == sec.bars - 1
                            fun put(lines: Map<Drum, String>, variant: Int) {
                                for ((drum, line) in lines) {
                                    for (s in 0 until spb) {
                                        // Lines longer than a bar run across several bars (a triangle every four bars).
                                        val ch = if (line.length > spb) line[(b * spb + s) % line.length] else line.getOrElse(s) { '.' }
                                        val (vel, prob) = when (ch) {
                                            'X' -> 1f to 1f; 'x' -> 0.85f to 1f; 'o' -> 0.6f to 1f; 'g' -> 0.35f to 1f
                                            '?' -> 0.4f to 0.35f; else -> continue
                                        }
                                        evs += Ev(base + b * spb + s, 1.0, p, drum.ordinal, vel, prob, if (last) group else 0, variant)
                                    }
                                }
                            }
                            if (last && group > 0) {
                                // Variant 0 is the plain groove; 1..n are the fills.
                                put(g.lines, 0)
                                g.fills.forEachIndexed { k, f -> put(f, k + 1) }
                            } else put(g.lines, 0)
                        }
                        if (g.crash) evs += Ev(base, spb.toDouble(), p, Drum.Crash.ordinal, 0.8f)
                        if (g.riser) evs += Ev(base + (sec.bars - 1) * spb.toDouble(), spb.toDouble(), p, Drum.Riser.ordinal, 0.7f)
                    }
                }
            }
            bar += sec.bars
        }
        // Fill events in the same group share one draw: the plain groove's events are dropped when a fill is chosen
        // only if the fill has a note on that drum line, handled at play time by the variant check.
        // Sustain pedal: a pedalled part's notes ring on until the pedal is lifted at the next change of harmony.
        changes += bar * spb.toDouble()
        changes.sort()
        for (k in evs.indices) {
            val e = evs[k]
            if (song.parts[e.part].patch?.pedal != true) continue
            val lift = changes.firstOrNull { it > e.start + 0.01 } ?: continue
            if (lift - e.start > e.len) evs[k] = Ev(e.start, lift - e.start, e.part, e.note, e.vel, e.prob, e.group, e.variant, e.minLoop, e.chord, e.nudge)
        }
        evs.sortWith(compareBy({ it.start }, { it.part }))
        val arr = evs.toTypedArray()
        val loopIndex = arr.indexOfFirst { it.start >= loopStart - 1e-6 }.let { if (it < 0) 0 else it }
        val chordArr = Array(totalSteps) { stepChords[it] ?: Theory.chord("C") }
        return Timeline(arr, loopStart, bar * spb.toDouble(), loopIndex, groupVariants.toIntArray(), tempo, chordArr)
    }

    /**
     * Expressive dynamics for songs with rubato: each four-bar phrase swells towards its middle and relaxes at its end,
     * and long notes sing out a little more than quick passing ones.
     */
    private fun phrasing(song: Song, t: Double, len: Double): Float {
        if (song.rubato <= 0) return 1f
        val spb = song.stepsPerBar
        val x = (t / (4.0 * spb)) % 1.0
        val arch = 0.9 + 0.16 * kotlin.math.sin(Math.PI * x)
        val sing = 0.92 + 0.08 * minOf(1.0, len / 6)
        return (arch * sing).toFloat()
    }

    /**
     * Chords of a section, bar by bar ("C | Am7 D7 | G"). Chords in one bar share it equally, unless they are given
     * lengths in beats: "Am7:2 D7:1" in 3/4.
     */
    private fun chordSpans(sec: Section, spb: Int): List<ChordSpan> {
        val bars = sec.chords.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<ChordSpan>()
        for (b in 0 until sec.bars) {
            val names = bars[b % bars.size].split(Regex("\\s+"))
            val beats = names.map { it.substringAfter(':', "").toDoubleOrNull() }
            val given = beats.filterNotNull().sum() * 4
            val free = beats.count { it == null }
            var at = 0.0
            names.forEachIndexed { k, n ->
                val len = beats[k]?.let { it * 4 } ?: ((spb - given) / free)
                out += ChordSpan(b * spb + at, len, Theory.chord(n.substringBefore(':')))
                at += len
            }
        }
        return out
    }

    private fun tokens(s: String): List<String> = s.replace("|", " ").split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun accent(tok: String, vel: Float): Pair<String, Float> = when {
        tok.endsWith("!") -> tok.dropLast(1) to minOf(1f, vel * 1.2f)
        tok.endsWith("?") -> tok.dropLast(1) to vel * 0.6f
        else -> tok to vel
    }

    /** Walks a 16-step rhythm repeated over [bars] bars; hits last until the next non-hold step. */
    private inline fun forRhythm(rhythm: String, bars: Int, spb: Int, hit: (at: Double, len: Double, ch: Char) -> Unit) {
        val r = rhythm.filter { !it.isWhitespace() && it != '|' }
        val n = r.length
        val total = bars * spb
        // Rhythms may span several bars; they repeat as a whole.
        for (s in 0 until total) {
            val ch = r[s % n]
            if (ch == '.' || ch == '-') continue
            var len = 1
            while (s + len < total && r[(s + len) % n] == '-') len++
            hit(s.toDouble(), len.toDouble(), ch)
        }
    }
}
