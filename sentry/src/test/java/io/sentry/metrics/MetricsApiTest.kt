package io.sentry.metrics

import io.sentry.IMetricsAggregator
import io.sentry.metrics.MetricsApi.IMetricsInterface
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.Test

class MetricsApiTest {

    class Fixture {
        val aggregator = mock<IMetricsAggregator>()
        val localMetricsAggregator = mock<LocalMetricsAggregator>()

        fun getSut(defaultTags: Map<String, String> = emptyMap()): MetricsApi {
            val localAggregator = localMetricsAggregator

            return MetricsApi(object : IMetricsInterface {
                override fun getMetricsAggregator(): IMetricsAggregator {
                    return aggregator
                }

                override fun getLocalMetricsAggregator(): LocalMetricsAggregator? = localAggregator

                override fun getDefaultTagsForMetrics(): Map<String, String> = defaultTags
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
            anyOrNull(),
            anyOrNull()
        )

        verify(fixture.aggregator).set(
            anyOrNull(),
            eq(1),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
            anyOrNull()
        )

        verify(fixture.aggregator).set(
            anyOrNull(),
            eq("string"),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
            anyOrNull()
        )

        verify(fixture.aggregator).gauge(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
            anyOrNull()
        )

        verify(fixture.aggregator).distribution(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
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
            anyOrNull(),
            anyOrNull()
        )

        verify(fixture.aggregator).set(
            anyOrNull(),
            eq(1),
            anyOrNull(),
            anyOrNull(),
            eq(1234),
            anyOrNull(),
            anyOrNull()
        )

        verify(fixture.aggregator).set(
            anyOrNull(),
            eq("string"),
            anyOrNull(),
            anyOrNull(),
            eq(1234),
            anyOrNull(),
            anyOrNull()
        )

        verify(fixture.aggregator).gauge(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(1234),
            anyOrNull(),
            anyOrNull()
        )

        verify(fixture.aggregator).distribution(
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            eq(1234),
            anyOrNull(),
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
            anyOrNull(),
            eq(fixture.localMetricsAggregator)
        )
    }
}
