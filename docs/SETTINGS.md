# Settings: where everything lives

Settings opens on seven tiles, one per page, each summarising what is set, with a search bar on top and **Quick
looks** (whole setups applied with one press) below. Each page is a few short groups; rare rows fold under **More
options**. X searches every row, including folded ones, by label, synonyms ("nintendo" finds Swap A and B) and
help text; picking a result opens its page, unfolds the group and pulses the row. L1 and R1 flip pages.

Code: `ui/SettingsCatalog.kt` (pages and groups: `SettingsPlan`), `ui/Settings.kt` (row types, two-pane layout, side
panel), `ui/SettingsHome.kt` (tiles, Quick looks), `ui/SettingsSearch.kt`, `ui/Presets.kt`.

## How to add a setting

1. Write the row where the feature lives, in its `xxxSettings()` builder or in `appearanceSections()`. Give it an
   `icon`, a plain-language `help`, and `keywords` with the words people might search for.
2. Choose the row type:
   - `toggle(...)` or `toggle = on` for a switch.
   - `choice(...)` for a cycler; Left and Right step through it.
   - `number(...)` for a slider; it draws a gauge.
   - `opens = true` for a link to another screen.
   - Anything else is an action.
3. Place it in `SettingsPlan` (a group's main rows, or its More options), or drop it there on purpose.
   `SettingsPlanTest` fails while a row is neither, and a debug build shows a stray row under **Not placed**. Add a
   line to the table below.
4. Keep labels short (about 28 characters fit on one line). Put the explanation in `help`; it shows in the side
   panel and on the bottom screen.

Before adding one, ask whether a good default would do, and whether the setting already exists somewhere else.

## Pages

| Tile | Summary shows | Groups (common rows · More options) |
|---|---|---|
| Look & feel | theme name, own accent, still | Theme gallery, Theme packs gallery, Colour, Wallpaper, Icons, Motion, Start over |
| Home & layout | layout, icons across or games view | Layout gallery, Home screen (the rows for the layout in use), Games, While a game runs, Dock, Widgets, Top bar, Rest |
| Two screens | single, swapped or the bottom style | Screens, Other screen, Moving apps |
| Sound & music | the theme pack's audio, or sound pack and music | Theme pack (only when the theme pack has music or sounds), Sounds, Music |
| Controls | prompt style, A/B swapped | Buttons, Controller Home button, Game controllers, Play with a controller |
| Library & art | games, systems, sources, RetroAchievements | Games, Systems, Artwork, Sources in order, RetroAchievements, Discord (only in builds with Discord's SDK), Bring your details |
| Play & system | resume state, Home screen | Playing, Privacy, Hidden, Stats, Your data, About |

## Every setting and where it is

"More" means the row is under that group's More options.

| Setting (stored in) | Where | Row type |
|---|---|---|
| `Look.theme` | Look & feel › Themes / Theme packs galleries | gallery |
| `Look.packParts` | Look & feel › Theme packs › Manage packs (pack manager); music and sounds also Sound & music › Theme pack | link / toggle |
| `Look.accent` | Look & feel › Colour › Accent colour (A types a hex colour; also sets what the highlight glows in) | choice |
| `Look.highlight` | Look & feel › Colour › Highlight style | choice |
| `Look.material` | Look & feel › Colour › More › Surface | choice |
| `Look.wallTop`, `wallBottom` | Look & feel › Wallpaper › Top screen / Bottom screen (your files from `ROMs/tandem/wallpapers`) | choice |
| `Look.wallDim` | Look & feel › Wallpaper › More › Picture dimming | slider |
| `Look.iconPack` | Look & feel › Icons › Icons from | choice |
| `Look.iconStyle` | Look & feel › Icons › Icon style | choice |
| `Look.motion` | Look & feel › Motion › Animation style | choice |
| `Settings.reduceMotion` | Look & feel › Motion › Reduce motion | toggle |
| `ExtrasSettings.floating`, `intensity` | Look & feel › Motion › More › Floating titles / Motion intensity | toggle / choice |
| (reset) | Look & feel › Start over › Reset look (keeps buttons, screens, sounds and music) | action |
| `Look.homeLayout` | Home & layout › Layout gallery | gallery |
| `Look.gridAcross` | Home & layout › Home screen › Icon size (icon grid; also L1/R1 on the grid) | choice |
| `Look.gridScroll` | Home & layout › Home screen › Scrolling (icon grid) | choice |
| `Look.gridLabels`, `ExtrasSettings.focusLabels` | Home & layout › Home screen › Icon names (icon grid) | action |
| `ExtrasSettings.recentStrip` | Home & layout › Home screen › Recently played strip (icon grid) | choice |
| `Look.gridFolders` | Home & layout › Home screen › Folders open (icon grid) | choice |
| `Look.gridRows`, `gridRoundness`, `gridGap`, `gridHeroSplit` | Home & layout › Home screen › More (icon grid) | choice / slider |
| `Look.homeSections` | Home & layout › Home screen › Home rows (other layouts) | choice |
| `Look.folderPopup`, `fullscreenFolders` | Home & layout › Home screen › Open folders (other layouts) | choice |
| `Look.systemTiles` | Home & layout › Home screen › More › System tiles (other layouts) | choice |
| `Look.gameView`, `gridSize`, `cardShape`, `Settings.showTitles` | Home & layout › Games | choice / toggle |
| `Look.nowPlaying`, `ExtrasSettings.resumeLanding` | Home & layout › While a game runs › Now playing / Home lands on Resume | toggle |
| `HomeSettings.showDock`, `dock`, `Look.dockStyle` | Home & layout › Dock › Apps dock / Choose dock apps / Dock style | toggle / link / choice |
| `Look.dockTop`, `dockAutoHide` | Home & layout › Dock › More | toggle |
| `WidgetStore.widgets`, `WidgetConfig.homeEnabled` | Home & layout › Widgets › Customize widgets / Widgets on the home screen | link / toggle |
| `WidgetConfig.gridPlace` | Home & layout › Widgets › More › Widgets on the grid (In cells, As a row) | choice |
| `IconGridConfig.spans` | Y on a widget on the icon grid › Resize | menu |
| `WidgetConfig.upNext` | the Up next widget, and Y › Add to Up next on a game | menu |
| `Look.clock`, `battery` | Home & layout › Top bar | choice / toggle |
| `HomeSettings.screensaverMinutes` | Home & layout › Rest › Screensaver (also Start › Screensaver) | choice |
| `SystemPrefs.singleScreen` | Two screens › Screens › Single screen | toggle |
| `Look.swapScreens` | Two screens › Screens › Swap screens | toggle |
| `AppsData.defaultScreen` | Two screens › Screens › Apps open on (Auto, Top, Bottom) | choice |
| `Look.swapStyle`, `SystemPrefs.keepRotationOff` | Two screens › Screens › More › Swap animation / Keep auto-rotate off | choice / toggle |
| `Look.gridDetails` (icon grid) or `Settings.bottomMode` (other layouts) | Two screens › Other screen › Other screen / Style | choice |
| `Look.videoSnaps`, `videoDelayMs`, `kenBurns` | Two screens › Other screen › Video snaps / Moving art | choice / toggle |
| (bottom widgets) | Two screens › Other screen › Bottom screen when idle | link |
| `Look.heroSplit`, `heroLogoScale`, `heroGradient` | Two screens › Other screen › More › Art height / Logo size / Art fade | slider / choice |
| `AppsData.holdBackMoves`, `moveGames` | Two screens › Moving apps (Hold Back says what it still needs: Shizuku, the Home button service) | toggle |
| Per-app screen, name, icon, game, hidden (`AppPrefs`) | Y on any app (dock, All apps, Android apps, grid) | menu |
| `PackParts.music`, `PackParts.sounds` | Sound & music › Theme pack › Theme pack music / Theme pack sounds | toggle |
| `Look.sounds`, `soundVolume` | Sound & music › Sounds › UI sounds / Sound volume | toggle / slider |
| `Look.soundPack`, `soundFiles` | Sound & music › Sounds › Sound pack (Tandem's 8 packs, then the recorded packs in `ROMs/assets/sounds`) | choice |
| `Look.gameJingles`, `pipVoice` | Sound & music › Sounds › Game jingles on hover / Pip's voice | toggle |
| `Look.music`, `musicVolume` | Sound & music › Music › Music / Music volume | toggle / slider |
| `Look.myMusic` | Sound & music › Music › Music from (My music folder, Built-in: Meadow Tales) | choice |
| (next song) | Sound & music › Music › Next song | action |
| `Look.musicShuffle` | Sound & music › Music › More › Shuffle my music | toggle |
| `Look.prompts`, `swapAB`, `xButton` | Controls › Buttons | choice / toggle |
| `Look.swapXY`, `cornerHints` | Controls › Buttons › More | toggle |
| (accessibility service `ControllerHomeService`) | Controls › Controller Home button (opens Android's Accessibility settings) | link |
| `ControllerSettings.holdHome` | Controls › Controller Home button › More › Hold the Home button | choice |
| `ControllerSettings.nintendo` | Controls › Game controllers › one row per connected controller (Nintendo button labels) | toggle |
| (`PadLink`, not saved) | Controls › Play with a controller › Controller in games (also Quick settings › Controller) | choice |
| `ControllerSettings.usePads` | Controls › Play with a controller › Always use a connected pad | toggle |
| `Settings.sort`, `showHidden` | Library & art › Games › Sort games by / Show hidden games | choice / toggle |
| (rescan) | Library & art › Games › Rescan games | action |
| Systems, emulators, folders (`SystemPrefs`); Cocoon and Daijisho platform files | Library & art › Systems › Systems and emulators | link |
| Android apps and games (`AppPrefs`) | Library & art › Systems › Android apps | link |
| Scraping (`ScrapeConfig`) | Library & art › Artwork › Scrape library; Set up sources; Artwork to download; More: region, language, clear media | link / choice / action |
| Scraping sources and their keys (LaunchBox, ScreenScraper, IGDB, SteamGridDB, libretro, HowLongToBeat) | Library & art › Sources, in order (each opens its setup card) | link |
| RetroAchievements (`HomeStore.ra`) | Library & art › RetroAchievements; More: Look up games again, Sign out | action / toggle |
| Discord status (`DiscordPresence`, token in `noBackupFilesDir/discord.json`) | Library & art › Discord › Discord status (Connect); More: Disconnect Discord; Quick settings › Discord status tile pauses it (`shown`). Only in builds with Discord's SDK | action |
| (import my Cocoon setup, import gamelists) | Library & art › Bring your details; More: Undo Cocoon import | action |
| Resume (Shizuku) | Play & system › Playing › Resume running games | action |
| `HomeSettings.closePrevious`, `askBeforeClosing` | Play & system › Playing › Close previous game / Ask before closing a game | toggle |
| `HomeSettings.freezeAsleep` | Play & system › Playing › More › Pause games in sleep (shows "Needs root Shizuku" without it) | toggle |
| `SystemPrefs.launchAnimation` | Play & system › Playing › More › Launch animation | toggle |
| `HomeSettings.privateMode`, `offlineHidden`, `offlineUntilClosed` | Play & system › Privacy › Private mode / Go offline while a hidden game plays / Stay offline until the game closes | toggle |
| `private-pin.json` (`PinVault`) | Play & system › Privacy › More › Private mode PIN | action |
| `Settings.hiddenFolders`, `HomeFolder.hidden`, hidden games | Play & system › Hidden (listed with Unhide only while hidden games show); also Start › Hidden | action |
| Play stats, export | Play & system › Stats (Export under More) | link / action |
| Home app role | Play & system › Your data › Use Tandem as the Home screen | link |
| Backups (`data/Backup.kt`, `ui/BackupScreen.kt`) | Play & system › Your data › Back up (parts to include, account keys) and Restore (backups newest first, X deletes; then parts to put back) | link |
| (about) | Play & system › About › About Tandem (version and licence) | action |
| Frame timing overlay, theme tour (`dev` preferences, `DevTools`) | Play & system › About › More | switch / action |
| `Settings.welcomeDone` | Shown once after the first scan; not a row | n/a |
| Game switcher, quick settings panel | Hold Select / hold Start anywhere (a note under Controls › Buttons) | n/a |

### Fields with no row

The theme decides highlight colour (`Look.highlightColor`, set with the accent), ring thickness (`highlightThickness`),
focus zoom (`focusScale`) and card corners (`corner`); theme packs can still set them. `Look.dockBackground`,
`dockCount` and `dockIconShape` keep their defaults unless an older setup changed them. `Look.splash` (the startup
splash) stays on. `Look.singleScreen` is migrated into `SystemPrefs.singleScreen` on start. `SystemPrefs.gameApps`
and `notGameApps` are migrated into `AppPref.game` the first time `app-prefs.json` is written. `AppPref.platform` is
stored but not used yet. A saved theme that is no longer built in, and the old "From the theme" value of
`Look.soundFiles`, are migrated on start (`Themes.migrate`).

## Quick looks

| Quick look | What it changes |
|---|---|
| Cozy shelves | Shelves home, grid of games, details below, floating dock, library on top |
| Icon grid look | Icon grid 7 across scrolling sideways, Spotlight, X swaps on the grid, folders as popups, pill dock; puts on an installed Cocoon theme pack (the most recently installed) when a built-in theme is on. With a Cocoon backup it runs Import my Cocoon setup instead (preset id `cocoon`) |
| Minimal | List home and games, outline highlight, minimal motion, music, game jingles and splash off, icons-only auto-hiding dock, no widgets row |
| Battery saver | Reduce motion, no video snaps, no panning art, music off, minimal animation; layout and theme unchanged |

A applies a Quick look. A again undoes it, restoring settings, Home settings and widget settings to how they were
before the first Quick look in a row.
