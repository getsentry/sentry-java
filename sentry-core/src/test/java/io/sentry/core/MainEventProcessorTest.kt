package io.sentry.core

import com.nhaarman.mockitokotlin2.mock
import io.sentry.core.hints.ApplyScopeData
import io.sentry.core.hints.Cached
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainEventProcessorTest {
    class Fixture {
        private val sentryOptions: SentryOptions = SentryOptions().apply {
            dsn = dsnString
            release = "release"
            environment = "environment"
            dist = "dist"
            serverName = "server"
        }
        fun getSut(attachThreads: Boolean = true): MainEventProcessor {
            sentryOptions.isAttachThreads = attachThreads
            return MainEventProcessor(sentryOptions)
        }
    }

    private val fixture = Fixture()

    @Test
    fun `when processing an event from UncaughtExceptionHandlerIntegration, crashed thread is flagged, mechanism added`() {
        val sut = fixture.getSut()

        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, null)

        assertSame(crashedThread.id, event.exceptions.first().threadId)
        assertTrue(event.threads.first { t -> t.id == crashedThread.id }.isCrashed)
        assertFalse(event.exceptions.first().mechanism.isHandled)
    }

    @Test
    fun `when processing an event from UncaughtExceptionHandlerIntegration, crashed thread is flagged, even if its not the current thread`() {
        val sut = fixture.getSut()

        val crashedThread = Thread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, null)

        assertTrue(event.threads.any { it.isCrashed })
    }

    @Test
    fun `When hint is not Cached, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, null)

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertTrue(event.threads.first { t -> t.id == crashedThread.id }.isCrashed)
    }

    @Test
    fun `When hint is ApplyScopeData, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, mock<ApplyScopeData>())

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertTrue(event.threads.first { t -> t.id == crashedThread.id }.isCrashed)
    }

    @Test
    fun `data should be applied only if event doesn't have them`() {
        val sut = fixture.getSut()
        var event = generateCrashedEvent()
        event.dist = "eventDist"
        event.environment = "eventEnvironment"
        event.release = "eventRelease"
        event.serverName = "eventServerName"

        event = sut.process(event, null)

        assertEquals("eventRelease", event.release)
        assertEquals("eventEnvironment", event.environment)
        assertEquals("eventDist", event.dist)
        assertEquals("eventServerName", event.serverName)
    }

    @Test
    fun `When hint is Cached, data should not be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, CachedEvent())

        assertNull(event.release)
        assertNull(event.environment)
        assertNull(event.dist)
        assertNull(event.serverName)
        assertNull(event.threads)
    }

    @Test
    fun `When hint is Cached but also ApplyScopeData, data should be applied`() {
        val sut = fixture.getSut()
        val crashedThread = Thread.currentThread()
        var event = generateCrashedEvent(crashedThread)
        event = sut.process(event, mock<CustomCachedApplyScopeDataHint>())

        assertEquals("release", event.release)
        assertEquals("environment", event.environment)
        assertEquals("dist", event.dist)
        assertEquals("server", event.serverName)
        assertTrue(event.threads.first { t -> t.id == crashedThread.id }.isCrashed)
    }

    @Test
    fun `when processing an event and attach threads is disabled, threads should not be set`() {
        val sut = fixture.getSut(false)

        var event = SentryEvent()
        event = sut.process(event, null)

        assertNull(event.threads)
    }

    @Test
    fun `when processing an event and attach threads is enabled, threads should be set`() {
        val sut = fixture.getSut()

        var event = SentryEvent()
        event = sut.process(event, null)

        assertNotNull(event.threads)
    }

    private fun generateCrashedEvent(crashedThread: Thread = Thread.currentThread()) = SentryEvent().apply {
        val mockThrowable = mock<Throwable>()
        val actualThrowable = UncaughtExceptionHandlerIntegration.getUnhandledThrowable(crashedThread, mockThrowable)
        throwable = actualThrowable
    }

    internal class CustomCachedApplyScopeDataHint : Cached, ApplyScopeData
}
