package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import io.sentry.protocol.profiling.SentryProfile
import io.sentry.protocol.profiling.SentrySample
import io.sentry.protocol.profiling.SentryThreadMetadata
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class SentryProfileSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      SentryProfile().apply {
        samples =
          listOf(
            SentrySample().apply {
              timestamp = 1753439655.387274
              threadId = "57"
              stackId = 0
            },
            SentrySample().apply {
              timestamp = 1753439655.415672
              threadId = "57"
              stackId = 1
            },
          )
        stacks = listOf(listOf(0, 1, 2), listOf(3, 4))
        frames =
          listOf(
            SentryStackFrame().apply {
              filename = "sun.nio.ch.Net"
              function = "accept"
              module = "sun.nio.ch.Net"
            },
            SentryStackFrame().apply {
              filename = "org.apache.tomcat.util.net.NioEndpoint"
              function = "serverSocketAccept"
              module = "org.apache.tomcat.util.net.NioEndpoint"
              lineno = 519
            },
            SentryStackFrame().apply {
              filename = "java.lang.Thread"
              function = "run"
              module = "java.lang.Thread"
              lineno = 840
            },
            SentryStackFrame().apply {
              filename = "io.sentry.samples.spring.boot.jakarta.quartz.SampleJob"
              function = "execute"
              module = "io.sentry.samples.spring.boot.jakarta.quartz.SampleJob"
              lineno = 14
              isInApp = true
            },
            SentryStackFrame().apply {
              filename = ""
              function = "Unsafe_Park"
              module = ""
              isInApp = false
            },
          )
        threadMetadata =
          mapOf(
            "57" to
              SentryThreadMetadata().apply {
                name = "http-nio-8080-Acceptor"
                priority = 0
              }
          )
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/sentry_profile.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/sentry_profile.json")
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

  private fun deserialize(json: String): SentryProfile {
    val reader = JsonObjectReader(StringReader(json))
    return SentryProfile.Deserializer().deserialize(reader, fixture.logger)
  }
}
