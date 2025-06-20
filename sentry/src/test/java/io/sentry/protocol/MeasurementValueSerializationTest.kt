package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class MeasurementValueSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    // float cannot represent 0.3 correctly
    // https://docs.oracle.com/cd/E19957-01/806-3568/ncg_goldberg.html
    fun getSut(value: Number = 0.30000001192092896, unit: String = "test") =
      MeasurementValue(value, unit, mapOf<String, Any>("new_type" to "newtype"))
  }

  private val fixture = Fixture()

  @Test
  fun `serialize double`() {
    val expected = sanitizedFile("json/measurement_value_double.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun `deserialize double`() {
    val expectedJson = sanitizedFile("json/measurement_value_double.json")
    val actual = deserialize(expectedJson)
    val actualJson = serialize(actual)
    assertEquals(expectedJson, actualJson)
  }

  @Test
  fun `serialize int`() {
    val expected = sanitizedFile("json/measurement_value_int.json")
    val actual = serialize(fixture.getSut(4))
    assertEquals(expected, actual)
  }

  @Test
  fun `deserialize int`() {
    val expectedJson = sanitizedFile("json/measurement_value_int.json")
    val actual = deserialize(expectedJson)
    val actualJson = serialize(actual)
    assertEquals(expectedJson, actualJson)
  }

  @Test(expected = IllegalStateException::class)
  fun `deserialize missing value`() {
    val expectedJson = sanitizedFile("json/measurement_value_missing.json")
    deserialize(expectedJson)
  }

  // Helper

  private fun sanitizedFile(path: String): String =
    FileFromResources.invoke(path).replace(Regex("[\n\r]"), "").replace(" ", "")

  private fun serialize(jsonSerializable: JsonSerializable): String {
    val wrt = StringWriter()
    val jsonWrt = JsonObjectWriter(wrt, 100)
    jsonSerializable.serialize(jsonWrt, fixture.logger)
    return wrt.toString()
  }

  private fun deserialize(json: String): MeasurementValue {
    val reader = JsonObjectReader(StringReader(json))
    return MeasurementValue.Deserializer().deserialize(reader, fixture.logger)
  }
}
