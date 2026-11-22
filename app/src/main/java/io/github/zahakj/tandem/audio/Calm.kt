package io.github.zahakj.tandem.audio

import io.github.zahakj.tandem.R

/**
 * "Calm": Tandem's default soundtrack, the first music a new user hears. Two short, slow pieces for soft piano,
 * celesta and harp over a warm pad: few notes, long rests, low and gentle, so it sits behind browsing rather than in
 * front of it. Meadow Tales ([Meadow]) is the fuller, orchestral choice.
 *
 * Each piece ends on the chord that leads back to its first, so the loop comes round without a seam.
 */
object Calm {
    val all: List<Song> by lazy { listOf(stillWater(), softMorning()) }

    // tx-ok: song titles are names, the same in every language
    val names: List<Pair<String, String>> = listOf("still-water" to "Still Water", "soft-morning" to "Soft Morning")

    // --- The instruments: soft and far away -----------------------------------------------------------------------

    private fun piano(gain: Float = 0.14f, pan: Float = 0f) =
        Patch(Osc.Piano, gain = gain, pan = pan, attack = 0.002, sustain = 1f, release = 0.6, pedal = true, velSens = 0.8f, reverb = 0.5f)

    private fun celesta(gain: Float = 0.1f, pan: Float = 0.25f) =
        Patch(Osc.Celesta, gain = gain, pan = pan, attack = 0.001, sustain = 1f, release = 1.0, velSens = 0.6f, reverb = 0.6f)

    private fun harp(gain: Float = 0.16f, pan: Float = -0.25f) =
        Patch(Osc.Plucked, gain = gain, pan = pan, bright = 0.35, ring = 0.85, attack = 0.001, sustain = 1f, release = 1.2, velSens = 0.6f, reverb = 0.55f)

    /** A warm, slow-breathing synth pad, filtered dark. */
    private fun pad(gain: Float = 0.05f) =
        Patch(Osc.Triangle, gain = gain, attack = 2.2, decay = 2.0, sustain = 0.85f, release = 2.8, unison = 2, detune = 7.0,
            cutoff = 900.0, keyTrack = 0.3, chorus = 0.35f, reverb = 0.6f, velSens = 0.4f)

    private fun strings(gain: Float = 0.06f) =
        Patch(Osc.Strings, gain = gain, attack = 1.6, decay = 1.5, sustain = 0.9f, release = 1.8, unison = 3, detune = 10.0,
            vibDepth = 0.04, vibRate = 4.6, vibDelay = 0.8, breath = 0.1, bright = 0.35, velSens = 0.6f, reverb = 0.6f)

    private fun room() = Mix(
        hall = true, reverbSize = 0.85, reverbDamp = 0.5, hallDecay = 3.4, preDelayMs = 30.0, reverbReturn = 0.6f,
        echoReturn = 0f, chorusRate = 0.2, chorusReturn = 0.5f, glue = 0.3f, tape = 0.15f, wow = 1.0, humanize = 10.0,
        velJitter = 0.05f, master = 1.0f,
    )

    // --- Still Water: D major, 4/4, piano over a pad, a few celesta notes -----------------------------------------

    private fun stillWater() = song("still-water", "Still Water", R.string.sound_mood_still_water, bpm = 64.0) {
        rubato = 0.04
        openVoicing = true
        part("piano", piano())
        part("celesta", celesta())
        part("pad", pad())
        part("strings", strings())
        mix = room()

        val chordsA = "Dmaj9 | Bm7 | Gmaj9 | Aadd9 | Dmaj9 | F#m7 | Gmaj9 | Asus4 A"
        val ripple = "B . . F 2 . . ."
        val themeA = "| F#5 - - - A5 - - - | D5 - - - - - . . | B4 - - - D5 - E5 - | E5 - - - - - . . " +
            "| F#5 - - - A5 - B5 - | A5 - - - - - . . | G5 - - - F#5 - D5 - | E5 - - - - - . . |"

        section("intro", 4, "Dmaj9 | Gmaj9 | Dmaj9 | Gmaj9") {
            broken("piano", ripple, lo = 57, hi = 72, bassLo = 38, vel = 0.3f)
            pad("pad", 50, 69, vel = 0.5f)
        }
        section("A", 8, chordsA) {
            broken("piano", ripple, lo = 57, hi = 72, bassLo = 38, vel = 0.32f)
            pad("pad", 50, 69, vel = 0.5f)
            melody("piano", themeA, vel = 0.5f)
        }
        section("B", 8, "Gmaj7 | Aadd9 | F#m7 | Bm7 | Gmaj7 | Aadd9 | Bm7 | Asus4 A") {
            broken("piano", "B . . . F . . .", lo = 55, hi = 69, bassLo = 38, vel = 0.3f)
            pad("strings", 54, 71, vel = 0.5f)
            melody("celesta", "| . . . . B5 - - - | C#6 - - - - - . . | A5 - - - - - . . | . . . . F#5 - - - " +
                "| . . . . B5 - - - | C#6 - - - E6 - - - | D6 - - - - - . . | . . . . . . . . |", vel = 0.45f)
        }
        section("A2", 8, chordsA) {
            broken("piano", ripple, lo = 57, hi = 72, bassLo = 38, vel = 0.3f)
            pad("pad", 50, 69, vel = 0.5f)
            melody("piano", themeA, vel = 0.42f)
            melody("celesta", "| . . . . . . . . | D6 - - - - - . . | . . . . . . . . | . . . . . . . . " +
                "| . . . . . . . . | A5 - - - - - . . | . . . . . . . . | . . . . . . . . |", vel = 0.35f)
        }
        form("intro", "A", "B", "A2", loopFrom = 0)
    }

    // --- Soft Morning: F major, 4/4, harp and celesta, the piano low -----------------------------------------------

    private fun softMorning() = song("soft-morning", "Soft Morning", R.string.sound_mood_soft_morning, bpm = 60.0) {
        rubato = 0.04
        openVoicing = true
        part("harp", harp())
        part("celesta", celesta(gain = 0.11f))
        part("piano", piano(gain = 0.13f, pan = 0.1f))
        part("pad", pad(gain = 0.045f))
        mix = room()

        val rolling = "B . 0 . 1 . 2 ."
        section("intro", 4, "Fmaj7 | Bbmaj7 | Fmaj7 | Bbmaj7") {
            broken("harp", rolling, lo = 60, hi = 76, bassLo = 41, vel = 0.32f)
            pad("pad", 53, 70, vel = 0.5f)
        }
        section("A", 8, "Fmaj7 | Am7 | Bbmaj7 | C6 | Dm7 | Am7 | Bbmaj7 | Csus4 C") {
            broken("harp", rolling, lo = 60, hi = 76, bassLo = 41, vel = 0.32f)
            pad("pad", 53, 70, vel = 0.5f)
            melody("celesta", "| C6 - - - A5 - - - | E5 - - - - - . . | D5 - - - F5 - A5 - | G5 - - - - - . . " +
                "| F5 - - - A5 - C6 - | E6 - - - - - C6 - | D6 - - - - - A5 - | G5 - - - - - . . |", vel = 0.45f)
        }
        section("B", 8, "Bbmaj7 | C/Bb | Am7 | Dm7 | Gm7 | C | Fmaj7 | Csus4 C") {
            broken("harp", "B . . . 1 . . .", lo = 60, hi = 76, bassLo = 41, vel = 0.3f)
            pad("pad", 53, 70, vel = 0.55f)
            melody("piano", "| D5 - - - - - . . | E5 - - - G5 - - - | C5 - - - - - . . | A4 - - - - - . . " +
                "| Bb4 - - - D5 - - - | E5 - - - - - . . | A4 - - - C5 - - - | G4 - - - - - . . |", vel = 0.45f)
        }
        form("intro", "A", "B", "A", loopFrom = 0)
    }
}
