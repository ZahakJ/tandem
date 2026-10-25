package io.github.zahakj.tandem.audio

import io.github.zahakj.tandem.audio.Drum.Clap
import io.github.zahakj.tandem.audio.Drum.HatClosed
import io.github.zahakj.tandem.audio.Drum.HatOpen
import io.github.zahakj.tandem.audio.Drum.Kick
import io.github.zahakj.tandem.audio.Drum.Rim
import io.github.zahakj.tandem.audio.Drum.Shaker
import io.github.zahakj.tandem.audio.Drum.Snare
import io.github.zahakj.tandem.audio.Drum.TomHigh
import io.github.zahakj.tandem.audio.Drum.TomLow
import io.github.zahakj.tandem.audio.Drum.Wood

/**
 * Tandem's original soundtracks. Every note is written here and played by the synthesizer, so the music is GPL like
 * the rest of the app and there are no audio files to license.
 *
 * Two packs: "Meadow Tales" (the default, acoustic and orchestral, see [Meadow]) and "Pastel Arcade" (the synth,
 * lo-fi and chiptune pieces in this file). In Pastel Arcade melodies use chord tones on strong beats and scale steps
 * between them; each song has A and B sections, variations on repeats, drum fills and a turnaround so the loop point
 * is seamless.
 */
object Tracks {
    val classic: List<Song> by lazy { listOf(pastelNight(), starlightArcade(), sakuraBreeze(), rainyLibrary(), synthwaveDrive(), pipsJam()) }
    val all: List<Song> by lazy { Meadow.all + classic }

    fun byId(id: String): Song? = all.firstOrNull { it.id == id }

    /** Soundtrack packs: id to name. */
    val packs: List<Pair<String, String>> = listOf("meadow" to "Meadow Tales", "classic" to "Pastel Arcade")

    private val classicNames: List<Pair<String, String>> = listOf(
        "pastel-night" to "Pastel Night", "starlight-arcade" to "Starlight Arcade", "sakura-breeze" to "Sakura Breeze",
        "rainy-library" to "Rainy Library", "synthwave-drive" to "Synthwave Drive", "pips-jam" to "Pip's Jam",
    )

    /** Track ids with friendly names in [pack], for settings. */
    fun names(pack: String): List<Pair<String, String>> = if (pack == "classic") classicNames else Meadow.names

    /** Every track of every pack. */
    val names: List<Pair<String, String>> get() = Meadow.names + classicNames

    /** The track of [pack] that suits each built-in theme. */
    fun forTheme(themeId: String, pack: String): String = if (pack == "classic") classicForTheme(themeId) else Meadow.forTheme(themeId)

    private fun classicForTheme(themeId: String): String = when (themeId) {
        "pastel-night", "lavender-dream", "oled", "high-contrast", "ocean" -> "pastel-night"
        "crt", "dmg" -> "starlight-arcade"
        "sakura", "forest" -> "sakura-breeze"
        "paper", "ember" -> "rainy-library"
        "sunset-arcade" -> "synthwave-drive"
        "mint-soda", "cotton-candy" -> "pips-jam"
        else -> "pastel-night"
    }

    // --- Pastel Night: dreamy lo-fi in F major, the home theme -------------------------------------------------

    private fun pastelNight() = song("pastel-night", "Pastel Night", "Dreamy lo-fi under a candy-coloured night sky", bpm = 78.0, swing = 0.16) {
        part("keys", Patch(Osc.EPiano, gain = 0.16f, pan = -0.1f, attack = 0.003, decay = 1.8, sustain = 0.22f, release = 0.5,
            fmIndex = 1.6, fmDecay = 0.9, reverb = 0.25f, chorus = 0.55f, duck = 0.25f))
        part("pad", Patch(Osc.Saw, gain = 0.06f, unison = 3, detune = 9.0, cutoff = 900.0, resonance = 0.6, attack = 0.9, decay = 1.0,
            sustain = 0.85f, release = 1.6, reverb = 0.5f, chorus = 0.6f, duck = 0.45f))
        part("bass", Patch(Osc.Triangle, gain = 0.3f, cutoff = 520.0, attack = 0.006, decay = 0.5, sustain = 0.65f, release = 0.12,
            velSens = 0.4f, duck = 0.15f, reverb = 0f))
        part(
            "lead",
            Patch(Osc.Flute, gain = 0.22f, pan = 0.1f, breath = 0.18, attack = 0.035, decay = 0.5, sustain = 0.75f, release = 0.35,
                vibDepth = 0.12, vibRate = 5.0, vibDelay = 0.3, bendFrom = -0.5, bendTime = 0.04, reverb = 0.4f, echo = 0.22f),
            alt = Patch(Osc.Fm, gain = 0.2f, pan = 0.1f, fmRatio = 4.0, fmIndex = 2.0, fmDecay = 0.25, attack = 0.002, decay = 1.2,
                sustain = 0f, release = 0.8, reverb = 0.45f, echo = 0.25f),
        )
        part("pluck", Patch(Osc.Pluck, gain = 0.17f, pan = 0.3f, bright = 0.5, ring = 0.4, attack = 0.001, sustain = 1f, release = 0.3,
            reverb = 0.35f, echo = 0.3f))
        drums("drums", Kit(KitStyle.LoFi, gain = 0.55f, reverb = 0.12f, trims = mapOf(HatClosed to 0.55f, Snare to 0.8f, Rim to 0.7f)))
        mix = Mix(reverbSize = 0.65, reverbDamp = 0.6, echoSteps = 3.0, echoFeedback = 0.38f, crackle = 0.5, wow = 6.0, lofi = 7500.0, humanize = 8.0, master = 1.16f)

        val groove = mapOf(Kick to "x......x..x.....", Snare to "....x.......x...", HatClosed to "x.o.x.o.x.o.x.oo", Rim to "..........?.....")
        val fills = listOf(
            mapOf(Kick to "x......x..x.....", Snare to "....x.......x.gx", HatClosed to "x.o.x.o.x.o.x..."),
            mapOf(Kick to "x......x..x...x.", Snare to "....x......gx..g", HatClosed to "x.o.x.o.x.o.o.o."),
            mapOf(Kick to "x...............", Snare to "....x...........", HatClosed to "x.o.x.o.x.o.x...", HatOpen to "..............x."),
        )
        val chordsA = "Gm9 | C9 | Fmaj9 | Dm9 | Bbmaj7 | Am7 | Gm7 | C7sus4 C7"
        val chordsB = "Bbmaj9 | C6 | Am7 | Dm9 | Bbmaj9 | C6 | Dbmaj7 | C7sus4"
        section("intro", 4, "Fmaj9 | Dm9 | Gm9 | C7sus4") {
            stabs("keys", "x-------..x-----", 53, 72, vel = 0.6f, strum = 0.12)
            pad("pad", 57, 76, vel = 0.5f)
            bass("bass", "R-------........", vel = 0.65f)
            melody("pluck", "| . . . . . . . . | . . . . . . . . | . D6 F6 A6 - - G6 F6 | F6 - - - . . . . |", vel = 0.6f)
        }
        section("A", 8, chordsA) {
            stabs("keys", "x-----x---..x---", 53, 72, vel = 0.62f, strum = 0.1)
            pad("pad", 55, 74, vel = 0.45f)
            bass("bass", "R-------..R-5---")
            melody("lead", "| . D5 F5 A5 - - G5 F5 | E5 - - - . . D5 E5 | . C5 E5 G5 - - F5 E5 | D5 - - - . . C5 D5 " +
                "| F5 - - D5 - - C5 D5 | E5 - - C5 - - A4 C5 | D5 - - Bb4 - - A4 G4 | F4 - - - E4 - - - |")
            drums(fills = fills, lines = groove)
        }
        section("A2", 8, chordsA) {
            stabs("keys", "x-----x---..x---", 53, 72, vel = 0.62f, strum = 0.1)
            pad("pad", 55, 74, vel = 0.45f)
            bass("bass", "R-------..R-5---")
            melody("lead", "| . D5 F5 A5 - - Bb5 A5 | G5 - - E5 - - D5 C5 | . C5 E5 G5 - - A5 G5 | F5 - - - E5 D5 C5 D5 " +
                "| F5 - - D5 - - C5 D5 | E5 - - C5 - - A4 C5 | D5 - F5 - A5 - G5 F5 | F5 - - - E5 - - - |")
            arp("pluck", ". 2 . 4 . 3 . 5", unit = 2.0, lo = 84, vel = 0.42f)
            drums(fills = fills, lines = groove)
        }
        section("B", 8, chordsB) {
            stabs("keys", "x-------x-------", 53, 72, vel = 0.58f, strum = 0.14)
            pad("pad", 57, 77, vel = 0.55f)
            bass("bass", "R-------..R-5---")
            melody("lead", "| D5 - F5 - A5 - - - | G5 - - A5 - - G5 E5 | E5 - - - - . C5 D5 | F5 - - E5 - - D5 - " +
                "| D5 - F5 - A5 - C6 - | A5 - - - G5 - - E5 | F5 - - Ab5 - - C6 - | - - - - . . . . |")
            arp("pluck", ". 2 . 4 . 3 . 5", unit = 2.0, lo = 84, vel = 0.4f)
            drums(fills = fills, lines = groove + (Shaker to "..x...x...x...x."))
        }
        section("A3", 8, chordsA) {
            stabs("keys", "x-----x---..x---", 53, 72, vel = 0.62f, strum = 0.1)
            pad("pad", 55, 74, vel = 0.45f)
            bass("bass", "R-------..R-5---")
            melody("lead", "| . D5 F5 A5 - - G5 F5 | E5 - - - . . D5 E5 | . C5 E5 G5 - - F5 E5 | D5 - - - . . C5 D5 " +
                "| F5 - - D5 - - C5 D5 | E5 - - C5 - - A4 C5 | D5 - - Bb4 - - A4 G4 | F4 - - - E4 - - - |")
            arp("pluck", ". 4 . 2 . 3 . 1", unit = 2.0, lo = 84, vel = 0.38f, minLoop = 1)
            drums(fills = fills, lines = groove)
        }
        section("outro", 4, "Gm9 | C9 | Fmaj9 | Fmaj9") {
            stabs("keys", "x-------........", 53, 72, vel = 0.55f, strum = 0.16)
            pad("pad", 55, 74, vel = 0.45f)
            bass("bass", "R-------........", vel = 0.7f)
            melody("lead", "| D5 - - - - - . . | E5 - - - - - . . | A4 - - - - - - - | - - - - - - . . |", vel = 0.65f)
            drums(lines = mapOf(Kick to "x...............", Snare to "....x.......x...", HatClosed to "x.o.x.o.x.o.x.o."))
        }
        form("intro", "A", "A2", "B", "A3", "outro")
    }

    // --- Starlight Arcade: upbeat chiptune in D major ------------------------------------------------------------

    private fun starlightArcade() = song("starlight-arcade", "Starlight Arcade", "Bright chiptune, coins and high scores", bpm = 140.0) {
        part(
            "lead",
            Patch(Osc.Pulse, gain = 0.25f, pw = 0.25, attack = 0.002, decay = 0.2, sustain = 0.75f, release = 0.06, vibDepth = 0.15,
                vibRate = 6.0, vibDelay = 0.18, steppedEnv = true, reverb = 0.12f, echo = 0.18f),
            alt = Patch(Osc.Pulse, gain = 0.26f, pw = 0.125, attack = 0.002, decay = 0.2, sustain = 0.75f, release = 0.06,
                vibDepth = 0.15, vibRate = 6.0, vibDelay = 0.18, steppedEnv = true, reverb = 0.12f, echo = 0.2f),
        )
        part("harmony", Patch(Osc.Pulse, gain = 0.095f, pan = -0.3f, pw = 0.5, attack = 0.002, decay = 0.15, sustain = 0.6f,
            release = 0.05, steppedEnv = true, reverb = 0.1f))
        part("arp", Patch(Osc.Pulse, gain = 0.09f, pan = 0.3f, pw = 0.125, attack = 0.001, decay = 0.1, sustain = 0.8f, release = 0.03,
            chipArpHz = 25.0, steppedEnv = true, reverb = 0.08f))
        part("bass", Patch(Osc.ChipTriangle, gain = 0.24f, attack = 0.001, decay = 0.1, sustain = 1f, release = 0.02, velSens = 0.1f, reverb = 0f))
        drums("drums", Kit(KitStyle.Chip, gain = 0.42f, reverb = 0.05f, trims = mapOf(HatClosed to 0.5f)))
        mix = Mix(reverbSize = 0.35, reverbReturn = 0.35f, echoSteps = 3.0, echoFeedback = 0.3f, echoReturn = 0.35f, humanize = 0.0, velJitter = 0.03f, master = 1.38f)

        val grooveA = mapOf(Kick to "x.....x...x.....", Snare to "....x.......x...", HatClosed to "x.x.x.x.x.x.x.x.")
        val grooveB = mapOf(Kick to "x..x..x...x..x..", Snare to "....x.......x...", HatClosed to "x.o.x.o.x.o.x.oo")
        val fills = listOf(
            mapOf(Kick to "x.....x...x.....", Snare to "....x...x.x.xxxx", HatClosed to "x.x.x.x.x.x....."),
            mapOf(Kick to "x.....x.........", Snare to "....x...........", TomHigh to "........x.x.....", TomLow to "............x.x."),
            mapOf(Kick to "x.....x...x.x.x.", Snare to "....x.......x.xx", HatClosed to "x.x.x.x.x.x....."),
        )
        val melodyA1 = "| F#5 - A5 - D6 - - - C#6 - D6 - A5 - - - | F#5 - - - E5 - F#5 - A5 - - - C#6 - - - " +
            "| B5 - - - A5 - G5 - D5 - G5 - B5 - - - | C#6 - - - B5 - A5 - E5 - - - . . . . " +
            "| F#5 - A5 - D6 - - - C#6 - D6 - F#6 - - - | E6 - - - C#6 - A5 - F#5 - - - A5 - - - "
        val chordsA = "D | F#m | G | A | D | F#m | Em7 | A7"
        section("intro", 4, "D | G | D | A") {
            chipChords("arp", "x---x---x---x---", lo = 62)
            bass("bass", "R.R.8.R.R.8.5.8.")
            drums(lines = mapOf(HatClosed to "x.x.x.x.x.x.x.x."), fills = listOf(mapOf(Snare to "x.x.x.x.xxxxxxxx", HatClosed to "x.x.x.x.........")), riser = false)
        }
        section("A", 8, chordsA) {
            chipChords("arp", "x---x---x---x---", lo = 62)
            bass("bass", "R.R.8.R.R.8.5.8.")
            melody("lead", melodyA1 + "| G5 - - - F#5 - E5 - B5 - - - A5 - G5 - | E5 - - - . . A5 - C#6 - E6 - G6 - - - |", unit = 1.0)
            drums(fills = fills, crash = true, lines = grooveA)
        }
        section("A2", 8, chordsA) {
            chipChords("arp", "x---x---x---x---", lo = 62)
            bass("bass", "R.R.8.R.R.8.5.8.")
            melody("lead", melodyA1 + "| G5 - F#5 - E5 - D5 - E5 - - - B4 - - - | C#5 - - - E5 - A5 - G5 - E5 - C#5 - - - |", unit = 1.0)
            melody("harmony", "| A4 F#4 | A4 C#5 | B4 D5 | C#5 E5 | A4 F#4 | A4 C#5 | B4 G4 | A4 C#5 |", unit = 8.0, vel = 0.7f)
            drums(fills = fills, lines = grooveA)
        }
        section("B", 8, "Bm | G | D | A | Bm | G | Em | Asus4 A") {
            chipChords("arp", "x-------x---x---", lo = 62)
            bass("bass", "R..R..R.R..R..8.")
            melody("lead", "| D5 - - F#5 - - B5 - - A5 - - F#5 - - - | G5 - - B5 - - D6 - - B5 - - G5 - A5 - " +
                "| F#5 - - A5 - - D6 - - C#6 - - A5 - - - | E5 - - - - - - - C#5 - E5 - A5 - - - " +
                "| D5 - - F#5 - - B5 - - A5 - - F#5 - - - | B5 - - D6 - - G6 - - F#6 - - D6 - - - " +
                "| E6 - - - D6 - B5 - G5 - - - E5 - - - | D5 - - - - - - - C#5 - - - - - - - |", unit = 1.0)
            melody("harmony", "| F#4 - D4 - | G4 - B4 - | A4 - F#4 - | E4 - - - | F#4 - D4 - | B4 - D5 - | B4 - G4 - | A4 - - - |", unit = 4.0, vel = 0.6f)
            drums(fills = fills, crash = true, lines = grooveB)
        }
        section("C", 4, "G | A | F#m | Bm") {
            chipChords("arp", "x-x-x-x-x-x-x-x-", lo = 62, vel = 0.5f)
            bass("bass", "R-------R-------")
            melody("harmony", "| B4 - D5 - | C#5 - E5 - | C#5 - A4 - | B4 - - - |", unit = 4.0, vel = 0.8f)
            drums(lines = mapOf(Kick to "x.......x......."), fills = listOf(mapOf(Kick to "x...x...x...x...", Snare to "....x...x.x.xxxx")), riser = true)
        }
        form("intro", "A", "A2", "B", "A", "C", "B", "A2")
    }

    // --- Sakura Breeze: gentle koto and flute, D major pentatonic ------------------------------------------------

    private fun sakuraBreeze() = song("sakura-breeze", "Sakura Breeze", "Koto, flute and falling petals", bpm = 88.0) {
        part("koto", Patch(Osc.Pluck, gain = 0.34f, pan = -0.15f, bright = 0.75, ring = 0.55, bendFrom = -0.4, bendTime = 0.04,
            attack = 0.001, sustain = 1f, release = 0.5, reverb = 0.35f, echo = 0.12f))
        part(
            "flute",
            Patch(Osc.Flute, gain = 0.15f, pan = 0.1f, breath = 0.25, attack = 0.07, decay = 0.4, sustain = 0.85f, release = 0.4,
                vibDepth = 0.22, vibRate = 4.8, vibDelay = 0.35, bendFrom = -1.0, bendTime = 0.06, reverb = 0.45f, echo = 0.12f),
            alt = Patch(Osc.Pluck, gain = 0.2f, pan = 0.1f, bright = 0.85, ring = 0.7, bendFrom = -0.5, bendTime = 0.05,
                attack = 0.001, sustain = 1f, release = 0.6, reverb = 0.4f, echo = 0.15f),
        )
        part("strings", Patch(Osc.Saw, gain = 0.04f, unison = 3, detune = 7.0, cutoff = 1400.0, attack = 1.0, decay = 1.0,
            sustain = 0.85f, release = 1.5, reverb = 0.6f, chorus = 0.5f))
        part("bass", Patch(Osc.Triangle, gain = 0.2f, cutoff = 500.0, attack = 0.01, decay = 0.8, sustain = 0.5f, release = 0.3, reverb = 0.05f))
        part("chime", Patch(Osc.Fm, gain = 0.1f, pan = 0.35f, fmRatio = 2.76, fmIndex = 1.5, fmDecay = 0.4, attack = 0.001,
            decay = 2.0, sustain = 0f, release = 1.5, reverb = 0.7f))
        drums("drums", Kit(KitStyle.Light, gain = 0.45f, reverb = 0.25f, trims = mapOf(Shaker to 0.5f, Wood to 0.6f, TomLow to 0.9f)))
        mix = Mix(reverbSize = 0.8, reverbDamp = 0.45, reverbReturn = 0.7f, echoSteps = 6.0, echoFeedback = 0.3f, echoReturn = 0.4f, humanize = 10.0, master = 1.68f)

        val grooveA = mapOf(TomLow to "x.......o.......", Shaker to "o.x.o.x.o.x.o.x.", Wood to "....o.......o...")
        val grooveB = mapOf(TomLow to "x.......x.....o.", Shaker to "x.x.x.x.x.x.x.x.", Wood to "....x.......x...")
        val chordsA = "Gmaj9 | Asus2 | Bm7 | F#m7 | Gmaj9 | A | D/F# | Em7 Asus4"
        val chordsB = "Bm7 | Gmaj7 | D | A | Bm7 | Gmaj7 | Em7 | Asus4 A"
        val kotoA = "0 2 4 5 6 5 4 2"
        section("intro", 4, "Gmaj9 | A | Bm7 | A") {
            arp("koto", kotoA, unit = 2.0, lo = 55, vel = 0.55f)
            melody("chime", "| . . . . | B5 - . . | . . F#6 - | E6 - - - |", unit = 4.0, vel = 0.6f)
        }
        section("A", 8, chordsA) {
            arp("koto", kotoA, unit = 2.0, lo = 55, vel = 0.45f)
            pad("strings", 55, 72, vel = 0.6f)
            bass("bass", "R-------5-------", lo = 38)
            melody("flute", "| D5 - E5 - F#5 - - A5 | - - - - B5 - A5 - | F#5 - - - - - E5 D5 | E5 - - - - - . . " +
                "| D5 - E5 - F#5 - A5 - | E6 - - C#6 - - B5 A5 | A5 - - F#5 - - E5 D5 | E5 - - - - - . . |")
            drums(lines = grooveA)
        }
        section("A2", 8, chordsA) {
            arp("koto", kotoA, unit = 2.0, lo = 55, vel = 0.45f)
            pad("strings", 55, 72, vel = 0.6f)
            bass("bass", "R-------5-------", lo = 38)
            melody("flute", "| A5 - B5 - D6 - - E6 | - - - - D6 - B5 - | A5 - - - - - B5 A5 | F#5 - - - - - . . " +
                "| D5 - E5 - F#5 - A5 - | B5 - - A5 - - F#5 E5 | F#5 - - - E5 - D5 - | E5 - - - - - D5 E5 |")
            melody("chime", "| . . . . | . . . . | . . F#6 - | . . . . | . . . . | E6 - . . | . . . . | B5 - - - |", unit = 4.0, vel = 0.5f)
            drums(lines = grooveA)
        }
        section("B", 8, chordsB) {
            arp("koto", "0 1 2 3 4 3 2 1", unit = 2.0, lo = 55, vel = 0.45f)
            pad("strings", 57, 74, vel = 0.7f)
            bass("bass", "R-------R---5---", lo = 38)
            melody("flute", "| F#6 - - E6 - - D6 - | B5 - - - - - A5 B5 | D6 - - - A5 - F#5 - | E5 - - - - - - - " +
                "| F#6 - - E6 - - D6 E6 | F#6 - - - B5 - D6 - | E6 - - D6 - - B5 A5 | B5 - - - A5 - - - |")
            drums(lines = grooveB, fills = listOf(mapOf(TomLow to "x.......x.x.x.xx", Shaker to "x.x.x.x.x.x.....")))
        }
        section("interlude", 4, "Gmaj9 | A | F#m7 | Bm7") {
            arp("koto", kotoA, unit = 2.0, lo = 55, vel = 0.5f)
            bass("bass", "R---------------", lo = 38, vel = 0.6f)
            melody("chime", "| B5 - A5 - | E6 - - - | C#6 - A5 - | F#5 - - - |", unit = 4.0, vel = 0.7f)
            drums(lines = mapOf(Shaker to "o...o...o...o..."))
        }
        section("outro", 4, "Gmaj9 | A | D | D") {
            arp("koto", kotoA, unit = 2.0, lo = 55, vel = 0.4f)
            pad("strings", 55, 72, vel = 0.5f)
            bass("bass", "R-------5-------", lo = 38, vel = 0.6f)
            melody("flute", "| D5 - E5 - F#5 - - A5 | - - - - B5 - A5 - | F#5 - - - - - - - | - - - - - - . . |")
        }
        form("intro", "A", "A2", "B", "interlude", "A", "B", "outro")
    }

    // --- Rainy Library: mellow electric piano, rain and vinyl, Eb major ------------------------------------------

    private fun rainyLibrary() = song("rainy-library", "Rainy Library", "Electric piano, rain on the window, warm tea", bpm = 72.0, swing = 0.2) {
        part("rhodes", Patch(Osc.EPiano, gain = 0.17f, pan = -0.12f, attack = 0.003, decay = 2.0, sustain = 0.2f, release = 0.5,
            fmIndex = 1.3, fmDecay = 1.0, reverb = 0.3f, chorus = 0.6f, duck = 0.2f))
        part(
            "lead",
            Patch(Osc.EPiano, gain = 0.32f, pan = 0.15f, attack = 0.003, decay = 1.5, sustain = 0.25f, release = 0.45,
                fmIndex = 2.2, fmDecay = 0.7, reverb = 0.35f, echo = 0.25f, chorus = 0.3f),
            alt = Patch(Osc.Fm, gain = 0.22f, pan = 0.15f, fmRatio = 4.0, fmIndex = 1.2, fmDecay = 0.5, attack = 0.002, decay = 1.6,
                sustain = 0.1f, release = 0.7, vibDepth = 0.08, vibRate = 4.5, vibDelay = 0.1, reverb = 0.4f, echo = 0.25f),
        )
        part("bass", Patch(Osc.Triangle, gain = 0.3f, cutoff = 380.0, attack = 0.008, decay = 0.6, sustain = 0.6f, release = 0.15, duck = 0.1f, reverb = 0f))
        part("strings", Patch(Osc.Saw, gain = 0.035f, unison = 2, detune = 8.0, cutoff = 700.0, attack = 1.2, decay = 1.0,
            sustain = 0.85f, release = 1.5, reverb = 0.6f, chorus = 0.5f))
        drums("drums", Kit(KitStyle.LoFi, gain = 0.5f, reverb = 0.1f, trims = mapOf(HatClosed to 0.45f, Snare to 0.75f)))
        mix = Mix(reverbSize = 0.55, reverbDamp = 0.65, reverbReturn = 0.5f, echoSteps = 4.0, echoFeedback = 0.35f, crackle = 1.0,
            rain = 1.0, wow = 9.0, lofi = 5200.0, humanize = 10.0, master = 1.3f)

        val groove = mapOf(Kick to "x.........x.....", Snare to "....x..g....x...", HatClosed to "x.o.x.o.x.o.x.o.")
        val fills = listOf(
            mapOf(Kick to "x.........x.....", Snare to "....x..g....x.gg", HatClosed to "x.o.x.o.x.o.x.o."),
            mapOf(Kick to "x.........x..x..", Snare to "....x.......x...", HatClosed to "x.o.x.o.x.o.x...", HatOpen to "..............x."),
        )
        val chordsA = "Abmaj9 | Gm7 | Fm9 | Bb7 | Ebmaj9 | Cm9 | Fm9 | Bb7sus4"
        val comp = "x-----.x--..x---"
        section("intro", 4, "Abmaj9 | Gm7 | Fm9 | Bb7sus4") {
            stabs("rhodes", "x---------------", 51, 70, vel = 0.55f, strum = 0.15)
            bass("bass", "R---------------", lo = 31, vel = 0.5f)
        }
        section("A", 8, chordsA) {
            stabs("rhodes", comp, 51, 70, vel = 0.58f, strum = 0.1)
            bass("bass", "R-------.R--5-..", lo = 31)
            melody("lead", "| . . G5 - Eb5 - C5 Eb5 | D5 - - - - - . . | . . Ab5 - G5 - Eb5 F5 | D5 - - - - - F5 G5 " +
                "| Bb5 - - - G5 - F5 - | G5 - - - Eb5 - D5 C5 | Ab5 - - G5 - - F5 Eb5 | F5 - - - - - . . |")
            drums(fills = fills, lines = groove)
        }
        section("A2", 8, chordsA) {
            stabs("rhodes", comp, 51, 70, vel = 0.58f, strum = 0.1)
            pad("strings", 55, 72, vel = 0.5f)
            bass("bass", "R-------.R--5-..", lo = 31)
            melody("lead", "| . . G5 - Eb5 - C5 Eb5 | F5 - - D5 - - Bb4 - | . . Ab5 - G5 - Eb5 F5 | G5 - - F5 - - D5 - " +
                "| Eb5 - - - G5 - Bb5 - | D6 - - - C6 - Bb5 G5 | Ab5 - - - G5 - F5 Eb5 | Eb5 - - - - - . . |")
            drums(fills = fills, lines = groove)
        }
        section("B", 8, "Cm9 | Abmaj7 | Bbsus4 Bb | Gm7 | Cm9 | Abmaj7 | Fm9 | Bb7") {
            stabs("rhodes", "x-------x-------", 51, 70, vel = 0.55f, strum = 0.12)
            pad("strings", 55, 74, vel = 0.65f)
            bass("bass", "R-------R---5---", lo = 31)
            melody("lead", "| G5 - Bb5 - D6 - - C6 | - - - - Eb5 - G5 - | F5 - - - D5 - - - | . . . . D5 F5 G5 Bb5 " +
                "| C6 - - - Bb5 - G5 - | Ab5 - - - G5 - Eb5 - | G5 - - F5 - - Eb5 C5 | D5 - - - - - . . |")
            drums(fills = fills, lines = groove + (Shaker to "..x...x...x...x."))
        }
        section("outro", 4, "Abmaj9 | Gm7 | Fm9 | Ebmaj9") {
            stabs("rhodes", "x-------........", 51, 70, vel = 0.5f, strum = 0.16)
            bass("bass", "R---------------", lo = 31, vel = 0.6f)
            melody("lead", "| . . G5 - Eb5 - C5 Eb5 | D5 - - - - - . . | . . Ab5 - G5 - F5 - | G5 - - - - - - . |", vel = 0.7f)
            drums(lines = mapOf(Kick to "x...............", HatClosed to "x.o.x.o.x.o.x.o."))
        }
        form("intro", "A", "A2", "B", "A", "outro")
    }

    // --- Synthwave Drive: retro 80s in A minor -------------------------------------------------------------------

    private fun synthwaveDrive() = song("synthwave-drive", "Synthwave Drive", "Neon highway at midnight", bpm = 100.0) {
        part(
            "lead",
            Patch(Osc.Saw, gain = 0.24f, unison = 2, detune = 8.0, cutoff = 2600.0, envMod = 1500.0, filterDecay = 0.3, resonance = 0.9,
                attack = 0.01, decay = 0.3, sustain = 0.8f, release = 0.25, glide = 0.06, mono = true, vibDepth = 0.15, vibDelay = 0.3,
                reverb = 0.3f, echo = 0.3f, chorus = 0.2f),
            alt = Patch(Osc.Pulse, gain = 0.22f, pw = 0.3, pwmRate = 0.3, pwmDepth = 0.15, cutoff = 3000.0, attack = 0.01, decay = 0.3,
                sustain = 0.8f, release = 0.25, glide = 0.05, mono = true, vibDepth = 0.15, vibDelay = 0.3, reverb = 0.3f, echo = 0.3f),
        )
        part("pad", Patch(Osc.Saw, gain = 0.065f, unison = 3, detune = 12.0, cutoff = 1500.0, resonance = 0.6, attack = 0.4, decay = 1.0,
            sustain = 0.85f, release = 0.8, reverb = 0.5f, chorus = 0.7f, duck = 0.7f))
        part("arp", Patch(Osc.Pulse, gain = 0.12f, pan = 0.25f, pw = 0.35, cutoff = 2200.0, envMod = 2500.0, filterDecay = 0.08,
            attack = 0.002, decay = 0.12, sustain = 0.2f, release = 0.08, echo = 0.35f, duck = 0.3f, reverb = 0.1f))
        part("bass", Patch(Osc.Saw, gain = 0.33f, cutoff = 380.0, envMod = 900.0, filterDecay = 0.1, resonance = 1.1, attack = 0.003,
            decay = 0.15, sustain = 0.6f, release = 0.05, duck = 0.5f, reverb = 0f))
        drums("drums", Kit(KitStyle.Retro, gain = 0.44f, reverb = 0.18f, trims = mapOf(Clap to 0.6f, HatClosed to 0.5f)))
        mix = Mix(reverbSize = 0.85, reverbDamp = 0.4, reverbReturn = 0.55f, echoSteps = 3.0, echoFeedback = 0.42f, echoReturn = 0.45f,
            chorusDepth = 3.0, chorusReturn = 0.7f, duckRelease = 0.5, humanize = 2.0, master = 1.25f)

        val groove = mapOf(Kick to "x...x...x...x...", Snare to "....x.......x...", Clap to "....o.......o...", HatClosed to "..x...x...x...x.")
        val fills = listOf(
            mapOf(Kick to "x...x...x...x...", Snare to "....x.......x.xx", HatClosed to "..x...x...x....."),
            mapOf(Kick to "x...x...x.......", Snare to "....x...........", TomHigh to "........x.x.....", TomLow to "............x.x."),
        )
        val arpA = "0 1 2 3 4 3 2 1"
        val melodyA = "| E5 - D5 - C5 - D5 - | E5 - - - A5 - - - | G5 - - - E5 - C5 - | D5 - - - - - . . " +
            "| E5 - D5 - C5 - D5 - | E5 - - - A5 - C6 - | B5 - - - G5 - E5 - | D5 - - - - - . . |"
        val melodyB = "| A5 - - - C6 - - - | B5 - - - D6 - - - | B5 - - - G5 - E5 - | C6 - - - B5 - A5 - " +
            "| A5 - - - C6 - F6 - | D6 - - - B5 - G5 - | C6 - - - E6 - G6 - | A5 - - - G#5 - - - |"
        section("intro", 4, "Am | F | C | G") {
            pad("pad", 57, 76, vel = 0.6f)
            arp("arp", arpA, unit = 1.0, lo = 57, vel = 0.5f)
            drums(lines = mapOf(Kick to "x..............."), riser = true)
        }
        section("A", 8, "Am | F | C | G | Am | F | C | G") {
            pad("pad", 57, 76)
            arp("arp", arpA, unit = 1.0, lo = 57)
            bass("bass", "R.R.8.R.R.R.8.R.", lo = 33)
            melody("lead", melodyA)
            drums(fills = fills, crash = true, lines = groove)
        }
        section("B", 8, "F | G | Em | Am | F | G | C | Esus4 E") {
            pad("pad", 57, 76, vel = 0.75f)
            arp("arp", "0 2 1 3 2 4 3 1", unit = 1.0, lo = 57)
            bass("bass", "R.R.R.R.R.R.R.R.", lo = 33)
            melody("lead", melodyB)
            drums(fills = fills, crash = true, lines = groove + (HatOpen to "..............o."))
        }
        section("breakdown", 8, "F | G | Am | Am | F | G | E | E") {
            pad("pad", 57, 76, vel = 0.7f)
            arp("arp", arpA, unit = 1.0, lo = 57, vel = 0.5f)
            bass("bass", "R-------........", lo = 33, vel = 0.6f)
            melody("lead", "| C6 - - - - - - - | D6 - - - - - - - | E6 - - - - - - - | - - - - - - - - " +
                "| C6 - - - - - - - | D6 - - - - - - - | B5 - - - - - - - | G#5 - - - - - - - |", vel = 0.7f)
            drums(lines = mapOf(Kick to "x.......x......."), riser = true)
        }
        section("outro", 4, "Am | F | G | Am") {
            pad("pad", 57, 76, vel = 0.6f)
            arp("arp", arpA, unit = 1.0, lo = 57, vel = 0.5f)
            bass("bass", "R.R.8.R.R.R.8.R.", lo = 33)
            melody("lead", "| E5 - D5 - C5 - D5 - | E5 - - - A5 - - - | B5 - - - D6 - - - | C6 - - - - - . . |")
            drums(lines = mapOf(Kick to "x...x...x...x...", HatClosed to "..x...x...x...x."))
        }
        form("intro", "A", "B", "A", "breakdown", "B", "A", "outro")
    }

    // --- Pip's Jam: Tandem's signature tune, bouncy G major --------------------------------------------------------

    private fun pipsJam() = song("pips-jam", "Pip's Jam", "Pip's happy little theme song", bpm = 116.0, swing = 0.12) {
        part(
            "lead",
            Patch(Osc.Pulse, gain = 0.22f, pw = 0.5, cutoff = 2200.0, attack = 0.01, decay = 0.25, sustain = 0.7f, release = 0.12,
                vibDepth = 0.1, vibDelay = 0.2, reverb = 0.25f, echo = 0.15f),
            alt = Patch(Osc.Flute, gain = 0.3f, breath = 0.12, attack = 0.02, decay = 0.3, sustain = 0.75f, release = 0.15,
                vibDepth = 0.15, vibDelay = 0.2, reverb = 0.3f, echo = 0.15f),
        )
        part("bell", Patch(Osc.Fm, gain = 0.1f, pan = 0.2f, fmRatio = 4.0, fmIndex = 1.5, fmDecay = 0.2, attack = 0.001, decay = 0.6,
            sustain = 0f, release = 0.4, reverb = 0.35f, echo = 0.2f))
        part("marimba", Patch(Osc.Fm, gain = 0.17f, pan = -0.25f, fmRatio = 1.0, fmIndex = 0.8, fmDecay = 0.08, attack = 0.001,
            decay = 0.35, sustain = 0f, release = 0.2, reverb = 0.2f))
        part("uke", Patch(Osc.Pluck, gain = 0.17f, pan = -0.1f, bright = 0.65, ring = 0.35, attack = 0.001, sustain = 1f, release = 0.15,
            reverb = 0.2f))
        part("bass", Patch(Osc.Pluck, gain = 0.6f, bright = 0.25, ring = 0.35, attack = 0.001, sustain = 1f, release = 0.08, reverb = 0f))
        drums("drums", Kit(KitStyle.Pop, gain = 0.42f, reverb = 0.12f, trims = mapOf(Clap to 0.7f, HatClosed to 0.6f)))
        mix = Mix(reverbSize = 0.5, reverbReturn = 0.45f, echoSteps = 3.0, echoFeedback = 0.25f, echoReturn = 0.3f, humanize = 4.0, master = 1.29f)

        val groove = mapOf(Kick to "x.......x.x.....", Snare to "....x.......x...", Clap to "....o.......o...", HatClosed to "x.o.x.o.x.o.x.o.")
        val fills = listOf(
            mapOf(Kick to "x.......x.x.....", Snare to "....x.......x.xx", Clap to "....o.......o...", HatClosed to "x.o.x.o.x.o....."),
            mapOf(Kick to "x.......x.......", Snare to "....x...........", TomHigh to "........x.x.....", TomLow to "............x.x."),
            mapOf(Kick to "x.......x.x.....", Clap to "....x.......x.x.", HatClosed to "x.o.x.o.x.o.x.o.", HatOpen to "..............x."),
        )
        val strum = "x...x.x...x.x..."
        val chordsA = "G | Em | C | D | G | Em | Am7 D7 | G"
        val hook = "| D5 G5 - B5 - A5 G5 - | B4 E5 - G5 - F#5 E5 - | C5 E5 - G5 - A5 G5 - | F#5 - A5 - D6 - - - " +
            "| D5 G5 - B5 - A5 G5 - | B4 E5 - G5 - B5 A5 B5 | A5 - C6 - F#5 - A5 - | G5 - - - . . D5 - |"
        val melodyB = "| E5 - - G5 - - C6 - | D6 - - A5 - - F#5 - | B5 - - F#5 - - D5 - | E5 - - - G5 - B5 - " +
            "| C6 - - B5 - - G5 - | A5 - - F#5 - - D5 E5 | E5 - A5 - C6 - B5 A5 | F#5 - - - A5 - C6 - |"
        section("intro", 2, "G | D7") {
            stabs("uke", strum, 60, 76, vel = 0.6f, strum = 0.15)
            bass("bass", "R...R.5.R...5.8.", lo = 40)
            drums(lines = mapOf(Kick to "x.......x.x.....", HatClosed to "x.o.x.o.x.o.x.o."), fills = listOf(mapOf(Kick to "x.......x.......", Snare to "........x.x.xxxx")))
        }
        section("A", 8, chordsA) {
            stabs("uke", strum, 60, 76, vel = 0.6f, strum = 0.15)
            bass("bass", "R...R.5.R...5.8.", lo = 40)
            melody("lead", hook)
            arp("marimba", ". 2 . 3 . 4 . 3", unit = 2.0, lo = 55, vel = 0.5f)
            drums(fills = fills, crash = true, lines = groove)
        }
        section("A2", 8, chordsA) {
            stabs("uke", strum, 60, 76, vel = 0.6f, strum = 0.15)
            bass("bass", "R...R.5.R...5.8.", lo = 40)
            melody("lead", hook)
            melody("bell", hook, transpose = 12, vel = 0.6f)
            arp("marimba", ". 2 . 3 . 4 . 3", unit = 2.0, lo = 55, vel = 0.5f)
            drums(fills = fills, lines = groove)
        }
        section("B", 8, "C | D | Bm | Em | C | D | Am7 | D7") {
            stabs("uke", "x-x.x-x.x-x.x-x.", 60, 76, vel = 0.55f, strum = 0.12)
            bass("bass", "R..R..5.R..R..8.", lo = 40)
            melody("lead", melodyB)
            drums(fills = fills, crash = true, lines = groove + (Shaker to "..x...x...x...x."))
        }
        section("C", 8, chordsA) {
            stabs("uke", strum, 60, 76, vel = 0.5f, strum = 0.15)
            bass("bass", "R...R.5.R...5.8.", lo = 40)
            melody("bell", "| G5 - . . B5 - . . | E5 - . . G5 - . . | C6 - . . E6 - . . | D6 - - - - - . . " +
                "| G5 B5 D6 B5 G5 . . . | E5 G5 B5 G5 E5 . . . | A5 C6 E6 . F#5 A5 D6 . | G5 - - - - - . . |", vel = 0.85f)
            arp("marimba", "0 1 2 3 2 1 0 1", unit = 1.0, lo = 60, vel = 0.4f)
            drums(fills = fills, lines = mapOf(Kick to "x.......x.x.....", Clap to "....x.......x...", Shaker to "x.x.x.x.x.x.x.x."))
        }
        form("intro", "A", "A2", "B", "A", "C", "B", "A2")
    }
}
