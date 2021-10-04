# Changelog

## Unreleased

## 5.2.1

* Feat: Add isCrashedLastRun support (#1739)
* Fix: Handle exception if Context.registerReceiver throws (#1747)
* Feat: Attach Java vendor and version to events and transactions (#1703)

## 5.2.0

* Feat: Allow setting proguard via Options and/or external resources (#1728)
* Feat: Add breadcrumbs for the Apollo integration (#1726)
* Fix: Don't set lastEventId for transactions (#1727)
* Fix: ActivityLifecycleIntegration#appStartSpan memory leak (#1732)

## 5.2.0-beta.3

* Fix: Check at runtime if AndroidX.Core is available (#1718)
* Feat: Add "data" to spans (#1717)
* Fix: Should not capture unfinished transaction (#1719)

## 5.2.0-beta.2

* Bump AGP to 7.0.2 (#1650)
* Fix: drop spans in BeforeSpanCallback. (#1713)

## 5.2.0-beta.1

* Feat: Add tracestate HTTP header support (#1683)
* Feat: Add option to filter which origins receive tracing headers (#1698)
* Feat: Include unfinished spans in transaction (#1699)
* Fix: Move tags from transaction.contexts.trace.tags to transaction.tags (#1700)
* Feat: Add static helpers for creating breadcrumbs (#1702)
* Feat: Performance support for Android Apollo (#1705)

Breaking changes:

* Updated proguard keep rule for enums, which affects consumer application code (#1694)

## 5.1.2

* Fix: Servlet 3.1 compatibility issue (#1681)
* Fix: Do not drop Contexts key if Collection, Array or Char (#1680)

## 5.1.1

* Fix: Remove onActivityPreCreated call in favor of onActivityCreated (#1661)
* Fix: Do not crash if SENSOR_SERVICE throws (#1655)
* Feat: Add support for async methods in Spring MVC (#1652)
* Feat: Add secondary constructor taking IHub to SentryOkHttpInterceptor (#1657)
* Feat: Merge external map properties (#1656)
* Fix: Make sure scope is popped when processing request results in exception (#1665)

## 5.1.0

* Feat: Spring WebClient integration (#1621)
* Feat: OpenFeign integration (#1632)
* Feat: Add more convenient way to pass BeforeSpanCallback in OpenFeign integration (#1637)
* Bump: sentry-native to 0.4.12 (#1651)

## 5.1.0-beta.9

- No documented changes.

## 5.1.0-beta.8

* Feat: Generate Sentry BOM (#1486)

## 5.1.0-beta.7

* Feat: Slow/Frozen frames metrics (#1609)

## 5.1.0-beta.6

* Fix: set min sdk version of sentry-android-fragment to API 14 (#1608)
* Fix: Ser/Deser of the UserFeedback from cached envelope (#1611)
* Feat: Add request body extraction for Spring MVC integration (#1595)

## 5.1.0-beta.5

* Make SentryAppender non-final for Log4j2 and Logback (#1603) 
* Fix: Do not throw IAE when tracing header contain invalid trace id (#1605)

## 5.1.0-beta.4

* Update sentry-native to 0.4.11 (#1591)

## 5.1.0-beta.3

* Feat: Spring Webflux integration (#1529)

## 5.1.0-beta.2

* Fix: Handling missing Spring Security on classpath on Java 8 (#1552)
* Feat: Support transaction waiting for children to finish. (#1535) 
* Feat: Capture logged marker in log4j2 and logback appenders (#1551)
* Feat: Allow clearing of attachments in the scope (#1562)
* Fix: Use a different method to get strings from JNI, and avoid excessive Stack Space usage. (#1214)
* Fix: Add data field to SentrySpan (#1555)
* Fix: Clock drift issue when calling DateUtils#getDateTimeWithMillisPrecision (#1557)
* Feat: Set mechanism type in SentryExceptionResolver (#1556)
* Feat: Perf. for fragments (#1528)
* Ref: Prefer snake case for HTTP integration data keys (#1559)
* Fix: Assign lastEventId only if event was queued for submission (#1565)

## 5.1.0-beta.1

* Feat: Measure app start time (#1487)
* Feat: Automatic breadcrumbs logging for fragment lifecycle (#1522) 

## 5.0.1

* Fix: Sources and Javadoc artifacts were mixed up (#1515)

## 5.0.0

This release brings many improvements but also new features:

* OkHttp Interceptor for Android (#1330)
* GraalVM Native Image Compatibility (#1329)
* Add option to ignore exceptions by type (#1352)
* Enrich transactions with device contexts (#1430) (#1469)
* Better interoperability with Kotlin null-safety (#1439) and (#1462)
* Add coroutines support (#1479)
* OkHttp callback for Customising the Span (#1478)
* Add breadcrumb in Spring RestTemplate integration (#1481)

Breaking changes:

* Migration Guide for [Java](https://docs.sentry.io/platforms/java/migration/)
* Migration Guide for [Android](https://docs.sentry.io/platforms/android/migration/)

Other fixes:

* Fix: Add attachmentType to envelope ser/deser. (#1504)

Thank you:

* @maciejwalkowiak for coding most of it.

## 5.0.0-beta.7

* Ref: Deprecate SentryBaseEvent#getOriginThrowable and add SentryBaseEvent#getThrowableMechanism (#1502)
* Fix: Graceful Shutdown flushes event instead of Closing SDK (#1500)
* Fix: Do not append threads that come from the EnvelopeFileObserver (#1501)
* Ref: Deprecate cacheDirSize and add maxCacheItems (#1499)
* Fix: Append all threads if Hint is Cached but attachThreads is enabled (#1503)

## 5.0.0-beta.6

* Feat: Add secondary constructor to SentryOkHttpInterceptor (#1491)
* Feat: Add option to enable debug mode in Log4j2 integration (#1492)
* Ref: Replace clone() with copy constructor (#1496)

## 5.0.0-beta.5

* Feat: OkHttp callback for Customising the Span (#1478)
* Feat: Add breadcrumb in Spring RestTemplate integration (#1481)
* Fix: Cloning Stack (#1483)
* Feat: Add coroutines support (#1479)

## 5.0.0-beta.4

* Fix: Enrich Transactions with Context Data (#1469)
* Bump: Apache HttpClient to 5.0.4 (#1476)

## 5.0.0-beta.3

* Fix: handling immutable collections on SentryEvent and protocol objects (#1468)
* Fix: associate event with transaction when thrown exception is not a direct cause (#1463)
* Ref: nullability annotations to Sentry module (#1439) and (#1462)
* Fix: NPE when adding Context Data with null values for log4j2 (#1465)

## 5.0.0-beta.2

* Fix: sentry-android-timber package sets sentry.java.android.timber as SDK name (#1456)
* Fix: When AppLifecycleIntegration is closed, it should remove observer using UI thread (#1459)
* Bump: AGP to 4.2.0 (#1460)

Breaking Changes:

* Remove: Settings.Secure.ANDROID_ID in favor of generated installationId (#1455)
* Rename: enableSessionTracking to enableAutoSessionTracking (#1457)

## 5.0.0-beta.1

* Fix: Activity tracing auto instrumentation for Android API < 29 (#1402)
* Fix: use connection and read timeouts in ApacheHttpClient based transport (#1397)
* Ref: Refactor converting HttpServletRequest to Sentry Request in Spring integration (#1387)
* Fix: set correct transaction status for unhandled exceptions in SentryTracingFilter (#1406)
* Fix: handle network errors in SentrySpanClientHttpRequestInterceptor (#1407)
* Fix: set scope on transaction (#1409)
* Fix: set status and associate events with transactions (#1426)
* Fix: Do not set free memory and is low memory fields when it's a NDK hard crash (#1399)
* Fix: Apply user from the scope to transaction (#1424)
* Fix: Pass maxBreadcrumbs config. to sentry-native (#1425)
* Fix: Run event processors and enrich transactions with contexts (#1430)
* Bump: sentry-native to 0.4.9 (#1431)
* Fix: Set Span status for OkHttp integration (#1447)
* Fix: Set user on transaction in Spring & Spring Boot integrations (#1443)

## 4.4.0-alpha.2

* Feat: Add option to ignore exceptions by type (#1352)
* Fix: NPE when MDC contains null values (sentry-logback) (#1364)
* Fix: Avoid NPE when MDC contains null values (sentry-jul) (#1385)
* Feat: Sentry closes Android NDK and ShutdownHook integrations (#1358)
* Enhancement: Allow inheritance of SentryHandler class in sentry-jul package(#1367)
* Fix: Accept only non null value maps (#1368)
* Bump: Upgrade Apache HttpComponents Core to 5.0.3 (#1375)
* Enhancement: Make NoOpHub public (#1379)
* Fix: Do not bind transactions to scope by default. (#1376)
* Fix: Hub thread safety (#1388)
* Fix: SentryTransactionAdvice should operate on the new scope (#1389)
* Feat: configure max spans per transaction (#1394)

## 4.4.0-alpha.1

* Bump: sentry-native to 0.4.8
* Feat: Add an overload for `startTransaction` that sets the created transaction to the Scope #1313
* Ref: Separate user facing and protocol classes in the Performance feature (#1304)
* Feat: Set SDK version on Transactions (#1307)
* Fix: Use logger set on SentryOptions in GsonSerializer (#1308)
* Fix: Use the bindToScope correctly
* Feat: GraalVM Native Image Compatibility (#1329)
* Fix: Allow 0.0 to be set on tracesSampleRate (#1328)
* Fix: set "java" platform to transactions #1332
* Feat: Add OkHttp client application interceptor (#1330)
* Fix: Allow disabling tracing through SentryOptions (#1337)

## 4.3.0

* Fix: setting in-app-includes from external properties (#1291)
* Fix: Initialize Sentry in Logback appender when DSN is not set in XML config (#1296)
* Fix: JUL integration SDK name (#1293)
* Feat: Activity tracing auto instrumentation

## 4.2.0

* Fix: Remove experimental annotation for Attachment #1257
* Fix: Mark stacktrace as snapshot if captured at arbitrary moment #1231
* Enchancement: Improve EventProcessor nullability annotations (#1229).
* Bump: sentry-native to 0.4.7
* Enchancement: Add ability to flush events synchronously.
* Fix: Disable Gson HTML escaping
* Enchancement: Support @SentrySpan and @SentryTransaction on classes and interfaces. (#1243)
* Enchancement: Do not serialize empty collections and maps (#1245)
* Ref: Optimize DuplicateEventDetectionEventProcessor performance (#1247).
* Ref: Prefix sdk.package names with io.sentry (#1249)
* Fix: Make the ANR Atomic flags immutable
* Enchancement: Integration interface better compatibility with Kotlin null-safety
* Enchancement: Simplify Sentry configuration in Spring integration (#1259)
* Enchancement: Simplify configuring Logback integration when environment variable with the DSN is not set (#1271)
* Fix: Prevent NoOpHub from creating heavy SentryOptions objects (#1272)
* Enchancement: Add Request to the Scope. #1270
* Fix: SentryTransaction#getStatus NPE (#1273)
* Enchancement: Optimize SentryTracingFilter when hub is disabled.
* Fix: Discard unfinished Spans before sending them over to Sentry (#1279)
* Fix: Interrupt the thread in QueuedThreadPoolExecutor (#1276)
* Fix: SentryTransaction#finish should not clear another transaction from the scope (#1278)

Breaking Changes:
* Enchancement: SentryExceptionResolver should not send handled errors by default (#1248).
* Ref: Simplify RestTemplate instrumentation (#1246)
* Enchancement: Add overloads for startTransaction taking op and description (#1244)

## 4.1.0

* Improve Kotlin compatibility for SdkVersion (#1213)
* Feat: Support logging via JUL (#1211)
* Fix: returning Sentry trace header from Span (#1217)
* Fix: Remove misleading error logs (#1222)

## 4.0.0

This release brings the Sentry Performance feature to Java SDK, Spring, Spring Boot, and Android integrations. Read more in the reference documentation:

- [Performance for Java](https://docs.sentry.io/platforms/java/performance/)
- [Performance for Spring](https://docs.sentry.io/platforms/java/guides/spring/)
- [Performance for Spring Boot](https://docs.sentry.io/platforms/java/guides/spring-boot/)
- [Performance for Android](https://docs.sentry.io/platforms/android/performance/)

### Other improvements:

#### Core:

- Improved loading external configuration:
  - Load `sentry.properties` from the application's current working directory (#1046)
  - Resolve `in-app-includes`, `in-app-excludes`, `tags`, `debug`, `uncaught.handler.enabled` parameters from the external configuration
- Set global tags on SentryOptions and load them from external configuration (#1066)
- Add support for attachments (#1082)
- Resolve `servername` from the localhost address
- Simplified transport configuration through setting `TransportFactory` instead of `ITransport` on SentryOptions (#1124)

#### Spring Boot:
- Add the ability to register multiple `OptionsConfiguration` beans (#1093)
- Initialize Logback after context refreshes (#1129)

#### Android:
- Add `isSideLoaded` and `installerStore` tags automatically (Where your App. was installed from eg Google Play, Amazon Store, downloaded APK, etc...)
- Bump: sentry-native to 0.4.6
- Bump: Gradle to 6.8.1 and AGP to 4.1.2

## 4.0.0-beta.1

* Feat: Add addToTransactions to Attachment (#1191)
* Enhancement: Support SENTRY_TRACES_SAMPLE_RATE conf. via env variables (#1171)
* Enhancement: Pass request to CustomSamplingContext in Spring integration (#1172)
* Ref: Set SpanContext on SentryTransaction to avoid potential NPE (#1173)
* Fix: Free Local Refs manually due to Android local ref. count limits
* Enhancement: Move `SentrySpanClientHttpRequestInterceptor` to Spring module (#1181)
* Enhancement: Add overload for `transaction/span.finish(SpanStatus)` (#1182)
* Fix: Bring back support for setting transaction name without ongoing transaction (#1183)
* Enhancement: Simplify registering traces sample callback in Spring integration (#1184)
* Enhancement: Polish Performance API (#1165)
* Enhancement: Set "debug" through external properties (#1186)
* Enhancement: Simplify Spring integration (#1188)
* Enhancement: Init overload with dsn (#1195)
* Enhancement: Enable Kotlin map-like access on CustomSamplingContext (#1192)
* Enhancement: Auto register custom ITransportFactory in Spring integration (#1194)
* Enhancement: Improve Kotlin property access in Performance API (#1193)
* Enhancement: Copy options tags to transactions (#1198)
* Enhancement: Add convenient method for accessing event's throwable (1202)

## 4.0.0-alpha.3

* Feat: Add maxAttachmentSize to SentryOptions (#1138)
* Feat: Drop invalid attachments (#1134)
* Ref: Make Attachment immutable (#1120)
* Fix inheriting sampling decision from parent (#1100)
* Fixes and Tests: Session serialization and deserialization
* Ref: using Calendar to generate Dates
* Fix: Exception only sets a stack trace if there are frames
* Feat: set isSideLoaded info tags
* Enhancement: Read tracesSampleRate from AndroidManifest
* Fix: Initialize Logback after context refreshes (#1129)
* Ref: Return NoOpTransaction instead of null (#1126)
* Fix: Do not crash when passing null values to @Nullable methods, eg User and Scope
* Ref: `ITransport` implementations are now responsible for executing request in asynchronous or synchronous way (#1118)
* Ref: Add option to set `TransportFactory` instead of `ITransport` on `SentryOptions` (#1124)
* Ref: Simplify ITransport creation in ITransportFactory (#1135) 
* Feat: Add non blocking Apache HttpClient 5 based Transport (#1136)
* Enhancement: Autoconfigure Apache HttpClient 5 based Transport in Spring Boot integration (#1143)
* Enhancement: Send user.ip_address = {{auto}} when sendDefaultPii is true (#1015)
* Fix: Resolving dashed properties from external configuration
* Feat: Read `uncaught.handler.enabled` property from the external configuration 
* Feat: Resolve servername from the localhost address
* Fix: Consider {{ auto }} as a default ip address (#1015) 
* Fix: Set release and environment on Transactions (#1152)
* Fix: Do not set transaction on the scope automatically   
* Enhancement: Automatically assign span context to captured events (#1156)
* Feat: OutboxSender supports all envelope item types #1158
* Enhancement: Improve ITransaction and ISpan null-safety compatibility (#1161)

## 4.0.0-alpha.2

* Feat: Add basic support for attachments (#1082)
* Fix: Remove method reference in SentryEnvelopeItem (#1091)
* Enhancement: Set transaction name on events and transactions sent using Spring integration (#1067)
* Fix: Set current thread only if there are no exceptions
* Enhancement: Set global tags on SentryOptions and load them from external configuration (#1066)
* Ref: Refactor resolving SpanContext for Throwable (#1068)
* Enhancement: Add API validator and remove deprecated methods
* Enhancement: Add more convenient method to start a child span (#1073)
* Enhancement: Autoconfigure traces callback in Spring Boot integration (#1074)
* Enhancement: Resolve in-app-includes and in-app-excludes parameters from the external configuration
* Enhancement: Make InAppIncludesResolver public (#1084)
* Ref: Change "op" to "operation" in @SentrySpan and @SentryTransaction
* Fix: SentryOptions creates GsonSerializer by default
* Enhancement: Add the ability to register multiple OptionsConfiguration beans (#1093)
* Fix: Append DebugImage list if event already has it
* Fix: Sort breadcrumbs by Date if there are breadcrumbs already in the event
* Feat: Database query tracing with datasource-proxy (#1095)

## 4.0.0-alpha.1

* Enhancement: Load `sentry.properties` from the application's current working directory (#1046)
* Ref: Refactor JSON deserialization (#1047)
* Feat: Performance monitoring (#971)
* Feat: Performance monitoring for Spring Boot applications (#971)

## 3.2.1

* Fix: Set current thread only if theres no exceptions (#1064)
* Fix: Append DebugImage list if event already has it (#1092)
* Fix: Sort breadcrumbs by Date if there are breadcrumbs already in the event (#1094)
* Fix: Free Local Refs manually due to Android local ref. count limits  (#1179)

## 3.2.0

* Bump: AGP 4.1.1 (#1040)
* Fix: use neutral Locale for String operations #1033
* Update to sentry-native 0.4.4 and fix shared library builds (#1039)
* Feat: Expose a Module (Debug images) Loader for Android thru sentry-native #1043
* Enhancement: Added java doc to protocol classes based on sentry-data-schemes project (#1045)
* Enhancement: Make SentryExceptionResolver Order configurable to not send handled web exceptions (#1008)
* Enhancement: Resolve HTTP Proxy parameters from the external configuration (#1028)
* Enhancement: Sentry NDK integration is compiled against default NDK version based on AGP's version #1048
* Fix: Clean up JNI code and properly free strings #1050
* Fix: set userId for hard-crashes if no user is set #1049

## 3.1.3

* Fix broken NDK integration on 3.1.2 (release failed on packaging a .so file)
* Increase max cached events to 30 (#1029)
* Normalize DSN URI (#1030)

## 3.1.2

* feat: Manually capturing User Feedback
* Enhancement: Set environment to "production" by default.
* Enhancement: Make public the Breadcrumb constructor that accepts a Date #1012
* ref: Validate event id on user feedback submission
 
## 3.1.1

* fix: Prevent Logback and Log4j2 integrations from re-initializing Sentry when Sentry is already initialized
* Enhancement: Bind logging related SentryProperties to Slf4j Level instead of Logback to improve Log4j2 compatibility
* fix: Make sure HttpServletRequestSentryUserProvider runs by default before custom SentryUserProvider beans
* fix: fix setting up Sentry in Spring Webflux annotation by changing the scope of Spring WebMvc related dependencies

## 3.1.0

* fix: Don't require `sentry.dsn` to be set when using `io.sentry:sentry-spring-boot-starter` and `io.sentry:sentry-logback` together #965
* Auto-Configure `inAppIncludes` in Spring Boot integration #966
* Enhancement: make getThrowable public and improve set contexts #967
* Bump: Android Gradle Plugin 4.0.2 #968
* Enhancement: accepted quoted values in properties from external configuration #972
* fix: remove chunked streaming mode #974
* fix: Android 11 + targetSdkVersion 30 crashes Sentry on start #977

## 3.0.0

## Java + Android

This release marks the re-unification of Java and Android SDK code bases.
It's based on the Android 2.0 SDK, which implements [Sentry's unified API](https://develop.sentry.dev/sdk/unified-api/).

Considerable changes were done, which include a lot of improvements. More are covered below, but the highlights are:

* Improved `log4j2` integration
  * Capture breadcrumbs for level INFO and higher
  * Raises event for ERROR and higher.
  * Minimum levels are configurable.
  * Optionally initializes the SDK via appender.xml
* Dropped support to `log4j`.
* Improved `logback` integration
  * Capture breadcrumbs for level INFO and higher
  * Raises event for ERROR and higher. 
  * Minimum levels are configurable.
  * Optionally initializes the SDK via appender.xml
  * Configurable via Spring integration if both are enabled
* Spring
  * No more duplicate events with Spring and logback
  * Auto initalizes if DSN is available
  * Configuration options available with auto complete
* Google App Engine support dropped

## What’s Changed

* Callback to validate SSL certificate (#944) 
* Attach stack traces enabled by default

### Android specific

* Release health enabled by default for Android
* Sync of Scopes for Java -> Native (NDK)
* Bump Sentry-Native v0.4.2
* Android 11 Support

[Android migration docs](https://docs.sentry.io/platforms/android/migration/#migrating-from-sentry-android-2x-to-sentry-android-3x)

### Java specific

* Unified API for Java SDK and integrations (Spring, Spring boot starter, Servlet, Logback, Log4j2)

New Java [docs](https://docs.sentry.io/platforms/java/) are live and being improved.

## Acquisition

Packages were released on [`bintray sentry-java`](https://dl.bintray.com/getsentry/sentry-java/io/sentry/), [`bintray sentry-android`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/)

## Where is the Java 1.7 code base?

The previous Java releases, are all available in this repository through the tagged releases.
## 3.0.0-beta.1

## What’s Changed

* feat: ssl support (#944) @ninekaw9 @marandaneto 
* feat: sync Java to C (#937) @bruno-garcia @marandaneto
* feat: Auto-configure Logback appender in Spring Boot integration. (#938) @maciejwalkowiak
* feat: Add Servlet integration. (#935) @maciejwalkowiak
* fix: Pop scope at the end of the request in Spring integration. (#936) @maciejwalkowiak
* bump: Upgrade Spring Boot to 2.3.4. (#932) @maciejwalkowiak
* fix: Do not set cookies when send pii is set to false. (#931) @maciejwalkowiak

Packages were released on [`bintray sentry-java`](https://dl.bintray.com/getsentry/sentry-java/io/sentry/), [`bintray sentry-android`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/)

We'd love to get feedback.

## 3.0.0-alpha.3

## What’s Changed

* Bump sentry-native to 0.4.2 (#926) @marandaneto
* feat: enable attach stack traces and disable attach threads by default (#921) @marandaneto
* fix: read sample rate correctly from manifest meta data (#923) @marandaneto
* ref: remove log level as RN do not use it anymore (#924) @marandaneto

Packages were released on [`bintray sentry-android`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/) and [`bintray sentry-java`](https://dl.bintray.com/getsentry/sentry-java/io/sentry/)

We'd love to get feedback.

## 3.0.0-alpha.2

TBD

Packages were released on [bintray](https://dl.bintray.com/getsentry/maven/io/sentry/)

> Note: This release marks the unification of the Java and Android Sentry codebases based on the core of the Android SDK (version 2.x).
Previous releases for the Android SDK (version 2.x) can be found on the now archived: https://github.com/getsentry/sentry-android/

## 3.0.0-alpha.1

## New releases will happen on a different repository:

https://github.com/getsentry/sentry-java

## What’s Changed

* feat: enable release health by default

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.3.1

## What’s Changed

* fix: add main thread checker for the app lifecycle integration (#525) @marandaneto
* Set correct migration link (#523) @fupduck
* Warn about Sentry re-initialization. (#521) @maciejwalkowiak
* Set SDK version in `MainEventProcessor`. (#513) @maciejwalkowiak
* Bump sentry-native to 0.4.0 (#512) @marandaneto
* Bump Gradle to 6.6 and fix linting issues (#510) @marandaneto
* fix(sentry-java): Contexts belong on the Scope (#504) @maciejwalkowiak
* Add tests for verifying scope changes thread isolation (#508) @maciejwalkowiak
* Set `SdkVersion` in default `SentryOptions` created in sentry-core module (#506) @maciejwalkowiak

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.3.0

## What’s Changed

* fix: converting UTC and ISO timestamp when missing Locale/TimeZone do not error (#505) @marandaneto
* Add console application sample. (#502) @maciejwalkowiak
* Log stacktraces in SystemOutLogger (#498) @maciejwalkowiak
* Add method to add breadcrumb with string parameter. (#501) @maciejwalkowiak
* Call `Sentry#close` on JVM shutdown. (#497) @maciejwalkowiak
* ref: sentry-core changes for console app (#473) @marandaneto

Obs: If you are using its own instance of `Hub`/`SentryClient` and reflection to set up the SDK to be usable within Libraries, this change may break your code, please fix the renamed classes.

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.2.2

## What’s Changed

* feat: add sdk to envelope header (#488) @marandaneto
* Bump plugin versions (#487) @marandaneto
* Bump: AGP 4.0.1 (#486) @marandaneto
* feat: log request if response code is not 200 (#484) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.2.1

## What’s Changed

* fix: Timber adds breadcrumb even if event level is < minEventLevel (#480) @marandaneto
* enhancement: Bump Gradle 6.5.1 (#479) @marandaneto
* fix: contexts serializer avoids reflection and fixes desugaring issue (#478) @marandaneto
* fix: clone session before sending to the transport (#474) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.2.0

## What’s Changed

* fix: negative session sequence if the date is before java date epoch (#471) @marandaneto
* fix: deserialise unmapped contexts values from envelope (#470) @marandaneto
* Bump: sentry-native 0.3.4 (#468) @marandaneto

* feat: timber integration (#464) @marandaneto

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

## What’s Changed

* fix: init native libs if available on SDK init (#461) @marandaneto
* Make JVM target explicit in sentry-core (#462) @dilbernd
* fix: timestamp with millis from react-native should be in UTC format (#456) @marandaneto
* Bump Gradle to 6.5 (#454) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.6

## What’s Changed

* fix: do not lookup sentry-debug-meta but instead load it directly (#445) @marandaneto
* fix: regression on v2.1.5 which can cause a crash on SDK init

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.5

This version has a severe bug and can cause a crash on SDK init

Please upgrade to https://github.com/getsentry/sentry-android/releases/tag/2.1.6

## 2.1.4

## What’s Changed

* bump: sentry-native to 0.3.1 (#440) @marandaneto
* fix: update last session timestamp (#437) @marandaneto
* feat: make gzip as default content encoding type (#433) @marandaneto
* enhancement: use AGP 4 features (#366) @marandaneto
* enhancement: Create GH Actions CI for Ubuntu/macOS (#403) @marandaneto
* enhancement: make root checker better and minimize false positive (#417) @marandaneto
* fix: filter trim memory breadcrumbs (#431) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.3

## What’s Changed

This fixes several critical bugs in sentry-android 2.0 and 2.1

* fix: Sentry.init register integrations after creating the main Hub instead of doing it in the main Hub ctor (#427) @marandaneto
* fix: make NoOpLogger public (#425) @marandaneto
* fix: ConnectivityChecker returns connection status and events are not trying to be sent if no connection. (#420) @marandaneto
* ref: thread pool executor is a single thread executor instead of scheduled thread executor (#422) @marandaneto
* fix: Add Abnormal to the Session.State enum as its part of the protocol (#424) @marandaneto
* Bump: Gradle to 6.4.1 (#419) @marandaneto

We recommend that you use sentry-android 2.1.3 over the initial release of sentry-android 2.0 and 2.1.

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.2

## What’s Changed

* fix: Phone state breadcrumbs require read_phone_state on older OS versions (#415) @marandaneto @bsergean
* fix: before raising ANR events, we check ProcessErrorStateInfo if available (#412) @marandaneto
* fix: send cached events to use a single thread executor (#405) @marandaneto
* enha: added options to configure http transport (#411) @marandaneto
* fix: initing SDK on AttachBaseContext (#409) @marandaneto
* fix: sessions can't be abnormal, but exited if not ended properly (#410) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.1

## What’s Changed

* fix: set missing release, environment and dist to sentry-native options (#404) @marandaneto
* fix: do not add automatic and empty sensor breadcrumbs (#401) @marandaneto
* enha: added missing getters on Breadcrumb and SentryEvent (#397) @marandaneto
* enha: bump sentry-native to 0.2.6 (#396) @marandaneto
* feat: add trim memory breadcrumbs (#395) @marandaneto
* enha: only set breadcrumb extras if not empty (#394) @marandaneto
* ref: removed Thread.sleep from LifecycleWatcher tests, using awaitility and DateProvider (#392) @marandaneto
* ref: added a DateTimeProvider for making retry after testable (#391) @marandaneto
* enha: BUMP Gradle to 6.4 (#390) @marandaneto
* enha: added samples of how to disable automatic breadcrumbs (#389) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.0

## What’s Changed

* Includes all the changes of 2.1.0 alpha, beta and RC
* fix when PhoneStateListener is not ready for use (#387) @marandaneto
* make ANR 5s by default (#388) @marandaneto
* fix: rate limiting by categories (#381) @marandaneto
* BUMP NDK to latest stable version 21.1.6352462 (#386) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.0.3

## What’s Changed

* patch from 2.1.0-alpha.2 - avoid crash if NDK throws UnsatisfiedLinkError (#344) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.
## 2.1.0-RC.1

## What’s Changed

* feat: Options for uncaught exception and make SentryOptions list Thread-Safe (#384) @marandaneto
* feat: automatic breadcrumbs for app, activity and sessions lifecycles and system events (#348) @marandaneto
* fix: if retry after header has empty categories, apply retry after to all of them (#377) @marandaneto
* fix: discard events and envelopes if cached and retry after (#378) @marandaneto
* add ScheduledForRemoval annotation to deprecated methods (#375) @marandaneto
* fix: Merge loadLibrary calls for sentry-native and clean up CMake files (#373) @Swatinem
* enha: make capture session and envelope internal (#372) @marandaneto
* fix: exceptions should be sorted oldest to newest (#370) @marandaneto
* fix: check external storage size even if its read only (#368) @marandaneto
* fix: wrong check for cellular network capability (#369) @marandaneto
* bump NDK to 21.0.6113669 (#367) @marandaneto
* bump AGP and add new make cmd to check for updates (#365) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.0-beta.2

## What’s Changed

* bump sentry-native to 0.2.4 (#364) @marandaneto
* update current session on session start after deleting previous session (#362) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.0-beta.1

## What’s Changed

* BUMP sentry-native to 0.2.3 (#357) @marandaneto
* check for androidx availability on runtime (#356) @marandaneto
* if theres a left over session file and its crashed, we should not overwrite its state (#354) @marandaneto
* session should be exited state if state was ok (#352) @marandaneto
* envelope has dedicated endpoint (#353) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.0-alpha.2

## What’s Changed

* change integration order for cached outbox events (#347) @marandaneto
* avoid crash if NDK throws UnsatisfiedLinkError (#344) @marandaneto
* Avoid getting a threadlocal twice. (#339) @metlos
* removing session tracking guard on hub and client (#338) @marandaneto
* bump agp to 3.6.2 (#336) @marandaneto
* fix racey ANR integration (#332) @marandaneto
* logging envelopes path when possible instead of nullable id (#331) @marandaneto
* renaming transport gate method (#330) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.1.0-alpha.1

Release of Sentry's new SDK for Android.

## What’s Changed

* BUMP sentry-native to 0.2.2 (#305) @Swatinem
* ANR report should have 'was active=yes' on the dashboard (#299) @marandaneto
* NDK events apply scoped data (#322) @marandaneto
* fix missing App's info (#315) @marandaneto
* buffered writers/readers - otimizations (#311) @marandaneto
* Add a StdoutTransport (#310) @mike-burns
* boot time should be UTC (#309) @marandaneto
* implementing new retry after protocol (#306) @marandaneto
* make transport result public (#300) @marandaneto
* release health @marandaneto @bruno-garcia 

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.0.2

Release of Sentry's new SDK for Android.

## What’s Changed

* BUMP AGP to 3.6.1 (#285) @marandaneto
* MavenCentral support (#284) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/sentry-android/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/) and [`mavenCentral`](https://repo.maven.apache.org/maven2/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.0.1

Release of Sentry's new SDK for Android.

## What’s Changed

* Add the default serverName to SentryOptions and use it in MainEventProcessor (#279) @metlos
* set current threadId when there's no mechanism set (#277) @marandaneto
* feat: attach threads/stacktraces (#267) @marandaneto
* fix: preview package manager (#269) @bruno-garcia

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.0.0

Release of Sentry's new SDK for Android.

New features not offered by (1.7.x):

* NDK support
  * Captures crashes caused by native code
  * Access to the [`sentry-native` SDK](https://github.com/getsentry/sentry-native/) API by your native (C/C++/Rust code/..).
* Automatic init (just add your `DSN` to the manifest)
   * Proguard rules are added automatically
   * Permission (Internet) is added automatically
* Uncaught Exceptions might be captured even before the app restarts
* Sentry's Unified API.
* More context/device information
* Packaged as `aar`
* Frames from the app automatically marked as `InApp=true` (stack traces in Sentry highlights them by default).
* Complete Sentry Protocol available.
* All threads and their stack traces are captured.
* Sample project in this repo to test many features (segfault, uncaught exception, ANR...)

Features from the current SDK like `ANR` are also available (by default triggered after 4 seconds).

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback.

## 2.0.0-rc04

Release of Sentry's new SDK for Android.

## What’s Changed

* fix: breacrumb.data to string,object, Add LOG level (#264) @HazAT
* read release conf. on manifest (#266) @marandaneto
* Support mills timestamp format (#263) @marandaneto
* adding logs to installed integrations (#265) @marandaneto
* feat: Take sampleRate from metadata (#262) @bruno-garcia

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.30](https://github.com/getsentry/sentry-java/releases/tag/v1.7.30)
## 2.0.0-rc03

Release of Sentry's new SDK for Android.

## What’s Changed

* fixes #259 - NPE check on getExternalFilesDirs items. (#260) @marandaneto
* fix strictMode typo (#258) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.30](https://github.com/getsentry/sentry-java/releases/tag/v1.7.30)
## 2.0.0-rc02

Release of Sentry's new SDK for Android.

## What’s Changed

* update ndk for new sentry-native version (#235) @Swatinem @marandaneto
* make integrations public (#256) @marandaneto
* BUMP build-tools (#255) @marandaneto
* added javadocs to scope and its dependencies (#253) @marandaneto
* build all ABIs (#254) @marandaneto
* moving back ANR timeout from long to int param. (#252) @marandaneto
* feat: Hub mode configurable (#247) @bruno-garcia
* Added HubAdapter to call Sentry static methods from Integrations (#250) @marandaneto
* new Release format (#242) @marandaneto
* Javadocs for SentryOptions (#246) @marandaneto
* non-app is already inApp excluded by default. (#244) @marandaneto
* added remove methods (tags/extras) to the sentry static class (#243) @marandaneto
* fix if symlink exists for sentry-native (#241) @marandaneto
* clone method - race condition free (#226) @marandaneto
* refactoring breadcrumbs callback (#239) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.30](https://github.com/getsentry/sentry-java/releases/tag/v1.7.30)

## 2.0.0-rc01

Release of Sentry's new SDK for Android.

## What’s Changed

* Honor RetryAfter (#236) @marandaneto
* Add tests for SentryValues (#238) @philipphofmann
* added remove methods for Scope data (#237) @marandaneto
* do not set frames if there's none (#234) @marandaneto
* always call interrupt after InterruptedException (#232) @marandaneto
* more device context (deviceId, connectionType and language) (#229) @marandaneto
* mark as current thread if its the main thread (#228) @marandaneto
* added a few java docs (Sentry, Hub and SentryClient) (#223) @marandaneto
* implemented diagnostic logger (#218) @marandaneto
* fix lgtm alerts (#219) @marandaneto
* written unit tests to ANR integration (#215) @marandaneto
* added blog posts to README (#214) @marandaneto
* added event processors to scope (#209) @marandaneto
* Raise code coverage for Dsn to 100% (#212) @philipphofmann
* Remove redundant times(1) for Mockito.verify (#211) @philipphofmann
* added android transport gate (#206) @marandaneto
* transport may be set on options (#203) @marandaneto
* dist may be set on options (#204) @marandaneto
* added executor for caching values out of the main thread (#201) @marandaneto
* throw an exception if DSN is not set (#200) @marandaneto
* migration guide markdown (#197) @marandaneto

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.29](https://github.com/getsentry/sentry-java/releases/tag/v1.7.29)

## 2.0.0-beta02

Release of Sentry's new SDK for Android.

* fix Android bug on API 24 and 25 about getting current threads and stack traces (#194)
* addBreadcrumb overloads #196 and #198

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)
## 2.0.0-beta01

Release of Sentry's new SDK for Android.

* ref: ANR doesn't set handled flag #186
* SDK final review (#183)
* ref: Drop errored in favor of crashed (#187)
* workaround android_id (#185)
* renamed sampleRate (#191)
* making timestamp package-private or test-only (#190)
* Split event processor in Device/App data (#180)

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)

## 2.0.0-alpha09

Release of Sentry's new SDK for Android.

* fix: DSN parsing (#165)
* BUMP: sentry-native with message object instead of a string (#172)
* Don't avoid exception type minification (#166)
* make Gson retro compatible with older versions of AGP (#177)
* adding nativeBundle plugin (#161)
* adding scope methods to sentry static class (#179)

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)

## 2.0.0-alpha08

Release of Sentry's new SDK for Android.

* DebugId endianness (#162)
* executed beforeBreadcrumb also for scope (#160)
* benefit of manifest merging when minSdk (#159)
* add method to captureMessage with level (#157)
* listing assets file on the wrong dir (#156)

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)

## 2.0.0-alpha07

Third release of Sentry's new SDK for Android.

*  Fixed release for jcenter and bintray

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)

## 2.0.0-alpha06

Second release of Sentry's new SDK for Android.

* Fixed a typo on pom generation.

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)

## 2.0.0-alpha05

First release of Sentry's new SDK for Android.

New features not offered by our current (1.7.x), stable SDK are:

* NDK support
  * Captures crashes caused by native code
  * Access to the [`sentry-native` SDK](https://github.com/getsentry/sentry-native/) API by your native (C/C++/Rust code/..).
* Automatic init (just add your `DSN` to the manifest)
   * Proguard rules are added automatically
   * Permission (Internet) is added automatically
* Uncaught Exceptions might be captured even before the app restarts
* Unified API which include scopes etc.
* More context/device information
* Packaged as `aar`
* Frames from the app automatically marked as `InApp=true` (stack traces in Sentry highlights them by default).
* Complete Sentry Protocol available.
* All threads and their stack traces are captured.
* Sample project in this repo to test many features (segfault, uncaught exception, scope)

Features from the current SDK like `ANR` are also available (by default triggered after 4 seconds).

Packages were released on [`bintray`](https://dl.bintray.com/getsentry/sentry-android/io/sentry/), [`jcenter`](https://jcenter.bintray.com/io/sentry/sentry-android/)

We'd love to get feedback and we'll work in getting the GA `2.0.0` out soon.
Until then, the [stable SDK offered by Sentry is at version 1.7.28](https://github.com/getsentry/sentry-java/releases/tag/v1.7.28)
