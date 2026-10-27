# Android performance-impact and integration tests

This directory is a separate Gradle build that includes the SDK build, so run its tasks with
`./gradlew -p sentry-android-integration-tests <task>`. The SDK build doesn't include it, so
`./gradlew` from the repository root doesn't build, lint or format these apps.

* [Espresso-based UI tests](./sentry-uitest-android) - run within SauceLabs (see /.sauce/*.yml)
  * Also used for compatibility test matrix against new AGP versions
