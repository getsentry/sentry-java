package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals

class W3CTraceparentHeaderTest {

  @Test
  fun `creates header with sampled true`() {
    val traceId = SentryId("12345678123456781234567812345678")
    val spanId = SpanId("1234567812345678")
    val header = W3CTraceparentHeader(traceId, spanId, true)

    assertEquals("traceparent", header.name)
    assertEquals("12345678123456781234567812345678-1234567812345678-01", header.value)
  }

  @Test
  fun `creates header with sampled false`() {
    val traceId = SentryId("12345678123456781234567812345678")
    val spanId = SpanId("1234567812345678")
    val header = W3CTraceparentHeader(traceId, spanId, false)

    assertEquals("traceparent", header.name)
    assertEquals("12345678123456781234567812345678-1234567812345678-00", header.value)
  }

  @Test
  fun `creates header with sampled null`() {
    val traceId = SentryId("12345678123456781234567812345678")
    val spanId = SpanId("1234567812345678")
    val header = W3CTraceparentHeader(traceId, spanId, null)

    assertEquals("traceparent", header.name)
    assertEquals("12345678123456781234567812345678-1234567812345678-00", header.value)
  }

  @Test
  fun `value follows W3C format`() {
    val traceId = SentryId("abcdefabcdefabcdabcdefabcdefabcd")
    val spanId = SpanId("abcdefabcdefabcd")
    val header = W3CTraceparentHeader(traceId, spanId, true)

    val value = header.value
    val parts = value.split("-")

    assertEquals(3, parts.size)
    assertEquals("abcdefabcdefabcdabcdefabcdefabcd", parts[0]) // Trace ID (32 chars)
    assertEquals("abcdefabcdefabcd", parts[1]) // Span ID (16 chars)
    assertEquals("01", parts[2]) // Sampled flag (2 chars)
  }
}
