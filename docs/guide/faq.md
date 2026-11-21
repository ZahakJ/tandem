# FAQ and troubleshooting

Answers to common questions, and what to check when something doesn't work. If your problem isn't here, open an [issue](https://github.com/ZahakJ/tandem/issues/new/choose) and Pip will take a look.

## Questions

### Does Tandem come with games, BIOS files or emulators?

No. Tandem shows the games you already have and starts them in emulators you install yourself. It never downloads games or system files; the only things it fetches are artwork and details when you scrape, and your RetroAchievements progress if you sign in.

### Which devices does it work on?

Any Android 11 or newer device. Tandem is designed around the AYN Thor's two screens and uses a second display on any device that has one. On a single screen, everything works the same, without the details panel.

### Is it free? Will it stay free?

Yes. Tandem is free software under the GNU GPL, version 3 or later. There are no ads, no paid tier and no account, and the license makes sure any version someone else publishes stays open too.

### Can I use it as my home screen?

Yes. **Settings › Play & system › Use Tandem as the Home screen** asks Android to make Tandem your Home app. Then the Home button brings you back to Tandem (to the Resume card if a game is still running), and your Android apps are in the apps drawer and the dock. See [Controls](controls.md#the-home-button).

### Can I keep using ES-DE or Cocoon?

Yes. Tandem doesn't change their files, and it reads the same folders and artwork. You can switch between them freely. See [Importing](importing.md) for bringing their data over.

### Does Tandem support Android apps and PC games?

Yes. Your installed Android games show up as their own system, every other app is in the apps drawer, and you can pin apps to the dock. PS Vita (Vita3K) and Windows games (GameNative, DroidDeck, Winlator) are in [`platforms.json`](platforms.md) like any other system; PC games are small shortcut files, see [Windows and Steam games](library.md#windows-and-steam-games). To play games streamed from your own PC, see [Streaming from a home PC](streaming.md).

### I skipped the art sources at first launch. How do I set them up later and get art?

Nothing is lost by skipping; everything in the welcome is in Settings.

1. **Start › Settings › Library & art › Set up sources.** The **Artwork & details setup** popup lists the sources in
   the order they are asked, each with its state.
2. **LaunchBox (no account):** A on **LaunchBox**, then **Download (105 MB)** and **Download**. Wait for "LaunchBox is
   ready". This one download is all the no-account path needs; libretro thumbnails is always ready too.
3. **SteamGridDB (optional, free key, best for PC and Switch):** get a key at
   steamgriddb.com/profile/preferences/api, then A on **SteamGridDB › API key**, type it, **Done**.
4. **ScreenScraper (optional):** it needs your own **developer ID and password** (ask for API access in
   screenscraper.fr's forum), entered on its card; **Your login** and **Your password** are optional and give you your
   own, bigger daily quota.
5. **IGDB (optional):** a free Twitch application's **Client ID** and **Client secret** (dev.twitch.tv/console/apps).
   **HowLongToBeat** needs no key but is off until you press **Turn on** on its card.
6. On each card you set up, press **Test**: ✓ means it works, ✗ says why.
7. Get the art: **Settings › Library & art › Scrape library** › **Start scraping** for everything, or Y on a system ›
   **Scrape these games**, or Y on a game › **More…** › **Scrape this game** for one.

[Artwork and scraping](scraping.md) has a worked example and every option. If a scrape finds nothing, see
[A scrape gets no art](#a-scrape-gets-no-art) below.

### Why doesn't Tandem come with keys for ScreenScraper, SteamGridDB or IGDB?

Those services hand keys to a person or a project, under terms that don't allow publishing them, and anything built
into an open-source app can be read by anyone. A shared key would also share one quota among every user, and be
revoked. So each person uses their own free key. LaunchBox and libretro thumbnails need none, and between them cover
most systems.

### I'm coming from Cocoon. Do I lose my art?

No. Tandem reads the same `ROMs/downloaded_media` art, and the first scan brings over what your newest Cocoon backup's
gamelists point to: titles, descriptions, favourites, play time and artwork. **Settings › Play & system › Your
data › Import my Cocoon setup** also brings your home grid, folders and theme. See [Importing](importing.md). For
the games Cocoon had no art for, LaunchBox (above) is the closest thing to Cocoon's no-setup scraper.

### Where is a setting?

<kbd>X</kbd> in Settings searches every row by name or by what it does. [Every setting](settings.md) lists them all,
page by page, with their defaults.

### Do I need Shizuku?

No. Without it Tandem works fully as a frontend; games start fresh each time. Shizuku adds resume, closing games,
screenshots from the bottom screen, the Deck, performance profiles, Peanuts Native and more: the full list is in
[What Shizuku is used for](resume.md#what-shizuku-is-used-for).

### How do I update Tandem, or turn off update notifications?

Installed from GitHub Releases, Tandem checks once a day and tells you when a new version is out: a message once, and
a **Tandem 0.6.1 is out** row in the Start panel. Press A on it, then **Update**; nothing installs before that.
A on **Settings › Play & system › Updates** checks at once.

To stop the daily check, its message and the Start panel row, switch **Settings › Play & system › Check
automatically** off. Then update by hand whenever you like: A on **Updates**, then **Update**, or download the APK from
the [releases page](https://github.com/ZahakJ/tandem/releases/latest). See
[Software updates](getting-started.md#software-updates).

### Which app does a Switch game play in?

Eden Duo (or Eden), unless Peanuts Native has built it: then it plays natively, with a **⚡ Native** badge. With both
Eden Duo and Peanuts Native, the newer save follows the game across. See [Switch games](switch.md).

### Is Tandem affiliated with AYN?

No. Tandem is an independent community project. It isn't affiliated with AYN or with any emulator project, and product names belong to their owners.

## Troubleshooting

### Tandem keeps showing the setup screen

Both setup steps must be done:

- **All files access** must be on for Tandem in Android's settings (**Settings › Apps › Special app access › All files access** on most devices).
- **A games folder** must be picked in the folder picker, with **Use this folder**: the folder your games are in, or `ROMs`. Picking a folder inside `ROMs` is refused, since `ROMs` is always read; pick `ROMs` itself.

### A system or game is missing

- Is the folder in? **Settings › Library & art › Game folders** lists every folder Tandem reads; **Pick your games folder…** adds one.
- Check the folder name: it must name the system, for example `n3ds`, `3DS` or `Nintendo 3DS`. Names Tandem knows are in [Systems and emulators](platforms.md#what-comes-built-in). Files only one system plays (`.3ds`, `.nsp`, `.gba`…) are found in any folder, but disc images and archives (`.iso`, `.chd`, `.zip`…) need a folder named for their system.
- Check the file extension against the system's list.
- Is the game hidden? Turn on **Settings › Play & system › Privacy & hidden › Show hidden games**, or look in **Start › Hidden**.
- Are the games on an SD card? Add the card's games folder in **Settings › Library & art › Game folders**. See [Where games live](library.md#where-games-live).
- Did you add the files while Tandem was open? They should appear by themselves within a few seconds while Tandem is on screen (**Watch game folders**); otherwise **Settings › Library & art › Rescan games**.
- More than three folders deep? Tandem stops at three levels below the system folder.
- Loose game files directly in `ROMs`, or in a folder in `ROMs` that isn't named for a system? `ROMs` keeps the
  folder-per-system layout: move them into the system's folder, or add their folder in **Game folders**.
- In a folder named like `Updates`, `DLC`, `Patches` or `Mods`? Those are never taken for games (outside a system's
  own folder).
- Is the whole system turned off? **Settings › Library & art › Systems and emulators** lists it under
  **Turned off**; A on it › **Show in library**.

### Pressing A does nothing, or says the emulator isn't installed

- Make sure the emulator is installed, and open it once on its own so it can finish its first-run setup.
- If you use a different build of the emulator (a fork, or a version with a different package name), Tandem's built-in entry won't find it. Update the `component` in your own [`platforms.json`](platforms.md#using-your-own-platformsjson).

### The emulator opens but doesn't load the game

- Emulators that receive a shared file need the game's folder to have been picked: during setup, or in **Settings › Library & art › Game folders**. Games in `ROMs` need `ROMs` itself picked; picking it again there renews the link.
- For RetroArch, check that the core named in `platforms.json` is installed (RetroArch › Online Updater › Core Downloader) and that the `CONFIGFILE` path exists.
- Some emulators need their own setup before any game loads: system files for Azahar, keys and firmware for Eden, game paths for Cemu.

### Games always start from the beginning

That is the normal behaviour without [Shizuku](resume.md). If Shizuku is set up, see [resume troubleshooting](resume.md#troubleshooting).

### No artwork shows

- No artwork yet? Let Tandem fetch it, with no account: **Settings › Library & art › Set up sources › LaunchBox › Download**, then **Scrape library**. See [Get artwork](getting-started.md#get-artwork).
- Artwork must be in `ROMs/downloaded_media/<system>/<kind>/`, named like the game file. [Your library](library.md#artwork) lists the folder names.
- If artwork came from an import, the paths in the gamelist must point to files that exist on this device.

### A scrape gets no art

- **Private mode is on.** It keeps Tandem offline: Pip says "Private mode keeps Tandem offline: no scraping or art
  lookups". Turn it off (**Settings › Play & system › Privacy & hidden › Private mode**, with your PIN), scrape, turn it back on.
- **No source can answer.** A source without its key is skipped, and libretro has nothing for Switch, Wii U or PC
  games. Download LaunchBox's index (Set up sources › LaunchBox › **Download**), or add a SteamGridDB key.
- **LaunchBox's index isn't downloaded,** or was skipped with **Skip LaunchBox**: the scrape notes "LaunchBox needs its
  database". Download it, then scrape again. A system you added later needs **Download again**.
- **ScreenScraper's quota is used up** for the day: the scrape says so and carries on with the other sources.
- **Nothing was missing.** In the Missing modes, games that already have what you picked are skipped. Use **Force all**,
  or Y › More › **Scrape this game**.
- **The game is hidden.** Hidden games are never sent anywhere.
- **No source knew the game.** **Missed** on the finished scrape lists them; fix the name in Edit details, or Y › More ›
  **Choose artwork** › **From this device**.

The full list of messages is in [When a scrape finds nothing](scraping.md#when-a-scrape-finds-nothing).

### The Switch save didn't carry over between Eden Duo and Peanuts Native

The save sync needs Eden Duo (not plain Eden), the game built in Peanuts Native, Shizuku running on a rooted handheld,
and the game started from Tandem while it isn't already open. When it copies, Pip says "Carried on from your … save";
when it can't, it says why, and both saves stay as they were. Replaced saves are kept in
`ROMs/tandem/save-sync/backups/`. See [Switch games](switch.md#one-save-across-eden-duo-and-peanuts-native).

### Build native version doesn't show for a Switch game

It shows only when Peanuts Native is installed, Shizuku is running with Tandem allowed, and Peanuts has a profile for
that game and version. A game it has already built shows the **⚡ Native** badge instead. See
[Switch games](switch.md#peanuts-native).

### The bottom screen is blank or shows the wrong thing

Leave Tandem with Home and come back; it claims the second screen each time it comes to the front. On the Thor, make sure the bottom screen is switched on. See [The bottom screen](ayn-thor.md#the-bottom-screen).

### My play time looks wrong

Sessions shorter than ten seconds don't count, and a session counts for at most six hours. Play time is only recorded for games started from Tandem. To start a game's history over, Y › **Reset play history**.

### Reporting a bug

Open a [bug report](https://github.com/ZahakJ/tandem/issues/new?template=bug_report.yml). It helps to include your device, Android version, Tandem version (Android's app info screen shows it), the emulator involved, and the steps that lead to the problem. If you can, a log from `adb logcat -s Tandem` while it happens is the most useful thing of all.
