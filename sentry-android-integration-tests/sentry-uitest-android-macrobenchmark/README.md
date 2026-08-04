# sentry-uitest-android-macrobenchmark

Jetpack Macrobenchmarks for `sentry-samples-android`, used to evaluate SDK initialization and Nav3
integration performance on a real device in a **stable, reproducible** way. Not run in CI.

## What it measures

`SentryStartupBenchmark` runs a cold start and reports two metrics per iteration:

- **`timeToInitialDisplay`** (`StartupTimingMetric`) — the whole app cold start, taken from
  framework trace events. Because it captures the entire start, an SDK change has to be large enough
  (roughly tens of milliseconds) to show above cold-start noise.
- **`SentryAndroid.init`** (`TraceSectionMetric`) — the duration of the `SentryAndroid.init`
  `android.os.Trace` section the SDK emits, which isolates SDK-init cost from the rest of the start
  and resolves changes that `timeToInitialDisplay` would lose in the noise.

For even finer detail (sub-millisecond changes, or cost inside init), capture a perfetto trace and
inspect the relevant slices directly (each iteration's trace is saved under
`build/outputs/connected_android_test_additional_output/`).

`CompilationMode.Full()` pins ART AOT so dexopt state can't drift between runs. `StartupMode.COLD`
does the correct force-stop sequencing (it does **not** `pm clear`, so app data/permissions are
kept). Iterations are capped at 12 because back-to-back cold starts thermally throttle an
unlocked-clock device after ~14 iterations, inflating the tail of longer runs.

### Nav3 Performance

`Nav3PerformanceBenchmark` drives fixed runs from the Nav3 sample's Performance tab. Each outer
iteration prepares a preset, runs five warm-up operations outside measurement, and measures 20
operations. The four workload levels increase captured depth and argument complexity:

| Workload | Captured entries | Arguments | Extractor |
|---|---:|---|---|
| Light | 1 | Empty | Normal |
| Normal | 20 | Flat | Normal |
| Heavy | 50 | Nested | Normal |
| Super heavy | 100 | Large | Heavy |

The suite also includes these controls:

- `fullStackWithoutArgumentsTopReplacement` uses the Super-heavy stack and name extractor without
  an argument extractor. Compare it with `superHeavyTopReplacement` to isolate argument extraction
  and sanitization.
- `topOnlyTopReplacement` tracks the current entry without capturing the back stack.
- `disabledTopReplacement` measures the same navigation shape without `SentryNavEffect`.
- `captureOneOfHundredTopReplacement`, `captureTwentyOfHundredTopReplacement`, and
  `captureHundredOfHundredTopReplacement` keep Normal extractors and Flat arguments fixed while
  varying effective captured depth across the same 100-entry stack.
- `superHeavyUnrelatedRecomposition` verifies that unchanged navigation inputs skip extractor work.
  It does not collect frame timing because the recompositions do not reliably produce a frame.

#### Cost Attribution

`Nav3CostAttributionMetric` joins each `Nav3Stress.SentryNavEffect` trace section to its name and
argument extractor children. It reports these sampled per-operation distributions:

- `nav3IntegrationDurationMs`: complete synchronous `SentryNavEffect` duration.
- `nav3ExtractorDurationPerOperationMs`: time in sample-provided extractors.
- `nav3NonExtractorDurationMs`: integration duration minus extractor duration. This estimates
  SDK-owned context construction, argument sanitization, comparison, and scope updates.

It also reports totals and counts for each outer iteration:

- `nav3IntegrationTotalMs`, `nav3ExtractorTotalMs`, and `nav3NonExtractorTotalMs`.
- `nav3IntegrationOperationCount` and `nav3CapturedEntryResolutionCount`.
- `nav3IntegrationPerOperationMs` and `nav3IntegrationPerCapturedEntryUs`.

Use the duration distributions for latency and the count metrics to validate the workload. For
example, a 20-operation Normal run should report 20 integration operations and 400 captured-entry
resolutions. An unrelated recomposition run should report 20 lightweight wrapper calls and zero
captured-entry resolutions.

The benchmark now fails if the expected trace sections are missing or incomplete. A renamed or
missing `Nav3Stress.SentryNavEffect`, `Nav3Stress.navigationToComposition`, or
`Nav3Stress.navigationToFirstDraw` section is treated as a benchmark failure rather than silently
skipping the regression checks.

#### Interleaved A/B Comparison

`interleavedAbComparison` runs two A/B sequences in each outer iteration. The first measures
Disabled then Enabled, and the second measures Enabled then Disabled. Both modes receive their own
warm-up before measurement. Alternating the order reduces systematic JIT, thermal, and scheduling
bias.

The A/B metric reports Disabled and Enabled distributions for mutation-to-composition and
mutation-to-first-draw latency, plus the mean Enabled-minus-Disabled delta:

- `nav3AbDisabledCompositionMs` and `nav3AbEnabledCompositionMs`.
- `nav3AbDisabledFirstDrawMs` and `nav3AbEnabledFirstDrawMs`.
- `nav3AbCompositionDeltaMs` and `nav3AbFirstDrawDeltaMs`.

Positive deltas mean the Enabled phase was slower. End-to-end latency is dominated by vsync phase,
so use the direct integration metrics when investigating sub-millisecond changes.

## Running

Connect a device, then:

```bash
./gradlew :sentry-android-integration-tests:sentry-uitest-android-macrobenchmark:connectedBenchmarkAndroidTest
```

Results print to the console and are written to
`build/outputs/connected_android_test_additional_output/.../*-benchmarkData.json`.

Run only the Nav3 suite with:

```bash
./gradlew :sentry-android-integration-tests:sentry-uitest-android-macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.sentry.uitest.android.macrobenchmark.Nav3PerformanceBenchmark
```

Run one scenario by appending the method name, for example:

```bash
./gradlew :sentry-android-integration-tests:sentry-uitest-android-macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.sentry.uitest.android.macrobenchmark.Nav3PerformanceBenchmark#normalTopReplacement
```

## Running on Sauce Labs

The `Integration Tests - Macrobenchmark` workflow (manual trigger) runs the same benchmark on a
Sauce Labs real device. It reports numbers only — it is not a PR gate, because cloud devices run
with unlocked CPU clocks and the run-to-run spread swamps most SDK-init changes.

Getting the numbers *back off* the device is the awkward part, so if you are changing this, know
what has already been ruled out:

- **`artifacts.download.match` in `.sauce/*.yml` cannot reach the device.** It filters a
  hardcoded list of assets Sauce hosts for the job (`device.log`, `junit.xml`, `video.mp4`,
  `network.har`, `crash.json`, `screenshots.zip`); nothing enumerates device storage. A
  `*-benchmarkData.json` pattern there matches nothing.
- **Macrobenchmark's own reporting channels don't survive.** The readable summary goes into the
  instrumentation status bundle, which only Studio and AGP consume, and `benchmarkData.json` is
  written to the app's external media dir, which Sauce never pulls.
- **The Real Device Access API can pull device files, but not on our account.** It offers
  `pullFile` and `executeShellCommand`, and `GET /rdc/v2/devices/status` even lists the whole
  public fleet — but `POST /sessions` answers `deviceClasses=[PRIVATE_DEVICE]` and there is no
  parameter to request a public device. It would need leased private devices. That route would
  also return the per-iteration perfetto traces, so it is worth revisiting if we ever get them.

So `SentryStartupBenchmark` echoes its own `benchmarkData.json` into logcat in chunks, which
reaches CI inside `device.log`, and `scripts/parse-macrobenchmark-log.py` reassembles it and
writes a `timeToInitialDisplay` table to the job summary. Note this recovers the metrics only —
the perfetto traces are megabytes each and cannot go through logcat, so sub-millisecond work
still needs a local device.

### Device hygiene (do this for trustworthy numbers)

- **Wake and unlock the device first** — the launch check fails with "Unable to confirm activity
  launch completion" on a dozing/locked screen
  (`adb shell input keyevent KEYCODE_WAKEUP && adb shell wm dismiss-keyguard`).
- **Charge above 25%** — Macrobenchmark refuses to run below that.
- **Lock CPU clocks** if the device is rooted: this is the single biggest cure for thermal drift.
- Otherwise: let the device cool between runs, keep it on AC power, enable airplane mode, and turn
  animations off (`adb shell settings put global window_animation_scale 0`, plus
  `transition_animation_scale` and `animator_duration_scale`).
- Heed Macrobenchmark's warnings about unlocked clocks / low battery — they mean the numbers are
  noisy.

## A/B-ing an SDK change

Macrobenchmark measures one build per run, so compare separate runs — but **interleave them**:
running all of variant A followed by all of variant B lets thermal drift systematically penalize
whichever variant runs second. Instead, alternate A/B rounds (build variant A, run, build variant
B, run, repeat 2–3 times), keep each round's `*-benchmarkData.json`, and compare the values pooled
per variant. Prefer the `SentryAndroid.init` metric for SDK-init changes — it isolates init cost, so
it moves on changes that `timeToInitialDisplay` would bury in cold-start noise.
