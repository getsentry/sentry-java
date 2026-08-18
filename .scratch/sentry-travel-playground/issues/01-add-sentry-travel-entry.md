# Add Sentry Travel sample entry

Status: ready-for-agent
Type: AFK

## Description

Wire Sentry Travel into the Android sample app. Add a `Sentry Travel` entry as the first item under `IntegrationsScreen()` in `MainActivity.kt`, and register `SentryBuddyActivity` in `AndroidManifest.xml`.

This slice can add a stub `SentryBuddyActivity` if needed so the entry compiles before the full UI lands.

## Acceptance criteria

- [ ] `Sentry Travel` appears first in the Integrations grid.
- [ ] Tapping the entry launches `SentryBuddyActivity`.
- [ ] `AndroidManifest.xml` declares the activity with `android:exported="false"`.
- [ ] The sample app compiles.

## Comments
