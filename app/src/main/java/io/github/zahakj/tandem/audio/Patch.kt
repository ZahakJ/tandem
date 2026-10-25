package io.github.zahakj.tandem.audio

/** The oscillator at the heart of a [Patch]. */
enum class Osc {
    /** Pure sine: soft pads, sub bass, flutes. */
    Sine,
    /** Rounded triangle: mellow bass and pads. */
    Triangle,
    /** NES-style 16-step triangle: chiptune bass. */
    ChipTriangle,
    /** Band-limited saw (one or more detuned copies with [Patch.unison]). */
    Saw,
    /** Band-limited pulse with width [Patch.pw] and optional PWM. */
    Pulse,
    /** Two-operator FM: bells, mallets, glassy keys. */
    Fm,
    /** FM electric piano: a warm body plus a short metallic tine. */
    EPiano,
    /** Karplus-Strong plucked string: koto, harp, ukulele, pizzicato. */
    Pluck,
    /** Breathy flute: sine with a little filtered noise. */
    Flute,
    /** Filtered noise (wind, sweeps). */
    Noise,

    // Acoustic instruments. Struck ones are modal (a bank of decaying partials); bowed and blown ones read a
    // per-note wavetable with the instrument's harmonic spectrum and body resonances baked in.
    /** Grand piano: stretched (inharmonic) partials, string unisons beating, hammer noise, pitch-dependent decay. */
    Piano,
    /** Celesta: soft hammered bars, a warm sine with a brief bright attack. */
    Celesta,
    /** Glockenspiel: bright steel bars with bell-like inharmonic partials. */
    Glockenspiel,
    /** Timpani: membrane modes over a felt-mallet thump; use [Patch.tremolo] for rolls. */
    Timpani,
    /** String section: several detuned players, violin body formants, delayed vibrato, bow noise. */
    Strings,
    /** Clarinet: hollow odd harmonics in the low register. */
    Clarinet,
    /** Oboe: reedy and nasal, with a weak fundamental. */
    Oboe,
    /** Accordion / musette: detuned reeds and a breathing bellows. */
    Accordion,
    /** French horn: round, darker when soft and brassier when loud. */
    Horn,
    /** Concert flute: almost pure tone, breath noise and a soft chiff. */
    ConcertFlute,
    /**
     * Acoustic plucked string (harp, mandolin, guitar): Karplus-Strong excited by a finger or plectrum shape rather
     * than noise, so it is clean and round; [Patch.tremolo] re-picks it for mandolin tremolo.
     */
    Plucked,
}

/**
 * Everything that defines how one instrument sounds. Times are in seconds, [cutoff] and [envMod] in Hz.
 * Patches are plain values made once when a song is built.
 */
data class Patch(
    val osc: Osc,
    val gain: Float = 0.3f,
    val pan: Float = 0f,
    // Envelope
    val attack: Double = 0.005,
    val decay: Double = 0.3,
    val sustain: Float = 0.7f,
    val release: Double = 0.25,
    // Oscillator details
    val octave: Int = 0,
    val detune: Double = 0.0,
    val unison: Int = 1,
    val pw: Double = 0.5,
    val pwmRate: Double = 0.0,
    val pwmDepth: Double = 0.0,
    val fmRatio: Double = 1.0,
    val fmIndex: Double = 1.0,
    /** How fast the FM index falls (seconds); 0 keeps it steady. */
    val fmDecay: Double = 0.0,
    /** Pluck brightness (0 dark, 1 bright) and sustain (0 short, 1 long ring). */
    val bright: Double = 0.6,
    val ring: Double = 0.6,
    /** Breath noise for flutes, 0..1. */
    val breath: Double = 0.0,
    // Filter (low-pass). [cutoff] 0 = no filter.
    val cutoff: Double = 0.0,
    val resonance: Double = 0.7,
    val envMod: Double = 0.0,
    val filterDecay: Double = 0.3,
    /** How much the cutoff follows the note (0 none, 1 fully). */
    val keyTrack: Double = 0.0,
    // Pitch
    val vibRate: Double = 5.0,
    val vibDepth: Double = 0.0,
    val vibDelay: Double = 0.25,
    /** Starts this many semitones away and slides in (koto bends, synth "blips"). */
    val bendFrom: Double = 0.0,
    val bendTime: Double = 0.05,
    /** Portamento for monophonic leads (seconds). */
    val glide: Double = 0.0,
    val mono: Boolean = false,
    /** Cycle through chord tones this many times a second (chiptune arpeggio). */
    val chipArpHz: Double = 0.0,
    /** Volume steps per second like old sound chips (0 = smooth). */
    val steppedEnv: Boolean = false,
    // Sends and sidechain
    val reverb: Float = 0.2f,
    val echo: Float = 0f,
    val chorus: Float = 0f,
    /** How much the kick ducks this instrument (0..1). */
    val duck: Float = 0f,
    /** Velocity sensitivity 0..1. */
    val velSens: Float = 0.6f,
    /** Sustain pedal: notes keep ringing until the harmony changes (the pedal is lifted on each new chord). */
    val pedal: Boolean = false,
    /** Re-strike this many times a second while the note is held: mandolin tremolo, timpani roll. */
    val tremolo: Double = 0.0,
)
