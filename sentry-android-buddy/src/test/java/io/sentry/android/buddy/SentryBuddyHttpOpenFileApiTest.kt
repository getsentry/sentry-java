package io.sentry.android.buddy

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.sentry.android.buddy.bridge.SentryBuddyHttpOpenFileApi
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
class SentryBuddyHttpOpenFileApiTest {
  private val server = MockWebServer()

  @AfterTest
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `open posts path and line to bridge`() {
    server.enqueue(MockResponse().setResponseCode(200))
    val api = SentryBuddyHttpOpenFileApi(server.url("/").toString())

    api.open(RuntimeEnvironment.getApplication(), "/repo/app/src/main/java/A.kt", 42)

    val recordedRequest = server.takeRequest()
    assertThat(recordedRequest.method).isEqualTo("POST")
    assertThat(recordedRequest.path).isEqualTo("/v1/open-file")
    assertThat(recordedRequest.body.readUtf8())
      .isEqualTo("""{"path":"/repo/app/src/main/java/A.kt","line":42}""")
  }

  @Test
  fun `open omits a null line`() {
    server.enqueue(MockResponse().setResponseCode(200))
    val api = SentryBuddyHttpOpenFileApi(server.url("/").toString())

    api.open(RuntimeEnvironment.getApplication(), "/repo/A.kt", null)

    assertThat(server.takeRequest().body.readUtf8()).isEqualTo("""{"path":"/repo/A.kt"}""")
  }

  @Test
  fun `http errors include bridge error message`() {
    server.enqueue(
      MockResponse().setResponseCode(409).setBody("""{"error":"Android Studio is not running"}""")
    )
    val api = SentryBuddyHttpOpenFileApi(server.url("/").toString())

    val error =
      assertFailsWith<IllegalStateException> {
        api.open(RuntimeEnvironment.getApplication(), "/repo/A.kt", 1)
      }

    assertThat(error).hasMessageThat().contains("HTTP 409")
    assertThat(error).hasMessageThat().contains("Android Studio is not running")
  }
}
