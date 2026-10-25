package io.github.zahakj.tandem.audio

/**
 * Short original jingles per family of systems: a 1-second "hello" when you open a system, and a half-second "ta-da"
 * when a game launches. Each family has its own instrument and key, so Nintendo handhelds chirp in square waves,
 * Sega answers in FM brass, PlayStation shimmers, and so on.
 */
enum class Family(val label: String, val key: Int, val motif: Int) {
    Pocket("Pocket", -12, 0),
    Home("Home console", -7, 1),
    Sony("Disc", -10, 2),
    Sega("FM", -8, 1),
    Arcade("Arcade", -5, 3),
    Computer("Computer", -15, 0),
    Retro("Retro", -2, 3),
    Indie("Indie", -4, 2),
    ;

    companion object {
        fun of(platformId: String): Family = when (platformId) {
            "n3ds", "nds", "gba", "gbc", "gb", "virtualboy", "pokemini", "gameandwatch", "ngp", "ngpc", "wonderswan",
            "wonderswancolor", "atarilynx", "megaduck", "supervision" -> Pocket
            "switch", "wiiu", "wii", "gc", "n64", "snes", "nes", "fds" -> Home
            "psx", "ps2", "ps3", "psp", "psvita", "xbox", "xbox360", "3do", "cdimono1", "pcfx" -> Sony
            "genesis", "mastersystem", "gamegear", "sg1000", "sega32x", "segacd", "pico", "saturn", "dreamcast", "naomi",
            "atomiswave", "model3", "stv" -> Sega
            "arcade", "neogeo", "neogeocd", "pcengine", "pcenginecd", "supergrafx" -> Arcade
            "windows", "dos", "amiga", "c64", "pet", "plus4", "vic20", "amstradcpc", "msx", "zxspectrum", "zx81", "x1",
            "x68000", "bbcmicro", "apple2", "pc88", "pc98", "atarist", "scummvm" -> Computer
            "atari2600", "atari5200", "atari7800", "atarijaguar", "colecovision", "intellivision", "vectrex", "channelf",
            "odyssey2", "vc4000", "arcadia" -> Retro
            else -> Indie
        }
    }
}

object Jingles {
    // Motifs written in C; each family transposes them to its own key.
    private val motifs = listOf(
        "| C6 E6 G6 C7 - - - - - - - - . . . . |",
        "| G5 - C6 - E6 D6 C6 - G6 - - - - - . . |",
        "| E5 - - G5 - - D6 - - - - - - - . . |",
        "| B5 E6 - - - - - - - - . . . . . . |",
    )
    // Quick pickup into the tonic: over in about 0.6 s, so the game never cuts it off (see Sounds.launch).
    private const val LAUNCH = "| G5 C6 E6 G6 - - . . . . . . . . . . |"

    private fun lead(f: Family): Patch = when (f) {
        Family.Pocket -> Patch(Osc.Pulse, gain = 0.3f, pw = 0.25, attack = 0.002, decay = 0.25, sustain = 0.6f, release = 0.08, steppedEnv = true, reverb = 0.1f)
        Family.Home -> Patch(Osc.Fm, gain = 0.4f, fmRatio = 1.0, fmIndex = 0.9, fmDecay = 0.08, attack = 0.001, decay = 0.5, sustain = 0f, release = 0.3, reverb = 0.25f)
        Family.Sony -> Patch(Osc.Fm, gain = 0.32f, fmRatio = 2.0, fmIndex = 1.2, fmDecay = 0.6, attack = 0.01, decay = 1.2, sustain = 0.2f, release = 0.8, reverb = 0.6f, chorus = 0.5f)
        Family.Sega -> Patch(Osc.Fm, gain = 0.3f, fmRatio = 1.0, fmIndex = 3.2, fmDecay = 0.15, attack = 0.004, decay = 0.4, sustain = 0.5f, release = 0.15, reverb = 0.2f)
        Family.Arcade -> Patch(Osc.Pulse, gain = 0.3f, pw = 0.5, attack = 0.001, decay = 0.2, sustain = 0.6f, release = 0.05, steppedEnv = true, reverb = 0.1f)
        Family.Computer -> Patch(Osc.Pulse, gain = 0.3f, pw = 0.4, pwmRate = 4.0, pwmDepth = 0.25, attack = 0.002, decay = 0.3, sustain = 0.5f, release = 0.1, reverb = 0.15f)
        Family.Retro -> Patch(Osc.ChipTriangle, gain = 0.45f, attack = 0.001, decay = 0.3, sustain = 0.6f, release = 0.06, reverb = 0.1f)
        Family.Indie -> Patch(Osc.Pluck, gain = 0.55f, bright = 0.8, ring = 0.35, attack = 0.001, sustain = 1f, release = 0.4, reverb = 0.3f)
    }

    private fun pad(f: Family): Patch = when (f) {
        Family.Pocket, Family.Arcade, Family.Retro -> Patch(Osc.Pulse, gain = 0.08f, pw = 0.125, attack = 0.002, decay = 0.4, sustain = 0.5f, release = 0.2, steppedEnv = true)
        Family.Sony -> Patch(Osc.Saw, gain = 0.06f, unison = 3, detune = 10.0, cutoff = 1600.0, attack = 0.15, decay = 1.0, sustain = 0.8f, release = 0.8, reverb = 0.6f, chorus = 0.6f)
        else -> Patch(Osc.Triangle, gain = 0.12f, attack = 0.02, decay = 0.8, sustain = 0.5f, release = 0.5, reverb = 0.3f)
    }

    private fun transpose(notes: String, by: Int): String = notes.split(' ').joinToString(" ") { tok ->
        val core = tok.trimEnd('!', '?')
        if (core.isEmpty() || core == "." || core == "-" || core == "|" || !core[0].isLetter()) tok
        else name(Theory.midi(core) + by) + tok.substring(core.length)
    }

    private fun name(m: Int): String {
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        return names[m % 12] + (m / 12 - 1)
    }

    private fun chordName(base: String, by: Int): String {
        val roots = mapOf("C" to 0, "F" to 5, "G" to 7)
        val root = roots.getValue(base.takeWhile { it.isUpperCase() })
        val names = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
        return names[((root + by) % 12 + 12) % 12] + base.dropWhile { it.isUpperCase() }
    }

    /** The jingle when a system opens. */
    fun details(f: Family): Song = song("jingle-${f.name}", f.label, "", bpm = 150.0) {
        part("lead", lead(f)); part("pad", pad(f))
        if (f == Family.Arcade || f == Family.Pocket) drums("drums", Kit(KitStyle.Chip, gain = 0.25f))
        else drums("drums", Kit(KitStyle.Light, gain = 0.2f, reverb = 0.3f))
        mix = Mix(reverbSize = 0.5, humanize = 0.0, velJitter = 0f)
        oneShot = true
        section("hello", 1, chordName("Cadd9", f.key)) {
            melody("lead", transpose(motifs[f.motif], f.key), unit = 1.0)
            pad("pad", 60, 76, vel = 0.5f, maxNotes = 3)
            drums(lines = mapOf(Drum.Shaker to "o.o.o.o........."))
        }
        form("hello", loopFrom = 0)
    }

    /** The jingle when a game launches: a quick "ta-da" in the family's key, cut to [LAUNCH_JINGLE_SECONDS]. */
    fun launch(f: Family): Song = song("launch-${f.name}", f.label, "", bpm = 240.0) {
        part("lead", lead(f)); part("pad", pad(f))
        drums("drums", Kit(if (f == Family.Arcade || f == Family.Pocket || f == Family.Retro) KitStyle.Chip else KitStyle.Pop, gain = 0.3f, reverb = 0.2f))
        mix = Mix(reverbSize = 0.6, humanize = 0.0, velJitter = 0f)
        oneShot = true
        section("tada", 1, chordName("Cadd9", f.key)) {
            melody("lead", transpose(LAUNCH, f.key), unit = 1.0)
            pad("pad", 57, 74, vel = 0.55f, maxNotes = 4)
            drums(lines = mapOf(Drum.Kick to "x..............."))
        }
        form("tada", loopFrom = 0)
    }
}

/** How long a launch jingle may ring: it has to be over before the game is on screen (see Sounds.launch). */
const val LAUNCH_JINGLE_SECONDS = 0.6

/** Renders a jingle to a mono buffer at a set peak level. */
fun renderJingle(song: Song, rate: Int, peak: Float = 0.6f, maxSeconds: Double = 2.2): FloatArray {
    val raw = renderOneShot(song, rate, tailSeconds = 1.0)
    var max = 1e-6f
    for (s in raw) max = maxOf(max, kotlin.math.abs(s))
    // Cut where the tail has faded 45 dB below the peak (or at [maxSeconds]), with a gentle fade (a third of a short
    // jingle, so a cut-off one still decays rather than stops).
    var end = minOf(raw.size, (maxSeconds * rate).toInt())
    while (end > rate / 4 && kotlin.math.abs(raw[end - 1]) < max * 0.0056f) end--
    val buf = raw.copyOf(end)
    val fade = minOf(end / 3, rate * 15 / 100)
    for (i in 0 until fade) { val x = i.toFloat() / fade; buf[end - 1 - i] *= x * x }
    val g = peak / max
    for (i in buf.indices) buf[i] *= g
    return buf
}
