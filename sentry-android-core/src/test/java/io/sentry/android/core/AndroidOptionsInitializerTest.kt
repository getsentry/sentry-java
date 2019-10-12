package io.sentry.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nhaarman.mockitokotlin2.mock
import io.sentry.core.SentryOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidOptionsInitializerTest {
    @Test
    fun `logger set to AndroidLogger`() {
        val sentryOptions = SentryOptions()
        AndroidOptionsInitializer.init(sentryOptions, mock())
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
        AndroidOptionsInitializer.init(sentryOptions, mock())
        val actual = sentryOptions.eventProcessors.firstOrNull { it::class == DefaultAndroidEventProcessor::class }
        assertNotNull(actual)
    }
}
