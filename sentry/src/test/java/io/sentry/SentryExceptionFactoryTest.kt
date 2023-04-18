package io.sentry

import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryExceptionFactoryTest {
    private class Fixture {

        fun getSut(
            stackTraceFactory: SentryStackTraceFactory = SentryStackTraceFactory(
                SentryOptions().apply { addInAppExclude("io.sentry") }
            )
        ): SentryExceptionFactory {
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
        assertNotNull(sentryExceptions[0].stacktrace) {
            assertTrue(it.frames!!.isNotEmpty())
        }
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

        val throwable = ExceptionMechanismException(mechanism, error, Thread.currentThread())

        val sentryExceptions = fixture.getSut().getSentryExceptions(throwable)
        assertNotNull(sentryExceptions[0].mechanism) {
            assertEquals("anr", it.type)
            assertFalse(it.isHandled!!)
        }
        assertNull(sentryExceptions[0].stacktrace?.snapshot)
    }

    @Test
    fun `When ExceptionMechanismException has threads snapshot, stack trace should set snapshot flag`() {
        val error = Exception("Exception")

        val throwable =
            ExceptionMechanismException(Mechanism(), error, Thread.currentThread(), true)
        val sentryExceptions = fixture.getSut().getSentryExceptions(throwable)

        assertTrue(sentryExceptions[0].stacktrace?.snapshot!!)
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
