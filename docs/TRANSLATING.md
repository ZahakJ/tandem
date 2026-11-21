# Translating Tandem

Tandem speaks English, and is being made ready for العربية (Arabic), 日本語 (Japanese), 简体中文 (Simplified Chinese,
zh-CN) and Français. This page is the pattern for moving text out of the code (extraction), for translating it, and
for the right-to-left pass still to come. Paths are relative to `app/src/main/`.

- [How it works](#how-it-works)
- [Extracting a file, step by step](#extracting-a-file-step-by-step)
- [Files and keys](#files-and-keys)
- [Reading text in code](#reading-text-in-code)
- [Arguments and plurals](#arguments-and-plurals)
- [Writing the XML](#writing-the-xml)
- [Settings rows, choices and enums](#settings-rows-choices-and-enums)
- [What is never translated](#what-is-never-translated)
- [The tests](#the-tests)
- [Fonts and fitting](#fonts-and-fitting)
- [Trying a language on the handheld](#trying-a-language-on-the-handheld)
- [For translators](#for-translators)
- [Right to left: notes for the RTL pass](#right-to-left-notes-for-the-rtl-pass)
- [Who extracts what](#who-extracts-what)

## How it works

- Every piece of text the user sees lives in `res/values/strings_<area>.xml` (English, the default) and, once
  translated, in `res/values-ar/`, `values-ja/`, `values-zh-rCN/` and `values-fr/` under the **same file names**.
  A string that isn't translated yet shows in English.
- Code reads it through **`Tx`** (`java/.../Tx.kt`): `Tx.s(R.string.x)`, `Tx.p(R.plurals.x, n)`. The same call works
  in composables, in click handlers, in `Toasts.show`, in Settings row builders, in services and on any thread.
- **`Lang`** (same file) is the language: Settings › Look & feel › Language (System default, English, العربية, 日本語,
  简体中文, Français). On Android 13 and later it is Android's own per-app language (`LocaleManager`, the same as
  Android Settings › Apps › Tandem › Language, offered from `res/xml/locales_config.xml`); on Android 11 and 12 Tandem
  keeps it itself. `TandemApplication` sets it up before anything shows.
- **Why not `stringResource`:** on Android 11 and 12 nothing makes an activity's resources follow an app language,
  short of recreating every activity (the library, its bottom screen `Presentation`, the companion, the
  bottom-screen home, the screensaver), which they were never built for. `Tx` reads from resources made for the
  chosen language instead, the same way on every version, and reading it inside a composable subscribes that
  composable to `Lang.revision`. Changing the language bumps the revision: both screens recompose in place, with
  their focus, scroll, open panels and routes untouched. Nothing is recreated: the activities list
  `locale|layoutDirection` in `configChanges`, so Android 13's own language change doesn't recreate them either.
  Do not use `stringResource` / `pluralStringResource`: on Android 11 and 12 they show Android's language.
- `TandemTheme` (`ui/Design.kt`) carries the language into the type (`TextStyle.localeList`, see
  [Fonts](#fonts-and-fitting)) and sets the layout direction from `Lang.layoutDirection`, left to right until the
  RTL pass sets `Lang.RTL_READY`.

## Extracting a file, step by step

1. Read the file and list every string a person sees: `Text`, `ShrinkText`, `Toasts.show`, `ButtonHint` labels,
   `MenuAction` labels, values and subs, `Pill`s, `TextEntry.ask`, `Confirm.ask`, `Undo.offer`, Settings rows (label,
   value, help, options), `Selected.Setting`, accessibility `contentDescription`s, notification titles, and the
   strings functions return to any of these (`pipLine()`-style helpers, `when` branches, enum labels).
2. Add each to your area's `strings_<area>.xml` (see [Who extracts what](#who-extracts-what)), keyed
   `<area>_<screen>_<meaning>` (see [Files and keys](#files-and-keys)), with a `<!-- comment -->` wherever the
   English alone doesn't say where it shows or what an argument is.
3. Replace the literal with `Tx.s(R.string.…)`, a count with `Tx.p(R.plurals.…, n)`, a sentence built from pieces
   with one string with `%1$s` arguments. Settings rows also get `en = "English label"` (see
   [Settings rows](#settings-rows-choices-and-enums)).
4. Add the file to `extracted` in `test/.../HardcodedTextTest.kt`.
5. Build and test (`nix-shell --run './gradlew -q assembleDebug testDebugUnitTest'`). English must read exactly as
   before: same words, same capitals, same punctuation. The extraction changes no wording.
6. Look at it in `en-XA` on a debug build if you can (see [Trying a language](#trying-a-language-on-the-handheld)):
   any text still in plain English there was missed.

`ui/StartPanel.kt`, `ui/QuickPanel.kt`, `ui/SidePanel.kt` and `ui/LanguageSettings.kt` are done and are the examples
to copy.

## Files and keys

- One file per area: `res/values/strings_<area>.xml`, `<area>` one lowercase word (`panels`, `settings`, `home`…).
  Each extraction agent owns its area files outright, so nobody edits the same XML. `strings.xml` holds only the
  few strings the manifest names (app name, the Controller Home accessibility service); leave it as it is.
- `strings_common.xml` (`common_…`) is the handful of words used everywhere: Back, Close, Choose, On, Off, Clear,
  `common_percent` ("87%"), `common_size_free` ("12 GB free"), the Language row. **During the parallel extraction it
  is frozen**: reuse its keys, and put anything else, even a common-looking word, in your own area. A later pass can
  merge duplicates; two agents editing one file can't be merged cleanly.
- Keys: `snake_case`, starting with the file's area and an underscore (`strings_panels.xml` holds only `panels_…`;
  the test checks this). After the area, the screen or feature, then what the text **means**, not what it says:
  `panels_start_hidden_tuck`, not `panels_tuck_away`; `home_grid_move_done`, not `home_moved_it_toast`. Keys are
  forever: renaming one drops its translations. Change the English text freely, the key stays.
- A word used in two places with two meanings gets two keys, even when the English is the same ("Showing" in Start
  › Hidden and "Showing" on the Discord tile are `panels_start_hidden_showing` and `panels_quick_discord_showing`):
  other languages may need different words, cases or genders.

## Reading text in code

| Where | How |
| --- | --- |
| A composable | `Text(Tx.s(R.string.panels_quick_title))` |
| A click handler, a callback, a coroutine | `Toasts.show(Tx.s(R.string.panels_quick_saver_on))` |
| A sentence with values | `Tx.s(R.string.panels_start_folder_made, name)` for `Made the folder %1$s. …` |
| A count | `Tx.p(R.plurals.panels_start_trophies, n)` (`%1$d` is `n`) |
| A count with more arguments | `Tx.p(R.plurals.home_x, n, n, name)` (pass `n` yourself among the arguments) |
| Android formatters | `DateUtils.formatDateTime(Tx.context, …)`, `Formatter.formatShortFileSize(Tx.context, …)` |
| Dates and numbers you format | `Lang.locale` (`DateTimeFormatter.ofPattern(…, Lang.locale)`, `String.format(Lang.locale, …)`) |
| A row's stable name, search | `Tx.en(R.string.x)`: the English text in every language (never shown) |

Rules:

- **Never keep a translated string.** Not in `remember { }`, a field, a data class held in state, a cache, a file.
  Keep the resource id and call `Tx` when showing it. If you must remember, key it:
  `remember(Lang.revision.intValue) { … }`. Text computed inside composition (including row builders like
  `xxxSettings()` and helpers they call) is fine: it is redone when the language changes.
- Text that is **saved** (an undo label stored with the grid, a note's default title) stays in the language it was
  made in. That's accepted; say so in a comment where it happens (see `sortHome` in `ui/StartPanel.kt`).
- Text made **off the main thread** and handed to state (a scan's progress line) keeps its language until it is made
  again. Fine for short-lived messages; for anything that stays up, store an id and arguments instead.
- `Tx` works before any UI exists (services, receivers, the screensaver): `TandemApplication` loads it first.
- `onOff(b)` (`ui/Settings.kt`) already gives On / Off in the language.
- **Don't build sentences from pieces.** `"Games: " + label` becomes `Games: %1$s`; `"$n playing"` becomes a plural;
  `greeting() + " All quiet."` becomes `%1$s All quiet.` Word order differs between languages; only a whole sentence
  with arguments lets a translator move them.
- Don't split a sentence over several resources to style part of it; ask before adding markup.
- Long English texts written as `"…" + "…"` across lines in Kotlin become **one** string in the XML.

## Arguments and plurals

- **Always positional:** `%1$s`, `%2$d`, never `%s` or `%d`, even with one argument. The test fails otherwise.
- A literal percent sign in a string that takes arguments is `%%` (`%1$d%%` for "87%"). In a string without arguments
  write a plain `%`.
- **Every count is a `<plurals>`**, even when English happens to read the same for all numbers ("3 playing"): other
  languages change. English needs `one` and `other`; the test checks each translation has what its language needs:

  | Language | Quantities |
  | --- | --- |
  | English (`values/`) | `one`, `other` |
  | Arabic (`values-ar/`) | `zero`, `one`, `two`, `few`, `many`, `other` (all six) |
  | French (`values-fr/`) | `one`, `other`, and `many` if you like (large round numbers: "1 000 000 de jeux") |
  | Japanese, Chinese | `other` only |

- A form may leave the number out ("One thing for you.", Arabic's `one` and `two` usually do), but may never use an
  argument English doesn't have, and `other` has them all.
- The app's own `plural(n, "game")` helper (`Selection.kt`, and private copies in a few files) is English only.
  Converted files must not use it (the test checks); it goes once every file is converted.

## Writing the XML

| Write | For |
| --- | --- |
| `\'` | an apostrophe: `can\'t` (a bare `'` fails the build or the test) |
| `\"` | a double quote |
| `\@`, `\?` | an `@` or `?` at the very start |
| `&amp;` `&lt;` | `&` and `<` (`Stats &amp; trophies`) |
| `…` | the one-character ellipsis, as the English uses it |
| `\n` | a line break (rare; prefer letting text wrap) |

- `›` in a path ("Settings › Look & feel") and button letters (A, B, X, Y, L1, R1, Start, Select) stay as they are
  in every language.
- `translatable="false"` marks a string that must never be translated (rare: use it instead of hard-coding a brand
  in a file the guard test watches, or keep the literal with `// tx-ok: a name`).
- Comments: one above any string whose place or arguments aren't obvious, saying where it shows and what
  `%1$s` is. Translators see nothing else.
- No `<string-array>`: the safety test doesn't read them. A list of lines (tips, Pip's sayings) is numbered keys
  (`home_tip_1`, `home_tip_2`…) and a `listOf(R.string.home_tip_1, …)` in code.

## Settings rows, choices and enums

Settings places rows by their English label (`SettingsPlan` in `ui/SettingsCatalog.kt`), and focus memory, search
jumps and the `Synonyms` map use it too. So a row whose label comes from the resources carries its English name:

```kotlin
toggle(
    Tx.s(R.string.look_reduce_motion), Glyph.Motion, Palette.Sky, Tx.s(R.string.look_reduce_motion_help), on,
    en = "Reduce motion",
) { … }

SettingRow(Tx.s(R.string.play_resume), value, Palette.Mint, Glyph.Play, Tx.s(R.string.play_resume_help), en = "Resume running games") { … }

SettingSection(Tx.s(R.string.look_group_colour), rows, en = "Colour & highlight")   // en on the SettingSection( line
```

- `en` is exactly today's English label, and never changes afterwards, even if the English text does: it is an id.
  `choice`, `number` and `toggle` (`ui/Appearance.kt`) take `en` too. `SettingsPlanTest` reads `en = "…"` the way
  it reads a literal label; keep `en` for a `SettingSection` on its `SettingSection(` line.
- Search finds a row by its label in the language **and** by `en` and the English `Synonyms`/`keywords`, so English
  words find settings in every language (the search keyboard has Latin letters only). `keywords` stay English;
  localized synonyms can be added to them.
- `SettingsPlan` itself (page groups `Group("Colour", …)`, `Sub(… title, help)`, `SettingsCategories.title`, the
  tiles' blurbs and summaries in `settingsCatalog()`) stays keyed in English; what it **shows** must be looked up:
  give `Group` and `Sub` a `@StringRes` for display and keep the English as the key. That is the settings
  extraction's first job (area A below), before the feature rows.
- A choice's options are `id to label`: keep the ids, translate the labels.
  `fitsOnRow` (`ui/Choices.kt`) decides segmented or picker by character count; a CJK character is about twice as
  wide as a Latin one, so check a segmented row in Japanese after translating.
- Enums with a label (`SortOrder.label`, `HighlightStyle.label`, `SoundPack.label`…): give the entry a `@StringRes`
  and make `label` a getter, so every caller keeps working:

  ```kotlin
  enum class SortOrder(@StringRes private val res: Int) {
      Name(R.string.library_sort_name), …;
      val label: String get() = Tx.s(res)
  }
  ```

  Never store the label in a saved file; store the enum's name.

## What is never translated

Ids and keys (`MenuAction("folder", …)`'s first argument, focus keys, routes), file and folder names, log tags and
log text, intents and settings keys, JSON field names, `en = "…"` and `keywords`, Shizuku/shell commands, button
letters, brand and product names (Tandem, Pip, Discord, Shizuku, RetroAchievements, Steam, the emulators), system and
console names (they come from `platforms.json` and the user's metadata), game titles, the soundtrack's song titles,
and anything the user typed.

## The tests

All run in `testDebugUnitTest`:

- **`StringResourcesTest`** reads every `values*/strings*.xml`: keys unique and named for their file; a translation
  holds only keys English has, in the same file and of the same kind; English uses positional arguments and `%%`;
  each translated string has the same arguments as English (plurals: never more, `other` the same); plurals have
  their language's quantities; apostrophes and quotes escaped; `Lang.offered` matches `locales_config.xml` and the
  translation folders. It prints a coverage line per language (`ja: 120 of 2400 translated (5%)`): missing
  translations are allowed and show in English.
- **`HardcodedTextTest`** guards converted files: no string literal with a letter in it passed to a call that shows
  text (the list is in the test), no `contentDescription = "…"`, no `plural(`. Ids, `en =`, `keywords =`, symbols
  ("›", "✓") and number templates ("$n%") pass. A literal that really must stay says why on its line:
  `// tx-ok: a name`. It can't see strings returned from helpers or held in `when` branches far from the call:
  step 1 above is still a careful read.
- **`SettingsPlanTest`** keeps every Settings row placed, by its literal label or its `en`.

## Fonts and fitting

- Nunito covers Latin only. Arabic, Japanese and Chinese characters fall back, glyph by glyph, to Android's own fonts
  (Noto), so mixed text ("ゼルダの伝説 · Switch") keeps Nunito for the Latin part and never shows empty boxes.
- Every style in `TandemTheme`'s typography has a fixed line height, so a taller fallback font doesn't push lines
  apart or move baselines. Keep it that way: don't set `lineHeight = fontSize` (or smaller) on text that can hold
  words; Arabic and CJK fallback glyphs need the usual room above and below. (Clock digits and icon letters are fine.)
- The typography carries `Lang.locale` (`localeList`), which picks the Japanese or the Simplified Chinese shapes of
  the characters both share, and their line breaking. A `TextStyle(...)` made from scratch doesn't have it: start
  from `MaterialTheme.typography.x.copy(...)` instead.
- `FontFamily.Monospace` and `FontFamily.Serif` styles (`SystemIcons`, notes' code) are for symbols and Latin; don't
  put translated words in them.
- Lengths: French runs about a quarter longer than English; Japanese and Chinese are shorter but each character is
  wide; Arabic is about as long. Rows are one line with `ShrinkText` or ellipsis: check yours in `en-XA` (a third
  longer). Short labels (tiles, segmented options, hints) get a comment in the XML asking to stay short.

## Trying a language on the handheld

- **In Tandem:** Settings › Look & feel › Language. Both screens switch at once: the panel you were on stays open,
  the highlight stays where it was, nothing restarts. On Android 13+ this is the same setting as Android's.
- **Android 13 and later:** Android Settings › Apps › Tandem › Language works too, and so does
  `adb shell cmd locale set-app-locales io.github.zahakj.tandem --locales ja` (`ar`, `zh-CN`, `fr`, `en`; an empty
  `--locales ""` follows the system). `adb shell cmd locale get-app-locales io.github.zahakj.tandem` shows it.
- **Android 11 and 12:** only Tandem's own row. Tandem keeps the choice itself and sets the process's default locale,
  so dates and numbers follow too.
- **Pseudo-locales (debug builds):** `--locales en-XA` shows every translated string accented and a third longer,
  so plain English left on screen is a string not extracted yet, and anything cut off needs room. `--locales ar-XB`
  shows the text mirrored right to left (the layout stays left to right until the RTL pass).
- What to look at: Start, the quick settings (hold Start) and the side panel (R2) in each language; text in the
  Settings › Look & feel › Language picker cards (each greeting in its own script); a language change while a panel is
  open; Japanese and Chinese characters drawn with their own regional shapes (骨, 直 differ); nothing as empty boxes.

## For translators

1. Copy `res/values/strings_<area>.xml` to `res/values-<lang>/strings_<area>.xml` (`values-ar`, `values-ja`,
   `values-zh-rCN`, `values-fr`). Keep the file name.
2. Translate the text between the tags. Keep every key, every `%1$s`/`%1$d` (you may move them in the sentence), and
   the escapes above. Delete strings you don't translate yet: they show in English.
3. Plurals: write the quantities your language needs (table above), each a full phrase.
4. Keep labels about as short as the English: they sit on one line in small panels.
5. Run `./gradlew testDebugUnitTest`: `StringResourcesTest` names any key, argument or plural problem, and prints
   how much is translated.

The tone is Tandem's: plain, friendly, short. Pip is the mascot's name; leave it.

## Right to left: notes for the RTL pass

Until `Lang.RTL_READY` is true (`Tx.kt`), every screen lays out left to right in every language, and
`android:supportsRtl` stays `false` in the manifest. Arabic text itself already runs right to left inside each line.
The pass then: works through this list, sets `RTL_READY = true` and `supportsRtl="true"`, and checks on the handheld
in `ar` and `ar-XB`. Compose mirrors `Row`, `padding(start/end)`, `Alignment.Start/End`, `Arrangement.Start` and
`RoundedCornerShape(topStart…)` by itself; what follows doesn't mirror and must be looked at:

- **D-pad Left / Right** handlers (Left must mean "towards the start", which is right in RTL, wherever the thing
  they move is mirrored): `MainActivity` (Pip's glance, sounds), `ui/Settings.kt` (`SettingRowCard`: `onLeft`/
  `onRight` for segmented choices and sliders), `ui/QuickPanel.kt` (`LevelCard` brightness and volume, whose −/+ ends
  and fill do mirror), `ui/Focus.kt`, `ui/IconGrid.kt`, `ui/Orbit.kt`, `ui/SpotlightHome.kt`, `ui/Bookshelf.kt`,
  `ui/Overlays.kt`, `ui/Stats.kt`, `ui/PlaySessions.kt`, `ui/Widgets.kt`, `ui/GalleryApp.kt`, `ui/ManualReader.kt`
  (page turns: in RTL the next page is to the left), `ui/SketchPad.kt`, `ui/ControllerTestApp.kt` (physical buttons:
  never mirror), `ui/Touch.kt`. Add one helper (Key and LayoutDirection to a −1/+1 step) and use it everywhere.
- **Custom layouts placing with `place()`** (absolute, never mirrored; `placeRelative()` mirrors):
  `ui/Choices.kt` (`SegmentTrack`: options and the sliding pill are always left to right, so its D-pad is consistent
  today; mirror both together), `ui/Settings.kt`, `ui/StartPanel.kt`, `ui/Appearance.kt`, `ui/App.kt`,
  `ui/AppWidgets.kt`, `ui/Bookshelf.kt`, `ui/CartCase.kt`, `ui/Edges.kt`, `ui/GridDrag.kt`, `ui/GridSpots.kt`,
  `ui/GridWidgets.kt`, `ui/LaunchOverlay.kt`, `ui/LogoFx.kt`, `ui/Orbit.kt`, `ui/ScrapeScreen.kt`,
  `ui/SpotlightHome.kt`, `ui/Switcher.kt`, `ui/SystemPage.kt`, `ui/Video.kt`. Decide per layout: a grid of tiles
  probably mirrors; the orbit's arc and spotlight rail have a geometry of their own (`OrbitLayout`,
  `SpotlightHomeLayout`, both tested) that needs a mirrored mode, not a flip.
- **Slide-ins and `translationX`**: the Start panel comes from the left (`ui/StartPanel.kt`: `translationX` from
  `-width`, `RoundedCornerShape(topEnd, bottomEnd)` mirrors but the translation doesn't) and its pages slide
  sideways (`side`); the side panel from the right (`SidePanel` in `ui/Overlays.kt`, `Scrim(side = true)`); `slideInHorizontally` in
  `ui/ActionMenu.kt`, `ui/App.kt`, `ui/Welcome.kt`; `translationX` in `ui/ActionMenu.kt`, `ui/AppDetails.kt`,
  `ui/CompanionScreen.kt`, `ui/CoverWall.kt`, `ui/Flair.kt`, `ui/HeroArt.kt`, `ui/Layouts.kt`, `ui/LogoFx.kt`,
  `ui/PanelMorph.kt`, `ui/ScreensaverUi.kt`, `ui/SecondaryHomeScreen.kt`, `ui/Spotlight.kt`, `ui/SwapFx.kt`. In RTL
  Start should open from the right and the side panel from the left (Android does this with its own drawers).
- **Drawings** (Canvas, `drawBehind`, `drawWithCache`, about 40 files): offsets from the left, fills that grow
  rightwards (`ValueTrack` in `ui/Choices.kt`, `LevelCard`'s bar uses `fillMaxWidth(fraction)` and mirrors already),
  `Brush.horizontalGradient` (doesn't mirror: `ui/ActionMenu.kt`, `ui/Appearance.kt`, `ui/Bookshelf.kt`,
  `ui/Edges.kt` (`fadingEdges` is symmetric, fine), `ui/HeroArt.kt`, `ui/Orbit.kt`, `ui/ScrapeScreen.kt`,
  `ui/SpotlightHome.kt`, `ui/StartPanel.kt` (row glow fades from the icon side), `ui/SystemIcons.kt`,
  `ui/SystemPage.kt`, `ui/WidgetKinds.kt`). Icons and glyphs (`ui/Glyphs.kt`, `ui/TandemIcons.kt`, QuickPanel's)
  are pictures: only arrows (`Glyph.Back`, chevrons, play) should mirror; a gamepad, a clock or a speaker shouldn't.
- **Arrows in text** need nothing: `‹ › « »` and brackets are mirrored by the text engine in a right-to-left
  paragraph, so `Settings › Look & feel` reads correctly in Arabic. `▸ ◂ ▶ ◀ → ←` are **not** mirrored: StartPanel's
  "▸" row marker is drawn as "›" (fine); search for the others when converting.
- **Horizontal lists** (`LazyRow`, `horizontalScroll`) mirror their order and scroll start in RTL: check the first
  item is where the highlight lands (`ui/HomeExtras.kt`, `ui/HomeMode.kt`, `ui/IconGrid.kt`, `ui/Layouts.kt`,
  `ui/Screens.kt`, `ui/SidePanel.kt` status chips, `ui/Stats.kt`, `ui/Switcher.kt`, `ui/ThemeGallery.kt`, …).
- **Numbers**: Arabic formats with Arabic-Indic digits (٣) through `Tx` and `Lang.locale`; text built with `"$n"`
  uses Latin digits. Pick one for Tandem (the handhelds' own Arabic UI uses Arabic-Indic) and make counts, clocks and
  percentages agree.
- **Search keyboards** (`ui/SettingsSearch.kt`, `ui/TextEntry.kt`, `ui/Overlays.kt` X search) type A–Z only:
  Japanese, Chinese and Arabic need Android's keyboard or a script-aware one, outside this pass's layout work.

## Who extracts what

Five areas, each with its own files and its own `strings_<area>.xml`, so five agents can work at once without
touching the same file. Counts are rough (capitalised literals per file; a help text split over several lines
counts once per line), about 4,000 in all with the lowercase ones. Shared helpers belong to the area of the file
they're in. If you find text in a file not listed, it belongs to the area of the files around it; say so in your
report.

**Done (the pilot):** `ui/StartPanel.kt`, `ui/QuickPanel.kt`, `ui/SidePanel.kt`, `ui/LanguageSettings.kt`
(`strings_panels.xml`, `strings_common.xml`).

### A. Settings, look and sound: `strings_settings.xml`, `strings_look.xml`, `strings_sound.xml` (≈ 1,000)

First the Settings frame (it decides how every row looks): `ui/SettingsCatalog.kt` (354, mostly plan keys that stay
English; the shown group and page titles, blurbs and summaries move), `ui/Settings.kt` (57: row kinds' A labels,
the frame, About and Library rows), `ui/SettingsSearch.kt` (42), `ui/SettingsHome.kt` (16), `ui/Choices.kt` (6),
`ui/Showcase.kt` (17). Then: `ui/Appearance.kt` (189), `ui/Theme.kt` (61: theme names and taglines),
`ui/ThemeGallery.kt` (4), `ui/ThemePacks.kt` (9), `ui/ThemePacksScreen.kt` (38), `ui/PackImport.kt` (7),
`ui/Flair.kt` (19), `ui/Presets.kt` (32), `ui/Welcome.kt` (45), `ui/StartFlow.kt` (3), `ui/Perf.kt` (7),
`ui/SoundSettings.kt` (38), `ui/Sound.kt` (5), `audio/Sfx.kt` (16: sound pack names), `audio/Calm.kt` (16),
`audio/Meadow.kt` (82), `audio/Tracks.kt`, `audio/Song.kt` (song titles: names, leave them; only labels around them).

### B. Home, grid and menus: `strings_home.xml`, `strings_grid.xml`, `strings_menus.xml` (≈ 1,000)

`ui/IconGrid.kt` (154), `ui/GridSpots.kt` (37), `ui/GridSelect.kt` (36), `ui/GridPickers.kt` (19),
`ui/GridDrag.kt` (7), `ui/FolderDesign.kt` (70), `ui/SmartFolderForm.kt` (80), `ui/SmartFolders.kt` (11),
`data/SmartRule.kt` (10), `data/GridTree.kt` (6), `data/IconGridStore.kt` (5), `ui/HomeMode.kt` (87),
`ui/Layouts.kt` (31), `ui/LayoutPrefs.kt` (13), `ui/Screens.kt` (56), `ui/BrowseBar.kt`, `ui/Spotlight.kt` (20),
`ui/SpotlightHome.kt` (32), `ui/Orbit.kt` (17), `ui/Bookshelf.kt` (3), `ui/HomeExtras.kt` (7), `ui/HeroArt.kt` (11),
`ui/Switcher.kt` (26), `ui/QuickFind.kt` (4), `ui/QuickJump.kt` (5), `ui/SystemFolders.kt` (1),
`ui/Overlays.kt` (98: Y game menu, view menu, X search, `Confirm`, `formatPlayTime`), `ui/ActionMenu.kt` (5),
`ui/AppActionMenu.kt` (52), `ui/HoldConfirm.kt` (3), `ui/App.kt` (34: setup screen, top bar), `ui/TextEntry.kt`
(10), `Collections.kt` (8: collection names), `Selection.kt` (1; `plural()` goes last, once nothing uses it).

### C. Library, art and scraping: `strings_library.xml`, `strings_scrape.xml`, `strings_data.xml` (≈ 800)

`ui/ScrapeScreen.kt` (115), `ui/ArtSetup.kt` (85), `data/ScrapeConfig.kt` (53), `data/Scraper.kt` (23),
`data/ScrapeSources.kt` (20), `data/ScrapeApis.kt` (16), `data/SourceTests.kt` (25), `data/LaunchBoxDb.kt` (6),
`ui/SystemsScreen.kt` (55), `ui/SystemPage.kt` (38), `ui/SystemDetails.kt`, `ui/SystemMakers.kt` (25),
`ui/GameFoldersScreen.kt` (48), `ui/GameEditor.kt` (46), `ui/GameExtrasEditor.kt` (55), `ui/DeleteGame.kt` (32),
`GameDelete.kt` (7), `ui/AppDetails.kt` (25), `ui/AppsScreen.kt` (11), `ui/CocoonSetup.kt` (17),
`ui/BackupScreen.kt` (39), `data/Backup.kt` (14), `data/GameStatus.kt` (12), `data/GameExtras.kt` (7),
`data/Store.kt` (6: `SortOrder` and friends), `data/AppPrefs.kt` (3), `data/Importer.kt` (1), `HiddenItems.kt` (6),
`GameFinder.kt` (10), `LibraryRoots.kt` (3), `AndroidApps.kt` (3), `Library.kt`, `Manuals.kt`.
Not UI, leave: `data/ScrapePlatforms.kt`, `data/LaunchBox.kt`, `data/Packaging.kt` (platform names and database
field names).

### D. Play, screens, controls and accounts: `strings_play.xml`, `strings_screens.xml`, `strings_controls.xml`, `strings_accounts.xml` (≈ 700)

`ui/BottomScreen.kt` (36), `ui/CompanionScreen.kt` (26), `ui/SecondaryHomeScreen.kt` (12), `ui/ScreenRoles.kt` (4),
`ui/ScreenOffSettings.kt` (7), `ScreenOff.kt` (5), `ui/ScreensaverUi.kt` (1), `ui/LaunchOverlay.kt` (20),
`ui/CloseAsk.kt` (8), `CloseCheck.kt` (5), `ui/StatusUi.kt` (7), `ui/PerformanceSettings.kt` (31),
`Performance.kt` (9), `ui/ControllerSettings.kt` (28), `ui/Controls.kt` (14: `Prompts`, `Clock`),
`ui/RebindScreen.kt` (30), `Bindings.kt` (40: command names), `ui/DeckPanel.kt` (23), `DeckKeys.kt` (27: key
labels; Ctrl, Shift, Enter… are translated on real keyboards in French, not in Japanese, so ask), `Deck.kt`,
`ui/PadLinkUi.kt` (4), `PadLink.kt` (7), `PadMap.kt` (8), `PadPlan.kt`, `ui/PinPad.kt` (24), `PrivacyPin.kt` (11),
`Offline.kt` (3), `Immersion.kt`, `GameCompanion.kt` (8), `GameClip.kt` (6), `GameShot.kt` (5), `ClipService.kt`
(4: its notification), `ClipAudio.kt`, `AppMove.kt` (10), `Updates.kt` (23), `ui/UpdatePopup.kt` (8),
`ui/DiscordSettings.kt` (14), `DiscordPresence.kt` (3), `Achievements.kt` (42), `ui/AchievementsUi.kt` (36),
`ui/SteamUi.kt` (55), `SteamApi.kt` (4), `SteamAchievements.kt` (2), `PeanutsNative.kt`, `SaveSync.kt`,
`CompanionRestore.kt`, `SecondaryHome.kt`, `MainActivity.kt`, `Launcher.kt`, `LaunchIntents.kt`, `Running.kt`,
`RunningTasks.kt`, `FocusGuard.kt`, `Chords.kt`, `Platforms.kt`, `CocoonPlatforms.kt`, `AcnhSave.kt` (one or two
each, mostly launch problems shown in toasts).

### E. Stats, built-in apps and widgets: `strings_stats.xml`, `strings_apps.xml`, `strings_widgets.xml` (≈ 830)

`ui/Stats.kt` (56), `ui/StatsLibrary.kt` (17), `ui/Trophies.kt` (3), `data/Trophies.kt` (56: trophy names and
how to earn them), `ui/PlaySessions.kt` (25), `ui/GalleryApp.kt` (57), `ui/NotesApp.kt` (49), `ui/NoteEditor.kt`
(68), `ui/NoteText.kt` (5), `data/Notes.kt` (4), `ui/SketchPad.kt` (18), `ui/JournalApp.kt` (13),
`ui/FilesApp.kt` (18), `ui/ControllerTestApp.kt` (8), `ui/BuiltInApps.kt` (34), `ui/ManualReader.kt` (27),
`data/ManualFiles.kt` (5), `ui/CompanionNotes.kt` (33), `ui/Widgets.kt` (26), `ui/WidgetKinds.kt` (162),
`ui/WidgetEditor.kt` (40), `ui/GridWidgets.kt` (21), `ui/AppWidgets.kt` (25), `data/WidgetStore.kt` (8),
`DeviceInfo.kt` (3).

### Rules for the parallel pass

- Edit only your area's Kotlin files and your own `strings_<area>.xml` files. `strings_common.xml`, `Tx.kt`,
  `ui/Design.kt` and the tests' shared parts are frozen; append your files to `HardcodedTextTest.extracted` (a one-line
  change each, easy to merge).
- A row built in your file but placed by `SettingsPlan` is yours to extract (its label, help and value, plus `en`);
  the plan and catalog are area A's.
- A helper in your file used everywhere (`formatPlayTime`, `Prompts.label`, `Clock`) is yours; keep its signature,
  so callers in other areas don't change.
- English must not change. Where the English was built from pieces in a way a translator can't follow, rewrite it as
  one string with arguments, with the same English result.
