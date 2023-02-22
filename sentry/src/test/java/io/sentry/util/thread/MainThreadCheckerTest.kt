package io.sentry.util.thread

import io.sentry.protocol.SentryThread
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainThreadCheckerTest {

    private val mainThreadChecker = MainThreadChecker.getInstance()

    @Test
    fun `When calling isMainThread from the same thread, it should return true`() {
        assertTrue(mainThreadChecker.isMainThread)
    }

    @Test
    fun `When calling isMainThread with the current thread, it should return true`() {
        val thread = Thread.currentThread()
        assertTrue(mainThreadChecker.isMainThread(thread))
    }

    @Test
    fun `When calling isMainThread from a different thread, it should return false`() {
        val thread = Thread()
        assertFalse(mainThreadChecker.isMainThread(thread))
    }

    @Test
    fun `When calling isMainThread with the current SentryThread, it should return true`() {
        val thread = Thread.currentThread()
        val sentryThread = SentryThread().apply {
            id = thread.id
        }
        assertTrue(mainThreadChecker.isMainThread(sentryThread))
    }

    @Test
    fun `When calling isMainThread from a different SentryThread, it should return false`() {
        val thread = Thread()
        val sentryThread = SentryThread().apply {
            id = thread.id
        }
        assertFalse(mainThreadChecker.isMainThread(sentryThread))
    }
}
