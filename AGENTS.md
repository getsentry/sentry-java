# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

## Domain-Specific Rules

This file covers the whole repository. Before working on a specific area, read the matching
rule file in `.cursor/rules/`:

| Rule | Read it when working on |
|---|---|
| `api` | Public API surface, binary compatibility, `.api` files, `apiDump`, `IScope`/`IScopes`/`Sentry` static API, protocol classes |
| `options` | `SentryOptions`, namespaced options, `ExternalOptions`, `sentry.properties`, `ManifestMetadataReader`, Spring Boot properties |
| `scopes` | Scope management, forking, lifecycle, `ScopeType`, thread-local storage, scope bleeding, Hub → Scopes migration |
| `deduplication` | Duplicate event detection, `DuplicateEventDetectionEventProcessor`, `enableDeduplication` |
| `offline` | Caching, envelope storage, network failure handling, retries, `AsyncHttpTransport`, `EnvelopeCache`, rate limiting |
| `feature_flags` | `addFeatureFlag`, `FeatureFlagBuffer`, `maxFeatureFlags`, LaunchDarkly and OpenFeature integrations |
| `metrics` | `Sentry.metrics()`, `IMetricsApi`, count/distribution/gauge, `MetricsBatchProcessor` |
| `queues` | Queue tracing, `queue.publish`/`queue.process`, `enableQueueTracing`, Kafka instrumentation, messaging span data |
| `continuous_profiling_jvm` | `sentry-async-profiler`, `IContinuousProfiler`, `ProfileChunk`, JFR files, `ProfileLifecycle` |
| `opentelemetry` | `sentry-opentelemetry-*`, agent vs agentless, span processing, sampling, context propagation |
| `new_module` | Adding a new integration or sample module |
| `e2e_tests` | System tests, sample applications, `system-test-runner.py`, mock Sentry server |

Rules can be combined — a tracing scope issue may need both `scopes` and `opentelemetry`.
There is no rule for Android profiling yet; read the `sentry-android-core` profiling code
directly and fetch related rules such as `options`, `offline`, or `api` as needed.

## Project Overview

This is the Sentry Java/Android SDK - a comprehensive error monitoring and performance tracking SDK for Java and Android applications. The repository contains multiple modules for different integrations and platforms.

## Build System

The project uses **Gradle** with Kotlin DSL. Key build files:
- `build.gradle.kts` - Root build configuration
- `settings.gradle.kts` - Multi-module project structure
- `buildSrc/` and `build-logic/` - Custom build logic and plugins
- `Makefile` - High-level build commands

## Essential Commands

```bash
# Format code and regenerate .api files (REQUIRED before committing)
./gradlew spotlessApply apiDump

# Run all tests and linter
./gradlew check

# Generate documentation
./gradlew aggregateJavadocs

# Dependency updates check
./gradlew dependencyUpdates -Drevision=release
```

To run tests, use the `test` skill rather than composing the Gradle invocation by hand — it
resolves the per-module test task and the unit-test vs system-test split for you.

### Android-Specific Commands
```bash
# Assemble Android test APKs
./gradlew :sentry-android-integration-tests:sentry-uitest-android:assembleRelease :sentry-android-integration-tests:sentry-uitest-android:assembleAndroidTest

# Run critical UI tests
./scripts/test-ui-critical.sh
```

## Development Workflow Rules

### Planning and Implementation Process
1. **First think through the problem**: Read the codebase for relevant files and propose a plan
2. **Check in before beginning**: Verify the plan before starting implementation
3. **Use todo tracking**: Work through todo items, marking them as complete as you go
4. **High-level communication**: Give high-level explanations of changes made, not step-by-step descriptions
5. **Simplicity first**: Make every task and code change as simple as possible. Avoid massive or complex changes. Impact as little code as possible.
6. **Format and regenerate**: Once done, format code and regenerate .api files: `./gradlew spotlessApply apiDump`
7. **Propose commit**: As final step, git stage relevant files and propose (but not execute) a single git commit command. This applies to implementation work; when the task is to open a PR, the `create-java-pr` skill takes over from here and does commit, push, and open it.

## Repository Skills

This repo ships task-specific skills, declared in `agents.toml` with sources under
`.agents/skills`. Your harness already lists them with their descriptions — prefer them over
performing the steps manually.

## Module Architecture

The repository is organized into multiple modules:

### Core Modules
- **`sentry`** - Core Java SDK implementation
- **`sentry-android-core`** - Core Android SDK implementation
- **`sentry-android`** - High-level Android SDK
- **`sentry-android-ndk`** - Native (NDK) crash handling

### Integration Modules
- **Spring Framework**: `sentry-spring*`, `sentry-spring-boot*`
- **Logging**: `sentry-logback`, `sentry-log4j2`, `sentry-jul`, `sentry-android-timber`
- **Web**: `sentry-servlet*`, `sentry-okhttp`, `sentry-openfeign`, `sentry-apache-http-client-5`
- **GraphQL**: `sentry-graphql*`, `sentry-apollo*`
- **Android UI**: `sentry-android-fragment`, `sentry-android-navigation`, `sentry-compose`
- **Session Replay**: `sentry-android-replay`
- **Database**: `sentry-jdbc`, `sentry-android-sqlite`, `sentry-jcache`
- **Reactive**: `sentry-reactor`, `sentry-ktor-client`
- **Feature Flags**: `sentry-launchdarkly-android`, `sentry-launchdarkly-server`, `sentry-openfeature`
- **Queues**: `sentry-kafka`
- **Profiling**: `sentry-async-profiler` (JVM continuous profiling)
- **Monitoring**: `sentry-opentelemetry*`, `sentry-quartz`
- **Other**: `sentry-spotlight`, `sentry-kotlin-extensions`, `sentry-android-distribution`

### Utility Modules
- **`sentry-test-support`** - Shared test utilities
- **`sentry-system-test-support`** - System testing infrastructure
- **`sentry-samples`** - Example applications
- **`sentry-bom`** - Bill of Materials for dependency management

### Key Architectural Patterns
- **Multi-platform**: Supports JVM, Android, and Kotlin Multiplatform (Compose modules)
- **Modular Design**: Each integration is a separate module with minimal dependencies
- **Options Pattern**: Features are opt-in via `SentryOptions` and similar configuration classes
- **Transport Layer**: Pluggable transport implementations for different environments
- **Scope Management**: Thread-safe scope/context management for error tracking

## Development Guidelines

### Code Style
- **Languages**: Java 8+ and Kotlin
- **Formatting**: Enforced via Spotless
- **API Compatibility**: Binary compatibility is enforced. `.api` files are generated, never hand-edited

### Exception Handling

**Never introduce a new `catch (Throwable)`.** Catch the narrowest type the guarded code can
actually throw. The repository still contains many pre-existing broad catches; they are legacy,
not a precedent to follow.

A broad catch swallows `OutOfMemoryError`, `StackOverflowError`, `ThreadDeath` and `LinkageError` —
conditions the JVM/ART cannot recover from and that leave the process in an undefined state — and
it hides real bugs in our own code behind a log line.

"The SDK must never crash the host application" is not a reason to catch `Throwable`. That goal is
served by `io.sentry.util.ExceptionUtils.rethrowIfFatal`, which lets the non-recoverable throwables
through while leaving everything else for the caller to log or ignore:

```java
try {
  doSomethingRisky();
} catch (Throwable t) {
  ExceptionUtils.rethrowIfFatal(t);
  options.getLogger().log(SentryLevel.ERROR, "Failed to do something risky", t);
}
```

Apply that pattern only where a broad catch is genuinely unavoidable — an entry point that runs
arbitrary user code or third-party callbacks. Everywhere else, name the exception types. Say in the
PR description why the broad catch is necessary.

### Concurrency

**Never hold a lock across a call that can re-enter the SDK.** Read what you need under the lock,
release it, then make the call.

Finishing a span or transaction, capturing an event, and invoking a user-supplied callback all run
arbitrary SDK code on the calling thread. `SentryTracer.finish()` calls `scopes.captureTransaction(...)`
synchronously, which runs every registered `EventProcessor` — and those processors take their own
locks and call back into SDK components. A lock held across such a call is therefore acquired in the
opposite order from the processor's, which is a deadlock. On Android the blocked thread is usually
the main thread, so it surfaces to users as an ANR rather than a hang.

```java
// Wrong: the lock is held while finish() captures the transaction and runs event processors,
// which take their own lock and then call back into this class.
try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
  final @Nullable ITransaction transaction = ownedTransaction;
  if (transaction != null && !transaction.isFinished()) {
    transaction.finish(SpanStatus.OK);
  }
}

// Right: read the field under the lock, call finish() outside it.
final @Nullable ITransaction transaction;
try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
  transaction = ownedTransaction;
}
if (transaction != null && !transaction.isFinished()) {
  transaction.finish(SpanStatus.OK);
}
```

The same applies to logging through `options.getLogger()`, invoking a listener, and any
`ISpan`/`IScopes` call reachable from a component that locks.

Moving the call out can break a compound operation that was atomic only because the lock spanned
it. When that happens, do not widen the original lock again — serialize the callers with a second
lock that the re-entrant path never acquires, and document the order the two are taken in.

Two more rules that catch most of the rest:

- **Mark a field `volatile` when it is written on one thread and read on another without a lock.**
  A plain field read is a data race, not merely a stale value.
- **Read mutable shared state once per operation.** Re-reading the same field for several
  decisions in one pass lets it change mid-pass, so the results disagree with each other.

Prefer `AutoClosableReentrantLock` over `synchronized`, matching the rest of the codebase.

### Testing Requirements
- Write comprehensive unit tests for new features
- Android modules require both unit tests and instrumented tests where applicable
- System tests validate end-to-end functionality with sample applications
- **Assertions**: For new unit tests, prefer [Google Truth](https://truth.dev/) (`com.google.common.truth.Truth.assertThat`) over `kotlin.test`/JUnit assertions for its readable, fluent API. Keep using `kotlin.test` for test structure (`@Test`, `assertFailsWith`). See `sentry/src/test/java/io/sentry/DsnTest.kt` for the style. Don't rewrite existing `kotlin.test` assertions solely to switch libraries.
- Truth is wired into the `sentry` module. When adding Truth-based tests to another module, add `testImplementation(libs.google.truth)` to that module's `build.gradle.kts`.

### Contributing Guidelines
1. Follow existing code style and language
2. Write comprehensive tests
3. New features must be **opt-in by default** - extend `SentryOptions` or similar Option classes with getters/setters
4. Consider backwards compatibility

### Third-Party Code Attribution
When adapting code from third-party libraries:
1. Add a license header at the top of the adapted file (before the `package` statement):
   ```java
   // Adapted from <Library Name>.
   // Copyright <year> <copyright holder>.
   // Licensed under the <License Name>.
   // <source URL>
   ```
2. Add a full attribution entry to `THIRD_PARTY_NOTICES.md` following the existing format (Source, License, Copyright, Scope, full license text)

3. Run the `check-code-attribution` skill locally or wait for it to be auto-run against your PR to check for required fields and verify new licenses against [Sentry's Open Source Legal Policy](https://open.sentry.io/licensing/).

### Getting PR Information

Use `gh pr view` to get PR details from the current branch. This is needed when adding changelog entries, which require the PR number.

```bash
# Get PR number for current branch
gh pr view --json number -q '.number'

# Get PR number for a specific branch
gh pr view <branch-name> --json number -q '.number'

# Get PR URL
gh pr view --json url -q '.url'
```

### Changelog

User-facing changes get an entry under the `## Unreleased` section of `CHANGELOG.md`. The
`create-java-pr` skill is the source of truth for the full changelog and PR workflow, including
subsection selection and the rebase caveat when a release renames `## Unreleased`.

## Useful Resources

- Main SDK documentation: https://develop.sentry.dev/sdk/overview/
- Internal contributing guide: https://docs.sentry.io/internal/contributing/
- Git commit message conventions: https://develop.sentry.dev/engineering-practices/commit-messages/

This SDK is production-ready and used by thousands of applications. Changes should be thoroughly tested and maintain backwards compatibility.
