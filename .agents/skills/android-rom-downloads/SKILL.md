---
name: android-rom-downloads
description: Guide development, review, and mock testing of RoMM 5.4.1 ROM downloads into Tandem on the AYN Thor, covering Android storage, streaming, safe completion, and library discovery. Use for ROM download functionality rather than general API browsing or APK installation.
---

# Android ROM Downloads

Follow `AGENTS.md` and the requested development/review/test scope. Keep RoMM optional, both screens and controller navigation responsive, and local games usable. Reuse existing Kotlin, coroutine, HTTP, privacy-gate, and test patterns; justify additional dependencies. This skill does not itself authorize production downloads, deletion, or unrelated implementation.

Invoke: `Use $android-rom-downloads to design and test cancellable RoMM downloads to internal storage and SD card.`

## Inspect the existing integration points

Under `app/src/main/java/io/github/zahakj/tandem/`, inspect:

- `Library.kt`, `LibraryRoots.kt`, `Platforms.kt`, `data/SystemPrefs.kt`, and `ui/GameFoldersScreen.kt`: roots, platform mapping, scanner keys, picker, and saved URI grants.
- `ui/App.kt` (`LibraryScan`), `ui/ScanRunner.kt`, `FolderWatch.kt`, and scan-cache behavior: serialized discovery and concurrent watcher scans.
- `data/ScrapeSources.kt` (`Net`) and `Updates.kt`: transport conventions and streaming examples. Do not reuse whole-response `Net.bytes`, the updater's APK size limit, or its unknown-length percentage behavior for ROMs.

Define the download lifecycle explicitly: leaving a screen, backgrounding, and process death need distinct behavior. Do not promise persistence or recovery without supporting state.

## Verify RoMM transport

Verify endpoints, token authentication, single-file/archive responses, metadata, and optional range support against **RoMM 5.4.1** documentation/OpenAPI. Never invent fields or resume support. Use configured HTTPS or Tailscale; preserve privacy gates and TLS validation. Never hardcode personal addresses, log tokens, forward credentials across untrusted redirects, or modify/delete server-side ROMs without explicit approval. Reject HTTP/authentication errors before saving payloads.

## Select storage and map identity

- Distinguish app-private internal storage, shared internal ROM folders, and SD volumes. Use existing destination/settings flows and permitted library roots; do not hardcode volume UUIDs or expand privileges.
- Use SAF where required: persist granted read/write URI permissions, verify access on reuse, and handle picker cancellation, failed persistence, revoked permissions, read-only providers, and missing cards. Operate on content URIs through document/`ContentResolver` APIs.
- SAF grants do not imply direct-path access. Tandem currently scans filesystem roots and maps selected local-provider URIs through `LibraryRoots`; arbitrary document providers are not automatically scannable. Surface incompatibility or explicitly scope an adapter.
- Map verified RoMM platform IDs to configured Tandem platform folders/extensions; resolve ambiguity rather than deriving folders from display names. Reject traversal/separators and confine paths canonically to the selected root or documents to the granted tree. Preserve supported multi-file layouts; do not silently flatten or extract archives.
- Check remote file identity, destination collisions, and duplicates across roots separately. Tandem currently keys games by platform/file name, with earlier roots winning; equal basenames do not prove equal content. Compare verified size/hash when available. Serialize competing writes and recheck before publication. Never overwrite existing games automatically; skip, rename, or obtain an explicit replacement decision.

## Stream and report progress

Run network, storage, and hashing work off the UI thread. Copy with a bounded reusable buffer, never a whole-ROM byte array. Close streams and transport on every exit.

Count successfully written bytes using `Long`. Report determinate progress only when a positive, trustworthy total describes those same bytes; avoid overflow and account for compression and resumed-response offsets. Otherwise show indeterminate progress and bytes written. Explicit zero-length payloads still require ROM validation. Throttle UI updates; separate transfer, verification, publication, and completed states.

## Cancel, retry, and commit safely

- Check cancellation during copying/verification and cancel or close blocked transport with bounded timeouts. Propagate coroutine cancellation; close writers before cleanup and prevent late publication.
- Bound transient retries with backoff. Do not retry cancellation, authentication rejection, denied access, or full storage unchanged. Restart with a fresh owned staging target by default; resume only with verified server support, representation identity, offset, and range-response checks. Never append a full response to partial data.
- Track each job's owned temporary files/document URIs and recovery state without secrets. Keep staging invisible to concurrent scanner/watcher passes. Check space when available, including staging plus final-copy costs; estimates are advisory. Handle short writes, failed close, full storage, filesystem size limits, card loss, and process interruption.
- Close/flush and validate actual length against trustworthy expected length and an incremental checksum when available from a verified source. Reject truncation/mismatch; do not claim checksum-level integrity when no trusted check exists.
- Prefer a supported atomic move on the same filesystem without replacing existing files. Cross-volume operations and SAF rename/copy are not inherently atomic: use safely staged publication, validate the resulting document, and exclude incomplete output from discovery until committed. Surface providers that cannot meet this guarantee. Recover consistently from interruption, including publication before bookkeeping.
- Cleanup must be idempotent and restricted to exact recorded artifacts still owned by that job, after writers stop. Never recursively clean game directories, delete by glob/extension, or remove unrelated, pre-existing, or completed ROMs as retry cleanup. Retain uncertain artifacts and report them.

## Notify Tandem's scanner

After verified publication, enter the existing `LibraryScan`/`reloadLibrary` flow on its expected main thread; scanning remains off the UI thread. Honor `ScanRunner` coalescing, cache invalidation, platform rules, and root precedence. Register roots through existing settings; do not mutate library lists or scan per chunk.

Verify no partial game appears during watcher scans, then confirm the completed game appears under its platform and survives relaunch. Report a rescan failure separately from a successful download; never delete or redownload a valid file to repair discovery.

## Test and report

Use existing JUnit/coroutine patterns, generated streaming bodies, temporary test directories, fake providers, and mock HTTP transport or a local server. Prefer existing facilities to new mock dependencies. Never use production credentials/ROM collections or fill/eject a user's card.

Cover:

- Known/unknown/chunked, zero, misleading, and truncated lengths; progress above 2 GiB without large allocations.
- Cancellation during connect/read/write/verification and before commit; closed resources, no late success, bounded retries, and invalid range responses if resume exists.
- Full/read-only storage, revoked persisted grants, missing SD, failed rename/copy/close, and restart with owned partial or published artifacts.
- Traversal, ambiguous mappings, content duplicates versus name collisions, concurrent jobs, and unrelated sentinel files surviving cleanup.
- Scanner invisibility before commit, correct platform after commit, concurrent scans, cache refresh, cross-root duplicates, and rescan failure.

Mocks do not establish real SAF/SD behavior: mark physical Thor permission/storage/controller/dual-screen checks not run until observed. For implementation changes run `.\gradlew.bat testDebugUnitTest` and `.\gradlew.bat assembleDebug`; distinguish new failures from the baseline in `AGENTS.md` without disabling tests.

Report affected files, verified API assumptions, commands/results, storage/provider coverage, justified dependencies, and remaining integrity/recovery limits. Claim only behavior supported by evidence.
