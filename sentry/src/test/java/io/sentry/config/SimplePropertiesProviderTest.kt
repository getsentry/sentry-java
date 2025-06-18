package io.sentry.config

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SimplePropertiesProviderTest {
    @Test
    fun `when property is set resolves property`() {
        val properties = Properties()
        properties["some-property"] = "\"some-value\""
        val provider = SimplePropertiesProvider(properties)

        val result = provider.getProperty("some-property")
        assertEquals("some-value", result)
    }

    @Test
    fun `when property is not set returns null`() {
        val provider = SimplePropertiesProvider(Properties())

        val result = provider.getProperty("some-property")
        assertNull(result)
    }

    @Test
    fun `resolves map properties`() {
        val properties = Properties()
        properties["some-property.key1"] = "\"value1\""
        properties["some-property.key2"] = "\"value2\""
        val provider = SimplePropertiesProvider(properties)

        val result = provider.getMap("some-property")
        assertEquals(mapOf("key1" to "value1", "key2" to "value2"), result)
    }
}
