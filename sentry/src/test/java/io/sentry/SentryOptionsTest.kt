package io.sentry

import com.nhaarman.mockitokotlin2.mock
import io.sentry.config.PropertiesProviderFactory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.rules.TemporaryFolder

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

        assertTrue(sdkVersion.packages!!.any {
            it.name == "maven:sentry" &&
            it.version == BuildConfig.VERSION_NAME
        })
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
    fun `when adds scope observer, observer list has it`() {
        val observer = mock<IScopeObserver>()
        val options = SentryOptions().apply {
            addScopeObserver(observer)
        }

        assertTrue(options.scopeObservers.contains(observer))
    }

    @Test
    fun `copies options from another SentryOptions instance`() {
        val externalOptions = SentryOptions()
        externalOptions.dsn = "http://key@localhost/proj"
        externalOptions.dist = "distribution"
        externalOptions.environment = "environment"
        externalOptions.release = "release"
        externalOptions.serverName = "serverName"
        externalOptions.proxy = SentryOptions.Proxy("example.com", "8090")
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
    }

    @Test
    fun `creates options with proxy using external properties`() {
        // create a sentry.properties file in temporary folder
        val temporaryFolder = TemporaryFolder()
        temporaryFolder.create()
        val file = temporaryFolder.newFile("sentry.properties")
        file.appendText("proxy.host=proxy.example.com\n")
        file.appendText("proxy.port=9090\n")
        file.appendText("proxy.user=some-user\n")
        file.appendText("proxy.pass=some-pass")
        // set location of the sentry.properties file
        System.setProperty("sentry.properties.file", file.absolutePath)

        try {
            val options = SentryOptions.from(PropertiesProviderFactory.create())
            assertNotNull(options.proxy)
            assertEquals("proxy.example.com", options.proxy!!.host)
            assertEquals("9090", options.proxy!!.port)
            assertEquals("some-user", options.proxy!!.user)
            assertEquals("some-pass", options.proxy!!.pass)
        } finally {
            temporaryFolder.delete()
        }
    }

    @Test
    fun `when proxy port is not set default proxy port is used`() {
        // create a sentry.properties file in temporary folder
        val temporaryFolder = TemporaryFolder()
        temporaryFolder.create()
        val file = temporaryFolder.newFile("sentry.properties")
        file.appendText("proxy.host=proxy.example.com\n")
        // set location of the sentry.properties file
        System.setProperty("sentry.properties.file", file.absolutePath)

        try {
            val options = SentryOptions.from(PropertiesProviderFactory.create())
            assertNotNull(options.proxy)
            assertEquals("proxy.example.com", options.proxy!!.host)
            assertEquals("80", options.proxy!!.port)
        } finally {
            temporaryFolder.delete()
        }
    }
}
