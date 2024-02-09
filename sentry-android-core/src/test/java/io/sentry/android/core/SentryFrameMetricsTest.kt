package io.sentry.android.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SentryFrameMetricsTest {
    @Test
    fun addFastFrame() {
        val frameMetrics = SentryFrameMetrics()
        frameMetrics.addNormalFrame(10)
        assertEquals(1, frameMetrics.normalFrameCount)

        frameMetrics.addNormalFrame(10)
        assertEquals(2, frameMetrics.normalFrameCount)
    }

    @Test
    fun addSlowFrame() {
        val frameMetrics = SentryFrameMetrics()
        frameMetrics.addSlowFrame(116, 100)
        assertEquals(1, frameMetrics.slowFrameCount)
        assertEquals(100, frameMetrics.slowFrameDelayNanos)

        frameMetrics.addSlowFrame(116, 100)
        assertEquals(2, frameMetrics.slowFrameCount)
        assertEquals(200, frameMetrics.slowFrameDelayNanos)
    }

    @Test
    fun addFrozenFrame() {
        val frameMetrics = SentryFrameMetrics()
        frameMetrics.addFrozenFrame(1016, 1000)
        assertEquals(1, frameMetrics.frozenFrameCount)
        assertEquals(1000, frameMetrics.frozenFrameDelayNanos)

        frameMetrics.addFrozenFrame(1016, 1000)
        assertEquals(2, frameMetrics.frozenFrameCount)
        assertEquals(2000, frameMetrics.frozenFrameDelayNanos)
    }

    @Test
    fun totalFrameCount() {
        val frameMetrics = SentryFrameMetrics()
        frameMetrics.addNormalFrame(10)
        frameMetrics.addSlowFrame(116, 100)
        frameMetrics.addFrozenFrame(1016, 1000)
        assertEquals(3, frameMetrics.totalFrameCount)
    }

    @Test
    fun duplicate() {
        val frameMetrics = SentryFrameMetrics()
        frameMetrics.addNormalFrame(10)
        frameMetrics.addSlowFrame(116, 100)
        frameMetrics.addFrozenFrame(1016, 1000)

        val dup = frameMetrics.duplicate()
        assertEquals(1, dup.normalFrameCount)
        assertEquals(1, dup.slowFrameCount)
        assertEquals(100, dup.slowFrameDelayNanos)
        assertEquals(1, dup.frozenFrameCount)
        assertEquals(1000, dup.frozenFrameDelayNanos)
        assertEquals(3, dup.totalFrameCount)
    }

    @Test
    fun diffTo() {
        // given one fast, 2 slow and 3 frozen frame
        val frameMetricsA = SentryFrameMetrics()
        frameMetricsA.addNormalFrame(10)
        frameMetricsA.addSlowFrame(116, 100)
        frameMetricsA.addSlowFrame(116, 100)
        frameMetricsA.addFrozenFrame(1016, 1000)
        frameMetricsA.addFrozenFrame(1016, 1000)
        frameMetricsA.addFrozenFrame(1016, 1000)

        // when 1 more slow and frozen frame is happening
        val frameMetricsB = frameMetricsA.duplicate()
        frameMetricsB.addSlowFrame(116, 100)
        frameMetricsB.addFrozenFrame(1016, 1000)

        // then the diff only contains the new data
        val diff = frameMetricsB.diffTo(frameMetricsA)
        assertEquals(1, diff.slowFrameCount)
        assertEquals(100, diff.slowFrameDelayNanos)

        assertEquals(1, diff.frozenFrameCount)
        assertEquals(1000, diff.frozenFrameDelayNanos)

        assertEquals(2, diff.totalFrameCount)
    }

    @Test
    fun clear() {
        val frameMetrics = SentryFrameMetrics().apply {
            addNormalFrame(10)
            addSlowFrame(116, 100)
            addFrozenFrame(1016, 1000)
        }

        frameMetrics.clear()

        assertEquals(0, frameMetrics.normalFrameCount)
        assertEquals(0, frameMetrics.slowFrameCount)
        assertEquals(0, frameMetrics.slowFrameDelayNanos)
        assertEquals(0, frameMetrics.frozenFrameCount)
        assertEquals(0, frameMetrics.frozenFrameDelayNanos)
        assertEquals(0, frameMetrics.totalFrameCount)
    }

    @Test
    fun containsValidData() {
        // when no data is added, it's still valid
        val frameMetrics = SentryFrameMetrics()
        assertTrue(frameMetrics.containsValidData())

        // when a normal frame is added, it's still valid
        frameMetrics.addNormalFrame(10)
        assertTrue(frameMetrics.containsValidData())

        // when frame metrics are negative, it's invalid
        val invalidData = SentryFrameMetrics().diffTo(frameMetrics)
        assertFalse(invalidData.containsValidData())
    }
}
