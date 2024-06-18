package io.sentry.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocalMetricsAggregatorTest {
    @Test
    fun `metrics are grouped by export key`() {
        val aggregator = LocalMetricsAggregator()

        val type = MetricType.Counter
        val key = "op.count"
        val unit = null
        val tags0 = mapOf(
            "tag" to "value0"
        )

        // when a metric is emitted
        aggregator.add(
            MetricsHelper.getMetricBucketKey(type, key, unit, tags0),
            type,
            key,
            1.0,
            unit,
            tags0
        )

        // and the same metric is emitted with different tags
        val tags1 = mapOf(
            "tag" to "value1"
        )
        aggregator.add(
            MetricsHelper.getMetricBucketKey(type, key, unit, tags1),
            type,
            key,
            1.0,
            unit,
            tags1
        )

        // then the summary contain a single top level group for the metric
        assertEquals(1, aggregator.summaries.size)
        assertNotNull(aggregator.summaries[MetricsHelper.getExportKey(type, key, unit)])

        // and 2 summaries based on the different tags should be present
        val metricSummaries = aggregator.summaries[MetricsHelper.getExportKey(type, key, unit)]!!
        assertEquals(2, metricSummaries.size)
    }

    @Test
    fun `metrics are aggregated`() {
        val aggregator = LocalMetricsAggregator()

        val type = MetricType.Counter
        val key = "op.count"
        val unit = null
        val tags = mapOf(
            "tag0" to "value0"
        )
        val timestamp = 0L

        // when a metric is emitted two times
        aggregator.add(
            MetricsHelper.getMetricBucketKey(type, key, unit, tags),
            type,
            key,
            1.0,
            unit,
            tags
        )

        aggregator.add(
            MetricsHelper.getMetricBucketKey(type, key, unit, tags),
            type,
            key,
            2.0,
            unit,
            tags
        )

        val metric = aggregator.summaries.values.first()[0]
        assertEquals(1.0, metric.min)
        assertEquals(2.0, metric.max)
        assertEquals(3.0, metric.sum)
        assertEquals(2, metric.count)
        assertEquals(tags, metric.tags)
    }
}
