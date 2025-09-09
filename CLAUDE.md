# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the Sentry SDK for Java and Android - a comprehensive error and performance monitoring solution. The repository is a multi-module Gradle project containing:
- Core Sentry Java SDK (`sentry/`)
- Android-specific SDK modules (`sentry-android-*`)
- Framework integrations (Spring, Spring Boot, Servlet, etc.)
- Logging integrations (Logback, Log4j2, JUL)
- HTTP client integrations (OkHttp, Apache HTTP Client)
- GraphQL, Apollo, OpenTelemetry integrations
- Sample applications for testing and demonstration

## Build System

**Primary Build Tool**: Gradle with Kotlin DSL (.kts files)
**Java Version**: JDK 17 required for development, targets Java 8 compatibility
**Android Requirements**: Android SDK with NDK (can be excluded if working only on Java modules)

### Essential Build Commands

```bash
# Clean build
make clean

# Build and run tests
make compile
# or: ./gradlew build

# Format code (required for CI)
make format
# or: ./gradlew spotlessApply

# Check code formatting
make checkFormat
# or: ./gradlew spotlessJavaCheck spotlessKotlinCheck

# Run tests and lint
make check
# or: ./gradlew check

# Update API compatibility files (after public API changes)
make api
# or: ./gradlew apiDump

# Generate Javadocs
make javadocs
# or: ./gradlew aggregateJavadocs

# Create coverage reports
make createCoverageReports
# or: ./gradlew jacocoTestReport koverXmlReportRelease

# Dry release (local Maven deploy)
make dryRelease
# or: ./gradlew aggregateJavadocs distZip --no-build-cache --no-configuration-cache
```

### Running Specific Tests

```bash
# Run tests for a specific module
./gradlew :sentry:test
./gradlew :sentry-android-core:test

# Run Android instrumentation tests
./gradlew :sentry-android-integration-tests:sentry-uitest-android:assembleAndroidTest

# System tests (requires Python setup)
make setupPython
make systemTest
```

## Code Architecture

### Core Module Structure

**sentry/**: Core Java SDK with protocol definitions, transport, scoping, and event processing
- `io.sentry`: Main public API classes (Sentry, Hub, Scope, etc.)
- `io.sentry.protocol`: Sentry protocol data structures
- `io.sentry.transport`: HTTP transport and envelope handling
- `io.sentry.util`: Utility classes and helpers

**sentry-android-core/**: Core Android functionality
- `io.sentry.android.core`: Android-specific implementations and integrations
- Lifecycle tracking, ANR detection, performance monitoring
- Activity/Fragment integrations, breadcrumb collection

**Integration Modules**: Each integration follows the pattern `sentry-{integration-name}`
- Self-contained with minimal dependencies
- Consistent naming: `io.sentry.{integration}` package structure
- Include both main implementation and test classes

### Key Patterns

**Integration Classes**: Most integrations implement `Integration` interface for lifecycle management
**Event Processors**: Implement `EventProcessor` to modify events before sending
**Options**: Each integration typically extends `SentryOptions` or adds configuration
**Transport**: Abstracted through `ITransport` interface with HTTP and stdout implementations

## Development Workflow

### Code Style
- **Formatting**: Spotless is enforced - always run `make format` before committing
- **API Compatibility**: Binary compatibility is validated - update API files with `make api` after public API changes
- **Java 8 Compatibility**: Code must compile and run on Java 8 despite being built with JDK 17

### Testing Strategy
- **Unit Tests**: Comprehensive coverage required, use JUnit 4/5 and Mockito
- **Android Tests**: Robolectric for unit tests, instrumentation tests for integration
- **System Tests**: Python-based end-to-end testing of sample applications
- **Coverage**: Jacoco for Java/Android modules, Kover for Kotlin Multiplatform (sentry-compose)

### Module Dependencies
- Keep integration modules lightweight with minimal external dependencies
- Android modules depend on `sentry-android-core`, which depends on `sentry`
- Spring integrations have both javax and jakarta variants for compatibility

### Common Tasks
- **Adding New Integration**: Create new module, add to `settings.gradle.kts`, follow existing integration patterns
- **Version Updates**: Modify `gradle.properties` `versionName` property
- **Platform-Specific Code**: Use `Platform.isAndroid()` checks for conditional Android functionality

## CI/CD Notes
- GitHub Actions run build, tests, and quality checks
- Spotless formatting and API compatibility are enforced
- Android builds require SDK/NDK setup
- Coverage reports are generated and uploaded to Codecov