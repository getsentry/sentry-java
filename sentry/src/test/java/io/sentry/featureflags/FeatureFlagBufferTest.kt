package io.sentry.featureflags

import io.sentry.SentryOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeatureFlagBufferTest {
  @Test
  fun `creates noop if limit is 0`() {
    val buffer = FeatureFlagBuffer.create(SentryOptions().also { it.maxFeatureFlags = 0 })
    assertTrue(buffer is NoOpFeatureFlagBuffer)
  }

  @Test
  fun `stores value`() {
    val buffer = FeatureFlagBuffer.create(SentryOptions().also { it.maxFeatureFlags = 2 })
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
  fun `drops oldest entry when limit is reached`() {
    val buffer = FeatureFlagBuffer.create(SentryOptions().also { it.maxFeatureFlags = 2 })
    buffer.add("a", true)
    buffer.add("b", true)
    buffer.add("c", true)

    val featureFlags = buffer.featureFlags
    assertNotNull(featureFlags)
    val featureFlagValues = featureFlags.values
    assertEquals(2, featureFlagValues.size)

    assertEquals("b", featureFlagValues[0]!!.flag)
    assertEquals("c", featureFlagValues[1]!!.flag)
  }

  @Test
  fun `drops oldest entries when merging multiple buffers`() {
    val options = SentryOptions().also { it.maxFeatureFlags = 2 }
    val globalBuffer = FeatureFlagBuffer.create(options)
    val isolationBuffer = FeatureFlagBuffer.create(options)
    val currentBuffer = FeatureFlagBuffer.create(options)
    globalBuffer.add("globalA", true)
    isolationBuffer.add("isolationA", true)
    currentBuffer.add("currentA", true)
    globalBuffer.add("globalB", true)
    isolationBuffer.add("isolationB", true)
    currentBuffer.add("currentB", true)
    globalBuffer.add("globalC", true)
    isolationBuffer.add("isolationC", true)
    currentBuffer.add("currentC", true)

    val buffer = FeatureFlagBuffer.merged(options, globalBuffer, isolationBuffer, currentBuffer)

    val featureFlags = buffer.featureFlags
    assertNotNull(featureFlags)
    val featureFlagValues = featureFlags.values
    assertEquals(2, featureFlagValues.size)

    assertEquals("isolationC", featureFlagValues[0]!!.flag)
    assertEquals("currentC", featureFlagValues[1]!!.flag)
  }

  @Test
  fun `drops oldest entries when merging multiple buffers even if assymetrically sized`() {
    val options = SentryOptions().also { it.maxFeatureFlags = 2 }
    val globalBuffer = FeatureFlagBuffer.create(options)
    val isolationBuffer = FeatureFlagBuffer.create(options)
    val currentBuffer = FeatureFlagBuffer.create(options)
    globalBuffer.add("globalA", true)
    isolationBuffer.add("isolationA", true)
    currentBuffer.add("currentA", true)
    globalBuffer.add("globalB", true)
    isolationBuffer.add("isolationB", true)
    currentBuffer.add("currentB", true)
    globalBuffer.add("globalC", true)

    val buffer = FeatureFlagBuffer.merged(options, globalBuffer, isolationBuffer, currentBuffer)

    val featureFlags = buffer.featureFlags
    assertNotNull(featureFlags)
    val featureFlagValues = featureFlags.values
    assertEquals(2, featureFlagValues.size)

    assertEquals("currentB", featureFlagValues[0]!!.flag)
    assertEquals("globalC", featureFlagValues[1]!!.flag)
  }

  @Test
  fun `drops oldest entries when merging multiple buffers all from same source`() {
    val options = SentryOptions().also { it.maxFeatureFlags = 2 }
    val globalBuffer = FeatureFlagBuffer.create(options)
    val isolationBuffer = FeatureFlagBuffer.create(options)
    val currentBuffer = FeatureFlagBuffer.create(options)
    globalBuffer.add("globalA", true)
    globalBuffer.add("globalB", true)
    globalBuffer.add("globalC", true)

    isolationBuffer.add("isolationA", true)
    isolationBuffer.add("isolationB", true)
    isolationBuffer.add("isolationC", true)

    currentBuffer.add("currentA", true)
    currentBuffer.add("currentB", true)
    currentBuffer.add("currentC", true)

    val buffer = FeatureFlagBuffer.merged(options, globalBuffer, isolationBuffer, currentBuffer)

    val featureFlags = buffer.featureFlags
    assertNotNull(featureFlags)
    val featureFlagValues = featureFlags.values
    assertEquals(2, featureFlagValues.size)

    assertEquals("currentB", featureFlagValues[0]!!.flag)
    assertEquals("currentC", featureFlagValues[1]!!.flag)
  }

  @Test
  fun `updates same flags value`() {
    val options = SentryOptions().also { it.maxFeatureFlags = 3 }
    val globalBuffer = FeatureFlagBuffer.create(options)
    val isolationBuffer = FeatureFlagBuffer.create(options)
    val currentBuffer = FeatureFlagBuffer.create(options)
    globalBuffer.add("a", true)
    globalBuffer.add("b", false)

    isolationBuffer.add("a", true)
    isolationBuffer.add("b", false)

    currentBuffer.add("a", false)
    currentBuffer.add("b", true)

    val buffer = FeatureFlagBuffer.merged(options, globalBuffer, isolationBuffer, currentBuffer)

    val featureFlags = buffer.featureFlags
    assertNotNull(featureFlags)
    val featureFlagValues = featureFlags.values
    assertEquals(2, featureFlagValues.size)

    assertEquals("a", featureFlagValues[0]!!.flag)
    assertFalse(featureFlagValues[0]!!.result)
    assertEquals("b", featureFlagValues[1]!!.flag)
    assertTrue(featureFlagValues[1]!!.result)
  }

  @Test
  fun `merges empty buffers`() {
    val options = SentryOptions().also { it.maxFeatureFlags = 2 }
    val globalBuffer = FeatureFlagBuffer.create(options)
    val isolationBuffer = FeatureFlagBuffer.create(options)
    val currentBuffer = FeatureFlagBuffer.create(options)
    val buffer = FeatureFlagBuffer.merged(options, globalBuffer, isolationBuffer, currentBuffer)

    assertTrue(buffer is NoOpFeatureFlagBuffer)
  }

  @Test
  fun `merges noop buffers`() {
    val options = SentryOptions().also { it.maxFeatureFlags = 0 }
    val globalBuffer = NoOpFeatureFlagBuffer.getInstance()
    val isolationBuffer = NoOpFeatureFlagBuffer.getInstance()
    val currentBuffer = NoOpFeatureFlagBuffer.getInstance()
    val buffer = FeatureFlagBuffer.merged(options, globalBuffer, isolationBuffer, currentBuffer)

    assertTrue(buffer is NoOpFeatureFlagBuffer)
  }
}
