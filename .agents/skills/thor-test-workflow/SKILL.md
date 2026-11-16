---
name: thor-test-workflow
description: Build, debug, and validate Tandem using its Gradle wrapper and Android SDK tools on a physical AYN Thor or Android Emulator, distinguishing emulator results from controller, dual-screen, storage and other hardware validation.
---

# Thor Test Workflow

## Activation and scope

From this repository, invoke `$thor-test-workflow` and describe the requested work. Examples:

- `Use $thor-test-workflow to build Tandem and report unit-test results; do not install it.`
- `Use $thor-test-workflow to install the debug APK on my connected Thor and validate controller navigation and both screens.`
- `Use $thor-test-workflow to investigate a crash during a RoMM download to SD card.`

The skill lives in `.agents/skills/thor-test-workflow` and supports normal automatic selection for matching project tasks. If it is absent from the current session's skill list, start a new session in this repository; the user can also explicitly provide the path to this `SKILL.md`.

Follow the repository's `AGENTS.md`. Perform only the requested phases: invoking this skill does not by itself authorize installation, configuration changes, or server testing. Reuse existing authorization; do not ask again for an installation already requested. Inspect relevant source and current features before choosing checks. Report a feature that is not implemented as unavailable, rather than inventing settings, endpoints, or results. A validation request alone does not authorize application-code changes.

Read [the progress journal](../../../docs/ROMM_PROGRESS.md) first for the current build/test history and accepted
user observations. Do not re-run accepted checks solely because the chat changed. Recheck source and run fresh
checks when code, configuration or symptoms justify it. Append meaningful new results with their evidence source;
never turn an emulator pass into a physical Thor pass.

For Windows Android Emulator work, read [the emulator reference](references/emulator.md). It covers the first-run
menu gate, ADB Start versus R2, editable token entry and the boundary between emulator and hardware validation.

Read [ADB and build commands](references/commands.md) when executing builds, installation, permissions, diagnostics, or crash investigation. Commands are PowerShell examples to adapt to verified paths and the selected device.

## Device and data safeguards

- Use the repository's existing Gradle wrapper and installed Android SDK tools. Do not introduce dependencies or replace toolchains to make a test pass.
- Run `adb devices -l`, identify the physical AYN Thor, and use `-s <serial>` for every subsequent device command. Do not select the first device blindly. If more than one target is plausible, resolve the ambiguity before device actions.
- An `unauthorized` device needs the owner's on-device debugging approval. For `offline` or absent devices, report the status and check cable, USB debugging, or the already configured wireless connection. Do not enable TCP debugging, root the device, or change system settings as a workaround.
- Without explicit approval for the exact action and its data impact, never run destructive ADB operations, factory resets, wipes, formatting, app-data clearing, application uninstalling, or device-file deletion. Never use these as automatic installation or crash-recovery fallbacks. Preserve existing ROMs, BIOS files, saves, app settings, and server content.
- Replace an installed APK only within the user's requested install/test scope. Stop on signing or downgrade conflicts; do not uninstall, clear data, force a downgrade, or grant all permissions to bypass the failure.
- Do not log or expose tokens, authorization headers, credentials, private server addresses, or private library data. Use the app's secure configuration flow for a user-supplied API token. Inspect and redact diagnostics locally before sharing them. Keep APKs and raw diagnostics out of commits.

## Build and install

1. Inspect `app/build.gradle.kts`, the manifest, and `docs/guide/building.md`; verify JDK/SDK availability. Current documented prerequisites are JDK 17, SDK platform 34, and build-tools 34.0.0; recheck if the project changes. An absent NDK omits the controller passthrough helper: report that limitation when relevant.
2. From the repository root run `.\gradlew.bat assembleDebug` and `.\gradlew.bat testDebugUnitTest` as separate commands, recording each exit code and failure independently. Do not install a stale APK after a failed build. A test compilation failure is not a completed test run.
   Compare failure identities with the journal/audit and a captured pre-change run; historical counts are not a
   permanent allowance for 23 failures. The root `AGENTS.md` need not contain the detailed baseline.
3. Use the generated APK and output metadata to verify the package, variant, version, and launch activity. Current defaults are `app/build/outputs/apk/debug/app-debug.apk`, `io.github.zahakj.tandem`, and `.MainActivity`; do not assume they remain unchanged.
4. When installation is requested, inspect the existing package, then use a serial-targeted `adb install -r`. This attempts to retain app data; it can fail when debug and release signing certificates differ. Stop and explain the conflict instead of erasing the installed app. Launch only after a successful install.

## Physical validation

Record expected behavior and observed results for each relevant row. Mark each as PASS, FAIL, BLOCKED, or NOT RUN; use N/A only with an applicability reason. Automated evidence cannot substitute for physical observations. Ask the user to perform required controller, touchscreen, hinge, or SD-card actions when direct observation is unavailable; leave results pending until evidence arrives.

| Area | Checks and expected behavior |
| --- | --- |
| Launch and permissions | Existing data survives an update. Inspect declared and granted runtime permissions, special access, and SAF grants separately. Follow Android's normal prompts; check denied-access behavior using a test setup without silently revoking existing access. No crash or grant-all workaround. |
| Controller navigation | Use physical D-pad, sticks, A/B, shoulders/triggers, and configured menu/search/swap actions across the library, settings, implemented RoMM views, dialogs, and errors. Verify visible focus, hold/repeat, back navigation, no duplicate activation or focus traps, and focus recovery after touch. Use Tandem's controller test UI if available. Injected ADB keys are only a supplemental smoke test. |
| Two screens | Confirm the actual display IDs, sizes, and roles. Check library/details synchronization, configured screen swap, single-screen fallback, overlays, permission dialogs, and keyboard placement. Controller input must reach the active library in both layouts; touching the lower screen must preserve intended controller focus. Check background/return, lid close/open and sleep/wake with user assistance, and emulator launch/return if in scope. Do not hardcode display ID 1 or assume a screenshot proves both screens work. |
| RoMM | With integration disabled or unconfigured, local Tandem remains usable. For implemented features, check configured connection, library loading, pagination if supported, download, useful errors, and responsive UI. Exercise missing/invalid token, authorization rejection, timeout, disconnection, and retry using mocks/test configuration first. Do not change a working user's token merely to provoke a failure. |
| Internal storage | Use an isolated test folder and synthetic or openly licensed fixture. Verify selection, free space, read/write access through the app, download completion, expected size/hash when supplied by a trusted source, and reopening/rescanning after relaunch. Check that existing files are not overwritten unexpectedly. |
| SD card | Discover the actual mounted removable volume and select an allowed test folder through the existing storage flow. Verify permission and persisted SAF access where used, download/readback, emulator URI handoff if applicable, and app relaunch. ADB-shell access does not prove app access. Report absent/read-only media; do not format or fill a real card to force a test. |
| Download recovery | Check cancellation, connectivity loss, app interruption when authorized, and unavailable/full storage with controlled fixtures. Partial/temp data must not appear as a completed ROM; retry/resume must follow the implemented behavior and validate before finalization. Check safe handling of existing destination names. No uncontrolled storage-filling or physical SD removal during writes. |
| Crash and stability | Reproduce with a bounded attempt, collect timestamped Logcat and crash evidence, distinguish crash, ANR, native failure, and process eviction, and verify recovery without clearing data. Stop repeated reproduction if it risks saves, downloads, or user state. |

## RoMM connectivity specifics

Target RoMM **5.4.1**. Before making requests, verify the actual operation against its documentation or OpenAPI; do not guess a health endpoint, URL path, schema, or authentication header. Prefer mocks and test data to production. Never modify or delete server-side ROMs without explicit approval.

Use the app on the Thor to validate its configured HTTPS or Tailscale path. Check DNS, TLS trust, authentication, and application responses separately. A host-PC request, ping, or USB ADB connection does not establish app connectivity. For a PC-hosted mock, verify a route reachable from the device rather than assuming device `localhost` points to the PC.

For Tailscale, confirm that the Thor's existing VPN connection and the server route/DNS name work; do not change VPN or server settings without authorization. Do not bypass certificate checks or enable cleartext to mask TLS failures. Avoid raw HTTP diagnostics that could expose the supplied token.

## Evidence and reporting

Keep local artifacts under ignored `app/build/thor-test/<run-id>/`. Use bounded, timestamped logs and record the APK hash, commit/worktree state, tool versions, device model/Android version, build variant, and relevant configuration without secrets. Device identifiers and private paths should be redacted in shared reports.

Report:

- Build and unit-test commands, exit codes, result counts, failure identities, report locations, and baseline comparison confidence.
- APK path/hash, installation and launch outcome, and whether device data was preserved; mark phases not requested or blocked accurately.
- Validation results with steps, expected/actual behavior, observations for both screens, physical controller evidence, RoMM transport, storage target, and permission state.
- For failures: the first actionable build cause or crash stack, reproduction steps and timestamps, affected source locations, relevant redacted evidence, and a concrete next step.
- Files changed, unresolved risks, and tests not run with reasons. Do not claim a build, hardware check, or fix passed without evidence.
