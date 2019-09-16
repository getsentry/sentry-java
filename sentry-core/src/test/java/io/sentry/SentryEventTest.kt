package io.sentry

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class SentryEventTest {
    @Test
    fun `constructor creates a non empty event id`() =
            assertNotEquals(UUID.fromString("00000000-0000-0000-0000-00000000000"), SentryEvent().eventId)

    @Test
    fun `constructor defines timestamp after now`() =
            assertTrue(Instant.now().plus(1, ChronoUnit.HOURS).isAfter(SentryEvent().timestamp.toInstant()))

    @Test
    fun `constructor defines timestamp before hour ago`() =
            assertTrue(Instant.now().minus(1, ChronoUnit.HOURS).isBefore(SentryEvent().timestamp.toInstant()))

    @Test
    fun `timestamp is formatted in ISO 8601 in UTC with Z format`() {
        // Sentry expects this format:
        val expected = "2000-12-31T23:59:58Z"
        val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssX", Locale.ROOT)
        val date = OffsetDateTime.parse(expected, formatter)
        val actual = SentryEvent(null, Date(date.toInstant().toEpochMilli()))
        assertEquals(expected, actual.timestampIsoFormat)
    }
}
