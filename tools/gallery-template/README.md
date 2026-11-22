# Tandem look gallery

Looks shared by [Tandem](https://github.com/ZahakJ/tandem) players. A look is a whole outfit for Tandem in one
`.tandem` file: a theme and its colours, wallpaper pictures, system and collection icons, the home layout, the
highlight and motion, and a sound pack.

**Browsing needs no account.** In Tandem, open **Settings › Look & feel › Get a look**: the gallery's looks show as
cards. Pick one to see a preview first; nothing changes until you choose **Use this look**, and **Put my old look
back** undoes it. You can also browse on the web at <https://zahakj.github.io/tandem-gallery/>.

## Share your look

1. In Tandem, open **Settings › Look & feel › Share my look**. Give it a name and, if you like, your name and a
   description.
2. Choose **Share to the gallery**. Tandem saves the look as `ROMs/tandem/looks/<name>.tandem.zip` and opens the
   **Share a look** form in the browser, with the name filled in. (GitHub doesn't accept `.tandem` attachments, so
   Tandem adds `.zip`; it is the same file.)
3. Drag the file into **The look file**, choose a license, tick the rules and submit. You need a GitHub account for
   this step only.
4. A bot checks the file within a minute or two and replies on your issue. If something is wrong, edit the issue and
   it checks again. When it passes, a pull request adds your look, and a maintainer merges it after a look at the
   pictures. Then it is in everyone's gallery, and **Show QR** in Tandem points at its page.

No handheld browser? Open the form on your phone or computer:
[Share a look](https://github.com/ZahakJ/tandem-gallery/issues/new?template=share-look.yml), and attach the file from
there.

## The rules

- **Original or properly licensed art only.** Every picture in a look must be your own work, or licensed so you may
  share it under the license you choose (CC BY 4.0, CC BY-SA 4.0 or CC0 1.0).
- **No art from commercial games**: no box art, logos, screenshots or characters. Console and system icons you drew
  yourself are fine; ones ripped from a commercial product are not.
- **Family-friendly.** Tandem is used by kids too.
- Looks that break the rules are removed. If you see one, open an issue.

## What a look holds, and what it never does

A `.tandem` file is a Tandem theme pack zip with a `look.json` beside the `theme.json`. Tandem builds it from an
allow-list: only how Tandem looks goes in. **Never** your games or game names, play time, saves, hidden games, PIN,
accounts or keys, folder paths or anything that identifies a device. Pictures are re-encoded when the file is made,
which also drops their metadata (a photo's location, say).

```
look.json                          the look's name, author, the look settings (layout, highlight, sounds…)
theme.json                         the theme: colours, shape, motion, wallpapers (docs/THEMES.md in Tandem)
preview.png                        the picture shown in galleries
wallpapers/top.webp, bottom.webp   wallpaper pictures (video wallpapers stay on the handheld)
systems/<id>/icon|hero|logo.*      system art
collections/<kind>/icon|hero.*     collection art: favorites, most_played, newly_added, recent
```

The checks, here and in Tandem: at most 24 MB per file and 48 MB unpacked, at most 1200 files, only the paths above,
JSON under 256 KB, real PNG, JPEG or WebP pictures under 8 MB and 4096 pixels a side, and a `look.json` of a version
Tandem knows. Nothing in a look is ever run.

## How the repository works

| Path | What |
| --- | --- |
| `index.json` | Every look: what Tandem reads. Written by the **Gallery index** workflow; don't edit it by hand. Its schema is `schema/index.schema.json`. |
| `looks/<id>/<id>.tandem` | The look's file. |
| `looks/<id>/preview.png` | Its preview, taken from the file. |
| `looks/<id>/meta.json` | Name, author, description, tags, license, date added, and the issue it came from. |
| `scripts/gallery.py` | The checks, the issue intake and the index, standard-library Python. |
| `.github/ISSUE_TEMPLATE/share-look.yml` | The **Share a look** form. |
| `.github/workflows/intake.yml` | Checks a submitted look and opens a pull request adding it. |
| `.github/workflows/index.yml` | Checks every look and rewrites `index.json` on `main`. |
| `index.html`, `look/index.html` | The web pages (GitHub Pages): the gallery, and one look with **Open in Tandem** (a `tandem://look?id=<id>` link). |

`index.json` lists, for each look: `id`, `name`, `author`, `description`, `version` (of its `look.json`), `preview`
and `file` (paths in this repository), `sha256` and `size` (of the `.tandem` file; Tandem refuses a download that
doesn't match), `tags`, `license` and `added` (`YYYY-MM-DD`). Tandem fetches it from
`https://raw.githubusercontent.com/ZahakJ/tandem-gallery/main/index.json`, only when someone opens **Get a look**,
and asks again only when it changed (ETag).

### Setting it up (maintainers)

1. Create the label **look** (the form adds it; the intake runs only for issues with it).
2. Settings › Actions › General: allow **Read and write permissions** and **Allow GitHub Actions to create and approve
   pull requests**, so the intake can open its pull request and the index workflow can push `index.json`.
3. Settings › Pages: deploy from the `main` branch, root folder. The look pages are then at
   `https://zahakj.github.io/tandem-gallery/look/?id=<id>`, which is what Tandem's **Show QR** points at.
4. Protect `main` so looks arrive only through reviewed pull requests; if that rule blocks direct pushes, let GitHub
   Actions bypass it, or the index workflow can't write `index.json`.

The scripts and pages here are GPL-3.0, like Tandem. Each look stays under the license its author chose, written in
its `meta.json` and in `index.json`.

To check a file by hand: `python3 scripts/gallery.py check some.tandem`.
