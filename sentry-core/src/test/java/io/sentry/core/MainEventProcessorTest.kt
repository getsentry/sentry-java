package io.sentry.core

import com.nhaarman.mockitokotlin2.mock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainEventProcessorTest {
    class Fixture {
        var sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
        }
        fun getSut() = MainEventProcessor(sentryOptions)
    }

    private val fixture = Fixture()

    @Test
    fun `when processing an event from UncaughtExceptionHandlerIntegration, crashed thread is flaged, mechanism added`() {
        val sut = fixture.getSut()

        val crashedThread = Thread.currentThread()
        val mockThrowable = mock<Throwable>()
        val actualThrowable = UncaughtExceptionHandlerIntegration.getUnhandledThrowable(crashedThread, mockThrowable)
        val event = SentryEvent().apply { throwable = actualThrowable }
        sut.process(event)

        assertSame(crashedThread.id, event.exceptions.first().threadId)
        assertTrue(event.threads.first { t -> t.id == crashedThread.id }.crashed)
        assertFalse(event.exceptions.first().mechanism.handled)
    }
}
