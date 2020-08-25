package io.sentry.android.core.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.sentry.core.protocol.SentryThread
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainThreadCheckerTest {

    @Test
    fun `When calling isMainThread from the same thread, it should return true`() {
        assertTrue(MainThreadChecker.isMainThread())
    }

    @Test
    fun `When calling isMainThread with the current thread, it should return true`() {
        val thread = Thread.currentThread()
        assertTrue(MainThreadChecker.isMainThread(thread))
    }

    @Test
    fun `When calling isMainThread from a different thread, it should return false`() {
        val thread = Thread()
        assertFalse(MainThreadChecker.isMainThread(thread))
    }

    @Test
    fun `When calling isMainThread with the current SentryThread, it should return true`() {
        val thread = Thread.currentThread()
        val sentryThread = SentryThread().apply {
            id = thread.id
        }
        assertTrue(MainThreadChecker.isMainThread(sentryThread))
    }

    @Test
    fun `When calling isMainThread from a different SentryThread, it should return false`() {
        val thread = Thread()
        val sentryThread = SentryThread().apply {
            id = thread.id
        }
        assertFalse(MainThreadChecker.isMainThread(sentryThread))
    }
}
