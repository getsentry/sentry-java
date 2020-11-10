package io.sentry.config

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.whenever
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
