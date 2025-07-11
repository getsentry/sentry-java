package io.sentry.config

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SystemPropertyPropertiesProviderTest {
  private val provider = SystemPropertyPropertiesProvider()

  @BeforeTest
  fun `clear system property`() {
    System.clearProperty("sentry.dsn")
  }

  @Test
  fun `when system property is set resolves property`() {
    System.setProperty("sentry.dsn", "\"some-dsn\"")
    val result = provider.getProperty("dsn")
    assertEquals("some-dsn", result)
  }

  @Test
  fun `when system property is not set returns null`() {
    val result = provider.getProperty("dsn")
    assertNull(result)
  }

  @Test
  fun `resolves map from system properties`() {
    System.setProperty("sentry.some-property.key1", "\"value1\"")
    System.setProperty("sentry.some-property.key2", "\"value2\"")

    val result = provider.getMap("some-property")
    assertEquals(mapOf("key1" to "value1", "key2" to "value2"), result)
  }
}
