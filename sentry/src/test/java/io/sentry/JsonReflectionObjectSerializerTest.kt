package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlin.test.assertEquals
import org.junit.Test

class JsonReflectionObjectSerializerTest {

    class Fixture {
        val logger = mock<ILogger>()
        fun getSut() = JsonReflectionObjectSerializer(5)
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
            true
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
        val actual = fixture.getSut().serialize(objectWithPrimitiveFields, fixture.logger)
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize object with string field`() {
        val objectWithPrivateStringField = ClassWithStringField(
            "fixture-string"
        )
        val expected = mapOf(
            "string" to "fixture-string"
        )
        val actual = fixture.getSut().serialize(objectWithPrivateStringField, fixture.logger)
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
        val actual = fixture.getSut().serialize(objectWithArrayField, fixture.logger)
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
        val actual = fixture.getSut().serialize(objectWithCollectionField, fixture.logger)
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
        val actual = fixture.getSut().serialize(objectWithMapField, fixture.logger)
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize object without fields`() {
        val objectWithoutFields = ClassWithoutFields()
        val expected = mapOf<String, Any>()
        val actual = fixture.getSut().serialize(objectWithoutFields, fixture.logger)
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
        val actual = fixture.getSut().serialize(objectGraph, fixture.logger)
        assertEquals(expected, actual)
    }

    @Test
    fun `cycle to object reference is not serialized`() {
        val secondChild = ClassWithNesting("Second Child", null)
        val firstChild = ClassWithNesting("First Child", secondChild)
        val root = ClassWithNesting("Root", firstChild)
        secondChild.child = root // Cycle to root

        val expected = mapOf<String, Any?>(
            "title" to "Root",
            "child" to mapOf<String, Any?>(
                "title" to "First Child",
                "child" to mapOf<String, Any?>(
                    "title" to "Second Child",
                    "child" to "fixture-toString"
                )
            )
        )
        val actual = fixture.getSut().serialize(root, fixture.logger)
        assertEquals(expected, actual)
        verify(fixture.logger).log(SentryLevel.INFO, "Cyclic reference detected. Calling toString() on object.")
    }

    @Test
    fun `cycle to array reference is not serialized`() {
        val firstChild = ClassWithAnyNesting("First Child", null)
        val secondChild = ClassWithAnyNesting("Second Child", null)
        val array = arrayOf<Any>(firstChild, secondChild)
        secondChild.child = array

        val expected = listOf(
            mapOf<String, Any?>(
                "title" to "First Child",
                "child" to null
            ),
            mapOf<String, Any?>(
                "title" to "Second Child",
                "child" to array.toString()
            )
        )
        val actual = fixture.getSut().serialize(array, fixture.logger)
        assertEquals(expected, actual)
        verify(fixture.logger).log(SentryLevel.INFO, "Cyclic reference detected. Calling toString() on object.")
    }

    @Test
    fun `cycle to collection reference is not serialized`() {
        val firstChild = ClassWithAnyNesting("First Child", null)
        val secondChild = ClassWithAnyNesting("Second Child", null)
        val list = mutableListOf<Any>(firstChild, secondChild)
        secondChild.child = list

        val expected = listOf(
            mapOf<String, Any?>(
                "title" to "First Child",
                "child" to null
            ),
            mapOf<String, Any?>(
                "title" to "Second Child",
                "child" to list.toString()
            )
        )
        val actual = fixture.getSut().serialize(list, fixture.logger)
        assertEquals(expected, actual)
        verify(fixture.logger).log(SentryLevel.INFO, "Cyclic reference detected. Calling toString() on object.")
    }

    @Test
    fun `cycle to map reference is not serialized`() {
        val firstChild = ClassWithAnyNesting("First Child", null)
        val secondChild = ClassWithAnyNesting("Second Child", null)
        val map = mapOf(
            "first" to firstChild,
            "second" to secondChild
        )
        secondChild.child = map

        val expected = mapOf<String, Any?>(
            "first" to mapOf<String, Any?>(
                "title" to "First Child",
                "child" to null
            ),
            "second" to mapOf<String, Any?>(
                "title" to "Second Child",
                "child" to map.toString()
            )
        )
        val actual = fixture.getSut().serialize(map, fixture.logger)
        assertEquals(expected, actual)
        verify(fixture.logger).log(SentryLevel.INFO, "Cyclic reference detected. Calling toString() on object.")
    }

    @Test
    fun `a-cyclic multiple references are serialized`() {
        val leaf = ClassWithNesting("Leaf!", null)
        val firstAncestorOfLeaf = ClassWithNesting("A", leaf)
        val secondAncestorOfLeaf = ClassWithNesting("B", leaf)
        val expected = listOf(
            mapOf<String, Any?>(
                "title" to "A",
                "child" to mapOf<String, Any?>(
                    "title" to "Leaf!",
                    "child" to null
                )
            ),
            mapOf<String, Any?>(
                "title" to "B",
                "child" to mapOf<String, Any?>(
                    "title" to "Leaf!",
                    "child" to null
                )
            )
        )
        val actual = fixture.getSut().serialize(listOf(firstAncestorOfLeaf, secondAncestorOfLeaf), fixture.logger)
        assertEquals(expected, actual)
    }

    @Test
    fun `stop serializing when max depth is reached`() {
        val six = ClassWithNesting("6", null)
        val five = ClassWithNesting("5", six)
        val four = ClassWithNesting("4", five)
        val three = ClassWithNesting("3", four)
        val two = ClassWithNesting("2", three)
        val one = ClassWithNesting("1", two)

        val expected = mapOf<String, Any?>(
            "title" to "1",
            "child" to mapOf<String, Any?>(
                "title" to "2",
                "child" to mapOf<String, Any?>(
                    "title" to "3",
                    "child" to mapOf<String, Any?>(
                        "title" to "4",
                        "child" to mapOf<String, Any?>(
                            "title" to "5",
                            "child" to "fixture-toString"
                        )
                    )
                )
            )
        )
        val actual = fixture.getSut().serialize(one, fixture.logger)
        assertEquals(expected, actual)
        verify(fixture.logger).log(SentryLevel.INFO, "Max depth exceeded. Calling toString() on object.")
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
        var child: ClassWithNesting?
    ) {
        override fun toString(): String {
            return "fixture-toString"
        }
    }

    class ClassWithAnyNesting(
        val title: String,
        var child: Any?
    ) {
        override fun toString(): String {
            return "fixture-toString"
        }
    }
}
