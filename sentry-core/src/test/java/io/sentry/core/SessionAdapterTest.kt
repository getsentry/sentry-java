package io.sentry.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionAdapterTest {
    @Test
    fun `capitalize string`() {
        assertEquals("test".capitalize(), SessionAdapter().capitalize("test"))
    }
}
