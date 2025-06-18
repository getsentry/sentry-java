package io.sentry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpAddressUtilsTest {
    @Test
    fun `{{auto}} is considered a default address`() {
        assertTrue(IpAddressUtils.isDefault("{{auto}}"))
    }

    @Test
    fun `{{ auto }} is considered a default address`() {
        assertTrue(IpAddressUtils.isDefault("{{ auto }}"))
    }

    @Test
    fun `real ip address is considered not a default address`() {
        assertFalse(IpAddressUtils.isDefault("192.168.0.1"))
    }
}
