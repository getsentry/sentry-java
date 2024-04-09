package io.sentry.metrics

import io.sentry.IMetricsAggregator
import io.sentry.ISpan
import io.sentry.MeasurementUnit
import io.sentry.metrics.MetricsApi.IMetricsInterface
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricsApiTest {

    class Fixture {
        val aggregator = mock<IMetricsAggregator>()
        val localMetricsAggregator = mock<LocalMetricsAggregator>()

        var lastSpan: ISpan? = null
        var lastOp: String? = null
        var lastDescription: String? = null

        fun getSut(
            defaultTags: Map<String, String> = emptyMap(),
            spanProvider: () -> ISpan? = { mock<ISpan>() }
        ): MetricsApi {
            val localAggregator = localMetricsAggregator

            return MetricsApi(object : IMetricsInterface {
                override fun getMetricsAggregator(): IMetricsAggregator {
                    return aggregator
                }

                override fun getLocalMetricsAggregator(): LocalMetricsAggregator? = localAggregator

                override fun getDefaultTagsForMetrics(): Map<String, String> = defaultTags

                override fun startSpanForMetric(op: String, description: String): ISpan? {
                    lastOp = op
                    lastDescription = description
                    lastSpan = spanProvider()
                    return lastSpan
                }
            })
        }
    }

    val fixture = Fixture()

    @Test
    fun `default timestamp is provided`() {
        val api = fixture.getSut()

        api.increment("name", 1.0, null, null, null)
        api.set("name", 1, null, null, null)
        api.set("name", "string", null, null, null)
        api.gauge("name", 1.0, null, null, null)
        api.distribution("name", 1.0, null, null, null)

        verify(fixture.aggregator).increment(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull()
        )

        verify(fixture.aggregator).set(
            anyOrNull(),
            eq(1),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull()
        )

        verify(fixture.aggregator).set(
            anyOrNull(),
            eq("string"),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull()
        )

        verify(fixture.aggregator).gauge(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull()
        )

        verify(fixture.aggregator).distribution(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull()
        )
    }

    @Test
    fun `timestamp is not overwritten`() {
        val api = fixture.getSut()

        api.increment("name", 1.0, null, null, 1234)
        api.set("name", 1, null, null, 1234)
        api.set("name", "string", null, null, 1234)
        api.gauge("name", 1.0, null, null, 1234)
        api.distribution("name", 1.0, null, null, 1234)

        verify(fixture.aggregator).increment(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(1234),
            anyOrNull()
        )

        verify(fixture.aggregator).set(
            anyOrNull(),
            eq(1),
            anyOrNull(),
            anyOrNull(),
            eq(1234),
            anyOrNull()
        )

        verify(fixture.aggregator).set(
            anyOrNull(),
            eq("string"),
            anyOrNull(),
            anyOrNull(),
            eq(1234),
            anyOrNull()
        )

        verify(fixture.aggregator).gauge(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(1234),
            anyOrNull()
        )

        verify(fixture.aggregator).distribution(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(1234),
            anyOrNull()
        )
    }

    @Test
    fun `tags are enriched with default tags`() {
        val api = fixture.getSut(
            defaultTags = mapOf(
                "release" to "1.0",
                "environment" to "prod"
            )
        )

        api.increment("name", 1.0, null, mapOf("a" to "b"))

        verify(fixture.aggregator).increment(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(
                mapOf(
                    "a" to "b",
                    "release" to "1.0",
                    "environment" to "prod"
                )
            ),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `existing environment and release tags are not overwritten`() {
        val api = fixture.getSut(
            defaultTags = mapOf(
                "release" to "1.0",
                "environment" to "prod"
            )
        )

        api.increment(
            "name",
            1.0,
            null,
            mapOf(
                "release" to "2.0",
                "environment" to "dev"
            )
        )

        verify(fixture.aggregator).increment(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(
                mapOf(
                    "release" to "2.0",
                    "environment" to "dev"
                )
            ),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `local aggregator is provided to aggregator`() {
        val api = fixture.getSut()

        api.increment("increment")
        verify(fixture.aggregator).increment(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(fixture.localMetricsAggregator)
        )

        api.set("set", 1)
        verify(fixture.aggregator).set(
            anyOrNull(),
            eq(1),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(fixture.localMetricsAggregator)
        )

        api.set("set", "string")
        verify(fixture.aggregator).set(
            anyOrNull(),
            eq("string"),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(fixture.localMetricsAggregator)
        )

        api.gauge("gauge", 1.0)
        verify(fixture.aggregator).gauge(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(fixture.localMetricsAggregator)
        )

        api.distribution("distribution", 1.0)
        verify(fixture.aggregator).distribution(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(fixture.localMetricsAggregator)
        )

        api.timing("timing") {
            // no-op
        }
        verify(fixture.aggregator).timing(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(fixture.localMetricsAggregator)
        )
    }

    @Test
    fun `timing starts and finishes a span`() {
        val api = fixture.getSut()

        api.timing("key") {
            // no-op
        }

        assertEquals("metric.timing", fixture.lastOp)
        assertEquals("key", fixture.lastDescription)

        verify(fixture.lastSpan!!).finish()
    }

    @Test
    fun `timing applies metric tags as span tags`() {
        val span = mock<ISpan>()
        val api = fixture.getSut(
            spanProvider = {
                span
            },
            defaultTags = mapOf(
                "release" to "1.0"
            )
        )
        // when timing is called
        api.timing("key", {
            // no-op
        }, MeasurementUnit.Duration.NANOSECOND, mapOf("a" to "b"))

        // the last span should have the metric tags, without the default ones
        verify(fixture.lastSpan!!, never()).setTag("release", "1.0")
        verify(fixture.lastSpan!!).setTag("a", "b")
    }

    @Test
    fun `if timing throws an exception, span still finishes`() {
        val api = fixture.getSut()

        try {
            api.timing("key") {
                throw IllegalStateException()
            }
        } catch (e: IllegalStateException) {
            // ignored
        }

        assertEquals("metric.timing", fixture.lastOp)
        assertEquals("key", fixture.lastDescription)
        verify(fixture.lastSpan!!).finish()
    }

    @Test
    fun `if timing does retrieve a null span, it still works`() {
        val api = fixture.getSut(
            spanProvider = { null }
        )
        api.timing("key") {
            // no-op
        }
        // no crash
    }
}
