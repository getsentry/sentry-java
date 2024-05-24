package io.sentry.metrics

import io.sentry.MetricsAggregator
import io.sentry.Sentry
import io.sentry.SentryClient
import io.sentry.SentryOptions
import io.sentry.TransactionOptions
import junit.framework.TestCase.assertEquals
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.BeforeTest
import kotlin.test.Test

class MetricsIntegrationTest {

    @BeforeTest
    fun setup() {
        Sentry.close()
    }

    @Test
    fun `metrics are collected`() {
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            release = "io.sentry.samples@2.3.0"
            enableTracing = true
            sampleRate = 1.0
            isEnableMetrics = true
        }
        Sentry.init(options)

        val client = mock<SentryClient>()
        val aggregator = MetricsAggregator(options, client)
        whenever(client.metricsAggregator).thenReturn(aggregator)
        Sentry.bindClient(client)

        // when metrics are emitted
        Sentry.metrics().increment("counter", 1.0)
        Sentry.metrics().increment("counter", 2.0)
        Sentry.metrics().increment("counter", 3.0)

        // and sentry is flushed
        // Sentry.close() would invoke client.close(), which calls aggregator.close()
        // but our client is mocked
        aggregator.close()

        // the aggregated metric should be captured
        verify(client).captureMetrics(
            check {
                assertEquals(1, it.buckets.size)
                val metric = it.buckets.values.first().values.first()
                assertEquals("counter", metric.key)
                assertEquals(listOf(6.0), metric.serialize().toList())
            }
        )
    }

    @Test
    fun `metric summaries are attached to txn and spans`() {
        // ---- time ------>
        // |-------- txn -------------|
        //        |-- span --|
        //   1.0       2.0        3.0

        // given an initialized SDK
        val options = SentryOptions().apply {
            dsn = "https://key@sentry.io/proj"
            release = "io.sentry.samples@2.3.0"
            enableTracing = true
            sampleRate = 1.0
            isEnableMetrics = true
        }
        Sentry.init(options)

        val client = mock<SentryClient>()
        whenever(client.isEnabled).thenReturn(true)
        val aggregator = MetricsAggregator(options, client)
        whenever(client.metricsAggregator).thenReturn(aggregator)
        Sentry.bindClient(client)

        // when a txn starts
        val txn = Sentry.startTransaction(
            "name",
            "op.load",
            TransactionOptions().apply {
                isBindToScope = true
            }
        )

        // inc 1.0 happens on txn
        Sentry.metrics().increment("counter", 1.0)

        // and a span starts
        val span = txn.startChild("op.child")

        // inc 2.0 happens on span
        Sentry.metrics().increment("counter", 2.0)
        span.finish()

        // inc 3.0 happens on txn again, as the span is already finished
        Sentry.metrics().increment("counter", 3.0)
        txn.finish()

        Sentry.flush(0)
        Sentry.close()

        // then the txn and span have the right summary
        verify(client).captureTransaction(
            check {
                assertEquals(1, it.metricSummaries!!.size)
                val txnSummary = it.metricSummaries!!.values.first().first()
                assertEquals(2, txnSummary.count)
                assertEquals(4.0, txnSummary.sum)

                assertEquals(1, it.spans[0].metricsSummaries!!.size)
                val spanSummary = it.spans[0].metricsSummaries!!.values.first().first()
                assertEquals(1, spanSummary.count)
                assertEquals(2.0, spanSummary.sum)
            },
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }
}
