> Valid for this sprint only. Delete when this feature ships.

# Sentry Buddy Research

## Scope

Focused research for `sentry-android-buddy` recording functionality and format:

- New Android module setup and local publishing.
- API-status annotation convention.
- Recording lifecycle and Sentry correlation APIs.
- Activity lifecycle collection.
- JSON serialization conventions.
- BOM exclusion.

## Product Context From The Hackweek Notes

The Google Doc reinforces that "recording" means generating a structured, temporally oriented representation of a flow. Video may be considered later, but the core artifact should be universal enough for future Android, backend, CI, dashboard, CLI, IDE, and possibly other-SDK consumers.

Important implications:

- The recording format should not be Android-only in its top-level concepts, even though this first implementation is Android.
- Developer intent is first-class data: flow definition, importance, goals, ownership/customer metadata, known pain points, annotations, and domain vocabulary are all future inputs.
- The UI should be swappable later, so the recording core should not depend on Compose, Activity UI, or a floating bubble.
- The MVP can focus on recorder + JSON + local summary; in-app UI and LLM endpoint work can consume the output later.
- The format should keep future CI diffing, generated dashboards, and backend upload possible without forcing those product directions now.

## Module And Publishing

Existing Android integration modules are normal Gradle Android library modules listed in `settings.gradle.kts`. Examples such as `sentry-android-timber`, `sentry-android-sqlite`, and `sentry-android-replay` use:

- `id("com.android.library")`
- `alias(libs.plugins.kotlin.android)`
- `alias(libs.plugins.gradle.versions)`
- optionally `alias(libs.plugins.detekt)`
- `android.namespace = "io.sentry.android.<module>"`
- `testBuildType = "release"`
- `androidComponents.beforeVariants { it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType) }`
- `kotlin { explicitApi() }`

The root build applies the distribution and Maven publishing plugins to non-sample, non-test-support modules. Therefore, adding `sentry-android-buddy` as a normal included module makes it publishable to Maven Local without a separate repo or custom publishing path. External sample apps can consume the local artifact after `publishToMavenLocal` or after copying the local Maven artifact.

The root `sentry-bom` includes most non-sample published modules automatically by iterating over subprojects. Since Buddy is local/copy-only for this Hackweek branch and not intended as a supported module, explicitly exclude `sentry-android-buddy` from `sentry-bom/build.gradle.kts`.

`Config.BuildScript.androidLibs` lists Android artifacts used by the root POM dependency rewriting logic. Add `sentry-android-buddy` there if it is published as an Android library.

## API Status Convention

The repo already uses `org.jetbrains.annotations.ApiStatus.Experimental` for experimental public APIs. `SentrySQLiteDriver` previously used that annotation and the current tree still has examples in modules such as `sentry-android-distribution` and `sentry-kotlin-extensions`.

Buddy should follow that convention instead of adding a Kotlin `@RequiresOptIn` marker:

- Add `compileOnly(libs.jetbrains.annotations)` or implementation if needed for dependency resolution.
- Annotate public Buddy entry points and model classes with `@ApiStatus.Experimental`.

## Sentry Correlation APIs

Public Sentry APIs are sufficient for the MVP:

- `Sentry.setTag(key, value)` sets Buddy tags.
- `Sentry.removeTag(key)` removes Buddy tags.
- `Sentry.startTransaction(name, operation, options)` starts a root transaction.
- `ITransaction.finish()` finishes the root transaction.
- `ISpan.getSpanContext()` and `ISpan.toSentryTrace()` can expose trace/span identity for correlation fields.

Recommended transaction setup:

- Name: `Sentry Buddy Recording: <flow_slug>`.
- Operation: `ui.flow_recording`.
- Use `TransactionOptions` with manual finish behavior. Avoid idle/deadline settings unless required.
- Set Buddy tags both on the current scope and the root transaction.

Buddy-owned tags:

- `sentry.buddy.recording_id`
- `sentry.buddy.flow_slug`
- `sentry.buddy.source`
- `sentry.buddy.use_case`

Do not restore previous values for these keys in the MVP. Buddy owns the `sentry.buddy.*` namespace while a recording is active.

## Activity Lifecycle Collection

`sentry-android-core` already uses `Application.ActivityLifecycleCallbacks` in `ActivityLifecycleIntegration`. Buddy can register its own callback in `SentryBuddy.install(application)` without depending on core internals.

For the MVP:

- Register a lightweight `ActivityLifecycleCallbacks` implementation.
- Record `screen` timeline items on `onActivityResumed` using `activity.javaClass.simpleName`.
- Ignore pre-Q expanded lifecycle callbacks unless needed later.
- Provide an uninstall/close path if practical, but do not make manifest auto-start part of the MVP.

This avoids touching app-owned Sentry callbacks and avoids reaching into `ActivityLifecycleIntegration` internals.

## App And Device Metadata

Use direct Android APIs for metadata instead of internal Sentry Android helpers:

- `Application.packageName` for package name.
- `PackageManager.getPackageInfo(...)` for version name/code, with SDK-version-specific overloads if needed.
- `android.os.Build.MANUFACTURER`, `MODEL`, and `VERSION.RELEASE` for device info.

Avoid using `io.sentry.android.core.ContextUtils` or `BuildInfoProvider` in the public Buddy implementation because they are marked internal and are optimized for core SDK internals.

## Serialization

Sentry's JSON pattern is `JsonSerializable.serialize(ObjectWriter, ILogger)`. The SDK serializer handles `JsonSerializable`, collections, maps, strings, numbers, booleans, and dates.

For Buddy:

- Implement Buddy model classes as Kotlin classes with explicit public API.
- Use `JsonSerializable` where useful for deterministic JSON field ordering.
- Provide a public `BuddyFlowRecordingJsonSerializer.serialize(recording): String` convenience API.
- Prefer explicit serialization over reflection so the format remains stable.
- Keep deserialization out of the first slice unless a test or sample app needs it.

## Recording Model Shape

Top-level artifact:

- `type`: `sentry.mobile_flow_recording`
- `version`: `1`
- `platform`: `android`
- `useCase`: `onboard_new_flow`
- `flow`
- `recording`
- `app`
- `device`
- `summary`
- `timeline`
- `sentry`

Timeline item shape:

- `type`: `recording_started`, `screen`, `step`, `breadcrumb`, or `recording_stopped`.
- `timestamp`
- `elapsedMs`
- optional `name`
- optional `data`

Only `recording_started`, `screen`, `step`, and `recording_stopped` are produced in the MVP. `breadcrumb` is reserved for the deferred SDK-signal interception option.

## Testing Notes

Use the new module's release unit test task because Android debug variants are disabled in CI for these modules:

`./gradlew :sentry-android-buddy:testReleaseUnitTest`

Likely test coverage:

- One active recording at a time.
- `recordStep` outside a recording is either ignored or fails deterministically; choose one in implementation.
- Start adds a `recording_started` item and Buddy tags.
- Activity resume adds a `screen` item only while recording.
- Stop adds `recording_stopped`, derives summary, finishes transaction, and removes tags.
- Serializer emits stable top-level fields and timeline ordering.
- BOM excludes `sentry-android-buddy`.

Truth is preferred for new tests if added to the module dependencies.

## Implementation Recommendation

Build order:

1. Add module skeleton: settings include, build file, proguard file, namespace, dependencies, API dump setup.
2. Add model classes and deterministic serializer.
3. Add recorder core with clock/ID/Sentry facade seams for tests.
4. Add Activity lifecycle collector registered by `SentryBuddy.install(application)`.
5. Add public `SentryBuddy` facade.
6. Add tests.
7. Run `./gradlew :sentry-android-buddy:testReleaseUnitTest` first, then broader formatting/API tasks.

Avoid for the first slice:

- Wrapping `beforeBreadcrumb`, `beforeSend`, or `beforeSendTransaction`.
- Adding core SDK observer hooks.
- Adding Compose or UI dependencies.
- Adding manifest auto-init.
- Adding file persistence.
- Adding real endpoint calls.
