# Android sample, performance-impact and integration tests

This directory is a separate Gradle build that includes the SDK build, so run its tasks with
`./gradlew -p sentry-android-integration-tests <task>`. The SDK build doesn't include it, so
`./gradlew` from the repository root doesn't build, lint or format these apps.
`gradle.properties`, `gradlew`, `gradle/wrapper` and `gradle/gradle-daemon-jvm.properties` are
symlinks to the SDK build's, so both builds use the same Gradle, JDK and settings.

* [Sample app](./sentry-samples-android)
* [Macrobenchmark](./sentry-uitest-android-macrobenchmark) - cold start of the sample app, run locally or within SauceLabs (see /.sauce/*.yml)
* [Espresso-based UI tests](./sentry-uitest-android) - run on an emulator in the AGP matrix workflow
* [Maestro-based critical UI tests](./sentry-uitest-android-critical) - run by the UI Tests Critical workflow
* [App size test](./test-app-size) - built by the SDK Size Analysis workflow
