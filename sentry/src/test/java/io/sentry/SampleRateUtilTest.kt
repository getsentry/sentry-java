package io.sentry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SampleRateUtilTest {

    @Test
    fun `accepts 0 dot 01 for sample rate`() {
        assertTrue(SampleRateUtil.isValidSampleRate(0.01))
    }

    @Test
    fun `accepts 1 for sample rate`() {
        assertTrue(SampleRateUtil.isValidSampleRate(1.0))
    }

    @Test
    fun `rejects 0 for sample rate`() {
        assertFalse(SampleRateUtil.isValidSampleRate(0.0))
    }

    @Test
    fun `rejects 1 dot 01 for sample rate`() {
        assertFalse(SampleRateUtil.isValidSampleRate(1.01))
    }

    @Test
    fun `rejects negative sample rate`() {
        assertFalse(SampleRateUtil.isValidSampleRate(-0.5))
    }

    @Test
    fun `rejects NaN sample rate`() {
        assertFalse(SampleRateUtil.isValidSampleRate(Double.NaN))
    }

    @Test
    fun `rejects positive infinite sample rate`() {
        assertFalse(SampleRateUtil.isValidSampleRate(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `rejects negative infinite sample rate`() {
        assertFalse(SampleRateUtil.isValidSampleRate(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `accepts null sample rate if told so`() {
        assertTrue(SampleRateUtil.isValidSampleRate(null, true))
    }

    @Test
    fun `rejects null sample rate if told so`() {
        assertFalse(SampleRateUtil.isValidSampleRate(null, false))
    }

    @Test
    fun `accepts 0 for traces sample rate`() {
        assertTrue(SampleRateUtil.isValidTracesSampleRate(0.0))
    }

    @Test
    fun `accepts 1 for traces sample rate`() {
        assertTrue(SampleRateUtil.isValidTracesSampleRate(1.0))
    }

    @Test
    fun `rejects negative traces sample rate`() {
        assertFalse(SampleRateUtil.isValidTracesSampleRate(-0.5))
    }

    @Test
    fun `rejects 1 dot 01 for traces sample rate`() {
        assertFalse(SampleRateUtil.isValidTracesSampleRate(1.01))
    }

    @Test
    fun `rejects NaN traces sample rate`() {
        assertFalse(SampleRateUtil.isValidTracesSampleRate(Double.NaN))
    }

    @Test
    fun `rejects positive infinite traces sample rate`() {
        assertFalse(SampleRateUtil.isValidTracesSampleRate(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `rejects negative infinite traces sample rate`() {
        assertFalse(SampleRateUtil.isValidTracesSampleRate(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `accepts null traces sample rate if told so`() {
        assertTrue(SampleRateUtil.isValidTracesSampleRate(null, true))
    }

    @Test
    fun `rejects null traces sample rate if told so`() {
        assertFalse(SampleRateUtil.isValidTracesSampleRate(null, false))
    }
}
