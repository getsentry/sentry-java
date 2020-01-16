package io.sentry.core

import io.sentry.core.exception.ExceptionMechanismException
import io.sentry.core.protocol.Mechanism
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SentryExceptionFactoryTest {
    private val sut = SentryExceptionFactory(SentryStackTraceFactory(listOf("io.sentry"), listOf()))

    @Test
    fun `when getSentryExceptions is called passing an Exception, not empty result`() {
        val exception = Exception("Exception")
        assertTrue(sut.getSentryExceptions(exception).size > 0)
    }

    @Test
    fun `when getSentryExceptions is called passing an Exception, it should set its fields`() {
        val exception = Exception("Exception")
        val sentryExceptions = sut.getSentryExceptions(exception)
        assertEquals("Exception", sentryExceptions[0].type)
        assertEquals("Exception", sentryExceptions[0].value)
        assertEquals("java.lang", sentryExceptions[0].module)
        assertTrue(sentryExceptions[0].stacktrace.frames.size > 0)
    }

    @Test
    fun `when getSentryExceptions is called passing a ExceptionMechanism, it should set its fields`() {
        val mechanism = Mechanism()
        mechanism.type = "anr"
        mechanism.isHandled = false

        val error = Exception("Exception")

        val throwable = ExceptionMechanismException(mechanism, error, null)

        val sentryExceptions = sut.getSentryExceptions(throwable)
        assertEquals("anr", sentryExceptions[0].mechanism.type)
        assertEquals(false, sentryExceptions[0].mechanism.isHandled)
    }

    @Test
    fun `when exception has a cause, ensure conversion queue keeps order`() {
        val exception = Exception("message", Exception("cause"))
        val queue = sut.extractExceptionQueue(exception)

        assertEquals("message", queue.first.value)
        assertEquals("cause", queue.last.value)
    }

    @Test
    fun `when getSentryExceptions is called passing an Inner exception, not empty result`() {
        val exception = InnerClassThrowable(InnerClassThrowable())
        val queue = sut.extractExceptionQueue(exception)
        assertEquals("SentryExceptionFactoryTest\$InnerClassThrowable", queue.first.type)
    }

    private inner class InnerClassThrowable constructor(cause: Throwable? = null) : Throwable(cause)
}
