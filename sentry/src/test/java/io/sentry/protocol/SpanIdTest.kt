package io.sentry.protocol

import io.sentry.SentryUUID
import io.sentry.SpanId
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito
import org.mockito.kotlin.never
import org.mockito.kotlin.times

class SpanIdTest {
  @Test
  fun `ID is not generated on initialization`() {
    val uuid = SentryUUID.generateSpanId()
    Mockito.mockStatic(SentryUUID::class.java).use { utils ->
      utils.`when`<String> { SentryUUID.generateSpanId() }.thenReturn(uuid)
      val ignored = SpanId()
      utils.verify({ SentryUUID.generateSpanId() }, never())
    }
  }

  @Test
  fun `ID is generated only once`() {
    val uuid = SentryUUID.generateSpanId()
    Mockito.mockStatic(SentryUUID::class.java).use { utils ->
      utils.`when`<String> { SentryUUID.generateSpanId() }.thenReturn(uuid)
      val spanId = SpanId()
      val uuid1 = spanId.toString()
      val uuid2 = spanId.toString()

      assertEquals(uuid1, uuid2)
      utils.verify({ SentryUUID.generateSpanId() }, times(1))
    }
  }
}
