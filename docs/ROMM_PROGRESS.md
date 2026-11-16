# Tandem × RoMM progress and handoff journal

Last updated: **2026-11-16**, Europe/London. Read the current snapshot first; use the linked documents for detail.
This is durable repository context, not a transcript or permission to perform the next milestone automatically.

## Current snapshot

- **Milestone 3: minimal connection implemented and accepted in the Windows Android Emulator.** The user confirmed
  successful connection, successful connection again after restarting the app, and private-mode blocking.
- **Physical AYN Thor validation is pending.** Emulator results do not establish the Thor's controls, displays,
  storage, VPN route or hardware Keystore behavior.
- **Next proposed milestone: remote library browsing**, starting with verification of the 5.4.1 ROM list,
  pagination, metadata and artwork contracts. No browsing implementation is authorized merely by this journal.
- No remote game browser, ROM downloader, save synchronization or ROM streaming has been implemented.
- Working branch at this handoff: `feature/romm-connection`; last inspected commit: `fe6dede` (`Initial RoMM integration`).
  Text-entry changes/tests and documentation were still uncommitted when this journal was created. Recheck Git;
  preserve existing changes and the user's untracked `Instructions for getting to the settings page.txt`.
- Latest automated run: **debug build passed; 662 unit tests, 639 passed, 23 failed**. All nine text-editing tests
  passed, and failure identities matched the pre-edit baseline. No tests were disabled; no app dependencies added.
- This handoff task changes documentation and skills only; it does not start remote browsing or install an APK.

## Checklist and evidence

Evidence labels: **AUTOMATED** = observed tool/test result; **USER-REPORTED** = the user's explicit observation;
**SOURCE** = inspected implementation; unchecked items remain pending. A passing count alone is not a regression comparison.

- [x] Audit architecture, dependencies, license and extension points. **SOURCE / AUTOMATED**.
- [x] Document integration design and milestone acceptance criteria. **SOURCE**.
- [x] Add optional RoMM settings, encrypted device-only credentials and explicit connection test. **SOURCE**.
- [x] Validate the server's OpenAPI version/operation before sending a bearer token to the platform endpoint. **SOURCE / AUTOMATED**.
- [x] Handle authentication, permissions, redirects, malformed responses, TLS, timeouts and network errors. **AUTOMATED mocks**.
- [x] Build a separate `Tandem RoMM Dev` debug APK. **AUTOMATED**.
- [x] Add editable text entry and Paste alongside the controller keyboard; mask secrets. **SOURCE / AUTOMATED editing tests**.
- [x] Connect from the Android Emulator to the user's configured RoMM server. **USER-REPORTED PASS, 2026-11-16**.
- [x] Restart the app and connect again with saved credentials. **USER-REPORTED PASS, 2026-11-16**.
- [x] Enable private mode and confirm connection testing stays offline. **USER-REPORTED PASS, 2026-11-16**.
- [ ] Independently exercise Ctrl+V/Paste, cursor/selection, Done/Cancel and focus handoff in the emulator. The user
  confirmed the connection workflow, but did not separately describe these input checks.
- [ ] Validate Forget RoMM, changed-server token removal and fault/retry scenarios interactively.
- [ ] Test the physical Thor: real D-pad/A/B, both display roles, swap, touch, sleep/lid/return and Keystore persistence.
- [ ] Verify live ROM list/pagination/art contracts, then implement remote platform/game browsing with offline/error states.
- [ ] Add availability indicators and explicit remote/local mapping without replacing local `Game`/library behavior.
- [ ] Implement and validate downloads to internal storage and SD/SAF, cancellation, integrity, publication and recovery.
- [ ] Discover completed downloads via the existing scanner and launch through the existing emulator configuration.

## Read only the context needed for the task

| Need | Source |
| --- | --- |
| Durable constraints and startup instructions | [AGENTS.md](../AGENTS.md) |
| Architecture and future design | [ROMM_INTEGRATION_PLAN.md](ROMM_INTEGRATION_PLAN.md) |
| Repository/dependency audit and exact original failure names | [ROMM_REPOSITORY_AUDIT.md](ROMM_REPOSITORY_AUDIT.md) |
| Emulator access, text input and Thor test procedures | [ROMM_TESTING.md](ROMM_TESTING.md) |
| RoMM development workflow | [API skill](../.agents/skills/romm-api-integration/SKILL.md) |
| Build, emulator and physical validation | [Thor test skill](../.agents/skills/thor-test-workflow/SKILL.md) |
| Future download work | [Download skill](../.agents/skills/android-rom-downloads/SKILL.md) |

## Implementation landmarks

Paths below start at `app/src/main/java/io/github/zahakj/tandem/`.

- `romm/RommClient.kt`: config/URL/token validation, transport, safe results and connection probe. Calls
  `<configured-root>/openapi.json` anonymously; accepts version `5.4.1` (optional leading `v`) and the platform GET
  operation, then calls `<configured-root>/api/platforms` with Bearer authentication. Retains a platform count only.
- `romm/RommVault.kt`: Android Keystore AES-GCM plus `AtomicFile`; encrypted config in
  `noBackupFilesDir/romm-config.enc`, outside the backup allowlist. Secure IO runs off the UI thread. Forget removes
  the record and key; changing the server clears the old token and disables RoMM.
- `ui/RommSettings.kt`, `ui/SettingsCatalog.kt`: shared settings state and Library & art placement; explicit
  enable/test/cancel/forget, no automatic startup/network synchronization.
- `ui/TextEntry.kt`, `ui/TextEntryEdits.kt`: native editable line, explicit clipboard Paste, selection-aware
  controller edits, Enter/Done, Escape/Cancel. Secret drafts are masked and are not saved into instance state.
- Reuse `Library.kt`, `LibraryRoots.kt`, `ui/ScanRunner.kt`/`LibraryScan`, `Platforms.kt`, `Launcher.kt` and
  `LaunchIntents.kt` for later discovery/launch work. Local `Game` requires a real file; never fake installed ROMs.

The connection contract was reviewed against upstream 5.4.1 source and checked at runtime by the app. The agent did
not fetch or archive the user's full live OpenAPI, credentials or server address. A successful platform request does
not verify future ROM/media/download operations. Current minimal scope is `platforms.read`; verify additional
read-only scopes against the actual operations before asking the user to replace/expand a token. Never add write
scopes merely to make browsing work, and never extract the user's token from device storage for diagnostics.

## Development and test facts

- Use the existing wrapper: `.\gradlew.bat assembleDebug` and `.\gradlew.bat testDebugUnitTest`, separately.
- Existing stack: JDK 17, SDK platform/build-tools 34/34.0.0, Gradle 8.9, AGP 8.5.2, Kotlin 2.4.21.
  Recheck the checkout before treating these as current. No framework upgrade was needed.
- In this shell, builds needed `GRADLE_USER_HOME` pointed at the existing user Gradle cache and `ANDROID_HOME`
  pointed at the installed SDK. Discover real locations; do not commit machine configuration. ADB similarly needed
  the existing Android user directory when its default resolved to a denied root location.
- Debug package: `io.github.zahakj.tandem.romm.dev`; label **Tandem RoMM Dev**; version `0.5.5-romm-dev`;
  activity `io.github.zahakj.tandem.MainActivity`. APK: `app/build/outputs/apk/debug/app-debug.apk` (ignored).
  Debug upstream self-updating is disabled. Release application ID remains upstream's; do not distribute a fork
  release without revisiting identity. Separate app IDs still share user-selected external directories.
- Original baseline: 624 tests / 601 passed / 23 failed. Connection addition: 653 / 630 / 23 (29 new tests passed).
  Text entry: **662 / 639 / 23** (nine additional tests passed). Compare identities, not just counts, on future runs.
- Baseline failing suites: `GameDeleteTest` (9), `GameFinderTest` (7), `data.BackupTest` (5), `data.GridUndoTest` (1),
  `data.NotesTest` (1). See the audit for exact methods. Evidence suggests Windows path/replacement issues in several;
  a complete diagnosis was not done. Do not suppress these tests or assume new failures belong to the baseline.
- Current XML/HTML reports live under `app/build/test-results/testDebugUnitTest/` and
  `app/build/reports/tests/testDebugUnitTest/`. Ignored snapshots were saved under `app/build/romm-baseline/` and
  `app/build/text-entry-baseline/`; clean builds/another checkout may not retain them. Durable failure names are in
  the audit. Later tasks must collect fresh reports for changed code.
- Additional lint run failed: 82 errors / 96 warnings, Kotlin metadata/lint compatibility diagnostics and existing-code
  findings. No findings pointed at the then-new RoMM Kotlin files, but no pre-change lint baseline was captured.
  This is **not** a clean lint result and the later editor was not separately lint-validated.
- No NDK was found; optional native controller passthrough was omitted. This is separate from normal D-pad support.

## Next-session checklist

1. Read this snapshot, the latest request and `AGENTS.md`; inspect branch/status before changing files.
2. Reuse accepted connection/persistence/private-mode results unless changes or new symptoms justify repeating them.
   Do not ask the user to re-enter credentials merely to reconstruct chat history.
3. For the proposed browsing milestone, verify exact 5.4.1 operations and response shapes; use sanitized schema excerpts
   or verified public sources plus mocks if live schema access is unavailable. No production mutations.
4. Explain the focused design before editing. Use a dedicated feature branch for substantial browsing work while
   preserving/reconciling existing edits. Keep downloading, saves and streaming out of a browsing-only task.
5. Add relevant tests, run the wrapper build/tests, compare failure identities and record what actually passed.
6. Update this snapshot/checklist and append a dated entry. Record scope, files, evidence, unresolved risks and the
   next proposed action. Keep secrets and private deployment data out; retain enough history without pasting logs.

## Journal

### 2026-11-16 — Entry 001: connection milestone and emulator acceptance

**Completed:** repository audit, integration architecture, optional connection settings, encrypted credentials,
version/operation validation, authenticated platform GET, safe failure states and separate debug identity. Source
areas: `romm/RommClient.kt`, `romm/RommVault.kt`, `ui/RommSettings.kt`, settings catalog, build/manifest and tests.
Documentation: integration plan, audit, testing, settings and privacy docs. GPL/attribution retained; no new app dependencies.

**Usability correction:** inspecting actual input routing identified the first-run permission/folder gate and the
difference between left Start, right side and quick panels. `108`/Start opens the left menu after setup; R2 opens
the right panel on home. The shared text-entry dialog then gained a native editable line, clipboard Paste and masked
tokens, plus `TextEntryEdits.kt` and nine unit tests. See the testing guide for exact emulator steps.

**Automated evidence:** final debug build exited 0. Unit tests exited 1: 662 total, 639 passed, the same 23 failure
identities. Nine new editing tests and 29 connection/encryption/transport tests passed. Lint remains unresolved above.

**User evidence:** after testing in the Windows Android Emulator, the user explicitly reported successful RoMM
connection, then confirmed successful restart/persistence and private-mode checks. These observations were reported
in this chat, not independently measured by the agent. Exact VPN route, packet-level blocking and separate clipboard
delivery were not reported. No physical Thor, SD card, ROM download or emulator-game launch was validated.

**Handoff:** create this journal, add its startup pointer to `AGENTS.md`, and update existing API/test/download skills
to reuse the implemented integration and distinguish emulator acceptance from physical-device evidence. Remote library
browsing is the next proposed milestone, pending a user request. Keep current uncommitted editor changes intact.

**Handoff verification:** all three updated skills passed the bundled skill validator; 22 local Markdown links and
the modified skill UI metadata were checked successfully. The API skill's leading blank line before YAML frontmatter
was removed. Validation-only PyYAML was placed under ignored `app/build/skill-validation/`; no app or global Python
dependency was changed. No Android build/tests were repeated for this documentation/skill-only task.
