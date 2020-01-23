package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.ILogger
import io.sentry.core.MainEventProcessor
import io.sentry.core.SentryOptions
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
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
        val mockLogger = mock<ILogger>()

        AndroidOptionsInitializer.init(sentryOptions, mockContext, mockLogger)
        val actual = sentryOptions.eventProcessors.any { it is DefaultAndroidEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `MainEventProcessor added to processors list and its the 1st`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()
        val mockLogger = mock<ILogger>()

        AndroidOptionsInitializer.init(sentryOptions, mockContext, mockLogger)
        val actual = sentryOptions.eventProcessors.firstOrNull { it is MainEventProcessor }
        assertNotNull(actual)
    }

    @Test
    fun `envelopesDir should be created at initialization`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()
        val mockLogger = mock<ILogger>()

        AndroidOptionsInitializer.init(sentryOptions, mockContext, mockLogger)

        assertTrue(sentryOptions.cacheDirPath?.endsWith("${File.separator}cache${File.separator}sentry")!!)
    }

    @Test
    fun `init should set context package name as appInclude`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = mock<ApplicationStub> {
            on { applicationContext } doReturn context
        }
        whenever(mockContext.cacheDir).thenReturn(File("${File.separator}cache"))
        whenever(mockContext.packageName).thenReturn("io.sentry.app")
        val mockLogger = mock<ILogger>()

        AndroidOptionsInitializer.init(sentryOptions, mockContext, mockLogger)

        assertTrue(sentryOptions.inAppIncludes.contains("io.sentry.app"))
    }

    @Test
    fun `init should set Android transport gate`() {
        val sentryOptions = SentryAndroidOptions()
        val mockContext = createMockContext()
        val mockLogger = mock<ILogger>()

        AndroidOptionsInitializer.init(sentryOptions, mockContext, mockLogger)

        assertNotNull(sentryOptions.transportGate)
        assertTrue(sentryOptions.transportGate is AndroidTransportGate)
    }

    private fun createMockContext(): Context {
        val mockContext = mock<Context> {
            on { applicationContext } doReturn context
        }
        whenever(mockContext.cacheDir).thenReturn(file)
        return mockContext
    }
}
