package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.MainEventProcessor
import io.sentry.core.SentryOptions
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidOptionsInitializerTest {
    private lateinit var context: Context
    private lateinit var file: File

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
        file = context.cacheDir
    }

    @Test
    fun `logger set to AndroidLogger`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val logger = SentryOptions::class.java.declaredFields.first { it.name == "logger" }
        logger.isAccessible = true
        val loggerField = logger.get(sentryOptions)
        val innerLogger = loggerField.javaClass.declaredFields.first { it.name == "logger" }
        innerLogger.isAccessible = true
        assertTrue(innerLogger.get(loggerField) is AndroidLogger)
    }

    @Test
    fun `AndroidEventProcessor added to processors list`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.eventProcessors.any { it is DefaultAndroidEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `MainEventProcessor added to processors list and its the 1st`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.eventProcessors.firstOrNull { it is MainEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `envelopesDir should be created at initialization`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertTrue(sentryOptions.cacheDirPath?.endsWith("${File.separator}cache${File.separator}sentry")!!)
        val file = File(sentryOptions.cacheDirPath!!)
        assertTrue(file.exists())
        file.deleteOnExit()
    }

    @Test
    fun `outboxDir should be created at initialization`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertTrue(sentryOptions.outboxPath?.endsWith("${File.separator}cache${File.separator}sentry${File.separator}outbox")!!)
        val file = File(sentryOptions.outboxPath!!)
        assertTrue(file.exists())
        file.deleteOnExit()
    }

    @Test
    fun `sessionDir should be created at initialization`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertTrue(sentryOptions.sessionsPath?.endsWith("${File.separator}cache${File.separator}sentry${File.separator}sessions")!!)
        val file = File(sentryOptions.sessionsPath!!)
        assertTrue(file.exists())
        file.deleteOnExit()
    }

    @Test
    fun `init should set context package name as appInclude`() {
        val sentryOptions = SentryAndroidOptions()

        AndroidOptionsInitializer.init(sentryOptions, context)

        // cant mock PackageInfo, its buggy
        assertTrue(sentryOptions.inAppIncludes.contains("io.sentry.android.core.test"))
    }

    @Test
    fun `init should set release if empty`() {
        val sentryOptions = SentryAndroidOptions()

        AndroidOptionsInitializer.init(sentryOptions, context)

        // cant mock PackageInfo, its buggy
        assertTrue(sentryOptions.release!!.startsWith("io.sentry.android.core.test@"))
    }

    @Test
    fun `init should not replace options if set on manifest`() {
        val sentryOptions = SentryAndroidOptions().apply {
            release = "release"
        }

        AndroidOptionsInitializer.init(sentryOptions, context)

        assertEquals("release", sentryOptions.release)
    }

    @Test
    fun `init should not set context package name if it starts with android package`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = ContextUtilsTest.createMockContext()
        whenever(mockContext.packageName).thenReturn("android.context")

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertFalse(sentryOptions.inAppIncludes.contains("android.context"))
    }

    @Test
    fun `init should set distinct id on start`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = ContextUtilsTest.createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertTrue(sentryOptions.distinctId.isNotEmpty())

        val installation = File(context.filesDir, Installation.INSTALLATION)
        installation.deleteOnExit()
    }

    @Test
    fun `init should set Android transport gate`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        assertNotNull(sentryOptions.transportGate)
        assertTrue(sentryOptions.transportGate is AndroidTransportGate)
    }

    @Test
    fun `init should set clientName`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)

        val clientName = "${BuildConfig.SENTRY_CLIENT_NAME}/${BuildConfig.VERSION_NAME}"

        assertEquals(clientName, sentryOptions.sentryClientName)
    }

    @Test
    fun `NdkIntegration added to integration list`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.integrations.firstOrNull { it is NdkIntegration }
        assertNotNull(actual)
    }

    @Test
    fun `AnrIntegration added to integration list`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.integrations.firstOrNull { it is AnrIntegration }
        assertNotNull(actual)
    }

    @Test
    fun `EnvelopeFileObserverIntegration added to integration list`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val actual = sentryOptions.integrations.firstOrNull { it is EnvelopeFileObserverIntegration }
        assertNotNull(actual)
    }

    private fun createMockContext(): Context {
        val mockContext = ContextUtilsTest.createMockContext()
        whenever(mockContext.cacheDir).thenReturn(file)
        return mockContext
    }
}
