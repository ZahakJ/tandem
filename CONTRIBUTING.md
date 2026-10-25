# Contributing

Thanks for wanting to help with Tandem. Bug reports, emulator configs, documentation fixes, translations and code are all welcome, and you don't need to be an Android developer for most of them.

## Ways to help

- **Report a bug.** Use the [bug report form](https://github.com/ZahakJ/tandem/issues/new?template=bug_report.yml). Your device, Android version, the emulator involved and the steps to reproduce matter more than anything else.
- **Add an emulator or system.** If you got one working in your own `platforms.json`, share the entry with a [new emulator request](https://github.com/ZahakJ/tandem/issues/new?template=new_emulator.yml), or open a pull request that adds it to [`app/src/main/assets/platforms.json`](app/src/main/assets/platforms.json). [Systems and emulators](docs/guide/platforms.md) explains the format.
- **Suggest a feature.** Check the [roadmap](ROADMAP.md) first, then use the [feature request form](https://github.com/ZahakJ/tandem/issues/new?template=feature_request.yml).
- **Improve the docs.** Every documentation page is a Markdown file in [`docs/`](docs/); each page on the website has an "Edit this page" link.
- **Write code.** Pick an open issue or a roadmap item, and say in the issue that you are on it, so two people don't build the same thing.

## Getting the code running

[Building from source](docs/guide/building.md) covers the setup. In short, with JDK 17 and the Android SDK (platform 34, build-tools 34.0.0):

```sh
git clone https://github.com/ZahakJ/tandem && cd tandem
./gradlew installDebug          # or: nix-shell --run './gradlew installDebug'
adb logcat -s Tandem
```

Read [the architecture guide](docs/ARCHITECTURE.md) before your first change. It is short, and it says where everything lives.

## How the code is written

The [architecture guide's conventions](docs/ARCHITECTURE.md#conventions) are the rules. The ones that matter most:

- **Controller first.** Every screen must be fully usable with the D-pad and A/B. Build focusable things from `FocusCard`, and restore focus with `remembersFocus`.
- **Sized for the handheld.** The top screen is about 830×470 dp, the bottom about 470×540 dp. Text must be readable at arm's length.
- **Friendly and plain.** Pastel palette, rounded corners, spring animations, Pip for empty states and messages. Write UI text the way you would explain it to a friend.
- **Private by default.** No analytics or telemetry, ever. Network access only for features the user switches on, and hidden games are never sent anywhere.
- **Small, separate changes.** Prefer a new file for a new feature, and keep edits to shared files small. Settings for a feature go in the feature's own file as a `SettingSection`.
- **Comments explain why,** not what the next line does.

## Pull requests

1. Fork, and branch from `main`.
2. Make one change per pull request. A refactor riding along with a feature makes both harder to review.
3. Build with `./gradlew assembleDebug` and try the change on a device or emulator, with a controller if it touches the UI.
4. If you changed something visible, add a screenshot or a short video to the pull request.
5. If you changed behaviour that the docs describe, update the page in `docs/` in the same pull request.

CI builds every pull request and attaches the debug APK to the run, so reviewers (and you) can install it straight away.

## Licence

Tandem is licensed under the GNU General Public License, version 3 or later. By contributing, you agree that your contribution is released under the same licence. Only contribute code you wrote yourself or that is under a compatible licence, and say so in the pull request if any of it came from elsewhere.

## Conduct

Be kind, assume good intent, and keep discussion about the work. The [code of conduct](CODE_OF_CONDUCT.md) has the details.
