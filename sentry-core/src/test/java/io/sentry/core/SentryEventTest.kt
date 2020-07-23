package io.sentry.core

import com.nhaarman.mockitokotlin2.mock
import io.sentry.core.exception.ExceptionMechanismException
import io.sentry.core.protocol.Mechanism
import io.sentry.core.protocol.SentryId
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SentryEventTest {
    @Test
    fun `constructor creates a non empty event id`() =
            assertNotEquals(SentryId.EMPTY_ID, SentryEvent().eventId)

    @Test
    fun `constructor defines timestamp after now`() =
            assertTrue(Instant.now().plus(1, ChronoUnit.HOURS).isAfter(Instant.parse(DateUtils.getTimestamp(SentryEvent().timestamp))))

    @Test
    fun `constructor defines timestamp before hour ago`() =
            assertTrue(Instant.now().minus(1, ChronoUnit.HOURS).isBefore(Instant.parse(DateUtils.getTimestamp(SentryEvent().timestamp))))

    @Test
    fun `timestamp is formatted in ISO 8601 in UTC with Z format`() {
        // Sentry expects this format:
        val expected = "2000-12-31T23:59:58.000Z"
        val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSX", Locale.ROOT)
        val date = OffsetDateTime.parse(expected, formatter)
        val actual = SentryEvent(null, Date(date.toInstant().toEpochMilli()))
        assertEquals(expected, DateUtils.getTimestampIsoFormat(actual.timestamp))
    }

    @Test
    fun `if mechanism is not handled, it should return isCrashed=true`() {
        val mechanism = Mechanism()
        mechanism.isHandled = false
        val event = SentryEvent()
        val factory = SentryExceptionFactory(mock())
        val sentryExceptions = factory.getSentryExceptions(ExceptionMechanismException(mechanism, Throwable(), Thread()))
        event.exceptions = sentryExceptions
        assertTrue(event.isCrashed)
    }

    @Test
    fun `if mechanism is handled, it should return isCrashed=false`() {
        val mechanism = Mechanism()
        mechanism.isHandled = true
        val event = SentryEvent()
        val factory = SentryExceptionFactory(mock())
        val sentryExceptions = factory.getSentryExceptions(ExceptionMechanismException(mechanism, Throwable(), Thread()))
        event.exceptions = sentryExceptions
        assertFalse(event.isCrashed)
    }

    @Test
    fun `if mechanism handled flag is null, it should return isCrashed=false`() {
        val mechanism = Mechanism()
        mechanism.isHandled = null
        val event = SentryEvent()
        val factory = SentryExceptionFactory(mock())
        val sentryExceptions = factory.getSentryExceptions(ExceptionMechanismException(mechanism, Throwable(), Thread()))
        event.exceptions = sentryExceptions
        assertFalse(event.isCrashed)
    }

    @Test
    fun `if mechanism is not set, it should return isCrashed=false`() {
        val event = SentryEvent()
        val factory = SentryExceptionFactory(mock())
        val sentryExceptions = factory.getSentryExceptions(RuntimeException(Throwable()))
        event.exceptions = sentryExceptions
        assertFalse(event.isCrashed)
    }
}
