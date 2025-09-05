package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.SentryLockReason
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class SentryStackFrameSerializationTest {
  private class Fixture {
    var logger: ILogger = mock()

    fun getSut() =
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
        addrMode = "49d415f3-1be5-422c-b877-b82b4e4c2990"
        rawFunction = "f33035a4-0cf0-453d-b6f4-d7c27e9af924"
        symbol = "d9807ffe-d517-11ed-afa1-0242ac120002"
        lock =
          SentryLockReason().apply {
            address = "0x0d3a2f0a"
            className = "Object"
            packageName = "java.lang"
            type = SentryLockReason.BLOCKED
            threadId = 11
          }
        preContext =
          listOf<String>(
            "f46ad4c7-a286-4936-a56c-825088227c88",
            "feeda7f3-1530-45c2-b8d8-5d201aaf6ce0",
          )
        postContext =
          listOf<String>(
            "2153c99d-2f17-45f1-a173-69e08cc6a219",
            "0a959b53-6bdf-45d1-93ca-936281d7897a",
            "4e6085a3-1e44-4aa2-b3d9-9b79dca970ed",
          )
        vars =
          mapOf<String, Any?>(
            "int_var" to 42,
            "array_var" to listOf<Any?>(true, 17, "7a3750d1-9177-4ee2-8016-3ba02fa90291"),
            "string_var" to "325ad70e-1a8b-4d2d-ba92-182ee49448b7",
            "object_var" to
              mapOf<String, Any?>(
                "int_prop" to 53,
                "string_prop" to "93db3ec5-b1ec-4e95-b6d0-b9633c9e03cd",
                "bool_prop" to false,
              ),
            "bool_var" to false,
          )
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/sentry_stack_frame.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/sentry_stack_frame.json")
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

  private fun deserialize(json: String): SentryStackFrame {
    val reader = JsonObjectReader(StringReader(json))
    return SentryStackFrame.Deserializer().deserialize(reader, fixture.logger)
  }
}
