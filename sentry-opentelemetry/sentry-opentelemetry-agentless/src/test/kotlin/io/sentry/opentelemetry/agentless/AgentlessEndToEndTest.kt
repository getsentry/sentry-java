package io.sentry.opentelemetry.agentless

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.sentry.Breadcrumb
import io.sentry.EventProcessor
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message
import io.sentry.test.MockSentryServer
import io.sentry.test.event
import io.sentry.test.hasFlag
import io.sentry.test.hasMetric
import io.sentry.test.hasMetricWithAttribute
import io.sentry.test.metrics
import io.sentry.test.profileChunk
import io.sentry.test.transaction
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Sets Sentry up the way an app without the agent does: the OpenTelemetry SDK's autoconfiguration
 * loads Sentry's customizer, which initializes Sentry and has it trace through OpenTelemetry.
 */
class AgentlessEndToEndTest {
  private val server = MockSentryServer()
  private var openTelemetry: OpenTelemetrySdk? = null

  @AfterTest
  fun teardown() {
    openTelemetry?.close()
    GlobalOpenTelemetry.resetForTest()
    Sentry.close()
    server.close()
    SENTRY_PROPERTIES.forEach { System.clearProperty(it) }
  }

  @Test
  fun `Sentry set up by OpenTelemetry reports events, a profiled transaction and metrics`() {
    // The customizer's Sentry.init reads these as external configuration.
    System.setProperty("sentry.dsn", server.dsn)
    System.setProperty("sentry.traces-sample-rate", "1.0")
    System.setProperty("sentry.profile-session-sample-rate", "1.0")
    System.setProperty("sentry.profile-lifecycle", "TRACE")
    // The profiler rejects a traces path with a dot anywhere in it, which the module's absolute
    // path can have.
    System.setProperty(
      "sentry.profiling-traces-dir-path",
      "${System.getProperty("java.io.tmpdir")}/sentry-agentless-end-to-end-test",
    )
    val openTelemetry =
      AutoConfiguredOpenTelemetrySdk.builder()
        .setResultAsGlobal()
        .addPropertiesSupplier {
          mapOf(
            "otel.logs.exporter" to "none",
            "otel.metrics.exporter" to "none",
            "otel.traces.exporter" to "none",
          )
        }
        .build()
        .openTelemetrySdk
    this.openTelemetry = openTelemetry

    Sentry.configureScope {
      it.addEventProcessor(
        object : EventProcessor {
          override fun process(event: SentryEvent, hint: Hint): SentryEvent = event.apply {
            if ((level?.ordinal ?: 0) > SentryLevel.INFO.ordinal) {
              addBreadcrumb(Breadcrumb("Processed by the event processor"))
            }
          }
        }
      )
    }
    Sentry.withScope {
      it.level = SentryLevel.FATAL
      Sentry.captureMessage("Fatal message!")
    }
    Sentry.captureMessage("Some warning!", SentryLevel.WARNING)
    Sentry.addFeatureFlag("my-feature-flag", true)
    Sentry.setAttribute("user.type", "admin")
    Sentry.setAttribute("feature.version", 2)
    Sentry.metrics().count("countMetric")
    Sentry.metrics().gauge("gaugeMetric", 5.0)
    Sentry.metrics().distribution("distributionMetric", 7.0)
    Sentry.captureException(RuntimeException("Some error!"))
    Sentry.captureEvent(
      SentryEvent().apply {
        message = Message().apply { message = "Detailed event" }
        fingerprints = listOf("NewClientDebug")
        level = SentryLevel.DEBUG
      }
    )
    repeat(10) { i ->
      Sentry.captureEvent(
        SentryEvent().apply { message = Message().apply { message = "event $i" } }
      )
    }
    val transaction = Sentry.startTransaction("transaction name", "op")
    val span = transaction.startChild("child")
    Thread.sleep(100)
    span.finish()
    transaction.finish()
    Sentry.flush(5000)
    // Sentry exports OpenTelemetry spans in batches every 5 seconds.
    openTelemetry.sdkTracerProvider.forceFlush().join(5, TimeUnit.SECONDS)

    val fatal =
      server.awaitEnvelope { it.event()?.message?.formatted == "Fatal message!" }.event()!!
    assertThat(fatal.level).isEqualTo(SentryLevel.FATAL)
    assertThat(fatal.breadcrumbs!!.map { it.message }).contains("Processed by the event processor")

    val warning =
      server.awaitEnvelope { it.event()?.message?.formatted == "Some warning!" }.event()!!
    assertThat(warning.level).isEqualTo(SentryLevel.WARNING)

    val error =
      server
        .awaitEnvelope { it.event()?.exceptions?.any { e -> e.value == "Some error!" } == true }
        .event()!!
    assertThat(error.hasFlag("my-feature-flag", true)).isTrue()

    val detailed =
      server.awaitEnvelope { it.event()?.message?.message == "Detailed event" }.event()!!
    assertThat(detailed.fingerprints).containsExactly("NewClientDebug")
    assertThat(detailed.level).isEqualTo(SentryLevel.DEBUG)

    repeat(10) { i -> server.awaitEnvelope { it.event()?.message?.message == "event $i" } }

    val sent =
      server.awaitEnvelope { it.transaction()?.transaction == "transaction name" }.transaction()!!
    assertThat(sent.spans.map { it.op }).contains("child")
    val profilerId = sent.contexts.profile?.profilerId
    assertThat(profilerId).isNotNull()
    server.awaitEnvelope(timeoutMillis = 30_000) { it.profileChunk()?.profilerId == profilerId }

    val metrics = server.awaitEnvelope { it.metrics() != null }.metrics()!!
    assertThat(metrics.hasMetric("countMetric", "counter", 1.0)).isTrue()
    assertThat(metrics.hasMetric("gaugeMetric", "gauge", 5.0)).isTrue()
    assertThat(metrics.hasMetric("distributionMetric", "distribution", 7.0)).isTrue()
    assertThat(metrics.hasMetricWithAttribute("countMetric", "user.type", "admin")).isTrue()
    assertThat(metrics.hasMetricWithAttribute("countMetric", "feature.version", 2)).isTrue()
  }

  private companion object {
    val SENTRY_PROPERTIES =
      listOf(
        "sentry.dsn",
        "sentry.traces-sample-rate",
        "sentry.profile-session-sample-rate",
        "sentry.profile-lifecycle",
        "sentry.profiling-traces-dir-path",
      )
  }
}
