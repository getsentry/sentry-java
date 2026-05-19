package io.sentry

import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnrHeartbeatRegistryTest {

  @AfterTest
  fun tearDown() {
    // Reset the static registry to avoid cross-test bleed.
    AnrHeartbeatRegistry.setListener(null)
  }

  @Test
  fun `notifyAlive without a listener is a no-op`() {
    // No setListener call - this must not throw.
    AnrHeartbeatRegistry.notifyAlive()
  }

  @Test
  fun `notifyAlive invokes the registered listener`() {
    val counter = AtomicInteger(0)
    AnrHeartbeatRegistry.setListener({ counter.incrementAndGet() })

    AnrHeartbeatRegistry.notifyAlive()
    AnrHeartbeatRegistry.notifyAlive()
    AnrHeartbeatRegistry.notifyAlive()

    assertEquals(3, counter.get())
  }

  @Test
  fun `clearing the listener stops notifications`() {
    val counter = AtomicInteger(0)
    AnrHeartbeatRegistry.setListener({ counter.incrementAndGet() })
    AnrHeartbeatRegistry.notifyAlive()
    assertEquals(1, counter.get())

    AnrHeartbeatRegistry.setListener(null)
    AnrHeartbeatRegistry.notifyAlive()
    AnrHeartbeatRegistry.notifyAlive()
    assertEquals(1, counter.get())
  }

  @Test
  fun `setListener replaces the previous listener`() {
    val firstCount = AtomicInteger(0)
    val secondCount = AtomicInteger(0)

    AnrHeartbeatRegistry.setListener({ firstCount.incrementAndGet() })
    AnrHeartbeatRegistry.notifyAlive()

    AnrHeartbeatRegistry.setListener({ secondCount.incrementAndGet() })
    AnrHeartbeatRegistry.notifyAlive()
    AnrHeartbeatRegistry.notifyAlive()

    assertEquals(1, firstCount.get())
    assertEquals(2, secondCount.get())
  }
}
