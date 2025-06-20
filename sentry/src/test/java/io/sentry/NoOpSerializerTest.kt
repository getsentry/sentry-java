package io.sentry

import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.kotlin.mock

class NoOpSerializerTest {
  private val sut: NoOpSerializer = NoOpSerializer.getInstance()

  @Test
  fun `deserializeEvent returns null on NoOp`() {
    assertEquals(null, sut.deserialize(mock(), SentryEvent::class.java))
  }
}
