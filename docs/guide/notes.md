# Notes

Notes are for writing things down while you play: what a glyph means, where the key was, what's left to do. Each game gets its own notebook, and every note is a plain Markdown file in `ROMs/notes`, yours to read and edit anywhere.

## Opening notes

- **From a game**: Y › **Notes** on any game opens that game's notebook.
- **While playing**: tap **Notes** on the bottom screen (both the Game art and the Tandem companion styles). It shows the game's notebook there, by touch, while the game keeps the controller.
- **The Notes app** on the home grid lists every notebook: **General** first, then the game you're playing, then every game with notes (latest first), then notebooks of your own.

## A note

A note holds any mix of:

| Part | What it is | In the file |
| --- | --- | --- |
| Text | Paragraphs, headings, lists, quotes, code: any Markdown | As written |
| Checklist items | Tick them off as you go | `- [ ] item`, `- [x] done` |
| Glossary | A table, Symbol and Meaning to start with; each symbol can be drawn | A Markdown table, drawings as pictures in its cells |
| Sketches | A drawing on paper | A picture: `![](Name.assets/sketch-….png)` |
| Screenshots and pictures | The game's screen, or a picture from the Gallery, drawn on and cropped if you like | A picture |

New notes are named by what they are and when ("Glossary 3 Nov 14.02"); rename them any time. **+ Note**, **+ Checklist**, **+ Glossary** and **+ Sketch** each start a note of that kind, but any note can hold all of them.

## On the top screen

| Where | Button | What it does |
| --- | --- | --- |
| Notebooks | A | Opens the notebook |
|  | X | New note in General, or a new notebook |
|  | Y | Open, New note here, Rename (your own notebooks), Delete notebook (only an empty one, hold A) |
|  | Select | Search every note (titles and text); the chip at the top clears it |
| A notebook | A | Opens the note |
|  | X | New note: Note, Checklist, Glossary or Sketch (also the chips at the top) |
|  | Y | Rename, Move to another notebook, Delete (hold A) |
|  | B | Back to the notebooks |
| A note | A | Edits the highlighted part: text opens the keyboard, a checklist item ticks, a picture opens for drawing on, a glossary entry opens its cells; on the title, renames the note |
|  | X | Adds text, checklist items, a glossary entry, a sketch or a picture from the Gallery, after the highlighted part |
|  | Y | That part's options: edit, caption, move up or down, delete (Undo for 6 seconds); for a glossary also its headings, a new column, or deleting it; on the title, Delete note (hold A) |
|  | B | Back to the notebook |

Typing uses Android's keyboard. **Start** (or the keyboard's Done) keeps what you typed. **B** keeps a note's text, and leaves a name as it was. While adding checklist items, each Done asks for the next; leave one empty to stop.

A glossary entry opens its cells: **A** types in one, **Y** (or a long press) draws in it, on a square sheet. That's made for symbols: draw the glyph, type what you think it means, and fix it when you learn more.

Touch works throughout: tap to highlight, tap again to edit, long-press for the options.

## Drawing

Sketches, glossary symbols and marks on screenshots all use the same pad. A finger draws on either screen. With the controller:

| Button | What it does |
| --- | --- |
| D-pad | Moves the cursor (faster while held) |
| A | Puts the pen down, or lifts it |
| Y | Next tool: Pen, Eraser (takes whole strokes), Crop (drag out the part to keep) |
| L1 / R1 | Ink: black, red, blue, green, gold |
| L2 / R2 | Thinner or bolder |
| X / Select | Undo / Redo |
| Start or B | Saves and closes |

Tap **Discard** to leave without saving. Drawing on a screenshot or a picture keeps the original as it was: the marked copy (`…-marked.png`) replaces it in the note, and the original stays in the note's folder. Each drawing keeps its strokes beside it (`….strokes.json`), so it opens again to carry on, undo included, with the crop still adjustable (**Whole picture** takes it off).

## While playing

On the bottom screen, **Notes** shows the game's notes by touch:

- **+ Screenshot** makes a new note holding a screenshot of the game, taken at that moment; inside a note, **+ Screenshot** adds one to it. Tap the screenshot to circle the clue or crop it. This needs [Shizuku](resume.md), like the companion's Screenshot button.
- **+ Note**, **+ Checklist**, **+ Glossary** and **+ Sketch** start notes; inside a note, **+ Text**, **+ Item**, **+ Entry**, **+ Sketch** add to it.
- Tap text to edit it, a checklist item to tick it, a picture to draw on it. In a glossary, tap a symbol to draw it and a meaning to type it (hold a cell for the other). Hold any part for **Up**, **Down**, **Delete** and more.
- Tap the note's name to rename it. **‹** steps back; **‹ Game** goes back to the art or companion.

Typing brings up Android's keyboard, which needs the controller for as long as it is up; once you're done, Tandem hands the controller back to the game (with Shizuku; without it, touch the game's screen). The bottom screen doesn't dim while notes are open.

## Where they are kept

```text
ROMs/notes/
  General/
    Shopping.md
  Chants of Sennaar/
    .game                     which game this notebook belongs to
    Glyphs.md
    Glyphs.assets/            the note's pictures
      shot-2026-11-03_14-02-11.png
      symbol-2026-11-03_14-05-40.png
      symbol-2026-11-03_14-05-40.strokes.json
```

- A game's notebook is named after the game (with its system when two games share a title) and found again by its `.game` file, so you can rename the folder. A folder you made by hand with the game's name is taken over.
- Links to pictures are relative, so a notebook can be copied anywhere and still open in any Markdown app (Obsidian, Markor, a text editor).
- Renaming or moving a note takes its `.assets` folder and links with it. Deleting a note deletes its `.assets` folder too.
- Files you add or change yourself show up the next time the list is opened. Markdown Tandem has no special view for stays as you wrote it; when Tandem saves a note it only tidies the spacing between parts, checklist marks (`- [ ]`) and tables.
- Notes of hidden games stay out of the Notes app while hidden games are hidden, like the games themselves. The files are still in `ROMs/notes`, where any app with storage access can read them.

Notes aren't part of Tandem's backups (they're already files of your own), and uninstalling Tandem leaves them where they are. Journal notes are different: one line per play session or game, kept with Tandem's data. See [Privacy](privacy.md).
