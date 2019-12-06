package io.sentry.core

import com.nhaarman.mockitokotlin2.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainEventProcessorTest {
    class Fixture {
        private val sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
            release = "release"
            environment = "environment"
        }
        fun getSut() = MainEventProcessor(sentryOptions)
    }

    private val fixture = Fixture()

    @Test
    fun `when processing an event from UncaughtExceptionHandlerIntegration, crashed thread is flaged, mechanism added`() {
        val sut = fixture.getSut()

        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, null)

        assertSame(crashedThread.id, event.exceptions.first().threadId)
        assertTrue(event.threads.first { t -> t.id == crashedThread.id }.isCrashed)
        assertFalse(event.exceptions.first().mechanism.isHandled)
    }

    @Test
    fun `When hint is not Cached, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, null)

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertTrue(event.threads.first { t -> t.id == crashedThread.id }.isCrashed)
    }

    @Test
    fun `When hint is Cached, data should not be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, CachedEvent())

        assertNull(event.release)
        assertNull(event.environment)
        assertNull(event.threads)
    }

    private fun generateCrashedEvent(crashedThread: Thread = Thread.currentThread()) = SentryEvent().apply {
        val mockThrowable = mock<Throwable>()
        val actualThrowable = UncaughtExceptionHandlerIntegration.getUnhandledThrowable(crashedThread, mockThrowable)
        throwable = actualThrowable
    }
}
