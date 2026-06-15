# Sentry Sample Android App

Sample application demonstrating how to use the Sentry Android SDK, including core functionality (error reporting, tracing, session replay,
profiling) and integrations (Compose, OkHttp, etc.).

## How to run it?

Install the app on your device or emulator:

```
./gradlew :sentry-samples:sentry-samples-android:installDebug
```

or simply open the project in Android Studio and run the `sentry-samples-android` configuration.

You can also apply the [Sentry Android Gradle Plugin](https://github.com/getsentry/sentry-android-gradle-plugin) (SAGP) when building (not applied by default):

```
./gradlew :sentry-samples:sentry-samples-android:installDebug -PuseSagp=true
```

In Android Studio, add `useSagp=true` to `gradle.properties` or pass it as a Gradle project property.

## Build modes

### With or without SAGP

The sample app can be built with or without the SAGP.

| Gradle Property | Required                 | Purpose                                                                                                        |
|-----------------|--------------------------|----------------------------------------------------------------------------------------------------------------|
| `useSagp`       | No (defaults to `false`) | When `true`, apply SAGP when building the sample app. When false or absent, build the sample app without SAGP. |

You can configure SAGP properties via the lambda passed to `extensions.configure<SentryPluginExtension>("sentry")` in the sample app's
`build.gradle.kts` file.

### Builds against your local sentry-java branch

Regardless of `useSagp`, the sample always depends on sentry-java modules from this monorepo (e.g., `projects.sentryAndroid`). SAGP's SDK
auto-installation is disabled, so the sample never pulls a separate SDK version from Maven. Local SDK changes in your branch are picked up
directly.

## Viewing SDK output

### Locally

Debug builds enable SDK debug logging, so captured envelopes are printed to logcat (tag `Sentry`):

```
adb logcat -s Sentry
```

### On Sentry UI

By default, SDK output produced by the sample app appears under the [sentry-sdk test project](https://sentry-sdks.sentry.io/issues/?project=5428559).
To redirect them to your own project, replace the test DSN (i.e., the `io.sentry.dsn` `meta-data` value in `src/main/AndroidManifest.xml`
with your own.
