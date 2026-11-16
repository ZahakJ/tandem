# Testing the RoMM connection milestone

## Automated evidence, 2026-11-16

| Check | Result |
| --- | --- |
| Untouched `./gradlew.bat assembleDebug` | Exit 0. |
| Untouched `./gradlew.bat testDebugUnitTest` | Exit 1: 624 tests, 601 passed, 23 failed. |
| Modified `./gradlew.bat assembleDebug` | Exit 0. |
| Modified `./gradlew.bat testDebugUnitTest` | Exit 1: 653 tests, 630 passed, 23 failed. |
| New RoMM suites | 29 passed: client 17, HTTPS transport 7, encryption 5. |
| Failure comparison | All 23 method identities exactly match the captured pre-change baseline. No new failing test methods. |
| APK inspection | `aapt` confirms package `io.github.zahakj.tandem.romm.dev`, label Tandem RoMM Dev, version `0.5.5-romm-dev`, original fully qualified main activity. |
| Additional `./gradlew.bat lintDebug` | Exit 1: 82 errors, 96 warnings. Kotlin metadata 2.4.0 vs lint expectation 2.0.0 diagnostics; first reported error is `RestrictedApi` in existing `GameCompanion.kt`. No lint findings point to the new RoMM Kotlin files. No pre-change lint baseline was captured, so project-wide lint regression attribution remains unverified. |

Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and
`app/build/test-results/testDebugUnitTest/TEST-*.xml`. Baseline failure records:
`app/build/romm-baseline/failures.json` (ignored local evidence). See
[repository audit](ROMM_REPOSITORY_AUDIT.md) for exact baseline failure names and investigation limits.

No new dependencies or test exclusions were added. NDK is absent, so the optional native controller passthrough
helper is not in this APK. Regular controller navigation still compiles; physical behavior has not been tested.

## Build locally

Use JDK 17, the installed Android SDK platform 34/build-tools 34.0.0, and the existing wrapper. Set `ANDROID_HOME`
to your installed SDK directory and, if the shell resolves Gradle's home incorrectly, `GRADLE_USER_HOME` to your
existing user Gradle cache. These are environment settings, not application/server configuration; do not commit
machine-specific paths.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Do not commit it. Use the debug variant for coexistence with official
Tandem. The fork's release application ID has not been changed.

## Install on the AYN Thor when ready

Installation was **not performed** during this milestone. Inspect connected devices and identify the Thor before
installing; substitute your verified SDK path and serial below. Never uninstall official Tandem or clear its data
to resolve a signing conflict.

```powershell
& '<SDK>\platform-tools\adb.exe' devices -l
& '<SDK>\platform-tools\adb.exe' -s '<Thor serial>' install -r 'app/build/outputs/apk/debug/app-debug.apk'
& '<SDK>\platform-tools\adb.exe' -s '<Thor serial>' shell am start -W -n 'io.github.zahakj.tandem.romm.dev/io.github.zahakj.tandem.MainActivity'
```

The new ID has separate app-private settings and permissions. Choose an isolated synthetic game folder using
Android's normal permission/folder-selection flow before experimenting. Shared artwork and ROM folders can still
be shared between the two applications; separate app IDs do not isolate shared storage.

## Connection walkthrough

1. Open **Tandem RoMM Dev → Settings → Library & art → RoMM** with the physical controller.
2. Check it starts disabled and local browsing works without configuration.
3. Enter your final HTTPS website URL in **RoMM server URL**, without `/api`, query parameters or embedded credentials.
   A plain reverse-proxy subpath is supported. For Tailscale, connect the Thor through its existing VPN route and use
   a name/address with a certificate Android trusts; cleartext HTTP and TLS bypass are unsupported.
4. In RoMM, create a dedicated Client API Token limited to `platforms.read` for this milestone. Enter it in
   **RoMM API token**. Never put it in shell commands, screenshots, logs, Git or issue reports.
5. Choose **Enable RoMM**, then **Test RoMM connection**. The expected result is
   `Connected · RoMM 5.4.1 · <count> platforms`. This proves only that probe succeeded at that time. No remote game
   list, download or server modification is implemented.
6. While connecting, press the test row again to cancel. Wait for the pending socket operation to exit; it may take
   up to its configured timeout. Retry through the same row.
7. Restart the application normally. The token should remain saved securely, and the state should require a new
   explicit connection test. Opening settings should not make a request automatically.
8. Enable private mode and try the test: it should show offline and make no new request. Return to local browsing
   and verify it remains usable. Disable private mode through the existing normal PIN flow when finished.
9. Check **More options → Forget RoMM** removes the saved configuration and disables the feature. A changed server
   must also remove the old token and disable RoMM. Use test configuration for these checks rather than replacing
   working production credentials.

## Pending physical and deployment checks

| Check | Expected | Status |
| --- | --- | --- |
| Android Keystore save/reload/forget | Secure configuration persists locally, is removable, and errors recover through Forget RoMM. | NOT RUN |
| D-pad, A/B, touch, keyboard | Visible focus, usable URL/token entry, cancel/back and focus recovery. | NOT RUN |
| Library on top and swapped to bottom | Same settings/state on both roles, readable panels, controller input reaches active library. | NOT RUN |
| Lid close/open and sleep/return | No crash or duplicate test; preserved local library. | NOT RUN |
| Live HTTPS / Tailscale | Valid certificate, reachable website/OpenAPI, 5.4.1 info and authenticated platforms. | NOT RUN |
| Invalid token / missing scope / offline / wrong version | Safe useful error, retry available, no raw response/exception leakage. | Mock tests PASS; device NOT RUN |
| Disabled/private mode local use | No new RoMM network traffic; local browsing/launch unaffected. | Static/mock evidence; device NOT RUN |
| Internal/SD download and emulator launch | Deferred until downloader and library integration exist. | N/A for this milestone |

The user's live server schema was not fetched. When testing a deployment, review its OpenAPI and proxy authentication
policy locally; do not publish the unredacted schema if it contains private deployment information. A mock is preferable
for failure injection. Do not change server settings, disable download authentication, or modify server ROMs to test
this feature.
