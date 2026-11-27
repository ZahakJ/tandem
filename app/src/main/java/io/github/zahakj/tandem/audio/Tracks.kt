package io.github.zahakj.tandem.audio

/**
 * Tandem's built-in soundtracks, played by the synthesizer, so the music is GPL like the rest of the app and there
 * are no audio files to license: three sister grooves ([Plaza]), "Plaza" (the default), "Lagoon" and "Terrace". The
 * chosen one is the music when the theme pack has none and the music folder isn't chosen (or is empty).
 *
 * A soundtrack is one groove, and it plays everywhere: on the home screen, in Settings and under the screensaver
 * (quieter there), looping without a seam. Next song moves on to the next groove for a while; good news brings in
 * the shared jingle ([jingle]), then the groove comes back.
 */
object Tracks {
    /** The soundtracks' ids, Plaza first (the default, Look.soundtrack), in [Plaza.all]'s order; known without building a note. */
    val ids: List<String> = listOf("plaza", "lagoon", "terrace")

    /** The soundtracks by id, with their names: the picker in Sound & music. */
    val soundtracks: List<Pair<String, String>> get() = Plaza.all.map { it.id to it.title }

    /** The default soundtrack's id. */
    const val DEFAULT = "plaza"

    /** Soundtrack [id]'s groove; an unknown id (one that was removed, such as "calm" or "meadow") is Plaza. */
    fun of(id: String): Song = Plaza.all.firstOrNull { it.id == id } ?: Plaza.all.first()

    /** The short jingle for good news: the same for every soundtrack. */
    val jingle: Song get() = Plaza.passby

    /** Every built-in piece: the grooves and the jingle. */
    val all: List<Song> get() = Plaza.all + Plaza.passby

    fun byId(id: String): Song? = all.firstOrNull { it.id == id }
}
