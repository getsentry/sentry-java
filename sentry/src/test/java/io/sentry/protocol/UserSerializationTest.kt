package io.sentry.protocol

import com.nhaarman.mockitokotlin2.mock
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class UserSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = User().apply {
            email = "c4d61c1b-c144-431e-868f-37a46be5e5f2"
            id = "efb2084b-1871-4b59-8897-b4bd9f196a01"
            username = "60c05dff-7140-4d94-9a61-c9cdd9ca9b96"
            ipAddress = "51d22b77-f663-4dbe-8103-8b749d1d9a48"
            others = mapOf(
                "dc2813d0-0f66-4a3f-a995-71268f61a8fa" to "991659ad-7c59-4dd3-bb89-0bd5c74014bd"
            )
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/user.json")
        val actual = serialize(fixture.getSut())
        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/user.json")
        val actual = deserialize(expectedJson)
        val actualJson = serialize(actual)
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

    private fun deserialize(json: String): User {
        val reader = JsonObjectReader(StringReader(json))
        return User.Deserializer().deserialize(reader, fixture.logger)
    }
}
