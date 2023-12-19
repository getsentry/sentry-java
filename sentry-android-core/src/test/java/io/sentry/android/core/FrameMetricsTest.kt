package io.sentry.android.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameMetricsTest {
    @Test
    fun addFastFrame() {
        val frameMetrics = FrameMetrics()
        frameMetrics.addFastFrame(10)
        assertEquals(1, frameMetrics.fastFrameCount)

        frameMetrics.addFastFrame(10)
        assertEquals(2, frameMetrics.fastFrameCount)
    }

    @Test
    fun addSlowFrame() {
        val frameMetrics = FrameMetrics()
        frameMetrics.addSlowFrame(100)
        assertEquals(1, frameMetrics.slowFrameCount)
        assertEquals(100, frameMetrics.slowFrameDuration)

        frameMetrics.addSlowFrame(100)
        assertEquals(2, frameMetrics.slowFrameCount)
        assertEquals(200, frameMetrics.slowFrameDuration)
    }

    @Test
    fun addFrozenFrame() {
        val frameMetrics = FrameMetrics()
        frameMetrics.addFrozenFrame(1000)
        assertEquals(1, frameMetrics.frozenFrameCount)
        assertEquals(1000, frameMetrics.frozenFrameDuration)

        frameMetrics.addFrozenFrame(1000)
        assertEquals(2, frameMetrics.frozenFrameCount)
        assertEquals(2000, frameMetrics.frozenFrameDuration)
    }

    @Test
    fun totalFrameCount() {
        val frameMetrics = FrameMetrics()
        frameMetrics.addFastFrame(10)
        frameMetrics.addSlowFrame(100)
        frameMetrics.addFrozenFrame(1000)
        assertEquals(3, frameMetrics.totalFrameCount)
    }

    @Test
    fun duplicate() {
        val frameMetrics = FrameMetrics()
        frameMetrics.addFastFrame(10)
        frameMetrics.addSlowFrame(100)
        frameMetrics.addFrozenFrame(1000)

        val dup = frameMetrics.duplicate()
        assertEquals(1, dup.fastFrameCount)
        assertEquals(1, dup.slowFrameCount)
        assertEquals(100, dup.slowFrameDuration)
        assertEquals(1, dup.frozenFrameCount)
        assertEquals(1000, dup.frozenFrameDuration)
        assertEquals(3, dup.totalFrameCount)
    }

    @Test
    fun diffTo() {
        // given one fast, 2 slow and 3 frozen frame
        val frameMetricsA = FrameMetrics()
        frameMetricsA.addFastFrame(10)
        frameMetricsA.addSlowFrame(100)
        frameMetricsA.addSlowFrame(100)
        frameMetricsA.addFrozenFrame(1000)
        frameMetricsA.addFrozenFrame(1000)
        frameMetricsA.addFrozenFrame(1000)

        // when 1 more slow and frozen frame is happening
        val frameMetricsB = frameMetricsA.duplicate()
        frameMetricsB.addSlowFrame(100)
        frameMetricsB.addFrozenFrame(1000)

        // then the diff only contains the new data
        val diff = frameMetricsB.diffTo(frameMetricsA)
        assertEquals(1, diff.slowFrameCount)
        assertEquals(100, diff.slowFrameDuration)

        assertEquals(1, diff.frozenFrameCount)
        assertEquals(1000, diff.frozenFrameDuration)

        assertEquals(2, diff.totalFrameCount)
    }

    @Test
    fun clear() {
        val frameMetrics = FrameMetrics().apply {
            addFastFrame(10)
            addSlowFrame(100)
            addFrozenFrame(1000)
        }

        frameMetrics.clear()

        assertEquals(0, frameMetrics.fastFrameCount)
        assertEquals(0, frameMetrics.slowFrameCount)
        assertEquals(0, frameMetrics.slowFrameDuration)
        assertEquals(0, frameMetrics.frozenFrameCount)
        assertEquals(0, frameMetrics.frozenFrameDuration)
        assertEquals(0, frameMetrics.totalFrameCount)
    }

    @Test
    fun containsValidData() {
        val frameMetrics = FrameMetrics()
        assertFalse(frameMetrics.containsValidData())

        frameMetrics.addFastFrame(10)
        assertTrue(frameMetrics.containsValidData())

        val invalidData = FrameMetrics().diffTo(frameMetrics)
        assertFalse(invalidData.containsValidData())
    }
}
