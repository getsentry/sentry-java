# sentry-uitest-android-macrobenchmark

Jetpack Macrobenchmark for cold-start of `sentry-samples-android`, used to evaluate SDK-init
performance changes on a real device in a **stable, reproducible** way. Not run in CI.

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

## Running

Connect a device, then:

```bash
./gradlew :sentry-android-integration-tests:sentry-uitest-android-macrobenchmark:connectedBenchmarkAndroidTest
```

Results print to the console and are written to
`build/outputs/connected_android_test_additional_output/.../*-benchmarkData.json`.

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
