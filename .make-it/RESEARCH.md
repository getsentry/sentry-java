> Valid for this sprint only. Delete when this feature ships.

# Research: Sentry Travel Sample App Playground

## Existing Entry Point Pattern

- `MainActivity.kt` owns the sample app category rail and screens.
- `IntegrationsScreen()` is a `LazyVerticalGrid` of `OutlinedButton` entries wrapped in `SentryTraced`.
- The new Sentry Travel entry should be the first `item` in `IntegrationsScreen()` and launch `SentryBuddyActivity` with an explicit `Intent`.

## Activity / Manifest Pattern

- Sample activities are declared in `sentry-samples/sentry-samples-android/src/main/AndroidManifest.xml` with `android:exported="false"`.
- Existing Compose sample is `.compose.ComposeActivity`; no `SentryBuddyActivity` currently exists.
- Add `.SentryBuddyActivity` or a package-local variant and register it in the manifest.

## Compose Pattern

- The sample app already uses `ComponentActivity.setContent`, Material3, `NavigationRail`, `LazyVerticalGrid`, `Navigation Compose`, and `SentryTraced`.
- `ComposeActivity` demonstrates `rememberNavController().withSentryObservableEffect()` and `NavHost` with route args.
- Sentry Travel can use a self-contained `NavHost` inside `SentryBuddyActivity` and wrap primary screens/actions in `SentryTraced` for Compose/user interaction spans.

## HTTP Spans

- Existing `GithubAPI` uses Retrofit with an OkHttp client configured with `SentryOkHttpEventListener` and `SentryOkHttpInterceptor`.
- The playground can safely reuse `GithubAPI.service.listReposAsync(...)` from visible user actions to generate HTTP spans without adding dependencies.
- Calls must be wrapped in coroutine state and catch failures so the playground remains usable offline.

## Database Spans

- The sample app already depends on `sentry-android-sqlite`, Room, SQLDelight, and SQLite drivers.
- Existing `SampleDatabases` exposes Sentry-wrapped direct SQLite and open-helper paths.
- For the playground, a small dedicated `TravelStore` can use `SampleDatabases.directHelper(context)` and `SupportSQLiteDatabase` statements to generate db spans while avoiding a broad reuse of the full SQLite demo UI.
- Database actions should be user-triggered: save booking, load trips, update traveler preferences.

## Custom Application Spans

- Custom spans can be created with `Sentry.getSpan()?.startChild(...)`, finished around suspend work or CPU loops.
- Good app-span operations: recommendation scoring, itinerary generation, price calculation, booking validation, support triage.

## Error and Breadcrumb Capture

- Buddy recently captures breadcrumbs and errors, so Sentry Travel should add breadcrumbs around major actions and provide a clearly labeled simulated failure action.
- The failure should use `Sentry.captureException(...)` and leave the app usable.

## Implementation Decision

- Build a single self-contained Kotlin activity file for the playground to keep hackweek iteration fast.
- Register it in the manifest and add the first Integrations entry.
- Use static travel data, Sentry Travel-specific Material colors, and cards/timelines for polish.
- Use existing `GithubAPI` for HTTP and a minimal direct SQLite helper for DB spans.
- Avoid new dependencies and avoid SDK/API changes.

## Verification Plan

- `./gradlew :sentry-samples:sentry-samples-android:assembleDebug :sentry-samples:sentry-samples-android:compileReleaseKotlin`
- Manual path: Integrations → Sentry Travel → Explore → Destination → Check availability → Build itinerary → Review → Save trip → My Trips → Trip details → Support → simulated failure.
