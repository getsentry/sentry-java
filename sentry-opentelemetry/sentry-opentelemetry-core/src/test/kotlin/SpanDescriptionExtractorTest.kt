package io.sentry.opentelemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.internal.AttributesMap
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes
import io.opentelemetry.semconv.incubating.GraphqlIncubatingAttributes
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes
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
    val attributes = AttributesMap.create(100, 100)
    var parentSpanContext = SpanContext.getInvalid()
    var spanKind = SpanKind.INTERNAL
    var spanName: String? = null
    var spanDescription: String? = null

    fun setup() {
      whenever(otelSpan.attributes).thenReturn(attributes)
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

  @Test
  fun `sets op to graphql for span with graphql operation type`() {
    givenAttributes(
      mapOf(
        GraphqlIncubatingAttributes.GRAPHQL_OPERATION_TYPE to "query",
        GraphqlIncubatingAttributes.GRAPHQL_OPERATION_NAME to "GreetingQuery",
      )
    )

    val info = whenExtractingSpanInfo()

    assertEquals("query GreetingQuery", info.op)
    assertEquals("query GreetingQuery", info.description)
    assertEquals(TransactionNameSource.TASK, info.transactionNameSource)
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
    map.forEach { k, v -> fixture.attributes.put(k, v) }
  }

  private fun whenExtractingSpanInfo(): OtelSpanInfo {
    fixture.setup()
    return SpanDescriptionExtractor().extractSpanInfo(fixture.otelSpan, fixture.sentrySpan)
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
