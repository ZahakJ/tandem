package io.github.zahakj.tandem.audio

/**
 * Tandem's built-in soundtracks, played by the synthesizer, so the music is GPL like the rest of the app and there
 * are no audio files to license: "Plaza" ([Plaza], the default), "Calm" ([Calm]) and "Meadow Tales" ([Meadow]). The
 * chosen one is the music when the theme pack has none and the music folder isn't chosen (or is empty).
 *
 * Calm and Meadow Tales play through their pieces in turn. Plaza follows where you are instead ([forScene]): one
 * piece for the home screen, one for Settings, one for the screensaver, and a jingle ([jingle]) for good news.
 */
object Tracks {
    /** The soundtracks by id, with their names (names, the same in every language): Plaza first, the default (Look.soundtrack). */
    val soundtracks: List<Pair<String, String>> = listOf("plaza" to "Plaza", "calm" to "Calm", "meadow" to "Meadow Tales")

    /** The default soundtrack's id. */
    const val DEFAULT = "plaza"

    /** The looping pieces of soundtrack [id] (an unknown id is Plaza). */
    fun of(id: String): List<Song> = when (id) {
        "meadow" -> Meadow.all
        "calm" -> Calm.all
        else -> Plaza.all
    }

    /** Where Tandem is, for soundtracks whose music follows it. */
    enum class Scene { Home, Settings, Idle }

    /** The piece soundtrack [id] plays in [scene], or null when it simply plays its pieces in turn. */
    fun forScene(id: String, scene: Scene): Song? {
        if (id in setOf("calm", "meadow")) return null
        val (plaza, shop, lounge) = Plaza.all
        return when (scene) { Scene.Home -> plaza; Scene.Settings -> shop; Scene.Idle -> lounge }
    }

    /** Soundtrack [id]'s short jingle for good news, if it has one. */
    fun jingle(id: String): Song? = if (id in setOf("calm", "meadow")) null else Plaza.passby

    /** Every piece of every soundtrack. */
    val all: List<Song> get() = Plaza.all + Plaza.passby + Calm.all + Meadow.all

    fun byId(id: String): Song? = all.firstOrNull { it.id == id }

    /** Track ids with friendly names. */
    val names: List<Pair<String, String>> get() = Plaza.names + Calm.names + Meadow.names
}
