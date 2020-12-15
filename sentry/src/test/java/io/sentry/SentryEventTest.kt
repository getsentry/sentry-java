package io.sentry

import com.nhaarman.mockitokotlin2.mock
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryId
import java.time.Instant
import java.time.temporal.ChronoUnit
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

    fun `adds breadcrumb with string as a parameter`() {
        val event = SentryEvent()
        event.addBreadcrumb("breadcrumb")
        assertEquals(1, event.breadcrumbs.filter { it.message == "breadcrumb" }.size)
    }
}
