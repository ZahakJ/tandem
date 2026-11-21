# Setting up on the AYN Thor

The AYN Thor is a clamshell handheld with a 1920×1080 top screen and a 1080×1240 touchscreen below. This page
covers a setup from scratch with the emulators Tandem knows out of the box. If you already use another frontend on
the Thor, skim to [Emulators on the Thor](#emulators-on-the-thor) and [The bottom screen](#the-bottom-screen).

## Before you start

- Update the Thor's system software from AYN's settings app.
- Copy your games to a folder in **internal storage** or on an SD card (`ROMs` is the default). See
  [Getting started](getting-started.md#put-your-games-in-place) for how Tandem finds them.

## Emulators on the Thor

Install the emulators for the systems you play and open each one once, so it can set itself up and ask for its
permissions.

| System | Emulator | Notes |
| --- | --- | --- |
| Nintendo 3DS | Azahar | Point it at your system files the first time you open it. |
| Nintendo DS | MelonDualDS | A melonDS build with dual-screen support (`me.magnum.melondualds`). Turn on both screens in its settings. |
| Nintendo Switch | Eden Duo or Eden | Install keys and firmware first. Eden Duo adds game companions on the bottom screen; Peanuts Native can play games natively. See [Switch games](switch.md). |
| Nintendo Wii U | Cemu | Set up its game paths once. |
| Game Boy, Color, Advance | RetroArch (64-bit) | Package `com.retroarch.aarch64`. Download the **mGBA** and **Gambatte** cores from the Online Updater. |

Any other emulator or build works too, once Tandem knows how to start it: see [Systems and emulators](platforms.md).

## Install Tandem

Install the APK from [GitHub Releases](https://github.com/ZahakJ/tandem/releases/latest) or through Obtainium, open
it, and go through the two [first-launch steps](getting-started.md#first-launch).

## The bottom screen

Tandem draws on both screens: the library on top, details for the highlighted item below. The bottom screen never
takes input focus, so the controller keeps driving the library.

![The bottom screen with nothing highlighted: Pip, the time, a greeting, the number of games ready to play and the last game played.](../../site/assets/screenshots/home-bottom.webp "Nothing highlighted: Pip's greeting")
![The bottom screen with a running game highlighted: Pokémon Emerald's art and logo, a Running badge and an A Resume hint.](../../site/assets/screenshots/game-bottom.webp "A running game, ready to resume")

### While you play

Tandem keeps the bottom screen during play, in one of four styles (PC games and streams get a fifth, the
[Deck](#while-you-play), below):

- **Game art** (the default): the game's art slowly drifting, its logo, this session's time and total play time.
  After a minute without a touch it dims and shifts a few pixels now and then against burn-in. Tap to wake it.
- **Tandem companion**: Pip playing along, clock and battery, session time, your dock apps, brightness, volume and
  Wi-Fi, and buttons for Back to Tandem, Game art and Close game. A dock app you tap opens on the bottom screen.
- **Screen off**: the bottom screen is black and still from the moment the game starts. The Thor's lower panel is
  an OLED, so black pixels are off pixels, and nothing is drawn or timed while it is dark: this is the biggest
  battery saving the bottom screen can give you. Tap it and the game art lights up for a minute (with Back to
  Tandem, Screenshot, Notes and the rest), then it goes dark again by itself. That first tap only wakes it, so you
  never press a button by accident.
- **Leave to emulator**: Tandem stays off the bottom screen. This is the default for 3DS and DS, whose emulators
  use both screens. Wii U gets Game art, since Cemu leaves the bottom screen empty.

Choose for every game at once in **Settings › Two screens › While playing › Bottom screen while playing** (3DS and DS
emulators keep their own screen whatever it says), per system (**Settings › Library & art › Systems and emulators**, A on the system › **Bottom screen while playing**) or
per game (Y › More › **Bottom screen while playing**); the game's choice beats the system's, which beats the
setting. Games started on the bottom screen never get it.

**Off after**, next to it, turns any style dark after 1, 2, 5 or 10 minutes without a touch on the bottom screen
(Never, the default, keeps the dimmed art or companion up all game long). With Screen off it is also how long a
tap lights the screen. A dark screen comes back on a tap, when a controller's Home button brings the game and its
companion back from under another app, when the handheld wakes, when you come back to Tandem and when the game
ends. The timer is nothing more than a delay inside the companion while it is on show: no wake lock and no alarm,
so it never keeps the Thor from sleeping, and a dark screen has no timer running at all.

Why black pixels and not the panel's power: Android 13 has no way to turn one built-in screen off while the other
stays on (both sit in one power group, and `cmd display` can only set the main screen's brightness), and a window's
own brightness setting is one value for every screen on this Android version, so using it on the bottom screen
would dim the game. On an OLED a black pixel costs the same at any brightness, so nothing is lost. The first time a
Screen off panel comes up with Shizuku running, Tandem writes what `dumpsys display` says about the bottom screen
(its power group, state and brightness) and any display-related settings keys to `adb logcat -s Tandem`, in case
AYN's own second-screen switch turns out to be reachable that way.

Both Tandem styles have a **Screenshot** button (needs [Shizuku](resume.md)): it saves the game's screen to
`ROMs/screenshots/`, where the Gallery shows it, and copies it to the clipboard to paste into a chat. **Clip**
records the next 15 seconds of the game's screen and sound, and your voice unless **Settings › Two screens › While
playing › Voice in clips** is off; tap it again to stop sooner. Clips go to `ROMs/clips/` and play, with sound, in
the Gallery, which can also share a picture or clip through Android's share sheet.

Both also have **Notes**: the game's notebook, by touch, with screenshots straight into a note, sketches, checklists
and glossaries. See [Notes](notes.md).

**Manual**: when the game has a manual or guide in `ROMs/manuals` (or one picked in its Edit details), a **Manual**
button at the bottom right of the Game art style, and beside the game's name in the Tandem companion, shows it on the
bottom screen, page by page, under the game. Tap the sides of a page to turn, the middle to zoom, and the bar under it
to jump; the page you were on is remembered for next time. See [Manuals and guides](library.md#manuals-and-guides).

**Game screen**: Switch games start in [Eden Duo](https://github.com/ZahakJ/eden-duo) when it is installed (Eden with
per-game companions). The game opens as usual, with Tandem below; when its companion is ready (an installed package
for that exact game version), a **Game screen** button puts it on the bottom screen: Animal Crossing's island map and
pockets, Fire Emblem's war map and units, and so on. Its **Hide** button, top right, hands the screen back to Tandem.
Tandem gives itself Eden Duo's control permission through Shizuku the first time. Games built by Peanuts Native draw
their own companion; see [Switch games](switch.md#peanuts-native).

**Deck**: PC games (GameNative, Winlator) and [streamed games](streaming.md) get a fifth style, a keyboard and
trackpad for the game on the top screen. Pick it with the **Deck** chip while a game plays, or make it the game's or
system's **Bottom screen while playing** choice; its **Game art** and **Companion** chips lead back. Two keyboards,
switched with the chips at the top: the **Full keyboard** (letters, numbers, arrows, Enter, Tab, Esc, Backspace; its
**Fn** key adds the F1–F12 row and **123** swaps the letters for a numpad with Home, End, Page Up and Down) and the
**Game keys** (the WASD cluster, Space, Shift, Ctrl, Q, E, R, F, Tab, Esc, 1–6, Enter, arrows and both mouse
buttons, which stay pressed as long as your finger does). Shift, Ctrl and Alt are sticky: tap one for the next key,
hold it to lock it until you tap it again. A held key repeats. The trackpad below moves the game's pointer as a
laptop's does: a drag moves it, a tap clicks, a two-finger tap right-clicks, and two fingers dragging or the strip at
the right edge scroll. **Settings › Controls › Deck** sets the trackpad's sensitivity, the key sounds and
haptics, and the keyboard the Deck opens with. The Deck never dims or goes dark while it is up, whatever **Off after**
says, so no key is lost to waking it.

The Deck needs [Shizuku](resume.md): without it the chip at the top says **Needs Shizuku**. With it, the chip says how
the keys reach the game. **Direct** puts them straight into Android's input service, in a few milliseconds, and the
pointer follows your finger. **Shell** means Android kept Tandem from that service, so each key is an `input`
command instead: a key takes a moment to land, held keys don't repeat, and the pointer on the game's screen doesn't
move; a tap clicks where the pad's blue mark points instead. Either way the controller stays with the game. A game
that reads the keyboard and mouse directly (some Winlator setups with raw input, a stream whose mouse mode is
captured) may ignore what the Deck sends; switch the game to the ordinary keyboard and mouse mode if it does.

**Pause games in sleep** (Settings › Play & system › Performance & sleep, on by default, needs Shizuku running as root): a
minute after the screen goes off, games left open are paused, so an emulator's audio can't keep the handheld
awake; they carry on where they were when you wake it.

**Immersion mode** (Settings › Play & system › Playing, on by default): while a game plays, Do Not Disturb is on
with Tandem's own rules, so nothing from Android cuts in: no notification pop-ups, sounds, lights or vibrations,
only alarms and the game's own sound. Notifications still land in the shade for later. Coming back to Tandem puts
Do Not Disturb back as it was, and one you turned on yourself is never touched. It needs Tandem allowed under
Android's Do Not Disturb access: letting Pip show your notifications already counts, and otherwise, with Shizuku,
Tandem allows itself the first time a game starts (or when you press A on the setting); without either, the setting
opens Android's list for you.

With an app open on the bottom screen over the game, **Home** brings the game and its companion back without
pausing the game; press it again for Tandem. From the game itself, Home goes to Tandem.

![The Game art style while Pokémon Emerald runs: the game's art and logo, the time played this session and in total, and Back to Tandem and Companion buttons.](../../site/assets/screenshots/ingame-art.webp "Game art, the default while you play")
![The in-game art screen on the bottom screen while a game runs.](../../site/assets/screenshots/ingame-art.webp "The in-game art screen")

The bottom screen answers touch only; the controller stays with the game. Tapping a dock app gives that app the
controls until you touch the game's screen again, as Android always does. **Close game** needs Shizuku and asks for
a second tap. The companion is designed to cost almost nothing: no continuous animation, and once dimmed it
redraws only twice a minute.

### While an app is open

When Tandem is your Home app, it also fills the bottom screen when nothing else is there, so Android's own app list
doesn't appear by accident:

- With an app in front on the top screen: that app's icon and name, clock, battery and **Back to Tandem**.
- Otherwise: the clock and Pip over a still of your bottom-screen wallpaper.

It dims after a minute and never takes the controller. With Shizuku it also notices apps opened from Recents or a
notification. Android's own launcher is still there when you want it: **All apps › Android home** opens Android's
Home screen on top, and the **Android apps** button opens Android's app list below. Choosing another Home app gives
Android back its usual bottom screen.

Right after installing or updating Tandem, Android may keep its old bottom screen until the next restart; Tandem
covers it the next time it starts an app or game.

If the bottom screen stays black or shows something else while Tandem is open, check that the second screen is on
in the Thor's display settings, then leave Tandem with Home and open it again.

## Performance profiles

The Thor's control centre has a performance mode (Standard, Performance, High performance), a fan mode (Off, Quiet,
Smart, Sport, or a custom speed) and a refresh rate for the top screen (60 or 120 Hz). Tandem can set them for the
time a game plays, and put them back when you come back:

- **For every game:** Settings › Play & system › Performance & sleep › **Performance mode**, **Fan mode** and **Refresh rate**.
  Each starts at **Keep**, which leaves that one as you set it in the control centre.
- **For one game:** Y on the game › More › the same three rows; A on one lists its options. **Default** follows the settings above, **Keep**
  leaves that one alone whatever the default says, and a value is this game's own. A demanding Switch game can have
  High performance, Sport and 120 Hz while Game Boy games keep the quiet Standard mode and 60 Hz.

The game's profile shows as a line on the bottom screen while it plays ("High performance · Sport · 120 Hz"). When the
session ends, Tandem puts back exactly what it changed, and nothing else: a mode you change yourself in the control
centre mid-game stays as you set it, and a mode that was already as the profile wanted is never touched. A custom
fan speed is AYN's own and is left as it is. If Tandem is closed or restarts while a game plays, the modes come back
once the game's session is over.

Tandem changes the modes the way the control centre does. Android lets only the shell write AYN's keys, whatever
permissions an app holds, so this needs **Shizuku** running with Tandem allowed; the Playing group says so until it
is. On a handheld without AYN's modes the rows give way to a note, and nothing is ever written.

## Resume with Shizuku

[Resume](resume.md) brings a game back mid-level instead of restarting it. It needs Shizuku:

1. Install [Shizuku](https://shizuku.rikka.app/) and start it with **wireless debugging** (needs Wi-Fi to start,
   not to keep running).
2. Allow Tandem, on its setup screen or later in Shizuku's list of authorized apps.
3. **Settings › Play & system › Playing › Resume running games** shows **Ready** when it works.

Shizuku stops when the Thor restarts; start it again after a reboot. Until then, games simply start fresh.

![The Resume page after pressing Home mid-game: a large Continue card for Pokémon Emerald with Resume and Close, beside the system icons.](../../site/assets/screenshots/resume.webp "Home mid-game lands on the Resume page")

## Controls

D-pad or left stick to move, <kbd>A</kbd> to open or play, <kbd>B</kbd> back, <kbd>X</kbd> search, <kbd>Y</kbd> options, <kbd>Start</kbd> the Start panel (Settings is in it),
<kbd>Select</kbd> a random unplayed game, <kbd>L1</kbd>/<kbd>R1</kbd> to flip between systems. The full table is in the
[controller reference](controls.md). Tandem follows whatever Android reports as A and B; if they feel swapped, check
the device's controller layout option.

## Tips

- **Keep Tandem's data with your games.** **Settings › Play & system › Your data › Back up** saves favorites, play time,
  the look, the home layout and emulator choices to `ROMs/tandem/backup/`; **Restore** puts them back on a new device.
- **Hide what you don't play:** Y on a game › **Hide this game**.
- **Already scraped with ES-DE?** Leave `ROMs/downloaded_media` where it is; Tandem uses the same artwork.
