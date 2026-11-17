package io.sentry.opentelemetry

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.sentry.Scope
import io.sentry.Scopes
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlin.test.AfterTest
import kotlin.test.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SentryContextWrapperTest {

  @AfterTest
  fun tearDown() {
    SentryWeakSpanStorage.getInstance().clear()
    Sentry.close()
  }

  @Test
  fun `ensure noop scopes are not stored on Context`() {
    val context = Context.root()
    assertNull(context.get(SENTRY_SCOPES_KEY))
    assertTrue(Sentry.getCurrentScopes().isNoOp)
    val wrappedContext = SentryContextWrapper.wrap(context)
    assertNull(wrappedContext.get(SENTRY_SCOPES_KEY))
  }

  @Test
  fun `uses span scopes when context scopes are the live weak parent`() {
    val options = SentryOptions().apply { dsn = "https://key@sentry.io/proj" }
    val rootScopes = Scopes(Scope(options), Scope(options), Scope(options), "root")
    val contextScopes = rootScopes.forkedCurrentScope("context")
    contextScopes.scope.setTag("source", "context")
    val spanScopes = contextScopes.forkedCurrentScope("span")
    spanScopes.scope.setTag("source", "span")

    val spanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val span = Span.wrap(spanContext)
    val sentrySpan = mock<IOtelSpanWrapper>()
    whenever(sentrySpan.scopes).thenReturn(spanScopes)
    SentryWeakSpanStorage.getInstance().storeSentrySpan(spanContext, sentrySpan)
    val context = Context.root().with(SENTRY_SCOPES_KEY, contextScopes).with(span)

    System.gc()
    val wrappedContext = SentryContextWrapper.wrap(context)
    val selectedScopes = wrappedContext.get(SENTRY_SCOPES_KEY)

    assertThat(selectedScopes).isNotNull()
    assertThat(selectedScopes!!.scope.tags["source"]).isEqualTo("span")
  }
}
