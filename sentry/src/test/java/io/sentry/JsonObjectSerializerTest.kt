package io.sentry

import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.net.URI
import java.util.Calendar
import java.util.Currency
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray

internal class JsonObjectSerializerTest {

    private inner class Fixture {
        val writer = mock<JsonObjectWriter>()
        val logger = mock<ILogger>()

        fun getSUT(): JsonObjectSerializer {
            return JsonObjectSerializer(100)
        }
    }

    private val fixture = Fixture()

    // region primitives

    @Test
    fun `serializing null`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, null)
        verify(fixture.writer).nullValue()
    }

    @Test
    fun `serializing bool`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, true)
        verify(fixture.writer).value(true)
    }

    @Test
    fun `serializing int`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, 1337)
        verify(fixture.writer).value(1337 as Number)
    }

    @Test
    fun `serializing long`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, 1337L)
        verify(fixture.writer).value(1337L as Number)
    }

    @Test
    fun `serializing double`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, 9.9)
        verify(fixture.writer).value(9.9 as Number)
    }

    @Test
    fun `serializing char`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, 'c')
        verify(fixture.writer).value("c")
    }

    // endregion

    @Test
    fun `serializing string`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, "fixture")
        verify(fixture.writer).value("fixture")
    }

    @Test
    fun `serializing date`() {
        val dateIsoFormat = "2021-08-05T15:15:15.000Z"
        val date = DateUtils.getDateTime(dateIsoFormat)
        fixture.getSUT().serialize(fixture.writer, fixture.logger, date)
        verify(fixture.writer).value(dateIsoFormat)
    }

    @Test
    fun `serializing timezone`() {
        val id = "Europe/Vienna"
        val timezone = TimeZone.getTimeZone(id)
        fixture.getSUT().serialize(fixture.writer, fixture.logger, timezone)
        verify(fixture.writer).value(id)
    }

    @Test
    fun `serializing collection`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, listOf("fixture"))
        verify(fixture.writer).beginArray()
        verify(fixture.writer).value("fixture")
        verify(fixture.writer).endArray()
    }

    @Test
    fun `serializing array`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, arrayOf("fixture"))
        verify(fixture.writer).beginArray()
        verify(fixture.writer).value("fixture")
        verify(fixture.writer).endArray()
    }

    @Test
    fun `serialize map`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, mapOf("fixture-key" to "fixture-value"))
        verify(fixture.writer).beginObject()
        verify(fixture.writer).name("fixture-key")
        verify(fixture.writer).value("fixture-value")
        verify(fixture.writer).endObject()
    }

    @Test
    fun `serialize json serializable`() {
        val jsonSerializable: JsonSerializable = mock()
        fixture.getSUT().serialize(fixture.writer, fixture.logger, jsonSerializable)
        verify(jsonSerializable).serialize(fixture.writer, fixture.logger)
    }

    @Test
    fun `serialize unknown object without data`() {
        val value = object {}
        fixture.getSUT().serialize(fixture.writer, fixture.logger, value)
        verify(fixture.writer).value(value.toString())
    }

    @Test
    fun `serialize enum`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, DataCategory.Session)
        verify(fixture.writer).value("Session")
    }

    @Test
    fun `serialize list of enum`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, listOf(DataCategory.Session))
        verify(fixture.writer).beginArray()
        verify(fixture.writer).value("Session")
        verify(fixture.writer).endArray()
    }

    @Test
    fun `serialize map of enum`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, mapOf("key" to DataCategory.Transaction))
        verify(fixture.writer).beginObject()
        verify(fixture.writer).name("key")
        verify(fixture.writer).value("Transaction")
        verify(fixture.writer).endObject()
    }

    @Test
    fun `serialize object with enum property`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, ClassWithEnumProperty(DataCategory.Attachment))
        verify(fixture.writer).beginObject()
        verify(fixture.writer).name("enumProperty")
        verify(fixture.writer).value("Attachment")
        verify(fixture.writer).endObject()
    }

    @Test
    fun `serialize unknown object with data`() {
        val objectWithPrimitiveFields = UnknownClassWithData(
            17,
            "fixtureString"
        )
        fixture.getSUT().serialize(fixture.writer, fixture.logger, objectWithPrimitiveFields)
        verify(fixture.writer).beginObject()
        verify(fixture.writer).name("integer")
        verify(fixture.writer).value(17 as Number)
        verify(fixture.writer).name("string")
        verify(fixture.writer).value("fixtureString")
        verify(fixture.writer).endObject()
    }

    @Test
    fun `serialize locale`() {
        val inOrder = inOrder(fixture.writer)
        fixture.getSUT().serialize(fixture.writer, fixture.logger, Locale.US)

        inOrder.verify(fixture.writer).value("en_US")
    }

    @Test
    fun `serialize locale in map`() {
        val map = mapOf<String, Locale>("one" to Locale.US)
        val inOrder = inOrder(fixture.writer)
        fixture.getSUT().serialize(fixture.writer, fixture.logger, map)
        inOrder.verify(fixture.writer).beginObject()
        inOrder.verify(fixture.writer).name("one")
        inOrder.verify(fixture.writer).value("en_US")
        inOrder.verify(fixture.writer).endObject()
    }

    @Test
    fun `serialize locale in list`() {
        val list = listOf<Locale>(Locale.US, Locale.GERMAN)
        val inOrder = inOrder(fixture.writer)
        fixture.getSUT().serialize(fixture.writer, fixture.logger, list)
        inOrder.verify(fixture.writer).beginArray()
        inOrder.verify(fixture.writer).value("en_US")
        inOrder.verify(fixture.writer).value("de")
        inOrder.verify(fixture.writer).endArray()
    }

    @Test
    fun `serialize locale in object`() {
        val obj = ClassWithLocaleProperty(Locale.US)
        val inOrder = inOrder(fixture.writer)
        fixture.getSUT().serialize(fixture.writer, fixture.logger, obj)
        inOrder.verify(fixture.writer).beginObject()
        inOrder.verify(fixture.writer).name("localeProperty")
        inOrder.verify(fixture.writer).value("en_US")
        inOrder.verify(fixture.writer).endObject()
    }

    @Test
    fun `serializing AtomicIntegerArray`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, AtomicIntegerArray(arrayOf(1, 2, 3).toIntArray()))
        verify(fixture.writer).beginArray()
        verify(fixture.writer).value(1 as Number)
        verify(fixture.writer).value(2 as Number)
        verify(fixture.writer).value(3 as Number)
        verify(fixture.writer).endArray()
    }

    @Test
    fun `serializing AtomicBoolean`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, AtomicBoolean(true))
        verify(fixture.writer).value(true)
    }

    @Test
    fun `serializing URI`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, URI("http://localhost:8081/api/product?id=99"))
        verify(fixture.writer).value("http://localhost:8081/api/product?id=99")
    }

    @Test
    fun `serializing UUID`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, "828900a5-15dc-413f-8c17-6ef04d74e074")
        verify(fixture.writer).value("828900a5-15dc-413f-8c17-6ef04d74e074")
    }

    @Test
    fun `serializing Currency`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, Currency.getInstance("USD"))
        verify(fixture.writer).value("USD")
    }

    @Test
    fun `serializing Calendar`() {
        val calendar = Calendar.getInstance()
        calendar.set(2022, 0, 1, 11, 59, 58)
        fixture.getSUT().serialize(fixture.writer, fixture.logger, calendar)
        verify(fixture.writer).beginObject()
        verify(fixture.writer).name("year")
        verify(fixture.writer).value(2022 as java.lang.Integer)
        verify(fixture.writer).name("month")
        verify(fixture.writer).value(0 as java.lang.Integer)
        verify(fixture.writer).name("dayOfMonth")
        verify(fixture.writer).value(1 as java.lang.Integer)
        verify(fixture.writer).name("hourOfDay")
        verify(fixture.writer).value(11 as java.lang.Integer)
        verify(fixture.writer).name("minute")
        verify(fixture.writer).value(59 as java.lang.Integer)
        verify(fixture.writer).name("second")
        verify(fixture.writer).value(58 as java.lang.Integer)
        verify(fixture.writer).endObject()
    }

    class UnknownClassWithData(
        private val integer: Int,
        private val string: String
    )
}

data class ClassWithEnumProperty(val enumProperty: DataCategory)
data class ClassWithLocaleProperty(val localeProperty: Locale)
