# Systems and emulators

Which systems Tandem knows, which emulators it starts, and how to add your own by editing `platforms.json`: no rebuild or app update, just change a file and rescan.

## What comes built in

Tandem knows 104 systems. The common ones:

| System | Folders under `ROMs/` | Main file types | Emulators |
| --- | --- | --- | --- |
| Nintendo 3DS | `n3ds`, `3ds` | `.3ds` `.cia` `.cci` `.cxi` `.3dsx` `.zcci` | Azahar, RetroArch (Citra) |
| Nintendo DS | `nds` | `.nds` `.dsi` `.zip` `.7z` | melonDS Dual, melonDS, DraStic, RetroArch |
| Nintendo Switch | `switch` | `.nsp` `.xci` `.nca` `.nro` | Eden |
| Nintendo Wii U | `wiiu` | `.wux` `.wud` `.wua` `.rpx` `.wuhb` | Cemu |
| Wii, GameCube | `wii`, `gc` | `.iso` `.rvz` `.wbfs` `.gcz` | Dolphin, RetroArch |
| Nintendo 64 | `n64` | `.n64` `.z64` `.v64` `.zip` | RetroArch (Mupen64Plus-Next, ParaLLEl), Mupen64Plus FZ |
| NES, SNES | `nes`, `snes` | `.nes` `.sfc` `.smc` `.zip` | RetroArch (FCEUmm, Nestopia, Mesen, Snes9x, bsnes) |
| Game Boy, Color, Advance | `gb`, `gbc`, `gba` | `.gb` `.gbc` `.gba` `.zip` `.7z` | RetroArch (Gambatte, SameBoy, mGBA, gpSP) |
| PlayStation | `psx`, `ps1` | `.cue` `.chd` `.pbp` `.m3u` `.iso` | DuckStation, RetroArch, ARMSX1 |
| PlayStation 2 | `ps2` | `.iso` `.chd` `.cso` | ARMSX2, NetherSX2 |
| PSP | `psp` | `.iso` `.cso` `.chd` `.pbp` | PPSSPP |
| PS Vita | `psvita`, `vita` | `.dpt` `.psvita` | Vita3K |
| Genesis, Master System, Game Gear | `genesis`, `megadrive`, `mastersystem`, `gamegear` | `.md` `.gen` `.sms` `.gg` `.zip` | RetroArch (Genesis Plus GX, PicoDrive) |
| Saturn, Dreamcast | `saturn`, `dreamcast` | `.chd` `.cue` `.gdi` `.cdi` | Yaba Sanshiro, Flycast, Redream, RetroArch |
| Arcade, Neo Geo | `arcade`, `mame`, `fbneo`, `neogeo` | `.zip` `.7z` `.chd` | RetroArch (FinalBurn Neo, MAME), MAME4droid |
| Windows | `windows`, `pc`, `steam` | `.pcgame` `.steam` `.desktop` and other shortcuts | GameNative, Winlator |
| DOS, ScummVM | `dos`, `scummvm` | `.zip` `.dosz` `.exe` `.scummvm` | DOSBox Pure, ScummVM |

The built-in [`platforms.json`](../../app/src/main/assets/platforms.json) lists every system, folder name and file
type. Tandem offers each installed emulator that fits, and a system appears only when its folder has at least one
game.

## Using your own platforms.json

The built-in list is [`app/src/main/assets/platforms.json`](../../app/src/main/assets/platforms.json). To change it:

1. Copy that file to `ROMs/tandem/platforms.json` on your device.
2. Edit it: add a system, add an emulator, change a folder name.
3. In Tandem, **Settings › Library › Rescan games**.

When `ROMs/tandem/platforms.json` exists, Tandem uses it instead of the built-in list, entirely. Start from a copy of the built-in file, so you keep the systems you didn't mean to change.

> [!TIP]
> If Tandem shows no systems after an edit, the file probably has a JSON syntax error, like a missing comma. Paste it into any JSON validator to find the line.

## The format

The file is one object with a `platforms` list. Each entry is a system:

```json
{
  "platforms": [
    {
      "id": "gba",
      "name": "Game Boy Advance",
      "short": "GBA",
      "folders": ["gba"],
      "extensions": ["gba", "zip", "7z"],
      "skipDirs": [],
      "art": ["gba"],
      "players": [
        {
          "name": "RetroArch",
          "component": "com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture",
          "action": "android.intent.action.MAIN",
          "game": "extra-path:ROM",
          "extras": {
            "LIBRETRO": "/data/data/com.retroarch.aarch64/cores/mgba_libretro_android.so",
            "CONFIGFILE": "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg"
          }
        }
      ]
    }
  ]
}
```

### System fields

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | Yes | A short, unique, lowercase name. It is part of each game's identity (`gba/Some Game.gba`), so changing it later makes Tandem forget favorites and play time for that system. |
| `name` | Yes | The full name, shown on system cards and the bottom screen. |
| `short` | No | A few letters for tiles and pills, like `GBA`. Defaults to `name`. |
| `folders` | No | Folder names under `ROMs/` to scan. Defaults to the `id`. |
| `extensions` | Yes | File extensions that count as games, without the dot. Case doesn't matter. |
| `skipDirs` | No | Subfolder names to ignore, for emulator data kept next to the games. |
| `art` | No | Folder names under `ROMs/downloaded_media/` to take artwork from. Defaults to the `id`. |
| `players` | Yes | The emulators that can run this system. The first one is the default. |

### Emulator fields

Each entry in `players` describes how to start one emulator.

| Field | Required | Meaning |
| --- | --- | --- |
| `name` | No | Shown in the game menu. Defaults to the package name. |
| `component` | Yes | The emulator's activity, as `package/full.activity.Name`. |
| `action` | No | The intent action. Defaults to `android.intent.action.VIEW`. |
| `game` | No | How the game is handed over; see below. Defaults to `data`. |
| `extras` | No | Extra string values to send with the intent, as `"KEY": "value"`. |

The `game` field takes one of three forms:

| Value | What the emulator receives |
| --- | --- |
| `data` | A content link to the game as the intent's data. Most standalone emulators. |
| `extra-uri:<key>` | The same content link, in the extra named `<key>`. |
| `extra-path:<key>` | The plain file path, in the extra named `<key>`. RetroArch works this way. |

A content link points into the `ROMs` folder you picked on first launch, and Tandem grants the emulator access to that one file. That is why the second setup step matters for emulators that use `data` or `extra-uri`.

## Finding an emulator's component and action

Most emulator projects document how frontends should launch them; look for "frontend", "intent" or "ES-DE" in their docs. ES-DE's own configuration (its `es_find_rules.xml` and `es_systems.xml` for Android) lists the activity and action for many emulators, and translates directly:

| In ES-DE | In `platforms.json` |
| --- | --- |
| The emulator's `package/activity` entry in `es_find_rules.xml` | `component` |
| `%ACTION%=…` | `action` |
| `%DATA%=%ROMSAF%` | `"game": "data"` |
| `%EXTRA_key%=%ROM%` (a file path) | `"game": "extra-path:key"` |
| `%EXTRA_key%=%ROMSAF%` (a content link) | `"game": "extra-uri:key"` |
| Any other `%EXTRA_key%=value` | an entry in `extras` |

With a computer and `adb`, you can also list an installed app's activities:

```sh
adb shell cmd package resolve-activity --brief -a android.intent.action.VIEW <package>
adb shell dumpsys package <package> | grep -i activity
```

## Several emulators for one system

Add more entries to `players`. The first is the default, and any game can use another from its game menu (Y › **Emulator**). For example, to offer a standalone mGBA alongside RetroArch for the GBA, add it as a second player.

## Sharing what works

If you get an emulator working, please share it: open a [new emulator request](https://github.com/ZahakJ/tandem/issues/new?template=new_emulator.yml) with your entry, or a pull request that adds it to the built-in file.