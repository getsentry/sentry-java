package io.sentry

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.protocol.App
import io.sentry.protocol.Browser
import io.sentry.protocol.Device
import io.sentry.protocol.Gpu
import io.sentry.protocol.OperatingSystem
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryRuntime
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Test serialization/deserialization for all classes implementing JsonUnknown
 */
class JsonUnknownSerializationTest {
    private class Fixture {
        val logger = mock<ILogger>()

        fun getApp() = givenJsonUnknown(App())
        fun getBrowser() = givenJsonUnknown(Browser())
        fun getDevice() = givenJsonUnknown(Device())
        fun getGpu() = givenJsonUnknown(Gpu())
        fun getOperatingSystem() = givenJsonUnknown(OperatingSystem())
        fun getSentryRuntime() = givenJsonUnknown(SentryRuntime())
        fun getSpanContext(): SpanContext {
            val operation = "c2fb8fee2e2b49758bcb67cda0f713c7"
            return givenJsonUnknown(SpanContext(operation))
        }
        fun getUserFeedback(): UserFeedback {
            val eventId = SentryId("c2fb8fee2e2b49758bcb67cda0f713c7")
            return givenJsonUnknown(UserFeedback(eventId))
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

    // Browser

    @Test
    fun `serializing and deserialize browser`() {
        val sut = fixture.getBrowser()

        val serialized = serialize(sut)
        val deserialized = deserialize(serialized, Browser.Deserializer())

        assertEquals(sut.unknown, deserialized.unknown)
    }

    @Test
    fun `serializing unknown calls json object writer for browser`() {
        val writer: JsonObjectWriter = mock()
        val logger: ILogger = mock()
        val sut = fixture.getBrowser()

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

    // GPU

    @Test
    fun `serializing and deserialize gpu`() {
        val sut = fixture.getGpu()

        val serialized = serialize(sut)
        val deserialized = deserialize(serialized, Gpu.Deserializer())

        assertEquals(sut.unknown, deserialized.unknown)
    }

    @Test
    fun `serializing unknown calls json object writer for gpu`() {
        val writer: JsonObjectWriter = mock()
        val logger: ILogger = mock()
        val sut = fixture.getGpu()

        sut.serialize(writer, logger)

        verify(writer).name("fixture-key")
        verify(writer).value(logger, "fixture-value")
    }

    // OperatingSystem

    @Test
    fun `serializing and deserialize operating system`() {
        val sut = fixture.getOperatingSystem()

        val serialized = serialize(sut)
        val deserialized = deserialize(serialized, OperatingSystem.Deserializer())

        assertEquals(sut.unknown, deserialized.unknown)
    }

    @Test
    fun `serializing unknown calls json object writer for operating system`() {
        val writer: JsonObjectWriter = mock()
        val logger: ILogger = mock()
        val sut = fixture.getOperatingSystem()

        sut.serialize(writer, logger)

        verify(writer).name("fixture-key")
        verify(writer).value(logger, "fixture-value")
    }

    // SentryRuntime

    @Test
    fun `serializing and deserialize sentry runtime`() {
        val sut = fixture.getSentryRuntime()

        val serialized = serialize(sut)
        val deserialized = deserialize(serialized, SentryRuntime.Deserializer())

        assertEquals(sut.unknown, deserialized.unknown)
    }

    @Test
    fun `serializing unknown calls json object writer for sentry runtime`() {
        val writer: JsonObjectWriter = mock()
        whenever(writer.name(any())).thenReturn(writer)
        val logger: ILogger = mock()
        val sut = fixture.getUserFeedback()

        sut.serialize(writer, logger)

        verify(writer).name("fixture-key")
        verify(writer).value(logger, "fixture-value")
    }

    // SpanContext

    @Test
    fun `serializing and deserialize span context`() {
        val sut = fixture.getSpanContext()

        val serialized = serialize(sut)
        val deserialized = deserialize(serialized, SpanContext.Deserializer())

        assertEquals(sut.unknown, deserialized.unknown)
    }

    @Test
    fun `serializing unknown calls json object writer for span context`() {
        val writer: JsonObjectWriter = mock()
        whenever(writer.name(any())).thenReturn(writer)
        val logger: ILogger = mock()
        val sut = fixture.getSpanContext()

        sut.serialize(writer, logger)

        verify(writer).name("fixture-key")
        verify(writer).value(logger, "fixture-value")
    }

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
        whenever(writer.name(any())).thenReturn(writer)
        val logger: ILogger = mock()
        val sut = fixture.getUserFeedback()

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
