package io.github.zahakj.tandem.data

/**
 * Where each Tandem system (and the common ES-DE folder names) lives in each scraping source: the
 * libretro-thumbnails folder, the ScreenScraper system id, the LaunchBox Games Database platform name and the
 * IGDB platform id. Null means the source has no such system.
 *
 * Pure Kotlin on purpose (no Android types), so the JVM unit tests can check it. The [io.github.zahakj.tandem.Game]
 * helpers are extension functions in ScrapeSources.kt.
 */
object ScrapeSystems {
    class Sys(val libretro: String?, val ss: Int?, val launchBox: String?, val igdb: Int?)

    private val table: Map<String, Sys> = buildMap {
        fun add(vararg ids: String, libretro: String? = null, ss: Int? = null, lb: String? = null, igdb: Int? = null) =
            ids.forEach { put(it, Sys(libretro, ss, lb, igdb)) }
        // Nintendo
        add("switch", ss = 225, lb = "Nintendo Switch", igdb = 130)
        add("n3ds", "3ds", libretro = "Nintendo - Nintendo 3DS", ss = 17, lb = "Nintendo 3DS", igdb = 37)
        add("nds", libretro = "Nintendo - Nintendo DS", ss = 15, lb = "Nintendo DS", igdb = 20)
        add("wiiu", ss = 18, lb = "Nintendo Wii U", igdb = 41)
        add("wii", libretro = "Nintendo - Wii", ss = 16, lb = "Nintendo Wii", igdb = 5)
        add("gc", "gamecube", "ngc", libretro = "Nintendo - GameCube", ss = 13, lb = "Nintendo GameCube", igdb = 21)
        add("n64", libretro = "Nintendo - Nintendo 64", ss = 14, lb = "Nintendo 64", igdb = 4)
        add("gba", libretro = "Nintendo - Game Boy Advance", ss = 12, lb = "Nintendo Game Boy Advance", igdb = 24)
        add("gbc", libretro = "Nintendo - Game Boy Color", ss = 10, lb = "Nintendo Game Boy Color", igdb = 22)
        add("gb", libretro = "Nintendo - Game Boy", ss = 9, lb = "Nintendo Game Boy", igdb = 33)
        add("nes", "famicom", libretro = "Nintendo - Nintendo Entertainment System", ss = 3, lb = "Nintendo Entertainment System", igdb = 18)
        add("fds", libretro = "Nintendo - Family Computer Disk System", ss = 106, lb = "Nintendo Famicom Disk System", igdb = 51)
        add("snes", "sfc", "superfamicom", libretro = "Nintendo - Super Nintendo Entertainment System", ss = 4, lb = "Super Nintendo Entertainment System", igdb = 19)
        add("virtualboy", libretro = "Nintendo - Virtual Boy", ss = 11, lb = "Nintendo Virtual Boy", igdb = 87)
        add("pokemini", libretro = "Nintendo - Pokemon Mini", ss = 211, lb = "Nintendo Pokemon Mini", igdb = 166)
        add("gameandwatch", libretro = "Handheld Electronic Game", ss = 52, lb = "Nintendo Game & Watch", igdb = 307)
        // Sony
        add("psx", "ps1", libretro = "Sony - PlayStation", ss = 57, lb = "Sony Playstation", igdb = 7)
        add("ps2", libretro = "Sony - PlayStation 2", ss = 58, lb = "Sony Playstation 2", igdb = 8)
        add("ps3", libretro = "Sony - PlayStation 3", ss = 59, lb = "Sony Playstation 3", igdb = 9)
        add("ps4", lb = "Sony Playstation 4", igdb = 48)
        add("psp", libretro = "Sony - PlayStation Portable", ss = 61, lb = "Sony PSP", igdb = 38)
        add("psvita", "vita", libretro = "Sony - PlayStation Vita", ss = 62, lb = "Sony Playstation Vita", igdb = 46)
        // Sega
        add("genesis", "megadrive", "md", libretro = "Sega - Mega Drive - Genesis", ss = 1, lb = "Sega Genesis", igdb = 29)
        add("mastersystem", "sms", libretro = "Sega - Master System - Mark III", ss = 2, lb = "Sega Master System", igdb = 64)
        add("gamegear", "gg", libretro = "Sega - Game Gear", ss = 21, lb = "Sega Game Gear", igdb = 35)
        add("segacd", "megacd", libretro = "Sega - Mega-CD - Sega CD", ss = 20, lb = "Sega CD", igdb = 78)
        add("sega32x", "32x", libretro = "Sega - 32X", ss = 19, lb = "Sega 32X", igdb = 30)
        add("pico", libretro = "Sega - PICO", lb = "Sega Pico", igdb = 339)
        add("saturn", libretro = "Sega - Saturn", ss = 22, lb = "Sega Saturn", igdb = 32)
        add("dreamcast", "dc", libretro = "Sega - Dreamcast", ss = 23, lb = "Sega Dreamcast", igdb = 23)
        add("sg1000", libretro = "Sega - SG-1000", ss = 109, lb = "Sega SG-1000", igdb = 84)
        add("naomi", libretro = "Sega - Naomi", ss = 56, lb = "Sega Naomi", igdb = 52)
        add("atomiswave", libretro = "Atomiswave", ss = 53, lb = "Sammy Atomiswave", igdb = 52)
        add("model3", ss = 55, lb = "Sega Model 3", igdb = 52)
        add("stv", lb = "Sega ST-V", igdb = 52)
        // Microsoft and PC
        add("xbox", libretro = "Microsoft - Xbox", ss = 32, lb = "Microsoft Xbox", igdb = 11)
        add("xbox360", ss = 33, lb = "Microsoft Xbox 360", igdb = 12)
        add("windows", "pc", "win", "winlator", "steam", ss = 138, lb = "Windows", igdb = 6)
        add("dos", libretro = "DOS", ss = 135, lb = "MS-DOS", igdb = 13)
        add("android", lb = "Android", igdb = 34)
        // Arcade
        add("arcade", "mame", libretro = "MAME", ss = 75, lb = "Arcade", igdb = 52)
        add("fbneo", libretro = "FBNeo - Arcade Games", ss = 75, lb = "Arcade", igdb = 52)
        add("neogeo", libretro = "SNK - Neo Geo", ss = 142, lb = "SNK Neo Geo AES", igdb = 80)
        add("neogeocd", libretro = "SNK - Neo Geo CD", ss = 70, lb = "SNK Neo Geo CD", igdb = 136)
        add("ngp", libretro = "SNK - Neo Geo Pocket", ss = 25, lb = "SNK Neo Geo Pocket", igdb = 119)
        add("ngpc", libretro = "SNK - Neo Geo Pocket Color", ss = 82, lb = "SNK Neo Geo Pocket Color", igdb = 120)
        // NEC
        add("pcengine", "tg16", libretro = "NEC - PC Engine - TurboGrafx 16", ss = 31, lb = "NEC TurboGrafx-16", igdb = 86)
        add("pcenginecd", "tg-cd", libretro = "NEC - PC Engine CD - TurboGrafx-CD", ss = 114, lb = "NEC TurboGrafx-CD", igdb = 150)
        add("supergrafx", libretro = "NEC - PC Engine SuperGrafx", ss = 105, lb = "PC Engine SuperGrafx", igdb = 128)
        add("pcfx", libretro = "NEC - PC-FX", ss = 72, lb = "NEC PC-FX", igdb = 274)
        add("pc88", ss = 221, lb = "NEC PC-8801", igdb = 125)
        add("pc98", libretro = "NEC - PC-98", ss = 208, lb = "NEC PC-9801", igdb = 149)
        // Atari and other consoles
        add("atari2600", libretro = "Atari - 2600", ss = 26, lb = "Atari 2600", igdb = 59)
        add("atari5200", libretro = "Atari - 5200", ss = 40, lb = "Atari 5200", igdb = 66)
        add("atari7800", libretro = "Atari - 7800", ss = 41, lb = "Atari 7800", igdb = 60)
        add("atarilynx", "lynx", libretro = "Atari - Lynx", ss = 28, lb = "Atari Lynx", igdb = 61)
        add("atarijaguar", "jaguar", libretro = "Atari - Jaguar", ss = 27, lb = "Atari Jaguar", igdb = 62)
        add("atarist", libretro = "Atari - ST", ss = 42, lb = "Atari ST", igdb = 63)
        add("3do", libretro = "The 3DO Company - 3DO", ss = 29, lb = "3DO Interactive Multiplayer", igdb = 50)
        add("cdimono1", "cdi", libretro = "Philips - CD-i", ss = 133, lb = "Philips CD-i", igdb = 117)
        add("colecovision", libretro = "Coleco - ColecoVision", ss = 48, lb = "ColecoVision", igdb = 68)
        add("intellivision", libretro = "Mattel - Intellivision", ss = 115, lb = "Mattel Intellivision", igdb = 67)
        add("vectrex", libretro = "GCE - Vectrex", ss = 102, lb = "GCE Vectrex", igdb = 70)
        add("channelf", libretro = "Fairchild - Channel F", ss = 80, lb = "Fairchild Channel F", igdb = 127)
        add("odyssey2", libretro = "Magnavox - Odyssey2", ss = 104, lb = "Magnavox Odyssey 2", igdb = 133)
        add("vc4000", lb = "Interton VC 4000")
        add("arcadia", lb = "Emerson Arcadia 2001")
        add("megaduck", libretro = "Welback - Mega Duck", lb = "Mega Duck")
        add("supervision", libretro = "Watara - Supervision", lb = "Watara Supervision")
        add("wonderswan", "ws", libretro = "Bandai - WonderSwan", ss = 45, lb = "WonderSwan", igdb = 57)
        add("wonderswancolor", "wsc", libretro = "Bandai - WonderSwan Color", ss = 46, lb = "WonderSwan Color", igdb = 123)
        add("uzebox", libretro = "Uzebox", lb = "Uzebox")
        // Computers
        add("amiga", libretro = "Commodore - Amiga", ss = 64, lb = "Commodore Amiga", igdb = 16)
        add("c64", libretro = "Commodore - 64", ss = 66, lb = "Commodore 64", igdb = 15)
        add("vic20", libretro = "Commodore - VIC-20", ss = 73, lb = "Commodore VIC-20", igdb = 71)
        add("plus4", libretro = "Commodore - Plus-4", ss = 99, lb = "Commodore Plus 4", igdb = 94)
        add("pet", lb = "Commodore PET", igdb = 90)
        add("amstradcpc", libretro = "Amstrad - CPC", ss = 65, lb = "Amstrad CPC", igdb = 25)
        add("msx", libretro = "Microsoft - MSX", ss = 113, lb = "Microsoft MSX", igdb = 27)
        add("zxspectrum", libretro = "Sinclair - ZX Spectrum", ss = 76, lb = "Sinclair ZX Spectrum", igdb = 26)
        add("zx81", libretro = "Sinclair - ZX 81", ss = 77, lb = "Sinclair ZX-81")
        add("x1", libretro = "Sharp - X1", ss = 220, lb = "Sharp X1", igdb = 77)
        add("x68000", libretro = "Sharp - X68000", ss = 79, lb = "Sharp X68000", igdb = 121)
        add("bbcmicro", ss = 37, lb = "BBC Microcomputer System", igdb = 69)
        add("apple2", ss = 86, lb = "Apple II", igdb = 75)
        add("ngage", lb = "Nokia N-Gage", igdb = 42)
        // Fantasy consoles and engines
        add("pico8", lb = "PICO-8")
        add("wasm4", lb = "WASM-4")
        add("arduboy", libretro = "Arduboy Inc - Arduboy", lb = "Arduboy")
        add("scummvm", libretro = "ScummVM", ss = 123, lb = "ScummVM")
    }

    /** The entry for the first of [ids] (system id, art folder names, ROM folder names) that is known. */
    fun of(ids: List<String>): Sys? = ids.firstNotNullOfOrNull { table[it.lowercase()] }

    fun launchBox(ids: List<String>): String? = of(ids)?.launchBox
    fun igdb(ids: List<String>): Int? = of(ids)?.igdb
    fun libretro(ids: List<String>): String? = of(ids)?.libretro
    fun screenScraper(ids: List<String>): Int? = of(ids)?.ss

    /** Systems whose heroes and logos SteamGridDB's community covers best (PC and modern consoles). */
    private val sgdbFirst = setOf("windows", "pc", "win", "winlator", "steam", "switch", "ps4", "android", "xbox360", "ps3", "wiiu")

    fun prefersSteamGridDb(ids: List<String>): Boolean = ids.any { it.lowercase() in sgdbFirst }
}
