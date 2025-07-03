/*
 * Adapted from https://github.com/MatejTymes/JavaFixes/blob/37e74b9d0a29f7a47485c6d1bb1307f01fb93634/src/test/java/javafixes/concurrency/ReusableCountLatchTest.java
 *
 * Copyright (C) 2016 Matej Tymes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sentry.transport

import java.util.concurrent.Executors.newScheduledThreadPool
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReusableCountLatchTest {
  @Test
  fun `should reflect initial value`() {
    val positiveCount = 10
    assertEquals(positiveCount, ReusableCountLatch(positiveCount).count)
    assertEquals(0, ReusableCountLatch().count)
  }

  @Test
  fun `should fail on negative initial value`() {
    assertFailsWith(IllegalArgumentException::class) { ReusableCountLatch(-10) }
  }

  @Test
  fun `should return current count`() {
    val latch = ReusableCountLatch(0)
    var expectedCount = 0
    for (iteration in 0..9999) {
      if (Random.nextBoolean()) {
        latch.increment()
        expectedCount++
      } else {
        latch.decrement()
        if (expectedCount > 0) {
          expectedCount--
        }
      }
      assertEquals(expectedCount, latch.count)
    }
  }

  @Test(timeout = 500L)
  fun `should not block if zero`() {
    val latch = ReusableCountLatch(2)
    latch.decrement()
    latch.decrement()

    // When
    val startTime = System.currentTimeMillis()
    latch.waitTillZero()
    val duration = System.currentTimeMillis() - startTime

    // Then
    assertTrue(duration < 10L)
  }

  @Test(timeout = 500L)
  fun `should block if not zero`() {
    val latch = ReusableCountLatch(2)
    latch.decrement()

    // When
    val startTime = System.currentTimeMillis()
    val isZero = latch.waitTillZero(200, TimeUnit.MILLISECONDS)
    val duration = System.currentTimeMillis() - startTime

    // Then
    assertTrue(duration >= 200L)
    assertFalse(isZero)
  }

  @Test(timeout = 2000L)
  fun `should block till zero`() {
    val executor: ScheduledExecutorService = newScheduledThreadPool(2)
    try {
      val latch = ReusableCountLatch()
      latch.increment()
      latch.increment()
      executor.schedule({ latch.decrement() }, 200L, TimeUnit.MILLISECONDS)
      executor.schedule({ latch.decrement() }, 600L, TimeUnit.MILLISECONDS)
      val startTime = System.currentTimeMillis()
      latch.waitTillZero()
      val duration = System.currentTimeMillis() - startTime
      assertTrue(duration >= 550L)
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  fun `should be reusable`() {
    val latch = ReusableCountLatch()
    latch.increment()
    var startTime = System.currentTimeMillis()
    var isZero = latch.waitTillZero(150, TimeUnit.MILLISECONDS)
    var duration = System.currentTimeMillis() - startTime
    assertFalse(isZero)
    assertTrue(duration >= 150L)
    latch.decrement()
    startTime = System.currentTimeMillis()
    isZero = latch.waitTillZero(150, TimeUnit.MILLISECONDS)
    duration = System.currentTimeMillis() - startTime
    assertTrue(isZero)
    assertTrue(duration < 10L)
    latch.increment()
    latch.increment()
    startTime = System.currentTimeMillis()
    isZero = latch.waitTillZero(150, TimeUnit.MILLISECONDS)
    duration = System.currentTimeMillis() - startTime
    assertFalse(isZero)
    assertTrue(duration >= 150L)
    latch.decrement()
    latch.decrement()
    startTime = System.currentTimeMillis()
    isZero = latch.waitTillZero(150, TimeUnit.MILLISECONDS)
    duration = System.currentTimeMillis() - startTime
    assertTrue(isZero)
    assertTrue(duration < 10L)
  }
}
