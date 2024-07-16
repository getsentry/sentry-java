package io.sentry.rrweb

import io.sentry.ILogger
import io.sentry.JsonDeserializer
import io.sentry.JsonSerializable
import io.sentry.ObjectReader
import io.sentry.ObjectWriter
import io.sentry.protocol.SerializationUtils.deserializeJson
import io.sentry.protocol.SerializationUtils.sanitizedFile
import io.sentry.protocol.SerializationUtils.serializeToString
import io.sentry.rrweb.RRWebEventType.Custom
import io.sentry.vendor.gson.stream.JsonToken
import org.junit.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class RRWebEventSerializationTest {

    /**
     * Make subclass, as `RRWebEvent` initializers are protected.
     */
    class Sut : RRWebEvent(), JsonSerializable {
        override fun serialize(writer: ObjectWriter, logger: ILogger) {
            writer.beginObject()
            Serializer().serialize(this, writer, logger)
            writer.endObject()
        }

        class Deserializer : JsonDeserializer<Sut> {
            override fun deserialize(reader: ObjectReader, logger: ILogger): Sut {
                val sut = Sut()
                reader.beginObject()

                val baseEventDeserializer = RRWebEvent.Deserializer()
                do {
                    val nextName = reader.nextName()
                    baseEventDeserializer.deserializeValue(sut, nextName, reader, logger)
                } while (reader.hasNext() && reader.peek() == JsonToken.NAME)
                reader.endObject()
                return sut
            }
        }
    }

    class Fixture {
        val logger = mock<ILogger>()

        fun update(rrWebEvent: RRWebEvent) {
            rrWebEvent.apply {
                type = Custom
                timestamp = 9999999
            }
        }
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        val expected = sanitizedFile("json/rrweb_event.json")
        val sut = Sut().apply { fixture.update(this) }
        val actual = serializeToString(sut, fixture.logger)

        assertEquals(expected, actual)
    }

    @Test
    fun deserialize() {
        val expectedJson = sanitizedFile("json/rrweb_event.json")
        val actual = deserializeJson(
            expectedJson,
            Sut.Deserializer(),
            fixture.logger
        )
        val actualJson = serializeToString(actual, fixture.logger)

        assertEquals(expectedJson, actualJson)
    }
}
