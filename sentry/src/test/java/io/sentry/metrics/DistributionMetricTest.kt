package io.sentry.metrics

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
            System.currentTimeMillis()
        )
        assertEquals(listOf(1.0), metric.serialize().toList())

        metric.add(1.0)
        metric.add(2.0)
        assertEquals(listOf(1.0, 1.0, 2.0), metric.serialize().toList())
    }

    @Test
    fun type() {
        val metric = DistributionMetric(
            "test",
            1.0,
            null,
            null,
            System.currentTimeMillis()
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
            System.currentTimeMillis()
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
            System.currentTimeMillis()
        )
        metric.add(2.0)

        val values = metric.serialize().toList()
        assertEquals(2, values.size)
        assertEquals(listOf(1.0, 2.0), values)
    }
}
