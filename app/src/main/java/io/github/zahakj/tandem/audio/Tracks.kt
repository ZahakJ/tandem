package io.github.zahakj.tandem.audio

/**
 * Tandem's built-in soundtracks, played by the synthesizer, so the music is GPL like the rest of the app and there
 * are no audio files to license: "Calm" ([Calm], the default) and "Meadow Tales" ([Meadow]). The chosen one is the
 * music when the theme pack has none and the music folder isn't chosen (or is empty); it plays through its pieces
 * in turn.
 */
object Tracks {
    /** The soundtracks by id, with their names: Calm first, the default (Look.soundtrack). */
    val soundtracks: List<Pair<String, String>> = listOf("calm" to "Calm", "meadow" to "Meadow Tales")

    /** The pieces of soundtrack [id] (an unknown id is Calm). */
    fun of(id: String): List<Song> = if (id == "meadow") Meadow.all else Calm.all

    /** Every piece of every soundtrack. */
    val all: List<Song> get() = Calm.all + Meadow.all

    fun byId(id: String): Song? = all.firstOrNull { it.id == id }

    /** Track ids with friendly names. */
    val names: List<Pair<String, String>> get() = Calm.names + Meadow.names
}
