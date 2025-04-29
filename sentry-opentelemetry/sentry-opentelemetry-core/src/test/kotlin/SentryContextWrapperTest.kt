package io.sentry.opentelemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import io.sentry.Sentry
import io.sentry.SentryNanotimeDate
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SentryContextWrapperTest {

    val spanStorage = SentryWeakSpanStorage.getInstance()

    @BeforeTest
    fun setup() {
        spanStorage.clear()
    }

    @AfterTest
    fun cleanup() {
        spanStorage.clear()
        Sentry.close()
    }

    @Test
    fun `returns global hub span if no transaction is available`() {
        Sentry.init {
            it.dsn = "https://key@sentry.io/proj"
            it.isGlobalHubMode = true
        }

        val c = SentryContextWrapper.wrap(Context.root())
        val returnedSpan = Span.fromContextOrNull(c)
        assertTrue(returnedSpan is SentryOtelGlobalHubModeSpan)
    }

    @Test
    fun `returns available transaction`() {
        Sentry.init {
            it.dsn = "https://key@sentry.io/proj"
            it.isGlobalHubMode = true
        }

        val otelSpan = createOtelSpan()
        val sentrySpan = createSentrySpan(otelSpan)

        spanStorage.storeSentrySpan(otelSpan.spanContext, sentrySpan)

        val c = SentryContextWrapper.wrap(Context.root())
        val returnedSpan = Span.fromContextOrNull(c)
        assertSame(otelSpan, returnedSpan)
    }

    @Test
    fun `returns available transaction if span in context is invalid`() {
        Sentry.init {
            it.dsn = "https://key@sentry.io/proj"
            it.isGlobalHubMode = true
        }

        val otelSpan = createOtelSpan()
        val sentrySpan = createSentrySpan(otelSpan)

        spanStorage.storeSentrySpan(otelSpan.spanContext, sentrySpan)

        val nonWrappedContext = Context.root()
        val wrappedContext = SentryContextWrapper.wrap(Span.getInvalid().storeInContext(nonWrappedContext))
        val returnedSpan = Span.fromContextOrNull(wrappedContext)
        assertSame(otelSpan, returnedSpan)
    }

    @Test
    fun `returns span from context if valid`() {
        Sentry.init {
            it.dsn = "https://key@sentry.io/proj"
            it.isGlobalHubMode = true
        }

        val otelSpan = createOtelSpan()
        val otelSpanInContext = createOtelSpan()
        val sentrySpan = createSentrySpan(otelSpan)

        spanStorage.storeSentrySpan(otelSpan.spanContext, sentrySpan)

        val nonWrappedContext = Context.root()
        val wrappedContext = SentryContextWrapper.wrap(otelSpanInContext.storeInContext(nonWrappedContext))
        val returnedSpan = Span.fromContextOrNull(wrappedContext)
        assertSame(otelSpanInContext, returnedSpan)
    }

    @Test
    fun `returns null if transaction is available but globalHubMode is false`() {
        Sentry.init {
            it.dsn = "https://key@sentry.io/proj"
            it.isGlobalHubMode = false
        }

        val otelSpan = createOtelSpan()
        val sentrySpan = createSentrySpan(otelSpan)

        spanStorage.storeSentrySpan(otelSpan.spanContext, sentrySpan)

        val c = SentryContextWrapper.wrap(Context.root())
        val returnedSpan = Span.fromContextOrNull(c)
        assertNull(returnedSpan)
    }

    @Test
    fun `returns null if available transaction is already finished`() {
        Sentry.init {
            it.dsn = "https://key@sentry.io/proj"
            it.isGlobalHubMode = true
        }

        val otelSpan = createOtelSpan()
        val sentrySpan = createSentrySpan(otelSpan)

        spanStorage.storeSentrySpan(otelSpan.spanContext, sentrySpan)
        sentrySpan.finish()

        val c = SentryContextWrapper.wrap(Context.root())
        val returnedSpan = Span.fromContextOrNull(c)
        assertSame(otelSpan, returnedSpan)
    }

    private fun createSentrySpan(otelSpan: ReadWriteSpan): OtelSpanWrapper {
        val scopes = Sentry.getCurrentScopes()
        return OtelSpanWrapper(otelSpan, scopes, SentryNanotimeDate(), null, null, null, null)
    }

    private fun createOtelSpan(): ReadWriteSpan {
        val otelSpanContext = SpanContext.create(
            "f9118105af4a2d42b4124532cd1065ff",
            "424cffc8f94feeee",
            TraceFlags.getSampled(),
            TraceState.getDefault()
        )
        val otelSpan = mock<ReadWriteSpan>()
        whenever(otelSpan.spanContext).thenReturn(otelSpanContext)
        whenever(otelSpan.name).thenReturn("some-name")

        val spanData = mock<SpanData>()
        whenever(spanData.status).thenReturn(StatusData.ok())
        whenever(otelSpan.toSpanData()).thenReturn(spanData)

        whenever(otelSpan.storeInContext(any())).thenCallRealMethod()

        return otelSpan
    }
}
