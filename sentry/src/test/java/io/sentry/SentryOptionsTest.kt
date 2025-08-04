package io.sentry

import io.sentry.SentryOptions.RequestSize
import io.sentry.util.StringUtils
import java.io.File
import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SentryOptionsTest {
  @Test
  fun `when options is initialized, logger is not null`() {
    assertNotNull(SentryOptions().logger)
  }

  @Test
  fun `when logger is set to null, logger getter returns not null`() {
    val options = SentryOptions()
    options.setLogger(null)
    assertNotNull(options.logger)
  }

  @Test
  fun `when options is initialized, diagnostic level is default`() {
    assertEquals(SentryOptions.DEFAULT_DIAGNOSTIC_LEVEL, SentryOptions().diagnosticLevel)
  }

  @Test
  fun `when diagnostic is set to null, diagnostic getter returns no default`() {
    val options = SentryOptions()
    options.setDiagnosticLevel(null)
    assertEquals(SentryOptions.DEFAULT_DIAGNOSTIC_LEVEL, SentryOptions().diagnosticLevel)
  }

  @Test
  fun `when options is initialized, debug is false`() {
    assertFalse(SentryOptions().isDebug)
  }

  @Test
  fun `when options is initialized, integrations contain UncaughtExceptionHandlerIntegration`() {
    assertTrue(SentryOptions().integrations.any { it is UncaughtExceptionHandlerIntegration })
  }

  @Test
  fun `when options is initialized, integrations contain ShutdownHookIntegration`() {
    assertTrue(SentryOptions().integrations.any { it is ShutdownHookIntegration })
  }

  @Test
  fun `when options is initialized, default maxBreadcrumb is 100`() =
    assertEquals(100, SentryOptions().maxBreadcrumbs)

  @Test
  fun `when setMaxBreadcrumb is called, overrides default`() {
    val options = SentryOptions()
    options.maxBreadcrumbs = 1
    assertEquals(1, options.maxBreadcrumbs)
  }

  @Test
  fun `when options is initialized, default sampling is null`() =
    assertNull(SentryOptions().sampleRate)

  @Test
  fun `when setSampling is called, overrides default`() {
    val options = SentryOptions()
    options.sampleRate = 0.5
    assertEquals(0.5, options.sampleRate)
  }

  @Test
  fun `when setSampling is called with null, disables it`() {
    val options = SentryOptions()
    options.sampleRate = null
    assertNull(options.sampleRate)
  }

  @Test
  fun `when setSampling is set to higher than 1_0, setter throws`() {
    assertFailsWith<IllegalArgumentException> { SentryOptions().sampleRate = 1.0000000000001 }
  }

  @Test
  fun `when setSampling is set to lower than 0, setter throws`() {
    assertFailsWith<IllegalArgumentException> { SentryOptions().sampleRate = -0.0000000000001 }
  }

  @Test
  fun `when setTracesSampleRate is set to exactly 0, value is set`() {
    val options = SentryOptions().apply { this.tracesSampleRate = 0.0 }
    assertEquals(0.0, options.tracesSampleRate)
  }

  @Test
  fun `when setTracesSampleRate is set to higher than 1_0, setter throws`() {
    assertFailsWith<IllegalArgumentException> { SentryOptions().tracesSampleRate = 1.0000000000001 }
  }

  @Test
  fun `when setTracesSampleRate is set to lower than 0, setter throws`() {
    assertFailsWith<IllegalArgumentException> {
      SentryOptions().tracesSampleRate = -0.0000000000001
    }
  }

  @Test
  fun `when tracesSampleRate is set tracing is considered enabled`() {
    val options = SentryOptions().apply { this.tracesSampleRate = 1.0 }

    assertTrue(options.isTracingEnabled)
  }

  @Test
  fun `when tracesSampler is set tracing is considered enabled`() {
    val options =
      SentryOptions().apply {
        this.tracesSampler = SentryOptions.TracesSamplerCallback { samplingContext -> 1.0 }
      }

    assertTrue(options.isTracingEnabled)
  }

  @Test
  fun `by default tracing is considered disabled`() {
    val options = SentryOptions()

    assertFalse(options.isTracingEnabled)
  }

  @Test
  fun `when there's no cacheDirPath, outboxPath returns null`() {
    val options = SentryOptions()
    assertNull(options.outboxPath)
  }

  @Test
  fun `when cacheDirPath is set, outboxPath concatenate outbox path`() {
    val options = SentryOptions()
    options.cacheDirPath = "${File.separator}test"
    assertEquals("${File.separator}test${File.separator}outbox", options.outboxPath)
  }

  @Test
  fun `SentryOptions creates SentryExecutorService on ctor`() {
    val options = SentryOptions()
    assertNotNull(options.executorService)
  }

  @Test
  fun `init should set SdkVersion`() {
    val sentryOptions = SentryOptions()
    assertNotNull(sentryOptions.sdkVersion)
    val sdkVersion = sentryOptions.sdkVersion!!

    assertEquals(BuildConfig.SENTRY_JAVA_SDK_NAME, sdkVersion.name)
    assertEquals(BuildConfig.VERSION_NAME, sdkVersion.version)

    assertTrue(
      sdkVersion.packageSet.any {
        it.name == "maven:io.sentry:sentry" && it.version == BuildConfig.VERSION_NAME
      }
    )
  }

  @Test
  fun `init should set clientName`() {
    val sentryOptions = SentryOptions()

    val clientName = "${BuildConfig.SENTRY_JAVA_SDK_NAME}/${BuildConfig.VERSION_NAME}"

    assertEquals(clientName, sentryOptions.sentryClientName)
  }

  @Test
  fun `when options is initialized, attachThreads is false`() {
    assertFalse(SentryOptions().isAttachThreads)
  }

  @Test
  fun `when options is initialized, attachStacktrace is true`() {
    assertTrue(SentryOptions().isAttachStacktrace)
  }

  @Test
  fun `when options is initialized, isProfilingEnabled is false and isContinuousProfilingEnabled is true`() {
    assertFalse(SentryOptions().isProfilingEnabled)
    assertFalse(SentryOptions().isContinuousProfilingEnabled)
  }

  @Test
  fun `when profilesSampleRate is null and profilesSampler is null, isProfilingEnabled is false and isContinuousProfilingEnabled is false`() {
    val options =
      SentryOptions().apply {
        this.profilesSampleRate = null
        this.profilesSampler = null
      }
    assertFalse(options.isProfilingEnabled)
    assertFalse(options.isContinuousProfilingEnabled)
  }

  @Test
  fun `when profilesSampleRate is 0 and profilesSampler is null, isProfilingEnabled is false and isContinuousProfilingEnabled is false`() {
    val options =
      SentryOptions().apply {
        this.profilesSampleRate = 0.0
        this.profilesSampler = null
      }
    assertFalse(options.isProfilingEnabled)
    assertFalse(options.isContinuousProfilingEnabled)
  }

  @Test
  fun `when profilesSampleRate is set to a value higher than 0, isProfilingEnabled is true and isContinuousProfilingEnabled is false`() {
    val options = SentryOptions().apply { this.profilesSampleRate = 0.1 }
    assertTrue(options.isProfilingEnabled)
    assertFalse(options.isContinuousProfilingEnabled)
  }

  @Test
  fun `when profilesSampler is set to a value, isProfilingEnabled is true and isContinuousProfilingEnabled is false`() {
    val options =
      SentryOptions().apply { this.profilesSampler = SentryOptions.ProfilesSamplerCallback { 1.0 } }
    assertTrue(options.isProfilingEnabled)
    assertFalse(options.isContinuousProfilingEnabled)
  }

  @Test
  fun `when profileSessionSampleRate is set to 0, isProfilingEnabled is false and isContinuousProfilingEnabled is false`() {
    val options = SentryOptions().apply { this.profileSessionSampleRate = 0.0 }
    assertFalse(options.isProfilingEnabled)
    assertFalse(options.isContinuousProfilingEnabled)
  }

  @Test
  fun `when profileSessionSampleRate is null, isProfilingEnabled is false and isContinuousProfilingEnabled is false`() {
    val options = SentryOptions()
    assertNull(options.profileSessionSampleRate)
    assertFalse(options.isProfilingEnabled)
    assertFalse(options.isContinuousProfilingEnabled)
  }

  @Test
  fun `when setProfilesSampleRate is set to exactly 0, value is set`() {
    val options = SentryOptions().apply { this.profilesSampleRate = 0.0 }
    assertEquals(0.0, options.profilesSampleRate)
  }

  @Test
  fun `when setProfilesSampleRate is set to higher than 1_0, setter throws`() {
    assertFailsWith<IllegalArgumentException> {
      SentryOptions().profilesSampleRate = 1.0000000000001
    }
  }

  @Test
  fun `when setProfilesSampleRate is set to lower than 0, setter throws`() {
    assertFailsWith<IllegalArgumentException> {
      SentryOptions().profilesSampleRate = -0.0000000000001
    }
  }

  @Test
  fun `when profileSessionSampleRate is set to exactly 0, value is set`() {
    val options = SentryOptions().apply { this.profileSessionSampleRate = 0.0 }
    assertEquals(0.0, options.profileSessionSampleRate)
  }

  @Test
  fun `when profileSessionSampleRate is set to higher than 1_0, setter throws`() {
    assertFailsWith<IllegalArgumentException> {
      SentryOptions().profileSessionSampleRate = 1.0000000000001
    }
  }

  @Test
  fun `when profileSessionSampleRate is set to lower than 0, setter throws`() {
    assertFailsWith<IllegalArgumentException> {
      SentryOptions().profileSessionSampleRate = -0.0000000000001
    }
  }

  @Test
  fun `when profileLifecycleSessionSampleRate is set to a value, value is set`() {
    val options = SentryOptions().apply { this.profileLifecycle = ProfileLifecycle.TRACE }
    assertEquals(ProfileLifecycle.TRACE, options.profileLifecycle)
  }

  @Test
  fun `profileLifecycleSessionSampleRate defaults to MANUAL`() {
    val options = SentryOptions()
    assertEquals(ProfileLifecycle.MANUAL, options.profileLifecycle)
  }

  @Test
  fun `when isStartProfilerOnAppStart is set to a value, value is set`() {
    val options = SentryOptions().apply { this.isStartProfilerOnAppStart = true }
    assertTrue(options.isStartProfilerOnAppStart)
  }

  @Test
  fun `isStartProfilerOnAppStart defaults to false`() {
    val options = SentryOptions()
    assertFalse(options.isStartProfilerOnAppStart)
  }

  @Test
  fun `when options is initialized, compositePerformanceCollector is set`() {
    assertIs<CompositePerformanceCollector>(SentryOptions().compositePerformanceCollector)
  }

  @Test
  fun `when options is initialized, transactionProfiler is noop`() {
    assert(SentryOptions().transactionProfiler == NoOpTransactionProfiler.getInstance())
  }

  @Test
  fun `when options is initialized, continuousProfiler is noop`() {
    assert(SentryOptions().continuousProfiler == NoOpContinuousProfiler.getInstance())
  }

  @Test
  fun `when options is initialized, collector is empty list`() {
    assertTrue(SentryOptions().performanceCollectors.isEmpty())
  }

  @Test
  fun `when adds scope observer, observer list has it`() {
    val observer = mock<IScopeObserver>()
    val options = SentryOptions().apply { addScopeObserver(observer) }

    assertTrue(options.scopeObservers.contains(observer))
  }

  @Test
  fun `when environment is not set, falls back to default value`() {
    val options = SentryOptions()
    assertEquals("production", options.environment)
  }

  @Test
  fun `when environment is set, correct value is returned`() {
    val options = SentryOptions().apply { environment = "debug" }
    assertEquals("debug", options.environment)
  }

  @Test
  fun `when adds options observer, observer list has it`() {
    val observer = mock<IOptionsObserver>()
    val options = SentryOptions().apply { addOptionsObserver(observer) }

    assertTrue(options.optionsObservers.contains(observer))
  }

  @Test
  fun `copies options from another SentryOptions instance`() {
    val externalOptions = ExternalOptions()
    externalOptions.dsn = "http://key@localhost/proj"
    externalOptions.dist = "distribution"
    externalOptions.environment = "environment"
    externalOptions.release = "release"
    externalOptions.serverName = "serverName"
    externalOptions.proxy = SentryOptions.Proxy("example.com", "8090", Proxy.Type.SOCKS)
    externalOptions.setTag("tag1", "value1")
    externalOptions.setTag("tag2", "value2")
    externalOptions.enableUncaughtExceptionHandler = false
    externalOptions.tracesSampleRate = 0.5
    externalOptions.profilesSampleRate = 0.5
    externalOptions.addInAppInclude("com.app")
    externalOptions.addInAppExclude("io.off")
    externalOptions.addTracePropagationTarget("localhost")
    externalOptions.addTracePropagationTarget("api.foo.com")
    externalOptions.addContextTag("userId")
    externalOptions.addContextTag("requestId")
    externalOptions.proguardUuid = "1234"
    externalOptions.idleTimeout = 1500L
    externalOptions.bundleIds.addAll(
      listOf("12ea7a02-46ac-44c0-a5bb-6d1fd9586411 ", " faa3ab42-b1bd-4659-af8e-1682324aa744")
    )
    externalOptions.isEnabled = false
    externalOptions.isEnablePrettySerializationOutput = false
    externalOptions.isSendModules = false
    externalOptions.ignoredCheckIns = listOf("slug1", "slug-B")
    externalOptions.ignoredTransactions = listOf("transactionName1", "transaction-name-B")
    externalOptions.ignoredErrors = listOf("Some error", "Another .*")
    externalOptions.isEnableBackpressureHandling = false
    externalOptions.maxRequestBodySize = SentryOptions.RequestSize.MEDIUM
    externalOptions.isSendDefaultPii = true
    externalOptions.isForceInit = true
    externalOptions.cron =
      SentryOptions.Cron().apply {
        defaultCheckinMargin = 10L
        defaultMaxRuntime = 30L
        defaultTimezone = "America/New_York"
        defaultFailureIssueThreshold = 40L
        defaultRecoveryThreshold = 50L
      }
    externalOptions.isEnableSpotlight = true
    externalOptions.spotlightConnectionUrl = "http://local.sentry.io:1234"
    externalOptions.isGlobalHubMode = true
    externalOptions.isEnableLogs = true

    val options = SentryOptions()

    options.merge(externalOptions)

    assertEquals("http://key@localhost/proj", options.dsn)
    assertEquals("distribution", options.dist)
    assertEquals("environment", options.environment)
    assertEquals("release", options.release)
    assertEquals("serverName", options.serverName)
    assertNotNull(options.proxy)
    assertEquals("example.com", options.proxy!!.host)
    assertEquals("8090", options.proxy!!.port)
    assertEquals(java.net.Proxy.Type.SOCKS, options.proxy!!.type)
    assertEquals(mapOf("tag1" to "value1", "tag2" to "value2"), options.tags)
    assertFalse(options.isEnableUncaughtExceptionHandler)
    assertEquals(0.5, options.tracesSampleRate)
    assertEquals(0.5, options.profilesSampleRate)
    assertEquals(listOf("com.app"), options.inAppIncludes)
    assertEquals(listOf("io.off"), options.inAppExcludes)
    assertEquals(listOf("localhost", "api.foo.com"), options.tracePropagationTargets)
    assertEquals(listOf("userId", "requestId"), options.contextTags)
    assertEquals("1234", options.proguardUuid)
    assertEquals(1500L, options.idleTimeout)
    assertEquals(
      setOf("12ea7a02-46ac-44c0-a5bb-6d1fd9586411", "faa3ab42-b1bd-4659-af8e-1682324aa744"),
      options.bundleIds,
    )
    assertFalse(options.isEnabled)
    assertFalse(options.isEnablePrettySerializationOutput)
    assertFalse(options.isSendModules)
    assertEquals(listOf(FilterString("slug1"), FilterString("slug-B")), options.ignoredCheckIns)
    assertEquals(
      listOf(FilterString("transactionName1"), FilterString("transaction-name-B")),
      options.ignoredTransactions,
    )
    assertEquals(
      listOf(FilterString("Some error"), FilterString("Another .*")),
      options.ignoredErrors,
    )
    assertFalse(options.isEnableBackpressureHandling)
    assertTrue(options.isForceInit)
    assertNotNull(options.cron)
    assertEquals(10L, options.cron?.defaultCheckinMargin)
    assertEquals(30L, options.cron?.defaultMaxRuntime)
    assertEquals(40L, options.cron?.defaultFailureIssueThreshold)
    assertEquals(50L, options.cron?.defaultRecoveryThreshold)
    assertEquals("America/New_York", options.cron?.defaultTimezone)
    assertTrue(options.isSendDefaultPii)
    assertEquals(RequestSize.MEDIUM, options.maxRequestBodySize)
    assertTrue(options.isEnableSpotlight)
    assertEquals("http://local.sentry.io:1234", options.spotlightConnectionUrl)
    assertTrue(options.isGlobalHubMode!!)
    assertTrue(options.logs.isEnabled!!)
  }

  @Test
  fun `merging options when enableUncaughtExceptionHandler is not set preserves the default value`() {
    val externalOptions = ExternalOptions()
    val options = SentryOptions()
    options.merge(externalOptions)
    assertTrue(options.isEnableUncaughtExceptionHandler)
  }

  @Test
  fun `merging options merges and overwrites existing tag values`() {
    val externalOptions = ExternalOptions()
    externalOptions.setTag("tag1", "value1")
    externalOptions.setTag("tag2", "value2")
    val options = SentryOptions()
    options.setTag("tag2", "original-options-value")
    options.setTag("tag3", "value3")

    options.merge(externalOptions)

    assertEquals(mapOf("tag1" to "value1", "tag2" to "value2", "tag3" to "value3"), options.tags)
  }

  @Test
  fun `merging options when tracePropagationTargets is not set preserves the default value`() {
    val externalOptions = ExternalOptions()
    val options = SentryOptions()
    options.merge(externalOptions)
    assertEquals(listOf(".*"), options.tracePropagationTargets)
  }

  @Test
  fun `merging options when tracePropagationTargets is empty`() {
    val externalOptions = ExternalOptions()
    externalOptions.addTracePropagationTarget("")
    val options = SentryOptions()
    options.merge(externalOptions)
    assertEquals(listOf(), options.tracePropagationTargets)
  }

  @Test
  fun `when options is initialized, Json Serializer is set by default`() {
    assertTrue(SentryOptions().serializer is JsonSerializer)
  }

  @Test
  fun `when options are initialized, maxAttachmentSize is 20`() {
    assertEquals((20 * 1024 * 1024).toLong(), SentryOptions().maxAttachmentSize)
  }

  @Test
  fun `when setting dsn, calculates hash and add as subfolder of caching dirs`() {
    val dsn = "http://key@localhost/proj"
    val hash = StringUtils.calculateStringHash(dsn, mock())
    val options =
      SentryOptions().apply {
        setDsn(dsn)
        cacheDirPath = "${File.separator}test"
      }

    assertEquals(
      "${File.separator}test${File.separator}${hash}${File.separator}outbox",
      options.outboxPath,
    )
    assertEquals(
      "${File.separator}test${File.separator}${hash}${File.separator}profiling_traces",
      options.profilingTracesDirPath,
    )
  }

  @Test
  fun `getCacheDirPathWithoutDsn does not contain dsn hash`() {
    val dsn = "http://key@localhost/proj"
    val hash = StringUtils.calculateStringHash(dsn, mock())
    val options =
      SentryOptions().apply {
        setDsn(dsn)
        cacheDirPath = "${File.separator}test"
      }

    val cacheDirPathWithoutDsn = options.cacheDirPathWithoutDsn!!
    assertNotEquals(cacheDirPathWithoutDsn, options.cacheDirPath)
    assertEquals(cacheDirPathWithoutDsn, options.cacheDirPath!!.substringBeforeLast("/"))
    assertFalse(cacheDirPathWithoutDsn.contains(hash.toString()))
  }

  @Test
  fun `when options are initialized, idleTimeout is 3000`() {
    assertEquals(3000L, SentryOptions().idleTimeout)
  }

  @Test
  fun `when options are initialized, CompositePerformanceCollector is a NoOp`() {
    assertEquals(
      SentryOptions().compositePerformanceCollector,
      NoOpCompositePerformanceCollector.getInstance(),
    )
  }

  @Test
  fun `when setCompositePerformanceCollector is called, overrides default`() {
    val performanceCollector = mock<CompositePerformanceCollector>()
    val options = SentryOptions()
    options.compositePerformanceCollector = performanceCollector
    assertEquals(performanceCollector, options.compositePerformanceCollector)
  }

  @Test
  fun `when options are initialized, TimeToFullDisplayTracing is false`() {
    assertFalse(SentryOptions().isEnableTimeToFullDisplayTracing)
  }

  @Test
  fun `when options are initialized, FullyDrawnReporter is set`() {
    assertEquals(FullyDisplayedReporter.getInstance(), SentryOptions().fullyDisplayedReporter)
  }

  @Test
  fun `when options are initialized, connectionStatusProvider is not null and default to noop`() {
    assertNotNull(SentryOptions().connectionStatusProvider)
    assertTrue(SentryOptions().connectionStatusProvider is NoOpConnectionStatusProvider)
  }

  @Test
  fun `when connectionStatusProvider is set, its returned as well`() {
    val options = SentryOptions()
    val customProvider =
      object : IConnectionStatusProvider {
        override fun close() = Unit

        override fun getConnectionStatus(): IConnectionStatusProvider.ConnectionStatus {
          return IConnectionStatusProvider.ConnectionStatus.UNKNOWN
        }

        override fun getConnectionType(): String? = null

        override fun addConnectionStatusObserver(
          observer: IConnectionStatusProvider.IConnectionStatusObserver
        ) = false

        override fun removeConnectionStatusObserver(
          observer: IConnectionStatusProvider.IConnectionStatusObserver
        ) {
          // no-op
        }
      }
    options.connectionStatusProvider = customProvider
    assertEquals(customProvider, options.connectionStatusProvider)
  }

  @Test
  fun `when options are initialized, enabled is set to true by default`() {
    assertTrue(SentryOptions().isEnabled)
  }

  @Test
  fun `when options are initialized, enablePrettySerializationOutput is set to true by default`() {
    assertTrue(SentryOptions().isEnablePrettySerializationOutput)
  }

  @Test
  fun `when options are initialized, sendModules is set to true by default`() {
    assertTrue(SentryOptions().isSendModules)
  }

  @Test
  fun `when options are initialized, enableBackpressureHandling is set to true by default`() {
    assertTrue(SentryOptions().isEnableBackpressureHandling)
  }

  @Test
  fun `when options are initialized, enableSpotlight is set to false by default`() {
    assertFalse(SentryOptions().isEnableSpotlight)
  }

  @Test
  fun `when options are initialized, spotlightConnectionUrl is not set by default`() {
    assertNull(SentryOptions().spotlightConnectionUrl)
  }

  @Test
  fun `when options are initialized, enableAppStartProfiling is set to false by default`() {
    assertFalse(SentryOptions().isEnableAppStartProfiling)
  }

  @Test
  fun `when options are initialized, isGlobalHubMode is set to null by default`() {
    assertNull(SentryOptions().isGlobalHubMode)
  }

  @Test
  fun `when setEnableAppStartProfiling is called, overrides default`() {
    val options = SentryOptions()
    options.isEnableAppStartProfiling = true
    options.profilesSampleRate = 1.0
    assertTrue(options.isEnableAppStartProfiling)
  }

  @Test
  fun `when profiling is disabled, isEnableAppStartProfiling is always false`() {
    val options = SentryOptions()
    options.isEnableAppStartProfiling = true
    options.profileSessionSampleRate = 0.0
    assertFalse(options.isEnableAppStartProfiling)
  }

  @Test
  fun `when setEnableAppStartProfiling is called and continuous profiling is enabled, isEnableAppStartProfiling is true`() {
    val options = SentryOptions()
    options.isEnableAppStartProfiling = true
    options.profileSessionSampleRate = 1.0
    assertTrue(options.isEnableAppStartProfiling)
  }

  @Test
  fun `when options are initialized, profilingTracesHz is set to 101 by default`() {
    assertEquals(101, SentryOptions().profilingTracesHz)
  }

  @Test
  fun `when setProfilingTracesHz is called, overrides default`() {
    val options = SentryOptions()
    options.profilingTracesHz = 13
    assertEquals(13, options.profilingTracesHz)
  }

  @Test
  fun `when options are initialized, spotlight is disabled by default and no url is set`() {
    val options = SentryOptions()
    assertFalse(options.isEnableSpotlight)
    assertNull(options.spotlightConnectionUrl)
  }

  @Test
  fun `when spotlight is configured, getters reflect that`() {
    val options =
      SentryOptions().apply {
        isEnableSpotlight = true
        spotlightConnectionUrl = "http://localhost:8080"
      }
    assertTrue(options.isEnableSpotlight)
    assertEquals("http://localhost:8080", options.spotlightConnectionUrl)
  }

  @Test
  fun `when options are initialized, enableScopePersistence is set to true by default`() {
    assertEquals(true, SentryOptions().isEnableScopePersistence)
  }

  @Test
  fun `existing cron defaults are not overridden if not present in external options`() {
    val options =
      SentryOptions().apply {
        cron =
          SentryOptions.Cron().apply {
            defaultCheckinMargin = 1
            defaultMaxRuntime = 2
            defaultTimezone = "America/New_York"
            defaultFailureIssueThreshold = 3
            defaultRecoveryThreshold = 4
          }
      }

    val externalOptions = ExternalOptions().apply { cron = SentryOptions.Cron() }

    options.merge(externalOptions)

    assertEquals(1, options.cron?.defaultCheckinMargin)
    assertEquals(2, options.cron?.defaultMaxRuntime)
    assertEquals("America/New_York", options.cron?.defaultTimezone)
    assertEquals(3, options.cron?.defaultFailureIssueThreshold)
    assertEquals(4, options.cron?.defaultRecoveryThreshold)
  }

  @Test
  fun `all cron properties set in external options override values set in sentry options`() {
    val options =
      SentryOptions().apply {
        cron =
          SentryOptions.Cron().apply {
            defaultCheckinMargin = 1
            defaultMaxRuntime = 2
            defaultTimezone = "America/New_York"
            defaultFailureIssueThreshold = 3
            defaultRecoveryThreshold = 4
          }
      }

    val externalOptions =
      ExternalOptions().apply {
        cron =
          SentryOptions.Cron().apply {
            defaultCheckinMargin = 10
            defaultMaxRuntime = 20
            defaultTimezone = "Europe/Vienna"
            defaultFailureIssueThreshold = 30
            defaultRecoveryThreshold = 40
          }
      }

    options.merge(externalOptions)

    assertEquals(10, options.cron?.defaultCheckinMargin)
    assertEquals(20, options.cron?.defaultMaxRuntime)
    assertEquals("Europe/Vienna", options.cron?.defaultTimezone)
    assertEquals(30, options.cron?.defaultFailureIssueThreshold)
    assertEquals(40, options.cron?.defaultRecoveryThreshold)
  }

  @Test
  fun `when options is initialized, InitPriority is set to MEDIUM by default`() {
    assertEquals(SentryOptions().initPriority, InitPriority.MEDIUM)
  }

  @Test
  fun `merging options when ignoredErrors is not set preserves the previous value`() {
    val externalOptions = ExternalOptions()
    val options = SentryOptions()
    options.setIgnoredErrors(listOf("error1", "error2"))
    options.merge(externalOptions)
    assertEquals(listOf(FilterString("error1"), FilterString("error2")), options.ignoredErrors)
  }

  @Test
  fun `merging options when ignoredTransactions is not set preserves the previous value`() {
    val externalOptions = ExternalOptions()
    val options = SentryOptions()
    options.setIgnoredTransactions(listOf("transaction1", "transaction2"))
    options.merge(externalOptions)
    assertEquals(
      listOf(FilterString("transaction1"), FilterString("transaction2")),
      options.ignoredTransactions,
    )
  }

  @Test
  fun `merging options when ignoredCheckIns is not set preserves the previous value`() {
    val externalOptions = ExternalOptions()
    val options = SentryOptions()
    options.setIgnoredCheckIns(listOf("checkin1", "checkin2"))
    options.merge(externalOptions)
    assertEquals(
      listOf(FilterString("checkin1"), FilterString("checkin2")),
      options.ignoredCheckIns,
    )
  }

  @Test
  fun `null tag`() {
    val options = SentryOptions.empty()
    options.setTag("k", "v")
    options.setTag("k", null)
    options.setTag(null, null)
    assertTrue(options.tags.isEmpty())
  }

  @Test
  fun `feedback dialog handler logs a warning`() {
    val logger = mock<ILogger>()
    val options =
      SentryOptions.empty().apply {
        setLogger(logger)
        isDebug = true
      }
    options.feedbackOptions.dialogHandler.showDialog(mock(), mock())
    verify(logger).log(eq(SentryLevel.WARNING), eq("showDialog() can only be called in Android."))
  }

  @Test
  fun `autoTransactionDeadlineTimeoutMillis option defaults to 30000`() {
    val options = SentryOptions.empty()
    assertEquals(30000L, options.deadlineTimeout)
  }

  @Test
  fun `autoTransactionDeadlineTimeoutMillis option can be changed`() {
    val options = SentryOptions.empty()
    options.deadlineTimeout = 60000L
    assertEquals(60000L, options.deadlineTimeout)
  }

  @Test
  fun `autoTransactionDeadlineTimeoutMillis option can be set to zero value`() {
    val options = SentryOptions.empty()
    options.deadlineTimeout = 0L
    assertEquals(0L, options.deadlineTimeout)
  }

  @Test
  fun `autoTransactionDeadlineTimeoutMillis option can be set to negative value`() {
    val options = SentryOptions.empty()
    options.deadlineTimeout = -1L
    assertEquals(-1L, options.deadlineTimeout)
  }
}
