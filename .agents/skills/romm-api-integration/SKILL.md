---
name: romm-api-integration
description: Use when developing, debugging or reviewing RoMM API integration in the Tandem Android frontend, including authentication, remote libraries, metadata, platform mapping and API error handling.
---

# RoMM API Integration Workflow

This skill supports development of native RoMM integration
for Tandem on the AYN Thor.

## Start from the handoff

Read [the progress journal](../../../docs/ROMM_PROGRESS.md) before planning. It holds current acceptance evidence,
baseline failures and the next proposed milestone; recheck Git/source before treating its snapshot as current.
Do not repeat accepted emulator setup just to reconstruct context, or treat a proposed milestone as authorization.

The existing connection implementation is in `romm/RommClient.kt`, `romm/RommVault.kt` and `ui/RommSettings.kt`
under `app/src/main/java/io/github/zahakj/tandem/`. Settings placement is in `ui/SettingsCatalog.kt`. Reuse those
components rather than creating a second client or credential store. Read
[the integration plan](../../../docs/ROMM_INTEGRATION_PLAN.md) for the verified connection contract and future design.

The current probe validates anonymous `/openapi.json`, then requests `/api/platforms` with a Bearer token and
`platforms.read`. This does not verify ROM list, pagination, artwork or download contracts or scopes. Verify each
additional operation before implementing it; do not broaden token privileges by guesswork.

`RommHttpTransport` intentionally rejects redirects and does not retain error bodies. Tandem's generic `Net`
follows redirects, so it is not an interchangeable authenticated transport. Preserve `NetGate`, strict HTTPS,
redacted failures and device-only encrypted credentials. Never retrieve a working token from emulator storage
just to reproduce a test. Emulator persistence/private-mode passes are not physical Thor evidence.

## Requirements
- Target RoMM version 5.4.1.
- Use verified RoMM API documentation and OpenAPI.
- Follow the repository's AGENTS.md.
- Reuse Tandem's existing architecture.
- Never expose credentials or use hardcoded tokens.

## Workflow

1. Inspect the relevant Tandem source files.
2. Identify existing models, repositories and UI components.
3. Verify API endpoints and response structures.
4. Explain the proposed implementation.
5. Introduce minimal changes.
6. Handle authentication and network failures.
7. Create unit tests using mocked server responses.
8. Run the relevant Gradle tests.
9. Report files modified and test results.
10. Update the progress journal after meaningful implementation or validation, labeling automated, user-reported
    emulator and physical-device evidence separately. Keep credentials, private addresses and library data out.

## Security
- Prefer read-only API permissions where sufficient.
- Never log access tokens.
- Never bypass HTTPS certificate validation.
- Do not implement unauthorised server-side mutations.
- Use synthetic or openly licensed test content.

## Output
After completing a task, report:
- Implementation summary
- API endpoints verified
- Dependencies added
- Tests run and results
- Remaining issues
- Recommended next step
