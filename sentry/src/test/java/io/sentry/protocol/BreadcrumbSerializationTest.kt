package io.sentry.protocol

import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreadcrumbSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            Breadcrumb(
                DateUtils.getDateTime("2009-11-16T01:08:47.000Z"),
            ).apply {
                message = "46f233c0-7c2d-488a-b05a-7be559173e16"
                type = "ace57e2e-305e-4048-abf0-6c8538ea7bf4"
                setData("6607d106-d426-462b-af74-f29fce978e48", "149bb94a-1387-4484-90be-2df15d1322ab")
                category = "b6eea851-5ae5-40ed-8fdd-5e1a655a879c"
                origin = "4d8085ef-22fc-49d5-801e-55d509fd1a1c"
                level = SentryLevel.DEBUG
            }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/breadcrumb.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/breadcrumb.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
        print(actualJson)
        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun deserializeFromMap() {
        val map: Map<String, Any?> =
            mapOf(
                "timestamp" to "2009-11-16T01:08:47.000Z",
                "message" to "46f233c0-7c2d-488a-b05a-7be559173e16",
                "type" to "ace57e2e-305e-4048-abf0-6c8538ea7bf4",
                "data" to
                    mapOf(
                        "6607d106-d426-462b-af74-f29fce978e48" to "149bb94a-1387-4484-90be-2df15d1322ab",
                    ),
                "category" to "b6eea851-5ae5-40ed-8fdd-5e1a655a879c",
                "origin" to "4d8085ef-22fc-49d5-801e-55d509fd1a1c",
                "level" to "debug",
            )
        val actual = Breadcrumb.fromMap(map, SentryOptions())
        val expected = fixture.getSut()

        assertEquals(expected.timestamp, actual?.timestamp)
        assertEquals(expected.message, actual?.message)
        assertEquals(expected.type, actual?.type)
        assertEquals(expected.data, actual?.data)
        assertEquals(expected.category, actual?.category)
        assertEquals(expected.origin, actual?.origin)
        assertEquals(expected.level, actual?.level)
    }

    @Test
    fun deserializeDataWithInvalidKey() {
        val map: Map<String, Any?> =
            mapOf(
                "data" to
                    mapOf(
                        123 to 456, // Invalid key type
                    ),
            )
        val actual = Breadcrumb.fromMap(map, SentryOptions())
        assertTrue(actual.data.isEmpty())
    }

    @Test
    fun deserializeDataWithNullKey() {
        val map: Map<String, Any?> =
            mapOf(
                "data" to
                    mapOf(
                        "null" to null,
                    ),
            )
        val actual = Breadcrumb.fromMap(map, SentryOptions())
        assertEquals(null, actual?.data?.get("null"))
    }

    // Helper

    private fun sanitizedFile(path: String): String =
        FileFromResources
            .invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")

    private fun serialize(jsonSerializable: JsonSerializable): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        jsonSerializable.serialize(jsonWrt, fixture.logger)
        return wrt.toString()
    }

    private fun deserialize(json: String): Breadcrumb {
        val reader = JsonObjectReader(StringReader(json))
        return Breadcrumb.Deserializer().deserialize(reader, fixture.logger)
    }
}
