package io.sentry.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SentryStackTraceFactoryTest {
    private val sut = SentryStackTraceFactory(listOf(), listOf())

    @Test
    fun `when getStackFrames is called passing a valid Array, not empty result`() {
        val stacktraces = Thread.currentThread().stackTrace
        val count = stacktraces.size
        assertEquals(count, sut.getStackFrames(stacktraces).count())
    }

    @Test
    fun `when getStackFrames is called passing null, empty result`() {
        assertEquals(0, sut.getStackFrames(null).count())
    }

    @Test
    fun `when getStackFrames is called passing a valid array, fields should be set`() {
        val element = generateStackTrace("class")
        val stacktraces = arrayOf(element)
        val stackFrames = sut.getStackFrames(stacktraces)
        assertEquals("class", stackFrames[0].module)
        assertEquals("method", stackFrames[0].function)
        assertEquals("fileName", stackFrames[0].filename)
        assertEquals(-2, stackFrames[0].lineno)
        assertEquals(true, stackFrames[0].isNative)
    }

    //region inAppExcludes
    @Test
    fun `when getStackFrames is called passing a valid inAppExcludes, inApp should be false if prefix matches it`() {
        val element = generateStackTrace("io.sentry.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(listOf("io.sentry"), null)
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertFalse(sentryElements.first().inApp)
    }

    @Test
    fun `when getStackFrames is called passing a valid inAppExcludes, inApp should be true if prefix doesnt matches it`() {
        val element = generateStackTrace("io.myapp.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(listOf("io.sentry"), null)
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertTrue(sentryElements.first().inApp)
    }

    @Test
    fun `when getStackFrames is called passing an invalid inAppExcludes, inApp should be false`() {
        val element = generateStackTrace("io.sentry.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(null, null)
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertTrue(sentryElements.first().inApp)
    }
    //endregion

    //region inAppIncludes
    @Test
    fun `when getStackFrames is called passing a valid inAppIncludes, inApp should be true if prefix matches it`() {
        val element = generateStackTrace("io.sentry.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(null, listOf("io.sentry"))
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertTrue(sentryElements.first().inApp)
    }

    @Test
    fun `when getStackFrames is called passing a valid inAppIncludes, inApp should be true if prefix doesnt matches it`() {
        val element = generateStackTrace("io.myapp.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(null, listOf("io.sentry"))
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertTrue(sentryElements.first().inApp)
    }

    @Test
    fun `when getStackFrames is called passing an invalid inAppIncludes, inApp should be true`() {
        val element = generateStackTrace("io.sentry.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(null, null)
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertTrue(sentryElements.first().inApp)
    }
    //endregion

    @Test
    fun `when getStackFrames is called passing a valid inAppIncludes and inAppExcludes, inApp should take precedence`() {
        val element = generateStackTrace("io.sentry.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(listOf("io.sentry"), listOf("io.sentry"))
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertTrue(sentryElements.first().inApp)
    }

    private fun generateStackTrace(className: String?) =
        StackTraceElement(className, "method", "fileName", -2)
}
