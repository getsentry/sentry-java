package io.sentry.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryThreadFactoryTest {

    class Fixture {
        internal fun getSut(attachStacktrace: Boolean = true) = SentryThreadFactory(SentryStackTraceFactory(listOf("io.sentry"), listOf()), with(SentryOptions()) {
            isAttachStacktrace = attachStacktrace
            this
        })
    }

    private val fixture = Fixture()

    @Test
    fun `when getCurrentThreads is called, not empty result`() {
        val sut = fixture.getSut()
        val threads = sut.getCurrentThreads(null)
        assertNotSame(0, threads!!.count())
    }

    @Test
    fun `when currentThreads is called, current thread is marked crashed`() {
        val sut = fixture.getSut()
        assertEquals(1, sut.getCurrentThreads(null)!!.filter { it.isCrashed }.count())
    }

    @Test
    fun `when currentThreads is called, thread state is captured`() {
        val sut = fixture.getSut()
        assertTrue(sut.getCurrentThreads(null)!!.all { it.state != null })
    }

    @Test
    fun `when currentThreads is called, some thread stack frames are captured`() {
        val sut = fixture.getSut()
        assertTrue(sut.getCurrentThreads(null)!!.filter { it.stacktrace != null }.any { it.stacktrace.frames.count() > 0 })
    }

    @Test
    fun `when currentThreads and attachStacktrace is disabled, stack frames are not captured`() {
        val sut = fixture.getSut(false)
        assertFalse(sut.getCurrentThreads(null)!!.filter { it.stacktrace != null }.any { it.stacktrace.frames.count() > 0 })
    }

    @Test
    fun `when getAllStackTraces don't return the current thread, add it manually`() {
        val sut = fixture.getSut()
        val stackTraces = Thread.getAllStackTraces()
        val currentThread = Thread.currentThread()
        stackTraces.remove(currentThread)

        val threads = sut.getCurrentThreads(stackTraces, null)

        assertNotNull(threads!!.firstOrNull { it.id == currentThread.id })
    }

    @Test
    fun `When passing empty param to getCurrentThreads, returns null`() {
        val sut = fixture.getSut()
        val threads = sut.getCurrentThreads(mapOf(), null)

        assertNull(threads)
    }

    @Test
    fun `when given mechanismThreadIds is there, thread should be crashed`() {
        val sut = fixture.getSut()
        val thread = Thread()
        val threadIds = listOf(thread.id)
        val stacktraces = emptyArray<StackTraceElement>()
        val threadList = mutableMapOf(thread to stacktraces)

        val threads = sut.getCurrentThreads(threadList, threadIds)

        assertNotNull(threads!!.firstOrNull { it.isCrashed })
    }
}
