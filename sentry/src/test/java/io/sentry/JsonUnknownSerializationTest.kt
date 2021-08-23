package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.protocol.App
import io.sentry.protocol.Device
import io.sentry.protocol.SentryId
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Test serialization/deserialization for all classes implementing JsonUnknown
 */
class JsonUnknownSerializationTest {
    private class Fixture {
        var logger: ILogger = mock()

        fun getUserFeedback(): UserFeedback {
            val eventId = SentryId("c2fb8fee2e2b49758bcb67cda0f713c7")
            return givenJsonUnknown(UserFeedback(eventId))
        }

        fun getApp(): App {
            return givenJsonUnknown(App())
        }

        fun getDevice(): Device {
            return givenJsonUnknown(Device())
        }

        private fun <T : JsonUnknown> givenJsonUnknown(jsonUnknown: T): T {
            return jsonUnknown.apply {
                unknown = mapOf(
                    "fixture-key" to "fixture-value"
                )
            }
        }
    }

    private val fixture = Fixture()

    // UserFeedback

    @Test
    fun `serializing and deserialize user feedback`() {
        val sut = fixture.getUserFeedback()

        val serialized = serialize(sut)
        val deserialized = deserialize(serialized, UserFeedback.Deserializer())

        assertEquals(sut.unknown, deserialized.unknown)
    }

    @Test
    fun `serializing unknown calls json object writer for user feedback`() {
        val writer: JsonObjectWriter = mock()
        val logger: ILogger = mock()
        val sut = fixture.getUserFeedback()

        sut.serialize(writer, logger)

        verify(writer).name("fixture-key")
        verify(writer).value(logger, "fixture-value")
    }

    // App

    @Test
    fun `serializing and deserialize app`() {
        val sut = fixture.getApp()

        val serialized = serialize(sut)
        val deserialized = deserialize(serialized, App.Deserializer())

        assertEquals(sut.unknown, deserialized.unknown)
    }

    @Test
    fun `serializing unknown calls json object writer for app`() {
        val writer: JsonObjectWriter = mock()
        whenever(writer.name(any())).thenReturn(writer)

        val logger: ILogger = mock()
        val sut = fixture.getApp()

        sut.serialize(writer, logger)

        verify(writer).name("fixture-key")
        verify(writer).value(logger, "fixture-value")
    }

    // Device

    @Test
    fun `serializing and deserialize device`() {
        val sut = fixture.getDevice()

        val serialized = serialize(sut)
        val deserialized = deserialize(serialized, Device.Deserializer())

        assertEquals(sut.unknown, deserialized.unknown)
    }

    @Test
    fun `serializing unknown calls json object writer for device`() {
        val writer: JsonObjectWriter = mock()
        whenever(writer.name(any())).thenReturn(writer)

        val logger: ILogger = mock()
        val sut = fixture.getDevice()

        sut.serialize(writer, logger)

        verify(writer).name("fixture-key")
        verify(writer).value(logger, "fixture-value")
    }

    // Helper

    private fun serialize(jsonSerializable: JsonSerializable): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt)
        jsonSerializable.serialize(jsonWrt, fixture.logger)
        return wrt.toString()
    }

    private fun <T> deserialize(json: String, deserializer: JsonDeserializer<T>): T {
        val reader = JsonObjectReader(StringReader(json))
        return deserializer.deserialize(reader, fixture.logger)
    }
}
