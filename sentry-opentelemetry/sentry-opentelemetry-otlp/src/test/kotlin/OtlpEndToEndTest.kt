package io.sentry.opentelemetry.otlp

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.sentry.Sentry
import io.sentry.test.MockSentryServer
import io.sentry.test.event
import io.sentry.test.hasMetric
import io.sentry.test.initForTest
import io.sentry.test.logs
import io.sentry.test.metrics
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * An app that exports its OpenTelemetry spans over OTLP and reports everything else to Sentry, as
 * the console OTLP sample does.
 */
class OtlpEndToEndTest {
  private val server = MockSentryServer()
  private val openTelemetry =
    OpenTelemetrySdk.builder().setTracerProvider(SdkTracerProvider.builder().build()).build()

  @AfterTest
  fun teardown() {
    Sentry.close()
    openTelemetry.close()
    server.close()
  }

  @Test
  fun `an event inside an OpenTelemetry span is linked to it`() {
    initForTest {
      it.dsn = server.dsn
      it.addEventProcessor(OpenTelemetryOtlpEventProcessor())
    }

    val span = openTelemetry.getTracer("otlp-end-to-end-test").spanBuilder("otelSpan").startSpan()
    span.makeCurrent().use {
      Sentry.captureMessage("this message is connected to the outerSpan")
      Sentry.logger().error("Some error log message")
      Sentry.metrics().count("invocations")
    }
    span.end()
    Sentry.flush(5000)

    val event =
      server
        .awaitEnvelope {
          it.event()?.message?.formatted == "this message is connected to the outerSpan"
        }
        .event()!!
    // The span's trace goes over OTLP, so the event points to it rather than to a Sentry span.
    val trace = event.contexts.trace!!
    assertThat(trace.traceId.toString()).isEqualTo(span.spanContext.traceId)
    assertThat(trace.spanId.toString()).isEqualTo(span.spanContext.spanId)

    val logs = server.awaitEnvelope { it.logs() != null }.logs()!!
    assertThat(logs.items.map { it.body }).contains("Some error log message")

    val metrics = server.awaitEnvelope { it.metrics() != null }.metrics()!!
    assertThat(metrics.hasMetric("invocations", "counter", 1.0)).isTrue()
  }
}
