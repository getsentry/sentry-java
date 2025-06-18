package io.sentry.android.core.internal.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.protocol.SentryThread
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidThreadCheckerTest {
    @Test
    fun `When calling isMainThread from the same thread, it should return true`() {
        assertTrue(AndroidThreadChecker.getInstance().isMainThread)
    }

    @Test
    fun `When calling isMainThread with the current thread, it should return true`() {
        val thread = Thread.currentThread()
        assertTrue(AndroidThreadChecker.getInstance().isMainThread(thread))
    }

    @Test
    fun `When calling isMainThread from a different thread, it should return false`() {
        val thread = Thread()
        assertFalse(AndroidThreadChecker.getInstance().isMainThread(thread))
    }

    @Test
    fun `When calling isMainThread with the current SentryThread, it should return true`() {
        val thread = Thread.currentThread()
        val sentryThread =
            SentryThread().apply {
                id = thread.id
            }
        assertTrue(AndroidThreadChecker.getInstance().isMainThread(sentryThread))
    }

    @Test
    fun `When calling isMainThread from a different SentryThread, it should return false`() {
        val thread = Thread()
        val sentryThread =
            SentryThread().apply {
                id = thread.id
            }
        assertFalse(AndroidThreadChecker.getInstance().isMainThread(sentryThread))
    }

    @Test
    fun `currentThreadName returns main when called on the main thread`() {
        val thread = Thread.currentThread()
        thread.name = "test"
        assertEquals("main", AndroidThreadChecker.getInstance().currentThreadName)
    }

    @Test
    fun `currentThreadName returns the name of the current thread`() {
        var threadName = ""
        val thread =
            Thread {
                threadName = AndroidThreadChecker.getInstance().currentThreadName
            }
        thread.name = "test"
        thread.start()
        thread.join()
        assertEquals("test", threadName)
    }
}
