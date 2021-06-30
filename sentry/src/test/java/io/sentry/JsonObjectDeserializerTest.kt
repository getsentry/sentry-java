package io.sentry

import com.nhaarman.mockitokotlin2.mock
import org.junit.Test
import java.io.StringReader
import kotlin.test.assertEquals

class JsonObjectDeserializerTest {

    private class Fixture {
        var logger: ILogger = mock()

        fun getSut(): JsonObjectDeserializer {
            return JsonObjectDeserializer()
        }
    }

    private val fixture = Fixture()

    @Test
    fun `deserialize json string`() {
        val json = "{\"fixture-key\": \"fixture-value\"}"
        val actual = deserialize(json)

        assertEquals(mapOf("fixture-key" to "fixture-value"), actual)
    }

    @Test
    fun `deserialize json int`() {
        val json = "{\"fixture-key\": 123}"
        val actual = deserialize(json)

        assertEquals(mapOf("fixture-key" to 123), actual)
    }

    @Test
    fun `deserialize json double`() {
        val json = "{\"fixture-key\": 123.321}"
        val actual = deserialize(json)

        assertEquals(mapOf("fixture-key" to 123.321), actual)
    }

    @Test
    fun `deserialize json boolean`() {
        val json = "{\"fixture-key\": true}"
        val actual = deserialize(json)

        assertEquals(mapOf("fixture-key" to true), actual)
    }

    @Test
    fun `deserialize json string array`() {
        val json = "{\"fixture-key\":[\"fixture-entry-1\",\"fixture-entry-2\"]}"
        val actual = deserialize(json)

        assertEquals(mapOf("fixture-key" to listOf("fixture-entry-1", "fixture-entry-2")), actual)
    }

    @Test
    fun `deserialize json int array`() {
        val json = "{\"fixture-key\":[1,2]}"
        val actual = deserialize(json)

        assertEquals(mapOf("fixture-key" to listOf(1, 2)), actual)
    }

    @Test
    fun `deserialize json double array`() {
        val json = "{\"fixture-key\":[1.1,2.2]}"
        val actual = deserialize(json)

        assertEquals(mapOf("fixture-key" to listOf(1.1, 2.2)), actual)
    }

    @Test
    fun `deserialize json object with nesting`() {
        val json = """
            "fixture-key": {
                "string": "fixture-string",
                "int": 123,
                "double": 123.321,
                "boolean": true,
                "array": ["a", "b", "c"],
                "object": {
                    "key": "value"
                },
            }
        """.trimIndent()

        val expected = mapOf<String, Any>(
            "fixture-key" to mapOf(
                "string" to "fixture-string",
                "int" to 123,
                "double" to 123.321,
                "boolean" to true,
                "array" to listOf("a", "b", "c"),
                "object" to mapOf(
                    "key" to "value"
                )
            )
        )

        val actual = deserialize(json)
        assertEquals(expected, actual)
    }

    // Helper

    private fun deserialize(string: String): Map<String, Any> {
        val rdr = StringReader(string)
        val jsonRdr = JsonObjectReader(rdr)
        return fixture.getSut().deserialize(jsonRdr, fixture.logger)
    }
}
