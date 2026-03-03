---
alwaysApply: false
description: Adding and modifying SDK options
---
# Adding Options to the SDK

New features must be **opt-in by default**. Options control whether a feature is enabled and how it behaves.

## Namespaced Options

Newer features use namespaced option classes nested inside `SentryOptions`, e.g.:
- `SentryOptions.getLogs()` → `SentryOptions.Logs`
- `SentryOptions.getMetrics()` → `SentryOptions.Metrics`

Each namespaced options class is a `public static final class` inside `SentryOptions` with its own fields, getters/setters, and callbacks (e.g. `BeforeSendLogCallback`, `BeforeSendMetricCallback`).

A typical namespaced options class contains:
- `enabled` boolean (default `false` for opt-in)
- `sampleRate` double (if the feature supports sampling)
- `beforeSend` callback interface (nested inside the options class)

To add a new namespaced options class:
1. Create the `public static final class` inside `SentryOptions` with fields, getters/setters, and any callback interfaces
2. Add a private field on `SentryOptions` initialized with `new SentryOptions.MyFeature()`
3. Add getter/setter on `SentryOptions` annotated with `@ApiStatus.Experimental`

## Direct (Non-Namespaced) Options

Options that apply globally across the SDK (e.g. `dsn`, `environment`, `release`, `sampleRate`, `maxBreadcrumbs`) live as direct fields on `SentryOptions` with getter/setter pairs. Use this pattern for options that aren't tied to a specific feature namespace.

## Configuration Layers

Options can be set through multiple layers. When adding a new option, consider which layers apply:

### 1. SentryOptions (always required)

The core options class. Add the field (or nested class) with getter/setter here.

**File:** `sentry/src/main/java/io/sentry/SentryOptions.java`

**Tests:** `sentry/src/test/java/io/sentry/SentryOptionsTest.kt`
- Test the default value
- Test merge behavior (see layer 2)

### 2. ExternalOptions (sentry.properties / environment variables)

Allows setting options via `sentry.properties` file or system properties. Fields use nullable wrapper types (`@Nullable Boolean`, `@Nullable Double`) since unset means "don't override the default."

**File:** `sentry/src/main/java/io/sentry/ExternalOptions.java`
- Add `@Nullable` fields with getter/setter for each externally configurable option (e.g. `enableMetrics`, `logsSampleRate`)
- Wire them in the static `from(PropertiesProvider)` method:
  - Boolean: `propertiesProvider.getBooleanProperty("metrics.enabled")`
  - Double: `propertiesProvider.getDoubleProperty("logs.sample-rate")`

**File:** `sentry/src/main/java/io/sentry/SentryOptions.java` — `merge()` method
- Add null-check blocks to apply each external option onto the namespaced options class:
  ```java
  if (options.isEnableMetrics() != null) {
      getMetrics().setEnabled(options.isEnableMetrics());
  }
  if (options.getLogsSampleRate() != null) {
      getLogs().setSampleRate(options.getLogsSampleRate());
  }
  ```

**Tests:**
- `sentry/src/test/java/io/sentry/ExternalOptionsTest.kt` — test true/false/null for booleans, valid values and null for doubles
- `sentry/src/test/java/io/sentry/SentryOptionsTest.kt` — test merge applies values and test merge preserves defaults when unset

### 3. Android Manifest Metadata (Android only)

Allows setting options via `AndroidManifest.xml` `<meta-data>` tags.

**File:** `sentry-android-core/src/main/java/io/sentry/android/core/ManifestMetadataReader.java`
- Add a `static final String` constant for the key (e.g. `"io.sentry.metrics.enabled"`)
- Read it in `applyMetadata()` using `readBool(metadata, logger, CONSTANT, defaultValue)`
- Apply to the namespaced options, e.g. `options.getMetrics().setEnabled(...)`

**Tests:** `sentry-android-core/src/test/java/io/sentry/android/core/ManifestMetadataReaderTest.kt`
- Test default value preserved when not in manifest
- Test explicit true
- Test explicit false

### 4. Spring Boot Properties (Spring Boot only)

`SentryProperties` extends `SentryOptions`, so namespaced options (nested classes) are automatically available as Spring Boot properties without extra code. For example, `SentryOptions.Logs` is automatically mapped to `sentry.logs.enabled` in `application.properties`.

No additional code is needed for namespaced options — Spring Boot auto-configuration handles this via property binding on the `SentryOptions` class hierarchy.

**Tests:** `sentry-spring-boot*/src/test/kotlin/.../SentryAutoConfigurationTest.kt`
- Add the property (e.g. `"sentry.logs.enabled=true"`) to the existing `resolves all properties` test
- Assert the value is set on the resolved `SentryProperties` bean
- There are three Spring Boot modules with separate test files: `sentry-spring-boot`, `sentry-spring-boot-jakarta`, `sentry-spring-boot-4`

### 5. Reading Options at Runtime

Features check their options at usage time. For namespaced features the check typically happens in the feature's API class (e.g. `LoggerApi`, `MetricsApi`):
- Check `options.getLogs().isEnabled()` early and return if disabled
- Apply sampling via `options.getLogs().getSampleRate()` if applicable
- Apply `beforeSend` callback in `SentryClient` before sending

When a feature has its own capture path (e.g. `captureLog`), the relevant classes are:
- `ISentryClient` — add the capture method signature
- `SentryClient` — implement capture, including `beforeSend` callback execution
- `NoOpSentryClient` — add no-op stub

## Checklist for Adding a New Namespaced Option

1. `SentryOptions.java` — nested options class + getter/setter on `SentryOptions`
2. `ExternalOptions.java` — `@Nullable` fields + wiring in `from()`
3. `SentryOptions.java` `merge()` — apply external options to namespaced class
4. `ManifestMetadataReader.java` — Android manifest support (if Android-relevant)
5. `SentryAutoConfigurationTest.kt` — Spring Boot property binding tests (all three Spring Boot modules)
6. Tests for all of the above (`SentryOptionsTest`, `ExternalOptionsTest`, `ManifestMetadataReaderTest`)
7. Run `./gradlew apiDump` — the nested class and its methods appear in `sentry.api`
