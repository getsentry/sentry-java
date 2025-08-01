# Changelog

## Unreleased

### Improvements

- Session Replay: Use main thread looper to schedule replay capture ([#4542](https://github.com/getsentry/sentry-java/pull/4542))

### Fixes

- Cache network capabilities and status to reduce IPC calls ([#4560](https://github.com/getsentry/sentry-java/pull/4560))
- Deduplicate battery breadcrumbs ([#4561](https://github.com/getsentry/sentry-java/pull/4561))
- Remove unused method in ManifestMetadataReader ([#4585](https://github.com/getsentry/sentry-java/pull/4585))
- Have single `NetworkCallback` registered at a time to reduce IPC calls ([#4562](https://github.com/getsentry/sentry-java/pull/4562))

## 8.18.0

### Features

- Add `SentryUserFeedbackButton` Composable ([#4559](https://github.com/getsentry/sentry-java/pull/4559))
  - Also added `Sentry.showUserFeedbackDialog` static method
- Add deadlineTimeout option ([#4555](https://github.com/getsentry/sentry-java/pull/4555))
- Add Ktor client integration ([#4527](https://github.com/getsentry/sentry-java/pull/4527))
  - To use the integration, add a dependency on `io.sentry:sentry-ktor-client`, then install the `SentryKtorClientPlugin` on your `HttpClient`,
    e.g.:
    ```kotlin
    val client =
      HttpClient(Java) {
        install(io.sentry.ktorClient.SentryKtorClientPlugin) {
          captureFailedRequests = true
          failedRequestTargets = listOf(".*")
          failedRequestStatusCodes = listOf(HttpStatusCodeRange(500, 599))
        }
      }
    ```

### Fixes

- Allow multiple UncaughtExceptionHandlerIntegrations to be active at the same time ([#4462](https://github.com/getsentry/sentry-java/pull/4462))
- Prevent repeated scroll target determination during a single scroll gesture ([#4557](https://github.com/getsentry/sentry-java/pull/4557))
  - This should reduce the number of ANRs seen in `SentryGestureListener`
- Do not use Sentry logging API in JUL if logs are disabled ([#4574](https://github.com/getsentry/sentry-java/pull/4574))
  - This was causing Sentry SDK to log warnings: "Sentry Log is disabled and this 'logger' call is a no-op."
- Do not use Sentry logging API in Log4j2 if logs are disabled ([#4573](https://github.com/getsentry/sentry-java/pull/4573))
  - This was causing Sentry SDK to log warnings: "Sentry Log is disabled and this 'logger' call is a no-op."
- SDKs send queue is no longer shutdown immediately on re-init ([#4564](https://github.com/getsentry/sentry-java/pull/4564))
  - This means we're no longer losing events that have been enqueued right before SDK re-init.
- Reduce scope forking when using OpenTelemetry ([#4565](https://github.com/getsentry/sentry-java/pull/4565))
  - `Sentry.withScope` now has the correct current scope passed to the callback. Previously our OpenTelemetry integration forked scopes an additional.
  - Overall the SDK is now forking scopes a bit less often.

## 8.17.0

### Features

- Send Timber logs through Sentry Logs ([#4490](https://github.com/getsentry/sentry-java/pull/4490))
  - Enable the Logs feature in your `SentryOptions` or with the `io.sentry.logs.enabled` manifest option and the SDK will automatically send Timber logs to Sentry, if the TimberIntegration is enabled.
  - The SDK will automatically detect Timber and use it to send logs to Sentry.
- Send logcat through Sentry Logs ([#4487](https://github.com/getsentry/sentry-java/pull/4487))
  - Enable the Logs feature in your `SentryOptions` or with the `io.sentry.logs.enabled` manifest option and the SDK will automatically send logcat logs to Sentry, if the Sentry Android Gradle plugin is applied.
  - To set the logcat level check the [Logcat integration documentation](https://docs.sentry.io/platforms/android/integrations/logcat/#configure).
- Read build tool info from `sentry-debug-meta.properties` and attach it to events ([#4314](https://github.com/getsentry/sentry-java/pull/4314))

### Dependencies

- Bump OpenTelemetry ([#4532](https://github.com/getsentry/sentry-java/pull/4532))
  - `opentelemetry-sdk` to `1.51.0`
  - `opentelemetry-instrumentation` to `2.17.0`
  - `opentelemetry-javaagent` to `2.17.0`
  - `opentelemetry-semconv` to `1.34.0`
  - We are now configuring OpenTelemetry to still behave the same way it did before for span names it generates in GraphQL auto instrumentation ([#4537](https://github.com/getsentry/sentry-java/pull/4537))
- Bump Gradle from v8.14.2 to v8.14.3 ([#4540](https://github.com/getsentry/sentry-java/pull/4540))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v8143)
  - [diff](https://github.com/gradle/gradle/compare/v8.14.2...v8.14.3)

### Fixes

- Use Spring Boot Starter 3 in `sentry-spring-boot-starter-jakarta` ([#4545](https://github.com/getsentry/sentry-java/pull/4545))
  - While refactoring our dependency management, we accidentally added Spring Boot 2 and Spring Boot Starter 2 as dependencies of `sentry-spring-boot-starter-jakarta`, which is intended for Spring Boot 3.
  - Now, the correct dependencies (Spring Boot 3 and Spring Boot Starter 3) are being added.

## 8.16.1-alpha.2

### Fixes

- Optimize scope when maxBreadcrumb is 0 ([#4504](https://github.com/getsentry/sentry-java/pull/4504))
- Fix javadoc on TransportResult ([#4528](https://github.com/getsentry/sentry-java/pull/4528))
- Session Replay: Fix `IllegalArgumentException` when `Bitmap` is initialized with non-positive values ([#4536](https://github.com/getsentry/sentry-java/pull/4536))
- Set thread information on transaction from OpenTelemetry attributes ([#4478](https://github.com/getsentry/sentry-java/pull/4478))

### Internal

- Flattened PerformanceCollectionData ([#4505](https://github.com/getsentry/sentry-java/pull/4505))

## 8.16.0

### Features

- Send JUL logs to Sentry as logs ([#4518](https://github.com/getsentry/sentry-java/pull/4518))
  - You need to enable the logs feature, either in `sentry.properties`:
    ```properties
    logs.enabled=true
    ```
  - Or, if you manually initialize Sentry, you may also enable logs on `Sentry.init`:
    ```java
    Sentry.init(options -> {
      ...
      options.getLogs().setEnabled(true);
    });
    ```
  - It is also possible to set the `minimumLevel` in `logging.properties`, meaning any log message >= the configured level will be sent to Sentry and show up under Logs:
    ```properties
    io.sentry.jul.SentryHandler.minimumLevel=CONFIG
    ```
- Send Log4j2 logs to Sentry as logs ([#4517](https://github.com/getsentry/sentry-java/pull/4517))
  - You need to enable the logs feature either in `sentry.properties`:
    ```properties
    logs.enabled=true
    ```
  - If you manually initialize Sentry, you may also enable logs on `Sentry.init`:
    ```java
    Sentry.init(options -> {
      ...
      options.getLogs().setEnabled(true);
    });
    ```
  - It is also possible to set the `minimumLevel` in `log4j2.xml`, meaning any log message >= the configured level will be sent to Sentry and show up under Logs:
    ```xml
    <Sentry name="Sentry"
        dsn="your DSN"
        minimumBreadcrumbLevel="DEBUG"
        minimumEventLevel="WARN"
        minimumLevel="DEBUG"
    />
    ```

## 8.15.1

### Fixes

- Enabling Sentry Logs through Logback in Spring Boot config did not work in 3.15.0 ([#4523](https://github.com/getsentry/sentry-java/pull/4523))

## 8.15.0

### Features

- Add chipset to device context ([#4512](https://github.com/getsentry/sentry-java/pull/4512))

### Fixes

- No longer send out empty log envelopes ([#4497](https://github.com/getsentry/sentry-java/pull/4497))
- Session Replay: Expand fix for crash on devices to all Unisoc/Spreadtrum chipsets ([#4510](https://github.com/getsentry/sentry-java/pull/4510))
- Log parameter objects are now turned into `String` via `toString` ([#4515](https://github.com/getsentry/sentry-java/pull/4515))
  - One of the two `SentryLogEventAttributeValue` constructors did not convert the value previously.
- Logs are now flushed on shutdown ([#4503](https://github.com/getsentry/sentry-java/pull/4503))
- User Feedback: Do not redefine system attributes for `SentryUserFeedbackButton`, but reference them instead ([#4519](https://github.com/getsentry/sentry-java/pull/4519))

### Features

- Send Logback logs to Sentry as logs ([#4502](https://github.com/getsentry/sentry-java/pull/4502))
  - You need to enable the logs feature and can also set the `minimumLevel` for log events:
    ```xml
    <appender name="sentry" class="io.sentry.logback.SentryAppender">
      <options>
        <!-- NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in your Sentry project/dashboard -->
        <dsn>https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563</dsn>
        <logs>
          <enabled>true</enabled>
        </logs>
      </options>
      <!-- Demonstrates how to modify the minimum values -->
      <!-- Default for Events is ERROR -->
      <minimumEventLevel>WARN</minimumEventLevel>
      <!-- Default for Breadcrumbs is INFO -->
      <minimumBreadcrumbLevel>DEBUG</minimumBreadcrumbLevel>
      <!-- Default for Log Events is INFO -->
      <minimumLevel>INFO</minimumLevel>
    </appender>
    ```
  - For Spring Boot you may also enable it in `application.properties` / `application.yml`:
    ```properties
    sentry.logs.enabled=true
    sentry.logging.minimum-level=error
    ```
  - If you manually initialize Sentry, you may also enable logs on `Sentry.init`:
    ```java
    Sentry.init(options -> {
      ...
      options.getLogs().setEnabled(true);
    });
    ```
  - Enabling via `sentry.properties` is also possible:
    ```properties
    logs.enabled=true
    ```
- Automatically use `SentryOptions.Logs.BeforeSendLogCallback` Spring beans ([#4509](https://github.com/getsentry/sentry-java/pull/4509))

### Dependencies

- Bump Gradle from v8.14.1 to v8.14.2 ([#4473](https://github.com/getsentry/sentry-java/pull/4473))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v8142)
  - [diff](https://github.com/gradle/gradle/compare/v8.14.1...v8.14.2)

## 8.14.0

### Fixes

- Fix Session Replay masking for newer versions of Jetpack Compose (1.8+) ([#4485](https://github.com/getsentry/sentry-java/pull/4485))

### Features

- Add New User Feedback Widget ([#4450](https://github.com/getsentry/sentry-java/pull/4450))
    - This widget is a custom button that can be used to show the user feedback form
- Add New User Feedback form ([#4384](https://github.com/getsentry/sentry-java/pull/4384))
    - We now introduce SentryUserFeedbackDialog, which extends AlertDialog, inheriting the show() and cancel() methods, among others.
      To use it, just instantiate it and call show() on the instance (Sentry must be previously initialized).
      For customization options, please check the [User Feedback documentation](https://docs.sentry.io/platforms/android/user-feedback/configuration/).
      ```java
      import io.sentry.android.core.SentryUserFeedbackDialog;
      
      new SentryUserFeedbackDialog.Builder(context).create().show();
      ```
      ```kotlin
      import io.sentry.android.core.SentryUserFeedbackDialog
    
      SentryUserFeedbackDialog.Builder(context).create().show()
      ```
- Add `user.id`, `user.name` and `user.email` to log attributes ([#4486](https://github.com/getsentry/sentry-java/pull/4486))
- User `name` attribute has been deprecated, please use `username` instead ([#4486](https://github.com/getsentry/sentry-java/pull/4486))
- Add device (`device.brand`, `device.model` and `device.family`) and OS (`os.name` and `os.version`) attributes to logs ([#4493](https://github.com/getsentry/sentry-java/pull/4493))
- Serialize `preContext` and `postContext` in `SentryStackFrame` ([#4482](https://github.com/getsentry/sentry-java/pull/4482))

### Internal

- User Feedback now uses SentryUser.username instead of SentryUser.name ([#4494](https://github.com/getsentry/sentry-java/pull/4494))

## 8.13.3

### Fixes

- Send UI Profiling app start chunk when it finishes ([#4423](https://github.com/getsentry/sentry-java/pull/4423))
- Republish Javadoc [#4457](https://github.com/getsentry/sentry-java/pull/4457)
- Finalize `OkHttpEvent` even if no active span in `SentryOkHttpInterceptor` [#4469](https://github.com/getsentry/sentry-java/pull/4469)
- Session Replay: Do not capture current replay for cached events from the past ([#4474](https://github.com/getsentry/sentry-java/pull/4474))
- Session Replay: Correctly capture Dialogs and non full-sized windows ([#4354](https://github.com/getsentry/sentry-java/pull/4354))
- Session Replay: Fix inconsistent `segment_id` ([#4471](https://github.com/getsentry/sentry-java/pull/4471))
- Session Replay: Fix crash on devices with the Unisoc/Spreadtrum T606 chipset ([#4477](https://github.com/getsentry/sentry-java/pull/4477))

## 8.13.2

### Fixes

- Don't apply Spring Boot plugin in `sentry-spring-boot-jakarta` ([#4456](https://github.com/getsentry/sentry-java/pull/4456))
  - The jar for `io.sentry:sentry-spring-boot-jakarta` is now correctly being built and published to Maven Central.

## 8.13.1

### Fixes

- Fix `SentryAndroid.init` crash if SDK is initialized from a background thread while an `Activity` is in resumed state ([#4449](https://github.com/getsentry/sentry-java/pull/4449))

### Dependencies

- Bump Gradle from v8.14 to v8.14.1 ([#4437](https://github.com/getsentry/sentry-java/pull/4437))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v8141)
  - [diff](https://github.com/gradle/gradle/compare/v8.14...v8.14.1)

## 8.13.0

### Features

- Add debug mode for Session Replay masking ([#4357](https://github.com/getsentry/sentry-java/pull/4357))
    - Use `Sentry.replay().enableDebugMaskingOverlay()` to overlay the screen with the Session Replay masks.
    - The masks will be invalidated at most once per `frameRate` (default 1 fps).
- Extend Logs API to allow passing in `attributes` ([#4402](https://github.com/getsentry/sentry-java/pull/4402))
  - `Sentry.logger.log` now takes a `SentryLogParameters`
  - Use `SentryLogParameters.create(SentryAttributes.of(...))` to pass attributes
    - Attribute values may be of type `string`, `boolean`, `integer` or `double`.
    - Other types will be converted to `string`. Currently we simply call `toString()` but we might offer more in the future.
    - You may manually flatten complex types into multiple separate attributes of simple types.
      - e.g. intead of `SentryAttribute.named("point", Point(10, 20))` you may store it as `SentryAttribute.integerAttribute("point.x", point.x)` and `SentryAttribute.integerAttribute("point.y", point.y)`
    - `SentryAttribute.named()` will automatically infer the type or fall back to `string`.
    - `SentryAttribute.booleanAttribute()` takes a `Boolean` value
    - `SentryAttribute.integerAttribute()` takes a `Integer` value
    - `SentryAttribute.doubleAttribute()` takes a `Double` value
    - `SentryAttribute.stringAttribute()` takes a `String` value
  - We opted for handling parameters via `SentryLogParameters` to avoid creating tons of overloads that are ambiguous.

### Fixes

- Isolation scope is now forked in `OtelSentrySpanProcessor` instead of `OtelSentryPropagator` ([#4434](https://github.com/getsentry/sentry-java/pull/4434))
  - Since propagator may never be invoked we moved the location where isolation scope is forked.
  - Not invoking `OtelSentryPropagator.extract` or having a `sentry-trace` header that failed to parse would cause isolation scope not to be forked.
  - This in turn caused data to bleed between scopes, e.g. from one request into another

### Dependencies

- Bump Spring Boot to `3.5.0` ([#4111](https://github.com/getsentry/sentry-java/pull/4111))

## 8.12.0

### Features

- Add new User Feedback API ([#4286](https://github.com/getsentry/sentry-java/pull/4286))
    - We now introduced Sentry.captureFeedback, which supersedes Sentry.captureUserFeedback
- Add Sentry Log Feature ([#4372](https://github.com/getsentry/sentry-java/pull/4372))
    - The feature is disabled by default and needs to be enabled by:
        - `options.getLogs().setEnabled(true)` in `Sentry.init` / `SentryAndroid.init`
        - `<meta-data android:name="io.sentry.logs.enabled" android:value="true" />` in `AndroidManifest.xml`
        - `logs.enabled=true` in `sentry.properties`
        - `sentry.logs.enabled=true` in `application.properties`
        - `sentry.logs.enabled: true` in `application.yml`
    - Logs can be captured using `Sentry.logger().info()` and similar methods.
    - Logs also take a format string and arguments which we then send through `String.format`.
    - Please use `options.getLogs().setBeforeSend()` to filter outgoing logs

### Fixes

- Hook User Interaction integration into running Activity in case of deferred SDK init ([#4337](https://github.com/getsentry/sentry-java/pull/4337))

### Dependencies

- Bump Gradle from v8.13 to v8.14.0 ([#4360](https://github.com/getsentry/sentry-java/pull/4360))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v8140)
  - [diff](https://github.com/gradle/gradle/compare/v8.13...v8.14.0)

## 8.11.1

### Fixes

- Fix Android profile chunk envelope type for UI Profiling ([#4366](https://github.com/getsentry/sentry-java/pull/4366))

## 8.11.0

### Features

- Make `RequestDetailsResolver` public ([#4326](https://github.com/getsentry/sentry-java/pull/4326))
  - `RequestDetailsResolver` is now public and has an additional constructor, making it easier to use a custom `TransportFactory`

### Fixes

- Session Replay: Fix masking of non-styled `Text` Composables ([#4361](https://github.com/getsentry/sentry-java/pull/4361))
- Session Replay: Fix masking read-only `TextField` Composables ([#4362](https://github.com/getsentry/sentry-java/pull/4362))

## 8.10.0

### Features

- Wrap configured OpenTelemetry `ContextStorageProvider` if available ([#4359](https://github.com/getsentry/sentry-java/pull/4359))
  - This is only relevant if you see `java.lang.IllegalStateException: Found multiple ContextStorageProvider. Set the io.opentelemetry.context.ContextStorageProvider property to the fully qualified class name of the provider to use. Falling back to default ContextStorage. Found providers: ...` 
  - Set `-Dio.opentelemetry.context.contextStorageProvider=io.sentry.opentelemetry.SentryContextStorageProvider` on your `java` command
  - Sentry will then wrap the other `ContextStorageProvider` that has been configured by loading it through SPI
  - If no other `ContextStorageProvider` is available or there are problems loading it, we fall back to using `SentryOtelThreadLocalStorage`
    
### Fixes

- Update profile chunk rate limit and client report ([#4353](https://github.com/getsentry/sentry-java/pull/4353))

### Dependencies

- Bump Native SDK from v0.8.3 to v0.8.4 ([#4343](https://github.com/getsentry/sentry-java/pull/4343))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#084)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.8.3...0.8.4)

## 8.9.0

### Features

- Add `SentryWrapper.wrapRunnable` to wrap `Runnable` for use with Sentry ([#4332](https://github.com/getsentry/sentry-java/pull/4332))

### Fixes

- Fix TTFD measurement when API called too early ([#4297](https://github.com/getsentry/sentry-java/pull/4297))
- Tag sockets traffic originating from Sentry's HttpConnection ([#4340](https://github.com/getsentry/sentry-java/pull/4340))
  - This should suppress the StrictMode's `UntaggedSocketViolation`
- Reduce debug logs verbosity ([#4341](https://github.com/getsentry/sentry-java/pull/4341))
- Fix unregister `SystemEventsBroadcastReceiver` when entering background ([#4338](https://github.com/getsentry/sentry-java/pull/4338))
  - This should reduce ANRs seen with this class in the stack trace for Android 14 and above

### Improvements

- Make user interaction tracing faster and do fewer allocations ([#4347](https://github.com/getsentry/sentry-java/pull/4347))
- Pre-load modules on a background thread upon SDK init ([#4348](https://github.com/getsentry/sentry-java/pull/4348))

## 8.8.0

### Features

- Add `CoroutineExceptionHandler` for reporting uncaught exceptions in coroutines to Sentry ([#4259](https://github.com/getsentry/sentry-java/pull/4259))
  - This is now part of `sentry-kotlin-extensions` and can be used together with `SentryContext` when launching a coroutine
  - Any exceptions thrown in a coroutine when using the handler will be captured (not rethrown!) and reported to Sentry
  - It's also possible to extend `CoroutineExceptionHandler` to implement custom behavior in addition to the one we provide by default

### Fixes

- Use thread context classloader when available ([#4320](https://github.com/getsentry/sentry-java/pull/4320))
  - This ensures correct resource loading in environments like Spring Boot where the thread context classloader is used for resource loading.
- Improve low memory breadcrumb capturing ([#4325](https://github.com/getsentry/sentry-java/pull/4325))
- Fix do not initialize SDK for Jetpack Compose Preview builds ([#4324](https://github.com/getsentry/sentry-java/pull/4324))
- Fix Synchronize Baggage values ([#4327](https://github.com/getsentry/sentry-java/pull/4327))

### Improvements

- Make `SystemEventsBreadcrumbsIntegration` faster ([#4330](https://github.com/getsentry/sentry-java/pull/4330))

## 8.7.0

### Features

- UI Profiling GA

  Continuous Profiling is now GA, named UI Profiling. To enable it you can use one of the following options. More info can be found at https://docs.sentry.io/platforms/android/profiling/.
    Note: Both `options.profilesSampler` and `options.profilesSampleRate` must **not** be set to enable UI Profiling.
    To keep the same transaction-based behaviour, without the 30 seconds limitation, you can use the `trace` lifecycle mode.
  
  ```xml
  <application>
    <!-- Enable UI profiling, adjust in production env. This is evaluated only once per session -->
    <meta-data android:name="io.sentry.traces.profiling.session-sample-rate" android:value="1.0" />
    <!-- Set profiling lifecycle, can be `manual` (controlled through `Sentry.startProfiler()` and `Sentry.stopProfiler()`) or `trace` (automatically starts and stop a profile whenever a sampled trace starts and finishes) -->
    <meta-data android:name="io.sentry.traces.profiling.lifecycle" android:value="trace" />
    <!-- Enable profiling on app start. The app start profile will be stopped automatically when the app start root span finishes -->
    <meta-data android:name="io.sentry.traces.profiling.start-on-app-start" android:value="true" />
  </application>
  ```
  ```java
  import io.sentry.ProfileLifecycle;
  import io.sentry.android.core.SentryAndroid;
  
  SentryAndroid.init(context, options -> {
      // Enable UI profiling, adjust in production env. This is evaluated only once per session
      options.setProfileSessionSampleRate(1.0);
      // Set profiling lifecycle, can be `manual` (controlled through `Sentry.startProfiler()` and `Sentry.stopProfiler()`) or `trace` (automatically starts and stop a profile whenever a sampled trace starts and finishes)
      options.setProfileLifecycle(ProfileLifecycle.TRACE);
      // Enable profiling on app start. The app start profile will be stopped automatically when the app start root span finishes
      options.setStartProfilerOnAppStart(true);
    });
  ```
  ```kotlin
  import io.sentry.ProfileLifecycle
  import io.sentry.android.core.SentryAndroid

  SentryAndroid.init(context, { options ->
    // Enable UI profiling, adjust in production env. This is evaluated only once per session
    options.profileSessionSampleRate = 1.0
    // Set profiling lifecycle, can be `manual` (controlled through `Sentry.startProfiler()` and `Sentry.stopProfiler()`) or `trace` (automatically starts and stop a profile whenever a sampled trace starts and finishes)
    options.profileLifecycle = ProfileLifecycle.TRACE
    // Enable profiling on app start. The app start profile will be stopped automatically when the app start root span finishes
    options.isStartProfilerOnAppStart = true
    })
  ```

  - Continuous Profiling - Stop when app goes in background ([#4311](https://github.com/getsentry/sentry-java/pull/4311))
  - Continuous Profiling - Add delayed stop ([#4293](https://github.com/getsentry/sentry-java/pull/4293))
  - Continuous Profiling - Out of Experimental ([#4310](https://github.com/getsentry/sentry-java/pull/4310))

### Fixes

- Compress Screenshots on a background thread ([#4295](https://github.com/getsentry/sentry-java/pull/4295))

## 8.6.0

### Behavioral Changes

- The Sentry SDK will now crash on startup if mixed versions have been detected ([#4277](https://github.com/getsentry/sentry-java/pull/4277))
  - On `Sentry.init` / `SentryAndroid.init` the SDK now checks if all Sentry Java / Android SDK dependencies have the same version.
  - While this may seem like a bad idea at first glance, mixing versions of dependencies has a very high chance of causing a crash later. We opted for a controlled crash that's hard to miss.
  - Note: This detection only works for new versions of the SDK, so please take this as a reminder to check your SDK version alignment manually when upgrading the SDK to this version and then you should be good.
  - The SDK will also print log messages if mixed versions have been detected at a later point. ([#4270](https://github.com/getsentry/sentry-java/pull/4270))
    - This takes care of cases missed by the startup check above due to older versions.

### Features

- Increase http timeouts from 5s to 30s to have a better chance of events being delivered without retry ([#4276](https://github.com/getsentry/sentry-java/pull/4276))
- Add `MANIFEST.MF` to Sentry JARs ([#4272](https://github.com/getsentry/sentry-java/pull/4272))
- Retain baggage sample rate/rand values as doubles ([#4279](https://github.com/getsentry/sentry-java/pull/4279))
- Introduce fatal SDK logger ([#4288](https://github.com/getsentry/sentry-java/pull/4288))
  - We use this to print out messages when there is a problem that prevents the SDK from working correctly.
  - One example for this is when the SDK has been configured with mixed dependency versions where we print out details, which module and version are affected.

### Fixes

- Do not override user-defined `SentryOptions` ([#4262](https://github.com/getsentry/sentry-java/pull/4262))
- Session Replay: Change bitmap config to `ARGB_8888` for screenshots ([#4282](https://github.com/getsentry/sentry-java/pull/4282))
- The `MANIFEST.MF` of `sentry-opentelemetry-agent` now has `Implementation-Version` set to the raw version ([#4291](https://github.com/getsentry/sentry-java/pull/4291))
  - An example value would be `8.6.0`
  - The value of the `Sentry-Version-Name` attribute looks like `sentry-8.5.0-otel-2.10.0`
- Fix tags missing for compose view hierarchies ([#4275](https://github.com/getsentry/sentry-java/pull/4275))
- Do not leak SentryFileInputStream/SentryFileOutputStream descriptors and channels ([#4296](https://github.com/getsentry/sentry-java/pull/4296))
- Remove "not yet implemented" from `Sentry.flush` comment ([#4305](https://github.com/getsentry/sentry-java/pull/4305))

### Internal

- Added `platform` to SentryEnvelopeItemHeader ([#4287](https://github.com/getsentry/sentry-java/pull/4287))
  - Set `android` platform to ProfileChunk envelope item header

### Dependencies

- Bump Native SDK from v0.8.1 to v0.8.3 ([#4267](https://github.com/getsentry/sentry-java/pull/4267), [#4298](https://github.com/getsentry/sentry-java/pull/4298))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#083)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.8.1...0.8.3)
- Bump Spring Boot from 2.7.5 to 2.7.18 ([#3496](https://github.com/getsentry/sentry-java/pull/3496))

## 8.5.0

### Features

- Add native stack frame address information and debug image metadata to ANR events ([#4061](https://github.com/getsentry/sentry-java/pull/4061))
    - This enables symbolication for stripped native code in ANRs
- Add Continuous Profiling Support ([#3710](https://github.com/getsentry/sentry-java/pull/3710))

  To enable Continuous Profiling use the `Sentry.startProfiler` and `Sentry.stopProfiler` experimental APIs. Sampling rate can be set through `options.profileSessionSampleRate`, which defaults to null (disabled).   
  Note: Both `options.profilesSampler` and `options.profilesSampleRate` must **not** be set to enable Continuous Profiling.

  ```java
  import io.sentry.ProfileLifecycle;
  import io.sentry.android.core.SentryAndroid;

  SentryAndroid.init(context) { options ->
   
    // Currently under experimental options:
    options.getExperimental().setProfileSessionSampleRate(1.0);
    // In manual mode, you need to start and stop the profiler manually using Sentry.startProfiler and Sentry.stopProfiler
    // In trace mode, the profiler will start and stop automatically whenever a sampled trace starts and finishes
    options.getExperimental().setProfileLifecycle(ProfileLifecycle.MANUAL);
  }
  // Start profiling
  Sentry.startProfiler();
  
  // After all profiling is done, stop the profiler. Profiles can last indefinitely if not stopped.
  Sentry.stopProfiler();
  ```
  ```kotlin
  import io.sentry.ProfileLifecycle
  import io.sentry.android.core.SentryAndroid

  SentryAndroid.init(context) { options ->
   
    // Currently under experimental options:
    options.experimental.profileSessionSampleRate = 1.0
    // In manual mode, you need to start and stop the profiler manually using Sentry.startProfiler and Sentry.stopProfiler
    // In trace mode, the profiler will start and stop automatically whenever a sampled trace starts and finishes
    options.experimental.profileLifecycle = ProfileLifecycle.MANUAL
  }
  // Start profiling
  Sentry.startProfiler()
  
  // After all profiling is done, stop the profiler. Profiles can last indefinitely if not stopped.
  Sentry.stopProfiler()
  ```

  To learn more visit [Sentry's Continuous Profiling](https://docs.sentry.io/product/explore/profiling/transaction-vs-continuous-profiling/#continuous-profiling-mode) documentation page.

### Fixes

- Reduce excessive CPU usage when serializing breadcrumbs to disk for ANRs ([#4181](https://github.com/getsentry/sentry-java/pull/4181))
- Ensure app start type is set, even when ActivityLifecycleIntegration is not running ([#4250](https://github.com/getsentry/sentry-java/pull/4250))
- Use `SpringServletTransactionNameProvider` as fallback for Spring WebMVC ([#4263](https://github.com/getsentry/sentry-java/pull/4263))
  - In certain cases the SDK was not able to provide a transaction name automatically and thus did not finish the transaction for the request.
  - We now first try `SpringMvcTransactionNameProvider` which would provide the route as transaction name.
  - If that does not return anything, we try `SpringServletTransactionNameProvider` next, which returns the URL of the request.

### Behavioral Changes

- The user's `device.name` is not reported anymore via the device context, even if `options.isSendDefaultPii` is enabled ([#4179](https://github.com/getsentry/sentry-java/pull/4179))

### Dependencies

- Bump Gradle from v8.12.1 to v8.13.0 ([#4209](https://github.com/getsentry/sentry-java/pull/4209))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v8130)
  - [diff](https://github.com/gradle/gradle/compare/v8.12.1...v8.13.0)

## 8.4.0

### Fixes

- The SDK now handles `null` on many APIs instead of expecting a non `null` value ([#4245](https://github.com/getsentry/sentry-java/pull/4245))
  - Certain APIs like `setTag`, `setData`, `setExtra`, `setContext` previously caused a `NullPointerException` when invoked with either `null` key or value.
  - The SDK now tries to have a sane fallback when `null` is passed and no longer throws `NullPointerException`
  - If `null` is passed, the SDK will
    - do nothing if a `null` key is passed, returning `null` for non void methods
    - remove any previous value if the new value is set to `null`
- Add support for setting in-app-includes/in-app-excludes via AndroidManifest.xml ([#4240](https://github.com/getsentry/sentry-java/pull/4240))
- Modifications to OkHttp requests are now properly propagated to the affected span / breadcrumbs ([#4238](https://github.com/getsentry/sentry-java/pull/4238))
  - Please ensure the SentryOkHttpInterceptor is added last to your OkHttpClient, as otherwise changes to the `Request`  by subsequent interceptors won't be considered
- Fix "class ch.qos.logback.classic.spi.ThrowableProxyVO cannot be cast to class ch.qos.logback.classic.spi.ThrowableProxy" ([#4206](https://github.com/getsentry/sentry-java/pull/4206))
  - In this case we cannot report the `Throwable` to Sentry as it's not available
  - If you are using OpenTelemetry v1 `OpenTelemetryAppender`, please consider upgrading to v2
- Pass OpenTelemetry span attributes into TracesSampler callback ([#4253](https://github.com/getsentry/sentry-java/pull/4253))
  - `SamplingContext` now has a `getAttribute` method that grants access to OpenTelemetry span attributes via their String key (e.g. `http.request.method`)
- Fix AbstractMethodError when using SentryTraced for Jetpack Compose ([#4255](https://github.com/getsentry/sentry-java/pull/4255))
- Assume `http.client` for span `op` if not a root span ([#4257](https://github.com/getsentry/sentry-java/pull/4257))
- Avoid unnecessary copies when using `CopyOnWriteArrayList` ([#4247](https://github.com/getsentry/sentry-java/pull/4247))
  - This affects in particular `SentryTracer.getLatestActiveSpan` which would have previously copied all child span references. This may have caused `OutOfMemoryError` on certain devices due to high frequency of calling the method.

### Features

- The SDK now automatically propagates the trace-context to the native layer. This allows to connect errors on different layers of the application. ([#4137](https://github.com/getsentry/sentry-java/pull/4137))
- Capture OpenTelemetry span events ([#3564](https://github.com/getsentry/sentry-java/pull/3564))
  - OpenTelemetry spans may have exceptions attached to them (`openTelemetrySpan.recordException`). We can now send those to Sentry as errors.
  - Set `capture-open-telemetry-events=true` in `sentry.properties` to enable it
  - Set `sentry.capture-open-telemetry-events=true` in Springs `application.properties` to enable it
  - Set `sentry.captureOpenTelemetryEvents: true` in Springs `application.yml` to enable it

### Behavioural Changes

- Use `java.net.URI` for parsing URLs in `UrlUtils` ([#4210](https://github.com/getsentry/sentry-java/pull/4210))
  - This could affect grouping for issues with messages containing URLs that fall in known corner cases that were handled incorrectly previously (e.g. email in URL path)

### Internal

- Also use port when checking if a request is made to Sentry DSN ([#4231](https://github.com/getsentry/sentry-java/pull/4231))
  - For our OpenTelemetry integration we check if a span is for a request to Sentry
  - We now also consider the port when performing this check

### Dependencies

- Bump Native SDK from v0.7.20 to v0.8.1 ([#4137](https://github.com/getsentry/sentry-java/pull/4137))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#0810)
  - [diff](https://github.com/getsentry/sentry-native/compare/v0.7.20...0.8.1)

## 8.3.0

### Features

- Add HTTP server request headers from OpenTelemetry span attributes to sentry `request` in payload ([#4102](https://github.com/getsentry/sentry-java/pull/4102))
  - You have to explicitly enable each header by adding it to the [OpenTelemetry config](https://opentelemetry.io/docs/zero-code/java/agent/instrumentation/http/#capturing-http-request-and-response-headers)
  - Please only enable headers you actually want to send to Sentry. Some may contain sensitive data like PII, cookies, tokens etc.
  - We are no longer adding request/response headers to `contexts/otel/attributes` of the event.
- The `ignoredErrors` option is now configurable via the manifest property `io.sentry.traces.ignored-errors` ([#4178](https://github.com/getsentry/sentry-java/pull/4178))
- A list of active Spring profiles is attached to payloads sent to Sentry (errors, traces, etc.) and displayed in the UI when using our Spring or Spring Boot integrations ([#4147](https://github.com/getsentry/sentry-java/pull/4147))
  - This consists of an empty list when only the default profile is active
- Added `enableTraceIdGeneration` to the AndroidOptions. This allows Hybrid SDKs to "freeze" and control the trace and connect errors on different layers of the application ([4188](https://github.com/getsentry/sentry-java/pull/4188))
- Move to a single NetworkCallback listener to reduce number of IPC calls on Android ([#4164](https://github.com/getsentry/sentry-java/pull/4164))
- Add GraphQL Apollo Kotlin 4 integration ([#4166](https://github.com/getsentry/sentry-java/pull/4166))
- Add support for async dispatch requests to Spring Boot 2 and 3 ([#3983](https://github.com/getsentry/sentry-java/pull/3983))
  - To enable it, please set `sentry.keep-transactions-open-for-async-responses=true` in `application.properties` or `sentry.keepTransactionsOpenForAsyncResponses: true` in `application.yml`
- Add constructor to JUL `SentryHandler` for disabling external config ([#4208](https://github.com/getsentry/sentry-java/pull/4208))

### Fixes

- Filter strings that cannot be parsed as Regex no longer cause an SDK crash ([#4213](https://github.com/getsentry/sentry-java/pull/4213))
  - This was the case e.g. for `ignoredErrors`, `ignoredTransactions` and `ignoredCheckIns`
  - We now simply don't use such strings for Regex matching and only use them for String comparison
- `SentryOptions.setTracePropagationTargets` is no longer marked internal ([#4170](https://github.com/getsentry/sentry-java/pull/4170))
- Session Replay: Fix crash when a navigation breadcrumb does not have "to" destination ([#4185](https://github.com/getsentry/sentry-java/pull/4185))
- Session Replay: Cap video segment duration to maximum 5 minutes to prevent endless video encoding in background ([#4185](https://github.com/getsentry/sentry-java/pull/4185))
- Check `tracePropagationTargets` in OpenTelemetry propagator ([#4191](https://github.com/getsentry/sentry-java/pull/4191))
  - If a URL can be retrieved from OpenTelemetry span attributes, we check it against `tracePropagationTargets` before attaching `sentry-trace` and `baggage` headers to outgoing requests
  - If no URL can be retrieved we always attach the headers
- Fix `ignoredErrors`, `ignoredTransactions` and `ignoredCheckIns` being unset by external options like `sentry.properties` or ENV vars ([#4207](https://github.com/getsentry/sentry-java/pull/4207))
  - Whenever parsing of external options was enabled (`enableExternalConfiguration`), which is the default for many integrations, the values set on `SentryOptions` passed to `Sentry.init` would be lost
  - Even if the value was not set in any external configuration it would still be set to an empty list

### Behavioural Changes

- The class `io.sentry.spring.jakarta.webflux.ReactorUtils` is now deprecated, please use `io.sentry.reactor.SentryReactorUtils` in the new `sentry-reactor` module instead ([#4155](https://github.com/getsentry/sentry-java/pull/4155))
  - The new module will be exposed as an `api` dependency when using `sentry-spring-boot-jakarta` (Spring Boot 3) or `sentry-spring-jakarta` (Spring 6). 
    Therefore, if you're using one of those modules, changing your imports will suffice.

## 8.2.0

### Breaking Changes

- The Kotlin Language version is now set to 1.6 ([#3936](https://github.com/getsentry/sentry-java/pull/3936))

### Features

- Create onCreate and onStart spans for all Activities ([#4025](https://github.com/getsentry/sentry-java/pull/4025))
- Add split apks info to the `App` context ([#3193](https://github.com/getsentry/sentry-java/pull/3193))
- Expose new `withSentryObservableEffect` method overload that accepts `SentryNavigationListener` as a parameter ([#4143](https://github.com/getsentry/sentry-java/pull/4143))
  - This allows sharing the same `SentryNavigationListener` instance across fragments and composables to preserve the trace 
- (Internal) Add API to filter native debug images based on stacktrace addresses ([#4089](https://github.com/getsentry/sentry-java/pull/4089))
- Propagate sampling random value ([#4153](https://github.com/getsentry/sentry-java/pull/4153))
  - The random value used for sampling traces is now sent to Sentry and attached to the `baggage` header on outgoing requests
- Update `sampleRate` that is sent to Sentry and attached to the `baggage` header on outgoing requests ([#4158](https://github.com/getsentry/sentry-java/pull/4158))
  - If the SDK uses its `sampleRate` or `tracesSampler` callback, it now updates the `sampleRate` in Dynamic Sampling Context.

### Fixes

- Log a warning when envelope or items are dropped due to rate limiting ([#4148](https://github.com/getsentry/sentry-java/pull/4148))
- Do not log if `OtelContextScopesStorage` cannot be found ([#4127](https://github.com/getsentry/sentry-java/pull/4127))
  - Previously `java.lang.ClassNotFoundException: io.sentry.opentelemetry.OtelContextScopesStorage` was shown in the log if the class could not be found.
  - This is just a lookup the SDK performs to configure itself. The SDK also works without OpenTelemetry.
- Session Replay: Fix various crashes and issues ([#4135](https://github.com/getsentry/sentry-java/pull/4135))
  - Fix `FileNotFoundException` when trying to read/write `.ongoing_segment` file
  - Fix `IllegalStateException` when registering `onDrawListener`
  - Fix SIGABRT native crashes on Motorola devices when encoding a video
- Mention javadoc and sources for published artifacts in Gradle `.module` metadata ([#3936](https://github.com/getsentry/sentry-java/pull/3936))
- (Jetpack Compose) Modifier.sentryTag now uses Modifier.Node ([#4029](https://github.com/getsentry/sentry-java/pull/4029))
  - This allows Composables that use this modifier to be skippable

### Dependencies

- Bump Native SDK from v0.7.19 to v0.7.20 ([#4128](https://github.com/getsentry/sentry-java/pull/4128))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#0720)
  - [diff](https://github.com/getsentry/sentry-native/compare/v0.7.19...0.7.20)
- Bump Gradle from v8.9.0 to v8.12.1 ([#4106](https://github.com/getsentry/sentry-java/pull/4106))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v8121)
  - [diff](https://github.com/gradle/gradle/compare/v8.9.0...v8.12.1)

## 8.1.0

### Features

- Add `options.ignoredErrors` to filter out errors that match a certain String or Regex ([#4083](https://github.com/getsentry/sentry-java/pull/4083))
  - The matching is attempted on `event.message`, `event.formatted`, and `{event.throwable.class.name}: {event.throwable.message}`
  - Can be set in `sentry.properties`, e.g. `ignored-errors=Some error,Another .*`
  - Can be set in environment variables, e.g. `SENTRY_IGNORED_ERRORS=Some error,Another .*`
  - For Spring Boot, it can be set in `application.properties`, e.g. `sentry.ignored-errors=Some error,Another .*`
- Log OpenTelemetry related Sentry config ([#4122](https://github.com/getsentry/sentry-java/pull/4122))

### Fixes

- Avoid logging an error when a float is passed in the manifest ([#4031](https://github.com/getsentry/sentry-java/pull/4031))
- Add `request` details to transactions created through OpenTelemetry ([#4098](https://github.com/getsentry/sentry-java/pull/4098))
  - We now add HTTP request method and URL where Sentry expects it to display it in Sentry UI
- Remove `java.lang.ClassNotFoundException` debug logs when searching for OpenTelemetry marker classes ([#4091](https://github.com/getsentry/sentry-java/pull/4091))
  - There was up to three of these, one for `io.sentry.opentelemetry.agent.AgentMarker`, `io.sentry.opentelemetry.agent.AgentlessMarker` and `io.sentry.opentelemetry.agent.AgentlessSpringMarker`.
  - These were not indicators of something being wrong but rather the SDK looking at what is available at runtime to configure itself accordingly.
- Do not instrument File I/O operations if tracing is disabled ([#4051](https://github.com/getsentry/sentry-java/pull/4051))
- Do not instrument User Interaction multiple times ([#4051](https://github.com/getsentry/sentry-java/pull/4051))
- Speed up view traversal to find touched target in `UserInteractionIntegration` ([#4051](https://github.com/getsentry/sentry-java/pull/4051))
- Reduce IPC/Binder calls performed by the SDK ([#4058](https://github.com/getsentry/sentry-java/pull/4058))

### Behavioural Changes

- Reduce the number of broadcasts the SDK is subscribed for ([#4052](https://github.com/getsentry/sentry-java/pull/4052))
  - Drop `TempSensorBreadcrumbsIntegration`
  - Drop `PhoneStateBreadcrumbsIntegration`
  - Reduce number of broadcasts in `SystemEventsBreadcrumbsIntegration`

Current list of the broadcast events can be found [here](https://github.com/getsentry/sentry-java/blob/9b8dc0a844d10b55ddeddf55d278c0ab0f86421c/sentry-android-core/src/main/java/io/sentry/android/core/SystemEventsBreadcrumbsIntegration.java#L131-L153). If you'd like to subscribe for more events, consider overriding the `SystemEventsBreadcrumbsIntegration` as follows:

```kotlin
SentryAndroid.init(context) { options ->
    options.integrations.removeAll { it is SystemEventsBreadcrumbsIntegration }
    options.integrations.add(SystemEventsBreadcrumbsIntegration(context, SystemEventsBreadcrumbsIntegration.getDefaultActions() + listOf(/* your custom actions */)))
}
```

If you would like to keep some of the default broadcast events as breadcrumbs, consider opening a [GitHub issue](https://github.com/getsentry/sentry-java/issues/new).
- Set mechanism `type` to `suppressed` for suppressed exceptions ([#4125](https://github.com/getsentry/sentry-java/pull/4125))
  - This helps to distinguish an exceptions cause from any suppressed exceptions in the Sentry UI

### Dependencies

- Bump Spring Boot to `3.4.2` ([#4081](https://github.com/getsentry/sentry-java/pull/4081))
- Bump Native SDK from v0.7.14 to v0.7.19 ([#4076](https://github.com/getsentry/sentry-java/pull/4076))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#0719)
  - [diff](https://github.com/getsentry/sentry-native/compare/v0.7.14...0.7.19)

## 8.0.0

### Summary

Version 8 of the Sentry Android/Java SDK brings a variety of features and fixes. The most notable changes are:

- `Hub` has been replaced by `Scopes`
- New `Scope` types have been introduced, see "Behavioural Changes" for more details.
- Lifecycle tokens have been introduced to manage `Scope` lifecycle, see "Behavioural Changes" for more details.
- Bumping `minSdk` level to 21 (Android 5.0)
- Our `sentry-opentelemetry-agent` has been improved and now works in combination with the rest of Sentry. You may now combine OpenTelemetry and Sentry for instrumenting your application.
    - You may now use both OpenTelemetry SDK and Sentry SDK to capture transactions and spans. They can also be mixed and end up on the same transaction.
    - OpenTelemetry extends the Sentry SDK by adding spans for numerous integrations, like Ktor, Vert.x and MongoDB. Please check [the OpenTelemetry GitHub repository](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation) for a full list.
    - OpenTelemetry allows propagating trace information from and to additional libraries, that Sentry did not support before, for example gRPC.
    - OpenTelemetry also has broader support for propagating the Sentry `Scopes` through reactive libraries like RxJava.
- The SDK is now compatible with Spring Boot 3.4
- We now support GraphQL v22 (`sentry-graphql-22`)
- Metrics have been removed

Please take a look at [our migration guide in docs](https://docs.sentry.io/platforms/java/migration/7.x-to-8.0).

### Sentry Self-hosted Compatibility

This SDK version is compatible with a self-hosted version of Sentry `22.12.0` or higher. If you are using an older version of [self-hosted Sentry](https://develop.sentry.dev/self-hosted/) (aka onpremise), you will need to [upgrade](https://develop.sentry.dev/self-hosted/releases/). If you're using `sentry.io` no action is required.

### Breaking Changes

- The Android minSdk level for all Android modules is now 21 ([#3852](https://github.com/getsentry/sentry-java/pull/3852))
- The minSdk level for sentry-android-ndk changed from 19 to 21 ([#3851](https://github.com/getsentry/sentry-java/pull/3851))
- Throw IllegalArgumentException when calling Sentry.init on Android ([#3596](https://github.com/getsentry/sentry-java/pull/3596))
- Metrics have been removed from the SDK ([#3774](https://github.com/getsentry/sentry-java/pull/3774))
    - Metrics will return but we don't know in what exact form yet
- `enableTracing` option (a.k.a `enable-tracing`) has been removed from the SDK ([#3776](https://github.com/getsentry/sentry-java/pull/3776))
    - Please set `tracesSampleRate` to a value >= 0.0 for enabling performance instead. The default value is `null` which means performance is disabled.
- Replace `synchronized` methods and blocks with `ReentrantLock` (`AutoClosableReentrantLock`) ([#3715](https://github.com/getsentry/sentry-java/pull/3715))
    - If you are subclassing any Sentry classes, please check if the parent class used `synchronized` before. Please make sure to use the same lock object as the parent class in that case.
- `traceOrigins` option (`io.sentry.traces.tracing-origins` in manifest) has been removed, please use `tracePropagationTargets` (`io.sentry.traces.trace-propagation-targets` in manifest`) instead ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `profilingEnabled` option (`io.sentry.traces.profiling.enable` in manifest) has been removed, please use `profilesSampleRate` (`io.sentry.traces.profiling.sample-rate` instead) instead ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `shutdownTimeout` option has been removed, please use `shutdownTimeoutMillis` instead ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `profilingTracesIntervalMillis` option for Android has been removed ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `io.sentry.session-tracking.enable` manifest option has been removed ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `Sentry.traceHeaders()` method has been removed, please use `Sentry.getTraceparent()` instead ([#3718](https://github.com/getsentry/sentry-java/pull/3718))
- `Sentry.reportFullDisplayed()` method has been removed, please use `Sentry.reportFullyDisplayed()` instead ([#3717](https://github.com/getsentry/sentry-java/pull/3717))
- `User.other` has been removed, please use `data` instead ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `SdkVersion.getIntegrations()` has been removed, please use `getIntegrationSet` instead ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `SdkVersion.getPackages()` has been removed, please use `getPackageSet()` instead ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `Device.language` has been removed, please use `locale` instead ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `TraceContext.user` and `TraceContextUser` class have been removed, please use `userId` on `TraceContext` instead ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `TransactionContext.fromSentryTrace()` has been removed, please use `Sentry.continueTrace()` instead ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `SentryDataFetcherExceptionHandler` has been removed, please use `SentryGenericDataFetcherExceptionHandler` in combination with `SentryInstrumentation` instead ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- `sentry-android-okhttp` has been removed in favor of `sentry-okhttp`, removing android dependency from the module ([#3510](https://github.com/getsentry/sentry-java/pull/3510))
- `Contexts` no longer extends `ConcurrentHashMap`, instead we offer a selected set of methods.
- User segment has been removed ([#3512](https://github.com/getsentry/sentry-java/pull/3512))
- One of the `AndroidTransactionProfiler` constructors has been removed, please use a different one ([#3780](https://github.com/getsentry/sentry-java/pull/3780))
- Use String instead of UUID for SessionId ([#3834](https://github.com/getsentry/sentry-java/pull/3834))
    - The `Session` constructor now takes a `String` instead of a `UUID` for the `sessionId` parameter.
    - `Session.getSessionId()` now returns a `String` instead of a `UUID`.
- All status codes below 400 are now mapped to `SpanStatus.OK` ([#3869](https://github.com/getsentry/sentry-java/pull/3869))
- Change OkHttp sub-spans to span attributes ([#3556](https://github.com/getsentry/sentry-java/pull/3556))
    - This will reduce the number of spans created by the SDK
- `instrumenter` option should no longer be needed as our new OpenTelemetry integration now works in combination with the rest of Sentry

### Behavioural Changes

- We're introducing some new `Scope` types in the SDK, allowing for better control over what data is attached where. Previously there was a stack of scopes that was pushed and popped. Instead we now fork scopes for a given lifecycle and then restore the previous scopes. Since `Hub` is gone, it is also never cloned anymore. Separation of data now happens through the different scope types while making it easier to manipulate exactly what you need without having to attach data at the right time to have it apply where wanted.
    - Global scope is attached to all events created by the SDK. It can also be modified before `Sentry.init` has been called. It can be manipulated using `Sentry.configureScope(ScopeType.GLOBAL, (scope) -> { ... })`.
    - Isolation scope can be used e.g. to attach data to all events that come up while handling an incoming request. It can also be used for other isolation purposes. It can be manipulated using `Sentry.configureScope(ScopeType.ISOLATION, (scope) -> { ... })`. The SDK automatically forks isolation scope in certain cases like incoming requests, CRON jobs, Spring `@Async` and more.
    - Current scope is forked often and data added to it is only added to events that are created while this scope is active. Data is also passed on to newly forked child scopes but not to parents. It can be manipulated using `Sentry.configureScope(ScopeType.CURRENT, (scope) -> { ... })`.
- `Sentry.popScope` has been deprecated, please call `.close()` on the token returned by `Sentry.pushScope` instead or use it in a way described in more detail in [our migration guide](https://docs.sentry.io/platforms/java/migration/7.x-to-8.0).
- We have chosen a default scope that is used for `Sentry.configureScope()` as well as API like `Sentry.setTag()`
    - For Android the type defaults to `CURRENT` scope
    - For Backend and other JVM applicatons it defaults to `ISOLATION` scope
- Event processors on `Scope` can now be ordered by overriding the `getOrder` method on implementations of `EventProcessor`. NOTE: This order only applies to event processors on `Scope` but not `SentryOptions` at the moment. Feel free to request this if you need it.
- `Hub` is deprecated in favor of `Scopes`, alongside some `Hub` relevant APIs. More details can be found in [our migration guide](https://docs.sentry.io/platforms/java/migration/7.x-to-8.0).
- Send file name and path only if `isSendDefaultPii` is `true` ([#3919](https://github.com/getsentry/sentry-java/pull/3919))
- (Android) Enable Performance V2 by default ([#3824](https://github.com/getsentry/sentry-java/pull/3824))
    - With this change cold app start spans will include spans for ContentProviders, Application and Activity load.
- (Android) Replace thread id with kernel thread id in span data ([#3706](https://github.com/getsentry/sentry-java/pull/3706))
- (Android) The JNI layer for sentry-native has now been moved from sentry-java to sentry-native ([#3189](https://github.com/getsentry/sentry-java/pull/3189))
    - This now includes prefab support for sentry-native, allowing you to link and access the sentry-native API within your native app code
    - Checkout the `sentry-samples/sentry-samples-android` example on how to configure CMake and consume `sentry.h`
- The user ip-address is now only set to `"{{auto}}"` if `sendDefaultPii` is enabled ([#4072](https://github.com/getsentry/sentry-java/pull/4072))
  - This change gives you control over IP address collection directly on the client

### Features

- The SDK is now compatible with Spring Boot 3.4 ([#3939](https://github.com/getsentry/sentry-java/pull/3939))
- Our `sentry-opentelemetry-agent` has been completely reworked and now plays nicely with the rest of the Java SDK
    - You may also want to give this new agent a try even if you haven't used OpenTelemetry (with Sentry) before. It offers support for [many more libraries and frameworks](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md), improving on our trace propagation, `Scopes` (used to be `Hub`) propagation as well as performance instrumentation (i.e. more spans).
    - If you are using a framework we did not support before and currently resort to manual instrumentation, please give the agent a try. See [here for a list of supported libraries, frameworks and application servers](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md).
    - Please see [Java SDK docs](https://docs.sentry.io/platforms/java/tracing/instrumentation/opentelemetry/) for more details on how to set up the agent. Please make sure to select the correct SDK from the dropdown on the left side of the docs.
    - What's new about the Agent
        - When the OpenTelemetry Agent is used, Sentry API creates OpenTelemetry spans under the hood, handing back a wrapper object which bridges the gap between traditional Sentry API and OpenTelemetry. We might be replacing some of the Sentry performance API in the future.
            - This is achieved by configuring the SDK to use `OtelSpanFactory` instead of `DefaultSpanFactory` which is done automatically by the auto init of the Java Agent.
        - OpenTelemetry spans are now only turned into Sentry spans when they are finished so they can be sent to the Sentry server.
        - Now registers an OpenTelemetry `Sampler` which uses Sentry sampling configuration
        - Other Performance integrations automatically stop creating spans to avoid duplicate spans
        - The Sentry SDK now makes use of OpenTelemetry `Context` for storing Sentry `Scopes` (which is similar to what used to be called `Hub`) and thus relies on OpenTelemetry for `Context` propagation.
        - Classes used for the previous version of our OpenTelemetry support have been deprecated but can still be used manually. We're not planning to keep the old agent around in favor of less complexity in the SDK.
- Add `sentry-opentelemetry-agentless-spring` module ([#4000](https://github.com/getsentry/sentry-java/pull/4000))
    - This module can be added as a dependency when using Sentry with OpenTelemetry and Spring Boot but don't want to use our Agent. It takes care of configuring OpenTelemetry for use with Sentry.
    - You may want to set `OTEL_LOGS_EXPORTER=none;OTEL_METRICS_EXPORTER=none;OTEL_TRACES_EXPORTER=none` env vars to not have the log flooded with error messages regarding OpenTelemetry features we don't use.
- Add `sentry-opentelemetry-agentless` module ([#3961](https://github.com/getsentry/sentry-java/pull/3961))
    - This module can be added as a dependency when using Sentry with OpenTelemetry but don't want to use our Agent. It takes care of configuring OpenTelemetry for use with Sentry.
    - To enable the auto configuration of it, please set `-Dotel.java.global-autoconfigure.enabled=true` on the `java` command, when starting your application.
    - You may also want to set `OTEL_LOGS_EXPORTER=none;OTEL_METRICS_EXPORTER=none;OTEL_TRACES_EXPORTER=none` env vars to not have the log flooded with error messages regarding OpenTelemetry features we don't use.
- `OpenTelemetryUtil.applyOpenTelemetryOptions` now takes an enum instead of a boolean for its mode
- Add `openTelemetryMode` option ([#3994](https://github.com/getsentry/sentry-java/pull/3994))
    - It defaults to `AUTO` meaning the SDK will figure out how to best configure itself for use with OpenTelemetry
    - Use of OpenTelemetry can also be disabled completely by setting it to `OFF` ([#3995](https://github.com/getsentry/sentry-java/pull/3995))
        - In this case even if OpenTelemetry is present, the Sentry SDK will not use it
    - Use `AGENT` when using `sentry-opentelemetry-agent`
    - Use `AGENTLESS` when using `sentry-opentelemetry-agentless`
    - Use `AGENTLESS_SPRING` when using `sentry-opentelemetry-agentless-spring`
- Add `ignoredTransactions` option to filter out transactions by name ([#3871](https://github.com/getsentry/sentry-java/pull/3871))
    - can be used via ENV vars, e.g. `SENTRY_IGNORED_TRANSACTIONS=POST /person/,GET /pers.*`
    - can also be set in options directly, e.g. `options.setIgnoredTransactions(...)`
    - can also be set in `sentry.properties`, e.g. `ignored-transactions=POST /person/,GET /pers.*`
    - can also be set in Spring config `application.properties`, e.g. `sentry.ignored-transactions=POST /person/,GET /pers.*`
- Add `scopeBindingMode` to `SpanOptions` ([#4004](https://github.com/getsentry/sentry-java/pull/4004))
    - This setting only affects the SDK when used with OpenTelemetry.
    - Defaults to `AUTO` meaning the SDK will decide whether the span should be bound to the current scope. It will not bind transactions to scope using `AUTO`, it will only bind spans where the parent span is on the current scope.
    - `ON` sets the new span on the current scope.
    - `OFF` does not set the new span on the scope.
- Add `ignoredSpanOrigins` option for ignoring spans coming from certain integrations
    - We pre-configure this to ignore Performance instrumentation for Spring and other integrations when using our OpenTelemetry Agent to avoid duplicate spans
- Support `graphql-java` v22 via a new module `sentry-graphql-22` ([#3740](https://github.com/getsentry/sentry-java/pull/3740))
    - If you are using `graphql-java` v21 or earlier, you can use the `sentry-graphql` module
    - For `graphql-java` v22 and newer please use the `sentry-graphql-22` module
- We now provide a `SentryInstrumenter` bean directly for Spring (Boot) if there is none yet instead of using `GraphQlSourceBuilderCustomizer` to add the instrumentation ([#3744](https://github.com/getsentry/sentry-java/pull/3744))
    - It is now also possible to provide a bean of type `SentryGraphqlInstrumentation.BeforeSpanCallback` which is then used by `SentryInstrumenter`
- Add data fetching environment hint to breadcrumb for GraphQL (#3413) ([#3431](https://github.com/getsentry/sentry-java/pull/3431))
- Report exceptions returned by Throwable.getSuppressed() to Sentry as exception groups ([#3396] https://github.com/getsentry/sentry-java/pull/3396)
  - Any suppressed exceptions are added to the issue details page in Sentry, the same way any cause is.
  - We are planning to improve how we visualize suppressed exceptions. See https://github.com/getsentry/sentry-java/issues/4059
- Enable `ThreadLocalAccessor` for Spring Boot 3 WebFlux by default ([#4023](https://github.com/getsentry/sentry-java/pull/4023))
- Allow passing `environment` to `CheckinUtils.withCheckIn` ([3889](https://github.com/getsentry/sentry-java/pull/3889))
- Add `globalHubMode` to options ([#3805](https://github.com/getsentry/sentry-java/pull/3805))
    - `globalHubMode` used to only be a param on `Sentry.init`. To make it easier to be used in e.g. Desktop environments, we now additionally added it as an option on SentryOptions that can also be set via `sentry.properties`.
    - If both the param on `Sentry.init` and the option are set, the option will win. By default the option is set to `null` meaning whatever is passed to `Sentry.init` takes effect.
- Lazy uuid generation for SentryId and SpanId ([#3770](https://github.com/getsentry/sentry-java/pull/3770))
- Faster generation of Sentry and Span IDs ([#3818](https://github.com/getsentry/sentry-java/pull/3818))
    - Uses faster implementation to convert UUID to SentryID String
    - Uses faster Random implementation to generate UUIDs
- Android 15: Add support for 16KB page sizes ([#3851](https://github.com/getsentry/sentry-java/pull/3851))
    - See https://developer.android.com/guide/practices/page-sizes for more details
- Add init priority settings ([#3674](https://github.com/getsentry/sentry-java/pull/3674))
    - You may now set `forceInit=true` (`force-init` for `.properties` files) to ensure a call to Sentry.init / SentryAndroid.init takes effect
- Add force init option to Android Manifest ([#3675](https://github.com/getsentry/sentry-java/pull/3675))
    - Use `<meta-data android:name="io.sentry.force-init" android:value="true" />` to ensure Sentry Android auto init is not easily overwritten
- Attach request body for `application/x-www-form-urlencoded` requests in Spring ([#3731](https://github.com/getsentry/sentry-java/pull/3731))
    - Previously request body was only attached for `application/json` requests
- Set breadcrumb level based on http status ([#3771](https://github.com/getsentry/sentry-java/pull/3771))
- Emit transaction.data inside contexts.trace.data ([#3735](https://github.com/getsentry/sentry-java/pull/3735))
    - Also does not emit `transaction.data` in `extras` anymore
- Add a sample for showcasing Sentry with OpenTelemetry for Spring Boot 3 with our Java agent (`sentry-samples-spring-boot-jakarta-opentelemetry`) ([#3856](https://github.com/getsentry/sentry-java/pull/3828))
- Add a sample for showcasing Sentry with OpenTelemetry for Spring Boot 3 without our Java agent (`sentry-samples-spring-boot-jakarta-opentelemetry-noagent`) ([#3856](https://github.com/getsentry/sentry-java/pull/3856))
- Add a sample for showcasing Sentry with OpenTelemetry (`sentry-samples-console-opentelemetry-noagent`) ([#3856](https://github.com/getsentry/sentry-java/pull/3862))

### Fixes

- Fix incoming defer sampling decision `sentry-trace` header ([#3942](https://github.com/getsentry/sentry-java/pull/3942))
    - A `sentry-trace` header that only contains trace ID and span ID but no sampled flag (`-1`, `-0` suffix) means the receiving system can make its own sampling decision
    - When generating `sentry-trace` header from `PropagationContext` we now copy the `sampled` flag.
    - In `TransactionContext.fromPropagationContext` when there is no parent sampling decision, keep the decision `null` so a new sampling decision is made instead of defaulting to `false`
- Fix order of calling `close` on previous Sentry instance when re-initializing ([#3750](https://github.com/getsentry/sentry-java/pull/3750))
    - Previously some parts of Sentry were immediately closed after re-init that should have stayed open and some parts of the previous init were never closed
- All status codes below 400 are now mapped to `SpanStatus.OK` ([#3869](https://github.com/getsentry/sentry-java/pull/3869))
- Improve ignored check performance ([#3992](https://github.com/getsentry/sentry-java/pull/3992))
    - Checking if a span origin, a transaction or a checkIn should be ignored is now faster
- Cache requests for Spring using Springs `ContentCachingRequestWrapper` instead of our own Wrapper to also cache parameters ([#3641](https://github.com/getsentry/sentry-java/pull/3641))
    - Previously only the body was cached which could lead to problems in the FilterChain as Request parameters were not available
- Close backpressure monitor on SDK shutdown ([#3998](https://github.com/getsentry/sentry-java/pull/3998))
    - Due to the backpressure monitor rescheduling a task to run every 10s, it very likely caused shutdown to wait the full `shutdownTimeoutMillis` (defaulting to 2s) instead of being able to terminate immediately
- Let OpenTelemetry auto instrumentation handle extracting and injecting tracing information if present ([#3953](https://github.com/getsentry/sentry-java/pull/3953))
    - Our integrations no longer call `.continueTrace` and also do not inject tracing headers if the integration has been added to `ignoredSpanOrigins`
- Fix testTag not working for Jetpack Compose user interaction tracking ([#3878](https://github.com/getsentry/sentry-java/pull/3878))
- Mark `DiskFlushNotification` hint flushed when rate limited ([#3892](https://github.com/getsentry/sentry-java/pull/3892))
    - Our `UncaughtExceptionHandlerIntegration` waited for the full flush timeout duration (default 15s) when rate limited.
- Do not replace `op` with auto generated content for OpenTelemetry spans with span kind `INTERNAL` ([#3906](https://github.com/getsentry/sentry-java/pull/3906))
- Add `enable-spotlight` and `spotlight-connection-url` to external options and check if spotlight is enabled when deciding whether to inspect an OpenTelemetry span for connecting to splotlight ([#3709](https://github.com/getsentry/sentry-java/pull/3709))
- Trace context on `Contexts.setTrace` has been marked `@NotNull` ([#3721](https://github.com/getsentry/sentry-java/pull/3721))
    - Setting it to `null` would cause an exception.
    - Transactions are dropped if trace context is missing
- Remove internal annotation on `SpanOptions` ([#3722](https://github.com/getsentry/sentry-java/pull/3722))
- `SentryLogbackInitializer` is now public ([#3723](https://github.com/getsentry/sentry-java/pull/3723))
- Parse and use `send-default-pii` and `max-request-body-size` from `sentry.properties` ([#3534](https://github.com/getsentry/sentry-java/pull/3534))
- `TracesSampler` is now only created once in `SentryOptions` instead of creating a new one for every `Hub` (which is now `Scopes`). This means we're now creating fewer `SecureRandom` instances.

### Internal

- Make `SentryClient` constructor public ([#4045](https://github.com/getsentry/sentry-java/pull/4045))
- Warm starts cleanup ([#3954](https://github.com/getsentry/sentry-java/pull/3954))

### Changes in pre-releases

These changes have been made during development of `8.0.0`. You may skip this section. We just put it here for sake of completeness.

- Extract OpenTelemetry `URL_PATH` span attribute into description ([#3933](https://github.com/getsentry/sentry-java/pull/3933))
- Replace OpenTelemetry `ContextStorage` wrapper with `ContextStorageProvider` ([#3938](https://github.com/getsentry/sentry-java/pull/3938))
    - The wrapper had to be put in place before any call to `Context` whereas `ContextStorageProvider` is automatically invoked at the correct time.
- Send `otel.kind` to Sentry ([#3907](https://github.com/getsentry/sentry-java/pull/3907))
- Spring Boot now automatically detects if OpenTelemetry is available and makes use of it ([#3846](https://github.com/getsentry/sentry-java/pull/3846))
    - This is only enabled if there is no OpenTelemetry agent available
    - We prefer to use the OpenTelemetry agent as it offers more auto instrumentation
    - In some cases the OpenTelemetry agent cannot be used, please see https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/ for more details on when to prefer the Agent and when the Spring Boot starter makes more sense.
    - In this mode the SDK makes use of the `OpenTelemetry` bean that is created by `opentelemetry-spring-boot-starter` instead of `GlobalOpenTelemetry`
- Spring Boot now automatically detects our OpenTelemetry agent if its auto init is disabled ([#3848](https://github.com/getsentry/sentry-java/pull/3848))
    - This means Spring Boot config mechanisms can now be combined with our OpenTelemetry agent
    - The `sentry-opentelemetry-extra` module has been removed again, most classes have been moved to `sentry-opentelemetry-bootstrap` which is loaded into the bootstrap classloader (i.e. `null`) when our Java agent is used. The rest has been moved into `sentry-opentelemetry-agentcustomization` and is loaded into the agent classloader when our Java agent is used.
    - The `sentry-opentelemetry-bootstrap` and `sentry-opentelemetry-agentcustomization` modules can be used without the agent as well, in which case all classes are loaded into the application classloader. Check out our `sentry-samples-spring-boot-jakarta-opentelemetry-noagent` sample.
    - In this mode the SDK makes use of `GlobalOpenTelemetry`
- Automatically set span factory based on presence of OpenTelemetry ([#3858](https://github.com/getsentry/sentry-java/pull/3858))
    - `SentrySpanFactoryHolder` has been removed as it is no longer required.

- Replace deprecated `SimpleInstrumentation` with `SimplePerformantInstrumentation` for graphql 22 ([#3974](https://github.com/getsentry/sentry-java/pull/3974))
- We now hold a strong reference to the underlying OpenTelemetry span when it is created through Sentry API ([#3997](https://github.com/getsentry/sentry-java/pull/3997))
    - This keeps it from being garbage collected too early
- Defer sampling decision by setting `sampled` to `null` in `PropagationContext` when using OpenTelemetry in case of an incoming defer sampling `sentry-trace` header. ([#3945](https://github.com/getsentry/sentry-java/pull/3945))
- Build `PropagationContext` from `SamplingDecision` made by `SentrySampler` instead of parsing headers and potentially ignoring a sampling decision in case a `sentry-trace` header comes in with deferred sampling decision. ([#3947](https://github.com/getsentry/sentry-java/pull/3947))
- The Sentry OpenTelemetry Java agent now makes sure Sentry `Scopes` storage is initialized even if the agents auto init is disabled ([#3848](https://github.com/getsentry/sentry-java/pull/3848))
    - This is required for all integrations to work together with our OpenTelemetry Java agent if its auto init has been disabled and the SDKs init should be used instead.
- Fix `startChild` for span that is not in current OpenTelemetry `Context` ([#3862](https://github.com/getsentry/sentry-java/pull/3862))
    - Starting a child span from a transaction that wasn't in the current `Context` lead to multiple transactions being created (one for the transaction and another per span created).
- Add `auto.graphql.graphql22` to ignored span origins when using OpenTelemetry ([#3828](https://github.com/getsentry/sentry-java/pull/3828))
- Use OpenTelemetry span name as fallback for transaction name ([#3557](https://github.com/getsentry/sentry-java/pull/3557))
    - In certain cases we were sending transactions as "<unlabeled transaction>" when using OpenTelemetry
- Add OpenTelemetry span data to Sentry span ([#3593](https://github.com/getsentry/sentry-java/pull/3593))
- No longer selectively copy OpenTelemetry attributes to Sentry spans / transactions `data` ([#3663](https://github.com/getsentry/sentry-java/pull/3663))
- Remove `PROCESS_COMMAND_ARGS` (`process.command_args`) OpenTelemetry span attribute as it can be very large ([#3664](https://github.com/getsentry/sentry-java/pull/3664))
- Use RECORD_ONLY sampling decision if performance is disabled ([#3659](https://github.com/getsentry/sentry-java/pull/3659))
    - Also fix check whether Performance is enabled when making a sampling decision in the OpenTelemetry sampler
- Sentry OpenTelemetry Java Agent now sets Instrumenter to SENTRY (used to be OTEL) ([#3697](https://github.com/getsentry/sentry-java/pull/3697))
- Set span origin in `ActivityLifecycleIntegration` on span options instead of after creating the span / transaction ([#3702](https://github.com/getsentry/sentry-java/pull/3702))
    - This allows spans to be filtered by span origin on creation
- Honor ignored span origins in `SentryTracer.startChild` ([#3704](https://github.com/getsentry/sentry-java/pull/3704))
- Use span id of remote parent ([#3548](https://github.com/getsentry/sentry-java/pull/3548))
    - Traces were broken because on an incoming request, OtelSentrySpanProcessor did not set the parentSpanId on the span correctly. Traces were not referencing the actual parent span but some other (random) span ID which the server doesn't know.
- Attach active span to scope when using OpenTelemetry ([#3549](https://github.com/getsentry/sentry-java/pull/3549))
    - Errors weren't linked to traces correctly due to parts of the SDK not knowing the current span
- Record dropped spans in client report when sampling out OpenTelemetry spans ([#3552](https://github.com/getsentry/sentry-java/pull/3552))
- Retrieve the correct current span from `Scope`/`Scopes` when using OpenTelemetry ([#3554](https://github.com/getsentry/sentry-java/pull/3554))
- Support spans that are split into multiple batches ([#3539](https://github.com/getsentry/sentry-java/pull/3539))
    - When spans belonging to a single transaction were split into multiple batches for SpanExporter, we did not add all spans because the isSpanTooOld check wasn't inverted.
- Partially fix bootstrap class loading ([#3543](https://github.com/getsentry/sentry-java/pull/3543))
    - There was a problem with two separate Sentry `Scopes` being active inside each OpenTelemetry `Context` due to using context keys from more than one class loader.
- The Spring Boot 3 WebFlux sample now uses our GraphQL v22 integration ([#3828](https://github.com/getsentry/sentry-java/pull/3828))
- Do not ignore certain span origins for OpenTelemetry without agent ([#3856](https://github.com/getsentry/sentry-java/pull/3856))
- `span.startChild` now uses `.makeCurrent()` by default ([#3544](https://github.com/getsentry/sentry-java/pull/3544))
    - This caused an issue where the span tree wasn't correct because some spans were not added to their direct parent
- Do not set the exception group marker when there is a suppressed exception ([#4056](https://github.com/getsentry/sentry-java/pull/4056))
    - Due to how grouping works in Sentry currently sometimes the suppressed exception is treated as the main exception. This change ensures we keep using the main exception and not change how grouping works.
    - As a consequence the list of exceptions in the group on top of an issue is no longer shown in Sentry UI.
    - We are planning to improve this in the future but opted for this fix first.

### Dependencies

- Bump Native SDK from v0.7.0 to v0.7.17 ([#3441](https://github.com/getsentry/sentry-java/pull/3189)) ([#3851](https://github.com/getsentry/sentry-java/pull/3851)) ([#3914](https://github.com/getsentry/sentry-java/pull/3914)) ([#4003](https://github.com/getsentry/sentry-java/pull/4003))
    - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#0717)
    - [diff](https://github.com/getsentry/sentry-native/compare/0.7.0...0.7.17)
- Bump OpenTelemetry to 1.44.1, OpenTelemetry Java Agent to 2.10.0 and Semantic Conventions to 1.28.0 ([#3668](https://github.com/getsentry/sentry-java/pull/3668)) ([#3935](https://github.com/getsentry/sentry-java/pull/3935))

### Migration Guide / Deprecations

Please take a look at [our migration guide in docs](https://docs.sentry.io/platforms/java/migration/7.x-to-8.0).

- `Hub` has been deprecated, we're replacing the following:
    - `IHub` has been replaced by `IScopes`, however you should be able to simply pass `IHub` instances to code expecting `IScopes`, allowing for an easier migration.
    - `HubAdapter.getInstance()` has been replaced by `ScopesAdapter.getInstance()`
    - The `.clone()` method on `IHub`/`IScopes` has been deprecated, please use `.pushScope()` or `.pushIsolationScope()` instead
    - Some internal methods like `.getCurrentHub()` and `.setCurrentHub()` have also been replaced.
- `Sentry.popScope` has been replaced by calling `.close()` on the token returned by `Sentry.pushScope()` and `Sentry.pushIsolationScope()`. The token can also be used in a `try` block like this:

```
try (final @NotNull ISentryLifecycleToken ignored = Sentry.pushScope()) {
  // this block has its separate current scope
}
```

as well as:


```
try (final @NotNull ISentryLifecycleToken ignored = Sentry.pushIsolationScope()) {
  // this block has its separate isolation scope
}
```
- Classes used by our previous OpenTelemetry integration have been deprecated (`SentrySpanProcessor`, `SentryPropagator`, `OpenTelemetryLinkErrorEventProcessor`). Please take a look at [docs](https://docs.sentry.io/platforms/java/tracing/instrumentation/opentelemetry/) on how to setup OpenTelemetry in v8.

You may also use `LifecycleHelper.close(token)`, e.g. in case you need to pass the token around for closing later.


### Changes from `rc.4`

If you have been using `8.0.0-rc.4` of the Java SDK, here's the new changes that have been included in the `8.0.0` release:

- Make `SentryClient` constructor public ([#4045](https://github.com/getsentry/sentry-java/pull/4045))
- The user ip-address is now only set to `"{{auto}}"` if sendDefaultPii is enabled ([#4072](https://github.com/getsentry/sentry-java/pull/4072))
    - This change gives you control over IP address collection directly on the client
- Do not set the exception group marker when there is a suppressed exception ([#4056](https://github.com/getsentry/sentry-java/pull/4056))
    - Due to how grouping works in Sentry currently sometimes the suppressed exception is treated as the main exception. This change ensures we keep using the main exception and not change how grouping works.
    - As a consequence the list of exceptions in the group on top of an issue is no longer shown in Sentry UI.
    - We are planning to improve this in the future but opted for this fix first.
- Fix swallow NDK loadLibrary errors ([#4082](https://github.com/getsentry/sentry-java/pull/4082))

## 7.22.6

### Fixes

- Compress Screenshots on a background thread ([#4295](https://github.com/getsentry/sentry-java/pull/4295))
- Improve low memory breadcrumb capturing ([#4325](https://github.com/getsentry/sentry-java/pull/4325))
- Make `SystemEventsBreadcrumbsIntegration` faster ([#4330](https://github.com/getsentry/sentry-java/pull/4330))
- Fix unregister `SystemEventsBroadcastReceiver` when entering background ([#4338](https://github.com/getsentry/sentry-java/pull/4338))
    - This should reduce ANRs seen with this class in the stack trace for Android 14 and above
- Pre-load modules on a background thread upon SDK init ([#4348](https://github.com/getsentry/sentry-java/pull/4348))
- Session Replay: Fix inconsistent `segment_id` ([#4471](https://github.com/getsentry/sentry-java/pull/4471))
- Session Replay: Do not capture current replay for cached events from the past ([#4474](https://github.com/getsentry/sentry-java/pull/4474))
- Session Replay: Fix crash on devices with the Unisoc/Spreadtrum T606 chipset ([#4477](https://github.com/getsentry/sentry-java/pull/4477))
- Session Replay: Fix masking of non-styled `Text` Composables ([#4361](https://github.com/getsentry/sentry-java/pull/4361))
- Session Replay: Fix masking read-only `TextField` Composables ([#4362](https://github.com/getsentry/sentry-java/pull/4362))
- Fix Session Replay masking for newer versions of Jetpack Compose (1.8+) ([#4485](https://github.com/getsentry/sentry-java/pull/4485))
- Session Replay: Expand fix for crash on devices to all Unisoc/Spreadtrum chipsets ([#4510](https://github.com/getsentry/sentry-java/pull/4510))

## 7.22.5

### Fixes

- Session Replay: Change bitmap config to `ARGB_8888` for screenshots ([#4282](https://github.com/getsentry/sentry-java/pull/4282))

## 7.22.4

### Fixes

- Session Replay: Fix crash when a navigation breadcrumb does not have "to" destination ([#4185](https://github.com/getsentry/sentry-java/pull/4185))
- Session Replay: Cap video segment duration to maximum 5 minutes to prevent endless video encoding in background ([#4185](https://github.com/getsentry/sentry-java/pull/4185))
- Avoid logging an error when a float is passed in the manifest ([#4266](https://github.com/getsentry/sentry-java/pull/4266))

## 7.22.3

### Fixes

- Reduce excessive CPU usage when serializing breadcrumbs to disk for ANRs ([#4181](https://github.com/getsentry/sentry-java/pull/4181))

## 7.22.2

### Fixes

- Fix AbstractMethodError when using SentryTraced for Jetpack Compose ([#4256](https://github.com/getsentry/sentry-java/pull/4256))

## 7.22.1

### Fixes

- Fix Ensure app start type is set, even when ActivityLifecycleIntegration is not running ([#4216](https://github.com/getsentry/sentry-java/pull/4216))
- Fix properly reset application/content-provider timespans for warm app starts ([#4244](https://github.com/getsentry/sentry-java/pull/4244))

## 7.22.0

### Fixes

- Session Replay: Fix various crashes and issues ([#4135](https://github.com/getsentry/sentry-java/pull/4135))
    - Fix `FileNotFoundException` when trying to read/write `.ongoing_segment` file
    - Fix `IllegalStateException` when registering `onDrawListener`
    - Fix SIGABRT native crashes on Motorola devices when encoding a video
- (Jetpack Compose) Modifier.sentryTag now uses Modifier.Node ([#4029](https://github.com/getsentry/sentry-java/pull/4029))
    - This allows Composables that use this modifier to be skippable

## 7.21.0

### Fixes

- Do not instrument File I/O operations if tracing is disabled ([#4051](https://github.com/getsentry/sentry-java/pull/4051))
- Do not instrument User Interaction multiple times ([#4051](https://github.com/getsentry/sentry-java/pull/4051))
- Speed up view traversal to find touched target in `UserInteractionIntegration` ([#4051](https://github.com/getsentry/sentry-java/pull/4051))
- Reduce IPC/Binder calls performed by the SDK ([#4058](https://github.com/getsentry/sentry-java/pull/4058))

### Behavioural Changes

- (changed in [7.20.1](https://github.com/getsentry/sentry-java/releases/tag/7.20.1)) The user ip-address is now only set to `"{{auto}}"` if sendDefaultPii is enabled ([#4071](https://github.com/getsentry/sentry-java/pull/4071))
    - This change gives you control over IP address collection directly on the client
- Reduce the number of broadcasts the SDK is subscribed for ([#4052](https://github.com/getsentry/sentry-java/pull/4052))
  - Drop `TempSensorBreadcrumbsIntegration`
  - Drop `PhoneStateBreadcrumbsIntegration`
  - Reduce number of broadcasts in `SystemEventsBreadcrumbsIntegration`

Current list of the broadcast events can be found [here](https://github.com/getsentry/sentry-java/blob/9b8dc0a844d10b55ddeddf55d278c0ab0f86421c/sentry-android-core/src/main/java/io/sentry/android/core/SystemEventsBreadcrumbsIntegration.java#L131-L153). If you'd like to subscribe for more events, consider overriding the `SystemEventsBreadcrumbsIntegration` as follows:

```kotlin
SentryAndroid.init(context) { options ->
    options.integrations.removeAll { it is SystemEventsBreadcrumbsIntegration }
    options.integrations.add(SystemEventsBreadcrumbsIntegration(context, SystemEventsBreadcrumbsIntegration.getDefaultActions() + listOf(/* your custom actions */)))
}
```

If you would like to keep some of the default broadcast events as breadcrumbs, consider opening a [GitHub issue](https://github.com/getsentry/sentry-java/issues/new).

## 7.21.0-beta.1

### Fixes

- Do not instrument File I/O operations if tracing is disabled ([#4051](https://github.com/getsentry/sentry-java/pull/4051))
- Do not instrument User Interaction multiple times ([#4051](https://github.com/getsentry/sentry-java/pull/4051))
- Speed up view traversal to find touched target in `UserInteractionIntegration` ([#4051](https://github.com/getsentry/sentry-java/pull/4051))
- Reduce IPC/Binder calls performed by the SDK ([#4058](https://github.com/getsentry/sentry-java/pull/4058))

### Behavioural Changes

- Reduce the number of broadcasts the SDK is subscribed for ([#4052](https://github.com/getsentry/sentry-java/pull/4052))
  - Drop `TempSensorBreadcrumbsIntegration`
  - Drop `PhoneStateBreadcrumbsIntegration`
  - Reduce number of broadcasts in `SystemEventsBreadcrumbsIntegration`

Current list of the broadcast events can be found [here](https://github.com/getsentry/sentry-java/blob/9b8dc0a844d10b55ddeddf55d278c0ab0f86421c/sentry-android-core/src/main/java/io/sentry/android/core/SystemEventsBreadcrumbsIntegration.java#L131-L153). If you'd like to subscribe for more events, consider overriding the `SystemEventsBreadcrumbsIntegration` as follows:

```kotlin
SentryAndroid.init(context) { options ->
    options.integrations.removeAll { it is SystemEventsBreadcrumbsIntegration }
    options.integrations.add(SystemEventsBreadcrumbsIntegration(context, SystemEventsBreadcrumbsIntegration.getDefaultActions() + listOf(/* your custom actions */)))
}
```

If you would like to keep some of the default broadcast events as breadcrumbs, consider opening a [GitHub issue](https://github.com/getsentry/sentry-java/issues/new).

## 7.20.1

### Behavioural Changes

- The user ip-address is now only set to `"{{auto}}"` if sendDefaultPii is enabled ([#4071](https://github.com/getsentry/sentry-java/pull/4071))
    - This change gives you control over IP address collection directly on the client

## 7.20.0

### Features

- Session Replay GA ([#4017](https://github.com/getsentry/sentry-java/pull/4017))

To enable Replay use the `sessionReplay.sessionSampleRate` or `sessionReplay.onErrorSampleRate` options.

  ```kotlin
  import io.sentry.SentryReplayOptions
  import io.sentry.android.core.SentryAndroid

  SentryAndroid.init(context) { options ->
   
    options.sessionReplay.sessionSampleRate = 1.0
    options.sessionReplay.onErrorSampleRate = 1.0
  
    // To change default redaction behavior (defaults to true)
    options.sessionReplay.redactAllImages = true
    options.sessionReplay.redactAllText = true
  
    // To change quality of the recording (defaults to MEDIUM)
    options.sessionReplay.quality = SentryReplayOptions.SentryReplayQuality.MEDIUM // (LOW|MEDIUM|HIGH)
  }
  ```

### Fixes

- Fix warm start detection ([#3937](https://github.com/getsentry/sentry-java/pull/3937))
- Session Replay: Reduce memory allocations, disk space consumption, and payload size ([#4016](https://github.com/getsentry/sentry-java/pull/4016))
- Session Replay: Do not try to encode corrupted frames multiple times ([#4016](https://github.com/getsentry/sentry-java/pull/4016))

### Internal

- Session Replay: Allow overriding `SdkVersion` for replay events ([#4014](https://github.com/getsentry/sentry-java/pull/4014))
- Session Replay: Send replay options as tags ([#4015](https://github.com/getsentry/sentry-java/pull/4015))

### Breaking changes

- Session Replay options were moved from under `experimental` to the main `options` object ([#4017](https://github.com/getsentry/sentry-java/pull/4017))

## 7.19.1

### Fixes

- Change TTFD timeout to 25 seconds ([#3984](https://github.com/getsentry/sentry-java/pull/3984))
- Session Replay: Fix memory leak when masking Compose screens ([#3985](https://github.com/getsentry/sentry-java/pull/3985))
- Session Replay: Fix potential ANRs in `GestureRecorder` ([#4001](https://github.com/getsentry/sentry-java/pull/4001))

### Internal

- Session Replay: Flutter improvements ([#4007](https://github.com/getsentry/sentry-java/pull/4007))

## 7.19.0

### Fixes

- Session Replay: fix various crashes and issues ([#3970](https://github.com/getsentry/sentry-java/pull/3970))
    - Fix `IndexOutOfBoundsException` when tracking window changes
    - Fix `IllegalStateException` when adding/removing draw listener for a dead view
    - Fix `ConcurrentModificationException` when registering window listeners and stopping `WindowRecorder`/`GestureRecorder`
- Add support for setting sentry-native handler_strategy ([#3671](https://github.com/getsentry/sentry-java/pull/3671))

### Dependencies

- Bump Native SDK from v0.7.8 to v0.7.16 ([#3671](https://github.com/getsentry/sentry-java/pull/3671))
    - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#0716)
    - [diff](https://github.com/getsentry/sentry-native/compare/0.7.8...0.7.16)

## 7.18.1

### Fixes

- Fix testTag not working for Jetpack Compose user interaction tracking ([#3878](https://github.com/getsentry/sentry-java/pull/3878))

## 7.18.0

### Features

- Android 15: Add support for 16KB page sizes ([#3620](https://github.com/getsentry/sentry-java/pull/3620))
    - See https://developer.android.com/guide/practices/page-sizes for more details
- Session Replay: Add `beforeSendReplay` callback ([#3855](https://github.com/getsentry/sentry-java/pull/3855))
- Session Replay: Add support for masking/unmasking view containers ([#3881](https://github.com/getsentry/sentry-java/pull/3881))

### Fixes

- Avoid collecting normal frames ([#3782](https://github.com/getsentry/sentry-java/pull/3782))
- Ensure android initialization process continues even if options configuration block throws an exception ([#3887](https://github.com/getsentry/sentry-java/pull/3887))
- Do not report parsing ANR error when there are no threads ([#3888](https://github.com/getsentry/sentry-java/pull/3888))
    - This should significantly reduce the number of events with message "Sentry Android SDK failed to parse system thread dump..." reported
- Session Replay: Disable replay in session mode when rate limit is active ([#3854](https://github.com/getsentry/sentry-java/pull/3854))

### Dependencies

- Bump Native SDK from v0.7.2 to v0.7.8 ([#3620](https://github.com/getsentry/sentry-java/pull/3620))
    - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#078)
    - [diff](https://github.com/getsentry/sentry-native/compare/0.7.2...0.7.8)

## 7.17.0

### Features

- Add meta option to set the maximum amount of breadcrumbs to be logged. ([#3836](https://github.com/getsentry/sentry-java/pull/3836))
- Use a separate `Random` instance per thread to improve SDK performance ([#3835](https://github.com/getsentry/sentry-java/pull/3835))

### Fixes

- Using MaxBreadcrumb with value 0 no longer crashes. ([#3836](https://github.com/getsentry/sentry-java/pull/3836))
- Accept manifest integer values when requiring floating values ([#3823](https://github.com/getsentry/sentry-java/pull/3823))
- Fix standalone tomcat jndi issue ([#3873](https://github.com/getsentry/sentry-java/pull/3873))
    - Using Sentry Spring Boot on a standalone tomcat caused the following error:
        - Failed to bind properties under 'sentry.parsed-dsn' to io.sentry.Dsn

## 7.16.0

### Features

- Add meta option to attach ANR thread dumps ([#3791](https://github.com/getsentry/sentry-java/pull/3791))

### Fixes

- Cache parsed Dsn ([#3796](https://github.com/getsentry/sentry-java/pull/3796))
- fix invalid profiles when the transaction name is empty ([#3747](https://github.com/getsentry/sentry-java/pull/3747))
- Deprecate `enableTracing` option ([#3777](https://github.com/getsentry/sentry-java/pull/3777))
- Vendor `java.util.Random` and replace `java.security.SecureRandom` usages ([#3783](https://github.com/getsentry/sentry-java/pull/3783))
- Fix potential ANRs due to NDK scope sync ([#3754](https://github.com/getsentry/sentry-java/pull/3754))
- Fix potential ANRs due to NDK System.loadLibrary calls ([#3670](https://github.com/getsentry/sentry-java/pull/3670))
- Fix slow `Log` calls on app startup ([#3793](https://github.com/getsentry/sentry-java/pull/3793))
- Fix slow Integration name parsing ([#3794](https://github.com/getsentry/sentry-java/pull/3794))
- Session Replay: Reduce startup and capture overhead ([#3799](https://github.com/getsentry/sentry-java/pull/3799))
- Load lazy fields on init in the background ([#3803](https://github.com/getsentry/sentry-java/pull/3803))
- Replace setOf with HashSet.add ([#3801](https://github.com/getsentry/sentry-java/pull/3801))

### Breaking changes

- The method `addIntegrationToSdkVersion(Ljava/lang/Class;)V` has been removed from the core (`io.sentry:sentry`) package. Please make sure all of the packages (e.g. `io.sentry:sentry-android-core`, `io.sentry:sentry-android-fragment`, `io.sentry:sentry-okhttp`  and others) are all aligned and using the same version to prevent the `NoSuchMethodError` exception.

## 7.16.0-alpha.1

### Features

- Add meta option to attach ANR thread dumps ([#3791](https://github.com/getsentry/sentry-java/pull/3791))

### Fixes

- Cache parsed Dsn ([#3796](https://github.com/getsentry/sentry-java/pull/3796))
- fix invalid profiles when the transaction name is empty ([#3747](https://github.com/getsentry/sentry-java/pull/3747))
- Deprecate `enableTracing` option ([#3777](https://github.com/getsentry/sentry-java/pull/3777))
- Vendor `java.util.Random` and replace `java.security.SecureRandom` usages ([#3783](https://github.com/getsentry/sentry-java/pull/3783))
- Fix potential ANRs due to NDK scope sync ([#3754](https://github.com/getsentry/sentry-java/pull/3754))
- Fix potential ANRs due to NDK System.loadLibrary calls ([#3670](https://github.com/getsentry/sentry-java/pull/3670))
- Fix slow `Log` calls on app startup ([#3793](https://github.com/getsentry/sentry-java/pull/3793))
- Fix slow Integration name parsing ([#3794](https://github.com/getsentry/sentry-java/pull/3794))
- Session Replay: Reduce startup and capture overhead ([#3799](https://github.com/getsentry/sentry-java/pull/3799))

## 7.15.0

### Features

- Add support for `feedback` envelope header item type ([#3687](https://github.com/getsentry/sentry-java/pull/3687))
- Add breadcrumb.origin field ([#3727](https://github.com/getsentry/sentry-java/pull/3727))
- Session Replay: Add options to selectively mask/unmask views captured in replay. The following options are available: ([#3689](https://github.com/getsentry/sentry-java/pull/3689))
    - `android:tag="sentry-mask|sentry-unmask"` in XML or `view.setTag("sentry-mask|sentry-unmask")` in code tags
        - if you already have a tag set for a view, you can set a tag by id: `<tag android:id="@id/sentry_privacy" android:value="mask|unmask"/>` in XML or `view.setTag(io.sentry.android.replay.R.id.sentry_privacy, "mask|unmask")` in code
    - `view.sentryReplayMask()` or `view.sentryReplayUnmask()` extension functions
    - mask/unmask `View`s of a certain type by adding fully-qualified classname to one of the lists `options.experimental.sessionReplay.addMaskViewClass()` or `options.experimental.sessionReplay.addUnmaskViewClass()`. Note, that all of the view subclasses/subtypes will be masked/unmasked as well
        - For example, (this is already a default behavior) to mask all `TextView`s and their subclasses (`RadioButton`, `EditText`, etc.): `options.experimental.sessionReplay.addMaskViewClass("android.widget.TextView")`
        - If you're using code obfuscation, adjust your proguard-rules accordingly, so your custom view class name is not minified
- Session Replay: Support Jetpack Compose masking ([#3739](https://github.com/getsentry/sentry-java/pull/3739))
  - To selectively mask/unmask @Composables, use `Modifier.sentryReplayMask()` and `Modifier.sentryReplayUnmask()` modifiers
- Session Replay: Mask `WebView`, `VideoView` and `androidx.media3.ui.PlayerView` by default ([#3775](https://github.com/getsentry/sentry-java/pull/3775))

### Fixes

- Avoid stopping appStartProfiler after application creation ([#3630](https://github.com/getsentry/sentry-java/pull/3630))
- Session Replay: Correctly detect dominant color for `TextView`s with Spans ([#3682](https://github.com/getsentry/sentry-java/pull/3682))
- Fix ensure Application Context is used even when SDK is initialized via Activity Context ([#3669](https://github.com/getsentry/sentry-java/pull/3669))
- Fix potential ANRs due to `Calendar.getInstance` usage in Breadcrumbs constructor ([#3736](https://github.com/getsentry/sentry-java/pull/3736))
- Fix potential ANRs due to default integrations ([#3778](https://github.com/getsentry/sentry-java/pull/3778))
- Lazily initialize heavy `SentryOptions` members to avoid ANRs on app start ([#3749](https://github.com/getsentry/sentry-java/pull/3749))

*Breaking changes*:

- `options.experimental.sessionReplay.errorSampleRate` was renamed to `options.experimental.sessionReplay.onErrorSampleRate` ([#3637](https://github.com/getsentry/sentry-java/pull/3637))
- Manifest option `io.sentry.session-replay.error-sample-rate` was renamed to `io.sentry.session-replay.on-error-sample-rate` ([#3637](https://github.com/getsentry/sentry-java/pull/3637))
- Change `redactAllText` and `redactAllImages` to `maskAllText` and `maskAllImages` ([#3741](https://github.com/getsentry/sentry-java/pull/3741))

## 7.14.0

### Features

- Session Replay: Gesture/touch support for Flutter ([#3623](https://github.com/getsentry/sentry-java/pull/3623))

### Fixes

- Fix app start spans missing from Pixel devices ([#3634](https://github.com/getsentry/sentry-java/pull/3634))
- Avoid ArrayIndexOutOfBoundsException on Android cpu data collection ([#3598](https://github.com/getsentry/sentry-java/pull/3598))
- Fix lazy select queries instrumentation ([#3604](https://github.com/getsentry/sentry-java/pull/3604))
- Session Replay: buffer mode improvements ([#3622](https://github.com/getsentry/sentry-java/pull/3622))
  - Align next segment timestamp with the end of the buffered segment when converting from buffer mode to session mode
  - Persist `buffer` replay type for the entire replay when converting from buffer mode to session mode
  - Properly store screen names for `buffer` mode
- Session Replay: fix various crashes and issues ([#3628](https://github.com/getsentry/sentry-java/pull/3628))
  - Fix video not being encoded on Pixel devices
  - Fix SIGABRT native crashes on Xiaomi devices when encoding a video
  - Fix `RejectedExecutionException` when redacting a screenshot
  - Fix `FileNotFoundException` when persisting segment values

### Chores

- Introduce `ReplayShadowMediaCodec` and refactor tests using custom encoder ([#3612](https://github.com/getsentry/sentry-java/pull/3612))

## 7.13.0

### Features

- Session Replay: ([#3565](https://github.com/getsentry/sentry-java/pull/3565)) ([#3609](https://github.com/getsentry/sentry-java/pull/3609))
  - Capture remaining replay segment for ANRs on next app launch
  - Capture remaining replay segment for unhandled crashes on next app launch

### Fixes

- Session Replay: ([#3565](https://github.com/getsentry/sentry-java/pull/3565)) ([#3609](https://github.com/getsentry/sentry-java/pull/3609))
  - Fix stopping replay in `session` mode at 1 hour deadline
  - Never encode full frames for a video segment, only do partial updates. This further reduces size of the replay segment
  - Use propagation context when no active transaction for ANRs

### Dependencies

- Bump Spring Boot to 3.3.2 ([#3541](https://github.com/getsentry/sentry-java/pull/3541))

## 7.12.1

### Fixes

- Check app start spans time and ignore background app starts ([#3550](https://github.com/getsentry/sentry-java/pull/3550))
  - This should eliminate long-lasting App Start transactions

## 7.12.0

### Features

- Session Replay Public Beta ([#3339](https://github.com/getsentry/sentry-java/pull/3339))

  To enable Replay use the `sessionReplay.sessionSampleRate` or `sessionReplay.errorSampleRate` experimental options.

  ```kotlin
  import io.sentry.SentryReplayOptions
  import io.sentry.android.core.SentryAndroid

  SentryAndroid.init(context) { options ->
   
    // Currently under experimental options:
    options.experimental.sessionReplay.sessionSampleRate = 1.0
    options.experimental.sessionReplay.errorSampleRate = 1.0
  
    // To change default redaction behavior (defaults to true)
    options.experimental.sessionReplay.redactAllImages = true
    options.experimental.sessionReplay.redactAllText = true
  
    // To change quality of the recording (defaults to MEDIUM)
    options.experimental.sessionReplay.quality = SentryReplayOptions.SentryReplayQuality.MEDIUM // (LOW|MEDIUM|HIGH)
  }
  ```

  To learn more visit [Sentry's Mobile Session Replay](https://docs.sentry.io/product/explore/session-replay/mobile/) documentation page.

## 7.11.0

### Features

- Report dropped spans ([#3528](https://github.com/getsentry/sentry-java/pull/3528))

### Fixes

- Fix duplicate session start for React Native ([#3504](https://github.com/getsentry/sentry-java/pull/3504))
- Move onFinishCallback before span or transaction is finished ([#3459](https://github.com/getsentry/sentry-java/pull/3459))
- Add timestamp when a profile starts ([#3442](https://github.com/getsentry/sentry-java/pull/3442))
- Move fragment auto span finish to onFragmentStarted ([#3424](https://github.com/getsentry/sentry-java/pull/3424))
- Remove profiling timeout logic and disable profiling on API 21 ([#3478](https://github.com/getsentry/sentry-java/pull/3478))
- Properly reset metric flush flag on metric emission ([#3493](https://github.com/getsentry/sentry-java/pull/3493))
- Use SecureRandom in favor of Random for Metrics ([#3495](https://github.com/getsentry/sentry-java/pull/3495))
- Fix UncaughtExceptionHandlerIntegration Memory Leak ([#3398](https://github.com/getsentry/sentry-java/pull/3398))
- Deprecated `User.segment`. Use a custom tag or context instead. ([#3511](https://github.com/getsentry/sentry-java/pull/3511))
- Fix duplicated http spans ([#3526](https://github.com/getsentry/sentry-java/pull/3526))
- When capturing unhandled hybrid exception session should be ended and new start if need ([#3480](https://github.com/getsentry/sentry-java/pull/3480))

### Dependencies

- Bump Native SDK from v0.7.0 to v0.7.2 ([#3314](https://github.com/getsentry/sentry-java/pull/3314))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#072)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.7.0...0.7.2)

## 7.10.0

### Features

- Publish Gradle module metadata ([#3422](https://github.com/getsentry/sentry-java/pull/3422))

### Fixes

- Fix faulty `span.frame_delay` calculation for early app start spans ([#3427](https://github.com/getsentry/sentry-java/pull/3427))
- Fix crash when installing `ShutdownHookIntegration` and the VM is shutting down ([#3456](https://github.com/getsentry/sentry-java/pull/3456))

## 7.9.0

### Features

- Add start_type to app context ([#3379](https://github.com/getsentry/sentry-java/pull/3379))
- Add ttid/ttfd contribution flags ([#3386](https://github.com/getsentry/sentry-java/pull/3386))

### Fixes

- (Internal) Metrics code cleanup ([#3403](https://github.com/getsentry/sentry-java/pull/3403))
- Fix Frame measurements in app start transactions ([#3382](https://github.com/getsentry/sentry-java/pull/3382))
- Fix timing metric value different from span duration ([#3368](https://github.com/getsentry/sentry-java/pull/3368))
- Do not always write startup crash marker ([#3409](https://github.com/getsentry/sentry-java/pull/3409))
  - This may have been causing the SDK init logic to block the main thread

## 7.8.0

### Features

- Add description to OkHttp spans ([#3320](https://github.com/getsentry/sentry-java/pull/3320))
- Enable backpressure management by default ([#3284](https://github.com/getsentry/sentry-java/pull/3284))

### Fixes

- Add rate limit to Metrics ([#3334](https://github.com/getsentry/sentry-java/pull/3334))
- Fix java.lang.ClassNotFoundException: org.springframework.web.servlet.HandlerMapping in Spring Boot Servlet mode without WebMVC ([#3336](https://github.com/getsentry/sentry-java/pull/3336))
- Fix normalization of metrics keys, tags and values ([#3332](https://github.com/getsentry/sentry-java/pull/3332))

## 7.7.0

### Features

- Add support for Spring Rest Client ([#3199](https://github.com/getsentry/sentry-java/pull/3199))
- Extend Proxy options with proxy type ([#3326](https://github.com/getsentry/sentry-java/pull/3326))

### Fixes

- Fixed default deadline timeout to 30s instead of 300s ([#3322](https://github.com/getsentry/sentry-java/pull/3322))
- Fixed `Fix java.lang.ClassNotFoundException: org.springframework.web.servlet.HandlerExceptionResolver` in Spring Boot Servlet mode without WebMVC ([#3333](https://github.com/getsentry/sentry-java/pull/3333))

## 7.6.0

### Features

- Experimental: Add support for Sentry Developer Metrics ([#3205](https://github.com/getsentry/sentry-java/pull/3205), [#3238](https://github.com/getsentry/sentry-java/pull/3238), [#3248](https://github.com/getsentry/sentry-java/pull/3248), [#3250](https://github.com/getsentry/sentry-java/pull/3250))  
  Use the Metrics API to track processing time, download sizes, user signups, and conversion rates and correlate them back to tracing data in order to get deeper insights and solve issues faster. Our API supports counters, distributions, sets, gauges and timers, and it's easy to get started:
  ```kotlin
  Sentry.metrics()
    .increment(
        "button_login_click", // key
        1.0,                  // value
        null,                 // unit
        mapOf(                // tags
            "provider" to "e-mail"
        )
    )
  ```
  To learn more about Sentry Developer Metrics, head over to our [Java](https://docs.sentry.io/platforms/java/metrics/) and [Android](https://docs.sentry.io//platforms/android/metrics/) docs page.

## 7.5.0

### Features

- Add support for measurements at span level ([#3219](https://github.com/getsentry/sentry-java/pull/3219))
- Add `enableScopePersistence` option to disable `PersistingScopeObserver` used for ANR reporting which may increase performance overhead. Defaults to `true` ([#3218](https://github.com/getsentry/sentry-java/pull/3218))
  - When disabled, the SDK will not enrich ANRv2 events with scope data (e.g. breadcrumbs, user, tags, etc.)
- Configurable defaults for Cron - MonitorConfig ([#3195](https://github.com/getsentry/sentry-java/pull/3195))
- We now display a warning on startup if an incompatible version of Spring Boot is detected ([#3233](https://github.com/getsentry/sentry-java/pull/3233))
  - This should help notice a mismatching Sentry dependency, especially when upgrading a Spring Boot application
- Experimental: Add Metrics API ([#3205](https://github.com/getsentry/sentry-java/pull/3205))

### Fixes

- Ensure performance measurement collection is not taken too frequently ([#3221](https://github.com/getsentry/sentry-java/pull/3221))
- Fix old profiles deletion on SDK init ([#3216](https://github.com/getsentry/sentry-java/pull/3216))
- Fix hub restore point in wrappers: SentryWrapper, SentryTaskDecorator and SentryScheduleHook ([#3225](https://github.com/getsentry/sentry-java/pull/3225))
  - We now reset the hub to its previous value on the thread where the `Runnable`/`Callable`/`Supplier` is executed instead of setting it to the hub that was used on the thread where the `Runnable`/`Callable`/`Supplier` was created.
- Fix add missing thread name/id to app start spans ([#3226](https://github.com/getsentry/sentry-java/pull/3226))

## 7.4.0

### Features

- Add new threshold parameters to monitor config ([#3181](https://github.com/getsentry/sentry-java/pull/3181))
- Report process init time as a span for app start performance ([#3159](https://github.com/getsentry/sentry-java/pull/3159))
- (perf-v2): Calculate frame delay on a span level ([#3197](https://github.com/getsentry/sentry-java/pull/3197))
- Resolve spring properties in @SentryCheckIn annotation ([#3194](https://github.com/getsentry/sentry-java/pull/3194))
- Experimental: Add Spotlight integration ([#3166](https://github.com/getsentry/sentry-java/pull/3166))
    - For more details about Spotlight head over to https://spotlightjs.com/
    - Set `options.isEnableSpotlight = true` to enable Spotlight

### Fixes

- Don't wait on main thread when SDK restarts ([#3200](https://github.com/getsentry/sentry-java/pull/3200))
- Fix Jetpack Compose widgets are not being correctly identified for user interaction tracing ([#3209](https://github.com/getsentry/sentry-java/pull/3209))
- Fix issue title on Android when a wrapping `RuntimeException` is thrown by the system ([#3212](https://github.com/getsentry/sentry-java/pull/3212))
  - This will change grouping of the issues that were previously titled `RuntimeInit$MethodAndArgsCaller` to have them split up properly by the original root cause exception

## 7.3.0

### Features

- Added App Start profiling
    - This depends on the new option `io.sentry.profiling.enable-app-start`, other than the already existing `io.sentry.traces.profiling.sample-rate`.
    - Sampler functions can check the new `isForNextAppStart` flag, to adjust startup profiling sampling programmatically.
      Relevant PRs:
    - Decouple Profiler from Transaction ([#3101](https://github.com/getsentry/sentry-java/pull/3101))
    - Add options and sampling logic ([#3121](https://github.com/getsentry/sentry-java/pull/3121))
    - Add ContentProvider and start profile ([#3128](https://github.com/getsentry/sentry-java/pull/3128))
- Extend internal performance collector APIs ([#3102](https://github.com/getsentry/sentry-java/pull/3102))
- Collect slow and frozen frames for spans using `OnFrameMetricsAvailableListener` ([#3111](https://github.com/getsentry/sentry-java/pull/3111))
- Interpolate total frame count to match span duration ([#3158](https://github.com/getsentry/sentry-java/pull/3158))

### Fixes

- Avoid multiple breadcrumbs from OkHttpEventListener ([#3175](https://github.com/getsentry/sentry-java/pull/3175))
- Apply OkHttp listener auto finish timestamp to all running spans ([#3167](https://github.com/getsentry/sentry-java/pull/3167))
- Fix not eligible for auto proxying warnings ([#3154](https://github.com/getsentry/sentry-java/pull/3154))
- Set default fingerprint for ANRv2 events to correctly group background and foreground ANRs ([#3164](https://github.com/getsentry/sentry-java/pull/3164))
  - This will improve grouping of ANRs that have similar stacktraces but differ in background vs foreground state. Only affects newly-ingested ANR events with `mechanism:AppExitInfo`
- Fix UserFeedback disk cache name conflicts with linked events ([#3116](https://github.com/getsentry/sentry-java/pull/3116))

### Breaking changes

- Remove `HostnameVerifier` option as it's flagged by security tools of some app stores ([#3150](https://github.com/getsentry/sentry-java/pull/3150))
  - If you were using this option, you have 3 possible paths going forward:
    - Provide a custom `ITransportFactory` through `SentryOptions.setTransportFactory()`, where you can copy over most of the parts like `HttpConnection` and `AsyncHttpTransport` from the SDK with necessary modifications
    - Get a certificate for your server through e.g. [Let's Encrypt](https://letsencrypt.org/)
    - Fork the SDK and add the hostname verifier back

### Dependencies

- Bump Native SDK from v0.6.7 to v0.7.0 ([#3133](https://github.com/getsentry/sentry-java/pull/3133))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#070)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.6.7...0.7.0)

## 7.2.0

### Features

- Handle `monitor`/`check_in` in client reports and rate limiter ([#3096](https://github.com/getsentry/sentry-java/pull/3096))
- Add support for `graphql-java` version 21 ([#3090](https://github.com/getsentry/sentry-java/pull/3090))

### Fixes

- Avoid concurrency in AndroidProfiler performance data collection ([#3130](https://github.com/getsentry/sentry-java/pull/3130))
- Improve thresholds for network changes breadcrumbs ([#3083](https://github.com/getsentry/sentry-java/pull/3083))
- SchedulerFactoryBeanCustomizer now runs first so user customization is not overridden ([#3095](https://github.com/getsentry/sentry-java/pull/3095))
  - If you are setting global job listeners please also add `SentryJobListener`
- Ensure serialVersionUID of Exception classes are unique ([#3115](https://github.com/getsentry/sentry-java/pull/3115))
- Get rid of "is not eligible for getting processed by all BeanPostProcessors" warnings in Spring Boot ([#3108](https://github.com/getsentry/sentry-java/pull/3108))
- Fix missing `release` and other fields for ANRs reported with `mechanism:AppExitInfo` ([#3074](https://github.com/getsentry/sentry-java/pull/3074))

### Dependencies

- Bump `opentelemetry-sdk` to `1.33.0` and `opentelemetry-javaagent` to `1.32.0` ([#3112](https://github.com/getsentry/sentry-java/pull/3112))

## 7.1.0

### Features

- Support multiple debug-metadata.properties ([#3024](https://github.com/getsentry/sentry-java/pull/3024))
- Automatically downsample transactions when the system is under load ([#3072](https://github.com/getsentry/sentry-java/pull/3072))
  - You can opt into this behaviour by setting `enable-backpressure-handling=true`.
  - We're happy to receive feedback, e.g. [in this GitHub issue](https://github.com/getsentry/sentry-java/issues/2829)
  - When the system is under load we start reducing the `tracesSampleRate` automatically.
  - Once the system goes back to healthy, we reset the `tracesSampleRate` to its original value.
- (Android) Experimental: Provide more detailed cold app start information ([#3057](https://github.com/getsentry/sentry-java/pull/3057))
  - Attaches spans for Application, ContentProvider, and Activities to app-start timings
  - Application and ContentProvider timings are added using bytecode instrumentation, which requires sentry-android-gradle-plugin version `4.1.0` or newer
  - Uses Process.startUptimeMillis to calculate app-start timings
  - To enable this feature set `options.isEnablePerformanceV2 = true`
- Move slow+frozen frame calculation, as well as frame delay inside SentryFrameMetricsCollector ([#3100](https://github.com/getsentry/sentry-java/pull/3100))
- Extract Activity Breadcrumbs generation into own Integration ([#3064](https://github.com/getsentry/sentry-java/pull/3064))

### Fixes

- Send breadcrumbs and client error in `SentryOkHttpEventListener` even without transactions ([#3087](https://github.com/getsentry/sentry-java/pull/3087))
- Keep `io.sentry.exception.SentryHttpClientException` from obfuscation to display proper issue title on Sentry ([#3093](https://github.com/getsentry/sentry-java/pull/3093))
- (Android) Fix wrong activity transaction duration in case SDK init is deferred ([#3092](https://github.com/getsentry/sentry-java/pull/3092))

### Dependencies

- Bump Gradle from v8.4.0 to v8.5.0 ([#3070](https://github.com/getsentry/sentry-java/pull/3070))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v850)
  - [diff](https://github.com/gradle/gradle/compare/v8.4.0...v8.5.0)

## 7.0.0

Version 7 of the Sentry Android/Java SDK brings a variety of features and fixes. The most notable changes are:
- Bumping `minSdk` level to 19 (Android 4.4)
- The SDK will now listen to connectivity changes and try to re-upload cached events when internet connection is re-established additionally to uploading events on app restart 
- `Sentry.getSpan` now returns the root transaction, which should improve the span hierarchy and make it leaner
- Multiple improvements to reduce probability of the SDK causing ANRs
- New `sentry-okhttp` artifact is unbundled from Android and can be used in pure JVM-only apps

## Sentry Self-hosted Compatibility

This SDK version is compatible with a self-hosted version of Sentry `22.12.0` or higher. If you are using an older version of [self-hosted Sentry](https://develop.sentry.dev/self-hosted/) (aka onpremise), you will need to [upgrade](https://develop.sentry.dev/self-hosted/releases/). If you're using `sentry.io` no action is required.

## Sentry Integrations Version Compatibility (Android)

Make sure to align _all_ Sentry dependencies to the same version when bumping the SDK to 7.+, otherwise it will crash at runtime due to binary incompatibility. (E.g. if you're using `-timber`, `-okhttp` or other packages)

For example, if you're using the [Sentry Android Gradle plugin](https://github.com/getsentry/sentry-android-gradle-plugin) with the `autoInstallation` [feature](https://docs.sentry.io/platforms/android/configuration/gradle/#auto-installation) (enabled by default), make sure to use version 4.+ of the gradle plugin together with version 7.+ of the SDK. If you can't do that for some reason, you can specify sentry version via the plugin config block:

```kotlin
sentry {
  autoInstallation {
    sentryVersion.set("7.0.0")
  }
}
```

Similarly, if you have a Sentry SDK (e.g. `sentry-android-core`) dependency on one of your Gradle modules and you're updating it to 7.+, make sure the Gradle plugin is at 4.+ or specify the SDK version as shown in the snippet above.

## Breaking Changes

- Bump min API to 19 ([#2883](https://github.com/getsentry/sentry-java/pull/2883))
- If you're using `sentry-kotlin-extensions`, it requires `kotlinx-coroutines-core` version `1.6.1` or higher now ([#2838](https://github.com/getsentry/sentry-java/pull/2838))
- Move enableNdk from SentryOptions to SentryAndroidOptions ([#2793](https://github.com/getsentry/sentry-java/pull/2793))
- Apollo v2 BeforeSpanCallback now allows returning null ([#2890](https://github.com/getsentry/sentry-java/pull/2890))
- `SentryOkHttpUtils` was removed from public API as it's been exposed by mistake ([#3005](https://github.com/getsentry/sentry-java/pull/3005))
- `Scope` now implements the `IScope` interface, therefore some methods like `ScopeCallback.run` accept `IScope` now ([#3066](https://github.com/getsentry/sentry-java/pull/3066))
- Cleanup `startTransaction` overloads ([#2964](https://github.com/getsentry/sentry-java/pull/2964))
    - We have reduced the number of overloads by allowing to pass in a `TransactionOptions` object instead of having separate parameters for certain options
    - `TransactionOptions` has defaults set and can be customized, for example:

```kotlin
// old
val transaction = Sentry.startTransaction("name", "op", bindToScope = true)
// new
val transaction = Sentry.startTransaction("name", "op", TransactionOptions().apply { isBindToScope = true })
```

## Behavioural Changes

- Android only: `Sentry.getSpan()` returns the root span/transaction instead of the latest span ([#2855](https://github.com/getsentry/sentry-java/pull/2855))
- Capture failed HTTP and GraphQL (Apollo) requests by default ([#2794](https://github.com/getsentry/sentry-java/pull/2794))
    - This can increase your event consumption and may affect your quota, because we will report failed network requests as Sentry events by default, if you're using the `sentry-android-okhttp` or `sentry-apollo-3` integrations. You can customize what errors you want/don't want to have reported for [OkHttp](https://docs.sentry.io/platforms/android/integrations/okhttp#http-client-errors) and [Apollo3](https://docs.sentry.io/platforms/android/integrations/apollo3#graphql-client-errors) respectively.
- Measure AppStart time till First Draw instead of `onResume` ([#2851](https://github.com/getsentry/sentry-java/pull/2851))
- Automatic user interaction tracking: every click now starts a new automatic transaction ([#2891](https://github.com/getsentry/sentry-java/pull/2891))
    - Previously performing a click on the same UI widget twice would keep the existing transaction running, the new behavior now better aligns with other SDKs
- Add deadline timeout for automatic transactions ([#2865](https://github.com/getsentry/sentry-java/pull/2865))
    - This affects all automatically generated transactions on Android (UI, clicks), the default timeout is 30s, meaning the automatic transaction will be force-finished with status `deadline_exceeded` when reaching the deadline 
- Set ip_address to {{auto}} by default, even if sendDefaultPII is disabled ([#2860](https://github.com/getsentry/sentry-java/pull/2860))
    - Instead use the "Prevent Storing of IP Addresses" option in the "Security & Privacy" project settings on sentry.io
- Raw logback message and parameters are now guarded by `sendDefaultPii` if an `encoder` has been configured ([#2976](https://github.com/getsentry/sentry-java/pull/2976))
- The `maxSpans` setting (defaults to 1000) is enforced for nested child spans which means a single transaction can have `maxSpans` number of children (nested or not) at most ([#3065](https://github.com/getsentry/sentry-java/pull/3065))
- The `ScopeCallback` in `withScope` is now always executed ([#3066](https://github.com/getsentry/sentry-java/pull/3066))

## Deprecations

- `sentry-android-okhttp` was deprecated in favour of the new `sentry-okhttp` module. Make sure to replace `io.sentry.android.okhttp` package name with `io.sentry.okhttp` before the next major, where the classes will be removed ([#3005](https://github.com/getsentry/sentry-java/pull/3005))

## Other Changes

### Features

- Observe network state to upload any unsent envelopes ([#2910](https://github.com/getsentry/sentry-java/pull/2910))
    - Android: it works out-of-the-box as part of the default `SendCachedEnvelopeIntegration`
    - JVM: you'd have to install `SendCachedEnvelopeFireAndForgetIntegration` as mentioned in https://docs.sentry.io/platforms/java/configuration/#configuring-offline-caching and provide your own implementation of `IConnectionStatusProvider` via `SentryOptions`
- Add `sentry-okhttp` module to support instrumenting OkHttp in non-Android projects ([#3005](https://github.com/getsentry/sentry-java/pull/3005))
- Do not filter out Sentry SDK frames in case of uncaught exceptions ([#3021](https://github.com/getsentry/sentry-java/pull/3021))
- Do not try to send and drop cached envelopes when rate-limiting is active ([#2937](https://github.com/getsentry/sentry-java/pull/2937))

### Fixes

- Use `getMyMemoryState()` instead of `getRunningAppProcesses()` to retrieve process importance ([#3004](https://github.com/getsentry/sentry-java/pull/3004))
    - This should prevent some app stores from flagging apps as violating their privacy
- Reduce flush timeout to 4s on Android to avoid ANRs ([#2858](https://github.com/getsentry/sentry-java/pull/2858))
- Reduce timeout of AsyncHttpTransport to avoid ANR ([#2879](https://github.com/getsentry/sentry-java/pull/2879))
- Do not overwrite UI transaction status if set by the user ([#2852](https://github.com/getsentry/sentry-java/pull/2852))
- Capture unfinished transaction on Scope with status `aborted` in case a crash happens ([#2938](https://github.com/getsentry/sentry-java/pull/2938))
    - This will fix the link between transactions and corresponding crashes, you'll be able to see them in a single trace
- Fix Coroutine Context Propagation using CopyableThreadContextElement ([#2838](https://github.com/getsentry/sentry-java/pull/2838))
- Fix don't overwrite the span status of unfinished spans ([#2859](https://github.com/getsentry/sentry-java/pull/2859))
- Migrate from `default` interface methods to proper implementations in each interface implementor ([#2847](https://github.com/getsentry/sentry-java/pull/2847))
    - This prevents issues when using the SDK on older AGP versions (< 4.x.x)
- Reduce main thread work on init ([#3036](https://github.com/getsentry/sentry-java/pull/3036))
- Move Integrations registration to background on init ([#3043](https://github.com/getsentry/sentry-java/pull/3043))
- Fix `SentryOkHttpInterceptor.BeforeSpanCallback` was not finishing span when it was dropped ([#2958](https://github.com/getsentry/sentry-java/pull/2958))

## 6.34.0

### Features

- Add current activity name to app context ([#2999](https://github.com/getsentry/sentry-java/pull/2999))
- Add `MonitorConfig` param to `CheckInUtils.withCheckIn` ([#3038](https://github.com/getsentry/sentry-java/pull/3038))
  - This makes it easier to automatically create or update (upsert) monitors.
- (Internal) Extract Android Profiler and Measurements for Hybrid SDKs ([#3016](https://github.com/getsentry/sentry-java/pull/3016))
- (Internal) Remove SentryOptions dependency from AndroidProfiler ([#3051](https://github.com/getsentry/sentry-java/pull/3051))
- (Internal) Add `readBytesFromFile` for use in Hybrid SDKs ([#3052](https://github.com/getsentry/sentry-java/pull/3052))
- (Internal) Add `getProguardUuid` for use in Hybrid SDKs ([#3054](https://github.com/getsentry/sentry-java/pull/3054))

### Fixes

-  Fix SIGSEV, SIGABRT and SIGBUS crashes happening after/around the August Google Play System update, see [#2955](https://github.com/getsentry/sentry-java/issues/2955) for more details (fix provided by Native SDK bump)
- Ensure DSN uses http/https protocol ([#3044](https://github.com/getsentry/sentry-java/pull/3044))

### Dependencies

- Bump Native SDK from v0.6.6 to v0.6.7 ([#3048](https://github.com/getsentry/sentry-java/pull/3048))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#067)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.6.6...0.6.7)

## 6.33.2-beta.1

### Fixes

-  Fix SIGSEV, SIGABRT and SIGBUS crashes happening after/around the August Google Play System update, see [#2955](https://github.com/getsentry/sentry-java/issues/2955) for more details (fix provided by Native SDK bump)

### Dependencies

- Bump Native SDK from v0.6.6 to v0.6.7 ([#3048](https://github.com/getsentry/sentry-java/pull/3048))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#067)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.6.6...0.6.7)

## 6.33.1

### Fixes

- Do not register `sentrySpringFilter` in ServletContext for Spring Boot ([#3027](https://github.com/getsentry/sentry-java/pull/3027))

## 6.33.0

### Features

- Add thread information to spans ([#2998](https://github.com/getsentry/sentry-java/pull/2998))
- Use PixelCopy API for capturing screenshots on API level 24+ ([#3008](https://github.com/getsentry/sentry-java/pull/3008))

### Fixes

- Fix crash when HTTP connection error message contains formatting symbols ([#3002](https://github.com/getsentry/sentry-java/pull/3002))
- Cap max number of stack frames to 100 to not exceed payload size limit ([#3009](https://github.com/getsentry/sentry-java/pull/3009))
  - This will ensure we report errors with a big number of frames such as `StackOverflowError`
- Fix user interaction tracking not working for Jetpack Compose 1.5+ ([#3010](https://github.com/getsentry/sentry-java/pull/3010))
- Make sure to close all Closeable resources ([#3000](https://github.com/getsentry/sentry-java/pull/3000))

## 6.32.0

### Features

- Make `DebugImagesLoader` public ([#2993](https://github.com/getsentry/sentry-java/pull/2993))

### Fixes

- Make `SystemEventsBroadcastReceiver` exported on API 33+ ([#2990](https://github.com/getsentry/sentry-java/pull/2990))
  - This will fix the `SystemEventsBreadcrumbsIntegration` crashes that you might have encountered on Play Console

## 6.31.0

### Features

- Improve default debouncing mechanism ([#2945](https://github.com/getsentry/sentry-java/pull/2945))
- Add `CheckInUtils.withCheckIn` which abstracts away some of the manual check-ins complexity ([#2959](https://github.com/getsentry/sentry-java/pull/2959))
- Add `@SentryCaptureExceptionParameter` annotation which captures exceptions passed into an annotated method ([#2764](https://github.com/getsentry/sentry-java/pull/2764))
  - This can be used to replace `Sentry.captureException` calls in `@ExceptionHandler` of a `@ControllerAdvice`
- Add `ServerWebExchange` to `Hint` for WebFlux as `WEBFLUX_EXCEPTION_HANDLER_EXCHANGE` ([#2977](https://github.com/getsentry/sentry-java/pull/2977))
- Allow filtering GraphQL errors ([#2967](https://github.com/getsentry/sentry-java/pull/2967))
  - This list can be set directly when calling the constructor of `SentryInstrumentation`
  - For Spring Boot it can also be set in `application.properties` as `sentry.graphql.ignored-error-types=SOME_ERROR,ANOTHER_ERROR`

### Fixes

- Add OkHttp span auto-close when response body is not read ([#2923](https://github.com/getsentry/sentry-java/pull/2923))
- Fix json parsing of nullable/empty fields for Hybrid SDKs ([#2968](https://github.com/getsentry/sentry-java/pull/2968))
  - (Internal) Rename `nextList` to `nextListOrNull` to actually match what the method does
  - (Hybrid) Check if there's any object in a collection before trying to parse it (which prevents the "Failed to deserilize object in list" log message)
  - (Hybrid) If a date can't be parsed as an ISO timestamp, attempts to parse it as millis silently, without printing a log message
  - (Hybrid) If `op` is not defined as part of `SpanContext`, fallback to an empty string, because the filed is optional in the spec
- Always attach OkHttp errors and Http Client Errors only to call root span ([#2961](https://github.com/getsentry/sentry-java/pull/2961))
- Fixed crash accessing Choreographer instance ([#2970](https://github.com/getsentry/sentry-java/pull/2970))

### Dependencies

- Bump Native SDK from v0.6.5 to v0.6.6 ([#2975](https://github.com/getsentry/sentry-java/pull/2975))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#066)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.6.5...0.6.6)
- Bump Gradle from v8.3.0 to v8.4.0 ([#2966](https://github.com/getsentry/sentry-java/pull/2966))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v840)
  - [diff](https://github.com/gradle/gradle/compare/v8.3.0...v8.4.0)

## 6.30.0

### Features

- Add `sendModules` option for disable sending modules ([#2926](https://github.com/getsentry/sentry-java/pull/2926))
- Send `db.system` and `db.name` in span data for androidx.sqlite spans ([#2928](https://github.com/getsentry/sentry-java/pull/2928))
- Check-ins (CRONS) support ([#2952](https://github.com/getsentry/sentry-java/pull/2952))
  - Add API for sending check-ins (CRONS) manually ([#2935](https://github.com/getsentry/sentry-java/pull/2935))
  - Support check-ins (CRONS) for Quartz ([#2940](https://github.com/getsentry/sentry-java/pull/2940))
  - `@SentryCheckIn` annotation and advice config for Spring ([#2946](https://github.com/getsentry/sentry-java/pull/2946))
  - Add option for ignoring certain monitor slugs ([#2943](https://github.com/getsentry/sentry-java/pull/2943))

### Fixes

- Always send memory stats for transactions ([#2936](https://github.com/getsentry/sentry-java/pull/2936))
  - This makes it possible to query transactions by the `device.class` tag on Sentry
- Add `sentry.enable-aot-compatibility` property to SpringBoot Jakarta `SentryAutoConfiguration` to enable building for GraalVM ([#2915](https://github.com/getsentry/sentry-java/pull/2915))

### Dependencies

- Bump Gradle from v8.2.1 to v8.3.0 ([#2900](https://github.com/getsentry/sentry-java/pull/2900))
  - [changelog](https://github.com/gradle/gradle/blob/master release-test/CHANGELOG.md#v830)
  - [diff](https://github.com/gradle/gradle/compare/v8.2.1...v8.3.0)

## 6.29.0

### Features

- Send `db.system` and `db.name` in span data ([#2894](https://github.com/getsentry/sentry-java/pull/2894))
- Send `http.request.method` in span data ([#2896](https://github.com/getsentry/sentry-java/pull/2896))
- Add `enablePrettySerializationOutput` option for opting out of pretty print ([#2871](https://github.com/getsentry/sentry-java/pull/2871))

## 6.28.0

### Features

- Add HTTP response code to Spring WebFlux transactions ([#2870](https://github.com/getsentry/sentry-java/pull/2870))
- Add `sampled` to Dynamic Sampling Context ([#2869](https://github.com/getsentry/sentry-java/pull/2869))
- Improve server side GraphQL support for spring-graphql and Nextflix DGS ([#2856](https://github.com/getsentry/sentry-java/pull/2856))
    - If you have already been using `SentryDataFetcherExceptionHandler` that still works but has been deprecated. Please use `SentryGenericDataFetcherExceptionHandler` combined with `SentryInstrumentation` instead for better error reporting.
    - More exceptions and errors caught and reported to Sentry by also looking at the `ExecutionResult` (more specifically its `errors`)
        - You may want to filter out certain errors, please see [docs on filtering](https://docs.sentry.io/platforms/java/configuration/filtering/)
    - More details for Sentry events: query, variables and response (where possible)
    - Breadcrumbs for operation (query, mutation, subscription), data fetchers and data loaders (Spring only)
    - Better hub propagation by using `GraphQLContext`
- Add autoconfigure modules for Spring Boot called `sentry-spring-boot` and `sentry-spring-boot-jakarta` ([#2880](https://github.com/getsentry/sentry-java/pull/2880))
  - The autoconfigure modules `sentry-spring-boot` and `sentry-spring-boot-jakarta` have a `compileOnly` dependency on `spring-boot-starter` which is needed for our auto installation in [sentry-android-gradle-plugin](https://github.com/getsentry/sentry-android-gradle-plugin)
  - The starter modules  `sentry-spring-boot-starter` and `sentry-spring-boot-starter-jakarta` now bring `spring-boot-starter` as a dependency
- You can now disable Sentry by setting the `enabled` option to `false` ([#2840](https://github.com/getsentry/sentry-java/pull/2840))

### Fixes

- Propagate OkHttp status to parent spans ([#2872](https://github.com/getsentry/sentry-java/pull/2872))

## 6.27.0

### Features

- Add TraceOrigin to Transactions and Spans ([#2803](https://github.com/getsentry/sentry-java/pull/2803))

### Fixes

- Deduplicate events happening in multiple threads simultaneously (e.g. `OutOfMemoryError`) ([#2845](https://github.com/getsentry/sentry-java/pull/2845))
  - This will improve Crash-Free Session Rate as we no longer will send multiple Session updates with `Crashed` status, but only the one that is relevant
- Ensure no Java 8 method reference sugar is used for Android ([#2857](https://github.com/getsentry/sentry-java/pull/2857))
- Do not send session updates for terminated sessions ([#2849](https://github.com/getsentry/sentry-java/pull/2849))

## 6.26.0

### Features
- (Internal) Extend APIs for hybrid SDKs ([#2814](https://github.com/getsentry/sentry-java/pull/2814), [#2846](https://github.com/getsentry/sentry-java/pull/2846))

### Fixes

- Fix ANRv2 thread dump parsing for native-only threads ([#2839](https://github.com/getsentry/sentry-java/pull/2839))
- Derive `TracingContext` values from event for ANRv2 events ([#2839](https://github.com/getsentry/sentry-java/pull/2839))

## 6.25.2

### Fixes

- Change Spring Boot, Apollo, Apollo 3, JUL, Logback, Log4j2, OpenFeign, GraphQL and Kotlin coroutines core dependencies to compileOnly ([#2837](https://github.com/getsentry/sentry-java/pull/2837))

## 6.25.1

### Fixes

- Allow removing integrations in SentryAndroid.init ([#2826](https://github.com/getsentry/sentry-java/pull/2826))
- Fix concurrent access to frameMetrics listener ([#2823](https://github.com/getsentry/sentry-java/pull/2823))

### Dependencies

- Bump Native SDK from v0.6.4 to v0.6.5 ([#2822](https://github.com/getsentry/sentry-java/pull/2822))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#065)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.6.4...0.6.5)
- Bump Gradle from v8.2.0 to v8.2.1 ([#2830](https://github.com/getsentry/sentry-java/pull/2830))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v821)
  - [diff](https://github.com/gradle/gradle/compare/v8.2.0...v8.2.1)

## 6.25.0

### Features

- Add manifest `AutoInit` to integrations list ([#2795](https://github.com/getsentry/sentry-java/pull/2795))
- Tracing headers (`sentry-trace` and `baggage`) are now attached and passed through even if performance is disabled ([#2788](https://github.com/getsentry/sentry-java/pull/2788))

### Fixes

- Set `environment` from `SentryOptions` if none persisted in ANRv2 ([#2809](https://github.com/getsentry/sentry-java/pull/2809))
- Remove code that set `tracesSampleRate` to `0.0` for Spring Boot if not set ([#2800](https://github.com/getsentry/sentry-java/pull/2800))
  - This used to enable performance but not send any transactions by default.
  - Performance is now disabled by default.
- Fix slow/frozen frames were not reported with transactions ([#2811](https://github.com/getsentry/sentry-java/pull/2811))

### Dependencies

- Bump Native SDK from v0.6.3 to v0.6.4 ([#2796](https://github.com/getsentry/sentry-java/pull/2796))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#064)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.6.3...0.6.4)
- Bump Gradle from v8.1.1 to v8.2.0 ([#2810](https://github.com/getsentry/sentry-java/pull/2810))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v820)
  - [diff](https://github.com/gradle/gradle/compare/v8.1.1...v8.2.0)

## 6.24.0

### Features

- Add debouncing mechanism and before-capture callbacks for screenshots and view hierarchies ([#2773](https://github.com/getsentry/sentry-java/pull/2773))
- Improve ANRv2 implementation ([#2792](https://github.com/getsentry/sentry-java/pull/2792))
  - Add a proguard rule to keep `ApplicationNotResponding` class from obfuscation
  - Add a new option `setReportHistoricalAnrs`; when enabled, it will report all of the ANRs from the [getHistoricalExitReasons](https://developer.android.com/reference/android/app/ActivityManager?hl=en#getHistoricalProcessExitReasons(java.lang.String,%20int,%20int)) list. 
  By default, the SDK only reports and enriches the latest ANR and only this one counts towards ANR rate. 
  Worth noting that this option is mainly useful when updating the SDK to the version where ANRv2 has been introduced, to report all ANRs happened prior to the SDK update. After that, the SDK will always pick up the latest ANR from the historical exit reasons list on next app restart, so there should be no historical ANRs to report.
  These ANRs are reported with the `HistoricalAppExitInfo` mechanism.
  - Add a new option `setAttachAnrThreadDump` to send ANR thread dump from the system as an attachment. 
  This is only useful as additional information, because the SDK attempts to parse the thread dump into proper threads with stacktraces by default.
  - If [ApplicationExitInfo#getTraceInputStream](https://developer.android.com/reference/android/app/ApplicationExitInfo#getTraceInputStream()) returns null, the SDK no longer reports an ANR event, as these events are not very useful without it.
  - Enhance regex patterns for native stackframes

## 6.23.0

### Features

- Add profile rate limiting ([#2782](https://github.com/getsentry/sentry-java/pull/2782))
- Support for automatically capturing Failed GraphQL (Apollo 3) Client errors ([#2781](https://github.com/getsentry/sentry-java/pull/2781))

```kotlin
import com.apollographql.apollo3.ApolloClient
import io.sentry.apollo3.sentryTracing

val apolloClient = ApolloClient.Builder()
    .serverUrl("https://example.com/graphql")
    .sentryTracing(captureFailedRequests = true)    
    .build()
```

### Dependencies

- Bump Native SDK from v0.6.2 to v0.6.3 ([#2746](https://github.com/getsentry/sentry-java/pull/2746))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#063)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.6.2...0.6.3)

### Fixes

- Align http.status with [span data conventions](https://develop.sentry.dev/sdk/performance/span-data-conventions/) ([#2786](https://github.com/getsentry/sentry-java/pull/2786))

## 6.22.0

### Features

- Add `lock` attribute to the `SentryStackFrame` protocol to better highlight offending frames in the UI ([#2761](https://github.com/getsentry/sentry-java/pull/2761))
- Enrich database spans with blocked main thread info ([#2760](https://github.com/getsentry/sentry-java/pull/2760))
- Add `api_target` to `Request` and `data` to `Response` Protocols ([#2775](https://github.com/getsentry/sentry-java/pull/2775))

### Fixes

- No longer use `String.join` in `Baggage` as it requires API level 26 ([#2778](https://github.com/getsentry/sentry-java/pull/2778))

## 6.21.0

### Features

- Introduce new `sentry-android-sqlite` integration ([#2722](https://github.com/getsentry/sentry-java/pull/2722))
    - This integration replaces the old `androidx.sqlite` database instrumentation in the Sentry Android Gradle plugin
    - A new capability to manually instrument your `androidx.sqlite` databases. 
      - You can wrap your custom `SupportSQLiteOpenHelper` instance into `SentrySupportSQLiteOpenHelper(myHelper)` if you're not using the Sentry Android Gradle plugin and still benefit from performance auto-instrumentation.
- Add SentryWrapper for Callable and Supplier Interface ([#2720](https://github.com/getsentry/sentry-java/pull/2720))
- Load sentry-debug-meta.properties ([#2734](https://github.com/getsentry/sentry-java/pull/2734))
  - This enables source context for Java
  - For more information on how to enable source context, please refer to [#633](https://github.com/getsentry/sentry-java/issues/633#issuecomment-1465599120)

### Fixes

- Finish WebFlux transaction before popping scope ([#2724](https://github.com/getsentry/sentry-java/pull/2724))
- Use daemon threads for SentryExecutorService ([#2747](https://github.com/getsentry/sentry-java/pull/2747))
  - We started using `SentryExecutorService` in `6.19.0` which caused the application to hang on shutdown unless `Sentry.close()` was called. By using daemon threads we no longer block shutdown.
- Use Base64.NO_WRAP to avoid unexpected char errors in Apollo ([#2745](https://github.com/getsentry/sentry-java/pull/2745))
- Don't warn R8 on missing `ComposeViewHierarchyExporter` class ([#2743](https://github.com/getsentry/sentry-java/pull/2743))

## 6.20.0

### Features

- Add support for Sentry Kotlin Compiler Plugin ([#2695](https://github.com/getsentry/sentry-java/pull/2695))
  - In conjunction with our sentry-kotlin-compiler-plugin we improved Jetpack Compose support for
    - [View Hierarchy](https://docs.sentry.io/platforms/android/enriching-events/viewhierarchy/) support for Jetpack Compose screens
    - Automatic breadcrumbs for [user interactions](https://docs.sentry.io/platforms/android/performance/instrumentation/automatic-instrumentation/#user-interaction-instrumentation)
- More granular http requests instrumentation with a new SentryOkHttpEventListener ([#2659](https://github.com/getsentry/sentry-java/pull/2659))
    - Create spans for time spent on:
        - Proxy selection
        - DNS resolution
        - HTTPS setup
        - Connection
        - Requesting headers
        - Receiving response
    - You can attach the event listener to your OkHttpClient through `client.eventListener(new SentryOkHttpEventListener()).addInterceptor(new SentryOkHttpInterceptor()).build();`
    - In case you already have an event listener you can use the SentryOkHttpEventListener as well through `client.eventListener(new SentryOkHttpEventListener(myListener)).addInterceptor(new SentryOkHttpInterceptor()).build();`
- Add a new option to disable `RootChecker` ([#2735](https://github.com/getsentry/sentry-java/pull/2735))

### Fixes

- Base64 encode internal Apollo3 Headers ([#2707](https://github.com/getsentry/sentry-java/pull/2707))
- Fix `SentryTracer` crash when scheduling auto-finish of a transaction, but the timer has already been cancelled ([#2731](https://github.com/getsentry/sentry-java/pull/2731))
- Fix `AndroidTransactionProfiler` crash when finishing a profile that happened due to race condition ([#2731](https://github.com/getsentry/sentry-java/pull/2731))

## 6.19.1

### Fixes

- Ensure screenshots and view hierarchies are captured on the main thread ([#2712](https://github.com/getsentry/sentry-java/pull/2712))

## 6.19.0

### Features

- Add Screenshot and ViewHierarchy to integrations list ([#2698](https://github.com/getsentry/sentry-java/pull/2698))
- New ANR detection based on [ApplicationExitInfo API](https://developer.android.com/reference/android/app/ApplicationExitInfo) ([#2697](https://github.com/getsentry/sentry-java/pull/2697))
    - This implementation completely replaces the old one (based on a watchdog) on devices running Android 11 and above:
      - New implementation provides more precise ANR events/ANR rate detection as well as system thread dump information. The new implementation reports ANRs exactly as Google Play Console, without producing false positives or missing important background ANR events.
      - New implementation reports ANR events with a new mechanism `mechanism:AppExitInfo`.
      - However, despite producing many false positives, the old implementation is capable of better enriching ANR errors (which is not available with the new implementation), for example:
        - Capturing screenshots at the time of ANR event;
        - Capturing transactions and profiling data corresponding to the ANR event;
        - Auxiliary information (such as current memory load) at the time of ANR event.
      - If you would like us to provide support for the old approach working alongside the new one on Android 11 and above (e.g. for raising events for slow code on main thread), consider upvoting [this issue](https://github.com/getsentry/sentry-java/issues/2693).
    - The old watchdog implementation will continue working for older API versions (Android < 11):
        - The old implementation reports ANR events with the existing mechanism `mechanism:ANR`.
- Open up `TransactionOptions`, `ITransaction` and `IHub` methods allowing consumers modify start/end timestamp of transactions and spans ([#2701](https://github.com/getsentry/sentry-java/pull/2701))
- Send source bundle IDs to Sentry to enable source context ([#2663](https://github.com/getsentry/sentry-java/pull/2663))
  - For more information on how to enable source context, please refer to [#633](https://github.com/getsentry/sentry-java/issues/633#issuecomment-1465599120)

### Fixes

- Android Profiler on calling thread ([#2691](https://github.com/getsentry/sentry-java/pull/2691))
- Use `configureScope` instead of `withScope` in `Hub.close()`. This ensures that the main scope releases the in-memory data when closing a hub instance. ([#2688](https://github.com/getsentry/sentry-java/pull/2688))
- Remove null keys/values before creating concurrent hashmap in order to avoid NPE ([#2708](https://github.com/getsentry/sentry-java/pull/2708))
- Exclude SentryOptions from R8/ProGuard obfuscation ([#2699](https://github.com/getsentry/sentry-java/pull/2699))
  - This fixes AGP 8.+ incompatibility, where full R8 mode is enforced

### Dependencies

- Bump Gradle from v8.1.0 to v8.1.1 ([#2666](https://github.com/getsentry/sentry-java/pull/2666))
  - [changelog](https://github.com/gradle/gradle/blob/master release-test/CHANGELOG.md#v811)
  - [diff](https://github.com/gradle/gradle/compare/v8.1.0...v8.1.1)
- Bump Native SDK from v0.6.1 to v0.6.2 ([#2689](https://github.com/getsentry/sentry-java/pull/2689))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#062)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.6.1...0.6.2)

## 6.18.1

### Fixes

- Fix crash when Sentry SDK is initialized more than once ([#2679](https://github.com/getsentry/sentry-java/pull/2679))
- Track a ttfd span per Activity ([#2673](https://github.com/getsentry/sentry-java/pull/2673))

## 6.18.0

### Features

- Attach Trace Context when an ANR is detected (ANRv1) ([#2583](https://github.com/getsentry/sentry-java/pull/2583))
- Make log4j2 integration compatible with log4j 3.0 ([#2634](https://github.com/getsentry/sentry-java/pull/2634))
    - Instead of relying on package scanning, we now use an annotation processor to generate `Log4j2Plugins.dat`
- Create `User` and `Breadcrumb` from map ([#2614](https://github.com/getsentry/sentry-java/pull/2614))
- Add `sent_at` to envelope header item ([#2638](https://github.com/getsentry/sentry-java/pull/2638))

### Fixes

- Fix timestamp intervals of PerformanceCollectionData in profiles ([#2648](https://github.com/getsentry/sentry-java/pull/2648))
- Fix timestamps of PerformanceCollectionData in profiles ([#2632](https://github.com/getsentry/sentry-java/pull/2632))
- Fix missing propagateMinConstraints flag for SentryTraced ([#2637](https://github.com/getsentry/sentry-java/pull/2637))
- Fix potential SecurityException thrown by ConnectivityManager on Android 11 ([#2653](https://github.com/getsentry/sentry-java/pull/2653))
- Fix aar artifacts publishing for Maven ([#2641](https://github.com/getsentry/sentry-java/pull/2641))

### Dependencies
- Bump Kotlin compile version from v1.6.10 to 1.8.0 ([#2563](https://github.com/getsentry/sentry-java/pull/2563))
- Bump Compose compile version from v1.1.1 to v1.3.0 ([#2563](https://github.com/getsentry/sentry-java/pull/2563))
- Bump AGP version from v7.3.0 to v7.4.2 ([#2574](https://github.com/getsentry/sentry-java/pull/2574))
- Bump Gradle from v7.6.0 to v8.0.2 ([#2563](https://github.com/getsentry/sentry-java/pull/2563))
    - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v802)
    - [diff](https://github.com/gradle/gradle/compare/v7.6.0...v8.0.2)
- Bump Gradle from v8.0.2 to v8.1.0 ([#2650](https://github.com/getsentry/sentry-java/pull/2650))
  - [changelog](https://github.com/gradle/gradle/blob/master/CHANGELOG.md#v810)
  - [diff](https://github.com/gradle/gradle/compare/v8.0.2...v8.1.0)

## 6.17.0

### Features

- Add `name` and `geo` to `User` ([#2556](https://github.com/getsentry/sentry-java/pull/2556)) 
- Add breadcrumbs on network changes ([#2608](https://github.com/getsentry/sentry-java/pull/2608))
- Add time-to-initial-display and time-to-full-display measurements to Activity transactions ([#2611](https://github.com/getsentry/sentry-java/pull/2611))
- Read integration list written by sentry gradle plugin from manifest ([#2598](https://github.com/getsentry/sentry-java/pull/2598))
- Add Logcat adapter ([#2620](https://github.com/getsentry/sentry-java/pull/2620))
- Provide CPU count/frequency data as device context ([#2622](https://github.com/getsentry/sentry-java/pull/2622))

### Fixes

- Trim time-to-full-display span if reportFullyDisplayed API is never called ([#2631](https://github.com/getsentry/sentry-java/pull/2631))
- Fix Automatic UI transactions having wrong durations ([#2623](https://github.com/getsentry/sentry-java/pull/2623))
- Fix wrong default environment in Session ([#2610](https://github.com/getsentry/sentry-java/pull/2610))
- Pass through unknown sentry baggage keys into SentryEnvelopeHeader ([#2618](https://github.com/getsentry/sentry-java/pull/2618))
- Fix missing null check when removing lifecycle observer ([#2625](https://github.com/getsentry/sentry-java/pull/2625))

### Dependencies

- Bump Native SDK from v0.6.0 to v0.6.1 ([#2629](https://github.com/getsentry/sentry-java/pull/2629))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#061)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.6.0...0.6.1)

## 6.16.0

### Features

- Improve versatility of exception resolver component for Spring with more flexible API for consumers. ([#2577](https://github.com/getsentry/sentry-java/pull/2577))
- Automatic performance instrumentation for WebFlux ([#2597](https://github.com/getsentry/sentry-java/pull/2597))
  - You can enable it by adding `sentry.enable-tracing=true` to your `application.properties`
- The Spring Boot integration can now be configured to add the `SentryAppender` to specific loggers instead of the `ROOT` logger ([#2173](https://github.com/getsentry/sentry-java/pull/2173))
  - You can specify the loggers using `"sentry.logging.loggers[0]=foo.bar` and `"sentry.logging.loggers[1]=baz` in your `application.properties`
- Add capabilities to track Jetpack Compose composition/rendering time ([#2507](https://github.com/getsentry/sentry-java/pull/2507))
- Adapt span op and description for graphql to fit spec ([#2607](https://github.com/getsentry/sentry-java/pull/2607))

### Fixes

- Fix timestamps of slow and frozen frames for profiles ([#2584](https://github.com/getsentry/sentry-java/pull/2584))
- Deprecate reportFullDisplayed in favor of reportFullyDisplayed ([#2585](https://github.com/getsentry/sentry-java/pull/2585))
- Add mechanism for logging integrations and update spring mechanism types ([#2595](https://github.com/getsentry/sentry-java/pull/2595))
	- NOTE: If you're using these mechanism types (`HandlerExceptionResolver`, `SentryWebExceptionHandler`) in your dashboards please update them to use the new types.
- Filter out session cookies sent by Spring and Spring Boot integrations ([#2593](https://github.com/getsentry/sentry-java/pull/2593))
  - We filter out some common cookies like JSESSIONID
  - We also read the value from `server.servlet.session.cookie.name` and filter it out
- No longer send event / transaction to Sentry if `beforeSend` / `beforeSendTransaction` throws ([#2591](https://github.com/getsentry/sentry-java/pull/2591))
- Add version to sentryClientName used in auth header ([#2596](https://github.com/getsentry/sentry-java/pull/2596))
- Keep integration names from being obfuscated ([#2599](https://github.com/getsentry/sentry-java/pull/2599))
- Change log level from INFO to WARN for error message indicating a failed Log4j2 Sentry.init ([#2606](https://github.com/getsentry/sentry-java/pull/2606))
  - The log message was often not visible as our docs suggest a minimum log level of WARN
- Fix session tracking on Android ([#2609](https://github.com/getsentry/sentry-java/pull/2609))
  - Incorrect number of session has been sent. In addition, some of the sessions were not properly ended, messing up Session Health Metrics.

### Dependencies

- Bump `opentelemetry-sdk` to `1.23.1` and `opentelemetry-javaagent` to `1.23.0` ([#2590](https://github.com/getsentry/sentry-java/pull/2590))
- Bump Native SDK from v0.5.4 to v0.6.0 ([#2545](https://github.com/getsentry/sentry-java/pull/2545))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#060)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.5.4...0.6.0)

## 6.15.0

### Features

- Adjust time-to-full-display span if reportFullDisplayed is called too early ([#2550](https://github.com/getsentry/sentry-java/pull/2550))
- Add `enableTracing` option ([#2530](https://github.com/getsentry/sentry-java/pull/2530))
    - This change is backwards compatible. The default is `null` meaning existing behaviour remains unchanged (setting either `tracesSampleRate` or `tracesSampler` enables performance).
    - If set to `true`, performance is enabled, even if no `tracesSampleRate` or `tracesSampler` have been configured.
    - If set to `false` performance is disabled, regardless of `tracesSampleRate` and `tracesSampler` options.
- Detect dependencies by listing MANIFEST.MF files at runtime ([#2538](https://github.com/getsentry/sentry-java/pull/2538))
- Report integrations in use, report packages in use more consistently ([#2179](https://github.com/getsentry/sentry-java/pull/2179))
- Implement `ThreadLocalAccessor` for propagating Sentry hub with reactor / WebFlux ([#2570](https://github.com/getsentry/sentry-java/pull/2570))
  - Requires `io.micrometer:context-propagation:1.0.2+` as well as Spring Boot 3.0.3+
  - Enable the feature by setting `sentry.reactive.thread-local-accessor-enabled=true`
  - This is still considered experimental. Once we have enough feedback we may turn this on by default.
  - Checkout the sample here: https://github.com/getsentry/sentry-java/tree/main/sentry-samples/sentry-samples-spring-boot-webflux-jakarta
  - A new hub is now cloned from the main hub for every request

### Fixes

- Leave `inApp` flag for stack frames undecided in SDK if unsure and let ingestion decide instead ([#2547](https://github.com/getsentry/sentry-java/pull/2547))
- Allow `0.0` error sample rate ([#2573](https://github.com/getsentry/sentry-java/pull/2573))
- Fix memory leak in WebFlux related to an ever growing stack ([#2580](https://github.com/getsentry/sentry-java/pull/2580))
- Use the same hub in WebFlux exception handler as we do in WebFilter ([#2566](https://github.com/getsentry/sentry-java/pull/2566))
- Switch upstream Jetpack Compose dependencies to `compileOnly` in `sentry-compose-android` ([#2578](https://github.com/getsentry/sentry-java/pull/2578))
  - NOTE: If you're using Compose Navigation/User Interaction integrations, make sure to have the following dependencies on the classpath as we do not bring them in transitively anymore:
    - `androidx.navigation:navigation-compose:`
    - `androidx.compose.runtime:runtime:`
    - `androidx.compose.ui:ui:`

## 6.14.0

### Features

- Add time-to-full-display span to Activity auto-instrumentation ([#2432](https://github.com/getsentry/sentry-java/pull/2432))
- Add `main` flag to threads and `in_foreground` flag for app contexts  ([#2516](https://github.com/getsentry/sentry-java/pull/2516))

### Fixes

- Ignore Shutdown in progress when closing ShutdownHookIntegration ([#2521](https://github.com/getsentry/sentry-java/pull/2521))
- Fix app start span end-time is wrong if SDK init is deferred ([#2519](https://github.com/getsentry/sentry-java/pull/2519))
- Fix invalid session creation when app is launched in background ([#2543](https://github.com/getsentry/sentry-java/pull/2543))

## 6.13.1

### Fixes

- Fix transaction performance collector oom ([#2505](https://github.com/getsentry/sentry-java/pull/2505))
- Remove authority from URLs sent to Sentry ([#2366](https://github.com/getsentry/sentry-java/pull/2366))
- Fix `sentry-bom` containing incorrect artifacts ([#2504](https://github.com/getsentry/sentry-java/pull/2504))

### Dependencies

- Bump Native SDK from v0.5.3 to v0.5.4 ([#2500](https://github.com/getsentry/sentry-java/pull/2500))
  - [changelog](https://github.com/getsentry/sentry-native/blob/master/CHANGELOG.md#054)
  - [diff](https://github.com/getsentry/sentry-native/compare/0.5.3...0.5.4)

## 6.13.0

### Features

- Send cpu usage percentage in profile payload ([#2469](https://github.com/getsentry/sentry-java/pull/2469))
- Send transaction memory stats in profile payload ([#2447](https://github.com/getsentry/sentry-java/pull/2447))
- Add cpu usage collection ([#2462](https://github.com/getsentry/sentry-java/pull/2462))
- Improve ANR implementation: ([#2475](https://github.com/getsentry/sentry-java/pull/2475))
  - Add `abnormal_mechanism` to sessions for ANR rate calculation
  - Always attach thread dump to ANR events
  - Distinguish between foreground and background ANRs
- Improve possible date precision to 10 μs ([#2451](https://github.com/getsentry/sentry-java/pull/2451))

### Fixes

- Fix performance collector setup called in main thread ([#2499](https://github.com/getsentry/sentry-java/pull/2499))
- Expand guard against CVE-2018-9492 "Privilege Escalation via Content Provider" ([#2482](https://github.com/getsentry/sentry-java/pull/2482))
- Prevent OOM by disabling TransactionPerformanceCollector for now ([#2498](https://github.com/getsentry/sentry-java/pull/2498))

## 6.12.1

### Fixes

- Create timer in `TransactionPerformanceCollector` lazily ([#2478](https://github.com/getsentry/sentry-java/pull/2478))

## 6.12.0

### Features

- Attach View Hierarchy to the errored/crashed events ([#2440](https://github.com/getsentry/sentry-java/pull/2440))
- Collect memory usage in transactions ([#2445](https://github.com/getsentry/sentry-java/pull/2445))
- Add `traceOptionsRequests` option to disable tracing of OPTIONS requests ([#2453](https://github.com/getsentry/sentry-java/pull/2453))
- Extend list of HTTP headers considered sensitive ([#2455](https://github.com/getsentry/sentry-java/pull/2455))

### Fixes

- Use a single TransactionPerfomanceCollector ([#2464](https://github.com/getsentry/sentry-java/pull/2464))
- Don't override sdk name with Timber ([#2450](https://github.com/getsentry/sentry-java/pull/2450))
- Set transactionNameSource to CUSTOM when setting transaction name ([#2405](https://github.com/getsentry/sentry-java/pull/2405))
- Guard against CVE-2018-9492 "Privilege Escalation via Content Provider" ([#2466](https://github.com/getsentry/sentry-java/pull/2466))

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

Breaking Changes:
- The boolean parameter `samplingDecision` in the `TransactionContext` constructor has been replaced with a `TracesSamplingDecision` object. Feel free to ignore the `@ApiStatus.Internal` in this case.

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
