package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class MessageSerializationTest {
    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() =
            Message().apply {
                formatted = "6a4706fe-386d-4e4c-acc4-cf27f0331ede"
                message = "96d75ef6-49fa-47dc-bb68-98f4293fa1eb"
                params =
                    listOf(
                        "3937ad9c-e4e0-45c1-bb58-f94c25072bc7",
                        "1afbadaf-3db6-48ea-ab85-fdcd24fceda2",
                    )
            }
    }

    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/message.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/message.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
        assertEquals(expectedJson, actualJson)
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

    private fun deserialize(json: String): Message {
        val reader = JsonObjectReader(StringReader(json))
        return Message.Deserializer().deserialize(reader, fixture.logger)
    }
}
