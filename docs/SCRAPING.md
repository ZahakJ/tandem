# Scraping artwork and game details

Start a scrape from **Settings › Art & accounts › Scrape library**, from Y on a system (**Scrape these games**), or
from Y › More › **Scrape this game**. **Settings › Art & accounts › Set up sources** (also a step in the first-run
welcome) explains every source, takes its keys, and has a **Test** button that does one real lookup ("Super Mario
Odyssey") and shows ✓ or ✗ with the reason.

## Sources

LaunchBox and libretro work with no account. The others need a free key of your own; Tandem can't ship keys, since
they belong to whoever registers them.

| Source | Account | Gives you |
| --- | --- | --- |
| LaunchBox Games Database | None | Box art, clear logos, fanart, title screens, screenshots, some trailers, and details (name, description, genres, developer, publisher, year, players, rating). Strong for Switch, Wii U, Vita and PC. |
| ScreenScraper | Developer credentials required, user login recommended | Matches the file by checksum: box art, fanart, logos, screenshots, video snaps and details |
| IGDB (Twitch) | Free Twitch application (client ID + secret) | Covers, artwork, screenshots and details |
| SteamGridDB | Free API key | Community box grids, heroes and logos, matched by title |
| libretro thumbnails | None | Box art, title screens and screenshots for classic systems |
| HowLongToBeat | None (off until you turn it on) | Time to beat (main, main + extras, 100%), shown on the bottom screen |

### Order

Sources are asked in order, and each is asked only for what is still missing: if LaunchBox found the box art and
description but no logo, SteamGridDB is asked for the logo only. Change the order with ◀▶ on a source. The default
is ScreenScraper, LaunchBox, IGDB, SteamGridDB, libretro, HowLongToBeat; a source without its keys is skipped. For
heroes and logos of PC, Switch and other modern systems, SteamGridDB goes first when it is on.

### LaunchBox

LaunchBox publishes its database as one file (`https://gamesdb.launchbox-app.com/Metadata.zip`, about 105 MB).
Tandem downloads it only when you agree (warning on metered connections), parses it while it downloads without
storing the zip, and keeps a small index of **only your systems** in its private storage (`launchbox/`). Games are
then looked up on the device; pictures come from `https://images.launchbox-app.com/<file>`. A scrape refreshes a
month-old index, on Wi-Fi only.

**Details without a scrape.** With the index on the device, Tandem fills in details for games that have no
description after every library scan, from the local index only. It fills only empty fields among those picked
under **Details**, never a field you edited or locked, and never the name. Each game is tried once per index
download (tracked in `details-tried.tsv`).

**Matching titles.** Games match by title on the same platform. Punctuation, accents, articles, bracketed tags and
dump leftovers (`v13.0.2`, `+ Update`, `+ 2 DLC`, `.nsp`) are ignored, and Roman numerals from II up count as
numbers ("Hades 2" finds "Hades II"). Failing an exact match, edition words ("Deluxe", "Definitive Edition", "HD"…)
are set aside, and last comes the closest title by shared words, if close enough.

### ScreenScraper

1. **Developer ID and password.** ScreenScraper issues these to software developers, and publishing them in
   open-source code would break its rules, so you need your own: register at screenscraper.fr, then ask for API
   access in its forum ("Demande d'accès à l'API").
2. **Your user login** (optional, recommended): your account's daily quota and up to 4 parallel requests instead
   of the shared anonymous quota.

Tandem checks the login when a scrape starts, stops using ScreenScraper for the run if the quota runs out or the
service is overloaded, and paces its requests.

### IGDB

1. Sign in at https://dev.twitch.tv/console/apps (Twitch requires two-factor sign-in).
2. **Register Your Application**: any name, OAuth redirect URL `http://localhost`, category "Application
   Integration", client type "Confidential".
3. Copy the **Client ID**, press **New Secret**, copy the **Client Secret**, and enter both on the IGDB card.

The access token is kept in memory only. Requests stay under IGDB's limit of 4 a second.

### SteamGridDB

Sign in at steamgriddb.com, open https://www.steamgriddb.com/profile/preferences/api, generate a key and enter it
on the SteamGridDB card.

### HowLongToBeat

There is no official API; Tandem uses the search the website itself uses. If the site changes, Tandem looks for
the new address in the site's scripts once, and otherwise skips HowLongToBeat for that scrape with a note.

## Options

- **Mode.**
  - **Missing basics**: only games without box art or a description; fetches box art and the main details.
  - **Missing selected**: fills in the artwork and details you picked that a game doesn't have yet (art from
    ES-DE, Cocoon or earlier scrapes counts). Art and details are checked separately.
  - **Force all**: fetches everything you picked again and replaces it.
- **Replace locked fields** (off by default). Anything you edit by hand (Y › Edit details, Y › Choose artwork) is
  locked. With this on, a scrape may replace locked fields too, and unlocks what it replaces. X on a field in Edit
  details locks or unlocks it.
- **Artwork** and **Details**: which pictures and details to fetch.
- **Region / Language**: which box art and description to prefer.
- **Games**: all systems, one system or one folder.
- **Video snaps** (off by default, about 3 MB a game): from ScreenScraper, plus Steam trailers for some PC games in
  LaunchBox.
- **Scrape this game** (Y menu) uses your settings with Force all, never replacing locked fields.

## Files

Media is saved as `ROMs/downloaded_media/<system>/<kind>/<rom file name without extension>.<ext>`, the ES-DE
layout, with kinds `icon` (box), `hero`, `logo`, `screenshot_title`, `screenshot_gameplay` and `videos`. Pictures
are scaled down (box 640 px, hero 1280 px, logo 800 px, screenshots 960 px) and saved as JPEG, or PNG when they
have transparency. Scraping pauses while a game is in front.

## Code

`data/ScrapeConfig.kt` (settings, sources, modes), `data/Scraper.kt` (the run and per-game worker),
`data/ScrapeHits.kt` (`RunSources`), `data/ScrapeSources.kt` (network, saving media, libretro, ScreenScraper,
SteamGridDB), `data/ScrapeApis.kt` (IGDB, HowLongToBeat), `data/LaunchBox.kt` (streaming parser and index),
`data/LaunchBoxDb.kt` (download, refresh), `data/DetailsFill.kt`, `data/ScrapePlatforms.kt`,
`data/SourceTests.kt`, `ui/ArtSetup.kt`. Unit tests in `app/src/test` cover parsing, title matching, the Missing
modes and the platform table: `./gradlew testDebugUnitTest`.

See also [PRIVACY.md](PRIVACY.md).
