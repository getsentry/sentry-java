package io.sentry

import io.sentry.metrics.IMetricsHub
import io.sentry.metrics.MetricsHelper
import io.sentry.metrics.NoopMetricsAggregator
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MetricsAggregatorTest {

    private val hub = mock<IMetricsHub>()
    private val logger = mock<ILogger>()
    private var currentTimeMillis: Long = 0
    private var aggregator: IMetricsAggregator =
        NoopMetricsAggregator()

    @BeforeTest
    fun setup() {
        MetricsHelper.setFlushShiftMs(0)
        aggregator = MetricsAggregator(hub, logger).also {
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
}
