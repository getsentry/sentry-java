package io.sentry

import io.sentry.profilemeasurements.ProfileMeasurement
import io.sentry.profilemeasurements.ProfileMeasurementValue
import io.sentry.protocol.Device
import io.sentry.protocol.Feedback
import io.sentry.protocol.ReplayRecordingSerializationTest
import io.sentry.protocol.Request
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryLogsSerializationTest
import io.sentry.protocol.SentryReplayEventSerializationTest
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.StringReader
import java.io.StringWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.util.Date
import java.util.HashMap
import java.util.TimeZone
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JsonSerializerTest {

  private class Fixture {
    val logger: ILogger = mock()
    val serializer: ISerializer
    val scopes = mock<IScopes>()
    val traceFile = Files.createTempFile("test", "here").toFile()
    val options = SentryOptions()

    init {
      options.dsn = "https://key@sentry.io/proj"
      options.setLogger(logger)
      options.isDebug = true
      whenever(scopes.options).thenReturn(options)
      serializer = JsonSerializer(options)
      options.setSerializer(serializer)
      options.setEnvelopeReader(EnvelopeReader(serializer))
    }
  }

  private lateinit var fixture: Fixture

  @BeforeTest
  fun before() {
    fixture = Fixture()
    SentryIntegrationPackageStorage.getInstance().clearStorage()
  }

  @After
  fun teardown() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
  }

  private fun <T> serializeToString(ev: T): String {
    return this.serializeToString { wrt -> fixture.serializer.serialize(ev!!, wrt) }
  }

  private fun serializeToString(serialize: (StringWriter) -> Unit): String {
    val wrt = StringWriter()
    serialize(wrt)
    return wrt.toString()
  }

  private fun serializeToString(envelope: SentryEnvelope): String {
    val outputStream = ByteArrayOutputStream()
    BufferedWriter(OutputStreamWriter(outputStream))
    fixture.serializer.serialize(envelope, outputStream)
    return outputStream.toString()
  }

  @Test
  fun `when serializing SentryEvent-SentryId object, it should become a event_id json without dashes`() {
    val dateIsoFormat = "2000-12-31T23:59:58.000Z"
    val sentryEvent = generateEmptySentryEvent(DateUtils.getDateTime(dateIsoFormat))

    val actual = serializeToString(sentryEvent)
    val expected =
      "{\"timestamp\":\"$dateIsoFormat\",\"event_id\":\"${sentryEvent.eventId}\",\"contexts\":{}}"

    assertEquals(actual, expected)
  }

  @Test
  fun `when deserializing event_id, it should become a SentryEvent-SentryId uuid`() {
    val expected = UUID.randomUUID().toString().replace("-", "")
    val jsonEvent = "{\"event_id\":\"$expected\"}"

    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

    assertEquals(expected, actual!!.eventId.toString())
  }

  @Test
  fun `when serializing SentryEvent-Date, it should become a timestamp json ISO format`() {
    val dateIsoFormat = "2000-12-31T23:59:58.000Z"
    val sentryEvent = generateEmptySentryEvent(DateUtils.getDateTime(dateIsoFormat))
    sentryEvent.eventId = null

    val expected = "{\"timestamp\":\"$dateIsoFormat\",\"contexts\":{}}"

    val actual = serializeToString(sentryEvent)

    assertEquals(expected, actual)
  }

  @Test
  fun `when deserializing timestamp, it should become a SentryEvent-Date`() {
    val dateIsoFormat = "2000-12-31T23:59:58.000Z"
    val expected = DateUtils.getDateTime(dateIsoFormat)

    val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

    assertEquals(expected, actual!!.timestamp)
  }

  @Test
  fun `when deserializing millis timestamp, it should become a SentryEvent-Date`() {
    val dateIsoFormat = "1581410911"
    val expected = DateUtils.getDateTimeWithMillisPrecision(dateIsoFormat)

    val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

    assertEquals(expected, actual!!.timestamp)
  }

  @Test
  fun `when deserializing millis timestamp with mills precision, it should become a SentryEvent-Date`() {
    val dateIsoFormat = "1581410911.988"
    val expected = DateUtils.getDateTimeWithMillisPrecision(dateIsoFormat)

    val jsonEvent = "{\"timestamp\":\"$dateIsoFormat\"}"

    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

    assertEquals(expected, actual!!.timestamp)
  }

  @Test
  fun `when deserializing unknown properties, it should be added to unknown field`() {
    val jsonEvent = "{\"string\":\"test\",\"int\":1,\"boolean\":true}"
    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

    assertEquals("test", actual!!.unknown!!["string"] as String)
    assertEquals(1, actual.unknown!!["int"] as Int)
    assertEquals(true, actual.unknown!!["boolean"] as Boolean)
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
    sentryEvent.setUnknown(unknown)

    val jsonEvent = "{\"object\":{\"int\":1,\"boolean\":true}}"

    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

    val hashMapActual = actual!!.unknown!!["object"] as Map<*, *> // gson creates it as JsonObject

    assertEquals(true, hashMapActual.get("boolean") as Boolean)
    assertEquals(1, hashMapActual.get("int") as Int)
  }

  @Test
  fun `when serializing unknown field, its keys should becom part of json`() {
    val dateIsoFormat = "2000-12-31T23:59:58.000Z"
    val sentryEvent = generateEmptySentryEvent(DateUtils.getDateTime(dateIsoFormat))
    sentryEvent.eventId = null

    val objects = hashMapOf<String, Any>()
    objects["int"] = 1
    objects["boolean"] = true

    val unknown = hashMapOf<String, Any>()
    unknown["object"] = objects

    sentryEvent.setUnknown(unknown)

    val actual = serializeToString(sentryEvent)

    val expected =
      "{\"timestamp\":\"2000-12-31T23:59:58.000Z\",\"contexts\":{},\"object\":{\"boolean\":true,\"int\":1}}"

    assertEquals(actual, expected)
  }

  @Test
  fun `when serializing a TimeZone, it should become a timezone ID string`() {
    val dateIsoFormat = "2000-12-31T23:59:58.000Z"
    val sentryEvent = generateEmptySentryEvent(DateUtils.getDateTime(dateIsoFormat))
    sentryEvent.eventId = null
    val device = Device()
    device.timezone = TimeZone.getTimeZone("Europe/Vienna")
    sentryEvent.contexts.setDevice(device)

    val expected =
      "{\"timestamp\":\"2000-12-31T23:59:58.000Z\",\"contexts\":{\"device\":{\"timezone\":\"Europe/Vienna\"}}}"
    val actual = serializeToString(sentryEvent)

    assertEquals(actual, expected)
  }

  @Test
  fun `when deserializing a timezone ID string, it should become a Device-TimeZone`() {
    val sentryEvent = generateEmptySentryEvent()
    sentryEvent.eventId = null

    val jsonEvent = "{\"contexts\":{\"device\":{\"timezone\":\"Europe/Vienna\"}}}"

    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

    assertEquals("Europe/Vienna", actual!!.contexts.device!!.timezone!!.id)
  }

  @Test
  fun `when serializing a DeviceOrientation, it should become an orientation string`() {
    val dateIsoFormat = "2000-12-31T23:59:58.000Z"
    val sentryEvent = generateEmptySentryEvent(DateUtils.getDateTime(dateIsoFormat))
    sentryEvent.eventId = null
    val device = Device()
    device.orientation = Device.DeviceOrientation.LANDSCAPE
    sentryEvent.contexts.setDevice(device)

    val expected =
      "{\"timestamp\":\"2000-12-31T23:59:58.000Z\",\"contexts\":{\"device\":{\"orientation\":\"landscape\"}}}"
    val actual = serializeToString(sentryEvent)
    assertEquals(actual, expected)
  }

  @Test
  fun `when deserializing an orientation string, it should become a DeviceOrientation`() {
    val sentryEvent = generateEmptySentryEvent()
    sentryEvent.eventId = null

    val jsonEvent = "{\"contexts\":{\"device\":{\"orientation\":\"landscape\"}}}"

    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

    assertEquals(Device.DeviceOrientation.LANDSCAPE, actual!!.contexts.device!!.orientation)
  }

  @Test
  fun `when serializing a SentryLevel, it should become a sentry level string`() {
    val dateIsoFormat = "2000-12-31T23:59:58.000Z"
    val sentryEvent = generateEmptySentryEvent(DateUtils.getDateTime(dateIsoFormat))
    sentryEvent.eventId = null
    sentryEvent.level = SentryLevel.DEBUG

    val expected =
      "{\"timestamp\":\"2000-12-31T23:59:58.000Z\",\"level\":\"debug\",\"contexts\":{}}"
    val actual = serializeToString(sentryEvent)

    assertEquals(actual, expected)
  }

  @Test
  fun `when deserializing a sentry level string, it should become a SentryLevel`() {
    val sentryEvent = generateEmptySentryEvent()
    sentryEvent.eventId = null

    val jsonEvent = "{\"level\":\"debug\"}"
    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

    assertEquals(SentryLevel.DEBUG, actual!!.level)
  }

  @Test
  fun `when deserializing a event with breadcrumbs containing data, it should become have breadcrumbs`() {
    val jsonEvent = FileFromResources.invoke("event_breadcrumb_data.json")

    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)

    assertNotNull(actual) { event -> assertNotNull(event.breadcrumbs) { assertEquals(2, it.size) } }
  }

  @Test
  fun `when deserializing a event with custom contexts, they should be set in the event contexts`() {
    val jsonEvent = FileFromResources.invoke("event_with_contexts.json")

    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), SentryEvent::class.java)
    val obj = actual!!.contexts["object"] as Map<*, *>
    val number = actual.contexts["number"] as Int
    val list = actual.contexts["list"] as List<*>
    val listObjects = actual.contexts["list_objects"] as List<*>

    assertTrue(obj["boolean"] as Boolean)
    assertEquals("hi", obj["string"] as String)
    assertEquals(9, obj["number"] as Int)

    assertEquals(50, number)

    assertEquals(1, list[0])
    assertEquals(2, list[1])

    val listObjectsFirst = listObjects[0] as Map<*, *>
    assertTrue(listObjectsFirst["boolean"] as Boolean)
    assertEquals("hi", listObjectsFirst["string"] as String)
    assertEquals(9, listObjectsFirst["number"] as Int)

    val listObjectsSecond = listObjects[1] as Map<*, *>
    assertFalse(listObjectsSecond["boolean"] as Boolean)
    assertEquals("ciao", listObjectsSecond["string"] as String)
    assertEquals(10, listObjectsSecond["number"] as Int)
  }

  @Test
  fun `when theres a null value, gson wont blow up`() {
    val json = FileFromResources.invoke("event.json")
    val event = fixture.serializer.deserialize(StringReader(json), SentryEvent::class.java)
    assertNotNull(event)
    assertNull(event.user)
  }

  @Test
  fun `When deserializing a Session all the values should be set to the Session object`() {
    val jsonEvent = FileFromResources.invoke("session.json")

    val actual = fixture.serializer.deserialize(StringReader(jsonEvent), Session::class.java)

    assertSessionData(actual)
  }

  @Test
  fun `When deserializing an Envelope and reader throws IOException it should return null `() {
    val inputStream = mock<InputStream>()
    whenever(inputStream.read(any())).thenThrow(IOException())

    val envelope = fixture.serializer.deserializeEnvelope(inputStream)
    assertNull(envelope)
  }

  @Test
  fun `When serializing a Session all the values should be set to the JSON string`() {
    val session = createSessionMockData()
    val jsonSession = serializeToString(session)
    // reversing, so we can assert values and not a json string
    val expectedSession =
      fixture.serializer.deserialize(StringReader(jsonSession), Session::class.java)

    assertSessionData(expectedSession)
  }

  @Test
  fun `session deserializes 32 character id`() {
    val sessionId = "c81d4e2ebcf211e6869b7df92533d2db"
    val session = createSessionMockData("c81d4e2ebcf211e6869b7df92533d2db")
    val jsonSession = serializeToString(session)
    // reversing, so we can assert values and not a json string
    val expectedSession =
      fixture.serializer.deserialize(StringReader(jsonSession), Session::class.java)

    assertSessionData(expectedSession, "c81d4e2ebcf211e6869b7df92533d2db")
  }

  @Test
  fun `When deserializing an Envelope, all the values should be set to the SentryEnvelope object`() {
    val jsonEnvelope = FileFromResources.invoke("envelope_session.txt")
    val envelope =
      fixture.serializer.deserializeEnvelope(
        ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8))
      )
    assertEnvelopeData(envelope)
  }

  @Test
  fun `When deserializing an Envelope, SdkVersion should be set`() {
    val jsonEnvelope = FileFromResources.invoke("envelope_session_sdkversion.txt")
    val envelope =
      fixture.serializer.deserializeEnvelope(
        ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8))
      )!!
    assertNotNull(envelope.header.sdkVersion)
    val sdkInfo = envelope.header.sdkVersion!!

    assertEquals("test", sdkInfo.name)
    assertEquals("1.2.3", sdkInfo.version)

    assertTrue(sdkInfo.integrationSet.contains("Ndk"))

    assertTrue(
      sdkInfo.packageSet.any {
        it.name == "io.sentry:maven:sentry-android-core" && it.version == "4.5.6"
      }
    )
  }

  @Test
  fun `When serializing an envelope, all the values should be set`() {
    val session = createSessionMockData()
    val sentryEnvelope = SentryEnvelope.from(fixture.serializer, session, null)

    val jsonEnvelope = serializeToString(sentryEnvelope)
    // reversing it so we can assert the values
    val envelope =
      fixture.serializer.deserializeEnvelope(
        ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8))
      )
    assertEnvelopeData(envelope)
  }

  @Test
  fun `When serializing an envelope, SdkVersion should be set`() {
    val session = createSessionMockData()
    val version =
      SdkVersion("test", "1.2.3").apply {
        addIntegration("TestIntegration")
        addPackage("abc", "4.5.6")
      }
    val sentryEnvelope = SentryEnvelope.from(fixture.serializer, session, version)

    val jsonEnvelope = serializeToString(sentryEnvelope)
    // reversing it so we can assert the values
    val envelope =
      fixture.serializer.deserializeEnvelope(
        ByteArrayInputStream(jsonEnvelope.toByteArray(Charsets.UTF_8))
      )!!
    assertNotNull(envelope.header.sdkVersion)

    val sdkVersion = envelope.header.sdkVersion!!
    assertEquals(version.name, sdkVersion.name)
    assertEquals(version.version, sdkVersion.version)
    assertTrue(sdkVersion.integrationSet.any { it == "TestIntegration" })
    assertTrue(sdkVersion.packageSet.any { it.name == "abc" && it.version == "4.5.6" })
  }

  @Test
  fun `when serializing a data map, data should be stringfied`() {
    val data = mapOf("a" to "b")
    val expected = "{\"a\":\"b\"}"

    val dataJson = fixture.serializer.serialize(data)

    assertEquals(expected, dataJson)
  }

  @Test
  fun `serializes trace context`() {
    val traceContext =
      SentryEnvelopeHeader(
        null,
        null,
        TraceContext(
          SentryId("3367f5196c494acaae85bbbd535379ac"),
          "key",
          "release",
          "environment",
          "userId",
          "transaction",
          "0.5",
          "true",
          SentryId("3367f5196c494acaae85bbbd535379aa"),
          "0.25",
        ),
      )
    val expected =
      """{"trace":{"trace_id":"3367f5196c494acaae85bbbd535379ac","public_key":"key","release":"release","environment":"environment","user_id":"userId","transaction":"transaction","sample_rate":"0.5","sample_rand":"0.25","sampled":"true","replay_id":"3367f5196c494acaae85bbbd535379aa"}}"""
    val json = serializeToString(traceContext)
    assertEquals(expected, json)
  }

  @Test
  fun `serializes trace context with user having null id`() {
    val traceContext =
      SentryEnvelopeHeader(
        null,
        null,
        TraceContext(
          SentryId("3367f5196c494acaae85bbbd535379ac"),
          "key",
          "release",
          "environment",
          null,
          "transaction",
          "0.6",
          "false",
          SentryId("3367f5196c494acaae85bbbd535379aa"),
          "0.3",
        ),
      )
    val expected =
      """{"trace":{"trace_id":"3367f5196c494acaae85bbbd535379ac","public_key":"key","release":"release","environment":"environment","transaction":"transaction","sample_rate":"0.6","sample_rand":"0.3","sampled":"false","replay_id":"3367f5196c494acaae85bbbd535379aa"}}"""
    val json = serializeToString(traceContext)
    assertEquals(expected, json)
  }

  @Test
  fun `deserializes trace context`() {
    val json =
      """{"trace":{"trace_id":"3367f5196c494acaae85bbbd535379ac","public_key":"key","release":"release","environment":"environment","user_id":"userId","transaction":"transaction"}}"""
    val actual =
      fixture.serializer.deserialize(StringReader(json), SentryEnvelopeHeader::class.java)
    assertNotNull(actual) {
      assertNotNull(it.traceContext) {
        assertEquals(SentryId("3367f5196c494acaae85bbbd535379ac"), it.traceId)
        assertEquals("key", it.publicKey)
        assertEquals("release", it.release)
        assertEquals("environment", it.environment)
        assertEquals("userId", it.userId)
      }
    }
  }

  @Test
  fun `deserializes trace context without user`() {
    val json =
      """{"trace":{"trace_id":"3367f5196c494acaae85bbbd535379ac","public_key":"key","release":"release","environment":"environment","transaction":"transaction"}}"""
    val actual =
      fixture.serializer.deserialize(StringReader(json), SentryEnvelopeHeader::class.java)
    assertNotNull(actual) {
      assertNotNull(it.traceContext) {
        assertEquals(SentryId("3367f5196c494acaae85bbbd535379ac"), it.traceId)
        assertEquals("key", it.publicKey)
        assertEquals("release", it.release)
        assertEquals("environment", it.environment)
        assertNull(it.userId)
      }
    }
  }

  @Test
  fun `serializes profile context`() {
    val profileContext = ProfileContext(SentryId("3367f5196c494acaae85bbbd535379ac"))
    val expected = """{"profiler_id":"3367f5196c494acaae85bbbd535379ac"}"""
    val json = serializeToString(profileContext)
    assertEquals(expected, json)
  }

  @Test
  fun `deserializes profile context`() {
    val json = """{"profiler_id":"3367f5196c494acaae85bbbd535379ac"}"""
    val actual = fixture.serializer.deserialize(StringReader(json), ProfileContext::class.java)
    assertNotNull(actual) {
      assertEquals(SentryId("3367f5196c494acaae85bbbd535379ac"), it.profilerId)
    }
  }

  @Test
  fun `serializes profilingTraceData`() {
    val profilingTraceData = ProfilingTraceData(fixture.traceFile, NoOpTransaction.getInstance())
    val now = Date()
    val measurementNow = SentryNanotimeDate().nanoTimestamp()
    val measurementNowSeconds =
      BigDecimal.valueOf(DateUtils.nanosToSeconds(measurementNow))
        .setScale(6, RoundingMode.DOWN)
        .toDouble()
    profilingTraceData.androidApiLevel = 21
    profilingTraceData.deviceLocale = "deviceLocale"
    profilingTraceData.deviceManufacturer = "deviceManufacturer"
    profilingTraceData.deviceModel = "deviceModel"
    profilingTraceData.deviceOsBuildNumber = "deviceOsBuildNumber"
    profilingTraceData.deviceOsVersion = "11"
    profilingTraceData.isDeviceIsEmulator = true
    profilingTraceData.cpuArchitecture = "cpuArchitecture"
    profilingTraceData.deviceCpuFrequencies = listOf(1, 2, 3, 4)
    profilingTraceData.devicePhysicalMemoryBytes = "2000000"
    profilingTraceData.buildId = "buildId"
    profilingTraceData.timestamp = now
    profilingTraceData.transactions =
      listOf(
        ProfilingTransactionData(NoOpTransaction.getInstance(), 1, 2),
        ProfilingTransactionData(NoOpTransaction.getInstance(), 2, 3),
      )
    profilingTraceData.transactionName = "transactionName"
    profilingTraceData.durationNs = "100"
    profilingTraceData.release = "release"
    profilingTraceData.transactionId = "transactionId"
    profilingTraceData.traceId = "traceId"
    profilingTraceData.profileId = "profileId"
    profilingTraceData.environment = "environment"
    profilingTraceData.truncationReason = "truncationReason"
    profilingTraceData.measurementsMap.putAll(
      hashMapOf(
        ProfileMeasurement.ID_SCREEN_FRAME_RATES to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_HZ,
            listOf(ProfileMeasurementValue(1, 60.1, measurementNow)),
          ),
        ProfileMeasurement.ID_MEMORY_FOOTPRINT to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_BYTES,
            listOf(ProfileMeasurementValue(2, 100.52, measurementNow)),
          ),
        ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_BYTES,
            listOf(ProfileMeasurementValue(3, 104.52, measurementNow)),
          ),
        ProfileMeasurement.ID_CPU_USAGE to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_PERCENT,
            listOf(ProfileMeasurementValue(5, 10.52, measurementNow)),
          ),
      )
    )
    profilingTraceData.sampledProfile = "sampled profile in base 64"

    val actual = serializeToString(profilingTraceData)
    val reader = StringReader(actual)
    val objectReader = JsonObjectReader(reader)
    val element = JsonObjectDeserializer().deserialize(objectReader) as Map<*, *>

    assertEquals(21, element["android_api_level"] as Int)
    assertEquals("deviceLocale", element["device_locale"] as String)
    assertEquals("deviceManufacturer", element["device_manufacturer"] as String)
    assertEquals("deviceModel", element["device_model"] as String)
    assertEquals("deviceOsBuildNumber", element["device_os_build_number"] as String)
    assertEquals("android", element["device_os_name"] as String)
    assertEquals("11", element["device_os_version"] as String)
    assertEquals(true, element["device_is_emulator"] as Boolean)
    assertEquals("cpuArchitecture", element["architecture"] as String)
    assertEquals(listOf(1, 2, 3, 4), element["device_cpu_frequencies"] as List<Int>)
    assertEquals("2000000", element["device_physical_memory_bytes"] as String)
    assertEquals("android", element["platform"] as String)
    assertEquals("buildId", element["build_id"] as String)
    assertEquals(DateUtils.getTimestamp(now), element["timestamp"] as String)
    assertEquals(
      listOf(
        mapOf(
          "trace_id" to "00000000000000000000000000000000",
          "relative_cpu_end_ms" to null,
          "name" to "unknown",
          "relative_start_ns" to 1,
          "relative_end_ns" to null,
          "id" to "00000000000000000000000000000000",
          "relative_cpu_start_ms" to 2,
        ),
        mapOf(
          "trace_id" to "00000000000000000000000000000000",
          "relative_cpu_end_ms" to null,
          "name" to "unknown",
          "relative_start_ns" to 2,
          "relative_end_ns" to null,
          "id" to "00000000000000000000000000000000",
          "relative_cpu_start_ms" to 3,
        ),
      ),
      element["transactions"],
    )
    assertEquals(
      mapOf(
        ProfileMeasurement.ID_SCREEN_FRAME_RATES to
          mapOf(
            "unit" to ProfileMeasurement.UNIT_HZ,
            "values" to
              listOf(
                mapOf(
                  "value" to 60.1,
                  "elapsed_since_start_ns" to "1",
                  "timestamp" to measurementNowSeconds,
                )
              ),
          ),
        ProfileMeasurement.ID_MEMORY_FOOTPRINT to
          mapOf(
            "unit" to ProfileMeasurement.UNIT_BYTES,
            "values" to
              listOf(
                mapOf(
                  "value" to 100.52,
                  "elapsed_since_start_ns" to "2",
                  "timestamp" to measurementNowSeconds,
                )
              ),
          ),
        ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT to
          mapOf(
            "unit" to ProfileMeasurement.UNIT_BYTES,
            "values" to
              listOf(
                mapOf(
                  "value" to 104.52,
                  "elapsed_since_start_ns" to "3",
                  "timestamp" to measurementNowSeconds,
                )
              ),
          ),
        ProfileMeasurement.ID_CPU_USAGE to
          mapOf(
            "unit" to ProfileMeasurement.UNIT_PERCENT,
            "values" to
              listOf(
                mapOf(
                  "value" to 10.52,
                  "elapsed_since_start_ns" to "5",
                  "timestamp" to measurementNowSeconds,
                )
              ),
          ),
      ),
      element["measurements"],
    )
    assertEquals("transactionName", element["transaction_name"] as String)
    assertEquals("100", element["duration_ns"] as String)
    assertEquals("release", element["version_name"] as String)
    assertEquals("", element["version_code"] as String)
    assertEquals("transactionId", element["transaction_id"] as String)
    assertEquals("traceId", element["trace_id"] as String)
    assertEquals("profileId", element["profile_id"] as String)
    assertEquals("environment", element["environment"] as String)
    assertEquals("truncationReason", element["truncation_reason"] as String)
    assertEquals("sampled profile in base 64", element["sampled_profile"] as String)
  }

  @Test
  fun `deserializes profilingTraceData`() {
    val json =
      """{
                            "android_api_level":21,
                            "device_locale":"deviceLocale",
                            "device_manufacturer":"deviceManufacturer",
                            "device_model":"deviceModel",
                            "device_os_build_number":"deviceOsBuildNumber",
                            "device_os_name":"android",
                            "device_os_version":"11",
                            "device_is_emulator":true,
                            "architecture":"arm64-v8a",
                            "device_cpu_frequencies":[1, 2, 3, 4],
                            "device_physical_memory_bytes":"2000000",
                            "platform":"android",
                            "build_id":"buildId",
                            "timestamp":"2024-05-24T12:52:03.561Z",
                            "transactions":[
                                {
                                    "id":"id",
                                    "trace_id":"traceId",
                                    "name":"name",
                                    "relative_start_ns":0,
                                    "relative_end_ns":10
                                },
                                {
                                    "id":"id2",
                                    "trace_id":"traceId2",
                                    "name":"name 2",
                                    "relative_start_ns":4,
                                    "relative_end_ns":21
                                }
                            ],
                            "measurements":{
                                "screen_frame_rates": {
                                    "unit":"hz",
                                    "values":[
                                        {"value":"60.1","elapsed_since_start_ns":"1", "timestamp": 0.000000001}
                                    ]
                                },
                                "frozen_frame_renders": {
                                    "unit":"nanosecond",
                                    "values":[
                                        {"value":"100","elapsed_since_start_ns":"2", "timestamp": 0.000000002}
                                    ]
                                },
                                "memory_footprint": {
                                    "unit":"byte",
                                    "values":[
                                        {"value":"1000","elapsed_since_start_ns":"3", "timestamp": 0.000000003}
                                    ]
                                },
                                "memory_native_footprint": {
                                    "unit":"byte",
                                    "values":[
                                        {"value":"1100","elapsed_since_start_ns":"4", "timestamp": 0.000000004}
                                    ]
                                },
                                "cpu_usage": {
                                    "unit":"percent",
                                    "values":[
                                        {"value":"17.04","elapsed_since_start_ns":"5","timestamp": 0.000000005}
                                    ]
                                }
                            },
                            "transaction_name":"transactionName",
                            "duration_ns":"100",
                            "version_name":"release",
                            "version_code":"",
                            "transaction_id":"transactionId",
                            "trace_id":"traceId",
                            "profile_id":"profileId",
                            "environment":"environment",
                            "truncation_reason":"truncationReason",
                            "sampled_profile":"sampled profile in base 64"
                            }"""
    val profilingTraceData =
      fixture.serializer.deserialize(StringReader(json), ProfilingTraceData::class.java)
    assertNotNull(profilingTraceData)
    assertEquals(21, profilingTraceData.androidApiLevel)
    assertEquals("deviceLocale", profilingTraceData.deviceLocale)
    assertEquals("deviceManufacturer", profilingTraceData.deviceManufacturer)
    assertEquals("deviceModel", profilingTraceData.deviceModel)
    assertEquals("deviceOsBuildNumber", profilingTraceData.deviceOsBuildNumber)
    assertEquals("android", profilingTraceData.deviceOsName)
    assertEquals("11", profilingTraceData.deviceOsVersion)
    assertEquals(true, profilingTraceData.isDeviceIsEmulator)
    assertEquals("arm64-v8a", profilingTraceData.cpuArchitecture)
    assertEquals(listOf(1, 2, 3, 4), profilingTraceData.deviceCpuFrequencies)
    assertEquals("2000000", profilingTraceData.devicePhysicalMemoryBytes)
    assertEquals("android", profilingTraceData.platform)
    assertEquals("buildId", profilingTraceData.buildId)
    assertEquals(DateUtils.getDateTime("2024-05-24T12:52:03.561Z"), profilingTraceData.timestamp)
    val expectedTransactions =
      listOf(
        ProfilingTransactionData().apply {
          id = "id"
          traceId = "traceId"
          name = "name"
          relativeStartNs = 0
          relativeEndNs = 10
        },
        ProfilingTransactionData().apply {
          id = "id2"
          traceId = "traceId2"
          name = "name 2"
          relativeStartNs = 4
          relativeEndNs = 21
        },
      )
    assertEquals(expectedTransactions, profilingTraceData.transactions)
    val expectedMeasurements =
      mapOf(
        ProfileMeasurement.ID_SCREEN_FRAME_RATES to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_HZ,
            listOf(ProfileMeasurementValue(1, 60.1, 1)),
          ),
        ProfileMeasurement.ID_FROZEN_FRAME_RENDERS to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_NANOSECONDS,
            listOf(ProfileMeasurementValue(2, 100, 2)),
          ),
        ProfileMeasurement.ID_MEMORY_FOOTPRINT to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_BYTES,
            listOf(ProfileMeasurementValue(3, 1000, 3)),
          ),
        ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_BYTES,
            listOf(ProfileMeasurementValue(4, 1100, 4)),
          ),
        ProfileMeasurement.ID_CPU_USAGE to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_PERCENT,
            listOf(ProfileMeasurementValue(5, 17.04, 5)),
          ),
      )
    assertEquals(expectedMeasurements, profilingTraceData.measurementsMap)
    assertEquals("transactionName", profilingTraceData.transactionName)
    assertEquals("100", profilingTraceData.durationNs)
    assertEquals("release", profilingTraceData.release)
    assertEquals("transactionId", profilingTraceData.transactionId)
    assertEquals("traceId", profilingTraceData.traceId)
    assertEquals("profileId", profilingTraceData.profileId)
    assertEquals("environment", profilingTraceData.environment)
    assertEquals("truncationReason", profilingTraceData.truncationReason)
    assertEquals("sampled profile in base 64", profilingTraceData.sampledProfile)
  }

  @Test
  fun `serializes profileMeasurement`() {
    val measurementValues =
      listOf(ProfileMeasurementValue(1, 2, 1000000), ProfileMeasurementValue(3, 4, 1000000))
    val profileMeasurement =
      ProfileMeasurement(ProfileMeasurement.UNIT_NANOSECONDS, measurementValues)
    val actual = serializeToString(profileMeasurement)
    val expected =
      "{\"unit\":\"nanosecond\",\"values\":[{\"value\":2.0,\"elapsed_since_start_ns\":\"1\",\"timestamp\":0.001000},{\"value\":4.0,\"elapsed_since_start_ns\":\"3\",\"timestamp\":0.001000}]}"
    assertEquals(expected, actual)
  }

  @Test
  fun `deserializes profileMeasurement`() {
    val json =
      """{
            "unit":"hz",
            "values":[
                {"value":"60.1","elapsed_since_start_ns":"1"},{"value":"100","elapsed_since_start_ns":"2", "timestamp": 0.001}
            ]
        }"""
    val profileMeasurement =
      fixture.serializer.deserialize(StringReader(json), ProfileMeasurement::class.java)
    val expected =
      ProfileMeasurement(
        ProfileMeasurement.UNIT_HZ,
        listOf(ProfileMeasurementValue(1, 60.1, 0), ProfileMeasurementValue(2, 100, 1000000)),
      )
    assertEquals(expected, profileMeasurement)
  }

  @Test
  fun `serializes profileMeasurementValue`() {
    val profileMeasurementValue = ProfileMeasurementValue(1, 2, 1000000)
    val actual = serializeToString(profileMeasurementValue)
    val expected = "{\"value\":2.0,\"elapsed_since_start_ns\":\"1\",\"timestamp\":0.001000}"
    assertEquals(expected, actual)
  }

  @Test
  fun `deserializes profileMeasurementValue`() {
    val json = """{"value":"60.1","elapsed_since_start_ns":"1"}"""
    val profileMeasurementValue =
      fixture.serializer.deserialize(StringReader(json), ProfileMeasurementValue::class.java)
    val expected = ProfileMeasurementValue(1, 60.1, 0)
    assertEquals(expected, profileMeasurementValue)
    assertEquals(60.1, profileMeasurementValue?.value)
    assertEquals("1", profileMeasurementValue?.relativeStartNs)
    assertEquals(0.0, profileMeasurementValue?.timestamp)
  }

  @Test
  fun `deserializes profileMeasurementValue with timestamp`() {
    val json = """{"value":"60.1","elapsed_since_start_ns":"1","timestamp":0.001000}"""
    val profileMeasurementValue =
      fixture.serializer.deserialize(StringReader(json), ProfileMeasurementValue::class.java)
    val expected = ProfileMeasurementValue(1, 60.1, 1000000)
    assertEquals(expected, profileMeasurementValue)
    assertEquals(60.1, profileMeasurementValue?.value)
    assertEquals("1", profileMeasurementValue?.relativeStartNs)
    assertEquals(0.001, profileMeasurementValue?.timestamp)
  }

  @Test
  fun `serializes profileChunk`() {
    val profilerId = SentryId()
    val chunkId = SentryId()
    fixture.options.sdkVersion = SdkVersion("test", "1.2.3")
    fixture.options.release = "release"
    fixture.options.environment = "environment"
    val profileChunk =
      ProfileChunk(profilerId, chunkId, fixture.traceFile, HashMap(), 5.3, fixture.options)
    val measurementNow = SentryNanotimeDate().nanoTimestamp()
    val measurementNowSeconds =
      BigDecimal.valueOf(DateUtils.nanosToSeconds(measurementNow))
        .setScale(6, RoundingMode.DOWN)
        .toDouble()
    profileChunk.sampledProfile = "sampled profile in base 64"
    profileChunk.measurements.putAll(
      hashMapOf(
        ProfileMeasurement.ID_SCREEN_FRAME_RATES to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_HZ,
            listOf(ProfileMeasurementValue(1, 60.1, measurementNow)),
          ),
        ProfileMeasurement.ID_MEMORY_FOOTPRINT to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_BYTES,
            listOf(ProfileMeasurementValue(2, 100.52, measurementNow)),
          ),
        ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_BYTES,
            listOf(ProfileMeasurementValue(3, 104.52, measurementNow)),
          ),
        ProfileMeasurement.ID_CPU_USAGE to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_PERCENT,
            listOf(ProfileMeasurementValue(5, 10.52, measurementNow)),
          ),
      )
    )

    val actual = serializeToString(profileChunk)
    val reader = StringReader(actual)
    val objectReader = JsonObjectReader(reader)
    val element = JsonObjectDeserializer().deserialize(objectReader) as Map<*, *>

    assertEquals("android", element["platform"] as String)
    assertEquals(profilerId.toString(), element["profiler_id"] as String)
    assertEquals(chunkId.toString(), element["chunk_id"] as String)
    assertEquals("environment", element["environment"] as String)
    assertEquals("release", element["release"] as String)
    assertEquals(
      mapOf("name" to "test", "version" to "1.2.3"),
      element["client_sdk"] as Map<String, String>,
    )
    assertEquals("2", element["version"] as String)
    assertEquals(5.3, element["timestamp"] as Double)
    assertEquals("sampled profile in base 64", element["sampled_profile"] as String)
    assertEquals(
      mapOf(
        ProfileMeasurement.ID_SCREEN_FRAME_RATES to
          mapOf(
            "unit" to ProfileMeasurement.UNIT_HZ,
            "values" to
              listOf(
                mapOf(
                  "value" to 60.1,
                  "elapsed_since_start_ns" to "1",
                  "timestamp" to measurementNowSeconds,
                )
              ),
          ),
        ProfileMeasurement.ID_MEMORY_FOOTPRINT to
          mapOf(
            "unit" to ProfileMeasurement.UNIT_BYTES,
            "values" to
              listOf(
                mapOf(
                  "value" to 100.52,
                  "elapsed_since_start_ns" to "2",
                  "timestamp" to measurementNowSeconds,
                )
              ),
          ),
        ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT to
          mapOf(
            "unit" to ProfileMeasurement.UNIT_BYTES,
            "values" to
              listOf(
                mapOf(
                  "value" to 104.52,
                  "elapsed_since_start_ns" to "3",
                  "timestamp" to measurementNowSeconds,
                )
              ),
          ),
        ProfileMeasurement.ID_CPU_USAGE to
          mapOf(
            "unit" to ProfileMeasurement.UNIT_PERCENT,
            "values" to
              listOf(
                mapOf(
                  "value" to 10.52,
                  "elapsed_since_start_ns" to "5",
                  "timestamp" to measurementNowSeconds,
                )
              ),
          ),
      ),
      element["measurements"],
    )
  }

  @Test
  fun `deserializes profileChunk`() {
    val profilerId = SentryId()
    val chunkId = SentryId()
    val json =
      """{
                            "client_sdk":{"name":"test","version":"1.2.3"},
                            "chunk_id":"$chunkId",
                            "environment":"environment",
                            "platform":"android",
                            "profiler_id":"$profilerId",
                            "release":"release",
                            "sampled_profile":"sampled profile in base 64",
                            "timestamp":"5.3",
                            "version":"2",
                            "measurements":{
                                "screen_frame_rates": {
                                    "unit":"hz",
                                    "values":[
                                        {"value":"60.1","elapsed_since_start_ns":"1", "timestamp": 0.000000001}
                                    ]
                                },
                                "frozen_frame_renders": {
                                    "unit":"nanosecond",
                                    "values":[
                                        {"value":"100","elapsed_since_start_ns":"2", "timestamp": 0.000000002}
                                    ]
                                },
                                "memory_footprint": {
                                    "unit":"byte",
                                    "values":[
                                        {"value":"1000","elapsed_since_start_ns":"3", "timestamp": 0.000000003}
                                    ]
                                },
                                "memory_native_footprint": {
                                    "unit":"byte",
                                    "values":[
                                        {"value":"1100","elapsed_since_start_ns":"4", "timestamp": 0.000000004}
                                    ]
                                },
                                "cpu_usage": {
                                    "unit":"percent",
                                    "values":[
                                        {"value":"17.04","elapsed_since_start_ns":"5", "timestamp": 0.000000005}
                                    ]
                                }
                            }
                            }"""
    val profileChunk = fixture.serializer.deserialize(StringReader(json), ProfileChunk::class.java)
    assertNotNull(profileChunk)
    assertEquals(SdkVersion("test", "1.2.3"), profileChunk.clientSdk)
    assertEquals(chunkId, profileChunk.chunkId)
    assertEquals("environment", profileChunk.environment)
    assertEquals("android", profileChunk.platform)
    assertEquals(profilerId, profileChunk.profilerId)
    assertEquals("release", profileChunk.release)
    assertEquals("sampled profile in base 64", profileChunk.sampledProfile)
    assertEquals(5.3, profileChunk.timestamp)
    assertEquals("2", profileChunk.version)
    val expectedMeasurements =
      mapOf(
        ProfileMeasurement.ID_SCREEN_FRAME_RATES to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_HZ,
            listOf(ProfileMeasurementValue(1, 60.1, 1)),
          ),
        ProfileMeasurement.ID_FROZEN_FRAME_RENDERS to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_NANOSECONDS,
            listOf(ProfileMeasurementValue(2, 100, 2)),
          ),
        ProfileMeasurement.ID_MEMORY_FOOTPRINT to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_BYTES,
            listOf(ProfileMeasurementValue(3, 1000, 3)),
          ),
        ProfileMeasurement.ID_MEMORY_NATIVE_FOOTPRINT to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_BYTES,
            listOf(ProfileMeasurementValue(4, 1100, 4)),
          ),
        ProfileMeasurement.ID_CPU_USAGE to
          ProfileMeasurement(
            ProfileMeasurement.UNIT_PERCENT,
            listOf(ProfileMeasurementValue(5, 17.04, 5)),
          ),
      )
    assertEquals(expectedMeasurements, profileChunk.measurements)
  }

  @Test
  fun `serializes transaction`() {
    val trace = TransactionContext("transaction-name", "http")
    trace.description = "some request"
    trace.status = SpanStatus.OK
    trace.setTag("myTag", "myValue")
    trace.sampled = true
    trace.data["dataKey"] = "dataValue"
    val tracer = SentryTracer(trace, fixture.scopes)
    tracer.setData("dataKey", "dataValue")
    val span = tracer.startChild("child")
    span.finish(SpanStatus.OK)
    tracer.finish()

    val stringWriter = StringWriter()
    fixture.serializer.serialize(SentryTransaction(tracer), stringWriter)

    val reader = StringReader(stringWriter.toString())
    val objectReader = JsonObjectReader(reader)
    val element = JsonObjectDeserializer().deserialize(objectReader) as Map<*, *>

    assertEquals("transaction-name", element["transaction"] as String)
    assertEquals("transaction", element["type"] as String)
    assertNotNull(element["start_timestamp"] as Number)
    assertNotNull(element["event_id"] as String)
    assertNotNull(element["spans"] as List<*>)
    assertEquals("myValue", (element["tags"] as Map<*, *>)["myTag"] as String)

    val jsonSpan = (element["spans"] as List<*>)[0] as Map<*, *>
    assertNotNull(jsonSpan["trace_id"])
    assertNotNull(jsonSpan["span_id"])
    assertNotNull(jsonSpan["parent_span_id"])
    assertEquals("child", jsonSpan["op"] as String)
    assertNotNull("ok", jsonSpan["status"] as String)
    assertNotNull(jsonSpan["timestamp"])
    assertNotNull(jsonSpan["start_timestamp"])

    val jsonTrace = (element["contexts"] as Map<*, *>)["trace"] as Map<*, *>
    assertEquals("dataValue", (jsonTrace["data"] as Map<*, *>)["dataKey"] as String)
    assertNotNull(jsonTrace["trace_id"] as String)
    assertNotNull(jsonTrace["span_id"] as String)
    assertNotNull(jsonTrace["data"] as Map<*, *>) { assertEquals("dataValue", it["dataKey"]) }
    assertEquals("http", jsonTrace["op"] as String)
    assertEquals("some request", jsonTrace["description"] as String)
    assertEquals("ok", jsonTrace["status"] as String)
  }

  @Test
  fun `deserializes transaction`() {
    val json =
      """{
                          "transaction": "a-transaction",
                          "type": "transaction",
                          "start_timestamp": 1632395079.503000,
                          "timestamp": 1632395079.807321,
                          "event_id": "3367f5196c494acaae85bbbd535379ac",
                          "contexts": {
                            "trace": {
                              "trace_id": "b156a475de54423d9c1571df97ec7eb6",
                              "span_id": "0a53026963414893",
                              "op": "http",
                              "status": "ok",
                              "data": {
                                "transactionDataKey": "transactionDataValue"
                              }
                            },
                            "custom": {
                              "some-key": "some-value"
                            }
                          },
                          "extra": {
                            "extraKey": "extraValue"
                          },
                          "spans": [
                            {
                              "start_timestamp": 1632395079.840000,
                              "timestamp": 1632395079.884043,
                              "trace_id": "2b099185293344a5bfdd7ad89ebf9416",
                              "span_id": "5b95c29a5ded4281",
                              "parent_span_id": "a3b2d1d58b344b07",
                              "op": "PersonService.create",
                              "description": "desc",
                              "status": "aborted",
                              "tags": {
                                "name": "value"
                              },
                              "data": {
                                "key": "value"
                              }
                            }
                          ]
                        }"""
    val transaction =
      fixture.serializer.deserialize(StringReader(json), SentryTransaction::class.java)
    assertNotNull(transaction)
    assertEquals("a-transaction", transaction.transaction)
    assertNotNull(transaction.startTimestamp)
    assertNotNull(transaction.timestamp)
    assertNotNull(transaction.contexts)
    assertNotNull(transaction.contexts.trace)
    assertEquals(SpanStatus.OK, transaction.status)
    assertEquals("transaction", transaction.type)
    assertEquals(
      "b156a475de54423d9c1571df97ec7eb6",
      transaction.contexts.trace!!.traceId.toString(),
    )
    assertEquals("0a53026963414893", transaction.contexts.trace!!.spanId.toString())
    assertEquals("http", transaction.contexts.trace!!.operation)
    assertNotNull(transaction.contexts["custom"])
    assertEquals("transactionDataValue", transaction.contexts.trace!!.data["transactionDataKey"])
    assertEquals("some-value", (transaction.contexts["custom"] as Map<*, *>)["some-key"])

    assertEquals("extraValue", transaction.getExtra("extraKey"))

    assertNotNull(transaction.spans)
    assertEquals(1, transaction.spans.size)
    val span = transaction.spans[0]
    assertNotNull(span.startTimestamp)
    assertNotNull(span.timestamp)
    assertNotNull(span.data) { assertEquals("value", it["key"]) }
    assertEquals("2b099185293344a5bfdd7ad89ebf9416", span.traceId.toString())
    assertEquals("5b95c29a5ded4281", span.spanId.toString())
    assertEquals("a3b2d1d58b344b07", span.parentSpanId.toString())
    assertEquals("PersonService.create", span.op)
    assertEquals(SpanStatus.ABORTED, span.status)
    assertEquals("desc", span.description)
    assertEquals(mapOf("name" to "value"), span.tags)
  }

  @Test
  fun `deserializes legacy timestamp format in spans and transactions`() {
    val json =
      """{
                          "transaction": "a-transaction",
                          "type": "transaction",
                          "start_timestamp": "2020-10-23T10:24:01.791Z",
                          "timestamp": "2020-10-23T10:24:02.791Z",
                          "event_id": "3367f5196c494acaae85bbbd535379ac",
                          "contexts": {
                            "trace": {
                              "trace_id": "b156a475de54423d9c1571df97ec7eb6",
                              "span_id": "0a53026963414893",
                              "op": "http",
                              "status": "ok"
                            }
                          },
                          "spans": [
                            {
                              "start_timestamp": "2021-03-05T08:51:12.838Z",
                              "timestamp": "2021-03-05T08:51:12.949Z",
                              "trace_id": "2b099185293344a5bfdd7ad89ebf9416",
                              "span_id": "5b95c29a5ded4281",
                              "parent_span_id": "a3b2d1d58b344b07",
                              "op": "PersonService.create",
                              "description": "desc",
                              "status": "aborted"
                            }
                          ]
                        }"""
    val transaction =
      fixture.serializer.deserialize(StringReader(json), SentryTransaction::class.java)
    assertNotNull(transaction) {
      assertNotNull(it.startTimestamp)
      assertNotNull(it.timestamp)
    }
  }

  @Test
  fun `serializing SentryAppStartProfilingOptions`() {
    val actual = serializeToString(appStartProfilingOptions)

    val expected =
      "{\"profile_sampled\":true,\"profile_sample_rate\":0.8,\"continuous_profile_sampled\":true," +
        "\"trace_sampled\":false,\"trace_sample_rate\":0.1,\"profiling_traces_dir_path\":null,\"is_profiling_enabled\":false," +
        "\"is_continuous_profiling_enabled\":false,\"profile_lifecycle\":\"TRACE\",\"profiling_traces_hz\":65," +
        "\"is_enable_app_start_profiling\":false,\"is_start_profiler_on_app_start\":true}"
    assertEquals(expected, actual)
  }

  @Test
  fun `deserializing SentryAppStartProfilingOptions`() {
    val jsonAppStartProfilingOptions =
      "{\"profile_sampled\":true,\"profile_sample_rate\":0.8,\"trace_sampled\"" +
        ":false,\"trace_sample_rate\":0.1,\"profiling_traces_dir_path\":null,\"is_profiling_enabled\":false," +
        "\"profile_lifecycle\":\"TRACE\",\"profiling_traces_hz\":65,\"continuous_profile_sampled\":true," +
        "\"is_enable_app_start_profiling\":false,\"is_start_profiler_on_app_start\":true}"

    val actual =
      fixture.serializer.deserialize(
        StringReader(jsonAppStartProfilingOptions),
        SentryAppStartProfilingOptions::class.java,
      )
    assertNotNull(actual)
    assertEquals(appStartProfilingOptions.traceSampled, actual.traceSampled)
    assertEquals(appStartProfilingOptions.traceSampleRate, actual.traceSampleRate)
    assertEquals(appStartProfilingOptions.profileSampled, actual.profileSampled)
    assertEquals(appStartProfilingOptions.profileSampleRate, actual.profileSampleRate)
    assertEquals(
      appStartProfilingOptions.continuousProfileSampled,
      actual.isContinuousProfileSampled,
    )
    assertEquals(appStartProfilingOptions.isProfilingEnabled, actual.isProfilingEnabled)
    assertEquals(
      appStartProfilingOptions.isContinuousProfilingEnabled,
      actual.isContinuousProfilingEnabled,
    )
    assertEquals(appStartProfilingOptions.profilingTracesHz, actual.profilingTracesHz)
    assertEquals(appStartProfilingOptions.profilingTracesDirPath, actual.profilingTracesDirPath)
    assertEquals(appStartProfilingOptions.profileLifecycle, actual.profileLifecycle)
    assertEquals(
      appStartProfilingOptions.isEnableAppStartProfiling,
      actual.isEnableAppStartProfiling,
    )
    assertEquals(
      appStartProfilingOptions.isStartProfilerOnAppStart,
      actual.isStartProfilerOnAppStart,
    )
    assertNull(actual.unknown)
  }

  @Test
  fun `serializes span data`() {
    val sentrySpan = SentrySpan(createSpan() as Span, mapOf("data1" to "value1"))

    val serialized = serializeToString(sentrySpan)
    val deserialized =
      fixture.serializer.deserialize(StringReader(serialized), SentrySpan::class.java)

    assertNotNull(deserialized?.data) { assertNotNull(it["data1"]) { assertEquals("value1", it) } }
    assertEquals(1, deserialized?.measurements?.get("test_measurement")?.value)
    assertEquals("test", deserialized?.measurements?.get("test_measurement")?.unit)
  }

  @Test
  fun `serializing user feedback`() {
    val actual = serializeToString(userFeedback)

    val expected =
      "{\"event_id\":\"${userFeedback.eventId}\",\"name\":\"${userFeedback.name}\"," +
        "\"email\":\"${userFeedback.email}\",\"comments\":\"${userFeedback.comments}\"}"

    assertEquals(expected, actual)
  }

  @Test
  fun `deserializing user feedback`() {
    val jsonUserFeedback =
      "{\"event_id\":\"c2fb8fee2e2b49758bcb67cda0f713c7\"," +
        "\"name\":\"John\",\"email\":\"john@me.com\",\"comments\":\"comment\"}"
    val actual =
      fixture.serializer.deserialize(StringReader(jsonUserFeedback), UserFeedback::class.java)
    assertNotNull(actual)
    assertEquals(userFeedback.eventId, actual.eventId)
    assertEquals(userFeedback.name, actual.name)
    assertEquals(userFeedback.email, actual.email)
    assertEquals(userFeedback.comments, actual.comments)
  }

  @Test
  fun `serialize envelope with item throwing`() {
    val eventID = SentryId()
    val header = SentryEnvelopeHeader(eventID)

    val message = "hello"
    val attachment = Attachment(message.toByteArray(), "bytes.txt")
    val validAttachmentItem =
      SentryEnvelopeItem.fromAttachment(fixture.serializer, fixture.options.logger, attachment, 5)

    val invalidAttachmentItem =
      SentryEnvelopeItem.fromAttachment(
        fixture.serializer,
        fixture.options.logger,
        Attachment("no"),
        5,
      )
    val envelope = SentryEnvelope(header, listOf(invalidAttachmentItem, validAttachmentItem))

    val actualJson = serializeToString(envelope)

    val expectedJson =
      "{\"event_id\":\"${eventID}\"}\n" +
        "{\"filename\":\"${attachment.filename}\"," +
        "\"type\":\"attachment\"," +
        "\"attachment_type\":\"event.attachment\"," +
        "\"length\":${attachment.bytes?.size}}\n" +
        "$message\n"

    assertEquals(expectedJson, actualJson)

    verify(fixture.logger)
      .log(
        eq(SentryLevel.ERROR),
        eq("Failed to create envelope item. Dropping it."),
        any<IOException>(),
      )
  }

  @Test
  fun `empty maps are serialized to null`() {
    val event = SentryEvent()
    event.tags = emptyMap()

    val serialized = serializeToString(event)
    val deserialized =
      fixture.serializer.deserialize(StringReader(serialized), SentryEvent::class.java)

    assertNull(deserialized?.tags)
  }

  @Test
  fun `empty lists are serialized to null`() {
    val event = generateEmptySentryEvent()
    event.threads = listOf()

    val serialized = serializeToString(event)
    val deserialized =
      fixture.serializer.deserialize(StringReader(serialized), SentryEvent::class.java)

    assertNull(deserialized?.threads)
  }

  @Test
  fun `Long can be serialized inside request data`() {
    val request = Request()

    data class LongContainer(val longValue: Long)

    request.data = LongContainer(10)

    val serialized = serializeToString(request)
    val deserialized = fixture.serializer.deserialize(StringReader(serialized), Request::class.java)

    val deserializedData = deserialized?.data as? Map<String, Any>
    assertNotNull(deserializedData)
    assertEquals(10, deserializedData["longValue"])
  }

  @Test
  fun `Primitives can be serialized inside request data`() {
    val request = Request()

    request.data =
      JsonReflectionObjectSerializerTest.ClassWithPrimitiveFields(
        17,
        3,
        'x',
        9001,
        0.9f,
        0.99,
        true,
      )

    val serialized = serializeToString(request)
    val deserialized = fixture.serializer.deserialize(StringReader(serialized), Request::class.java)

    val deserializedData = deserialized?.data as? Map<String, Any>
    assertNotNull(deserializedData)
    assertEquals(17, deserializedData["byte"])
    assertEquals(3, deserializedData["short"])
    assertEquals("x", deserializedData["char"])
    assertEquals(9001, deserializedData["integer"])
    assertEquals(0.9, deserializedData["float"])
    assertEquals(0.99, deserializedData["double"])
    assertEquals(true, deserializedData["boolean"])
  }

  @Test
  fun `json serializer uses logger set on SentryOptions`() {
    val logger = mock<ILogger>()
    val options = SentryOptions()
    options.setLogger(logger)
    options.setDebug(true)
    whenever(logger.isEnabled(any())).thenReturn(true)

    (options.serializer as JsonSerializer).serialize(mapOf("key" to "val"), mock())
    verify(logger)
      .log(any(), check { assertTrue(it.startsWith("Serializing object:")) }, any<Any>())
  }

  @Test
  fun `json serializer does not close the stream that is passed in`() {
    val stream = mock<OutputStream>()
    JsonSerializer(SentryOptions())
      .serialize(SentryEnvelope.from(fixture.serializer, SentryEvent(), null), stream)

    verify(stream, never()).close()
  }

  @Test
  fun `known primitives can be deserialized`() {
    val string = serializeToString("value")
    val collection = serializeToString(listOf("hello", "hallo"))
    val map = serializeToString(mapOf("one" to "two"))

    val deserializedString =
      fixture.serializer.deserialize(StringReader(string), String::class.java)
    val deserializedCollection =
      fixture.serializer.deserialize(StringReader(collection), List::class.java)
    val deserializedMap = fixture.serializer.deserialize(StringReader(map), Map::class.java)

    assertEquals("value", deserializedString)
    assertEquals(listOf("hello", "hallo"), deserializedCollection)
    assertEquals(mapOf("one" to "two"), deserializedMap)
  }

  @Test
  fun `collection with element deserializer can be deserialized`() {
    val breadcrumb1 = Breadcrumb.debug("test")
    val breadcrumb2 = Breadcrumb.navigation("one", "other")
    val collection = serializeToString(listOf(breadcrumb1, breadcrumb2))

    val deserializedCollection =
      fixture.serializer.deserializeCollection(
        StringReader(collection),
        List::class.java,
        Breadcrumb.Deserializer(),
      )

    assertEquals(listOf(breadcrumb1, breadcrumb2), deserializedCollection)
  }

  @Test
  fun `collection without element deserializer can be deserialized as map`() {
    val timestamp = Date(0)
    val timestampSerialized = serializeToString(timestamp).removePrefix("\"").removeSuffix("\"")
    val collection =
      serializeToString(
        listOf(
          Breadcrumb(timestamp).apply { message = "test" },
          Breadcrumb(timestamp).apply { category = "navigation" },
        )
      )

    val deserializedCollection =
      fixture.serializer.deserialize(StringReader(collection), List::class.java)

    assertEquals(
      listOf(
        mapOf(
          "data" to emptyMap<String, String>(),
          "message" to "test",
          "timestamp" to timestampSerialized,
        ),
        mapOf(
          "data" to emptyMap<String, String>(),
          "category" to "navigation",
          "timestamp" to timestampSerialized,
        ),
      ),
      deserializedCollection,
    )
  }

  @Test
  fun `serializes feedback`() {
    val replayId = SentryId("00000000-0000-0000-0000-000000000001")
    val eventId = SentryId("00000000-0000-0000-0000-000000000002")
    val feedback = Feedback("message")
    feedback.name = "name"
    feedback.contactEmail = "email"
    feedback.url = "url"
    feedback.setReplayId(replayId)
    feedback.setAssociatedEventId(eventId)
    val actual = serializeToString(feedback)
    val expected =
      "{\"message\":\"message\",\"contact_email\":\"email\",\"name\":\"name\",\"associated_event_id\":\"00000000000000000000000000000002\",\"replay_id\":\"00000000000000000000000000000001\",\"url\":\"url\"}"
    assertEquals(expected, actual)
  }

  @Test
  fun `deserializes feedback`() {
    val json =
      """{
            "message":"message",
            "contact_email":"email",
            "name":"name",
            "associated_event_id":"00000000000000000000000000000002",
            "replay_id":"00000000000000000000000000000001",
            "url":"url"
        }"""
    val feedback = fixture.serializer.deserialize(StringReader(json), Feedback::class.java)
    val expected =
      Feedback("message").apply {
        name = "name"
        contactEmail = "email"
        url = "url"
        setReplayId(SentryId("00000000-0000-0000-0000-000000000001"))
        setAssociatedEventId(SentryId("00000000-0000-0000-0000-000000000002"))
      }
    assertEquals(expected, feedback)
  }

  @Test
  fun `ser deser replay data`() {
    val replayEvent = SentryReplayEventSerializationTest.Fixture().getSut()
    val replayRecording = ReplayRecordingSerializationTest.Fixture().getSut()
    val serializedEvent = serializeToString(replayEvent)
    val serializedRecording = serializeToString(replayRecording)

    val deserializedEvent =
      fixture.serializer.deserialize(StringReader(serializedEvent), SentryReplayEvent::class.java)
    val deserializedRecording =
      fixture.serializer.deserialize(StringReader(serializedRecording), ReplayRecording::class.java)

    assertEquals(replayEvent, deserializedEvent)
    assertEquals(replayRecording, deserializedRecording)
  }

  @Test
  fun `ser deser logs data`() {
    val logEvent = SentryLogsSerializationTest.Fixture().getSut()
    val serializedEvent = serializeToString(logEvent)

    val deserializedEvent =
      fixture.serializer.deserialize(StringReader(serializedEvent), SentryLogEvents::class.java)

    assertNotNull(deserializedEvent)
    assertEquals(serializedEvent, serializeToString(deserializedEvent))
  }

  private fun assertSessionData(
    expectedSession: Session?,
    expectedSessionId: String = "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
  ) {
    assertNotNull(expectedSession)
    assertEquals(expectedSessionId, expectedSession.sessionId)
    assertEquals("123", expectedSession.distinctId)
    assertTrue(expectedSession.init!!)
    assertEquals("2020-02-07T14:16:00.000Z", DateUtils.getTimestamp(expectedSession.started!!))
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
      val reader = InputStreamReader(ByteArrayInputStream(it.data), Charsets.UTF_8)
      val actualSession = fixture.serializer.deserialize(reader, Session::class.java)
      assertSessionData(actualSession)
    }
  }

  private fun generateEmptySentryEvent(date: Date = Date()): SentryEvent = SentryEvent(date)

  private fun createSessionMockData(
    sessionId: String = "c81d4e2e-bcf2-11e6-869b-7df92533d2db"
  ): Session =
    Session(
      Session.State.Ok,
      DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
      DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
      2,
      "123",
      sessionId,
      true,
      123456.toLong(),
      6000.toDouble(),
      "127.0.0.1",
      "jamesBond",
      "debug",
      "io.sentry@1.0+123",
      "anr_foreground",
    )

  private val userFeedback: UserFeedback
    get() {
      val eventId = SentryId("c2fb8fee2e2b49758bcb67cda0f713c7")
      return UserFeedback(eventId).apply {
        name = "John"
        email = "john@me.com"
        comments = "comment"
      }
    }

  private val appStartProfilingOptions =
    SentryAppStartProfilingOptions().apply {
      traceSampled = false
      traceSampleRate = 0.1
      continuousProfileSampled = true
      profileSampled = true
      profileSampleRate = 0.8
      isProfilingEnabled = false
      isContinuousProfilingEnabled = false
      profilingTracesHz = 65
      profileLifecycle = ProfileLifecycle.TRACE
      isEnableAppStartProfiling = false
      isStartProfilerOnAppStart = true
    }

  private fun createSpan(): ISpan {
    val trace =
      TransactionContext("transaction-name", "http").apply {
        description = "some request"
        status = SpanStatus.OK
        setTag("myTag", "myValue")
      }
    val tracer = SentryTracer(trace, fixture.scopes)
    val span = tracer.startChild("child")
    span.setMeasurement("test_measurement", 1, MeasurementUnit.Custom("test"))
    span.finish(SpanStatus.OK)
    tracer.finish()
    return span
  }
}
