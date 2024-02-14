package io.sentry.metrics

import kotlin.test.Test
import kotlin.test.assertEquals

class MetricHelperTest {

    @Test
    fun sanitizeKey() {
        assertEquals("foo-bar", MetricHelper.sanitizeKey("foo-bar"))
        assertEquals("foo_bar", MetricHelper.sanitizeKey("foo\$\$\$bar"))
        assertEquals("fo_-bar", MetricHelper.sanitizeKey("foö-bar"))
    }

    @Test
    fun sanitizeValue() {
        assertEquals("_\$foo", MetricHelper.sanitizeValue("%\$foo"))
        assertEquals("blah{}", MetricHelper.sanitizeValue("blah{}"))
        assertEquals("sn_wm_n", MetricHelper.sanitizeValue("snöwmän"))
    }

    @Test
    fun getTimeBucketKey() {
        assertEquals(
            10,
            MetricHelper.getTimeBucketKey(10_000)
        )

        assertEquals(
            10,
            MetricHelper.getTimeBucketKey(10_001)
        )

        assertEquals(
            20,
            MetricHelper.getTimeBucketKey(20_000)
        )

        assertEquals(
            20,
            MetricHelper.getTimeBucketKey(29_999)
        )

        assertEquals(
            30,
            MetricHelper.getTimeBucketKey(30_000)
        )
    }
}
