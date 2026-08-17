# Create publishable Buddy module skeleton

Status: ready-for-agent
Type: AFK

## Description

Create the new `sentry-android-buddy` Android library module so later Buddy recording code has a proper SDK home. The module should build with the repo's Android library conventions, participate in local Maven publication, expose experimental public API status in the same style as existing Sentry modules, and stay out of the Sentry BOM.

## Acceptance criteria

- [ ] `sentry-android-buddy` is included in the Gradle build as an Android library module.
- [ ] The module namespace is `io.sentry.android.buddy`.
- [ ] The module follows existing Android integration module conventions for Kotlin, explicit API, test build type, variant disabling, lint, and dependencies.
- [ ] The module is locally publishable through the existing Maven Local workflow.
- [ ] `sentry-android-buddy` is added to Android artifact build config where needed.
- [ ] `sentry-android-buddy` is explicitly excluded from `sentry-bom`.
- [ ] Public Buddy APIs can use `org.jetbrains.annotations.ApiStatus.Experimental`.
- [ ] A minimal smoke test or equivalent focused Gradle validation can run for the module.

## Comments
