package io.sentry

import io.sentry.protocol.SentryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class W3CTraceparentHeaderTest {

  @Test
  fun `creates header with sampled true`() {
    val traceId = SentryId("12345678123456781234567812345678")
    val spanId = SpanId("1234567812345678")
    val header = W3CTraceparentHeader(traceId, spanId, true)

    assertEquals("traceparent", header.name)
    assertEquals("00-12345678123456781234567812345678-1234567812345678-01", header.value)
    assertEquals(traceId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertTrue(header.isSampled() ?: false)
  }

  @Test
  fun `creates header with sampled false`() {
    val traceId = SentryId("12345678123456781234567812345678")
    val spanId = SpanId("1234567812345678")
    val header = W3CTraceparentHeader(traceId, spanId, false)

    assertEquals("traceparent", header.name)
    assertEquals("00-12345678123456781234567812345678-1234567812345678-00", header.value)
    assertEquals(traceId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertEquals(false, header.isSampled())
  }

  @Test
  fun `creates header with sampled null`() {
    val traceId = SentryId("12345678123456781234567812345678")
    val spanId = SpanId("1234567812345678")
    val header = W3CTraceparentHeader(traceId, spanId, null)

    assertEquals("traceparent", header.name)
    assertEquals("00-12345678123456781234567812345678-1234567812345678-00", header.value)
    assertEquals(traceId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertNull(header.isSampled())
  }

  @Test
  fun `value follows W3C format`() {
    val traceId = SentryId("abcdefabcdefabcdabcdefabcdefabcd")
    val spanId = SpanId("abcdefabcdefabcd")
    val header = W3CTraceparentHeader(traceId, spanId, true)

    val value = header.value
    val parts = value.split("-")

    assertEquals(4, parts.size)
    assertEquals("00", parts[0]) // Version
    assertEquals("abcdefabcdefabcdabcdefabcdefabcd", parts[1]) // Trace ID (32 hex chars)
    assertEquals("abcdefabcdefabcd", parts[2]) // Span ID (16 hex chars)
    assertEquals("01", parts[3]) // Sampled flag
  }
}
