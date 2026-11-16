# RoMM repository audit and dependency assessment

Audit date: 2026-11-16. Baseline commit: `a0a2bfaf35cef75f1f597c216aed7069e40d7e66`.
Development branch: `feature/romm-connection`.

## Scope and evidence

The repository inventory, build scripts, manifest, architecture/build/UI/settings/privacy documentation,
and integration-relevant source were inspected before implementation. This is a structural and extension-point
audit, not an exhaustive line-by-line security review of every feature. The supplied hosted Tandem architecture
and building URLs were unavailable to the browsing tool; their checked-in equivalents,
`docs/ARCHITECTURE.md` and `docs/guide/building.md`, were used.

The worktree initially contained untracked `AGENTS.md` and `.agents/`; these user-provided instructions were retained.
No production server address, credentials or OpenAPI document was provided. No production requests or device
installation were performed.

## Verified architecture and extension points

Paths in this table start at `app/src/main/java/io/github/zahakj/tandem/`.

| Area | Existing source | Integration consequence |
| --- | --- | --- |
| App and startup | `MainActivity.kt`, `ui/App.kt`, `ui/StartFlow.kt` | Single Android application module; shared objects and Compose state, no DI framework. Keep connection work off startup's critical path. |
| Local library | `Library.kt`, `GameFinder.kt`, `LibraryRoots.kt`, `data/ScanCache.kt` | `Game` requires a real `File`; its key is platform ID plus filename. Do not represent remote-only entries as local games. |
| Refresh | `ui/App.kt` (`LibraryScan`), `ui/ScanRunner.kt`, `FolderWatch.kt` | Scans are serialized and coalesced across both displays. Later completed downloads should request this existing scan. |
| System mapping | `Platforms.kt`, `assets/platforms.json`, `data/SystemPrefs.kt` | Existing system aliases, extensions, emulator choices and extra roots are available for explicit mapping. |
| Storage selection | `ui/GameFoldersScreen.kt`, `LibraryRoots.kt` | Existing folder picker uses `OpenDocumentTree` and persisted URI permissions, alongside direct file scanning through all-files access. Direct paths and SAF trees are not interchangeable. |
| Launching | `Launcher.kt`, `LaunchIntents.kt`, `Emulators.kt` | Reuse emulator selection, document URI grants, resume and display selection after a local scan discovers the completed file. |
| Persistence | `data/Store.kt`, `data/HomeStore.kt`, `data/SystemPrefs.kt`, `data/Backup.kt` | JSON files with in-memory Compose state and background writers; backups use an explicit file list. No database needed. |
| Networking | `data/ScrapeSources.kt` (`Net`), `Updates.kt`, `SteamAchievements.kt`, `ui/IconArt.kt` (`OfflineImages`) | Existing HTTP stack is `HttpURLConnection`; `Net` follows redirects and retains error bodies. A separate small transport is appropriate for bearer credentials. |
| Private mode | `Offline.kt` (`NetGate`), `PrivacyPin.kt`, `data/HomeStore.kt` | Every new outgoing request must check `NetGate`; also recheck before the authenticated request and while reading. |
| Settings/navigation | `ui/Settings.kt`, `ui/SettingsCatalog.kt`, `ui/SettingsHome.kt`, `ui/SettingsSearch.kt`, `ui/App.kt` | Feature-owned `SettingSection` functions are explicitly placed in `SettingsPlan`; search and focus behavior are inherited. |
| Controller/text input | `MainActivity.kt`, `Chords.kt`, `Bindings.kt`, `ui/Focus.kt`, `ui/Design.kt`, `ui/TextEntry.kt` | Reuse `SettingRow`, `FocusCard` and the secret controller keyboard; no new input routing needed. |
| Dual screens | `ui/BottomScreen.kt`, `ui/ScreenRoles.kt`, `Selection.kt`, `GameCompanion.kt`, `SecondaryHome.kt` | A `Presentation` renders the other display; library controls can move between displays. Share RoMM state between compositions; do not hardcode display IDs. |

## Verified environment and baseline

- Windows, Temurin JDK 17.0.21, existing Gradle wrapper 8.9.
- Android platform 34 and build-tools 34.0.0 installed; app min SDK 30 and target/compile SDK 34.
- The shell initially supplied neither a usable Gradle user-home nor SDK location. Supplying the existing user
  cache through `GRADLE_USER_HOME` and installed SDK through `ANDROID_HOME` resolved the failures. No toolchain
  upgrades, local configuration files or dependency additions were necessary.
- No NDK was found: the build omits the optional native controller passthrough helper. This does not disable
  ordinary D-pad/gamepad navigation. Optional Discord SDK integration was absent from this plain build.
- `./gradlew.bat assembleDebug`: **exit 0**, baseline build successful.
- `./gradlew.bat testDebugUnitTest`: **exit 1**, 624 tests, 601 passed, 23 failed before source changes.

The failing methods were captured before editing in ignored `app/build/romm-baseline/failures.json`.
They are:

| Suite | Failing methods |
| --- | --- |
| `GameDeleteTest` (9) | `deleteNeverFollowsLinks`, `m3uTakesDiscsAndTheirTracks`, `gdiTracks`, `shortcutIsJustTheFile`, `cueTakesItsTracks`, `folderGame`, `partSharedWithAnotherPlaylistStays`, `artByName`, `singleFile` |
| `GameFinderTest` (7) | `ambiguousFilesCountInsideASystemFolderDeeperDown`, `aFolderOfSystemFoldersIsReadAsEver`, `systemFoldersMayHaveHumanNames`, `theRomsFolderOnlyHasGamesInItsSystemFolders`, `oneSystemsOwnFolderPickedDirectly`, `platformsJsonFoldersComeFirstThenOtherNamesThenLooseFiles`, `aMixedFolderSortsFilesByKind` |
| `data.BackupTest` (5) | `oldSingleSlotBackupIsListedLast`, `partialFailureRestoresTheRest`, `aFailedCheckStopsOnlyItsCategory`, `onlyLookLeavesHiddenFoldersAlone`, `restoreMergesSharedFilesAndKeepsKeysAndPrivateMode` |
| `data.GridUndoTest` (1) | `stepsSurviveARestart` |
| `data.NotesTest` (1) | `attachmentsGoBesideTheNote` |

Observed failure evidence includes forward-slash expectations against Windows file paths (`GameFinderTest`),
`Couldn't write icon-grid.json` from existing atomic replacement (`BackupTest`), and a Unix absolute path interpreted
as relative on Windows (`NotesTest`). These suggest Windows portability issues; a complete diagnosis of all 23
failures is outside this connection milestone. No tests were disabled or altered to conceal them.

## Existing dependencies

Declared versions are from the checkout, rather than versions inferred from upstream documentation.

| Component | Declared version | Assessment |
| --- | --- | --- |
| Android Gradle Plugin | 8.5.2 | Reused. |
| Kotlin Android / Compose compiler / serialization plugins | 2.4.21 | Reused. Gradle 8.9 produces a future compatibility warning, not a build failure. |
| Compose BOM | 2024.09.03 | Existing UI, foundation and Material 3. |
| Activity Compose | 1.9.2 | Existing activity/result handling. |
| Lifecycle runtime KTX | 2.8.6 | Existing coroutine/lifecycle stack. Coroutines already compile in the app; no new coroutine artifact added. |
| Coil Compose / video | 2.7.0 | Keep for future remote art; authenticated artwork needs a gated, origin-aware loader. |
| Kotlin serialization JSON | 1.12.0 | Reused for OpenAPI/response parsing and encrypted configuration serialization. |
| Media3 ExoPlayer | 1.4.1 | Unrelated, unchanged. |
| Shizuku API / provider | 13.1.5 | Existing optional privileged functionality, unchanged. |
| Profile installer | 1.3.1 | Unchanged. |
| Metrics performance | 1.0.0-beta01 | Unchanged. |
| Optional AndroidX Browser | 1.8.0 | Discord builds only; unchanged. |
| JUnit / kxml2 | 4.13.2 / 2.3.0 | Existing JVM tests, reused. |

**New dependencies: none.** Android Keystore, AES-GCM, `AtomicFile`, HTTPS connections and existing coroutines are
enough for this milestone. Retrofit/OkHttp would add client infrastructure without a current need. WorkManager is
deferred until durable background downloads require it; Room, a backend and Docker are unnecessary.

## License and development identity

The upstream README identifies GPL-3.0-or-later, with the GPL text in `LICENSE`. Nunito's OFL attribution is retained
under `app/src/main/assets/licenses/`. No license or attribution files were removed or changed; distributing a fork
still requires the applicable source and notices.

The debug build now uses `io.github.zahakj.tandem.romm.dev`, label **Tandem RoMM Dev**, version
`0.5.5-romm-dev`. Task affinities and existing providers use the variant application ID, and debug upstream
self-updating is disabled. This allows separate private app data alongside official Tandem without uninstalling it.
Shared ROM directories and user-selected shared backup/artwork paths remain shared; choose an isolated fixture root
for testing. Release identity is unchanged and must be decided before distributing a fork release.

## Outcome and remaining validation

The connection implementation, test evidence and API references are documented in
[ROMM_INTEGRATION_PLAN.md](ROMM_INTEGRATION_PLAN.md) and [ROMM_TESTING.md](ROMM_TESTING.md).
Live OpenAPI compatibility, real Android Keystore persistence, physical controller input, actual two-screen layouts
and Tailscale connectivity remain unverified on the Thor. APK compilation and JVM tests cannot establish these.
