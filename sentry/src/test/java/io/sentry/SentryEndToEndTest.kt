package io.sentry

import com.google.common.truth.Truth.assertThat
import io.sentry.protocol.Message
import io.sentry.test.MockSentryServer
import io.sentry.test.event
import io.sentry.test.hasFlag
import io.sentry.test.hasMetric
import io.sentry.test.hasMetricWithAttribute
import io.sentry.test.initForTest
import io.sentry.test.metrics
import io.sentry.test.transaction
import kotlin.test.AfterTest
import kotlin.test.Test

/** Reports through the APIs an app uses without integrations, over the real HTTP transport. */
class SentryEndToEndTest {
  private val server = MockSentryServer()

  @AfterTest
  fun teardown() {
    Sentry.close()
    server.close()
  }

  @Test
  fun `events, a transaction and metrics reach Sentry`() {
    initForTest {
      it.dsn = server.dsn
      it.tracesSampleRate = 1.0
      it.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
        if (breadcrumb.message == "bad breadcrumb") null else breadcrumb
      }
    }
    Sentry.addBreadcrumb("bad breadcrumb")
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
    transaction.startChild("child").finish()
    transaction.finish()
    Sentry.flush(5000)

    val fatal =
      server.awaitEnvelope { it.event()?.message?.formatted == "Fatal message!" }.event()!!
    assertThat(fatal.level).isEqualTo(SentryLevel.FATAL)
    assertThat(fatal.breadcrumbs!!.map { it.message }).contains("Processed by the event processor")
    assertThat(fatal.breadcrumbs!!.map { it.message }).doesNotContain("bad breadcrumb")

    val warning =
      server.awaitEnvelope { it.event()?.message?.formatted == "Some warning!" }.event()!!
    assertThat(warning.level).isEqualTo(SentryLevel.WARNING)

    val error =
      server
        .awaitEnvelope { it.event()?.exceptions?.any { e -> e.value == "Some error!" } == true }
        .event()!!
    assertThat(error.exceptions!!.map { it.type to it.value })
      .contains("RuntimeException" to "Some error!")
    assertThat(error.hasFlag("my-feature-flag", true)).isTrue()

    val detailed =
      server.awaitEnvelope { it.event()?.message?.message == "Detailed event" }.event()!!
    assertThat(detailed.fingerprints).containsExactly("NewClientDebug")
    assertThat(detailed.level).isEqualTo(SentryLevel.DEBUG)

    repeat(10) { i -> server.awaitEnvelope { it.event()?.message?.message == "event $i" } }

    val sent =
      server.awaitEnvelope { it.transaction()?.transaction == "transaction name" }.transaction()!!
    assertThat(sent.spans.map { it.op }).contains("child")

    val metrics = server.awaitEnvelope { it.metrics() != null }.metrics()!!
    assertThat(metrics.hasMetric("countMetric", "counter", 1.0)).isTrue()
    assertThat(metrics.hasMetric("gaugeMetric", "gauge", 5.0)).isTrue()
    assertThat(metrics.hasMetric("distributionMetric", "distribution", 7.0)).isTrue()
    assertThat(metrics.hasMetricWithAttribute("countMetric", "user.type", "admin")).isTrue()
    assertThat(metrics.hasMetricWithAttribute("countMetric", "feature.version", 2)).isTrue()
  }
}
