package io.sentry.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DebugMetaTest {

    @Test
    fun `when setDebugImages receives immutable list as an argument, its still possible to add more debugImages`() {
        val meta = DebugMeta().apply {
            images = listOf(DebugImage(), DebugImage())
            images!! += DebugImage()
        }
        assertNotNull(meta.images) {
            assertEquals(3, it.size)
        }
    }
}
