# Changelog

## Unreleased

### Features

- Attach View Hierarchy to the errored/crashed events ([#2440](https://github.com/getsentry/sentry-java/pull/2440))
- Collect memory usage in transactions ([#2445](https://github.com/getsentry/sentry-java/pull/2445))
- Add `traceOptionsRequests` option to disable tracing of OPTIONS requests ([#2453](https://github.com/getsentry/sentry-java/pull/2453))
- Extend list of HTTP headers considered sensitive ([#2455](https://github.com/getsentry/sentry-java/pull/2455))

### Fixes

- Use a single TransactionPerfomanceCollector ([#2464](https://github.com/getsentry/sentry-java/pull/2464))
- Don't override sdk name with Timber ([#2450](https://github.com/getsentry/sentry-java/pull/2450))
- Set transactionNameSource to CUSTOM when setting transaction name ([#2405](https://github.com/getsentry/sentry-java/pull/2405))

## 6.11.0

### Features

- Disable Android concurrent profiling ([#2434](https://github.com/getsentry/sentry-java/pull/2434))
- Add logging for OpenTelemetry integration ([#2425](https://github.com/getsentry/sentry-java/pull/2425))
- Auto add `OpenTelemetryLinkErrorEventProcessor` for Spring Boot ([#2429](https://github.com/getsentry/sentry-java/pull/2429))

### Fixes

- Use minSdk compatible `Objects` class ([#2436](https://github.com/getsentry/sentry-java/pull/2436))
- Prevent R8 from warning on missing classes, as we check for their presence at runtime ([#2439](https://github.com/getsentry/sentry-java/pull/2439))

### Dependencies

- Bump Gradle from v7.5.1 to v7.6.0 ([#2438](https://github.com/getsentry/sentry-java/pull/2438))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v760)
  - [diff](https://github.com/gradle/gradle/compare/v7.5.1...v7.6.0)

## 6.10.0

### Features

- Add time-to-initial-display span to Activity transactions ([#2369](https://github.com/getsentry/sentry-java/pull/2369))
- Start a session after init if AutoSessionTracking is enabled ([#2356](https://github.com/getsentry/sentry-java/pull/2356))
- Provide automatic breadcrumbs and transactions for click/scroll events for Compose ([#2390](https://github.com/getsentry/sentry-java/pull/2390))
- Add `blocked_main_thread` and `call_stack` to File I/O spans to detect performance issues ([#2382](https://github.com/getsentry/sentry-java/pull/2382))

### Dependencies

- Bump Native SDK from v0.5.2 to v0.5.3 ([#2423](https://github.com/getsentry/sentry-java/pull/2423))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#053)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.5.2...0.5.3)

## 6.9.2

### Fixes

- Updated ProfileMeasurementValue types ([#2412](https://github.com/getsentry/sentry-java/pull/2412))
- Clear window reference only on activity stop in profileMeasurements collector ([#2407](https://github.com/getsentry/sentry-java/pull/2407))
- No longer disable OpenTelemetry exporters in default Java Agent config ([#2408](https://github.com/getsentry/sentry-java/pull/2408))
- Fix `ClassNotFoundException` for `io.sentry.spring.SentrySpringServletContainerInitializer` in `sentry-spring-jakarta` ([#2411](https://github.com/getsentry/sentry-java/issues/2411))
- Fix `sentry-samples-spring-jakarta` ([#2411](https://github.com/getsentry/sentry-java/issues/2411))

### Features

- Add SENTRY_AUTO_INIT environment variable to control OpenTelemetry Agent init ([#2410](https://github.com/getsentry/sentry-java/pull/2410))
- Add OpenTelemetryLinkErrorEventProcessor for linking errors to traces created via OpenTelemetry ([#2418](https://github.com/getsentry/sentry-java/pull/2418))

### Dependencies

- Bump OpenTelemetry to 1.20.1 and OpenTelemetry Java Agent to 1.20.2 ([#2420](https://github.com/getsentry/sentry-java/pull/2420))

## 6.9.1

### Fixes

- OpenTelemetry modules were missing in `6.9.0` so we released the same code again as `6.9.1` including OpenTelemetry modules

## 6.9.0

### Fixes

- Use `canonicalName` in Fragment Integration for better de-obfuscation ([#2379](https://github.com/getsentry/sentry-java/pull/2379))
- Fix Timber and Fragment integrations auto-installation for obfuscated builds ([#2379](https://github.com/getsentry/sentry-java/pull/2379))
- Don't attach screenshots to events from Hybrid SDKs ([#2360](https://github.com/getsentry/sentry-java/pull/2360))
- Ensure Hints do not cause memory leaks ([#2387](https://github.com/getsentry/sentry-java/pull/2387))
- Do not attach empty `sentry-trace` and `baggage` headers ([#2385](https://github.com/getsentry/sentry-java/pull/2385))

### Features

- Add beforeSendTransaction which allows users to filter and change transactions ([#2388](https://github.com/getsentry/sentry-java/pull/2388))
- Add experimental support for OpenTelemetry ([README](sentry-opentelemetry/README.md))([#2344](https://github.com/getsentry/sentry-java/pull/2344))

### Dependencies

- Update Spring Boot Jakarta to Spring Boot 3.0.0 ([#2389](https://github.com/getsentry/sentry-java/pull/2389))
- Bump Spring Boot to 2.7.5 ([#2383](https://github.com/getsentry/sentry-java/pull/2383))

## 6.8.0

### Features

- Add FrameMetrics to Android profiling data ([#2342](https://github.com/getsentry/sentry-java/pull/2342))

### Fixes

- Remove profiler main thread io ([#2348](https://github.com/getsentry/sentry-java/pull/2348))
- Fix ensure all options are processed before integrations are loaded ([#2377](https://github.com/getsentry/sentry-java/pull/2377))

## 6.7.1

### Fixes

- Fix `Gpu.vendorId` should be a String ([#2343](https://github.com/getsentry/sentry-java/pull/2343))
- Don't set device name on Android if `sendDefaultPii` is disabled ([#2354](https://github.com/getsentry/sentry-java/pull/2354))
- Fix corrupted UUID on Motorola devices ([#2363](https://github.com/getsentry/sentry-java/pull/2363))
- Fix ANR on dropped uncaught exception events ([#2368](https://github.com/getsentry/sentry-java/pull/2368))

### Features

- Update Spring Boot Jakarta to Spring Boot 3.0.0-RC2 ([#2347](https://github.com/getsentry/sentry-java/pull/2347))

## 6.7.0

### Fixes

- Use correct set-cookie for the HTTP Client response object ([#2326](https://github.com/getsentry/sentry-java/pull/2326))
- Fix NoSuchElementException in CircularFifoQueue when cloning a Scope ([#2328](https://github.com/getsentry/sentry-java/pull/2328))

### Features

- Customizable fragment lifecycle breadcrumbs ([#2299](https://github.com/getsentry/sentry-java/pull/2299))
- Provide hook for Jetpack Compose navigation instrumentation ([#2320](https://github.com/getsentry/sentry-java/pull/2320))
- Populate `event.modules` with dependencies metadata ([#2324](https://github.com/getsentry/sentry-java/pull/2324))
- Support Spring 6 and Spring Boot 3 ([#2289](https://github.com/getsentry/sentry-java/pull/2289))

### Dependencies

- Bump Native SDK from v0.5.1 to v0.5.2 ([#2315](https://github.com/getsentry/sentry-java/pull/2315))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#052)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.5.1...0.5.2)

## 6.6.0

### Fixes

- Ensure potential callback exceptions are caught #2123 ([#2291](https://github.com/getsentry/sentry-java/pull/2291))
- Remove verbose FrameMetricsAggregator failure logging ([#2293](https://github.com/getsentry/sentry-java/pull/2293))
- Ignore broken regex for tracePropagationTarget ([#2288](https://github.com/getsentry/sentry-java/pull/2288))
- No longer serialize static fields; use toString as fallback ([#2309](https://github.com/getsentry/sentry-java/pull/2309))
- Fix `SentryFileWriter`/`SentryFileOutputStream` append overwrites file contents ([#2304](https://github.com/getsentry/sentry-java/pull/2304))
- Respect incoming parent sampled decision when continuing a trace ([#2311](https://github.com/getsentry/sentry-java/pull/2311))

### Features

- Profile envelopes are sent directly from profiler ([#2298](https://github.com/getsentry/sentry-java/pull/2298))
- Add support for using Encoder with logback.SentryAppender ([#2246](https://github.com/getsentry/sentry-java/pull/2246))
- Report Startup Crashes ([#2277](https://github.com/getsentry/sentry-java/pull/2277))
- HTTP Client errors for OkHttp ([#2287](https://github.com/getsentry/sentry-java/pull/2287))
- Add option to enable or disable Frame Tracking ([#2314](https://github.com/getsentry/sentry-java/pull/2314))

### Dependencies

- Bump Native SDK from v0.5.0 to v0.5.1 ([#2306](https://github.com/getsentry/sentry-java/pull/2306))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#051)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.5.0...0.5.1)

## 6.5.0

### Fixes

- Improve public facing API for creating Baggage from header ([#2284](https://github.com/getsentry/sentry-java/pull/2284))

## 6.5.0-beta.3

### Features

- Provide API for attaching custom measurements to transactions ([#2260](https://github.com/getsentry/sentry-java/pull/2260))
- Bump spring to 2.7.4 ([#2279](https://github.com/getsentry/sentry-java/pull/2279))

## 6.5.0-beta.2

### Features

- Make user segment a top level property ([#2257](https://github.com/getsentry/sentry-java/pull/2257))
- Replace user `other` with `data` ([#2258](https://github.com/getsentry/sentry-java/pull/2258))
- `isTraceSampling` is now on by default. `tracingOrigins` has been replaced by `tracePropagationTargets` ([#2255](https://github.com/getsentry/sentry-java/pull/2255))

## 6.5.0-beta.1

### Features

- Server-Side Dynamic Sampling Context support  ([#2226](https://github.com/getsentry/sentry-java/pull/2226))

## 6.4.4

### Fixes

- Fix ConcurrentModificationException due to FrameMetricsAggregator manipulation ([#2282](https://github.com/getsentry/sentry-java/pull/2282))

## 6.4.3

- Fix slow and frozen frames tracking ([#2271](https://github.com/getsentry/sentry-java/pull/2271))

## 6.4.2

### Fixes

- Fixed AbstractMethodError when getting Lifecycle ([#2228](https://github.com/getsentry/sentry-java/pull/2228))
- Missing unit fields for Android measurements ([#2204](https://github.com/getsentry/sentry-java/pull/2204))
- Avoid sending empty profiles ([#2232](https://github.com/getsentry/sentry-java/pull/2232))
- Fix file descriptor leak in FileIO instrumentation ([#2248](https://github.com/getsentry/sentry-java/pull/2248))

## 6.4.1

### Fixes

- Fix memory leak caused by throwableToSpan ([#2227](https://github.com/getsentry/sentry-java/pull/2227))

## 6.4.0

### Fixes

- make profiling rate defaults to 101 hz ([#2211](https://github.com/getsentry/sentry-java/pull/2211))
- SentryOptions.setProfilingTracesIntervalMillis has been deprecated
- Added cpu architecture and default environment in profiles envelope ([#2207](https://github.com/getsentry/sentry-java/pull/2207))
- SentryOptions.setProfilingEnabled has been deprecated in favor of setProfilesSampleRate
- Use toString for enum serialization ([#2220](https://github.com/getsentry/sentry-java/pull/2220))

### Features

- Concurrent profiling 3 - added truncation reason ([#2247](https://github.com/getsentry/sentry-java/pull/2247))
- Concurrent profiling 2 - added list of transactions ([#2218](https://github.com/getsentry/sentry-java/pull/2218))
- Concurrent profiling 1 - added envelope payload data format ([#2216](https://github.com/getsentry/sentry-java/pull/2216))
- Send source for transactions ([#2180](https://github.com/getsentry/sentry-java/pull/2180))
- Add profilesSampleRate and profileSampler options for Android sdk ([#2184](https://github.com/getsentry/sentry-java/pull/2184))
- Add baggage header to RestTemplate ([#2206](https://github.com/getsentry/sentry-java/pull/2206))
- Bump Native SDK from v0.4.18 to v0.5.0 ([#2199](https://github.com/getsentry/sentry-java/pull/2199))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#050)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.4.18...0.5.0)
- Bump Gradle from v7.5.0 to v7.5.1 ([#2212](https://github.com/getsentry/sentry-java/pull/2212))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v751)
  - [diff](https://github.com/gradle/gradle/compare/v7.5.0...v7.5.1)

## 6.3.1

### Fixes

- Prevent NPE by checking SentryTracer.timer for null again inside synchronized ([#2200](https://github.com/getsentry/sentry-java/pull/2200))
- Weakly reference Activity for transaction finished callback ([#2203](https://github.com/getsentry/sentry-java/pull/2203))
- `attach-screenshot` set on Manual init. didn't work ([#2186](https://github.com/getsentry/sentry-java/pull/2186))
- Remove extra space from `spring.factories` causing issues in old versions of Spring Boot ([#2181](https://github.com/getsentry/sentry-java/pull/2181))


### Features

- Bump Native SDK to v0.4.18 ([#2154](https://github.com/getsentry/sentry-java/pull/2154))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#0418)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.4.17...0.4.18)
- Bump Gradle to v7.5.0 ([#2174](https://github.com/getsentry/sentry-java/pull/2174), [#2191](https://github.com/getsentry/sentry-java/pull/2191))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v750)
  - [diff](https://github.com/gradle/gradle/compare/v7.4.2...v7.5.0)

## 6.3.0

### Features

- Switch upstream dependencies to `compileOnly` in integrations ([#2175](https://github.com/getsentry/sentry-java/pull/2175))

### Fixes

- Lazily retrieve HostnameCache in MainEventProcessor ([#2170](https://github.com/getsentry/sentry-java/pull/2170))

## 6.2.1

### Fixes

- Only send userid in Dynamic Sampling Context if sendDefaultPii is true ([#2147](https://github.com/getsentry/sentry-java/pull/2147))
- Remove userId from baggage due to PII ([#2157](https://github.com/getsentry/sentry-java/pull/2157))

### Features

- Add integration for Apollo-Kotlin 3 ([#2109](https://github.com/getsentry/sentry-java/pull/2109))
- New package `sentry-android-navigation` for AndroidX Navigation support ([#2136](https://github.com/getsentry/sentry-java/pull/2136))
- New package `sentry-compose` for Jetpack Compose support (Navigation) ([#2136](https://github.com/getsentry/sentry-java/pull/2136))
- Add sample rate to baggage as well as trace in envelope header and flatten user ([#2135](https://github.com/getsentry/sentry-java/pull/2135))

## 6.1.4

### Fixes

- Filter out app starts with more than 60s ([#2127](https://github.com/getsentry/sentry-java/pull/2127))

## 6.1.3

### Fixes

- Fix thread leak due to Timer being created and never cancelled ([#2131](https://github.com/getsentry/sentry-java/pull/2131))

## 6.1.2

### Fixes

- Swallow error when reading ActivityManager#getProcessesInErrorState instead of crashing ([#2114](https://github.com/getsentry/sentry-java/pull/2114))
- Use charset string directly as StandardCharsets is not available on earlier Android versions ([#2111](https://github.com/getsentry/sentry-java/pull/2111))

## 6.1.1

### Features

- Replace `tracestate` header with `baggage` header ([#2078](https://github.com/getsentry/sentry-java/pull/2078))
- Allow opting out of device info collection that requires Inter-Process Communication (IPC) ([#2100](https://github.com/getsentry/sentry-java/pull/2100))

## 6.1.0

### Features

- Implement local scope by adding overloads to the capture methods that accept a ScopeCallback ([#2084](https://github.com/getsentry/sentry-java/pull/2084))
- SentryOptions#merge is now public and can be used to load ExternalOptions ([#2088](https://github.com/getsentry/sentry-java/pull/2088))

### Fixes

- Fix proguard rules to work R8 [issue](https://issuetracker.google.com/issues/235733922) around on AGP 7.3.0-betaX and 7.4.0-alphaX ([#2094](https://github.com/getsentry/sentry-java/pull/2094))
- Fix GraalVM Native Image compatibility ([#2172](https://github.com/getsentry/sentry-java/pull/2172))

## 6.0.0

### Sentry Self-hosted Compatibility

- Starting with version `6.0.0` of the `sentry` package, [Sentry's self hosted version >= v21.9.0](https://github.com/getsentry/self-hosted/releases) is required or you have to manually disable sending client reports via the `sendClientReports` option. This only applies to self-hosted Sentry. If you are using [sentry.io](https://sentry.io), no action is needed.

### Features

- Allow optimization and obfuscation of the SDK by reducing proguard rules ([#2031](https://github.com/getsentry/sentry-java/pull/2031))
- Relax TransactionNameProvider ([#1861](https://github.com/getsentry/sentry-java/pull/1861))
- Use float instead of Date for protocol types for higher precision ([#1737](https://github.com/getsentry/sentry-java/pull/1737))
- Allow setting SDK info (name & version) in manifest ([#2016](https://github.com/getsentry/sentry-java/pull/2016))
- Allow setting native Android SDK name during build ([#2035](https://github.com/getsentry/sentry-java/pull/2035))
- Include application permissions in Android events ([#2018](https://github.com/getsentry/sentry-java/pull/2018))
- Automatically create transactions for UI events ([#1975](https://github.com/getsentry/sentry-java/pull/1975))
- Hints are now used via a Hint object and passed into beforeSend and EventProcessor as @NotNull Hint object ([#2045](https://github.com/getsentry/sentry-java/pull/2045))
- Attachments can be manipulated via hint ([#2046](https://github.com/getsentry/sentry-java/pull/2046))
- Add sentry-servlet-jakarta module ([#1987](https://github.com/getsentry/sentry-java/pull/1987))
- Add client reports ([#1982](https://github.com/getsentry/sentry-java/pull/1982))
- Screenshot is taken when there is an error ([#1967](https://github.com/getsentry/sentry-java/pull/1967))
- Add Android profiling traces ([#1897](https://github.com/getsentry/sentry-java/pull/1897)) ([#1959](https://github.com/getsentry/sentry-java/pull/1959)) and its tests ([#1949](https://github.com/getsentry/sentry-java/pull/1949))
- Enable enableScopeSync by default for Android ([#1928](https://github.com/getsentry/sentry-java/pull/1928))
- Feat: Vendor JSON ([#1554](https://github.com/getsentry/sentry-java/pull/1554))
    - Introduce `JsonSerializable` and `JsonDeserializer` interfaces for manual json
      serialization/deserialization.
    - Introduce `JsonUnknwon` interface to preserve unknown properties when deserializing/serializing
      SDK classes.
    - When passing custom objects, for example in `Contexts`, these are supported for serialization:
        - `JsonSerializable`
        - `Map`, `Collection`, `Array`, `String` and all primitive types.
        - Objects with the help of refection.
            - `Map`, `Collection`, `Array`, `String` and all primitive types.
            - Call `toString()` on objects that have a cyclic reference to a ancestor object.
            - Call `toString()` where object graphs exceed max depth.
    - Remove `gson` dependency.
    - Remove `IUnknownPropertiesConsumer`
- Pass MDC tags as Sentry tags ([#1954](https://github.com/getsentry/sentry-java/pull/1954))

### Fixes

- Calling Sentry.init and specifying contextTags now has an effect on the Logback SentryAppender ([#2052](https://github.com/getsentry/sentry-java/pull/2052))
- Calling Sentry.init and specifying contextTags now has an effect on the Log4j SentryAppender ([#2054](https://github.com/getsentry/sentry-java/pull/2054))
- Calling Sentry.init and specifying contextTags now has an effect on the jul SentryAppender ([#2057](https://github.com/getsentry/sentry-java/pull/2057))
- Update Spring Boot dependency to 2.6.8 and fix the CVE-2022-22970 ([#2068](https://github.com/getsentry/sentry-java/pull/2068))
- Sentry can now self heal after a Thread had its currentHub set to a NoOpHub ([#2076](https://github.com/getsentry/sentry-java/pull/2076))
- No longer close OutputStream that is passed into JsonSerializer ([#2029](https://github.com/getsentry/sentry-java/pull/2029))
- Fix setting context tags on events captured by Spring ([#2060](https://github.com/getsentry/sentry-java/pull/2060))
- Isolate cached events with hashed DSN subfolder ([#2038](https://github.com/getsentry/sentry-java/pull/2038))
- SentryThread.current flag will not be overridden by DefaultAndroidEventProcessor if already set ([#2050](https://github.com/getsentry/sentry-java/pull/2050))
- Fix serialization of Long inside of Request.data ([#2051](https://github.com/getsentry/sentry-java/pull/2051))
- Update sentry-native to 0.4.17 ([#2033](https://github.com/getsentry/sentry-java/pull/2033))
- Update Gradle to 7.4.2 and AGP to 7.2 ([#2042](https://github.com/getsentry/sentry-java/pull/2042))
- Change order of event filtering mechanisms ([#2001](https://github.com/getsentry/sentry-java/pull/2001))
- Only send session update for dropped events if state changed ([#2002](https://github.com/getsentry/sentry-java/pull/2002))
- Android profiling initializes on first profile start ([#2009](https://github.com/getsentry/sentry-java/pull/2009))
- Profiling rate decreased from 300hz to 100hz ([#1997](https://github.com/getsentry/sentry-java/pull/1997))
- Allow disabling sending of client reports via Android Manifest and external options ([#2007](https://github.com/getsentry/sentry-java/pull/2007))
- Ref: Upgrade Spring Boot dependency to 2.5.13 ([#2011](https://github.com/getsentry/sentry-java/pull/2011))
- Ref: Make options.printUncaughtStackTrace primitive type ([#1995](https://github.com/getsentry/sentry-java/pull/1995))
- Ref: Remove not needed interface abstractions on Android ([#1953](https://github.com/getsentry/sentry-java/pull/1953))
- Ref: Make hints Map<String, Object> instead of only Object ([#1929](https://github.com/getsentry/sentry-java/pull/1929))
- Ref: Simplify DateUtils with ISO8601Utils ([#1837](https://github.com/getsentry/sentry-java/pull/1837))
- Ref: Remove deprecated and scheduled fields ([#1875](https://github.com/getsentry/sentry-java/pull/1875))
- Ref: Add shutdownTimeoutMillis in favor of shutdownTimeout ([#1873](https://github.com/getsentry/sentry-java/pull/1873))
- Ref: Remove Attachment ContentType since the Server infers it ([#1874](https://github.com/getsentry/sentry-java/pull/1874))
- Ref: Bind external properties to a dedicated class. ([#1750](https://github.com/getsentry/sentry-java/pull/1750))
- Ref: Debug log serializable objects ([#1795](https://github.com/getsentry/sentry-java/pull/1795))
- Ref: catch Throwable instead of Exception to suppress internal SDK errors ([#1812](https://github.com/getsentry/sentry-java/pull/1812))
- `SentryOptions` can merge properties from `ExternalOptions` instead of another instance of `SentryOptions`
- Following boolean properties from `SentryOptions` that allowed `null` values are now not nullable - `debug`, `enableUncaughtExceptionHandler`, `enableDeduplication`
- `SentryOptions` cannot be created anymore using `PropertiesProvider` with `SentryOptions#from` method. Use `ExternalOptions#from` instead and merge created object with `SentryOptions#merge`
- Bump: Kotlin to 1.5 and compatibility to 1.4 for sentry-android-timber ([#1815](https://github.com/getsentry/sentry-java/pull/1815))

## 5.7.4

### Fixes

* Change order of event filtering mechanisms and only send session update for dropped events if session state changed (#2028)

## 5.7.3

### Fixes

- Sentry Timber integration throws an exception when using args ([#1986](https://github.com/getsentry/sentry-java/pull/1986))

## 5.7.2

### Fixes

- Bring back support for `Timber.tag` ([#1974](https://github.com/getsentry/sentry-java/pull/1974))

## 5.7.1

### Fixes

- Sentry Timber integration does not submit msg.formatted breadcrumbs ([#1957](https://github.com/getsentry/sentry-java/pull/1957))
- ANR WatchDog won't crash on SecurityException ([#1962](https://github.com/getsentry/sentry-java/pull/1962))

## 5.7.0

### Features

- Automatically enable `Timber` and `Fragment` integrations if they are present on the classpath ([#1936](https://github.com/getsentry/sentry-java/pull/1936))

## 5.6.3

### Fixes

- If transaction or span is finished, do not allow to mutate ([#1940](https://github.com/getsentry/sentry-java/pull/1940))
- Keep used AndroidX classes from obfuscation (Fixes UI breadcrumbs and Slow/Frozen frames) ([#1942](https://github.com/getsentry/sentry-java/pull/1942))

## 5.6.2

### Fixes

- Ref: Make ActivityFramesTracker public to be used by Hybrid SDKs ([#1931](https://github.com/getsentry/sentry-java/pull/1931))
- Bump: AGP to 7.1.2 ([#1930](https://github.com/getsentry/sentry-java/pull/1930))
- NPE while adding "response_body_size" breadcrumb, when response body length is unknown ([#1908](https://github.com/getsentry/sentry-java/pull/1908))
- Do not include stacktrace frames into Timber message ([#1898](https://github.com/getsentry/sentry-java/pull/1898))
- Potential memory leaks ([#1909](https://github.com/getsentry/sentry-java/pull/1909))

Breaking changes:
`Timber.tag` is no longer supported by our [Timber integration](https://docs.sentry.io/platforms/android/configuration/integrations/timber/) and will not appear on Sentry for error events.
Please vote on this [issue](https://github.com/getsentry/sentry-java/issues/1900), if you'd like us to provide support for that.

## 5.6.2-beta.3

### Fixes

- Ref: Make ActivityFramesTracker public to be used by Hybrid SDKs ([#1931](https://github.com/getsentry/sentry-java/pull/1931))
- Bump: AGP to 7.1.2 ([#1930](https://github.com/getsentry/sentry-java/pull/1930))

## 5.6.2-beta.2

### Fixes

- NPE while adding "response_body_size" breadcrumb, when response body length is unknown ([#1908](https://github.com/getsentry/sentry-java/pull/1908))

## 5.6.2-beta.1

### Fixes

- Do not include stacktrace frames into Timber message ([#1898](https://github.com/getsentry/sentry-java/pull/1898))
- Potential memory leaks ([#1909](https://github.com/getsentry/sentry-java/pull/1909))

Breaking changes:
`Timber.tag` is no longer supported by our [Timber integration](https://docs.sentry.io/platforms/android/configuration/integrations/timber/) and will not appear on Sentry for error events.
Please vote on this [issue](https://github.com/getsentry/sentry-java/issues/1900), if you'd like us to provide support for that.

## 5.6.1

### Features

- Add options.printUncaughtStackTrace to print uncaught exceptions ([#1890](https://github.com/getsentry/sentry-java/pull/1890))

### Fixes

- NPE while adding "response_body_size" breadcrumb, when response body is null ([#1884](https://github.com/getsentry/sentry-java/pull/1884))
- Bump: AGP to 7.1.0 ([#1892](https://github.com/getsentry/sentry-java/pull/1892))

## 5.6.0

### Features

- Add breadcrumbs support for UI events (automatically captured) ([#1876](https://github.com/getsentry/sentry-java/pull/1876))

### Fixes

- Change scope of servlet-api to compileOnly ([#1880](https://github.com/getsentry/sentry-java/pull/1880))

## 5.5.3

### Fixes

- Do not create SentryExceptionResolver bean when Spring MVC is not on the classpath ([#1865](https://github.com/getsentry/sentry-java/pull/1865))

## 5.5.2

### Fixes

- Detect App Cold start correctly for Hybrid SDKs ([#1855](https://github.com/getsentry/sentry-java/pull/1855))
- Bump: log4j to 2.17.0 ([#1852](https://github.com/getsentry/sentry-java/pull/1852))
- Bump: logback to 1.2.9 ([#1853](https://github.com/getsentry/sentry-java/pull/1853))

## 5.5.1

### Fixes

- Bump: log4j to 2.16.0 ([#1845](https://github.com/getsentry/sentry-java/pull/1845))
- Make App start cold/warm visible to Hybrid SDKs ([#1848](https://github.com/getsentry/sentry-java/pull/1848))

## 5.5.0

### Features

- Add locale to device context and deprecate language ([#1832](https://github.com/getsentry/sentry-java/pull/1832))
- Add `SentryFileInputStream` and `SentryFileOutputStream` for File I/O performance instrumentation ([#1826](https://github.com/getsentry/sentry-java/pull/1826))
- Add `SentryFileReader` and `SentryFileWriter` for File I/O instrumentation ([#1843](https://github.com/getsentry/sentry-java/pull/1843))

### Fixes

- Bump: log4j to 2.15.0 ([#1839](https://github.com/getsentry/sentry-java/pull/1839))
- Ref: Rename Fragment span operation from `ui.fragment.load` to `ui.load` ([#1824](https://github.com/getsentry/sentry-java/pull/1824))
- Ref: change `java.util.Random` to `java.security.SecureRandom` for possible security reasons ([#1831](https://github.com/getsentry/sentry-java/pull/1831))

## 5.4.3

### Fixes

- Only report App start measurement for full launch on Android ([#1821](https://github.com/getsentry/sentry-java/pull/1821))

## 5.4.2

### Fixes

- Ref: catch Throwable instead of Exception to suppress internal SDK errors ([#1812](https://github.com/getsentry/sentry-java/pull/1812))

## 5.4.1

### Features

- Refactor OkHttp and Apollo to Kotlin functional interfaces ([#1797](https://github.com/getsentry/sentry-java/pull/1797))
- Add secondary constructor to SentryInstrumentation ([#1804](https://github.com/getsentry/sentry-java/pull/1804))

### Fixes

- Do not start fragment span if not added to the Activity ([#1813](https://github.com/getsentry/sentry-java/pull/1813))

## 5.4.0

### Features

- Add `graphql-java` instrumentation ([#1777](https://github.com/getsentry/sentry-java/pull/1777))

### Fixes

- Do not crash when event processors throw a lower level Throwable class ([#1800](https://github.com/getsentry/sentry-java/pull/1800))
- ActivityFramesTracker does not throw if Activity has no observers ([#1799](https://github.com/getsentry/sentry-java/pull/1799))

## 5.3.0

### Features

- Add datasource tracing with P6Spy ([#1784](https://github.com/getsentry/sentry-java/pull/1784))

### Fixes

- ActivityFramesTracker does not throw if Activity has not been added ([#1782](https://github.com/getsentry/sentry-java/pull/1782))
- PerformanceAndroidEventProcessor uses up to date isTracingEnabled set on Configuration callback ([#1786](https://github.com/getsentry/sentry-java/pull/1786))

## 5.2.4

### Fixes

- Window.FEATURE_NO_TITLE does not work when using activity traces ([#1769](https://github.com/getsentry/sentry-java/pull/1769))
- unregister UncaughtExceptionHandler on close ([#1770](https://github.com/getsentry/sentry-java/pull/1770))

## 5.2.3

### Fixes

- Make ActivityFramesTracker operations thread-safe ([#1762](https://github.com/getsentry/sentry-java/pull/1762))
- Clone Scope Contexts ([#1763](https://github.com/getsentry/sentry-java/pull/1763))
- Bump: AGP to 7.0.3 ([#1765](https://github.com/getsentry/sentry-java/pull/1765))

## 5.2.2

### Fixes

- Close HostnameCache#executorService on SentryClient#close ([#1757](https://github.com/getsentry/sentry-java/pull/1757))

## 5.2.1

### Features

- Add isCrashedLastRun support ([#1739](https://github.com/getsentry/sentry-java/pull/1739))
- Attach Java vendor and version to events and transactions ([#1703](https://github.com/getsentry/sentry-java/pull/1703))

### Fixes

- Handle exception if Context.registerReceiver throws ([#1747](https://github.com/getsentry/sentry-java/pull/1747))

## 5.2.0

### Features

- Allow setting proguard via Options and/or external resources ([#1728](https://github.com/getsentry/sentry-java/pull/1728))
- Add breadcrumbs for the Apollo integration ([#1726](https://github.com/getsentry/sentry-java/pull/1726))

### Fixes

- Don't set lastEventId for transactions ([#1727](https://github.com/getsentry/sentry-java/pull/1727))
- ActivityLifecycleIntegration#appStartSpan memory leak ([#1732](https://github.com/getsentry/sentry-java/pull/1732))

## 5.2.0-beta.3

### Features

- Add "data" to spans ([#1717](https://github.com/getsentry/sentry-java/pull/1717))

### Fixes

- Check at runtime if AndroidX.Core is available ([#1718](https://github.com/getsentry/sentry-java/pull/1718))
- Should not capture unfinished transaction ([#1719](https://github.com/getsentry/sentry-java/pull/1719))

## 5.2.0-beta.2

### Fixes

- Bump AGP to 7.0.2 ([#1650](https://github.com/getsentry/sentry-java/pull/1650))
- Drop spans in BeforeSpanCallback. ([#1713](https://github.com/getsentry/sentry-java/pull/1713))

## 5.2.0-beta.1

### Features

- Add tracestate HTTP header support ([#1683](https://github.com/getsentry/sentry-java/pull/1683))
- Add option to filter which origins receive tracing headers ([#1698](https://github.com/getsentry/sentry-java/pull/1698))
- Include unfinished spans in transaction ([#1699](https://github.com/getsentry/sentry-java/pull/1699))
- Add static helpers for creating breadcrumbs ([#1702](https://github.com/getsentry/sentry-java/pull/1702))
- Performance support for Android Apollo ([#1705](https://github.com/getsentry/sentry-java/pull/1705))

### Fixes

- Move tags from transaction.contexts.trace.tags to transaction.tags ([#1700](https://github.com/getsentry/sentry-java/pull/1700))

Breaking changes:

- Updated proguard keep rule for enums, which affects consumer application code ([#1694](https://github.com/getsentry/sentry-java/pull/1694))

## 5.1.2

### Fixes

- Servlet 3.1 compatibility issue ([#1681](https://github.com/getsentry/sentry-java/pull/1681))
- Do not drop Contexts key if Collection, Array or Char ([#1680](https://github.com/getsentry/sentry-java/pull/1680))

## 5.1.1

### Features

- Add support for async methods in Spring MVC ([#1652](https://github.com/getsentry/sentry-java/pull/1652))
- Add secondary constructor taking IHub to SentryOkHttpInterceptor ([#1657](https://github.com/getsentry/sentry-java/pull/1657))
- Merge external map properties ([#1656](https://github.com/getsentry/sentry-java/pull/1656))

### Fixes

- Remove onActivityPreCreated call in favor of onActivityCreated ([#1661](https://github.com/getsentry/sentry-java/pull/1661))
- Do not crash if SENSOR_SERVICE throws ([#1655](https://github.com/getsentry/sentry-java/pull/1655))
- Make sure scope is popped when processing request results in exception ([#1665](https://github.com/getsentry/sentry-java/pull/1665))

## 5.1.0

### Features

- Spring WebClient integration ([#1621](https://github.com/getsentry/sentry-java/pull/1621))
- OpenFeign integration ([#1632](https://github.com/getsentry/sentry-java/pull/1632))
- Add more convenient way to pass BeforeSpanCallback in OpenFeign integration ([#1637](https://github.com/getsentry/sentry-java/pull/1637))

### Fixes

- Bump: sentry-native to 0.4.12 ([#1651](https://github.com/getsentry/sentry-java/pull/1651))

## 5.1.0-beta.9

- No documented changes.

## 5.1.0-beta.8

### Features

- Generate Sentry BOM ([#1486](https://github.com/getsentry/sentry-java/pull/1486))

## 5.1.0-beta.7

### Features

- Slow/Frozen frames metrics ([#1609](https://github.com/getsentry/sentry-java/pull/1609))

## 5.1.0-beta.6

### Features

- Add request body extraction for Spring MVC integration ([#1595](https://github.com/getsentry/sentry-java/pull/1595))

### Fixes

- set min sdk version of sentry-android-fragment to API 14 ([#1608](https://github.com/getsentry/sentry-java/pull/1608))
- Ser/Deser of the UserFeedback from cached envelope ([#1611](https://github.com/getsentry/sentry-java/pull/1611))

## 5.1.0-beta.5

### Fixes

- Make SentryAppender non-final for Log4j2 and Logback ([#1603](https://github.com/getsentry/sentry-java/pull/1603))
- Do not throw IAE when tracing header contain invalid trace id ([#1605](https://github.com/getsentry/sentry-java/pull/1605))

## 5.1.0-beta.4

### Fixes

- Update sentry-native to 0.4.11 ([#1591](https://github.com/getsentry/sentry-java/pull/1591))

## 5.1.0-beta.3

### Features

- Spring Webflux integration ([#1529](https://github.com/getsentry/sentry-java/pull/1529))

## 5.1.0-beta.2

### Features

- Support transaction waiting for children to finish. ([#1535](https://github.com/getsentry/sentry-java/pull/1535))
- Capture logged marker in log4j2 and logback appenders ([#1551](https://github.com/getsentry/sentry-java/pull/1551))
- Allow clearing of attachments in the scope ([#1562](https://github.com/getsentry/sentry-java/pull/1562))
- Set mechanism type in SentryExceptionResolver ([#1556](https://github.com/getsentry/sentry-java/pull/1556))
- Perf. for fragments ([#1528](https://github.com/getsentry/sentry-java/pull/1528))

### Fixes

- Handling missing Spring Security on classpath on Java 8 ([#1552](https://github.com/getsentry/sentry-java/pull/1552))
- Use a different method to get strings from JNI, and avoid excessive Stack Space usage. ([#1214](https://github.com/getsentry/sentry-java/pull/1214))
- Add data field to SentrySpan ([#1555](https://github.com/getsentry/sentry-java/pull/1555))
- Clock drift issue when calling DateUtils#getDateTimeWithMillisPrecision ([#1557](https://github.com/getsentry/sentry-java/pull/1557))
- Prefer snake case for HTTP integration data keys ([#1559](https://github.com/getsentry/sentry-java/pull/1559))
- Assign lastEventId only if event was queued for submission ([#1565](https://github.com/getsentry/sentry-java/pull/1565))

## 5.1.0-beta.1

### Features

- Measure app start time ([#1487](https://github.com/getsentry/sentry-java/pull/1487))
- Automatic breadcrumbs logging for fragment lifecycle ([#1522](https://github.com/getsentry/sentry-java/pull/1522))

## 5.0.1

### Fixes

- Sources and Javadoc artifacts were mixed up ([#1515](https://github.com/getsentry/sentry-java/pull/1515))

## 5.0.0

This release brings many improvements but also new features:

- OkHttp Interceptor for Android ([#1330](https://github.com/getsentry/sentry-java/pull/1330))
- GraalVM Native Image Compatibility ([#1329](https://github.com/getsentry/sentry-java/pull/1329))
- Add option to ignore exceptions by type ([#1352](https://github.com/getsentry/sentry-java/pull/1352))
- Enrich transactions with device contexts ([#1430](https://github.com/getsentry/sentry-java/pull/1430)) ([#1469](https://github.com/getsentry/sentry-java/pull/1469))
- Better interoperability with Kotlin null-safety ([#1439](https://github.com/getsentry/sentry-java/pull/1439)) and ([#1462](https://github.com/getsentry/sentry-java/pull/1462))
- Add coroutines support ([#1479](https://github.com/getsentry/sentry-java/pull/1479))
- OkHttp callback for Customising the Span ([#1478](https://github.com/getsentry/sentry-java/pull/1478))
- Add breadcrumb in Spring RestTemplate integration ([#1481](https://github.com/getsentry/sentry-java/pull/1481))

Breaking changes:

- Migration Guide for [Java](https://docs.sentry.io/platforms/java/migration/)
- Migration Guide for [Android](https://docs.sentry.io/platforms/android/migration/)

Other fixes:

- Fix: Add attachmentType to envelope ser/deser. ([#1504](https://github.com/getsentry/sentry-java/pull/1504))

Thank you:

- @maciejwalkowiak for coding most of it.

## 5.0.0-beta.7

### Fixes


- Ref: Deprecate SentryBaseEvent#getOriginThrowable and add SentryBaseEvent#getThrowableMechanism ([#1502](https://github.com/getsentry/sentry-java/pull/1502))
- Graceful Shutdown flushes event instead of Closing SDK ([#1500](https://github.com/getsentry/sentry-java/pull/1500))
- Do not append threads that come from the EnvelopeFileObserver ([#1501](https://github.com/getsentry/sentry-java/pull/1501))
- Ref: Deprecate cacheDirSize and add maxCacheItems ([#1499](https://github.com/getsentry/sentry-java/pull/1499))
- Append all threads if Hint is Cached but attachThreads is enabled ([#1503](https://github.com/getsentry/sentry-java/pull/1503))

## 5.0.0-beta.6

### Features

- Add secondary constructor to SentryOkHttpInterceptor ([#1491](https://github.com/getsentry/sentry-java/pull/1491))
- Add option to enable debug mode in Log4j2 integration ([#1492](https://github.com/getsentry/sentry-java/pull/1492))

### Fixes

- Ref: Replace clone() with copy constructor ([#1496](https://github.com/getsentry/sentry-java/pull/1496))

## 5.0.0-beta.5

### Features

- OkHttp callback for Customising the Span ([#1478](https://github.com/getsentry/sentry-java/pull/1478))
- Add breadcrumb in Spring RestTemplate integration ([#1481](https://github.com/getsentry/sentry-java/pull/1481))
- Add coroutines support ([#1479](https://github.com/getsentry/sentry-java/pull/1479))

### Fixes

- Cloning Stack ([#1483](https://github.com/getsentry/sentry-java/pull/1483))

## 5.0.0-beta.4

### Fixes

- Enrich Transactions with Context Data ([#1469](https://github.com/getsentry/sentry-java/pull/1469))
- Bump: Apache HttpClient to 5.0.4 ([#1476](https://github.com/getsentry/sentry-java/pull/1476))

## 5.0.0-beta.3

### Fixes

- Handling immutable collections on SentryEvent and protocol objects ([#1468](https://github.com/getsentry/sentry-java/pull/1468))
- Associate event with transaction when thrown exception is not a direct cause ([#1463](https://github.com/getsentry/sentry-java/pull/1463))
- Ref: nullability annotations to Sentry module ([#1439](https://github.com/getsentry/sentry-java/pull/1439)) and ([#1462](https://github.com/getsentry/sentry-java/pull/1462))
- NPE when adding Context Data with null values for log4j2 ([#1465](https://github.com/getsentry/sentry-java/pull/1465))

## 5.0.0-beta.2

### Fixes

- sentry-android-timber package sets sentry.java.android.timber as SDK name ([#1456](https://github.com/getsentry/sentry-java/pull/1456))
- When AppLifecycleIntegration is closed, it should remove observer using UI thread ([#1459](https://github.com/getsentry/sentry-java/pull/1459))
- Bump: AGP to 4.2.0 ([#1460](https://github.com/getsentry/sentry-java/pull/1460))

Breaking Changes:

- Remove: Settings.Secure.ANDROID_ID in favor of generated installationId ([#1455](https://github.com/getsentry/sentry-java/pull/1455))
- Rename: enableSessionTracking to enableAutoSessionTracking ([#1457](https://github.com/getsentry/sentry-java/pull/1457))

## 5.0.0-beta.1

### Fixes

- Ref: Refactor converting HttpServletRequest to Sentry Request in Spring integration ([#1387](https://github.com/getsentry/sentry-java/pull/1387))
- Bump: sentry-native to 0.4.9 ([#1431](https://github.com/getsentry/sentry-java/pull/1431))
- Activity tracing auto instrumentation for Android API < 29 ([#1402](https://github.com/getsentry/sentry-java/pull/1402))
- use connection and read timeouts in ApacheHttpClient based transport ([#1397](https://github.com/getsentry/sentry-java/pull/1397))
- set correct transaction status for unhandled exceptions in SentryTracingFilter ([#1406](https://github.com/getsentry/sentry-java/pull/1406))
- handle network errors in SentrySpanClientHttpRequestInterceptor ([#1407](https://github.com/getsentry/sentry-java/pull/1407))
- set scope on transaction ([#1409](https://github.com/getsentry/sentry-java/pull/1409))
- set status and associate events with transactions ([#1426](https://github.com/getsentry/sentry-java/pull/1426))
- Do not set free memory and is low memory fields when it's a NDK hard crash ([#1399](https://github.com/getsentry/sentry-java/pull/1399))
- Apply user from the scope to transaction ([#1424](https://github.com/getsentry/sentry-java/pull/1424))
- Pass maxBreadcrumbs config. to sentry-native ([#1425](https://github.com/getsentry/sentry-java/pull/1425))
- Run event processors and enrich transactions with contexts ([#1430](https://github.com/getsentry/sentry-java/pull/1430))
- Set Span status for OkHttp integration ([#1447](https://github.com/getsentry/sentry-java/pull/1447))
- Set user on transaction in Spring & Spring Boot integrations ([#1443](https://github.com/getsentry/sentry-java/pull/1443))

## 4.4.0-alpha.2

### Features

- Add option to ignore exceptions by type ([#1352](https://github.com/getsentry/sentry-java/pull/1352))
- Sentry closes Android NDK and ShutdownHook integrations ([#1358](https://github.com/getsentry/sentry-java/pull/1358))
- Allow inheritance of SentryHandler class in sentry-jul package([#1367](https://github.com/getsentry/sentry-java/pull/1367))
- Make NoOpHub public ([#1379](https://github.com/getsentry/sentry-java/pull/1379))
- Configure max spans per transaction ([#1394](https://github.com/getsentry/sentry-java/pull/1394))

### Fixes

- Bump: Upgrade Apache HttpComponents Core to 5.0.3 ([#1375](https://github.com/getsentry/sentry-java/pull/1375))
- NPE when MDC contains null values (sentry-logback) ([#1364](https://github.com/getsentry/sentry-java/pull/1364))
- Avoid NPE when MDC contains null values (sentry-jul) ([#1385](https://github.com/getsentry/sentry-java/pull/1385))
- Accept only non null value maps ([#1368](https://github.com/getsentry/sentry-java/pull/1368))
- Do not bind transactions to scope by default. ([#1376](https://github.com/getsentry/sentry-java/pull/1376))
- Hub thread safety ([#1388](https://github.com/getsentry/sentry-java/pull/1388))
- SentryTransactionAdvice should operate on the new scope ([#1389](https://github.com/getsentry/sentry-java/pull/1389))

## 4.4.0-alpha.1

### Features

- Add an overload for `startTransaction` that sets the created transaction to the Scope ([#1313](https://github.com/getsentry/sentry-java/pull/1313))
- Set SDK version on Transactions ([#1307](https://github.com/getsentry/sentry-java/pull/1307))
- GraalVM Native Image Compatibility ([#1329](https://github.com/getsentry/sentry-java/pull/1329))
- Add OkHttp client application interceptor ([#1330](https://github.com/getsentry/sentry-java/pull/1330))

### Fixes

- Bump: sentry-native to 0.4.8
- Ref: Separate user facing and protocol classes in the Performance feature ([#1304](https://github.com/getsentry/sentry-java/pull/1304))
- Use logger set on SentryOptions in GsonSerializer ([#1308](https://github.com/getsentry/sentry-java/pull/1308))
- Use the bindToScope correctly
- Allow 0.0 to be set on tracesSampleRate ([#1328](https://github.com/getsentry/sentry-java/pull/1328))
- set "java" platform to transactions ([#1332](https://github.com/getsentry/sentry-java/pull/1332))
- Allow disabling tracing through SentryOptions ([#1337](https://github.com/getsentry/sentry-java/pull/1337))

## 4.3.0

### Features

- Activity tracing auto instrumentation

### Fixes

- Aetting in-app-includes from external properties ([#1291](https://github.com/getsentry/sentry-java/pull/1291))
- Initialize Sentry in Logback appender when DSN is not set in XML config ([#1296](https://github.com/getsentry/sentry-java/pull/1296))
- JUL integration SDK name ([#1293](https://github.com/getsentry/sentry-java/pull/1293))

## 4.2.0

### Features

- Improve EventProcessor nullability annotations ([#1229](https://github.com/getsentry/sentry-java/pull/1229)).
- Add ability to flush events synchronously.
- Support @SentrySpan and @SentryTransaction on classes and interfaces. ([#1243](https://github.com/getsentry/sentry-java/pull/1243))
- Do not serialize empty collections and maps ([#1245](https://github.com/getsentry/sentry-java/pull/1245))
- Integration interface better compatibility with Kotlin null-safety
- Simplify Sentry configuration in Spring integration ([#1259](https://github.com/getsentry/sentry-java/pull/1259))
- Simplify configuring Logback integration when environment variable with the DSN is not set ([#1271](https://github.com/getsentry/sentry-java/pull/1271))
- Add Request to the Scope. [#1270](https://github.com/getsentry/sentry-java/pull/1270))
- Optimize SentryTracingFilter when hub is disabled.

### Fixes

- Bump: sentry-native to 0.4.7
- Optimize DuplicateEventDetectionEventProcessor performance ([#1247](https://github.com/getsentry/sentry-java/pull/1247)).
- Prefix sdk.package names with io.sentry ([#1249](https://github.com/getsentry/sentry-java/pull/1249))
- Remove experimental annotation for Attachment ([#1257](https://github.com/getsentry/sentry-java/pull/1257))
- Mark stacktrace as snapshot if captured at arbitrary moment ([#1231](https://github.com/getsentry/sentry-java/pull/1231))
- Disable Gson HTML escaping
- Make the ANR Atomic flags immutable
- Prevent NoOpHub from creating heavy SentryOptions objects ([#1272](https://github.com/getsentry/sentry-java/pull/1272))
- SentryTransaction#getStatus NPE ([#1273](https://github.com/getsentry/sentry-java/pull/1273))
- Discard unfinished Spans before sending them over to Sentry ([#1279](https://github.com/getsentry/sentry-java/pull/1279))
- Interrupt the thread in QueuedThreadPoolExecutor ([#1276](https://github.com/getsentry/sentry-java/pull/1276))
- SentryTransaction#finish should not clear another transaction from the scope ([#1278](https://github.com/getsentry/sentry-java/pull/1278))

Breaking Changes:
- Enchancement: SentryExceptionResolver should not send handled errors by default ([#1248](https://github.com/getsentry/sentry-java/pull/1248)).
- Ref: Simplify RestTemplate instrumentation ([#1246](https://github.com/getsentry/sentry-java/pull/1246))
- Enchancement: Add overloads for startTransaction taking op and description ([#1244](https://github.com/getsentry/sentry-java/pull/1244))

## 4.1.0

### Features

- Improve Kotlin compatibility for SdkVersion ([#1213](https://github.com/getsentry/sentry-java/pull/1213))
- Support logging via JUL ([#1211](https://github.com/getsentry/sentry-java/pull/1211))

### Fixes

- Returning Sentry trace header from Span ([#1217](https://github.com/getsentry/sentry-java/pull/1217))
- Remove misleading error logs ([#1222](https://github.com/getsentry/sentry-java/pull/1222))

## 4.0.0

This release brings the Sentry Performance feature to Java SDK, Spring, Spring Boot, and Android integrations. Read more in the reference documentation:

- [Performance for Java](https://docs.sentry.io/platforms/java/performance/)
- [Performance for Spring](https://docs.sentry.io/platforms/java/guides/spring/)
- [Performance for Spring Boot](https://docs.sentry.io/platforms/java/guides/spring-boot/)
- [Performance for Android](https://docs.sentry.io/platforms/android/performance/)

### Other improvements:

#### Core:

- Improved loading external configuration:
  - Load `sentry.properties` from the application's current working directory ([#1046](https://github.com/getsentry/sentry-java/pull/1046))
  - Resolve `in-app-includes`, `in-app-excludes`, `tags`, `debug`, `uncaught.handler.enabled` parameters from the external configuration
- Set global tags on SentryOptions and load them from external configuration ([#1066](https://github.com/getsentry/sentry-java/pull/1066))
- Add support for attachments ([#1082](https://github.com/getsentry/sentry-java/pull/1082))
- Resolve `servername` from the localhost address
- Simplified transport configuration through setting `TransportFactory` instead of `ITransport` on SentryOptions ([#1124](https://github.com/getsentry/sentry-java/pull/1124))

#### Spring Boot:

- Add the ability to register multiple `OptionsConfiguration` beans ([#1093](https://github.com/getsentry/sentry-java/pull/1093))
- Initialize Logback after context refreshes ([#1129](https://github.com/getsentry/sentry-java/pull/1129))

#### Android:

- Add `isSideLoaded` and `installerStore` tags automatically (Where your App. was installed from eg Google Play, Amazon Store, downloaded APK, etc...)
- Bump: sentry-native to 0.4.6
- Bump: Gradle to 6.8.1 and AGP to 4.1.2

## 4.0.0-beta.1

### Features

- Add addToTransactions to Attachment ([#1191](https://github.com/getsentry/sentry-java/pull/1191))
- Support SENTRY_TRACES_SAMPLE_RATE conf. via env variables ([#1171](https://github.com/getsentry/sentry-java/pull/1171))
- Pass request to CustomSamplingContext in Spring integration ([#1172](https://github.com/getsentry/sentry-java/pull/1172))
- Move `SentrySpanClientHttpRequestInterceptor` to Spring module ([#1181](https://github.com/getsentry/sentry-java/pull/1181))
- Add overload for `transaction/span.finish(SpanStatus)` ([#1182](https://github.com/getsentry/sentry-java/pull/1182))
- Simplify registering traces sample callback in Spring integration ([#1184](https://github.com/getsentry/sentry-java/pull/1184))
- Polish Performance API ([#1165](https://github.com/getsentry/sentry-java/pull/1165))
- Set "debug" through external properties ([#1186](https://github.com/getsentry/sentry-java/pull/1186))
- Simplify Spring integration ([#1188](https://github.com/getsentry/sentry-java/pull/1188))
- Init overload with dsn ([#1195](https://github.com/getsentry/sentry-java/pull/1195))
- Enable Kotlin map-like access on CustomSamplingContext ([#1192](https://github.com/getsentry/sentry-java/pull/1192))
- Auto register custom ITransportFactory in Spring integration ([#1194](https://github.com/getsentry/sentry-java/pull/1194))
- Improve Kotlin property access in Performance API ([#1193](https://github.com/getsentry/sentry-java/pull/1193))
- Copy options tags to transactions ([#1198](https://github.com/getsentry/sentry-java/pull/1198))
- Add convenient method for accessing event's throwable ([#1202](https://github.com/getsentry/sentry-java/pull/1202))

### Fixes

- Ref: Set SpanContext on SentryTransaction to avoid potential NPE ([#1173](https://github.com/getsentry/sentry-java/pull/1173))
- Free Local Refs manually due to Android local ref. count limits
- Bring back support for setting transaction name without ongoing transaction ([#1183](https://github.com/getsentry/sentry-java/pull/1183))

## 4.0.0-alpha.3

### Features

- Improve ITransaction and ISpan null-safety compatibility ([#1161](https://github.com/getsentry/sentry-java/pull/1161))
- Automatically assign span context to captured events ([#1156](https://github.com/getsentry/sentry-java/pull/1156))
- Autoconfigure Apache HttpClient 5 based Transport in Spring Boot integration ([#1143](https://github.com/getsentry/sentry-java/pull/1143))
- Send user.ip_address = {{auto}} when sendDefaultPii is true ([#1015](https://github.com/getsentry/sentry-java/pull/1015))
- Read tracesSampleRate from AndroidManifest
- OutboxSender supports all envelope item types ([#1158](https://github.com/getsentry/sentry-java/pull/1158))
- Read `uncaught.handler.enabled` property from the external configuration
- Resolve servername from the localhost address
- Add maxAttachmentSize to SentryOptions ([#1138](https://github.com/getsentry/sentry-java/pull/1138))
- Drop invalid attachments ([#1134](https://github.com/getsentry/sentry-java/pull/1134))
- Set isSideLoaded info tags
- Add non blocking Apache HttpClient 5 based Transport ([#1136](https://github.com/getsentry/sentry-java/pull/1136))

### Fixes

- Ref: Make Attachment immutable ([#1120](https://github.com/getsentry/sentry-java/pull/1120))
- Ref: using Calendar to generate Dates
- Ref: Return NoOpTransaction instead of null ([#1126](https://github.com/getsentry/sentry-java/pull/1126))
- Ref: `ITransport` implementations are now responsible for executing request in asynchronous or synchronous way ([#1118](https://github.com/getsentry/sentry-java/pull/1118))
- Ref: Add option to set `TransportFactory` instead of `ITransport` on `SentryOptions` ([#1124](https://github.com/getsentry/sentry-java/pull/1124))
- Ref: Simplify ITransport creation in ITransportFactory ([#1135](https://github.com/getsentry/sentry-java/pull/1135))
- Fixes and Tests: Session serialization and deserialization
- Inheriting sampling decision from parent ([#1100](https://github.com/getsentry/sentry-java/pull/1100))
- Exception only sets a stack trace if there are frames
- Initialize Logback after context refreshes ([#1129](https://github.com/getsentry/sentry-java/pull/1129))
- Do not crash when passing null values to @Nullable methods, eg User and Scope
- Resolving dashed properties from external configuration
- Consider {{ auto }} as a default ip address ([#1015](https://github.com/getsentry/sentry-java/pull/1015))
- Set release and environment on Transactions ([#1152](https://github.com/getsentry/sentry-java/pull/1152))
- Do not set transaction on the scope automatically

## 4.0.0-alpha.2

### Features

- Add basic support for attachments ([#1082](https://github.com/getsentry/sentry-java/pull/1082))
- Set transaction name on events and transactions sent using Spring integration ([#1067](https://github.com/getsentry/sentry-java/pull/1067))
- Set global tags on SentryOptions and load them from external configuration ([#1066](https://github.com/getsentry/sentry-java/pull/1066))
- Add API validator and remove deprecated methods
- Add more convenient method to start a child span ([#1073](https://github.com/getsentry/sentry-java/pull/1073))
- Autoconfigure traces callback in Spring Boot integration ([#1074](https://github.com/getsentry/sentry-java/pull/1074))
- Resolve in-app-includes and in-app-excludes parameters from the external configuration
- Make InAppIncludesResolver public ([#1084](https://github.com/getsentry/sentry-java/pull/1084))
- Add the ability to register multiple OptionsConfiguration beans ([#1093](https://github.com/getsentry/sentry-java/pull/1093))
- Database query tracing with datasource-proxy ([#1095](https://github.com/getsentry/sentry-java/pull/1095))

### Fixes

- Ref: Refactor resolving SpanContext for Throwable ([#1068](https://github.com/getsentry/sentry-java/pull/1068))
- Ref: Change "op" to "operation" in @SentrySpan and @SentryTransaction
- Remove method reference in SentryEnvelopeItem ([#1091](https://github.com/getsentry/sentry-java/pull/1091))
- Set current thread only if there are no exceptions
- SentryOptions creates GsonSerializer by default
- Append DebugImage list if event already has it
- Sort breadcrumbs by Date if there are breadcrumbs already in the event

## 4.0.0-alpha.1

### Features

- Load `sentry.properties` from the application's current working directory ([#1046](https://github.com/getsentry/sentry-java/pull/1046))
- Performance monitoring ([#971](https://github.com/getsentry/sentry-java/pull/971))
- Performance monitoring for Spring Boot applications ([#971](https://github.com/getsentry/sentry-java/pull/971))

### Fixes

- Ref: Refactor JSON deserialization ([#1047](https://github.com/getsentry/sentry-java/pull/1047))

## 3.2.1

### Fixes

- Set current thread only if theres no exceptions ([#1064](https://github.com/getsentry/sentry-java/pull/1064))
- Append DebugImage list if event already has it ([#1092](https://github.com/getsentry/sentry-java/pull/1092))
- Sort breadcrumbs by Date if there are breadcrumbs already in the event ([#1094](https://github.com/getsentry/sentry-java/pull/1094))
- Free Local Refs manually due to Android local ref. count limits  ([#1179](https://github.com/getsentry/sentry-java/pull/1179))

## 3.2.0

### Features

- Expose a Module (Debug images) Loader for Android thru sentry-native ([#1043](https://github.com/getsentry/sentry-java/pull/1043))
- Added java doc to protocol classes based on sentry-data-schemes project ([#1045](https://github.com/getsentry/sentry-java/pull/1045))
- Make SentryExceptionResolver Order configurable to not send handled web exceptions ([#1008](https://github.com/getsentry/sentry-java/pull/1008))
- Resolve HTTP Proxy parameters from the external configuration ([#1028](https://github.com/getsentry/sentry-java/pull/1028))
- Sentry NDK integration is compiled against default NDK version based on AGP's version ([#1048](https://github.com/getsentry/sentry-java/pull/1048))

### Fixes

- Bump: AGP 4.1.1 ([#1040](https://github.com/getsentry/sentry-java/pull/1040))
- Update to sentry-native 0.4.4 and fix shared library builds ([#1039](https://github.com/getsentry/sentry-java/pull/1039))
- use neutral Locale for String operations ([#1033](https://github.com/getsentry/sentry-java/pull/1033))
- Clean up JNI code and properly free strings ([#1050](https://github.com/getsentry/sentry-java/pull/1050))
- set userId for hard-crashes if no user is set ([#1049](https://github.com/getsentry/sentry-java/pull/1049))

## 3.1.3

### Fixes

- Fix broken NDK integration on 3.1.2 (release failed on packaging a .so file)
- Increase max cached events to 30 ([#1029](https://github.com/getsentry/sentry-java/pull/1029))
- Normalize DSN URI ([#1030](https://github.com/getsentry/sentry-java/pull/1030))

## 3.1.2

### Features

- Manually capturing User Feedback
- Set environment to "production" by default.
- Make public the Breadcrumb constructor that accepts a Date ([#1012](https://github.com/getsentry/sentry-java/pull/1012))

### Fixes

- ref: Validate event id on user feedback submission

## 3.1.1

### Features

- Bind logging related SentryProperties to Slf4j Level instead of Logback to improve Log4j2 compatibility

### Fixes

- Prevent Logback and Log4j2 integrations from re-initializing Sentry when Sentry is already initialized
- Make sure HttpServletRequestSentryUserProvider runs by default before custom SentryUserProvider beans
- Fix setting up Sentry in Spring Webflux annotation by changing the scope of Spring WebMvc related dependencies

## 3.1.0

### Features

- Make getThrowable public and improve set contexts ([#967](https://github.com/getsentry/sentry-java/pull/967))
- Accepted quoted values in properties from external configuration ([#972](https://github.com/getsentry/sentry-java/pull/972))

### Fixes

- Auto-Configure `inAppIncludes` in Spring Boot integration ([#966](https://github.com/getsentry/sentry-java/pull/966))
- Bump: Android Gradle Plugin 4.0.2 ([#968](https://github.com/getsentry/sentry-java/pull/968))
- Don't require `sentry.dsn` to be set when using `io.sentry:sentry-spring-boot-starter` and `io.sentry:sentry-logback` together ([#965](https://github.com/getsentry/sentry-java/pull/965))
- Remove chunked streaming mode ([#974](https://github.com/getsentry/sentry-java/pull/974))
- Android 11 + targetSdkVersion 30 crashes Sentry on start ([#977](https://github.com/getsentry/sentry-java/pull/977))

## 3.0.0

## Java + Android

This release marks the re-unification of Java and Android SDK code bases.
It's based on the Android 2.0 SDK, which implements [Sentry's unified API](https://develop.sentry.dev/sdk/unified-api/).

Considerable changes were done, which include a lot of improvements. More are covered below, but the highlights are:

- Improved `log4j2` integration
  - Capture breadcrumbs for level INFO and higher
  - Raises event for ERROR and higher.
  - Minimum levels are configurable.
  - Optionally initializes the SDK via appender.xml
- Dropped support to `log4j`.
- Improved `logback` integration
  - Capture breadcrumbs for level INFO and higher
  - Raises event for ERROR and higher.
  - Minimum levels are configurable.
  - Optionally initializes the SDK via appender.xml
  - Configurable via Spring integration if both are enabled
- Spring
  - No more duplicate events with Spring and logback
  - Auto initalizes if DSN is available
  - Configuration options available with auto complete
- Google App Engine support dropped

## What’s Changed

- Callback to validate SSL certificate ([#944](https://github.com/getsentry/sentry-java/pull/944))
- Attach stack traces enabled by default

### Android specific

- Release health enabled by default for Android
- Sync of Scopes for Java -> Native (NDK)
- Bump Sentry-Native v0.4.2
- Android 11 Support

[Android migration docs](https://docs.sentry.io/platforms/android/migration/#migrating-from-sentry-android-2x-to-sentry-android-3x)

### Java specific

- Unified API for Java SDK and integrations (Spring, Spring boot starter, Servlet, Logback, Log4j2)

New Java [docs](https://docs.sentry.io/platforms/java/) are live and being improved.

## Acquisition

Packages were released on [`bintray sentry-java`](https://dl.bintray.com/getsentry/sentry-java/io/sentry/), [`bintray sentry-android`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/)

## Where is the Java 1.7 code base?

The previous Java releases, are all available in this repository through the tagged releases.
## 3.0.0-beta.1

## What’s Changed

- feat: ssl support ([#944](https://github.com/getsentry/sentry-java/pull/944)) @ninekaw9 @marandaneto
- feat: sync Java to C ([#937](https://github.com/getsentry/sentry-java/pull/937)) @bruno-garcia @marandaneto
- feat: Auto-configure Logback appender in Spring Boot integration. ([#938](https://github.com/getsentry/sentry-java/pull/938)) @maciejwalkowiak
- feat: Add Servlet integration. ([#935](https://github.com/getsentry/sentry-java/pull/935)) @maciejwalkowiak
- fix: Pop scope at the end of the request in Spring integration. ([#936](https://github.com/getsentry/sentry-java/pull/936)) @maciejwalkowiak
- bump: Upgrade Spring Boot to 2.3.4. ([#932](https://github.com/getsentry/sentry-java/pull/932)) @maciejwalkowiak
- fix: Do not set cookies when send pii is set to false. ([#931](https://github.com/getsentry/sentry-java/pull/931)) @maciejwalkowiak

Packages were released on [`bintray sentry-java`](https://dl.bintray.com/getsentry/sentry-java/io/sentry/), [`bintray sentry-android`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/)

We'd love to get feedback.

## 3.0.0-alpha.3

### Features

- Enable attach stack traces and disable attach threads by default ([#921](https://github.com/getsentry/sentry-java/pull/921)) @marandaneto

### Fixes

- Bump sentry-native to 0.4.2 ([#926](https://github.com/getsentry/sentry-java/pull/926)) @marandaneto
- ref: remove log level as RN do not use it anymore ([#924](https://github.com/getsentry/sentry-java/pull/924)) @marandaneto
- Read sample rate correctly from manifest meta data ([#923](https://github.com/getsentry/sentry-java/pull/923)) @marandaneto

Packages were released on [`bintray sentry-android`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/) and [`bintray sentry-java`](https://dl.bintray.com/getsentry/sentry-java/io/sentry/)

We'd love to get feedback.

## 3.0.0-alpha.2

TBD

Packages were released on [bintray](https://dl.bintray.com/getsentry/maven/io/sentry/)

> Note: This release marks the unification of the Java and Android Sentry codebases based on the core of the Android SDK (version 2.x).
Previous releases for the Android SDK (version 2.x) can be found on the now archived: https://github.com/getsentry/sentry-android/

## 3.0.0-alpha.1

### Features

### Fixes


## New releases will happen on a different repository:

https://github.com/getsentry/sentry-java

## What’s Changed

### Features

### Fixes


- feat: enable release health by default

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.3.1

### Fixes

- Add main thread checker for the app lifecycle integration ([#525](https://github.com/getsentry/sentry-android/pull/525)) @marandaneto
- Set correct migration link ([#523](https://github.com/getsentry/sentry-android/pull/523)) @fupduck
- Warn about Sentry re-initialization. ([#521](https://github.com/getsentry/sentry-android/pull/521)) @maciejwalkowiak
- Set SDK version in `MainEventProcessor`. ([#513](https://github.com/getsentry/sentry-android/pull/513)) @maciejwalkowiak
- Bump sentry-native to 0.4.0 ([#512](https://github.com/getsentry/sentry-android/pull/512)) @marandaneto
- Bump Gradle to 6.6 and fix linting issues ([#510](https://github.com/getsentry/sentry-android/pull/510)) @marandaneto
- fix(sentry-java): Contexts belong on the Scope ([#504](https://github.com/getsentry/sentry-android/pull/504)) @maciejwalkowiak
- Add tests for verifying scope changes thread isolation ([#508](https://github.com/getsentry/sentry-android/pull/508)) @maciejwalkowiak
- Set `SdkVersion` in default `SentryOptions` created in sentry-core module ([#506](https://github.com/getsentry/sentry-android/pull/506)) @maciejwalkowiak

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.3.0

### Features

- Add console application sample. ([#502](https://github.com/getsentry/sentry-android/pull/502)) @maciejwalkowiak
- Log stacktraces in SystemOutLogger ([#498](https://github.com/getsentry/sentry-android/pull/498)) @maciejwalkowiak
- Add method to add breadcrumb with string parameter. ([#501](https://github.com/getsentry/sentry-android/pull/501)) @maciejwalkowiak

### Fixes

- Converting UTC and ISO timestamp when missing Locale/TimeZone do not error ([#505](https://github.com/getsentry/sentry-android/pull/505)) @marandaneto
- Call `Sentry#close` on JVM shutdown. ([#497](https://github.com/getsentry/sentry-android/pull/497)) @maciejwalkowiak
- ref: sentry-core changes for console app ([#473](https://github.com/getsentry/sentry-android/pull/473)) @marandaneto

Obs: If you are using its own instance of `Hub`/`SentryClient` and reflection to set up the SDK to be usable within Libraries, this change may break your code, please fix the renamed classes.

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.2.2

### Features

- Add sdk to envelope header ([#488](https://github.com/getsentry/sentry-android/pull/488)) @marandaneto
- Log request if response code is not 200 ([#484](https://github.com/getsentry/sentry-android/pull/484)) @marandaneto

### Fixes

- Bump plugin versions ([#487](https://github.com/getsentry/sentry-android/pull/487)) @marandaneto
- Bump: AGP 4.0.1 ([#486](https://github.com/getsentry/sentry-android/pull/486)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.2.1

### Fixes

- Timber adds breadcrumb even if event level is < minEventLevel ([#480](https://github.com/getsentry/sentry-android/pull/480)) @marandaneto
- Contexts serializer avoids reflection and fixes desugaring issue ([#478](https://github.com/getsentry/sentry-android/pull/478)) @marandaneto
- clone session before sending to the transport ([#474](https://github.com/getsentry/sentry-android/pull/474)) @marandaneto
- Bump Gradle 6.5.1 ([#479](https://github.com/getsentry/sentry-android/pull/479)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.2.0

### Fixes

- Negative session sequence if the date is before java date epoch ([#471](https://github.com/getsentry/sentry-android/pull/471)) @marandaneto
- Deserialise unmapped contexts values from envelope ([#470](https://github.com/getsentry/sentry-android/pull/470)) @marandaneto
- Bump: sentry-native 0.3.4 ([#468](https://github.com/getsentry/sentry-android/pull/468)) @marandaneto

- feat: timber integration ([#464](https://github.com/getsentry/sentry-android/pull/464)) @marandaneto

1) To add integrations it requires a [manual initialization](https://docs.sentry.io/platforms/android/#manual-initialization) of the Android SDK.

2) Add the `sentry-android-timber` dependency:

```groovy
implementation 'io.sentry:sentry-android-timber:{version}' // version >= 2.2.0
```

3) Initialize and add the `SentryTimberIntegration`:

```java
SentryAndroid.init(this, options -> {
    // default values:
    // minEventLevel = ERROR
    // minBreadcrumbLevel = INFO
    options.addIntegration(new SentryTimberIntegration());

    // custom values for minEventLevel and minBreadcrumbLevel
    // options.addIntegration(new SentryTimberIntegration(SentryLevel.WARNING, SentryLevel.ERROR));
});
```

4) Use the Timber integration:

```java
try {
    int x = 1 / 0;
} catch (Exception e) {
    Timber.e(e);
}
```

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.7

### Fixes

- Init native libs if available on SDK init ([#461](https://github.com/getsentry/sentry-android/pull/461)) @marandaneto
- Make JVM target explicit in sentry-core ([#462](https://github.com/getsentry/sentry-android/pull/462)) @dilbernd
- Timestamp with millis from react-native should be in UTC format ([#456](https://github.com/getsentry/sentry-android/pull/456)) @marandaneto
- Bump Gradle to 6.5 ([#454](https://github.com/getsentry/sentry-android/pull/454)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.6

### Fixes

- Do not lookup sentry-debug-meta but instead load it directly ([#445](https://github.com/getsentry/sentry-android/pull/445)) @marandaneto
- Regression on v2.1.5 which can cause a crash on SDK init

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.5

### Fixes

This version has a severe bug and can cause a crash on SDK init

Please upgrade to https://github.com/getsentry/sentry-android/releases/tag/2.1.6

## 2.1.4

### Features

- Make gzip as default content encoding type ([#433](https://github.com/getsentry/sentry-android/pull/433)) @marandaneto
- Use AGP 4 features ([#366](https://github.com/getsentry/sentry-android/pull/366)) @marandaneto
- Create GH Actions CI for Ubuntu/macOS ([#403](https://github.com/getsentry/sentry-android/pull/403)) @marandaneto
- Make root checker better and minimize false positive ([#417](https://github.com/getsentry/sentry-android/pull/417)) @marandaneto

### Fixes

- bump: sentry-native to 0.3.1 ([#440](https://github.com/getsentry/sentry-android/pull/440)) @marandaneto
- Update last session timestamp ([#437](https://github.com/getsentry/sentry-android/pull/437)) @marandaneto
- Filter trim memory breadcrumbs ([#431](https://github.com/getsentry/sentry-android/pull/431)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.3

### Fixes

This fixes several critical bugs in sentry-android 2.0 and 2.1

- Sentry.init register integrations after creating the main Hub instead of doing it in the main Hub ctor ([#427](https://github.com/getsentry/sentry-android/pull/427)) @marandaneto
- make NoOpLogger public ([#425](https://github.com/getsentry/sentry-android/pull/425)) @marandaneto
- ConnectivityChecker returns connection status and events are not trying to be sent if no connection. ([#420](https://github.com/getsentry/sentry-android/pull/420)) @marandaneto
- thread pool executor is a single thread executor instead of scheduled thread executor ([#422](https://github.com/getsentry/sentry-android/pull/422)) @marandaneto
- Add Abnormal to the Session.State enum as its part of the protocol ([#424](https://github.com/getsentry/sentry-android/pull/424)) @marandaneto
- Bump: Gradle to 6.4.1 ([#419](https://github.com/getsentry/sentry-android/pull/419)) @marandaneto

We recommend that you use sentry-android 2.1.3 over the initial release of sentry-android 2.0 and 2.1.

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.2

### Features

- Added options to configure http transport ([#411](https://github.com/getsentry/sentry-android/pull/411)) @marandaneto

### Fixes

- Phone state breadcrumbs require read_phone_state on older OS versions ([#415](https://github.com/getsentry/sentry-android/pull/415)) @marandaneto @bsergean
- before raising ANR events, we check ProcessErrorStateInfo if available ([#412](https://github.com/getsentry/sentry-android/pull/412)) @marandaneto
- send cached events to use a single thread executor ([#405](https://github.com/getsentry/sentry-android/pull/405)) @marandaneto
- initing SDK on AttachBaseContext ([#409](https://github.com/getsentry/sentry-android/pull/409)) @marandaneto
- sessions can't be abnormal, but exited if not ended properly ([#410](https://github.com/getsentry/sentry-android/pull/410)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.1

### Features

- Added missing getters on Breadcrumb and SentryEvent ([#397](https://github.com/getsentry/sentry-android/pull/397)) @marandaneto
- Add trim memory breadcrumbs ([#395](https://github.com/getsentry/sentry-android/pull/395)) @marandaneto
- Only set breadcrumb extras if not empty ([#394](https://github.com/getsentry/sentry-android/pull/394)) @marandaneto
- Added samples of how to disable automatic breadcrumbs ([#389](https://github.com/getsentry/sentry-android/pull/389)) @marandaneto

### Fixes

- Set missing release, environment and dist to sentry-native options ([#404](https://github.com/getsentry/sentry-android/pull/404)) @marandaneto
- Do not add automatic and empty sensor breadcrumbs ([#401](https://github.com/getsentry/sentry-android/pull/401)) @marandaneto
- ref: removed Thread.sleep from LifecycleWatcher tests, using awaitility and DateProvider ([#392](https://github.com/getsentry/sentry-android/pull/392)) @marandaneto
- ref: added a DateTimeProvider for making retry after testable ([#391](https://github.com/getsentry/sentry-android/pull/391)) @marandaneto
- Bump Gradle to 6.4 ([#390](https://github.com/getsentry/sentry-android/pull/390)) @marandaneto
- Bump sentry-native to 0.2.6 ([#396](https://github.com/getsentry/sentry-android/pull/396)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.0

### Features

- Includes all the changes of 2.1.0 alpha, beta and RC

### Fixes

- fix when PhoneStateListener is not ready for use ([#387](https://github.com/getsentry/sentry-android/pull/387)) @marandaneto
- make ANR 5s by default ([#388](https://github.com/getsentry/sentry-android/pull/388)) @marandaneto
- rate limiting by categories ([#381](https://github.com/getsentry/sentry-android/pull/381)) @marandaneto
- Bump NDK to latest stable version 21.1.6352462 ([#386](https://github.com/getsentry/sentry-android/pull/386)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.0.3

### Fixes

- patch from 2.1.0-alpha.2 - avoid crash if NDK throws UnsatisfiedLinkError ([#344](https://github.com/getsentry/sentry-android/pull/344)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.0-RC.1

### Features

- Options for uncaught exception and make SentryOptions list Thread-Safe ([#384](https://github.com/getsentry/sentry-android/pull/384)) @marandaneto
- Automatic breadcrumbs for app, activity and sessions lifecycles and system events ([#348](https://github.com/getsentry/sentry-android/pull/348)) @marandaneto
- Make capture session and envelope internal ([#372](https://github.com/getsentry/sentry-android/pull/372)) @marandaneto

### Fixes

- If retry after header has empty categories, apply retry after to all of them ([#377](https://github.com/getsentry/sentry-android/pull/377)) @marandaneto
- Discard events and envelopes if cached and retry after ([#378](https://github.com/getsentry/sentry-android/pull/378)) @marandaneto
- Merge loadLibrary calls for sentry-native and clean up CMake files ([#373](https://github.com/getsentry/sentry-android/pull/373)) @Swatinem
- Exceptions should be sorted oldest to newest ([#370](https://github.com/getsentry/sentry-android/pull/370)) @marandaneto
- Check external storage size even if its read only ([#368](https://github.com/getsentry/sentry-android/pull/368)) @marandaneto
- Wrong check for cellular network capability ([#369](https://github.com/getsentry/sentry-android/pull/369)) @marandaneto
- add ScheduledForRemoval annotation to deprecated methods ([#375](https://github.com/getsentry/sentry-android/pull/375)) @marandaneto
- Bump NDK to 21.0.6113669 ([#367](https://github.com/getsentry/sentry-android/pull/367)) @marandaneto
- Bump AGP and add new make cmd to check for updates ([#365](https://github.com/getsentry/sentry-android/pull/365)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.0-beta.2

### Fixes

- Bump sentry-native to 0.2.4 ([#364](https://github.com/getsentry/sentry-android/pull/364)) @marandaneto
- Update current session on session start after deleting previous session ([#362](https://github.com/getsentry/sentry-android/pull/362)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.0-beta.1

### Fixes

- Bump sentry-native to 0.2.3 ([#357](https://github.com/getsentry/sentry-android/pull/357)) @marandaneto
- Check for androidx availability on runtime ([#356](https://github.com/getsentry/sentry-android/pull/356)) @marandaneto
- If theres a left over session file and its crashed, we should not overwrite its state ([#354](https://github.com/getsentry/sentry-android/pull/354)) @marandaneto
- Session should be exited state if state was ok ([#352](https://github.com/getsentry/sentry-android/pull/352)) @marandaneto
- Envelope has dedicated endpoint ([#353](https://github.com/getsentry/sentry-android/pull/353)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.0-alpha.2

### Fixes

- Change integration order for cached outbox events ([#347](https://github.com/getsentry/sentry-android/pull/347)) @marandaneto
- Avoid crash if NDK throws UnsatisfiedLinkError ([#344](https://github.com/getsentry/sentry-android/pull/344)) @marandaneto
- Avoid getting a threadlocal twice. ([#339](https://github.com/getsentry/sentry-android/pull/339)) @metlos
- Removing session tracking guard on hub and client ([#338](https://github.com/getsentry/sentry-android/pull/338)) @marandaneto
- Bump agp to 3.6.2 ([#336](https://github.com/getsentry/sentry-android/pull/336)) @marandaneto
- Fix racey ANR integration ([#332](https://github.com/getsentry/sentry-android/pull/332)) @marandaneto
- Logging envelopes path when possible instead of nullable id ([#331](https://github.com/getsentry/sentry-android/pull/331)) @marandaneto
- Renaming transport gate method ([#330](https://github.com/getsentry/sentry-android/pull/330)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.0-alpha.1

Release of Sentry's new SDK for Android.

## What’s Changed

### Features

- Release health @marandaneto @bruno-garcia
- ANR report should have 'was active=yes' on the dashboard ([#299](https://github.com/getsentry/sentry-android/pull/299)) @marandaneto
- NDK events apply scoped data ([#322](https://github.com/getsentry/sentry-android/pull/322)) @marandaneto
- Add a StdoutTransport ([#310](https://github.com/getsentry/sentry-android/pull/310)) @mike-burns
- Implementing new retry after protocol ([#306](https://github.com/getsentry/sentry-android/pull/306)) @marandaneto

### Fixes

- Bump sentry-native to 0.2.2 ([#305](https://github.com/getsentry/sentry-android/pull/305)) @Swatinem
- Missing App's info ([#315](https://github.com/getsentry/sentry-android/pull/315)) @marandaneto
- Buffered writers/readers - otimizations ([#311](https://github.com/getsentry/sentry-android/pull/311)) @marandaneto
- Boot time should be UTC ([#309](https://github.com/getsentry/sentry-android/pull/309)) @marandaneto
- Make transport result public ([#300](https://github.com/getsentry/sentry-android/pull/300)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.0.2

Release of Sentry's new SDK for Android.

### Features

- MavenCentral support ([#284](https://github.com/getsentry/sentry-android/pull/284)) @marandaneto

### Fixes

- Bump AGP to 3.6.1 ([#285](https://github.com/getsentry/sentry-android/pull/285)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.0.1

Release of Sentry's new SDK for Android.

## What’s Changed

### Features

- Attach threads/stacktraces ([#267](https://github.com/getsentry/sentry-android/pull/267)) @marandaneto
- Add the default serverName to SentryOptions and use it in MainEventProcessor ([#279](https://github.com/getsentry/sentry-android/pull/279)) @metlos

### Fixes

- set current threadId when there's no mechanism set ([#277](https://github.com/getsentry/sentry-android/pull/277)) @marandaneto
- Preview package manager ([#269](https://github.com/getsentry/sentry-android/pull/269)) @bruno-garcia

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.0.0

Release of Sentry's new SDK for Android.

New features not offered by (1.7.x):

- NDK support
  - Captures crashes caused by native code
  - Access to the [`sentry-native` SDK](https://github.com/getsentry/sentry-native/) API by your native (C/C++/Rust code/..).
- Automatic init (just add your `DSN` to the manifest)
   - Proguard rules are added automatically
   - Permission (Internet) is added automatically
- Uncaught Exceptions might be captured even before the app restarts
- Sentry's Unified API.
- More context/device information
- Packaged as `aar`
- Frames from the app automatically marked as `InApp=true` (stack traces in Sentry highlights them by default).
- Complete Sentry Protocol available.
- All threads and their stack traces are captured.
- Sample project in this repo to test many features (segfault, uncaught exception, ANR...)

Features from the current SDK like `ANR` are also available (by default triggered after 4 seconds).

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.0.0-rc04

Release of Sentry's new SDK for Android.

### Features

- Take sampleRate from metadata ([#262](https://github.com/getsentry/sentry-android/pull/262)) @bruno-garcia
- Support mills timestamp format ([#263](https://github.com/getsentry/sentry-android/pull/263)) @marandaneto
- Adding logs to installed integrations ([#265](https://github.com/getsentry/sentry-android/pull/265)) @marandaneto

### Fixes

- Breacrumb.data to string,object, Add LOG level ([#264](https://github.com/getsentry/sentry-android/pull/264)) @HazAT
- Read release conf. on manifest ([#266](https://github.com/getsentry/sentry-android/pull/266)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.30](https://github.com/getsentry/sentry-java/releases/tag/v1.7.30)

## 2.0.0-rc03

Release of Sentry's new SDK for Android.

### Fixes

- fixes ([#259](https://github.com/getsentry/sentry-android/issues/259)) - NPE check on getExternalFilesDirs items. ([#260](https://github.com/getsentry/sentry-android/pull/260)) @marandaneto
- strictMode typo ([#258](https://github.com/getsentry/sentry-android/pull/258)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.30](https://github.com/getsentry/sentry-java/releases/tag/v1.7.30)

## 2.0.0-rc02

Release of Sentry's new SDK for Android.

### Features

- Hub mode configurable ([#247](https://github.com/getsentry/sentry-android/pull/247)) @bruno-garcia
- Added remove methods (tags/extras) to the sentry static class ([#243](https://github.com/getsentry/sentry-android/pull/243)) @marandaneto

### Fixes


- Update ndk for new sentry-native version ([#235](https://github.com/getsentry/sentry-android/pull/235)) @Swatinem @marandaneto
- Make integrations public ([#256](https://github.com/getsentry/sentry-android/pull/256)) @marandaneto
- Bump build-tools ([#255](https://github.com/getsentry/sentry-android/pull/255)) @marandaneto
- Added javadocs to scope and its dependencies ([#253](https://github.com/getsentry/sentry-android/pull/253)) @marandaneto
- Build all ABIs ([#254](https://github.com/getsentry/sentry-android/pull/254)) @marandaneto
- Moving back ANR timeout from long to int param. ([#252](https://github.com/getsentry/sentry-android/pull/252)) @marandaneto
- Added HubAdapter to call Sentry static methods from Integrations ([#250](https://github.com/getsentry/sentry-android/pull/250)) @marandaneto
- New Release format ([#242](https://github.com/getsentry/sentry-android/pull/242)) @marandaneto
- Javadocs for SentryOptions ([#246](https://github.com/getsentry/sentry-android/pull/246)) @marandaneto
- non-app is already inApp excluded by default. ([#244](https://github.com/getsentry/sentry-android/pull/244)) @marandaneto
- Fix if symlink exists for sentry-native ([#241](https://github.com/getsentry/sentry-android/pull/241)) @marandaneto
- Clone method - race condition free ([#226](https://github.com/getsentry/sentry-android/pull/226)) @marandaneto
- Refactoring breadcrumbs callback ([#239](https://github.com/getsentry/sentry-android/pull/239)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.30](https://github.com/getsentry/sentry-java/releases/tag/v1.7.30)

## 2.0.0-rc01

Release of Sentry's new SDK for Android.

## What’s Changed

### Features

- Added remove methods for Scope data ([#237](https://github.com/getsentry/sentry-android/pull/237)) @marandaneto
- More device context (deviceId, connectionType and language) ([#229](https://github.com/getsentry/sentry-android/pull/229)) @marandaneto
- Added a few java docs (Sentry, Hub and SentryClient) ([#223](https://github.com/getsentry/sentry-android/pull/223)) @marandaneto
- Implemented diagnostic logger ([#218](https://github.com/getsentry/sentry-android/pull/218)) @marandaneto
- Added event processors to scope ([#209](https://github.com/getsentry/sentry-android/pull/209)) @marandaneto
- Added android transport gate ([#206](https://github.com/getsentry/sentry-android/pull/206)) @marandaneto
- Added executor for caching values out of the main thread ([#201](https://github.com/getsentry/sentry-android/pull/201)) @marandaneto

### Fixes


- Honor RetryAfter ([#236](https://github.com/getsentry/sentry-android/pull/236)) @marandaneto
- Add tests for SentryValues ([#238](https://github.com/getsentry/sentry-android/pull/238)) @philipphofmann
- Do not set frames if there's none ([#234](https://github.com/getsentry/sentry-android/pull/234)) @marandaneto
- Always call interrupt after InterruptedException ([#232](https://github.com/getsentry/sentry-android/pull/232)) @marandaneto
- Mark as current thread if its the main thread ([#228](https://github.com/getsentry/sentry-android/pull/228)) @marandaneto
- Fix lgtm alerts ([#219](https://github.com/getsentry/sentry-android/pull/219)) @marandaneto
- Written unit tests to ANR integration ([#215](https://github.com/getsentry/sentry-android/pull/215)) @marandaneto
- Added blog posts to README ([#214](https://github.com/getsentry/sentry-android/pull/214)) @marandaneto
- Raise code coverage for Dsn to 100% ([#212](https://github.com/getsentry/sentry-android/pull/212)) @philipphofmann
- Remove redundant times(1) for Mockito.verify ([#211](https://github.com/getsentry/sentry-android/pull/211)) @philipphofmann
- Transport may be set on options ([#203](https://github.com/getsentry/sentry-android/pull/203)) @marandaneto
- dist may be set on options ([#204](https://github.com/getsentry/sentry-android/pull/204)) @marandaneto
- Throw an exception if DSN is not set ([#200](https://github.com/getsentry/sentry-android/pull/200)) @marandaneto
- Migration guide markdown ([#197](https://github.com/getsentry/sentry-android/pull/197)) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.29](https://github.com/getsentry/sentry-java/releases/tag/v1.7.29)

## 2.0.0-beta02

Release of Sentry's new SDK for Android.

### Features

- addBreadcrumb overloads ([#196](https://github.com/getsentry/sentry-android/pull/196)) and ([#198](https://github.com/getsentry/sentry-android/pull/198))

### Fixes

- fix Android bug on API 24 and 25 about getting current threads and stack traces ([#194](https://github.com/getsentry/sentry-android/pull/194))

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)

## 2.0.0-beta01

Release of Sentry's new SDK for Android.

### Fixes

- ref: ANR doesn't set handled flag ([#186](https://github.com/getsentry/sentry-android/pull/186))
- SDK final review ([#183](https://github.com/getsentry/sentry-android/pull/183))
- ref: Drop errored in favor of crashed ([#187](https://github.com/getsentry/sentry-android/pull/187))
- Workaround android_id ([#185](https://github.com/getsentry/sentry-android/pull/185))
- Renamed sampleRate ([#191](https://github.com/getsentry/sentry-android/pull/191))
- Making timestamp package-private or test-only ([#190](https://github.com/getsentry/sentry-android/pull/190))
- Split event processor in Device/App data ([#180](https://github.com/getsentry/sentry-android/pull/180))

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)

## 2.0.0-alpha09

Release of Sentry's new SDK for Android.

### Features

- Adding nativeBundle plugin ([#161](https://github.com/getsentry/sentry-android/pull/161))
- Adding scope methods to sentry static class ([#179](https://github.com/getsentry/sentry-android/pull/179))

### Fixes

- fix: DSN parsing ([#165](https://github.com/getsentry/sentry-android/pull/165))
- Don't avoid exception type minification ([#166](https://github.com/getsentry/sentry-android/pull/166))
- make Gson retro compatible with older versions of AGP ([#177](https://github.com/getsentry/sentry-android/pull/177))
- Bump sentry-native with message object instead of a string ([#172](https://github.com/getsentry/sentry-android/pull/172))

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)

## 2.0.0-alpha08

Release of Sentry's new SDK for Android.

### Fixes

- DebugId endianness ([#162](https://github.com/getsentry/sentry-android/pull/162))
- Executed beforeBreadcrumb also for scope ([#160](https://github.com/getsentry/sentry-android/pull/160))
- Benefit of manifest merging when minSdk ([#159](https://github.com/getsentry/sentry-android/pull/159))
- Add method to captureMessage with level ([#157](https://github.com/getsentry/sentry-android/pull/157))
- Listing assets file on the wrong dir ([#156](https://github.com/getsentry/sentry-android/pull/156))

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)

## 2.0.0-alpha07

Third release of Sentry's new SDK for Android.

### Fixes

-  Fixed release for jcenter and bintray

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)

## 2.0.0-alpha06

Second release of Sentry's new SDK for Android.

### Fixes

- Fixed a typo on pom generation.

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)

## 2.0.0-alpha05

First release of Sentry's new SDK for Android.

New features not offered by our current (1.7.x), stable SDK are:

- NDK support
  - Captures crashes caused by native code
  - Access to the [`sentry-native` SDK](https://github.com/getsentry/sentry-native/) API by your native (C/C++/Rust code/..).
- Automatic init (just add your `DSN` to the manifest)
   - Proguard rules are added automatically
   - Permission (Internet) is added automatically
- Uncaught Exceptions might be captured even before the app restarts
- Unified API which include scopes etc.
- More context/device information
- Packaged as `aar`
- Frames from the app automatically marked as `InApp=true` (stack traces in Sentry highlights them by default).
- Complete Sentry Protocol available.
- All threads and their stack traces are captured.
- Sample project in this repo to test many features (segfault, uncaught exception, scope)

Features from the current SDK like `ANR` are also available (by default triggered after 4 seconds).

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)
