# Performance

Tandem targets smooth motion on a 120 Hz top screen (8.3 ms a frame) and a second display, on handheld hardware.
Two things matter most: the first frame of every screen change lands on the very next vsync, and an idle Tandem
costs as little CPU and GPU as possible. This page lists the targets, the techniques that keep Tandem within them
and the rules new code follows.

## Targets

Measured on the release build with `tools/perf.sh` (see [Measuring](#measuring)). Current: AYN Thor at 60 Hz (the
refresh rate it is set to), a video wallpaper on each screen, a game suspended (the Resume page on the grid).

| Measure | Target | Current |
| --- | --- | --- |
| Cold start to the home drawn | ≤ 700 ms | 470 to 490 ms (`TotalTime` about 460 ms) |
| Frames over 16.7 ms in the first 500 ms after a Home return | 0 | 0 to 2 (one run in four up to 4), worst 17 to 27 ms (was about 9, worst 35 to 48 ms) |
| D-pad held on the grid | janky < 1 %, p95 ≤ 8.3 ms | janky 0.4 to 0.6 %, p95 9 ms, p99 14 to 17 ms (was 0.9 %, 10, 15) |
| Idle CPU on the home | < 3 % of one core (still wallpaper), < 15 % (drawn backdrop or video) | 13 to 15 % (video; was about 30 %) |
| Idle CPU in the screensaver | as on the home | about 16 % (not measured again) |
| PSS | ≤ 220 MB | 200 to 237 MB, 265 with both videos playing (was 229 to 326) |
| Rescan with no folder changed | ≤ 250 ms | about 30 ms |
| UI thread blocked by scraping or a running game | never over 8 ms | not measured by the script |

Known gaps. The first frame back still composes for 4 to 6 ms and its RenderThread work takes 5, so it lands at
17 to 21 ms about half the time; Android re-lays out the bottom-screen home's window (under Tandem's bottom screen)
in that frame, with the borrowed library still in it (8 to 14 ms). The old script missed most of these: it skipped
the first frame back (a layout-change frame) and counted the windows from the bottom-screen home's earlier frames. A held D-pad costs about 8 ms a frame on the scrolling top screen (the lazy grid's
layout, and its faded edges drawn offscreen). Most of the memory left is graphics: the window buffers and the video
decoders' output buffers (16 of 3 MB for a 1080p60 wallpaper).

## Release build

Always judge smoothness on the release build; a debug build of Compose is several times slower.

- **R8** (`isMinifyEnabled`, `isShrinkResources`, `app/proguard-rules.pro`). Names are kept (`-dontobfuscate`) so
  stack traces stay readable. Keep rules cover Shizuku (provider, AIDL, the `newProcess` reflection) and
  kotlinx-serialization; media3 and Coil ship their own.
- **Baseline profile** (`app/src/main/baseline-prof.txt`, hand-written). It marks Tandem's own code and the Compose
  runtime, UI, foundation, animation and Material 3 paths, androidx.core, Coil and the media3 player as hot, so
  ART compiles them ahead of time. `androidx.profileinstaller` applies it on sideloaded installs. It is written by
  hand rather than generated with a Macrobenchmark module: that needs androidx.benchmark, UI Automator and the
  baseline-profile Gradle plugin (not in the offline build's dependencies), and a run that restarts the Home app
  on the device many times.
- **Signing.** Without a `keystore.properties` in the project root (`storeFile`, `storePassword`, `keyAlias`,
  `keyPassword`; never committed), `assembleRelease` signs with the debug key so the APK installs straight away. On
  CI (`CI=true`) the APK stays unsigned and the release workflow signs it ([Releasing](RELEASING.md)).

## Screen changes

- **Home and the first Settings screen stay composed** (`KeptScreen` in ui/App.kt) and measured while hidden.
  A hidden kept screen is not placed or drawn, cannot take focus, and its lifecycle is capped at CREATED
  (`CappedLifecycleOwner`), so its videos and loops stop. Showing it is only a fade and slide in the graphics layer.
  Settings is composed ahead once Tandem is idle after start, or when the Start panel opens.
- **Start panel pages** stay composed while the panel is open, so opening one is a layer slide.
- **One motion spec, GPU only.** Route changes use `Motion.screenIn` / `Motion.screenOut` (220 ms, 160 ms with
  Minimal; emphasized decelerate in, emphasized accelerate out) as a graphics-layer translation (`routeMotion`),
  never a layout offset.
- **The top bar lies over the screens** (`TopBarOverlay`); each screen keeps a fixed room for it (`TopBarHeight`),
  so showing or hiding it never re-lays out the screen.
- **Focus shows at once:** `takesFocus` requests focus right after the next frame. Whether its layer is on top is
  watched in an effect (`snapshotFlow`), so popups opening or closing recompose nothing underneath.
- **Cached data.** `Facts` (ui/Warm.kt) reads folder listings, backups and role/display state on a background
  thread and keeps them as Compose state. `Collections` and `Libraries.games` are cached per library change and
  warmed on `Dispatchers.Default`.
- **Cheap cards and lists.** `FocusCard` works out its focus zoom inside the graphics layer, not from measured size
  in state. Lists use `contentType`; `Game` and `GameCollection` are `@Immutable`.
- **Icon grid.** Only tiles gaining or losing focus recompose. Tile pictures are decoded ahead into the memory
  cache, nearest the highlight first, and share one cache entry per file (`sizedRequest`), so zooming never blanks
  the grid. Page-by-page scrolling is one 220 ms tween (`slideToColumn`) over tiles that are already laid out.
  Empty spots (`EmptySlot` in ui/GridSpots.kt) are lazy items with their own `contentType`, drawn in one cached
  pass; their focus animates a value read only while drawing, so moving through them recomposes nothing. A widget
  on the grid is one lazy cell that draws over the cells it covers (`spill` in ui/GridWidgets.kt), so no row or
  column changes size; the cells it covers are plain spacers.

## Cold start

The sequence lives in `ui/StartFlow.kt`; the starting window's colour in `StartLook.kt`.

- **Starting window.** `MainActivity` uses `@style/Tandem.Start`: the theme's backdrop colour, full screen, no
  bars, and on Android 12+ a blank splash icon with no animation. There are ten colour variants
  (`Tandem.Start.*`). On Android 13+ the one closest to the theme in use is chosen for the next start
  (`setSplashScreenTheme`, called from `onStop` only when it changes). The exact colour is kept in a small
  preference (`start_look`), and `MainActivity`, the bottom-screen home and the bottom screen paint their windows
  with it before anything else loads.
- **Library first.** `LibraryScan.startEarly` starts the scan at the end of `onCreate`, so the cached library
  (`scan-cache.json`, `Library.scan(quick = true)`) is read on `Dispatchers.IO` while Compose builds the first
  frame. Both passes share one `Platforms.load`. The full pass waits 1.2 s after the cached library is shown, and
  when no folder changed it keeps the cached library instead of replacing it, so the home isn't rebuilt.
- **Held first frame.** When a scan cache exists, an `OnPreDrawListener` holds the window's first draw until the
  home is composed, for at most 800 ms. Android's starting window stays up meanwhile, so the first frame Tandem
  draws is the home. Composition and layout still run during the hold; only the draw waits.
- **Arrival.** Both screens' stages hold their content out of sight (`SwapFx.introHold`: the swap clock waits at
  0) and play the arrival half of the X swap once the home is composed (`introPlay`). The grid's tiles fly in
  staggered from the highlighted one, and other screens rise in whole. This is the same graphics-layer clock as a
  swap, and it runs for 950 ms, once per process.
- **Splash.** It shows only when the home isn't ready in time: a first run, an empty cache, or a read slower than
  the hold. It is drawn over the stage's wallpaper (`StartSplash`) with `Animatable`s that run only while it is
  composed. The count of games found (`Library.found`) is polled every 150 ms. With reduce motion nothing is held
  or animated.
- **Bottom-screen home at boot.** For its first 4 s after the process starts, it draws only the still bottom
  wallpaper, the same one the library's bottom screen opens on.

## Coming back from a game

The top screen shows its last picture first; everything else is staggered with `AfterFrames` (ui/Warm.kt):

| When | What |
| --- | --- |
| at once | Idle timer reset, play session closed; the library stops taking keys on the bottom-screen home |
| after 1 frame | The kept bottom screen shows again (`BottomScreen.unpark`) |
| after 2 frames | Play time recorded; a new bottom screen if none was kept |
| after 2 frames + 220 ms | Music resumes |
| after 3 frames | `returnCount` (the Resume page landing) |
| after 450 ms | Running-games check (Shizuku) |
| after 2 frames + 600 ms | The bottom-screen home, now covered, lets the borrowed library go (`guestBack`) |
| after 3 frames + 650 ms | `resumeCount` (permission, Home role and display checks), `Facts` refresh |
| after about 1.4 s | Library rescan (changed folders only) |

Pending work is cancelled, or run at once for play time and `guestBack`, if Tandem leaves again. Video wallpapers
keep their player for 8 seconds in the background (`VideoKeeper` in ui/Video.kt), so a quick return needs no new
decoder.

- **The bottom screen is kept, hidden.** Going to the background, `MainActivity` hides the bottom-screen
  presentation (`BottomScreen.park`: `Dialog.hide()` plus `FLAG_NOT_TOUCHABLE`, it was already never focusable)
  instead of dismissing it. A hidden window has no surface and is no input target, so the controller, the touch
  screen and the display belong to the game, its companion or the bottom-screen home. Its content stops with the
  activity's lifecycle. Coming back shows the same window and composition again; it is taken down after 5 minutes
  away, when Android is short of memory (`onTrimMemory`), when a game opens on the bottom screen, or when an app was
  moved down there (`AppMove`).
- **Nothing recomposes for a return that doesn't change.** A focus hand-back (`Nav.focusTick`) is watched inside
  `takesFocus`'s effect, not read while composing, and a screen kept composed while hidden (Settings) ignores it until
  it shows (`keptTick`). Settings reads `resumeCount` only while open (`settingsResumes`). The grid catalog and the
  dock follow package changes (`Emulators.changes`), not every return.
- **No accessibility tree for services that can't read it** (`QuietSemantics`, ui/QuietSemantics.kt). Compose
  updates its accessibility tree and sends events every frame something changes as soon as any accessibility service
  is on. The Thor always has two (the AYN Game Assistant and Tandem's Controller Home button), and neither can read
  window content; that work was about a quarter of the main thread's time on a return and during a held D-pad. While
  no enabled service can read window content and touch exploration is off, each Compose window is told no service is
  on (reflection on Compose's `enabledServices`, kept by an R8 rule); any change to accessibility checks again, so a
  screen reader or Switch Access turns it back on at once.

Polls run only while they are needed, always off the main thread: the running-games check every 12 s (3 s with the
switcher open) only while Tandem is resumed; the focus check (`FocusGuard`) once or twice after a resume or Home;
the game-exit watch (`GameExit`, every 2.5 s) only while the companion is up during a game, and every 10 s
without a Shizuku call while the screen is off.

## Idle

- **One ambient clock** (`Ambient` in ui/Ambient.kt) drives all decorative motion: floating titles, drawn
  backdrops, Ken Burns art, folder sheens, Pip, the Pulse highlight. Motion reads `Ambient.ms` inside layer or draw
  lambdas, never in composition or layout. The clock ticks only while a showing, resumed screen watches it
  (`rememberAmbient`).
- **Calmer over time.** Ten seconds after the last input it ticks on a timer at about 30 fps, and about 12 fps
  after half a minute; the main thread sleeps between ticks. `Ambient.livelyMs` stands still when idle (Pip stops
  bobbing; blinks and glances carry on, rarer). With only Pip watching, no frames are requested until the next input.
- **One tick for everything.** Short decorative animations (Pip's blinks and glances, a logo's shine) run on
  `AmbientFrames`: the display's frames while Tandem is in use, the ambient clock's ticks once idle, with their values
  written inside the tick's frame callback. So idle motion draws 30 frames a second per screen, never extra frames
  in between.
- **Resting after three minutes** without input (`Ambient.resting`): the clock stops, Pip rests, wallpaper videos
  hold their frame and then let their player go. Clocks on screen keep their own minute timer. Any input wakes it.
- **What the home grid holds rests when idle.** Motion inside the grid (the Resume page's logo and dot,
  `floating(restsWhenIdle = true)`) stops with `livelyMs`: every tick of it redrew the whole grid under its faded
  edges (about 2.4 ms of RenderThread a frame). Idle on the home, the top screen draws only for Pip.
- **Covered means stopped.** Under a popup, the Start panel, a menu or the launch animation, the clock stops and
  videos hold their frame. Under the screensaver only its own scene runs. A running game stops everything.
- **The screensaver** runs for hours, so its scene (both screens) moves on a 30 fps `SteadyClock`, not on every
  vsync.
- **Clocks tick once a minute**, on the minute (`rememberMinute`), so a time on screen recomposes nothing in
  between.
- **Video wallpapers** draw to a `SurfaceView`, so frames go straight to the compositor; video only (no audio
  renderer), capped at about 30 fps (`CappedVideoRenderer`), and only 2.5 to 5 s of the file read ahead (the
  player's default keeps up to 50 s of samples on the Java heap). The same video on both screens is decoded once; the
  bottom screen shows a still (`Stills`). Faded or masked videos keep a `TextureView`. Under something opaque that
  fills the screen (Spotlight's game art marks it with `WallpaperCover`), inside Settings, under a popup or while
  resting, the video holds its frame, and after 4 s its player goes and a still frame stands in (a 1080p decoder
  holds tens of MB of buffers); coming back, the still stays over the video until its first frame.
  `experimentalSetDynamicSchedulingEnabled` was tried: with the frame cap it made the playback thread busier.
- **A held D-pad** moves the highlight on every repeat, but the details it drives (the other screen's Spotlight, the
  name by Pip, a hero wallpaper) wait until the highlight rests for 150 ms or the D-pad is let go
  (`Selection.follow`); a single press shows them at once. Meanwhile the ambient clock ticks at the idle pace.
- **Images.** Coil's memory cache is 15 % of the app's memory class (`Images.install`; Coil's default is 25 %).
- **The library under an app.** While an app has the top screen, the bottom-screen home composes the library
  (`LibraryGuest`) under its own lifecycle, so it stops when covered or off. It starts no rescan or `Collections`
  warm-up of its own: the stopped top library's watchers still run.
- **Settings over a calm wallpaper.** Inside Settings both screens show a small, pre-blurred, dimmed still of their
  wallpaper (`WallpaperStill(soft = true)`, made on a background thread) and drawn backdrops stop moving.

## The in-game companion

The bottom screen during play (ui/CompanionScreen.kt) never sees the player's input, so it does not follow the
`Ambient` idle rules.

- **Its own steady clock** (`SteadyClock` / `rememberSteadyClock`) ticks at 30 fps while the screen is bright.
  Art pan and zoom, the logo's sway and shine, and Pip's animation read it in layer and draw lambdas.
- **Easing to rest.** When the screen dims after a minute, motion eases to its rest pose over the dim fade, then
  the clock stops. Only the minute timer and the burn-in drift remain: every 40 s the picture eases one pixel
  along a small loop (never more than 2 px off), at about 10 fps for 6 s. A touch eases it back in.
- **Logo shine.** Each pass of a logo's shine is its own 1.1 s animation (about 30 fps on the companion or once
  idle), so it always finishes. The next pass starts only while the logo moves and nothing covers or dims it.
- Reduce motion, animations Off and battery saver keep it still; in the background it stops.

## Rules for new code

- Never read files, list folders, query the package manager or call Shizuku while composing. Read in the
  background and keep the result as state (`Facts`, or `produceState` on `Dispatchers.IO`).
- Don't filter or sort the whole library in a composable: use `Collections` or `remember` keyed on its inputs.
- Animate graphics-layer properties (alpha, translation, scale) read in the layer lambda, not layout offsets or
  sizes. Use `Motion.screenIn/screenOut` for screen changes and `Motion.focus/soft/move` for the rest.
- Never animate a layout size (`expandVertically`, `animateContentSize`) of something that pushes a screen around.
- No `rememberInfiniteTransition` for idle decorative motion: use `rememberAmbient()` and `Ambient.ms` /
  `Ambient.loop` / `Ambient.pingPong` (or `livelyMs` for motion that should rest when idle). Companion motion
  reads a `SteadyClock`.
- Nothing behind a popup moves. Pause videos and loops when covered.
- Full-screen videos use `LoopingVideo(surface = true)`.
- Don't keep a measured size in state to compute something visual; use `graphicsLayer` or `drawWithCache`.
- Kept screens stay composed while hidden: anything in them that reacts to buttons (`Buttons.events`,
  `InterceptButtons`, `BackHandler`) checks `Nav.route` first.
- Work that can wait after a screen appears goes through `AfterFrames.run` or a delayed `LaunchedEffect`.
- Short decorative animations (a blink, a shine) run in `withContext(AmbientFrames)` and write their value inside the
  `withFrameNanos` block, so idle they draw on the ambient tick.
- Don't read `Nav.focusTick` or `MainActivity.resumeCount` while composing a whole screen: both change on every
  return. Use `rememberFocusTarget` / `takesFocus`, `settingsResumes()` in Settings, or read them in an effect.
- Something that moves inside the home grid uses `floating(restsWhenIdle = true)` (or `livelyMs`).

## Measuring

Build and install the release APK:

```sh
nix-shell --run './gradlew assembleRelease'
adb install -r app/build/outputs/apk/release/app-release.apk
```

It is signed with the debug key, so it installs over a debug build and keeps its data. To apply the baseline
profile at once rather than in the background:

```sh
adb shell cmd package compile -f -m speed-profile io.github.zahakj.tandem
adb shell dumpsys package dexopt | grep -A2 io.github.zahakj.tandem   # expect [status=speed-profile]
```

### tools/perf.sh

With the release build installed and Tandem on the home screen (icon grid):

```sh
ADB=adb SERIAL=<serial> tools/perf.sh                 # every step, about 4 minutes
STEPS="cold home" OUT=/tmp/perf.json tools/perf.sh     # only some steps
```

| Step | What it does |
| --- | --- |
| `cold` | `am start -S -W` five times: median `TotalTime`, and the median time from the process start to the first frame drawn with the home (logged as `TandemPerf`). A run where force-stop leaves the Home app alive is skipped |
| `home` | `HOME_RUNS` times (default 3): opens `OTHER_APP` (Settings), presses Home, reads `gfxinfo framestats` of all of Tandem's windows: frames over 16.7 ms in the first 500 ms from the top screen's first frame back (the worst run); then the rescan time Tandem logs (`TandemPerf`) |
| `dpad` | holds D-pad Right for 5 s with `su -c sendevent` (`INPUT_DEV`, default `/dev/input/event9`; `KEY_RIGHT=547`, decimal): janky %, p50 to p99 from `gfxinfo` |
| `idle` | presses Home, waits 12 s, then counts the process's `utime + stime` over 40 s, as % of one core (inside a minute, before a screensaver set to a minute starts) |
| `mem` | total PSS from `dumpsys meminfo` |
| `apk` | installed and built APK size |

It prints a table against the targets above and writes the numbers to `OUT` (default `perf-result.json`). The
idle result depends on the wallpaper: run it once on a still one and once on a drawn backdrop or video.

### gfxinfo

```sh
adb shell dumpsys gfxinfo io.github.zahakj.tandem reset
# use Tandem: open Settings and a page, go back, return from a game, swap screens
adb shell dumpsys gfxinfo io.github.zahakj.tandem
```

Watch "Janky frames", the percentiles, "Number Missed Vsync" and "Number Slow UI thread" (composition and layout).
Add `framestats` for per-frame timings of the last 120 frames.

### Frame timing overlay

**Settings › Data & about › Developer › Frame timing overlay** turns on JankStats for the top screen: a corner
readout of frames, slow frames and the worst frame. Every frame longer than twice the display's frame time is
logged with the route, open popup and swap state, with a summary every 10 seconds:

```sh
adb logcat -s TandemJank
```
