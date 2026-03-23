package io.sentry.opentelemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.sentry.Sentry
import io.sentry.opentelemetry.SentryOtelKeys.SENTRY_BAGGAGE_KEY
import io.sentry.opentelemetry.SentryOtelKeys.SENTRY_TRACE_KEY
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SentryPropagatorTest {

  @BeforeTest
  fun setup() {
    Sentry.init("https://key@sentry.io/proj")
  }

  @Suppress("DEPRECATION")
  @Test
  fun `ignores incoming headers when strict continuation rejects org id`() {
    Sentry.init { options ->
      options.dsn = "https://key@o2.ingest.sentry.io/123"
      options.isStrictTraceContinuation = true
    }

    val propagator = SentryPropagator()
    val carrier: Map<String, String> =
      mapOf(
        "sentry-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
        "baggage" to "sentry-trace_id=f9118105af4a2d42b4124532cd1065ff,sentry-org_id=1",
      )

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    assertFalse(Span.fromContext(newContext).spanContext.isValid)
    assertNull(newContext.get(SENTRY_TRACE_KEY))
    assertNull(newContext.get(SENTRY_BAGGAGE_KEY))
  }
}
