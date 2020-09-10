package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryStackTraceFactoryTest {
    private val sut = SentryStackTraceFactory(listOf(), listOf())

    @Test
    fun `when getStackFrames is called passing a valid Array, not empty result`() {
        val stacktrace = Thread.currentThread().stackTrace
        val count = stacktrace.size
        assertEquals(count, sut.getStackFrames(stacktrace)!!.count())
    }

    @Test
    fun `when line number is negative, not added to sentry stacktrace`() {
        val stacktrace = StackTraceElement("class", "method", "fileName", -2)
        val actual = sut.getStackFrames(arrayOf(stacktrace))
        assertNull(actual!![0].lineno)
    }

    @Test
    fun `when line number is positive, gets added to sentry stacktrace`() {
        val stacktrace = StackTraceElement("class", "method", "fileName", 1)
        val actual = sut.getStackFrames(arrayOf(stacktrace))
        assertEquals(stacktrace.lineNumber, actual!![0].lineno)
    }

    @Test
    fun `when getStackFrames is called passing empty elements, return null`() {
        assertNull(sut.getStackFrames(arrayOf()))
    }

    @Test
    fun `when getStackFrames is called passing null, return null`() {
        assertNull(sut.getStackFrames(null))
    }

    @Test
    fun `when getStackFrames is called passing a valid array, fields should be set`() {
        val element = generateStackTrace("class")
        val stacktrace = arrayOf(element)
        val stackFrames = sut.getStackFrames(stacktrace)
        assertEquals("class", stackFrames!![0].module)
        assertEquals("method", stackFrames[0].function)
        assertEquals("fileName", stackFrames[0].filename)
        assertEquals(10, stackFrames[0].lineno)
        assertEquals(false, stackFrames[0].isNative)
    }

    //region inAppExcludes
    @Test
    fun `when getStackFrames is called passing a valid inAppExcludes, inApp should be false if prefix matches it`() {
        val element = generateStackTrace("io.sentry.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(listOf("io.sentry"), null)
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertFalse(sentryElements!!.first().isInApp)
    }

    @Test
    fun `when getStackFrames is called passing a valid inAppExcludes, inApp should be false if prefix doesnt matches it`() {
        val element = generateStackTrace("io.myapp.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(listOf("io.sentry"), null)
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertFalse(sentryElements!!.first().isInApp)
    }

    @Test
    fun `when getStackFrames is called passing an invalid inAppExcludes, inApp should be false`() {
        val element = generateStackTrace("io.sentry.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(null, null)
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertFalse(sentryElements!!.first().isInApp)
    }
    //endregion

    //region inAppIncludes
    @Test
    fun `when getStackFrames is called passing a valid inAppIncludes, inApp should be true if prefix matches it`() {
        val element = generateStackTrace("io.sentry.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(null, listOf("io.sentry"))
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertTrue(sentryElements!!.first().isInApp)
    }

    @Test
    fun `when getStackFrames is called passing a valid inAppIncludes, inApp should be false if prefix doesnt matches it`() {
        val element = generateStackTrace("io.myapp.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(null, listOf("io.sentry"))
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertFalse(sentryElements!!.first().isInApp)
    }

    @Test
    fun `when getStackFrames is called passing an invalid inAppIncludes, inApp should be false`() {
        val element = generateStackTrace("io.sentry.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(null, null)
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertFalse(sentryElements!!.first().isInApp)
    }
    //endregion

    @Test
    fun `when getStackFrames is called passing a valid inAppIncludes and inAppExcludes, inApp should take precedence`() {
        val element = generateStackTrace("io.sentry.MyActivity")
        val elements = arrayOf(element)
        val sentryStackTraceFactory = SentryStackTraceFactory(listOf("io.sentry"), listOf("io.sentry"))
        val sentryElements = sentryStackTraceFactory.getStackFrames(elements)

        assertTrue(sentryElements!!.first().isInApp)
    }

    @Test
    fun `when class is defined in the app, inApp is true`() {
        val sentryStackTraceFactory = SentryStackTraceFactory(listOf("io.sentry.not"), listOf("io.sentry.inApp"))
        assertTrue(sentryStackTraceFactory.isInApp("io.sentry.inApp.ClassName"))
        assertTrue(sentryStackTraceFactory.isInApp("io.sentry.inApp.somePackage.ClassName"))
        assertFalse(sentryStackTraceFactory.isInApp("io.sentry.not.ClassName"))
        assertFalse(sentryStackTraceFactory.isInApp("io.sentry.not.somePackage.ClassName"))
    }

    @Test
    fun `when class is not in the list, is not inApp`() {
        val sentryStackTraceFactory = SentryStackTraceFactory(listOf(), listOf("io.sentry"))
        assertFalse(sentryStackTraceFactory.isInApp("com.getsentry"))
    }

    private fun generateStackTrace(className: String?) =
        StackTraceElement(className, "method", "fileName", 10)
}
