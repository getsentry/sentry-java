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
        val result = deserialize(json)

        assertEquals(mapOf("fixture-key" to "fixture-value"), result)
    }

    @Test
    fun `deserialize json int`() {
        val json = "{\"fixture-key\": 123}"
        val result = deserialize(json)

        assertEquals(mapOf("fixture-key" to 123), result)
    }

    @Test
    fun `deserialize json double`() {
        val json = "{\"fixture-key\": 123.321}"
        val result = deserialize(json)

        assertEquals(mapOf("fixture-key" to 123.321), result)
    }

    @Test
    fun `deserialize json boolean`() {
        val json = "{\"fixture-key\": true}"
        val result = deserialize(json)

        assertEquals(mapOf("fixture-key" to true), result)
    }

    @Test
    fun `deserialize json string array`() {
        val json = "{\"fixture-key\":[\"fixture-entry-1\",\"fixture-entry-2\"]}"
        val result = deserialize(json)

        assertEquals(mapOf("fixture-key" to listOf("fixture-entry-1", "fixture-entry-2")), result)
    }

    @Test
    fun `deserialize json int array`() {
        val json = "{\"fixture-key\":[1,2]}"
        val result = deserialize(json)

        assertEquals(mapOf("fixture-key" to listOf(1, 2)), result)
    }

    @Test
    fun `deserialize json double array`() {
        val json = "{\"fixture-key\":[1.1,2.2]}"
        val result = deserialize(json)

        assertEquals(mapOf("fixture-key" to listOf(1.1, 2.2)), result)
    }

    // Helper

    private fun deserialize(string: String): Map<String, Any> {
        val rdr = StringReader(string)
        val jsonRdr = JsonObjectReader(rdr)
        return fixture.getSut().deserialize(jsonRdr, fixture.logger)
    }
}
