package io.sentry.android.core

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.sentry.core.DateUtils
import io.sentry.core.SentryEvent
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidSerializerTest {

    private val serializer = AndroidSerializer()

    @Test
    fun `when serializing SentryEvent-SentryId object, it should become a event_id json without dashes`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.timestamp = null

        val actual = serializer.serialize(sentryEvent)

        val expected = "{\"event_id\":\"${sentryEvent.eventId}\"}"

        assertEquals(expected, actual)
    }

    @Test
    fun `when deserializing event_id, it should become a SentryEvent-SentryId uuid`() {
        val expected = UUID.randomUUID().toString().replace("-", "")
        val jsonEvent = "{\"event_id\":\"$expected\"}"

        val actual = serializer.deserializeEvent(jsonEvent)

        assertEquals(expected, actual.eventId.toString())
    }

    @Test
    fun `when serializing SentryEvent-Date, it should become a timestamp json ISO format`() {
        val sentryEvent = generateEmptySentryEvent()
        val dateIsoFormat = "2000-12-31T23:59:58Z"
        sentryEvent.eventId = null
        sentryEvent.timestamp = DateUtils.getDateTime(dateIsoFormat)

        val expected = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = serializer.serialize(sentryEvent)

        assertEquals(expected, actual)
    }

    @Test
    fun `when deserializing timestamp, it should become a SentryEvent-Date`() {
        val sentryEvent = generateEmptySentryEvent()
        val dateIsoFormat = "2000-12-31T23:59:58Z"
        sentryEvent.eventId = null
        val expected = DateUtils.getDateTime(dateIsoFormat)
        sentryEvent.timestamp = expected

        val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = serializer.deserializeEvent(jsonEvent)

        assertEquals(expected, actual.timestamp)
    }

    @Test
    fun `when deserializing unknown properties, it should be added to unknown field`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null
        sentryEvent.timestamp = null

        val jsonEvent = "{\"string\":\"test\",\"int\":1,\"boolean\":true}"

        val actual = serializer.deserializeEvent(jsonEvent)

        assertEquals("test", (actual.unknown["string"] as JsonPrimitive).asString)
        assertEquals(1, (actual.unknown["int"] as JsonPrimitive).asInt)
        assertEquals(true, (actual.unknown["boolean"] as JsonPrimitive).asBoolean)
    }

    @Test
    fun `when deserializing unknown properties with nested objects, it should be added to unknown field`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null
        sentryEvent.timestamp = null

        val objects = hashMapOf<String, Any>()
        objects["int"] = 1
        objects["boolean"] = true

        val unknown = hashMapOf<String, Any>()
        unknown["object"] = objects
        sentryEvent.acceptUnknownProperties(unknown)

        val jsonEvent = "{\"object\":{\"int\":1,\"boolean\":true}}"

        val actual = serializer.deserializeEvent(jsonEvent)

        val hashMapActual = actual.unknown["object"] as JsonObject // gson creates it as JsonObject

        assertEquals(true, hashMapActual.get("boolean").asBoolean)
        assertEquals(1, (hashMapActual.get("int")).asInt)
    }

    @Test
    fun `when serializing unknown field, it should become unknown as json format`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null
        sentryEvent.timestamp = null

        val objects = hashMapOf<String, Any>()
        objects["int"] = 1
        objects["boolean"] = true

        val unknown = hashMapOf<String, Any>()
        unknown["object"] = objects

        sentryEvent.acceptUnknownProperties(unknown)

        val actual = serializer.serialize(sentryEvent)

        val expected = "{\"unknown\":{\"object\":{\"boolean\":true,\"int\":1}}}"

        assertEquals(expected, actual)
    }

    private fun generateEmptySentryEvent(): SentryEvent {
        return SentryEvent().apply {
            setBreadcrumbs(null)
            setTags(null)
            setExtra(null)
            fingerprint = null
            contexts = null
        }
    }
}
