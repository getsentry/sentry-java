package io.sentry.metrics

import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals

class DistributionMetricTest {

    @Test
    fun add() {
        val metric = DistributionMetric(
            "test",
            1.0,
            null,
            null,
            Calendar.getInstance()
        )
        assertEquals(listOf(1.0), metric.values.toList())

        metric.add(1.0)
        metric.add(2.0)
        assertEquals(listOf(1.0, 1.0, 2.0), metric.values.toList())
    }

    @Test
    fun type() {
        val metric = DistributionMetric(
            "test",
            1.0,
            null,
            null,
            Calendar.getInstance()
        )
        assertEquals(MetricType.Distribution, metric.type)
    }

    @Test
    fun weight() {
        val metric = DistributionMetric(
            "test",
            1.0,
            null,
            null,
            Calendar.getInstance()
        )
        assertEquals(1, metric.weight)

        metric.add(2.0)
        assertEquals(2, metric.weight)
    }

    @Test
    fun values() {
        val metric = DistributionMetric(
            "test",
            1.0,
            null,
            null,
            Calendar.getInstance()
        )
        metric.add(2.0)

        val values = metric.values.toList()
        assertEquals(2, values.size)
        assertEquals(listOf(1.0, 2.0), values)
    }
}
