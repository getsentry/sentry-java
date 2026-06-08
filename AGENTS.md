# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

## STOP — Required Reading (Do This First)

Before doing ANYTHING else (including answering questions), you MUST use the Read tool to load these files:
1. `.cursor/rules/coding.mdc`
2. `.cursor/rules/overview_dev.mdc`

Then identify and read any topically relevant `.cursor/rules/*.mdc` files for the area you're working on (e.g., `opentelemetry.mdc` for OTel work, `metrics.mdc` for metrics work). Use the Glob tool on `.cursor/rules/*.mdc` to discover available rule files.

Do NOT skip this step. Do NOT proceed without reading these files first.

## Project Overview

This is the Sentry Java/Android SDK - a comprehensive error monitoring and performance tracking SDK for Java and Android applications. The repository contains multiple modules for different integrations and platforms.

## Build System

The project uses **Gradle** with Kotlin DSL. Key build files:
- `build.gradle.kts` - Root build configuration
- `settings.gradle.kts` - Multi-module project structure
- `buildSrc/` and `build-logic/` - Custom build logic and plugins
- `Makefile` - High-level build commands

## Essential Commands

### Development Workflow
```bash
# Format code and regenerate .api files (REQUIRED before committing)
./gradlew spotlessApply apiDump

# Run all tests and linter
./gradlew check

# Build entire project
./gradlew build

# Create coverage reports
./gradlew jacocoTestReport koverXmlReportRelease

# Generate documentation
./gradlew aggregateJavadocs
```

### Testing
```bash
# Run unit tests for a specific file
./gradlew ':<module>:testDebugUnitTest' --tests="*<file name>*" --info

# Run system tests (requires Python virtual env)
make systemTest

# Run specific test suites
./gradlew :sentry-android-core:testDebugUnitTest
./gradlew :sentry:test
```

### Code Quality
```bash
# Check code formatting
./gradlew spotlessJavaCheck spotlessKotlinCheck

# Apply code formatting
./gradlew spotlessApply

# Update API dump files (after API changes)
./gradlew apiDump

# Dependency updates check
./gradlew dependencyUpdates -Drevision=release
```

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
7. **Propose commit**: As final step, git stage relevant files and propose (but not execute) a single git commit command

## Repository Skills

This repo ships task-specific skills (declared in `agents.toml`, sources under `.agents/skills`). Prefer them over performing the steps manually:
- **`create-java-pr`**: Branch, format, `apiDump`, commit, push, open PR, and add the changelog entry (automates the PR workflow above)
- **`test`**: Run unit or system tests for a module or a specific class
- **`check-code-attribution`**: Verify third-party code attribution on the current branch (see Third-Party Code Attribution below)
- **`btrace-perfetto`**: Capture and compare Perfetto traces for Android performance work

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
- **Formatting**: Enforced via Spotless - always run `./gradlew spotlessApply` before committing
- **API Compatibility**: Binary compatibility is enforced - run `./gradlew apiDump` after API changes

### Testing Requirements
- Write comprehensive unit tests for new features
- Android modules require both unit tests and instrumented tests where applicable
- System tests validate end-to-end functionality with sample applications
- Coverage reports are generated for both JaCoCo (Java/Android) and Kover (KMP modules)

### Contributing Guidelines
1. Follow existing code style and language
2. Do not modify API files (e.g. sentry.api) manually - run `./gradlew apiDump` to regenerate them
3. Write comprehensive tests
4. New features must be **opt-in by default** - extend `SentryOptions` or similar Option classes with getters/setters
5. Consider backwards compatibility

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

User-facing changes get an entry under the `## Unreleased` section of `CHANGELOG.md`. When rebasing onto `main`, a release may have renamed the `## Unreleased` heading your entry was under to a version number — if so, move your entry back into an `## Unreleased` section at the top of the file (create it if it no longer exists). See `.cursor/rules/pr.mdc` for the full changelog and PR workflow.

## Useful Resources

- Main SDK documentation: https://develop.sentry.dev/sdk/overview/
- Internal contributing guide: https://docs.sentry.io/internal/contributing/
- Git commit message conventions: https://develop.sentry.dev/engineering-practices/commit-messages/

This SDK is production-ready and used by thousands of applications. Changes should be thoroughly tested and maintain backwards compatibility.
