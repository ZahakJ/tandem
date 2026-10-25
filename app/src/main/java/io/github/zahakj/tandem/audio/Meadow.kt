package io.github.zahakj.tandem.audio

import io.github.zahakj.tandem.audio.Drum.HatClosed
import io.github.zahakj.tandem.audio.Drum.Kick
import io.github.zahakj.tandem.audio.Drum.Snare
import io.github.zahakj.tandem.audio.Drum.Wood

/**
 * "Meadow Tales": Tandem's default soundtrack. Six original pieces for a small acoustic orchestra (piano, strings,
 * woodwinds, horn, accordion, mandolin, harp, celesta and soft percussion) in a warm, pastoral, storybook style:
 * lyrical melodies, waltzes, added-ninth and major-seventh harmony with Lydian and Dorian colours, and rubato.
 *
 * Every note is written here and played by Tandem's own synthesizer, so like the rest of the app it is GPL and there
 * are no recordings to license. Each piece has an intro, a theme (A), its variation (A'), a contrasting section (B),
 * a bridge, a fuller return and a coda; the loop goes back to the start so the coda leads home naturally.
 */
object Meadow {
    val all: List<Song> by lazy { listOf(hillsideBreeze(), lanternTown(), seasideTrain(), forestSpiritsWaltz(), summerRainWindow(), flyingHome()) }

    val names: List<Pair<String, String>> = listOf(
        "hillside-breeze" to "Hillside Breeze", "lantern-town" to "Lantern Town", "seaside-train" to "Seaside Train",
        "forest-spirits" to "Forest Spirits Waltz", "summer-rain" to "Summer Rain Window", "flying-home" to "Flying Home",
    )

    /** The piece that suits each built-in theme. */
    fun forTheme(themeId: String): String = when (themeId) {
        "crt", "dmg", "mint-soda", "cotton-candy", "kart-rainbow", "indigo-cube", "pocket-atomic" -> "lantern-town"
        "ocean", "blue-boot", "midnight-disc", "neon-arcade" -> "seaside-train"
        "sakura", "forest", "monster-catcher", "dungeon" -> "forest-spirits"
        "paper", "ember", "oled", "high-contrast", "crimson-visor" -> "summer-rain"
        "sunset-arcade", "sunset-racer", "star-shooter" -> "flying-home"
        else -> "hillside-breeze"
    }

    // --- The orchestra --------------------------------------------------------------------------------------------

    private fun piano(gain: Float = 0.26f, pan: Float = 0f, reverb: Float = 0.32f, pedal: Boolean = true) =
        Patch(Osc.Piano, gain = gain, pan = pan, attack = 0.002, sustain = 1f, release = 0.3, pedal = pedal, velSens = 0.75f, reverb = reverb)

    private fun violins(gain: Float = 0.2f, pan: Float = -0.2f, attack: Double = 0.32, bright: Double = 0.65) =
        Patch(Osc.Strings, gain = gain, pan = pan, attack = attack, decay = 1.0, sustain = 0.92f, release = 0.75, unison = 4, detune = 9.0,
            vibDepth = 0.11, vibRate = 5.4, vibDelay = 0.38, breath = 0.22, bright = bright, velSens = 0.75f, reverb = 0.42f)

    private fun stringPad(gain: Float = 0.12f, pan: Float = 0f, attack: Double = 0.9) =
        Patch(Osc.Strings, gain = gain, pan = pan, attack = attack, decay = 1.5, sustain = 0.9f, release = 1.4, unison = 3, detune = 10.0,
            vibDepth = 0.05, vibRate = 4.8, vibDelay = 0.6, breath = 0.12, bright = 0.45, velSens = 0.7f, reverb = 0.5f)

    private fun cello(gain: Float = 0.2f, pan: Float = 0.25f, attack: Double = 0.18) =
        Patch(Osc.Strings, gain = gain, pan = pan, attack = attack, decay = 1.2, sustain = 0.88f, release = 0.6, unison = 3, detune = 7.0,
            vibDepth = 0.09, vibRate = 5.0, vibDelay = 0.4, breath = 0.15, bright = 0.5, velSens = 0.65f, reverb = 0.3f)

    /** Short-bowed strings for running figures. */
    private fun spiccato(gain: Float = 0.12f, pan: Float = 0.15f) =
        Patch(Osc.Strings, gain = gain, pan = pan, attack = 0.025, decay = 0.25, sustain = 0.4f, release = 0.12, unison = 3, detune = 8.0,
            breath = 0.3, bright = 0.55, velSens = 0.7f, reverb = 0.35f)

    private fun flute(gain: Float = 0.22f, pan: Float = 0.15f) =
        Patch(Osc.ConcertFlute, gain = gain, pan = pan, attack = 0.06, decay = 0.4, sustain = 0.88f, release = 0.25, vibDepth = 0.13, vibRate = 5.0,
            vibDelay = 0.3, breath = 0.12, bright = 0.6, bendFrom = -0.15, bendTime = 0.03, reverb = 0.42f)

    private fun clarinet(gain: Float = 0.22f, pan: Float = -0.15f) =
        Patch(Osc.Clarinet, gain = gain, pan = pan, attack = 0.05, decay = 0.5, sustain = 0.86f, release = 0.18, vibDepth = 0.03, vibRate = 4.5,
            vibDelay = 0.5, breath = 0.15, bright = 0.6, reverb = 0.38f)

    private fun oboe(gain: Float = 0.17f, pan: Float = 0.1f) =
        Patch(Osc.Oboe, gain = gain, pan = pan, attack = 0.045, decay = 0.5, sustain = 0.86f, release = 0.15, vibDepth = 0.11, vibRate = 5.3,
            vibDelay = 0.28, breath = 0.12, bright = 0.55, reverb = 0.38f)

    private fun horn(gain: Float = 0.22f, pan: Float = 0.3f) =
        Patch(Osc.Horn, gain = gain, pan = pan, attack = 0.09, decay = 0.6, sustain = 0.88f, release = 0.35, vibDepth = 0.04, vibRate = 4.8,
            vibDelay = 0.5, bright = 0.7, velSens = 0.8f, reverb = 0.5f)

    private fun accordion(gain: Float = 0.17f, pan: Float = -0.1f) =
        Patch(Osc.Accordion, gain = gain, pan = pan, attack = 0.035, decay = 0.4, sustain = 0.9f, release = 0.22, bright = 0.55, velSens = 0.6f,
            reverb = 0.3f)

    private fun celesta(gain: Float = 0.17f, pan: Float = 0.3f) =
        Patch(Osc.Celesta, gain = gain, pan = pan, attack = 0.001, sustain = 1f, release = 0.5, velSens = 0.6f, reverb = 0.45f)

    private fun glock(gain: Float = 0.09f, pan: Float = 0.35f) =
        Patch(Osc.Glockenspiel, gain = gain, pan = pan, attack = 0.001, sustain = 1f, release = 0.8, velSens = 0.6f, reverb = 0.45f)

    private fun harp(gain: Float = 0.22f, pan: Float = -0.3f) =
        Patch(Osc.Plucked, gain = gain, pan = pan, bright = 0.45, ring = 0.8, attack = 0.001, sustain = 1f, release = 0.9, velSens = 0.6f, reverb = 0.45f)

    private fun mandolin(gain: Float = 0.15f, pan: Float = 0.25f, tremolo: Double = 12.5) =
        Patch(Osc.Plucked, gain = gain, pan = pan, bright = 0.62, ring = 0.35, attack = 0.001, sustain = 1f, release = 0.2, tremolo = tremolo,
            velSens = 0.5f, reverb = 0.3f)

    private fun pizz(gain: Float = 0.3f, pan: Float = 0.1f) =
        Patch(Osc.Plucked, gain = gain, pan = pan, bright = 0.3, ring = 0.3, attack = 0.001, sustain = 1f, release = 0.15, velSens = 0.6f, reverb = 0.28f)

    private fun timpani(gain: Float = 0.3f, roll: Boolean = false) =
        Patch(Osc.Timpani, gain = gain, pan = -0.05f, attack = if (roll) 0.6 else 0.002, sustain = 1f, release = 0.6, tremolo = if (roll) 14.0 else 0.0,
            velSens = 0.8f, reverb = 0.35f)

    private fun kit(gain: Float = 0.35f) = Kit(KitStyle.Orchestra, gain = gain, reverb = 0.3f)

    private fun hallMix(master: Float, decay: Double = 2.6, size: Double = 0.75, rain: Double = 0.0) = Mix(
        hall = true, reverbSize = size, reverbDamp = 0.4, hallDecay = decay, preDelayMs = 26.0, reverbReturn = 0.55f,
        echoReturn = 0f, chorusReturn = 0f, glue = 0.5f, tape = 0.22f, wow = 1.5, rain = rain, humanize = 9.0, velJitter = 0.06f, master = master,
    )

    // --- Hillside Breeze: the home theme. F major, 4/4, flute and piano over strings ------------------------------

    private fun hillsideBreeze() = song("hillside-breeze", "Hillside Breeze", "Flute and piano on a sunny, windy hill", bpm = 76.0) {
        rubato = 0.05
        openVoicing = true
        part("piano", piano(gain = 0.15f))
        part("flute", flute(gain = 0.32f))
        part("violins", violins(gain = 0.3f))
        part("clarinet", clarinet(gain = 0.2f))
        part("oboe", oboe(gain = 0.32f))
        part("horn", horn(gain = 0.19f))
        part("pad", stringPad(gain = 0.088f))
        part("cello", cello(gain = 0.17f))
        part("harp", harp(gain = 0.21f))
        part("celesta", celesta(gain = 0.19f))
        part("glock", glock(gain = 0.1f))
        part("timp", timpani(gain = 0.22f, roll = true))
        drums("perc", kit(0.2f))
        mix = hallMix(master = 1.06f)

        val themeA = "| A4 - - C5 G5 - F5 - | E5 - - - D5 - C5 D5 | F5 - - A5 C6 - - A5 | A5 - - - - - G5 F5 " +
            "| G5 - - Bb5 D6 - C6 Bb5 | A5 - - - C#6 - E6 - "
        val endOpen = "| D6 - - C6 B5 - - A5 | G5 - - - F5 - - - |"
        val endHome = "| D6 - - C6 Bb5 - - G5 | F5 - - - - - . . |"
        val chordsA = "Fadd9 | C/E | Dm7 | Bbmaj7 | Gm7 | A7 | Dm7 G7 | Gm7 C7sus4"
        val chordsA2 = "Fadd9 | C/E | Dm7 | Bbmaj7 | Gm7 | A7 | Gm7 C7 | Fadd9"
        val counter = "| F4 - - - G4 - - - | G4 - - - - - - - | A4 - - - - - - - | F4 - - - D4 - - - " +
            "| Bb4 - - - - - - - | C#5 - - - A4 - - - | Bb4 - - - G4 - - - | A4 - - - - - - - |"
        val pianoA = "B F 0 1 2 1 0 1"

        section("intro", 4, "Fmaj7 | G/F | Fmaj7 | G/F") {
            broken("harp", "B F 0 1 2 3 2 1", lo = 60, hi = 76, bassLo = 41, vel = 0.42f)
            pad("pad", 57, 74, vel = 0.4f)
            melody("celesta", "| . . . . . . . . | . . . . . . . . | A5 - - C6 G6 - F6 - | D6 - - - B5 - - - |", vel = 0.5f)
        }
        section("A", 8, chordsA) {
            broken("piano", pianoA, lo = 57, hi = 72, bassLo = 36, vel = 0.42f)
            pad("pad", 55, 72, vel = 0.42f)
            bass("cello", "R-------R-------", lo = 36, vel = 0.5f)
            melody("flute", themeA + endOpen, vel = 0.72f)
        }
        section("A2", 8, chordsA2) {
            broken("piano", pianoA, lo = 57, hi = 72, bassLo = 36, vel = 0.4f)
            pad("pad", 55, 72, vel = 0.4f)
            bass("cello", "R-------5-------", lo = 36, vel = 0.55f)
            melody("violins", themeA + endHome, vel = 0.7f)
            melody("clarinet", counter, vel = 0.6f)
            broken("harp", ". . . . . . 0 1 | 2 . . . . . . . ", lo = 72, hi = 86, vel = 0.35f)
        }
        section("B", 8, "Dm7 | Bbmaj7 | Gm7 | A7sus4 A7 | Dm7 | Bbmaj7 | Ebadd9 | C7sus4 C7") {
            broken("piano", "B - 0+1+2 - F - 0+1+2 -", lo = 55, hi = 70, bassLo = 38, vel = 0.42f)
            pad("pad", 55, 74, vel = 0.5f)
            bass("cello", "R-----------5---", lo = 38, vel = 0.6f)
            melody("oboe", "| A4 - - - E5 - D5 - | D5 - - - F5 - A5 - | G5 - - Bb5 F5 - - - | E5 - D5 - C#5 - - - " +
                "| A5 - - - E6 - D6 - | D6 - - C6 A5 - F5 - | G5 - Bb5 - Eb6 - D6 - | C6 - - - Bb5 - - - |", vel = 0.7f)
            melody("violins", "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . " +
                "| @0.6 A4 - - - E5 - D5 - | D5 - - C5 A4 - F4 - | G4 - Bb4 - Eb5 - D5 - | @0.75 C5 - - - Bb4 - - - |", vel = 0.7f)
            melody("horn", "| F4 - - - - - - - | F4 - - - - - - - | D4 - - - - - - - | E4 - - - C#4 - - - " +
                "| F4 - - - - - - - | F4 - - - D4 - - - | G4 - - - Bb4 - - - | G4 - - - E4 - - - |", vel = 0.6f)
            drums("perc", lines = mapOf(Wood to "x" + ".".repeat(63)))
        }
        section("bridge", 8, "Dbmaj7 | Eb/Db | Cm7 | Fm7 | Bbmaj7 | Gm7 | Ebmaj7 | D7sus4 D7", rit = 0.22, ritBars = 2) {
            val tune = "| F5 - - Ab5 Eb6 - Db6 - | Bb5 - - - G5 - - - | Eb5 - - G5 D6 - C6 - | C6 - - - Ab5 - - - " +
                "| D5 - - F5 C6 - Bb5 - | F5 - - - D5 - - - | G5 - - Bb5 D6 - - - | G5 - - - F#5 - - - |"
            melody("piano", tune, vel = 0.85f)
            melody("celesta", tune, transpose = 12, vel = 0.32f)
            broken("piano", "B . F . 0+1 . F .", lo = 53, hi = 66, bassLo = 36, vel = 0.36f)
            pad("pad", 53, 70, vel = 0.32f)
            melody("timp", "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . " +
                "| . . . . . . . . | . . . . . . . . | . . . . . . . . | @0.4 D3 - - - - - - - |", vel = 0.7f)
            drums("perc", lines = mapOf(), riser = true)
        }
        // The return, a step higher in G major, with everyone playing.
        section("A3", 8, "Gadd9 | D/F# | Em7 | Cmaj7 | Am7 | B7 | Am7 D7 | Gadd9") {
            broken("piano", pianoA, lo = 59, hi = 74, bassLo = 38, vel = 0.5f)
            pad("pad", 57, 76, vel = 0.55f)
            bass("cello", "R-------5-------", lo = 38, vel = 0.65f)
            melody("violins", themeA + endHome, transpose = 2, vel = 0.85f)
            melody("flute", themeA + endHome, transpose = 14, vel = 0.55f)
            melody("horn", counter, transpose = 2 - 12, vel = 0.7f)
            melody("glock", "| B6 - - - - - - - | . . . . . . . . | . . . . . . . . | . . . . . . . . " +
                "| A6 - - - - - - - | . . . . . . . . | . . . . . . . . | . . . . . . . . |", vel = 0.5f)
            drums("perc", lines = mapOf(Wood to "x" + ".".repeat(63), Kick to "x..............."), crash = true)
        }
        section("coda", 7, "Gadd9 | A/G | Em7 | Cmaj7 | Am7 D7sus4 | Gadd9 | Gadd9", rit = 0.4, ritBars = 3) {
            broken("harp", "B F 0 1 2 3 2 1 ".repeat(6) + "B 0 1 2 3 4 - -", lo = 62, hi = 78, bassLo = 43, vel = 0.4f)
            pad("pad", 55, 74, vel = 0.4f)
            bass("cello", "R---------------", lo = 36, vel = 0.45f)
            melody("flute", "| B4 - - D5 A5 - G5 - | E5 - - - C#5 - - - | B4 - - D5 F#5 - E5 - | G5 - - - - - E5 - " +
                "| E5 - - - D5 - - - | B4 - - - - - - - | - - - - - - . . |", vel = 0.6f)
            melody("celesta", "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . " +
                "| . . . . . . . . | . . . . D6 - B6 - | . . . . . . . . |", vel = 0.4f)
        }
        form("intro", "A", "A2", "B", "bridge", "A3", "coda", loopFrom = 0)
    }

    // --- Lantern Town: a little town waltz. D minor and F major, 3/4, accordion and mandolin ---------------------

    private fun lanternTown() = song("lantern-town", "Lantern Town", "An accordion waltz in a lamplit little town", bpm = 132.0) {
        beats = 3
        rubato = 0.06
        openVoicing = true
        part("accordion", accordion(gain = 0.32f))
        part("mandolin", mandolin(gain = 0.31f))
        part("picked", mandolin(gain = 0.42f, pan = -0.2f, tremolo = 0.0))
        part("piano", piano(gain = 0.11f, pedal = false))
        part("bass", pizz(gain = 0.31f))
        part("clarinet", clarinet(gain = 0.3f))
        part("violins", violins(gain = 0.34f))
        part("pad", stringPad(gain = 0.1f))
        part("celesta", celesta(gain = 0.3f))
        part("glock", glock(gain = 0.1f))
        drums("perc", kit(0.45f))
        mix = hallMix(master = 1.12f, decay = 2.0, size = 0.6)

        val oompah = "B - 0+1+2 - 0+1+2 -"
        val groove = mapOf(Snare to "....o...o...", HatClosed to "..g...g...g.")
        val themeA = "| F5 - - E5 D5 E5 | F5 - A5 - - - | D5 - - C5 Bb4 C5 | C#5 - - - A4 - " +
            "| Bb4 - - A4 G4 A4 | Bb4 - G5 - - - | F5 - - E5 F5 A5 | G5 - - - - - " +
            "| F5 - - E5 D5 E5 | F5 - A5 - D6 - | D6 - - C6 Bb5 A5 | Bb5 - - - G5 - " +
            "| E5 - - G5 Bb5 D6 | C#6 - - - A5 G5 "
        val chordsA = "Dm | Dm/C | Bb | A7 | Gm | C7 | F | A7 | Dm | Dm/C | Bb | Gm | Em7b5 | A7"
        val tremoloLine = "| D5 - - - - - | C5 - - - - - | Bb4 - - - - - | A4 - - - - - | G4 - - - - - | G4 - - - - - | A4 - - - - - | A4 - - - - - " +
            "| D5 - - - - - | C5 - - - - - | D5 - - - - - | D5 - - - - - | Bb4 - - - - - | A4 - - - - - "

        section("intro", 8, "Dm | Bb/D | Dm | A7 | Dm | Bb | Gm | A7") {
            broken("piano", oompah, lo = 57, hi = 69, bassLo = 38, vel = 0.4f)
            bass("bass", "R-----------", lo = 38, vel = 0.6f)
            melody("mandolin", "| . . . . . . | . . . . . . | . . . . . . | . . . . . . | A4 - - - - - | Bb4 - - - - - | Bb4 - - - - - | A4 - - - . . |", vel = 0.5f)
            drums("perc", lines = mapOf(HatClosed to "..g...g...g."))
        }
        section("A", 16, "$chordsA | Dm | A7") {
            broken("piano", oompah, lo = 57, hi = 69, bassLo = 38, vel = 0.42f)
            bass("bass", "R-----------", lo = 38, vel = 0.7f)
            melody("accordion", themeA + "| F5 - - E5 D5 - | . . . . C#5 E5 |", vel = 0.72f)
            drums("perc", lines = groove)
        }
        section("A2", 16, "$chordsA | Dm | C7") {
            broken("piano", oompah, lo = 57, hi = 69, bassLo = 38, vel = 0.42f)
            bass("bass", "R-----------", lo = 38, vel = 0.7f)
            melody("accordion", themeA + "| F5 - - E5 D5 - | . . . . . . |", vel = 0.72f)
            melody("mandolin", "$tremoloLine| A4 - - - - - | G4 - - - - - |", vel = 0.55f)
            drums("perc", lines = groove + (Wood to "x" + ".".repeat(47)), fills = emptyList())
        }
        section("B", 16, "F | C/E | Dm | Am | Bb | F/A | Gm7 | C7 | F | C/E | Dm | Bbm6 | F/C | C7 | F | A7") {
            broken("piano", oompah, lo = 57, hi = 70, bassLo = 36, vel = 0.4f)
            bass("bass", "R-----------", lo = 36, vel = 0.7f)
            pad("pad", 53, 69, vel = 0.4f)
            val tune = "| A4 - - F5 - - | E5 - - - D5 C5 | D5 - - A5 - - | - - - - G5 E5 | D5 - - - - F5 | A5 - - - G5 F5 " +
                "| G5 - - F5 - D5 | E5 - - - - - "
            melody("clarinet", tune + "| . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . |", vel = 0.7f)
            melody("violins", "| . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . " +
                "| A4 - - F5 - - | E5 - - - D5 C5 | D5 - - F5 A5 - | Bb5 - - - Db6 - | C6 - - - A5 F5 | E5 - - G5 Bb5 - | A5 - - - - - | - - - . . . |", vel = 0.72f)
            melody("accordion", "| C5 - - - - - | C5 - - - - - | A4 - - - - - | A4 - - - - - | Bb4 - - - - - | A4 - - - - - | Bb4 - - - - - | Bb4 - - - - - " +
                "| C5 - - - - - | C5 - - - - - | A4 - - - - - | Bb4 - - - - - | A4 - - - - - | Bb4 - - - - - | A4 - - - - - | A4 - - - - - |", vel = 0.45f)
            drums("perc", lines = groove)
        }
        section("bridge", 16, "Gm | C7 | F | Dm | Gm | A7 | Dm | Dm | Bb | C | Am | Dm | Gm | Em7b5 | A7 | A7", rit = 0.3, ritBars = 2) {
            broken("piano", "B - 0+1 - 0+1 -", lo = 57, hi = 69, bassLo = 38, vel = 0.34f)
            bass("bass", "R-----------", lo = 38, vel = 0.6f)
            melody("picked", "| G4 . Bb4 . D5 . | E5 . C5 . Bb4 . | A4 . C5 . F5 . | F5 . E5 . D5 . | G4 . Bb4 . D5 . | C#5 . E5 . G5 . | F5 - - - D5 - | A4 - - - - - " +
                "| . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . |", vel = 0.6f)
            melody("celesta", "| . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . " +
                "| D6 - - F6 D6 - | E6 - - G6 E6 - | A5 - - C6 E6 - | D6 - - - - - | Bb5 - - D6 G6 - | E6 - - - D6 - | C#6 - - - - - | - - - . . . |", vel = 0.55f)
            pad("pad", 55, 69, vel = 0.3f)
            drums("perc", lines = mapOf(HatClosed to "..g...g...g."))
        }
        section("A3", 16, "$chordsA | Dm | Dm") {
            broken("piano", oompah, lo = 57, hi = 69, bassLo = 38, vel = 0.46f)
            bass("bass", "R-----------", lo = 38, vel = 0.75f)
            melody("accordion", themeA + "| F5 - - E5 D5 - | . . . . . . |", vel = 0.8f)
            melody("violins", themeA + "| F5 - - E5 D5 - | . . . . . . |", transpose = -12, vel = 0.62f)
            melody("mandolin", "$tremoloLine| A4 - - - - - | A4 - - - - - |", vel = 0.5f)
            melody("glock", "| F6 - - - - - | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . " +
                "| F6 - - - - - | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . |", vel = 0.5f)
            drums("perc", lines = groove + (Wood to "x" + ".".repeat(47)))
        }
        section("coda", 9, "Dm | Bb | Gm6 | A7 | Dm | Bb | A7 | D | D", rit = 0.4, ritBars = 3) {
            broken("piano", "B - 0+1+2 - . . ".repeat(7) + "B - 0+1+2+3 - - - | - - - - - -", lo = 57, hi = 69, bassLo = 38, vel = 0.38f)
            bass("bass", "R-----------", lo = 38, vel = 0.6f)
            melody("accordion", "| F5 - - E5 D5 E5 | F5 - - - - - | E5 - - - D5 - | C#5 - - - - - | F5 - - E5 D5 E5 | F5 - - - D5 - | E5 - - - C#5 - " +
                "| D5 - - - - - | - - - - . . |", vel = 0.62f)
            melody("mandolin", "| . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | F#4 - - - - - | - - - - . . |", vel = 0.45f)
            pad("pad", 54, 69, vel = 0.35f)
        }
        form("intro", "A", "A2", "B", "bridge", "A3", "coda", loopFrom = 0)
    }

    // --- Seaside Train: a slow train along the coast. D major, 4/4, piano ostinato and brushes --------------------

    private fun seasideTrain() = song("seaside-train", "Seaside Train", "A little train along the coast, waves out of the window", bpm = 92.0) {
        rubato = 0.03
        openVoicing = true
        part("piano", piano(gain = 0.127f))
        part("clarinet", clarinet(gain = 0.33f))
        part("flute", flute(gain = 0.31f))
        part("violins", violins(gain = 0.29f))
        part("horn", horn(gain = 0.2f))
        part("pad", stringPad(gain = 0.1f))
        part("bass", pizz(gain = 0.38f))
        part("celesta", celesta(gain = 0.2f))
        part("glock", glock(gain = 0.11f))
        drums("perc", kit(0.3f))
        mix = hallMix(master = 1.15f, decay = 2.2, size = 0.65)

        val train = "B 0 2 1 F 0 2 1"
        val brushes = mapOf(Snare to "....o.......o...", HatClosed to "g.g.g.g.g.g.g.g.", Kick to "g.......g.......")
        val themeA = "| F#5 - - - E5 - D5 - | E5 - - - C#5 - A#4 - | B4 - - - D5 - F#5 - | A5 - - - - - G5 F#5 " +
            "| G5 - - - F#5 - D5 - | E5 - - - G#5 - B5 - "
        val chordsA = "Dadd9 | C#m7b5 F#7 | Bm7 | Bm7/A | Gadd9 | E9"
        val counter = "| A4 - - - - - - - | G4 - - - F#4 - - - | D4 - - - - - - - | F#4 - - - - - - - " +
            "| D4 - - - - - - - | D4 - - - E4 - - - | E4 - - - G4 - - - | F#4 - - - - - - - |"

        section("intro", 4, "Dadd9 | Dadd9 | Gmaj7 | A7sus4") {
            broken("piano", train, lo = 57, hi = 71, bassLo = 38, vel = 0.4f)
            bass("bass", "R.......R.......", lo = 38, vel = 0.5f)
            drums("perc", lines = mapOf(HatClosed to "g.g.g.g.g.g.g.g."))
            melody("celesta", "| . . . . . . . . | . . . . . . A5 B5 | D6 - - - - - . . | . . . . . . . . |", vel = 0.45f)
        }
        section("A", 8, "$chordsA | Em7 | A7sus4 A7") {
            broken("piano", train, lo = 57, hi = 71, bassLo = 38, vel = 0.42f)
            bass("bass", "R.......5.......", lo = 38, vel = 0.65f)
            melody("clarinet", themeA + "| G5 - - - E5 - D5 - | D5 - - - C#5 - - - |", transpose = -12, vel = 0.72f)
            drums("perc", lines = brushes)
        }
        section("A2", 8, "$chordsA | Em7 A7 | Dadd9") {
            broken("piano", train, lo = 57, hi = 71, bassLo = 38, vel = 0.42f)
            bass("bass", "R.......5.......", lo = 38, vel = 0.65f)
            melody("flute", themeA + "| G5 - - E5 - - C#5 - | D5 - - - - - . . |", vel = 0.72f)
            melody("clarinet", counter, vel = 0.55f)
            pad("pad", 54, 69, vel = 0.35f)
            drums("perc", lines = brushes + (Wood to "x" + ".".repeat(63)))
        }
        section("B", 8, "Gmaj7 | A/G | F#m7 | Bm7 | Em7 | A7 | Fmaj7 | A7sus4 A7") {
            broken("piano", "B 0 1 2 F 0 1 2", lo = 57, hi = 71, bassLo = 36, vel = 0.42f)
            bass("bass", "R.......R.......", lo = 36, vel = 0.65f)
            pad("pad", 55, 71, vel = 0.45f)
            melody("violins", "| B5 - - - - - A5 G5 | A5 - - - - - E5 - | F#5 - - - A5 - C#6 - | B5 - - - - - . . " +
                "| G5 - - - F#5 - E5 - | A5 - - - C#6 - E6 - | E6 - - - C6 - A5 - | D6 - - - C#6 - - - |", vel = 0.72f)
            melody("horn", "| D4 - - - - - - - | E4 - - - - - - - | C#4 - - - - - - - | D4 - - - - - - - " +
                "| B3 - - - - - - - | C#4 - - - - - - - | C4 - - - - - - - | D4 - - - C#4 - - - |", vel = 0.6f)
            drums("perc", lines = brushes)
        }
        // The train pulls into a little station: the brushes stop, the piano slows.
        section("bridge", 8, "Bm7 | Gmaj7 | Bm7 | Gmaj7 | Em7 | F#m7 | Gmaj7 | A7sus4", rit = 0.25, ritBars = 2) {
            broken("piano", "B . 0 1 F . 0 1", lo = 57, hi = 71, bassLo = 35, vel = 0.36f)
            bass("bass", "R...............", lo = 35, vel = 0.5f)
            pad("pad", 54, 69, vel = 0.3f)
            melody("clarinet", "| D5 - - - C#5 - B4 - | F#5 - - - - - . . | D5 - - - C#5 - B4 - | A4 - - - - - . . " +
                "| G4 - - B4 E5 - - - | F#5 - - - E5 - C#5 - | D5 - - - B4 - - - | D5 - - - - - - - |", vel = 0.62f)
            melody("celesta", "| . . . . . . F#6 - | . . . . . . . . | . . . . . . D6 - | . . . . . . . . " +
                "| . . . . . . . . | . . . . . . . . | . . . . . . A5 B5 | . . . . . . . . |", vel = 0.4f)
        }
        section("A3", 8, "$chordsA | Em7 A7 | Dadd9") {
            broken("piano", train, lo = 57, hi = 71, bassLo = 38, vel = 0.48f)
            bass("bass", "R.......5.......", lo = 38, vel = 0.7f)
            melody("violins", themeA + "| G5 - - E5 - - C#5 - | D5 - - - - - . . |", vel = 0.8f)
            melody("flute", themeA + "| G5 - - E5 - - C#5 - | D5 - - - - - . . |", transpose = 12, vel = 0.5f)
            melody("horn", counter, vel = 0.65f)
            pad("pad", 55, 71, vel = 0.45f)
            melody("glock", "| F#6 - - - - - - - | . . . . . . . . | . . . . . . . . | . . . . . . . . " +
                "| . . . . . . . . | B6 - - - - - - - | . . . . . . . . | . . . . . . . . |", vel = 0.5f)
            drums("perc", lines = brushes + (Wood to "x" + ".".repeat(63)), crash = true)
        }
        section("coda", 7, "Dadd9 | E/D | Dadd9 | E/D | Gmaj7 | Dadd9 | Dadd9", rit = 0.35, ritBars = 3) {
            broken("piano", "$train ".repeat(5) + "B 0 2 1 F 0 2 1 | B 0 1 2 3 - - -", lo = 57, hi = 71, bassLo = 38, vel = 0.34f)
            bass("bass", "R...............", lo = 38, vel = 0.5f)
            melody("clarinet", "| F#5 - - - E5 - D5 - | E5 - - - - - . . | F#5 - - - E5 - D5 - | G#5 - - - - - . . | B4 - - - - - - - | A4 - - - - - - - | - - - - - - . . |", vel = 0.5f)
            drums("perc", lines = mapOf(HatClosed to "g...g...g...g..."))
        }
        form("intro", "A", "A2", "B", "bridge", "A3", "coda", loopFrom = 0)
    }

    // --- Forest Spirits Waltz: E Dorian and G major, 3/4, pizzicato, celesta and flute ---------------------------

    private fun forestSpiritsWaltz() = song("forest-spirits", "Forest Spirits Waltz", "Little spirits dancing between the old trees", bpm = 144.0) {
        beats = 3
        rubato = 0.06
        openVoicing = true
        part("flute", flute(gain = 0.32f))
        part("clarinet", clarinet(gain = 0.3f))
        part("violins", violins(gain = 0.32f))
        part("celesta", celesta(gain = 0.28f))
        part("harp", harp(gain = 0.15f))
        part("pizz", pizz(gain = 0.18f, pan = 0.2f))
        part("bass", pizz(gain = 0.29f, pan = 0f))
        part("pad", stringPad(gain = 0.09f))
        part("glock", glock(gain = 0.1f))
        drums("perc", kit(0.28f))
        mix = hallMix(master = 1.15f, decay = 2.8, size = 0.8)

        val waltz = "B - 0+1 - 0+1 -"
        val themeA = "| B4 . E5 . G5 . | F#5 - - E5 C#5 - | B4 . E5 . G5 . | A5 - - - E5 - | E5 - - G5 B5 - | A5 - - F#5 D5 - " +
            "| D5 - - - F#5 - | E5 - - - - - | B4 . E5 . G5 . | F#5 - - E5 C#5 - | B4 . D5 . F#5 . | A5 - - - - - " +
            "| B5 - - G5 E5 - | C6 - - - A5 - "
        val chordsA = "Em | A/E | Em | A/E | Cmaj7 | D | Bm7 | Em | Em | A/E | Gmaj7 | D/F# | Cmaj7 | Am7"
        val fluteCounter = "| G5 - - - - - | E5 - - - - - | G5 - - - - - | E5 - - - - - | E5 - - - - - | F#5 - - - - - | F#5 - - - - - | G5 - - - - - " +
            "| G5 - - - - - | E5 - - - - - | F#5 - - - - - | F#5 - - - - - | E5 - - - - - | E5 - - - - - | D#5 - - - - - | E5 - - - - - |"

        section("intro", 8, "Em | A/E | Em | A/E | Cmaj7 | Cmaj7 | B7sus4 | B7") {
            broken("harp", "B 0 1 2 3 2", lo = 59, hi = 74, bassLo = 40, vel = 0.4f)
            melody("celesta", "| . . . . . . | . . . . . . | B5 . E6 . G6 . | F#6 - - E6 C#6 - | . . . . . . | . . . . . . | . . . . . . | D#6 - - - - - |", vel = 0.45f)
            pad("pad", 55, 71, vel = 0.35f)
        }
        section("A", 16, "$chordsA | B7sus4 | B7") {
            broken("pizz", waltz, lo = 59, hi = 71, bassLo = 40, vel = 0.5f)
            bass("bass", "R-----------", lo = 40, vel = 0.65f)
            melody("flute", themeA + "| A5 - - - F#5 E5 | D#5 - - - - - |", vel = 0.7f)
            drums("perc", lines = mapOf(Wood to "x" + ".".repeat(47)), fills = emptyList())
        }
        section("A2", 16, "$chordsA | B7 | Em") {
            broken("pizz", waltz, lo = 59, hi = 71, bassLo = 40, vel = 0.5f)
            bass("bass", "R-----------", lo = 40, vel = 0.65f)
            melody("clarinet", themeA + "| A5 - - - F#5 D#5 | E5 - - - - - |", transpose = -12, vel = 0.72f)
            melody("flute", fluteCounter, vel = 0.5f)
            melody("celesta", "| . . . . . . | . . . . . . | . . . . G6 B6 | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . E6 G6 " +
                "| . . . . . . | . . . . . . | . . . . F#6 A6 | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . |", vel = 0.45f)
            drums("perc", lines = mapOf(Wood to "x" + ".".repeat(47), HatClosed to "....g...g..."))
        }
        section("B", 16, "G | D/F# | Em | Bm/D | C | G/B | Am7 | D7 | G | D/F# | Em | Bm/D | C | Cm6 | G/D | D7") {
            broken("harp", "B 0 1 2 1 0", lo = 59, hi = 74, bassLo = 38, vel = 0.42f)
            bass("bass", "R-----------", lo = 38, vel = 0.65f)
            pad("pad", 55, 71, vel = 0.42f)
            melody("violins", "| D5 - - - - - | - - - - E5 F#5 | G5 - - - - - | F#5 - - - D5 - | E5 - - - G5 - | D5 - - - B4 - | C5 - - - E5 - | F#5 - - - - - " +
                "| D5 - - - - - | - - - - E5 F#5 | G5 - - - B5 - | D6 - - - - - | E6 - - - C6 - | Eb6 - - - C6 - | B5 - - - G5 - | A5 - - - - - |", vel = 0.72f)
            melody("clarinet", "| B4 - - - - - | A4 - - - - - | B4 - - - - - | F#4 - - - - - | G4 - - - - - | G4 - - - - - | A4 - - - - - | A4 - - - - - " +
                "| B4 - - - - - | A4 - - - - - | B4 - - - - - | B4 - - - - - | G4 - - - - - | G4 - - - - - | G4 - - - - - | F#4 - - - - - |", vel = 0.5f)
            drums("perc", lines = mapOf(HatClosed to "....g...g..."))
        }
        section("bridge", 16, "Am | D7 | G | Cmaj7 | F#m7b5 | B7 | Em | Em | Am | D7 | G | Cmaj7 | Am7 | F#m7b5 | B7sus4 | B7", rit = 0.28, ritBars = 2) {
            broken("pizz", waltz, lo = 57, hi = 71, bassLo = 40, vel = 0.42f)
            bass("bass", "R-----------", lo = 40, vel = 0.55f)
            melody("celesta", "| A5 - C6 - E6 - | D6 - - C6 - - | G5 - B5 - D6 - | B5 - - - - - | F#5 - A5 - C6 - | B5 - - A5 - - | G5 - - F#5 E5 - | B4 - - - - - " +
                "| . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . |", vel = 0.6f)
            melody("flute", "| A5 - C6 - E6 - | D6 - - C6 - - | G5 - B5 - D6 - | B5 - - - - - | F#5 - A5 - C6 - | B5 - - A5 - - | G5 - - F#5 E5 - | B4 - - - - - " +
                "| . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . |", vel = 0.4f)
            melody("clarinet", "| . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . " +
                "| C5 - E5 - A5 - | F#5 - - - D5 - | B4 - D5 - F#5 - | E5 - - - - - | C5 - E5 - G5 - | A5 - - - - - | A5 - - - E5 - | D#5 - - - F#5 - |", vel = 0.65f)
            pad("pad", 55, 71, vel = 0.3f)
        }
        section("A3", 16, "$chordsA | B7 | Em") {
            broken("pizz", waltz, lo = 59, hi = 71, bassLo = 40, vel = 0.55f)
            broken("harp", "B 0 1 2 3 2", lo = 59, hi = 74, bassLo = 40, vel = 0.35f)
            bass("bass", "R-----------", lo = 40, vel = 0.7f)
            melody("flute", themeA + "| A5 - - - F#5 D#5 | E5 - - - - - |", vel = 0.75f)
            melody("violins", themeA + "| A5 - - - F#5 D#5 | E5 - - - - - |", transpose = -12, vel = 0.6f)
            pad("pad", 55, 71, vel = 0.4f)
            melody("glock", "| E6 - - - - - | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . " +
                "| E6 - - - - - | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . |", vel = 0.45f)
            drums("perc", lines = mapOf(Wood to "x" + ".".repeat(47), HatClosed to "....g...g..."))
        }
        section("coda", 9, "Em | A/E | Em | A/E | Cmaj7 | B7sus4 | Em | Em | Em", rit = 0.4, ritBars = 3) {
            broken("harp", "B 0 1 2 3 2 ".repeat(7) + "B 0 1 2 3 - | - - - - - -", lo = 59, hi = 74, bassLo = 40, vel = 0.36f)
            bass("bass", "R-----------", lo = 40, vel = 0.5f)
            melody("flute", "| B4 . E5 . G5 . | F#5 - - E5 C#5 - | B4 . E5 . G5 . | A5 - - - E5 - | G5 - - - - - | F#5 - - - E5 - | E5 - - - - - | - - - - - - | - - - - . . |", vel = 0.55f)
            melody("celesta", "| . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | . . . . . . | F#6 - - - - - | . . . . . . |", vel = 0.4f)
            pad("pad", 55, 71, vel = 0.35f)
        }
        form("intro", "A", "A2", "B", "bridge", "A3", "coda", loopFrom = 0)
    }

    // --- Summer Rain Window: piano by a rainy window. Ab major and F minor, 4/4 ----------------------------------

    private fun summerRainWindow() = song("summer-rain", "Summer Rain Window", "Piano and soft rain on a lazy summer afternoon", bpm = 66.0) {
        rubato = 0.07
        openVoicing = true
        part("piano", piano(gain = 0.36f, pan = 0.05f))
        part("pianoL", piano(gain = 0.15f, pan = -0.05f))
        part("oboe", oboe(gain = 0.32f))
        part("violins", violins(gain = 0.32f))
        part("cello", cello(gain = 0.19f))
        part("clarinet", clarinet(gain = 0.2f))
        part("pad", stringPad(gain = 0.09f))
        mix = hallMix(master = 1.14f, decay = 2.8, size = 0.8, rain = 0.35)

        val lh = "B F O F 1 2 1 F"
        val themeA = "| G5 - - Eb5 C5 - - - | Ab5 - - F5 C5 - - - | F5 - - Ab5 C6 - - - | Bb5 - - Ab5 G5 - - - " +
            "| G5 - - Eb5 C5 - Bb4 - | Eb5 - - - C5 - Ab4 - | Db5 - - F5 Eb5 - G5 - "
        val chordsA = "Abmaj7 | Fm7 | Dbmaj7 | Eb7sus4 Eb7 | Abmaj7/C | Fm7 | Bbm7 Eb7"

        section("intro", 4, "Dbmaj7 | Cm7 | Bbm7 | Eb7sus4") {
            broken("pianoL", lh, lo = 56, hi = 70, bassLo = 37, vel = 0.4f)
            melody("piano", "| . . . . . . . . | . . . . . . . . | . . . . F5 - Ab5 - | Bb5 - - - - - - - |", vel = 0.45f)
        }
        section("A", 8, "$chordsA | Abadd9") {
            broken("pianoL", lh, lo = 56, hi = 70, bassLo = 37, vel = 0.4f)
            melody("piano", themeA + "| Ab5 - - - - - . . |", vel = 0.6f)
            pad("pad", 60, 74, vel = 0.3f)
        }
        section("A2", 8, "$chordsA | Abadd9 C7") {
            broken("pianoL", lh, lo = 56, hi = 70, bassLo = 37, vel = 0.38f)
            melody("oboe", themeA + "| Ab5 - - - G5 - E5 - |", vel = 0.68f)
            bass("cello", "R-------5-------", lo = 37, vel = 0.5f)
            pad("pad", 60, 74, vel = 0.32f)
        }
        section("B", 8, "Fm7 | Dbmaj7 | Bbm7 | C7sus4 C7 | Fm7 | Dbmaj7 | Gm7b5 C7 | Fm7 Eb7sus4") {
            broken("pianoL", "B F O 1 2 3 2 1", lo = 56, hi = 72, bassLo = 36, vel = 0.42f)
            melody("violins", "| C5 - - - Ab5 - G5 - | F5 - - - - - C5 - | Db5 - - F5 C6 - Bb5 - | F5 - - - E5 - - - " +
                "| C6 - - - Ab5 - G5 - | Ab5 - - - F5 - C6 - | Bb5 - Db6 - C6 - G5 - | Ab5 - - - - - . . |", vel = 0.72f)
            melody("cello", "| F3 - - - - - - - | F3 - - - - - - - | F3 - - - - - - - | G3 - - - - - - - " +
                "| Ab3 - - - - - - - | Ab3 - - - - - - - | Bb3 - - - Bb3 - - - | C4 - - - Bb3 - - - |", vel = 0.62f)
            pad("pad", 60, 75, vel = 0.42f)
        }
        section("bridge", 4, "Dbmaj7 | Cm7 | Bbm7 | Eb7sus4", rit = 0.3, ritBars = 2) {
            broken("pianoL", "B . F . 1 . 2 .", lo = 56, hi = 70, bassLo = 37, vel = 0.34f)
            melody("piano", "| F5 - - Ab5 C6 - - - | G5 - - Eb5 C5 - - - | Db5 - - F5 C6 - - - | Bb5 - - - Ab5 - - - |", vel = 0.5f)
        }
        section("A3", 8, "$chordsA | Abadd9") {
            broken("pianoL", lh, lo = 56, hi = 70, bassLo = 37, vel = 0.42f)
            melody("piano", themeA + "| Ab5 - - - - - . . |", vel = 0.66f)
            melody("violins", themeA + "| Ab5 - - - - - . . |", transpose = -12, vel = 0.5f)
            melody("clarinet", "| Eb4 - - - - - - - | C4 - - - - - - - | Ab4 - - - - - - - | Bb4 - - - - - - - " +
                "| G4 - - - - - - - | Ab4 - - - - - - - | F4 - - - G4 - - - | Eb4 - - - - - - - |", vel = 0.5f)
            bass("cello", "R-------5-------", lo = 37, vel = 0.5f)
            pad("pad", 60, 74, vel = 0.35f)
        }
        section("coda", 5, "Dbmaj7 | Abmaj7/C | Bbm7 Eb7sus4 | Abadd9 | Abadd9", rit = 0.4, ritBars = 3) {
            broken("pianoL", "B F O F 1 2 1 F ".repeat(3) + "B F O 1 2 3 - - | - - - - - - - -", lo = 56, hi = 70, bassLo = 37, vel = 0.34f)
            melody("piano", "| F5 - - Ab5 C6 - - - | G5 - - Eb5 C5 - - - | Db5 - - F5 Eb5 - - - | C5 - - - - - - - | - - - - - - - - |", vel = 0.5f)
            pad("pad", 60, 74, vel = 0.3f)
        }
        form("intro", "A", "A2", "B", "bridge", "A3", "coda", loopFrom = 0)
    }

    // --- Flying Home: soaring over the fields. Bb major rising to C major, 4/4, horns and full strings ------------

    private fun flyingHome() = song("flying-home", "Flying Home", "Soaring over the fields on the way home", bpm = 100.0) {
        rubato = 0.04
        openVoicing = true
        part("horn", horn(gain = 0.285f))
        part("violins", violins(gain = 0.29f))
        part("flute", flute(gain = 0.3f))
        part("runs", spiccato(gain = 0.27f))
        part("pad", stringPad(gain = 0.09f))
        part("cello", cello(gain = 0.19f))
        part("harp", harp(gain = 0.17f))
        part("piano", piano(gain = 0.1f))
        part("glock", glock(gain = 0.1f))
        part("timp", timpani(gain = 0.38f))
        part("roll", timpani(gain = 0.22f, roll = true))
        drums("perc", kit(0.18f))
        mix = hallMix(master = 1.08f, decay = 2.6, size = 0.8)

        val runs = "0 1 2 1 0 1 2 1"
        val themeA = "| D5 - - Eb5 F5 - Bb5 - | A5 - - - F5 - C5 - | Bb4 - - C5 D5 - G5 - | G5 - - - F5 - Eb5 - " +
            "| Gb5 - - - - - F5 Eb5 | D5 - - - F5 - Bb5 - "
        val chordsA = "Bb | F/A | Gm7 | Ebadd9 | Ebm6 | Bb/D"

        section("intro", 4, "Bbadd9 | Bbadd9 | Gm7 | F7sus4") {
            broken("runs", runs, lo = 58, hi = 72, vel = 0.4f)
            pad("pad", 55, 72, vel = 0.4f)
            melody("roll", "| Bb2 - - - - - - - | - - - - - - - - | . . . . . . . . | F2 - - - - - - - |", vel = 0.45f)
            broken("harp", ". ".repeat(56) + "0 1 2 3 4 5 6 7", unit = 1.0, lo = 60, hi = 76, vel = 0.4f)
            drums("perc", lines = mapOf(), riser = true)
        }
        section("A", 8, "$chordsA | Cm7 | F7sus4 F7") {
            broken("runs", runs, lo = 58, hi = 72, vel = 0.45f)
            pad("pad", 55, 72, vel = 0.4f)
            bass("cello", "R-------R-------", lo = 34, vel = 0.6f)
            melody("horn", themeA + "| C6 - - - Bb5 - G5 - | Bb5 - - - A5 - - - |", transpose = -12, vel = 0.75f)
            melody("timp", "| Bb2 - - - . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | F2 - - - . . . . |", vel = 0.5f)
            drums("perc", lines = mapOf(), crash = true)
        }
        section("A2", 8, "$chordsA | Cm7 F7 | Bb") {
            broken("runs", runs, lo = 58, hi = 72, vel = 0.48f)
            broken("piano", "B . 0+1+2 . F . 0+1+2 .", lo = 58, hi = 72, bassLo = 34, vel = 0.4f)
            bass("cello", "R-------5-------", lo = 34, vel = 0.65f)
            melody("violins", themeA + "| C6 - - - A5 - F5 - | Bb5 - - - - - . . |", vel = 0.78f)
            melody("horn", "| F4 - - - - - - - | F4 - - - - - - - | D4 - - - - - - - | Eb4 - - - - - - - " +
                "| Eb4 - - - - - - - | F4 - - - - - - - | G4 - - - Eb4 - - - | D4 - - - - - - - |", vel = 0.6f)
        }
        section("B", 8, "Gm | Ebmaj7 | Cm7 | D7sus4 D7 | Gm | Ebmaj7 | Abadd9 | F7sus4 F7") {
            broken("harp", "B 0 1 2 3 2 1 0", lo = 58, hi = 74, bassLo = 43, vel = 0.4f)
            broken("runs", runs, lo = 58, hi = 72, vel = 0.42f)
            bass("cello", "R-------R-------", lo = 36, vel = 0.62f)
            pad("pad", 55, 74, vel = 0.45f)
            melody("flute", "| D6 - - - Bb5 - G5 - | G5 - - - - - Bb5 - | C6 - - - Eb6 - G5 - | G5 - - - F#5 - - - " +
                "| D6 - - - Bb5 - G5 - | G5 - - Bb5 D6 - Eb6 - | Eb6 - - - C6 - Ab5 - | C6 - - - A5 - - - |", vel = 0.72f)
            melody("violins", "| Bb4 - - - - - - - | Bb4 - - - - - - - | G4 - - - - - - - | A4 - - - - - - - " +
                "| Bb4 - - - - - - - | Bb4 - - - - - - - | C5 - - - - - - - | Eb5 - - - - - - - |", vel = 0.55f)
            melody("timp", "| G2 - - - . . . . | . . . . . . . . | . . . . . . . . | D3 - - - . . . . | G2 - - - . . . . | . . . . . . . . | . . . . . . . . | F2 - - - . . . . |", vel = 0.45f)
        }
        // Above the clouds: quiet, then the timpani and cymbal lift everything into C major.
        section("bridge", 8, "Ebmaj7 | Dm7 | Cm7 | F/Eb | Dm7 | Gm7 | Abmaj7 | G7sus4 G7", rit = 0.15, ritBars = 2) {
            broken("harp", "B 0 1 2 3 2 1 0", lo = 58, hi = 74, bassLo = 39, vel = 0.36f)
            pad("pad", 53, 72, vel = 0.4f)
            bass("cello", "R---------------", lo = 36, vel = 0.5f)
            melody("horn", "| G4 - - - Bb4 - D5 - | F5 - - - - - . . | Eb5 - - - G5 - Bb5 - | A5 - - - - - . . " +
                "| F5 - - - A5 - C6 - | D6 - - - - - Bb5 - | C6 - - - - - G5 - | C6 - - - B5 - - - |", transpose = -12, vel = 0.62f)
            melody("glock", "| . . . . . . . . | . . . . . . A6 - | . . . . . . . . | . . . . . . A6 - | . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . |", vel = 0.4f)
            melody("roll", "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | G2 - - - - - - - |", vel = 0.6f)
            drums("perc", lines = mapOf(), riser = true)
        }
        section("A3", 8, "C | G/B | Am7 | Fadd9 | Fm6 | C/E | Dm7 G7 | C") {
            broken("runs", runs, lo = 60, hi = 74, vel = 0.52f)
            broken("piano", "B . 0+1+2 . F . 0+1+2 .", lo = 60, hi = 74, bassLo = 36, vel = 0.45f)
            pad("pad", 57, 76, vel = 0.5f)
            bass("cello", "R-------5-------", lo = 36, vel = 0.7f)
            melody("violins", themeA + "| C6 - - - A5 - F5 - | Bb5 - - - - - . . |", transpose = 2, vel = 0.88f)
            melody("flute", themeA + "| C6 - - - A5 - F5 - | Bb5 - - - - - . . |", transpose = 14, vel = 0.55f)
            melody("horn", "| F4 - - - - - - - | F4 - - - - - - - | D4 - - - - - - - | Eb4 - - - - - - - " +
                "| Eb4 - - - - - - - | F4 - - - - - - - | G4 - - - Eb4 - - - | D4 - - - - - - - |", transpose = 2, vel = 0.75f)
            melody("timp", "| C3 - - - . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . G2 - - - | C3 - - - . . . . |", vel = 0.55f)
            melody("glock", "| E6 - - - - - - - | . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | E6 - - - - - - - | . . . . . . . . | . . . . . . . . |", vel = 0.5f)
            drums("perc", lines = mapOf(Kick to "x..............."), crash = true)
        }
        section("coda", 7, "Fmaj7 | Em7 | Dm7 | G7sus4 | Cadd9 | Cadd9 | Cadd9", rit = 0.35, ritBars = 3) {
            broken("harp", "B 0 1 2 3 2 1 0 ".repeat(5) + "B 0 1 2 3 4 5 6 | - - - - - - - -", lo = 60, hi = 76, bassLo = 41, vel = 0.36f)
            pad("pad", 55, 74, vel = 0.42f)
            bass("cello", "R---------------", lo = 36, vel = 0.5f)
            melody("flute", "| E5 - - F5 G5 - C6 - | B5 - - - - - G5 - | A5 - - - F5 - D5 - | F5 - - - - - D5 - | E5 - - - - - - - | - - - - - - - - | - - - - . . . . |", vel = 0.58f)
            melody("roll", "| . . . . . . . . | . . . . . . . . | . . . . . . . . | . . . . . . . . | C3 - - - - - - - | - - - - . . . . | . . . . . . . . |", vel = 0.3f)
        }
        form("intro", "A", "A2", "B", "bridge", "A3", "coda", loopFrom = 0)
    }
}
