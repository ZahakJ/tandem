| (`PadLink`, not saved) | Controls › Play with a controller › Controller in games (Built-in or a connected controller; also Quick settings › Controller) | choice |
| `ControllerSettings.usePads` | Controls › Play with a controller › Always use a connected pad | toggle |
# Settings: where everything lives

Settings opens on nine tiles, one per page, each summarising what is set, with a search bar on top and **Quick
looks** (whole setups applied with one press) below. Each page is a few short groups; rare rows fold under **More
options**. X searches every row, including folded ones, by label, synonyms ("nintendo" finds Swap A and B) and
help text; picking a result opens its page, unfolds the group and pulses the row. L1 and R1 flip pages.

Code: `ui/SettingsCatalog.kt` (pages and groups), `ui/Settings.kt` (row types, two-pane layout, side panel),
`ui/SettingsHome.kt` (tiles, Quick looks), `ui/SettingsSearch.kt`, `ui/Presets.kt`.

## How to add a setting

1. Write the row where the feature lives, in its `xxxSettings()` builder or in `appearanceSections()`. Give it an
   `icon`, a plain-language `help`, and `keywords` with the words people might search for.
2. Choose the row type:
   - `toggle(...)` or `toggle = on` for a switch.
   - `choice(...)` for a cycler; Left and Right step through it.
   - `number(...)` for a slider; it draws a gauge.
   - `opens = true` for a link to another screen.
   - Anything else is an action.
3. Pick it into a group in `settingsCatalog()`, or let it fall through. Any row no page picks still shows up under
   **Other** on its owner page (see `Owners`). Add it to a group on purpose, and add a line to the table below.
4. Keep labels short (about 28 characters fit on one line). Put the explanation in `help`; it shows in the side
   panel and on the bottom screen.

## Pages

| Tile | Summary shows | Groups (common rows · More options) |
|---|---|---|
| Look & feel | theme name, own accent, still | Theme gallery, Theme packs gallery, Colour, Wallpaper, Icons, Motion, Start over |
| Home & layout | layout, icons across or games view | Layout gallery, Home screen, Icon grid (first when it is your layout, last otherwise), Games, Dock, Widgets, Top bar |
| Two screens | single, swapped or the bottom style | Screens (Apps open on; More: Hold Back to move apps, Move games too), Bottom screen |
| Sound & music | sound pack, music on/off | Sounds, Music |
| Controls | prompt style, A/B swapped | Buttons, Controller Home button, Game controllers, Play with a controller, Shortcuts |
| Library | games and systems count | Games, Systems, Bring your details |
| Art & accounts | which accounts are set | Artwork, Accounts, RetroAchievements, Discord (only in builds with Discord's SDK) |
| Play & stats | resume state, sessions | Playing, Stats, Privacy & rest |
| Data & about | Home screen on/off | Home screen, Your data, About, Developer |

## Every setting and where it is

"More" means the row is under that group's More options.

| Setting (stored in) | Where | Row type |
|---|---|---|
| `Look.theme` | Look & feel › Themes / Theme packs galleries | gallery |
| `Look.packParts` | Look & feel › Theme packs › Manage packs (pack manager) | link |
| `Look.accent` | Look & feel › Colour › Accent colour (A types a hex colour) | choice |
| `Look.highlight` | Look & feel › Colour › Highlight style | choice |
| `Look.highlightColor` | Look & feel › Colour › More › Highlight colour | choice |
| `Look.highlightThickness`, `focusScale`, `corner` | Look & feel › Colour › More | slider |
| `Look.material` | Look & feel › Colour › More › Surface | choice |
| `Look.wallTop`, `wallBottom` | Look & feel › Wallpaper › Top screen / Bottom screen | choice |
| `Look.wallDim` | Look & feel › Wallpaper › More › Picture dimming | slider |
| (wallpaper folder) | Look & feel › Wallpaper › More › Wallpaper folder | action |
| `Look.iconPack` | Look & feel › Icons › Icons from | choice |
| `Look.iconStyle` | Look & feel › Icons › Icon style | choice |
| `Look.motion` | Look & feel › Motion › Animation style | choice |
| `Settings.reduceMotion` | Look & feel › Motion › Reduce motion | toggle |
| `Look.splash` | Look & feel › Motion › More › Startup splash | toggle |
| (reset) | Look & feel › Start over › Reset appearance | action |
| (reload packs) | Look & feel › Start over › More › Reload theme packs | action |
| `Look.homeLayout` | Home & layout › Layout gallery, and Home screen › Home layout | gallery / choice |
| `Look.homeSections` | Home & layout › Home screen › Home rows (More while the Icon grid is on) | choice |
| `Look.nowPlaying` | Home & layout › Home screen › Now playing | toggle |
| `Look.systemTiles` | Home & layout › Home screen › More › System tiles | choice |
| `Look.folderPopup`, `fullscreenFolders` | Home & layout › Home screen › More › Open folders | choice |
| `Look.gridAcross` | Home & layout › Icon grid › Icon size (also L1/R1 on the grid) | choice |
| `Look.gridRows` | Home & layout › Icon grid › Rows of icons (Auto, 1-5; also L1/R1 on the grid) | choice |
| `Look.gridScroll` | Home & layout › Icon grid › Scrolling | choice |
| `Look.gridLabels`, `ExtrasSettings.focusLabels` | Home & layout › Icon grid › Icon names (always, highlighted only, off) | action |
| `ExtrasSettings.recentStrip` | Home & layout › Icon grid › Recently played strip | choice |
| `ExtrasSettings.resumeLanding` | Home & layout › Icon grid › More › Home lands on Resume | toggle |
| `ExtrasSettings.floating` | Look & feel › Motion › Floating titles | toggle |
| `ExtrasSettings.intensity` | Look & feel › Motion › More › Motion intensity | choice |
| `Look.gridRoundness`, `gridGap`, `gridHeroSplit` | Home & layout › Icon grid › More | slider |
| `Look.gridDetails` | Home & layout › Icon grid › More › Other screen | choice |
| `Look.gridFolders` | Home & layout › Icon grid › More › Folders open | choice |
| `Look.gameView` | Home & layout › Games › Games view | choice |
| `Look.gridSize` | Home & layout › Games › Grid size | choice |
| `Look.cardShape` | Home & layout › Games › Card shape | choice |
| `Settings.showTitles` | Home & layout › Games › Game titles | toggle |
| `HomeSettings.showDock` | Home & layout › Dock › Apps dock | toggle |
| `HomeSettings.dock` | Home & layout › Dock › Choose dock apps | link |
| `Look.dockStyle` | Home & layout › Dock › Dock style | choice |
| `Look.dockTop`, `dockAutoHide`, `dockBackground` | Home & layout › Dock › More | toggle |
| `Look.dockCount` | Home & layout › Dock › More › Icons on dock | slider |
| `Look.dockIconShape` | Home & layout › Dock › More › Dock icon shape | choice |
| `WidgetStore.widgets` | Home & layout › Widgets › Customize widgets | link |
| `WidgetConfig.homeEnabled` | Home & layout › Widgets › Widgets on the home screen | toggle |
| `WidgetConfig.gridPlace` | Home & layout › Widgets › Widgets on the grid (In cells, As a row) | choice |
| `IconGridConfig.spans` | Y on a widget on the icon grid › Resize | menu |
| `WidgetConfig.upNext` | Home & layout › Widgets › More › Add to Up next | action |
| `Look.clock`, `battery` | Home & layout › Top bar | choice / toggle |
| `SystemPrefs.singleScreen` | Two screens › Screens › Single screen | toggle |
| `Look.swapScreens` | Two screens › Screens › Swap screens | toggle |
| `AppsData.defaultScreen` | Two screens › Screens › Apps open on (Auto, Top, Bottom) | choice |
| `AppsData.holdBackMoves`, `moveGames` | Two screens › Screens › More › Hold Back to move apps / Move games too | toggle |
| Per-app screen, name, icon, game, hidden (`AppPrefs`) | Y on any app (dock, All apps, Android apps, grid) | menu |
| `Settings.bottomMode` | Two screens › Bottom screen › Style | choice |
| `Look.videoSnaps`, `videoDelayMs` | Two screens › Bottom screen › Video snaps | choice |
| `Look.kenBurns` | Two screens › Bottom screen › Moving art | toggle |
| (bottom widgets) | Two screens › Bottom screen › Bottom screen when idle | link |
| `Look.heroSplit`, `heroLogoScale` | Two screens › Bottom screen › More › Art height / Logo size | slider |
| `Look.heroGradient` | Two screens › Bottom screen › More › Art fade | choice |
| `Look.sounds`, `soundPack`, `soundVolume` | Sound & music › Sounds | toggle / choice / slider |
| `Look.jingles`, `pipVoice` | Sound & music › Sounds › More | choice / toggle |
| `Look.music`, `musicTrack`, `musicVolume` | Sound & music › Music | toggle / choice / slider |
| (next song) | Sound & music › Music › Next song | action |
| `Look.myMusic`, `musicShuffle` | Sound & music › Music › More | toggle |
| `Look.prompts`, `swapAB`, `swapXY` | Controls › Buttons | choice / toggle |
| `Look.xButton` | Controls › Buttons › X button | choice |
| (accessibility service `ControllerHomeService`) | Controls › Controller Home button › Controller Home button (opens Android's Accessibility settings) | link |
| `ControllerSettings.holdHome` | Controls › Controller Home button › More › Hold the Home button | choice |
| `ControllerSettings.nintendo` | Controls › Game controllers › one row per connected controller (Nintendo button labels) | toggle |
| (switcher, quick panel) | Controls › Shortcuts › Game switcher / Quick settings panel (A opens them now) | action |
| `Look.cornerHints` | Controls › Shortcuts › More › Corner hints | toggle |
| `Settings.sort` | Library › Games › Sort games by | choice |
| `Settings.showHidden` | Library › Games › Show hidden games | toggle |
| (rescan) | Library › Games › Rescan games | action |
| Systems, emulators, folders (`SystemPrefs`) | Library › Systems › Systems and emulators | link |
| Android apps and games (`AppPrefs`) | Library › Systems › Android apps | link |
| Cocoon platform files | Library › Systems › More › Cocoon platform files | action |
| (import gamelists) | Library › Bring your details › Import details | action |
| Scraping (`ScrapeConfig`) | Art & accounts › Artwork › Scrape library; Set up sources; Artwork to download; More: region, language, clear media | link / choice / action |
| Scraping sources and their keys (LaunchBox, ScreenScraper, IGDB, SteamGridDB, libretro, HowLongToBeat) | Art & accounts › Sources, in order (each opens its setup card) | link |
| RetroAchievements (`HomeStore.ra`) | Art & accounts › RetroAchievements; More: Look up games again, Sign out | action / toggle |
| Discord status (`DiscordPresence`, token in `noBackupFilesDir/discord.json`) | Art & accounts › Discord › Discord status (Connect); More: Disconnect Discord; Quick settings › Discord status tile pauses it (`shown`). Only in builds with Discord's SDK | action |
| Resume (Shizuku) | Play & stats › Playing › Resume running games | action |
| `HomeSettings.closePrevious` | Play & stats › Playing › Close previous game | toggle |
| `HomeSettings.askBeforeClosing` | Play & stats › Playing › Ask before closing a game (also turned off by Y, "Don't ask again", in the question itself) | toggle |
| `SystemPrefs.launchAnimation` | Play & stats › Playing › More › Launch animation | toggle |
| Play stats, export | Play & stats › Stats (Export under More) | link / action |
| `HomeSettings.freezeAsleep` | Play & stats › Privacy & rest › Pause games in sleep | toggle |
| `HomeSettings.privateMode` | Play & stats › Privacy & rest › Private mode | toggle |
| `HomeSettings.offlineHidden` | Play & stats › Privacy & rest › Go offline while a hidden game plays | toggle |
| `private-pin.json` (`PinVault`) | Play & stats › Privacy & rest › More › Private mode PIN | action |
| `HomeSettings.screensaverMinutes` | Play & stats › Privacy & rest › Screensaver | choice |
| `Settings.hiddenFolders`, `HomeFolder.hidden`, hidden games | Play & stats › Hidden (listed with Unhide only while hidden games show); also Start › Hidden | action |
| Home app role | Data & about › Home screen › Use Tandem as the Home screen | link |
| Backups (`data/Backup.kt`, `ui/BackupScreen.kt`) | Data & about › Your data › Back up (parts to include, account keys) and Restore (backups newest first, X deletes; then parts to put back) | link |
| About, language | Data & about › About | action |
| Frame timing overlay (`dev` preferences, `DevTools`) | Data & about › Developer | switch |
| `Settings.welcomeDone` | Shown once after the first scan; not a row | n/a |

### Fields with no row

`Look.homeColumns` and `Look.noSplitFromRow` are unused. `Look.singleScreen` is migrated into `SystemPrefs.singleScreen` on start. `SystemPrefs.gameApps` and `notGameApps` are migrated into `AppPref.game` the first time `app-prefs.json` is written. `AppPref.platform` is stored but not used yet.

## Quick looks

| Quick look | What it changes |
|---|---|
| Cozy shelves | Shelves home, grid of games, details below, floating dock, library on top |
| Icon grid look | Icon grid 7 across scrolling sideways, Spotlight, X swaps on the grid, folders as popups, pill dock; puts on an installed Cocoon theme pack (the most recently installed) when a built-in theme is on. With a Cocoon backup it runs Import my Cocoon setup instead (preset id `cocoon`) |
| Minimal | List home and games, outline highlight, minimal motion, music, jingles and splash off, icons-only auto-hiding dock, no widgets row |
| Battery saver | Reduce motion, no video snaps, no panning art, music off, minimal animation; layout and theme unchanged |

A applies a Quick look. A again undoes it, restoring settings, Home settings and widget settings to how they were
before the first Quick look in a row.
