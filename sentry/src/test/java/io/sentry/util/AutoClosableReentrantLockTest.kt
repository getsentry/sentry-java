package io.sentry.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AutoClosableReentrantLockTest {
  @Test
  fun `calls lock in acquire and unlock on close`() {
    val lock = AutoClosableReentrantLock()
    lock.acquire().use { assertTrue(lock.isLocked) }
    assertFalse(lock.isLocked)
  }

  @Test
  fun `acquire returns the lock itself as the token, allocating nothing`() {
    val lock = AutoClosableReentrantLock()
    lock.acquire().use { token -> assertSame(lock, token) }
  }

  @Test
  fun `does not allocate the underlying lock until first acquire`() {
    val lock = AutoClosableReentrantLock()
    assertFalse(lock.isLockAllocated)
    lock.acquire().use {}
    assertTrue(lock.isLockAllocated)
  }

  @Test
  fun `supports reentrant acquire from the same thread`() {
    val lock = AutoClosableReentrantLock()
    lock.acquire().use {
      lock.acquire().use { assertTrue(lock.isLocked) }
      assertTrue(lock.isLocked)
    }
    assertFalse(lock.isLocked)
  }

  @Test
  fun `mutually excludes concurrent threads`() {
    val lock = AutoClosableReentrantLock()
    val inCriticalSection = AtomicInteger(0)
    val maxObserved = AtomicInteger(0)
    val start = CountDownLatch(1)
    val threadCount = 8
    val iterations = 1000
    val threads =
      (0 until threadCount).map {
        Thread {
          start.await()
          repeat(iterations) {
            lock.acquire().use {
              val current = inCriticalSection.incrementAndGet()
              maxObserved.accumulateAndGet(current, ::maxOf)
              inCriticalSection.decrementAndGet()
            }
          }
        }
      }
    threads.forEach(Thread::start)
    start.countDown()
    threads.forEach { it.join(TimeUnit.SECONDS.toMillis(10)) }

    assertEquals(1, maxObserved.get())
    assertFalse(lock.isLocked)
  }
}
