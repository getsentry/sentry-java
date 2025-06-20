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

class ViewHierarchyNodeSerializationTest {
  private class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      ViewHierarchyNode().apply {
        type = "com.example.ui.FancyButton"
        identifier = "button_logout"
        children =
          listOf(
            ViewHierarchyNode().apply {
              renderingSystem = "compose"
              type = "Clickable"
            }
          )
        width = 100.0
        height = 200.0
        x = 0.0
        y = 2.0
        visibility = "visible"
        alpha = 1.0
        unknown = mapOf("extra_property" to 42)
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/view_hierarchy_node.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/view_hierarchy_node.json")
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

  private fun deserialize(json: String): ViewHierarchyNode {
    val reader = JsonObjectReader(StringReader(json))
    return ViewHierarchyNode.Deserializer().deserialize(reader, fixture.logger)
  }
}
