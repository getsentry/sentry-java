package io.sentry.rrweb

import io.sentry.ILogger
import io.sentry.SentryLevel.INFO
import io.sentry.protocol.SerializationUtils
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class RRWebBreadcrumbEventSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            RRWebBreadcrumbEvent().apply {
                timestamp = 12345678901
                breadcrumbType = "default"
                breadcrumbTimestamp = 12345678.901
                category = "navigation"
                message = "message"
                level = INFO
                data =
                    mapOf(
                        "screen" to "MainActivity",
                        "state" to "resumed",
                    )
            }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = SerializationUtils.sanitizedFile("json/rrweb_breadcrumb_event.json")
        val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = SerializationUtils.sanitizedFile("json/rrweb_breadcrumb_event.json")
        val actual =
            SerializationUtils.deserializeJson(expectedJson, RRWebBreadcrumbEvent.Deserializer(), fixture.logger)
        val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)
        assertEquals(expectedJson, actualJson)
    }
}
