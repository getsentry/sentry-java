# sentry-uitest-android-macrobenchmark

Jetpack Macrobenchmark for cold-start of `sentry-samples-android`, used to evaluate SDK-init
performance changes on a real device in a **stable, reproducible** way. Runs on Sauce Labs from the
`Integration Tests - Macrobenchmark` workflow, on every pull request.

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
kept).

## Comparing two builds

Absolute cold-start numbers are only meaningful next to a baseline measured under the same
conditions, so the benchmark measures **two builds of the sample app at once**:

| Variant | Package | Built from |
|---|---|---|
| `CANDIDATE` | `io.sentry.samples.android` | the PR merged into its base |
| `BASELINE` | `io.sentry.samples.android.baseline` | the base commit alone |

Because the candidate is the *merge* of the PR into its base, anything that landed on the base
since the PR forked is present in both arms and cancels out, leaving the PR's own contribution.

Both are installed on the same device and the run alternates between them, so whatever the device
does over the session — thermal throttling above all — lands on both and cancels out in the
difference. Two things follow from that, and both are deliberate:

- **The run alternates ABBA, not ABAB.** In ABAB the candidate always follows the baseline, so
  drift within a pair is charged to the candidate every single time. Mirroring each round makes
  each variant the trailing one equally often.
- **The absolute numbers get worse, and that's fine.** 24 alternating cold starts throttle this
  class of device where 12 back-to-back ones only start to. Read the delta, not the medians either
  side of it.

The suffixed applicationId comes from the `sampleAppIdSuffix` Gradle property, which
`sentry-samples-android` reads in its `defaultConfig`. The baseline build passes
`-PsampleAppIdSuffix=.baseline`; every ordinary build leaves it unset.

That property has to exist in the **base** commit's build script, since that is what the baseline
is built from. Against a base predating it, both builds produce the same package and the workflow
fails with an explicit error rather than installing one over the other and comparing the app
against itself.

If only the candidate is installed, the baseline steps are **skipped** (not failed) and the report
falls back to single-build numbers.

## Running locally

Connect a device, then:

```bash
./gradlew :sentry-android-integration-tests:sentry-uitest-android-macrobenchmark:connectedBenchmarkAndroidTest
```

Results print to the console and are written to
`build/outputs/connected_android_test_additional_output/.../*-benchmarkData.json`.

That measures the candidate only. To get the comparison locally, build a second copy of the app
with the suffix and install it alongside. Point the worktree at whatever you want as the baseline
— the merge base, or just `origin/main`:

```bash
git worktree add ../baseline origin/main
(cd ../baseline && ./gradlew :sentry-samples:sentry-samples-android:assembleRelease \
  -PsampleAppIdSuffix=.baseline)
adb install -r ../baseline/sentry-samples/sentry-samples-android/build/outputs/apk/release/sentry-samples-android-release.apk
```

Then re-run the benchmark. `scripts/parse-macrobenchmark-log.py` also reads `adb logcat` output, so
the same base-vs-PR table can be produced from a local run.

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

## Running on Sauce Labs

The `Integration Tests - Macrobenchmark` workflow runs the same benchmark on a Sauce Labs real
device: it builds the sample app from the PR merge and from the base commit, ships both, and posts
the comparison to the job summary and to a PR comment it keeps updating. It reports numbers only
and never fails on them.

It runs on every pull request. One run costs two Gradle builds and up to an hour of a Sauce device
at concurrency 1, so the workflow's concurrency group cancels a PR's in-flight run when a new push
arrives. If that turns out to be too expensive in aggregate, the cheapest lever is a `paths` filter
or a label gate on the job — neither changes anything else here.

PRs from forks get no secrets, so the Sauce steps skip. Do not reach for `pull_request_target` to
change that: it would run a fork's build scripts with our Sauce credentials in scope.

Two configuration details in `.sauce/sentry-uitest-android-macrobenchmark.yml` are load-bearing:

- **`otherApps`** carries the baseline APK. Sauce installs dependent apps without instrumenting or
  modifying them, and allows up to seven.
- **`appSettings.resigningEnabled: false`.** Sauce resigns the app under test on real devices but
  never touches `otherApps`. Left on, the candidate would carry an injected agent the baseline does
  not, and every delta would include the cost of that agent.

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
writes the comparison table to the job summary. Note this recovers the metrics only — the perfetto
traces are megabytes each and cannot go through logcat, so sub-millisecond work still needs a
local device.

## Known limitations

- **A base commit older than the `SentryAndroid.init` trace section** (added in #5901) has no such
  section for `TraceSectionMetric` to find, and Macrobenchmark fails the whole run rather than
  reporting the other metric. Rebase onto a newer `main` if you hit this.
- Both builds carry the same DSN, so baseline launches also send events to the sample project.
  Harmless for the measurement.
- `p` in the report is a permutation test on the difference of medians. It says how hard the
  observed gap is to explain as a reshuffle of the same measurements; a large `p` is not evidence
  that nothing changed.
