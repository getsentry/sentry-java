package io.sentry.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoClosableReentrantLockTest {

    @Test
    fun `calls lock in acquire and unlock on close`() {
        val lock = AutoClosableReentrantLock()
        lock.acquire().use {
            assertTrue(lock.isLocked)
        }
        assertFalse(lock.isLocked)
    }
}
