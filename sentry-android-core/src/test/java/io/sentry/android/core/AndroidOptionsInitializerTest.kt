package io.sentry.android.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.sentry.core.ILogger
import io.sentry.core.SentryOptions
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidOptionsInitializerTest {
    private lateinit var context: Context

    @BeforeTest
    fun `set up`() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `logger set to AndroidLogger`() {
        val sentryOptions = SentryOptions()
        val mockContext = mock<Context> {
            on { applicationContext } doReturn context
        }

        AndroidOptionsInitializer.init(sentryOptions, mockContext)
        val logger = sentryOptions.javaClass.declaredFields.first { it.name == "logger" }
        logger.isAccessible = true
        val loggerField = logger.get(sentryOptions)
        val innerLogger = loggerField.javaClass.declaredFields.first { it.name == "logger" }
        innerLogger.isAccessible = true
        assertEquals(AndroidLogger::class, innerLogger.get(loggerField)::class)
    }

    @Test
    fun `AndroidEventProcessor added to processors list`() {
        val sentryOptions = SentryOptions()
        val mockContext = mock<Context> {
            on { applicationContext } doReturn context
        }
        val mockLogger = mock<ILogger>()

        AndroidOptionsInitializer.init(sentryOptions, mockContext, mockLogger)
        val actual = sentryOptions.eventProcessors.firstOrNull { it::class == DefaultAndroidEventProcessor::class }
        assertNotNull(actual)
    }
}
