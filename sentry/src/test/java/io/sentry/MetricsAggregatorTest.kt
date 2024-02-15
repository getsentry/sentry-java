package io.sentry

import io.sentry.metrics.IMetricsHub
import io.sentry.metrics.MetricsHelper
import io.sentry.metrics.MetricsHelperTest
import io.sentry.test.DeferredExecutorService
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetricsAggregatorTest {

    private class Fixture {
        val options = SentryOptions()
        val hub = mock<IMetricsHub>()
        var currentTimeMillis: Long = 0
        var executorService = DeferredExecutorService()

        fun getSut(): MetricsAggregator {
            return MetricsAggregator(hub, options, executorService).also {
                it.setTimeProvider {
                    currentTimeMillis
                }
            }
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun setup() {
        MetricsHelper.setFlushShiftMs(0)
    }

    @Test
    fun `flush is a no-op when there's nothing to flush`() {
        val aggregator = fixture.getSut()

        // when no metrics are collected

        // then flush does nothing
        aggregator.flush(false)

        verify(fixture.hub, never()).captureMetrics(any())
    }

    @Test
    fun `flush performs a flush when needed`() {
        val aggregator = fixture.getSut()

        // when a metric is emitted
        fixture.currentTimeMillis = 20_000
        aggregator.increment("key", 1.0, null, null, 20_001, 1)

        // then flush does nothing because there's no data inside the flush interval
        aggregator.flush(false)
        verify(fixture.hub, never()).captureMetrics(any())

        // as times moves on
        fixture.currentTimeMillis = 30_000

        // the metric should be flushed
        aggregator.flush(false)
        verify(fixture.hub).captureMetrics(any())
    }

    @Test
    fun `force flush performs a flushing`() {
        val aggregator = fixture.getSut()
        // when a metric is emitted
        fixture.currentTimeMillis = 20_000
        aggregator.increment("key", 1.0, null, null, 20_001, 1)

        // then force flush flushes the metric
        aggregator.flush(true)
        verify(fixture.hub).captureMetrics(any())
    }

    @Test
    fun `same metrics are aggregated when in same bucket`() {
        val aggregator = fixture.getSut()
        fixture.options.environment = "prod"

        fixture.currentTimeMillis = 20_000

        aggregator.increment(
            "name",
            1.0,
            MeasurementUnit.Custom("apples"),
            mapOf("a" to "b"),
            20_001,
            1
        )
        aggregator.increment(
            "name",
            1.0,
            MeasurementUnit.Custom("apples"),
            mapOf("a" to "b"),
            25_001,
            1
        )

        // then flush does nothing because there's no data inside the flush interval
        aggregator.flush(true)

        verify(fixture.hub).captureMetrics(
            check {
                val metrics = MetricsHelperTest.parseMetrics(it.statsd)
                assertEquals(1, metrics.size)
                assertEquals(
                    MetricsHelperTest.Companion.StatsDMetric(
                        20,
                        "name",
                        "apples",
                        "c",
                        listOf("2.0"),
                        mapOf("a" to "b", "environment" to "prod")
                    ),
                    metrics[0]
                )
            }
        )
    }

    @Test
    fun `different metrics are not aggregated when in same bucket`() {
        val aggregator = fixture.getSut()

        // when different metrics are emitted in the same bucket
        fixture.currentTimeMillis = 20_000
        aggregator.distribution(
            "name0",
            1.0,
            MeasurementUnit.Custom("unit0"),
            mapOf("key0" to "value0"),
            20_001,
            1
        )
        aggregator.increment(
            "name0",
            1.0,
            MeasurementUnit.Custom("unit0"),
            mapOf("key0" to "value0"),
            20_001,
            1
        )
        aggregator.increment(
            "name0",
            1.0,
            MeasurementUnit.Custom("unit1"),
            mapOf("key0" to "value0"),
            20_001,
            1
        )
        aggregator.increment(
            "name0",
            1.0,
            MeasurementUnit.Custom("unit1"),
            mapOf("key1" to "value0"),
            20_001,
            1
        )
        aggregator.increment(
            "name0",
            1.0,
            MeasurementUnit.Custom("unit1"),
            mapOf("key1" to "value1"),
            20_001,
            1
        )

        aggregator.flush(true)

        // then all of them are emitted separately
        verify(fixture.hub).captureMetrics(
            check {
                val metrics = MetricsHelperTest.parseMetrics(it.statsd)
                assertEquals(5, metrics.size)
            }
        )
    }

    @Test
    fun `once the aggregator is closed, emissions are ignored`() {
        val aggregator = fixture.getSut()

        // when aggregator is closed
        aggregator.close()

        // and a metric is emitted
        fixture.currentTimeMillis = 20_000
        aggregator.increment(
            "name0",
            1.0,
            MeasurementUnit.Custom("unit0"),
            mapOf("key0" to "value0"),
            20_001,
            1
        )

        // then the metric is never captured
        aggregator.flush(true)
        verify(fixture.hub, never()).captureMetrics(any())
    }

    @Test
    fun `all metric types can be emitted`() {
        val aggregator = fixture.getSut()

        fixture.currentTimeMillis = 20_000
        aggregator.increment(
            "name0",
            1.0,
            MeasurementUnit.Custom("unit0"),
            mapOf("key0" to "value0"),
            20_001,
            1
        )
        aggregator.distribution(
            "name0",
            1.0,
            MeasurementUnit.Custom("unit0"),
            mapOf("key0" to "value0"),
            20_001,
            1
        )
        aggregator.set(
            "name0",
            "Hello",
            MeasurementUnit.Custom("unit0"),
            mapOf("key0" to "value0"),
            20_001,
            1
        )
        aggregator.gauge(
            "name0",
            1.0,
            MeasurementUnit.Custom("unit0"),
            mapOf("key0" to "value0"),
            20_001,
            1
        )

        aggregator.flush(true)
        verify(fixture.hub).captureMetrics(
            check {
                val metrics = MetricsHelperTest.parseMetrics(it.statsd)
                assertEquals(4, metrics.size)
            }
        )
    }

    @Test
    fun `flushing gets scheduled and captures metrics`() {
        val aggregator = fixture.getSut()

        // when nothing happened so far
        // then no flushing is scheduled
        assertFalse(fixture.executorService.hasScheduledRunnables())

        // when a metric gets emitted
        fixture.currentTimeMillis = 20_000
        aggregator.increment(
            "name0",
            1.0,
            MeasurementUnit.Custom("unit0"),
            mapOf("key0" to "value0"),
            20_001,
            1
        )

        // then a flush is scheduled
        assertTrue(fixture.executorService.hasScheduledRunnables())

        // after the flush is executed, the metric is captured
        fixture.currentTimeMillis = 31_000
        fixture.executorService.runAll()
        verify(fixture.hub).captureMetrics(any())

        // and flushing is scheduled again
        assertTrue(fixture.executorService.hasScheduledRunnables())
    }

    @Test
    fun `tags are enriched with environment and release`() {
        val aggregator = fixture.getSut()

        fixture.options.release = "1.0"
        fixture.options.environment = "prod"

        // when a metric gets emitted
        fixture.currentTimeMillis = 20_000
        aggregator.increment(
            "name",
            1.0,
            MeasurementUnit.Custom("apples"),
            mapOf("a" to "b"),
            20_001,
            1
        )

        aggregator.flush(true)
        verify(fixture.hub).captureMetrics(
            check {
                val metrics = MetricsHelperTest.parseMetrics(it.statsd)
                assertEquals(
                    MetricsHelperTest.Companion.StatsDMetric(
                        20,
                        "name",
                        "apples",
                        "c",
                        listOf("1.0"),
                        mapOf(
                            "a" to "b",
                            "release" to "1.0",
                            "environment" to "prod"
                        )
                    ),
                    metrics[0]
                )
            }
        )
    }

    @Test
    fun `existing environment and release tags are not overwritten`() {
        val aggregator = fixture.getSut()

        fixture.options.release = "1.0"
        fixture.options.environment = "prod"

        // when a metric gets emitted
        fixture.currentTimeMillis = 20_000
        aggregator.increment(
            "name",
            1.0,
            MeasurementUnit.Custom("apples"),
            mapOf(
                "release" to "2.0",
                "environment" to "prod-2"
            ),
            20_001,
            1
        )

        aggregator.flush(true)
        verify(fixture.hub).captureMetrics(
            check {
                val metrics = MetricsHelperTest.parseMetrics(it.statsd)
                assertEquals(
                    MetricsHelperTest.Companion.StatsDMetric(
                        20,
                        "name",
                        "apples",
                        "c",
                        listOf("1.0"),
                        mapOf(
                            "release" to "2.0",
                            "environment" to "prod-2"
                        )
                    ),
                    metrics[0]
                )
            }
        )
    }
}
