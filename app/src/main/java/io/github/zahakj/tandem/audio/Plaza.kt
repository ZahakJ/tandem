package io.github.zahakj.tandem.audio

import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.audio.Drum.HatClosed
import io.github.zahakj.tandem.audio.Drum.Kick
import io.github.zahakj.tandem.audio.Drum.Rim
import io.github.zahakj.tandem.audio.Drum.Shaker
import io.github.zahakj.tandem.audio.Drum.Snare
import io.github.zahakj.tandem.audio.Drum.Wood

/**
 * Tandem's built-in music: three rhythm beds, like the calm idle loop of a console's system menu. Nothing in them is
 * a tune: each is one chord instrument playing the same small rhythm every bar (or every two bars), a bass on the
 * downbeats, and one or two soft percussion layers that never change pattern. The only movement is the chords, a
 * handful held for bars at a time and voiced to share notes, and the little unevenness of a person playing. No
 * melody, no ornaments, no fills, no pickups, nothing that only sometimes plays: you barely notice it, it just feels
 * warm and moving. Every note is written here (original) and played by Tandem's own synthesizer.
 *
 * - [plaza], the default: marimba chords on a Charleston "took took" figure, plucked bass, brushes. Bb, 104 BPM, swung.
 * - [lagoon]: kalimba picking the chords on a slow bossa figure, plucked bass, a rim on a clave, a shaker. Eb, 96 BPM.
 * - [terrace]: a nylon guitar picking each chord in even quarter notes and letting it ring, a round bass, a shaker
 *   and a soft rim. G, 100 BPM.
 *
 * The chosen groove plays everywhere (home, Settings, the screensaver, where it is only quieter). [passby] is a short
 * jingle for good news (new games found, an achievement) shared by all three, after which the groove comes back.
 * Each loop is 16 bars and ends on the chord that leads into its first, so it comes round without a seam.
 */
object Plaza {
    /** The three grooves, Plaza (the default) first: also the order Next song goes through them. */
    val all: List<Song> by lazy { listOf(plaza(), lagoon(), terrace()) }

    /** The good-news jingle; plays once. */
    val passby: Song by lazy { passbyJingle() }

    // --- The instruments: small, clean and close ----------------------------------------------------------------

    /** A soft square: hollow and friendly, rounded off so it never buzzes (the jingle's tune). */
    private fun lead(gain: Float = 0.2f, pan: Float = 0.05f) =
        Patch(Osc.Pulse, gain = gain, pan = pan, pw = 0.5, cutoff = 1500.0, keyTrack = 0.7, resonance = 0.6, attack = 0.008, decay = 0.3,
            sustain = 0.68f, release = 0.12, vibDepth = 0.07, vibRate = 5.4, vibDelay = 0.3, reverb = 0.22f, chorus = 0.12f, velSens = 0.5f)

    private fun marimba(gain: Float = 0.17f, pan: Float = -0.2f) =
        Patch(Osc.Marimba, gain = gain, pan = pan, attack = 0.001, sustain = 1f, release = 0.22, velSens = 0.65f, reverb = 0.22f)

    private fun vibes(gain: Float = 0.12f, pan: Float = 0.2f, release: Double = 1.2) =
        Patch(Osc.Vibes, gain = gain, pan = pan, attack = 0.002, sustain = 1f, release = release, velSens = 0.55f, reverb = 0.35f)

    /** Upright bass, plucked: round, short and woody. */
    private fun bass(gain: Float = 0.19f, bright: Double = 0.26) =
        Patch(Osc.Plucked, gain = gain, pan = 0f, bright = bright, ring = 0.42, attack = 0.001, sustain = 1f, release = 0.12, velSens = 0.55f, reverb = 0.08f)

    /** A round, soft bass: a triangle with a gentle fall, warm enough to carry on small speakers. */
    private fun roundBass(gain: Float = 0.2f) =
        Patch(Osc.Triangle, gain = gain, pan = 0f, attack = 0.004, decay = 0.32, sustain = 0.45f, release = 0.08, cutoff = 900.0, keyTrack = 0.4,
            resonance = 0.6, velSens = 0.5f, reverb = 0.04f)

    /** A kalimba with a soft dotted-eighth echo, for the lagoon. */
    private fun kalimba(gain: Float = 0.16f, pan: Float = -0.12f) =
        Patch(Osc.Kalimba, gain = gain, pan = pan, attack = 0.001, sustain = 1f, release = 0.6, velSens = 0.6f, reverb = 0.38f, echo = 0.22f, chorus = 0.08f)

    /**
     * A fingerpicked nylon guitar: warm, a soft thumb, a touch of chorus, and the notes of a chord left ringing until
     * the harmony moves (the pedal), as a player holding the shape would.
     */
    private fun nylon(gain: Float = 0.13f, pan: Float = -0.15f) =
        Patch(Osc.Plucked, gain = gain, pan = pan, bright = 0.4, ring = 0.7, attack = 0.001, sustain = 1f, release = 0.35, velSens = 0.45f,
            reverb = 0.26f, chorus = 0.25f, pedal = true)

    private fun glock(gain: Float = 0.05f, pan: Float = 0.35f) =
        Patch(Osc.Glockenspiel, gain = gain, pan = pan, attack = 0.001, sustain = 1f, release = 0.7, velSens = 0.6f, reverb = 0.4f)

    private fun brushes(gain: Float = 0.3f) = Kit(KitStyle.Orchestra, gain = gain, reverb = 0.18f, trims = mapOf(Kick to 0.45f))

    private fun hands(gain: Float = 0.3f) = Kit(KitStyle.Light, gain = gain, reverb = 0.16f, trims = mapOf(Kick to 0.55f, Shaker to 0.5f, Rim to 0.42f))

    /** A small, bright room: present and clean, no tape, no hall; played evenly, with only a person's small unevenness. */
    private fun room(master: Float, size: Double = 0.42, ret: Float = 0.32f) = Mix(
        reverbSize = size, reverbDamp = 0.55, reverbReturn = ret, echoReturn = 0f, chorusRate = 0.3, chorusDepth = 2.0, chorusReturn = 0.4f,
        glue = 0.35f, tape = 0f, wow = 0.0, humanize = 5.0, velJitter = 0.04f, master = master,
    )

    // --- Plaza: the default. Bb major, 4/4, a light swing ------------------------------------------------------
    //
    // The soft "took took" of marimba chords on a Charleston-ish two-bar figure, a plucked bass on one and three and
    // brushes ticking on the hi-hat, and nothing else. The chords sway between Bb and a suspended F, then lean to Eb
    // and Gm for a while, two bars each.

    private fun plaza() = song("plaza", "Plaza", R.string.sound_mood_plaza, bpm = 104.0, swing = 0.24) {
        part("marimba", marimba(gain = 0.16f))
        part("bass", bass(gain = 0.17f))
        drums("drums", brushes(0.22f))
        mix = room(master = 1.29f)

        section("A", 16, "Bbmaj7 | Bbmaj7 | F7sus4 | F7sus4 | Bbmaj7 | Bbmaj7 | F7sus4 | F7sus4 | " +
            "Ebmaj7 | Ebmaj7 | Gm7 | Gm7 | Ebmaj7 | Ebmaj7 | F7sus4 | F7sus4") {
            stabs("marimba", "x-....x-........" + "....x-....x-..x-", lo = 58, hi = 72, vel = 0.52f)
            bass("bass", "R.......5.......", lo = 34, vel = 0.72f)
            drums("drums", lines = mapOf(HatClosed to "x...x.o.x...x.o.", Snare to "....g.......g...", Kick to "g..............."))
        }
        form("A", loopFrom = 0)
    }

    // --- Lagoon: dusk by the water. Eb major, 4/4, a slow, dreamy bossa -----------------------------------------
    //
    // A kalimba picks the chord's notes on one two-bar bossa figure, its soft echo carrying it out over the water; a
    // plucked bass on one and three, a rim clicking a slow clave across the two bars, a shaker on the eighths. Four
    // chords, four bars each.

    private fun lagoon() = song("lagoon", "Lagoon", R.string.sound_mood_lagoon, bpm = 96.0) {
        part("kalimba", kalimba(gain = 0.15f))
        part("bass", bass(gain = 0.17f, bright = 0.2))
        drums("hands", hands(0.2f))
        mix = room(master = 1.77f, size = 0.55, ret = 0.36f).copy(echoSteps = 3.0, echoFeedback = 0.28f, echoReturn = 0.4f)

        section("A", 16, "Ebmaj9 | Ebmaj9 | Ebmaj9 | Ebmaj9 | Abmaj7 | Abmaj7 | Abmaj7 | Abmaj7 | " +
            "Fm9 | Fm9 | Fm9 | Fm9 | Bb7sus4 | Bb7sus4 | Bb7sus4 | Bb7sus4") {
            broken("kalimba", "0 . 2 1 . 3 . 2 | . 1 . 0 . 2 . . |", lo = 63, hi = 80, vel = 0.55f)
            bass("bass", "R.......5.......", lo = 39, vel = 0.7f)
            drums("hands", lines = mapOf(
                Rim to "o.....o.....o..." + "....o...o.......",
                Shaker to "g.g.g.g.g.g.g.g.",
                Kick to "g.......g.......",
            ))
        }
        form("A", loopFrom = 0)
    }

    // --- Terrace: a sunny morning. G major, 4/4, a fingerpicked guitar -----------------------------------------
    //
    // A nylon guitar picks each chord in even quarter notes and lets the notes ring until the chord changes, like a
    // player holding the shape; a round bass on one and three, a shaker on the eighths and a soft rim on two and four.
    // Four chords, four bars each, sharing notes from one to the next (G and C run through all of them).

    private fun terrace() = song("terrace", "Terrace", R.string.sound_mood_terrace, bpm = 100.0, swing = 0.1) {
        part("guitar", nylon(gain = 0.13f))
        part("bass", roundBass(gain = 0.19f))
        drums("hands", Kit(KitStyle.Light, gain = 0.2f, reverb = 0.16f, trims = mapOf(Kick to 0.5f, Shaker to 0.4f, Rim to 0.3f)))
        mix = room(master = 1.32f, size = 0.4, ret = 0.3f)

        section("A", 16, "Gmaj7 | Gmaj7 | Gmaj7 | Gmaj7 | Cmaj7 | Cmaj7 | Cmaj7 | Cmaj7 | " +
            "Am7 | Am7 | Am7 | Am7 | D7sus4 | D7sus4 | D7sus4 | D7sus4") {
            broken("guitar", "0 - 2 - 1 - 2 - |", lo = 52, hi = 67, vel = 0.5f)
            bass("bass", "R-------5-------", lo = 43, vel = 0.65f)
            drums("hands", lines = mapOf(Shaker to "g.g.g.g.g.g.g.g.", Rim to "....g.......g...", Kick to "g.......g......."))
        }
        form("A", loopFrom = 0)
    }

    // --- Passby: good news. C major, a bouncy few seconds ---------------------------------------------------------

    private fun passbyJingle() = song("passby", "Passby", R.string.sound_mood_passby, bpm = 138.0, swing = 0.16) {
        oneShot = true
        part("lead", lead(gain = 0.16f))
        part("marimba", marimba(gain = 0.14f))
        part("vibes", vibes(gain = 0.05f, release = 1.8))
        part("bass", bass(gain = 0.17f))
        part("glock", glock(gain = 0.05f))
        drums("drums", brushes(0.22f))
        mix = room(master = 0.85f)

        val tune = "| E5 G5 - E5 A5 - G5 . | F5 A5 - F5 C6 - A5 . | D5 - F5 - G5 - B5 - | C6 - - - - - - - |"
        section("run", 1, "G7sus4") {
            melody("marimba", ". . . . . . . . G4 A4 B4 C5 D5 E5 F5 G5", unit = 1.0, vel = 0.5f)
            drums("drums", lines = mapOf(Snare to "........g.g.x.x."))
        }
        section("tune", 4, "C6 | Fmaj7 | Dm7 G7 | C6") {
            melody("lead", tune, vel = 0.7f)
            stabs("marimba", "..x...x...x...x." + "..x...x...x...x." + "..x...x...x...x." + "x---------------", lo = 60, hi = 72, vel = 0.42f)
            pad("vibes", 55, 71, vel = 0.5f)
            bass("bass", "R...5...R...5..." + "R...5...R...5..." + "R...5...R...5..." + "R---------------", lo = 36, vel = 0.72f)
            drums("drums", lines = mapOf(
                HatClosed to "x.x.x.x.x.x.x.x." + "x.x.x.x.x.x.x.x." + "x.x.x.x.x.x.x.x." + "................",
                Snare to "....x.......x..." + "....x.......x..." + "....x...x.x.x.x." + "................",
                Kick to "x.......x......." + "x.......x......." + "x.......x......." + "x...............",
                Wood to "................" + "................" + "................" + "x...............",
            ))
            melody("glock", "| . . . . . . . . | . . . . . . . . | . . . . . . . . | C6 E6 G6 C7 - - - - |", unit = 2.0, vel = 0.45f)
        }
        form("run", "tune", loopFrom = 0)
    }
}
