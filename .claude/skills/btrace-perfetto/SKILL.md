---
name: btrace-perfetto
description: Capture and compare Perfetto traces using btrace 3.0 on an Android device. Use when asked to "profile", "capture trace", "perfetto trace", "btrace", "compare traces", "record perfetto", "trace touch events", "measure performance on device", or benchmark Android SDK changes between branches.
allowed-tools: Bash, Read, Write, Edit, Glob, Grep, WebFetch, AskUserQuestion
argument-hint: "[branch1] [branch2] [duration] [sql-query]"
---

# btrace Perfetto Trace Capture

Capture Perfetto traces with btrace 3.0 on a connected Android device, optionally comparing two branches. Opens results in Perfetto UI with a prefilled SQL query.

## Prerequisites

Before starting, verify:

1. **Connected device**: `adb devices` shows a device (Android 8.0+, 64-bit)
2. **btrace CLI jar**: Check if `tools/btrace/rhea-trace-shell.jar` exists. If not, download it:
   ```bash
   mkdir -p tools/btrace/traces
   curl -sL "https://repo1.maven.org/maven2/com/bytedance/btrace/rhea-trace-processor/3.0.0/rhea-trace-processor-3.0.0.jar" \
     -o tools/btrace/rhea-trace-shell.jar
   ```
3. **Device ABI**: Run `adb shell getprop ro.product.cpu.abi` — btrace only supports arm64-v8a and armeabi-v7a (no x86/x86_64)

## Step 1: Parse Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| branch1 | current branch | First branch to trace |
| branch2 | `main` | Second branch to compare against |
| duration | `20` | Trace duration in seconds |
| sql-query | see below | SQL query to prefill in Perfetto UI |

If no arguments are provided, ask the user what they want to trace and which branches to compare. If only one branch is given, capture only that branch (no comparison).

## Step 2: Integrate btrace into Sample App

The sample app is at `sentry-samples/sentry-samples-android/`.

### 2a: Add btrace dependency

In `sentry-samples/sentry-samples-android/build.gradle.kts`, add to the `dependencies` block:

```kotlin
implementation("com.bytedance.btrace:rhea-inhouse:3.0.0")
```

### 2b: Restrict ABI to device architecture

The btrace native library (shadowhook) does not support x86/x86_64. Replace the `ndk` abiFilters line in `defaultConfig` to match the connected device:

```kotlin
ndk { abiFilters.addAll(listOf("arm64-v8a")) }
```

Adjust if the device reports a different ABI.

### 2c: Initialize btrace in Application

In `MyApplication.java`, add `attachBaseContext`:

```java
import android.content.Context;
import com.bytedance.rheatrace.RheaTrace3;

// Add before onCreate:
@Override
protected void attachBaseContext(Context base) {
    super.attachBaseContext(base);
    RheaTrace3.init(base);
}
```

**Important**: The package is `com.bytedance.rheatrace`, not `com.bytedance.btrace`.

## Step 3: Build and Install

```bash
./gradlew :sentry-samples:sentry-samples-android:installDebug
```

## Step 4: Capture Trace

For each branch to trace:

### 4a: Set btrace properties and launch app

```bash
adb shell setprop debug.rhea3.startWhenAppLaunch 1
adb shell setprop debug.rhea3.waitTraceTimeout 60
adb shell am force-stop io.sentry.samples.android
sleep 1
adb shell am start -n io.sentry.samples.android/.MainActivity
```

The app must be started AFTER `debug.rhea3.startWhenAppLaunch` is set, otherwise the trace server won't initialize.

### 4b: Tell the user to interact with the app, then capture

```bash
java -jar tools/btrace/rhea-trace-shell.jar \
  -a io.sentry.samples.android \
  -t ${duration} \
  -waitTraceTimeout 60 \
  -o tools/btrace/traces/${branch_name}.pb \
  sched
```

Do NOT use the `-r` flag — it fails to resolve the launcher activity because LeakCanary registers a second one. Launch the app manually in step 4a instead.

### 4c: Switch branches for comparison

When capturing a second branch:

1. Stash the btrace integration changes:
   ```bash
   git stash push -m "btrace integration" -- \
     sentry-samples/sentry-samples-android/build.gradle.kts \
     sentry-samples/sentry-samples-android/src/main/java/io/sentry/samples/android/MyApplication.java
   ```
2. Checkout the other branch
3. Pop the stash: `git stash pop`
4. Rebuild and install: `./gradlew :sentry-samples:sentry-samples-android:installDebug`
5. Repeat steps 4a and 4b with a different output filename
6. Switch back to the original branch and restore files

## Step 5: Open in Perfetto UI

Generate a viewer HTML and serve it locally. Use the template at `assets/viewer-template.html` as a base — copy it to `tools/btrace/traces/viewer.html` and replace the placeholder values:

- `TRACE_FILES`: array of `{file, title}` objects for each captured trace
- `SQL_QUERY`: the SQL query to prefill

The SQL query is passed via the URL hash parameter: `https://ui.perfetto.dev/#!/?query=...`

The trace data is sent via the postMessage API (required for local files — URL deep-linking does not work with `file://`).

Start a local HTTP server and open the viewer:

```bash
cd tools/btrace/traces && python3 -m http.server 8008 &
open http://localhost:8008/viewer.html
```

### Default SQL Query

If no custom query is provided, use:

```sql
SELECT
  s.name AS slice_name,
  s.dur / 1e6 AS dur_ms,
  s.ts,
  t.name AS track_name
FROM slice s
JOIN thread_track t ON s.track_id = t.id
WHERE s.name GLOB '*SentryWindowCallback.dispatch*'
ORDER BY s.ts
```

## Cleanup

After tracing is complete, remind the user that the btrace integration changes to the sample app should NOT be committed. The `tools/btrace/` directory is gitignored.

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `No compatible library found [shadowhook]` | Restrict `ndk.abiFilters` to arm64-v8a only |
| `package com.bytedance.btrace does not exist` | Use `com.bytedance.rheatrace` (not `btrace`) |
| `ResolverActivity does not exist` with `-r` flag | Don't use `-r`; launch the app manually before capturing |
| `wait for trace ready timeout` on download | Set `debug.rhea3.startWhenAppLaunch=1` BEFORE launching the app, and use `-waitTraceTimeout 60` |
| Empty jar file (0 bytes) | Download from Maven Central (`repo1.maven.org`), not `oss.sonatype.org` |
| `FileNotFoundException` on sampling download | App was already running when properties were set; force-stop and relaunch |
