# Sentry Sample Android App

Sample application demonstrating how to use the Sentry Android SDK, including core functionality (error reporting, tracing, session replay, profiling) and integrations (Compose, OkHttp, SQLDelight, etc.).

## How to run it?

Install the app on your device or emulator:

```
./gradlew :sentry-samples:sentry-samples-android:installDebug
```

or simply open the project in Android Studio and run the `sentry-samples-android` configuration.

## Viewing events locally

Debug builds enable SDK debug logging, so captured envelopes are printed to logcat (tag `Sentry`):

```
adb logcat -s Sentry
```

## Viewing events on Sentry UI

By default, events appear under the [sentry-sdk test project](https://sentry-sdks.sentry.io/issues/?project=5428559).
To redirect them to your own project, replace the test DSN (i.e., the `io.sentry.dsn` `meta-data` value)
in `src/main/AndroidManifest.xml` with your own.
