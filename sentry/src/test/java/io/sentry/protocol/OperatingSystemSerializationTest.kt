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

class OperatingSystemSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      OperatingSystem().apply {
        name = "686a11a8-eae7-4393-aa10-a1368d523cb2"
        version = "3033f32d-6a27-4715-80c8-b232ce84ca61"
        rawDescription = "eb2d0c5e-f5d4-49c7-b876-d8a654ee87cf"
        build = "bd197b97-eb68-49c3-9d07-ef789caf3069"
        kernelVersion = "1df24aec-3a6f-49a9-8b50-69ae5f9dde08"
        isRooted = true
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/operating_system.json")
    val actual = serializeToString(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/operating_system.json")
    val actual = deserialize(expectedJson)
    val actualJson = serializeToString(actual)
    assertEquals(expectedJson, actualJson)
  }

  // Helper

  private fun sanitizedFile(path: String): String =
    FileFromResources.invoke(path).replace(Regex("[\n\r]"), "").replace(" ", "")

  private fun serializeToString(jsonSerializable: JsonSerializable): String =
    this.serializeToString { wrt -> jsonSerializable.serialize(wrt, fixture.logger) }

  private fun serializeToString(serialize: (JsonObjectWriter) -> Unit): String {
    val wrt = StringWriter()
    val jsonWrt = JsonObjectWriter(wrt, 100)
    serialize(jsonWrt)
    return wrt.toString()
  }

  private fun deserialize(json: String): OperatingSystem {
    val reader = JsonObjectReader(StringReader(json))
    return OperatingSystem.Deserializer().deserialize(reader, fixture.logger)
  }
}
