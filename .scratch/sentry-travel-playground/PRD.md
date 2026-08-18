# PRD: Sentry Travel Playground

Status: ready-for-agent

## Problem Statement

Sentry Buddy needs a realistic Android sample flow that produces rich recording data. Today the sample app has individual demos for Compose, HTTP, and SQLite, but it does not provide one cohesive, polished app-like flow for validating Buddy's screen, action, breadcrumb, error, and span capture together.

## Solution

Add a Compose-based Sentry Travel mini-app to `sentry-samples-android`.

The sample app's Integrations section should show a new `Sentry Travel` entry as the first integration. Tapping it launches `SentryBuddyActivity`.

`SentryBuddyActivity` presents a polished travel-planning experience with a deep navigation graph:

`Home -> Explore -> Destination -> Stay -> Review -> Confirmation -> My Trips -> Trip Details -> Itinerary Day -> Activity Details`, plus Profile and Support branches.

Visible user actions should intentionally produce Sentry data:

- HTTP spans through the existing Retrofit/OkHttp `GithubAPI` path.
- Database spans through a lightweight local travel store using the existing SQLite integration infrastructure.
- Custom application spans around recommendation scoring, itinerary generation, price calculation, booking validation, support triage, and related travel work.
- Breadcrumbs for meaningful travel actions.
- A clearly labeled simulated failure action that captures an exception without crashing or trapping the app.

The activity should be visually polished: travel-themed cards, gradients, hierarchy, timelines, chips, and clear primary actions. It should not look like a debug button grid.

## User Stories

1. As an SDK engineer, I want to open Sentry Travel from the Integrations section so that I can quickly record a rich Buddy flow.
2. As a Buddy developer, I want to navigate through several realistic travel screens so that I can validate screen ordering and flow summaries.
3. As a QA reviewer, I want visible actions that trigger HTTP, database, and custom spans so that I can verify Buddy captures each class of signal.
4. As a demo user, I want the sample to look like a credible mini-app so that Buddy demos feel product-like instead of synthetic.
5. As a contributor, I want the sample implementation to be isolated and understandable so that I can add future scenarios without touching SDK runtime behavior.

## Implementation Decisions

- Add the entry wiring in `MainActivity.kt` and `AndroidManifest.xml`.
- Implement `SentryBuddyActivity` as a `ComponentActivity` using Compose.
- Keep the user-facing brand as `Sentry Travel` while the activity remains named `SentryBuddyActivity`.
- Use `rememberNavController().withSentryObservableEffect()` and a local `NavHost`.
- Keep most UI and state in the activity file for hackweek speed, but split small helpers if clarity demands it.
- Use existing dependencies only: Material3, Navigation Compose, Sentry Compose, Retrofit/OkHttp, SQLite helpers, and coroutines.
- Use `GithubAPI.service.listReposAsync(...)` for safe HTTP spans and graceful offline handling.
- Use a minimal local `TravelStore` for saved trips and preferences, backed by existing SQLite integration paths where practical.
- Use manual child spans through `Sentry.getSpan()?.startChild(...)` for custom application spans.
- Use `Sentry.addBreadcrumb(...)` and `Sentry.captureException(...)` for action and error validation.
- Do not change Sentry Buddy SDK behavior or public APIs.

## Testing Decisions

- Verify the sample app compiles in debug and release Kotlin:
  - `./gradlew :sentry-samples:sentry-samples-android:assembleDebug :sentry-samples:sentry-samples-android:compileReleaseKotlin`
- Run formatting/API tasks appropriate for touched modules:
  - `./gradlew spotlessApply apiDump`
- Manually verify the primary path:
  - Integrations -> Sentry Travel -> Explore -> Destination -> Check availability -> Stay -> Review -> Confirm -> My Trips -> Trip Details -> Support -> Simulate failure.
- Add unit tests only for non-trivial helper logic if introduced. UI automation is out of scope unless existing sample app test patterns make it cheap.

## Out of Scope

- Real travel booking, payment, auth, maps, remote configuration, or vendor APIs.
- New image loading or design asset dependencies.
- Production-grade caching or persistence.
- SDK runtime behavior changes.
- Public API changes.
- Comprehensive tablet/foldable layout work.

## Further Notes

The Product Owner approved this module breakdown:

1. Entry wiring: `MainActivity` Integrations item and manifest registration.
2. `SentryBuddyActivity` Compose shell, navigation, and theme.
3. Static Sentry Travel domain model and screen UI components.
4. `TravelTelemetry` helper for breadcrumbs, custom spans, HTTP action wrapper, DB action wrapper, and simulated error.
5. Lightweight `TravelStore` for saved trips and preferences.

## Comments
