# Resume and Shizuku

Tandem can bring a running game back instead of starting it over. This needs Shizuku; without it, games start
fresh as in most frontends.

## What resume does

Press Home mid-game, browse, then pick the same game again: if it is still open in its emulator, Tandem brings the
emulator back to the front exactly as you left it. A highlighted game that is still running shows a **● Running**
badge, and the A hint says **Resume**. A resume counts toward play time but not as a new play.

## Why it needs Shizuku

Android doesn't let apps see which other apps are running. [Shizuku](https://shizuku.rikka.app/) lets an app you
allow run a few commands with `adb`-level rights. Tandem uses only these:

- `ps -A -o NAME` and `dumpsys activity activities`, to see which emulators have a game open.
- `am force-stop <emulator package>`, to close an emulator before switching games (see below).

### What counts as running

A game counts as running only while its emulator has a window open (in front or in Recents). For emulators with a separate game screen (Eden, Azahar, Dolphin, Cemu, DuckStation, AetherSX2, melonDS, Winlator), that screen must be open; their menu alone doesn't count. A process Android keeps after you close a game is never shown as running or asked about.

## Setting it up

1. Install Shizuku and open it.
2. Start it. Without root, use **Start via wireless debugging** and follow its pairing steps (Wi-Fi is needed to
   start, not to keep running).
3. Allow Tandem: on Tandem's setup screen (**Resume games** step) if Shizuku is already running, or later in
   Shizuku's list of authorized apps.
4. **Settings › Play & stats › Playing › Resume running games** shows **Ready** once it works.

Shizuku doesn't survive a reboot on most devices; start it again after restarting.

## How Tandem decides

An emulator holds one game at a time, whatever the system: RetroArch runs every core, and GameNative or Winlator run
every PC game. Games in **different** emulators can stay open side by side.

When you press A on a game, Tandem:

1. Asks Shizuku whether the game's emulator is running.
2. If it is, and the last game Tandem started there is this one, brings it to the front. That is a resume.
3. If it is running a **different** game, closes the emulator first, then starts the new game. Some emulators
   ignore a new game while another is open, so this is the only reliable way to switch.
4. With **Close previous game** on, also closes every other running emulator.
5. Otherwise, starts the game normally.

> [!WARNING]
> Steps 3 and 4 close emulators the way Android's **Force stop** does. Anything unsaved in those games is lost.
> Save first, or use the emulator's save states.

### Close previous game

**Settings › Play & stats › Playing › Close previous game** (on by default) closes other emulators when you start a
game, so a game left in the background doesn't use memory and battery. Turn it off to keep games in different
emulators open; the [game switcher](controls.md) (hold Select) lists them.

### Tandem asks first

Before closing anything, Tandem asks. If a game is still running and starting the one you picked would close it,
a question names the running game (or each of them, with cover and session time):

| Button | What it does |
| --- | --- |
| **Resume** the running game (highlighted first) | Goes back to it. Nothing is closed. |
| **Close and start** the new game | Press A twice. The running game is closed and the new one starts. |
| **Cancel** (<kbd>B</kbd>) | Back to the library. |
| **Don't ask again** (<kbd>Y</kbd>) | Stops asking. Nothing is closed this time; press A again to start. |

This applies wherever you start a game: grids, search, collections, Up next, the Start panel, the game switcher
and widgets. In private mode a hidden game is shown only as **A hidden game is still running**.

A game whose file was deleted is never named; the question names its emulator instead.

**Settings › Play & stats › Playing › Ask before closing a game** brings the question back or turns it off.

## Troubleshooting

**The badge never shows.** If **Resume running games** says **Allow in Shizuku**, Shizuku isn't running or Tandem
isn't allowed in it.

**The game restarted anyway.** Android may have closed the emulator in the background to free memory, or the
emulator quits when you leave it; check its settings for a "pause in background" option.

**It resumed the wrong thing.** Tandem only knows the last game it started in that emulator. If you opened another
game from inside the emulator, close the emulator and pick the game again.
