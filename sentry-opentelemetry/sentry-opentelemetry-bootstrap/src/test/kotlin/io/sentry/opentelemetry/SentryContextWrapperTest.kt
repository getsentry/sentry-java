package io.sentry.opentelemetry

import io.opentelemetry.context.Context
import io.sentry.Sentry
import io.sentry.opentelemetry.SentryOtelKeys.SENTRY_SCOPES_KEY
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlin.test.Test

class SentryContextWrapperTest {

  @Test
  fun `x`() {
    val context = Context.root()
    assertNull(context.get(SENTRY_SCOPES_KEY))
    assertTrue(Sentry.getCurrentScopes().isNoOp)
    val wrappedContext = SentryContextWrapper.wrap(context)
    assertNull(wrappedContext.get(SENTRY_SCOPES_KEY))
  }
}
