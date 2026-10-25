# Screenshots

Real captures from an AYN Thor (top screen 1920×1080, bottom 1240×1080), used by
the landing page (`site/index.html`) and the README. They are WebP, resized to
1600 px wide (top screen) or 1000 px wide (bottom screen), about 1 MB in all.

| File | Screen | What it shows |
| --- | --- | --- |
| `home-top.webp` | Top | Home, icon grid layout, Nintendo 3DS highlighted (hero) |
| `home-bottom-system.webp` | Bottom | The same moment: the 3DS with its game count and play time (hero) |
| `home-bottom.webp` | Bottom | Pip's greeting, clock and Last played, with nothing highlighted |
| `game-bottom.webp` | Bottom | A running game: **● Running** badge and **A Resume** |
| `game-art-bottom.webp` | Bottom | A highlighted game's hero art and logo |
| `folder-bottom.webp` | Bottom | A highlighted folder with a fan of its covers |
| `ingame-art.webp` | Bottom | The in-game art screen while a game runs |
| `system-grid.webp` | Top | A system opened, with genre folders first |
| `folder-games.webp` | Top | A folder of games |
| `game-menu.webp` | Top | The Y menu on a game |
| `search.webp` | Top | Search with four letters typed |
| `resume.webp` | Top | The Resume card after pressing Home mid-game |
| `settings.webp` | Top | Settings › Art & accounts |
| `start-panel.webp` | Top | The Start side panel |
| `screensaver.webp` | Top | The screensaver |

## Before capturing

- Use a library of family-friendly games, and make sure nothing hidden is showing:
  private mode on and hidden games not revealed, **Show hidden games** off.
- Nothing personal on screen: no account names, notifications or personal photos.
- Only Tandem's own art: a built-in theme (these use Pastel Night) with Icon pack set to Tandem's drawn icons,
  no community theme packs, and no third-party app icons (keep app tiles, app folders and Android apps off the
  grid, and put only Tandem's own apps in the dock).

## Capturing

With the Thor connected over adb, find the two display IDs, then capture each.
Which ID is the top screen depends on whether the screens are swapped, so check
the images:

```sh
adb shell dumpsys SurfaceFlinger --display-id
adb exec-out screencap -p -d <top id>    > top.png
adb exec-out screencap -p -d <bottom id> > bottom.png
```

Then convert to WebP:

```sh
magick top.png -alpha off -resize 1600x -strip -quality 82 home-top.webp
magick bottom.png -alpha off -resize 1000x -strip -quality 82 home-bottom.webp
```

## Also used by

- **F-Droid:** copy PNG versions to `fastlane/metadata/android/en-US/images/phoneScreenshots/` as `1.png`, `2.png`, …
