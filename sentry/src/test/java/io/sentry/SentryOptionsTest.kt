package io.sentry

import io.sentry.util.StringUtils
import org.mockito.kotlin.mock
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `when setSampling is set to exactly 0, setter throws`() {
        assertFailsWith<IllegalArgumentException> { SentryOptions().sampleRate = 0.0 }
    }

    @Test
    fun `when setTracesSampleRate is set to exactly 0, value is set`() {
        val options = SentryOptions().apply {
            this.tracesSampleRate = 0.0
        }
        assertEquals(0.0, options.tracesSampleRate)
    }

    @Test
    fun `when setTracesSampleRate is set to higher than 1_0, setter throws`() {
        assertFailsWith<IllegalArgumentException> { SentryOptions().tracesSampleRate = 1.0000000000001 }
    }

    @Test
    fun `when setTracesSampleRate is set to lower than 0, setter throws`() {
        assertFailsWith<IllegalArgumentException> { SentryOptions().tracesSampleRate = -0.0000000000001 }
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
            sdkVersion.packages!!.any {
                it.name == "maven:io.sentry:sentry" &&
                    it.version == BuildConfig.VERSION_NAME
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
    fun `when options is initialized, enableScopeSync is false`() {
        assertFalse(SentryOptions().isEnableScopeSync)
    }

    @Test
    fun `when options is initialized, isProfilingEnabled is false`() {
        assertFalse(SentryOptions().isProfilingEnabled)
    }

    @Test
    fun `when profilesSampleRate is null and profilesSampler is null, isProfilingEnabled is false`() {
        val options = SentryOptions().apply {
            this.profilesSampleRate = null
            this.profilesSampler = null
        }
        assertFalse(options.isProfilingEnabled)
    }

    @Test
    fun `when profilesSampleRate is 0 and profilesSampler is null, isProfilingEnabled is false`() {
        val options = SentryOptions().apply {
            this.profilesSampleRate = 0.0
            this.profilesSampler = null
        }
        assertFalse(options.isProfilingEnabled)
    }

    @Test
    fun `when profilesSampleRate is set to a value higher than 0, isProfilingEnabled is true`() {
        val options = SentryOptions().apply {
            this.profilesSampleRate = 0.1
        }
        assertTrue(options.isProfilingEnabled)
    }

    @Test
    fun `when profilesSampler is set to a value, isProfilingEnabled is true`() {
        val options = SentryOptions().apply {
            this.profilesSampler = SentryOptions.ProfilesSamplerCallback { 1.0 }
        }
        assertTrue(options.isProfilingEnabled)
    }

    @Test
    fun `when setProfilesSampleRate is set to exactly 0, value is set`() {
        val options = SentryOptions().apply {
            this.profilesSampleRate = 0.0
        }
        assertEquals(0.0, options.profilesSampleRate)
    }

    @Test
    fun `when setProfilesSampleRate is set to higher than 1_0, setter throws`() {
        assertFailsWith<IllegalArgumentException> { SentryOptions().profilesSampleRate = 1.0000000000001 }
    }

    @Test
    fun `when setProfilesSampleRate is set to lower than 0, setter throws`() {
        assertFailsWith<IllegalArgumentException> { SentryOptions().profilesSampleRate = -0.0000000000001 }
    }

    @Test
    fun `when profilingEnabled is set to true, profilesSampleRate is set to 1`() {
        val options = SentryOptions()
        options.isProfilingEnabled = true
        assertEquals(1.0, options.profilesSampleRate)
    }

    @Test
    fun `when profilingEnabled is set to false, profilesSampleRate is set to null`() {
        val options = SentryOptions()
        options.isProfilingEnabled = false
        assertNull(options.profilesSampleRate)
    }

    @Test
    fun `when profilesSampleRate is set, setting profilingEnabled is ignored`() {
        val options = SentryOptions()
        options.profilesSampleRate = 0.2
        options.isProfilingEnabled = true
        assertEquals(0.2, options.profilesSampleRate)
        options.isProfilingEnabled = false
        assertEquals(0.2, options.profilesSampleRate)
    }

    @Test
    fun `when options is initialized, transactionPerformanceCollector is set`() {
        assertIs<TransactionPerformanceCollector>(SentryOptions().transactionPerformanceCollector)
    }

    @Test
    fun `when options is initialized, transactionProfiler is noop`() {
        assert(SentryOptions().transactionProfiler == NoOpTransactionProfiler.getInstance())
    }

    @Test
    fun `when options is initialized, memoryCollector is noop`() {
        assert(SentryOptions().memoryCollector == NoOpMemoryCollector.getInstance())
    }

    @Test
    fun `when a null memoryCollector is set, memoryCollector is noop`() {
        val options = SentryOptions()
        options.setMemoryCollector(null)
        assert(SentryOptions().memoryCollector == NoOpMemoryCollector.getInstance())
    }

    @Test
    fun `when adds scope observer, observer list has it`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            addScopeObserver(observer)
        }

        assertTrue(options.scopeObservers.contains(observer))
    }

    @Test
    fun `copies options from another SentryOptions instance`() {
        val externalOptions = ExternalOptions()
        externalOptions.dsn = "http://key@localhost/proj"
        externalOptions.dist = "distribution"
        externalOptions.environment = "environment"
        externalOptions.release = "release"
        externalOptions.serverName = "serverName"
        externalOptions.proxy = SentryOptions.Proxy("example.com", "8090")
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
        val options = SentryOptions().apply {
            setDsn(dsn)
            cacheDirPath = "${File.separator}test"
        }

        assertEquals("${File.separator}test${File.separator}${hash}${File.separator}outbox", options.outboxPath)
        assertEquals("${File.separator}test${File.separator}${hash}${File.separator}profiling_traces", options.profilingTracesDirPath)
    }

    fun `when options are initialized, idleTimeout is 3000`() {
        assertEquals(3000L, SentryOptions().idleTimeout)
    }
}
