---
alwaysApply: false
description: JVM Continuous Profiling (sentry-async-profiler)
---
# JVM Continuous Profiling

Use this rule when working on JVM continuous profiling in `sentry-async-profiler` and the related core profiling abstractions in `sentry`.

This area is suitable for LLM work, but do not rely on this rule alone for behavior changes. Always read the implementation and nearby tests first, especially for sampling, lifecycle, rate limiting, and file cleanup behavior.

## Module Structure

- **`sentry-async-profiler`**: standalone module containing the async-profiler integration
  - Uses Java `ServiceLoader` discovery
  - No direct dependency from core `sentry` module
  - Enabled by adding the module as a dependency

- **`sentry` core abstractions**:
  - `IContinuousProfiler`: profiler lifecycle interface
  - `ProfileChunk`: profile chunk payload sent to Sentry
  - `IProfileConverter`: converts JVM JFR files into `SentryProfile`
  - `ProfileLifecycle`: controls MANUAL vs TRACE lifecycle
  - `ProfilingServiceLoader`: loads profiler and converter implementations via `ServiceLoader`

## Key Classes

### `JavaContinuousProfiler` (`sentry-async-profiler`)
- Wraps the native async-profiler library
- Writes JFR files to `profilingTracesDirPath`
- Rotates chunks periodically via `MAX_CHUNK_DURATION_MILLIS` (currently 10s)
- Implements `RateLimiter.IRateLimitObserver`
- Maintains `rootSpanCounter` for TRACE lifecycle
- Keeps a session-level `profilerId` across chunks until the profiling session ends
- `getChunkId()` currently returns `SentryId.EMPTY_ID`, but emitted `ProfileChunk`s get a fresh chunk id when built in `stop(...)`

### `ProfileChunk`
- Carries `profilerId`, `chunkId`, timestamp, platform, measurements, and a JFR file reference
- Built via `ProfileChunk.Builder`
- For JVM, the JFR file is converted later during envelope item creation, not inside `JavaContinuousProfiler`

### `ProfileLifecycle`
- `MANUAL`: explicit `Sentry.startProfiler()` / `Sentry.stopProfiler()`
- `TRACE`: profiler lifecycle follows active sampled root spans

## Configuration

Continuous profiling is **not** controlled by `profilesSampleRate`.

Key options:
- **`profileSessionSampleRate`**: session-level sample rate for continuous profiling
- **`profileLifecycle`**: `ProfileLifecycle.MANUAL` (default) or `ProfileLifecycle.TRACE`
- **`cacheDirPath`**: base SDK cache directory; profiling traces are written under the derived `profilingTracesDirPath`
- **`profilingTracesHz`**: sampling frequency in Hz (default: 101)

Continuous profiling is enabled when:
- `profilesSampleRate == null`
- `profilesSampler == null`
- `profileSessionSampleRate != null && profileSessionSampleRate > 0`

Example:

```java
options.setProfileSessionSampleRate(1.0);
options.setCacheDirPath("/tmp/sentry-cache");
options.setProfileLifecycle(ProfileLifecycle.MANUAL);
options.setProfilingTracesHz(101);
```

## How It Works

### Initialization
- `InitUtil.initializeProfiler(...)` resolves or creates the profiling traces directory
- `ProfilingServiceLoader.loadContinuousProfiler(...)` uses `ServiceLoader` to find `JavaContinuousProfilerProvider`
- `AsyncProfilerContinuousProfilerProvider` instantiates `JavaContinuousProfiler`
- `ProfilingServiceLoader.loadProfileConverter()` separately loads the `JavaProfileConverterProvider`

### Profiling Flow

**Start**
- Sampling decision is made via `TracesSampler.sampleSessionProfile(...)`
- Sampling is session-based and cached until `reevaluateSampling()`
- Scopes and rate limiter are initialized lazily via `initScopes()`
- Rate limits for `All` or `ProfileChunk` abort startup
- JFR filename is generated under `profilingTracesDirPath`
- async-profiler is started with a command like:
  - `start,jfr,event=wall,nobatch,interval=<interval>,file=<path>`
- Automatic chunk stop is scheduled after `MAX_CHUNK_DURATION_MILLIS`

**Chunk Rotation**
- `stop(true)` stops async-profiler and validates the JFR file
- A `ProfileChunk.Builder` is created with:
  - current `profilerId`
  - a fresh `chunkId`
  - trace file
  - chunk timestamp
  - platform `java`
- Builder is buffered in `payloadBuilders`
- Chunks are sent if scopes are available
- Profiling is restarted for the next chunk

**Stop**
- `MANUAL`: stop immediately, do not restart, reset `profilerId`
- `TRACE`: decrement `rootSpanCounter`; stop only when it reaches 0
- `close(...)` also forces shutdown and resets TRACE state

### Sending and Conversion
- `JavaContinuousProfiler` buffers `ProfileChunk.Builder` instances
- `sendChunks(...)` builds `ProfileChunk` objects and calls `scopes.captureProfileChunk(...)`
- `SentryClient.captureProfileChunk(...)` creates an envelope item
- JVM JFR-to-`SentryProfile` conversion happens in `SentryEnvelopeItem.fromProfileChunk(...)` using the loaded `IProfileConverter`
- Trace files are deleted in the envelope item path after serialization attempts

## TRACE Mode Lifecycle
- `rootSpanCounter` increments when sampled root spans start
- `rootSpanCounter` decrements when root spans finish
- Profiler runs while `rootSpanCounter > 0`
- Multiple concurrent sampled transactions can share the same profiling session
- Be careful when changing lifecycle logic: this area is lock-protected and concurrency-sensitive

## Rate Limiting and Buffering

### Rate Limiting
- Registers as a `RateLimiter.IRateLimitObserver`
- If rate limited for `ProfileChunk` or `All`:
  - profiler stops immediately
  - it does not auto-restart when the limit expires
- Startup also checks rate limiting before profiling begins

### Buffering / pre-init behavior
- JFR files are written to `profilingTracesDirPath` and marked `deleteOnExit()` when a chunk is accepted
- If scopes are not yet available, `ProfileChunk.Builder`s remain buffered in memory in `payloadBuilders`
- This commonly matters for profiling that starts before SDK scopes are ready
- This is not a dedicated durable offline queue owned by the profiler itself; conversion and final send happen later in the normal client/envelope path

## Extending

To add or replace JVM profiler implementations:
- implement `IContinuousProfiler`
- implement `JavaContinuousProfilerProvider`
- register provider in:
  - `META-INF/services/io.sentry.profiling.JavaContinuousProfilerProvider`

To add or replace JVM profile conversion:
- implement `IProfileConverter`
- implement `JavaProfileConverterProvider`
- register provider in:
  - `META-INF/services/io.sentry.profiling.JavaProfileConverterProvider`

## Code Locations

Primary implementation:
- `sentry/src/main/java/io/sentry/IContinuousProfiler.java`
- `sentry/src/main/java/io/sentry/ProfileChunk.java`
- `sentry/src/main/java/io/sentry/profiling/ProfilingServiceLoader.java`
- `sentry/src/main/java/io/sentry/util/InitUtil.java`
- `sentry/src/main/java/io/sentry/SentryEnvelopeItem.java`
- `sentry-async-profiler/src/main/java/io/sentry/asyncprofiler/profiling/JavaContinuousProfiler.java`
- `sentry-async-profiler/src/main/java/io/sentry/asyncprofiler/provider/AsyncProfilerContinuousProfilerProvider.java`
- `sentry-async-profiler/src/main/java/io/sentry/asyncprofiler/provider/AsyncProfilerProfileConverterProvider.java`
- `sentry-async-profiler/src/main/java/io/sentry/asyncprofiler/convert/JfrAsyncProfilerToSentryProfileConverter.java`

Tests to read first:
- `sentry-async-profiler/src/test/java/io/sentry/asyncprofiler/profiling/JavaContinuousProfilerTest.kt`
- `sentry-async-profiler/src/test/java/io/sentry/asyncprofiler/JavaContinuousProfilingServiceLoaderTest.kt`
- `sentry-async-profiler/src/test/java/io/sentry/asyncprofiler/convert/JfrAsyncProfilerToSentryProfileConverterTest.kt`

## LLM Guidance

This rule is good enough for orientation, but for actual code changes always verify:
- the sampling path in `TracesSampler`
- continuous profiling enablement in `SentryOptions`
- lifecycle entry points in `Scopes` and `SentryTracer`
- conversion and file deletion behavior in `SentryEnvelopeItem`
- existing tests before changing concurrency or lifecycle semantics
