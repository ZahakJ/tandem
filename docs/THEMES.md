# Themes

Tandem ships with 24 themes and reads theme packs from your ROMs folder: its own, and packs in the Cocoon theme format. Pick one in **Settings › Look & feel**; it applies instantly with a short cross-fade. Everything a theme sets (accent, corners, highlight, surface, motion, wallpapers, icon style) can be overridden there too.

## Built-in themes

Pastel Night is the default. The game-flavoured themes each have their own animated backdrop, card style and system icon style. All their art is original and drawn by Tandem.

| Theme | Backdrop | Icon style |
| --- | --- | --- |
| Cartridge (`crt`) | 8-bit evening: blocky hills, pixel moon, scanlines | Pixel |
| Handheld Green (`dmg`) | Dot-matrix screen with sprites bouncing about | LCD |
| Indigo Cube (`indigo-cube`) | Floating translucent purple cubes | Clear plastic |
| Neon Arcade (`neon-arcade`) | Tube signs, chasing marquee bulbs, scanlines | Neon outline |
| Pocket Atomic (`pocket-atomic`) | A clear purple shell over a glowing circuit board | Clear plastic |
| Midnight Disc (`midnight-disc`) | A spinning disc with a rainbow sheen | Chrome |
| Blue Console Boot (`blue-boot`) | Towers of blue cubes rising in the fog | Glossy bubble |
| Crimson Visor (`crimson-visor`) | A red wireframe tunnel on black | Neon outline |
| Sunset Racer (`sunset-racer`) | Sun, palms and a road rushing by | Chrome |
| Starfield Shooter (`star-shooter`) | Warp stars, a squadron and laser fire | Pixel |
| Cozy Farm (`cozy-farm`) | Rolling fields, puffy clouds, a fence | Sticker |
| Monster Catcher (`monster-catcher`) | Tall grass that rustles where something hides | Glossy bubble |
| Dungeon (`dungeon`) | Stone wall lit by flickering torches | Carved |
| Kart Rainbow (`kart-rainbow`) | A striped road winding through space | Glossy bubble |

And the calmer ones: Pastel Night (`pastel-night`), OLED Black (`oled`), Sakura (`sakura`), Mint Soda (`mint-soda`), Ocean (`ocean`), Forest (`forest`), Lavender Dream (`lavender-dream`), Paper (`paper`), Cotton Candy (`cotton-candy`) and High Contrast (`high-contrast`). The ids `sunset-arcade` and `ember` are aliases for Sunset Racer and Dungeon.

### System icons

Without a pack, every system tile shows a little original drawing of its kind of device (clamshell, landscape or brick handheld, hybrid, console, controller, disc, cartridge, computer, phone, arcade cabinet, screen, book) with the system's short name, in the theme's icon style: glossy bubble, neon outline, pixel, flat, clear plastic, chrome, LCD, sticker or carved. **Look & feel › Icons** changes the style, or uses one pack's icons with any theme (**Icons from**). On **Auto**, a theme pack shows its own art, and a built-in theme borrows the first Cocoon smart-folder pack in `ROMs/assets/smart_folders` if you have one; choose **Drawn by Tandem** to see the theme's own icon style.

## Theme packs

| Folder | What |
| --- | --- |
| `ROMs/tandem/themes/<pack>/` | Tandem packs, and every pack you import |
| `ROMs/themes/<pack>/theme.json` | Packs Cocoon downloaded (read in place, never changed) |
| `ROMs/assets/smart_folders/<pack>/`, `ROMs/assets/icon_overlays/<pack>/` | Cocoon's icon packs, found through the theme's `asset_refs` |

**Look & feel › Theme packs › Manage packs** lists every pack with a live preview, its author and what it brings. Press A on a pack to:

- **Use this theme**: its colours, wallpapers, icons, heroes and logos.
- **Use its icons with my theme**: keep your theme, borrow the pack's art.
- Switch **Colours**, **Wallpaper**, **System icons**, **Heroes & logos** and **Cover frames** on or off independently. With colours off the pack sits on top of the theme it builds on.
- **Remove pack** (only packs in `ROMs/tandem/themes`; Cocoon's own are left alone).

### Importing Cocoon packs

Packs you already downloaded in Cocoon show up by themselves, with their art: Tandem reads the theme's `asset_refs`, finds the smart folder and icon overlay packs in `ROMs/assets/` by the `id` in their `metadata.json`, and credits their authors.

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
```

`smart_folders/.../by_platform/<id>/` and `icon_overlays/<id>/` (Cocoon's names) work in place of `systems/` and `overlays/`. `<id>` is Tandem's system id (`n3ds`, `gba`, `psx`... see `assets/platforms.json`) or any alias. PNG, WebP and JPG are read.

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
| `base` | A built-in theme id (see the tables above) |
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

To publish a pack, zip the folder. Use only art you made or may share, and put your name in `author`.

## Wallpapers without a theme

Copy pictures or videos to `ROMs/tandem/wallpapers/`. They show up in **Look & feel › Wallpaper** for either screen, next to the animated backdrops and **Game art (blurred)**.

Videos play muted, looping and hardware decoded. The player is released whenever Tandem is not on screen, so a video wallpaper never runs while you play.

## Sounds and music

UI sounds and the built-in soundtracks are synthesized by Tandem, so there are no sound files. Your own music goes in
`ROMs/tandem/music/` (MP3, OGG, Opus, FLAC, M4A, WAV).
