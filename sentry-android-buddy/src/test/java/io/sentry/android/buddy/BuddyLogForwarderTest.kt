package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
import io.sentry.SpanContext
import io.sentry.SpanId
import io.sentry.SpanStatus
import io.sentry.android.buddy.bridge.BuddyLogItem
import io.sentry.android.buddy.bridge.SentryBuddyLogIngestApi
import io.sentry.protocol.MeasurementValue
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.TransactionInfo
import kotlin.test.Test

class BuddyLogForwarderTest {
  private class CapturingLogIngestApi : SentryBuddyLogIngestApi {
    val items = mutableListOf<BuddyLogItem>()

    override fun ingest(sessionId: String, items: List<BuddyLogItem>): Int {
      this.items += items
      return items.size
    }
  }

  @Test
  fun `transaction item carries child spans with timing, status and attributes`() {
    val api = CapturingLogIngestApi()
    val forwarder = BuddyLogForwarder(api, "session-1", executor = { it.run() })
    val traceId = SentryId()
    val rootSpanId = SpanId()
    val childSpanId = SpanId()
    val transaction =
      SentryTransaction(
          "LoginActivity",
          10.0,
          12.5,
          listOf(
            SentrySpan(
              11.25,
              11.75,
              traceId,
              childSpanId,
              rootSpanId,
              "http.client",
              "POST /auth",
              SpanStatus.INTERNAL_ERROR,
              "auto.http.okhttp",
              mapOf("tag" to "value"),
              mapOf("bytes" to MeasurementValue(512, "byte")),
              mapOf("http.response.status_code" to 500),
            )
          ),
          mapOf("ttid" to MeasurementValue(300, "millisecond")),
          TransactionInfo("component"),
        )
        .apply {
          contexts.setTrace(SpanContext(traceId, rootSpanId, "ui.load", null, null))
        }

    forwarder.forwardTransaction(transaction)

    val data = api.items.single().data
    assertThat(data["op"]).isEqualTo("ui.load")
    assertThat(data["span_id"]).isEqualTo(rootSpanId.toString())
    assertThat(data["start_timestamp_ms"]).isEqualTo(10_000L)
    assertThat(data["end_timestamp_ms"]).isEqualTo(12_500L)
    assertThat(data["duration_ms"]).isEqualTo(2_500L)
    assertThat(data["measurements"] as Map<*, *>)
      .containsEntry("ttid", mapOf("value" to 300, "unit" to "millisecond"))

    val span = (data["spans"] as List<*>).single() as Map<*, *>
    assertThat(span["span_id"]).isEqualTo(childSpanId.toString())
    assertThat(span["parent_span_id"]).isEqualTo(rootSpanId.toString())
    assertThat(span["op"]).isEqualTo("http.client")
    assertThat(span["description"]).isEqualTo("POST /auth")
    assertThat(span["status"]).isEqualTo("internal_error")
    assertThat(span["origin"]).isEqualTo("auto.http.okhttp")
    assertThat(span["offset_ms"]).isEqualTo(1_250L)
    assertThat(span["duration_ms"]).isEqualTo(500L)
    assertThat(span["tags"]).isEqualTo(mapOf("tag" to "value"))
    assertThat(span["data"]).isEqualTo(mapOf("http.response.status_code" to 500))
    assertThat(span["measurements"])
      .isEqualTo(mapOf("bytes" to mapOf("value" to 512, "unit" to "byte")))
  }
}
