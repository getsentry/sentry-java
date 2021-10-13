package io.sentry

import org.junit.Test
import kotlin.test.assertEquals

class JsonReflectionObjectSerializerTest {

    class Fixture {
        fun getSut() = JsonReflectionObjectSerializer()
    }

    val fixture = Fixture()

    @Test
    fun `serialize object with primitive fields`() {
        val objectWithPrimitiveFields = ClassWithPrimitiveFields(
            17,
            3,
            'x',
            9001,
            0.9f,
            0.99,
            true,
        )
        val expected = mapOf(
            "byte" to 17,
            "short" to 3,
            "char" to "x",
            "integer" to 9001,
            "float" to 0.9f,
            "double" to 0.99,
            "boolean" to true
        )
        val actual = fixture.getSut().serialize(objectWithPrimitiveFields)
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize object with string field`() {
        val objectWithPrivateStringField = ClassWithStringField(
            "fixture-string",
        )
        val expected = mapOf(
            "string" to "fixture-string",
        )
        val actual = fixture.getSut().serialize(objectWithPrivateStringField)
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize object with array field`() {
        val objectWithArrayField = ClassWithArrayField(
            arrayOf("fixture-string")
        )
        val expected = mapOf(
            "array" to listOf("fixture-string")
        )
        val actual = fixture.getSut().serialize(objectWithArrayField)
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize object with collection field`() {
        val objectWithCollectionField = ClassWithCollectionField(
            listOf("fixture-string")
        )
        val expected = mapOf(
            "array" to listOf("fixture-string")
        )
        val actual = fixture.getSut().serialize(objectWithCollectionField)
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize object with map field`() {
        val objectWithMapField = ClassWithMapField(
            mapOf("fixture-key" to "fixture-value")
        )
        val expected = mapOf(
            "map" to mapOf("fixture-key" to "fixture-value")
        )
        val actual = fixture.getSut().serialize(objectWithMapField)
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize object without fields`() {
        val objectWithoutFields = ClassWithoutFields()
        val expected = mapOf<String, Any>()
        val actual = fixture.getSut().serialize(objectWithoutFields)
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize nested object`() {
        val objectGraph = ClassWithNesting(
            "Root",
            ClassWithNesting("Child", null)
        )
        val expected = mapOf<String, Any?>(
            "title" to "Root",
            "child" to mapOf<String, Any?>(
                "title" to "Child",
                "child" to null
            )
        )
        val actual = fixture.getSut().serialize(objectGraph)
        assertEquals(expected, actual)
    }

    // Helper

    class ClassWithPrimitiveFields(
        private val byte: Byte,
        private val short: Short,
        private val char: Char,
        private val integer: Int,
        private val float: Float,
        private val double: Double,
        private val boolean: Boolean
    )

    class ClassWithStringField(
        private val string: String
    )

    class ClassWithArrayField(
        private val array: Array<String>
    )

    class ClassWithCollectionField(
        private val array: Collection<String>
    )

    class ClassWithMapField(
        private val map: Map<String, String>
    )

    class ClassWithoutFields

    class ClassWithNesting(
        val title: String,
        val child: ClassWithNesting?
    )
}
