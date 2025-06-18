package io.sentry.rrweb

import io.sentry.ILogger
import io.sentry.protocol.SerializationUtils
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class RRWebSpanEventSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            RRWebSpanEvent().apply {
                timestamp = 12345678901
                op = "resource.http"
                description = "https://api.github.com/users/getsentry/repos"
                startTimestamp = 12345678.901
                endTimestamp = 12345679.901
                data =
                    mapOf(
                        "method" to "POST",
                        "status_code" to 200,
                    )
            }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = SerializationUtils.sanitizedFile("json/rrweb_span_event.json")
        val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = SerializationUtils.sanitizedFile("json/rrweb_span_event.json")
        val actual =
            SerializationUtils.deserializeJson(expectedJson, RRWebSpanEvent.Deserializer(), fixture.logger)
        val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)
        assertEquals(expectedJson, actualJson)
    }
}
