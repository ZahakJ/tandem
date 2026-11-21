package io.github.zahakj.tandem.ui

/**
 * Who made each well-known system, for the Systems screen: search finds "sega" or "sony", the system's page names
 * its maker, and systems with the same number of games sit together by maker (Nintendo's next to each other).
 */
object SystemMakers {
    /** Makers in the order their systems are listed when game counts tie; any other comes after them. */
    private val order = listOf(
        "Nintendo", "Sony", "Sega", "Microsoft", "Atari", "NEC", "SNK", "Bandai", "Commodore", "Arcade", "PC", "Android",
    )

    private val byId: Map<String, String> = buildMap {
        fun make(maker: String, vararg ids: String) = ids.forEach { put(it, maker) }
        make("Nintendo", "switch", "wiiu", "wii", "gc", "n64", "snes", "nes", "fds", "n3ds", "nds", "gba", "gbc", "gb", "virtualboy", "pokemini", "gameandwatch")
        make("Sony", "psx", "ps2", "ps3", "psp", "psvita")
        make("Sega", "genesis", "mastersystem", "gamegear", "sg1000", "sega32x", "segacd", "saturn", "dreamcast", "naomi", "stv", "model3")
        make("Microsoft", "xbox", "xbox360", "msx")
        make("Atari", "atari2600", "atari5200", "atari7800", "atarijaguar", "atarilynx", "atarist")
        make("NEC", "pcengine", "pcenginecd", "supergrafx", "pcfx", "pc88", "pc98")
        make("SNK", "neogeo", "neogeocd", "ngp", "ngpc")
        make("Bandai", "wonderswan", "wonderswancolor")
        make("Commodore", "amiga", "c64", "pet", "plus4", "vic20")
        make("Arcade", "arcade", "atomiswave", "cannonball")
        make("PC", "windows", "dos", "scummvm")
        make("Android", "android")
        make("Panasonic", "3do")
        make("Sinclair", "zxspectrum", "zx81")
        make("Amstrad", "amstradcpc")
        make("Mattel", "intellivision")
        make("Coleco", "colecovision")
        make("Sharp", "x1", "x68000")
        make("Philips", "cdimono1", "odyssey2")
    }

    fun of(platformId: String): String? = byId[platformId.lowercase()]

    /** Where [maker]'s systems go among others with as many games. */
    fun rank(maker: String?): Int = order.indexOf(maker).let { if (it < 0) order.size else it }
}
