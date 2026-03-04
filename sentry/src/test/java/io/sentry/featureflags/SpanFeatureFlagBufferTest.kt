package io.sentry.featureflags

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpanFeatureFlagBufferTest {
  @Test
  fun `stores value`() {
    val buffer = SpanFeatureFlagBuffer.create()
    buffer.add("a", true)
    buffer.add("b", false)

    val featureFlags = buffer.featureFlags
    assertNotNull(featureFlags)

    val featureFlagValues = featureFlags.values
    assertEquals(2, featureFlagValues.size)

    assertEquals("a", featureFlagValues[0]!!.flag)
    assertTrue(featureFlagValues[0]!!.result)

    assertEquals("b", featureFlagValues[1]!!.flag)
    assertFalse(featureFlagValues[1]!!.result)
  }

  @Test
  fun `rejects new entries when limit is reached`() {
    val buffer = SpanFeatureFlagBuffer.create()
    // Fill up to maxSize (10)
    for (i in 1..10) {
      buffer.add("flag$i", true)
    }
    buffer.add("rejected", true) // This should be rejected

    val featureFlags = buffer.featureFlags
    assertNotNull(featureFlags)
    val featureFlagValues = featureFlags.values
    assertEquals(10, featureFlagValues.size)

    // Verify "rejected" was not added
    assertFalse(featureFlagValues.any { it.flag == "rejected" })
  }

  @Test
  fun `allows updates to existing entries even when full`() {
    val buffer = SpanFeatureFlagBuffer.create()
    // Fill up to maxSize (10)
    for (i in 1..10) {
      buffer.add("flag$i", true)
    }

    // Buffer is full, but updating existing entry should work
    buffer.add("flag1", false)

    val featureFlags = buffer.featureFlags
    assertNotNull(featureFlags)
    val featureFlagValues = featureFlags.values
    assertEquals(10, featureFlagValues.size)

    assertEquals("flag1", featureFlagValues[0]!!.flag)
    assertFalse(featureFlagValues[0]!!.result) // Updated from true to false
  }

  @Test
  fun `clone returns new instance`() {
    val buffer = SpanFeatureFlagBuffer.create()
    val cloned = buffer.clone()
    assertNotNull(cloned)
    assertTrue(cloned is SpanFeatureFlagBuffer)
  }

  @Test
  fun `ignores null flag or result`() {
    val buffer = SpanFeatureFlagBuffer.create()
    buffer.add(null, true)
    buffer.add("a", null)

    val featureFlags = buffer.featureFlags
    assertEquals(null, featureFlags)
  }

  @Test
  fun `maintains insertion order`() {
    val buffer = SpanFeatureFlagBuffer.create()
    buffer.add("uno", true)
    buffer.add("due", false)
    buffer.add("tre", true)

    val featureFlags = buffer.featureFlags
    assertNotNull(featureFlags)
    val featureFlagValues = featureFlags.values
    assertEquals(3, featureFlagValues.size)

    assertEquals("uno", featureFlagValues[0]!!.flag)
    assertEquals("due", featureFlagValues[1]!!.flag)
    assertEquals("tre", featureFlagValues[2]!!.flag)
  }

  @Test
  fun `updating existing flag maintains its position`() {
    val buffer = SpanFeatureFlagBuffer.create()
    buffer.add("uno", true)
    buffer.add("due", false)
    buffer.add("tre", true)

    // Update the uno flag
    buffer.add("uno", false)

    val featureFlags = buffer.featureFlags
    assertNotNull(featureFlags)
    val featureFlagValues = featureFlags.values
    assertEquals(3, featureFlagValues.size)

    // Order should remain the same
    assertEquals("uno", featureFlagValues[0]!!.flag)
    assertFalse(featureFlagValues[0]!!.result) // Value updated
    assertEquals("due", featureFlagValues[1]!!.flag)
    assertEquals("tre", featureFlagValues[2]!!.flag)
  }
}
