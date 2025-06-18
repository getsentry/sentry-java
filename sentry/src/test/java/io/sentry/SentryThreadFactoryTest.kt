package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryThreadFactoryTest {
    class Fixture {
        internal fun getSut(attachStacktrace: Boolean = true) =
            SentryThreadFactory(
                SentryStackTraceFactory(SentryOptions().apply { addInAppExclude("io.sentry") }),
                with(SentryOptions()) {
                    isAttachStacktrace = attachStacktrace
                    this
                },
            )
    }

    private val fixture = Fixture()

    @Test
    fun `when getCurrentThreads is called, not empty result`() {
        val sut = fixture.getSut()
        val threads = sut.getCurrentThreads(null)
        assertNotEquals(0, threads!!.count())
    }

    @Test
    fun `when currentThreads is called, current thread is marked crashed`() {
        val sut = fixture.getSut()
        assertEquals(1, sut.getCurrentThreads(null)!!.filter { it.isCrashed == true }.count())
    }

    @Test
    fun `when currentThreads is called with ignoreCurrentThread, current thread is not marked crashed`() {
        val sut = fixture.getSut()
        assertEquals(0, sut.getCurrentThreads(null, true)!!.filter { it.isCrashed == true }.count())
    }

    @Test
    fun `when currentThreads is called, thread state is captured`() {
        val sut = fixture.getSut()
        assertTrue(sut.getCurrentThreads(null)!!.all { it.state != null })
    }

    @Test
    fun `when currentThreads is called, some thread stack frames are captured`() {
        val sut = fixture.getSut()
        assertTrue(sut.getCurrentThreads(null)!!.filter { it.stacktrace != null }.any { it.stacktrace!!.frames!!.count() > 0 })
    }

    @Test
    fun `when currentThreads is called, stack traces are snapshot`() {
        val sut = fixture.getSut()
        assertTrue(sut.getCurrentThreads(null)!!.filter { it.stacktrace != null }.any { it.stacktrace!!.snapshot == true })
    }

    @Test
    fun `when currentThreads and attachStacktrace is disabled, stack frames are not captured`() {
        val sut = fixture.getSut(false)
        assertFalse(sut.getCurrentThreads(null)!!.filter { it.stacktrace != null }.any { it.stacktrace!!.frames!!.count() > 0 })
    }

    @Test
    fun `when getAllStackTraces don't return the current thread, add it manually`() {
        val sut = fixture.getSut()
        val stackTraces = Thread.getAllStackTraces()
        val currentThread = Thread.currentThread()
        stackTraces.remove(currentThread)

        val threads = sut.getCurrentThreads(stackTraces, null, false)

        assertNotNull(threads!!.firstOrNull { it.id == currentThread.id })
    }

    @Test
    fun `When passing empty param to getCurrentThreads, returns null`() {
        val sut = fixture.getSut()
        val threads = sut.getCurrentThreads(mapOf(), null, false)

        assertNull(threads)
    }

    @Test
    fun `when given mechanismThreadIds is there, thread should be crashed`() {
        val sut = fixture.getSut()
        val thread = Thread()
        val threadIds = listOf(thread.id)
        val stacktraces = emptyArray<StackTraceElement>()
        val threadList = mutableMapOf(thread to stacktraces)

        val threads = sut.getCurrentThreads(threadList, threadIds, false)

        assertNotNull(threads!!.firstOrNull { it.isCrashed == true })
    }

    @Test
    fun `when getCurrentThread is called, returns current thread`() {
        val sut = fixture.getSut()
        val threads = sut.currentThread
        assertEquals(1, threads!!.count())
    }
}
