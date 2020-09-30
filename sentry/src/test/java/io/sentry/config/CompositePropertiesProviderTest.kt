package io.sentry.config

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
