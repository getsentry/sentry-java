package io.sentry

import java.io.IOException
import java.io.StringReader
import java.lang.Exception
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.fail
import org.junit.Test

class JsonObjectDeserializerTest {
  private class Fixture {
    fun getSut(): JsonObjectDeserializer = JsonObjectDeserializer()
  }

  private val fixture = Fixture()

  @Test
  fun `deserialize null`() {
    val json = "null"
    val actual = deserialize(json)
    assertNull(actual)
  }

  @Test
  fun `deserialize string`() {
    val json = "\"String\""
    val actual = deserialize(json)
    assertEquals("String", actual)
  }

  @Test
  fun `deserialize int`() {
    val json = "1"
    val actual = deserialize(json)
    assertEquals(1, actual)
  }

  @Test
  fun `deserialize double`() {
    val json = "1.1"
    val actual = deserialize(json)
    assertEquals(1.1, actual)
  }

  // A value that is integral and fits an int is typed as Integer (regardless of how it was
  // written); anything else is a Double. This matches the behavior prior to removing the
  // exception-based number typing.

  @Test
  fun `deserialize negative int`() {
    assertEquals(-5, deserialize("-5"))
  }

  @Test
  fun `deserialize negative double`() {
    assertEquals(-3.14, deserialize("-3.14"))
  }

  @Test
  fun `deserialize integral exponent notation as int`() {
    assertEquals(100, deserialize("1e2"))
    assertEquals(100, deserialize("1E2"))
  }

  @Test
  fun `deserialize fractional exponent notation as double`() {
    assertEquals(0.0025, deserialize("2.5e-3"))
  }

  @Test
  fun `deserialize whole-valued decimal as int`() {
    assertEquals(1, deserialize("1.0"))
  }

  @Test
  fun `deserialize integer larger than int range as double`() {
    assertEquals(1.0e10, deserialize("10000000000"))
    assertEquals(2147483648.0, deserialize("2147483648"))
  }

  @Test
  fun `deserialize max int as int`() {
    assertEquals(Int.MAX_VALUE, deserialize("2147483647"))
  }

  @Test
  fun `deserialize rejects literal overflowing to infinity`() {
    // Strict JSON forbids non-finite numbers, so an out-of-range literal must fail rather than be
    // stored as Infinity.
    assertFailsWith<IOException> { deserialize("1e400") }
  }

  @Test
  fun `deserialize array`() {
    val json = "[\"a\",\"b\"]"
    val actual = deserialize(json)
    assertEquals(listOf("a", "b"), actual)
  }

  @Test
  fun `deserialize malformed fails`() {
    val json = "{\"fixture-key\": \"fixture-value\""
    try {
      deserialize(json)
      fail()
    } catch (e: Exception) {
      // Success
    }
  }

  @Test
  fun `deserialize json empty`() {
    val json = "{}"
    val actual = deserialize(json)

    assertEquals(emptyMap<String, Any>(), actual)
  }

  @Test
  fun `deserialize json null`() {
    val json = "{\"fixture-key\": null}"
    val actual = deserialize(json)

    assertEquals(mapOf("fixture-key" to null), actual)
  }

  @Test
  fun `deserialize json string`() {
    val json = "{\"fixture-key\": \"fixture-value\"}"
    val actual = deserialize(json)

    assertEquals(mapOf("fixture-key" to "fixture-value"), actual)
  }

  @Test
  fun `deserialize json object int`() {
    val json = "{\"fixture-key\": 123}"
    val actual = deserialize(json)

    assertEquals(mapOf("fixture-key" to 123), actual)
  }

  @Test
  fun `deserialize json object double`() {
    val json = "{\"fixture-key\": 123.321}"
    val actual = deserialize(json)

    assertEquals(mapOf("fixture-key" to 123.321), actual)
  }

  @Test
  fun `deserialize json object boolean`() {
    val json = "{\"fixture-key\": true}"
    val actual = deserialize(json)

    assertEquals(mapOf("fixture-key" to true), actual)
  }

  @Test
  fun `deserialize json object null array`() {
    val json = "{\"fixture-key\":[null,null]}"
    val actual = deserialize(json)

    assertEquals(mapOf("fixture-key" to listOf(null, null)), actual)
  }

  @Test
  fun `deserialize json object string array`() {
    val json = "{\"fixture-key\":[\"fixture-entry-1\",\"fixture-entry-2\"]}"
    val actual = deserialize(json)

    assertEquals(mapOf("fixture-key" to listOf("fixture-entry-1", "fixture-entry-2")), actual)
  }

  @Test
  fun `deserialize json object int array`() {
    val json = "{\"fixture-key\":[1,2]}"
    val actual = deserialize(json)

    assertEquals(mapOf("fixture-key" to listOf(1, 2)), actual)
  }

  @Test
  fun `deserialize json object double array`() {
    val json = "{\"fixture-key\":[1.1,2.2]}"
    val actual = deserialize(json)

    assertEquals(mapOf("fixture-key" to listOf(1.1, 2.2)), actual)
  }

  @Test
  fun `deserialize json object object array`() {
    val json = "{\"fixture-key\":[{\"id\":1},{\"id\":2}]}"
    val expected = mapOf("fixture-key" to listOf(mapOf("id" to 1), mapOf("id" to 2)))

    val actual = deserialize(json)
    assertEquals(expected, actual)
  }

  @Test
  fun `deserialize json object array array`() {
    val json = "{\"fixture-key\":[[\"a\"],[\"b\"]]}"
    val expected = mapOf("fixture-key" to listOf(listOf("a"), listOf("b")))

    val actual = deserialize(json)
    assertEquals(expected, actual)
  }

  @Test
  fun `deserialize json object object`() {
    val json =
      """
      {
          "key": {
              "key": "value"
          }
      }
      """
        .trimIndent()
    val expected = mapOf<String, Any>("key" to mapOf("key" to "value"))

    val actual = deserialize(json)
    assertEquals(expected, actual)
  }

  @Test
  fun `deserialize json object object with nesting`() {
    val json =
      """
      {
          "fixture-key":
          {
              "string": "fixture-string",
              "int": 123,
              "double": 123.321,
              "boolean": true,
              "array":
              [
                  "a",
                  "b",
                  "c"
              ],
              "object":
              {
                  "key": "value"
              }
          }
      }
      """
        .trimIndent()

    val expected =
      mapOf<String, Any>(
        "fixture-key" to
          mapOf(
            "string" to "fixture-string",
            "int" to 123,
            "double" to 123.321,
            "boolean" to true,
            "array" to listOf("a", "b", "c"),
            "object" to mapOf("key" to "value"),
          )
      )

    val actual = deserialize(json)
    assertEquals(expected, actual)
  }

  // Helper

  private fun deserialize(string: String): Any? {
    val rdr = StringReader(string)
    val jsonRdr = JsonObjectReader(rdr)
    return fixture.getSut().deserialize(jsonRdr)
  }
}
