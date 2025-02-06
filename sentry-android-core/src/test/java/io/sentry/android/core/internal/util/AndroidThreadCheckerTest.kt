package io.sentry.android.core.internal.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.protocol.SentryThread
import org.junit.runner.RunWith
import kotlin.test.Test
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
        val sentryThread = SentryThread().apply {
            id = thread.id
        }
        assertTrue(AndroidThreadChecker.getInstance().isMainThread(sentryThread))
    }

    @Test
    fun `When calling isMainThread from a different SentryThread, it should return false`() {
        val thread = Thread()
        val sentryThread = SentryThread().apply {
            id = thread.id
        }
        assertFalse(AndroidThreadChecker.getInstance().isMainThread(sentryThread))
    }
}
