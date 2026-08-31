package io.sentry.okhttp

import com.google.common.truth.Truth.assertThat
import io.sentry.IScopes
import io.sentry.SentryOptions
import java.io.IOException
import kotlin.test.Test
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SentryOkHttpEventListenerDelegationTest {
  class RecordingListener(val ownCall: Call) : EventListener() {
    val received = mutableListOf<Pair<String, Call>>()

    override fun callStart(call: Call) {
      received += "callStart" to call
    }

    override fun dnsStart(call: Call, domainName: String) {
      received += "dnsStart" to call
    }

    override fun callEnd(call: Call) {
      received += "callEnd" to call
    }

    override fun callFailed(call: Call, ioe: IOException) {
      received += "callFailed" to call
    }

    override fun canceled(call: Call) {
      received += "canceled" to call
    }

    fun mismatches(): List<Pair<String, Call>> = received.filter { it.second !== ownCall }
  }

  class Fixture {
    val scopes = mock<IScopes>()
    val client = OkHttpClient()
    val listeners = mutableListOf<RecordingListener>()

    fun getSut(): SentryOkHttpEventListener {
      whenever(scopes.options).thenReturn(SentryOptions())
      return SentryOkHttpEventListener(
        scopes,
        EventListener.Factory { call -> RecordingListener(call).also { listeners.add(it) } },
      )
    }

    fun newCall(path: String): Call =
      client.newCall(Request.Builder().url("http://localhost/$path").build())

    // The tests drive the listener by hand, so no call is ever really executed. canceled() branches
    // on Call.isExecuted() to tell a cancel that precedes callStart() from one that follows the
    // terminal event, so those cases need a Call that reports the state under test.
    fun mockCall(path: String, isExecuted: Boolean): Call {
      val request = Request.Builder().url("http://localhost/$path").build()
      return mock<Call>().also {
        whenever(it.request()).thenReturn(request)
        whenever(it.isExecuted()).thenReturn(isExecuted)
      }
    }
  }

  private val fixture = Fixture()

  @Test
  fun `each call is delegated to the listener created for it`() {
    val sut = fixture.getSut()
    val call1 = fixture.newCall("1")
    val call2 = fixture.newCall("2")

    sut.callStart(call1)
    sut.callStart(call2)
    sut.dnsStart(call1, "sentry.io")
    sut.dnsStart(call2, "sentry.io")
    sut.callEnd(call1)
    sut.callEnd(call2)

    val (listener1, listener2) = fixture.listeners
    assertThat(listener1.mismatches()).isEmpty()
    assertThat(listener2.mismatches()).isEmpty()
    assertThat(listener1.received.map { it.first })
      .containsExactly("callStart", "dnsStart", "callEnd")
      .inOrder()
    assertThat(listener2.received.map { it.first })
      .containsExactly("callStart", "dnsStart", "callEnd")
      .inOrder()
  }

  @Test
  fun `callbacks without a preceding callStart are not delegated`() {
    val sut = fixture.getSut()
    val call1 = fixture.newCall("1")
    val call2 = fixture.newCall("2")

    sut.callStart(call1)
    sut.dnsStart(call2, "sentry.io")
    sut.callEnd(call2)

    assertThat(fixture.listeners).hasSize(1)
    assertThat(fixture.listeners.single().received.map { it.first }).containsExactly("callStart")
  }

  @Test
  fun `a finished call is no longer delegated to`() {
    val sut = fixture.getSut()
    val call = fixture.newCall("1")

    sut.callStart(call)
    sut.callEnd(call)
    sut.dnsStart(call, "sentry.io")

    assertThat(fixture.listeners.single().received.map { it.first })
      .containsExactly("callStart", "callEnd")
      .inOrder()
  }

  @Test
  fun `a failed call is no longer delegated to`() {
    val sut = fixture.getSut()
    val call = fixture.newCall("1")

    sut.callStart(call)
    sut.callFailed(call, IOException())
    sut.dnsStart(call, "sentry.io")

    assertThat(fixture.listeners.single().received.map { it.first })
      .containsExactly("callStart", "callFailed")
      .inOrder()
  }

  @Test
  fun `cancel during a call is delegated to the listener of that call`() {
    val sut = fixture.getSut()
    val call = fixture.newCall("1")

    sut.callStart(call)
    sut.canceled(call)
    sut.callFailed(call, IOException())

    assertThat(fixture.listeners.single().received.map { it.first })
      .containsExactly("callStart", "canceled", "callFailed")
      .inOrder()
  }

  @Test
  fun `cancel before callStart binds the listener that callStart then reuses`() {
    val sut = fixture.getSut()
    val call = fixture.mockCall("1", isExecuted = false)

    sut.canceled(call)
    sut.callStart(call)
    sut.dnsStart(call, "sentry.io")
    sut.callEnd(call)

    assertThat(fixture.listeners).hasSize(1)
    assertThat(fixture.listeners.single().received.map { it.first })
      .containsExactly("canceled", "callStart", "dnsStart", "callEnd")
      .inOrder()
  }

  @Test
  fun `cancel after the terminal event is ignored`() {
    val sut = fixture.getSut()
    val call = fixture.mockCall("1", isExecuted = true)

    sut.callStart(call)
    sut.callEnd(call)
    sut.canceled(call)

    assertThat(fixture.listeners).hasSize(1)
    assertThat(fixture.listeners.single().received.map { it.first })
      .containsExactly("callStart", "callEnd")
      .inOrder()
  }

  @Test
  fun `cancel after a failed call is ignored`() {
    val sut = fixture.getSut()
    val call = fixture.mockCall("1", isExecuted = true)

    sut.callStart(call)
    sut.callFailed(call, IOException())
    sut.canceled(call)

    assertThat(fixture.listeners).hasSize(1)
    assertThat(fixture.listeners.single().received.map { it.first })
      .containsExactly("callStart", "callFailed")
      .inOrder()
  }

  @Test
  fun `a single wrapped listener receives cancels outside of the call window`() {
    whenever(fixture.scopes.options).thenReturn(SentryOptions())
    val call = fixture.mockCall("1", isExecuted = true)
    val listener = RecordingListener(call)
    val sut = SentryOkHttpEventListener(fixture.scopes, listener)

    sut.canceled(call)
    sut.callStart(call)
    sut.callEnd(call)
    sut.canceled(call)

    assertThat(listener.received.map { it.first })
      .containsExactly("canceled", "callStart", "callEnd", "canceled")
      .inOrder()
  }
}
