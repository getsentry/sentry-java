package io.sentry.opentelemetry

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.semconv.HttpAttributes
import io.sentry.ISentryClient
import io.sentry.NoOpScopes
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SpanDataConvention
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.test.createSentryClientMock
import io.sentry.test.createTestScopes
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentrySpanExporterTest {
  private val options =
    SentryOptions().apply {
      dsn = "https://key@sentry.io/proj"
      tracesSampleRate = 1.0
    }
  private val client: ISentryClient = createSentryClientMock()
  private val scopes = createTestScopes(options).also { it.bindClient(client) }
  private val exporter = SentrySpanExporter(scopes)

  init {
    whenever(
        client.captureTransaction(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
      )
      .thenReturn(SentryId())
    Sentry.setCurrentScopes(scopes)
  }

  @AfterTest
  fun tearDown() {
    Sentry.setCurrentScopes(NoOpScopes.getInstance())
  }

  @Test
  fun `server roots and children get server kind and root response status`() {
    withOpenTelemetry { openTelemetry ->
      val tracer = openTelemetry.getTracer("test")
      val root =
        tracer
          .spanBuilder("server-root")
          .setSpanKind(SpanKind.SERVER)
          .setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 404L)
          .startSpan()
      val child =
        tracer
          .spanBuilder("server-child")
          .setParent(root.storeInContext(Context.root()))
          .setSpanKind(SpanKind.SERVER)
          .startSpan()

      child.end()
      root.end()
    }

    val transaction = capturedTransactions().single()
    assertThat(transaction.contexts.trace!!.data[SpanDataConvention.SENTRY_KIND_KEY])
      .isEqualTo("server")
    assertThat(transaction.contexts.trace!!.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
      .isEqualTo(404L)
    assertThat(transaction.contexts.trace!!.data["otel.kind"]).isEqualTo("SERVER")
    assertThat(transaction.spans).hasSize(1)
    assertThat(transaction.spans.single().data!![SpanDataConvention.SENTRY_KIND_KEY])
      .isEqualTo("server")
    assertThat(transaction.spans.single().data!!["otel.kind"]).isEqualTo("SERVER")
  }

  @Test
  fun `client and internal roots do not get server data`() {
    withOpenTelemetry { openTelemetry ->
      val tracer = openTelemetry.getTracer("test")
      tracer
        .spanBuilder("client-root")
        .setSpanKind(SpanKind.CLIENT)
        .setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 404L)
        .startSpan()
        .end()
      tracer
        .spanBuilder("internal-root")
        .setSpanKind(SpanKind.INTERNAL)
        .setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 500L)
        .startSpan()
        .end()
    }

    val transactions = capturedTransactions().associateBy { it.transaction }
    assertThat(transactions.keys).containsExactly("client-root", "internal-root")
    assertThat(transactions["client-root"]!!.contexts.trace!!.data)
      .doesNotContainKey(SpanDataConvention.SENTRY_KIND_KEY)
    assertThat(transactions["client-root"]!!.contexts.trace!!.data)
      .doesNotContainKey(SpanDataConvention.HTTP_STATUS_CODE_KEY)
    assertThat(transactions["internal-root"]!!.contexts.trace!!.data)
      .doesNotContainKey(SpanDataConvention.SENTRY_KIND_KEY)
    assertThat(transactions["internal-root"]!!.contexts.trace!!.data)
      .doesNotContainKey(SpanDataConvention.HTTP_STATUS_CODE_KEY)
  }

  @Test
  fun `rpc server root does not fabricate HTTP response status`() {
    withOpenTelemetry { openTelemetry ->
      openTelemetry
        .getTracer("test")
        .spanBuilder("rpc-server")
        .setSpanKind(SpanKind.SERVER)
        .startSpan()
        .end()
    }

    val transaction = capturedTransactions().single()
    assertThat(transaction.contexts.trace!!.data[SpanDataConvention.SENTRY_KIND_KEY])
      .isEqualTo("server")
    assertThat(transaction.contexts.trace!!.data)
      .doesNotContainKey(SpanDataConvention.HTTP_STATUS_CODE_KEY)
  }

  @Test
  fun `Sentry server marker survives default OTel internal kind`() {
    withOpenTelemetry(includeSentrySpanProcessor = true) { openTelemetry ->
      options.spanFactory = OtelSpanFactory(openTelemetry)
      val transaction =
        scopes.startTransaction(
          TransactionContext("native-server", "http.server"),
          TransactionOptions(),
        )
      transaction.setData(SpanDataConvention.SENTRY_KIND_KEY, "server")
      transaction.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, 404)
      transaction.finish()
    }

    val transaction = capturedTransactions().single()
    assertThat(transaction.contexts.trace!!.data[SpanDataConvention.SENTRY_KIND_KEY])
      .isEqualTo("server")
    assertThat(transaction.contexts.trace!!.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
      .isEqualTo(404)
    assertThat(transaction.contexts.trace!!.data["otel.kind"]).isEqualTo("INTERNAL")
  }

  private fun withOpenTelemetry(
    includeSentrySpanProcessor: Boolean = false,
    callback: (OpenTelemetrySdk) -> Unit,
  ) {
    val providerBuilder = SdkTracerProvider.builder()
    if (includeSentrySpanProcessor) {
      providerBuilder.addSpanProcessor(OtelSentrySpanProcessor(scopes))
    }
    val provider = providerBuilder.addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
    val openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(provider).build()
    try {
      callback(openTelemetry)
      provider.forceFlush().join(10, TimeUnit.SECONDS)
    } finally {
      provider.close()
    }
  }

  private fun capturedTransactions(): List<SentryTransaction> {
    val transaction = argumentCaptor<SentryTransaction>()
    verify(client, atLeastOnce())
      .captureTransaction(
        transaction.capture(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
        anyOrNull(),
      )
    return transaction.allValues
  }
}
