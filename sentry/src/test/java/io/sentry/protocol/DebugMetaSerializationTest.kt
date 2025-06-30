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

class DebugMetaSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      DebugMeta().apply {
        sdkInfo =
          SdkInfo().apply {
            sdkName = "182c4407-c1e1-4427-9b5a-ad2e22b1046a"
            versionMajor = 2045114005
            versionMinor = 1436566288
            versionPatchlevel = 1637914973
          }
        images =
          listOf(
            DebugImage().apply {
              uuid = "8994027e-1cd9-4be8-b611-88ce08cf16e6"
              type = "fd6e053b-a7fe-4754-916e-bfb3ab77177d"
              debugId = "8c653f5a-3418-4823-ba91-29a84c9c1235"
              debugFile = "55cc15dd-51f3-4cad-803c-6fd90eac21f6"
              codeId = "01230ece-f729-4af4-8b48-df74700aa4bf"
              codeFile = "415c8995-1cb4-4bed-ba5c-5b3d6ba1ad47"
              imageAddr = "8a258c81-641d-4e54-b06e-a0f56b1ee2ef"
              imageSize = -7905338721846826571L
              arch = "d00d5bea-fb5c-43c9-85f0-dc1350d957a4"
            }
          )
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/debug_meta.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/debug_meta.json")
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

  private fun deserialize(json: String): DebugMeta {
    val reader = JsonObjectReader(StringReader(json))
    return DebugMeta.Deserializer().deserialize(reader, fixture.logger)
  }
}
