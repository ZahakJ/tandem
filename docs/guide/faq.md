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

Yes. **Settings › Data & about › Use Tandem as the Home screen** asks Android to make Tandem your Home app. Then the Home button brings you back to Tandem (to the Resume card if a game is still running), and your Android apps are in the apps drawer and the dock. See [Controls](controls.md#the-home-button).

### Can I keep using ES-DE or Cocoon?

Yes. Tandem doesn't change their files, and it reads the same folders and artwork. You can switch between them freely. See [Importing](importing.md) for bringing their data over.

### Does Tandem support Android apps and PC games?

Yes. Your installed Android games show up as their own system, every other app is in the apps drawer, and you can pin apps to the dock. PS Vita (Vita3K) and Windows games (GameNative, Winlator) are in [`platforms.json`](platforms.md) like any other system.

### Is Tandem affiliated with AYN?

No. Tandem is an independent community project. It isn't affiliated with AYN or with any emulator project, and product names belong to their owners.

## Troubleshooting

### Tandem keeps showing the setup screen

Both setup steps must be done:

- **All files access** must be on for Tandem in Android's settings (**Settings › Apps › Special app access › All files access** on most devices).
- **The ROMs folder** must be picked in the folder picker, with **Use this folder**. If you picked a different folder by mistake, clear Tandem's storage in Android's app settings and go through setup again.

### A system or game is missing

- Check the folder name: it must match one of the system's `folders` in [Systems and emulators](platforms.md#what-comes-built-in), for example `ROMs/n3ds` and not `ROMs/Nintendo 3DS`.
- Check the file extension against the system's list.
- Is the game hidden? Turn on **Settings › Library › Show hidden games**.
- Are the games on an SD card? Tandem only reads `ROMs` in internal storage for now.
- Did you add the files while Tandem was open? **Settings › Library › Rescan games**.
- More than three folders deep? Tandem stops at three levels below the system folder.

### Pressing A does nothing, or says the emulator isn't installed

- Make sure the emulator is installed, and open it once on its own so it can finish its first-run setup.
- If you use a different build of the emulator (a fork, or a version with a different package name), Tandem's built-in entry won't find it. Update the `component` in your own [`platforms.json`](platforms.md#using-your-own-platformsjson).

### The emulator opens but doesn't load the game

- Emulators that receive a shared file need the `ROMs` folder you picked during setup. If you picked a subfolder, or a different folder, clear Tandem's storage and pick `ROMs` again.
- For RetroArch, check that the core named in `platforms.json` is installed (RetroArch › Online Updater › Core Downloader) and that the `CONFIGFILE` path exists.
- Some emulators need their own setup before any game loads: system files for Azahar, keys and firmware for Eden, game paths for Cemu.

### Games always start from the beginning

That is the normal behaviour without [Shizuku](resume.md). If Shizuku is set up, see [resume troubleshooting](resume.md#troubleshooting).

### No artwork shows

- No artwork yet? Let Tandem fetch it: **Settings › Art & accounts › Scrape library** (LaunchBox and libretro need no account).
- Artwork must be in `ROMs/downloaded_media/<system>/<kind>/`, named like the game file. [Your library](library.md#artwork) lists the folder names.
- If artwork came from an import, the paths in the gamelist must point to files that exist on this device.

### The bottom screen is blank or shows the wrong thing

Leave Tandem with Home and come back; it claims the second screen each time it comes to the front. On the Thor, make sure the bottom screen is switched on. See [The bottom screen](ayn-thor.md#the-bottom-screen).

### My play time looks wrong

Sessions shorter than ten seconds don't count, and a session counts for at most six hours. Play time is only recorded for games started from Tandem. To start a game's history over, Y › **Reset play history**.

### Reporting a bug

Open a [bug report](https://github.com/ZahakJ/tandem/issues/new?template=bug_report.yml). It helps to include your device, Android version, Tandem version (Android's app info screen shows it), the emulator involved, and the steps that lead to the problem. If you can, a log from `adb logcat -s Tandem` while it happens is the most useful thing of all.
