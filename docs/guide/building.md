# Building from source

Build the Tandem APK with Nix or with a plain JDK and Android SDK, and install it on a device.

## What you need

- **JDK 17**
- **Android SDK** with platform 34 and build-tools 34.0.0
- Git

Gradle itself comes with the repository (`./gradlew`), and the first build downloads the rest.

## With Nix or NixOS

The repository's `shell.nix` provides JDK 17, the Android SDK and `adb`:

```sh
git clone https://github.com/ZahakJ/tandem && cd tandem
nix-shell --run './gradlew assembleDebug'
```

The shell also points Gradle at the SDK's own `aapt2`, because the one Gradle downloads is a generic Linux binary that doesn't run on NixOS.

## With a plain JDK and SDK

Install JDK 17 and the Android SDK (Android Studio installs both). Then tell Gradle where the SDK is, either with an environment variable:

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
```

or with a `local.properties` file in the repository root (it is ignored by git):

```properties
sdk.dir=/home/you/Android/Sdk
```

Make sure the right SDK packages are installed:

```sh
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-34" "build-tools;34.0.0"
```

and build:

```sh
./gradlew assembleDebug
```

## Where the APK goes

| Command | Output |
| --- | --- |
| `./gradlew assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew assembleRelease` | `app/build/outputs/apk/release/app-release.apk` (R8 and baseline profile; signed with the debug key unless a `keystore.properties` is present) |

A debug build is signed with your computer's debug key; official releases are signed with the project's release key. Both use the same app id, so Android won't install one over the other: switching between them means uninstalling first. Uninstalling deletes Tandem's data, so back up first (**Settings › Data & about › Back up**).

## Installing on a device

With USB debugging (or wireless debugging) on:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`./gradlew installDebug` builds and installs in one step. To see what Tandem logs:

```sh
adb logcat -s Tandem
```

## Continuous integration

Every push and pull request is built by GitHub Actions ([`ci.yml`](../../.github/workflows/ci.yml)), and the debug APK is attached to the run as an artifact, so you can try a pull request without building it yourself. Releases are built from tags by [`release.yml`](../../.github/workflows/release.yml); see [Releasing](../RELEASING.md).

## Building this website

The website lives in `site/`, and the documentation pages are generated from the Markdown in `docs/` by a small script with no dependencies:

```sh
node scripts/build-docs.mjs
```

That writes `site/docs/`, and fails on a broken link between pages. To preview, serve `site/` with any static file server, for example `npx serve site` or `python3 -m http.server -d site`.
