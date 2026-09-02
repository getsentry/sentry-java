# PR Review Guidelines for Cursor Bugbot

You are reviewing a pull request for the Sentry Java/Android SDK.

Read [`AGENTS.md`](../AGENTS.md) for build commands and contributing rules, and the matching
rule file in [`.cursor/rules/`](rules) for the area the diff touches (`api`, `options`, `scopes`,
`offline`, `opentelemetry`, ...).

## Critical

### Never crash or hang the host application

- While we don't want to crash or hang the host application, we also don't want to leave the host application in a bad or unrecoverable state. Therefore catch the narrowest type the guarded code can throw.
- Existing broad catches like `catch (Throwable)` are legacy, not precedent. Where a broad catch is genuinely unavoidable (an entry point
  running user code or third-party callbacks), it must call `ExceptionUtils.rethrowIfFatal(t)` first
  and the PR description must say why the broad catch is needed.
- Code probing for an optional `compileOnly` dependency must catch the specific `LinkageError`
  subclass (`NoClassDefFoundError`, `NoSuchMethodError`, ...) only.
- The SDK must never `captureException`/`captureMessage` for its own failures or for exceptions
  thrown inside user callbacks (`beforeSend`, `beforeBreadcrumb`, `tracesSampler`, ...). Log via
  `options.getLogger()` instead — capturing here loops. See
  [Never capture your own exceptions](https://develop.sentry.dev/sdk/getting-started/principles/#never-capture-your-own-exceptions).
- Flag `System.out`/`System.err`, `printStackTrace()`, and `android.util.Log` in SDK source; use
  `options.getLogger().log(...)`.
- Flag resources acquired but not released: streams, files, `ExecutorService`s, `BroadcastReceiver`s,
  lifecycle/activity callbacks, sensors, timers. Anything registered during init must be undone in
  the integration's `close()`.

### Security and privacy

- Real secrets, tokens, or DSNs in code, logs, or configs. Obviously-fake DSNs in tests, samples,
  and docs are expected — do not flag those.
- New code that collects user-identifiable data (headers, cookies, request/response bodies, URL
  query strings, IPs, usernames, file paths, device identifiers) must be gated behind
  `options.isSendDefaultPii()`, and must not be on by default otherwise.
- Debug flags, verbose logging, or sampling overrides accidentally left enabled in production
  defaults.

### Public API and compatibility

- `.api` files are generated. Flag hand edits; the fix is `./gradlew apiDump`.
- New public API must be intentional: new internal classes/methods need `@ApiStatus.Internal`, new
  unstable API needs `@ApiStatus.Experimental`.
- Removing or changing the signature of public API, or silently changing a default, sampling rate,
  or feature toggle, without a deprecation and a `CHANGELOG.md`/`MIGRATION.md` note.
- New features must be **opt-in by default** via `SentryOptions` (or a namespaced options class).
- Adding a method to `IScope`/`IScopes` requires updating every implementation and stub —
  `Scope`, `Scopes`, `CombinedScopeView`, `NoOpScope`, `NoOpScopes`, `ScopesAdapter`, `HubAdapter`.
  Flag partial updates.
- New fields on `io.sentry.protocol` classes need both serialization and deserialization, plus a
  round-trip test.
- Raising `minSdk`, the Java level, or a supported framework version without an explicit callout.

## Java and Android specifics

- The core `sentry` module is Java 8 and must not reference Android or JVM-only APIs. Reach optional
  platform code through `Platform`, `LoadClass`, or a separate module.
- Android code calling an API newer than `minSdk` must be guarded by
  `BuildInfoProvider.getSdkInfoVersion()`.
- `Sentry.init` can be called from any thread, and on Android it runs on the main thread during app
  startup. Flag disk I/O, network calls, reflection, class loading, regex compilation, or eager
  allocation newly added to an init path — and static mutable state that is not thread-safe.

## Instrumentation conventions

- Every started span must be finished on all paths, including error paths.
- Automatically instrumented spans set an origin (`SpanOptions.setOrigin`) and a standard
  [span op](https://develop.sentry.dev/sdk/telemetry/traces/span-operations/). Origins must match
  `[A-Za-z0-9_.]` — see the
  [trace origin spec](https://develop.sentry.dev/sdk/telemetry/traces/trace-origin/).
- New integrations register themselves with `IntegrationUtils.addIntegrationToSdkVersion(...)`.
- Errors in instrumented user code should bubble up so the host app's handlers see them. Flag
  instrumentation that swallows an error without recording it, and instrumentation that captures an
  error that would also reach the global handlers (double reporting).

## Concurrency
- The SDK uses raw java concurrency primitives. Ensure we are using them correctly.
- Ensure that atomic actions are atomic.
- Watch for possible deadlocks in general but especially when two locks are held and another thread can grab them in the opposite order.
- Prefer using existing executors over creating new threads.
- Do not block the main thread on Android with locking, synchronization or I/O calls.
- Watch for ordering issues when classes can be called from different threads.
- Flag a lock held across a callback into user code, an I/O call, or an `ExecutorService` submission.
- Mark a field `volatile` when it is written on one thread and read on another without a lock. A plain field read is a data race, not merely a stale value.
- Read mutable shared state once per operation. Re-reading the same field for several decisions in one pass lets it change mid-pass, so the results disagree with each other.
- Prefer the `synchronized` keyword. Existing code that uses `AutoClosableReentrantLock` is legacy.

## Clocks
- Ensure we are using a monotonic clock to measure time intervals.
- Ensure we are using a wall clock for dates and timestamps.

## Tests

- Behavior changes need tests. A `fix` PR should include a regression test that fails without the
  fix; if the diff doesn't make that clear, ask the author to confirm.
- Flag hollow tests: assertions that only prove "did not throw", or that assert on a payload without
  checking the newly added data.
- New assertions should use Google Truth (`com.google.common.truth.Truth.assertThat`); `kotlin.test`
  stays for structure (`@Test`, `assertFailsWith`). Don't flag existing `kotlin.test` assertions.
- Flag likely flakes: `Thread.sleep`, wall-clock or ordering assumptions, real network or filesystem
  access, and shared static state left dirty between tests.

## What NOT to flag

- Formatting and import order — Spotless owns it.
- Contents of generated `.api` files, beyond confirming `apiDump` was run.
- Conventional commit / PR title format, and missing changelog entries — CI and Danger check both.
- Speculative refactors or improvements unrelated to the diff.
