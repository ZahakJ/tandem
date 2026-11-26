# Themes

Tandem ships with three themes (Pastel Night, Paper and OLED Black) and reads theme packs from your ROMs folder: its own, and packs in the Cocoon theme format. Pick one in **Settings › Look & feel**; it applies instantly with a short cross-fade. Everything a theme sets (accent, corners, highlight, surface, motion, wallpapers, icon style) can be overridden there too.

## Built-in themes

Three themes are built in. All their art is original and drawn by Tandem.

| Theme | Look | Backdrop | Icon style |
| --- | --- | --- | --- |
| Pastel Night (`pastel-night`) | The default: soft night sky, candy colours | Blobs | Glossy bubble |
| Paper (`paper`) | Light, calm, daylight friendly | Blobs | Flat |
| OLED Black (`oled`) | True black, outline highlight | Plain | Flat |

Tandem used to ship 21 more (Cartridge, Neon Arcade, Cozy Farm and the rest). Their backdrops are all still there as
wallpapers (`backdrop` below, and **Look & feel › Wallpaper**), and their icon styles under **Icon style**. A saved
theme or a pack's `base` that names one of them becomes the built-in theme closest to it (`BuiltInThemes.renamed`):
light ones Paper, the darkest OLED Black, the rest Pastel Night.

A chosen accent colour is shifted towards the theme's text colour until it stands out from the background (a contrast
of 3:1, `Themes.readableOn`), so an accent picked on a dark theme still shows on Paper, and a dark one on OLED Black.

### System icons

Without a pack, every system tile shows a little original drawing of its kind of device (clamshell, landscape or brick handheld, hybrid, console, controller, disc, cartridge, computer, phone, arcade cabinet, screen, book) with the system's short name, in the theme's icon style: glossy bubble, neon outline, pixel, flat, clear plastic, chrome, LCD, sticker or carved. **Look & feel › Icon style** changes the style, or uses one pack's icons with any theme (**Icons from**). On **Auto**, a theme pack shows its own art and a built-in theme shows Tandem's drawn icons; a pack's art (a theme pack, or a Cocoon smart-folder pack in `ROMs/assets/smart_folders`) shows with a built-in theme only when you pick it in **Icons from**. **Look & feel › System icons › Art set** (Tandem's own drawn sets: Pastel Pop, Neon Arcade, Paper Cartridge, Minimal Mono; see the [guide](guide/themes.md#art-sets)) comes before any pack's system art when it is on, and a system can pick its own set or keep the pack's art in its artwork studio. A shared look carries the set ids in `look.json` (`artSet`, `systemArtSets`), never pictures. **System icons › Real art** (`Look.systemArt`) and a system's **From the web** picks use system art fetched from the web ([guide](guide/scraping.md#system-art)); they come before the art set for every system and never travel in a shared look.

## Theme packs

| Folder | What |
| --- | --- |
| `ROMs/tandem/themes/<pack>/` | Tandem packs, and every pack you import |
| `ROMs/themes/<pack>/theme.json` | Packs Cocoon downloaded (read in place, never changed) |
| `ROMs/assets/smart_folders/<pack>/`, `ROMs/assets/icon_overlays/<pack>/` | Cocoon's icon packs, found through the theme's `asset_refs` |
| `ROMs/assets/music/<pack>/`, `ROMs/assets/sounds/<pack>/` | Cocoon's music and UI sound packs, found the same way; every sound pack here is also offered on its own under **Custom sounds** |

**Look & feel › Theme packs › Manage packs** lists every pack with a live preview, its author and what it brings. Press A on a pack to:

- **Use this theme**: its colours, wallpapers, icons, heroes and logos.
- **Use its icons with my theme**: keep your theme, borrow the pack's art.
- Switch **Colours**, **Wallpaper**, **System icons**, **Heroes & logos**, **Cover frames**, **Music** and **Sounds** on or off independently (the last two only for a pack that has them; they are `PackParts.music` and `sounds`, also switched in **Sound & music › Theme pack**). With colours off the pack sits on top of the theme it builds on.
- **Remove pack** (only packs in `ROMs/tandem/themes`; Cocoon's own are left alone).

### Importing Cocoon packs

Packs you already downloaded in Cocoon show up by themselves, with their art, music and sounds: Tandem reads the theme's `asset_refs`, finds the smart folder, icon overlay, music and sound packs in `ROMs/assets/<category>/` by the `id` in their `metadata.json` (else by `name`, else by folder name), and credits the icon packs' authors.

To keep a pack (or bring one from another device), use **Import a folder** or **Import a .zip** in Manage packs and pick the pack folder, or a zip of it. Tandem copies it into `ROMs/tandem/themes/<name>/`. A zip may hold the theme folder itself or put it one or two levels down; it can also include the asset packs (`smart_folders/...`, `icon_overlays/...`) next to `theme.json`. An icon pack on its own (a `smart_folders` pack without a theme) imports too and shows up under **Icons from**.

Tandem never downloads packs and never talks to Cocoon's catalogue. Packs belong to their authors: keep their names and credits, and share them the way the author asks.

What Tandem reads from a Cocoon theme (see `docs/example-cocoon-theme.json`):

| Cocoon key | Becomes |
| --- | --- |
| `theme_mode` (`dark`, `oled`, `light`) | The base theme (Pastel Night, OLED Black or Paper) |
| `background_gradient_start` / `_end` | Background and deep background |
| `card_gradient_start` (or `tile_background`) | Card colour; the raised card is a touch lighter |
| `text_primary`, `text_secondary` | Text and muted text |
| `accent_gradient_start` / `_end` | Accent (and lavender) / sky |
| `accent_glow` | Highlight glow colour (made opaque) |
| `success`, `warning` | Mint, butter |
| `settings.icon_roundness` | Corner radius (a share of a 104 dp card) |
| `settings.hover_scale` | Focus zoom |
| `settings.icon_scale` | Grid size multiplier |
| `settings.hero_logo_scale` | Logo size on the bottom screen |
| `settings.hero_gradient_style` | Art fade (`NONE` turns it off) |
| `wallpaper_main` / `wallpaper_external` | Top / bottom screen wallpaper (picture or video) |
| `asset_refs` (`smart_folders`) | `by_platform/<id>/icon.png`, `hero.png`, `logo.png` per system; `favorites`, `most_played`, `newly_added`, `recent` icons |
| `asset_refs` (`icon_overlays`) | `<id>/overlay.png` frames over game covers (off until switched on) |
| `asset_refs` (`music`) | Every audio file in the pack (three folders deep, by path), played as the theme's music |
| `asset_refs` (`sounds`) | The pack's recorded UI sounds (its `sounds/` folder, or the pack folder itself) |

System folders are matched by Tandem's system id, its aliases, folder names and common other names (`3ds`, `megadrive`, `ps1`, `gamecube`, ...).

## Tandem pack format

A Tandem pack is a folder; everything but `theme.json` is optional. It is a superset of the Cocoon layout, so a Cocoon pack with its asset folders copied in is also a valid Tandem pack.

```
my-pack/
  theme.json
  preview.png              shown in the galleries (16:10 works best)
  top.mp4, bottom.jpg      wallpapers named in theme.json
  systems/<id>/icon.png    square system icon (1024 px is plenty)
  systems/<id>/hero.png    16:9 art for the bottom screen and the grid banner
  systems/<id>/logo.png    wide transparent logo drawn over the hero
  collections/<kind>/icon.png   favorites, most_played, newly_added, recent
  overlays/<id>/overlay.png     a frame drawn over that system's game covers
  music/*.mp3                   the theme's music, played in name order (bgm/ works too)
  sounds/navigate.ogg ...       recorded UI sounds, named as below
```

`smart_folders/.../by_platform/<id>/` and `icon_overlays/<id>/` (Cocoon's names) work in place of `systems/` and `overlays/`. `<id>` is Tandem's system id (`n3ds`, `gba`, `psx`... see `assets/platforms.json`) or any alias. PNG, WebP and JPG are read; audio can be MP3, OGG, Opus, FLAC, M4A, AAC or WAV.

`theme.json`: every key is optional; anything left out comes from the `base` theme.

```json
{
  "name": "Midnight Mango",
  "author": "you",
  "version": "1.0",
  "description": "Warm orange on deep blue",
  "base": "pastel-night",
  "mode": "dark",
  "colors": {
    "background": "#101426",
    "background_deep": "#080A16",
    "card": "#1B2140",
    "card_high": "#252C52",
    "text": "#F4F6FF",
    "muted": "#9AA3C7",
    "on_accent": "#101426",
    "accent": "#FFB347",
    "glow": "#FFB347",
    "mint": "#8EECC8",
    "peach": "#FFB347",
    "lavender": "#B8A8FF",
    "sky": "#8FD3FF",
    "butter": "#FFE49A"
  },
  "shape": {
    "corner": 18,
    "focus_scale": 1.1,
    "highlight": "glow",
    "highlight_thickness": 4
  },
  "material": "frosted",
  "motion": "smooth",
  "backdrop": "starfield",
  "overlay": "none",
  "icon_style": "neon",
  "wallpaper": { "top": "top.mp4", "bottom": "bottom.jpg" }
}
```

| Key | Values |
| --- | --- |
| `base` | `pastel-night`, `paper` or `oled` (an old theme id still works, as above) |
| `mode` | `dark`, `oled` or `light` (used when there is no `base`) |
| colours | `#RRGGBB`, `#AARRGGBB` or `rgba(r,g,b,a)`. `mint`, `peach`, `lavender`, `sky` and `butter` are the five colours Tandem uses for systems and collections; `on_accent` is text drawn on top of them |
| `shape.corner` | Card corner radius in dp (0 to 60) |
| `shape.focus_scale` | How much the highlighted card grows, 1.0 to 1.3 (large items always grow less) |
| `shape.highlight` | `glow`, `outline`, `lift` or `pulse` |
| `shape.highlight_thickness` | Ring width in dp (0 to 10; Tandem always draws at least 2) |
| `material` | `solid`, `frosted` or `outlined` |
| `motion` | `bouncy`, `smooth`, `minimal` or `off` |
| `backdrop` | `blobs`, `starfield`, `waves`, `synthwave`, `bubbles`, `petals`, `fireflies`, `pixels`, `lcd`, `cubes`, `towers`, `marquee`, `shell`, `disc`, `visor`, `road`, `shooter`, `farm`, `meadow`, `torch`, `rainbow` or `plain` |
| `overlay` | `none`, `scanlines` or `dot_matrix` |
| `icon_style` | `bubble`, `neon`, `pixel`, `flat`, `plastic`, `chrome`, `lcd`, `sticker` or `rune` (carved) |
| `wallpaper.top` / `wallpaper.bottom` | A picture (PNG, JPG, WebP) or video (MP4, WebM, MKV) in the pack folder |
| `music` | Optional list of audio files in the pack, in play order. Without it, every file in `music/` plays, by name |
| `icon_scale` | Grid size multiplier, 0.6 to 1.6 (1 by default) |
| `hero_logo_scale` | Logo width over a system's hero, as a share of it (0.2 to 1) |
| `hero_gradient` | `fade`, `strong`, `vignette` or `none` |

To publish a pack, zip the folder. Use only art you made or may share, and put your name in `author`.

## Shared looks (`.tandem`)

**Share my look** (Settings › Look & feel) writes the whole look as one `.tandem` file: a pack zip in the format above
with a `look.json` beside `theme.json`, so **Import a .zip** still installs it as a theme. **Get a look** installs it
as a pack and also applies `look.json`. The code is `data/LookFile.kt` (the format and its checks, unit tested in
`LookFileTest`) and `ui/LookShare.kt` (making one from the current look, applying one).

```
look.json                          {"format": "tandem-look", "version": 1, "name", "author", "description",
                                    "created": "YYYY-MM-DD", "app", "parts": [...], "look": {...}}
theme.json                         the theme, as above (colours, shape, motion, backdrop, wallpaper names)
preview.png                        640×360, drawn from the look for galleries
wallpapers/top.webp, bottom.webp   wallpaper pictures, re-encoded (no metadata); videos are left out
systems/<id>/icon.png, hero.webp, logo.png       the art in use (Icons from), re-encoded
collections/<kind>/icon.png, hero.webp           favorites, most_played, newly_added, recent
```

`look.look` holds only the fields of `SharedLook`, an allow-list of look settings (accent, highlight, surface, icon
style, motion, wallpaper choice as `theme`/`hero`/a backdrop id, home layout and its options, the dock's look, the
other screen's art style, a built-in sound pack and soundtrack); every value is checked against Tandem's own ids and
ranges on import, and an unknown one is ignored. Nothing else from the settings ever goes in: no theme or pack ids,
no paths, no games, play data, hidden games, PIN, accounts or keys.

Limits on import: 24 MB per file, 48 MB unpacked, 1200 files, JSON under 256 KB, pictures under 8 MB and at most
4096 × 4096, real PNG, JPEG or WebP whose kind matches the extension (read from the header, then by Android's decoder);
only the paths above, nothing with `..`, `\` or a leading `/`. A `look.json` with a newer `version` is refused with
"needs a newer Tandem".

## Wallpapers without a theme

Copy pictures or videos to `ROMs/tandem/wallpapers/`. They show up in **Look & feel › Wallpaper** for either screen, next to the animated backdrops and **Game art (blurred)**.

Videos play muted, looping and hardware decoded. The player is released whenever Tandem is not on screen, so a video wallpaper never runs while you play.

## Sounds and music

Tandem's UI sounds (eight packs: Modern, Playful, Chiptune, Bubbly, Minimal, Studio, Glass and Air) and its two
built-in soundtracks, Calm (the default: two slow, soft pieces) and Meadow Tales (six original pieces for a small
orchestra), each played through its pieces in turn, are synthesized by Tandem as it runs, so there are no sound files. Pip's voice is synthesized too: two to four soft, hummed notes with Pip's messages. Your own
music goes in `ROMs/tandem/music/` (MP3, OGG, Opus, FLAC, M4A, WAV), picked with **Sound & music › Music** (My music).
There are no system or game jingles: a game starts with the launch sound of whatever sound pack is playing.

**A theme pack's music.** A `music/` folder in the pack (`bgm/` also works), a `music` list in `theme.json`, or, for
Cocoon packs, the music asset packs its `asset_refs` name (in `ROMs/assets/music/`). While that pack is the theme and
its **Music** part is on, its music plays in order instead of the music chosen in Sound & music, and switching to
another theme goes back. Importing a pack copies its own `music/` with everything else; linked Cocoon packs stay in
`ROMs/assets`.

**Recorded UI sounds.** A `sounds/` folder in the pack, or the Cocoon sound asset pack its `asset_refs` name (in
`ROMs/assets/sounds/`). Files are named for what they play: `navigate` (or `move`), `select`, `confirm`, `back`,
`launch`, `notification`, `folder_open` / `menu_open`, `folder_close` / `menu_close`, `grid_zoom_in`,
`grid_zoom_out`, `page`, `favorite`, `error` and `saving_end` (`ui/SoundFiles.kt`). While the pack is the theme
and its **Sounds** part is on, they play; sounds a pack doesn't have come from the chosen synthesized pack.

Any recorded pack can also be used with any theme: **Sound & music › UI sounds › Custom**, then **Custom sounds**
picks among every Cocoon sound pack in `ROMs/assets/sounds` and every theme pack with sounds of its own. The Start
panel's **Sounds & music** page lists them too.
