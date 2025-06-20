package io.sentry

import io.sentry.exception.InvalidSentryTraceHeaderException
import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SentryTraceHeaderTest {
  @Test
  fun `when sentry-trace header is incorrect throws exception`() {
    val sentryId = SentryId()
    val ex = assertFailsWith<InvalidSentryTraceHeaderException> { SentryTraceHeader("$sentryId") }
    assertEquals("sentry-trace header does not conform to expected format: $sentryId", ex.message)
  }

  @Test
  fun `handles header with positive sampling decision`() {
    val sentryId = SentryId()
    val spanId = SpanId()
    val header = SentryTraceHeader("$sentryId-$spanId-1")
    assertEquals(sentryId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertEquals(true, header.isSampled)
  }

  @Test
  fun `handles header with negative sampling decision`() {
    val sentryId = SentryId()
    val spanId = SpanId()
    val header = SentryTraceHeader("$sentryId-$spanId-0")
    assertEquals(sentryId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertEquals(false, header.isSampled)
  }

  @Test
  fun `handles header without sampling decision`() {
    val sentryId = SentryId()
    val spanId = SpanId()
    val header = SentryTraceHeader("$sentryId-$spanId")
    assertEquals(sentryId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertNull(header.isSampled)
  }

  @Test
  fun `when sampling decision is not made, getValue returns header with traceId and spanId`() {
    val sentryId = SentryId()
    val spanId = SpanId()
    val header = SentryTraceHeader("$sentryId-$spanId")
    assertEquals("$sentryId-$spanId", header.value)
  }

  @Test
  fun `when sampling decision positive, getValue returns header with traceId and spanId and sampling`() {
    val sentryId = SentryId()
    val spanId = SpanId()
    val header = SentryTraceHeader("$sentryId-$spanId-1")
    assertEquals("$sentryId-$spanId-1", header.value)
  }

  @Test
  fun `when sampling decision negative, getValue returns header with traceId and spanId and sampling`() {
    val sentryId = SentryId()
    val spanId = SpanId()
    val header = SentryTraceHeader("$sentryId-$spanId-0")
    assertEquals("$sentryId-$spanId-0", header.value)
  }

  @Test
  fun `throws InvalidSentryTraceHeaderException when traceId has invalid value`() {
    assertFailsWith<InvalidSentryTraceHeaderException> { SentryTraceHeader("xxx-${SpanId()}-0") }
  }
}
