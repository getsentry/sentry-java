package io.sentry.util.thread

import io.sentry.protocol.SentryThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadCheckerTest {
    private val threadChecker = ThreadChecker.getInstance()

    @Test
    fun `When calling isMainThread from the same thread, it should return true`() {
        assertTrue(threadChecker.isMainThread)
    }

    @Test
    fun `When calling isMainThread with the current thread, it should return true`() {
        val thread = Thread.currentThread()
        assertTrue(threadChecker.isMainThread(thread))
    }

    @Test
    fun `When calling isMainThread from a different thread, it should return false`() {
        val thread = Thread()
        assertFalse(threadChecker.isMainThread(thread))
    }

    @Test
    fun `When calling isMainThread with the current SentryThread, it should return true`() {
        val thread = Thread.currentThread()
        val sentryThread =
            SentryThread().apply {
                id = thread.id
            }
        assertTrue(threadChecker.isMainThread(sentryThread))
    }

    @Test
    fun `When calling isMainThread from a different SentryThread, it should return false`() {
        val thread = Thread()
        val sentryThread =
            SentryThread().apply {
                id = thread.id
            }
        assertFalse(threadChecker.isMainThread(sentryThread))
    }

    @Test
    fun `currentThreadName returns the name of the current thread`() {
        val thread = Thread.currentThread()
        thread.name = "test"
        assertEquals("test", threadChecker.currentThreadName)
    }
}
