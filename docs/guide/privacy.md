# Privacy and scraping

Your library and play history stay on your handheld. Tandem has no analytics, telemetry, ads or account of its own, and goes online only for features you start: scraping artwork and details, and RetroAchievements if you sign in.

## When Tandem uses the network

Tandem declares Android's `INTERNET` permission for scraping and RetroAchievements only; there are no crash reports or update checks. See the [manifest](../../app/src/main/AndroidManifest.xml) and the code.

It goes online only when you ask it to:

- **Scraping**: **Settings › Art & accounts › Scrape library**, **Scrape these games** in a system's Y menu, or **Scrape this game** in a game's Y menu. A scrape you start keeps going in the background, with its progress on the bottom screen, until it finishes or you stop it.
- **Choose artwork** in a game's Y menu, which asks your sources for pictures of that one game.
- **Test** on a source's card, which looks up one well-known game, never one of yours.
- **The LaunchBox database**, downloaded only after you agree to it. A scrape you start refreshes a month-old copy, on Wi-Fi only.
- **RetroAchievements**, only after you sign in with your username and web API key, and for **Find match** in a game's Edit details when you open it (never for a hidden game).

Tandem never scrapes on its own or on a schedule. `ACCESS_NETWORK_STATE` only lets it warn you before a big download on mobile data.

## What is sent, and to whom

Each source can be switched off in **Settings › Art & accounts**; a source that is off is never contacted.

| Service | When | What it receives |
| --- | --- | --- |
| LaunchBox | On by default, no account | Nothing about your games: the whole database is downloaded and games are matched on the device. Then the pictures you get are downloaded by name. |
| libretro thumbnails | On by default, no account | A system's folder listing, once a week; then picture names made from your game's file name or title. |
| ScreenScraper | Only with your own credentials | Your login and developer ID, and for each game its file name, size, system and checksum (for files up to 64 MB), or its title when the file isn't recognised. |
| IGDB | Only with your own Twitch client ID and secret | Your client ID and secret (to Twitch, for a short-lived token), then the game's title and system. |
| SteamGridDB | Only with your own API key | Your API key and the game's title. |
| HowLongToBeat | Off until you switch it on | The game's title. |
| RetroAchievements | Only when signed in | Your username and web API key, the system's public game list (cached for a week), and the ID of a matched game to get your progress. Games are matched by a hash computed on the device; the file itself is never sent. |

**Hidden games are never sent anywhere**, and neither is any game in a hidden folder or system. They are left out of every scrape and achievement lookup, and **Choose artwork** won't run for them.

Each service has its own privacy policy, which applies to what you send it. [docs/PRIVACY.md](../PRIVACY.md) lists every address in detail.

## Hidden games, folders and systems

**Start › Hidden** lists everything hidden: games, folders, systems and home folders. **A** unhides one; **Y** shows just that one until hidden games are next tucked away; the top row shows or tucks away all of them. **Settings › Play & stats › Hidden** lists the same things.

The Start row appears only when something is hidden. In private mode it appears only while **L1 + R1 + Select** has hidden games showing, so nothing on the Start menu suggests that anything is hidden. Showing something does not change what is sent: it is still left out of scraping and achievements.

## What Tandem stores

All of it is on your device:

| What | Where | Why |
| --- | --- | --- |
| Per-game details: title, description, genres, year, rating, favorite, hidden, chosen emulator, artwork paths | `library.json` in Tandem's private storage | To show your library |
| Play history: play count, total play time, last played, first seen | `library.json` | Continue playing, Most played, sorting |
| Settings | `settings.json` in Tandem's private storage | To remember your choices |
| Journal notes and play sessions | Tandem's private storage | The journal and the stats screen |
| Scraping keys and logins (ScreenScraper, IGDB, SteamGridDB) and your RetroAchievements key | `scraping.json` and `achievements.json` in Tandem's private storage | To talk to the services you set up. Left out of Tandem's backups unless you include them, and never in Android's cloud backup |
| Downloaded artwork, logos and video snaps | `ROMs/downloaded_media/<system>/…` | So they are yours to keep, in the layout other frontends read. **Clear downloaded media** removes only what Tandem downloaded |
| The `ROMs` folder you picked | Android app preferences | To share game files with emulators |
| The last game started in each emulator, and when the current session began | Android app preferences | Resume and play time |

The JSON files are plain text. **Settings › Data & about › Back up** copies them to a dated folder in `ROMs/tandem/backup/`, where you can read them; keys and logins only go too if you switch on **Account keys and logins**. Tandem also makes automatic backups there before a restore, a Cocoon import, a Quick look or a reset of the look, and keeps the last five. A backup holds hidden games and hidden folders as they are in Tandem, and other apps with storage access can read that folder. Uninstalling Tandem deletes its private storage, but not those backups or your downloaded artwork. See [Back up and restore](library.md#back-up-and-restore).

Android's own backup may include Tandem's private storage if you have it switched on, the same as for any app, except the files holding keys and logins and the LaunchBox database. That backup goes wherever your device's backup settings send it.

## Permissions

| Permission | Used for |
| --- | --- |
| All files access | Reading the `ROMs` folder and its artwork. Tandem writes only to `ROMs/tandem/` (backups, imported theme packs) and `ROMs/downloaded_media/` (artwork you scrape). |
| Internet | Scraping and RetroAchievements, only when you start them (see above). |
| Network state | Warning you before a big download on mobile data. |
| Wi-Fi state, and Modify system settings (optional) | The quick panel (hold Start): showing whether Wi-Fi is on, and changing brightness once you allow it. |
| Access to the `ROMs` folder you pick | Handing individual game files to emulators that only accept shared files. |
| See installed apps | Finding your emulators and bringing a running one back to the front. |
| Shizuku (optional) | Checking whether an emulator is still running, and closing it before switching games. See [Resume and Shizuku](resume.md). |
| Accessibility: "Tandem: Controller Home button" (optional, off until you switch it on) | Making a Bluetooth or USB controller's Home (guide) button work in other apps. It filters key events only: it keeps that one button from external controllers and lets everything else through. It cannot read the screen, records nothing and sends nothing. See [Controls](controls.md#bluetooth-and-usb-controllers). |

## The website

This website is static pages on GitHub Pages. It sets no cookies, runs no analytics, and loads nothing from other sites; the typeface is served from here. GitHub, as the host, sees the usual request details (such as your IP address) as described in [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

## Questions

Something here unclear or wrong? Open an [issue](https://github.com/ZahakJ/tandem/issues). For anything sensitive, use a [private security report](https://github.com/ZahakJ/tandem/security/advisories/new) instead.
