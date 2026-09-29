package io.sentry.android.cronet

import com.google.common.truth.Truth.assertThat
import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryCronetCallbackTest {
  private class Fixture {
    val scopes = mock<IScopes>()
    val options = SentryOptions()
    val request = mock<UrlRequest>()
    val delegate = mock<UrlRequest.Callback>()
    val tracer: SentryTracer
    val response = mock<UrlResponseInfo>()

    init {
      whenever(scopes.options).thenReturn(options)
      tracer = SentryTracer(TransactionContext("test", "test"), scopes)
      whenever(scopes.transaction).thenReturn(tracer)
      whenever(response.httpStatusCode).thenReturn(200)
      whenever(response.negotiatedProtocol).thenReturn("h3")
    }

    fun callback(url: String = "https://example.com/api") =
      SentryCronetCallback(url, "GET", delegate, scopes)

    fun breadcrumb(): Breadcrumb {
      val captor = argumentCaptor<Breadcrumb>()
      verify(scopes).addBreadcrumb(captor.capture())
      return captor.firstValue
    }
  }

  private val fixture = Fixture()

  @Test
  fun `starts tracing only when the request starts`() {
    val callback = fixture.callback()
    assertThat(fixture.tracer.spans).isEmpty()
    callback.start(fixture.request)
    verify(fixture.request).start()
    val span = fixture.tracer.spans.single()
    assertThat(span.operation).isEqualTo("http.client")
    assertThat(span.description).isEqualTo("GET https://example.com/api")
    assertThat(span.spanContext.origin).isEqualTo("auto.http.cronet")
    assertThat(span.getData(SpanDataConvention.HTTP_METHOD_KEY)).isEqualTo("GET")
    assertThat(span.isFinished).isFalse()
  }

  @Test
  fun `success finishes span with status and negotiated HTTP3 protocol`() {
    val callback = fixture.callback()
    callback.start(fixture.request)
    callback.onSucceeded(fixture.request, fixture.response)
    val span = fixture.tracer.spans.single()
    assertThat(span.isFinished).isTrue()
    assertThat(span.status).isEqualTo(SpanStatus.OK)
    assertThat(span.getData(SpanDataConvention.HTTP_STATUS_CODE_KEY)).isEqualTo(200)
    assertThat(span.getData("protocol")).isEqualTo("h3")
    val breadcrumb = fixture.breadcrumb()
    assertThat(breadcrumb.category).isEqualTo("http")
    assertThat(breadcrumb.getData("protocol")).isEqualTo("h3")
    assertThat(breadcrumb.getData(SpanDataConvention.HTTP_START_TIMESTAMP)).isNotNull()
    assertThat(breadcrumb.getData(SpanDataConvention.HTTP_END_TIMESTAMP)).isNotNull()
    verify(fixture.delegate).onSucceeded(fixture.request, fixture.response)
  }

  @Test
  fun `HTTP errors use HTTP status mapping`() {
    whenever(fixture.response.httpStatusCode).thenReturn(503)
    val callback = fixture.callback()
    callback.start(fixture.request)
    callback.onSucceeded(fixture.request, fixture.response)
    assertThat(fixture.tracer.spans.single().status).isEqualTo(SpanStatus.UNAVAILABLE)
  }

  @Test
  fun `network failure without response closes span and forwards the same error`() {
    val error = mock<CronetException>()
    val callback = fixture.callback()
    callback.start(fixture.request)
    callback.onFailed(fixture.request, null, error)
    val span = fixture.tracer.spans.single()
    assertThat(span.isFinished).isTrue()
    assertThat(span.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
    assertThat(span.throwable).isSameInstanceAs(error)
    assertThat(fixture.breadcrumb().getData("status_code")).isNull()
    verify(fixture.delegate).onFailed(fixture.request, null, error)
  }

  @Test
  fun `cancellation after response overrides HTTP success status`() {
    val callback = fixture.callback()
    callback.start(fixture.request)
    callback.onCanceled(fixture.request, fixture.response)
    assertThat(fixture.tracer.spans.single().status).isEqualTo(SpanStatus.CANCELLED)
    assertThat(fixture.tracer.spans.single().isFinished).isTrue()
    verify(fixture.delegate).onCanceled(fixture.request, fixture.response)
  }

  @Test
  fun `cancellation before headers supports null response`() {
    val callback = fixture.callback()
    callback.start(fixture.request)
    callback.onCanceled(fixture.request, null)
    assertThat(fixture.tracer.spans.single().status).isEqualTo(SpanStatus.CANCELLED)
    fixture.breadcrumb()
    verify(fixture.delegate).onCanceled(fixture.request, null)
  }

  @Test
  fun `redirect response and read callbacks retain application flow control`() {
    val callback = fixture.callback()
    val buffer = ByteBuffer.allocateDirect(8)
    callback.start(fixture.request)
    callback.onRedirectReceived(fixture.request, fixture.response, "https://other.example/api")
    callback.onResponseStarted(fixture.request, fixture.response)
    callback.onReadCompleted(fixture.request, fixture.response, buffer)
    verify(fixture.delegate)
      .onRedirectReceived(fixture.request, fixture.response, "https://other.example/api")
    verify(fixture.delegate).onResponseStarted(fixture.request, fixture.response)
    verify(fixture.delegate).onReadCompleted(fixture.request, fixture.response, buffer)
    verify(fixture.request, never()).followRedirect()
    verify(fixture.request, never()).read(any())
    assertThat(fixture.tracer.spans.single().isFinished).isFalse()
    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>())
  }

  @Test
  fun `breadcrumbs work without an active transaction`() {
    whenever(fixture.scopes.transaction).thenReturn(null)
    val callback = fixture.callback()
    callback.start(fixture.request)
    callback.onSucceeded(fixture.request, fixture.response)
    assertThat(fixture.tracer.spans).isEmpty()
    assertThat(fixture.breadcrumb().getData("url")).isEqualTo("https://example.com/api")
  }

  @Test
  fun `synchronous start failure is rethrown and span is finished`() {
    val error = IllegalStateException("invalid request")
    doThrow(error).whenever(fixture.request).start()
    val callback = fixture.callback()
    assertThat(assertFailsWith<IllegalStateException> { callback.start(fixture.request) })
      .isSameInstanceAs(error)
    assertThat(fixture.tracer.spans.single().status).isEqualTo(SpanStatus.INTERNAL_ERROR)
    assertThat(fixture.tracer.spans.single().isFinished).isTrue()
    fixture.breadcrumb()
  }

  @Test
  fun `terminal callback exceptions cannot leak spans`() {
    val error = IllegalStateException("application failure")
    doThrow(error).whenever(fixture.delegate).onSucceeded(fixture.request, fixture.response)
    val callback = fixture.callback()
    callback.start(fixture.request)
    assertThat(
        assertFailsWith<IllegalStateException> {
          callback.onSucceeded(fixture.request, fixture.response)
        }
      )
      .isSameInstanceAs(error)
    assertThat(fixture.tracer.spans.single().isFinished).isTrue()
    fixture.breadcrumb()
  }

  @Test
  fun `does not finish or breadcrumb twice`() {
    val callback = fixture.callback()
    callback.start(fixture.request)
    callback.onSucceeded(fixture.request, fixture.response)
    callback.onCanceled(fixture.request, null)
    assertThat(fixture.tracer.spans.single().status).isEqualTo(SpanStatus.OK)
    fixture.breadcrumb()
  }

  @Test
  fun `callback cannot be reused`() {
    val callback = fixture.callback()
    callback.start(fixture.request)
    assertFailsWith<IllegalStateException> { callback.start(fixture.request) }
    verify(fixture.request, times(1)).start()
    assertThat(fixture.tracer.spans).hasSize(1)
  }

  @Test
  fun `callbacks still delegate when start helper was not used`() {
    val callback = fixture.callback()
    callback.onSucceeded(fixture.request, fixture.response)
    verify(fixture.delegate).onSucceeded(fixture.request, fixture.response)
    assertThat(fixture.tracer.spans).isEmpty()
    verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>())
  }

  @Test
  fun `URL credentials are filtered from descriptions and breadcrumbs`() {
    val callback = fixture.callback("https://user:secret@example.com/api?key=value#fragment")
    callback.start(fixture.request)
    callback.onSucceeded(fixture.request, fixture.response)
    val description = fixture.tracer.spans.single().description
    assertThat(description).doesNotContain("user")
    assertThat(description).doesNotContain("secret")
    assertThat(description).doesNotContain("key=value")
    assertThat(fixture.breadcrumb().getData("url").toString()).doesNotContain("secret")
  }
}
