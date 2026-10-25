# Roadmap

A complete handheld frontend with nothing hidden: no telemetry, open formats, and data you can take with you.

## Milestone 1: a solid everyday library
- [x] Layout sized for handheld screens, on both the top and bottom screens
- [x] Library database: titles, descriptions, genres, developer, year and players, kept on device and exportable
- [x] Import existing metadata and artwork from Cocoon backups and ES-DE `gamelist.xml`, so switching costs nothing
- [x] Game menu (Y): favorite, hide, choose emulator, edit details
- [x] Search with a controller-friendly keyboard (games, apps and settings)
- [x] Sorting: name, recently played, most played, year, rating, recently added
- [x] Settings screen (controller-navigable, with search)

## Milestone 2: smarter collections and play tracking
- [x] Play time and play count per game, with a stats screen (sessions, streaks, CSV export) and a play journal
- [x] Automatic collections: Continue playing, Favorites, Recently added, Most played, Never played
- [ ] Virtual genre, developer and year folders from metadata, with no need to move files (genres done; developer and year to do)
- [x] Game switcher: see running games, jump between them, close them
- [x] Android apps drawer, plus a dock
- [x] Optional Home-screen mode, and a controller Home button that works in every app

## Milestone 3: artwork and more systems
- [x] Scraping: LaunchBox and libretro thumbnails (no account), ScreenScraper, IGDB and SteamGridDB (your own keys), HowLongToBeat; runs in the background with progress on the bottom screen
- [x] Per-game art picker and manual art
- [x] Detect installed emulators automatically and offer every compatible one per system
- [x] PS Vita (Vita3K) and Windows (GameNative, Winlator) support
- [x] Per-game emulator and core override

## Milestone 4: make it yours
- [x] Themes: 24 built-in themes, five home layouts (icon grid with 1 to 5 rows, shelves, carousel, list, console), card styles, theme packs (see docs/THEMES.md)
- [x] Bottom-screen modes: details, art, animated art, clock, video snaps, widgets, and an in-game art or companion screen
- [x] Single-screen mode and swappable screens (X, animated)
- [x] Background music and UI sounds (two soundtracks, five sound packs)
- [x] Screensaver, also as Android's screensaver
- [x] Private mode for hidden games, folders and systems
- [ ] Backup and restore of everything to `ROMs/tandem/` (today: library and settings; home grid, widgets and journal to do)
- [x] RetroAchievements profile and progress

## Milestone 5: release
- [x] GitHub Actions builds and signed releases
- [x] F-Droid metadata
- [ ] F-Droid submission
- [ ] Translations
- [x] User guide and website
