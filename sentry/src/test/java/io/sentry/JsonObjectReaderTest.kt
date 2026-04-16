package io.sentry

import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class JsonObjectReaderTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut(jsonString: String): JsonObjectReader = JsonObjectReader(StringReader(jsonString))
  }

  val fixture = Fixture()

  private val throwingValueDeserializer =
    JsonDeserializer<String> { reader, _ ->
      reader.beginObject()
      reader.nextName()
      val value = reader.nextString()
      if (value == "fail") {
        throw IllegalStateException("intentional")
      }
      reader.endObject()
      value
    }

  private val postParseThrowingValueDeserializer =
    JsonDeserializer<String> { reader, _ ->
      reader.beginObject()
      reader.nextName()
      val value = reader.nextString()
      reader.endObject()
      if (value == "fail") {
        throw IllegalStateException("intentional")
      }
      value
    }

  private fun getValuesReader(jsonValue: String): JsonObjectReader =
    fixture.getSut("{\"values\": $jsonValue}").apply {
      beginObject()
      nextName()
    }

  private fun <T> assertNextMapOrNullRecoversAfterFailedPrimitiveRead(
    badValue: String,
    goodValue: String,
    expectedValue: T,
    deserializer: JsonDeserializer<T>,
  ) {
    val actual =
      getValuesReader("{\"bad\": $badValue, \"good\": $goodValue}")
        .nextMapOrNull(fixture.logger, deserializer)

    assertEquals(mapOf("good" to expectedValue), actual)
  }

  // nextStringOrNull

  @Test
  fun `returns null for null string`() {
    val jsonString = "{\"key\": null}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertNull(reader.nextStringOrNull())
  }

  @Test
  fun `returns string for non-null string`() {
    val jsonString = "{\"key\": \"value\"}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertEquals("value", reader.nextStringOrNull())
  }

  // nextDoubleOrNull

  @Test
  fun `returns null for null double`() {
    val jsonString = "{\"key\": null}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertNull(reader.nextDoubleOrNull())
  }

  @Test
  fun `returns double for non-null double`() {
    val jsonString = "{\"key\": 1.0}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertEquals(1.0, reader.nextDoubleOrNull())
  }

  // nextLongOrNull

  @Test
  fun `returns null for null long`() {
    val jsonString = "{\"key\": null}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertNull(reader.nextLongOrNull())
  }

  @Test
  fun `returns long for non-null long`() {
    val jsonString = "{\"key\": 9223372036854775807}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertEquals(9223372036854775807, reader.nextLongOrNull())
  }

  // nextIntegerOrNull

  @Test
  fun `returns null for null integer`() {
    val jsonString = "{\"key\": null}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertNull(reader.nextIntegerOrNull())
  }

  @Test
  fun `returns integer for non-null integer`() {
    val jsonString = "{\"key\": 2147483647}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertEquals(2147483647, reader.nextIntegerOrNull())
  }

  // nextBooleanOrNull

  @Test
  fun `returns null for null boolean`() {
    val jsonString = "{\"key\": null}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertNull(reader.nextBooleanOrNull())
  }

  @Test
  fun `returns boolean for non-null boolean`() {
    val jsonString = "{\"key\": true}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertEquals(true, reader.nextBooleanOrNull())
  }

  // nextList

  @Test
  fun `returns list of deserializables for list`() {
    val deserializableA = "{\"foo\": \"foo\", \"bar\": \"bar\"}"
    val deserializableB = "{\"foo\": \"fooo\", \"bar\": \"baar\"}"
    val jsonString = "{\"deserializable\": [$deserializableA,$deserializableB]}"
    val reader = fixture.getSut(jsonString)
    val logger = mock<ILogger>()
    reader.beginObject()
    reader.nextName()

    val expected = listOf(Deserializable("foo", "bar"), Deserializable("fooo", "baar"))
    val actual = reader.nextListOrNull(logger, Deserializable.Deserializer())
    assertEquals(expected, actual)
  }

  @Test
  fun `returns empty list for empty list`() {
    val jsonString = "{\"deserializable\": []}"
    val reader = fixture.getSut(jsonString)
    val logger = mock<ILogger>()
    reader.beginObject()
    reader.nextName()

    val expected = emptyList<Deserializable>()
    val actual = reader.nextListOrNull(logger, Deserializable.Deserializer())
    assertEquals(expected, actual)
    verify(fixture.logger, never()).log(any(), any(), any<Throwable>())
  }

  // nextMap

  @Test
  fun `returns null for null map`() {
    val jsonString = "{\"key\": null}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertNull(reader.nextMapOrNull(fixture.logger, Deserializable.Deserializer()))
  }

  @Test
  fun `returns map of deserializables`() {
    val deserializableA = "{\"foo\": \"foo\", \"bar\": \"bar\"}"
    val deserializableB = "{\"foo\": \"fooo\", \"bar\": \"baar\"}"
    val jsonString = "{\"deserializable\": { \"a\":$deserializableA,\"b\":$deserializableB}}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    val expected = mapOf("a" to Deserializable("foo", "bar"), "b" to Deserializable("fooo", "baar"))
    val actual = reader.nextMapOrNull(fixture.logger, Deserializable.Deserializer())
    assertEquals(expected, actual)
  }

  @Test
  fun `returns empty map`() {
    val jsonString = "{\"deserializable\": {}}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    val expected = mapOf<String, Deserializable>()
    val actual = reader.nextMapOrNull(fixture.logger, Deserializable.Deserializer())
    assertEquals(expected, actual)
    verify(fixture.logger, never()).log(any(), any(), any<Throwable>())
  }

  @Test
  fun `nextListOrNull skips a failing element`() {
    val actual =
      getValuesReader("[{\"value\": \"fail\"}]")
        .nextListOrNull(fixture.logger, throwingValueDeserializer)

    assertEquals(emptyList(), actual)
  }

  @Test
  fun `nextListOrNull skips an unconsumed failing element`() {
    var callCount = 0
    val deserializer =
      JsonDeserializer<String> { reader, logger ->
        if (callCount++ == 0) {
          throw IllegalStateException("intentional")
        }
        throwingValueDeserializer.deserialize(reader, logger)
      }

    val actual =
      getValuesReader("[{\"value\": \"ignored\"}, {\"value\": \"two\"}]")
        .nextListOrNull(fixture.logger, deserializer)

    assertEquals(listOf("two"), actual)
  }

  @Test
  fun `nextListOrNull keeps elements before a failing element`() {
    val actual =
      getValuesReader("[{\"value\": \"one\"}, {\"value\": \"fail\"}]")
        .nextListOrNull(fixture.logger, throwingValueDeserializer)

    assertEquals(listOf("one"), actual)
  }

  @Test
  fun `nextListOrNull keeps elements after a failing element`() {
    val actual =
      getValuesReader("[{\"value\": \"fail\"}, {\"value\": \"two\"}]")
        .nextListOrNull(fixture.logger, throwingValueDeserializer)

    assertEquals(listOf("two"), actual)
  }

  @Test
  fun `nextListOrNull keeps elements after a fully consumed failing element`() {
    val actual =
      getValuesReader("[{\"value\": \"fail\"}, {\"value\": \"two\"}]")
        .nextListOrNull(fixture.logger, postParseThrowingValueDeserializer)

    assertEquals(listOf("two"), actual)
  }

  @Test
  fun `nextListOrNull keeps elements after skipValue consumes a failing element`() {
    var callCount = 0
    val deserializer =
      JsonDeserializer<String> { reader, logger ->
        if (callCount++ == 0) {
          reader.skipValue()
          throw IllegalStateException("intentional")
        }
        throwingValueDeserializer.deserialize(reader, logger)
      }

    val actual =
      getValuesReader("[{\"value\": \"ignored\"}, {\"value\": \"two\"}]")
        .nextListOrNull(fixture.logger, deserializer)

    assertEquals(listOf("two"), actual)
  }

  @Test
  fun `nextMapOrNull skips a failing value`() {
    val actual =
      getValuesReader("{\"bad\": {\"value\": \"fail\"}}")
        .nextMapOrNull(fixture.logger, throwingValueDeserializer)

    assertEquals(emptyMap(), actual)
  }

  @Test
  fun `nextMapOrNull recovers after failed primitive reads`() {
    assertNextMapOrNullRecoversAfterFailedPrimitiveRead(
      badValue = "true",
      goodValue = "2",
      expectedValue = 2,
      deserializer = JsonDeserializer { reader, _ -> reader.nextInt() },
    )
    assertNextMapOrNullRecoversAfterFailedPrimitiveRead(
      badValue = "true",
      goodValue = "2",
      expectedValue = 2L,
      deserializer = JsonDeserializer { reader, _ -> reader.nextLong() },
    )
    assertNextMapOrNullRecoversAfterFailedPrimitiveRead(
      badValue = "true",
      goodValue = "\"two\"",
      expectedValue = "two",
      deserializer = JsonDeserializer { reader, _ -> reader.nextString() },
    )
    assertNextMapOrNullRecoversAfterFailedPrimitiveRead(
      badValue = "1",
      goodValue = "false",
      expectedValue = false,
      deserializer = JsonDeserializer { reader, _ -> reader.nextBoolean() },
    )
    assertNextMapOrNullRecoversAfterFailedPrimitiveRead(
      badValue = "true",
      goodValue = "2.5",
      expectedValue = 2.5,
      deserializer = JsonDeserializer { reader, _ -> reader.nextDouble() },
    )
    assertNextMapOrNullRecoversAfterFailedPrimitiveRead(
      badValue = "true",
      goodValue = "null",
      expectedValue = Unit,
      deserializer = JsonDeserializer { reader, _ -> reader.nextNull() },
    )
    assertNextMapOrNullRecoversAfterFailedPrimitiveRead(
      badValue = "true",
      goodValue = "2.5",
      expectedValue = 2.5f,
      deserializer = JsonDeserializer { reader, _ -> reader.nextFloat() },
    )
  }

  @Test
  fun `nextMapOrNull keeps values before a failing value`() {
    val actual =
      getValuesReader("{\"good\": {\"value\": \"one\"}, \"bad\": {\"value\": \"fail\"}}")
        .nextMapOrNull(fixture.logger, throwingValueDeserializer)

    assertEquals(mapOf("good" to "one"), actual)
  }

  @Test
  fun `nextMapOrNull keeps values after a failing value`() {
    val actual =
      getValuesReader("{\"bad\": {\"value\": \"fail\"}, \"good\": {\"value\": \"two\"}}")
        .nextMapOrNull(fixture.logger, throwingValueDeserializer)

    assertEquals(mapOf("good" to "two"), actual)
  }

  @Test
  fun `nextMapOfListOrNull skips a failing value`() {
    val actual =
      getValuesReader("{\"bad\": {\"value\": \"fail\"}}")
        .nextMapOfListOrNull(fixture.logger, throwingValueDeserializer)

    assertEquals(emptyMap(), actual)
  }

  @Test
  fun `nextMapOfListOrNull keeps values before a failing value`() {
    val actual =
      getValuesReader("{\"good\": [{\"value\": \"one\"}], \"bad\": {\"value\": \"fail\"}}")
        .nextMapOfListOrNull(fixture.logger, throwingValueDeserializer)

    assertEquals(mapOf("good" to listOf("one")), actual)
  }

  @Test
  fun `nextMapOfListOrNull keeps values after a failing value`() {
    val actual =
      getValuesReader("{\"bad\": {\"value\": \"fail\"}, \"good\": [{\"value\": \"two\"}]}")
        .nextMapOfListOrNull(fixture.logger, throwingValueDeserializer)

    assertEquals(mapOf("good" to listOf("two")), actual)
  }

  @Test
  fun `nextMapOfListOrNull keeps nested values after skipValue consumes a failing element`() {
    var callCount = 0
    val deserializer =
      JsonDeserializer<String> { reader, logger ->
        if (callCount++ == 0) {
          reader.skipValue()
          throw IllegalStateException("intentional")
        }
        throwingValueDeserializer.deserialize(reader, logger)
      }

    val actual =
      getValuesReader("{\"good\": [{\"value\": \"ignored\"}, {\"value\": \"two\"}]}")
        .nextMapOfListOrNull(fixture.logger, deserializer)

    assertEquals(mapOf("good" to listOf("two")), actual)
  }

  @Test
  fun `nextListOrNull logs and aborts when recovery fails`() {
    assertFailsWith<Exception> {
      fixture
        .getSut("[{\"value\": \"fail\"")
        .nextListOrNull(fixture.logger, throwingValueDeserializer)
    }

    verify(fixture.logger)
      .log(
        eq(SentryLevel.ERROR),
        eq("Stream unrecoverable, aborting list deserialization."),
        any<Throwable>(),
      )
  }

  @Test
  fun `nextMapOrNull logs and aborts when recovery fails`() {
    assertFailsWith<Exception> {
      fixture
        .getSut("{\"bad\": {\"value\": \"fail\"")
        .nextMapOrNull(fixture.logger, throwingValueDeserializer)
    }

    verify(fixture.logger)
      .log(
        eq(SentryLevel.ERROR),
        eq("Stream unrecoverable, aborting map deserialization."),
        any<Throwable>(),
      )
  }

  @Test
  fun `nextMapOfListOrNull logs and aborts when recovery fails`() {
    assertFailsWith<Exception> {
      fixture
        .getSut("{\"bad\": [{\"value\": \"fail\"")
        .nextMapOfListOrNull(fixture.logger, throwingValueDeserializer)
    }

    verify(fixture.logger)
      .log(
        eq(SentryLevel.ERROR),
        eq("Stream unrecoverable, aborting map-of-lists deserialization."),
        any<Throwable>(),
      )
  }

  @Test
  fun `nextUnknown logs when recovery fails`() {
    val unknown = mutableMapOf<String, Any>()
    val reader = fixture.getSut("{\"key\": {\"value\": \"fail\"")
    reader.beginObject()
    val name = reader.nextName()

    reader.nextUnknown(fixture.logger, unknown, name)

    assertEquals(emptyMap(), unknown)
    verify(fixture.logger)
      .log(
        eq(SentryLevel.ERROR),
        any<Throwable>(),
        eq("Error deserializing unknown key: %s"),
        eq("key"),
      )
    verify(fixture.logger)
      .log(
        eq(SentryLevel.ERROR),
        eq("Stream unrecoverable after unknown key deserialization failure."),
        any<Throwable>(),
      )
    verifyNoMoreInteractions(fixture.logger)
  }

  // nextDateOrNull

  @Test
  fun `returns null for null date`() {
    val jsonString = "{\"key\": null}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertNull(reader.nextDateOrNull(fixture.logger))
  }

  @Test
  fun `returns date for iso date`() {
    val dateIsoFormat = "2000-12-31T23:59:58.000Z"
    val jsonString = "{\"key\": \"${dateIsoFormat}\"}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    val expected = DateUtils.getDateTime(dateIsoFormat)
    val actual = reader.nextDateOrNull(fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun `returns date date for timestamp date`() {
    val dateTimestampFormat = "1581410911"
    val jsonString = "{\"key\": \"${dateTimestampFormat}\"}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    val expected = DateUtils.getDateTimeWithMillisPrecision(dateTimestampFormat)
    val actual = reader.nextDateOrNull(fixture.logger)
    assertEquals(expected, actual)
    verify(fixture.logger, never()).log(any(), any(), any<Throwable>())
  }

  @Test
  fun `returns date for timestamp date with mills precision`() {
    val dateTimestampWithMillis = "1581410911.988"
    val jsonString = "{\"key\": \"${dateTimestampWithMillis}\"}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    val expected = DateUtils.getDateTimeWithMillisPrecision(dateTimestampWithMillis)
    val actual = reader.nextDateOrNull(fixture.logger)
    assertEquals(expected, actual)
    verify(fixture.logger, never()).log(any(), any(), any<Throwable>())
  }

  // nextTimeZoneOrNull

  @Test
  fun `returns null for null timezone`() {
    val jsonString = "{\"key\": null}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertNull(reader.nextTimeZoneOrNull(fixture.logger))
  }

  @Test
  fun `when deserializing a timezone ID string, it should become a Device-TimeZone`() {
    val jsonString = "{\"timezone\": \"Europe/Vienna\"}"
    val reader = fixture.getSut(jsonString)
    reader.beginObject()
    reader.nextName()

    assertEquals("Europe/Vienna", reader.nextTimeZoneOrNull(fixture.logger)?.id)
  }

  data class Deserializable(var foo: String? = null, var bar: String? = null) {
    class Deserializer : JsonDeserializer<Deserializable> {
      override fun deserialize(reader: ObjectReader, logger: ILogger): Deserializable =
        Deserializable().apply {
          reader.beginObject()
          reader.nextName()
          foo = reader.nextStringOrNull()
          reader.nextName()
          bar = reader.nextStringOrNull()
          reader.endObject()
        }
    }
  }
}
