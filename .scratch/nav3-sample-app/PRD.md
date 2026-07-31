# PRD: Android Nav3 Sample App

Status: ready-for-agent

## Problem Statement

Sentry's Android SDK branch now includes Navigation 3 support, but the existing Android sample app does not give maintainers a focused way to exercise that integration in a real application. The branch has unit coverage for the integration, but maintainers still need a practical manual surface for validating that Nav3 interactions generate the expected navigation spans, breadcrumbs, screen updates, and navigation context when used from an Android app.

Navigation 2 span generation is already represented inside the existing Android sample app through a Compose Activity. Nav3 should have a similar in-app sample surface rather than a separate Gradle sample module.

## Solution

Add a new Compose-based Navigation Activity to the existing Android sample app and expose it from the Tracing section as “Open Navigation Activity”. The Activity will act as a manual verification surface for the `sentry-android-navigation3` integration.

The Activity should demonstrate four practical Nav3 scenarios:

- Simple single-stack navigation between ordinary screens.
- Dialog-like navigation through a route/entry that lets maintainers inspect transient destination behavior.
- Multipane/list-detail navigation using the holder plus decorator API so visible entries and primary route selection can be exercised.
- Tabbed navigation with multiple retained back stacks so selected-stack changes and retained-stack context can be exercised.

The sample should include safe, clearly non-PII route arguments through `argumentsExtractor`, such as demo item IDs. It should not imply that real user identifiers, emails, auth tokens, or deep-link query parameters are safe to send.

## User Stories

1. As an Android SDK maintainer, I want to open a Nav3 sample from the existing Android sample app, so that I can verify Nav3 instrumentation without building a separate sample module.
2. As an Android SDK maintainer, I want the Nav3 sample to live in the Tracing section, so that it is grouped with other span-generation verification surfaces.
3. As an Android SDK maintainer, I want simple screen-to-screen Nav3 navigation, so that I can verify basic navigation spans and breadcrumbs.
4. As an Android SDK maintainer, I want a visible current-route label in the sample UI, so that I can correlate manual taps with expected Sentry route names.
5. As an Android SDK maintainer, I want to push a detail route with a demo argument, so that I can verify safe argument extraction behavior.
6. As an Android SDK maintainer, I want to pop routes from the sample UI, so that I can verify back navigation does not break the active navigation state.
7. As an Android SDK maintainer, I want a dialog-like route in the sample, so that I can inspect how transient destinations affect spans and breadcrumbs.
8. As an Android SDK maintainer, I want multipane/list-detail routes rendered together, so that I can verify visible-entry context and primary-route behavior.
9. As an Android SDK maintainer, I want the multipane sample to choose a detail route as primary, so that screen tracking and navigation transaction names match the user-visible detail pane.
10. As an Android SDK maintainer, I want tabbed navigation backed by retained stacks, so that I can verify selected-stack changes generate navigation behavior.
11. As an Android SDK maintainer, I want inactive tabs to retain their route histories, so that I can verify retained backstack context contains more than the selected tab.
12. As an Android SDK maintainer, I want each tab to support independent pushes, so that I can verify inactive stack changes and selected stack changes remain distinguishable.
13. As an Android SDK maintainer, I want the sample to use the real `sentry-android-navigation3` APIs, so that manual verification exercises the SDK surface users will copy.
14. As an Android SDK maintainer, I want the sample to avoid extra dependencies where possible, so that it does not make the Android sample app harder to build.
15. As an Android SDK maintainer, I want the sample to compile as part of the existing Android sample, so that future Nav3 API drift breaks loudly.
16. As an SDK documentation author, I want the sample code to be readable, so that it can inform future docs or examples.
17. As an Android developer evaluating Sentry, I want the sample to show where to place `SentryNav3NavigationEffect`, so that I do not accidentally scope it inside a destination that leaves composition.
18. As an Android developer evaluating Sentry, I want the sample to show the holder/decorator pattern, so that I understand how to wire multipane visible-entry tracking.
19. As an Android developer evaluating Sentry, I want route arguments to be clearly marked as safe demo data, so that I do not copy a pattern that sends sensitive values.
20. As a reviewer, I want the feature to avoid new public APIs, so that the sample app can land without expanding SDK compatibility risk.

## Implementation Decisions

- Modify the existing Android sample app rather than adding a new sample module.
- Add a new Activity dedicated to Nav3 navigation scenarios.
- Launch the new Activity from the existing Tracing screen with a button labeled “Open Navigation Activity”.
- Register the Activity in the Android sample manifest as non-exported.
- Add the sample app dependency on `sentry-android-navigation3` and the existing `androidx.navigation3:navigation3-runtime` catalog entry.
- Prefer the existing catalog/runtime dependency only. Add more Nav3 dependencies only if compilation proves a required sample API is unavailable.
- Use a small sealed route model owned by the sample app, matching Nav3's app-owned state model.
- Wire `SentryNav3NavigationEffect` at the same composition level as the Nav3 state owner and display, not inside individual destinations.
- Use `nameExtractor` to keep span and breadcrumb route names stable and readable.
- Use `argumentsExtractor` only for non-PII demo values.
- Use the multiple-stack overload for tabbed retained backstacks.
- Use `rememberSentryNavStateHolder` and `rememberSentryNavEntryDecorator` for the multipane/list-detail scenario.
- Keep the UI simple and explicit: scenario sections, route labels, and buttons for navigation actions are more important than polished visuals.
- Do not change the Nav3 integration module's public API or behavior as part of this work.

## Testing Decisions

- This PRD calls for a manual verification sample, not a new automated test suite.
- Existing Nav3 unit tests remain the behavior-level coverage for breadcrumbs, transactions, navigation context, selected-stack handling, and visible entries.
- Validate the implementation by compiling/building the Android sample app.
- If compile failures reveal missing dependencies, prefer the smallest dependency change necessary for the sample to build.
- Manual QA should launch the sample app, open the Navigation Activity, exercise each scenario, and inspect generated Sentry navigation telemetry.
- Do not add Android UI tests, emulator assertions, mock-envelope assertions, or backend system tests for this sample.

## Out of Scope

- Creating a separate `sentry-samples-android-navigation3` module.
- Reworking the existing Navigation 2 Compose sample.
- Adding new SDK APIs or changing Nav3 integration behavior.
- Adding a comprehensive Navigation 3 recipes clone.
- Adding automated Android UI or system tests for the sample.
- Adding remote issue tracker artifacts, opening a PR, or pushing any branch.

## Further Notes

- The public Android Navigation 3 docs fetch timed out during research, so implementation should rely first on checked-in examples and compile feedback from the current dependency set.
- If a true Nav3 dialog API is not available from the current runtime dependency, model the dialog scenario as the simplest compiling dialog-like route/entry rather than expanding the sample's dependency surface prematurely.
- The sample should be treated as an SDK verification surface. If it starts requiring a large app architecture, split the implementation back down to the smallest scenarios that exercise the Sentry integration.

## Comments
