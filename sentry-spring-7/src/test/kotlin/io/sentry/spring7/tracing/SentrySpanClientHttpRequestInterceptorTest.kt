package io.sentry.spring7.tracing

import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.W3CTraceparentHeader
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
class SentrySpanClientHttpRequestInterceptorTest {

  class Fixture {
    val request = MockClientHttpRequest(HttpMethod.GET, URI.create("https://example.com/users/123"))

    val options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        tracesSampleRate = 1.0
      }
    val scope = Scope(options)

    val scopes = mock<IScopes>()
    val requestExecution = mock<ClientHttpRequestExecution>()
    val body = "data".toByteArray()

    init {
      whenever(scopes.options).thenReturn(options)
      doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }
        .whenever(scopes)
        .configureScope(any())

      whenever(requestExecution.execute(any(), any())).thenReturn(mock<ClientHttpResponse>())
    }

    fun create(
      config: Sentry.OptionsConfiguration<SentryOptions>
    ): SentrySpanClientHttpRequestInterceptor {
      config.configure(options)
      return SentrySpanClientHttpRequestInterceptor(scopes)
    }
  }

  val fixture = Fixture()

  @Test
  fun `attaches w3c trace parent header when enabled`() {
    val sut = fixture.create { options -> options.isPropagateTraceparent = true }
    sut.intercept(fixture.request, fixture.body, fixture.requestExecution)

    assertNotNull(fixture.request.headers.get(SentryTraceHeader.SENTRY_TRACE_HEADER))
    assertNotNull(fixture.request.headers.get(W3CTraceparentHeader.TRACEPARENT_HEADER))
  }

  @Test
  fun `does not attach w3c trace parent header when disabled`() {
    val sut = fixture.create { options -> options.isPropagateTraceparent = false }
    sut.intercept(fixture.request, fixture.body, fixture.requestExecution)

    assertNotNull(fixture.request.headers.get(SentryTraceHeader.SENTRY_TRACE_HEADER))
    assertNull(fixture.request.headers.get(W3CTraceparentHeader.TRACEPARENT_HEADER))
  }
}
