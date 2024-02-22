package io.sentry.metrics

import kotlin.test.Test
import kotlin.test.assertEquals

class GaugeMetricTest {

    @Test
    fun add() {
        val metric = GaugeMetric(
            "test",
            1.0,
            null,
            null,
            System.currentTimeMillis()
        )
        assertEquals(
            listOf(
                1.0,
                1.0,
                1.0,
                1.0,
                1
            ),
            metric.serialize().toList()
        )

        metric.add(5.0)
        metric.add(4.0)
        metric.add(3.0)
        metric.add(2.0)
        metric.add(1.0)
        assertEquals(
            listOf(
                1.0, // last
                1.0, // min
                5.0, // max
                16.0, // sum
                6 // count
            ),
            metric.serialize().toList()
        )
    }

    @Test
    fun type() {
        val metric = GaugeMetric(
            "test",
            1.0,
            null,
            null,
            System.currentTimeMillis()
        )
        assertEquals(MetricType.Gauge, metric.type)
    }

    @Test
    fun weight() {
        val metric = GaugeMetric(
            "test",
            1.0,
            null,
            null,
            System.currentTimeMillis()
        )
        assertEquals(5, metric.weight)

        // even when values are added, the weight is still 5
        metric.add(2.0)
        assertEquals(5, metric.weight)
    }
}
