package io.sentry.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MessageTest {
    @Test
    fun `when setParams receives immutable list as an argument, its still possible to add more params`() {
        val message =
            Message().apply {
                params = listOf("a", "b")
                params!!.add("c")
            }
        assertNotNull(message.params) {
            assertEquals(listOf("a", "b", "c"), it)
        }
    }
}
