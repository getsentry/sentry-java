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

    @Test
    fun `adds breadcrumb with string as a parameter`() {
        val event = SentryEvent()
        event.addBreadcrumb("breadcrumb")
        assertEquals(1, event.breadcrumbs.filter { it.message == "breadcrumb" }.size)
    }

    @Test
    fun `when throwable is a ExceptionMechanismException, getOriginThrowable unwraps original throwable`() {
        val event = SentryEvent()
        val ex = RuntimeException()
        event.throwable = ExceptionMechanismException(Mechanism(), ex, Thread.currentThread())
        assertEquals(ex, event.originThrowable)
    }

    @Test
    fun `when throwable is not a ExceptionMechanismException, getOriginThrowable returns throwable`() {
        val event = SentryEvent()
        val ex = RuntimeException()
        event.throwable = ex
        assertEquals(ex, event.originThrowable)
    }

    @Test
    fun `addBreadcrumbs copies elements to SentryEvent and does not change the event breadcrumbs reference`() {
        val event = SentryEvent()
        event.addBreadcrumb("breadcrumb")
        val eventBreadcrumbs = event.breadcrumbs

        val breadcrumbsList = listOf(Breadcrumb(), Breadcrumb())
        event.addBreadcrumbs(breadcrumbsList)

        assertEquals(3, event.breadcrumbs.size)
        assertEquals(eventBreadcrumbs, event.breadcrumbs)
        assertNotEquals(breadcrumbsList, event.breadcrumbs)
    }

    @Test
    fun `addTags copies elements to SentryEvent and does not change the event tags reference`() {
        val event = SentryEvent()
        event.addTag("key1", "value1")
        val eventTags = event.tags

        val tagsMap = mapOf("key2" to "value2", "key3" to "value3")
        event.addTags(tagsMap)

        assertEquals(3, event.tags.size)
        assertEquals(eventTags, event.tags)
        assertNotEquals(tagsMap, event.tags)

    }
}
