package io.sentry

import io.sentry.protocol.SentryId
import io.sentry.protocol.SerializationUtils
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.StringReader
import java.io.StringWriter
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class CheckInSerializationTest {

    private class Fixture {
        val logger = mock<ILogger>()

        fun getSut(type: MonitorScheduleType): CheckIn {
            return CheckIn("some_slug", CheckInStatus.ERROR).apply {
                contexts.trace = TransactionContext.fromPropagationContext(
                    PropagationContext().also {
                        it.traceId = SentryId("f382e3180c714217a81371f8c644aefe")
                        it.spanId = SpanId("85694b9f567145a6")
                    }
                ).apply {
                    data[SpanDataConvention.THREAD_ID] = 10
                    data[SpanDataConvention.THREAD_NAME] = "test"
                }
                duration = 12.3
                environment = "env"
                release = "1.0.1"
                val monitorConfigTmp =
                    if (MonitorScheduleType.CRONTAB.equals(type)) {
                        MonitorConfig(MonitorSchedule.crontab("0 * * * *"))
                    } else {
                        MonitorConfig(MonitorSchedule.interval(42, MonitorScheduleUnit.MINUTE))
                    }
                monitorConfig = monitorConfigTmp.apply {
                    checkinMargin = 8L
                    maxRuntime = 9L
                    timezone = ZoneId.of("Europe/Vienna").id
                    failureIssueThreshold = 10
                    recoveryThreshold = 20
                }
            }
        }
    }
    private val fixture = Fixture()

    @Test
    fun serializeInterval() {
        val checkIn = fixture.getSut(MonitorScheduleType.INTERVAL)
        val actual = serialize(checkIn)
        val expected = SerializationUtils.sanitizedFile("json/checkin_interval.json")
            .replace("6d55218195564d6d88cf3883b84666f1", checkIn.checkInId.toString())

        assertEquals(expected, actual)
    }

    @Test
    fun serializeCrontab() {
        val checkIn = fixture.getSut(MonitorScheduleType.CRONTAB)
        val actual = serialize(checkIn)
        val expected = SerializationUtils.sanitizedFile("json/checkin_crontab.json")
            .replace("6d55218195564d6d88cf3883b84666f1", checkIn.checkInId.toString())
            .replace("0_*_*_*_*", "0 * * * *")

        assertEquals(expected, actual)
    }

    @Test
    fun deserializeCrontab() {
        val checkIn = fixture.getSut(MonitorScheduleType.CRONTAB)
        val jsonCheckIn = SerializationUtils.sanitizedFile("json/checkin_crontab.json")
            .replace("0_*_*_*_*", "0 * * * *")
        val reader = JsonObjectReader(StringReader(jsonCheckIn))
        val actual = CheckIn.Deserializer().deserialize(reader, fixture.logger)
        assertNotNull(actual)
        assertEquals("6d55218195564d6d88cf3883b84666f1", actual.checkInId.toString())
        assertEquals(checkIn.status, actual.status)
        assertTrue((checkIn.duration!! - actual.duration!!) < 0.01)
        assertEquals(checkIn.release, actual.release)
        assertEquals(checkIn.environment, actual.environment)
        val actualContext = actual.contexts
        assertEquals(checkIn.contexts.trace!!.traceId, actualContext.trace!!.traceId)
        val actualConfig = actual.monitorConfig!!
        val actualSchedule = actualConfig.schedule!!
        val expectedConfig = checkIn.monitorConfig!!
        val expectedSchedule = expectedConfig.schedule!!
        assertEquals(expectedConfig.maxRuntime, actualConfig.maxRuntime)
        assertEquals(expectedConfig.checkinMargin, actualConfig.checkinMargin)
        assertEquals(expectedConfig.timezone, actualConfig.timezone)
        assertEquals(expectedConfig.failureIssueThreshold, actualConfig.failureIssueThreshold)
        assertEquals(expectedConfig.recoveryThreshold, actualConfig.recoveryThreshold)
        assertEquals(expectedSchedule.type, actualSchedule.type)
        assertEquals(expectedSchedule.value, actualSchedule.value)
        assertEquals(expectedSchedule.unit, actualSchedule.unit)
    }

    @Test
    fun deserializeInterval() {
        val checkIn = fixture.getSut(MonitorScheduleType.INTERVAL)
        val jsonCheckIn = SerializationUtils.sanitizedFile("json/checkin_interval.json")
        val reader = JsonObjectReader(StringReader(jsonCheckIn))
        val actual = CheckIn.Deserializer().deserialize(reader, fixture.logger)
        assertNotNull(actual)
        assertEquals("6d55218195564d6d88cf3883b84666f1", actual.checkInId.toString())
        assertEquals(checkIn.status, actual.status)
        assertTrue((checkIn.duration!! - actual.duration!!) < 0.01)
        assertEquals(checkIn.release, actual.release)
        assertEquals(checkIn.environment, actual.environment)
        val actualContext = actual.contexts
        assertEquals(checkIn.contexts.trace!!.traceId, actualContext.trace!!.traceId)
        val actualConfig = actual.monitorConfig!!
        val actualSchedule = actualConfig.schedule!!
        val expectedConfig = checkIn.monitorConfig!!
        val expectedSchedule = expectedConfig.schedule!!
        assertEquals(expectedConfig.maxRuntime, actualConfig.maxRuntime)
        assertEquals(expectedConfig.checkinMargin, actualConfig.checkinMargin)
        assertEquals(expectedConfig.timezone, actualConfig.timezone)
        assertEquals(expectedConfig.failureIssueThreshold, actualConfig.failureIssueThreshold)
        assertEquals(expectedConfig.recoveryThreshold, actualConfig.recoveryThreshold)
        assertEquals(expectedSchedule.type, actualSchedule.type)
        assertEquals(expectedSchedule.value, actualSchedule.value)
        assertEquals(expectedSchedule.unit, actualSchedule.unit)
    }

    @Test
    fun `deserializing checkin with missing required fields`() {
        val jsonCheckInWithoutId = "{\"status\":\"error\",\"monitor_slug\":\"some_slug\"}"
        val reader = JsonObjectReader(StringReader(jsonCheckInWithoutId))

        try {
            CheckIn.Deserializer().deserialize(reader, fixture.logger)
            fail()
        } catch (exception: Exception) {
            verify(fixture.logger).log(eq(SentryLevel.ERROR), any(), any<Exception>())
        }
    }

    // Helper

    private fun serialize(jsonSerializable: JsonSerializable): String {
        val wrt = StringWriter()
        val jsonWrt = JsonObjectWriter(wrt, 100)
        jsonSerializable.serialize(jsonWrt, fixture.logger)
        return wrt.toString()
    }
}
