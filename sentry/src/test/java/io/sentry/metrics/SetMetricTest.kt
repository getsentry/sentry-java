package io.sentry.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetMetricTest {

    @Test
    fun add() {
        val metric = SetMetric(
            "test",
            null,
            null,
            System.currentTimeMillis()
        )
        assertTrue(metric.values.toList().isEmpty())

        metric.add(1.0)
        metric.add(2.0)
        metric.add(3.0)

        assertEquals(3, metric.values.toList().size)

        // when an already existing item is added
        // size stays the same
        metric.add(3.0)
        assertEquals(3, metric.values.toList().size)
    }

    @Test
    fun type() {
        val metric = SetMetric(
            "test",
            null,
            null,
            System.currentTimeMillis()
        )
        assertEquals(MetricType.Set, metric.type)
    }

    @Test
    fun weight() {
        val metric = SetMetric(
            "test",
            null,
            null,
            System.currentTimeMillis()
        )
        assertEquals(0, metric.weight)

        metric.add(1.0)
        metric.add(2.0)
        metric.add(3.0)
        metric.add(3.0)

        // weight should be the number of distinct items
        assertEquals(3, metric.weight)
    }
}
