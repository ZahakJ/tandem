package io.github.zahakj.tandem.audio

import io.github.zahakj.tandem.R
import io.github.zahakj.tandem.audio.Drum.HatClosed
import io.github.zahakj.tandem.audio.Drum.HatOpen
import io.github.zahakj.tandem.audio.Drum.Kick
import io.github.zahakj.tandem.audio.Drum.Rim
import io.github.zahakj.tandem.audio.Drum.Shaker
import io.github.zahakj.tandem.audio.Drum.Snare
import io.github.zahakj.tandem.audio.Drum.Wood

/**
 * "Plaza": Tandem's default soundtrack. Clean, bright, friendly console-menu music: short whistle-able tunes on a
 * soft square lead, marimba and vibraphone, a plucked bass and brushes, in a small dry room. Every note is written
 * here (original, like the other soundtracks) and played by Tandem's own synthesizer.
 *
 * It follows where you are ([Tracks.forScene]): [plaza] on the home screen, [shop] in Settings, [lounge] while the
 * screensaver is up; [passby] is a short jingle for good news (new games found, an achievement), after which the
 * piece of the moment comes back. Each loop ends on the chord that leads into its first bar, so it comes round
 * without a seam.
 */
object Plaza {
    /** The looping pieces, in the order Next song goes through them. */
    val all: List<Song> by lazy { listOf(plaza(), shop(), lounge()) }

    /** The good-news jingle; plays once. */
    val passby: Song by lazy { passbyJingle() }

    // tx-ok: song titles are names, the same in every language
    val names: List<Pair<String, String>> = listOf("plaza" to "Plaza", "shop" to "Shop", "lounge" to "Lounge", "passby" to "Passby")

    // --- The instruments: small, clean and close ----------------------------------------------------------------

    /** A soft square: hollow and friendly, rounded off so it never buzzes. */
    private fun lead(gain: Float = 0.2f, pan: Float = 0.05f) =
        Patch(Osc.Pulse, gain = gain, pan = pan, pw = 0.5, cutoff = 1500.0, keyTrack = 0.7, resonance = 0.6, attack = 0.008, decay = 0.3,
            sustain = 0.68f, release = 0.12, vibDepth = 0.07, vibRate = 5.4, vibDelay = 0.3, reverb = 0.22f, chorus = 0.12f, velSens = 0.5f)

    /** A whistle an octave over the lead: almost a pure sine, a breath at the start, a little vibrato. */
    private fun whistle(gain: Float = 0.07f, pan: Float = -0.1f) =
        Patch(Osc.Flute, gain = gain, pan = pan, attack = 0.03, decay = 0.3, sustain = 0.85f, release = 0.1, breath = 0.1,
            vibDepth = 0.12, vibRate = 5.6, vibDelay = 0.2, reverb = 0.3f, velSens = 0.5f)

    private fun marimba(gain: Float = 0.17f, pan: Float = -0.2f) =
        Patch(Osc.Marimba, gain = gain, pan = pan, attack = 0.001, sustain = 1f, release = 0.22, velSens = 0.65f, reverb = 0.22f)

    private fun vibes(gain: Float = 0.12f, pan: Float = 0.2f, release: Double = 1.2) =
        Patch(Osc.Vibes, gain = gain, pan = pan, attack = 0.002, sustain = 1f, release = release, velSens = 0.55f, reverb = 0.35f)

    /** Upright bass, plucked: round, short and woody. */
    private fun bass(gain: Float = 0.19f) =
        Patch(Osc.Plucked, gain = gain, pan = 0f, bright = 0.26, ring = 0.42, attack = 0.001, sustain = 1f, release = 0.12, velSens = 0.55f, reverb = 0.08f)

    /** Nylon-string guitar for bossa comping: warm, a soft thumb, a quick strum. */
    private fun nylon(gain: Float = 0.13f, pan: Float = -0.25f) =
        Patch(Osc.Plucked, gain = gain, pan = pan, bright = 0.33, ring = 0.55, attack = 0.001, sustain = 1f, release = 0.18, velSens = 0.6f, reverb = 0.22f)

    private fun epiano(gain: Float = 0.2f, pan: Float = 0.1f) =
        Patch(Osc.EPiano, gain = gain, pan = pan, fmIndex = 1.6, fmDecay = 0.9, attack = 0.002, decay = 1.6, sustain = 0.35f, release = 0.35,
            velSens = 0.6f, reverb = 0.28f, chorus = 0.35f)

    private fun glock(gain: Float = 0.05f, pan: Float = 0.35f) =
        Patch(Osc.Glockenspiel, gain = gain, pan = pan, attack = 0.001, sustain = 1f, release = 0.7, velSens = 0.6f, reverb = 0.4f)

    private fun celesta(gain: Float = 0.12f, pan: Float = -0.2f) =
        Patch(Osc.Celesta, gain = gain, pan = pan, attack = 0.001, sustain = 1f, release = 0.8, velSens = 0.6f, reverb = 0.45f)

    private fun musicBox(gain: Float = 0.17f, pan: Float = 0.15f) =
        Patch(Osc.MusicBox, gain = gain, pan = pan, attack = 0.001, sustain = 1f, release = 1.4, velSens = 0.55f, reverb = 0.45f)

    /** A warm pad far behind everything, for the waltz. */
    private fun pad(gain: Float = 0.035f) =
        Patch(Osc.Triangle, gain = gain, attack = 1.4, decay = 1.5, sustain = 0.85f, release = 1.8, unison = 2, detune = 6.0,
            cutoff = 1100.0, keyTrack = 0.3, chorus = 0.4f, reverb = 0.5f, velSens = 0.4f)

    private fun brushes(gain: Float = 0.3f) = Kit(KitStyle.Orchestra, gain = gain, reverb = 0.18f, trims = mapOf(Kick to 0.45f, HatOpen to 0.6f))

    private fun hands(gain: Float = 0.3f) = Kit(KitStyle.Light, gain = gain, reverb = 0.16f, trims = mapOf(Kick to 0.55f, Shaker to 0.5f, Rim to 0.42f))

    /** A small, bright room: present and clean, no tape, no hall. */
    private fun room(master: Float, size: Double = 0.42, ret: Float = 0.32f) = Mix(
        reverbSize = size, reverbDamp = 0.55, reverbReturn = ret, echoReturn = 0f, chorusRate = 0.3, chorusDepth = 2.0, chorusReturn = 0.4f,
        glue = 0.35f, tape = 0f, wow = 0.0, humanize = 5.0, velJitter = 0.06f, master = master,
    )

    // --- Plaza: the home screen. Bb major, 4/4, a light swing --------------------------------------------------

    private fun plaza() = song("plaza", "Plaza", R.string.sound_mood_plaza, bpm = 108.0, swing = 0.24) {
        part("lead", lead())
        part("whistle", whistle())
        part("marimba", marimba())
        part("mallets", marimba(gain = 0.11f, pan = 0.15f))
        part("vibes", vibes(gain = 0.15f))
        part("vibepad", vibes(gain = 0.05f, pan = 0.25f, release = 1.6))
        part("bass", bass())
        part("glock", glock())
        drums("drums", brushes())
        mix = room(master = 1.1f)

        // The hook: up the scale to the fifth with a little lydian lean-in, then the same on the fourth chord.
        val hook = "| Bb4 C5 D5 F5 - E5 F5 . | Eb5 F5 G5 Bb5 - A5 Bb5 . | C6 - A5 F5 - D5 F5 - | G5 - - - . . . . " +
            "| . G5 G5 G5 F5 Eb5 - D5 | C5 - - - Eb5 - D5 - "
        val openEnd = "| F5 - D5 - B4 - D5 - | C5 - - - . . . . |"
        val homeEnd = "| Eb5 - D5 - C5 - A4 - | Bb4 - - - . . . . |"
        val chordsA = "Bbmaj7 | Ebmaj7 | Dm7 | Gm7 | Cm7 | F7 | Dm7 G7 | Cm7 F7"
        val chordsA2 = "Bbmaj7 | Ebmaj7 | Dm7 | Gm7 | Cm7 | F7 | Cm7 F7 | Bb6"
        // Marimba comping, a Charleston-ish two-bar figure; brushes keep a soft swing.
        val comp = "x-....x-........" + "....x-....x-..x-"
        val swingRide = mapOf(HatClosed to "x...x.o.x...x.o.", Snare to "....x.....g.x...", Kick to "x.......o.......")
        val fills = listOf(
            mapOf(HatClosed to "x...x.o.x.......", Snare to "....x.....x.x.x.", Kick to "x..............."),
            mapOf(HatClosed to "x...x.o.x...x...", Snare to "....x...g.g.x.xx", HatOpen to "..............x."),
        )
        // Little chimes at the ends of phrases.
        val chimes = "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . D6 F6 Bb6 - " +
            "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . |"

        section("intro", 2, "Bbmaj7 | F7sus4") {
            stabs("marimba", comp, lo = 58, hi = 72, vel = 0.55f)
            bass("bass", "R.......5.......", lo = 34, vel = 0.7f)
            drums("drums", lines = mapOf(HatClosed to "x...x.o.x...x.o."))
            melody("glock", "| . . . . . . . . | . . . . F6 - A6 - |", vel = 0.5f)
        }
        section("A", 8, chordsA) {
            melody("lead", hook + openEnd, vel = 0.72f)
            stabs("marimba", comp, lo = 58, hi = 72, vel = 0.5f)
            pad("vibepad", 57, 72, vel = 0.45f)
            bass("bass", "R...5...8...5...", lo = 34, vel = 0.75f)
            drums("drums", fills = fills, lines = swingRide)
            melody("glock", chimes, vel = 0.45f)
        }
        section("A2", 8, chordsA2) {
            melody("lead", hook + homeEnd, vel = 0.72f)
            melody("whistle", hook + homeEnd, transpose = 12, vel = 0.6f)
            stabs("marimba", comp, lo = 58, hi = 72, vel = 0.5f)
            pad("vibepad", 57, 72, vel = 0.45f)
            bass("bass", "R...5...8...5...", lo = 34, vel = 0.75f)
            drums("drums", fills = fills, lines = swingRide)
            melody("glock", "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . G6 Bb6 D7 - " +
                "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . F6 D6 Bb5 - |", vel = 0.42f)
        }
        // The bridge: the vibraphone sings, over the fourth chord turning minor.
        section("B", 8, "Ebmaj7 | Ebm6 | Dm7 | G7 | Cm7 | Am7b5 D7 | Gm7 C7 | Cm7 F7") {
            melody("vibes", "| G5 - - Bb5 - - G5 . | Gb5 - - F5 - - Eb5 . | F5 - - A5 - - C6 . | B5 - - - A5 - G5 - " +
                "| G5 - - Bb5 - - Eb6 . | D6 - - C6 - - A5 . | Bb5 - - - G5 - E5 - | Eb5 - - - D5 - C5 - |", vel = 0.75f)
            melody("lead", "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . " +
                "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . A5 - F5 - |", vel = 0.45f)
            stabs("marimba", "x-......x-......", lo = 55, hi = 69, vel = 0.42f)
            bass("bass", "R.......5.....r.", lo = 34, vel = 0.72f)
            drums("drums", fills = fills, lines = mapOf(HatClosed to "x...x.o.x...x.o.", Snare to "....x.......x..."))
        }
        // The return: the marimba doubles the tune an octave down.
        section("A3", 8, "Bbmaj7 | Ebmaj7 | Dm7 | Gm7 | Cm7 | F7 | Cm7 F7 | Bb6 F7") {
            melody("lead", hook + homeEnd, vel = 0.74f)
            melody("whistle", hook + homeEnd, transpose = 12, vel = 0.55f)
            melody("mallets", hook + homeEnd, transpose = -12, vel = 0.6f)
            stabs("marimba", comp, lo = 58, hi = 72, vel = 0.45f)
            pad("vibepad", 57, 72, vel = 0.45f)
            bass("bass", "R...5...8...5...", lo = 34, vel = 0.75f)
            drums("drums", fills = fills, lines = swingRide)
            melody("glock", chimes, vel = 0.45f)
        }
        form("intro", "A", "A2", "B", "A3")
    }

    // --- Shop: Settings. D major, 4/4, a cozy bossa nova --------------------------------------------------------

    private fun shop() = song("shop", "Shop", R.string.sound_mood_shop, bpm = 126.0) {
        part("epiano", epiano())
        part("vibes", vibes(gain = 0.14f, pan = 0.25f))
        part("guitar", nylon())
        part("bass", bass())
        part("glock", glock(gain = 0.045f))
        drums("hands", hands())
        mix = room(master = 1.0f, size = 0.4, ret = 0.3f)

        // The guitar's two-bar bossa figure (chords on a 3-3-4-3-3 clave), the bass on one and the and of two.
        val bossa = "x-....x-....x-......x-....x-...."
        val bassLine = "R-----5-5-----r-"
        val groove = mapOf(Shaker to "xgogxgogxgogxgog", Rim to "x..x..x...........x...x.........", Kick to "x.......x.......")
        val chordsA = "Dmaj9 | Dmaj9 | Gmaj9 | Gm6 | F#m7 | B7 | Em9 | A13"
        val theme = "| E5 - - F#5 - - A5 - | C#6 - - - - - . . | B5 - - A5 - - F#5 - | Bb5 - - A5 - - G5 - " +
            "| A5 - - - - - E5 - | D#5 - - E5 - - F#5 - "

        section("intro", 2, "Dmaj9 | A13") {
            stabs("guitar", bossa, lo = 54, hi = 69, vel = 0.6f, strum = 0.08)
            bass("bass", bassLine, lo = 38, vel = 0.65f)
            drums("hands", lines = mapOf(Shaker to "xgogxgogxgogxgog"))
        }
        section("A", 8, chordsA) {
            melody("epiano", theme + "| G5 - - - F#5 - - E5 | - - - - G5 - F#5 - |", vel = 0.7f)
            stabs("guitar", bossa, lo = 54, hi = 69, vel = 0.55f, strum = 0.08)
            bass("bass", bassLine, lo = 38, vel = 0.72f)
            drums("hands", lines = groove)
        }
        section("A2", 8, "Dmaj9 | Dmaj9 | Gmaj9 | Gm6 | F#m7 | B7 | Em9 A13 | Dmaj9") {
            melody("epiano", theme + "| G5 - - - E5 - C#5 - | D5 - - - - - . . |", vel = 0.7f)
            melody("vibes", "| . . . . . . . . | E6 - - - - - . . | . . . . . . . . | D6 - - - - - . . " +
                "| . . . . . . . . | A5 - - - - - . . | . . . . . . . . | F#6 - - - - - . . |", vel = 0.4f)
            stabs("guitar", bossa, lo = 54, hi = 69, vel = 0.55f, strum = 0.08)
            bass("bass", bassLine, lo = 38, vel = 0.72f)
            drums("hands", lines = groove)
        }
        section("B", 8, "Gmaj9 | C9 | F#m7 | Bm9 | Em9 | Gm6 | F#m7 B7 | Em9 A13") {
            melody("vibes", "| D6 - - - B5 - A5 - | Bb5 - - - G5 - E5 - | A5 - - - - - C#6 - | - - - - B5 - - - " +
                "| G5 - - - F#5 - B5 - | Bb5 - - - A5 - G5 - | A5 - - - F#5 - D#5 - | E5 - - - G5 - F#5 - |", vel = 0.78f)
            stabs("epiano", "x-------........", lo = 57, hi = 71, vel = 0.32f)
            stabs("guitar", bossa, lo = 54, hi = 69, vel = 0.5f, strum = 0.08)
            bass("bass", bassLine, lo = 38, vel = 0.7f)
            drums("hands", lines = groove)
            melody("glock", "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . " +
                "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . A6 - |", vel = 0.45f)
        }
        section("A3", 8, chordsA) {
            melody("epiano", theme + "| G5 - - - F#5 - - E5 | - - - - G5 - F#5 - |", vel = 0.72f)
            melody("vibes", theme + "| G5 - - - F#5 - - E5 | - - - - G5 - F#5 - |", transpose = 12, vel = 0.28f)
            stabs("guitar", bossa, lo = 54, hi = 69, vel = 0.55f, strum = 0.08)
            bass("bass", bassLine, lo = 38, vel = 0.72f)
            drums("hands", lines = groove)
        }
        form("intro", "A", "A2", "B", "A3")
    }

    // --- Lounge: the screensaver. F major, a slow 3/4 waltz on a music box -----------------------------------------

    private fun lounge() = song("lounge", "Lounge", R.string.sound_mood_lounge, bpm = 88.0) {
        beats = 3
        rubato = 0.03
        openVoicing = true
        part("box", musicBox())
        part("celesta", celesta())
        part("vibes", vibes(gain = 0.12f, pan = -0.15f, release = 1.6))
        part("comp", vibes(gain = 0.05f, pan = 0.2f, release = 0.9))
        part("pad", pad())
        part("bass", bass(gain = 0.17f))
        mix = room(master = 1.62f, size = 0.6, ret = 0.4f)

        val tune = "| C6 - - A5 E6 - | D6 - - - - - | Bb5 - - G5 D6 - | C6 - - - - - " +
            "| A5 - - C6 E6 - | F#6 - - - D6 - | G5 - - Bb5 D6 C6 "
        val waltz = "....x---x---"

        section("intro", 2, "Fmaj7 | C7sus4") {
            stabs("comp", waltz, lo = 57, hi = 72, vel = 0.5f)
            bass("bass", "R-----------", lo = 41, vel = 0.6f)
            pad("pad", 53, 69, vel = 0.5f)
        }
        section("A", 8, "Fmaj7 | Dm7 | Gm7 | C7 | Am7 | D7 | Gm7 | C7sus4:2 C7:1") {
            melody("box", tune + "| Bb5 - - - G5 - |", vel = 0.7f)
            stabs("comp", waltz, lo = 57, hi = 72, vel = 0.5f)
            bass("bass", "R-----------", lo = 41, vel = 0.65f)
            pad("pad", 53, 69, vel = 0.5f)
        }
        section("A2", 8, "Fmaj7 | Dm7 | Gm7 | C7 | Am7 | D7 | Gm7:2 C7:1 | Fmaj7") {
            melody("box", tune + "| A5 - - - - - |", vel = 0.7f)
            melody("celesta", "| A4 - - - C5 - | F5 - - - - - | D5 - - - Bb4 - | E5 - - - - - " +
                "| E5 - - - G5 - | A5 - - - F#5 - | Bb4 - - - E5 - | F5 - - - - - |", vel = 0.5f)
            stabs("comp", waltz, lo = 57, hi = 72, vel = 0.5f)
            bass("bass", "R-------5---", lo = 41, vel = 0.65f)
            pad("pad", 53, 69, vel = 0.5f)
        }
        section("B", 8, "Bbmaj7 | Bbm6 | Am7 | Dm7 | Gm7 | C7 | Am7:2 D7:1 | Gm7:2 C7:1") {
            melody("vibes", "| F5 - - A5 D6 - | Db6 - - - - - | C6 - - E5 G5 - | F5 - - - - - " +
                "| D5 - - F5 Bb5 - | Bb5 - - G5 E5 - | C6 - - - F#5 A5 | Bb5 - - - G5 - |", vel = 0.72f)
            stabs("comp", waltz, lo = 55, hi = 70, vel = 0.42f)
            bass("bass", "R-------5---", lo = 41, vel = 0.62f)
            pad("pad", 53, 69, vel = 0.55f)
        }
        section("A3", 8, "Fmaj7 | Dm7 | Gm7 | C7 | Am7 | D7 | Gm7:2 C7:1 | Fmaj7:2 C7:1") {
            melody("box", tune + "| A5 - - - G5 Bb5 |", vel = 0.7f)
            melody("celesta", "| A4 - - - C5 - | F5 - - - - - | D5 - - - Bb4 - | E5 - - - - - " +
                "| E5 - - - G5 - | A5 - - - F#5 - | Bb4 - - - E5 - | F5 - - - E5 - |", vel = 0.45f)
            stabs("comp", waltz, lo = 57, hi = 72, vel = 0.5f)
            bass("bass", "R-----------", lo = 41, vel = 0.65f)
            pad("pad", 53, 69, vel = 0.5f)
        }
        form("intro", "A", "A2", "B", "A3")
    }

    // --- Passby: good news. C major, a bouncy ten seconds ---------------------------------------------------------

    private fun passbyJingle() = song("passby", "Passby", R.string.sound_mood_passby, bpm = 138.0, swing = 0.16) {
        oneShot = true
        part("lead", lead())
        part("whistle", whistle(gain = 0.065f))
        part("marimba", marimba())
        part("vibes", vibes(gain = 0.06f, release = 1.8))
        part("bass", bass())
        part("glock", glock(gain = 0.06f))
        drums("drums", brushes(0.3f))
        mix = room(master = 1.0f)

        val tune = "| E5 G5 - E5 A5 - G5 . | F5 A5 - F5 C6 - A5 . | G5 - E5 - C#5 - E5 - | F5 - A5 - B5 - D6 - | C6 - - - - - - - |"
        section("run", 1, "G7sus4") {
            melody("marimba", ". . . . . . . . G4 A4 B4 C5 D5 E5 F5 G5", unit = 1.0, vel = 0.55f)
            drums("drums", lines = mapOf(Snare to "........g.g.x.x."))
        }
        section("tune", 5, "C6 | Fmaj7 | Em7 A7 | Dm7 G7 | C6") {
            melody("lead", tune, vel = 0.75f)
            melody("whistle", tune, transpose = 12, vel = 0.5f)
            stabs("marimba", "..x...x...x...x." + "..x...x...x...x." + "..x...x...x...x." + "..x...x...x...x." + "x---------------",
                lo = 60, hi = 72, vel = 0.45f)
            pad("vibes", 55, 71, vel = 0.5f)
            bass("bass", "R...5...R...5..." + "R...5...R...5..." + "R...5...R...5..." + "R...5...R...5..." + "R---------------", lo = 36, vel = 0.75f)
            drums("drums", lines = mapOf(
                HatClosed to "x.x.x.x.x.x.x.x." + "x.x.x.x.x.x.x.x." + "x.x.x.x.x.x.x.x." + "x.x.x.x.x.x.x.x." + "................",
                Snare to "....x.......x..." + "....x.......x..." + "....x.......x..." + "....x...x.x.x.x." + "................",
                Kick to "x.......x......." + "x.......x......." + "x.......x......." + "x.......x......." + "x...............",
                Wood to "................" + "................" + "................" + "................" + "x...............",
            ))
            melody("glock", "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | C6 E6 G6 C7 - - - - |", unit = 2.0, vel = 0.5f)
        }
        form("run", "tune", loopFrom = 0)
    }
}
