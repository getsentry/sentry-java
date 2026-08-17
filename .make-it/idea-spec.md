# Idea Spec

## Problem

Android developers can install Sentry, but still have to translate app-specific intent into useful instrumentation, monitoring, dashboards, and performance guardrails by hand. The SDK observes lots of telemetry, but it does not have a lightweight way for a developer to say, "this app flow matters," record that flow locally, and turn the result into a reusable, structured artifact.

For Hackweek, Sentry Buddy should prove the first part of that loop: a debug-oriented Android SDK module that records a real app flow as structured data with developer intent, local timeline signals, and Sentry correlation. UI, LLM analysis, CI enforcement, generated PRs, and dashboard creation can build on that foundation later.

## Target Users

- Android developers using Sentry in debug builds who want to onboard an important app flow.
- Sentry SDK engineers demonstrating a mobile-first developer surface during Hackweek.
- Future Buddy/LLM tooling that needs structured flow recordings as input.
- External sample apps outside `sentry-java` that need to consume a local/copied Buddy artifact for demos.

## Solution Overview

Create a new Android library module, `sentry-android-buddy`, with namespace `io.sentry.android.buddy`. The module is intended for `debugImplementation` use in apps, but it remains publishable to Maven Local as `io.sentry:sentry-android-buddy:<version>` so external sample apps can consume it. It should be excluded from `sentry-bom` to avoid presenting it as a supported, general-consumption SDK module.

The first implementation focuses on recording functionality and the recording format, not the UI. It provides a tiny explicit public API:

- `SentryBuddy.install(application)` registers Buddy's lifecycle collector.
- `SentryBuddy.startRecording(intent)` starts one active recording.
- `SentryBuddy.recordStep(name, data)` records developer-authored flow milestones.
- `SentryBuddy.stopRecording()` finishes the recording and returns a `BuddyFlowRecording`.
- `BuddyFlowRecordingJsonSerializer.serialize(recording)` exports the versioned JSON artifact.

The recording format is an intent-rich, versioned JSON artifact with type `sentry.mobile_flow_recording`. It should encode developer intent, app/device metadata, recording metadata, deterministic summary stats, a normalized timeline, and Sentry correlation fields. It should not be a Sentry envelope, replay/video surrogate, or CI config yet.

The MVP captures the minimal reliable signal set:

- Duration.
- Flow intent.
- App and device metadata.
- Buddy-authored timeline items.
- Explicit developer-recorded steps.
- Activity/screen transitions from Buddy's own lifecycle callback.
- Sentry scope tags for correlation.
- One root Buddy transaction for the recording window.

The MVP deliberately does not intercept every breadcrumb, span, error, or transaction. Deferred options are tracked in `~/Desktop/sentry-buddy-foregone-options.txt`.

## Key Constraints

- This is a Hackweek branch and is not intended to merge to `main` as-is.
- Artifacts should be published locally/copied for demo use, not promoted for general consumption.
- The module should be separate from `sentry-android-core`; avoid adding `SentryAndroidOptions` or core SDK API surface.
- Public APIs should use the existing Sentry Java convention: `org.jetbrains.annotations.ApiStatus.Experimental`.
- The module should get its own API dump if required by the repository tooling.
- The module should be excluded from `sentry-bom`.
- Debug-build exclusion is the consuming app's Gradle responsibility via `debugImplementation`.
- One recording can be active at a time.
- Do not mutate app-owned Sentry callbacks in the MVP.
- Do not add deep SDK hooks in the MVP.
- Do not capture video, screenshots, text input, or view hierarchy.
- Use Buddy-owned `sentry.buddy.*` tag namespace while recording.
- On stop, remove Buddy's known scope tags; do not restore previous values for those same keys in the MVP.

## Out of Scope

- UI and recommendation rendering, except for any minimal sample wiring needed later.
- Real LLM endpoint integration.
- Generated PRs or auto-instrumentation patches.
- CI/Macrobenchmark enforcement.
- Production flow comparison.
- Backend ingest as a Sentry envelope.
- Perfect span/error/breadcrumb collection.
- Session Replay integration.
- Profile parsing or ANR diagnosis.
- Screenshots, text input capture, or view hierarchy capture.
- General SDK release/publishing support.

## Open Questions

- Whether the first external sample app will need any helper API beyond start/step/stop/serialize.
- Whether the first UI pass should consume `BuddyFlowRecording` directly or consume only serialized JSON plus a local summary model.
- Whether future SDK-signal interception should wrap callbacks or wait for a core observer API.
- Whether Buddy should eventually share concepts with Spotlight or remain separate.
- Which exact sample app will consume the locally published artifact for the Hackweek demo.

## Success Criteria

- A new `sentry-android-buddy` module builds as part of the repo.
- The module can be published locally and consumed by an external Android sample app.
- The module is excluded from `sentry-bom`.
- The public Buddy APIs are marked `@ApiStatus.Experimental`.
- A developer can install Buddy, start a recording, record explicit steps, trigger Activity/screen transitions, stop recording, and receive a `BuddyFlowRecording`.
- The recording produces versioned JSON with flow intent, recording metadata, app/device metadata, summary stats, timeline items, and Sentry correlation fields.
- Starting a recording sets Buddy scope tags and starts a root Buddy transaction.
- Stopping a recording finishes the transaction and removes Buddy scope tags.
- Unit tests cover the recording lifecycle, one-active-recording behavior, summary derivation, JSON serialization, tag cleanup behavior, and BOM exclusion/build wiring where practical.
