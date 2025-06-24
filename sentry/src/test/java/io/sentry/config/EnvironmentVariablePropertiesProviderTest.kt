package io.sentry.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnvironmentVariablePropertiesProviderTest {
  private val provider = EnvironmentVariablePropertiesProvider()

  @Test
  fun `when system property is set resolves property replacing dots with underscores`() {
    // SENTRY_TEST_PROPERTY is set in Gradle build configuration
    val result = provider.getProperty("test.property")
    assertEquals("some-value", result)
  }

  @Test
  fun `when system property is set resolves property replacing dashes with underscores`() {
    // SENTRY_TEST_PROPERTY is set in Gradle build configuration
    val result = provider.getProperty("test-property")
    assertEquals("some-value", result)
  }

  @Test
  fun `when system property is not set returns null`() {
    val result = provider.getProperty("not.set.property")
    assertNull(result)
  }

  @Test
  fun `resolves map properties`() {
    // SENTRY_TEST_MAP_KEY1 and SENTRY_TEST_MAP_KEY2 are set in Gradle build configuration
    val result = provider.getMap("test.map")
    assertEquals(mapOf("key1" to "value1", "key2" to "value2"), result)
  }
}
