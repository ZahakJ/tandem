
# Tandem × RoMM Development Instructions

## Project
We are modifying the open-source Tandem Android frontend to add
native RoMM integration for the AYN Thor dual-screen handheld.

Target RoMM version: 5.4.1.
Connection methods: HTTPS or Tailscale.
Authentication: dedicated user-supplied API token.

## Architecture
- Modify Tandem directly; do not create a companion app.
- Follow existing Kotlin and Android architecture.
- Reuse existing libraries and components where practical.
- Preserve dual-screen functionality and controller navigation.
- Keep RoMM integration optional and disabled until configured.
- Prefer focused, incremental changes.

## API requirements
- Verify endpoints against RoMM 5.4.1 documentation or OpenAPI.
- Never invent undocumented endpoints or response fields.
- Support network failures and authentication errors.
- Never hardcode or log API tokens.
- Do not embed my personal RoMM address in the application.
- Never modify or delete server-side ROMs without explicit approval.

## Android requirements
- Support Android internal storage and SD cards.
- Follow Android storage permissions and SAF requirements.
- Avoid blocking the UI thread.
- Support safe cancellation and recovery of downloads.
- Use temporary files and validate downloads before completion.

## Testing
- Use the project's existing Gradle wrapper.
- Build with: .\gradlew.bat assembleDebug
- Test with: .\gradlew.bat testDebugUnitTest
- Investigate new failures separately from baseline failures.
- Never disable tests just to obtain a passing result.
- Add tests for newly implemented functionality.

## Development workflow
- Inspect relevant source before modifying it.
- Use separate feature branches for substantial changes.
- Keep changes small and reviewable.
- Do not introduce new dependencies without justification.
- Maintain GPL licensing and required attribution.
- Do not commit secrets, ROMs, BIOS files or APK binaries.
- After changes, summarize affected files, tests and risks.
- Prefer mock servers and test data over production testing.
