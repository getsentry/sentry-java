package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.vendor.gson.stream.JsonWriter
import org.junit.Test

internal class JsonObjectSerializerTest {

    private inner class Fixture {
        var writer: JsonObjectWriter = mock()
        var logger: ILogger = mock()

        fun getSUT(): JsonObjectSerializer {
            return JsonObjectSerializer()
        }
    }

    private val fixture = Fixture()

    @Test
    fun `serializing null`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, null)
        verify(fixture.writer).nullValue()
    }

    @Test
    fun `serializing string`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, "fixture")
        verify(fixture.writer).value("fixture")
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
    fun `serialize unknown object`() {
        fixture.getSUT().serialize(fixture.writer, fixture.logger, object {})
        verify(fixture.writer).value(JsonObjectSerializer.OBJECT_PLACEHOLDER)
    }
}
