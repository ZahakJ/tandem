# Contributing

Small things help: a bug report, a fixed typo, one emulator entry. Most of it needs no Android experience.

## Easy first steps

- **Report a bug or share an idea:** [open an issue](https://github.com/ZahakJ/tandem/issues/new/choose). A sentence
  is enough.
- **Add an emulator:** an entry in [`app/src/main/assets/platforms.json`](app/src/main/assets/platforms.json).
  [Systems and emulators](docs/guide/platforms.md) explains the format.
- **Translate:** strings live in `app/src/main/res/values*/`. [TRANSLATING.md](docs/TRANSLATING.md) has the how-to,
  and [`docs/i18n/`](docs/i18n/) a glossary per language.
- **Fix the docs:** every page is Markdown in [`docs/`](docs/), and each page on the website has an "Edit this page"
  link.
- **Themes and icons:** the pack format is in [THEMES.md](docs/THEMES.md). Art must be your own or freely licensed.

## Build it

JDK 17 and the Android SDK (platform 34), then:

```sh
./gradlew installDebug
```

On Nix, `nix-shell --run './gradlew installDebug'`. CI builds every pull request and attaches a debug APK, so you can
also skip building and test that. More in [Building from source](docs/guide/building.md).

## Where things live

- `app/src/main/java/.../tandem/`: the app. `ui/` holds the screens, `data/` the library and settings.
- `app/src/main/assets/platforms.json`: every system and emulator.
- `app/src/main/res/values*/strings_*.xml`: all text, by area.
- `docs/guide/`: the user guide (also the website's docs). `site/`: the website.

[ARCHITECTURE.md](docs/ARCHITECTURE.md) has the full map and the conventions. The short version: everything works
with a D-pad and A/B, text is plain, and nothing goes online unless the player switched it on.

## Pull requests

- Small and focused is best. One change per PR.
- Commit messages are `area: summary`, lower case, like `grid: Down off the last row goes to the dock` or
  `docs: Plaza, the third soundtrack`. See `git log` for more.
- Add a screenshot if it changes something you can see, and update the page in `docs/` if it changes what the docs
  say.
- Not tried on a device? Say so; that's fine.

## Licence

Tandem is GPL-3.0-or-later. By contributing, you agree your contribution is released under the same licence. Only
contribute work you made yourself or that is under a compatible licence, and say so if any of it came from elsewhere.

Be kind, and see the [code of conduct](CODE_OF_CONDUCT.md). Security problems go privately, as in
[SECURITY.md](SECURITY.md).
