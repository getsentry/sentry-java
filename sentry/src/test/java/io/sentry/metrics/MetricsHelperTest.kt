package io.sentry.metrics

import kotlin.test.Test
import kotlin.test.assertEquals

class MetricsHelperTest {

    @Test
    fun sanitizeKey() {
        assertEquals("foo-bar", MetricsHelper.sanitizeKey("foo-bar"))
        assertEquals("foo_bar", MetricsHelper.sanitizeKey("foo\$\$\$bar"))
        assertEquals("fo_-bar", MetricsHelper.sanitizeKey("foö-bar"))
    }

    @Test
    fun sanitizeValue() {
        assertEquals("_\$foo", MetricsHelper.sanitizeValue("%\$foo"))
        assertEquals("blah{}", MetricsHelper.sanitizeValue("blah{}"))
        assertEquals("sn_wm_n", MetricsHelper.sanitizeValue("snöwmän"))
    }

    @Test
    fun getTimeBucketKey() {
        assertEquals(
            10,
            MetricsHelper.getTimeBucketKey(10_000)
        )

        assertEquals(
            10,
            MetricsHelper.getTimeBucketKey(10_001)
        )

        assertEquals(
            20,
            MetricsHelper.getTimeBucketKey(20_000)
        )

        assertEquals(
            20,
            MetricsHelper.getTimeBucketKey(29_999)
        )

        assertEquals(
            30,
            MetricsHelper.getTimeBucketKey(30_000)
        )
    }

    @Test
    fun sanitizeUnit() {
        val items = listOf(
            "Test123_." to "Test123_.",
            "test{value}" to "test_value_",
            "test-value" to "test_value"
        )

        for (item in items) {
            assertEquals(item.second, MetricsHelper.sanitizeUnit(item.first))
        }
    }
}
