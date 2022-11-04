package io.sentry.protocol

import io.sentry.Breadcrumb
import io.sentry.DateUtils
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SentryLevel
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class BreadcrumbSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = Breadcrumb(
            DateUtils.getDateTime("2009-11-16T01:08:47.000Z")
        ).apply {
            message = "46f233c0-7c2d-488a-b05a-7be559173e16"
            type = "ace57e2e-305e-4048-abf0-6c8538ea7bf4"
            setData("6607d106-d426-462b-af74-f29fce978e48", "149bb94a-1387-4484-90be-2df15d1322ab")
            category = "b6eea851-5ae5-40ed-8fdd-5e1a655a879c"
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

    // Helper

    private fun sanitizedFile(path: String): String {
        return FileFromResources.invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")
    }

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
