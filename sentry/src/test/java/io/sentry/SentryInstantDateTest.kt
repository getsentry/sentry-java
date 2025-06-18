package io.sentry

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SentryInstantDateTest {
    @Test
    fun `nanos() offers ns precision`() {
        val date = SentryInstantDate(Instant.ofEpochSecond(1672742031, 123456789))
        assertEquals(1672742031123456789L, date.nanoTimestamp())
    }

    @Test
    fun `laterDateNanosByDiff() offers ns precision`() {
        val date1 = SentryInstantDate(Instant.ofEpochSecond(1672742031, 123456788))
        val date2 = SentryInstantDate(Instant.ofEpochSecond(1672742031, 123456789))
        assertEquals(1672742031123456789L, date1.laterDateNanosTimestampByDiff(date2))
    }

    @Test
    fun `laterDateNanosByDiff() offers ns precision reverse`() {
        val date1 = SentryInstantDate(Instant.ofEpochSecond(1672742031, 123456789))
        val date2 = SentryInstantDate(Instant.ofEpochSecond(1672742031, 123456788))
        assertEquals(1672742031123456789L, date1.laterDateNanosTimestampByDiff(date2))
    }

    // compareTo()

    @Test
    fun `compareTo() with equal dates returns 0`() {
        val date1 = SentryInstantDate(Instant.ofEpochSecond(1672742031, 123456789))
        val date2 = SentryInstantDate(Instant.ofEpochSecond(1672742031, 123456789))
        assertEquals(0, 1672742031123456789.compareTo(1672742031123456789))
        assertEquals(0, date1.compareTo(date2))
    }

    @Test
    fun `compareTo() returns -1 for earlier date`() {
        val date1 = SentryInstantDate(Instant.ofEpochSecond(1672742031, 123456788))
        val date2 = SentryInstantDate(Instant.ofEpochSecond(1672742031, 123456789))
        assertEquals(-1, 1672742031123456788.compareTo(1672742031123456789))
        assertEquals(-1, date1.compareTo(date2))
    }

    @Test
    fun `compareTo() returns 1 for later date`() {
        val date1 = SentryInstantDate(Instant.ofEpochSecond(1672742031, 123456789))
        val date2 = SentryInstantDate(Instant.ofEpochSecond(1672742031, 123456788))
        assertEquals(1, 1672742031123456789.compareTo(1672742031123456788))
        assertEquals(1, date1.compareTo(date2))
    }
}
