package io.sentry.metrics

import io.sentry.MeasurementUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricsHelperTest {

    companion object {

        data class StatsDMetric(
            val timestamp: Long?,
            val name: String?,
            val unit: String?,
            val type: String?,
            val values: List<String>?,
            val tags: Map<String, Any>?
        )

        fun parseMetrics(byteArray: ByteArray): List<StatsDMetric> {
            val metrics = mutableListOf<StatsDMetric>()

            val encodedMetrics = byteArray.decodeToString()
            for (line in encodedMetrics.split("\n")) {
                if (line.isEmpty()) {
                    continue
                }

                val pieces = line.split("|")
                val payload = pieces[0].split(":")

                val nameAndUnit = payload[0].split("@", limit = 2)
                val name = nameAndUnit[0]
                val unit = if (nameAndUnit.size == 2) nameAndUnit[1] else null

                val values = payload.subList(1, payload.size)
                val type = pieces[1]

                var timestamp: Long? = null
                val tags = mutableMapOf<String, String>()

                for (piece in pieces.subList(2, pieces.size)) {
                    if (piece[0] == '#') {
                        for (pair in piece.substring(1, piece.length).split(",")) {
                            val (k, v) = pair.split(":", limit = 2)
                            tags[k] = v
                        }
                    } else if (piece[0] == 'T') {
                        timestamp = piece.substring(1, piece.length).toLong()
                    } else {
                        throw IllegalArgumentException("unknown piece $piece")
                    }
                }
                metrics.add(StatsDMetric(timestamp, name, unit, type, values, tags))
            }
            metrics.sortBy { it.timestamp }
            return metrics
        }
    }

    @Test
    fun sanitizeKey() {
        assertEquals("foo-bar", MetricsHelper.sanitizeKey("foo-bar"))
        assertEquals("foo_bar", MetricsHelper.sanitizeKey("foo\$\$\$bar"))
        assertEquals("fo_-bar", MetricsHelper.sanitizeKey("foö-bar"))
    }

    @Test
    fun sanitizeValue() {
        assertEquals("_\$foo", MetricsHelper.sanitizeValue("%\$foo"))
        assertEquals("blah{}", MetricsHelper.sanitizeValue("blah{}"))
        assertEquals("sn_wm_n", MetricsHelper.sanitizeValue("snöwmän"))
    }

    @Test
    fun sanitizeUnit() {
        val items = listOf(
            "Test123_." to "Test123_.",
            "test{value}" to "test_value_",
            "test-value" to "test_value"
        )

        for (item in items) {
            assertEquals(item.second, MetricsHelper.sanitizeUnit(item.first))
        }
    }

    @Test
    fun getTimeBucketKey() {
        assertEquals(
            0,
            MetricsHelper.getTimeBucketKey(5000)
        )

        assertEquals(
            -1,
            MetricsHelper.getTimeBucketKey(-5000)
        )

        assertEquals(
            10,
            MetricsHelper.getTimeBucketKey(10_000)
        )

        assertEquals(
            10,
            MetricsHelper.getTimeBucketKey(10_001)
        )

        assertEquals(
            20,
            MetricsHelper.getTimeBucketKey(20_000)
        )

        assertEquals(
            20,
            MetricsHelper.getTimeBucketKey(29_999)
        )

        assertEquals(
            30,
            MetricsHelper.getTimeBucketKey(30_000)
        )
    }

    @Test
    fun encode() {
        val stringBuilder = StringBuilder()
        MetricsHelper.encodeMetrics(
            1000,
            listOf(
                CounterMetric(
                    "name",
                    1.0,
                    MeasurementUnit.Custom("oranges"),
                    mapOf(
                        "tag1" to "value1",
                        "tag2" to "value2"
                    ),
                    1000
                )
            ),
            stringBuilder
        )

        val metrics = parseMetrics(stringBuilder.toString().toByteArray())

        assertEquals(1, metrics.size)

        assertEquals(
            StatsDMetric(
                1000,
                "name",
                "oranges",
                "c",
                listOf("1.0"),
                mapOf("tag1" to "value1", "tag2" to "value2")
            ),
            metrics[0]
        )
    }
}
