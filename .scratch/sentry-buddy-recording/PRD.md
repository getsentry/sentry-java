# PRD: Sentry Buddy Recording

Status: ready-for-agent

## Problem Statement

Android developers can install Sentry and still struggle to shape Sentry around the flows that matter most in their app. They know a checkout, login, onboarding, or other key journey is important, but they have to manually decide which spans, tags, dashboards, budgets, alerts, and checks are needed. Sentry observes telemetry, but the SDK does not yet provide a mobile-first way for the developer to say, "this flow matters," record the flow locally, and export a structured artifact that captures both app behavior and developer intent.

For Hackweek, Sentry Buddy should establish the recording foundation for that developer loop. The first deliverable is not the final UI or LLM experience. It is a new Android debug-oriented SDK module that can produce an intent-rich, temporally ordered flow recording that later UI, CLI, IDE, backend, LLM, CI, dashboard, and generated-instrumentation work can consume.

## Solution

Create a new `sentry-android-buddy` Android library module with namespace `io.sentry.android.buddy`. Apps can consume it through `debugImplementation` and, for Hackweek demos, from a Maven Local or copied local artifact. The module should be publishable locally but excluded from `sentry-bom` so it is not presented as a supported general-consumption SDK module.

The module exposes a tiny explicit API for recording app flows. A developer installs Buddy with their `Application`, starts one active recording with a flow intent, optionally records meaningful steps, navigates through the app, stops the recording, and receives a `BuddyFlowRecording`. The recording can then be serialized to deterministic versioned JSON.

The recording JSON is an intent-rich artifact, not a Sentry envelope, replay, screenshot, or CI config. It includes developer intent, recording metadata, app/device metadata, summary stats, timeline items, and Sentry correlation fields. The MVP captures only reliable signals Buddy can own directly: duration, explicit steps, Activity/screen transitions, app/device metadata, Buddy scope tags, and one root Buddy transaction.

## User Stories

1. As an Android developer, I want to add a debug-only Buddy dependency, so that I can try flow recording without changing release builds.
2. As an Android developer, I want to publish Buddy locally from `sentry-java`, so that an external demo app can consume the artifact.
3. As an Android developer, I want Buddy excluded from the Sentry BOM, so that local Hackweek artifacts are not represented as supported SDK modules.
4. As an Android developer, I want to install Buddy explicitly from my `Application`, so that I control when the debug helper is active.
5. As an Android developer, I want to start a named flow recording, so that Sentry can understand which app journey I care about.
6. As an Android developer, I want to include a developer goal with the flow, so that later analysis can answer the right question.
7. As an Android developer, I want to mark a flow's importance, so that future recommendations can scale with business criticality.
8. As an Android developer, I want to record meaningful steps during the flow, so that the recording includes domain moments that automatic telemetry cannot infer.
9. As an Android developer, I want Buddy to record screen transitions while I navigate, so that the timeline reflects the shape of the app journey.
10. As an Android developer, I want Buddy to record start and stop timeline items, so that the artifact has a clear recording window.
11. As an Android developer, I want Buddy to prevent overlapping recordings, so that exported artifacts are unambiguous.
12. As an Android developer, I want stopping a recording to return a structured object, so that UI, CLI, or test code can decide what to do next.
13. As an Android developer, I want a deterministic JSON serializer, so that recordings are easy to inspect, diff, and pass to another process.
14. As an Android developer, I want the JSON to include app metadata, so that future analysis can identify which app/build produced the recording.
15. As an Android developer, I want the JSON to include device metadata, so that future analysis can understand the device context.
16. As an Android developer, I want the JSON to include summary stats, so that a UI can show useful results without reparsing the full timeline.
17. As an Android developer, I want the JSON to include Sentry correlation fields, so that the recording can be connected to Sentry telemetry.
18. As an Android developer, I want Buddy to tag Sentry telemetry during recording, so that events and transactions can be searched by recording ID or flow slug.
19. As an Android developer, I want Buddy to start a root transaction for the recording window, so that the recording has a trace-level anchor in Sentry.
20. As an Android developer, I want Buddy to clean up its tags when recording stops, so that later app activity is not incorrectly associated with the flow.
21. As an Android developer, I want Buddy public APIs marked experimental, so that Hackweek code can move quickly without claiming stable SDK status.
22. As an SDK engineer, I want the recorder core decoupled from Android lifecycle and Sentry static APIs, so that lifecycle behavior can be unit-tested.
23. As an SDK engineer, I want the recording format model separated from the recorder, so that the schema can evolve independently from capture mechanics.
24. As an SDK engineer, I want deferred SDK-signal interception documented separately, so that this MVP does not accidentally mutate app-owned callbacks.
25. As a future Buddy UI author, I want a simple recording object and serializer, so that UI work can focus on interaction design instead of telemetry mechanics.
26. As a future CLI or IDE integration author, I want the recording artifact not to depend on Android UI, so that alternate input surfaces can use the same protocol later.
27. As a future backend or LLM integration author, I want the recording artifact to include developer intent, so that recommendations can be contextual instead of generic.
28. As a future CI integration author, I want the artifact to be temporally ordered and deterministic, so that later work can compare key flow runs.

## Implementation Decisions

- Create a new Android library module named `sentry-android-buddy`.
- Use the package namespace `io.sentry.android.buddy`.
- Keep Buddy separate from `sentry-android-core` and avoid adding `SentryAndroidOptions` configuration.
- Make the module publishable through the existing local Maven publishing conventions.
- Exclude `sentry-android-buddy` from `sentry-bom`.
- Add Buddy to the Android library artifact list used by the root build's POM handling.
- Use Kotlin with explicit API, following existing Android integration module conventions.
- Use `org.jetbrains.annotations.ApiStatus.Experimental` on public Buddy APIs and model classes.
- Do not add a Kotlin `@RequiresOptIn` marker for Buddy.
- Define a small public facade for install, start, step, stop, and serialization.
- Allow one active recording at a time.
- Make overlapping start attempts fail deterministically.
- Make recording steps outside an active recording fail deterministically rather than silently dropping developer intent.
- Register an `Application.ActivityLifecycleCallbacks` collector from explicit install.
- Record `screen` timeline items on Activity resume using the Activity simple class name.
- Do not add manifest auto-initialization in the MVP.
- Do not add Compose, floating bubble, or bottom-sheet dependencies in this PRD.
- Do not intercept Sentry callbacks in this PRD.
- Do not add deep SDK observer hooks in this PRD.
- Use public Sentry APIs to set and remove Buddy tags.
- Start one root Buddy transaction with operation `ui.flow_recording`.
- Name the root transaction `Sentry Buddy Recording: <flow_slug>`.
- Set Buddy correlation tags on the current scope and on the root transaction.
- Use Buddy-owned tag keys under `sentry.buddy.*`.
- Remove Buddy's known scope tags on stop.
- Do not restore previous values for those Buddy-owned tag keys in the MVP.
- Include the root transaction trace/span identifiers in the recording when available.
- Use direct Android framework APIs for app and device metadata.
- Avoid depending on internal Sentry Android helper classes for metadata.
- Define the recording artifact as type `sentry.mobile_flow_recording`, version `1`, platform `android`, and use case `onboard_new_flow`.
- Include `flow`, `recording`, `app`, `device`, `summary`, `timeline`, and `sentry` sections in the JSON.
- Use a normalized timeline with `recording_started`, `screen`, `step`, `breadcrumb`, and `recording_stopped` item types.
- Produce only `recording_started`, `screen`, `step`, and `recording_stopped` timeline items in the MVP.
- Reserve `breadcrumb` as a future schema type for later SDK-signal interception.
- Implement deterministic JSON serialization with explicit field ordering.
- Keep deserialization out of scope unless it becomes necessary for tests.
- Track deferred SDK-signal interception and deep SDK hook options outside the implementation plan for now.

## Testing Decisions

- Tests should focus on external behavior: public API behavior, returned recording objects, serialized JSON, and observable Sentry facade interactions.
- Tests should avoid asserting private implementation details such as internal list mutation order beyond the public timeline order.
- The recording format module should have serialization tests that assert stable top-level structure and representative nested fields.
- The recorder core should have unit tests for one-active-recording behavior, lifecycle transitions, summary derivation, elapsed time calculation, and deterministic failure behavior.
- The Sentry correlation seam should be tested through a fake facade rather than real network/event delivery.
- The Activity lifecycle collector should be tested with Robolectric or a small fake callback path where practical.
- Build wiring should be validated by running the new module's release unit test task.
- API dump generation should be run after the public module is added.
- Truth should be used for new assertions where the module includes the dependency.
- The initial focused validation command should be `./gradlew :sentry-android-buddy:testReleaseUnitTest`.
- Before final handoff, run formatting and API generation with `./gradlew spotlessApply apiDump`.
- If time permits, run the broader check command or at least a targeted module check.

## Out of Scope

- In-app Buddy UI.
- Floating bubble or bottom sheet.
- CLI or IDE integration.
- Voice commentary.
- Text-file import.
- Real LLM endpoint integration.
- Dummy recommendation endpoint.
- Generated instrumentation snippets.
- Generated PRs.
- Custom dashboard generation.
- CI golden-flow diffing.
- Macrobenchmark integration.
- Production comparison.
- Backend ingest as a Sentry envelope.
- Sentry Spotlight integration.
- Session Replay integration.
- Video recording.
- Screenshots.
- Text input capture.
- View hierarchy capture.
- Profile parsing.
- ANR diagnosis.
- Full breadcrumb/span/error interception.
- Core SDK observer APIs.
- Stable SDK release support.
- Adding Buddy to the Sentry BOM.
- Adding Buddy options to `SentryAndroidOptions`.

## Further Notes

This PRD intentionally treats Buddy as a recording protocol and recorder core first. The long-term product ambition is broader: a mobile-first platform for telling Sentry what matters from inside the app, CLI, IDE, or other developer surfaces. The MVP should keep that future possible by making the recording artifact explicit, versioned, and intent-rich, while avoiding premature coupling to any one UI or backend path.

Deferred capture strategies are tracked in `~/Desktop/sentry-buddy-foregone-options.txt`. The most important deferred options are SDK callback interception and deeper SDK observer hooks. They are valuable future directions, but they are deliberately excluded from this first implementation to keep the Hackweek slice reliable and low-risk.

## Comments
