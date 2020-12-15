package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryExceptionFactoryTest {
    private class Fixture {

        fun getSut(stackTraceFactory: SentryStackTraceFactory = SentryStackTraceFactory(listOf("io.sentry"), listOf())): SentryExceptionFactory {
            return SentryExceptionFactory(stackTraceFactory)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when getSentryExceptions is called passing an Exception, not empty result`() {
        val exception = Exception("Exception")
        assertTrue(fixture.getSut().getSentryExceptions(exception).isNotEmpty())
    }

    @Test
    fun `when getSentryExceptions is called passing an Exception, it should set its fields`() {
        val exception = Exception("Exception")
        val sentryExceptions = fixture.getSut().getSentryExceptions(exception)
        assertEquals("Exception", sentryExceptions[0].type)
        assertEquals("Exception", sentryExceptions[0].value)
        assertEquals("java.lang", sentryExceptions[0].module)
        assertTrue(sentryExceptions[0].stacktrace.frames.isNotEmpty())
    }

    @Test
    fun `when frames are null, do not set a stack trace object`() {
        val stackTraceFactory = mock<SentryStackTraceFactory>()
        whenever(stackTraceFactory.getStackFrames(any())).thenReturn(null)

        val sut = fixture.getSut(stackTraceFactory)
        val exception = Exception("Exception")

        val sentryExceptions = sut.getSentryExceptions(exception)

        assertNull(sentryExceptions[0].stacktrace)
    }

    @Test
    fun `when frames are empty, do not set a stack trace object`() {
        val stackTraceFactory = mock<SentryStackTraceFactory>()
        whenever(stackTraceFactory.getStackFrames(any())).thenReturn(emptyList())

        val sut = fixture.getSut(stackTraceFactory)
        val exception = Exception("Exception")

        val sentryExceptions = sut.getSentryExceptions(exception)

        assertNull(sentryExceptions[0].stacktrace)
    }

    @Test
    fun `when getSentryExceptions is called passing a ExceptionMechanism, it should set its fields`() {
        val mechanism = Mechanism()
        mechanism.type = "anr"
        mechanism.isHandled = false

        val error = Exception("Exception")

        val throwable = ExceptionMechanismException(mechanism, error, null)

        val sentryExceptions = fixture.getSut().getSentryExceptions(throwable)
        assertEquals("anr", sentryExceptions[0].mechanism.type)
        assertEquals(false, sentryExceptions[0].mechanism.isHandled)
    }

    @Test
    fun `when exception is nested, it should be sorted oldest to newest`() {
        val exception = Exception("message", Exception("cause"))
        val queue = fixture.getSut().extractExceptionQueue(exception)

        assertEquals("cause", queue.first.value)
        assertEquals("message", queue.last.value)
    }

    @Test
    fun `when getSentryExceptions is called passing an Inner exception, not empty result`() {
        val exception = InnerClassThrowable(InnerClassThrowable())
        val queue = fixture.getSut().extractExceptionQueue(exception)
        assertEquals("SentryExceptionFactoryTest\$InnerClassThrowable", queue.first.type)
    }

    @Test
    fun `when getSentryExceptions is called passing an anonymous exception, not empty result`() {
        val queue = fixture.getSut().extractExceptionQueue(anonymousException)
        assertEquals("SentryExceptionFactoryTest\$anonymousException\$1", queue.first.type)
    }

    @Test
    fun `when exception has no mechanism, it should get and set the current threadId`() {
        val threadId = Thread.currentThread().id
        val exception = Exception("message", Exception("cause"))
        val queue = fixture.getSut().extractExceptionQueue(exception)

        assertEquals(threadId, queue.first.threadId)
    }

    @Test
    fun `when exception has a mechanism, it should get and set the mechanism's threadId`() {
        val exception = Exception("message")
        val mechanism = Mechanism()
        mechanism.type = "ANR"
        val thread = Thread()
        val throwable = ExceptionMechanismException(mechanism, exception, thread)

        val queue = fixture.getSut().extractExceptionQueue(throwable)

        assertEquals(thread.id, queue.first.threadId)
    }

    internal class InnerClassThrowable constructor(cause: Throwable? = null) : Throwable(cause)

    private val anonymousException = object : Exception() {
    }
}
