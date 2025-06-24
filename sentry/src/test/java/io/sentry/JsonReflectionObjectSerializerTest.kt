package io.sentry

import java.net.URI
import java.util.Calendar
import java.util.Currency
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class JsonReflectionObjectSerializerTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() = JsonReflectionObjectSerializer(5)
  }

  val fixture = Fixture()

  @Test
  fun `serialize object with primitive fields`() {
    val objectWithPrimitiveFields = ClassWithPrimitiveFields(17, 3, 'x', 9001, 0.9f, 0.99, true)
    val expected =
      mapOf(
        "byte" to 17.toByte(),
        "short" to 3.toShort(),
        "char" to "x",
        "integer" to 9001,
        "float" to 0.9f,
        "double" to 0.99,
        "boolean" to true,
      )
    val actual = fixture.getSut().serialize(objectWithPrimitiveFields, fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun `serialize object with string field`() {
    val objectWithPrivateStringField = ClassWithStringField("fixture-string")
    val expected = mapOf("string" to "fixture-string")
    val actual = fixture.getSut().serialize(objectWithPrivateStringField, fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun `serialize object with array field`() {
    val objectWithArrayField = ClassWithArrayField(arrayOf("fixture-string"))
    val expected = mapOf("array" to listOf("fixture-string"))
    val actual = fixture.getSut().serialize(objectWithArrayField, fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun `serialize object with collection field`() {
    val objectWithCollectionField = ClassWithCollectionField(listOf("fixture-string"))
    val expected = mapOf("array" to listOf("fixture-string"))
    val actual = fixture.getSut().serialize(objectWithCollectionField, fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun `serialize object with map field`() {
    val objectWithMapField = ClassWithMapField(mapOf("fixture-key" to "fixture-value"))
    val expected = mapOf("map" to mapOf("fixture-key" to "fixture-value"))
    val actual = fixture.getSut().serialize(objectWithMapField, fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun `serialize object without fields using toString`() {
    val objectWithoutFields = ClassWithoutFields()
    val expected = mapOf<String, Any>("toString" to "")
    val actual = fixture.getSut().serialize(objectWithoutFields, fixture.logger)
    assertEquals(objectWithoutFields.toString(), actual)
  }

  @Test
  fun `serialize nested object`() {
    val objectGraph = ClassWithNesting("Root", ClassWithNesting("Child", null))
    val expected =
      mapOf<String, Any?>(
        "title" to "Root",
        "child" to mapOf<String, Any?>("title" to "Child", "child" to null),
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

    val expected =
      mapOf<String, Any?>(
        "title" to "Root",
        "child" to
          mapOf<String, Any?>(
            "title" to "First Child",
            "child" to mapOf<String, Any?>("title" to "Second Child", "child" to "fixture-toString"),
          ),
      )
    val actual = fixture.getSut().serialize(root, fixture.logger)
    assertEquals(expected, actual)
    verify(fixture.logger)
      .log(SentryLevel.INFO, "Cyclic reference detected. Calling toString() on object.")
  }

  @Test
  fun `cycle to array reference is not serialized`() {
    val firstChild = ClassWithAnyNesting("First Child", null)
    val secondChild = ClassWithAnyNesting("Second Child", null)
    val array = arrayOf<Any>(firstChild, secondChild)
    secondChild.child = array

    val expected =
      listOf(
        mapOf<String, Any?>("title" to "First Child", "child" to null),
        mapOf<String, Any?>("title" to "Second Child", "child" to array.toString()),
      )
    val actual = fixture.getSut().serialize(array, fixture.logger)
    assertEquals(expected, actual)
    verify(fixture.logger)
      .log(SentryLevel.INFO, "Cyclic reference detected. Calling toString() on object.")
  }

  @Test
  fun `cycle to collection reference is not serialized`() {
    val firstChild = ClassWithAnyNesting("First Child", null)
    val secondChild = ClassWithAnyNesting("Second Child", null)
    val list = mutableListOf<Any>(firstChild, secondChild)
    secondChild.child = list

    val expected =
      listOf(
        mapOf<String, Any?>("title" to "First Child", "child" to null),
        mapOf<String, Any?>("title" to "Second Child", "child" to list.toString()),
      )
    val actual = fixture.getSut().serialize(list, fixture.logger)
    assertEquals(expected, actual)
    verify(fixture.logger)
      .log(SentryLevel.INFO, "Cyclic reference detected. Calling toString() on object.")
  }

  @Test
  fun `cycle to map reference is not serialized`() {
    val firstChild = ClassWithAnyNesting("First Child", null)
    val secondChild = ClassWithAnyNesting("Second Child", null)
    val map = mapOf("first" to firstChild, "second" to secondChild)
    secondChild.child = map

    val expected =
      mapOf<String, Any?>(
        "first" to mapOf<String, Any?>("title" to "First Child", "child" to null),
        "second" to mapOf<String, Any?>("title" to "Second Child", "child" to map.toString()),
      )
    val actual = fixture.getSut().serialize(map, fixture.logger)
    assertEquals(expected, actual)
    verify(fixture.logger)
      .log(SentryLevel.INFO, "Cyclic reference detected. Calling toString() on object.")
  }

  @Test
  fun `a-cyclic multiple references are serialized`() {
    val leaf = ClassWithNesting("Leaf!", null)
    val firstAncestorOfLeaf = ClassWithNesting("A", leaf)
    val secondAncestorOfLeaf = ClassWithNesting("B", leaf)
    val expected =
      listOf(
        mapOf<String, Any?>(
          "title" to "A",
          "child" to mapOf<String, Any?>("title" to "Leaf!", "child" to null),
        ),
        mapOf<String, Any?>(
          "title" to "B",
          "child" to mapOf<String, Any?>("title" to "Leaf!", "child" to null),
        ),
      )
    val actual =
      fixture.getSut().serialize(listOf(firstAncestorOfLeaf, secondAncestorOfLeaf), fixture.logger)
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

    val expected =
      mapOf<String, Any?>(
        "title" to "1",
        "child" to
          mapOf<String, Any?>(
            "title" to "2",
            "child" to
              mapOf<String, Any?>(
                "title" to "3",
                "child" to
                  mapOf<String, Any?>(
                    "title" to "4",
                    "child" to mapOf<String, Any?>("title" to "5", "child" to "fixture-toString"),
                  ),
              ),
          ),
      )
    val actual = fixture.getSut().serialize(one, fixture.logger)
    assertEquals(expected, actual)
    verify(fixture.logger)
      .log(SentryLevel.INFO, "Max depth exceeded. Calling toString() on object.")
  }

  @Test
  fun `enum`() {
    val actual = fixture.getSut().serialize(DataCategory.Error, fixture.logger)
    assertEquals("Error", actual)
  }

  @Test
  fun `locale`() {
    val actual = fixture.getSut().serialize(Locale.US, fixture.logger)
    assertEquals("en_US", actual)
  }

  @Test
  fun `AtomicIntegerArray is serialized`() {
    val actual =
      fixture.getSut().serialize(AtomicIntegerArray(arrayOf(1, 2, 3).toIntArray()), fixture.logger)
    assertEquals(listOf(1, 2, 3), actual)
  }

  @Test
  fun `AtomicBoolean is serialized`() {
    val actual = fixture.getSut().serialize(AtomicBoolean(true), fixture.logger)
    assertEquals(true, actual)
  }

  @Test
  fun `StringBuffer is serialized`() {
    val sb = StringBuffer()
    sb.append("hello")
    sb.append(" ")
    sb.append("world")
    val actual = fixture.getSut().serialize(sb, fixture.logger)
    assertEquals("hello world", actual)
  }

  @Test
  fun `StringBuilder is serialized`() {
    val sb = StringBuilder()
    sb.append("hello")
    sb.append(" ")
    sb.append("world")
    val actual = fixture.getSut().serialize(sb, fixture.logger)
    assertEquals("hello world", actual)
  }

  @Test
  fun `URI is serialized`() {
    val actual =
      fixture.getSut().serialize(URI("http://localhost:8081/api/product?id=99"), fixture.logger)
    assertEquals("http://localhost:8081/api/product?id=99", actual)
  }

  @Test
  fun `UUID is serialized`() {
    val actual = fixture.getSut().serialize("828900a5-15dc-413f-8c17-6ef04d74e074", fixture.logger)
    assertEquals("828900a5-15dc-413f-8c17-6ef04d74e074", actual)
  }

  @Test
  fun `Currency is serialized`() {
    val actual = fixture.getSut().serialize(Currency.getInstance("USD"), fixture.logger)
    assertEquals("USD", actual)
  }

  @Test
  fun `Calendar is serialized`() {
    val calendar = Calendar.getInstance()
    calendar.set(2022, 0, 1, 11, 59, 58)
    val actual = fixture.getSut().serialize(calendar, fixture.logger)
    val expected =
      mapOf<String, Any?>(
        "month" to 0,
        "year" to 2022,
        "dayOfMonth" to 1,
        "hourOfDay" to 11,
        "minute" to 59,
        "second" to 58,
      )
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
    private val boolean: Boolean,
  )

  class ClassWithStringField(private val string: String)

  class ClassWithArrayField(private val array: Array<String>)

  class ClassWithCollectionField(private val array: Collection<String>)

  class ClassWithMapField(private val map: Map<String, String>)

  class ClassWithoutFields

  class ClassWithNesting(val title: String, var child: ClassWithNesting?) {
    override fun toString(): String = "fixture-toString"
  }

  class ClassWithAnyNesting(val title: String, var child: Any?) {
    override fun toString(): String = "fixture-toString"
  }
}
