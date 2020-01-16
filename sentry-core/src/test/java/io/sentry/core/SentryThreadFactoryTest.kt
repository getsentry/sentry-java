package io.sentry.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryThreadFactoryTest {

    private val sut = SentryThreadFactory(SentryStackTraceFactory(listOf("io.sentry"), listOf()))

    @Test
    fun `when getCurrentThreads is called, not empty result`() {
        val threads = sut.currentThreads
        assertNotSame(0, threads!!.count())
    }

    @Test
    fun `when currentThreads is called, current thread is marked crashed`() =
        assertEquals(1, sut.currentThreads!!.filter { it.isCrashed }.count())

    @Test
    fun `when currentThreads is called, thread state is captured`() =
        assertTrue(sut.currentThreads!!.all { it.state != null })

    @Test
    fun `when currentThreads is called, some thread stack frames are captured`() =
        assertTrue(sut.currentThreads!!.filter { it.stacktrace != null }.any { it.stacktrace.frames.count() > 0 })

    @Test
    fun `when getAllStackTraces don't return the current thread, add it manually`() {
        val stackTraces = Thread.getAllStackTraces()
        val currentThread = Thread.currentThread()
        stackTraces.remove(currentThread)

        val threads = sut.getCurrentThreads(stackTraces)

        assertNotNull(threads!!.firstOrNull { it.id == currentThread.id })
    }

    @Test
    fun `When passing empty param to getCurrentThreads, returns null`() {
        val threads = sut.getCurrentThreads(mapOf())

        assertNull(threads)
    }
}
