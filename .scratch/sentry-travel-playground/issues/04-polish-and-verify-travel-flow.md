# Polish and verify Sentry Travel flow

Status: ready-for-agent
Type: AFK
Blocked by: 03

## Description

Polish the final Sentry Travel experience and run verification.

Ensure screens have balanced spacing, readable text, useful loading/error states, and natural action labels. Confirm the primary manual path works: Integrations -> Sentry Travel -> Explore -> Destination -> Check availability -> Stay -> Review -> Confirm -> My Trips -> Trip Details -> Support -> Simulate failure.

## Acceptance criteria

- [ ] UI is polished and usable on a typical phone screen.
- [ ] The primary manual path is complete and does not crash.
- [ ] HTTP, DB, custom span, breadcrumb, and error actions are discoverable.
- [ ] `./gradlew :sentry-samples:sentry-samples-android:assembleDebug :sentry-samples:sentry-samples-android:compileReleaseKotlin` passes.
- [ ] Formatting has been applied.

## Comments
