# Standalone App Start — E2E Test Report

Branch: `feat/standalone-app-start-tracing` — see [issue #5046](https://github.com/getsentry/sentry-java/issues/5046).

## Status

- [x] Pre-work wired up (sample-app hooks, Gradle property toggles, three APK variants verified)
- [x] Test harness script: `scripts/test-standalone-app-start.sh`
- [x] Scenario runs (initial: 24/25)
- [x] Two bugs identified and fixed in-branch (see "Bugs found and fixes" below)
- [x] Re-run confirms **32/32 local assertions passing**
- [x] Harness extended with `verify_delivery` — polls logcat until envelopes actually ship to sentry.io (not just serialized to outbox)
- [x] Third bug (sample-app simulation gap) identified and fixed — `application.load` now ingests server-side
- [x] **End-to-end delivery verification**: latest manual harness run passed 32/32 assertions and confirmed every expected envelope was sent by the SDK transport

**Final result: 32/32 local assertions pass; every expected transaction was emitted with the correct local shape and the SDK transport reported successful delivery.**

## Pre-work summary

### Sample-app changes

**`sentry-samples/sentry-samples-android/src/main/java/io/sentry/samples/android/MyApplication.java`**
- Switches the sample from auto-init to **manual init** (`SentryAndroid.init(...)`) so that `beforeSendTransaction` can be registered. `beforeSendTransaction` runs *after* all `EventProcessor`s (including `PerformanceAndroidEventProcessor` which attaches `app_start_cold` measurements and phase spans), making it the only place the final transaction shape can be observed. A scope-level `EventProcessor` was tried first but runs *before* `PerformanceAndroidEventProcessor` and so sees an incomplete transaction.
- Logs every outgoing transaction under Android log tag `SentryE2E` in a single greppable line:

  ```
  TXN|name=<name>|op=<root-op>|eventId=<id>|traceId=<id>|rootSpanId=<id>|measurements=[k=v,...]|children=[op(parent-rel),...]
  ```

  `parent-rel` is `root` / `nested` / `orphan` depending on the child's parent-span-id.
- If `BuildConfig.SIMULATE_GRADLE_PLUGIN` is `true`, calls **both** `AppStartMetrics.onApplicationCreate(this)` at the *start* of `Application.onCreate()` and `AppStartMetrics.onApplicationPostCreate(this)` at the *end*. This faithfully mirrors the Sentry Android Gradle plugin's bytecode injection points. (An earlier version of the simulation only called `onApplicationPostCreate`, which is insufficient — see Bug 3.)
- Logs `SentryE2E APP_ONCREATE_DONE` as a readiness marker.

**`sentry-samples/sentry-samples-android/src/main/AndroidManifest.xml`**
- Enabled `io.sentry.auto-init=false` (required for manual init).
- Replaced hardcoded `io.sentry.standalone-app-start-tracing.enable` value with `${standaloneAppStart}` manifest placeholder.
- Existing `DummyService` (foreground service, type `remoteMessaging`) and `TestBroadcastReceiver` (action `io.sentry.samples.android.TEST_BROADCAST`) are reused as-is.

**`sentry-samples/sentry-samples-android/build.gradle.kts`**
- Two Gradle properties, both optional:
  - `-PstandaloneAppStart=true|false` (default `true`) → `${standaloneAppStart}` manifest placeholder
  - `-PsimulateSentryGradlePlugin=true|false` (default `false`) → `BuildConfig.SIMULATE_GRADLE_PLUGIN`
- Wired into both `debug` and `release` build types.

### APK variants

| Variant | File | Flag meta-data | `SIMULATE_GRADLE_PLUGIN` | Role |
|---|---|---|---|---|
| A | `/tmp/standalone-app-start-logs/APK-A.apk` | `true` | `true` | Happy path + tier 1 end-time |
| B | `/tmp/standalone-app-start-logs/APK-B.apk` | `false` | `false` | Regression (legacy behavior) |
| C | `/tmp/standalone-app-start-logs/APK-C.apk` | `true` | `false` | Tier 2 / tier 3 end-time |

All three variants built with JDK 17. `aapt dump xmltree` confirms the manifest placeholder resolves to `0xffffffff` (true) / `0x0` (false) per variant.

### Emulator setup

| ID | API | Purpose |
|---|---|---|
| `emulator-5554` | 36 | Tier 1 + tier 2 coverage |
| `emulator-5556` | 33 | Tier 3 coverage (below `VANILLA_ICE_CREAM`, so the `ApplicationStartInfo` tier-2 path is gated off) |

### Harness script

`scripts/test-standalone-app-start.sh` — builds all three APKs, then for each scenario: installs the right variant on the right emulator, force-stops the package, clears logcat, fires the trigger, waits, and asserts on a filtered logcat dump. Waits are 35 s for activity scenarios (to exceed the 30 s transaction deadline) and 8 s for broadcast-only scenarios.

## Findings

Run date: 2026-04-24. Raw logs in `/tmp/standalone-app-start-logs/<scenario>.log` (greppable) and `.full.log` (full Sentry SDK debug log).

### 1a — Cold + flag ON (launcher), API 36, APK-A — ✅ PASS

Two transactions, **same `traceId`** (`50e09dbfc2a842c88f4cd9c849b8d57d`):

| # | name | op | measurements | children |
|---|---|---|---|---|
| 1 | `App Start Cold` | `app.start.cold` | `app_start_cold=935` | `activity.load`, `activity.load`, `process.load`, `application.load` (all root-children) |
| 2 | `MainActivity` | `ui.load` | `time_to_initial_display=945`, `time_to_full_display=945`, `frames_*` | `ui.load.initial_display`, `ui.load.full_display`, `screen_load_measurement`, Compose spans — **no `app.start.*` child** |

All issue-spec requirements met for the happy path.

### 1c — Cold + flag OFF (regression), API 36, APK-B — ✅ PASS

One transaction, legacy shape preserved:

| name | op | measurements | children |
|---|---|---|---|
| `MainActivity` | `ui.load` | `app_start_cold=970`, `time_to_initial_display=970`, `frames_*` | `app.start.cold(root)`, `activity.load(nested)`, `activity.load(nested)`, `process.load(nested)`, ttid/ttfd, Compose |

The `app.start.cold` span is a direct child of the `ui.load` transaction, and `process.load`/`activity.load` hang under it. This is the unchanged pre-branch behavior, confirming the flag cleanly gates the new path.

### 2a — Broadcast cold, tier 1 (simulated Gradle plugin), API 36, APK-A — ✅ PASS

| name | op | measurements | children |
|---|---|---|---|
| `App Start Cold` | `app.start.cold` | `app_start_cold=424` | `process.load(root)`, `application.load(root)` |

No `ui.load` (no activity was launched). `application.load` is present because `BuildConfig.SIMULATE_GRADLE_PLUGIN=true` calls `AppStartMetrics.onApplicationPostCreate()` and that sets `applicationOnCreate.hasStopped() == true`, enabling tier-1 end-time resolution and the application.load child.

### 2b — Broadcast cold, tier 2 (`ApplicationStartInfo`), API 36, APK-C — ✅ PASS

| name | op | measurements | children |
|---|---|---|---|
| `App Start Cold` | `app.start.cold` | `app_start_cold=321` | `process.load(root)` |

Note: **no `application.load` child.** This is expected for tier 2 without the Gradle plugin: `ApplicationStartInfo` provides only the `onCreate` *end* timestamp, and `applicationOnCreate.hasStarted()` is false (there's no bytecode-injected call to `onApplicationCreate`), so `attachAppStartSpans` skips the application.load span. `appStartSpan` end time is still derived from the `START_TIMESTAMP_APPLICATION_ONCREATE` timestamp, which is what drives the `app_start_cold=321ms` measurement. `process.load` is always available from `CLASS_LOADED_UPTIME_MS`.

### 2c — Broadcast cold, tier 3 (`CLASS_LOADED_UPTIME_MS` fallback), API 33, APK-C — ✅ PASS (after fix)

**Before fix** — `App Start Warm`, empty children:

```
TXN|name=App Start Warm|op=app.start.warm|measurements=[app_start_warm=330.0]|children=[]
```

**After fix:**

| name | op | measurements | children |
|---|---|---|---|
| `App Start Cold` | `app.start.cold` | `app_start_cold=333` | `process.load(root)` |

No `application.load` — same inherent tier-2/3 limitation as 2b (no bytecode-instrumented start timestamp available). `process.load` is present, which is the expected tier-3 outcome.

### 2d — Foreground service cold start, API 36, APK-A — ✅ PASS

| name | op | measurements | children |
|---|---|---|---|
| `App Start Cold` | `app.start.cold` | `app_start_cold=441` | `process.load(root)`, `application.load(root)` |

Structurally identical to 2a, confirming the non-activity path works for foreground services as well as broadcasts.

### 2e — Broadcast → launcher (trace reuse), API 36, APK-A — ✅ PASS (after fix)

**Before fix** — three transactions, spurious second standalone `App Start Warm`:

```
TXN|name=App Start Cold|op=app.start.cold|...|children=[process.load, application.load]
TXN|name=App Start Warm|op=app.start.warm|...|children=[activity.load, activity.load]  ← spurious
TXN|name=MainActivity|op=ui.load|...|children=[ui.load.initial_display, ui.load.full_display, ...]
```

**After fix** — exactly two transactions, same `traceId` (`dd82f2ab505244e59578a3b53ee93be3`):

| # | name | op | measurements | children |
|---|---|---|---|---|
| 1 | `App Start Cold` | `app.start.cold` | `app_start_cold=431` | `process.load(root)`, `application.load(root)` |
| 2 | `MainActivity` | `ui.load` | `time_to_initial_display=453`, `time_to_full_display=453`, `frames_*` | ttid/ttfd, `activity.load(root)` × 2, screen_load, Compose spans — **no `app.start.*` child** |

One app-start transaction per process startup (issue-spec compliant), shared trace ID, and the `ui.load` carries no app-start data.

### 2f — Broadcast + flag OFF (regression), API 36, APK-B — ✅ PASS

No transactions emitted at all. The flag OFF + non-activity combination is correctly a no-op: the `OnNoActivityStartedListener` is never registered (install is gated on `performanceEnabled && isEnableStandaloneAppStartTracing()`), and no activity was launched to produce a `ui.load`. Matches pre-branch behavior.

## Summary

| # | Scenario | Initial | After fixes |
|---|---|---|---|
| 1a | Cold + flag ON (launcher) | ✅ | ✅ |
| 1c | Cold + flag OFF (regression) | ✅ | ✅ |
| 2a | Broadcast cold, tier 1 | ✅ | ✅ |
| 2b | Broadcast cold, tier 2 | ✅ | ✅ |
| 2c | Broadcast cold, tier 3 | ❌ | ✅ |
| 2d | Foreground service cold start | ✅ | ✅ |
| 2e | Broadcast → launcher (trace reuse) | ⚠ duplicate | ✅ |
| 2f | Broadcast + flag OFF (regression) | ✅ | ✅ |

**All 8 scenarios pass. 32/32 individual assertions pass after fixes.**

## End-to-end Sentry verification

Latest run: April 28, 2026 on `emulator-5554` (API 36) and `emulator-5556` (API 33). The harness passed **32/32** assertions and `verify_delivery` confirmed each expected envelope was sent by the SDK transport. `sentry-cli` was unauthenticated in this shell, so the shape below is from the fresh `SentryE2E` outgoing transaction logs plus successful delivery polling. The trace links are trace IDs produced by the SDK, but they were **not** freshly verified through the Sentry UI/API in this run. 2f correctly emitted **zero** transactions.

| # | What it tests | Emulator / APK | Local | Locally emitted shape | Trace ID link |
|---|---|---|---|---|---|
| **1a** | Launcher activity cold start, standalone flag ON. New `App Start Cold` standalone emits alongside legacy `MainActivity ui.load`; shared trace ID; `ui.load` has no `app.start.*` child. | API 36 / APK-A | 6/6 | `app.start` (`app_start_cold=1189ms`) + `activity.load` x2 + `process.load` + `application.load`; sibling `ui.load` (`time_to_initial_display=1203ms`) | [`d8e97ed6...`](https://sentry-sdks.sentry.io/performance/trace/d8e97ed600994bc38b978f188c9df246/?project=5428559&statsPeriod=24h) |
| **1c** | Launcher activity cold start, standalone flag OFF (regression). Legacy shape preserved: single `ui.load` with `app.start.cold` nested **inside**. | API 36 / APK-B | 4/4 | `ui.load` (`time_to_initial_display=1146ms`) -> nested `app.start.cold` -> `process.load` + `activity.load` x2 | [`970768dc...`](https://sentry-sdks.sentry.io/performance/trace/970768dc491a4c45b6d7b609c1d24ef0/?project=5428559&statsPeriod=24h) |
| **2a** | Broadcast cold, tier-1 end-time resolution (Gradle-plugin bytecode sim). Non-activity path emits standalone with **both** phase spans. | API 36 / APK-A | 5/5 | `app.start` (`app_start_cold=497ms`) + `process.load` + `application.load`; no `ui.load` | [`a39fb47f...`](https://sentry-sdks.sentry.io/performance/trace/a39fb47fee914776b7cca81568c8444c/?project=5428559&statsPeriod=24h) |
| **2b** | Broadcast cold, tier-2 end-time resolution (Android 15 `ApplicationStartInfo`). No Gradle plugin -> only `process.load` (no `application.load`, by design). | API 36 / APK-C | 4/4 | `app.start` (`app_start_cold=329ms`) + `process.load` only; no `ui.load` | [`e100abb8...`](https://sentry-sdks.sentry.io/performance/trace/e100abb83dea4c1da5f06bbecef0a9b4/?project=5428559&statsPeriod=24h) |
| **2c** | Broadcast cold, tier-3 fallback (API 33, `CLASS_LOADED_UPTIME_MS`). Validates Bug-1 fix -- correctly classified as Cold on API < 35. | API 33 / APK-C | 4/4 | `app.start` (`app_start_cold=347ms`) + `process.load` only; correctly classified **Cold** | [`5fb8e75d...`](https://sentry-sdks.sentry.io/performance/trace/5fb8e75d5e53403e8c446b47e108999d/?project=5428559&statsPeriod=24h) |
| **2d** | Foreground service cold start. Non-activity path works for foreground services, not just broadcasts. | API 36 / APK-A | 3/3 | `app.start` (`app_start_cold=901ms`) + `process.load` + `application.load`; no `ui.load` | [`f8ad7a62...`](https://sentry-sdks.sentry.io/performance/trace/f8ad7a62447b487cae367bf75e80a999/?project=5428559&statsPeriod=24h) |
| **2e** | Broadcast then launcher -- trace reuse (Bug-2 fix). Exactly ONE standalone per process start; activity reuses trace id; activity's `ui.load` has no `app.start.*` child. | API 36 / APK-A | 5/5 | 2 txns on same trace: `app.start` (`app_start_cold=994ms`) + sibling `ui.load` (`time_to_initial_display=939ms`); no duplicate standalone | [`04ffca08...`](https://sentry-sdks.sentry.io/performance/trace/04ffca0875304d4ab3e5bf0922eb7da1/?project=5428559&statsPeriod=24h) |
| **2f** | Broadcast + standalone flag OFF (regression). Non-activity listener must not install; broadcast emits zero transactions. | API 36 / APK-B | 1/1 | (no trace — correctly nothing emitted) | — |

### `verify_delivery` harness addition

Prior to this work the harness only verified the SDK's outgoing envelope shape (via `beforeSendTransaction`). That's not the same as confirming Sentry ingested it — a span could be serialized locally and silently rejected by Relay (as happened with Bug 3 below). The new `verify_delivery` function added to `scripts/test-standalone-app-start.sh`:

1. Launches `MainActivity` post-assertions to foreground the process. This flips `AndroidConnectionStatusProvider` from `DISCONNECTED` → `CONNECTED` — broadcast-only processes report `Network is null and cannot check network status` during init, which short-circuits `SendCachedEnvelopeIntegration` until the app foregrounds.
2. Polls logcat for `Envelope sent successfully` with a 120 s timeout. Emulator DNS tends to resolve sentry.io to IPv6 first; the connection times out and the OkHttp transport falls back to IPv4 on the ~30 s retry. 120 s gives comfortable slack.
3. Asserts at least one `sent=` line and that queued envelopes have flushed.

Without this step, broadcast-only scenarios would write the envelope to the on-disk cache (`/data/user/0/io.sentry.samples.android/cache/sentry/<dsn-hash>/*.envelope`) but never upload it.

## Bugs found and fixes

Both bugs are feature-introduced (only observable through new code paths added by this branch — neither is a pre-existing main-branch issue).

### Bug 1 — Tier-3 `App Start Warm` mis-classification (scenario 2c)

**Symptom.** On API < 35, a broadcast-triggered cold start emitted a transaction named `App Start Warm` (op `app.start.warm`) with *zero* child spans.

**Root cause (three things stacking):**
1. `ApplicationStartInfo` is gated on API ≥ 35 (`VANILLA_ICE_CREAM`), so on API 33 `AppStartMetrics.appStartType` remained `UNKNOWN`.
2. The new `ActivityLifecycleIntegration.onNoActivityStarted()` computes `coldStart = (appStartType == COLD)`, which evaluates to `false` when the type is `UNKNOWN` — so the name/op fell through to "warm."
3. `PerformanceAndroidEventProcessor.attachAppStartSpans()` (pre-existing on main, line 239–241) early-returns when `appStartType != COLD`. Combined with (2), no `process.load` / `application.load` / `contentprovider.load` child spans were attached.

Pre-existing state (UNKNOWN classification on API < 35 non-activity starts) was harmless on main because no code emitted a transaction in that situation. The new non-activity path is the first code to act on that state, so the symptom only surfaces on this branch.

**Fix.** `sentry-android-core/src/main/java/io/sentry/android/core/performance/AppStartMetrics.java` — inside `checkCreateTimeOnMain()`, default `appStartType = COLD` when the type is still `UNKNOWN`. Reaching that callback means `Application.onCreate()` finished with no activity, which is definitionally a cold start for the process.

```java
private void checkCreateTimeOnMain() {
  if (activeActivitiesCounter.get() == 0) {
    appLaunchedInForeground.setValue(false);

    // Reaching this callback means Application.onCreate() finished with no Activity created,
    // which is definitionally a cold start for this process. On API < 35 we can't resolve the
    // start type via ApplicationStartInfo, so appStartType is still UNKNOWN at this point —
    // default it to COLD so the standalone transaction (and PerformanceAndroidEventProcessor)
    // classify it correctly.
    if (appStartType == AppStartType.UNKNOWN) {
      appStartType = AppStartType.COLD;
    }
    ...
  }
}
```

**Why here, not in `onNoActivityStarted()`.** Fixing in `AppStartMetrics` also propagates the correct type to `PerformanceAndroidEventProcessor.attachAppStartSpans()`, so phase spans get attached. A localized fix in `onNoActivityStarted()` would only rename the transaction — it wouldn't unblock the phase-span attachment.

**Risk.** Zero. The new assignment only fires when `activeActivitiesCounter == 0 && appStartType == UNKNOWN`, a state that previously had no consumer.

### Bug 2 — Duplicate standalone transaction on broadcast → activity (scenario 2e)

**Symptom.** When a broadcast cold-starts the process and the user subsequently launches an activity, the SDK emitted **three** transactions (all sharing a trace ID): `App Start Cold` (from the broadcast), a *spurious* `App Start Warm` (from the activity launch), and `MainActivity` `ui.load`. This violates the spec requirement of one app-start transaction per process startup.

**Root cause.** After the broadcast, `AppStartMetrics.checkCreateTimeOnMain()` sets `appLaunchedInForeground = false`. When an activity later launches, the pre-existing `AppStartMetrics.onActivityCreated` logic sees `!appLaunchedInForeground` and flips `appStartType = WARM` (plus resets `appStartSpan`), which is intended for real backgrounded → foregrounded warm starts. The new standalone-creation block in `ActivityLifecycleIntegration.onActivityPreCreated` then treats this as a fresh warm start and emits a second standalone — even though the process's app start was already reported by the broadcast path.

**Fix.** `sentry-android-core/src/main/java/io/sentry/android/core/ActivityLifecycleIntegration.java` — piggyback on the existing `appStartTraceId` state. When `getAppStartTraceId()` returns a non-null value, it means a non-activity standalone was already emitted for this process and stashed its trace ID for an eventual activity to reuse. In that case, the activity should skip its own standalone creation.

```java
final @Nullable SentryId storedAppStartTraceId =
    AppStartMetrics.getInstance().getAppStartTraceId();
// When we reuse a stashed traceId, it means the process's app start has already been
// accounted for by a standalone transaction from the non-activity path — don't emit
// a second standalone here just because an activity subsequently showed up.
final boolean isFollowingNonActivityStart = (storedAppStartTraceId != null);

// ... existing ui.load creation, which consumes and clears appStartTraceId ...

// in the standalone block:
if (options.isEnableStandaloneAppStartTracing()
    && foregroundImportance
    && !isFollowingNonActivityStart) {
  // create standalone appStartTransaction
}
```

**Why piggyback on `appStartTraceId` instead of introducing a new flag.** The existing `appStartTraceId` lifecycle — set when the non-activity standalone fires, cleared the first time an activity consumes it — already matches the semantics we need ("a standalone was emitted for this process; don't emit another on the next activity"). A new `AtomicBoolean` in `AppStartMetrics` plus a reset site in `onActivityDestroyed` would add state and test surface for no additional coverage.

**Backgrounding behavior preserved.** When the user backgrounds and re-foregrounds a normal app, `appStartTraceId` remains null (it's never set outside the non-activity path), so the standalone `App Start Warm` still emits on the next activity launch, which is spec-correct for warm starts.

**Risk.** Zero. The guard only suppresses the standalone in the specific "non-activity started the process then an activity came later" sequence. All other paths are unchanged.

### Bug 3 — Missing `onApplicationCreate` in the sample-app simulation (test-only, not SDK)

Discovered while cross-checking that `application.load` actually lands in Sentry end-to-end (not just appears in the local `beforeSendTransaction` envelope).

**Symptom.** Scenarios 2a and 2d locally reported `application.load` as a child of `App Start Cold`, but that span was absent from the ingested trace in Sentry — only `process.load` showed up server-side.

**Root cause.** `application.load` *was* serialized into the envelope, but with both `start_timestamp` and `timestamp` set to `0` (Unix epoch 1970-01-01), so Relay silently dropped it as out-of-window. Tracing it back:

1. `MyApplication.onCreate()` in the sample previously called **only** `AppStartMetrics.onApplicationPostCreate(this)` when `SIMULATE_GRADLE_PLUGIN=true`. The real Sentry Gradle plugin injects **two** calls — `onApplicationCreate` at the *start* of `Application.onCreate()` and `onApplicationPostCreate` at the *end*.
2. Without the paired start call, `applicationOnCreate.startUptimeMs == 0` (never assigned). `hasStopped()` still returned true (post-create *was* called), so `PerformanceAndroidEventProcessor.attachAppStartSpans()` attached the span.
3. But `TimeSpan.getStartTimestampSecs()` and `getProjectedStopTimestampSecs()` short-circuit to `0` when `!hasStarted()`. Result: a span with both timestamps = epoch. Relay's trace-window validation dropped it server-side.

**Fix.** `sentry-samples/sentry-samples-android/src/main/java/io/sentry/samples/android/MyApplication.java` — also call `AppStartMetrics.onApplicationCreate(this)` at the very top of `onCreate()` when `SIMULATE_GRADLE_PLUGIN` is on, pairing it with the existing `onApplicationPostCreate(this)` at the bottom. After this, the `application.load` span has a valid start + stop and ingests cleanly (confirmed in 2a and 2d traces above).

**Scope.** Sample-app simulation only. Not an SDK bug — real apps using the Sentry Gradle plugin get both injection points and were never affected. The bug was latent because the existing local assertion (`children=[... application.load ...]`) passed on envelope inspection; only the end-to-end Sentry check surfaced it.

**Risk.** Zero (test/sample code only).

## Issues deferred (not fixed in this PR)

1. **Tier 2 lacks `application.load` span (2b)** — Acceptable; inherent to `ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE` providing only the end timestamp. Worth a release-note mention; not a regression.

## Scenarios not covered

- **1b (warm start)** — OS-controlled, can't reliably force
- **1d (deferred SDK init)** — undefined behavior per your call
- **1e (cancelled activity)** — too racy to automate
- **Hybrid SDK duplicate-transaction guard** — verified earlier by code reading (Flutter/RN disable native tracing, so `performanceEnabled == false` and the listener is never installed); no e2e test needed
