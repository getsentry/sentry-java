This is the Sentry Java/Android SDK. It runs inside customers' applications, on their threads, from
`Sentry.init` on the Android main thread to every captured event. A defect here crashes or hangs
someone else's app, and a released SDK version stays in shipped apps for months. Judge a change
by what it can do to the host application, not by its size. Read the points below as mechanisms
to look for in the code, not as labels by path.

Credible paths to high risk:

- Crashing or hanging the host app. Examples: a new broad catch (`Throwable`, `Exception`, ...)
  outside a public API or cross-process boundary, a probe for an optional `compileOnly` dependency
  guarded only by `ExceptionUtils.rethrowIfFatal` (it rethrows `LinkageError`), or an SDK-internal
  failure that calls `captureException` and loops.
- Deadlocks and ANRs. Examples: a lock held while finishing a span or transaction, capturing an
  event, logging through `options.getLogger()`, or invoking a user callback (these re-enter the
  SDK), two locks taken in different orders, or blocking work on the Android main thread.
- New disk I/O, network calls, reflection, class loading or eager allocation on the init path.
- Changed defaults: a feature that is on without an option, a new default sample rate, or a
  changed feature toggle or option default. New features must be opt-in.
- Removing or changing the signature of public API in `sentry`, `sentry-android-core` or an
  integration without a deprecation, or a breaking change to `InternalSentrySdk`, which hybrid
  SDKs (React Native, Flutter, Unity, ...) call.
- Collecting user-identifiable data (headers, cookies, bodies, query strings, IPs, user names,
  device identifiers) without gating it on `options.isSendDefaultPii()`.
- Envelope, transport, caching, rate limiting or retry changes (`AsyncHttpTransport`,
  `HttpConnection`, `RateLimiter`, `EnvelopeCache`). A mistake here can drop or flood all data for
  every customer.
- Changing serialization or deserialization of `io.sentry.protocol` classes or envelope items.
- Raising `minSdk`, the Java level or a supported framework version, or bumping a dependency in a
  version-matrix slot that should stay on an older major (for example `springKafka2` moving off
  2.x).
- Measuring durations with wall-clock time, or subtracting timestamps, in code that dates or orders
  events, crashes, ANRs or sessions.

Usually low when the code confirms it:

- Changes only to tests, `sentry-samples`, the Android integration test build, docs, `CHANGELOG.md`,
  `MIGRATION.md`, `.agents`/`.cursor` instructions, or CI that does not publish or sign releases.
- Generated `.api` files that only add entries; they mirror source changes elsewhere in the diff.
- Dependency bumps of test-only or sample-only dependencies.

A small diff in the areas above can still be high, and a large diff of tests or samples can still
be low.
