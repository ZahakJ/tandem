# Privacy

Tandem has no analytics, no telemetry, no ads and no account of its own. It does not phone home.

## When Tandem uses the network

Only when you ask it to look something up online:

- **Scraping** (Settings › Library & art › Scrape library, "Scrape these games" in a system's Y menu, or
  "Scrape this game" in a game's Y menu).
- **Choose artwork** in a game's Y menu, which asks every configured source for pictures of that one game.
- **Test** on a source's setup card, which looks up one well-known game ("Super Mario Odyssey"), never yours.
- **Downloading the LaunchBox database**, only after you agree to it (the LaunchBox card, the welcome, or the
  question when you start a scrape that needs it). A scrape you start refreshes a month-old copy, only on Wi-Fi.

**Steam shortcuts' art.** After every library scan, a Windows game that is a `.steam` shortcut (a file holding a
Steam app id) and has no box art, hero or logo yet gets the missing ones from Steam's public store CDN
(`cdn.cloudflare.steamstatic.com/steam/apps/<app id>/`: `library_600x900_2x.jpg`, `library_hero.jpg`, `logo.png`),
saved like scraped art (`data/SteamArt.kt`). It sends only the app id, needs no account or key, never runs for a
hidden game and is blocked in private mode. This is the one lookup Tandem makes without being asked; a failed one is
tried again after the next scan.

RetroAchievements is contacted only after you sign in, to show your profile and progress, and when you open
**Find match** in a game's Edit details (never for a hidden game).

Steam's Web API is contacted only after you enter your own Web API key and account (Settings › Library & art ›
Steam): once to check them (and to resolve a profile name you typed), then for each Steam game in your library when
you sign in or choose **Look up achievements**, when the game is highlighted and its record is over ten minutes old,
and once right after you come back from playing it, so Pip can say what you unlocked (`SteamAchievements.kt`). Never
for a hidden game. Each game's record is kept in `steam/<app id>.json` in Tandem's private storage; **Sign out**
deletes them.

Discord is contacted when you connect your Discord account, then only while a game plays (the Discord
status) and once to revoke the link when you disconnect. Builds without Discord's SDK never contact it. For a
game whose cover address isn't known yet, the Discord status looks one up once (LaunchBox's index on this device first, then libretro's folder listing, then
SteamGridDB if you entered a key; only sources that are on, never for a hidden game or in private mode).

While private mode is on, none of the above happens: every request Tandem makes stops before it leaves the device
(`NetGate` in `Offline.kt`: scraping and its downloads, the LaunchBox database, RetroAchievements, cover lookups and
remote pictures, which then come only from the picture cache), a scrape under way is stopped, and nothing is kept to
send later. With **Go offline while a hidden game plays** (off by default, needs Shizuku), starting a hidden game also
turns on airplane mode (and Wi-Fi off, if Android keeps it on in airplane mode) until the game's session ends; airplane
mode the user turned on is never turned off. Emulators' own network features are outside Tandem's control except
through airplane mode. See [Private mode](guide/privacy.md#private-mode).

Tandem never starts a scrape on its own or on a schedule (a scrape you start does keep running in the background until it finishes); the Steam shortcut art above is the only exception. The `INTERNET` permission exists only
for these features; `ACCESS_NETWORK_STATE` only lets Tandem warn you before a big download on mobile data.

## What is sent, and to whom

Each source can be turned off in Settings › Library & art; a source that is off is never contacted.

| Service | When | What it receives |
| --- | --- | --- |
| LaunchBox (gamesdb.launchbox-app.com, images.launchbox-app.com) | On by default, no account | Nothing about your games: the whole database is downloaded and games are looked up on the device. Then the chosen pictures are downloaded by their LaunchBox file names. |
| thumbnails.libretro.com | On by default, no account | The system's folder listing is downloaded once a week; then picture names derived from your game's file name or title. |
| ScreenScraper (api.screenscraper.fr) | Only if you entered developer credentials | Your ScreenScraper login (if set), the developer ID, and for each game: the file name, file size, system, and CRC32/MD5 of the file (only for files up to 64 MB). If the file isn't recognised, the game's title. |
| IGDB (id.twitch.tv, api.igdb.com, images.igdb.com) | Only if you entered a Twitch client ID and secret | Your client ID and secret (to Twitch, for an access token kept in memory), then the game's title and system. |
| SteamGridDB (steamgriddb.com) | Only if you entered an API key | Your API key and the game's title (also for the Discord status's cover lookup, when connected). |
| HowLongToBeat (howlongtobeat.com) | Off until you turn it on, no account | The game's title. |
| Steam's video servers | Only for video snaps from LaunchBox | The trailer's address. |
| Steam's store CDN (cdn.cloudflare.steamstatic.com) | After a library scan, for `.steam` shortcuts missing art | The app id in the shortcut, as part of the picture's address. |
| RetroAchievements (retroachievements.org, media.retroachievements.org) | Only after you sign in (Settings › Library & art › RetroAchievements) | Your username and web API key; the console's public game list with hashes (cached for a week); the ID of a matched game, for your progress in it; badge pictures. Games are matched by a hash computed on the device, the file is never sent. **Find match** in Edit details uses the same cached game list and sends nothing about the game; an ID you type or "No achievements" stays on the device. |
| Discord (discord.com, through Discord's Social SDK) | Only after you connect your account (Settings › Library & art › Discord), in builds that include the SDK | Account linking through Discord's OAuth2 (PKCE, public client; Tandem never sees a password). While a game plays: its title, when the session started and the public address of its cover picture (thumbnails.libretro.com, cdn2.steamgriddb.com and similar, images.launchbox-app.com or images.igdb.com, which Discord's servers then fetch), or of the Tandem icon on the project site when no cover address is known. An address with a login or key in it is never sent: ScreenScraper's never, other addresses only without their query. Never for hidden games or while private mode is on. The connection opens when a game starts and closes a minute after it stops. |

**Hidden games are never sent anywhere**, and neither is any game in a hidden folder or hidden system (or inside a
hidden icon-grid folder). They are left out of every scrape, Tandem checks again right before
each lookup in case you hid a game mid-scrape, and "Choose artwork" refuses to run for them.

Pictures you pick from the device stay on the device.

## Screenshots, clips and sharing

The game companion's **Screenshot** and **Clip** save to `ROMs/screenshots/` and `ROMs/clips/` on the device and
send nothing. A clip records the game's screen and sound, and your voice from the microphone (Settings › Two screens › While playing › Voice in
clips, on by default), only for the clip's 15 seconds or until you stop it; Android shows a "Recording a clip"
notification meanwhile. With Shizuku, Tandem gives itself Android's screen-capture grant and the microphone
permission so a clip starts without asking. **Share** in the Gallery hands a copy of one picture or clip to the app
you pick in Android's share sheet, and only that app can read it.

## Notifications

The side panel (R2 on home) shows the device's notifications once you allow Tandem in Android's **Notification
access** (with Shizuku, Tandem can allow itself when you ask). They are read by `NoteListener` (`Notes.kt`) and kept
in memory only while the listener is connected: never written to a file, logged or sent. A opens one and Y clears
it, as Android would. In private mode the cards show only the app and the time.

## Where things are stored

- Downloaded pictures and clips: `ROMs/downloaded_media/<system>/<icon|hero|logo|screenshot_title|screenshot_gameplay|videos>/`.
  Tandem keeps a list of files it downloaded (in its private storage) so "Clear downloaded media" deletes only
  those, never art from other apps or pictures you chose by hand.
- Notes (the Notes app and the companion's Notes): plain Markdown files in `ROMs/notes/<notebook>/<name>.md`, their
  pictures in `<name>.assets/` beside them (screenshots, drawings with their `.strokes.json`, pictures copied from the
  Gallery), and a `.game` file in each game's notebook naming the game. Nothing in them is sent anywhere; there is no
  sharing. A screenshot for a note is read from the game's screen on the device through Shizuku (`screencap`) and
  saved only in the note's folder: not in `ROMs/screenshots`, not on the clipboard, not added to Android's media
  library. The files are on shared storage, so any app with storage access can read them, and they stay after Tandem
  is uninstalled; they are not in Tandem's backups. A hidden game's notebook is left out of Tandem's lists while
  hidden games are hidden, but its files stay where they are.
- Game details: Tandem's private `library.json`.
- Account details (ScreenScraper login and developer credentials, IGDB client ID and secret, SteamGridDB key):
  Tandem's private `scraping.json`. They are stored as plain text inside app-private storage, which other apps cannot read. They
  are left out of the backups Tandem writes to `ROMs/tandem/backup`, because that folder is on shared storage,
  unless the user switches on **Account keys and logins** when backing up. They are excluded from Android's own
  cloud backup and device transfer.
- A cache of libretro folder listings in Tandem's cache folder (safe to clear at any time).
- Cover addresses for the Discord status: the box art's public address in `library.json` (`coverUrl`, set by a
  scrape), and the ones looked up for the Discord status in `discord-covers.json` in the cache folder.
- The Discord refresh token (only after connecting Discord, in builds with Discord's SDK): `discord.json` in
  Tandem's no-backup storage (`noBackupFilesDir`), so it is never in Tandem's backups, Android's backups or
  device transfers. Disconnect deletes it and revokes it with Discord.
- The private mode PIN as a salted PBKDF2 hash, with the count of wrong tries (`private-pin.json`), and what Tandem
  changed to go offline for a hidden game (`offline.json`, only while it is offline): both in the no-backup storage,
  never in any backup.
- The LaunchBox index (only your systems) in Tandem's private `launchbox/` folder. "Download again" on the
  LaunchBox card rebuilds it.
