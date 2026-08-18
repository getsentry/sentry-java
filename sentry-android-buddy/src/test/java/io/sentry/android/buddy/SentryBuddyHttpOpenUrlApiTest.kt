package io.sentry.android.buddy

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class SentryBuddyHttpOpenUrlApiTest {
  private val server = MockWebServer()

  @AfterTest
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `open posts url to bridge`() {
    server.enqueue(MockResponse().setResponseCode(200))
    val api = SentryBuddyHttpOpenUrlApi(server.url("/").toString())

    api.open(RuntimeEnvironment.getApplication(), "https://sentry.io/issues/1")

    val recordedRequest = server.takeRequest()
    assertThat(recordedRequest.method).isEqualTo("POST")
    assertThat(recordedRequest.path).isEqualTo("/v1/open-url")
    assertThat(recordedRequest.body.readUtf8())
      .isEqualTo("""{"url":"https://sentry.io/issues/1"}""")
  }

  @Test
  fun `http errors include bridge error message`() {
    server.enqueue(
      MockResponse().setResponseCode(400).setBody("""{"error":"url must use https"}""")
    )
    val api = SentryBuddyHttpOpenUrlApi(server.url("/").toString())

    val error =
      assertFailsWith<IllegalStateException> {
        api.open(RuntimeEnvironment.getApplication(), "http://sentry.io")
      }

    assertThat(error).hasMessageThat().contains("HTTP 400")
    assertThat(error).hasMessageThat().contains("url must use https")
  }
}
