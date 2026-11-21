# Settings: where everything lives

Settings opens on seven tiles, one per page (four, then three), each summarising what is set, under a search bar.
Each page is a few short groups, the rows people change most first, and nothing folds away: a page fits in about a
screen and a half. A coherent set of rarer rows (the wallpapers, the dock and widgets, accounts, performance) is a
short **subpage**, opened from one › row on its page; B goes back to the page. X searches every row of every page and
subpage by label, synonyms ("nintendo" finds Swap A and B) and help text, plus the few settings that live on a screen
of their own (see [Found elsewhere](#found-elsewhere)); picking a result opens its page (a subpage over its page, so
Back goes there) and pulses the row. L1 and R1 flip pages (from a subpage, its page's neighbours). The quick switches
people flip while playing also sit in Quick settings (see [Found elsewhere](#found-elsewhere)).

Code: `ui/SettingsCatalog.kt` (pages, subpages and groups: `SettingsPlan`), `ui/Settings.kt` (row types, two-pane
layout, side panel), `ui/Choices.kt` (segmented choices, sliders, the option picker), `ui/Showcase.kt` (the side
panel's showcases and their miniature scenes), `ui/SettingsHome.kt` (tiles), `ui/SettingsSearch.kt`. `ui/Presets.kt` holds the four whole-setup
looks the first-run welcome offers (Cozy shelves, Icon grid look, Orbit, Minimal); Settings no longer shows them. The
user guide has every row in plain words, with defaults: [Every setting](guide/settings.md).

## How to add a setting

1. Write the row where the feature lives, in its `xxxSettings()` builder or in `appearanceSections()`. Give it an
   `icon`, a plain-language `help`, and `keywords` with the words people might search for.
2. Choose the row type:
   - `toggle(...)` or `toggle = on` for a switch.
   - `choice(...)` for a choice; never build one by hand with `onLeft`/`onRight`, so the row can show its options
     (`ui/Choices.kt`). Two to four short options (about 30 letters in all) sit on the row as a segmented control:
     Left and Right move along them, A steps on. More, or longer, show the value with a grid mark, and A opens
     `ChoicePicker`, a popup of option cards starting on the one in use (B returns to the row); Left and Right still
     step. Toggles that only mean something together (one needs the other on) are one choice instead: UI sounds (Off
     or a pack), Music (Off or a source), Hold Back to move (Off, Apps, Apps + games), Offline for hidden games, the
     icon grid's Resume page.
   - `number(...)` for a slider (track, fill, knob, value; Left and Right adjust). With a `themeValue`, a "Theme"
     stop below the minimum lets the theme decide again.
   - `opens = true` for a link to another screen.
   - Anything else is an action.
3. If the setting changes how anything looks, give it a showcase (`ui/Showcase.kt`): pass `look`, a miniature of the
   setting's effect drawn in Compose in the live theme's colours. For a toggle `look(on)` draws the On or the Off
   look and the side panel plays them in turn (`ToggleShowcase`); for a choice `look(id)` draws one option, the side
   panel plays every option in turn (`ChoiceShowcase`) and the picker's cards show it too. Each look holds about two
   seconds with a crossfade, under a chip naming it and marking the one **In use**; with Reduce motion they stand
   side by side, still. Keep scenes cheap: one Canvas, motion read in draw or layer lambdas (`loopClock`). Build
   them from the scene helpers there (`MiniScreen`, `DeviceScene`, `LayoutPreview` with a changed `Look`) rather
   than new drawing kits. A `preview` replaces the showcase where a live picture says more (a slider's value, the
   accent swatches side by side).
4. Place it in `SettingsPlan`: in a group of its page, or of a subpage when it is rare and belongs with others there
   (`Sub`, opened by a `link(...)` row on the page). Or drop it there on purpose. There are no folds: no "More
   options". `SettingsPlanTest` fails while a row is neither placed nor dropped, when a page grows past twelve rows,
   or when a subpage isn't opened from exactly one row; a debug build shows a stray row under **Not placed**. Add a
   line to the table below.
5. Keep labels short (about 28 characters fit on one line). Put the explanation in `help`; it shows in the side
   panel and on the bottom screen.

Before adding one, ask whether a good default would do, and whether the setting already exists somewhere else. Each
setting has one place: a page never repeats a row from another page or a subpage, and Settings doesn't repeat what a
screen of its own already holds (the scrape options, the source order, the hidden list).

## Pages

| Tile | Summary shows | Groups (› opens a subpage) |
|---|---|---|
| Look & feel | theme name, own accent, still | Theme gallery, Theme packs gallery; Colour; Wallpaper, icons & motion (Wallpaper ›, Motion ›); Language; Start over |
| Home & layout | layout, icons across or games view | Layout gallery (seven layouts, Orbit and Spotlight included); Home screen (the rows for the layout in use, Grid shape › on the icon grid); Around the home (How games show ›, Dock & widgets ›, top bar, screensaver) |
| Two screens | single, swapped or the bottom style | Screens; Other screen (Art & video ›); While playing |
| Sound & music | the theme pack's audio, or sound pack and music | Theme pack (only when the theme pack has music or sounds); Sounds; Music |
| Controls | prompt style, A/B swapped, how many buttons are rebound | Buttons; Controllers (Connected controllers ›, Deck ›) |
| Library & art | games, systems, sources, RetroAchievements, Steam | Games & systems; Artwork & accounts (Accounts ›: RetroAchievements, Steam, Discord) |
| Play & system | resume state, Home screen | Playing (Performance & sleep ›); Tandem (Privacy & hidden ›, Your data ›, For tinkerers ›) |

## Every setting and where it is

A "›" step after a page is its subpage.

| Setting (stored in) | Where | Row type |
|---|---|---|
| `Look.theme` | Look & feel › Themes / Theme packs galleries | gallery |
| `Look.packParts` | Look & feel › Theme packs › Manage packs (pack manager); music and sounds also Sound & music › Theme pack | link / toggle |
| `Look.accent` | Look & feel › Colour › Accent colour (A types a hex colour; also sets what the highlight glows in) | choice |
| `Look.highlight` | Look & feel › Colour › Highlight style | choice |
| `Look.material` | Look & feel › Colour › Surface | choice |
| `Look.wallTop`, `wallBottom` | Look & feel › Wallpaper › Top screen / Bottom screen (your files from `ROMs/tandem/wallpapers`) | choice |
| `Look.wallDim` | Look & feel › Wallpaper › Picture dimming | slider |
| `Look.iconPack` | Look & feel › Icons from | choice |
| `Look.iconStyle` | Look & feel › Icon style | choice |
| `Look.motion` | Look & feel › Motion › Animation style | choice |
| `Settings.reduceMotion` | Look & feel › Motion › Reduce motion | toggle |
| `ExtrasSettings.floating`, `intensity` | Look & feel › Motion › Floating titles / Motion intensity | toggle / choice |
| `Look.swapStyle` | Look & feel › Motion › Swap animation | choice |
| (language, own preferences + LocaleManager on Android 13+) | Look & feel › Language › Language (System default or English, العربية, 日本語, 简体中文, Français; see TRANSLATING.md) | choice |
| (reset) | Look & feel › Start over › Reset look (keeps buttons, screens, sounds and music) | action |
| `Look.homeLayout` | Home & layout › Layout gallery | gallery |
| `Look.gridAcross` | Home & layout › Home screen › Icon size (icon grid; also L1/R1 on the grid) | choice |
| `Look.gridScroll` | Home & layout › Home screen › Scrolling (icon grid) | choice |
| `Look.gridLabels`, `ExtrasSettings.focusLabels` | Home & layout › Home screen › Icon names (icon grid: Always, Highlighted, Off) | choice |
| `ExtrasSettings.recentStrip` | Home & layout › Home screen › Recently played strip (icon grid) | choice |
| `Look.gridFolders` | Home & layout › Home screen › Folders open (icon grid) | choice |
| `Look.nowPlaying`, `ExtrasSettings.resumeLanding` | Home & layout › Home screen › Resume page (icon grid: Off, On, Opens first) or Now playing (every other layout: `nowPlaying` alone) | choice / toggle |
| `Look.gridRows`, `gridRoundness`, `gridGap`, `gridHeroSplit` | Home & layout › Grid shape (icon grid) | choice / slider |
| `Look.homeSections` | Home & layout › Home screen › Home rows (shelves, carousel, list, console) | choice |
| `Look.systemTiles` | Home & layout › Home screen › System tiles (shelves, carousel, list, console) | choice |
| `Look.folderPopup`, `fullscreenFolders` | Home & layout › Home screen › Open folders (other layouts) | choice |
| `Look.orbitGroups`, `orbitContinue` | Home & layout › Home screen › Orbit holds / Continue ribbon (Orbit) | choice / toggle |
| `Look.spotlightRail`, `spotlightBackdrop`, `spotlightMotion` | Home & layout › Home screen › Spotlight rail (left, right) / Spotlight backdrop (art, blurred cover, dim) / Spotlight motion (Spotlight) | choice / choice / toggle |
| `Look.gameView`, `gridSize`, `cardShape`, `shelfLook`, `Settings.showTitles` | Home & layout › How games show (Shelf look: Wood, Matte or Open bookcase for the Shelf view) | choice / toggle |
| `HomeSettings.showDock`, `dock`, `Look.dockStyle`, `dockTop`, `dockAutoHide` | Home & layout › Dock & widgets › Dock › Apps dock / Choose dock apps / Dock style / Dock on top / Auto-hide dock | toggle / link / choice |
| `WidgetStore.widgets`, `WidgetConfig.homeEnabled` | Home & layout › Dock & widgets › Widgets › Customize widgets / Widgets on the home screen | link / toggle |
| `WidgetConfig.gridPlace` | Home & layout › Dock & widgets › Widgets › Widgets on the grid (icon grid: In cells, As a row) | choice |
| `IconGridConfig.spans` | Y on a widget on the icon grid › Resize | menu |
| `WidgetConfig.upNext` | the Up next widget, and Y › Add to Up next on a game | menu |
| `Look.clock`, `battery` | Home & layout › Around the home › Clock / Battery | choice / toggle |
| `HomeSettings.screensaverMinutes` | Home & layout › Around the home › Screensaver (Quick settings › Screensaver starts it now) | choice |
| `SystemPrefs.singleScreen` | Two screens › Screens › Single screen | toggle |
| `Look.swapScreens` | Two screens › Screens › Swap screens | toggle |
| `AppsData.defaultScreen` | Two screens › Screens › Apps open on (Auto, Top, Bottom) | choice |
| `AppsData.holdBackMoves`, `moveGames` | Two screens › Screens › Hold Back to move (Off, Apps, Apps + games; says what it still needs: Shizuku, the Home button service) | choice |
| `SystemPrefs.keepRotationOff` | Two screens › Screens › Keep auto-rotate off | toggle |
| `Look.gridDetails` (icon grid) or `Settings.bottomMode` (other layouts) | Two screens › Other screen › Other screen / Style | choice |
| (bottom widgets) | Two screens › Other screen › Bottom screen when idle | link |
| `Look.videoSnaps`, `videoDelayMs`, `kenBurns` | Two screens › Art & video › Video snaps / Moving art | choice / toggle |
| `Look.heroLogoScale`, `heroGradient` | Two screens › Art & video › Logo size / Art fade (`Look.heroSplit` has no row since the details became one piece of art) | slider / choice |
| `HomeSettings.companion` | Two screens › While playing › Bottom screen while playing (Game art, Tandem companion, Screen off, Leave to emulator: the choice for every system and game without one of its own; 3DS and DS emulators keep their screen) | choice |
| `HomeSettings.companionOffAfter` | Two screens › While playing › Off after (Never, 1, 2, 5, 10 minutes without a touch before the bottom screen goes dark while a game plays; with Screen off, how long a tap lights it) | choice |
| `SystemPrefs.clipVoice` | Two screens › While playing › Voice in clips (your microphone in the companion's 15-second clips) | toggle |
| `SystemConfig.companion`, `GameMeta.companion` | Library & art › Systems and emulators › A on a system › Bottom screen while playing; Y on a game › More › Bottom screen while playing (the same four choices, for that system or game alone) | menu |
| Per-app screen, name, icon, game, hidden (`AppPrefs`) | Y on any app (dock, All apps, Android apps, grid) | menu |
| `PackParts.music`, `PackParts.sounds` | Sound & music › Theme pack › Theme pack music / Theme pack sounds (the group shows only while the theme is a pack with music or sounds of its own) | toggle |
| `Look.sounds`, `soundPack`, `soundVolume` | Sound & music › Sounds › UI sounds (Off, Tandem's 8 packs, then **Custom** when any recorded pack is on the device) / Sound volume | choice / slider |
| `Look.soundFiles` | Sound & music › Sounds › Custom sounds (shown while UI sounds is Custom: Cocoon's sound packs in `ROMs/assets/sounds`, then theme packs' own `sounds`; `none` is no recorded pack) | choice |
| `Look.pipVoice` | Sound & music › Sounds › Pip's voice (a few soft hummed notes with Pip's messages) | toggle |
| `Look.music`, `myMusic`, `soundtrack`, `musicVolume` | Sound & music › Music › Music (Off, My music, Calm, Meadow Tales; `soundtrack` null reads as Calm, or as Meadow Tales for a setup from before Calm) / Music volume | choice / slider |
| `Look.musicShuffle` | Sound & music › Music › Shuffle my music | toggle |
| (next song) | Sound & music › Music › Next song | action |
| `Look.prompts`, `xButton` | Controls › Buttons › Button prompts / X button (swap on the icon grid and Orbit homes, everywhere, or search; on Spotlight the first opens the story) | choice |
| `ControllerSettings.bindings` (Swap A and B, Swap X and Y) | Controls › Buttons › Swap A and B (Open on B, Back on A) / Swap X and Y | toggle |
| `ControllerSettings.bindings` | Controls › Buttons › Rebind buttons (a page: every command with its button, chord or hold; A listens for 5 s, X resets one, Y clears one, Reset all) | link |
| `Look.cornerHints` | Controls › Buttons › Corner hints | toggle |
| (`PadLink`, not saved) | Controls › Controllers › Controller in games (also Quick settings › Controller) | choice |
| `ControllerSettings.usePads` | Controls › Controllers › Always use a connected pad | toggle |
| `ControllerSettings.nintendo` | Controls › Connected controllers › one row per connected controller (Nintendo button labels) | toggle |
| (accessibility service `ControllerHomeService`) | Controls › Connected controllers › Controller Home button (opens Android's Accessibility settings) | link |
| `ControllerSettings.holdHome` | Controls › Connected controllers › Hold the Home button | choice |
| `HomeSettings.deckSensitivity` | Controls › Deck › Trackpad sensitivity (0.5× to 3×; the Deck is the bottom-screen keyboard and trackpad for PC games and streams) | slider |
| `HomeSettings.deckLayout` | Controls › Deck › Default layout (Full keyboard, Game keys) | choice |
| `HomeSettings.deckSounds`, `deckHaptics` | Controls › Deck › Key sounds / Key haptics | toggle |
| `GameMeta.companion`, `SystemConfig.companion` (`deck`) | Y on a PC game › More › Bottom screen while playing, and the system's row in Systems: the Deck is a choice there only for PC games and streams | menu |
| `SystemPrefs.roots` (with their document links in `SystemPrefs.trees`) | Library & art › Games & systems › Game folders (the folder manager: `ui/GameFoldersScreen.kt`; A on a folder for options, Y takes it out with Undo) | link |
| (rescan, manuals found: `Manuals.count`) | Library & art › Games & systems › Rescan games (how many games and manuals; manuals in `ROMs/manuals/<system>/`. Per game: Y › Edit details › Manual, stored in `GameMeta.manual`) | action |
| `Settings.watchFolders` | Library & art › Games & systems › Watch game folders (`FolderWatch.kt`; only while Tandem is on screen) | toggle |
| `Settings.sort` | Library & art › Games & systems › Sort games by (also Start › Sort) | choice |
| Systems, emulators, folders (`SystemPrefs`); Cocoon and Daijisho platform files | Library & art › Games & systems › Systems and emulators | link |
| Android apps and games (`AppPrefs`) | Library & art › Games & systems › Android apps | link |
| Scraping (`ScrapeConfig`) | Library & art › Artwork & accounts › Scrape library (what to fetch, region, language: the scrape screen); Clear downloaded media | link / action |
| Scraping sources, their order and keys (LaunchBox, ScreenScraper, IGDB, SteamGridDB, libretro, HowLongToBeat) | Library & art › Artwork & accounts › Set up sources (the tiles in order, L1/R1 move one; A opens its card) | link |
| RetroAchievements (`HomeStore.ra`) | Library & art › Accounts › RetroAchievements › Account / Show achievements / Look up games again / Sign out | action / toggle |
| Steam achievements (`SteamStore.account`, `steam.json`) | Library & art › Accounts › Steam › Steam Web API key / Steam account / Look up achievements / Sign out (the last two once signed in) | action |
| Discord status (`DiscordPresence`, token in `noBackupFilesDir/discord.json`) | Library & art › Accounts › Discord › Discord status (Connect) / Disconnect Discord; Quick settings › Discord status tile pauses it (`shown`). Only in builds with Discord's SDK | action |
| Resume (Shizuku) | Play & system › Playing › Resume running games | action |
| `HomeSettings.closePrevious`, `askBeforeClosing` | Play & system › Playing › Close previous game / Ask before closing a game | toggle |
| `HomeSettings.immersion` | Play & system › Playing › Immersion mode (shows "Needs Do Not Disturb access" until Tandem is allowed; A allows it through Shizuku, or opens Android's list) | toggle |
| `SystemPrefs.launchAnimation` | Play & system › Playing › Launch animation | toggle |
| `HomeSettings.perf` (`mode`, `fan`, `hz`) | Play & system › Performance & sleep › Performance mode / Fan mode / Refresh rate (AYN devices: the modes while any game plays, each Keep by default; the refresh rates are the ones the display has) | choice |
| (none) | Play & system › Performance & sleep › Performance profiles: the one row that stands in for the three above on a device without AYN's modes ("AYN devices only"), or while Shizuku is not running ("Needs Shizuku": Android lets only the shell write AYN's keys) | action |
| `GameMeta.perf` | Y on a game › More › Performance mode / Fan mode / Refresh rate (Default, Keep, or a value for this game) | menu |
| `HomeSettings.freezeAsleep` | Play & system › Performance & sleep › Pause games in sleep (shows "Needs root Shizuku" without it) | toggle |
| `HomeSettings.privateMode` | Play & system › Privacy & hidden › Private mode | toggle |
| `Settings.showHidden` | Play & system › Privacy & hidden › Show hidden games (in private mode it asks for the PIN) | toggle |
| `HomeSettings.offlineHidden`, `offlineUntilClosed` | Play & system › Privacy & hidden › Offline for hidden games (Off, Until closed, While playing) | choice |
| `private-pin.json` (`PinVault`) | Play & system › Privacy & hidden › Private mode PIN | action |
| `Settings.hiddenFolders`, `HomeFolder.hidden`, hidden games | Start › Hidden (each with Unhide; shown while something is hidden and, in private mode, revealed) | menu |
| Home app role | Play & system › Tandem › Use Tandem as the Home screen | link |
| Backups (`data/Backup.kt`, `ui/BackupScreen.kt`) | Play & system › Your data › Back up (parts to include, account keys) and Restore (backups newest first, X deletes; then parts to put back) | link |
| (import my Cocoon setup, import gamelists) | Play & system › Your data › Import my Cocoon setup / Undo Cocoon import / Import details | action |
| Play stats, export | Start › Stats & trophies (the Stats screen); Play & system › Your data › Export play stats (CSV in `ROMs/tandem`) | link / action |
| Update check (`Updates.kt`) | Play & system › Tandem › Updates (this version and the update's state: Up to date, 0.4.1 available, Downloading 42 %, Verifying, or what went wrong; A checks, or shows the update). Builds with `-PselfUpdate=false` show About Tandem (version and licence) instead | action |
| `Settings.checkUpdates` | Play & system › Tandem › Check automatically (once a day while Tandem is open, with a toast once and the Start panel row; off, only Updates looks; not in builds with `-PselfUpdate=false`) | toggle |
| Frame timing overlay, theme tour (`dev` preferences, `DevTools`) | Play & system › For tinkerers › Frame timing overlay / Tour every theme | switch / action |
| `Settings.welcomeDone` | Shown once after the first scan; not a row | n/a |
| Game switcher, quick settings panel | Hold Select / hold Start anywhere, or what they are rebound to (a note under Controls › Buttons shows the live bindings) | n/a |
| `Settings.batterySaver` (what Battery saver changed) | Quick settings › Battery saver | tile |

### Found elsewhere

Settings search also finds these, which live on a screen of their own, and takes you there (`elsewhereHits` in
`ui/SettingsSearch.kt`): what a scrape fetches (`ScrapeConfig.kinds`), the preferred region and language (the Scrape
library screen), each art source (its card under Set up sources), and Play stats (the Stats screen).

### Fields with no row

The theme decides highlight colour (`Look.highlightColor`, set with the accent), ring thickness (`highlightThickness`),
focus zoom (`focusScale`) and card corners (`corner`); theme packs can still set them. `Look.dockBackground`,
`dockCount` and `dockIconShape` keep their defaults unless an older setup changed them. `Look.splash` (the startup
splash) stays on. `Look.singleScreen` is migrated into `SystemPrefs.singleScreen` on start. `Look.swapAB` and `swapXY` are migrated into `ControllerSettings.bindings` on start (Open and Back, Game options and
Swap screens / Search swapped) and then cleared; the Swap rows read and write the bindings. `SystemPrefs.gameApps`
and `notGameApps` are migrated into `AppPref.game` the first time `app-prefs.json` is written. `AppPref.platform` is
stored but not used yet. A saved theme that is no longer built in, and the old "From the theme" value of
`Look.soundFiles`, are migrated on start (`Themes.migrate`).

## Quick switches

**Start** (the panel that folds open from the left) holds no settings: it goes places and arranges the home screen,
and its **Settings** row opens Settings' home. Its **Sort ▸** page sets `Settings.sort` (how games are sorted) beside
the home grid's sorts. Code: `startRows` in `ui/StartPanel.kt`.

**Quick settings** (hold Start, the ◐ Quick chip, the Battery widget, and at the top of the R2 side panel: one set of
controls, `QuickControls`) has the device switches only: brightness and volume sliders, then tiles for Wi-Fi,
Bluetooth and Airplane (switched in place with Shizuku, else they open Android's page), **Controller** (only where
controller passthrough can work), **Discord** (only once an account is connected), **Battery saver**, **Private
mode**, **Screensaver** (starts it now) and **Android** (Android's settings), in rows as even as they come out.
Code: `ui/QuickPanel.kt`.

**Battery saver** turns on Reduce motion and the Minimal animation style, and turns off video snaps, moving art and
music. It notes those five values first (`Settings.batterySaver`) and puts exactly them back when turned off; the
theme, layout and everything else are left alone. Android's own battery saver also stills the backdrops while it is
on (`Themes.still`).
