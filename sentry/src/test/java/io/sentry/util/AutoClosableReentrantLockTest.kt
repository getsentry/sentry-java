package io.sentry.util

import com.google.common.truth.Truth.assertThat
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
  fun `tryAcquire returns the lock itself as the token when free`() {
    val lock = AutoClosableReentrantLock()
    val token = lock.tryAcquire(1, TimeUnit.SECONDS)
    assertThat(token).isSameInstanceAs(lock)
    token!!.use { assertThat(lock.isLocked).isTrue() }
    assertThat(lock.isLocked).isFalse()
  }

  @Test
  fun `tryAcquire does not allocate the underlying lock until first use`() {
    val lock = AutoClosableReentrantLock()
    assertThat(lock.isLockAllocated).isFalse()
    lock.tryAcquire(1, TimeUnit.SECONDS)!!.use {}
    assertThat(lock.isLockAllocated).isTrue()
  }

  @Test
  fun `tryAcquire returns null when another thread holds the lock past the timeout`() {
    val lock = AutoClosableReentrantLock()
    val acquired = CountDownLatch(1)
    val release = CountDownLatch(1)
    val holder = Thread {
      lock.acquire().use {
        acquired.countDown()
        release.await()
      }
    }
    holder.start()
    try {
      assertThat(acquired.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(lock.tryAcquire(10, TimeUnit.MILLISECONDS)).isNull()
    } finally {
      release.countDown()
      holder.join(TimeUnit.SECONDS.toMillis(10))
    }
    assertThat(lock.isLocked).isFalse()
  }

  @Test
  fun `tryAcquire is reentrant from the same thread`() {
    val lock = AutoClosableReentrantLock()
    lock.acquire().use {
      lock.tryAcquire(0, TimeUnit.MILLISECONDS)!!.use { assertThat(lock.isLocked).isTrue() }
      assertThat(lock.isLocked).isTrue()
    }
    assertThat(lock.isLocked).isFalse()
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
