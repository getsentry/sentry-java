package io.sentry.protocol

import io.sentry.IScopes
import io.sentry.SentryLongDate
import io.sentry.SentryTracer
import io.sentry.Span
import io.sentry.SpanOptions
import io.sentry.TransactionContext
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SentrySpanTest {
    @Test
    fun `end timestamps is kept null if not provided`() {
        // when a span with a start timestamp is generated
        val span =
            Span(
                TransactionContext("name", "op"),
                mock<SentryTracer>(),
                mock<IScopes>(),
                SpanOptions().also { it.startTimestamp = SentryLongDate(1000000) },
            )

        val sentrySpan = SentrySpan(span)

        // then the start timestamp should be correctly set
        assertEquals(0.001, sentrySpan.startTimestamp)

        // but the end time should remain untouched
        assertNull(sentrySpan.timestamp)
    }
}
