package io.sentry.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SampleRateUtilTest {

    @Test
    fun `accepts 0 dot 01 for sample rate`() {
        assertTrue(SampleRateUtils.isValidSampleRate(0.01))
    }

    @Test
    fun `accepts 1 for sample rate`() {
        assertTrue(SampleRateUtils.isValidSampleRate(1.0))
    }

    @Test
    fun `accepts 0 for sample rate`() {
        assertTrue(SampleRateUtils.isValidSampleRate(0.0))
    }

    @Test
    fun `rejects 1 dot 01 for sample rate`() {
        assertFalse(SampleRateUtils.isValidSampleRate(1.01))
    }

    @Test
    fun `rejects negative sample rate`() {
        assertFalse(SampleRateUtils.isValidSampleRate(-0.5))
    }

    @Test
    fun `rejects NaN sample rate`() {
        assertFalse(SampleRateUtils.isValidSampleRate(Double.NaN))
    }

    @Test
    fun `rejects positive infinite sample rate`() {
        assertFalse(SampleRateUtils.isValidSampleRate(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `rejects negative infinite sample rate`() {
        assertFalse(SampleRateUtils.isValidSampleRate(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `accepts 0 for traces sample rate`() {
        assertTrue(SampleRateUtils.isValidTracesSampleRate(0.0))
    }

    @Test
    fun `accepts 1 for traces sample rate`() {
        assertTrue(SampleRateUtils.isValidTracesSampleRate(1.0))
    }

    @Test
    fun `rejects negative traces sample rate`() {
        assertFalse(SampleRateUtils.isValidTracesSampleRate(-0.5))
    }

    @Test
    fun `rejects 1 dot 01 for traces sample rate`() {
        assertFalse(SampleRateUtils.isValidTracesSampleRate(1.01))
    }

    @Test
    fun `rejects NaN traces sample rate`() {
        assertFalse(SampleRateUtils.isValidTracesSampleRate(Double.NaN))
    }

    @Test
    fun `rejects positive infinite traces sample rate`() {
        assertFalse(SampleRateUtils.isValidTracesSampleRate(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `rejects negative infinite traces sample rate`() {
        assertFalse(SampleRateUtils.isValidTracesSampleRate(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `accepts null traces sample rate if told so`() {
        assertTrue(SampleRateUtils.isValidTracesSampleRate(null, true))
    }

    @Test
    fun `rejects null traces sample rate if told so`() {
        assertFalse(SampleRateUtils.isValidTracesSampleRate(null, false))
    }

    @Test
    fun `accepts 0 for profiles sample rate`() {
        assertTrue(SampleRateUtils.isValidProfilesSampleRate(0.0))
    }

    @Test
    fun `accepts 1 for profiles sample rate`() {
        assertTrue(SampleRateUtils.isValidProfilesSampleRate(1.0))
    }

    @Test
    fun `rejects negative profiles sample rate`() {
        assertFalse(SampleRateUtils.isValidProfilesSampleRate(-0.5))
    }

    @Test
    fun `rejects 1 dot 01 for profiles sample rate`() {
        assertFalse(SampleRateUtils.isValidProfilesSampleRate(1.01))
    }

    @Test
    fun `rejects NaN profiles sample rate`() {
        assertFalse(SampleRateUtils.isValidProfilesSampleRate(Double.NaN))
    }

    @Test
    fun `rejects positive infinite profiles sample rate`() {
        assertFalse(SampleRateUtils.isValidProfilesSampleRate(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `rejects negative infinite profiles sample rate`() {
        assertFalse(SampleRateUtils.isValidProfilesSampleRate(Double.NEGATIVE_INFINITY))
    }

    @Test
    fun `accepts null profiles sample rate`() {
        assertTrue(SampleRateUtils.isValidProfilesSampleRate(null))
    }

    @Test
    fun `accepts 0 for continuous profiles sample rate`() {
        assertTrue(SampleRateUtils.isValidContinuousProfilesSampleRate(0.0))
    }

    @Test
    fun `accepts 1 for continuous profiles sample rate`() {
        assertTrue(SampleRateUtils.isValidContinuousProfilesSampleRate(1.0))
    }

    @Test
    fun `accepts null continuous profiles sample rate`() {
        assertTrue(SampleRateUtils.isValidProfilesSampleRate(null))
    }

    @Test
    fun `rejects negative continuous profiles sample rate`() {
        assertFalse(SampleRateUtils.isValidContinuousProfilesSampleRate(-0.5))
    }

    @Test
    fun `rejects 1 dot 01 for continuous profiles sample rate`() {
        assertFalse(SampleRateUtils.isValidContinuousProfilesSampleRate(1.01))
    }

    @Test
    fun `rejects NaN continuous profiles sample rate`() {
        assertFalse(SampleRateUtils.isValidContinuousProfilesSampleRate(Double.NaN))
    }

    @Test
    fun `rejects positive infinite continuous profiles sample rate`() {
        assertFalse(SampleRateUtils.isValidContinuousProfilesSampleRate(Double.POSITIVE_INFINITY))
    }

    @Test
    fun `rejects negative infinite continuous profiles sample rate`() {
        assertFalse(SampleRateUtils.isValidContinuousProfilesSampleRate(Double.NEGATIVE_INFINITY))
    }
}
