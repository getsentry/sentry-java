package io.sentry.protocol

import com.google.common.truth.Truth.assertThat
import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.mockito.kotlin.mock

class AnrFlameGraphSerializationTest {
  private class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      AnrFlameGraph(
          "1",
          "java",
          listOf(frame("main", 10), frame("work", 20)),
          listOf(
            AnrFlameGraphNode(0, 3, mutableListOf(1)),
            AnrFlameGraphNode(1, 2, mutableListOf()),
          ),
          listOf(
            AnrFlameGraphTree(listOf(0)).apply {
              threadId = "1"
              threadAttributed = true
            }
          ),
        )
        .apply {
          debugMeta =
            DebugMeta().apply {
              images =
                listOf(
                  DebugImage().apply {
                    type = DebugImage.PROGUARD
                    uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                  }
                )
            }
        }

    private fun frame(function: String, lineno: Int) =
      SentryStackFrame().apply {
        filename = "App.java"
        this.function = function
        module = "com.example.App"
        this.lineno = lineno
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/anr_flame_graph.json")
    val actual = serialize(fixture.getSut())
    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/anr_flame_graph.json")
    val actual = deserialize(expectedJson)
    val actualJson = serialize(actual)
    assertThat(actualJson).isEqualTo(expectedJson)
  }

  @Test
  fun `deserializes a node without children as a node with no children`() {
    val node =
      AnrFlameGraphNode.Deserializer()
        .deserialize(
          JsonObjectReader(StringReader("""{"frame_id":1,"sample_count":2}""")),
          fixture.logger,
        )

    assertThat(node.children).isEmpty()
  }

  @Test
  fun `keeps unknown fields`() {
    val json = """{"version":"1","platform":"java","frames":[],"nodes":[],"trees":[],"extra":"x"}"""

    assertThat(serialize(deserialize(json))).isEqualTo(json)
  }

  @Test
  fun `fails to deserialize without a required field`() {
    assertFailsWith<IllegalStateException> {
      deserialize("""{"version":"1","platform":"java","frames":[],"nodes":[]}""")
    }
  }

  private fun sanitizedFile(path: String): String =
    FileFromResources.invoke(path).replace(Regex("[\n\r]"), "").replace(" ", "")

  private fun serialize(jsonSerializable: JsonSerializable): String {
    val wrt = StringWriter()
    val jsonWrt = JsonObjectWriter(wrt, 100)
    jsonSerializable.serialize(jsonWrt, fixture.logger)
    return wrt.toString()
  }

  private fun deserialize(json: String): AnrFlameGraph {
    val reader = JsonObjectReader(StringReader(json))
    return AnrFlameGraph.Deserializer().deserialize(reader, fixture.logger)
  }
}
