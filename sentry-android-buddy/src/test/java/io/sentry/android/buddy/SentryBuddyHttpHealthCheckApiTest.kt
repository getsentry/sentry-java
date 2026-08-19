package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
import io.sentry.android.buddy.bridge.*
import io.sentry.android.buddy.model.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class SentryBuddyHttpHealthCheckApiTest {
  private val server = MockWebServer()

  @AfterTest
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `check posts health check request and parses recommendations`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """
          {
            "recommendations": [{
              "id": "sdk-outdated",
              "title": "Upgrade Sentry SDK to 8.40.0",
              "description": "Version io.sentry.android@8.39.0 detected, but sentry-java 8.40.0 is available.",
              "link": "https://github.com/getsentry/sentry-java/releases/tag/8.40.0",
              "severity": "LOW",
              "resolvable": true,
              "status": "OPEN"
            }]
          }
          """
            .trimIndent()
        )
    )
    val api = SentryBuddyHttpHealthCheckApi(server.url("/").toString())

    val response = api.check(request())

    assertThat(response.recommendations).hasSize(1)
    val recommendation = response.recommendations.single()
    assertThat(recommendation.id).isEqualTo("sdk-outdated")
    assertThat(recommendation.title).contains("Upgrade")
    assertThat(recommendation.severity).isEqualTo(Severity.LOW)
    assertThat(recommendation.status).isEqualTo(RecommendationStatus.OPEN)
    assertThat(recommendation.link)
      .isEqualTo("https://github.com/getsentry/sentry-java/releases/tag/8.40.0")
    val recordedRequest = server.takeRequest()
    assertThat(recordedRequest.method).isEqualTo("POST")
    assertThat(recordedRequest.path).isEqualTo("/v1/health-check")
    val body = recordedRequest.body.readUtf8()
    assertThat(body).contains("\"sdk\":\"io.sentry.android@8.39.0\"")
    assertThat(body).contains("\"dsn_configured\":true")
    assertThat(body).contains("\"session_replay_enabled\":false")
    assertThat(body).contains("\"traces_sample_rate\":1.0")
  }

  @Test
  fun `check tolerates a response without recommendations`() {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))
    val api = SentryBuddyHttpHealthCheckApi(server.url("/").toString())

    assertThat(api.check(request()).recommendations).isEmpty()
  }

  @Test
  fun `http errors include bridge error message`() {
    server.enqueue(
      MockResponse().setResponseCode(400).setBody("""{"error":"sdk must not be blank"}""")
    )
    val api = SentryBuddyHttpHealthCheckApi(server.url("/").toString())

    val error = assertFailsWith<IllegalStateException> { api.check(request()) }

    assertThat(error).hasMessageThat().contains("HTTP 400")
    assertThat(error).hasMessageThat().contains("sdk must not be blank")
  }

  private fun request(): BuddyHealthCheckRequest =
    BuddyHealthCheckRequest(
      sdk = "io.sentry.android@8.39.0",
      config =
        BuddySdkConfigSnapshot(
          dsnConfigured = true,
          tracesSampleRate = 1.0,
          sessionReplayEnabled = false,
        ),
    )
}
