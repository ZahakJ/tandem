# Privacy

Tandem keeps your library and play history on your device. There are no analytics, crash reports, ads, accounts or update checks, and nothing phones home.

**When it goes online.** Tandem declares Android's internet permission for two optional features, and uses it only for them:

- **Scraping** artwork and details, only when you start it (for the whole library, a system, or one game), from the sources you leave switched on: LaunchBox, libretro thumbnails, ScreenScraper, IGDB, SteamGridDB and HowLongToBeat. The last four are off until you add your own keys or switch them on.
- **RetroAchievements,** only after you sign in with your own username and API key.

**Hidden games are never sent anywhere,** nor are games in hidden folders or systems. Keys and logins stay in Tandem's private storage and are left out of every backup.

**What it stores, all locally:**

- **About your games:** titles, descriptions, genres and other details, favorites, hidden games, your chosen emulator per game, artwork paths, and play history (play count, play time, sessions, last played). In Tandem's private storage.
- **Your settings,** journal notes, and the keys for the services you set up, next to it.
- **Downloaded artwork** in `ROMs/downloaded_media/`, where it stays yours (and other frontends can use it).

**Settings › Data & about › Back up Tandem** copies your library and settings as JSON to `ROMs/tandem/backup/` so you can read, keep or move them. Uninstalling Tandem deletes its private storage but not that backup.

The full version, with every service and exactly what it receives, is the [Privacy and scraping](docs/guide/privacy.md) page of the documentation, and the technical detail is in [docs/PRIVACY.md](docs/PRIVACY.md).
