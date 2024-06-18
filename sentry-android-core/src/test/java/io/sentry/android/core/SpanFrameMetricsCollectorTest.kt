package io.sentry.android.core

import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.NoOpSpan
import io.sentry.NoOpTransaction
import io.sentry.SentryNanotimeDate
import io.sentry.SpanContext
import io.sentry.android.core.internal.util.SentryFrameMetricsCollector
import io.sentry.protocol.MeasurementValue
import org.mockito.AdditionalMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class SpanFrameMetricsCollectorTest {

    private class Fixture {
        val options = SentryAndroidOptions()
        val frameMetricsCollector = mock<SentryFrameMetricsCollector>()
        var timeNanos = 0L
        var lastKnownChoreographerFrameTimeNanos = 0L

        fun getSut(enabled: Boolean = true): SpanFrameMetricsCollector {
            whenever(frameMetricsCollector.startCollection(any())).thenReturn(
                UUID.randomUUID().toString()
            )
            whenever(frameMetricsCollector.getLastKnownFrameStartTimeNanos()).thenAnswer {
                return@thenAnswer lastKnownChoreographerFrameTimeNanos
            }
            options.frameMetricsCollector = frameMetricsCollector
            options.isEnableFramesTracking = enabled
            options.isEnablePerformanceV2 = enabled
            options.dateProvider = SentryAndroidDateProvider()

            return SpanFrameMetricsCollector(options, frameMetricsCollector)
        }
    }

    private fun createFakeSpan(
        startTimeStampNanos: Long = 1000,
        endTimeStampNanos: Long? = 2000
    ): ISpan {
        val span = mock<ISpan>()
        val spanContext = SpanContext("op.fake")
        whenever(span.spanContext).thenReturn(spanContext)
        whenever(span.startDate).thenReturn(
            SentryNanotimeDate(
                Date(),
                startTimeStampNanos
            )
        )
        whenever(span.finishDate).thenReturn(
            if (endTimeStampNanos != null) {
                SentryNanotimeDate(
                    Date(),
                    endTimeStampNanos
                )
            } else {
                null
            }
        )
        return span
    }

    private fun createFakeTxn(
        startTimeStampNanos: Long = 1000,
        endTimeStampNanos: Long? = 2000
    ): ITransaction {
        val span = mock<ITransaction>()
        val spanContext = SpanContext("op.fake")
        whenever(span.spanContext).thenReturn(spanContext)
        whenever(span.startDate).thenReturn(
            SentryNanotimeDate(
                Date(),
                startTimeStampNanos
            )
        )
        whenever(span.finishDate).thenReturn(
            if (endTimeStampNanos != null) {
                SentryNanotimeDate(
                    Date(),
                    endTimeStampNanos
                )
            } else {
                null
            }
        )
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
        fixture.timeNanos = 1000
        val span = createFakeSpan(1000, 2000)
        sut.onSpanStarted(span)

        // then it registers for frame metrics
        verify(fixture.frameMetricsCollector).startCollection(any())

        // when the span is finished
        fixture.timeNanos = 2000
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
        fixture.timeNanos = 0
        val span0 = createFakeSpan(0, 800)
        sut.onSpanStarted(span0)

        // and one fast, two slow frames and one frozen is are recorded
        sut.onFrameMetricCollected(0, 10, 10, 0, false, false, 60.0f)
        sut.onFrameMetricCollected(16, 48, 32, 16, true, false, 60.0f)
        sut.onFrameMetricCollected(60, 92, 32, 16, true, false, 60.0f)
        sut.onFrameMetricCollected(100, 800, 800, 784, true, true, 60.0f)

        // then a second span starts
        fixture.timeNanos = 800
        sut.onSpanFinished(span0)

        fixture.timeNanos = 820
        val span1 = createFakeSpan(820, 840)
        sut.onSpanStarted(span1)

        // and another slow frame is recorded
        fixture.timeNanos = 840
        sut.onFrameMetricCollected(820, 840, 20, 4, true, false, 60.0f)
        sut.onSpanFinished(span1)

        // then the metrics are set on the spans
        verify(span0).setData("frames.total", 4)
        verify(span0).setData("frames.slow", 2)
        verify(span0).setData("frames.frozen", 1)

        verify(span1).setData("frames.total", 1)
        verify(span1).setData("frames.slow", 1)
        verify(span1).setData("frames.frozen", 0)
    }

    @Test
    fun `slow and frozen frames are calculated even when spans overlap`() {
        val sut = fixture.getSut()

        // when 4 spans are running at the same time
        fixture.timeNanos = 0
        val span0 = createFakeSpan(0, 2000)
        val span1 = createFakeSpan(200, 2200)
        val span2 = createFakeSpan(400, 2400)
        val span3 = createFakeSpan(600, 2600)

        fixture.timeNanos = 0
        sut.onSpanStarted(span0)

        fixture.timeNanos = 200
        sut.onSpanStarted(span1)

        fixture.timeNanos = 400
        sut.onSpanStarted(span2)

        fixture.timeNanos = 600
        sut.onSpanStarted(span3)

        // and one frozen frame is captured right when all spans are running
        fixture.timeNanos = 620
        sut.onFrameMetricCollected(620, 1620, 1000, 984, true, true, 60.0f)

        fixture.timeNanos = 2000
        fixture.lastKnownChoreographerFrameTimeNanos = 2000
        sut.onSpanFinished(span0)

        fixture.timeNanos = 2200
        fixture.lastKnownChoreographerFrameTimeNanos = 2200
        sut.onSpanFinished(span1)

        fixture.timeNanos = 2400
        fixture.lastKnownChoreographerFrameTimeNanos = 2400
        sut.onSpanFinished(span2)

        fixture.timeNanos = 2600
        fixture.lastKnownChoreographerFrameTimeNanos = 2600
        sut.onSpanFinished(span3)

        // every span should contain the frozen frame information
        verify(span0).setData("frames.frozen", 1)
        verify(span1).setData("frames.frozen", 1)
        verify(span2).setData("frames.frozen", 1)
        verify(span3).setData("frames.frozen", 1)
    }

    @Test
    fun `when a span finishes which was never started no-op`() {
        val sut = fixture.getSut()

        // when 4 spans are running at the same time
        fixture.timeNanos = 0
        val span = createFakeSpan(0, 2000)

        sut.onFrameMetricCollected(0, 100, 1000, 984, true, true, 60.0f)

        sut.onSpanFinished(span)
        verify(span, never()).setData(any(), any())
    }

    @Test
    fun `measurements are not set on spans`() {
        val sut = fixture.getSut()

        fixture.timeNanos = 900
        val span = createFakeSpan(900, 1110)

        sut.onSpanStarted(span)
        sut.onFrameMetricCollected(1000, 1010, 10, 0, false, false, 60.0f)

        fixture.timeNanos = 1020
        sut.onSpanFinished(span)

        // then the metrics are set on the spans
        verify(span).setData("frames.total", 1)
        verify(span, never()).setMeasurement(MeasurementValue.KEY_FRAMES_TOTAL, 1)
    }

    @Test
    fun `measurements are set on transactions`() {
        val sut = fixture.getSut()

        fixture.timeNanos = 900
        val span = createFakeTxn(900, 1110)

        sut.onSpanStarted(span)
        sut.onFrameMetricCollected(1000, 1010, 10, 0, false, false, 60.0f)

        fixture.timeNanos = 1020
        sut.onSpanFinished(span)

        // then the metrics are set on the spans
        verify(span).setData("frames.total", 1)
        verify(span).setMeasurement(MeasurementValue.KEY_FRAMES_TOTAL, 1)
    }

    @Test
    fun `when no frame data is collected the total count is interpolated`() {
        val sut = fixture.getSut()

        // given a span which lasts for 1 second
        fixture.timeNanos = TimeUnit.SECONDS.toNanos(1)
        val span = createFakeSpan(
            TimeUnit.SECONDS.toNanos(1),
            TimeUnit.SECONDS.toNanos(2)
        )

        sut.onSpanStarted(span)
        // but no frames are drawn

        // and the span finishes
        // and the choreographer reports a recent update
        fixture.lastKnownChoreographerFrameTimeNanos = TimeUnit.SECONDS.toNanos(2)
        fixture.timeNanos = TimeUnit.SECONDS.toNanos(2)
        sut.onSpanFinished(span)

        // then still 60 frames should be reported (1 second at 60fps)
        verify(span).setData("frames.total", 60)
        verify(span).setData("frames.slow", 0)
        verify(span).setData("frames.frozen", 0)
    }

    @Test
    fun `when no frame data is collected the total count is interpolated and frame delay is added`() {
        val sut = fixture.getSut()

        // given a span which lasts for 2 seconds
        fixture.timeNanos = TimeUnit.SECONDS.toNanos(1)
        val span = createFakeSpan(
            TimeUnit.SECONDS.toNanos(1),
            TimeUnit.SECONDS.toNanos(3)
        )

        sut.onSpanStarted(span)
        // but no frames are drawn

        // and the span finishes
        // but the choreographer has no update for the last second
        fixture.lastKnownChoreographerFrameTimeNanos = TimeUnit.SECONDS.toNanos(2)
        fixture.timeNanos = TimeUnit.SECONDS.toNanos(3)
        sut.onSpanFinished(span)

        // then
        // still 60 fps should be reported for 1 seconds
        // and one frame with frame delay should be reported (1s - 16ms)
        verify(span).setData("frames.total", 61)
        verify(span).setData("frames.slow", 0)
        verify(span).setData("frames.frozen", 1)
        verify(span).setData(eq("frames.delay"), AdditionalMatchers.eq(0.983333334, 0.01))
    }

    @Test
    fun `when frame data is only partially collected the total count is still interpolated`() {
        val sut = fixture.getSut()

        // given a span which lasts for 1 second
        val span = createFakeSpan(
            startTimeStampNanos = TimeUnit.SECONDS.toNanos(1),
            endTimeStampNanos = TimeUnit.SECONDS.toNanos(2)
        )

        fixture.timeNanos = TimeUnit.SECONDS.toNanos(1)
        sut.onSpanStarted(span)

        // when one frozen frame is recorded
        sut.onFrameMetricCollected(
            TimeUnit.MILLISECONDS.toNanos(1000),
            TimeUnit.MILLISECONDS.toNanos(1800),
            TimeUnit.MILLISECONDS.toNanos(800),
            TimeUnit.MILLISECONDS.toNanos(800 - 16),
            false,
            true,
            60.0f
        )

        // and the span finishes
        fixture.timeNanos = TimeUnit.SECONDS.toNanos(2)
        sut.onSpanFinished(span)

        // then 13 frames should be reported
        // 1 frame at 800ms + 1 frames at 16ms = 992ms
        verify(span).setData("frames.total", 2)
        verify(span).setData("frames.slow", 0)
        verify(span).setData("frames.frozen", 2)
    }

    @Test
    fun `when frame data is only partially collected the total count is not interpolated in case the span didn't finish`() {
        val sut = fixture.getSut()

        // given a span has no end date
        fixture.timeNanos = TimeUnit.SECONDS.toNanos(1)
        val span = createFakeSpan(
            startTimeStampNanos = TimeUnit.SECONDS.toNanos(1),
            endTimeStampNanos = null
        )

        sut.onSpanStarted(span)

        // when one frozen frame is recorded
        sut.onFrameMetricCollected(
            TimeUnit.MILLISECONDS.toNanos(1000),
            TimeUnit.MILLISECONDS.toNanos(1800),
            TimeUnit.MILLISECONDS.toNanos(800),
            TimeUnit.MILLISECONDS.toNanos(800 - 16),
            false,
            true,
            60.0f
        )

        // and the span finishes without a finish date
        fixture.timeNanos = TimeUnit.MILLISECONDS.toNanos(1800)
        sut.onSpanFinished(span)

        // then no frame stats should be reported
        verify(span, never()).setData(any(), any())
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

    @Test
    fun `SentryNanoDate diff does nano precision`() {
        // having this in here, as SpanFrameMetricsCollector relies on this behavior
        val a = SentryNanotimeDate(Date(1234), 567)
        val b = SentryNanotimeDate(Date(1234), 0)

        assertEquals(567, a.diff(b))
    }
}
