# UI rules

Tandem draws on two fixed screens: the top one is about 832×468 dp (1920×1080 px at density 369), the bottom one
about 468×538 dp (1080×1240 px). Nothing scrolls the window, so every screen, panel and popup must fit, and nothing
may look cut off. The helpers live in `ui/Edges.kt`.

## Room for the highlight

A highlighted `FocusCard` reaches past its bounds: it grows by up to `MaxFocusGrowth` (14 dp of width), lifts 4 dp
(7 dp with Lift) and casts a 10 dp glow (14 dp with Lift). So:

- Leave `FocusRoom` (20 dp) above and below cards, and `FocusRoomSide` (16 dp) beside them wherever the container
  clips sideways (horizontal rows, popups, anything with `fadingEdges`).
- Put that room inside the container (`contentPadding` or inner padding), not around it.
- Never `clip()` a bar, dock or row that holds focusable cards; draw its background in its shape instead
  (`background(color, shape)`, as `DockBar` does).
- To line a row up with tiles around it, use `Modifier.bleed(FocusRoomSide)` and pad the content back in
  (see `HomeWidgetsRow`).

## Soft edges

- Every scrolling container gets `Modifier.fadingEdges(state)` (`horizontal = true` for rows). It fades content
  over `FadeEdge` (22 dp) only where more content lies beyond the edge.
- `KeepInView` scrolls the target `KeepRoom` (28 dp) clear of the top edge. Rows that snap to the top on purpose
  pass `room = 0.dp` with a shorter fade.
- D-pad focus keeps 40 dp of room around the highlighted item (`PaddedScrolling`, set at the app root).

## Popups

- `OverlayPanel` gives its content the screen minus `PanelMargin` (16 dp) on every side (`PopupRoom`). Don't add
  your own outer padding.
- Content that can outgrow the panel goes in a scrolling container with `Modifier.weight(1f, fill = false)` (or
  `PopupRoom`'s `maxHeight`), `fadingEdges`, highlight room and `KeepInView`. The Y game menu is the model.

## Text

- At least `TextInset` (12 dp) between a container edge and its text; 16–22 dp on screens.
- Every `Text` in a fixed-size place has `maxLines` and `overflow = TextOverflow.Ellipsis`.
- Long help and details ellipsize or scroll; they never push button hints off a panel.
- Pills hold one line. Text that can wrap goes in a rounded rectangle.
- Rows of buttons that can grow use `FlowRow`.
- A place whose size changes (widgets, icon grid cells) picks its layout from the room it really gets
  (`BoxWithConstraints`), drops secondary lines first, and shows a main value that must not be ellipsized (a time,
  a percentage, free space) through `ShrinkToFit` (ui/Edges.kt), which scales it down instead of cutting it. Never
  let a `Column` of lines run past a fixed height: the last line's descenders are the first thing cut.
- Widgets on the smallest grid cells (about 53 dp at five rows) may go down to a 6 dp inset (`widgetPad`).

## No dark scrim bands

Never lay a grey or black gradient band over art or behind text. Instead:

1. Fade the art into what is behind it: `fadeMask(...)`, `fadeMaskHorizontal(...)`, `heroFade()`.
2. Use the tile's own colour, deepened: `lerp(color, Palette.Night, 0.6f)`.
3. Give text on art a soft shadow: `MaterialTheme.typography.x.onArt()`.
4. Show unavailable things faded (`graphicsLayer { alpha = 0.35f }`), not covered.

Small chips over art (`darkChip()`), the full-screen dim behind a popup and the launch transition are fine.

## Panels, empty pages, settings rows

- Previews in a side panel go through `FitInside` (ui/Settings.kt), scaled to the room left.
- A list, grid or page that can be empty shows `EmptyNote` (ui/HomeHost.kt): Pip, one line, and one focusable button
  that does the obvious thing (Add one, Look again, Show hidden games, Go back), so it never looks frozen on a
  controller. `PipNote` is for loading and error notes.
- Settings rows use the row types in [SETTINGS.md](SETTINGS.md), a short label (about 28 characters) and the
  explanation in `help`. Rare options go in the group's `more`.

## Touch

Touch does what the controller does, through the same code (helpers in `ui/Touch.kt`).

- **Entries take two taps.** An entry is a thing in a list, grid, shelf or dock that shows its details when
  highlighted: games, systems, collections, apps, icon grid tiles, the Recently played strip, the Resume page,
  Gallery, Files, Up next, Journal and Stats rows. The first tap highlights it (the other screen shows it); a tap
  on the highlighted entry opens it. Mark them `FocusCard(entry = true)`; a tile that draws its own focusable box
  uses `Modifier.touchEntry()` before its `focusable()`.
- **Controls act on the first tap** and take the highlight with them: buttons, menu rows, settings rows, keys,
  chips, All apps.
- **A long press is Y.** On an entry it highlights it, then presses Y through `Buttons`, so it opens whatever Y
  opens there. It buzzes (`HapticFeedbackType.LongPress`); what opens makes its own sound. A long press never also
  counts as a tap. Other long presses: a widget opens its options (`FocusCard(onLongPress = …)`), the Resume page
  opens the game's options (its Y closes the game, which a long press must never start), a Y menu row shows its
  `MenuAction.help` as a toast when it has one, a hold control confirms, and nothing else reacts.
- **Button hints are buttons.** A tapped `ButtonHint` presses its button: A and B through
  `MainActivity.tapKey` (after the A/B and X/Y swaps), the other buttons through `Buttons.press`, a ◀▶ or ▲▼ hint
  the arm on the side that was tapped. A hint inside a `FocusCard` leaves the tap to the card; a legend that only
  explains buttons passes `tappable = false`; `onClick` overrides the press. Hits reach Android's 48 dp minimum
  touch target on their own; keep 10 dp or more between hints so the targets don't overlap.
- **Lists scroll by finger.** A drag scrolls and never taps or highlights. A scroll doesn't move the highlight; if
  the highlighted entry scrolls away, the next D-pad press brings it back (`FocusCards.rescue`).

## Confirming

One pattern everywhere (ui/HoldConfirm.kt):

- **Can't be undone** (delete a game, a folder with its subfolders, a backup, a screenshot, a file, a session or a
  theme pack; clear sessions or downloaded media; reset play history or the whole look; restore over current
  data): the control acts only when **A is held** until its ring fills (`HOLD_MS`, 0.8 s) over a rising tone.
  Letting go early cancels and the ring drains; a quick press says how in a toast. Touch: press and hold the control.
  Use `HoldButton` in a popup, `MenuAction(hold = true)` in a Y menu, `HoldMenuRow` in a popup list, or
  `Confirm.hold(...)` for a small popup that opens on the held button.
- **Needs a choice** (delete a folder: keep its contents or delete it all): a popup or menu page with the choices;
  the destructive choice is the hold control.
- **Can be undone** (hide, unhide, favourite, take off Up next, grid changes): no question. Do it, then say so with
  `Undo.offer(label) { revert }` (or `GridUndoUi.run` on the grid): a toast with Undo on Select or a tap, for 6 s.
- Plain questions that aren't about losing anything (download a database, close a running game first) stay
  `Confirm.ask`. Closing a running game keeps its press-twice prompt (X or Y twice).

## Sounds

Each interaction makes one cue from `Sounds` (ui/Sound.kt), never two:

| What | Cue | Where it comes from |
| --- | --- | --- |
| Highlight moves | `move` | `FocusCard`, `ActionRow` (on focus) |
| A on a control | `select` | `FocusCard`, `ActionRow` (dropped when a richer cue just played) |
| B | `back` | `MainActivity` |
| A popup, menu or panel opens / closes | `menuOpen` / `menuClose` | `SoundCues`, from `Nav.overlayOpen` and `Layers.count`; never call them by hand |
| On / off, hide / unhide, unfavourite | `toggle(on)` | the action |
| Favourite | `favorite` | the action |
| Hold to confirm | `holdRise`, then `confirm` | `HoldState` |
| Something saved or finished | `confirm` | the action |
| Undo | `undo` | `Undo.offer`, `GridUndoUi.undo` |
| Refused, failed | `error` | the action |
| Toast | `toast` (skipped right after a richer cue) | `SoundCues` |

## Focus after a change

- After a popup closes, a toast action, a delete or a hide, focus returns to the item it came from, or its nearest
  neighbour in the list as it was (`FocusMemory.resolve`), never the first item. Give lists stable keys (not
  indexes), and bump `Nav.focusTick` after the change.
- B closes exactly one layer: the top popup, menu page or panel, then the screen.

## Checklist for a new screen or popup

- [ ] Fits 832×468 dp (top) or 468×538 dp (bottom) with the longest title, description and emulator name, at the
      largest grid size.
- [ ] Every scrolling container has `fadingEdges` and `FocusRoom` / `FocusRoomSide` padding.
- [ ] The first, last and corner items show their whole ring and glow when highlighted.
- [ ] Scrolled to the middle, content fades at the top bar and screen edges instead of being cut.
- [ ] Popups: nothing off-screen, long content scrolls inside, the highlighted row stays in view.
- [ ] No dark band over art or behind text, in light and dark themes.
- [ ] Touch: entries are marked `entry = true`, a long press does what Y does, every hint can be tapped.
- [ ] Details screen: fits both screen shapes (swapped screens), with a game running and a scrape running; it uses
      `Modifier.clearOfNowPlaying()` and `Modifier.clearOfScrapeCard()`, not fixed offsets.
