# Idea Spec

## Problem

The current branch introduces `sentry-android-navigation3`, but the Android sample app does not provide a focused way to exercise the new Navigation 3 instrumentation in a real app. Developers and SDK maintainers need a manual sample surface that drives the new API through realistic navigation patterns and makes span generation easy to inspect in Sentry.

Navigation 2 span generation is already represented in the existing Android sample through `ComposeActivity`, which uses `rememberNavController().withSentryObservableEffect()` and a `NavHost`. Nav3 should follow that precedent by living in the same Android sample app rather than becoming a separate sample module.

## Target Users

Sentry Android SDK maintainers validating the Nav3 integration before release, and Android developers looking for an executable example of how to wire `SentryNav3NavigationEffect` and `rememberSentryNavEntryDecorator` into a Navigation 3 app.

## Solution Overview

Add a new `NavigationActivity` to `sentry-samples-android` and expose it from the existing `TracingScreen` as an “Open Navigation Activity” entry.

The Activity should be a Compose-based manual verification surface for the Navigation 3 integration. It should include several navigation scenarios in one screen flow:

- Simple single-stack screens to verify ordinary route changes create navigation spans and update Sentry navigation context.
- Dialog-style destinations to verify transient destinations can be represented without breaking span generation.
- Multipane/list-detail screens wired through `rememberSentryNavStateHolder` and `rememberSentryNavEntryDecorator` to verify visible-entry and primary-route behavior.
- Tabbed navigation backed by multiple retained back stacks to verify selected-stack changes and retained-stack context.
- Safe demo route arguments through `argumentsExtractor`, using clearly non-PII values such as demo item IDs.

The sample is for manual verification only. Automated behavior coverage should remain in `sentry-android-navigation3` unit tests; this change only needs build/compile validation for the Android sample.

## Key Constraints

- Keep the change inside `sentry-samples:sentry-samples-android`; do not add a new Gradle sample module unless the existing sample cannot compile cleanly with Nav3 dependencies.
- Match existing sample app patterns: Activity registered in `AndroidManifest.xml`, launched from `MainActivity`’s Tracing section, and implemented with Compose like other sample surfaces.
- The sample must depend on `projects.sentryAndroidNavigation3` and `libs.androidx.navigation3.runtime` without changing public SDK APIs.
- Any route arguments sent to Sentry must be obviously safe sample data and should not imply that real user IDs, emails, auth tokens, or deep-link query params are safe to send.
- The UI should be practical for manual testing on a local emulator/device; avoid complex visual polish beyond clear scenario labels and controls.
- Avoid flaky instrumentation or envelope assertions for this sample.

## Out of Scope

- Adding a separate `sentry-samples-android-navigation3` module.
- Adding Android UI tests or system tests for the sample app.
- Changing the Nav3 integration public API or behavior.
- Reworking the existing Navigation 2 Compose sample.
- Building a production-quality app shell or exhaustive Nav3 recipes demo.

## Open Questions

- Whether `androidx.navigation3.runtime` alone is sufficient for the desired sample UI, or whether additional Nav3 UI/scene dependencies are required for dialog or multipane examples.
- Exact route model names and visual layout can be decided during implementation, as long as the scenarios above remain covered.

## Success Criteria

- `sentry-samples-android` has a new “Open Navigation Activity” control in the Tracing section.
- Launching the Activity presents manual controls for simple navigation, dialog navigation, multipane visible entries, and tabbed multi-backstack navigation.
- The Activity wires the new Nav3 Sentry APIs in the same place a real app would own its Nav3 state.
- Manual navigation generates Nav3 navigation spans/breadcrumbs and updates navigation context when run with the sample app’s existing Sentry configuration.
- The Android sample compiles successfully with the new dependencies and Activity.
