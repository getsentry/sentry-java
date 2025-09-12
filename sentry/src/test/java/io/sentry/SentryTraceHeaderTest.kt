package io.sentry

import io.sentry.exception.InvalidSentryTraceHeaderException
import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.text.substring

class SentryTraceHeaderTest {
  @Test
  fun `when sentry-trace header is incorrect throws exception`() {
    val sentryId = SentryId()
    val ex = assertFailsWith<InvalidSentryTraceHeaderException> { SentryTraceHeader("$sentryId") }
    assertEquals("sentry-trace header does not conform to expected format: $sentryId", ex.message)
  }

  @Test
  fun `when there is a trailing dash without sampling decision throws exception`() {
    val sentryId = SentryId()
    val spanId = SpanId()
    val ex =
      assertFailsWith<InvalidSentryTraceHeaderException> { SentryTraceHeader("$sentryId-$spanId-") }
    assertEquals(
      "sentry-trace header does not conform to expected format: $sentryId-$spanId-",
      ex.message,
    )
  }

  @Test
  fun `when trace-id has less than 32 characters throws exception`() {
    val sentryId = SentryId().toString().substring(0, 8)
    val spanId = SpanId()
    val ex =
      assertFailsWith<InvalidSentryTraceHeaderException> { SentryTraceHeader("$sentryId-$spanId") }
    assertEquals(
      "sentry-trace header does not conform to expected format: $sentryId-$spanId",
      ex.message,
    )
  }

  @Test
  fun `when trace-id has more than 32 characters throws exception`() {
    val sentryId = SentryId().toString() + "abc"
    val spanId = SpanId()
    val ex =
      assertFailsWith<InvalidSentryTraceHeaderException> { SentryTraceHeader("$sentryId-$spanId") }
    assertEquals(
      "sentry-trace header does not conform to expected format: $sentryId-$spanId",
      ex.message,
    )
  }

  @Test
  fun `when trace-id contains invalid characters throws exception`() {
    var sentryId = SentryId().toString()
    sentryId = sentryId.substring(0, 8) + "g" + sentryId.substring(8)
    val spanId = SpanId()
    val ex =
      assertFailsWith<InvalidSentryTraceHeaderException> { SentryTraceHeader("$sentryId-$spanId") }
    assertEquals(
      "sentry-trace header does not conform to expected format: $sentryId-$spanId",
      ex.message,
    )
  }

  @Test
  fun `when span-id has less than 16 characters throws exception`() {
    val sentryId = SentryId()
    val spanId = SpanId().toString().substring(0, 8)
    val ex =
      assertFailsWith<InvalidSentryTraceHeaderException> { SentryTraceHeader("$sentryId-$spanId") }
    assertEquals(
      "sentry-trace header does not conform to expected format: $sentryId-$spanId",
      ex.message,
    )
  }

  @Test
  fun `when span-id has more than 32 characters throws exception`() {
    val sentryId = SentryId()
    val spanId = SpanId().toString() + "abc"
    val ex =
      assertFailsWith<InvalidSentryTraceHeaderException> { SentryTraceHeader("$sentryId-$spanId") }
    assertEquals(
      "sentry-trace header does not conform to expected format: $sentryId-$spanId",
      ex.message,
    )
  }

  @Test
  fun `when span-id contains invalid characters throws exception`() {
    val sentryId = SentryId()
    var spanId = SpanId().toString()
    spanId = spanId.substring(0, 8) + "g" + spanId.substring(8)
    val ex =
      assertFailsWith<InvalidSentryTraceHeaderException> { SentryTraceHeader("$sentryId-$spanId") }
    assertEquals(
      "sentry-trace header does not conform to expected format: $sentryId-$spanId",
      ex.message,
    )
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
  fun `handles header without sampling decision and leading whitespace`() {
    val sentryId = SentryId()
    val spanId = SpanId()
    val header = SentryTraceHeader(" \t $sentryId-$spanId")
    assertEquals(sentryId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertNull(header.isSampled)
  }

  @Test
  fun `handles header without sampling decision and trailing whitespace`() {
    val sentryId = SentryId()
    val spanId = SpanId()
    val header = SentryTraceHeader("$sentryId-$spanId \t ")
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
