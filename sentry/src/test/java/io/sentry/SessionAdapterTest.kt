package io.sentry

import io.sentry.test.injectForField
import org.mockito.kotlin.mock
import java.io.StringReader
import java.io.StringWriter
import java.lang.Exception
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SessionAdapterTest {
    private val serializer = JsonSerializer(SentryOptions().apply { setLogger(mock()) })

    @Test
    fun `null abnormal_mechanism does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                null,
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                null,
            )

        val actual = getActual(expected)!!

        assertSessionData(expected, actual)
    }

    @Test
    fun `null timestamp does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                null,
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        val actual = getActual(expected)!!

        assertSessionData(expected, actual)
    }

    @Test
    fun `null distinctId does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                null,
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        val actual = getActual(expected)!!

        assertSessionData(expected, actual)
    }

    @Test
    fun `null init does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                null,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        val actual = getActual(expected)!!

        assertSessionData(expected, actual)
    }

    @Test
    fun `null sequence does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                null,
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        val actual = getActual(expected)!!

        assertSessionData(expected, actual)
    }

    @Test
    fun `null duration does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                null,
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        val actual = getActual(expected)!!

        assertSessionData(expected, actual)
    }

    @Test
    fun `null IP does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                null,
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        val actual = getActual(expected)!!

        assertSessionData(expected, actual)
    }

    @Test
    fun `null user agent does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                null,
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        val actual = getActual(expected)!!

        assertSessionData(expected, actual)
    }

    @Test
    fun `null env does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                null,
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        val actual = getActual(expected)!!

        assertSessionData(expected, actual)
    }

    @Test
    fun `null started does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )
        expected.injectForField("started", null)
        val actual = getActual(expected)

        assertNull(actual)
    }

    @Test
    fun `null release does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )
        expected.injectForField("release", null)
        val actual = getActual(expected)

        assertNull(actual)
    }

    @Test
    fun `null status does not serialize `() {
        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )
        expected.injectForField("status", null)
        assertFailsWith<Exception> { getActual(expected) }
    }

    @Test
    fun `extra value does not serialize `() {
        val json =
            "{\n" +
                "  \"status\": \"ok\",\n" +
                "  \"sid\": \"c81d4e2e-bcf2-11e6-869b-7df92533d2db\",\n" +
                "  \"did\": \"123\",\n" +
                "  \"init\": true,\n" +
                "  \"extraValue\": \"test\",\n" +
                "  \"started\": \"2020-02-07T14:16:00.000Z\",\n" +
                "  \"seq\": 123456,\n" +
                "  \"errors\": 2,\n" +
                "  \"duration\": 6000,\n" +
                "  \"timestamp\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"abnormal_mechanism\": \"anr_foreground\",\n" +
                "  \"attrs\": {\n" +
                "    \"release\": \"io.sentry@1.0+123\",\n" +
                "    \"environment\": \"debug\",\n" +
                "    \"ip_address\": \"127.0.0.1\",\n" +
                "    \"user_agent\": \"jamesBond\"\n" +
                "  }\n" +
                "}"
        val actual = serializer.deserialize(StringReader(json), Session::class.java)!!

        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        assertSessionData(expected, actual)
    }

    @Test
    fun `extra attr does not serialize `() {
        val json =
            "{\n" +
                "  \"status\": \"ok\",\n" +
                "  \"sid\": \"c81d4e2e-bcf2-11e6-869b-7df92533d2db\",\n" +
                "  \"did\": \"123\",\n" +
                "  \"init\": true,\n" +
                "  \"started\": \"2020-02-07T14:16:00.000Z\",\n" +
                "  \"seq\": 123456,\n" +
                "  \"errors\": 2,\n" +
                "  \"duration\": 6000,\n" +
                "  \"timestamp\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"abnormal_mechanism\": \"anr_foreground\",\n" +
                "  \"attrs\": {\n" +
                "    \"release\": \"io.sentry@1.0+123\",\n" +
                "    \"environment\": \"debug\",\n" +
                "    \"ip_address\": \"127.0.0.1\",\n" +
                "    \"extraValue\": \"test\",\n" +
                "    \"user_agent\": \"jamesBond\"\n" +
                "  }\n" +
                "}"
        val actual = serializer.deserialize(StringReader(json), Session::class.java)!!

        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.000Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        assertSessionData(expected, actual)
    }

    @Test
    fun `missing status does not serialize `() {
        val json =
            "{\n" +
                "  \"sid\": \"c81d4e2e-bcf2-11e6-869b-7df92533d2db\",\n" +
                "  \"did\": \"123\",\n" +
                "  \"init\": true,\n" +
                "  \"started\": \"2020-02-07T14:16:00.000Z\",\n" +
                "  \"seq\": 123456,\n" +
                "  \"errors\": 2,\n" +
                "  \"duration\": 6000,\n" +
                "  \"timestamp\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"abnormal_mechanism\": \"anr_foreground\",\n" +
                "  \"attrs\": {\n" +
                "    \"release\": \"io.sentry@1.0+123\",\n" +
                "    \"environment\": \"debug\",\n" +
                "    \"ip_address\": \"127.0.0.1\",\n" +
                "    \"user_agent\": \"jamesBond\"\n" +
                "  }\n" +
                "}"
        val actual = serializer.deserialize(StringReader(json), Session::class.java)

        assertNull(actual)
    }

    @Test
    fun `missing started does not serialize `() {
        val json =
            "{\n" +
                "  \"sid\": \"c81d4e2e-bcf2-11e6-869b-7df92533d2db\",\n" +
                "  \"did\": \"123\",\n" +
                "  \"init\": true,\n" +
                "  \"status\": \"ok\",\n" +
                "  \"seq\": 123456,\n" +
                "  \"errors\": 2,\n" +
                "  \"duration\": 6000,\n" +
                "  \"timestamp\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"abnormal_mechanism\": \"anr_foreground\",\n" +
                "  \"attrs\": {\n" +
                "    \"release\": \"io.sentry@1.0+123\",\n" +
                "    \"environment\": \"debug\",\n" +
                "    \"ip_address\": \"127.0.0.1\",\n" +
                "    \"user_agent\": \"jamesBond\"\n" +
                "  }\n" +
                "}"
        val actual = serializer.deserialize(StringReader(json), Session::class.java)

        assertNull(actual)
    }

    @Test
    fun `missing release does not serialize `() {
        val json =
            "{\n" +
                "  \"sid\": \"c81d4e2e-bcf2-11e6-869b-7df92533d2db\",\n" +
                "  \"did\": \"123\",\n" +
                "  \"init\": true,\n" +
                "  \"status\": \"ok\",\n" +
                "  \"seq\": 123456,\n" +
                "  \"errors\": 2,\n" +
                "  \"duration\": 6000,\n" +
                "  \"started\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"timestamp\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"abnormal_mechanism\": \"anr_foreground\",\n" +
                "  \"attrs\": {\n" +
                "    \"environment\": \"debug\",\n" +
                "    \"ip_address\": \"127.0.0.1\",\n" +
                "    \"user_agent\": \"jamesBond\"\n" +
                "  }\n" +
                "}"
        val actual = serializer.deserialize(StringReader(json), Session::class.java)

        assertNull(actual)
    }

    @Test
    fun `invalid status does not serialize `() {
        val json =
            "{\n" +
                "  \"sid\": \"c81d4e2e-bcf2-11e6-869b-7df92533d2db\",\n" +
                "  \"did\": \"123\",\n" +
                "  \"init\": true,\n" +
                "  \"status\": \"lala\",\n" +
                "  \"seq\": 123456,\n" +
                "  \"errors\": 2,\n" +
                "  \"duration\": 6000,\n" +
                "  \"started\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"timestamp\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"abnormal_mechanism\": \"anr_foreground\",\n" +
                "  \"attrs\": {\n" +
                "    \"release\": \"io.sentry@1.0+123\",\n" +
                "    \"environment\": \"debug\",\n" +
                "    \"ip_address\": \"127.0.0.1\",\n" +
                "    \"user_agent\": \"jamesBond\"\n" +
                "  }\n" +
                "}"
        val actual = serializer.deserialize(StringReader(json), Session::class.java)

        assertNull(actual)
    }

    @Test
    fun `invalid timestamp does not serialize `() {
        val json =
            "{\n" +
                "  \"sid\": \"c81d4e2e-bcf2-11e6-869b-7df92533d2db\",\n" +
                "  \"did\": \"123\",\n" +
                "  \"init\": true,\n" +
                "  \"status\": \"ok\",\n" +
                "  \"seq\": 123456,\n" +
                "  \"errors\": 2,\n" +
                "  \"duration\": 6000,\n" +
                "  \"started\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"timestamp\": \"kkk\",\n" +
                "  \"abnormal_mechanism\": \"anr_foreground\",\n" +
                "  \"attrs\": {\n" +
                "    \"release\": \"io.sentry@1.0+123\",\n" +
                "    \"environment\": \"debug\",\n" +
                "    \"ip_address\": \"127.0.0.1\",\n" +
                "    \"user_agent\": \"jamesBond\"\n" +
                "  }\n" +
                "}"
        val actual = serializer.deserialize(StringReader(json), Session::class.java)!!

        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                null,
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        assertSessionData(expected, actual)
    }

    @Test
    fun `invalid sid does not serialize `() {
        val json =
            "{\n" +
                "  \"sid\": \"not a uuid\",\n" +
                "  \"did\": \"123\",\n" +
                "  \"init\": true,\n" +
                "  \"status\": \"ok\",\n" +
                "  \"seq\": 123456,\n" +
                "  \"errors\": 2,\n" +
                "  \"duration\": 6000,\n" +
                "  \"started\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"timestamp\": \"kkk\",\n" +
                "  \"abnormal_mechanism\": \"anr_foreground\",\n" +
                "  \"attrs\": {\n" +
                "    \"release\": \"io.sentry@1.0+123\",\n" +
                "    \"environment\": \"debug\",\n" +
                "    \"ip_address\": \"127.0.0.1\",\n" +
                "    \"user_agent\": \"jamesBond\"\n" +
                "  }\n" +
                "}"
        val actual = serializer.deserialize(StringReader(json), Session::class.java)!!

        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                null,
                2,
                "123",
                null,
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                "anr_foreground",
            )

        assertSessionData(expected, actual)
    }

    @Test
    fun `missing abnormal_mechanism does not serialize `() {
        val json =
            "{\n" +
                "  \"sid\": \"c81d4e2e-bcf2-11e6-869b-7df92533d2db\",\n" +
                "  \"did\": \"123\",\n" +
                "  \"init\": true,\n" +
                "  \"status\": \"ok\",\n" +
                "  \"seq\": 123456,\n" +
                "  \"errors\": 2,\n" +
                "  \"duration\": 6000,\n" +
                "  \"started\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"timestamp\": \"2020-02-07T14:16:00.001Z\",\n" +
                "  \"attrs\": {\n" +
                "    \"release\": \"io.sentry@1.0+123\",\n" +
                "    \"environment\": \"debug\",\n" +
                "    \"ip_address\": \"127.0.0.1\",\n" +
                "    \"user_agent\": \"jamesBond\"\n" +
                "  }\n" +
                "}"
        val actual = serializer.deserialize(StringReader(json), Session::class.java)!!

        val expected =
            Session(
                Session.State.Ok,
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                DateUtils.getDateTime("2020-02-07T14:16:00.001Z"),
                2,
                "123",
                "c81d4e2e-bcf2-11e6-869b-7df92533d2db",
                true,
                123456.toLong(),
                6000.toDouble(),
                "127.0.0.1",
                "jamesBond",
                "debug",
                "io.sentry@1.0+123",
                null,
            )

        assertSessionData(expected, actual)
    }

    // TODO: create a serializer.kt extensions in the sentry-test-support module
    private fun serializeToString(serialize: (StringWriter) -> Unit): String {
        val wrt = StringWriter()
        serialize(wrt)
        return wrt.toString()
    }

    private fun serializeToString(session: Session): String = this.serializeToString { wrt -> serializer.serialize(session, wrt) }

    private fun assertSessionData(
        expected: Session,
        actual: Session,
    ) {
        assertEquals(expected.sessionId, actual.sessionId)
        assertEquals(expected.distinctId, actual.distinctId)
        assertEquals(expected.init, actual.init)
        assertEquals(expected.started, actual.started)
        assertEquals(expected.timestamp, actual.timestamp)
        assertEquals(expected.duration, actual.duration)
        assertEquals(expected.status, actual.status)
        assertEquals(expected.errorCount(), actual.errorCount())
        assertEquals(expected.sequence, actual.sequence)
        assertEquals(expected.release, actual.release)
        assertEquals(expected.environment, actual.environment)
        assertEquals(expected.ipAddress, actual.ipAddress)
        assertEquals(expected.userAgent, actual.userAgent)
        assertEquals(expected.abnormalMechanism, actual.abnormalMechanism)
    }

    private fun getActual(expected: Session): Session? {
        val json = serializeToString(expected)

        return serializer.deserialize(StringReader(json), Session::class.java)
    }
}
