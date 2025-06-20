package io.sentry.spring.jakarta.webflux

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.PropagationContext
import io.sentry.ScopeCallback
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanId
import io.sentry.SpanStatus
import io.sentry.TraceContext
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.protocol.TransactionNameSource
import io.sentry.spring.jakarta.webflux.AbstractSentryWebFilter.SENTRY_SCOPES_KEY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.server.WebFilterChain
import org.springframework.web.util.pattern.PathPatternParser
import reactor.core.publisher.Mono

class SentryWebFluxTracingFilterTest {
  private class Fixture {
    val scopes = mock<IScopes>()
    lateinit var request: MockServerHttpRequest
    lateinit var exchange: MockServerWebExchange
    val chain = mock<WebFilterChain>()
    val options =
      SentryOptions().apply {
        dsn = "https://key@sentry.io/proj"
        tracesSampleRate = 1.0
      }
    val logger = mock<ILogger>()

    init {
      whenever(scopes.options).thenReturn(options)
    }

    fun getSut(
      isEnabled: Boolean = true,
      status: HttpStatus = HttpStatus.OK,
      sentryTraceHeader: String? = null,
      baggageHeaders: List<String>? = null,
      method: HttpMethod = HttpMethod.POST,
    ): SentryWebFilter {
      var requestBuilder = MockServerHttpRequest.method(method, "/product/{id}", 12)
      if (sentryTraceHeader != null) {
        requestBuilder = requestBuilder.header("sentry-trace", sentryTraceHeader)
        whenever(scopes.startTransaction(any(), check<TransactionOptions> { it.isBindToScope }))
          .thenAnswer { SentryTracer(it.arguments[0] as TransactionContext, scopes) }
      }
      if (baggageHeaders != null) {
        requestBuilder = requestBuilder.header("baggage", *baggageHeaders.toTypedArray())
      }
      request = requestBuilder.build()
      exchange = MockServerWebExchange.builder(request).build()
      exchange.attributes.put(SENTRY_SCOPES_KEY, scopes)
      exchange.attributes.put(
        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
        PathPatternParser().parse("/product/{id}"),
      )
      exchange.response.statusCode = status
      whenever(
          scopes.startTransaction(any(), check<TransactionOptions> { assertTrue(it.isBindToScope) })
        )
        .thenAnswer { SentryTracer(it.arguments[0] as TransactionContext, scopes) }
      whenever(scopes.isEnabled).thenReturn(isEnabled)
      whenever(chain.filter(any())).thenReturn(Mono.create { s -> s.success() })
      whenever(scopes.continueTrace(anyOrNull(), anyOrNull())).thenAnswer {
        TransactionContext.fromPropagationContext(
          PropagationContext.fromHeaders(
            logger,
            it.arguments[0] as String?,
            it.arguments[1] as List<String>?,
          )
        )
      }
      return SentryWebFilter(scopes)
    }
  }

  private val fixture = Fixture()

  fun withMockScopes(closure: () -> Unit) =
    Mockito.mockStatic(Sentry::class.java).use {
      it.`when`<Any> { Sentry.getCurrentScopes() }.thenReturn(fixture.scopes)
      it.`when`<Any> { Sentry.forkedRootScopes(any()) }.thenReturn(fixture.scopes)
      closure.invoke()
    }

  @Test
  fun `creates transaction around the request`() {
    val filter = fixture.getSut()
    withMockScopes {
      filter.filter(fixture.exchange, fixture.chain).block()

      verify(fixture.scopes)
        .startTransaction(
          check<TransactionContext> {
            assertEquals("POST /product/12", it.name)
            assertEquals(TransactionNameSource.URL, it.transactionNameSource)
            assertEquals("http.server", it.operation)
          },
          check<TransactionOptions> {
            assertNotNull(it.customSamplingContext?.get("request"))
            assertTrue(it.customSamplingContext?.get("request") is ServerHttpRequest)
            assertTrue(it.isBindToScope)
            assertThat(it.origin).isEqualTo("auto.spring_jakarta.webflux")
          },
        )
      verify(fixture.chain).filter(fixture.exchange)
      verify(fixture.scopes)
        .captureTransaction(
          check {
            assertThat(it.transaction).isEqualTo("POST /product/{id}")
            assertThat(it.contexts.trace!!.status).isEqualTo(SpanStatus.OK)
            assertThat(it.contexts.trace!!.operation).isEqualTo("http.server")
            assertThat(it.contexts.response!!.statusCode).isEqualTo(200)
          },
          anyOrNull<TraceContext>(),
          anyOrNull(),
          anyOrNull(),
        )
    }
  }

  @Test
  fun `sets correct span status based on the response status`() {
    val filter = fixture.getSut(status = HttpStatus.INTERNAL_SERVER_ERROR)

    withMockScopes {
      filter.filter(fixture.exchange, fixture.chain).block()

      verify(fixture.scopes)
        .captureTransaction(
          check {
            assertThat(it.contexts.trace!!.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
            assertThat(it.contexts.response!!.statusCode).isEqualTo(500)
          },
          anyOrNull<TraceContext>(),
          anyOrNull(),
          anyOrNull(),
        )
    }
  }

  @Test
  fun `does not set span status for response status that dont match predefined span statuses`() {
    val filter = fixture.getSut(status = HttpStatus.INSUFFICIENT_STORAGE)

    withMockScopes {
      filter.filter(fixture.exchange, fixture.chain).block()

      verify(fixture.scopes)
        .captureTransaction(
          check { assertThat(it.contexts.trace!!.status).isNull() },
          anyOrNull<TraceContext>(),
          anyOrNull(),
          anyOrNull(),
        )
    }
  }

  @Test
  fun `when sentry trace is not present, transaction does not have parentSpanId set`() {
    val filter = fixture.getSut()

    withMockScopes {
      filter.filter(fixture.exchange, fixture.chain).block()

      verify(fixture.scopes)
        .captureTransaction(
          check { assertThat(it.contexts.trace!!.parentSpanId).isNull() },
          anyOrNull<TraceContext>(),
          anyOrNull(),
          anyOrNull(),
        )
    }
  }

  @Test
  fun `when sentry trace is present, transaction has parentSpanId set`() {
    val parentSpanId = SpanId()
    val filter = fixture.getSut(sentryTraceHeader = "${SentryId()}-$parentSpanId-1")

    withMockScopes {
      filter.filter(fixture.exchange, fixture.chain).block()

      verify(fixture.scopes)
        .captureTransaction(
          check { assertThat(it.contexts.trace!!.parentSpanId).isEqualTo(parentSpanId) },
          anyOrNull<TraceContext>(),
          anyOrNull(),
          anyOrNull(),
        )
    }
  }

  @Test
  fun `when scopes is disabled, components are not invoked`() {
    val filter = fixture.getSut(isEnabled = false)

    withMockScopes {
      filter.filter(fixture.exchange, fixture.chain).block()

      verify(fixture.chain).filter(fixture.exchange)

      verify(fixture.scopes, times(2)).isEnabled
      verifyNoMoreInteractions(fixture.scopes)
    }
  }

  @Test
  fun `sets status to internal server error when chain throws exception`() {
    val filter = fixture.getSut()

    withMockScopes {
      whenever(fixture.chain.filter(any())).thenReturn(Mono.error(RuntimeException("error")))

      try {
        filter.filter(fixture.exchange, fixture.chain).block()
        fail("filter is expected to rethrow exception")
      } catch (_: Exception) {}
      verify(fixture.scopes)
        .captureTransaction(
          check { assertThat(it.status).isEqualTo(SpanStatus.INTERNAL_ERROR) },
          anyOrNull<TraceContext>(),
          anyOrNull(),
          anyOrNull(),
        )
    }
  }

  @Test
  fun `does not track OPTIONS request with traceOptionsRequests=false`() {
    val filter = fixture.getSut(method = HttpMethod.OPTIONS)

    withMockScopes {
      fixture.options.isTraceOptionsRequests = false

      filter.filter(fixture.exchange, fixture.chain).block()

      verify(fixture.chain).filter(fixture.exchange)

      verify(fixture.scopes, times(2)).isEnabled
      verify(fixture.scopes, times(4)).options
      verify(fixture.scopes).continueTrace(anyOrNull(), anyOrNull())
      verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), any<Hint>())
      verify(fixture.scopes).configureScope(any<ScopeCallback>())
      verifyNoMoreInteractions(fixture.scopes)
    }
  }

  @Test
  fun `tracks OPTIONS request with traceOptionsRequests=true`() {
    val filter = fixture.getSut(method = HttpMethod.OPTIONS)

    withMockScopes {
      fixture.options.isTraceOptionsRequests = true

      filter.filter(fixture.exchange, fixture.chain).block()

      verify(fixture.chain).filter(fixture.exchange)

      verify(fixture.scopes)
        .captureTransaction(
          check { assertThat(it.contexts.trace!!.parentSpanId).isNull() },
          anyOrNull<TraceContext>(),
          anyOrNull(),
          anyOrNull(),
        )
    }
  }

  @Test
  fun `tracks POST request with traceOptionsRequests=false`() {
    val filter = fixture.getSut(method = HttpMethod.POST)

    withMockScopes {
      fixture.options.isTraceOptionsRequests = false

      filter.filter(fixture.exchange, fixture.chain).block()

      verify(fixture.chain).filter(fixture.exchange)

      verify(fixture.scopes)
        .captureTransaction(
          check { assertThat(it.contexts.trace!!.parentSpanId).isNull() },
          anyOrNull<TraceContext>(),
          anyOrNull(),
          anyOrNull(),
        )
    }
  }

  @Test
  fun `continues incoming trace even is performance is disabled`() {
    val parentSpanId = SpanId()
    val sentryTraceHeaderString = "2722d9f6ec019ade60c776169d9a8904-$parentSpanId-1"
    val baggageHeaderStrings =
      listOf(
        "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=2722d9f6ec019ade60c776169d9a8904,sentry-transaction=HTTP%20GET"
      )
    fixture.options.tracesSampleRate = null
    val filter =
      fixture.getSut(
        sentryTraceHeader = sentryTraceHeaderString,
        baggageHeaders = baggageHeaderStrings,
      )

    withMockScopes {
      filter.filter(fixture.exchange, fixture.chain).block()

      verify(fixture.chain).filter(fixture.exchange)

      verify(fixture.scopes, never())
        .captureTransaction(
          anyOrNull<SentryTransaction>(),
          anyOrNull<TraceContext>(),
          anyOrNull(),
          anyOrNull(),
        )

      verify(fixture.scopes).continueTrace(eq(sentryTraceHeaderString), eq(baggageHeaderStrings))
    }
  }

  @Test
  fun `does not continue incoming trace is span origin is ignored`() {
    val parentSpanId = SpanId()
    val sentryTraceHeaderString = "2722d9f6ec019ade60c776169d9a8904-$parentSpanId-1"
    val baggageHeaderStrings =
      listOf(
        "sentry-public_key=502f25099c204a2fbf4cb16edc5975d1,sentry-sample_rate=1,sentry-trace_id=2722d9f6ec019ade60c776169d9a8904,sentry-transaction=HTTP%20GET"
      )
    fixture.options.tracesSampleRate = null
    fixture.options.setIgnoredSpanOrigins(listOf("auto.spring_jakarta.webflux"))
    val filter =
      fixture.getSut(
        sentryTraceHeader = sentryTraceHeaderString,
        baggageHeaders = baggageHeaderStrings,
      )

    withMockScopes {
      filter.filter(fixture.exchange, fixture.chain).block()

      verify(fixture.chain).filter(fixture.exchange)

      verify(fixture.scopes, never())
        .captureTransaction(
          anyOrNull<SentryTransaction>(),
          anyOrNull<TraceContext>(),
          anyOrNull(),
          anyOrNull(),
        )

      verify(fixture.scopes, never()).continueTrace(any(), any())
    }
  }
}
