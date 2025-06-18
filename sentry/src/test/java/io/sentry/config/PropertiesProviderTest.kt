package io.sentry.config

import org.mockito.kotlin.any
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PropertiesProviderTest {
    private val propertiesProvider = spy<PropertiesProvider>()

    @Test
    fun `when property is set returns the property value`() {
        whenever(propertiesProvider.getProperty(any())).thenReturn("value")
        val result = propertiesProvider.getProperty("prop", "defaultValue")
        assertEquals("value", result)
    }

    @Test
    fun `when property not set returns the default value`() {
        whenever(propertiesProvider.getProperty(any())).thenReturn(null)
        val result = propertiesProvider.getProperty("prop", "defaultValue")
        assertEquals("defaultValue", result)
    }

    @Test
    fun `when property is value is true returns true`() {
        whenever(propertiesProvider.getProperty(any())).thenReturn("true")
        val result = propertiesProvider.getBooleanProperty("prop")
        assertNotNull(result)
        assertTrue(result)
    }

    @Test
    fun `when property is value is false returns false`() {
        whenever(propertiesProvider.getProperty(any())).thenReturn("false")
        val result = propertiesProvider.getBooleanProperty("prop")
        assertNotNull(result)
        assertFalse(result)
    }

    @Test
    fun `when property is value does not match boolean returns false`() {
        whenever(propertiesProvider.getProperty(any())).thenReturn("something")
        val result = propertiesProvider.getBooleanProperty("prop")
        assertNotNull(result)
        assertFalse(result)
    }
}
