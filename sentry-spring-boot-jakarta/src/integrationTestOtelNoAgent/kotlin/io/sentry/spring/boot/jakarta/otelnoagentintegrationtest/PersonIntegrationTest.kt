package io.sentry.spring.boot.jakarta.otelnoagentintegrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.Sentry
import io.sentry.protocol.FeatureFlag
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.test.event
import io.sentry.test.hasFlag
import io.sentry.test.hasLogWithAttribute
import io.sentry.test.hasLogWithBody
import io.sentry.test.hasOp
import io.sentry.test.hasSpanWithOp
import io.sentry.test.hasSpanWithOpAndDescription
import io.sentry.test.logs
import io.sentry.test.profileChunk
import io.sentry.test.transaction
import kotlin.test.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders

class PersonIntegrationTest : IntegrationTest() {

  @Test
  fun `failed request reports errors, nested spans with their flags, and logs`() {
    val response = restTemplate.getForEntity("/person/1", String::class.java)
    assertThat(response.statusCode.value()).isEqualTo(500)
    Sentry.flush(5000)

    val logged =
      server.awaitEnvelope { it.event()?.message?.formatted == "Trying person with id=1" }.event()!!
    assertThat(logged.hasFlag("inner-feature-flag", true)).isTrue()

    val thrown =
      server
        .awaitEnvelope {
          it.event()?.exceptions?.any { e -> e.value == "Something went wrong [id=1]" } == true
        }
        .event()!!
    assertThat(thrown.hasFlag("inner-feature-flag", true)).isTrue()

    val transaction =
      server.awaitEnvelope { it.transaction()?.transaction == "GET /person/{id}" }.transaction()!!
    assertThat(transaction.hasOp("http.server")).isTrue()
    assertThat(
        transaction.hasSpanWithOp(
          "personSpanThroughOtelAnnotation",
          FeatureFlag("flag.evaluation.outer-feature-flag", true),
        )
      )
      .isTrue()
    assertThat(
        transaction.hasSpanWithOp(
          "spanCreatedThroughOtelApi",
          FeatureFlag("flag.evaluation.inner-feature-flag", true),
        )
      )
      .isTrue()
    assertThat(transaction.hasSpanWithOp("spanCreatedThroughSentryApi", noFeatureFlags = true))
      .isTrue()

    val logs =
      server
        .awaitEnvelope {
          it.logs()?.items?.any { log -> log.body == "warn Sentry logging" } == true
        }
        .logs()!!
    assertThat(logs.hasLogWithBody("error Sentry logging")).isTrue()
    assertThat(logs.hasLogWithBody("hello there world!")).isTrue()
    assertThat(logs.hasLogWithAttribute("warn Sentry logging", "user.type", "admin")).isTrue()
    assertThat(logs.hasLogWithAttribute("warn Sentry logging", "feature.version", 2)).isTrue()
    assertThat(logs.hasLogWithAttribute("warn Sentry logging", "debug.enabled", true)).isTrue()
  }

  @Test
  fun `creating a person traces the OpenTelemetry, Sentry and database spans`() {
    assertCreatingPersonIsTraced(HttpHeaders())
  }

  @Test
  fun `creating a person starts a profile linked to the transaction`() {
    val response =
      restTemplate.postForEntity("/person/", Person("firstA", "lastB"), Person::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)

    val transaction =
      server.awaitEnvelope { it.transaction()?.transaction == "POST /person/" }.transaction()!!
    val profilerId = transaction.contexts.profile?.profilerId
    assertThat(profilerId).isNotNull()
    server.awaitEnvelope(timeoutMillis = 30_000) { it.profileChunk()?.profilerId == profilerId }
  }

  @Test
  fun `incoming trace without a sampled flag is traced`() {
    val traceId = SentryId()
    assertCreatingPersonIsTraced(traceHeaders("$traceId-424cffc8f94feeee", traceId), traceId)
  }

  @Test
  fun `incoming sampled trace is traced`() {
    val traceId = SentryId()
    assertCreatingPersonIsTraced(traceHeaders("$traceId-424cffc8f94feeee-1", traceId), traceId)
  }

  private fun assertCreatingPersonIsTraced(headers: HttpHeaders, traceId: SentryId? = null) {
    val response =
      restTemplate.postForEntity(
        "/person/",
        HttpEntity(Person("firstA", "lastB"), headers),
        Person::class.java,
      )
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body!!.firstName).isEqualTo("firstA")
    assertThat(response.body!!.lastName).isEqualTo("lastB")

    val transaction = awaitCreateTransaction(traceId)
    assertThat(transaction.hasSpanWithOp("spanCreatedThroughOtelApi")).isTrue()
    assertThat(transaction.hasSpanWithOp("spanCreatedThroughSentryApi")).isTrue()
    assertThat(
        transaction.hasSpanWithOpAndDescription(
          "db",
          "insert into person (firstName, lastName) values (?, ?)",
        )
      )
      .isTrue()
  }

  private fun awaitCreateTransaction(traceId: SentryId?): SentryTransaction =
    server
      .awaitEnvelope {
        it.transaction()?.let { t ->
          t.transaction == "POST /person/" &&
            (traceId == null || t.contexts.trace?.traceId == traceId)
        } == true
      }
      .transaction()!!

  private fun traceHeaders(sentryTrace: String, traceId: SentryId): HttpHeaders =
    HttpHeaders().apply {
      set("sentry-trace", sentryTrace)
      set(
        "baggage",
        "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=$traceId,sentry-transaction=HTTP%20GET",
      )
    }
}
