package io.sentry.protocol

import io.sentry.FileFromResources
import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.JsonSerializable
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class ViewHierarchyNodeSerializationTest {

    private class Fixture {
        val logger = mock<ILogger>()

        fun getSut() = ViewHierarchyNode().apply {
            setType("com.example.ui.FancyButton")
            setIdentifier("button_logout")
            setChildren(
                listOf(
                    ViewHierarchyNode().apply {
                        setRenderingSystem("compose")
                        setType("Clickable")
                    }
                )
            )
            setWidth(100.0)
            setHeight(200.0)
            setX(0.0)
            setY(2.0)
            setVisible(true)
            setAlpha(1.0)
            unknown = mapOf(
                "extra_property" to 42
            )
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

    private fun sanitizedFile(path: String): String {
        return FileFromResources.invoke(path)
            .replace(Regex("[\n\r]"), "")
            .replace(" ", "")
    }

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
