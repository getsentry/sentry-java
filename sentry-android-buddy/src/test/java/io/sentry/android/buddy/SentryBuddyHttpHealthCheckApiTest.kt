package io.sentry.android.buddy

import com.google.common.truth.Truth.assertThat
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
  fun `check posts health check request and parses findings`() {
    server.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(
          """
          {
            "summary": "Buddy found 1 finding worth checking.",
            "findings": [{
              "id": "sdk-outdated",
              "title": "Upgrade the Sentry SDK",
              "description": "Newer SDK versions are available.",
              "severity": "LOW",
              "currentValue": "8.39.0",
              "suggestedValue": "8.40.0",
              "link": "https://github.com/getsentry/sentry-java/releases/tag/8.40.0"
            }]
          }
          """
            .trimIndent()
        )
    )
    val api = SentryBuddyHttpHealthCheckApi(server.url("/").toString())

    val response = api.check(request())

    assertThat(response.summary).contains("1 finding")
    assertThat(response.findings).hasSize(1)
    assertThat(response.findings.single().title).contains("Upgrade")
    val recordedRequest = server.takeRequest()
    assertThat(recordedRequest.method).isEqualTo("POST")
    assertThat(recordedRequest.path).isEqualTo("/v1/health-check")
    val body = recordedRequest.body.readUtf8()
    assertThat(body).contains("\"sdk\":\"io.sentry.android@8.39.0\"")
    assertThat(body).contains("\"dsnConfigured\":true")
    assertThat(body).contains("\"sessionReplayEnabled\":false")
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
