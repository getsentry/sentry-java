package io.sentry.config

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class EnvironmentVariablePropertiesProviderTest {
    private val provider = EnvironmentVariablePropertiesProvider()

    @Test
    fun `when system property is set resolves property`() {
        // SENTRY_TEST_PROPERTY is set in Gradle build configuration
        val result = provider.getProperty("test.property")
        assertEquals("some-value", result)
    }

    @Test
    fun `when system property is not set returns null`() {
        val result = provider.getProperty("not.set.property")
        assertNull(result)
    }
}
