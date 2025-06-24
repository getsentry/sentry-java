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

class SentryExceptionSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      SentryException().apply {
        type = "32a4ff49-f989-4698-964e-d0f7904026c4"
        value = "c27490af-12c7-4986-a039-0093c309e395"
        module = "dc6bf597-7627-4da5-a7f1-0696d465fd1f"
        threadId = 7583606041296518095
        stacktrace =
          SentryStackTrace().apply {
            frames =
              listOf(
                SentryStackFrame().apply {
                  filename = "81e8dd1c-240b-4fe8-8adf-2bfdc50b711a"
                  function = "e8944b85-094a-4bb3-8c72-8bf94cb6e216"
                  module = "009e5045-861b-4742-9a81-1772919f785c"
                  lineno = -1980831353
                  colno = 58702552
                  absPath = "77064e93-76f6-4652-9da1-50a617a7fb43"
                  contextLine = "0d08ebbd-27f9-40ff-8c93-ad45f8f329da"
                  isInApp = false
                  `package` = "0fc25e57-4aa7-4085-aa0c-25ac7958e6dd"
                  isNative = true
                  platform = "430a10fb-0bc5-449f-b471-6786db7be722"
                  imageAddr = "27ec1be5-e8a1-485c-b020-f4d9f80a6624"
                  symbolAddr = "180e12cd-1fa8-405d-8dd8-e87b33fa2eb0"
                  instructionAddr = "19864a78-2466-461f-9f0b-93a5c9ae7622"
                  rawFunction = "f33035a4-0cf0-453d-b6f4-d7c27e9af924"
                }
              )
            registers =
              mapOf(
                "160eef70-2b1f-4ad8-8449-902bf7bef11b" to "6000b565-2a55-4337-99be-edf8a2668d1b"
              )
            snapshot = true
          }
        mechanism =
          Mechanism().apply {
            type = "5f5e111d-5fd5-41e2-8fcb-2d40eb4e4b32"
            description = "683d3710-ab97-459e-a219-3b72b98aa370"
            helpLink = "bcbf2733-0b75-4491-b837-18f8d63099a5"
            isHandled = false
            meta =
              mapOf(
                "91e0d6d4-0818-403e-9826-6e4443f2b54e" to "11707d85-3cae-4a4c-8157-7a9b717cbe1e"
              )
            data =
              mapOf(
                "0275caba-1fd8-4de3-9ead-b6c8dcdd5666" to "669cc6ad-1435-4233-b199-2800f901bbcd"
              )
            synthetic = false
          }
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/sentry_exception.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/sentry_exception.json")
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

  private fun deserialize(json: String): SentryException {
    val reader = JsonObjectReader(StringReader(json))
    return SentryException.Deserializer().deserialize(reader, fixture.logger)
  }
}
