# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
./gradlew :sentry-android-integration-tests:sentry-uitest-android:assembleRelease
./gradlew :sentry-android-integration-tests:sentry-uitest-android:assembleAndroidTest -DtestBuildType=release

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

## Module Architecture

The repository is organized into multiple modules:

### Core Modules
- **`sentry`** - Core Java SDK implementation
- **`sentry-android-core`** - Core Android SDK implementation
- **`sentry-android`** - High-level Android SDK

### Integration Modules
- **Spring Framework**: `sentry-spring*`, `sentry-spring-boot*`
- **Logging**: `sentry-logback`, `sentry-log4j2`, `sentry-jul`
- **Web**: `sentry-servlet*`, `sentry-okhttp`, `sentry-apache-http-client-5`
- **GraphQL**: `sentry-graphql*`, `sentry-apollo*`
- **Android UI**: `sentry-android-fragment`, `sentry-android-navigation`, `sentry-compose`
- **Reactive**: `sentry-reactor`, `sentry-ktor-client`
- **Monitoring**: `sentry-opentelemetry*`, `sentry-quartz`

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

## Domain-Specific Knowledge Areas

For complex SDK functionality, refer to the detailed cursor rules in `.cursor/rules/`:

- **Scopes and Hub Management**: See `.cursor/rules/scopes.mdc` for details on `IScopes`, scope types (global/isolation/current), thread-local storage, forking behavior, and v7→v8 migration patterns
- **Event Deduplication**: See `.cursor/rules/deduplication.mdc` for `DuplicateEventDetectionEventProcessor` and `enableDeduplication` option
- **Offline Behavior and Caching**: See `.cursor/rules/offline.mdc` for envelope caching, retry logic, transport behavior, and Android vs JVM differences
- **OpenTelemetry Integration**: See `.cursor/rules/opentelemetry.mdc` for agent vs agentless modes, span processing, context propagation, and configuration
- **System Testing (E2E)**: See `.cursor/rules/e2e_tests.mdc` for system test framework, mock server setup, and CI workflows

### Usage Pattern
When working on these specific areas, read the corresponding cursor rule file first to understand the detailed architecture, then proceed with implementation.

## Useful Resources

- Main SDK documentation: https://develop.sentry.dev/sdk/overview/
- Internal contributing guide: https://docs.sentry.io/internal/contributing/
- Git commit message conventions: https://develop.sentry.dev/engineering-practices/commit-messages/

This SDK is production-ready and used by thousands of applications. Changes should be thoroughly tested and maintain backwards compatibility.