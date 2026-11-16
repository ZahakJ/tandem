
---
name: romm-api-integration
description: Use when developing, debugging or reviewing RoMM API integration in the Tandem Android frontend, including authentication, remote libraries, metadata, platform mapping and API error handling.
---

# RoMM API Integration Workflow

This skill supports development of native RoMM integration
for Tandem on the AYN Thor.

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
