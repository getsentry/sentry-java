package io.sentry.android.core

import io.sentry.ISpan
import io.sentry.NoOpSpan
import io.sentry.NoOpTransaction
import io.sentry.SpanContext
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.Test

class SpanFrameMetricsCollectorTest {

    private class Fixture {
        val options = SentryAndroidOptions()
        val frameMetricsCollector = mock<SentryFrameMetricsCollector>()

        fun getSut(enabled: Boolean = true): SpanFrameMetricsCollector {
            whenever(frameMetricsCollector.startCollection(any())).thenReturn(
                UUID.randomUUID().toString()
            )
            options.frameMetricsCollector = frameMetricsCollector
            options.isEnableFramesTracking = enabled
            options.isEnablePerformanceV2 = enabled

            return SpanFrameMetricsCollector(options)
        }
    }

    private fun createFakeSpan(): ISpan {
        val span = mock<ISpan>()
        val spanContext = SpanContext("op.fake")
        whenever(span.spanContext).thenReturn(spanContext)
        return span
    }

    private val fixture = Fixture()

    @Test
    fun `When AndroidSlowFrozenFrameCollector is initialized, it doesn't register any listener`() {
        fixture.getSut()
        verify(fixture.frameMetricsCollector, never()).startCollection(any())
    }

    @Test
    fun `If disabled but a span is launched, it doesn't register a listener`() {
        val sut = fixture.getSut(enabled = false)

        // when a span is started
        val span = createFakeSpan()
        sut.onSpanStarted(span)

        // then it doesn't register for frame metrics
        verify(fixture.frameMetricsCollector, never()).startCollection(any())

        // nor unregisters when span is finished
        sut.onSpanFinished(span)
        verify(fixture.frameMetricsCollector, never()).stopCollection(any())
    }

    @Test
    fun `No-op txn and spans are ignored`() {
        val sut = fixture.getSut()

        // when fake transactions and spans are provided
        val txn = NoOpTransaction.getInstance()
        val span = NoOpSpan.getInstance()

        sut.onSpanStarted(txn)
        sut.onSpanStarted(span)

        // then it never registers for frame metrics
        verify(fixture.frameMetricsCollector, never()).startCollection(any())
    }

    @Test
    fun `Once a span is launched, it registers for frame metrics`() {
        val sut = fixture.getSut()

        // when a span is started
        val span = createFakeSpan()
        sut.onSpanStarted(span)

        // then it registers for frame metrics
        verify(fixture.frameMetricsCollector).startCollection(any())

        // when the span is finished
        sut.onSpanFinished(span)

        // then it unregisters from frame metrics
        verify(fixture.frameMetricsCollector).stopCollection(any())
    }

    @Test
    fun `If multiple spans are launched, unregister is called after the last span finishes`() {
        val sut = fixture.getSut()

        // when 2 spans are started
        val span0 = createFakeSpan()
        val span1 = createFakeSpan()
        sut.onSpanStarted(span0)
        sut.onSpanStarted(span1)

        // and one finishes, but not the other one
        sut.onSpanFinished(span0)

        // then it doesn't unregister from frame metrics
        verify(fixture.frameMetricsCollector, never()).stopCollection(any())

        // but if the other one finishes
        sut.onSpanFinished(span1)

        // then it unregisters from frame metrics
        verify(fixture.frameMetricsCollector).stopCollection(any())
    }

    @Test
    fun `slow and frozen frames are calculated per span`() {
        val sut = fixture.getSut()

        // when the first span starts
        val span0 = createFakeSpan()
        sut.onSpanStarted(span0)

        // and one fast, two slow frames and one frozen is are recorded
        sut.onFrameMetricCollected(0, 10, 10, 0, false, false, 60.0f)
        sut.onFrameMetricCollected(0, 20, 20, 4, true, false, 60.0f)
        sut.onFrameMetricCollected(0, 20, 20, 4, true, false, 60.0f)
        sut.onFrameMetricCollected(0, 800, 800, 784, true, true, 60.0f)

        // then a second span starts
        val span1 = createFakeSpan()
        sut.onSpanStarted(span1)

        // and another slow frame is recorded
        sut.onFrameMetricCollected(0, 20, 20, 4, true, false, 60.0f)

        sut.onSpanFinished(span0)
        sut.onSpanFinished(span1)

        // then the metrics are set on the spans
        verify(span0).setData("frames.slow", 3)
        verify(span0).setData("frames.frozen", 1)
        verify(span0).setData("frames.total", 5)

        verify(span1).setData("frames.slow", 1)
        verify(span1).setData("frames.frozen", 0)
        verify(span1).setData("frames.total", 1)
    }

    @Test
    fun `clear unregisters from frame collector`() {
        val sut = fixture.getSut()

        // when a span starts
        val span0 = createFakeSpan()
        sut.onSpanStarted(span0)

        // but clear is called
        sut.clear()

        // then it unregisters from frame metrics
        verify(fixture.frameMetricsCollector).stopCollection(any())

        // and no span data should be attached
        verify(span0, never()).setData(any(), any())
    }
}
