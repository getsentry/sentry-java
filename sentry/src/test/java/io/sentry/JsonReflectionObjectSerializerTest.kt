package io.sentry

import org.junit.Test
import kotlin.test.assertEquals

class JsonReflectionObjectSerializerTest {

    class Fixture {
        fun getSut() = JsonReflectionObjectSerializer()
    }

    val fixture = Fixture()

    @Test
    fun `serializes class with primitive fields to map`() {
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
    fun `serializes class with string field to map`() {
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
    fun `serializes class with array field to map`() {
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
    fun `serializes class with collection field to map`() {
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
    fun `serializes class with map field to map`() {
        val objectWithMapField = ClassWithMapField(
            mapOf("fixture-key" to "fixture-value")
        )
        val expected = mapOf(
            "map" to mapOf("fixture-key" to "fixture-value")
        )
        val actual = fixture.getSut().serialize(objectWithMapField)
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
}
