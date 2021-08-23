package io.sentry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.protocol.Gpu
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

        fun getGpu(): Gpu {
            return givenJsonUnknown(Gpu())
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
