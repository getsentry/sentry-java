package io.sentry.metrics

import kotlin.test.Test
import kotlin.test.assertEquals

class CounterMetricTest {

    @Test
    fun add() {
        val metric = CounterMetric(
            "test",
            1.0,
            null,
            mapOf(
                "tag1" to "value1",
                "tag2" to "value2"
            ),
            System.currentTimeMillis()
        )
        assertEquals(1.0, metric.value)

        metric.add(2.0)
        assertEquals(3.0, metric.value)

        // TODO should we allow negative values?
        // TODO should we do any bounds checks?
        metric.add(-3.0)
        assertEquals(0.0, metric.value)
    }

    @Test
    fun type() {
        val metric = CounterMetric(
            "test",
            1.0,
            null,
            mapOf(
                "tag1" to "value1",
                "tag2" to "value2"
            ),
            System.currentTimeMillis()
        )
        assertEquals(MetricType.Counter, metric.type)
    }

    @Test
    fun weight() {
        val metric = CounterMetric(
            "test",
            1.0,
            null,
            mapOf(
                "tag1" to "value1",
                "tag2" to "value2"
            ),
            System.currentTimeMillis()
        )
        assertEquals(1, metric.weight)
    }

    @Test
    fun values() {
        val metric = CounterMetric(
            "test",
            1.0,
            null,
            mapOf(
                "tag1" to "value1",
                "tag2" to "value2"
            ),
            System.currentTimeMillis()
        )

        val values0 = metric.serialize().toList()
        assertEquals(1, values0.size)
        assertEquals(1.0, values0[0] as Double)

        metric.add(1.0)
        val values1 = metric.serialize().toList()
        assertEquals(1, values1.size)
        assertEquals(2.0, values1[0] as Double)
    }
}
