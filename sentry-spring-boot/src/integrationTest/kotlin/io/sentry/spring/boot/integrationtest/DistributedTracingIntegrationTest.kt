package io.sentry.spring.boot.integrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.SentryEnvelope
import io.sentry.protocol.SentryId
import io.sentry.test.transaction
import kotlin.test.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod

/**
 * Sends requests that continue an incoming trace, and checks that both the request's transaction
 * and the one for the app's own outgoing request carry the trace.
 */
class DistributedTracingIntegrationTest : IntegrationTest() {

  @Test
  fun `sampled trace continues through the outgoing request`() {
    val traceId = SentryId()
    val status =
      get(
        "/tracing/1",
        sentryTrace = "$traceId-424cffc8f94feeee-1",
        baggage =
          "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET",
      )
    assertThat(status).isEqualTo(500)

    awaitTransaction("GET /tracing/{id}", traceId)
    awaitTransaction("GET /person/{id}", traceId)
  }

  @Test
  fun `unsampled trace sends no transactions`() {
    val traceId = SentryId()
    val status =
      get(
        "/tracing/1",
        sentryTrace = "$traceId-424cffc8f94feeee-0",
        baggage =
          "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=false,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET",
      )
    assertThat(status).isEqualTo(500)

    server.assertNoEnvelope {
      isTransaction(it, "GET /tracing/{id}", traceId) ||
        isTransaction(it, "GET /person/{id}", traceId)
    }
  }

  @Test
  fun `sample_rand generated for baggage without one is propagated`() {
    val traceId = SentryId()
    val status =
      get(
        "/tracing/1",
        sentryTrace = "$traceId-424cffc8f94feeee-1",
        baggage =
          "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET",
      )
    assertThat(status).isEqualTo(500)

    val incoming = awaitTransaction("GET /tracing/{id}", traceId).header.traceContext!!
    val outgoing = awaitTransaction("GET /person/{id}", traceId).header.traceContext!!
    assertThat(incoming.sampleRand).isNotNull()
    assertThat(outgoing.sampleRand).isEqualTo(incoming.sampleRand)
  }

  @Test
  fun `deferred sampling decision updates the propagated sample_rate`() {
    val traceId = SentryId()
    val status =
      get(
        "/tracing/1",
        sentryTrace = "$traceId-424cffc8f94feeee",
        baggage =
          "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=0.5,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET",
      )
    assertThat(status).isEqualTo(500)

    val incoming = awaitTransaction("GET /tracing/{id}", traceId).header.traceContext!!
    val outgoing = awaitTransaction("GET /person/{id}", traceId).header.traceContext!!
    assertThat(incoming.sampleRate).isNotEqualTo("0.5")
    assertThat(outgoing.sampleRate).isEqualTo(incoming.sampleRate)
  }

  @Test
  fun `sampled trace continues through an outgoing POST`() {
    val traceId = SentryId()
    val headers =
      traceHeaders(
        sentryTrace = "$traceId-424cffc8f94feeee-1",
        baggage =
          "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rand=0.456789,sentry-sample_rate=0.5,sentry-sampled=true,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET",
      )
    val response =
      restTemplate.postForEntity(
        "/tracing/",
        HttpEntity(Person("firstA", "lastB"), headers),
        Person::class.java,
      )
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body!!.firstName).isEqualTo("firstA")
    assertThat(response.body!!.lastName).isEqualTo("lastB")

    awaitTransaction("POST /tracing/", traceId)
    awaitTransaction("POST /person/", traceId)
  }

  private fun get(path: String, sentryTrace: String, baggage: String): Int =
    restTemplate
      .exchange(
        path,
        HttpMethod.GET,
        HttpEntity<Void>(traceHeaders(sentryTrace, baggage)),
        String::class.java,
      )
      .statusCode
      .value()

  private fun traceHeaders(sentryTrace: String, baggage: String): HttpHeaders =
    HttpHeaders().apply {
      set("sentry-trace", sentryTrace)
      set("baggage", baggage)
    }

  private fun awaitTransaction(name: String, traceId: SentryId): SentryEnvelope =
    server.awaitEnvelope {
      isTransaction(it, name, traceId)
    }

  private fun isTransaction(envelope: SentryEnvelope, name: String, traceId: SentryId): Boolean {
    val transaction = envelope.transaction() ?: return false
    return transaction.transaction == name && transaction.contexts.trace?.traceId == traceId
  }
}
