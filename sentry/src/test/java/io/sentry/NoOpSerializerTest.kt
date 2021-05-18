package io.sentry

import com.nhaarman.mockitokotlin2.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class NoOpSerializerTest {
    private val sut: NoOpSerializer = NoOpSerializer.getInstance()

    @Test
    fun `deserializeEvent returns null on NoOp`() {
        assertEquals(null, sut.deserialize(mock(), SentryEvent::class.java))
    }
}
