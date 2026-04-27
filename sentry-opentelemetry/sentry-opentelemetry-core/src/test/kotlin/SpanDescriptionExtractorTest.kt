package io.sentry.opentelemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes
import io.sentry.SentryOptions
import io.sentry.protocol.TransactionNameSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SpanDescriptionExtractorTest {
  private class Fixture {
    val sentrySpan = mock<IOtelSpanWrapper>()
    val otelSpan = mock<SpanData>()
    var attributes: Attributes = Attributes.empty()
    var parentSpanContext = SpanContext.getInvalid()
    var spanKind = SpanKind.INTERNAL
    var spanName: String? = null
    var spanDescription: String? = null

    fun setup() {
      whenever(otelSpan.attributes).thenAnswer { attributes }
      whenever(otelSpan.parentSpanContext).thenReturn(parentSpanContext)
      whenever(otelSpan.kind).thenReturn(spanKind)
      spanName?.let { whenever(otelSpan.name).thenReturn(it) }
      spanDescription?.let { whenever(sentrySpan.description).thenReturn(it) }
    }
  }

  private val fixture = Fixture()

  @Test
  fun `sets op to http server for kind SERVER`() {
    givenSpanKind(SpanKind.SERVER)
    givenAttributes(mapOf(HttpAttributes.HTTP_REQUEST_METHOD to "GET"))

    val info = whenExtractingSpanInfo()

    assertEquals("http.server", info.op)
    assertNull(info.description)
    assertEquals(TransactionNameSource.CUSTOM, info.transactionNameSource)
  }

  @Test
  fun `sets op to http client for kind CLIENT`() {
    givenSpanKind(SpanKind.CLIENT)
    givenAttributes(mapOf(HttpAttributes.HTTP_REQUEST_METHOD to "GET"))

    val info = whenExtractingSpanInfo()

    assertEquals("http.client", info.op)
    assertNull(info.description)
    assertEquals(TransactionNameSource.CUSTOM, info.transactionNameSource)
  }

  @Test
  fun `sets op to http without server for root span with http GET`() {
    givenAttributes(mapOf(HttpAttributes.HTTP_REQUEST_METHOD to "GET"))

    val info = whenExtractingSpanInfo()

    assertEquals("http", info.op)
    assertNull(info.description)
    assertEquals(TransactionNameSource.CUSTOM, info.transactionNameSource)
  }

  @Test
  fun `sets op to http without server for non root span with remote parent with http GET`() {
    givenParentContext(createSpanContext(true))
    givenAttributes(mapOf(HttpAttributes.HTTP_REQUEST_METHOD to "GET"))

    val info = whenExtractingSpanInfo()

    assertEquals("http", info.op)
    assertNull(info.description)
    assertEquals(TransactionNameSource.CUSTOM, info.transactionNameSource)
  }

  @Test
  fun `sets op to http client for non root span with http GET`() {
    givenParentContext(createSpanContext(false))
    givenAttributes(mapOf(HttpAttributes.HTTP_REQUEST_METHOD to "GET"))

    val info = whenExtractingSpanInfo()

    assertEquals("http.client", info.op)
    assertNull(info.description)
    assertEquals(TransactionNameSource.CUSTOM, info.transactionNameSource)
  }

  @Test
  fun `uses URL_FULL for description`() {
    givenSpanKind(SpanKind.SERVER)
    givenAttributes(
      mapOf(
        HttpAttributes.HTTP_REQUEST_METHOD to "GET",
        UrlAttributes.URL_FULL to "https://sentry.io/some/path?q=1#top",
      )
    )

    val info = whenExtractingSpanInfo()

    assertEquals("http.server", info.op)
    assertEquals("GET https://sentry.io/some/path?q=1#top", info.description)
    assertEquals(TransactionNameSource.URL, info.transactionNameSource)
  }

  @Test
  fun `uses URL_PATH for description`() {
    givenSpanKind(SpanKind.SERVER)
    givenAttributes(
      mapOf(HttpAttributes.HTTP_REQUEST_METHOD to "GET", UrlAttributes.URL_PATH to "/some/path")
    )

    val info = whenExtractingSpanInfo()

    assertEquals("http.server", info.op)
    assertEquals("GET /some/path", info.description)
    assertEquals(TransactionNameSource.URL, info.transactionNameSource)
  }

  @Test
  fun `uses HTTP_TARGET for description`() {
    givenSpanKind(SpanKind.SERVER)
    givenAttributes(
      mapOf(
        HttpAttributes.HTTP_REQUEST_METHOD to "GET",
        HttpAttributes.HTTP_ROUTE to "/some/{id}",
        HttpIncubatingAttributes.HTTP_TARGET to "some/path?q=1#top",
        UrlAttributes.URL_PATH to "/some/path",
      )
    )

    val info = whenExtractingSpanInfo()

    assertEquals("http.server", info.op)
    assertEquals("GET /some/{id}", info.description)
    assertEquals(TransactionNameSource.ROUTE, info.transactionNameSource)
  }

  @Test
  fun `uses span name as description fallback`() {
    givenSpanKind(SpanKind.SERVER)
    givenSpanName("span name")
    givenAttributes(mapOf(HttpAttributes.HTTP_REQUEST_METHOD to "GET"))

    val info = whenExtractingSpanInfo()

    assertEquals("http.server", info.op)
    assertEquals("span name", info.description)
    assertEquals(TransactionNameSource.CUSTOM, info.transactionNameSource)
  }

  @Test
  fun `no description if no span name as fallback`() {
    givenSpanKind(SpanKind.SERVER)
    givenAttributes(mapOf(HttpAttributes.HTTP_REQUEST_METHOD to "GET"))

    val info = whenExtractingSpanInfo()

    assertEquals("http.server", info.op)
    assertNull(info.description)
    assertEquals(TransactionNameSource.CUSTOM, info.transactionNameSource)
  }

  @Test
  fun `sets op to db for span with db system and query text`() {
    givenAttributes(
      mapOf(
        DbIncubatingAttributes.DB_SYSTEM to "some",
        DbIncubatingAttributes.DB_QUERY_TEXT to "SELECT * FROM tbl",
      )
    )

    val info = whenExtractingSpanInfo()

    assertEquals("db", info.op)
    assertEquals("SELECT * FROM tbl", info.description)
    assertEquals(TransactionNameSource.TASK, info.transactionNameSource)
  }

  @Test
  fun `sets op to db for span with db system and statement`() {
    givenAttributes(
      mapOf(
        DbIncubatingAttributes.DB_SYSTEM to "some",
        DbIncubatingAttributes.DB_STATEMENT to "SELECT * FROM tbl",
      )
    )

    val info = whenExtractingSpanInfo()

    assertEquals("db", info.op)
    assertEquals("SELECT * FROM tbl", info.description)
    assertEquals(TransactionNameSource.TASK, info.transactionNameSource)
  }

  @Test
  fun `sets op to db for span with db system`() {
    givenAttributes(mapOf(DbIncubatingAttributes.DB_SYSTEM to "some"))

    val info = whenExtractingSpanInfo()

    assertEquals("db", info.op)
    assertNull(info.description)
    assertEquals(TransactionNameSource.TASK, info.transactionNameSource)
  }

  @Test
  fun `sets op to db for span with db system fallback to span name as description`() {
    givenSpanName("span name")
    givenAttributes(mapOf(DbIncubatingAttributes.DB_SYSTEM to "some"))

    val info = whenExtractingSpanInfo()

    assertEquals("db", info.op)
    assertEquals("span name", info.description)
    assertEquals(TransactionNameSource.TASK, info.transactionNameSource)
  }

  @Test
  fun `ignores messaging system when queue tracing disabled`() {
    givenSpanName("my-topic publish")
    givenAttributes(
      mapOf(
        MessagingIncubatingAttributes.MESSAGING_SYSTEM to "kafka",
        MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME to "my-topic",
        MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE to "publish",
      )
    )

    val info = whenExtractingSpanInfo(queueTracingEnabled = false)

    assertEquals("my-topic publish", info.op)
    assertEquals("my-topic publish", info.description)
    assertEquals(TransactionNameSource.CUSTOM, info.transactionNameSource)
  }

  @Test
  fun `maps messaging publish operation type to queue publish op`() {
    givenAttributes(
      mapOf(
        MessagingIncubatingAttributes.MESSAGING_SYSTEM to "kafka",
        MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME to "my-topic",
        MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE to "publish",
      )
    )

    val info = whenExtractingSpanInfo(queueTracingEnabled = true)

    assertEquals("queue.publish", info.op)
    assertEquals("my-topic", info.description)
    assertEquals(TransactionNameSource.TASK, info.transactionNameSource)
  }

  @Test
  fun `maps messaging process operation type to queue process op`() {
    givenAttributes(
      mapOf(
        MessagingIncubatingAttributes.MESSAGING_SYSTEM to "kafka",
        MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME to "my-topic",
        MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE to "process",
      )
    )

    val info = whenExtractingSpanInfo(queueTracingEnabled = true)

    assertEquals("queue.process", info.op)
    assertEquals("my-topic", info.description)
    assertEquals(TransactionNameSource.TASK, info.transactionNameSource)
  }

  @Test
  fun `maps messaging receive operation type to queue receive op`() {
    givenAttributes(
      mapOf(
        MessagingIncubatingAttributes.MESSAGING_SYSTEM to "kafka",
        MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME to "my-topic",
        MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE to "receive",
      )
    )

    val info = whenExtractingSpanInfo(queueTracingEnabled = true)

    assertEquals("queue.receive", info.op)
    assertEquals("my-topic", info.description)
    assertEquals(TransactionNameSource.TASK, info.transactionNameSource)
  }

  @Test
  fun `falls back to legacy messaging operation attribute`() {
    @Suppress("DEPRECATION")
    givenAttributes(
      mapOf(
        MessagingIncubatingAttributes.MESSAGING_SYSTEM to "rabbitmq",
        MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME to "queue-name",
        MessagingIncubatingAttributes.MESSAGING_OPERATION to "publish",
      )
    )

    val info = whenExtractingSpanInfo(queueTracingEnabled = true)

    assertEquals("queue.publish", info.op)
    assertEquals("queue-name", info.description)
  }

  @Test
  fun `falls back to PRODUCER span kind when no operation attribute`() {
    givenSpanKind(SpanKind.PRODUCER)
    givenAttributes(
      mapOf(
        MessagingIncubatingAttributes.MESSAGING_SYSTEM to "kafka",
        MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME to "my-topic",
      )
    )

    val info = whenExtractingSpanInfo(queueTracingEnabled = true)

    assertEquals("queue.publish", info.op)
    assertEquals("my-topic", info.description)
  }

  @Test
  fun `falls back to CONSUMER span kind when no operation attribute`() {
    givenSpanKind(SpanKind.CONSUMER)
    givenAttributes(
      mapOf(
        MessagingIncubatingAttributes.MESSAGING_SYSTEM to "kafka",
        MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME to "my-topic",
      )
    )

    val info = whenExtractingSpanInfo(queueTracingEnabled = true)

    assertEquals("queue.process", info.op)
    assertEquals("my-topic", info.description)
  }

  @Test
  fun `falls back to span name as description when destination missing`() {
    givenSpanName("my-topic publish")
    givenAttributes(
      mapOf(
        MessagingIncubatingAttributes.MESSAGING_SYSTEM to "kafka",
        MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE to "publish",
      )
    )

    val info = whenExtractingSpanInfo(queueTracingEnabled = true)

    assertEquals("queue.publish", info.op)
    assertEquals("my-topic publish", info.description)
  }

  @Test
  fun `uses span name as op and description if no relevant attributes`() {
    givenSpanName("span name")
    givenAttributes(emptyMap())

    val info = whenExtractingSpanInfo()

    assertEquals("span name", info.op)
    assertEquals("span name", info.description)
    assertEquals(TransactionNameSource.CUSTOM, info.transactionNameSource)
  }

  @Test
  fun `uses existing sentry span description as description`() {
    givenSpanName("span name")
    givenSentrySpanDescription("span description")
    givenAttributes(emptyMap())

    val info = whenExtractingSpanInfo()

    assertEquals("span name", info.op)
    assertEquals("span description", info.description)
    assertEquals(TransactionNameSource.CUSTOM, info.transactionNameSource)
  }

  private fun createSpanContext(
    isRemote: Boolean,
    traceId: String = "f9118105af4a2d42b4124532cd1065ff",
    spanId: String = "424cffc8f94feeee",
  ): SpanContext {
    if (isRemote) {
      return SpanContext.createFromRemoteParent(
        traceId,
        spanId,
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    } else {
      return SpanContext.create(traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault())
    }
  }

  private fun givenAttributes(map: Map<AttributeKey<out Any>, Any>) {
    fixture.attributes = buildAttributes(map)
  }

  private fun buildAttributes(map: Map<AttributeKey<out Any>, Any>): Attributes {
    val builder = Attributes.builder()
    map.forEach { (key, value) -> putAttribute(builder, key, value) }
    return builder.build()
  }

  @Suppress("UNCHECKED_CAST")
  private fun putAttribute(
    builder: io.opentelemetry.api.common.AttributesBuilder,
    key: AttributeKey<out Any>,
    value: Any,
  ) {
    builder.put(key as AttributeKey<Any>, value)
  }

  private fun whenExtractingSpanInfo(queueTracingEnabled: Boolean = false): OtelSpanInfo {
    fixture.setup()
    val options = SentryOptions().apply { isEnableQueueTracing = queueTracingEnabled }
    return SpanDescriptionExtractor().extractSpanInfo(fixture.otelSpan, fixture.sentrySpan, options)
  }

  private fun givenParentContext(parentContext: SpanContext) {
    fixture.parentSpanContext = parentContext
  }

  private fun givenSpanName(name: String) {
    fixture.spanName = name
  }

  private fun givenSentrySpanDescription(description: String) {
    fixture.spanDescription = description
  }

  private fun givenSpanKind(spanKind: SpanKind) {
    fixture.spanKind = spanKind
  }
}
