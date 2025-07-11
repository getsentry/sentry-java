# sentry-java Development Guide for Claude

## Overview

sentry-java is the Java and Android SDK for Sentry. This repository contains the source code and examples for SDK usage.

## Tech Stack

- **Language**: Java and Kotlin
- **Build Framework**: Gradle

## Key Commands

```bash
# Format Code and regenerate .api file
./gradlew spotlessApply apiDump

# Run tests and lint
./gradlew check
```

## Contributing Guidelines

1. Before implementing a new feature, checkout main, pull the latest changes and branch-off
```bash
git checkout main
git pull origin main
git checkout -b markushi/[fix/feat]/[feature-name]
```
2. Follow existing code style and language
3. Do not modify the API files (e.g. sentry.api) manually, instead run `./gradlew  apiDump` to regenerate them
4. Write comprehensive tests
5. Use Kotlin only for test code and Android modules which already use Kotlin, otherwise use Java
6. New features should be opt-in by default, extend `SentryOptions` with getters and setters to enable/disable a new feature
7. Consider backwards compatibility

## Useful Resources

- Main Documentation: https://docs.sentry.io/
- Internal Contributing Guide: https://docs.sentry.io/internal/contributing/
- Git Commit messages https://develop.sentry.dev/engineering-practices/commit-messages/
