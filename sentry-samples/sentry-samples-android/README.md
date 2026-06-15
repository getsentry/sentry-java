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
./gradlew :sentry-samples:sentry-samples-android:installDebug -PuseSagp
```

In Android Studio, add `useSagp=` (empty value) to `gradle.properties`, or pass `-PuseSagp` as a Gradle project property.

## Build modes

### With or without SAGP

The sample app can be built with or without the SAGP.

| Gradle Property | Required | Purpose                                                                                         |
|-----------------|----------|-------------------------------------------------------------------------------------------------|
| `useSagp`       | No       | When present, apply SAGP when building the sample app. Omit the property to build without SAGP. |

You can configure SAGP properties via the lambda passed to `extensions.configure<SentryPluginExtension>("sentry")` in the sample app's
`build.gradle.kts` file.

### Testing an unpublished SAGP build

`-PuseSagp` builds check `mavenLocal()` first when resolving SAGP. To test a local SAGP branch:

1. In your `sentry-android-gradle-plugin` checkout, temporarily set a unique local version in `plugin-build/gradle.properties` (e.g.
   `6.10.0-LOCAL`) and publish to Maven Local:

```
./gradlew -p plugin-build publishToMavenLocal
```

Re-run `publishToMavenLocal` after each SAGP change.

2. Temporarily bump the `sagp` pin in `gradle/libs.versions.toml` to match that version.

Then build from sentry-java:

```
./gradlew :sentry-samples:sentry-samples-android:installDebug -PuseSagp
```

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
