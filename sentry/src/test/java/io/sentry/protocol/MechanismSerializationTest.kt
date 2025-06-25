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

class MechanismSerializationTest {
  private class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      Mechanism().apply {
        type = "5f5e111d-5fd5-41e2-8fcb-2d40eb4e4b32"
        description = "683d3710-ab97-459e-a219-3b72b98aa370"
        helpLink = "bcbf2733-0b75-4491-b837-18f8d63099a5"
        isHandled = false
        meta =
          mapOf("91e0d6d4-0818-403e-9826-6e4443f2b54e" to "11707d85-3cae-4a4c-8157-7a9b717cbe1e")
        data =
          mapOf("0275caba-1fd8-4de3-9ead-b6c8dcdd5666" to "669cc6ad-1435-4233-b199-2800f901bbcd")
        synthetic = false
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/mechanism.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/mechanism.json")
    val actual = deserialize(expectedJson)
    val actualJson = serialize(actual)
    assertEquals(expectedJson, actualJson)
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

  private fun deserialize(json: String): Mechanism {
    val reader = JsonObjectReader(StringReader(json))
    return Mechanism.Deserializer().deserialize(reader, fixture.logger)
  }
}
