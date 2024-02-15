package io.sentry

import io.sentry.metrics.IMetricsHub
import io.sentry.metrics.MetricsHelper
import io.sentry.metrics.MetricsHelperTest
import io.sentry.metrics.NoopMetricsAggregator
import io.sentry.test.DeferredExecutorService
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetricsAggregatorTest {

    private val hub = mock<IMetricsHub>()
    private val logger = mock<ILogger>()
    private var currentTimeMillis: Long = 0
    private var aggregator: IMetricsAggregator =
        NoopMetricsAggregator()
    private var executorService = DeferredExecutorService()

    @BeforeTest
    fun setup() {
        MetricsHelper.setFlushShiftMs(0)
        executorService = DeferredExecutorService()
        aggregator = MetricsAggregator(hub, logger, executorService).also {
            it.setTimeProvider {
                currentTimeMillis
            }
        }
        reset(hub)
    }

    @AfterTest
    fun tearDown() {
        aggregator.close()
        aggregator = NoopMetricsAggregator()
        executorService.close(0)
    }

    @Test
    fun `flush is a no-op when there's nothing to flush`() {
        // when no metrics are collected

        // then flush does nothing
        aggregator.flush(false)

        verify(hub, never()).captureMetrics(any())
    }

    @Test
    fun `flush performs a flush when needed`() {
        // when a metric is emitted
        currentTimeMillis = 20_000
        aggregator.increment("key", 1.0, null, null, 20_001, 1)

        // then flush does nothing because there's no data inside the flush interval
        aggregator.flush(false)
        verify(hub, never()).captureMetrics(any())

        // as times moves on
        currentTimeMillis = 30_000

        // the metric should be flushed
        aggregator.flush(false)
        verify(hub).captureMetrics(any())
    }

    @Test
    fun `force flush performs a flushing`() {
        // when a metric is emitted
        currentTimeMillis = 20_000
        aggregator.increment("key", 1.0, null, null, 20_001, 1)

        // then force flush flushes the metric
        aggregator.flush(true)
        verify(hub).captureMetrics(any())
    }

    @Test
    fun `same metrics are aggregated when in same bucket`() {
        currentTimeMillis = 20_000

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

        verify(hub).captureMetrics(
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
                        mapOf("a" to "b")
                    ),
                    metrics[0]
                )
            }
        )
    }

    @Test
    fun `different metrics are not aggregated when in same bucket`() {
        // when different metrics are emitted in the same bucket
        currentTimeMillis = 20_000
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
        verify(hub).captureMetrics(
            check {
                val metrics = MetricsHelperTest.parseMetrics(it.statsd)
                assertEquals(5, metrics.size)
            }
        )
    }

    @Test
    fun `once the aggregator is closed, emissions are ignored`() {
        // when aggregator is closed
        aggregator.close()

        // and a metric is emitted
        currentTimeMillis = 20_000
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
        verify(hub, never()).captureMetrics(any())
    }

    @Test
    fun `all metric types can be emitted`() {
        currentTimeMillis = 20_000
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
        verify(hub).captureMetrics(
            check {
                val metrics = MetricsHelperTest.parseMetrics(it.statsd)
                assertEquals(4, metrics.size)
            }
        )
    }

    @Test
    fun `flushing gets scheduled and captures metrics`() {
        // when nothing happened so far
        // then no flushing is scheduled
        assertFalse(executorService.hasScheduledRunnables())

        // when a metric gets emitted
        currentTimeMillis = 20_000
        aggregator.increment(
            "name0",
            1.0,
            MeasurementUnit.Custom("unit0"),
            mapOf("key0" to "value0"),
            20_001,
            1
        )

        // then a flush is scheduled
        assertTrue(executorService.hasScheduledRunnables())

        // after the flush is executed, the metric is captured
        currentTimeMillis = 31_000
        executorService.runAll()
        verify(hub).captureMetrics(any())

        // and flushing is scheduled again
        assertTrue(executorService.hasScheduledRunnables())
    }
}
