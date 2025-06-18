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
            stackTraceFactory: SentryStackTraceFactory =
                SentryStackTraceFactory(
                    SentryOptions(),
                ),
        ): SentryExceptionFactory = SentryExceptionFactory(stackTraceFactory)
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
        val mechanism =
            Mechanism().apply {
                isHandled = false
                type = "UncaughtExceptionHandler"
            }
        val thread = Thread()
        val throwable = ExceptionMechanismException(mechanism, exception, thread)

        val queue = fixture.getSut().extractExceptionQueue(throwable)

        assertTrue(
            queue.first.stacktrace!!.frames!!.any {
                it.module != null && it.module!!.startsWith("io.sentry")
            },
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
        val thread =
            SentryThread().apply {
                id = 121
                stacktrace =
                    SentryStackTrace().apply {
                        frames =
                            listOf(
                                SentryStackFrame().apply {
                                    lineno = 777
                                    module = "io.sentry.samples.MainActivity"
                                    function = "run"
                                },
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

    @Test
    fun `when exception with mechanism suppressed exceptions, add them and show as group`() {
        val exception = Exception("message")
        val suppressedException = Exception("suppressed exception")
        exception.addSuppressed(suppressedException)

        val mechanism = Mechanism()
        mechanism.type = "ANR"
        val thread = Thread()
        val throwable = ExceptionMechanismException(mechanism, exception, thread)

        val queue = fixture.getSut().extractExceptionQueue(throwable)

        val suppressedInQueue = queue.pop()
        val mainInQueue = queue.pop()

        assertEquals("suppressed exception", suppressedInQueue.value)
        assertEquals(1, suppressedInQueue.mechanism?.exceptionId)
        assertEquals(0, suppressedInQueue.mechanism?.parentId)
        assertEquals("suppressed", suppressedInQueue.mechanism?.type)

        assertEquals("message", mainInQueue.value)
        assertEquals(0, mainInQueue.mechanism?.exceptionId)
        assertEquals("ANR", mainInQueue.mechanism?.type)
//        assertEquals(true, mainInQueue.mechanism?.isExceptionGroup)
    }

    @Test
    fun `nested exception that contains suppressed exceptions is marked as group`() {
        val exception = Exception("inner")
        val suppressedException = Exception("suppressed exception")
        exception.addSuppressed(suppressedException)

        val outerException = Exception("outer", exception)

        val queue = fixture.getSut().extractExceptionQueue(outerException)

        val suppressedInQueue = queue.pop()
        val mainInQueue = queue.pop()
        val outerInQueue = queue.pop()

        assertEquals("suppressed exception", suppressedInQueue.value)
        assertEquals(2, suppressedInQueue.mechanism?.exceptionId)
        assertEquals(1, suppressedInQueue.mechanism?.parentId)
        assertEquals("suppressed", suppressedInQueue.mechanism?.type)

        assertEquals("inner", mainInQueue.value)
        assertEquals(1, mainInQueue.mechanism?.exceptionId)
        assertEquals(0, mainInQueue.mechanism?.parentId)
        assertEquals("chained", mainInQueue.mechanism?.type)
//        assertEquals(true, mainInQueue.mechanism?.isExceptionGroup)

        assertEquals("outer", outerInQueue.value)
        assertEquals(0, outerInQueue.mechanism?.exceptionId)
        assertNull(outerInQueue.mechanism?.parentId)
        assertEquals("chained", outerInQueue.mechanism?.type)
//        assertNull(outerInQueue.mechanism?.isExceptionGroup)
    }

    @Test
    fun `nested exception within Mechanism that contains suppressed exceptions is marked as group`() {
        val exception = Exception("inner")
        val suppressedException = Exception("suppressed exception")
        exception.addSuppressed(suppressedException)

        val mechanism = Mechanism()
        mechanism.type = "ANR"
        val thread = Thread()

        val outerException = ExceptionMechanismException(mechanism, Exception("outer", exception), thread)

        val queue = fixture.getSut().extractExceptionQueue(outerException)

        val suppressedInQueue = queue.pop()
        val mainInQueue = queue.pop()
        val outerInQueue = queue.pop()

        assertEquals("suppressed exception", suppressedInQueue.value)
        assertEquals(2, suppressedInQueue.mechanism?.exceptionId)
        assertEquals(1, suppressedInQueue.mechanism?.parentId)
        assertEquals("suppressed", suppressedInQueue.mechanism?.type)

        assertEquals("inner", mainInQueue.value)
        assertEquals(1, mainInQueue.mechanism?.exceptionId)
        assertEquals(0, mainInQueue.mechanism?.parentId)
        assertEquals("chained", mainInQueue.mechanism?.type)
//        assertEquals(true, mainInQueue.mechanism?.isExceptionGroup)

        assertEquals("outer", outerInQueue.value)
        assertEquals(0, outerInQueue.mechanism?.exceptionId)
        assertNull(outerInQueue.mechanism?.parentId)
        assertEquals("ANR", outerInQueue.mechanism?.type)
//        assertNull(outerInQueue.mechanism?.isExceptionGroup)
    }

    @Test
    fun `nested exception with nested exception that contain suppressed exceptions are marked as group`() {
        val innerMostException = Exception("innermost")
        val innerMostSuppressed = Exception("innermostSuppressed")
        innerMostException.addSuppressed(innerMostSuppressed)

        val innerException = Exception("inner", innerMostException)
        val innerSuppressed = Exception("suppressed exception")
        innerException.addSuppressed(innerSuppressed)

        val outerException = Exception("outer", innerException)

        val queue = fixture.getSut().extractExceptionQueue(outerException)

        val innerMostSuppressedInQueue = queue.pop()
        val innerMostExceptionInQueue = queue.pop()
        val innerSuppressedInQueue = queue.pop()
        val innerExceptionInQueue = queue.pop()
        val outerInQueue = queue.pop()

        assertEquals("innermostSuppressed", innerMostSuppressedInQueue.value)
        assertEquals(4, innerMostSuppressedInQueue.mechanism?.exceptionId)
        assertEquals(3, innerMostSuppressedInQueue.mechanism?.parentId)
        assertEquals("suppressed", innerMostSuppressedInQueue.mechanism?.type)
        assertNull(innerMostSuppressedInQueue.mechanism?.isExceptionGroup)

        assertEquals("innermost", innerMostExceptionInQueue.value)
        assertEquals(3, innerMostExceptionInQueue.mechanism?.exceptionId)
        assertEquals(1, innerMostExceptionInQueue.mechanism?.parentId)
        assertEquals("chained", innerMostExceptionInQueue.mechanism?.type)
//        assertEquals(true, innerMostExceptionInQueue.mechanism?.isExceptionGroup)

        assertEquals("suppressed exception", innerSuppressedInQueue.value)
        assertEquals(2, innerSuppressedInQueue.mechanism?.exceptionId)
        assertEquals(1, innerSuppressedInQueue.mechanism?.parentId)
        assertEquals("suppressed", innerSuppressedInQueue.mechanism?.type)
        assertNull(innerSuppressedInQueue.mechanism?.isExceptionGroup)

        assertEquals("inner", innerExceptionInQueue.value)
        assertEquals(1, innerExceptionInQueue.mechanism?.exceptionId)
        assertEquals(0, innerExceptionInQueue.mechanism?.parentId)
        assertEquals("chained", innerExceptionInQueue.mechanism?.type)
//        assertEquals(true, innerExceptionInQueue.mechanism?.isExceptionGroup)

        assertEquals("outer", outerInQueue.value)
        assertEquals(0, outerInQueue.mechanism?.exceptionId)
        assertNull(outerInQueue.mechanism?.parentId)
        assertNull(outerInQueue.mechanism?.isExceptionGroup)
        assertEquals("chained", outerInQueue.mechanism?.type)
    }

    @Test
    fun `nested exception with nested exception that contain suppressed exceptions with a nested exception are marked as group`() {
        val innerMostException = Exception("innermost")

        val innerMostSuppressedNestedException = Exception("innermostSuppressedNested")
        val innerMostSuppressed = Exception("innermostSuppressed", innerMostSuppressedNestedException)
        innerMostException.addSuppressed(innerMostSuppressed)

        val innerException = Exception("inner", innerMostException)
        val innerSuppressed = Exception("suppressed exception")
        innerException.addSuppressed(innerSuppressed)

        val outerException = Exception("outer", innerException)

        val queue = fixture.getSut().extractExceptionQueue(outerException)

        val innerMostSuppressedNestedExceptionInQueue = queue.pop()
        val innerMostSuppressedInQueue = queue.pop()
        val innerMostExceptionInQueue = queue.pop()
        val innerSuppressedInQueue = queue.pop()
        val innerExceptionInQueue = queue.pop()
        val outerInQueue = queue.pop()

        assertEquals("innermostSuppressedNested", innerMostSuppressedNestedExceptionInQueue.value)
        assertEquals(5, innerMostSuppressedNestedExceptionInQueue.mechanism?.exceptionId)
        assertEquals(4, innerMostSuppressedNestedExceptionInQueue.mechanism?.parentId)
        assertNull(innerMostSuppressedNestedExceptionInQueue.mechanism?.isExceptionGroup)
        assertEquals("chained", innerMostSuppressedNestedExceptionInQueue.mechanism?.type)

        assertEquals("innermostSuppressed", innerMostSuppressedInQueue.value)
        assertEquals(4, innerMostSuppressedInQueue.mechanism?.exceptionId)
        assertEquals(3, innerMostSuppressedInQueue.mechanism?.parentId)
        assertNull(innerMostSuppressedInQueue.mechanism?.isExceptionGroup)
        assertEquals("suppressed", innerMostSuppressedInQueue.mechanism?.type)

        assertEquals("innermost", innerMostExceptionInQueue.value)
        assertEquals(3, innerMostExceptionInQueue.mechanism?.exceptionId)
        assertEquals(1, innerMostExceptionInQueue.mechanism?.parentId)
        assertEquals("chained", innerMostExceptionInQueue.mechanism?.type)
//        assertEquals(true, innerMostExceptionInQueue.mechanism?.isExceptionGroup)

        assertEquals("suppressed exception", innerSuppressedInQueue.value)
        assertEquals(2, innerSuppressedInQueue.mechanism?.exceptionId)
        assertEquals(1, innerSuppressedInQueue.mechanism?.parentId)
        assertEquals("suppressed", innerSuppressedInQueue.mechanism?.type)
        assertNull(innerSuppressedInQueue.mechanism?.isExceptionGroup)

        assertEquals("inner", innerExceptionInQueue.value)
        assertEquals(1, innerExceptionInQueue.mechanism?.exceptionId)
        assertEquals(0, innerExceptionInQueue.mechanism?.parentId)
        assertEquals("chained", innerExceptionInQueue.mechanism?.type)
//        assertEquals(true, innerExceptionInQueue.mechanism?.isExceptionGroup)

        assertEquals("outer", outerInQueue.value)
        assertEquals(0, outerInQueue.mechanism?.exceptionId)
        assertNull(outerInQueue.mechanism?.parentId)
        assertNull(outerInQueue.mechanism?.isExceptionGroup)
        assertEquals("chained", outerInQueue.mechanism?.type)
    }

    internal class InnerClassThrowable constructor(
        cause: Throwable? = null,
    ) : Throwable(cause)

    private val anonymousException =
        object : Exception() {
        }
}
