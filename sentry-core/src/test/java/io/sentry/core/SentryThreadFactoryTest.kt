package io.sentry.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class SentryThreadFactoryTest {

    private val sut = SentryThreadFactory(SentryStackTraceFactory(listOf("io.sentry"), listOf()))

    @Test
    fun `when getCurrentThreads is called, not empty result`() {
        val threads = sut.currentThreads
        assertNotSame(0, threads.count())
    }

    @Test
    fun `when currentThreads is called, current thread is marked crashed`() =
        assertEquals(1, sut.currentThreads.filter { it.isCrashed }.count())

    @Test
    fun `when currentThreads is called, thread state is captured`() =
        assertTrue(sut.currentThreads.all { it.state != null })

    @Test
    fun `when currentThreads is called, some thread stack frames are captured`() =
        assertTrue(sut.currentThreads.filter { it.stacktrace != null }.any { it.stacktrace.frames.count() > 0 })
}
