package io.sentry.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CompositePropertiesProviderTest {
  private val first = mock<PropertiesProvider>()
  private val second = mock<PropertiesProvider>()
  private val provider = CompositePropertiesProvider(listOf(first, second))

  @Test
  fun `resolves properties in order`() {
    whenever(first.getProperty("property")).thenReturn("firstResult")
    whenever(second.getProperty("property")).thenReturn("secondResult")
    assertEquals("firstResult", provider.getProperty("property"))
  }

  @Test
  fun `resolves property from the second provider if first provider returns null`() {
    whenever(first.getProperty("property")).thenReturn(null)
    whenever(second.getProperty("property")).thenReturn("secondResult")
    assertEquals("secondResult", provider.getProperty("property"))
  }

  @Test
  fun `returns null if no providers resolve value`() {
    whenever(first.getProperty("property")).thenReturn(null)
    whenever(second.getProperty("property")).thenReturn(null)
    assertNull(provider.getProperty("property"))
  }

  @Test
  fun `combines map results from multiple providers into single map`() {
    whenever(first.getMap("tags")).thenReturn(mapOf("first_tag" to "val1"))
    whenever(second.getMap("tags")).thenReturn(mapOf("second_tag" to "val2"))
    assertEquals(mapOf("first_tag" to "val1", "second_tag" to "val2"), provider.getMap("tags"))
  }

  @Test
  fun `when multiple providers return same map entries, the last one takes the precedence`() {
    whenever(first.getMap("tags"))
      .thenReturn(mapOf("first_tag" to "val1", "conflicting_tag" to "val3"))
    whenever(second.getMap("tags"))
      .thenReturn(mapOf("second_tag" to "val2", "conflicting_tag" to "val4"))
    assertEquals(
      mapOf("first_tag" to "val1", "second_tag" to "val2", "conflicting_tag" to "val4"),
      provider.getMap("tags"),
    )
  }
}
