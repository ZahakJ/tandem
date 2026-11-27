# Tandem wardrobe: spec for the vector parts

Phase 1 drafts for Tandem Pass characters (`ui/AvatarArt.kt`, `pass/Avatar.kt`). Every item here is a flat-vector
draft in the character's own **100 × 120 unit box**, drawn by `tools/*.py` on an exact SVG copy of the current
character (`tools/base.py`), so coordinates can be ported straight into `DrawScope` calls.

## Conventions (same as AvatarArt)

- **Crown coordinates.** Hair and headwear are drawn for the default crown (head shape 0: top 16, widest 25..75 at
  y 41, eyes at y ≈ 44.9, brows ≈ 37.5, ears at x ≈ 21 / 79, mouth ≈ 59.4) and go through `Head.crown()` (translate +
  scale about (50, 16)) for the other five face shapes. Face parts (eyes, glasses, earrings, extras) use the head's
  own `eyeY`, `dx`, `mouthY`, `hw`, as AvatarArt's `eyes()` / `glasses()` do.
- **Body coordinates.** Torso `TORSO` (32.8..67.2 × 70.5..103.6) or `DRESS`; shoulders at (40 / 60, 76.5), resting
  wrists at (31.4 / 68.6, 97.2); neck 44.6..55.4 × 62..74.6; legs 99..112.6; shoes at y 113.4.
- **Outlines** are `edge()` of the fill (same hue, darker, a touch cooler) at width ≈ 1.1; **highlights** are white
  arcs at 0.2–0.25 alpha; no gradients anywhere.
- **User colours.** In the SVGs, regions that follow a user choice carry a class: `cloth` (top/hat colour, the
  existing TopColor), `hair` (HairColor), `skin`, `iris` (EyeColor), each with `-edge` / `-deep` / `-light` variants
  meaning `edge()`, `deep(f)`, `light(f)` of that colour. Anything without a class is a fixed colour (gold, white
  fur, lens tint...). The drafts are rendered in the default blue `#63A8EE`.
- **SVG files.** `svg/<category>/<id>.svg` hold the part alone, its layers as `<g id="...">` groups in draw order,
  over a faint `<g id="guide">` (the head and torso outline, delete when porting). A comment at the top restates
  the layers and colour regions.

## Draw order (where each layer goes in `character()`)

1. `back`: behind everything (long hair, hijab drape, hood back, capes, big brims behind the head)
2. arms, legs, torso (`torso` / `inside` clipped / `over_torso`), `behind_neck`, the neck, `neck` (ties, bows), hands, `front` (top parts over the hands)
3. `hanging`: hair over the body, behind the head (braids, locs)
4. `behind_head` (hats): over the body, behind the head
5. ears, head, the hair's brow shadow (from the hair's `front_d`), earrings, eyes, cheeks, extras, brows, nose, mouth, glasses
6. hair `front` (clipped to below `hair_clip_y` when a hat says so; dropped when a hat says `hides_hair: all`), then the hat's `front`

Two small engine additions the drafts rely on: a hat may clip the hair front (`hair_clip_y`, a clipRect in crown
coordinates) or hide all hair (hijab, astronaut helmet); and a top may supply its own torso path, sleeve length,
legs, and a `back` layer.

## Codec impact

One byte per part, so counts up to 255 fit. Growing `Headwear`, `Top`, `Hair`, `Eyes`, `EyeColor`, `Glasses`,
`Earrings` is safe: an older Tandem reads an unknown value as choice 0 (no hat, tee, buzz...), which is the
designed fallback. New parts (face extras, a beard slot) are appended after `Theme`; the card stays well under
`MAX_BYTES = 32`. Keep the existing choices at their current numbers and append the new ones.

## What Atelier contributed (benzene-dsk, local models only)

Atelier made concept images; the parts themselves are hand-built vectors (`tools/*.py`), because a programmer needs
exact geometry and the per-region colour classes, which no image model gives.

- **FLUX.2 klein 4B reference edits** (`atelier/probe/k_*.png`, `atelier/concepts/k_*.png`, contact sheet
  `atelier/sheet-klein-concepts.png`): our own base character (rendered from `tools/base.py`, `atelier/base_*.png`)
  was given one garment or hairstyle at a time. These stay on-model (same face, proportions, outline style) and are
  good design references. They are raster with soft shading and klein nudges the body (the tee goes boxier), so
  they are not usable as parts directly. Used: the robe's A-line flare (thobe, kaftan) follows klein's thobe.
  Worth consulting when porting: `hanbok` (klein's short yellow jeogori + white collar band + red goreum over a
  full skirt is a clean, dignified reading), `wrap_jacket` (crossed front + dark sash), `bunny_ears` (both ears
  upright reads better than our flopped ear), `pilot` (fleece-trim flaps), `cornrows` (rows ending in hanging
  braids), `hijab` (soft drape over the shoulders).
- **Z-Image-Turbo text-only sheets** (`atelier/probe/z_*.png`): clean flat icons, but uniform navy outlines (not our
  same-hue edges), repeated items, empty garments drawn as creatures, and not our proportions: ideation only.
- **Licences** (Atelier `docs/LICENSES.md`, checked 2026-10-15): FLUX.2 klein 4B and Z-Image-Turbo are Apache-2.0;
  outputs may be used commercially. No new models were downloaded. Every image keeps its prompt, seed and model in
  `atelier/*/manifest.jsonl`.
- **GPU time**: 117 s sampling for the 10-image probe (lock held about 3 min including model loads) and 291 s for
  the 28-image concept batch (about 6 min with loads), so roughly 9 minutes of GPU lock in all. The probe waited
  about 4.3 hours at Atelier's gates first (a Gradle build queue on benzene-dsk, then 194 MB of VRAM short of the
  9 GB gate while the desktop was in use); nothing was bypassed.
