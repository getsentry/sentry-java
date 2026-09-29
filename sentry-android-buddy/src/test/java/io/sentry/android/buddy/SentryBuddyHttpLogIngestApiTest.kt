package io.sentry.android.buddy

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.android.buddy.bridge.BuddyLogItem
import io.sentry.android.buddy.bridge.SentryBuddyHttpLogIngestApi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class SentryBuddyHttpLogIngestApiTest {
  private val server = MockWebServer()

  @AfterTest
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `ingest posts items under the session and returns the stored count`() {
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"stored":1}"""))
    val api = SentryBuddyHttpLogIngestApi(server.url("/").toString())

    val stored =
      api.ingest(
        "session-1",
        listOf(BuddyLogItem(type = "log", timestamp = 1000L, data = mapOf("message" to "hi"))),
      )

    assertThat(stored).isEqualTo(1)
    val recordedRequest = server.takeRequest()
    assertThat(recordedRequest.method).isEqualTo("POST")
    assertThat(recordedRequest.path).isEqualTo("/v1/logs/session-1")
    assertThat(recordedRequest.body.readUtf8())
      .isEqualTo("""[{"type":"log","timestamp":1000,"data":{"message":"hi"}}]""")
  }

  @Test
  fun `http errors include bridge error message`() {
    server.enqueue(
      MockResponse().setResponseCode(400).setBody("""{"error":"invalid session id"}""")
    )
    val api = SentryBuddyHttpLogIngestApi(server.url("/").toString())

    val error = assertFailsWith<IllegalStateException> { api.ingest("../evil", listOf()) }

    assertThat(error).hasMessageThat().contains("HTTP 400")
    assertThat(error).hasMessageThat().contains("invalid session id")
  }
}
