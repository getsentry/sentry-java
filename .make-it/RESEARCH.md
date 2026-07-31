> Valid for this sprint only. Delete when this feature ships.

# Research: Android Nav3 Sample Surface

## Scope

- Verify the Navigation 3 API/dependency shape available on this branch.
- Decide whether the sample can live inside `sentry-samples-android`.
- Identify the smallest sample scenarios needed to manually verify Nav3 span generation.
- Capture launch/build patterns from the existing Android sample app.

## Existing Nav2 Sample Precedent

The current Android sample already exercises Navigation 2 from the existing `sentry-samples-android` app:

- `sentry-samples/sentry-samples-android/src/main/java/io/sentry/samples/android/compose/ComposeActivity.kt` creates `rememberNavController().withSentryObservableEffect()`.
- `SampleNavigation` uses `NavHost` with a landing route, a simple GitHub route, and an argument-bearing GitHub route.
- The Activity is launched from the existing sample UI rather than from a separate navigation-specific sample module.

This supports adding Nav3 as another Activity in the existing sample app.

## Existing Sample App Patterns

`MainActivity.TracingScreen` is the right launch point for a span-generation sample. It already contains buttons like “Open Second Activity”, “Open Profiling Activity”, and “Open SQLite Activity”, each wrapped in `SentryTraced` and launched through an explicit `Intent`.

The Nav3 sample should follow the SQLite pattern:

- Register the Activity in `src/main/AndroidManifest.xml` with `android:exported="false"`.
- Add one `TracingScreen` grid item labeled “Open Navigation Activity”.
- Wrap the launcher button in a unique `SentryTraced` tag such as `open_navigation_activity`.
- Implement the Activity as a `ComponentActivity` or existing-compatible Compose Activity.

## Nav3 Integration APIs Available Locally

The branch includes `sentry-android-navigation3` and checked-in examples that use only the Navigation 3 runtime API shape:

- `SentryNav3NavigationEffect(backStack = ..., nameExtractor = ..., argumentsExtractor = ...)` for simple single-stack observation.
- `SentryNav3NavigationEffect(selectedStack = ..., backStacks = ..., stacksInUse = ..., stackNameExtractor = ..., nameExtractor = ..., argumentsExtractor = ...)` for multiple retained stacks.
- `rememberSentryNavStateHolder` plus `rememberSentryNavEntryDecorator(holder)` for multipane/visible-entry observation.
- `NavDisplay`, `NavEntry`, `entryProvider`, and `entryDecorators` in the checked-in usage examples.

The current Gradle catalog defines `androidx.navigation3:navigation3-runtime` as `libs.androidx.navigation3.runtime` at version `1.1.4`. No separate Nav3 UI/scene dependency is currently defined.

## Dependency Decision

The first implementation attempt should add these dependencies to `sentry-samples-android`:

- `implementation(projects.sentryAndroidNavigation3)`
- `implementation(libs.androidx.navigation3.runtime)`

Do not introduce another sample module. Do not add extra Nav3 dependencies unless compilation proves that a desired sample API is unavailable from `navigation3-runtime`.

## Scenario Design

The sample should use a small sealed route model, for example:

- `Home`
- `Details(id: String)`
- `SettingsDialog`
- `ListPane`
- `DetailPane(id: String)`

Recommended scenario structure:

1. Single-stack demo: push/pop ordinary routes from one `SnapshotStateList` and wire `SentryNav3NavigationEffect` at the same level as `NavDisplay`.
2. Dialog demo: represent the dialog as a Nav3 destination/entry in the same route model or as a clearly labeled overlay driven by a route key. Prefer the simplest compiling Nav3 pattern; the purpose is manual span verification, not a full dialog-navigation abstraction.
3. Multipane demo: render list/detail routes together and wire a shared `SentryNavStateHolder` through `rememberSentryNavEntryDecorator`. Use metadata or `primaryRouteSelector` so the detail pane can become the primary route.
4. Multi-backstack demo: model tabs as a selected stack plus a `Map<Tab, List<Route>>`. Switch tabs and push routes independently per tab, then call the multiple-stack `SentryNav3NavigationEffect` overload.
5. Safe arguments: use `argumentsExtractor` for non-PII sample values, such as `item_id = "demo-42"` and `tab = "home"`.

## Testing Decision

This feature should add a manual sample surface only. The integration module already contains behavior-level unit tests for breadcrumbs, transactions, backstack context, selected stack behavior, visible entries, and primary pane selection.

Verification for this sample should be:

- Compile/build `sentry-samples-android`.
- Optionally install/run the sample manually and inspect generated Sentry spans/breadcrumbs/context.

Do not add flaky Android UI tests or mock-envelope assertions as part of this sample change.

## Risks

- `NavDisplay` dialog or multipane helper APIs may require a dependency not currently in the catalog. If so, prefer a minimal runtime-only representation before expanding dependencies.
- The sample can become too large if it tries to be a full Nav3 recipes app. Keep it a manual SDK verification surface, not a comprehensive Navigation 3 tutorial.
- Route arguments are easy to copy incorrectly. The sample must label argument extraction as demo-only safe data.

## Outcome

Proceed with a PRD for an in-app `NavigationActivity` under `sentry-samples-android`, launched from `TracingScreen`, covering single-stack, dialog-like, multipane, and multi-backstack Nav3 scenarios with safe demo arguments and compile/build verification only.
