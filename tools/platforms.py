"""Generates app/src/main/assets/platforms.json. Run: python3 tools/platforms.py app/src/main/assets/platforms.json

Launch details come from each emulator's own manifests/sources and public frontend docs (ES-DE's MIT-licensed Android
rules were used to cross-check). Entries marked "verify" in the "note" field were not confirmed against source.
"""
import json
import sys

VIEW = "android.intent.action.VIEW"
MAIN = "android.intent.action.MAIN"

CORE_NAMES = {
    "mgba": "mGBA", "gpsp": "gpSP", "vbam": "VBA-M", "gambatte": "Gambatte", "sameboy": "SameBoy", "gearboy": "Gearboy",
    "fceumm": "FCEUmm", "nestopia": "Nestopia", "mesen": "Mesen", "quicknes": "QuickNES", "snes9x": "Snes9x",
    "snes9x2010": "Snes9x 2010", "bsnes": "bsnes", "mesen-s": "Mesen-S", "mupen64plus_next_gles3": "Mupen64Plus-Next",
    "parallel_n64": "ParaLLEl N64", "genesis_plus_gx": "Genesis Plus GX", "picodrive": "PicoDrive",
    "gearsystem": "Gearsystem", "smsplus": "SMS Plus GX", "mednafen_pce_fast": "Beetle PCE Fast",
    "mednafen_pce": "Beetle PCE", "mednafen_supergrafx": "Beetle SuperGrafx", "mednafen_pcfx": "Beetle PC-FX",
    "fbneo": "FinalBurn Neo", "mame2003_plus": "MAME 2003-Plus", "mamearcade": "MAME", "mame2010": "MAME 2010",
    "geolith": "Geolith", "neocd": "NeoCD", "stella": "Stella", "atari800": "Atari800", "a5200": "a5200",
    "prosystem": "ProSystem", "virtualjaguar": "Virtual Jaguar", "handy": "Handy", "mednafen_lynx": "Beetle Lynx",
    "hatari": "Hatari", "flycast": "Flycast", "mednafen_psx_hw": "Beetle PSX HW", "pcsx_rearmed": "PCSX ReARMed",
    "swanstation": "SwanStation", "ppsspp": "PPSSPP", "dolphin": "Dolphin", "yabasanshiro": "YabaSanshiro",
    "mednafen_saturn": "Beetle Saturn", "opera": "Opera", "cap32": "Caprice32", "crocods": "CrocoDS",
    "mamemess": "MAME (MESS)", "arduous": "Arduous", "b2": "b2", "freechaf": "FreeChaF", "freeintv": "FreeIntv",
    "bluemsx": "blueMSX", "fmsx": "fMSX", "gearcoleco": "Gearcoleco", "vice_x64sc": "VICE x64sc", "vice_x64": "VICE x64",
    "puae": "PUAE", "vice_xpet": "VICE xpet", "vice_xplus4": "VICE xplus4", "vice_xvic": "VICE xvic",
    "prboom": "PrBoom", "dosbox_pure": "DOSBox Pure", "nxengine": "NXEngine", "cannonball": "Cannonball",
    "jaxe": "JAXE", "reminiscence": "REminiscence", "lowresnx": "LowRes NX", "o2em": "O2EM", "sameduck": "SameDuck",
    "quasi88": "QUASI88", "np2kai": "Neko Project II Kai", "pokemini": "PokeMini", "mednafen_vb": "Beetle VB",
    "fake08": "FAKE-08", "retro8": "Retro8", "mu": "Mu", "same_cdi": "SAME CDi", "tyrquake": "TyrQuake",
    "vitaquake2": "vitaQuake 2", "easyrpg": "EasyRPG", "scummvm": "ScummVM", "x1": "X Millennium", "px68k": "PX68k",
    "tic80": "TIC-80", "uzem": "Uzem", "vecx": "vecx", "wasm4": "WASM-4", "potator": "Potator",
    "mednafen_wswan": "Beetle Cygne", "81": "EightyOne", "fuse": "Fuse", "squirreljme": "SquirrelJME",
    "melondsds": "melonDS DS", "desmume": "DeSmuME", "citra": "Citra", "gw": "Game & Watch", "race": "RACE",
    "mednafen_ngp": "Beetle NeoPop", "supermodel": "Supermodel", "pcsx2": "LRPS2", "ffmpeg": "FFmpeg",
}


def ra(core):
    return {"name": "RetroArch · " + CORE_NAMES.get(core, core), "core": core}


def sa(name, component, action=VIEW, game="data", store=None, note=None, **kw):
    p = {"name": name, "component": component, "action": action, "game": game}
    if store:
        p["store"] = store
    if note:
        p["note"] = note
    p.update(kw)
    return p


AZAHAR = sa("Azahar", "org.azahar_emu.azahar/org.citra.citra_emu.activities.EmulationActivity",
            store="https://github.com/azahar-emu/azahar/releases")
MELONDS_DUAL = sa("melonDS Dual", "me.magnum.melondualds/me.magnum.melonds.ui.emulator.EmulatorActivity",
                  action="me.magnum.melondualds.LAUNCH_ROM", game="extra-uri:uri",
                  note="verify: fork of melonDS; other frontends send me.magnum.melonds(.dev).LAUNCH_ROM")
MELONDS = sa("melonDS", "me.magnum.melonds/me.magnum.melonds.ui.emulator.EmulatorActivity",
             action="me.magnum.melonds.LAUNCH_ROM", game="extra-uri:uri",
             store="https://github.com/rafaelvcaetano/melonDS-android/releases")
DRASTIC = sa("DraStic", "com.dsemu.drastic/com.dsemu.drastic.DraSticActivity")
EDEN = sa("Eden", "dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity",
          action="android.nfc.action.TECH_DISCOVERED", store="https://github.com/eden-emulator/Releases/releases",
          note="verify store page")
CEMU = sa("Cemu", "info.cemu.cemu/info.cemu.cemu.emulation.EmulationActivity",
          store="https://github.com/SSimco/Cemu/releases", note="verify store page")
VITA3K = sa("Vita3K", "org.vita3k.emulator/org.vita3k.emulator.Emulator", action=MAIN, game="none",
            extras={"AppStartParameters": ["-r", "{file.id}"]}, store="https://github.com/Vita3K/Vita3K-Android/releases")
DOLPHIN = sa("Dolphin", "org.dolphinemu.dolphinemu/org.dolphinemu.dolphinemu.ui.main.MainActivity", action=MAIN,
             game="extra-uri:AutoStartFile")
PPSSPP = sa("PPSSPP", "org.ppsspp.ppsspp/org.ppsspp.ppsspp.PpssppActivity", categories=["android.intent.category.DEFAULT"])
PPSSPP_GOLD = sa("PPSSPP Gold", "org.ppsspp.ppssppgold/org.ppsspp.ppsspp.PpssppActivity",
                 categories=["android.intent.category.DEFAULT"])
DUCKSTATION = sa("DuckStation", "com.github.stenzek.duckstation/com.github.stenzek.duckstation.EmulationActivity",
                 action=MAIN, game="extra-uri:bootPath", extras={"resumeState": False})
ARMSX1 = sa("ARMSX1", "com.nanodata.armsx/com.armsx2.Main", store="https://github.com/ARMSX2/ARMSX1/releases",
            note="verify")
ARMSX2 = sa("ARMSX2", "com.armsx2/com.armsx2.MainActivity", store="https://github.com/ARMSX2/ARMSX2/releases",
            note="verify: MainActivity is an alias of com.armsx2.Main")
NETHERSX2 = sa("NetherSX2", "xyz.aethersx2.android/xyz.aethersx2.android.EmulationActivity", action=MAIN,
               game="extra-uri:bootPath", store="https://github.com/Trixarian/NetherSX2-patch/releases")
APS3E = sa("aPS3e", "aenu.aps3e/aenu.aps3e.EmulatorActivity", action="aenu.intent.action.APS3E",
           game="extra-uri:iso_uri", store="https://github.com/aenu1/aps3e/releases",
           byExtension={
               "dir": {"game": "none", "extras": {"game_dir": "{file.path}"}},
               "ps3": {"game": "none", "extras": {
                   "game_dir": "{storage}/Android/data/{package}/files/aps3e/config/dev_hdd0/game/{file.id}"}},
           })
ARMSX3 = sa("ARMSX3", "com.armsx3/com.armsx2.Main", store="https://github.com/ARMSX2/ARMSX3/releases",
            byExtension={
                "dir": {"game": "none", "extras": {"path": "{file.path}"}},
                "ps3": {"game": "none", "extras": {"title_id": "{file.id}"}},
            })
FLYCAST = sa("Flycast", "com.flycast.emulator/com.flycast.emulator.MainActivity",
             store="https://github.com/flyinghead/flycast/releases")
REDREAM = sa("Redream", "io.recompiled.redream/io.recompiled.redream.MainActivity")
YABA2 = sa("Yaba Sanshiro 2", "org.devmiyax.yabasanshioro2/org.uoyabause.android.Yabause",
           game="extra-path:org.uoyabause.android.FileNameUri", note="verify")
X1BOX = sa("X1 BOX", "com.izzy2lost.x1box/com.izzy2lost.x1box.LauncherActivity",
           store="https://github.com/izzy2lost/x1box/releases")
XENDROID = sa("XenDroid", "xendroid.compose/xendroid.compose.EmulatorHostActivity", note="verify: some frontends pass game_uri as an extra",
              extras={"game_uri": "{file.uri}"})
AX360E = sa("aX360e", "aenu.ax360e/aenu.ax360e.EmulatorActivity", action="aenu.intent.action.AX360E",
            game="extra-uri:game_uri", store="https://github.com/aenu1/ax360e/releases")
GAMENATIVE = sa("GameNative", "app.gamenative/app.gamenative.MainActivity", action="app.gamenative.LAUNCH_GAME",
                game="none", extras={"app_id": {"int": "{file.id}"}, "game_source": "STEAM"},
                store="https://github.com/utkarshdalal/GameNative/releases",
                byExtension={
                    "pcgame": {"extras": {"game_source": "CUSTOM_GAME"}},
                    "customgame": {"extras": {"game_source": "CUSTOM_GAME"}},
                    "steam": {"extras": {"game_source": "STEAM"}},
                    "steamappid": {"extras": {"game_source": "STEAM"}},
                    "epic": {"extras": {"game_source": "EPIC"}},
                    "epicgame": {"extras": {"game_source": "EPIC"}},
                    "gog": {"extras": {"game_source": "GOG"}},
                    "amazon": {"extras": {"game_source": "AMAZON"}},
                })
WINLATOR = sa("Winlator", "com.winlator/com.winlator.XServerDisplayActivity", action=MAIN, game="extra-path:shortcut_path",
              store="https://github.com/brunodev85/winlator/releases")
WINLATOR_CMOD = sa("Winlator Cmod", "com.winlator.cmod/com.winlator.cmod.XServerDisplayActivity", action=MAIN,
                   game="extra-path:shortcut_path", store="https://github.com/coffincolors/winlator/releases", note="verify store page")
MAME4DROID = sa("MAME4droid", "com.seleuco.mame4d2024/com.seleuco.mame4droid.MAME4droid")
SCUMMVM = sa("ScummVM", "org.scummvm.scummvm/org.scummvm.scummvm.SplashActivity", action=MAIN, game="none",
             data="{file.id}", note="verify: .scummvm files hold the game id")
J2ME = sa("J2ME Loader", "ru.playsoftware.j2meloader/ru.playsoftware.j2meloader.MainActivity")
EKA2L1 = sa("EKA2L1", "com.github.eka2l1/com.github.eka2l1.emu.EmulatorActivity", note="verify")
PICO8 = sa("PICO-8", "io.wip.pico8/com.godot.game.GodotAppLauncher", note="verify")
RUFFLE = sa("Ruffle", "rs.ruffle/rs.ruffle.PlayerActivity", note="verify")
EASYRPG = sa("EasyRPG", "org.easyrpg.player/org.easyrpg.player.player.EasyRpgPlayerActivity", note="verify")
MUPEN = sa("Mupen64Plus FZ", "org.mupen64plusae.v3.fzurita/paulscode.android.mupen64plusae.SplashActivity")
VVB = sa("Virtual Virtual Boy", "com.simongellis.vvb/com.simongellis.vvb.MainActivity", note="verify")
MOONLIGHT = sa("Moonlight", "com.limelight/com.limelight.ShortcutTrampoline", game="none",
               extras={"UUID": "{tags.uuid}", "AppId": "{tags.appid}"},
               note="verify: .moonlight file lines '[uuid] <host uuid>' and '[appid] <app id>'")
STARBOARD = sa("Starboard", "org.force9.starboard/org.force9.starboard.ui.game.GameActivity", game="extra-path:port_file",
               note="verify")
OPEN_WITH = sa("Default app", "*", game="none", data="{file.uri}", type="{file.mime}")

P = []


def plat(id, name, short, exts, players, folders=None, aliases=(), art=None, skip=None):
    p = {"id": id, "name": name, "short": short}
    p["folders"] = folders or [id]
    p["extensions"] = exts.split()
    if aliases:
        p["aliases"] = list(aliases)
    if art:
        p["art"] = art
    if skip:
        p["skipDirs"] = skip
    p["players"] = players
    P.append(p)


ARC = "zip 7z"
# --- Nintendo ---
plat("n3ds", "Nintendo 3DS", "3DS", "3ds cia cci cxi 3dsx app zcci zcxi z3dsx elf axf", [AZAHAR, ra("citra")],
     folders=["n3ds", "3ds"], aliases=["3ds"], art=["n3ds", "3ds"],
     skip=["cheats", "config", "gpu_drivers", "log", "nand", "sdmc", "shaders", "sysdata", "load", "dump"])
plat("nds", "Nintendo DS", "DS", "nds dsi ids zip 7z", [MELONDS_DUAL, MELONDS, DRASTIC, ra("melondsds"), ra("desmume")],
     aliases=["ndsi", "dsi"])
plat("switch", "Nintendo Switch", "Switch", "nsp xci nca nro nso", [EDEN])
plat("wiiu", "Nintendo Wii U", "Wii U", "wux wud wua rpx wuhb", [CEMU])
plat("wii", "Nintendo Wii", "Wii", "iso rvz wbfs wia gcz ciso wad dol elf m3u", [DOLPHIN, ra("dolphin")],
     aliases=["wiiware"])
plat("gc", "Nintendo GameCube", "GC", "iso rvz gcz gcm ciso wia tgc dol elf m3u", [DOLPHIN, ra("dolphin")],
     folders=["gc", "gamecube", "ngc"], aliases=["gamecube", "ngc"])
plat("n64", "Nintendo 64", "N64", "n64 z64 v64 ndd u1 " + ARC, [ra("mupen64plus_next_gles3"), ra("parallel_n64"), MUPEN])
plat("snes", "Super Nintendo", "SNES", "sfc smc fig swc bs st bsx " + ARC, [ra("snes9x"), ra("bsnes"), ra("mesen-s")],
     folders=["snes", "sfc", "snesna", "satellaview", "snesmsu1"], aliases=["sfc", "satellaview", "snesmsu1"])
plat("nes", "Nintendo Entertainment System", "NES", "nes unf unif " + ARC, [ra("fceumm"), ra("nestopia"), ra("mesen")],
     folders=["nes", "famicom"], aliases=["famicom"])
plat("fds", "Famicom Disk System", "FDS", "fds qd " + ARC, [ra("fceumm"), ra("nestopia"), ra("mesen")])
plat("gba", "Game Boy Advance", "GBA", "gba agb gbz bin " + ARC, [ra("mgba"), ra("gpsp"), ra("vbam")])
plat("gbc", "Game Boy Color", "GBC", "gbc gb cgb sgb " + ARC, [ra("gambatte"), ra("mgba"), ra("sameboy"), ra("gearboy")])
plat("gb", "Game Boy", "GB", "gb dmg sgb " + ARC, [ra("gambatte"), ra("mgba"), ra("sameboy"), ra("gearboy")])
plat("virtualboy", "Virtual Boy", "VB", "vb vboy " + ARC, [ra("mednafen_vb"), VVB])
plat("pokemini", "Pokémon Mini", "Mini", "min " + ARC, [ra("pokemini")])
plat("gameandwatch", "Game & Watch", "G&W", "mgw " + ARC, [ra("gw"), ra("mamemess")], aliases=["gw"])
# --- Sony ---
plat("psx", "PlayStation", "PS1", "cue chd pbp m3u iso img ccd mds toc ecm exe " + "7z zip",
     [DUCKSTATION, ra("swanstation"), ra("pcsx_rearmed"), ra("mednafen_psx_hw"), ARMSX1], folders=["psx", "ps1"],
     aliases=["ps1", "playstation"])
plat("ps2", "PlayStation 2", "PS2", "iso chd cso zso gz bin img mdf nrg isz elf m3u",
     [ARMSX2, NETHERSX2, ra("pcsx2")], aliases=["playstation2", "namcops2"])
plat("ps3", "PlayStation 3", "PS3", "iso ps3 ps3folder ps3titleid", [APS3E, ARMSX3])
plat("psp", "PlayStation Portable", "PSP", "iso cso chd pbp elf prx", [PPSSPP, PPSSPP_GOLD, ra("ppsspp")],
     aliases=["pspminis"])
plat("psvita", "PlayStation Vita", "Vita", "dpt psvita", [VITA3K], folders=["psvita", "vita"], aliases=["vita"])
# --- Sega ---
plat("genesis", "Sega Genesis", "Genesis", "md gen smd bin 68k sgd chd cue m3u " + ARC,
     [ra("genesis_plus_gx"), ra("picodrive")], folders=["genesis", "megadrive", "md", "genesismsu"],
     aliases=["megadrive", "genesismsu"], art=["genesis", "megadrive"])
plat("mastersystem", "Sega Master System", "SMS", "sms bin " + ARC, [ra("genesis_plus_gx"), ra("gearsystem"), ra("smsplus")],
     folders=["mastersystem", "sms", "master"], aliases=["master", "sms"])
plat("gamegear", "Sega Game Gear", "GG", "gg bin " + ARC, [ra("genesis_plus_gx"), ra("gearsystem"), ra("smsplus")])
plat("sg1000", "Sega SG-1000", "SG-1000", "sg sc bin " + ARC, [ra("gearsystem"), ra("genesis_plus_gx"), ra("bluemsx")])
plat("sega32x", "Sega 32X", "32X", "32x bin md " + ARC, [ra("picodrive")])
plat("segacd", "Sega CD", "Sega CD", "chd cue iso m3u", [ra("genesis_plus_gx"), ra("picodrive")], aliases=["megacd"])
plat("pico", "Sega Pico", "Pico", "md bin " + ARC, [ra("picodrive")])
plat("saturn", "Sega Saturn", "Saturn", "chd cue iso ccd mds toc m3u zip", [ra("yabasanshiro"), ra("mednafen_saturn"), YABA2])
plat("dreamcast", "Sega Dreamcast", "DC", "chd gdi cdi cue m3u elf lst " + ARC, [FLYCAST, ra("flycast"), REDREAM],
     aliases=["dc"])
plat("naomi", "Sega NAOMI", "NAOMI", "zip 7z dat lst", [FLYCAST, ra("flycast")])
plat("atomiswave", "Atomiswave", "AW", "zip 7z", [FLYCAST, ra("flycast")])
plat("model3", "Sega Model 3", "Model 3", "zip", [ra("supermodel")])
plat("stv", "Sega Titan Video", "ST-V", "zip", [ra("mednafen_saturn")])
# --- Microsoft / PC ---
plat("xbox", "Xbox", "Xbox", "iso xiso", [X1BOX])
plat("xbox360", "Xbox 360", "360", "iso xex zar", [XENDROID, AX360E])
plat("windows", "Windows", "PC", "pcgame steam epic gog amazon customgame steamappid epicgame desktop lnk",
     [GAMENATIVE, WINLATOR, WINLATOR_CMOD], folders=["windows", "pc", "steam"], aliases=["steam", "pc"])
plat("dos", "DOS", "DOS", "zip dosz exe com bat iso cue img m3u conf", [ra("dosbox_pure")], aliases=["pcdos"])
# --- Arcade ---
plat("arcade", "Arcade", "Arcade", "zip 7z chd", [ra("fbneo"), ra("mame2003_plus"), ra("mamearcade"), MAME4DROID],
     folders=["arcade", "mame", "fbneo", "cps1", "cps2", "cps3"], aliases=["mame", "fbneo", "cps1", "cps2", "cps3"])
plat("neogeo", "Neo Geo", "Neo Geo", "zip 7z", [ra("fbneo"), ra("geolith")])
plat("neogeocd", "Neo Geo CD", "NGCD", "chd cue", [ra("neocd")])
plat("ngp", "Neo Geo Pocket", "NGP", "ngp " + ARC, [ra("mednafen_ngp"), ra("race")])
plat("ngpc", "Neo Geo Pocket Color", "NGPC", "ngc ngpc ngp " + ARC, [ra("mednafen_ngp"), ra("race")])
# --- NEC ---
plat("pcengine", "PC Engine / TurboGrafx-16", "PCE", "pce sgx " + ARC, [ra("mednafen_pce_fast"), ra("mednafen_pce")],
     folders=["pcengine", "tg16", "turbografx16"], aliases=["tg16", "turbografx16"])
plat("pcenginecd", "PC Engine CD / TurboGrafx-CD", "PCE CD", "chd cue ccd m3u", [ra("mednafen_pce_fast"), ra("mednafen_pce")],
     folders=["pcenginecd", "tgcd"], aliases=["tgcd"])
plat("supergrafx", "SuperGrafx", "SGX", "sgx pce " + ARC, [ra("mednafen_supergrafx"), ra("mednafen_pce")])
plat("pcfx", "PC-FX", "PC-FX", "chd cue ccd toc", [ra("mednafen_pcfx")])
plat("pc88", "NEC PC-88", "PC-88", "d88 u88 m3u", [ra("quasi88")])
plat("pc98", "NEC PC-98", "PC-98", "d98 fdi hdi hdm fdd nhd tfd xdf zip", [ra("np2kai")])
# --- Atari ---
plat("atari2600", "Atari 2600", "2600", "a26 bin " + ARC, [ra("stella")])
plat("atari5200", "Atari 5200", "5200", "a52 bin car " + ARC, [ra("a5200"), ra("atari800")])
plat("atari7800", "Atari 7800", "7800", "a78 bin " + ARC, [ra("prosystem")])
plat("atarijaguar", "Atari Jaguar", "Jaguar", "j64 jag abs cof rom " + ARC, [ra("virtualjaguar")], aliases=["jaguar"])
plat("atarilynx", "Atari Lynx", "Lynx", "lnx o " + ARC, [ra("handy"), ra("mednafen_lynx")], aliases=["lynx"])
plat("atarist", "Atari ST", "ST", "st msa stx dim ipf m3u " + ARC, [ra("hatari")])
# --- Other consoles ---
plat("3do", "3DO", "3DO", "iso chd cue", [ra("opera")])
plat("cdimono1", "Philips CD-i", "CD-i", "chd cue iso", [ra("same_cdi")], aliases=["cdi"])
plat("colecovision", "ColecoVision", "Coleco", "col cv bin rom " + ARC, [ra("gearcoleco"), ra("bluemsx")], aliases=["coleco"])
plat("intellivision", "Intellivision", "Intv", "int bin rom " + ARC, [ra("freeintv")])
plat("vectrex", "Vectrex", "Vectrex", "vec bin " + ARC, [ra("vecx")])
plat("channelf", "Fairchild Channel F", "Ch F", "chf bin " + ARC, [ra("freechaf")])
plat("odyssey2", "Magnavox Odyssey 2", "O2", "bin " + ARC, [ra("o2em")], aliases=["videopac", "g7400"])
plat("vc4000", "Interton VC 4000", "VC4000", "bin rom " + ARC, [ra("mamemess")])
plat("arcadia", "Arcadia 2001", "Arcadia", "bin " + ARC, [ra("mamemess")])
plat("megaduck", "Mega Duck", "Duck", "bin " + ARC, [ra("sameduck")])
plat("supervision", "Watara Supervision", "SV", "sv bin " + ARC, [ra("potator")])
plat("wonderswan", "WonderSwan", "WS", "ws " + ARC, [ra("mednafen_wswan")], aliases=["ws"])
plat("wonderswancolor", "WonderSwan Color", "WSC", "wsc " + ARC, [ra("mednafen_wswan")], aliases=["wsc"])
plat("uzebox", "Uzebox", "Uzebox", "uze " + ARC, [ra("uzem")])
# --- Computers ---
plat("amiga", "Commodore Amiga", "Amiga", "adf adz dms ipf hdf hdz lha m3u " + ARC, [ra("puae")])
plat("c64", "Commodore 64", "C64", "d64 d71 d81 t64 tap prg crt g64 m3u " + ARC, [ra("vice_x64sc"), ra("vice_x64")])
plat("pet", "Commodore PET", "PET", "d64 prg tap t64 " + ARC, [ra("vice_xpet")])
plat("plus4", "Commodore Plus/4", "Plus/4", "d64 prg tap t64 " + ARC, [ra("vice_xplus4")])
plat("vic20", "Commodore VIC-20", "VIC-20", "20 40 60 a0 b0 crt d64 prg tap t64 " + ARC, [ra("vice_xvic")])
plat("amstradcpc", "Amstrad CPC", "CPC", "dsk sna cdt voc tap cpr m3u " + ARC, [ra("cap32"), ra("crocods")], aliases=["cpc"])
plat("msx", "MSX", "MSX", "rom mx1 mx2 dsk cas col ri " + ARC, [ra("bluemsx"), ra("fmsx")], folders=["msx", "msx1", "msx2"])
plat("zxspectrum", "ZX Spectrum", "ZX", "tzx tap z80 sna dsk scl trd rzx " + ARC, [ra("fuse")])
plat("zx81", "ZX81", "ZX81", "p t81 tzx " + ARC, [ra("81")])
plat("x1", "Sharp X1", "X1", "2hd 88d d88 dx1 dup hdm tap cmd " + ARC, [ra("x1")])
plat("x68000", "Sharp X68000", "X68K", "dim d88 88d hdm dup 2hd xdf hdf cmd m3u " + ARC, [ra("px68k")])
plat("bbcmicro", "BBC Micro", "BBC", "ssd dsd", [ra("b2")])
plat("apple2", "Apple II", "Apple II", "dsk do po nib woz 2mg cmd", [ra("mamemess")], aliases=["appleii"])
plat("palm", "Palm OS", "Palm", "prc pdb pqa img", [ra("mu")])
plat("j2me", "Java ME", "J2ME", "jar jad", [J2ME, ra("squirreljme")], aliases=["javame"])
plat("ngage", "Nokia N-Gage", "N-Gage", "ngage zip json", [EKA2L1])
# --- Engines and fantasy consoles ---
plat("pico8", "PICO-8", "PICO-8", "p8 png", [ra("fake08"), ra("retro8"), PICO8])
plat("tic80", "TIC-80", "TIC-80", "tic", [ra("tic80")])
plat("wasm4", "WASM-4", "WASM-4", "wasm", [ra("wasm4")])
plat("lowresnx", "LowRes NX", "LowRes", "nx", [ra("lowresnx")])
plat("arduboy", "Arduboy", "Arduboy", "hex arduboy", [ra("arduous")])
plat("chip8", "CHIP-8", "CHIP-8", "ch8 " + ARC, [ra("jaxe")])
plat("doom", "DOOM", "DOOM", "wad iwad pwad", [ra("prboom")])
plat("quake", "Quake", "Quake", "pak", [ra("tyrquake")])
plat("quake2", "Quake II", "Quake II", "pak", [ra("vitaquake2")])
plat("cavestory", "Cave Story", "Cave Story", "exe", [ra("nxengine")])
plat("cannonball", "Cannonball (OutRun)", "OutRun", "88 game", [ra("cannonball")])
plat("flashback", "Flashback", "Flashback", "map aba seq lev", [ra("reminiscence")])
plat("easyrpg", "RPG Maker", "RPG", "ldb easyrpg zip", [ra("easyrpg"), EASYRPG], aliases=["rpgmaker"])
plat("scummvm", "ScummVM", "ScummVM", "scummvm svm", [ra("scummvm"), SCUMMVM])
plat("flash", "Adobe Flash", "Flash", "swf", [RUFFLE], aliases=["adobeflash"])
plat("ports", "Ports", "Ports", "port", [STARBOARD])
# --- Streaming and media ---
plat("moonlight", "Moonlight", "Stream", "moonlight", [MOONLIGHT])
plat("videos", "Videos", "Video", "mp4 mkv webm avi mov m4v mpg mpeg", [OPEN_WITH], aliases=["videoplayer"])
plat("books", "Books", "Books", "pdf epub cbz cbr mobi azw3 djvu fb2", [OPEN_WITH], aliases=["ebook", "bookreader"])

comment = (
    "Systems Tandem knows about. Copy this file to ROMs/tandem/platforms.json to customise it. "
    "'folders' are looked up under the ROMs folder (or full paths); subfolders become folders in Tandem. "
    "'aliases' are other names for the system (used to match Cocoon/Daijisho platform files). "
    "Each system lists its 'players' (emulators); the first installed one is the default. "
    "A player is either {\"core\": \"mgba\"} (RetroArch with that core; add \"package\" for another RetroArch build) or "
    "{\"component\": \"pkg/activity\", \"action\": ..., \"game\": ..., \"extras\": {...}}. "
    "'game' says how the emulator receives the game: 'data' (shared link as the intent data), 'data-file' (file:// link), "
    "'extra-uri:<key>' (shared link in a string extra), 'extra-path:<key>' (file path in a string extra) or 'none'. "
    "Extras keep their JSON type: string, list (string array), true/false, number; templated numbers are written "
    "{\"int\": \"{file.id}\"} (also long, float, bool, uri). 'data' and 'type' are templates for the intent data and MIME type. "
    "Templates: {file.path} {file.uri} {file.fileuri} {file.name} {file.stem} {file.ext} {file.dir} {file.mime} "
    "{file.id} (the id inside a small shortcut file such as a Vita .dpt or GameNative .pcgame) {file.text} "
    "{tags.<name>} (a '[name] value' line in the file) {storage} {roms} {package}. "
    "'byExtension' changes a player for one extension, or 'dir' for games that are folders. "
    "'store' is where to get the emulator (GitHub releases open in Obtainium when it is installed); without it the Play Store is used. "
    "'note' marks launch details still to verify on a device. Component '*' opens the file with the default app."
)
out = {"_comment": comment, "platforms": P}
json.dump(out, open(sys.argv[1], "w"), indent=1, ensure_ascii=False)
print(len(P), "systems")
