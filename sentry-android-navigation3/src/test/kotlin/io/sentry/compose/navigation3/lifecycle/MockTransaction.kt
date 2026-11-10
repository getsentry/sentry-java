package io.sentry.compose.navigation3.lifecycle

import io.sentry.ITransaction
import io.sentry.SpanContext
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Returns a transaction mock with a span context, which `Scope.setTransaction` passes to scope
 * observers.
 */
internal fun mockTransaction(): ITransaction =
  mock<ITransaction>().also { whenever(it.spanContext).thenReturn(SpanContext("navigation")) }
