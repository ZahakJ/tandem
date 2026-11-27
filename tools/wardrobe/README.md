# The wardrobe kit

The Python kit the Me wardrobe (hats, outfits, hairstyles, eyes, glasses, earrings, face details, beards) was drawn
with. It draws each part as SVG in the character's own 100 × 120 units, on an exact copy of the character in
`ui/AvatarArt.kt` (`base.py`), with every region that follows a user colour marked.

- `hats.py`, `tops.py`, `hair.py`, `face.py`: the parts, as first drafted (`spec_intro.md` has the conventions).
- `overrides.py`: the redraws after review (upright bunny ears, a simpler pilot cap, a hood that reads on its own, a
  graduation cap that shows on dark themes, hair that doesn't read as a hat in light colours, a told-apart bomber, a
  bolder ear cuff). The scar was dropped: it can't show at 64 px.
- `gen.py`: makes `app/src/main/java/io/github/zahakj/tandem/ui/WardrobeData.kt`. It makes the colours symbolic
  (cloth, hair, skin, iris, ink, the outfit's second colours, and what `deep` / `light` / `edge` / `lerp` make of
  them), draws every part for the six face shapes (kept once when they agree), and writes each as a small display
  list that AvatarArt reads once and keeps. It also says which kit part fills which choice number: existing choices
  keep their numbers, new ones are appended.

```sh
nix shell nixpkgs#python3 -c python3 tools/wardrobe/gen.py app/src/main/java/io/github/zahakj/tandem/ui/WardrobeData.kt
```

A new part goes at the end of its list in `gen.py` (and the part's count in `pass/Avatar.kt`); `WardrobeDataTest`
checks the two agree.

`render.py` (needs `rsvg-convert`) draws the kit's own contact sheets, without the overrides; check the app's drawing
with real renders instead.
