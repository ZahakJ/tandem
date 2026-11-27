# Privacy and scraping

Your library and play history stay on your handheld. Tandem has no analytics, telemetry, ads or account of its own, and goes online only for features you start: scraping artwork and details, RetroAchievements or Steam achievements if you sign in, the Discord status if you connect it, and the look gallery when you open it. The two exceptions are a daily check for a new version of Tandem, and art for Steam shortcuts, both below. [Bump](#bump), if you switch it on, swaps a small card with Tandems in Bluetooth reach and never goes online.

## When Tandem uses the network

Here is every moment Tandem talks to the internet. Almost all of them start with something you do; the last two (marked as exceptions) are the only ones that happen on their own.

- **Scraping**: **Settings › Library & art › Scrape library**, <kbd>Y</kbd> › **Scrape these games** on a system, or <kbd>Y</kbd> › **Scrape this game** on a game. A scrape you start keeps going in the background, with its progress on the bottom screen, until it finishes or you stop it.
- <kbd>Y</kbd> › **Choose artwork** on a game, which asks your sources for pictures of that one game.
- **Fetch system art** (**Settings › Look & feel › System icons**, the **Scrape library** screen, or a system's **Edit › Look › Artwork**), which fetches photos, logos and backdrops of your systems themselves, never anything about your games. See [System art](scraping.md#system-art).
- **Test** on a source's card, which looks up one well-known game, never one of yours.
- **The LaunchBox database**, downloaded only after you agree to it. A scrape you start refreshes a month-old copy, on Wi-Fi only.
- **RetroAchievements**, only after you sign in with your username and web API key: your profile and recent unlocks on Stats › Achievements, a game's progress when it is highlighted (at most every ten minutes), its whole list when you open <kbd>Y</kbd> › **Achievements** (or the in-game **Achievements** page, when nothing was kept from before), and **Find match** in a game's Edit details when you open it. An achievement's comments (players' hints) are fetched only when you press <kbd>A</kbd> or tap that achievement, never ahead. Nothing is checked while you play, and never for a hidden game.
- **Steam achievements**, only after you enter your own Steam Web API key and account: when you sign in, when you choose **Look up achievements**, when a Steam game is highlighted (at most every ten minutes), and once when you come back from playing one (never for a hidden game).
- **Discord status**, only after you connect your Discord account, and only while a game plays (see [Discord status](#discord-status)).
- **Get a look** (**Settings › Look & feel › Get a look**): opening it reads the community gallery's list from GitHub, then the preview pictures of the looks it shows, and a look's file when you pick one. **From a link**, or a `tandem://look` link from a QR code, downloads one file, after you say yes. No account, and nothing about you or your games is sent. See [Sharing looks](themes.md#sharing-looks).
- **The update check**, the first exception: once a day while Tandem is open (and when you press <kbd>A</kbd> on **Settings › Play & system › Updates › Updates**), Tandem asks GitHub for the number of its latest release. Nothing about you is sent beyond what any download carries (your IP address). Opening the update popup also downloads the release's screenshots, when it has some, for the preview. The new APK is downloaded only when you press **Update**. **Check automatically** (there too) turns it off, private mode skips it, and builds made without self-update (for F-Droid) have none. See [Software updates](getting-started.md#software-updates).
- **Steam shortcuts' art**, the second exception: after a library scan, a Windows game that is a `.steam` shortcut (or a Steam game installed in DroidDeck or GameNative) and has no box art, hero or logo yet gets them from Steam's public store pictures, by its app id. No account, no key, nothing else sent, never for a hidden game, and never in private mode. To avoid it, put the pictures in place yourself ([Windows and Steam art](library.md#windows-and-steam-games)).

Tandem never scrapes on its own or on a schedule. Peanuts Native, the Switch save sync ([Switch games](switch.md)) and reading the games installed in DroidDeck and GameNative ([how](library.md#games-installed-in-droiddeck-and-gamenative)) work on the device only, through Shizuku, and never go online: their pictures are copied from what the apps already have.

> [!TIP]
> In [private mode](#private-mode) none of this happens: Tandem stays offline.

<details>
<summary>The permissions behind it</summary>

Tandem declares Android's `INTERNET` permission for scraping, RetroAchievements, Steam achievements, the Discord status, the look gallery and its own update check only; there are no crash reports. See the [manifest](../../app/src/main/AndroidManifest.xml) and the code. `ACCESS_NETWORK_STATE` only lets it warn you before a big download on mobile data.

</details>

## What is sent, and to whom

This is exactly what each service receives. Each source can be switched off in **Settings › Library & art › Set up sources**; a source that is off is never contacted.

| Service | When | What it receives |
| --- | --- | --- |
| LaunchBox | On by default, no account | Nothing about your games: the whole database is downloaded and games are matched on the device. Then the pictures you get are downloaded by name. |
| libretro thumbnails | On by default, no account | A system's folder listing, once a week; then picture names made from your game's file name or title. |
| ScreenScraper | Only with your own credentials | Your login and developer ID, and for each game its file name, size, system and checksum (for files up to 64 MB), or its title when the file isn't recognised. |
| IGDB | Only with your own Twitch client ID and secret | Your client ID and secret (to Twitch, for a short-lived token), then the game's title and system. |
| SteamGridDB | Only with your own API key | Your API key and the game's title. |
| HowLongToBeat | Off until you switch it on | The game's title. |
| System art: Wikidata and Wikimedia Commons | When you fetch system art; no account | Wikidata: the Wikidata ids of your systems, in one query. Commons: the file names Wikidata gave back. Then the pictures, by their addresses. |
| System art: RetroArch's icons (on GitHub) | When you fetch system art; no account | Each system's libretro name ("Nintendo - Game Boy"), as part of its icon's address. |
| System art: ScreenScraper and IGDB | When you fetch system art, only when switched on and set up | ScreenScraper: your login and developer ID, to read its list of every system (which of them you have isn't sent), then the pictures. IGDB: your client ID and secret (to Twitch), then the IGDB ids of your systems. |
| System art: Wikidata and Wikimedia Commons | When you fetch system art, no account | Wikidata: the Wikidata ids of your systems (one query). Commons: the file names Wikidata gave. Then the pictures, by address. Nothing about your games. |
| System art: RetroArch icons (GitHub) | When you fetch system art, no account | Each system's libretro name ("Nintendo - Game Boy"), as part of the icon's address. |
| System art: ScreenScraper and IGDB | When you fetch system art, only when set up | ScreenScraper: your login and developer ID, for its list of every system (it learns nothing about which you have). IGDB: your client ID and secret (to Twitch), then the IGDB ids of your systems. |
| RetroAchievements | Only when signed in | Your username and web API key, the system's public game list (cached for a week), the ID of a matched game to get your progress and its list, and the ID of an achievement whose hints you open. Games are matched by a hash computed on the device (for disc games too: PlayStation, PlayStation 2, PSP, Sega CD, Saturn, PC Engine CD); the file and the hash are never sent, the hash is only compared with the list on the device. Badge pictures come from media.retroachievements.org. |
| Steam store pictures | After a scan, for `.steam` shortcuts missing art | The app id in the shortcut, as part of the picture's address. |
| Steam Web API | Only with your own key and account | Your key and your SteamID64 (or the profile name you typed, once, to find it), and the app id of each Steam game in your library, to read its achievements and their names and icons. The share of players who have each one is asked for with the app id alone. |
| Discord | Only after you connect your account, in builds that include it | While a game plays: its title, its cover and when you started. The connection to Discord stays open only while you play and for a minute after. |
| The look gallery (GitHub) | When you open **Get a look**, and when you pick a look or a link | Nothing about you: requests for the gallery's list, its preview pictures and the look you pick. The list is asked for again only when it changed. |

> [!IMPORTANT]
> **Hidden games are never sent anywhere**, and neither is any game in a hidden folder or system. They are left out of every scrape and achievement lookup, **Choose artwork** won't run for them, and they never show as your Discord status.

Each service has its own privacy policy, which applies to what you send it. [docs/PRIVACY.md](../PRIVACY.md) lists every address in detail.

## Discord status

Tandem can show what you play as your Discord status: "Playing" and the game's title, its cover, and how long you've been playing. It clears when you stop. It is off until you connect your account in **Settings › Library & art › Accounts › Discord status**: Discord (or your browser, without the Discord app) asks you to authorize Tandem, once. Tandem never asks for your Discord password and never sees it.

- Hidden games, and games in hidden folders or systems, never show, and nothing is sent while private mode is on (not even a cover lookup).
- The **Discord status** tile in **Quick settings** (hold <kbd>Start</kbd>) pauses it and turns it back on, staying connected; turning it off clears your status at once.
- Tandem keeps only the token Discord gives it, in its private storage, outside Tandem's backups and Android's backups. **Disconnect Discord** (the row below it) clears your status, revokes the token with Discord and forgets it. Removing Tandem from **Authorized Apps** in Discord's settings works too.
- The status uses Discord's own Social SDK, which is not open source. A build made without it has no Discord status at all and never contacts Discord; see [Building from source](building.md#discord-status).

## Bump

[Bump](plaza.md) swaps a small card with other Tandems you bump into, over Bluetooth LE. It never goes online, and
it is off until you switch it on in **Settings › Play & system › Bump**.

- **What goes out, and to whom**: your card, only to Tandems within Bluetooth reach, only while Bump is on and
  running: your character, the name you gave it, a preset greeting (never free text), your favourite game's title if
  you picked one, the title you played last only if you share it, how many Tandems you've bumped into, and the public key
  that signs the card. **What others see** (on the same page) shows exactly that, from your current card. Hidden games
  are never on it.
- **What never goes out**: your location, accounts, anything you typed, your library, play time, files, and anything
  at all over the internet. Tandem doesn't ask for the location permission; Android's **Nearby devices** permission is
  declared "never for location".
- **On the air**: an advertisement with Bump's service and a random code that changes about every 15 minutes.
  No name, no Bluetooth name and no fixed number, so nobody can tell it's the same handheld from one quarter of an hour
  to the next by listening. The swap itself is a short Bluetooth connection; each card is signed over a fresh random
  number from the other side, so it can't be forged or recorded and replayed. It isn't encrypted: someone listening
  nearby could read the card as it passes, which is no more than anyone you bump into sees on their Plaza.
- **When it runs**: only while Tandem is in front, unless you switch on **Bump while playing**, which keeps it going
  in games with a notification. Private mode pauses it and closes the Plaza.
- **People you met** stay on your handheld (`plaza.json`). **Hide**, **Block** (by key: their cards are refused and
  never answered) and **Clear Plaza** are in the Plaza and on the settings page.

## Hidden games, folders and systems

One place lists everything you've hidden, so nothing gets lost. <kbd>Start</kbd> › **Hidden** shows games, folders, systems and home folders:

- <kbd>A</kbd> unhides one.
- <kbd>Y</kbd> shows just that one until hidden games are next tucked away.
- The top row shows or tucks away all of them.

The Start row appears only when something is hidden. In private mode it appears only while your PIN has hidden games showing, so nothing on the Start menu suggests that anything is hidden. Showing something does not change what is sent: it is still left out of scraping and achievements.

Hiding asks nothing: the message says it is hidden, and <kbd>Select</kbd> (or a tap on the message) undoes it for 6 seconds. After that, <kbd>Start</kbd> › **Hidden** brings it back.

## Private mode

Without private mode, anyone holding your handheld can switch on **Show hidden games**. Private mode puts hidden games behind a PIN and keeps Tandem offline. Switch it on in **Settings › Play & system › Privacy & hidden › Private mode**, or press <kbd>L1</kbd> + <kbd>R1</kbd> + <kbd>Select</kbd> anywhere in Tandem.

- **The PIN.** The first time, Tandem asks you to choose a PIN of 4 to 6 digits, twice. Type it with the D-pad and <kbd>A</kbd> on the keypad, or tap the keys; <kbd>X</kbd> deletes, <kbd>Start</kbd> is OK, <kbd>B</kbd> cancels. **Settings › Play & system › Privacy & hidden › Private mode PIN** changes it, after asking for the current one.
- **Showing hidden games** needs the PIN: <kbd>L1</kbd> + <kbd>R1</kbd> + <kbd>Select</kbd>, **Settings › Play & system › Privacy & hidden › Show hidden games** or a View menu, or the top row of <kbd>Start</kbd> › **Hidden**. Tucking them away never asks: <kbd>L1</kbd> + <kbd>R1</kbd> + <kbd>Select</kbd> again, **Tuck away**, or the screensaver, which tucks them away by itself.
- **Turning private mode off** needs the PIN too.
- **The quick switch.** While private mode is off, <kbd>L1</kbd> + <kbd>R1</kbd> + <kbd>Select</kbd> turns it on at once (asking you to choose a PIN first only if there is none yet), and whatever hidden games were showing go out of sight.
- **Wrong tries.** After three wrong PINs in a row, each further try has to wait: 30 seconds, then 1 minute, 2, 4 and so on, up to 30 minutes. The count survives a restart.
- **Forgot your PIN?** Press <kbd>Y</kbd> (or tap **Forgot**) on the PIN pad and start the wait. Everything hidden stays hidden. After 24 hours the PIN pad lets you choose a new PIN, and anything that was showing is tucked away first. Entering the right PIN before then calls the wait off. Nothing gets round a forgotten PIN faster, which is the point: someone who has your handheld for a while can't use Forgot PIN to see your hidden games.
- **Private mode from before PINs.** If private mode was already on when you updated Tandem (or a restored backup turned it on), there is no PIN yet: the first time something asks for it, you choose one.

> [!WARNING]
> The PIN keeps hidden games out of Tandem. It is not encryption: the game files stay in your games folders, where a file manager or an emulator can still see them.

<details>
<summary>How the PIN is stored</summary>

The PIN itself is never stored, only a salted hash of it (PBKDF2), in `private-pin.json` in Tandem's no-backup storage. It is never in Tandem's backups, Android's backups or device transfers, so restoring a backup never brings an old PIN back. Clearing Tandem's storage or uninstalling it removes the PIN too.

</details>

### Staying offline

While private mode is on, Tandem itself sends nothing online:

- No scraping, **Choose artwork**, source tests or LaunchBox database download; they say private mode keeps Tandem offline. A scrape under way stops when private mode turns on.
- No RetroAchievements: no progress, profile, game lists, hints or sign-in; a game's list shows what was kept from before.
- No Steam achievements: no lookups, no sign-in; what was fetched before still shows.
- No Discord status, and no cover lookups for it.
- No look gallery and no look downloads; a `.tandem` file you pick still imports.
- No pictures from the web: only those already in Tandem's picture cache show.
- No Bump: it pauses (nothing is advertised or listened for) and the Plaza stays closed.

Nothing is kept to send later. Turning private mode off only makes the next lookup you start possible again.

**Settings › Play & system › Privacy & hidden › Offline for hidden games** (off by default, needs [Shizuku](resume.md)) goes further. When a hidden game starts in private mode (so after your PIN brought it out), Tandem turns on Android's airplane mode first, and switches Wi-Fi off too if Android keeps it on in airplane mode. That keeps the emulator itself, and every other app, offline while the game runs: its own achievements, netplay, update checks or rich presence.

<details>
<summary>How Tandem goes offline and comes back</summary>

- When the game's session ends, Tandem puts airplane mode and Wi-Fi back as they were: when you come back to Tandem, when the game quits or crashes (Tandem looks every few seconds while its bottom screen shows the game), or when Tandem starts again after being closed with no game left open.
- Airplane mode that was already on stays on: Tandem only turns off what it turned on.
- Tandem writes down what it changed as soon as it changes it (`offline.json`, in the same no-backup storage), so it can put it back after a restart too.
- If Tandem can't confirm the handheld is offline (Shizuku isn't running, or Android refuses), the game doesn't start. Pip says "Couldn't go offline, game not started", without naming the game, and offers **Start anyway**.
- **Until closed** (the first choice after Off) keeps airplane mode on when you come back to Tandem with the hidden game still open behind it, where it could still sync. Tandem checks every 20 seconds and puts the radios back once the emulator's app has closed. Tucking hidden games away again (<kbd>L1</kbd> + <kbd>R1</kbd> + <kbd>Select</kbd>) brings them back at once. With **While playing**, coming back to Tandem ends the session and the handheld goes back online; resuming the game takes it offline again.

</details>

What it doesn't cover: whatever an emulator saved while offline and sends once the handheld is back online, and games or apps started outside Tandem. For anything that must never go online, also turn off the emulator's own network features (RetroAchievements, netplay, cloud saves) in its settings.

## What Tandem stores

Everything Tandem knows lives on your device. Here is each piece, where it sits and why:

| What | Where | Why |
| --- | --- | --- |
| Per-game details: title, description, genres, year, rating, favorite, hidden, chosen emulator, artwork paths | `library.json` in Tandem's private storage | To show your library |
| Play history: play count, total play time, last played, first seen | `library.json` | Continue playing, Most played, sorting |
| This week: whether it shows, the games you said aren't for you, what the board showed in the last eight weeks, this week's re-rolls, and a random number made once on the handheld | `week.json` in Tandem's private storage | To keep the board the same all week and not repeat itself. The picks and your taste are worked out on the handheld each time; nothing is sent anywhere, and hidden games never take part. In Tandem's backups with the game data, never in a shared look. See [This week](library.md#this-week) |
| Settings | `settings.json` in Tandem's private storage | To remember your choices |
| Bump: its switches, your card (Me), the people you bumped into (their latest card, how often and when you bumped into them, waves), and the keys you blocked | `plaza.json` in Tandem's private storage | For the Plaza. In Tandem's backups with the game data, never in a shared look, and never in Android's cloud backup (it holds other people's cards). See [Bump](plaza.md) |
| Your Bump key (a 32-byte seed) | `pass-identity.bin` in Tandem's private no-backup storage, sealed with a key that stays in the Android keystore | To sign your card. Never in Tandem's backups, Android's backups or a transfer: a new handheld makes a new key |
| Journal notes and play sessions | Tandem's private storage | The journal and the stats screen |
| Notes, with their screenshots and drawings | `ROMs/notes/<notebook>/` as plain Markdown files, pictures in `<note>.assets/` | So they are yours: readable in any app, kept after uninstalling. Never sent anywhere; any app with storage access can read them. A hidden game's notebook stays out of Tandem's lists while hidden games are hidden. See [Notes](notes.md) |
| Scraping keys and logins (ScreenScraper, IGDB, SteamGridDB), your RetroAchievements key, and your Steam Web API key and account | `scraping.json`, `achievements.json` and `steam.json` in Tandem's private storage | To talk to the services you set up. Left out of Tandem's backups unless you include them, and never in Android's cloud backup |
| Your RetroAchievements progress in each game you opened or highlighted, as last fetched | `ra/progress-<game id>.json` in Tandem's private storage (beside `ra/console-<id>.json`, each system's public game list) | To show a game's list at once, and offline. Not in any backup. **Sign out** deletes them; players' hints are only kept in memory until Tandem closes |
| Your Steam games' achievements, as last fetched | `steam/<app id>.json` in Tandem's private storage | To show them at once, and to tell what is new after a session. Not in any backup; fetched again on the device. **Sign out** of Steam deletes them |
| Your Discord token, if you connected Discord | `discord.json` in Tandem's private no-backup storage | To show your status. Never in Tandem's backups or Android's backups; **Disconnect Discord** deletes it |
| A salted hash of your private mode PIN, and the count of wrong tries | `private-pin.json` in Tandem's private no-backup storage | To check the PIN. Never in Tandem's backups or Android's backups |
| What Tandem turned off to go offline for a hidden game | `offline.json` in Tandem's private no-backup storage, only while it is offline | To put airplane mode and Wi-Fi back, even after a restart |
| Downloaded artwork, logos and video snaps | `ROMs/downloaded_media/<system>/…` | So they are yours to keep, in the layout other frontends read. **Clear downloaded media** removes only what Tandem downloaded |
| System art you fetched (console photos, logos, backdrops), and who made each | `ROMs/tandem/art/systems/<system>/`, with `credits.json`; the icons and heroes made from them in Tandem's cache | To dress your systems. Never put in a shared look; **Clear downloaded media** removes it. See [System art](scraping.md#system-art) |
| Switch saves replaced by the save sync | `ROMs/tandem/save-sync/backups/<title ID>/<app>/` | So a save that was overwritten can be put back. The newest 20 per game and app are kept. See [Switch games](switch.md#backups) |
| Game manuals and guides, and the page each was left at | `ROMs/manuals/<system>/` (your files, and copies of the ones you pick in Edit details); `manual-pages.json` in Tandem's private storage | Read on either screen. Never sent anywhere, never deleted by Tandem. See [Manuals and guides](library.md#manuals-and-guides) |
| Looks you save, and looks you get | `ROMs/tandem/looks/` (files you save with **Share my look**); `ROMs/tandem/themes/<name>/` (each look you use, as a theme pack); the name, author and description you typed, in Android app preferences; the gallery's list and previews in Tandem's cache | To share and use looks. A look file holds only look settings and pictures, never anything about your games or accounts |
| Bug reports you save | `ROMs/tandem/reports/tandem-report-<date>.zip` | Only when you choose **Send a bug report**. Redacted first (keys, logins, passwords, e-mail and IP addresses, hidden games' names, your folder and file names); sent nowhere unless you share it. Delete them whenever you like. See [Reporting a bug](faq.md#reporting-a-bug) |
| Playlists for multi-disc games | `.m3u` files next to the discs, each starting `# Made by Tandem` | Only when you choose **Make playlists**. See [Multi-disc games](library.md#multi-disc-games) |
| The `ROMs` folder, if you picked it | Android app preferences | To share game files with emulators |
| The last game started in each emulator, and when the current session began | Android app preferences | Resume and play time |

<details>
<summary>Backups, and what Android's own backup may include</summary>

The JSON files are plain text. **Settings › Play & system › Your data › Back up** copies them to a dated folder in `ROMs/tandem/backup/`, where you can read them; keys and logins only go too if you switch on **Account keys and logins**. Tandem also makes automatic backups there before a restore, a Cocoon import or a reset of the look, and keeps the last five. A backup holds hidden games and hidden folders as they are in Tandem, and other apps with storage access can read that folder. Uninstalling Tandem deletes its private storage, but not those backups or your downloaded artwork. See [Back up and restore](library.md#back-up-and-restore).

Android's own backup may include Tandem's private storage if you have it switched on, the same as for any app, except the files holding keys and logins and the LaunchBox database. That backup goes wherever your device's backup settings send it.

</details>

## Permissions

Every permission Tandem asks for, and the one thing each is for. The optional ones stay off until you allow them.

| Permission | Used for |
| --- | --- |
| All files access | Reading the `ROMs` folder and its artwork. Tandem writes only to `ROMs/tandem/` (backups, imported theme packs, play stats exports, Switch save-sync backups, system art you fetch), `ROMs/downloaded_media/` (artwork you scrape), `ROMs/screenshots/` and `ROMs/clips/` (the companion's Screenshot and Clip buttons) and `ROMs/notes/` (your notes). |
| Internet | Scraping, RetroAchievements, Steam achievements, the Discord status and the look gallery, only when you start or connect them, and the daily update check (see above). |
| Install apps, and uninstall apps | Installing a new Tandem when you press **Update**; opening Android's uninstall screen for **Uninstall app…** on an Android game. |
| Do Not Disturb access | **Immersion mode**: Do Not Disturb while a game plays. |
| Eden Duo's companion control | Showing a Switch game's own second screen through Eden Duo (**Game screen**). |
| Network state | Warning you before a big download on mobile data. |
| Wi-Fi state, and Modify system settings (optional) | The quick panel (hold <kbd>Start</kbd>): showing whether Wi-Fi is on, and changing brightness once you allow it. |
| Access to the games folders you pick | Handing individual game files to emulators that only accept shared files. |
| See installed apps | Finding your emulators and bringing a running one back to the front. |
| Notification access (optional, off until you allow it) | Showing your notifications in the side panel (<kbd>R2</kbd> on home), where <kbd>A</kbd> opens one and <kbd>Y</kbd> clears it. They are kept in memory only, never stored or sent; in private mode only the app and time show. With Shizuku, Tandem can switch this on for itself when you ask. |
| Nearby devices: Bluetooth scan, advertise and connect (optional, asked only when you switch Bump on) | [Bump](plaza.md): finding other Tandems close by, being found with a code that changes every 15 minutes, and swapping cards. Declared never for location; Tandem doesn't ask for location at all. Android 12 and newer only. |
| Connected-device foreground service (optional) | **Bump while playing**: Bump keeps going behind a game, with its notification. Only while that setting is on. |
| Microphone and screen capture (optional) | The companion's 15-second clips: the game's screen and sound, and your voice unless **Voice in clips** is off. With Shizuku, Tandem grants these to itself so a clip starts without asking. |
| Shizuku (optional) | Checking whether an emulator is still running, and closing it before switching games, and the other features listed in [What Shizuku is used for](resume.md#what-shizuku-is-used-for). In private mode, also turning airplane mode on while a hidden game plays. With root, also copying Switch saves between Eden Duo and Peanuts Native ([Switch games](switch.md#one-save-across-eden-duo-and-peanuts-native)). |
| Accessibility: "Tandem: Controller Home button" (optional, off until you switch it on) | Making a Bluetooth or USB controller's Home (guide) button work in other apps. It filters key events only: it keeps that one button from external controllers and lets everything else through. It cannot read the screen, records nothing and sends nothing. See [Controls](controls.md#bluetooth-and-usb-controllers). |

## The website

This website is static pages on GitHub Pages. It sets no cookies, runs no analytics, and loads nothing from other sites; the typeface is served from here. GitHub, as the host, sees the usual request details (such as your IP address) as described in [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

## Questions

Something here unclear or wrong? Open an [issue](https://github.com/ZahakJ/tandem/issues). For anything sensitive, use a [private security report](https://github.com/ZahakJ/tandem/security/advisories/new) instead.
