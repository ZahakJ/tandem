# Streaming from a home PC

Some PC games are too big for a handheld. With [Sunshine](https://github.com/LizardByte/Sunshine) on your PC and
[Moonlight](https://moonlight-stream.org/) on the handheld, the PC runs the game and streams it over your home
network, and the handheld's controls go back to the PC. This page walks through one setup that works well: an Arch
Linux PC running Hyprland with an NVIDIA graphics card, and an ultrawide monitor. Most of it applies to any Linux PC.

> [!NOTE]
> Tandem doesn't stream anything itself: Moonlight does. Tandem gives each streamed game its own tile, with art,
> next to your other PC games, and opens Moonlight straight into that game; see
> [A tile for each streamed game](#a-tile-for-each-streamed-game).

## What you need

- A PC with a recent graphics card, wired to your router if you can (see [When it stutters](#when-it-stutters)
  if it can't be). Wi-Fi on the handheld's side is fine; 5 GHz is much better than 2.4 GHz.
- Sunshine on the PC (on Arch, the `sunshine` package from the AUR or LizardByte's own repository).
- Moonlight on the handheld (`com.limelight`, from the Play Store or Moonlight's GitHub releases).
- Steam on the PC, if that's where your games are.

## Set up Sunshine

Sunshine runs as a user service, so it starts with your desktop session:

```sh
systemctl --user enable --now sunshine
```

Its web page is at `https://localhost:47990` on the PC. The first visit asks you to choose a username and password;
the browser warns about the certificate, which Sunshine made for itself.

Settings live in `~/.config/sunshine/sunshine.conf`. Two lines matter on a Hyprland PC with an NVIDIA card:

```ini
# Encode on the graphics card (NVENC), which costs the game almost nothing.
encoder = nvenc
# Capture the screen through Wayland's wlroots protocol, which Hyprland supports.
capture = wlr
```

Sunshine's other way to capture, KMS, reads the screen straight from the graphics driver. It needs extra rights
(`cap_sys_admin` on the Sunshine binary), and run as a systemd user service it failed to capture at all in this
setup. `capture = wlr` needs none of that. Restart Sunshine after changing the file:

```sh
systemctl --user restart sunshine
```

## Stream at 1920×1080 from an ultrawide

An ultrawide screen streamed as it is leaves black bars on the handheld, or a stretched picture. Sunshine can run a
command before a stream starts and another when it ends (a "prep command"), so the monitor can switch to 1920×1080
for the stream and back afterwards.

First find your monitor's name and its usual mode:

```sh
hyprctl monitors
```

Then add the prep commands to the app in `~/.config/sunshine/apps.json` (or in the web page, under
**Applications**). Here the monitor is `DP-1` and its usual mode is 3440×1440 at 144 Hz; use your own:

```json
{
  "env": {},
  "apps": [
    {
      "name": "Desktop",
      "prep-cmd": [
        {
          "do": "hyprctl keyword monitor DP-1,1920x1080@60,0x0,1",
          "undo": "hyprctl keyword monitor DP-1,3440x1440@144,0x0,1"
        }
      ]
    },
    {
      "name": "Steam Big Picture",
      "detached": ["setsid steam steam://open/bigpicture"],
      "prep-cmd": [
        {
          "do": "hyprctl keyword monitor DP-1,1920x1080@60,0x0,1",
          "undo": "hyprctl keyword monitor DP-1,3440x1440@144,0x0,1"
        }
      ]
    }
  ]
}
```

`hyprctl` has to find the running Hyprland, which it does through the session's environment. If the prep command
does nothing, let the user service see that environment by adding this to `hyprland.conf` and logging in again:

```ini
exec-once = systemctl --user import-environment WAYLAND_DISPLAY HYPRLAND_INSTANCE_SIGNATURE XDG_RUNTIME_DIR
```

## Pair Moonlight

On the same network, Moonlight usually finds the PC by itself. If it doesn't (a PC on another subnet, or a network
that blocks discovery), add it by address:

1. On the PC, find its address on your home network: `ip -4 addr`.
2. In Moonlight, tap **+** (Add PC manually) and type that address.
3. Tap the PC. Moonlight shows a four-digit PIN.
4. Enter the PIN in Sunshine's web page, under **PIN**, with a name for the handheld.

Pairing from the PC's command line works too, which helps when you're on the handheld and the PC has no screen to
hand. Recent Sunshine versions want the id of the waiting pairing request along with the PIN: ask for it first, then
send both (use your Sunshine username and password, and the PIN Moonlight shows):

```sh
# What is waiting to be paired, with its pairing_id
curl -sk -u USER:PASSWORD https://localhost:47990/api/pin

# Answer it
curl -sk -u USER:PASSWORD -X POST https://localhost:47990/api/pin \
  -H 'Content-Type: application/json' \
  -d '{"pin": "1234", "name": "Handheld", "pairing_id": "ID-FROM-ABOVE"}'
```

Older Sunshine versions take only `pin` and `name`.

Once paired, Moonlight lists the apps from `apps.json`. In Moonlight's settings, 1080p at 60 FPS and a bitrate around
20 to 40 Mbps suit a handheld on good Wi-Fi.

## Windows games on a Linux drive

If the PC also has Windows, its Steam games can play on Linux through Proton without downloading them again. Linux
mounts a Windows (NTFS) drive read-only in many setups, and Steam can't run games from a library it can't write to,
so copy them to a Steam library on a Linux drive instead:

1. In Steam on Linux, add a library folder on a Linux drive: **Steam › Settings › Storage › Add drive**. Say it
   is `~/Games/SteamLibrary`.
2. Copy the game's folder from the Windows library's `steamapps/common/` into the new library's
   `steamapps/common/`:

   ```sh
   cp -a "/mnt/windows/Program Files (x86)/Steam/steamapps/common/Some Game" ~/Games/SteamLibrary/steamapps/common/
   ```

3. Copy the game's manifest too. It is `steamapps/appmanifest_<app id>.acf` in the Windows library, and tells Steam
   the game is installed (the app id is in the game's store page address):

   ```sh
   cp "/mnt/windows/Program Files (x86)/Steam/steamapps/appmanifest_123456.acf" ~/Games/SteamLibrary/steamapps/
   ```

4. Restart Steam. The game shows as installed; Steam checks the files and downloads only what differs. In the
   game's **Properties › Compatibility**, pick a Proton version if Steam doesn't on its own.

Copy while Steam is closed, and keep the manifest and the folder name as they were, since the manifest names the
folder.

## A tile for each streamed game

A streamed game is a small text file in `ROMs/windows` (or `ROMs/pc`, `ROMs/steam`) named after the game and ending
`.moonlight`. It shows up with your other PC games, and pressing A opens Moonlight straight into it, with no PC or
app list in between:

```
[uuid] 01234567-89AB-CDEF-0123-456789ABCDEF
[appid] 1234567890
```

- **`[uuid]`** is the PC's id in Moonlight. On the PC: `curl -s http://localhost:47989/serverinfo | grep -o '<uniqueid>[^<]*'`.
- **`[appid]`** is Sunshine's number for the app. For an app without its own image in `apps.json` it is the CRC32
  of the app's name, as a signed 32-bit number without its sign:

  ```sh
  python3 -c "import zlib,ctypes,sys; print(abs(ctypes.c_int32(zlib.crc32(sys.argv[1].encode())).value))" "Some Game"
  ```

  With an image, Sunshine adds the image's SHA-256 to what it hashes; leaving the image out keeps the id simple, and
  the art comes from Tandem anyway. A wrong id lands on Moonlight's app list for the PC instead of in the game.

Moonlight always opens these files: GameNative, DroidDeck and Winlator are never offered for them, whichever is the
system's emulator. Art works as for any game: scrape it, or copy pictures named like the file into
`ROMs/downloaded_media/windows/icon/`, `hero/` and `logo/`.

**Leaving and coming back.** Turn on **Picture-in-picture** in Moonlight's settings. Home then shrinks the stream to
a small window, still connected, in place of dropping it, and pressing A on the game in Tandem
([resume](resume.md)) opens it back up at once. Without it, Home ends the connection and coming back reconnects.

## Console-style sessions

The prep commands above only change the monitor's mode: the desktop stays on the monitor, the game has to be
started from Steam inside the stream, and nothing closes it afterwards. `tools/streaming/stream-session` in this
repository makes a stream behave like a console, on a Hyprland PC:

| When | What happens on the PC |
| --- | --- |
| A stream starts | A headless 1920×1080 screen is created and the real monitor is switched off, so the stream shows only the game and nothing shows in the room. The idle lock, dimming and screen-off are held off for the session. |
| The game is asked for | Steam starts in the background if it isn't running, and is asked for the game every ten seconds until the game's process appears (Steam drops the request while it is still starting). |
| The game exits, or you pick **Quit session** in Moonlight | The game and Steam close, the monitor comes back in its usual mode, and the headless screen is removed. |
| Moonlight is closed and stays away for 90 seconds | The watchdog ends the session the same way. |
| Sunshine crashes | The watchdog restores the PC and starts Sunshine again. |

Set it up:

1. Copy `stream-session` to `~/.local/bin/` and `stream-watch.service` to `~/.config/systemd/user/`.
2. Write `~/.config/stream-session.conf` with your monitor (the script's first lines list every setting):

   ```sh
   MON=DP-1
   MON_MODE="3440x1440@144,0x0,1"
   SUNSHINE=sunshine.service
   WEB_USER=your-sunshine-username
   ```

   The watchdog closes an abandoned session through Sunshine's web page, so put that page's password, alone, in
   `~/.config/sunshine/webui-password` (mode 600).
3. Give each game an entry in `apps.json` that runs the script. The two words after `game` are the Steam app id and
   the name of the game's `.exe` without the extension (as `pgrep -af '\.exe'` shows it while the game runs):

   ```json
   {
     "name": "Some Game",
     "cmd": "stream-session game 123456 SomeGame-Win64-Shipping",
     "auto-detach": "false",
     "prep-cmd": [{ "do": "stream-session start", "undo": "stream-session stop" }]
   }
   ```

   Sunshine needs `~/.local/bin` on its path: `"env": { "PATH": "$(PATH):$(HOME)/.local/bin" }`.
4. Start everything with the desktop session, in `hyprland.conf`. The first line brings the monitor back after a
   crash or reboot mid-stream; the last is a key to end a session from the PC's keyboard:

   ```ini
   exec-once = stream-session restore
   exec-once = systemctl --user import-environment DISPLAY WAYLAND_DISPLAY HYPRLAND_INSTANCE_SIGNATURE XDG_CURRENT_DESKTOP && systemctl --user restart sunshine.service stream-watch.service
   bind = SUPER CTRL, F12, exec, stream-session stop
   ```

What the script did, and when, is in `~/.local/state/stream-session.log`.

**A locked PC.** With the monitor off, a locked session shows its lock screen in the stream. Type the password with
Moonlight's on-screen keyboard, or set `UNLOCK=1` in `stream-session.conf`: a stream then unlocks the session
(hyprlock) and locks it again when it ends. That lets anyone holding a paired handheld into the PC without the
password, so it is off unless you ask for it. After a reboot someone still has to log in at the PC once: Sunshine
starts with the desktop session.

> [!NOTE]
> Started over SSH, Sunshine has no desktop to capture and stops with "Unable to find display or encoder". Start it
> from the session (the `exec-once` line above), or give the user service the session's `WAYLAND_DISPLAY` and
> `HYPRLAND_INSTANCE_SIGNATURE` first with `systemctl --user set-environment`.

## When it stutters

Work through these in order; each was a real cause in the setup this page describes.

1. **Moonlight's own settings.** It starts at 720p and a low bitrate, which looks like low graphics settings. Set
   1080p, 60 FPS and 20 to 40 Mbps.
2. **The game's settings on the PC.** A game copied from Windows starts with whatever Proton detects; check its
   graphics menu once.
3. **Something else holding the graphics card.** `nvidia-smi` lists what uses its memory. A local AI model server
   left running took 7 of 12 GB here and halved the game's frame rate.
4. **The PC's Wi-Fi.** A cable from the PC to the router is the fix that always works. If the PC has to be on
   Wi-Fi, measure before guessing: Moonlight's "slow connection to PC" means packets arrive late, not that they are
   lost.

### A PC on Wi-Fi 6 behind a gateway that schedules the uplink

Some gateways tell Wi-Fi 6 clients not to send whenever they like and to wait for the gateway to give them a turn
(an "MU EDCA" rule in the gateway's beacon, with `AIFSN 0`). A Wi-Fi 6 card obeys; a Wi-Fi 5 card never hears of
it. A PC that streams is almost all sending, so every video packet waits for a turn. The signal and link speed
look perfect while the stream stutters.

To check, send a paced stream between the PC and another computer on the same network with
`tools/streaming/udpprobe.py`, in both directions:

```sh
# on the PC
python3 udpprobe.py serve 47555
# on the other computer: the PC sends 40 Mbit/s for 10 s, then this one sends
python3 udpprobe.py down PC-ADDRESS 47555 40 10
python3 udpprobe.py up   PC-ADDRESS 47555 40 10
```

It prints how late packets arrived, compared with the fastest one. Measured here, on an Xfinity XB7 gateway with a
MediaTek MT7922 card in the PC, the PC idle:

| Sender | Typical delay | Packets later than 16 ms | Stalls over 30 ms in 10 s | Worst |
| --- | --- | --- | --- | --- |
| The PC, joined as Wi-Fi 6 | 8.5 to 9.5 ms | 18 to 23 % | 31 to 52 | 320 to 710 ms |
| The PC, joined as Wi-Fi 5 | 2.6 to 3.0 ms | 2 to 4 % | 4 to 9 | 37 to 81 ms |
| A laptop with a Wi-Fi 5 card | 1.7 to 2.5 ms | 1 to 4 % | 2 to 11 | 36 to 90 ms |

If the PC sends much worse than it receives, join as Wi-Fi 5. With NetworkManager and wpa_supplicant (use your
interface's name, from `ip link`):

```sh
sudo wpa_cli -i wlan0 set_network 0 disable_he 1 && sudo wpa_cli -i wlan0 reassociate
iw dev wlan0 link    # "VHT-MCS" in the bitrate lines means Wi-Fi 5; "HE-MCS" is Wi-Fi 6
```

That lasts until the Wi-Fi reconnects. `tools/streaming/50-wifi5-uplink` does it on every connection: set `IFACE`
in it and install it as root in `/etc/NetworkManager/dispatcher.d/` with mode 755. The link still runs at several
hundred Mbit/s, far more than a stream needs.

Two smaller things helped on the same card:

- **PCIe power saving.** `options mt7921e disable_aspm=Y` in `/etc/modprobe.d/mt7921e.conf`, then a reboot. Before
  it, pings to the router averaged 70 to 110 ms under load; after, 15 to 18 ms.
- **Wi-Fi power saving.** `nmcli connection modify "<network>" 802-11-wireless.powersave 2`.

A crowded channel (many neighbours' networks on yours) adds 30 to 60 ms of jitter for every device and is not
something the PC can fix; a cable takes the PC out of it.

### Other things seen

- **Steam's window flickers or its pages never load during a stream.** With only a headless screen, Steam's
  built-in browser restarted every ten seconds. `stream-session` starts Steam with `-cef-disable-gpu` for that reason.
- **The game closes when the controller reconnects.** One game crashed each time Moonlight reconnected. With
  picture-in-picture on, Home no longer disconnects, which avoids it.
- Sunshine's web page has a **Troubleshooting** page with its log; a failed capture or encoder shows there first.
