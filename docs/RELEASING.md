# Releasing

How a Tandem release is cut: the signing key, the four repository secrets the release workflow needs, the steps for each version, and the F-Droid listing. This is the maintainer's runbook.

## The signing key

Every release APK must be signed with the same key, or Android refuses to install it over the previous version. Create it once, keep it safe, and never commit it (`*.keystore` and `*.jks` are ignored by git for that reason).

```sh
keytool -genkeypair -v \
  -keystore tandem-release.jks -storetype PKCS12 \
  -alias tandem -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Tandem, O=ZahakJ"
```

`keytool` asks for a password. With PKCS12 keystores the key password is the same as the keystore password; use it for both secrets below.

Back the `.jks` file and its password up somewhere other than your computer, such as a password manager. If the key is lost, existing users have to uninstall (losing their data unless they backed it up) to move to a build signed with a new one.

## Repository secrets

In the repository, **Settings › Secrets and variables › Actions › New repository secret**:

| Secret | Value |
| --- | --- |
| `TANDEM_KEYSTORE_BASE64` | The keystore, base64-encoded: `base64 -w0 tandem-release.jks` (on macOS, `base64 -i tandem-release.jks`) |
| `TANDEM_KEYSTORE_PASSWORD` | The keystore password |
| `TANDEM_KEY_ALIAS` | `tandem`, or whatever alias you chose |
| `TANDEM_KEY_PASSWORD` | The key password (the same as the keystore password for PKCS12) |

The release workflow stops with a clear error if any of them is missing. The secrets are only available to workflows on this repository, never to pull requests from forks.

## Cutting a release

1. Update `versionCode` (add one) and `versionName` in `app/build.gradle.kts`.
2. **A new minor version (x.Y.0) needs a What's new deck** (see [What's new](#whats-new) below). The unit tests
   fail until it has one (`WhatsNewTest`). A patch (x.Y.z) needs nothing: it shows its minor's deck.
3. Add a changelog for F-Droid: `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, a few plain lines, under 500 characters.
4. Tick off what shipped in `ROADMAP.md`.
5. Commit, then tag and push:

   ```sh
   git tag v0.3.0
   git push origin main v0.3.0
   ```

6. The [release workflow](../.github/workflows/release.yml) checks that the tag matches `versionName`, builds the release APK, signs it, and creates the GitHub Release with `tandem-<version>.apk`, a `SHA256SUMS` file and generated notes. If you drafted the release by hand first, it uploads the files to your draft instead.
7. Edit the release notes on GitHub to say what changed for players, in plain words. Write them as **bold part titles** (or `##` headings) with `-` points under each: the update popup turns every part into a card of its own.

To rebuild a release (for example after fixing a secret), run the workflow from the Actions tab with **Run workflow** and the tag name. It replaces the files on the existing release.

## What's new

Players see a release twice: **before** updating, in the update popup's preview (the release notes as cards, plus
the release's screenshots), and **after**, in the What's new deck on the first start of the new version.

**Every minor version (x.Y.0) has a deck** (`app/src/main/java/.../ui/whatsnew/`). `WhatsNewTest` reads `versionName`
from `app/build.gradle.kts` and fails the build when its minor version has none, so a version bump without a deck
can't ship. Bug-fix versions (x.Y.z) need nothing: they show their minor's deck, and only to someone who hasn't seen
it. To write one:

1. Pick the 6 to 8 things players will love most; group small ones. One slide each, a punchy title and one or two
   lines.
2. Give each slide a picture: preferably a live piece of Tandem drawn by its own composables with sample data (the
   0.9 deck in `Visuals09.kt` uses the art sets, the layout sketches, the L1/R1 strip, the try-on popup, a QR code,
   Pip; the 0.10 deck in `Visuals010.kt` reuses its sample covers and draws This week's reasons, the Settings tiles,
   Pip), laid out at the design size (360 × 232 dp) from the start side so it mirrors in Arabic, its parts
   arriving one after another (`Modifier.arrive(i)`) and then looping (`rememberLoop`), holding a telling still
   frame with Reduce motion. A drawing in a `Canvas` comes next; a picture file is the last resort.
3. Put its strings in `res/values/strings_whatsnew.xml` (`whatsnew_<version>_<slide>_title`, `_body`, `_short`) and
   translate them into all four other languages (`values-ar`, `values-ja`, `values-zh-rCN`, `values-fr`, per
   [TRANSLATING.md](TRANSLATING.md) and the glossaries): the test checks every language has every string.
4. Add the `Deck` to `Decks.all` in `ui/whatsnew/Decks.kt`, after the older ones.

To see it on the handheld: Settings › Play & system › **What's new in x.Y** (and search finds "what's new"), or make it
open by itself again by taking `"whatsNewSeen"` out of Tandem's `files/settings.json`.

**Screenshots for the preview (optional).** Attach pictures named `whatsnew-*.webp` (or `.png`) to the GitHub release,
for example `whatsnew-1-lift.webp`, `whatsnew-2-stage.webp`; the popup shows up to eight, in name order, big, beside
the notes. Take them on the handheld in **the public look**, never your own setup:

- the **Pastel Night** theme, with **Tandem's drawn icons** (no icon pack or art set from elsewhere);
- **only Tandem's own apps** in the dock;
- **no personal information**: no account names, notes, photos, play history you'd rather keep, or file paths with
  your name;
- **family-friendly games** only.

WebP at about 1600 px wide keeps each one well under the popup's 6 MB limit.

## Checking a release

The workflow prints the signing certificate's SHA-256 fingerprint and puts it in the release notes. It never changes as long as the key doesn't, so users and tools like Obtainium and AppVerifier can confirm an APK is genuine. To check one locally:

```sh
apksigner verify --print-certs tandem-0.3.0.apk
sha256sum -c SHA256SUMS
```

To see how a release looks to players (the Start panel row, the popup, Settings › Play & system › Updates), build a Tandem that thinks it is
older: `./gradlew assembleDebug -PupdateFrom=0.6.0`. It checks for updates as if it were 0.6.0, so the latest release
shows up as new; a debug build can't install it (the signing keys differ), so Update stops with a message.

## F-Droid

The store listing lives in `fastlane/metadata/android/en-US/`, in the layout F-Droid reads: title, short and full description, changelogs and images. F-Droid builds apps from source itself; to submit Tandem, open a merge request on [fdroiddata](https://gitlab.com/fdroid/fdroiddata) with a build recipe pointing at this repository and the release tags. F-Droid can also publish our own signed APK when its build reproduces it byte for byte, which keeps one signature for every install source.

Two things F-Droid reviewers will ask about, with the answers:

- **All files access** (`MANAGE_EXTERNAL_STORAGE`): needed to read the `ROMs` folder and artwork that other apps (emulators, scrapers) also use.
- **See all installed apps** (`QUERY_ALL_PACKAGES`): needed to find emulators, which can be any app.

Neither involves the network. Tandem also declares `INTERNET`, used only for scraping the user starts and for RetroAchievements after signing in; [docs/PRIVACY.md](PRIVACY.md) lists every service and what it receives. All of them are optional third-party services, so expect reviewers to weigh the NonFreeNet anti-feature.

## The website

The [pages workflow](../.github/workflows/pages.yml) publishes `site/` and the documentation whenever they change on `main`, once Pages is switched on (see the comment at the top of that file). Nothing needs doing per release, except adding screenshots to `site/assets/screenshots/` when the look changes.
