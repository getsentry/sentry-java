# Validate local artifact consumption workflow

Status: ready-for-agent
Type: AFK
Blocked by: 04

## Description

Validate the new Buddy module as a local Hackweek artifact. The result should be buildable, testable, API-dumped, and locally publishable for external sample-app consumption without adding Buddy to the Sentry BOM or treating it as a stable SDK module.

## Acceptance criteria

- [ ] The focused module test task runs successfully.
- [ ] Formatting and API dump generation are run for the new public APIs.
- [ ] The generated API dump reflects the experimental Buddy public API.
- [ ] Maven Local publication for `sentry-android-buddy` is validated if practical.
- [ ] Buddy remains excluded from `sentry-bom`.
- [ ] Any command needed by an external sample app to consume the local artifact is documented in a suitable local artifact or module README if needed.

## Comments
