# Test Plan — `feat/standalone-app-start-tracing`

## Context

The branch adds a new, opt-in `SentryAndroidOptions.enableStandaloneAppStartTracing` option (and matching manifest key `io.sentry.standalone-app-start-tracing.enable`) that changes how app-start data is reported:

- **Flag OFF (default):** legacy behavior — `app.start.cold` is a child span nested inside `MainActivity ui.load`.
- **Flag ON:** a separate standalone `App Start Cold/Warm` transaction is emitted alongside `ui.load` (shared `traceId`); the activity's `ui.load` carries no `app.start.*` child. A **non-activity** cold start (broadcast receiver, foreground service) now emits the standalone transaction on its own, without any activity.

The branch also ships two SDK bug fixes that were uncovered during manual E2E validation (see `standalone_app_start_report.md`):

1. **Bug 1 (classification)** — `AppStartMetrics.checkCreateTimeOnMain()` defaults `appStartType` from `UNKNOWN → COLD` on API < 35 when `Application.onCreate()` finishes with no activity.
2. **Bug 2 (duplicate emission)** — `ActivityLifecycleIntegration.onActivityPreCreated` derives `isFollowingNonActivityStart` from `AppStartMetrics.getAppStartTraceId()` and suppresses the activity's standalone block when a non-activity standalone already ran.

**Problem this plan addresses:** zero automated tests were added across the 5 feature commits (`ddd9bed03e`, `fdd26df9b8`, `e5b7a1844a`, `11898dc637`, `215a549a93`). The current guardrails are the manual E2E harness (`scripts/test-standalone-app-start.sh`) and shape verification against Sentry — neither runs in CI. This plan closes that gap with targeted unit tests and reasons explicitly about redundancy, so we don't over-test.

---

## Files to modify (tests only — no production code change)

| # | File | Change |
|---|---|---|
| T1 | `sentry-android-core/src/test/java/io/sentry/android/core/ManifestMetadataReaderTest.kt` | +3 tests |
| T2 | `sentry-android-core/src/test/java/io/sentry/android/core/SentryAndroidOptionsTest.kt` | +1 test |
| T3 | `sentry-android-core/src/test/java/io/sentry/android/core/performance/AppStartMetricsTest.kt` | +8 tests, +1 extension |
| T3b | `sentry-android-core/src/test/java/io/sentry/android/core/performance/AppStartMetricsTestApi35.kt` | +2 tests (API 35 tier-2 coverage) |
| T4 | `sentry-android-core/src/test/java/io/sentry/android/core/PerformanceAndroidEventProcessorTest.kt` | +3 tests |
| T5 | `sentry-android-core/src/test/java/io/sentry/android/core/ActivityLifecycleIntegrationTest.kt` | +11 tests, +1 fixture update |
| T6 | `scripts/test-standalone-app-start.sh` | strengthen scenario 2e assertions |
| T7 | `sentry/src/test/java/io/sentry/TransactionContextTest.kt` | +2 tests |
| T8 | `sentry-android-integration-tests/sentry-uitest-android/src/androidTest/java/io/sentry/uitest/android/StandaloneAppStartTracingIntegrationTest.kt` | optional best-effort on-device smoke tests only |
| T9 | `sentry-android-integration-tests/sentry-uitest-android/src/main/java/io/sentry/uitest/android/StandaloneAppStartActivity.kt` + `.../src/main/AndroidManifest.xml` | optional lightweight activity fixture for T8 |

**Feature code under test (no edits — reference only):**

- `sentry-android-core/src/main/java/io/sentry/android/core/performance/AppStartMetrics.java:53–55,92–95,173–196,430–496` (listener interface, new fields, accessors, `checkCreateTimeOnMain` Bug 1 fix, `resolveNonActivityAppStartEndTime`)
- `sentry-android-core/src/main/java/io/sentry/android/core/ActivityLifecycleIntegration.java:130–132,145,252–309,841–849,851–857,867–878,880–920` (listener registration, trace-id reuse, Bug 2 guard, standalone creation, cleanup, `onNoActivityStarted`)
- `sentry-android-core/src/main/java/io/sentry/android/core/PerformanceAndroidEventProcessor.java:86–119,259–265` (standalone-txn measurement branch, parent-span fallback)
- `sentry-android-core/src/main/java/io/sentry/android/core/SentryAndroidOptions.java` (new option, default `false`)
- `sentry-android-core/src/main/java/io/sentry/android/core/ManifestMetadataReader.java:109–110` (manifest key)
- `sentry/src/main/java/io/sentry/TransactionContext.java:87–104` (constructor that reuses explicit `traceId` for sibling transactions in the same trace)
- Optional on-device smoke path, if T8/T9 are implemented, exercised via `MockRelay` and `assertEnvelopeTransaction` patterns in `sentry-android-integration-tests/sentry-uitest-android`

**Existing helpers / patterns to reuse:**

- `AppStartMetricsTest.waitForMainLooperIdle()` (file:211) — drives the idle handler that invokes `checkCreateTimeOnMain()`.
- `AppStartMetrics.getInstance().registerLifecycleCallbacks(mock<Application>())` — canonical way to trigger `checkCreateTimeOnMain` in tests, modeled after `if activity is never started, stops app start profiler if running` (file:248).
- `AppStartMetricsTestApi35` + `SentryShadowActivityManager` — existing API-35 test harness proving this module can unit-test `ApplicationStartInfo` behavior under Robolectric.
- `ActivityLifecycleIntegrationTest.Fixture.getSut()` (file:87) + the `argumentCaptor<TransactionContext>` + `SentryTracer` wrapping trick (file:101–108) — lets us inspect created transactions rather than stubbing them. **Before T5 multi-transaction tests, extend this fixture to keep `createdTransactions`, `capturedContexts`, and `capturedOptions` lists instead of only the last `fixture.transaction`; append to those lists from the existing `thenAnswer`.**
- `ActivityLifecycleIntegrationTest.setAppStartTime()` (file:1740+) — standard helper to populate `AppStartMetrics` for activity-path tests.
- Add a small `ActivityLifecycleIntegrationTest.waitForMainLooperIdle()` helper mirroring `AppStartMetricsTest.waitForMainLooperIdle()` for tests that drive `OnNoActivityStartedListener`.
- `PerformanceAndroidEventProcessorTest.createAppStartSpan()` (file:62) — canonical way to build a `SentrySpan` with `APP_START_COLD/WARM` op.
- `ManifestMetadataReaderTest` pattern — `bundleOf(ManifestMetadataReader.KEY to value)` + `fixture.getContext(metaData = bundle)` (file:127–138).
- `@BeforeTest` pattern: `AppStartMetrics.getInstance().clear()` + `ContextUtils.resetInstance()` — required to avoid singleton bleed.

---

## Tests to add

Each test below lists: **intent → covers**, and where it's a negative case or directly covers a bug fix it's flagged.

### T1 — `ManifestMetadataReaderTest.kt`

Follows the existing `applyMetadata reads <option> to options` / `... keep default value if not found` pattern (file:127, file:115).

1. **`applyMetadata reads standalone app start tracing to options`** *(positive)* — bundle with `ENABLE_STANDALONE_APP_START_TRACING = true`; assert `options.isEnableStandaloneAppStartTracing()` is `true`.
2. **`applyMetadata reads standalone app start tracing false to options`** *(explicit false)* — preset `options.isEnableStandaloneAppStartTracing = true`, bundle with `ENABLE_STANDALONE_APP_START_TRACING = false`; assert it becomes `false`. This covers the APK-B/manual-regression configuration, not just the default.
3. **`applyMetadata reads standalone app start tracing and keep default value if not found`** *(default)* — empty bundle; assert option stays `false`.

### T2 — `SentryAndroidOptionsTest.kt`

1. **`enableStandaloneAppStartTracing defaults to false`** — asserts the AGENTS.md "opt-in by default" contract for the new experimental option.

> *Skipped as redundant:* a setter/getter round-trip test — trivial boolean field, no logic; its behavior is covered transitively by T1/T5 which set the option before exercising gated code paths.

### T3 — `AppStartMetricsTest.kt`

Tests driven via `registerLifecycleCallbacks(mock()) + waitForMainLooperIdle()` unless otherwise noted.

1. **`checkCreateTimeOnMain defaults appStartType to COLD when UNKNOWN and no activity started`** *(Bug 1 fix — positive)* — start `appStartTimeSpan`, leave `appStartType == UNKNOWN`; drive idle; assert type becomes `COLD` and `isAppLaunchedInForeground == false`.
2. **`checkCreateTimeOnMain does not overwrite appStartType when already set`** *(Bug 1 fix — regression guard)* — pre-set `appStartType = WARM` (as `ApplicationStartInfo` would on API 35+); assert it stays `WARM` after idle.
3. **`checkCreateTimeOnMain fires onNoActivityStartedListener when no activity started`** *(listener wiring — positive)* — register a listener that flips an `AtomicBoolean`; assert invoked exactly once after idle.
4. **`checkCreateTimeOnMain does not fire onNoActivityStartedListener when an activity has started`** *(listener wiring — negative)* — call `onActivityCreated(mock, null)` before registering lifecycle callbacks so `activeActivitiesCounter == 1`; assert listener never invoked.
5. **`resolveNonActivityAppStartEndTime uses applicationOnCreate stop when Gradle plugin instrumented`** *(tier 1)* — set `appStartTimeSpan.startedAt = 100`, set `applicationOnCreate.startedAt = 120` and `applicationOnCreate.stoppedAt = 200`; drive idle; assert `appStartTimeSpan.durationMs == 100` (or projected stop equals `200`, depending on the helper/assertion available).
6. **`resolveNonActivityAppStartEndTime falls back to CLASS_LOADED_UPTIME_MS when no plugin and no ApplicationStartInfo`** *(tier 3)* — set `CLASS_LOADED_UPTIME_MS` to a deterministic value, set `appStartTimeSpan.startedAt` before it, leave `applicationOnCreate` untouched under default `@Config(sdk = N)`; drive idle; assert the resolved stop/duration matches `getClassLoadedUptimeMs()`.
7. **Extend existing `metrics are properly cleared`** (file:56) to additionally assert: `setAppStartTraceId(SentryId())` then `clear()` → `getAppStartTraceId() == null`.
8. **`getAppStartTimeSpanDirect falls back to sdkInitTimeSpan when appStartSpan has not stopped`** — set `appStartTimeSpan.startedAt` but do not stop it; set `sdkInitTimeSpan.startedAt` and `sdkInitTimeSpan.stoppedAt`; assert `getAppStartTimeSpanDirect()` returns `sdkInitTimeSpan`. (This is branchy enough to warrant its own test; it's the one fallback that's actually user-observable in tier-3 warm-start corners.)
9. **`checkCreateTimeOnMain keeps appStartType COLD on API 35 when ApplicationStartInfo reports cold start`** *(tier-2 + Bug 1 regression guard)* — run in `AppStartMetricsTestApi35`; seed `ApplicationStartInfo.START_TYPE_COLD`, drive idle callback, assert type remains `COLD` and non-activity listener path can execute without reclassification.
10. **`resolveNonActivityAppStartEndTime uses ApplicationStartInfo START_TIMESTAMP_APPLICATION_ONCREATE on API 35`** *(tier-2 positive)* — in `AppStartMetricsTestApi35`, provide `startupTimestamps` map with `START_TIMESTAMP_APPLICATION_ONCREATE`; simulate non-activity launch and drive idle; assert the resolved app-start duration/stop equals the converted onCreate timestamp. Keep `applicationOnCreate` unstarted so this proves tier 2, not tier 1.

> *Skipped as redundant:* standalone getter/setter tests for `appStartTraceId`, `onNoActivityStartedListener`, `getAppStartTimeSpanDirect()` when both spans are identical — the property-level behavior is exercised indirectly by T3.3/T3.7/T3.8 and T5.5/T5.8.
>
> **Kept for `getAppStartTimeSpanDirect` fallback:** a single test because the `appStartSpan vs sdkInitTimeSpan` fallback has real branching logic — see T3.8 below.

### T4 — `PerformanceAndroidEventProcessorTest.kt`

Follows existing fixture (file:39) + `createAppStartSpan(traceId, coldStart)` helper (file:62). Tests construct a `SentryTransaction` manually (the existing pattern — see `add cold start measurement` at file:87).

**How to simulate "Gradle plugin instrumented" vs "not instrumented" in a unit test.** The Gradle plugin's only effect at runtime is to inject two static calls — `AppStartMetrics.onApplicationCreate(app)` at the top of `Application.onCreate()` and `AppStartMetrics.onApplicationPostCreate(app)` at the bottom — which set `startedAt` / `stoppedAt` on `applicationOnCreate`. The only thing the processor checks is `applicationOnCreate.hasStopped()` (PerformanceAndroidEventProcessor.java:291). So:
- **"plugin instrumented"** → populate the TimeSpan directly: `AppStartMetrics.getInstance().applicationOnCreateTimeSpan.apply { setStartedAt(100); setStoppedAt(200) }` — matches the existing pattern in `AppStartMetricsTest.metrics are properly cleared` (file:61).
- **"not instrumented"** → leave `applicationOnCreate` in its default unstarted state. `hasStopped()` returns false and the attachment block is skipped.

Using the direct TimeSpan setter is preferred over calling the statics: it's deterministic (no current-time dependency) and targets the exact gating condition.

For all three tests, set deterministic app-start inputs before processing: `appStartType = COLD`, `isAppLaunchedInForeground = false`, `CLASS_LOADED_UPTIME_MS`, and `appStartTimeSpan.startedAt/stoppedAt`. Build the `SentryTransaction` with a root trace context whose op is `APP_START_COLD` and with **no** `APP_START_COLD` child span, so the tests specifically exercise the standalone-root path.

1. **`standalone App Start Cold txn with Gradle-plugin instrumented application.onCreate attaches both process_load and application_load`** *(tier-1 shape — positive, matches report scenario 2a)* — set `isAppLaunchedInForeground = false`, populate `appStartTimeSpan` (started + stopped), **populate `applicationOnCreateTimeSpan` (`setStartedAt` + `setStoppedAt`)** per the guidance above, build a `SentryTransaction` whose root span op is `APP_START_COLD` (no `app.start.cold` child span). Process; assert `measurements[KEY_APP_START_COLD]` present and that **both** `process.load` AND `application.load` child spans are attached under the transaction root. *Covers the `isStandaloneAppStartTxn || shouldSendStartMeasurements()` branch (file:96), the `application.load` attachment gated on `applicationOnCreate.hasStopped()` (file:291), and the new `parentSpanId = traceContext.getSpanId()` fallback (file:260–265).*
2. **`standalone App Start Cold txn without instrumented application.onCreate attaches only process_load`** *(tier-2/3 shape — positive, matches report scenarios 2b & 2c)* — identical setup but **leave `applicationOnCreateTimeSpan` unstarted**. Process; assert `process.load` child span IS attached and NO span with op `application.load` is attached. *Locks in the tier-1 vs tier-2/3 shape distinction called out in the report.*
3. **`standalone App Start Cold txn without APP_START_COLD child uses transaction root span id as parent`** *(parent-span resolution — positive)* — same setup as #2 but assert the injected `process.load` span's `parentSpanId == txn.contexts.trace.spanId`.

> *Skipped as redundant:*
> - "non-app-start txn with no app_start_cold child is ignored" — already implicitly covered by existing `hasAppStartSpan` gating tests; adding a sibling test here has negligible marginal coverage.
> - "standalone warm txn adds warm measurement" — the cold/warm switch is a trivial string map; the cold path above exercises the new branch, and existing `add warm start measurement` (file:110) already validates the warm key. Redundant.

### T5 — `ActivityLifecycleIntegrationTest.kt`

Reuse `fixture.getSut()` (file:87) + `argumentCaptor<TransactionContext>`/`TransactionOptions` pattern (file:101). First extend the fixture so every `startTransaction` call appends its `TransactionContext`, `TransactionOptions`, and returned `SentryTracer` to lists. Keep `fixture.transaction` pointing at the last tracer for existing tests, but use the lists for new tests that assert one vs two transactions. All tests use `initializer = { it.isEnableStandaloneAppStartTracing = true; it.tracesSampleRate = 1.0 }` unless otherwise noted.

1. **`OnNoActivityStartedListener is registered when standalone flag is on and performance enabled`** *(gating — positive)* — after `sut.register(...)`, call `AppStartMetrics.getInstance().registerLifecycleCallbacks(mock())` + `waitForMainLooperIdle()`; assert `scopes.startTransaction` was called with op `app.start.cold` (listener fired → `onNoActivityStarted` ran).
2. **`OnNoActivityStartedListener is NOT registered when standalone flag is off`** *(gating — negative, default case)* — flag left at default `false`; drive idle; assert no `app.start.*` transaction started. *Covers the flag half of the guard at `ActivityLifecycleIntegration.java:130`.*
3. **`OnNoActivityStartedListener is NOT registered when performance is disabled`** *(gating — negative)* — `tracesSampleRate = null` (or `enableAutoActivityLifecycleTracing = false`), flag ON; drive idle; assert no `app.start.*` transaction. *Covers the `performanceEnabled` half of the guard.*
4. **`close clears OnNoActivityStartedListener`** *(cleanup — negative)* — `register`, then `close`, then drive idle; assert no `app.start.*` transaction. *Covers file:145 unregistration.*
5. **`onNoActivityStarted creates standalone App Start Cold transaction and stashes trace id`** *(happy path — cold)* — populate `appStartTimeSpan` (started + stopped in a valid range), `appStartType = COLD`; drive idle; assert exactly one `startTransaction` call with `op = "app.start.cold"`, `name = "App Start Cold"`, `transactionOptions.bindToScope = false`, start timestamp = `appStartTime`; assert `AppStartMetrics.getInstance().getAppStartTraceId() == captured transaction.traceId`; assert the returned tracer is finished with `OK`. *Covers `onNoActivityStarted` (file:880–920) and the trace-id stash at file:917.*
6. **`onNoActivityStarted creates standalone App Start Warm transaction when appStartType is WARM`** *(happy path — warm)* — same setup, `appStartType = WARM`; assert op `app.start.warm`, name `App Start Warm`. *Validates `getAppStartTxnName/Op(coldStart)` warm branch.*
7. **`onNoActivityStarted does nothing when appStartTimeSpan is incomplete`** *(negative)* — leave `appStartTimeSpan` unstarted (or started but not stopped); drive idle; assert no transaction started. *Covers the guard at file:890–892.*
8. **`launcher activity emits both ui_load and standalone App Start Cold sharing the same traceId and ui_load has no app_start child`** *(Bug 2 negative counterpart — standalone happy path, matches report scenario 1a)* — `setAppStartTime()`, `isAppLaunchedInForeground = true`, foreground importance; `register` + `onActivityCreated(activity, bundle)`. Capture all `startTransaction` calls. Assert exactly two: one with op `ui.load` and one with op `app.start.cold`, and `traceId` is identical across both. Assert `bindToScope = false` on the app-start standalone and default (true) on `ui.load`. **Assert the `ui.load` tracer has no child span with op `app.start.cold` or `app.start.warm`** — this is the core invariant of flag-ON: app-start data lives on the sibling standalone, not under `ui.load`. Then call `onActivityPostStarted(activity)` (or the API-appropriate started callback path) and assert the generated `activity.load` spans are parented to the standalone app-start transaction root, matching report scenario 1a. *Covers file:287–309 and indirectly validates `getAppStartParent()` precedence.*
9. **`activity following a non-activity start reuses trace id and does NOT emit a second standalone`** *(Bug 2 fix — positive, matches report scenario 2e)* — pre-seed `AppStartMetrics.getInstance().setAppStartTraceId(someId)`; `setAppStartTime()`; `register` + `onActivityCreated`. Capture `startTransaction` calls. Assert exactly **one** call, with `TransactionContext.traceId == someId` and `op == "ui.load"` (no app.start.* transaction). Assert `AppStartMetrics.getInstance().getAppStartTraceId() == null` (consumed). *Covers the Bug 2 guard at file:252–270 and the suppression at file:287–289.*
10. **`standalone flag OFF: launcher activity emits single ui_load with nested app_start_cold child`** *(legacy regression — matches report scenario 1c)* — `setAppStartTime()`, `isAppLaunchedInForeground = true`, foreground importance, `options.isEnableStandaloneAppStartTracing = false` (default), `tracesSampleRate = 1.0`; `register` + `onActivityCreated`. Capture `startTransaction` calls. Assert exactly **one** call with op `ui.load`. Assert the captured tracer has a direct child span with op `app.start.cold`. *Locks in the pre-branch behavior so the flag-OFF path cannot silently regress; mirrors T5.8 but with the flag OFF.*
11. **Add a sibling of `When Activity is destroyed, sets appStartSpan status to cancelled and finish it`** (file:525) — enable the standalone flag and assert that the created sibling standalone app-start transaction is finished with `CANCELLED` when the activity is destroyed before app-start finishes. Use the fixture transaction list to find the standalone transaction rather than relying on `fixture.transaction`, which points at the last created tracer. *Covers the `appStartTransaction` cancel path in `onActivityDestroyed()` (file:617–619) and avoids weakening the legacy nested-span test.*

> *Skipped / subsumed:*
> - Separate reflection-based tests for `getAppStartParent` precedence (transaction > span > activity txn) — subsumed by T5.8 once `activity.load` parentage is asserted.
> - Foreground-service (report scenario 2d) — the SDK code does not distinguish broadcast receivers from foreground services; both route through the same `OnNoActivityStartedListener` path. T5.5/T5.6 cover the code-level equivalent. The harness scenario 2d remains the integration-level check.

### T6 — `scripts/test-standalone-app-start.sh`

Strengthen scenario 2e assertions so the harness fails if duplicate standalone transactions regress.

1. **`2e asserts exactly one standalone app-start transaction`** *(Bug 2 hard guard)* — count `TXN|name=App Start Cold|...` / `TXN|name=App Start Warm|...` lines in `2e.log`; assert count is exactly `1`.
2. **`2e asserts exactly two total transactions (App Start + ui.load)`** *(shape guard)* — count all `SentryE2E.*TXN|` lines in `2e.log`; assert count is exactly `2`.

This closes the current gap where 2e only asserts "at least one app.start txn exists" and would not fail on a duplicate standalone.

### T7 — `TransactionContextTest.kt`

Add explicit tests for the new constructor introduced in this feature branch:

1. **`traceId constructor reuses provided trace id and operation`** — create `TransactionContext(traceId, name, source, op, samplingDecision)` and assert: `traceId` is preserved, `name` + `transactionNameSource` + `op` are set correctly, and `sampled/profileSampled` align with the given decision.
2. **`traceId constructor creates a fresh span id and baggage`** — assert `spanId` is non-null/new (not parent-linked), `parentSpanId == null`, and `baggage` exists with seeded sampling context.

### T8 — Optional on-device smoke tests (`connectedAndroidTest`)

These are optional best-effort smoke tests, not required branch guardrails. The existing `sentry-uitest-android` app removes both Sentry init providers and initializes the SDK from the instrumentation test after the app process already exists, so `launchActivity()` is not guaranteed to exercise a true cold app-start process. Treat these tests as useful envelope-shape smoke coverage only; the required app-start correctness coverage is T1–T7 plus T6/manual harness.

If implemented, add a single `StandaloneAppStartTracingIntegrationTest` that uses `MockRelay` and keeps assertions structural:

1. **`flag ON smoke emits standalone and ui.load when app-start data is available`** — initialize SDK with `enableStandaloneAppStartTracing=true`, `tracesSampleRate=1.0`, launch `StandaloneAppStartActivity`; if two transactions are emitted, assert one root op is `app.start.cold`/`app.start.warm`, one is `ui.load`, trace IDs match, and `ui.load` has no `app.start.*` child. Use `Assume`/skip semantics if the test environment does not produce app-start data.
2. **`flag OFF smoke keeps legacy nested app.start child when app-start data is available`** — initialize with `enableStandaloneAppStartTracing=false`, launch the same activity; if an app-start span is emitted, assert there is a single `ui.load` transaction with nested `app.start.*`.

**Notes for integration determinism**
- Use a dedicated `StandaloneAppStartActivity` with minimal startup work to reduce unrelated spans.
- Do not assert exact durations or cold vs warm classification in this instrumentation layer.
- Do not use these tests to replace the manual broadcast/service harness; instrumentation-runner process lifecycle makes non-activity cold-start orchestration flaky.

### T9 — Optional integration fixture additions

1. **Add `StandaloneAppStartActivity`** — a minimal activity used only by optional T8 smoke tests to reduce noise in span trees.
2. **Manifest registration in `sentry-uitest-android`** — register the new activity in the test app manifest.

---

## Integration / system tests

Use a two-layer required strategy, with optional smoke coverage:

1. **Unit tests (T1–T7)** — branch-level logic safety and edge-case coverage in CI.
2. **Manual system harness (T6 + existing script)** — full sentry.io ingestion validation across broader device/API scenarios.
3. **Optional on-device smoke tests (T8–T9)** — `connectedAndroidTest` + `MockRelay` envelope assertions if the environment produces app-start data, but not part of the done bar.

**Do not add new backend/system-test infrastructure.** Keep using the existing manual script (`scripts/test-standalone-app-start.sh`) for sentry.io ingestion checks. Add `connectedAndroidTest` coverage only as optional smoke protection, not as the source of truth for app-start behavior.

---

## Redundancy summary

**Merged into existing tests (not new test methods):**

- `appStartTraceId` cleared by `clear()` — extend `metrics are properly cleared` (AppStartMetricsTest.kt:56).
- `appStartTransaction` cancelled on destroy — add a sibling of `When Activity is destroyed, sets appStartSpan status to cancelled and finish it` (ActivityLifecycleIntegrationTest.kt:525) to cover the flag-ON branch without changing the legacy assertion.

**Dropped (low marginal value):**

- `appStartTraceId` / `onNoActivityStartedListener` getter/setter round-trip — trivial, exercised transitively by T3.3 / T5.5 / T5.9.
- `SentryAndroidOptions.setEnableStandaloneAppStartTracing` round-trip — trivial boolean setter; default is what matters and T2 covers it.
- Warm-path counterpart of T4.1 — the cold/warm switch is a trivial string lookup; already covered for the legacy path.
- `PerformanceAndroidEventProcessor` "non-app-start txn ignored" — subsumed by existing `hasAppStartSpan` gating tests.
- Direct reflection-based `getAppStartParent` precedence test — subsumed by T5.8 once `activity.load` parentage is asserted.
- Broadcast-only non-activity flow in `connectedAndroidTest` — kept in manual harness for now; instrumentation-runner lifecycle/force-stop constraints make reliable cold-start broadcast orchestration flaky. Script scenarios 2a–2f remain the source of truth for that path.

**Bug 3 in the report** (sample-app's missing `onApplicationCreate` call) is **not SDK code** and needs no SDK unit test — it's a fixture gap in the manual harness, already fixed in `MyApplication.java`. To prevent the harness from missing this class of issue again, optionally extend the `SentryE2E` log/assertions to expose non-zero `application.load` span start/stop or duration when `SIMULATE_GRADLE_PLUGIN=true`; local child-span presence alone was not enough to catch epoch-0 timestamps.

---

## Verification

1. **Run the new + extended tests locally:**
   ```bash
   ./gradlew :sentry-android-core:testDebugUnitTest \
     --tests '*ManifestMetadataReaderTest*' \
     --tests '*SentryAndroidOptionsTest*' \
     --tests '*AppStartMetricsTest*' \
     --tests '*AppStartMetricsTestApi35*' \
     --tests '*PerformanceAndroidEventProcessorTest*' \
     --tests '*ActivityLifecycleIntegrationTest*' \
     --info
   ```
2. **Run the core `sentry` unit tests for `TransactionContext` constructor coverage:**
   ```bash
   ./gradlew :sentry:test --tests '*TransactionContextTest*' --info
   ```
3. **Optional: run on-device smoke tests if T8/T9 are implemented:**
   ```bash
   ./gradlew :sentry-android-integration-tests:sentry-uitest-android:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=io.sentry.uitest.android.StandaloneAppStartTracingIntegrationTest \
     --info
   ```
4. **Run the full module suite to catch any incidental regressions:**
   ```bash
   ./gradlew :sentry-android-core:testDebugUnitTest
   ```
5. **Format + API check (must pass before commit):**
   ```bash
   ./gradlew spotlessApply apiDump
   ./gradlew :sentry-android-core:spotlessJavaCheck :sentry-android-core:spotlessKotlinCheck
   ```
6. **Sanity-run the E2E harness once** (`scripts/test-standalone-app-start.sh` with both emulators up) to confirm no unintended behavioral change — this remains the only check that verifies final ingestion shape in sentry.io exactly as documented in `standalone_app_start_report.md`.
7. **CI:** android-core tests run in existing `sentry-android-core:testDebugUnitTest`; `TransactionContextTest` runs in existing `:sentry:test`. Optional T8/T9 smoke tests can run in existing Android UI test lanes if they are implemented, but they should not gate this branch.

**Done when:** all required new tests pass (unit + API-35 + `TransactionContext`), existing suites stay green, the 2e harness cardinality checks pass, and spotless + apiDump are clean. Optional T8/T9 smoke tests are a bonus, not required for completion.
