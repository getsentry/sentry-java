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

class SentryThreadSerializationTest {
  class Fixture {
    var logger: ILogger = mock()

    fun getSut() =
      SentryThread().apply {
        id = 1033645608864915956
        priority = -57895510
        name = "cf31c7e1-22ce-4543-ba56-ca524c53a5ed"
        state = "3cd6b62e-06e0-4429-b39d-338e917fa6ce"
        isCrashed = false
        isCurrent = false
        isDaemon = true
        isMain = true
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
                "160eef70-2b1f-4ad8-8449-902bf7bef11b" to "6000b565-2a55-4337-99be-edf8a2668d1b",
                "e7a3db0b-8cad-4eab-8315-03d5dc2edcd9" to "8bba0819-ac58-4e5c-bec7-32e1033b7bdf",
              )
            snapshot = true
            heldLocks =
              mapOf(
                "0x0d3a2f0a" to
                  SentryLockReason().apply {
                    address = "0x0d3a2f0a"
                    className = "Object"
                    packageName = "java.lang"
                    type = SentryLockReason.BLOCKED
                    threadId = 11
                  }
              )
          }
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/sentry_thread.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/sentry_thread.json")
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

  private fun deserialize(json: String): SentryThread {
    val reader = JsonObjectReader(StringReader(json))
    return SentryThread.Deserializer().deserialize(reader, fixture.logger)
  }
}
