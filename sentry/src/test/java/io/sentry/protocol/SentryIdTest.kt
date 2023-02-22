package io.sentry.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class SentryIdTest {

    @Test
    fun `does not throw when instantiated with corrupted UUID`() {
        val id = SentryId("0000-0000")
        assertEquals("00000000000000000000000000000000", id.toString())
    }
}
