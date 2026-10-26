# Privacy and scraping

Your library and play history stay on your handheld. Tandem has no analytics, telemetry, ads or account of its own, and goes online only for features you start: scraping artwork and details, RetroAchievements if you sign in, and the Discord status if you connect it.

## When Tandem uses the network

Tandem declares Android's `INTERNET` permission for scraping, RetroAchievements and the Discord status only; there are no crash reports or update checks. See the [manifest](../../app/src/main/AndroidManifest.xml) and the code.

It goes online only when you ask it to:

- **Scraping**: **Settings › Art & accounts › Scrape library**, **Scrape these games** in a system's Y menu, or **Scrape this game** in a game's Y menu. A scrape you start keeps going in the background, with its progress on the bottom screen, until it finishes or you stop it.
- **Choose artwork** in a game's Y menu, which asks your sources for pictures of that one game.
- **Test** on a source's card, which looks up one well-known game, never one of yours.
- **The LaunchBox database**, downloaded only after you agree to it. A scrape you start refreshes a month-old copy, on Wi-Fi only.
- **RetroAchievements**, only after you sign in with your username and web API key, and for **Find match** in a game's Edit details when you open it (never for a hidden game).
- **Discord status**, only after you connect your Discord account, and only while a game plays (see [Discord status](#discord-status)).

Tandem never scrapes on its own or on a schedule. `ACCESS_NETWORK_STATE` only lets it warn you before a big download on mobile data.

In [private mode](#private-mode) none of this happens: Tandem stays offline.

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
| Discord | Only after you connect your account, in builds that include it | While a game plays: its title, its system and when you started. The connection to Discord stays open only while you play and for a minute after. |

**Hidden games are never sent anywhere**, and neither is any game in a hidden folder or system. They are left out of every scrape and achievement lookup, **Choose artwork** won't run for them, and they never show as your Discord status.

Each service has its own privacy policy, which applies to what you send it. [docs/PRIVACY.md](../PRIVACY.md) lists every address in detail.

## Discord status

Tandem can show what you play as your Discord status: "Playing" and the game's title, its system under it, and how long you've been playing. It clears when you stop. It is off until you connect your account in **Settings › Art & accounts › Discord › Discord status**: Discord (or your browser, without the Discord app) asks you to authorize Tandem, once. Tandem never asks for your Discord password and never sees it.

- Hidden games, and games in hidden folders or systems, never show, and nothing is sent while private mode is on (not even a cover lookup).
- The **Discord status** tile in **Quick settings** (hold Start) pauses it and turns it back on, staying connected; turning it off clears your status at once.
- Tandem keeps only the token Discord gives it, in its private storage, outside Tandem's backups and Android's backups. **Disconnect Discord** (under More options) clears your status, revokes the token with Discord and forgets it. Removing Tandem from **Authorized Apps** in Discord's settings works too.
- The status uses Discord's own Social SDK, which is not open source. A build made without it has no Discord status at all and never contacts Discord; see [Building from source](building.md#discord-status).

## Hidden games, folders and systems

**Start › Hidden** lists everything hidden: games, folders, systems and home folders. **A** unhides one; **Y** shows just that one until hidden games are next tucked away; the top row shows or tucks away all of them. **Settings › Play & stats › Hidden** lists the same things.

The Start row appears only when something is hidden. In private mode it appears only while your PIN has hidden games showing, so nothing on the Start menu suggests that anything is hidden. Showing something does not change what is sent: it is still left out of scraping and achievements.

Hiding asks nothing: the message says it is hidden, and **Select** (or a tap on the message) undoes it for 6 seconds. After that, **Start › Hidden** brings it back.

## Private mode

Without private mode, anyone holding your handheld can switch on **Show hidden games**. Private mode puts hidden games behind a PIN and keeps Tandem offline. Switch it on in **Settings › Play & stats › Privacy & rest › Private mode**, or press **L1 + R1 + Select** anywhere in Tandem.

- **The PIN.** The first time, Tandem asks you to choose a PIN of 4 to 6 digits, twice. Type it with the D-pad and **A** on the keypad, or tap the keys; **X** deletes, **Start** is OK, **B** cancels. **Private mode PIN** (under More options) changes it, after asking for the current one.
- **Showing hidden games** needs the PIN: **L1 + R1 + Select**, **Show hidden games** in Settings or a View menu, or **Show** on **Settings › Play & stats › Hidden**. Tucking them away never asks: **L1 + R1 + Select** again, **Tuck away**, or the screensaver, which tucks them away by itself.
- **Turning private mode off** needs the PIN too.
- **The quick switch.** While private mode is off, **L1 + R1 + Select** turns it on at once (asking you to choose a PIN first only if there is none yet), and whatever hidden games were showing go out of sight.
- **Wrong tries.** After three wrong PINs in a row, each further try has to wait: 30 seconds, then 1 minute, 2, 4 and so on, up to 30 minutes. The count survives a restart.
- **Forgot your PIN?** Press **Y** (or tap **Forgot**) on the PIN pad and start the wait. Everything hidden stays hidden. After 24 hours the PIN pad lets you choose a new PIN, and anything that was showing is tucked away first. Entering the right PIN before then calls the wait off. Nothing gets round a forgotten PIN faster, which is the point: someone who has your handheld for a while can't use Forgot PIN to see your hidden games.
- **Private mode from before PINs.** If private mode was already on when you updated Tandem (or a restored backup turned it on), there is no PIN yet: the first time something asks for it, you choose one.

The PIN itself is never stored, only a salted hash of it (PBKDF2), in `private-pin.json` in Tandem's no-backup storage. It is never in Tandem's backups, Android's backups or device transfers, so restoring a backup never brings an old PIN back. Clearing Tandem's storage or uninstalling it removes the PIN too.

The PIN keeps hidden games out of Tandem. It is not encryption: the game files stay in your ROMs folder, where a file manager or an emulator can still see them.

### Staying offline

While private mode is on, Tandem itself sends nothing online:

- No scraping, **Choose artwork**, source tests or LaunchBox database download; they say private mode keeps Tandem offline. A scrape under way stops when private mode turns on.
- No RetroAchievements: no progress, profile, game lists or sign-in.
- No Discord status, and no cover lookups for it.
- No pictures from the web: only those already in Tandem's picture cache show.

Nothing is kept to send later. Turning private mode off only makes the next lookup you start possible again.

**Go offline while a hidden game plays** (**Settings › Play & stats › Privacy & rest**, off by default, needs [Shizuku](resume.md)) goes further. When a hidden game starts in private mode (so after your PIN brought it out), Tandem turns on Android's airplane mode first, and switches Wi-Fi off too if Android keeps it on in airplane mode. That keeps the emulator itself, and every other app, offline while the game runs: its own achievements, netplay, update checks or rich presence.

- When the game's session ends, Tandem puts airplane mode and Wi-Fi back as they were: when you come back to Tandem, when the game quits or crashes (Tandem looks every few seconds while its bottom screen shows the game), or when Tandem starts again after being closed with no game left open.
- Airplane mode that was already on stays on: Tandem only turns off what it turned on.
- Tandem writes down what it changed as soon as it changes it (`offline.json`, in the same no-backup storage), so it can put it back after a restart too.
- Without Shizuku, or if Android refuses, the game still starts, online.
- Coming back to Tandem ends the session, so the handheld is back online while you are in Tandem, even with the game still open behind it. Resuming the game from Tandem takes it offline again.

What it doesn't cover: whatever an emulator saved while offline and sends once the handheld is back online, and games or apps started outside Tandem. For anything that must never go online, also turn off the emulator's own network features (RetroAchievements, netplay, cloud saves) in its settings.

## What Tandem stores

All of it is on your device:

| What | Where | Why |
| --- | --- | --- |
| Per-game details: title, description, genres, year, rating, favorite, hidden, chosen emulator, artwork paths | `library.json` in Tandem's private storage | To show your library |
| Play history: play count, total play time, last played, first seen | `library.json` | Continue playing, Most played, sorting |
| Settings | `settings.json` in Tandem's private storage | To remember your choices |
| Journal notes and play sessions | Tandem's private storage | The journal and the stats screen |
| Scraping keys and logins (ScreenScraper, IGDB, SteamGridDB) and your RetroAchievements key | `scraping.json` and `achievements.json` in Tandem's private storage | To talk to the services you set up. Left out of Tandem's backups unless you include them, and never in Android's cloud backup |
| Your Discord token, if you connected Discord | `discord.json` in Tandem's private no-backup storage | To show your status. Never in Tandem's backups or Android's backups; **Disconnect Discord** deletes it |
| A salted hash of your private mode PIN, and the count of wrong tries | `private-pin.json` in Tandem's private no-backup storage | To check the PIN. Never in Tandem's backups or Android's backups |
| What Tandem turned off to go offline for a hidden game | `offline.json` in Tandem's private no-backup storage, only while it is offline | To put airplane mode and Wi-Fi back, even after a restart |
| Downloaded artwork, logos and video snaps | `ROMs/downloaded_media/<system>/…` | So they are yours to keep, in the layout other frontends read. **Clear downloaded media** removes only what Tandem downloaded |
| The `ROMs` folder you picked | Android app preferences | To share game files with emulators |
| The last game started in each emulator, and when the current session began | Android app preferences | Resume and play time |

The JSON files are plain text. **Settings › Data & about › Back up** copies them to a dated folder in `ROMs/tandem/backup/`, where you can read them; keys and logins only go too if you switch on **Account keys and logins**. Tandem also makes automatic backups there before a restore, a Cocoon import, a Quick look or a reset of the look, and keeps the last five. A backup holds hidden games and hidden folders as they are in Tandem, and other apps with storage access can read that folder. Uninstalling Tandem deletes its private storage, but not those backups or your downloaded artwork. See [Back up and restore](library.md#back-up-and-restore).

Android's own backup may include Tandem's private storage if you have it switched on, the same as for any app, except the files holding keys and logins and the LaunchBox database. That backup goes wherever your device's backup settings send it.

## Permissions

| Permission | Used for |
| --- | --- |
| All files access | Reading the `ROMs` folder and its artwork. Tandem writes only to `ROMs/tandem/` (backups, imported theme packs) and `ROMs/downloaded_media/` (artwork you scrape). |
| Internet | Scraping, RetroAchievements and the Discord status, only when you start or connect them (see above). |
| Network state | Warning you before a big download on mobile data. |
| Wi-Fi state, and Modify system settings (optional) | The quick panel (hold Start): showing whether Wi-Fi is on, and changing brightness once you allow it. |
| Access to the `ROMs` folder you pick | Handing individual game files to emulators that only accept shared files. |
| See installed apps | Finding your emulators and bringing a running one back to the front. |
| Shizuku (optional) | Checking whether an emulator is still running, and closing it before switching games. See [Resume and Shizuku](resume.md). In private mode, also turning airplane mode on while a hidden game plays. |
| Accessibility: "Tandem: Controller Home button" (optional, off until you switch it on) | Making a Bluetooth or USB controller's Home (guide) button work in other apps. It filters key events only: it keeps that one button from external controllers and lets everything else through. It cannot read the screen, records nothing and sends nothing. See [Controls](controls.md#bluetooth-and-usb-controllers). |

## The website

This website is static pages on GitHub Pages. It sets no cookies, runs no analytics, and loads nothing from other sites; the typeface is served from here. GitHub, as the host, sees the usual request details (such as your IP address) as described in [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

## Questions

Something here unclear or wrong? Open an [issue](https://github.com/ZahakJ/tandem/issues). For anything sensitive, use a [private security report](https://github.com/ZahakJ/tandem/security/advisories/new) instead.
