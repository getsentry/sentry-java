package io.sentry.spring.boot4.webfluxintegrationtest

import com.google.common.truth.Truth.assertThat
import io.sentry.Sentry
import io.sentry.test.event
import io.sentry.test.hasFlag
import io.sentry.test.hasLogWithAttribute
import io.sentry.test.hasLogWithBody
import io.sentry.test.logs
import io.sentry.test.profileChunk
import io.sentry.test.transaction
import kotlin.test.Test

class PersonWebfluxIntegrationTest : WebfluxIntegrationTest() {

  @Test
  fun `failed request reports the error, the transaction and logs`() {
    val response = restTemplate.getForEntity("/person/1", String::class.java)
    assertThat(response.statusCode.value()).isEqualTo(500)
    Sentry.flush(5000)

    val thrown =
      server
        .awaitEnvelope {
          it.event()?.exceptions?.any { e -> e.value == "Something went wrong [id=1]" } == true
        }
        .event()!!
    assertThat(thrown.hasFlag("my-feature-flag", true)).isTrue()

    val transaction =
      server.awaitEnvelope { it.transaction()?.transaction == "GET /person/{id}" }.transaction()!!
    assertThat(transaction.contexts.trace!!.operation).isEqualTo("http.server")

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
  fun `creating a person on another thread is traced`() {
    val response =
      restTemplate.postForEntity("/person/", Person("firstA", "lastB"), Person::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body!!.firstName).isEqualTo("firstA")
    assertThat(response.body!!.lastName).isEqualTo("lastB")

    val transaction =
      server.awaitEnvelope { it.transaction()?.transaction == "POST /person/" }.transaction()!!
    assertThat(transaction.contexts.trace!!.operation).isEqualTo("http.server")
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
}
