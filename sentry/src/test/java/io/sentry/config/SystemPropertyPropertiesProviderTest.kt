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
        System.setProperty("sentry.dsn", "some-dsn")
        val result = provider.getProperty("dsn")
        assertEquals("some-dsn", result)
    }

    @Test
    fun `when system property is not set returns null`() {
        val result = provider.getProperty("dsn")
        assertNull(result)
    }
}
