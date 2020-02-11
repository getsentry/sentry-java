package io.sentry.android.core

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.nhaarman.mockitokotlin2.mock
import io.sentry.core.DateUtils
import io.sentry.core.SentryEvent
import io.sentry.core.SentryLevel
import io.sentry.core.protocol.Contexts
import io.sentry.core.protocol.Device
import java.io.StringReader
import java.io.StringWriter
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidSerializerTest {

    private val serializer = AndroidSerializer(mock())

    private fun serializeToString(ev: SentryEvent): String {
        val wrt = StringWriter()
        serializer.serialize(ev, wrt)
        return wrt.toString()
    }

    @Test
    fun `when serializing SentryEvent-SentryId object, it should become a event_id json without dashes`() {
        val sentryEvent = generateEmptySentryEvent(null)

        val actual = serializeToString(sentryEvent)

        val expected = "{\"event_id\":\"${sentryEvent.eventId}\"}"

        assertEquals(expected, actual)
    }

    @Test
    fun `when deserializing event_id, it should become a SentryEvent-SentryId uuid`() {
        val expected = UUID.randomUUID().toString().replace("-", "")
        val jsonEvent = "{\"event_id\":\"$expected\"}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals(expected, actual.eventId.toString())
    }

    @Test
    fun `when serializing SentryEvent-Date, it should become a timestamp json ISO format`() {
        val dateIsoFormat = "2000-12-31T23:59:58Z"
        val sentryEvent = generateEmptySentryEvent(DateUtils.getDateTime(dateIsoFormat))
        sentryEvent.eventId = null

        val expected = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = serializeToString(sentryEvent)

        assertEquals(expected, actual)
    }

    @Test
    fun `when deserializing timestamp, it should become a SentryEvent-Date`() {
        val dateIsoFormat = "2000-12-31T23:59:58Z"
        val expected = DateUtils.getDateTime(dateIsoFormat)

        val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals(expected, actual.timestamp)
    }

    @Test
    fun `when deserializing mills timestamp, it should become a SentryEvent-Date`() {
        val dateIsoFormat = "1581410911"
        val expected = DateUtils.getDateTimeWithMillsPrecision(dateIsoFormat)

        val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals(expected, actual.timestamp)
    }

    @Test
    fun `when deserializing mills timestamp with mills precision, it should become a SentryEvent-Date`() {
        val dateIsoFormat = "1581410911.988"
        val expected = DateUtils.getDateTimeWithMillsPrecision(dateIsoFormat)

        val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals(expected, actual.timestamp)
    }

    @Test
    fun `when deserializing unknown properties, it should be added to unknown field`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val jsonEvent = "{\"string\":\"test\",\"int\":1,\"boolean\":true}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals("test", (actual.unknown["string"] as JsonPrimitive).asString)
        assertEquals(1, (actual.unknown["int"] as JsonPrimitive).asInt)
        assertEquals(true, (actual.unknown["boolean"] as JsonPrimitive).asBoolean)
    }

    @Test
    fun `when deserializing unknown properties with nested objects, it should be added to unknown field`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val objects = hashMapOf<String, Any>()
        objects["int"] = 1
        objects["boolean"] = true

        val unknown = hashMapOf<String, Any>()
        unknown["object"] = objects
        sentryEvent.acceptUnknownProperties(unknown)

        val jsonEvent = "{\"object\":{\"int\":1,\"boolean\":true}}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        val hashMapActual = actual.unknown["object"] as JsonObject // gson creates it as JsonObject

        assertEquals(true, hashMapActual.get("boolean").asBoolean)
        assertEquals(1, (hashMapActual.get("int")).asInt)
    }

    @Test
    fun `when serializing unknown field, it should become unknown as json format`() {
        val sentryEvent = generateEmptySentryEvent(null)
        sentryEvent.eventId = null

        val objects = hashMapOf<String, Any>()
        objects["int"] = 1
        objects["boolean"] = true

        val unknown = hashMapOf<String, Any>()
        unknown["object"] = objects

        sentryEvent.acceptUnknownProperties(unknown)

        val actual = serializeToString(sentryEvent)

        val expected = "{\"unknown\":{\"object\":{\"boolean\":true,\"int\":1}}}"

        assertEquals(expected, actual)
    }

    @Test
    fun `when serializing a TimeZone, it should become a timezone ID string`() {
        val sentryEvent = generateEmptySentryEvent(null)
        sentryEvent.eventId = null
        val device = Device()
        device.timezone = TimeZone.getTimeZone("Europe/Vienna")
        val contexts = Contexts()
        contexts.device = device
        sentryEvent.contexts = contexts

        val expected = "{\"contexts\":{\"device\":{\"timezone\":\"Europe/Vienna\"}}}"

        val actual = serializeToString(sentryEvent)

        assertEquals(expected, actual)
    }

    @Test
    fun `when deserializing a timezone ID string, it should become a Device-TimeZone`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val jsonEvent = "{\"contexts\":{\"device\":{\"timezone\":\"Europe/Vienna\"}}}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals("Europe/Vienna", actual.contexts.device.timezone.id)
    }

    @Test
    fun `when serializing a DeviceOrientation, it should become an orientation string`() {
        val sentryEvent = generateEmptySentryEvent(null)
        sentryEvent.eventId = null
        val device = Device()
        device.orientation = Device.DeviceOrientation.LANDSCAPE
        val contexts = Contexts()
        contexts.device = device
        sentryEvent.contexts = contexts

        val expected = "{\"contexts\":{\"device\":{\"orientation\":\"landscape\"}}}"

        val actual = serializeToString(sentryEvent)

        assertEquals(expected, actual)
    }

    @Test
    fun `when deserializing an orientation string, it should become a DeviceOrientation`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val jsonEvent = "{\"contexts\":{\"device\":{\"orientation\":\"landscape\"}}}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals(Device.DeviceOrientation.LANDSCAPE, actual.contexts.device.orientation)
    }

    @Test
    fun `when serializing a SentryLevel, it should become a sentry level string`() {
        val sentryEvent = generateEmptySentryEvent(null)
        sentryEvent.eventId = null
        sentryEvent.level = SentryLevel.DEBUG

        val expected = "{\"level\":\"debug\"}"

        val actual = serializeToString(sentryEvent)

        assertEquals(expected, actual)
    }

    @Test
    fun `when deserializing a sentry level string, it should become a SentryLevel`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val jsonEvent = "{\"level\":\"debug\"}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals(SentryLevel.DEBUG, actual.level)
    }

    @Test
    fun `when deserializing a event with breadcrumbs containing data, it should become have breadcrumbs`() {
        val jsonEvent = FileFromResources.invoke("event_breadcrumb_data.json")

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals(2, actual.breadcrumbs.size)
    }

    @Test
    fun `when theres a null value, gson wont blow up`() {
        val json = FileFromResources.invoke("event.json")
        val event = serializer.deserializeEvent(StringReader(json))
        assertNull(event.user)
    }

    private fun generateEmptySentryEvent(date: Date? = null): SentryEvent {
        return SentryEvent(date).apply {
            contexts = null
        }
    }
}
