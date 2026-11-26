package io.sentry.opentelemetry

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes
import io.sentry.ISentryClient
import io.sentry.NoOpScopes
import io.sentry.Sentry
import io.sentry.SentryOptions
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

class SentrySpanExporterMessagingTest {
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
        client.captureTransaction(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()),
      )
      .thenReturn(SentryId())
    Sentry.setCurrentScopes(scopes)
  }

  @AfterTest
  fun tearDown() {
    Sentry.setCurrentScopes(NoOpScopes.getInstance())
  }

  @Test
  fun `normalizes replacement messaging system on roots and children`() {
    withOpenTelemetry { openTelemetry ->
      val tracer = openTelemetry.getTracer("test")
      val root =
        tracer
          .spanBuilder("root")
          .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM_NAME, "kafka")
          .startSpan()
      val child =
        tracer
          .spanBuilder("child")
          .setParent(root.storeInContext(Context.root()))
          .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM_NAME, "rabbitmq")
          .startSpan()

      child.end()
      root.end()
    }

    val transaction = capturedTransactions().single()
    assertThat(transaction.contexts.trace!!.data["messaging.system"]).isEqualTo("kafka")
    assertThat(transaction.spans).hasSize(1)
    assertThat(transaction.spans.single().data!!["messaging.system"]).isEqualTo("rabbitmq")
  }

  @Suppress("DEPRECATION")
  @Test
  fun `prefers legacy messaging system on roots and children`() {
    withOpenTelemetry { openTelemetry ->
      val tracer = openTelemetry.getTracer("test")
      val root =
        tracer
          .spanBuilder("root")
          .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "kafka")
          .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM_NAME, "ignored-root")
          .startSpan()
      val child =
        tracer
          .spanBuilder("child")
          .setParent(root.storeInContext(Context.root()))
          .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, "rabbitmq")
          .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM_NAME, "ignored-child")
          .startSpan()

      child.end()
      root.end()
    }

    val transaction = capturedTransactions().single()
    assertThat(transaction.contexts.trace!!.data["messaging.system"]).isEqualTo("kafka")
    assertThat(transaction.spans).hasSize(1)
    assertThat(transaction.spans.single().data!!["messaging.system"]).isEqualTo("rabbitmq")
  }

  private fun withOpenTelemetry(callback: (OpenTelemetrySdk) -> Unit) {
    val provider =
      SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
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
