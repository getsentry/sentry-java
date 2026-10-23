# Sentry Sample Android App

Sample application demonstrating how to use the Sentry Android SDK, including core functionality (error reporting, tracing, session replay,
profiling) and integrations (Compose, OkHttp, SQLite, etc.).

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

For LLM-driven verification, use `adb` to inspect and interact with the running emulator. Compose-based screens often expose stable
`Modifier.testTag()` values, which can be used with the UI hierarchy dump to locate controls reliably.

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

## Native crash scenarios

The Errors screen includes the baseline **Native Crash**. The **Native (inproc)** or
**Native (outproc)** screen includes scenarios for exhausted memory, allocator lock, heap corruption,
FD exhaustion, stack corruption, stack overflow, and concurrent crashes. The `many_threads` scenario
with 500 idle threads is available through the activity and runner.
The stack overflow faults in the recursive function. The other scenarios fault in
`trigger_null_deref` after setting up the bad state.

Build a release APK with extracted native libraries (the sample default):

```sh
./gradlew :sentry-samples:sentry-samples-android:installRelease
```

Pass `-PsentryLegacyPackaging=false` to keep native libraries in the APK instead. To compare
tombstone capture, change `io.sentry.tombstone.enable` in `src/main/AndroidManifest.xml` and rebuild.

Run a scenario without interacting with the UI:

```sh
adb shell am start -n io.sentry.samples.android/.NativeCrashActivity \
  --es scenario locked_heap --es run_id my-run
```

The activity waits one second, sets `sample.scenario` and `sample.run`, then calls the native
scenario. Native code logs `status=fault scenario=... run_id=...` under `SentryCrashScenario`
immediately before the fault (before recursion for stack overflow). An unsupported or failed
setup logs `status=error` instead. Heap corruption requires a writable heap mapping with at
least 4 MiB after an allocated buffer, so it can overwrite neighboring chunks without faulting
on an unmapped page first.

Run all scenarios ten times on a selected emulator or device:

```sh
python3 sentry-samples/sentry-samples-android/scripts/crash-scenarios.py \
  --serial emulator-5554 --runs 10 > results.csv
```

Use `--scenario baseline locked_heap` to select scenarios, `--package io.sentry.samples.android.debug`
for a debug build, and `--output-dir` to keep each backend's logs separately. The runner prints
CSV with each run ID, status, and milliseconds from the native fault marker to ActivityManager's
matching process death message. It saves logcat per run, relaunches the app after each crash,
and waits ten seconds for next-launch delivery (`--delivery-wait`). A timeout or setup failure
produces no timing and makes the runner exit with a failure status. Timeouts are force-stopped
before the next run.

Compare release builds on an API 36 emulator and a device, with each packaging and tombstone
setting. Find events in Sentry using `sample.run:<run_id>` and record delivery (at crash time,
next launch, or never), crashing-thread frame count, captured thread count, minidump attachment,
and whether a tombstone was merged. CSV timings measure process lifetime after the fault marker,
including crash handling by the SDK and Android.
