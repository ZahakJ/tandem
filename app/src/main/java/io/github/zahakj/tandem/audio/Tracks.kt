package io.github.zahakj.tandem.audio

/**
 * Tandem's built-in soundtrack: "Meadow Tales" ([Meadow]), six original pieces played by the synthesizer, so the music
 * is GPL like the rest of the app and there are no audio files to license. It is the music when the theme pack has
 * none and the music folder isn't chosen (or is empty); it plays through its pieces in turn.
 */
object Tracks {
    val all: List<Song> get() = Meadow.all

    fun byId(id: String): Song? = all.firstOrNull { it.id == id }

    /** Track ids with friendly names. */
    val names: List<Pair<String, String>> get() = Meadow.names
}
