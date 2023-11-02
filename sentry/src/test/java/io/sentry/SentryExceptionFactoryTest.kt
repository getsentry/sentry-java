package io.sentry

import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryStackFrame
import io.sentry.protocol.SentryStackTrace
import io.sentry.protocol.SentryThread
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
                SentryOptions()
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
        whenever(stackTraceFactory.getStackFrames(any(), eq(false))).thenReturn(null)

        val sut = fixture.getSut(stackTraceFactory)
        val exception = Exception("Exception")

        val sentryExceptions = sut.getSentryExceptions(exception)

        assertNull(sentryExceptions[0].stacktrace)
    }

    @Test
    fun `when frames are empty, do not set a stack trace object`() {
        val stackTraceFactory = mock<SentryStackTraceFactory>()
        whenever(stackTraceFactory.getStackFrames(any(), eq(false))).thenReturn(emptyList())

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

    @Test
    fun `when exception has an unhandled mechanism, it should include sentry frames`() {
        val exception = Exception("message")
        val mechanism = Mechanism().apply {
            isHandled = false
            type = "UncaughtExceptionHandler"
        }
        val thread = Thread()
        val throwable = ExceptionMechanismException(mechanism, exception, thread)

        val queue = fixture.getSut().extractExceptionQueue(throwable)

        assertTrue(
            queue.first.stacktrace!!.frames!!.any {
                it.module != null && it.module!!.startsWith("io.sentry")
            }
        )
    }

    @Test
    fun `returns empty list if stacktrace is not available for SentryThread`() {
        val thread = SentryThread()
        val mechanism = Mechanism()
        val throwable = Exception("msg")

        val exceptions = fixture.getSut().getSentryExceptionsFromThread(thread, mechanism, throwable)

        assertTrue(exceptions.isEmpty())
    }

    @Test
    fun `returns proper exception backfilled from SentryThread`() {
        val thread = SentryThread().apply {
            id = 121
            stacktrace = SentryStackTrace().apply {
                frames = listOf(
                    SentryStackFrame().apply {
                        lineno = 777
                        module = "io.sentry.samples.MainActivity"
                        function = "run"
                    }
                )
            }
        }
        val mechanism = Mechanism().apply { type = "AppExitInfo" }
        val throwable = Exception("msg")

        val exceptions = fixture.getSut().getSentryExceptionsFromThread(thread, mechanism, throwable)

        val exception = exceptions.first()
        assertEquals("AppExitInfo", exception.mechanism!!.type)
        assertEquals("java.lang", exception.module)
        assertEquals("Exception", exception.type)
        assertEquals("msg", exception.value)
        assertEquals(121, exception.threadId)
        assertEquals(true, exception.stacktrace!!.snapshot)
        val frame = exception.stacktrace!!.frames!!.first()
        assertEquals("io.sentry.samples.MainActivity", frame.module)
        assertEquals("run", frame.function)
        assertEquals(777, frame.lineno)
    }

    internal class InnerClassThrowable constructor(cause: Throwable? = null) : Throwable(cause)

    private val anonymousException = object : Exception() {
    }
}
