package io.sentry.core

import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.core.protocol.Contexts
import io.sentry.core.protocol.Device
import io.sentry.core.protocol.SdkVersion
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.io.StringWriter
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GsonSerializerTest {

    private val serializer = GsonSerializer(mock(), EnvelopeReader())

    private fun serializeToString(ev: SentryEvent): String {
        val wrt = StringWriter()
        serializer.serialize(ev, wrt)
        return wrt.toString()
    }

    private fun serializeToString(session: Session): String {
        val wrt = StringWriter()
        serializer.serialize(session, wrt)
        return wrt.toString()
    }

    private fun serializeToString(envelope: SentryEnvelope): String {
        val wrt = StringWriter()
        serializer.serialize(envelope, wrt)
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

        assertEquals(expected, actual!!.eventId.toString())
    }

    @Test
    fun `when serializing SentryEvent-Date, it should become a timestamp json ISO format`() {
        val dateIsoFormat = "2000-12-31T23:59:58.000Z"
        val sentryEvent = generateEmptySentryEvent(DateUtils.getDateTime(dateIsoFormat))
        sentryEvent.eventId = null

        val expected = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = serializeToString(sentryEvent)

        assertEquals(expected, actual)
    }

    @Test
    fun `when deserializing timestamp, it should become a SentryEvent-Date`() {
        val dateIsoFormat = "2000-12-31T23:59:58.000Z"
        val expected = DateUtils.getDateTime(dateIsoFormat)

        val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals(expected, actual!!.timestamp)
    }

    @Test
    fun `when deserializing mills timestamp, it should become a SentryEvent-Date`() {
        val dateIsoFormat = "1581410911"
        val expected = DateUtils.getDateTimeWithMillisPrecision(dateIsoFormat)

        val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals(expected, actual!!.timestamp)
    }

    @Test
    fun `when deserializing mills timestamp with mills precision, it should become a SentryEvent-Date`() {
        val dateIsoFormat = "1581410911.988"
        val expected = DateUtils.getDateTimeWithMillisPrecision(dateIsoFormat)

        val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals(expected, actual!!.timestamp)
    }

    @Test
    fun `when deserializing mills timestamp with mills precision, it should be UTC`() {
        // Jun 7, 2020 12:38:12 PM UTC
        val dateIsoFormat = "1591533492.631"
        val actual = DateUtils.getDateTimeWithMillisPrecision(dateIsoFormat)

        val expected = DateUtils.getTimestamp(actual)

        assertEquals("2020-06-07T12:38:12.631Z", expected)
    }

    @Test
    fun `when deserializing unknown properties, it should be added to unknown field`() {
        val sentryEvent = generateEmptySentryEvent()
        sentryEvent.eventId = null

        val jsonEvent = "{\"string\":\"test\",\"int\":1,\"boolean\":true}"

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals("test", (actual!!.unknown["string"] as JsonPrimitive).asString)
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

        val hashMapActual = actual!!.unknown["object"] as JsonObject // gson creates it as JsonObject

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

        assertEquals("Europe/Vienna", actual!!.contexts.device.timezone.id)
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

        assertEquals(Device.DeviceOrientation.LANDSCAPE, actual!!.contexts.device.orientation)
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

        assertEquals(SentryLevel.DEBUG, actual!!.level)
    }

    @Test
    fun `when deserializing a event with breadcrumbs containing data, it should become have breadcrumbs`() {
        val jsonEvent = FileFromResources.invoke("event_breadcrumb_data.json")

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))

        assertEquals(2, actual!!.breadcrumbs.size)
    }

    @Test
    fun `when deserializing a event with custom contexts, they should be set in the event contexts`() {
        val jsonEvent = FileFromResources.invoke("event_with_contexts.json")

        val actual = serializer.deserializeEvent(StringReader(jsonEvent))
        val obj = actual!!.contexts["object"] as Map<*, *>
        val number = actual.contexts["number"] as Double
        val list = actual.contexts["list"] as List<*>
        val listObjects = actual.contexts["list_objects"] as List<*>

        assertTrue(obj["boolean"] as Boolean)
        assertEquals("hi", obj["string"] as String)
        assertEquals(9.0, obj["number"] as Double)

        assertEquals(50.0, number)

        assertEquals(1.0, list[0])
        assertEquals(2.0, list[1])

        val listObjectsFirst = listObjects[0] as Map<*, *>
        assertTrue(listObjectsFirst["boolean"] as Boolean)
        assertEquals("hi", listObjectsFirst["string"] as String)
        assertEquals(9.0, listObjectsFirst["number"] as Double)

        val listObjectsSecond = listObjects[1] as Map<*, *>
        assertFalse(listObjectsSecond["boolean"] as Boolean)
        assertEquals("ciao", listObjectsSecond["string"] as String)
        assertEquals(10.0, listObjectsSecond["number"] as Double)
    }

    @Test
    fun `when theres a null value, gson wont blow up`() {
        val json = FileFromResources.invoke("event.json")
        val event = serializer.deserializeEvent(StringReader(json))
        assertNotNull(event)
        assertNull(event.user)
    }

    @Test
    fun `When deserializing a Session all the values should be set to the Session object`() {
        val jsonEvent = FileFromResources.invoke("session.json")

        val actual = serializer.deserializeSession(StringReader(jsonEvent))

        assertSessionData(actual)
    }

    @Test
    fun `When deserializing an Envelope and reader throws IOException it should return null `() {
        val inputStream = mock<InputStream>()
        whenever(inputStream.read(any())).thenThrow(IOException())

        val envelope = serializer.deserializeEnvelope(inputStream)
        assertNull(envelope)
    }

    @Test
    fun `When serializing a Session all the values should be set to the JSON string`() {
        val session = createSessionMockData()
        val jsonSession = serializeToString(session)
        // reversing, so we can assert values and not a json string
        val expectedSession = serializer.deserializeSession(StringReader(jsonSession))

        assertSessionData(expectedSession)
    }

    @Test
    fun `When deserializing an Envelope, all the values should be set to the SentryEnvelope object`() {
        val jsonEnvelope = FileFromResources.invoke("envelope_session.txt")
        val envelope = serializer.deserializeEnvelope(ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8)))
        assertEnvelopeData(envelope)
    }

    @Test
    fun `When deserializing an Envelope, SdkVersion should be set`() {
        val jsonEnvelope = FileFromResources.invoke("envelope_session_sdkversion.txt")
        val envelope = serializer.deserializeEnvelope(ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8)))!!
        assertNotNull(envelope.header.sdkVersion)
        val sdkInfo = envelope.header.sdkVersion!!

        assertEquals("test", sdkInfo.name)
        assertEquals("1.2.3", sdkInfo.version)

        assertNotNull(sdkInfo.integrations)
        assertTrue(sdkInfo.integrations!!.any { it == "NdkIntegration" })

        assertNotNull(sdkInfo.packages)

        assertTrue(sdkInfo.packages!!.any {
            it.name == "maven:sentry-android-core"
            it.version == "4.5.6"
        })
    }

    @Test
    fun `When serializing an envelope, all the values should be set`() {
        val session = createSessionMockData()
        val sentryEnvelope = SentryEnvelope.fromSession(serializer, session, null)

        val jsonEnvelope = serializeToString(sentryEnvelope)
        // reversing it so we can assert the values
        val envelope = serializer.deserializeEnvelope(ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8)))
        assertEnvelopeData(envelope)
    }

    @Test
    fun `When serializing an envelope, SdkVersion should be set`() {
        val session = createSessionMockData()
        val version = SdkVersion().apply {
            name = "test"
            version = "1.2.3"
            addIntegration("TestIntegration")
            addPackage("abc", "4.5.6")
        }
        val sentryEnvelope = SentryEnvelope.fromSession(serializer, session, version)

        val jsonEnvelope = serializeToString(sentryEnvelope)
        // reversing it so we can assert the values
        val envelope = serializer.deserializeEnvelope(ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8)))!!
        assertNotNull(envelope.header.sdkVersion)

        val sdkVersion = envelope.header.sdkVersion!!
        assertEquals(version.name, sdkVersion.name)
        assertEquals(version.version, sdkVersion.version)

        assertNotNull(sdkVersion.integrations)
        assertTrue(sdkVersion.integrations!!.any { it == "TestIntegration" })

        assertNotNull(sdkVersion.packages)
        assertTrue(sdkVersion.packages!!.any {
            it.name == "abc"
            it.version == "4.5.6"
        })
    }

    private fun assertSessionData(expectedSession: Session?) {
        assertNotNull(expectedSession)
        assertEquals(UUID.fromString("c81d4e2e-bcf2-11e6-869b-7df92533d2db"), expectedSession.sessionId)
        assertEquals("123", expectedSession.distinctId)
        assertTrue(expectedSession.init!!)
        assertEquals("2020-02-07T14:16:00.000Z", DateUtils.getTimestamp(expectedSession.started))
        assertEquals("2020-02-07T14:16:00.000Z", DateUtils.getTimestamp(expectedSession.timestamp!!))
        assertEquals(6000.toDouble(), expectedSession.duration)
        assertEquals(Session.State.Ok, expectedSession.status)
        assertEquals(2, expectedSession.errorCount())
        assertEquals(123456.toLong(), expectedSession.sequence)
        assertEquals("io.sentry@1.0+123", expectedSession.release)
        assertEquals("debug", expectedSession.environment)
        assertEquals("127.0.0.1", expectedSession.ipAddress)
        assertEquals("jamesBond", expectedSession.userAgent)
    }

    private fun assertEnvelopeData(expectedEnveope: SentryEnvelope?) {
        assertNotNull(expectedEnveope)
        assertEquals(1, expectedEnveope.items.count())
        expectedEnveope.items.forEach {
            assertEquals(SentryItemType.Session, it.header.type)
            val reader =
                InputStreamReader(ByteArrayInputStream(it.data), Charsets.UTF_8)
            val actualSession = serializer.deserializeSession(reader)
            assertSessionData(actualSession)
        }
    }

    private fun generateEmptySentryEvent(date: Date? = null): SentryEvent =
        SentryEvent(date).apply {
            contexts = null
        }

    private fun createSessionMockData(): Session =
        Session(
            Session.State.Ok,
            DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
            DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
            2,
            "123",
            UUID.fromString("c81d4e2e-bcf2-11e6-869b-7df92533d2db"),
            true,
            123456.toLong(),
            6000.toDouble(),
            "127.0.0.1",
            "jamesBond",
            "debug",
            "io.sentry@1.0+123"
        )
}
