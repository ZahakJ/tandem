# Importing from Cocoon and ES-DE

Switching to Tandem shouldn't cost you the titles, descriptions, favorites and play time you built up somewhere else. Tandem reads the `gamelist.xml` files ES-DE writes and Cocoon exports, and merges them into its own library.

## When it happens

- **Automatically, once,** the first time Tandem scans your games.
- **Whenever you ask,** with **Settings › Library › Import details**. Run it again after scraping more games in ES-DE, or after making a new Cocoon backup.

Importing never touches the files it reads, and your old frontend keeps working.

## Where Tandem looks

For each system, Tandem checks these places and uses every file it finds:

| Source | Location |
| --- | --- |
| A gamelist next to the games | `ROMs/<system folder>/gamelist.xml` |
| ES-DE | `ES-DE/gamelists/<system>/gamelist.xml` in internal storage |
| Cocoon | The newest `ROMs/backups/cocoon_backup_*` folder, in `categories/metadata/<system>/gamelist.xml` |

`<system>` is the system's `id` or one of its `art` names from [`platforms.json`](platforms.md), so for the 3DS both `n3ds` and `3ds` work.

To import from Cocoon, make a backup in Cocoon first and make sure it is saved under `ROMs/backups/`.

## What comes across

Each `<game>` entry is matched to a game in your library by **file name**, so the files need to be the same ones the other frontend saw.

| Tandem field | Read from |
| --- | --- |
| Title | Cocoon's own title, or `name` |
| Description | `desc` |
| Genres | `genre`, split on `;`, `,` and `/` |
| Developer, publisher | `developer`, `publisher` |
| Year | the first four digits of `releasedate` |
| Players, rating | `players`, `rating` |
| Favorite, hidden | `favorite`, `hidden` |
| Play count, last played | `playcount`, `lastplayed` |
| Play time | Cocoon's play time |
| Cover | Cocoon's local box art, or `image`, or `thumbnail` |
| Hero | Cocoon's local hero, or `fanart` |
| Logo | Cocoon's local logo, or `marquee` |
| Screenshot | Cocoon's gameplay or title screenshot, or `screenshot` |

Artwork paths can be relative to the gamelist (`./media/…`), start with `~/`, be absolute, or be content links into internal storage. Tandem only keeps a path if the file is actually there.

## What wins when both have a value

Tandem never overwrites what it already has:

- **Text and artwork** (titles, descriptions, genres and so on): Tandem's value stays if it has one; the import only fills gaps.
- **Favorite and hidden**: a game is a favorite if either side says so. The same goes for hidden.
- **Play count, play time and last played**: the larger value wins, so history from both places isn't double counted, and isn't lost.

## Themes from Cocoon

Theme packs you downloaded in Cocoon (in `ROMs/themes`) show up in **Settings › Look & feel › Theme packs** by themselves, with their wallpapers, system icons, heroes and logos. You can also import a pack from a folder or a `.zip`. See [Themes](themes.md) for what Tandem reads from a pack, and the [example of the format](../example-cocoon-theme.json).

**Import my Cocoon setup** (**Settings › Library › Bring your details**) goes further: from your newest Cocoon backup it brings over the home grid arrangement, folders, the theme pack and appearance values. **Undo Cocoon import** puts everything back as it was.

## Going the other way

Tandem's own data is plain JSON. **Settings › Data & about › Back up** writes it to a dated folder in `ROMs/tandem/backup/`, and any tool can read it. See [Back up and restore](library.md#back-up-and-restore).
