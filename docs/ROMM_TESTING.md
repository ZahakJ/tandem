# Testing the RoMM connection milestone

Current handoff/checklist: [ROMM_PROGRESS.md](ROMM_PROGRESS.md). Historical runs below remain useful baseline evidence.

## Automated evidence, 2026-11-16

| Check | Result |
| --- | --- |
| Untouched `./gradlew.bat assembleDebug` | Exit 0. |
| Untouched `./gradlew.bat testDebugUnitTest` | Exit 1: 624 tests, 601 passed, 23 failed. |
| Modified `./gradlew.bat assembleDebug` | Exit 0. |
| After connection implementation: `./gradlew.bat testDebugUnitTest` | Exit 1: 653 tests, 630 passed, 23 failed. |
| After keyboard/paste support: `./gradlew.bat testDebugUnitTest` | Exit 1: 662 tests, 639 passed, 23 failed; all nine new editor tests passed. |
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

## User-reported emulator acceptance, 2026-11-16

The user reported these passes in the project chat after running Tandem RoMM Dev on the Windows Android Emulator:

| Check | Result and evidence limit |
| --- | --- |
| Connect to configured RoMM server | USER-REPORTED PASS; no private address or token retained in project docs. |
| Restart app and test again with saved credentials | USER-REPORTED PASS; confirms the workflow in the emulator, not Thor Keystore hardware behavior. |
| Enable private mode and try connection testing | USER-REPORTED PASS; no independent packet capture was performed. |

These checks do not need repeating merely because a new chat begins. Repeat when relevant code/configuration changes
or a new symptom warrants it. Native clipboard/typing behavior was implemented but not separately described by the
user; leave focused input validation pending. Physical Thor validation below is still NOT RUN. The transport path
(direct HTTPS versus Tailscale) and full live OpenAPI were not supplied to the agent.

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

1. Open **Tandem RoMM Dev → Start menu → Settings → Library & art → RoMM**. Use controller Start or the emulator
   instructions below; a physical controller is not required for the emulator.
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

## Entering URLs and tokens in the Windows emulator

Complete first-run **Read your games** (all-files access) and **Pick your games folder** before opening the menu.
An empty test folder on internal storage is sufficient; no ROMs are required. `LibraryRoot` only creates the main
menu/navigation after both setup conditions are satisfied.

With Tandem in the foreground, a short ADB `input keyevent 108` opens the **left Start panel**. Scroll to
**Settings → All settings** for the main Settings screen, or **Settings → Library & art** to go directly to that
category. RoMM is below Steam. `input keyevent 82` is also mapped to Start. These are controller/Android keycodes;
do not assume an ordinary PC letter or emulator function-key shortcut sends them. Use `adb devices -l` and target
the verified emulator serial with `-s`; do not assume a prior session's serial.

The **right side panel** opens with R2 (`105`) on the home screen and contains notifications/quick controls; it
does not lead to Tandem's full Settings. Holding Start for at least 550 ms opens the separate quick panel. Optional
touchable Start/Menu hints default to off and are hidden on several home layouts, so use the injected short Start
press when no menu button is visible. Close other dialogs/screensavers first if a press is intercepted.

The text-entry dialog has an editable line above the controller keyboard. Click that line to type with the PC
keyboard or paste with Ctrl+V. Its **Paste** key also inserts text from the Android clipboard at the current cursor,
replacing any selection. Windows-to-emulator clipboard sharing must be working for Windows clipboard content to
reach Android; if the Paste key inserts nothing, first check the emulator's clipboard/input settings.

Press Enter or choose **Done** to save; Escape or the existing Back/Cancel action discards the edit. Controller
navigation still starts on the first letter, and its keys edit at the shared cursor/selection. Press D-pad Down from
the editable line to return to the controller keys. Tokens are fully masked; do not paste them into ADB commands.

The change applies to the shared text-entry dialog, including both RoMM URL and token entry. Install the newly built
debug APK over the existing development app before checking this behavior. No credentials are required to test the
editor: use a synthetic value and cancel without saving it.

Nine JVM editing tests cover mixed controller/native insertion, forward/reverse selection replacement, multiline
paste normalization, deletion at the start/in the middle, and whole-code-point deletion of pasted emoji. Native
keyboard/clipboard delivery and controller focus still need interactive emulator/device validation.

## Pending physical Thor and deployment checks

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

The full live server schema was not fetched by the agent; the app's successful probe reads and checks the live
OpenAPI before its authenticated platform request. When testing further operations, review their OpenAPI and proxy authentication
policy locally; do not publish the unredacted schema if it contains private deployment information. A mock is preferable
for failure injection. Do not change server settings, disable download authentication, or modify server ROMs to test
this feature.
