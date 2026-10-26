# Privacy

Tandem has no analytics, no telemetry, no ads and no account of its own. It does not phone home.

## When Tandem uses the network

Only when you ask it to look something up online:

- **Scraping** (Settings › Art & accounts › Scrape library, "Scrape these games" in a system's Y menu, or
  "Scrape this game" in a game's Y menu).
- **Choose artwork** in a game's Y menu, which asks every configured source for pictures of that one game.
- **Test** on a source's setup card, which looks up one well-known game ("Super Mario Odyssey"), never yours.
- **Downloading the LaunchBox database**, only after you agree to it (the LaunchBox card, the welcome, or the
  question when you start a scrape that needs it). A scrape you start refreshes a month-old copy, only on Wi-Fi.

RetroAchievements is contacted only after you sign in, to show your profile and progress, and when you open
**Find match** in a game's Edit details (never for a hidden game).

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

Tandem never starts a scrape on its own or on a schedule (a scrape you start does keep running in the background until it finishes). The `INTERNET` permission exists only
for these features; `ACCESS_NETWORK_STATE` only lets Tandem warn you before a big download on mobile data.

## What is sent, and to whom

Each source can be turned off in Settings › Art & accounts; a source that is off is never contacted.

| Service | When | What it receives |
| --- | --- | --- |
| LaunchBox (gamesdb.launchbox-app.com, images.launchbox-app.com) | On by default, no account | Nothing about your games: the whole database is downloaded and games are looked up on the device. Then the chosen pictures are downloaded by their LaunchBox file names. |
| thumbnails.libretro.com | On by default, no account | The system's folder listing is downloaded once a week; then picture names derived from your game's file name or title. |
| ScreenScraper (api.screenscraper.fr) | Only if you entered developer credentials | Your ScreenScraper login (if set), the developer ID, and for each game: the file name, file size, system, and CRC32/MD5 of the file (only for files up to 64 MB). If the file isn't recognised, the game's title. |
| IGDB (id.twitch.tv, api.igdb.com, images.igdb.com) | Only if you entered a Twitch client ID and secret | Your client ID and secret (to Twitch, for an access token kept in memory), then the game's title and system. |
| SteamGridDB (steamgriddb.com) | Only if you entered an API key | Your API key and the game's title (also for the Discord status's cover lookup, when connected). |
| HowLongToBeat (howlongtobeat.com) | Off until you turn it on, no account | The game's title. |
| Steam's video servers | Only for video snaps from LaunchBox | The trailer's address. |
| RetroAchievements (retroachievements.org, media.retroachievements.org) | Only after you sign in (Settings › Art & accounts › RetroAchievements) | Your username and web API key; the console's public game list with hashes (cached for a week); the ID of a matched game, for your progress in it; badge pictures. Games are matched by a hash computed on the device, the file is never sent. **Find match** in Edit details uses the same cached game list and sends nothing about the game; an ID you type or "No achievements" stays on the device. |
| Discord (discord.com, through Discord's Social SDK) | Only after you connect your account (Settings › Art & accounts › Discord), in builds that include the SDK | Account linking through Discord's OAuth2 (PKCE, public client; Tandem never sees a password). While a game plays: its title, its system name, when the session started and the public address of its cover picture (thumbnails.libretro.com, cdn2.steamgriddb.com and similar, images.launchbox-app.com or images.igdb.com, which Discord's servers then fetch), or of the Tandem icon on the project site when no cover address is known. An address with a login or key in it is never sent: ScreenScraper's never, other addresses only without their query. Never for hidden games or while private mode is on. The connection opens when a game starts and closes a minute after it stops. |

**Hidden games are never sent anywhere**, and neither is any game in a hidden folder or hidden system (or inside a
hidden icon-grid folder). They are left out of every scrape, Tandem checks again right before
each lookup in case you hid a game mid-scrape, and "Choose artwork" refuses to run for them.

Pictures you pick from the device stay on the device.

## Where things are stored

- Downloaded pictures and clips: `ROMs/downloaded_media/<system>/<icon|hero|logo|screenshot_title|screenshot_gameplay|videos>/`.
  Tandem keeps a list of files it downloaded (in its private storage) so "Clear downloaded media" deletes only
  those, never art from other apps or pictures you chose by hand.
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
