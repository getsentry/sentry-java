package io.sentry.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class ArtContextTest {
  @Test
  fun `copying art context wont have the same references`() {
    val artContext = ArtContext()
    val unknown = mapOf(Pair("unknown", "unknown"))
    artContext.setUnknown(unknown)

    val clone = ArtContext(artContext)

    assertNotNull(clone)
    assertNotSame(artContext, clone)
    assertNotSame(artContext.unknown, clone.unknown)
  }

  @Test
  fun `copying art context will have the same values`() {
    val artContext = ArtContext()
    artContext.gcTotalCount = 10L
    artContext.gcTotalTime = 11.807
    artContext.gcBlockingCount = 2L
    artContext.gcBlockingTime = 5.123
    artContext.gcPreOomeCount = 1L
    artContext.gcWaitingTime = 8.054
    artContext.freeMemory = 3181568L
    artContext.freeMemoryUntilGc = 3181568L
    artContext.freeMemoryUntilOome = 196083712L
    artContext.totalMemory = 7774208L
    artContext.maxMemory = 201326592L
    val unknown = mapOf(Pair("unknown", "unknown"))
    artContext.setUnknown(unknown)

    val clone = ArtContext(artContext)

    assertEquals(10L, clone.gcTotalCount)
    assertEquals(11.807, clone.gcTotalTime)
    assertEquals(2L, clone.gcBlockingCount)
    assertEquals(5.123, clone.gcBlockingTime)
    assertEquals(1L, clone.gcPreOomeCount)
    assertEquals(8.054, clone.gcWaitingTime)
    assertEquals(3181568L, clone.freeMemory)
    assertEquals(3181568L, clone.freeMemoryUntilGc)
    assertEquals(196083712L, clone.freeMemoryUntilOome)
    assertEquals(7774208L, clone.totalMemory)
    assertEquals(201326592L, clone.maxMemory)
    assertNotNull(clone.unknown) { assertEquals("unknown", it["unknown"]) }
  }
}
