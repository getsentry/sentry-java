---
name: btrace-perfetto
description: Capture and compare Perfetto traces using btrace 3.0 on an Android device. Use when asked to "profile", "capture trace", "perfetto trace", "btrace", "compare traces", "record perfetto", "trace touch events", "measure performance on device", or benchmark Android SDK changes between branches.
allowed-tools: Bash, Read, Write, Edit, Glob, Grep, WebFetch, AskUserQuestion
argument-hint: "[branch1] [branch2] [duration] [sql-query]"
---

# btrace Perfetto Trace Capture

Capture Perfetto traces with btrace 3.0 on a connected Android device, optionally comparing two branches. Opens results in Perfetto UI with a prefilled SQL query. After capture, query traces locally with `trace_processor` to compute comparison stats.

## Prerequisites

Before starting, verify:

1. **Connected device**: `adb devices` shows a device (Android 8.0+, 64-bit)
2. **btrace CLI jar**: Check if `tools/btrace/rhea-trace-shell.jar` exists. If not, download it:
   ```bash
   mkdir -p tools/btrace/traces
   curl -sL "https://repo1.maven.org/maven2/com/bytedance/btrace/rhea-trace-processor/3.0.0/rhea-trace-processor-3.0.0.jar" \
     -o tools/btrace/rhea-trace-shell.jar
   ```
3. **Perfetto trace_processor**: Check if `/tmp/trace_processor` exists. If not, download it:
   ```bash
   # Download trace_processor (--fail ensures HTTP errors don't leave a file behind)
   curl -sSL --fail "https://get.perfetto.dev/trace_processor" -o /tmp/trace_processor

   # Verify magic bytes directly — file(1) output is too inconsistent across
   # versions/platforms to rely on for scripts or PIE binaries.
   magic=$(head -c 4 /tmp/trace_processor 2>/dev/null | od -An -vtx1 -N4 | tr -d ' \n')
   case "$magic" in
     2321*)                               ;; # #! shebang (script)
     7f454c46)                            ;; # ELF (Linux)
     cffaedfe|cefaedfe|feedfacf|feedface) ;; # Mach-O (macOS)
     cafebabe)                            ;; # Mach-O universal
     *)
       echo "Error: Downloaded file is not a valid script or executable (magic: ${magic:-empty})"
       rm -f /tmp/trace_processor
       exit 1
       ;;
   esac

   # Make executable only after verification
   chmod +x /tmp/trace_processor
   ```
4. **Device ABI**: Run `adb shell getprop ro.product.cpu.abi` — btrace only supports arm64-v8a and armeabi-v7a (no x86/x86_64)

## Step 1: Parse Arguments

| Argument | Default | Description |
|----------|---------|-------------|
| branch1 | current branch | First branch to trace |
| branch2 | `main` | Second branch to compare against |
| duration | `30` | Trace duration in seconds |
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

### 2d: Add ProGuard keep rules (release builds only)

Only needed when building release. In `sentry-samples/sentry-samples-android/proguard-rules.pro`, add:

```
-keep class com.bytedance.rheatrace.** { *; }
-keepnames class io.sentry.** { *; }
```

The first rule prevents R8 from stripping btrace's HTTP server classes (fails with `SocketException` otherwise). The second preserves Sentry class and method names so they appear readable in the Perfetto trace instead of obfuscated single-letter names.

## Step 3: Build and Install

Prefer **debug builds** — they provide richer tracing instrumentation (Handler, MessageQueue, Monitor:Lock slices visible) which is essential for comparing internal SDK behavior. Use the default 1kHz btrace sampling rate for debug builds.

```bash
./gradlew :sentry-samples:sentry-samples-android:installDebug
```

**Release builds** are useful when you need to measure real-world performance without StrictMode/debuggable overhead or with R8 optimizations. Require the ProGuard keep rules from step 2d. Use `-sampleInterval 333000` (333μs / 3kHz) for finer granularity since release code runs faster.

```bash
./gradlew :sentry-samples:sentry-samples-android:installRelease
```

## Step 4: Capture Trace

For each branch to trace:

### 4a: Set btrace properties and launch app

Clear any stale port files, set properties, and launch:

```bash
adb shell "rm -rf /storage/emulated/0/Android/data/io.sentry.samples.android/files/rhea-port"
adb shell setprop debug.rhea3.startWhenAppLaunch 1
adb shell setprop debug.rhea3.waitTraceTimeout 60
adb shell am force-stop io.sentry.samples.android
sleep 2
adb shell am start -n io.sentry.samples.android/.MainActivity
sleep 5
```

The app must be started AFTER `debug.rhea3.startWhenAppLaunch` is set, otherwise the trace server won't initialize. The 5s sleep after launch gives the btrace HTTP server time to start.

### 4b: Play a sound to signal the user, then capture

Play a sound when tracing actually starts so the user knows to begin interacting. Pipe btrace output through a loop that triggers the sound on the "start tracing" line:

```bash
java -jar tools/btrace/rhea-trace-shell.jar \
  -a io.sentry.samples.android \
  -t ${duration} \
  -waitTraceTimeout 60 \
  -o tools/btrace/traces/${branch_name}.pb \
  sched 2>&1 | while IFS= read -r line; do
    echo "$line"
    if [[ "$line" == *"start tracing"* ]]; then
      afplay -v 1.5 /System/Library/Sounds/Ping.aiff &
    fi
  done
```

For release builds with finer sampling, add `-sampleInterval 333000`.

Do NOT use the `-r` flag — it fails to resolve the launcher activity because LeakCanary registers a second one. Launch the app manually in step 4a instead.

### 4c: Switch branches for comparison

When capturing a second branch:

1. Stash the btrace integration changes:
   ```bash
   git stash push -m "btrace integration" -- \
     sentry-samples/sentry-samples-android/build.gradle.kts \
     sentry-samples/sentry-samples-android/src/main/java/io/sentry/samples/android/MyApplication.java \
     sentry-samples/sentry-samples-android/proguard-rules.pro
   ```
2. Checkout the other branch
3. Pop the stash: `git stash pop`
4. Rebuild and install (same variant — debug or release — as the first branch)
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

## Step 6: Query and Compare Traces

After capturing both branches, use `trace_processor` to compute comparison stats locally.

### Basic stats query

For each trace file, run:

```bash
/tmp/trace_processor -Q "
WITH events AS (
  SELECT s.dur / 1e6 as dur_ms FROM slice s
  WHERE s.name GLOB '*${METHOD_GLOB}*' AND s.dur > 0
  ORDER BY s.dur
)
SELECT COUNT(*) as count,
  ROUND(AVG(dur_ms), 4) as avg_ms,
  ROUND((SELECT dur_ms FROM events LIMIT 1 OFFSET (SELECT COUNT(*)/2 FROM events)), 4) as median_ms,
  ROUND(MIN(dur_ms), 4) as min_ms,
  ROUND(MAX(dur_ms), 4) as max_ms
FROM events
" tools/btrace/traces/${trace_file}.pb
```

Replace `${METHOD_GLOB}` with the method pattern to compare (e.g. `SentryGestureDetector.onTouchEvent`, `SentryWindowCallback.dispatchTouchEvent`).

### Finding child calls (debug builds)

To find what happens inside a method (e.g. Handler calls, lock acquisitions):

```bash
/tmp/trace_processor -Q "
WITH RECURSIVE descendants(id, depth) AS (
  SELECT s.id, 0 FROM slice s WHERE s.name GLOB '*${PARENT_METHOD}*'
  UNION ALL
  SELECT s.id, d.depth + 1 FROM slice s JOIN descendants d ON s.parent_id = d.id WHERE d.depth < 10
)
SELECT s.name, COUNT(*) as count, ROUND(AVG(s.dur / 1e6), 3) as avg_ms
FROM slice s JOIN descendants d ON s.id = d.id
WHERE d.depth > 0
GROUP BY s.name ORDER BY count DESC
LIMIT 20
" tools/btrace/traces/${trace_file}.pb
```

### Build the comparison table

Run the stats query on both trace files, then present a markdown table:

```
| Metric | Branch A | Branch B | Delta |
|--------|----------|----------|-------|
| Count  | ...      | ...      |       |
| Average| ...      | ...      | -X%   |
| Median | ...      | ...      | -X%   |
| Max    | ...      | ...      | -X%   |
```

Compute delta as `(branchA - branchB) / branchB * 100`. Negative means branch A is faster.

### Sampling rate reference

| Rate | Interval | `-sampleInterval` | Use case |
|------|----------|-------------------|----------|
| 1 kHz | 1ms | `1000000` (default) | Debug builds, general profiling |
| 3 kHz | 333μs | `333000` | Release builds, finer granularity |
| 10 kHz | 100μs | `100000` | Maximum detail, higher overhead |

Higher sampling rates capture shorter method calls but add CPU overhead which can skew results. For most comparisons, the default 1kHz is sufficient.

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
| `SocketException: Unexpected end of file` in release builds | R8 stripped btrace classes; add `-keep class com.bytedance.rheatrace.** { *; }` to proguard-rules.pro |
| Stale port from previous session | Run `adb shell "rm -rf /storage/emulated/0/Android/data/io.sentry.samples.android/files/rhea-port"` before launching |
| Most `onTouchEvent` durations are 0ms | Increase sampling rate with `-sampleInterval 333000` (3kHz) |
