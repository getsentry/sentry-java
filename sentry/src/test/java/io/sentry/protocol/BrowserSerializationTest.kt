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

class BrowserSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      Browser().apply {
        name = "e1c723db-7408-4043-baa7-f4e96234e5dc"
        version = "724a48e9-2d35-416b-9f79-132beba2473a"
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/browser.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/browser.json")
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

  private fun deserialize(json: String): Browser {
    val reader = JsonObjectReader(StringReader(json))
    return Browser.Deserializer().deserialize(reader, fixture.logger)
  }
}
