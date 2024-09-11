package io.sentry.protocol

import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.SentryItemType
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals

class SentryItemTypeSerializationTest {

    class Fixture {
        val logger = mock<ILogger>()
    }
    private val fixture = Fixture()

    @Test
    fun serialize() {
        assertEquals(serialize(SentryItemType.Session), json("session"))
        assertEquals(serialize(SentryItemType.Event), json("event"))
        assertEquals(serialize(SentryItemType.UserFeedback), json("user_report"))
        assertEquals(serialize(SentryItemType.Attachment), json("attachment"))
        assertEquals(serialize(SentryItemType.Transaction), json("transaction"))
        assertEquals(serialize(SentryItemType.Profile), json("profile"))
        assertEquals(serialize(SentryItemType.ClientReport), json("client_report"))
        assertEquals(serialize(SentryItemType.ReplayEvent), json("replay_event"))
        assertEquals(serialize(SentryItemType.ReplayRecording), json("replay_recording"))
        assertEquals(serialize(SentryItemType.ReplayVideo), json("replay_video"))
        assertEquals(serialize(SentryItemType.CheckIn), json("check_in"))
        assertEquals(serialize(SentryItemType.Statsd), json("statsd"))
        assertEquals(serialize(SentryItemType.Feedback), json("feedback"))
    }

    @Test
    fun deserialize() {
        assertEquals(deserialize(json("session")), SentryItemType.Session)
        assertEquals(deserialize(json("event")), SentryItemType.Event)
        assertEquals(deserialize(json("user_report")), SentryItemType.UserFeedback)
        assertEquals(deserialize(json("attachment")), SentryItemType.Attachment)
        assertEquals(deserialize(json("transaction")), SentryItemType.Transaction)
        assertEquals(deserialize(json("profile")), SentryItemType.Profile)
        assertEquals(deserialize(json("client_report")), SentryItemType.ClientReport)
        assertEquals(deserialize(json("replay_event")), SentryItemType.ReplayEvent)
        assertEquals(deserialize(json("replay_recording")), SentryItemType.ReplayRecording)
        assertEquals(deserialize(json("replay_video")), SentryItemType.ReplayVideo)
        assertEquals(deserialize(json("check_in")), SentryItemType.CheckIn)
        assertEquals(deserialize(json("statsd")), SentryItemType.Statsd)
        assertEquals(deserialize(json("feedback")), SentryItemType.Feedback)
    }

    private fun json(type: String): String {
        return "{\"type\":\"${type}\"}"
    }

    private fun serialize(src: SentryItemType): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        jsonWrt.beginObject()
        jsonWrt.name("type")
        src.serialize(jsonWrt, fixture.logger)
        jsonWrt.endObject()
        return wrt.toString()
    }

    private fun deserialize(json: String): SentryItemType {
        val reader = JsonObjectReader(StringReader(json))
        reader.beginObject()
        reader.nextName()
        return SentryItemType.Deserializer().deserialize(reader, fixture.logger)
    }
}
